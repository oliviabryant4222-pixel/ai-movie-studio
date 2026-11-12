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

    private static native int[] nativeGenerate(String model, String prompt, String negative,
                                               int width, int height, int steps, long seed, int threads);
    private static native String nativeVersion();
    private static native void nativeCancel();
}
