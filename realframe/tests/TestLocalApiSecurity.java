import studio.realframe.LocalApiSecurity;

public final class TestLocalApiSecurity {
    static int count=0;
    static void check(String message,boolean ok){
        if(!ok)throw new AssertionError(message);
        count++;
    }
    public static void main(String[] args){
        String token="N2xxM3ABc4deF5GhI6JkL7mnO8PQ9rsT0uvWxYzaA1b";
        check("valid token matches",LocalApiSecurity.matchesToken(token,token));
        check("wrong token rejected",!LocalApiSecurity.matchesToken(token,token+"broken"));
        check("missing token rejected",!LocalApiSecurity.matchesToken(token,""));
        check("null supplied token rejected",!LocalApiSecurity.matchesToken(token,null));
        check("weak token rejected",!LocalApiSecurity.matchesToken("short","short"));
        check("status path accepted",LocalApiSecurity.safePath("/v1/status"));
        check("media path accepted",LocalApiSecurity.safePath("/v1/jobs/abcd1234/media/0"));
        check("root path accepted",LocalApiSecurity.safePath("/"));
        check("null path rejected",!LocalApiSecurity.safePath(null));
        check("bad path rejected",!LocalApiSecurity.safePath("/v1/../secret"));
        check("encoded traversal rejected",!LocalApiSecurity.safePath("/%2e%2e/secret"));
        check("backslash rejected",!LocalApiSecurity.safePath("/foo\\bar"));
        check("double slash rejected",!LocalApiSecurity.safePath("//foo"));
        check("query in path rejected",!LocalApiSecurity.safePath("/v1/status?access_key=secret"));
        check("single body accepted",LocalApiSecurity.acceptedBody(250,false));
        check("limit body accepted",LocalApiSecurity.acceptedBody(16384,false));
        check("oversized body rejected",!LocalApiSecurity.acceptedBody(16385,false));
        check("negative length rejected",!LocalApiSecurity.acceptedBody(-1,false));
        check("chunked request rejected",!LocalApiSecurity.acceptedBody(0,true));
        check("cross origin disabled",!LocalApiSecurity.allowCrossOrigin());
        System.out.println("PASS: "+count+" local API authentication and request validation tests");
    }
}
