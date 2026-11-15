package studio.realframe;

/**
 * Classifies the results of a genuine on-device JNI render with observed
 * Android connectivity at the beginning and end of the render. This does not
 * inspect network traffic; the caller must use LocalImageEngine, NOT an HTTP
 * endpoint. A successful test is evidence of on-device model execution, not
 * proof of zero packets from all other applications.
 */
public final class LocalInferenceProbe {
    public enum Result { FAILED, LOCAL_WITH_INTERNET, LOCAL_WITHOUT_INTERNET, NETWORK_CHANGED }

    private LocalInferenceProbe() {}

    public static Result classify(boolean generatedPixels, boolean startOnline, boolean endOnline) {
        if (!generatedPixels) return Result.FAILED;
        if (startOnline != endOnline) return Result.NETWORK_CHANGED;
        return startOnline ? Result.LOCAL_WITH_INTERNET : Result.LOCAL_WITHOUT_INTERNET;
    }

    public static String summary(Result result) {
        switch (result) {
            case LOCAL_WITH_INTERNET:
                return "Local AI rendered on this phone while Internet was available.";
            case LOCAL_WITHOUT_INTERNET:
                return "Local AI rendered on this phone while Internet was unavailable.";
            case NETWORK_CHANGED:
                return "Local AI rendered, but network availability changed during the test.";
            default:
                return "No confirmed local image was generated.";
        }
    }
}
