package studio.realframe;

/**
 * Tiny, dependency-free byte signature check used before MediaStore writes.
 * An API may return an HTML error page with HTTP 200; never label that as a PNG/MP4.
 * This is a header sanity check, not a security scanner or full codec decoder.
 */
public final class MediaSignature {
    private MediaSignature() { }

    private static boolean ascii(byte[] bytes, int start, String expected) {
        if (bytes == null || start < 0 || bytes.length < start + expected.length()) return false;
        for (int i = 0; i < expected.length(); ++i) {
            if ((bytes[start + i] & 0xff) != expected.charAt(i)) return false;
        }
        return true;
    }

    public static String detect(byte[] bytes) {
        if (bytes == null) return "";
        if (bytes.length >= 8 && (bytes[0] & 255) == 137 && ascii(bytes, 1, "PNG") &&
                (bytes[4] & 255) == 13 && (bytes[5] & 255) == 10 &&
                (bytes[6] & 255) == 26 && (bytes[7] & 255) == 10) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 &&
                (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) return "image/jpeg";
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP"))
            return "image/webp";
        if (bytes.length >= 12 && ascii(bytes, 4, "ftyp")) return "video/mp4";
        return "";
    }

    public static boolean matchesKind(String mime, String kind) {
        if ("video".equals(kind)) return "video/mp4".equals(mime);
        return "image/png".equals(mime) || "image/jpeg".equals(mime) || "image/webp".equals(mime);
    }

    public static String extension(String mime) {
        if ("video/mp4".equals(mime)) return ".mp4";
        if ("image/jpeg".equals(mime)) return ".jpg";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/png".equals(mime)) return ".png";
        throw new IllegalArgumentException("Unsupported media type: " + mime);
    }
}
