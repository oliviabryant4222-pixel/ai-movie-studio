import studio.realframe.ModelFileInspector;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tiny sparse binary fixtures; does not load or generate with real model weights. */
public final class TestModelFileInspector {
    static int passed=0;
    static void check(String name,boolean condition) {
        if(!condition)throw new AssertionError(name);
        passed++;
    }
    static File file(Path folder,String name,byte[] header)throws Exception {
        File f=folder.resolve(name).toFile();
        try(RandomAccessFile out=new RandomAccessFile(f,"rw")){
            out.setLength(9L*1024*1024);
            out.seek(0);out.write(header);
        }
        return f;
    }
    public static void main(String[] args)throws Exception {
        Path folder=Files.createTempDirectory("rf-model-fixtures");
        try {
            byte[] safe=new byte[16];
            safe[0]=16;safe[8]='{';
            File good=file(folder,"image.safetensors",safe);
            check("safetensors header accepted",ModelFileInspector.inspect(good).plausible);
            byte[] wrong=new byte[16];wrong[0]=(byte)255;wrong[7]=127;wrong[8]='{';
            File bad=file(folder,"broken.safetensors",wrong);
            check("impossible metadata size rejected",!ModelFileInspector.inspect(bad).plausible);
            byte[] magic=new byte[16];
            magic[0]='G';magic[1]='G';magic[2]='U';magic[3]='F';magic[4]=3;
            File gguf=file(folder,"weights.gguf",magic);
            check("gguf magic accepted",ModelFileInspector.inspect(gguf).plausible);
            byte[] invalid=new byte[16];invalid[0]='b';
            File badGguf=file(folder,"fake.gguf",invalid);
            check("invalid gguf rejected",!ModelFileInspector.inspect(badGguf).plausible);
            File unrecognized=file(folder,"fake.bin",magic);
            check("unrecognized extension rejected",!ModelFileInspector.inspect(unrecognized).plausible);
            check("missing file rejected",!ModelFileInspector.inspect(new File(folder.toFile(),"missing.gguf")).plausible);
            System.out.println("PASS: "+passed+" offline checkpoint header smoke tests");
        }finally{
            try(java.util.stream.Stream<Path> files=Files.list(folder)){
                files.forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
            }
            Files.deleteIfExists(folder);
        }
    }
}
