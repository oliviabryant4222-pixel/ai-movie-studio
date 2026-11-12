package studio.realframe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Pure Java guardrails, separately testable without Android. */
public final class LocalApiSecurity {
    private LocalApiSecurity(){}
    public static boolean matchesToken(String expected,String supplied) {
        if(expected==null||supplied==null||expected.length()<40||supplied.length()>256)
            return false;
        byte[] a=expected.getBytes(StandardCharsets.US_ASCII);
        byte[] b=supplied.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(a,b);
    }
    public static boolean safePath(String path) {
        if(path==null||path.length()<1||path.length()>180||path.charAt(0)!='/')
            return false;
        if(path.contains("..")||path.contains("%")||path.contains("\\")||
           path.contains("//")||path.indexOf('\0')>=0)
            return false;
        for(int i=0;i<path.length();i++){
            char c=path.charAt(i);
            if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||
                 (c>='0'&&c<='9')||c=='/'||c=='_'||c=='-'))
                return false;
        }
        return true;
    }
    public static boolean acceptedBody(int len,boolean transferEncoding){
        return !transferEncoding&&len>=0&&len<=16_384;
    }
    /** This app intentionally does NOT set Access-Control-Allow-Origin: *. */
    public static boolean allowCrossOrigin(){return false;}
}
