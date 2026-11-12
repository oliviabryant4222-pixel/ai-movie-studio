package studio.realframe;

/**
 * Pure-Java routing policy. Never silently sends a locally requested prompt
 * to a hosted provider. The UI asks explicit permission before cloud entry.
 */
public final class HybridRoutePolicy {
    public enum Mode { LOCAL, ONLINE, HYBRID }
    public enum Decision { LOCAL_IMAGE, ONLINE_CONFIRM, MANUAL_CHOICE, UNSUPPORTED_LOCAL_VIDEO }

    private static final long SAFE_IMAGE_RAM = 3L * 1024L * 1024L * 1024L;

    private HybridRoutePolicy() {}

    public static Mode parse(String mode) {
        if ("local".equalsIgnoreCase(mode)) return Mode.LOCAL;
        if ("online".equalsIgnoreCase(mode)) return Mode.ONLINE;
        return Mode.HYBRID;
    }

    public static Decision decide(Mode mode, boolean video, boolean installedNativeEngine,
                                  boolean importedModel, long availableRamBytes) {
        if (mode == Mode.ONLINE) return Decision.ONLINE_CONFIRM;
        if (video) {
            // Local video diffusion is not compiled; do not substitute still-video.
            if (mode == Mode.LOCAL) return Decision.UNSUPPORTED_LOCAL_VIDEO;
            return Decision.MANUAL_CHOICE;
        }
        if (mode == Mode.LOCAL) return Decision.LOCAL_IMAGE;
        if (installedNativeEngine && importedModel && availableRamBytes >= SAFE_IMAGE_RAM)
            return Decision.LOCAL_IMAGE;
        return Decision.MANUAL_CHOICE;
    }

    public static boolean cloudRequiresConsent(Decision target) {
        return target == Decision.ONLINE_CONFIRM || target == Decision.MANUAL_CHOICE;
    }
}
