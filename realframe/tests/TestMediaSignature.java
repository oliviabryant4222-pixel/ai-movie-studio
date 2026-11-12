import studio.realframe.MediaSignature;

public class TestMediaSignature {
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check("image/png".equals(MediaSignature.detect(new byte[]{(byte)137,80,78,71,13,10,26,10})),"png");
        check("image/jpeg".equals(MediaSignature.detect(new byte[]{(byte)255,(byte)216,(byte)255,12})),"jpeg");
        check("image/webp".equals(MediaSignature.detect("RIFF0000WEBP".getBytes())),"webp");
        check("video/mp4".equals(MediaSignature.detect("0000ftypisom".getBytes())),"mp4");
        check("".equals(MediaSignature.detect("<html>bad token".getBytes())),"reject html");
        check(!MediaSignature.matchesKind("image/png", "video"),"reject mismatched kind");
        check(".webp".equals(MediaSignature.extension("image/webp")),"extension");
        System.out.println("7 media header tests passed");
    }
}
