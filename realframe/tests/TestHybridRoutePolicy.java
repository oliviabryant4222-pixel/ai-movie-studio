import studio.realframe.HybridRoutePolicy;
import studio.realframe.HybridRoutePolicy.Mode;
import studio.realframe.HybridRoutePolicy.Decision;

/** Native-framework-free routing policy test runnable with javac. */
public final class TestHybridRoutePolicy {
  private static int passed=0;
  private static void test(String title,Decision actual,Decision expected){
    if(actual!=expected)throw new AssertionError(title+": "+actual+" expected "+expected);
    ++passed;
  }
  public static void main(String[] args){
    test("online image",HybridRoutePolicy.decide(Mode.ONLINE,false,false,false,0),Decision.ONLINE_CONFIRM);
    test("online video",HybridRoutePolicy.decide(Mode.ONLINE,true,false,false,0),Decision.ONLINE_CONFIRM);
    test("local image without model does not silently cloud",HybridRoutePolicy.decide(Mode.LOCAL,false,false,false,0),Decision.LOCAL_IMAGE);
    test("local video unsupported",HybridRoutePolicy.decide(Mode.LOCAL,true,true,true,8L<<30),Decision.UNSUPPORTED_LOCAL_VIDEO);
    test("hybrid model ready",HybridRoutePolicy.decide(Mode.HYBRID,false,true,true,5L<<30),Decision.LOCAL_IMAGE);
    test("hybrid RAM low",HybridRoutePolicy.decide(Mode.HYBRID,false,true,true,2L<<30),Decision.MANUAL_CHOICE);
    test("hybrid missing native library",HybridRoutePolicy.decide(Mode.HYBRID,false,false,true,5L<<30),Decision.MANUAL_CHOICE);
    test("hybrid no model",HybridRoutePolicy.decide(Mode.HYBRID,false,true,false,5L<<30),Decision.MANUAL_CHOICE);
    test("hybrid video",HybridRoutePolicy.decide(Mode.HYBRID,true,true,true,5L<<30),Decision.MANUAL_CHOICE);
    if(HybridRoutePolicy.parse("local")!=Mode.LOCAL)throw new AssertionError("parse LOCAL");
    if(HybridRoutePolicy.parse("online")!=Mode.ONLINE)throw new AssertionError("parse ONLINE");
    if(HybridRoutePolicy.parse("hybrid")!=Mode.HYBRID)throw new AssertionError("parse HYBRID");
    if(HybridRoutePolicy.cloudRequiresConsent(Decision.LOCAL_IMAGE))throw new AssertionError("local never requires cloud");
    if(!HybridRoutePolicy.cloudRequiresConsent(Decision.ONLINE_CONFIRM))throw new AssertionError("cloud must confirm");
    System.out.println("PASS: "+passed+" hybrid routing cases + 5 consent and parsing checks");
  }
}
