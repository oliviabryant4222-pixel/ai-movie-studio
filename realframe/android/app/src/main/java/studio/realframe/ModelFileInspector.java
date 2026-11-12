package studio.realframe;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Cheap, offline, read-only checkpoint format preflight.
 * Positive result means the container HEADER looks plausible; it does NOT
 * prove SD1.5 architecture, model completeness, compatible weights or license.
 */
public final class ModelFileInspector {
    public static final class Result {
        public final boolean plausible;
        public final String kind;
        public final String detail;
        private Result(boolean plausible,String kind,String detail) {
            this.plausible=plausible;this.kind=kind;this.detail=detail;
        }
    }
    private ModelFileInspector() { }
    private static Result bad(String kind,String why){return new Result(false,kind,why);}
    private static Result good(String kind,String why){return new Result(true,kind,why);}

    public static Result inspect(File file) {
        if(file==null||!file.isFile())return bad("unknown","File is missing");
        if(file.length()<8L*1024*1024)return bad("unknown","File too small; likely a model pointer or broken download");
        String name=file.getName().toLowerCase(Locale.ROOT);
        try(FileInputStream stream=new FileInputStream(file)) {
            byte[] first=new byte[16];
            if(stream.read(first)!=16)return bad("unknown","Cannot read checkpoint header");
            if(name.endsWith(".safetensors")) {
                long size=ByteBuffer.wrap(first,0,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                if(size<2||size>16L*1024*1024||size+8>=file.length())
                    return bad("safetensors","Invalid or truncated safetensors metadata header");
                if(first[8]!='{')return bad("safetensors","Expected JSON tensor metadata");
                // A complete header is checked by the native diffusion loader.
                return good("safetensors","Header looks plausible. Weight architecture and tensor shapes not yet verified.");
            }
            if(name.endsWith(".gguf")) {
                if(first[0]!='G'||first[1]!='G'||first[2]!='U'||first[3]!='F')
                    return bad("GGUF","GGUF magic bytes missing");
                int version=ByteBuffer.wrap(first,4,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if(version<2||version>4)return bad("GGUF","Unsupported GGUF container version");
                return good("GGUF","GGUF header looks plausible; standalone image checkpoint compatibility is NOT guaranteed.");
            }
            if(name.endsWith(".ckpt")) {
                boolean zip=first[0]==0x50&&first[1]==0x4B&&first[2]==0x03&&first[3]==0x04;
                boolean pickle=(first[0]&0xFF)==0x80 && (first[1]&0xFF)>=2 && (first[1]&0xFF)<=5;
                if(!zip&&!pickle)return bad("ckpt","Unknown PyTorch checkpoint signature");
                return good("ckpt","PyTorch container looks plausible; compatibility and safety are not guaranteed.");
            }
            return bad("unknown","Only .safetensors, .gguf and .ckpt are accepted");
        }catch(IOException error){return bad("unknown","Model is not readable: "+error.getMessage());}
    }
}
