package studio.realframe;

/**
 * Stable-diffusion.cpp JNI bridge. Compiled into the arm64 APK for genuine
 * fully on-device CPU text-to-image generation. NOT QNN/NPU accelerated.
 * Import an architecture-compatible model checkpoint separately.
 */
public final class LocalImageEngine {
    private static final boolean loaded;
    private static final String loadError;
    static {
        boolean ok = false;
        String reason = "";
        try { System.loadLibrary("realframe_sd"); ok = true; }
        catch (Throwable e) { reason = e.toString(); }
        loaded = ok;
        loadError = reason;
    }
    private LocalImageEngine() { }

    public static boolean isAvailable() { return loaded; }
    public static String unavailableReason() { return loadError; }
    public static String version() {
        if (!loaded) return "Native engine missing: " + loadError;
        try { return nativeVersion(); } catch (Throwable t) { return "Engine load failure: " + t; }
    }

    /**
     * Generate a real image locally from a compatible SD checkpoint.
     * Must be called ONLY from a background thread. RAM use can be several GB.
     */
    public static int[] generate(String modelPath, String prompt, String negative,
                                 int width, int height, int steps, long seed, int threads) {
        if (!loaded) throw new IllegalStateException("Native image engine missing: " + loadError);
        if (modelPath == null || modelPath.isEmpty()) throw new IllegalArgumentException("Import a model first");
        if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("Enter a prompt");
        if (width < 64 || width > 1024 || width % 64 != 0 ||
            height < 64 || height > 1024 || height % 64 != 0)
            throw new IllegalArgumentException("Image dimensions must be multiples of 64, up to 1024");
        if (steps < 1 || steps > 100 || threads < 1 || threads > 8)
            throw new IllegalArgumentException("Invalid steps or CPU threads");
        return nativeGenerate(modelPath, prompt.trim(), negative==null?"":negative.trim(),
                              width, height, steps, seed, threads);
    }
    public static void cancel() { if (loaded) nativeCancel(); }

    /** Called synchronously on the rendering worker after each completed image.
     * The consumer should save the image promptly; JNI releases its native pixels
     * immediately after this call returns.
     */
    public interface BatchListener {
        void onFrame(int imageIndex, int[] argb);
    }

    /** Genuine on-device sequential batch; one native model load, 1–8 images.
     * Not GPU parallelism. Cancelling may return a partial batch.
     */
    public static int generateBatch(String modelPath, String prompt, String negative,
                                    int width, int height, int steps, long seed, int threads,
                                    int count, BatchListener callback) {
        if (!loaded) throw new IllegalStateException("Local diffusion library unavailable: " + loadError);
        if (callback == null) throw new IllegalArgumentException("Missing frame callback");
        if (count < 1 || count > 8) throw new IllegalArgumentException("Batch count must be 1–8");
        if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("Prompt required");
        if (modelPath == null || modelPath.isEmpty()) throw new IllegalArgumentException("Import a local model first");
        if (width < 64 || width > 1024 || width % 64 != 0 ||
            height < 64 || height > 1024 || height % 64 != 0)
            throw new IllegalArgumentException("Resolution must be multiples of 64 up to 1024");
        if (steps < 1 || steps > 100 || threads < 1 || threads > 8)
            throw new IllegalArgumentException("Invalid steps or thread count");
        if (seed < 1 || seed > 2147483647L)
            throw new IllegalArgumentException("Batch seed must be 1 to 2147483647");
        return nativeGenerateBatch(modelPath, prompt.trim(), negative == null ? "" : negative,
                                   width, height, steps, seed, threads, count, callback);
    }

    /**
     * Real img2img reference-conditioned SD generation. The reference is RGB
     * photo input, NOT face-identity embedding. Smaller strength retains more
     * of the original composition and features; consistency is not guaranteed.
     */
    public static int generateReferenceBatch(String model, String prompt, String negative,
                                             int width,int height,int steps,long seed,int threads,
                                             int count,int[] referenceARGB,float strength,
                                             BatchListener sink) {
        if(!loaded)throw new IllegalStateException("Native local AI engine unavailable: "+loadError);
        if(model==null||model.trim().isEmpty()||prompt==null||prompt.trim().isEmpty())
            throw new IllegalArgumentException("Choose a local model and enter a prompt");
        if(width<64||height<64||width>1024||height>1024||width%64!=0||height%64!=0)
            throw new IllegalArgumentException("Invalid image resolution");
        if(referenceARGB==null||referenceARGB.length!=(long)width*height)
            throw new IllegalArgumentException("Reference image dimensions must match output");
        if(strength<0.10f||strength>0.95f)
            throw new IllegalArgumentException("Strength must be 0.10–0.95");
        if(count<1||count>8||steps<1||steps>100||seed<1||seed>2147483647L||
           threads<1||threads>8||sink==null)
            throw new IllegalArgumentException("Invalid local image settings");
        return nativeReferenceBatch(model,prompt.trim(),negative==null?"":negative.trim(),
            width,height,steps,seed,threads,count,referenceARGB,strength,sink);
    }

    /**
     * Genuine on-device AI storyboard keyframes. Every scene gets its own
     * text prompt. Scene N+1 uses the generated scene N keyframe as img2img
     * input, which can improve continuity but cannot guarantee face identity.
     * A separate Android video encoder can animate these still keyframes.
     */
    public static int generateStoryboard(String model,String[] scenes,String negative,
                                         int width,int height,int steps,long seed,int threads,
                                         float continuityStrength,BatchListener sink){
        if(!loaded)throw new IllegalStateException("Local SD library is missing: "+loadError);
        if(model==null||model.isEmpty())throw new IllegalArgumentException("Import SD model first");
        if(scenes==null||scenes.length<1||scenes.length>12)
            throw new IllegalArgumentException("Use 1–12 storyboard scenes");
        for(String prompt:scenes)
            if(prompt==null||prompt.trim().isEmpty()||prompt.length()>4000)
                throw new IllegalArgumentException("Empty or excessively long scene prompt");
        if(width<64||width>768||height<64||height>768||width%64!=0||height%64!=0)
            throw new IllegalArgumentException("Unsupported storyboard resolution");
        if(steps<1||steps>40||seed<1||seed>2147483647L||threads<1||threads>8||
           continuityStrength<0.15f||continuityStrength>0.90f||sink==null)
            throw new IllegalArgumentException("Invalid storyboard options");
        return nativeStoryboard(model,scenes,negative==null?"":negative,width,height,steps,
                                seed,threads,continuityStrength,sink);
    }
    private static native int nativeStoryboard(String model,String[] scenes,String negative,
            int width,int height,int steps,long seed,int threads,float continuityStrength,
            BatchListener sink);

    private static native int nativeReferenceBatch(String model,String prompt,String negative,
            int width,int height,int steps,long seed,int threads,int count,
            int[] referenceARGB,float strength,BatchListener sink);

    private static native int nativeGenerateBatch(String model, String prompt, String negative,
                                                   int width, int height, int steps, long seed,
                                                   int threads, int count, BatchListener listener);

    private static native int[] nativeGenerate(String model, String prompt, String negative,
                                               int width, int height, int steps, long seed, int threads);
    private static native String nativeVersion();
    private static native void nativeCancel();
}
