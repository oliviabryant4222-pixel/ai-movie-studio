package studio.realframe;

/**
 * Experimental CPU-only on-device Wan T2V inference.
 * No network calls; no checkpoint weights bundled.
 * An arm64 CPU video model with T5, VAE and enough phone memory is required.
 * A successful Android APK build is NOT proof of acceptable device performance.
 */
public final class LocalVideoEngine {
    private static final boolean available;
    private static final String problem;
    static {
        boolean ok=false;
        String failure="";
        try { System.loadLibrary("realframe_sd"); ok=true; }
        catch (Throwable e) { failure=e.toString(); }
        available=ok;problem=failure;
    }
    private LocalVideoEngine(){ }

    public interface FrameSink {
        /**
         * Invoked on the native inference worker thread. Can block to encode.
         * The array is fresh per frame and released after this method returns.
         */
        void onFrame(int index,int width,int height,int fps,int[] argb);
    }

    public static boolean isAvailable(){return available;}
    public static String issue(){return problem;}

    public static int generate(String diffusionPath,String t5Path,String vaePath,
                               String prompt,String negative,
                               int width,int height,int frames,int steps,long seed,
                               int threads,FrameSink sink){
        if(!available)throw new IllegalStateException("Native video engine is missing: "+problem);
        if(diffusionPath==null||diffusionPath.isEmpty()||
           t5Path==null||t5Path.isEmpty()||vaePath==null||vaePath.isEmpty())
            throw new IllegalArgumentException("Import compatible Wan diffusion, T5 and VAE components");
        if(prompt==null||prompt.trim().isEmpty())throw new IllegalArgumentException("Prompt required");
        if(sink==null)throw new IllegalArgumentException("Missing frame encoder");
        if(width<192||width>512||width%64!=0||height<192||height>512||height%64!=0)
            throw new IllegalArgumentException("Video dimensions must be multiples of 64 between 192 and 512");
        if(frames<5||frames>33||(frames-1)%4!=0)
            throw new IllegalArgumentException("Frames must be 5, 9, 13, ... 33");
        if(steps<1||steps>30||seed<1||seed>2147483647L||threads<1||threads>8)
            throw new IllegalArgumentException("Invalid steps, seed or thread count");
        return nativeGenerate(diffusionPath,t5Path,vaePath,prompt.trim(),negative==null?"":negative.trim(),
                              width,height,frames,steps,seed,threads,sink);
    }
    public static void cancel(){if(available)nativeCancel();}
    private static native int nativeGenerate(String diffusion,String t5,String vae,
                                             String prompt,String negative,int width,
                                             int height,int frames,int steps,long seed,
                                             int threads,FrameSink sink);
    private static native void nativeCancel();
}
