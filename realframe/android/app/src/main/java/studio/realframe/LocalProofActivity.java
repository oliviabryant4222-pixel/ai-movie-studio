package studio.realframe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real smoke test for Local-While-Online and Fully-Offline: runs the JNI SD
 * engine against an installed, local model without calling any hosted API.
 * Internet may be ON or OFF. No external requests are made by this activity.
 *
 * Do not claim device verification until a genuine pixel array returns.
 * An Android CI build alone does not run this on a physical handset.
 */
public final class LocalProofActivity extends Activity {
    private static final int BACK=0xFF0B1425, CARD=0xFF192A43, WHITE=0xFFF2F6FF,
        MUTED=0xFFB8C6D9, AQUA=0xFF77E2CD, AMBER=0xFFFFD8A1;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private volatile boolean cancelRequested;
    private TextView status, readiness;
    private ImageView preview;
    private Button testButton;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    private GradientDrawable background(int color){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(16));
        return shape;
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void add(LinearLayout layout,View v){layout.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void space(LinearLayout layout,int height){
        layout.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));
    }
    private Button action(LinearLayout layout,String title,int color,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(WHITE);
        b.setTextSize(15);b.setBackground(background(color));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));
        p.topMargin=dp(8);layout.addView(b,p);b.setOnClickListener(v->run.run());return b;
    }

    private boolean internetAvailable(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc=cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Exception ignored){return false;}
    }
    private String modelPath(){
        return getSharedPreferences("realframe_local_sd",MODE_PRIVATE).getString("modelPath","");
    }
    private File model(){return new File(modelPath());}

    private void refresh(){
        if(readiness==null)return;
        File model=model();
        boolean nativeReady=LocalImageEngine.isAvailable();
        boolean modelReady=model.isFile()&&model.length()>8L*1024*1024;
        ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(mi);
        readiness.setText(
            "Native SD engine: "+(nativeReady?"Loaded":"Missing")+
            "\nLocal model: "+(modelReady?"Present ("+(model.length()/(1024*1024))+" MiB)":"Not installed")+
            "\nInternet: "+(internetAvailable()?"Available (local-only test still runs on phone)":"Unavailable (offline test)")+
            "\nFree RAM: "+(mi.availMem/(1024*1024))+" MiB"+
            (nativeReady?"":"\nEngine error: "+LocalImageEngine.unavailableReason()));
        if(testButton!=null)testButton.setEnabled(!running.get()&&nativeReady&&modelReady);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);getWindow().setNavigationBarColor(BACK);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACK);root.setPadding(dp(17),dp(14),dp(17),dp(8));
        setContentView(root);
        TextView header=label("←  ON-DEVICE AI PROOF",23,WHITE,true);
        header.setOnClickListener(v->finish());add(root,header);space(root,7);
        add(root,label("Test real AI inference, not a photo slideshow or cloud demo.",13,AQUA,false));
        space(root,11);
        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(17),dp(16),dp(17));panel.setBackground(background(CARD));
        scroll.addView(panel);
        add(panel,label("LOCAL + Internet ON / OFF",19,WHITE,true));space(panel,9);
        add(panel,label("This test calls the on-phone stable-diffusion.cpp JNI engine directly: 256×256, 4 steps, one image. It does not contact a cloud provider or upload the prompt. Internet availability is checked before and after rendering, not monitored continuously.",13,MUTED,false));
        space(panel,11);
        readiness=label("",14,AQUA,false);add(panel,readiness);
        testButton=action(panel,"▶ Run 256px / 4-step LOCAL image test",0xFF357F74,()->startTest(256,4));
        action(panel,"◉ FAST LOW-RAM TEST (128px / 1 step)",0xFF34597D,()->startTest(128,1));
        action(panel,"■ Cancel local rendering",0xFF624862,()->{
            if(running.get()){
                cancelRequested=true;
                LocalImageEngine.cancel();
                status.setText("Cancel requested. Native processing may take time to stop.");
            }
        });
        action(panel,"⬇ Set up LOCAL image model automatically",0xFF4B4580,
            ()->startActivity(new Intent(this,QuickStartActivity.class)));
        space(panel,10);
        status=label("No physical-device inference has been confirmed in this session.",14,AMBER,false);
        add(panel,status);space(panel,10);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(background(0xFF0F1E31));
        panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(290)));
        space(panel,10);
        add(panel,label("For a second test, disable mobile data and Wi-Fi in Android Settings and run again. Keep RealFrame open; CPU sampling may be very slow or run out of RAM. Do not start another local generation simultaneously.",12,MUTED,false));
        refresh();
    }
    private void startTest(int size,int sampleSteps){
        if(!running.compareAndSet(false,true))return;
        File checkpoint=model();
        if(!LocalImageEngine.isAvailable()||!checkpoint.isFile()){
            running.set(false);refresh();
            status.setText("Missing native engine or local checkpoint. Use one-tap model setup first.");
            return;
        }
        cancelRequested=false;
        boolean onlineAtStart=internetAvailable();
        String checkpointPath=checkpoint.getAbsolutePath();
        status.setText("Running "+size+"px, "+sampleSteps+"-step real on-phone SD diffusion. No cloud fallback...");
        refresh();
        worker.execute(()->{
            final long started=SystemClock.elapsedRealtime();
            try{
                int cpuThreads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()));
                int[] pixels=LocalImageEngine.generate(checkpointPath,
                    "A red ceramic mug on a wooden table, daylight, realistic photograph",
                    "text, watermark, distorted",size,size,sampleSteps,1337L,cpuThreads);
                if(cancelRequested)throw new IllegalStateException("Local test was cancelled");
                if(pixels==null||pixels.length!=size*size)
                    throw new IllegalStateException("Native generator did not return a complete "+size+"x"+size+" frame");
                Bitmap bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
                boolean onlineAtEnd=internetAvailable();
                LocalInferenceProbe.Result verdict=LocalInferenceProbe.classify(true,onlineAtStart,onlineAtEnd);
                long seconds=(SystemClock.elapsedRealtime()-started)/1000;
                String report=LocalInferenceProbe.summary(verdict)+"\n"+
                    "Native "+size+"×"+size+" image: SUCCESS; "+sampleSteps+" steps; "+seconds+" seconds."+
                    "\nThis verifies a local render in this test, not Wan video, NPU acceleration or zero network traffic by other apps.";
                android.content.SharedPreferences.Editor proof=
                    getSharedPreferences("realframe_local_proof",MODE_PRIVATE).edit()
                    .putString("last_result",verdict.name())
                    .putLong("last_duration_sec",seconds)
                    .putLong("last_success_ms",System.currentTimeMillis());
                // Keep separate evidence for on-device render with and without Internet.
                // A network transition never counts as either stable test mode.
                if(verdict==LocalInferenceProbe.Result.LOCAL_WITH_INTERNET)
                    proof.putBoolean("local_while_online_verified",true);
                if(verdict==LocalInferenceProbe.Result.LOCAL_WITHOUT_INTERNET)
                    proof.putBoolean("fully_offline_verified",true);
                proof.apply();
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    preview.setImageBitmap(bitmap);
                    status.setText(report);
                });
            }catch(Throwable failure){
                String detail=failure.getMessage()==null?failure.toString():failure.getMessage();
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    getSharedPreferences("realframe_local_proof",MODE_PRIVATE).edit()
                        .putString("last_result","FAILED")
                        .putString("last_error",detail.length()>360?detail.substring(0,360):detail)
                        .putLong("last_failure_ms",System.currentTimeMillis()).apply();
                    status.setText("LOCAL TEST NOT VERIFIED: "+detail+
                        "\nNative loader: "+LocalImageEngine.lastNativeIssue()+
                        "\nOpen Device Health and copy diagnostics for a reproducible error.");
                });
            }finally{
                running.set(false);
                runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())refresh();});
            }
        });
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){
        if(running.get()){cancelRequested=true;LocalImageEngine.cancel();}
        worker.shutdownNow();
        super.onDestroy();
    }
}
