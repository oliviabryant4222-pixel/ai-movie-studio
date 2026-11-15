import studio.realframe.ModelStoragePolicy;
import java.io.File;
import java.nio.file.Files;

/** Pure JDK regression checks; deleting generated files outside allow-list is forbidden. */
public final class TestModelStoragePolicy {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception {
        File app=Files.createTempDirectory("realframe-safe-models").toFile();
        File managed=ModelStoragePolicy.image(app);
        check(managed.getParentFile().mkdirs(),"create managed model cache");
        check(managed.createNewFile(),"create managed model file");
        check(ModelStoragePolicy.isManaged(app,managed),"image weights allowed");
        check(ModelStoragePolicy.isManaged(app,ModelStoragePolicy.imagePartial(app)),"image partial allowed");
        for(int i=0;i<3;i++){
            check(ModelStoragePolicy.isManaged(app,ModelStoragePolicy.video(app,i)),"video "+i);
            check(ModelStoragePolicy.isManaged(app,ModelStoragePolicy.videoPartial(app,i)),"video partial "+i);
        }
        check(ModelStoragePolicy.managed(app).length==8,"eight exact managed paths");
        check(!ModelStoragePolicy.isManaged(app,new File(app,"my-characters.json")),"character vault protected");
        check(!ModelStoragePolicy.isManaged(app,new File(app,"wan_video_models/my-custom-model.gguf")),"imported model protected");
        File other=Files.createTempFile("user-image","png").toFile();
        check(!ModelStoragePolicy.isManaged(app,other),"outside app protected");
        check(!ModelStoragePolicy.isManaged(app,new File(app,"model_cache/../my-characters.json")),"traversal prevented");
        try{
            ModelStoragePolicy.deleteManaged(app,other);
            throw new AssertionError("External file deletion was permitted");
        }catch(SecurityException expected){}
        check(other.exists(),"external file untouched");
        check(ModelStoragePolicy.deleteManaged(app,managed),"managed file deleted");
        check(!managed.exists(),"auto image model removed");
        other.delete();
        app.delete();
        System.out.println("PASS: 14 exact managed-path / content preservation checks");
    }
}
