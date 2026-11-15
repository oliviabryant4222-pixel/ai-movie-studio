package studio.realframe;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * Lightweight, consistent Android UI design and click safety. No framework,
 * network dependency or external icon/font assets. All controls are real
 * View.OnClickListeners: fast double tapping never launches two render screens.
 */
public final class StudioUi {
    public static final int BG=0xFF090F1C;
    public static final int CARD=0xFF17243A;
    public static final int CARD_ALT=0xFF21334D;
    public static final int WHITE=0xFFF4F7FF;
    public static final int MUTED=0xFFAFC2D8;
    public static final int MINT=0xFF68E3D1;
    public static final int VIOLET=0xFFB6A3FF;
    public static final int OUTLINE=0xFF354867;
    private static final long TAP_GUARD_MS=480L;

    private StudioUi(){}
    public static int dp(Activity a,float size){
        return Math.round(size*a.getResources().getDisplayMetrics().density);
    }
    public static GradientDrawable surface(Activity a,int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(a,radius));
        d.setStroke(dp(a,1),OUTLINE);
        return d;
    }
    public static GradientDrawable gradient(Activity a,int color1,int color2,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{color1,color2});
        d.setCornerRadius(dp(a,radius));
        return d;
    }
    public static Drawable ripple(Activity a,Drawable content,int radius){
        GradientDrawable mask=new GradientDrawable();
        mask.setColor(0xFFFFFFFF);mask.setCornerRadius(dp(a,radius));
        return new RippleDrawable(ColorStateList.valueOf(0x36FFFFFF),content,mask);
    }
    public static void bind(Activity activity,View view,String description,Runnable action){
        if(view==null||action==null)
            throw new IllegalArgumentException("Clickable control needs a view and an action");
        view.setClickable(true);
        view.setFocusable(true);
        if(description!=null&&!description.trim().isEmpty())
            view.setContentDescription(description);
        final long[] last={0L};
        view.setOnClickListener(v->{
            long now=SystemClock.uptimeMillis();
            if(now-last[0]<TAP_GUARD_MS)return;
            last[0]=now;
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            try{
                action.run();
            }catch(ActivityNotFoundException missing){
                Toast.makeText(activity,"This screen is unavailable in this build",Toast.LENGTH_LONG).show();
            }catch(RuntimeException error){
                String reason=error.getMessage();
                Toast.makeText(activity,"Action could not open: "+
                    (reason==null?error.getClass().getSimpleName():reason),
                    Toast.LENGTH_LONG).show();
            }
        });
    }
    public static void styleButton(Activity a,Button button,int fill,String description,Runnable action){
        button.setAllCaps(false);
        int r=(fill>>>16)&255,g=(fill>>>8)&255,b=(fill)&255;
        int brightness=(r*299+g*587+b*114)/1000;
        button.setTextColor(brightness>155?0xFF102033:WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(a,50));
        button.setPadding(dp(a,12),dp(a,9),dp(a,12),dp(a,9));
        button.setBackground(ripple(a,surface(a,fill,14),14));
        bind(a,button,description,action);
    }
    public static void styleTile(Activity a,View tile,String label,Runnable action){
        tile.setBackground(ripple(a,surface(a,CARD_ALT,15),15));
        tile.setMinimumHeight(dp(a,68));
        tile.setElevation(dp(a,2));
        bind(a,tile,label,action);
    }
    public static void open(Activity from,Class<? extends Activity> target){
        // The OS still validates the explicit activity. Failure is surfaced to
        // the user through bind(), never swallowed or recorded as success.
        Intent intent=new Intent(from,target);
        ComponentName destination=intent.getComponent();
        if(destination==null)throw new ActivityNotFoundException("Missing screen target");
        from.startActivity(intent);
    }
}
