import studio.realframe.LocalInferenceProbe;
import studio.realframe.LocalInferenceProbe.Result;

/** Test classifier independent of Android SDK: no fake 'verified' verdict. */
public final class TestLocalInferenceProbe {
    private static void expect(String title,Result actual,Result expected){
        if(actual!=expected)throw new AssertionError(title+": "+actual+" != "+expected);
    }
    public static void main(String[] args){
        expect("failed while offline",LocalInferenceProbe.classify(false,false,false),Result.FAILED);
        expect("failed while online",LocalInferenceProbe.classify(false,true,true),Result.FAILED);
        expect("local with online network",LocalInferenceProbe.classify(true,true,true),Result.LOCAL_WITH_INTERNET);
        expect("local with offline network",LocalInferenceProbe.classify(true,false,false),Result.LOCAL_WITHOUT_INTERNET);
        expect("wifi changed",LocalInferenceProbe.classify(true,true,false),Result.NETWORK_CHANGED);
        expect("wifi recovered",LocalInferenceProbe.classify(true,false,true),Result.NETWORK_CHANGED);
        if(!LocalInferenceProbe.summary(Result.LOCAL_WITH_INTERNET).contains("on this phone"))
            throw new AssertionError("Must identify on-device inference");
        if(!LocalInferenceProbe.summary(Result.FAILED).contains("No confirmed"))
            throw new AssertionError("Must not report a false success");
        System.out.println("PASS: 6 local-render classifier paths + 2 honesty checks");
    }
}
