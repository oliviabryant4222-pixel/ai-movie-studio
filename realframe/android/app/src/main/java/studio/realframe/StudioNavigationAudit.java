package studio.realframe;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;

/**
 * On-device manifest linkage inspection. This verifies that every major
 * destination exists in the installed APK, NOT that every dynamic action
 * can render without models, user input or network access.
 */
public final class StudioNavigationAudit {
    private StudioNavigationAudit(){}
    public static final class Result {
        public final int activities,services;
        public final List<String> issues;
        Result(int a,int s,List<String> failures){
            activities=a;services=s;issues=failures;
        }
        public boolean passed(){return issues.isEmpty();}
        public String detail(){
            if(passed())return "All "+activities+" main screens and "+services+
                " background services are registered in this APK. Live taps, models and cloud endpoints still require functional tests.";
            return "Navigation check detected "+issues.size()+" missing components:\n"+
                android.text.TextUtils.join("\n",issues);
        }
    }
    private static final Class<?>[] ACTIVITIES={
        StudioDashboardActivity.class,QuickStartActivity.class,
        UnifiedCreateActivity.class,LocalImageActivity.class,LocalVideoActivity.class,
        LocalProofActivity.class,StudioHealthActivity.class,
        ReferenceStudioActivity.class,LocalAutoFilmActivity.class,
        StoryMovieComposerActivity.class,LocalApiActivity.class,LocalWebClientActivity.class,
        WebStudioActivity.class,ModelManagerActivity.class,MainActivity.class,
        ProductionPlannerActivity.class,OfflinePhotoLabActivity.class,
        OfflineImageVideoActivity.class
    };
    private static final Class<?>[] SERVICES={
        AutoModelDownloadService.class,AutoVideoModelDownloadService.class,
        LocalApiService.class,RenderService.class
    };
    public static Result check(Context context){
        PackageManager pm=context.getPackageManager();
        List<String> errors=new ArrayList<>();
        for(Class<?> klass:ACTIVITIES){
            try{
                pm.getActivityInfo(new ComponentName(context,klass),0);
            }catch(PackageManager.NameNotFoundException error){
                errors.add("Missing screen: "+klass.getSimpleName());
            }
        }
        for(Class<?> klass:SERVICES){
            try{
                pm.getServiceInfo(new ComponentName(context,klass),0);
            }catch(PackageManager.NameNotFoundException error){
                errors.add("Missing background service: "+klass.getSimpleName());
            }
        }
        return new Result(ACTIVITIES.length,SERVICES.length,errors);
    }
}
