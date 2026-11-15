package studio.realframe;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Strict allow-list for automated model weights and .part download files.
 * Never delete gallery outputs, imported user checkpoints, reference photos,
 * saved character profiles, scripts or any arbitrary URI/path.
 */
public final class ModelStoragePolicy {
    private ModelStoragePolicy() {}
    public static final String IMAGE="model_cache/checkpoint_auto_sd15_q4_0.gguf";
    public static final List<String> WAN=Collections.unmodifiableList(Arrays.asList(
        "wan_video_models/diffusion_auto_Wan2.1-T2V-1.3B-Q4_K_S.gguf",
        "wan_video_models/t5_auto_umt5-xxl-encoder-Q4_K_M.gguf",
        "wan_video_models/vae_auto_wan_2.1_vae.safetensors"
    ));
    public static File image(File filesDir){return new File(filesDir,IMAGE);}
    public static File imagePartial(File filesDir){return new File(filesDir,IMAGE+".part");}
    public static File video(File filesDir,int i){
        if(i<0||i>=WAN.size())throw new IllegalArgumentException("Invalid video component");
        return new File(filesDir,WAN.get(i));
    }
    public static File videoPartial(File filesDir,int i){
        return new File(filesDir,WAN.get(i)+".part");
    }
    public static boolean isManaged(File appFiles,File candidate){
        if(appFiles==null||candidate==null)return false;
        try{
            String actual=candidate.getCanonicalPath();
            for(File allowed:managed(appFiles)) {
                if(allowed.getCanonicalPath().equals(actual))return true;
            }
        }catch(IOException ignored){}
        return false;
    }
    public static File[] managed(File appFiles){
        File[] paths=new File[2+WAN.size()*2];
        paths[0]=image(appFiles);paths[1]=imagePartial(appFiles);
        for(int i=0;i<WAN.size();i++){
            paths[2+i*2]=video(appFiles,i);
            paths[3+i*2]=videoPartial(appFiles,i);
        }
        return paths;
    }
    public static long bytes(File f){return f!=null&&f.isFile()?f.length():0;}
    public static long imageBytes(File filesDir){
        return bytes(image(filesDir))+bytes(imagePartial(filesDir));
    }
    public static long videoBytes(File filesDir){
        long total=0;
        for(int i=0;i<WAN.size();i++)
            total+=bytes(video(filesDir,i))+bytes(videoPartial(filesDir,i));
        return total;
    }
    public static boolean deleteManaged(File appFiles,File target){
        if(!isManaged(appFiles,target))throw new SecurityException("Refusing to delete non-managed file");
        return !target.exists()||target.isFile()&&target.delete();
    }
}
