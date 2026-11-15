package studio.realframe;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/**
 * Release maintenance + Android device diagnostics. No network calls.
 * Safely cleans only our own automatic installer files after explicit consent.
 * Preserves character presets, Gallery media and manually imported models.
 */
public final class StudioHealthActivity extends Activity {
    private static final int BG=0xFF0B1324, CARD=0xFF1A2940, WHITE=0xFFF2F5FF;
    private static final int MUTED=0xFFB2C2D4, AQUA=0xFF72E1CD, WARN=0xFFFFD3A3;
    private TextView report,storage;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private TextView label(String text,int font,int color,boolean bold){
        TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(font);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private void put(LinearLayout parent,View child){
        parent.addView(child,new LinearLayout.LayoutParams(-1,-2));
    }
    private void gap(LinearLayout parent,int height){
        parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout panel=vertical();
        panel.setPadding(dp(16),dp(17),dp(16),dp(17));panel.setBackground(shape(CARD));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(15);parent.addView(panel,p);return panel;
    }
    private void button(LinearLayout parent,String title,int color,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(WHITE);
        b.setTextSize(14);b.setBackground(shape(color));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(49));
        p.topMargin=dp(9);parent.addView(b,p);
        StudioUi.styleButton(this,b,color,title,action);
    }
    private String mb(long bytes){
        return String.format(Locale.US,"%.1f MB",bytes/1_000_000.0);
    }
    private boolean online(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities c=cm.getNetworkCapabilities(cm.getActiveNetwork());
            return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Exception ignored){return false;}
    }
    private String generateReport(){
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memory);
        File base=getFilesDir();
        StatFs disk=new StatFs(base.getAbsolutePath());
        SharedPreferences imageTests=getSharedPreferences("realframe_local_proof",MODE_PRIVATE);
        SharedPreferences videoTests=getSharedPreferences("realframe_video_proof",MODE_PRIVATE);
        String proof=imageTests.getString("last_result","not yet tested");
        String wanState=videoTests.getString("last_status","not yet tested");
        String wanDetails=videoTests.getString("last_detail","");
        return "REALFRAME v2.4 Android diagnostics (no prompts/secrets)\n"+
            "Device: "+Build.MANUFACTURER+" "+Build.MODEL+"\n"+
            "Android SDK: "+Build.VERSION.SDK_INT+"\n"+
            "CPU ABIs: "+String.join(", ",Build.SUPPORTED_ABIS)+"\n"+
            "Free RAM: "+mb(memory.availMem)+"/"+mb(memory.totalMem)+" total\n"+
            "Free storage: "+mb(disk.getAvailableBytes())+"\n"+
            "Network: "+(online()?"connected":"offline")+"\n"+
            "Native SD engine: "+(LocalImageEngine.isAvailable()?"loaded":"missing")+"\n"+
            "Image auto model: "+(AutoModelDownloadService.installed(this)?"installed":"not installed")+"\n"+
            "Wan components: "+(AutoVideoModelDownloadService.installed(this)?"selected":"not all installed")+"\n"+
            "WAN bridge: "+(LocalVideoEngine.isAvailable()?"loaded (not proof of rendering)":"missing")+"\n"+
            "Native model job active: "+LocalImageEngine.isBusy()+"\n"+
            "Phone local API: "+(LocalApiService.isRunning()?"active":"stopped")+"\n"+
            "Last local image proof: "+proof+"\n"+
            "LOCAL image with Internet ON: "+(imageTests.getBoolean("local_while_online_verified",false)?"PHYSICAL RENDER VERIFIED":"not verified")+"\n"+
            "LOCAL image Internet OFF: "+(imageTests.getBoolean("fully_offline_verified",false)?"PHYSICAL RENDER VERIFIED":"not verified")+"\n"+
            "Native WAN video proof: "+wanState+"\n"+
            "WAN video details: "+wanDetails+"\n"+
            "Auto image model storage: "+mb(ModelStoragePolicy.imageBytes(base))+"\n"+
            "Auto video model storage: "+mb(ModelStoragePolicy.videoBytes(base))+"\n"+
            "Note: app installation/CI alone does not prove successful Wan AI video rendering.";
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=vertical();root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(13),dp(16),dp(8));setContentView(root);
        TextView header=label("←   REALFRAME DEVICE HEALTH",23,WHITE,true);
        StudioUi.bind(this,header,"Back to studio",this::finish);put(root,header);gap(root,8);
        put(root,label("Model storage • safe cleanup • local diagnostics",13,AQUA,false));
        gap(root,14);
        ScrollView sc=new ScrollView(this);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=vertical();sc.addView(content);

        LinearLayout statusPanel=card(content);
        put(statusPanel,label("On-device status",19,WHITE,true));gap(statusPanel,9);
        report=label("",13,MUTED,false);put(statusPanel,report);
        button(statusPanel,"↻ Refresh device status",0xFF356D72,this::refresh);
        button(statusPanel,"▣ Copy diagnostic report (no prompts/tokens)",0xFF49417B,()->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("RealFrame diagnostics",generateReport()));
            Toast.makeText(this,"Diagnostics copied — no private prompts or API tokens",Toast.LENGTH_LONG).show();
        });
        button(statusPanel,"✓ Test actual LOCAL inference",0xFF327F72,
            ()->startActivity(new Intent(this,LocalProofActivity.class)));
        button(statusPanel,"▶ Test genuine WAN video + verify saved MP4",0xFF335B83,
            ()->startActivity(new Intent(this,LocalVideoActivity.class)));

        LinearLayout modelPanel=card(content);
        put(modelPanel,label("Automatic model storage",19,WHITE,true));gap(modelPanel,7);
        storage=label("",14,AQUA,false);put(modelPanel,storage);gap(modelPanel,7);
        put(modelPanel,label("Remove only files installed by RealFrame automatically. Your own imported checkpoints, Gallery photos, MP4s, characters and projects will remain untouched. Stop active rendering before deleting model weights.",13,MUTED,false));
        button(modelPanel,"⬇ Manage image model setup",0xFF356D72,
            ()->startActivity(new Intent(this,QuickStartActivity.class)));
        button(modelPanel,"⬇ Manage WAN video model setup",0xFF356D72,
            ()->startActivity(new Intent(this,LocalVideoActivity.class)));
        button(modelPanel,"♻ Clear unfinished installer downloads",0xFF7D6347,
            ()->confirm("Clear unfinished model downloads?",
                "This deletes only interrupted .part transfers. Installed models and saved projects are preserved. Unfinished downloads would restart.",
                0));
        button(modelPanel,"✕ Uninstall automatic IMAGE weights",0xFF724855,
            ()->confirm("Remove downloaded SD1.5 image model?",
                "RealFrame will delete only the automatically installed SD1.5 Q4 checkpoint. Future local image generation needs the model reinstalled. Imported models and Gallery outputs are preserved.",
                1));
        button(modelPanel,"✕ Uninstall automatic WAN video weights",0xFF724855,
            ()->confirm("Remove downloaded Wan video pack?",
                "RealFrame will delete only the auto-downloaded WAN diffusion, T5 and VAE. Video inference will require reinstallation. Imported models, characters and existing MP4s are preserved.",
                2));
        LinearLayout note=card(content);
        put(note,label("Release boundaries",17,WARN,true));gap(note,8);
        put(note,label("This screen checks model presence and a recorded real image test. It cannot certify unlimited cloud compute, exact identity lock, Qualcomm NPU acceleration, or a real long-form on-device motion-video model. 12 GB shared phone RAM can limit Wan inference.",13,MUTED,false));
        refresh();
    }
    private boolean cleanupAllowed(){
        if(AutoModelDownloadService.isDownloading()||AutoVideoModelDownloadService.isDownloading()){
            Toast.makeText(this,"Pause both model installers before cleanup",Toast.LENGTH_LONG).show();
            return false;
        }
        if(LocalImageEngine.isBusy()||LocalApiService.isRunning()){
            Toast.makeText(this,"Stop local AI renders and Local API server before cleanup",Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
    private void confirm(String title,String message,int category){
        if(!cleanupAllowed())return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Keep models",null)
            .setPositiveButton("Delete selected files",(d,w)->remove(category))
            .show();
    }
    private void remove(int category){
        if(!cleanupAllowed())return;
        File base=getFilesDir();
        try{
            long freed=0;
            if(category==0||category==1){
                if(category==0){
                    File part=ModelStoragePolicy.imagePartial(base);
                    freed+=removeOne(base,part);
                }else{
                    File model=ModelStoragePolicy.image(base);
                    freed+=removeOne(base,model);
                    SharedPreferences p=getSharedPreferences("realframe_local_sd",MODE_PRIVATE);
                    if(model.getAbsolutePath().equals(p.getString("modelPath","")))
                        p.edit().remove("modelPath").apply();
                }
            }
            if(category==0||category==2){
                SharedPreferences p=getSharedPreferences("realframe_wan_local_v18",MODE_PRIVATE);
                SharedPreferences.Editor editor=p.edit();
                for(int i=0;i<ModelStoragePolicy.WAN.size();i++){
                    File part=ModelStoragePolicy.videoPartial(base,i);
                    File installed=ModelStoragePolicy.video(base,i);
                    if(category==0)freed+=removeOne(base,part);
                    else {
                        freed+=removeOne(base,installed);
                        String role=i==0?"diffusion":i==1?"t5":"vae";
                        if(installed.getAbsolutePath().equals(p.getString(role+"Path","")))
                            editor.remove(role+"Path");
                    }
                }
                if(category==2)editor.apply();
            }
            refresh();
            Toast.makeText(this,"Safely reclaimed "+mb(freed)+". Your Gallery and projects are untouched.",Toast.LENGTH_LONG).show();
        }catch(Exception error){
            refresh();
            new AlertDialog.Builder(this).setTitle("Storage cleanup incomplete")
                .setMessage(error.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private long removeOne(File base,File file){
        long size=ModelStoragePolicy.bytes(file);
        if(!ModelStoragePolicy.deleteManaged(base,file))
            throw new IllegalStateException("Could not remove "+file.getName());
        return size;
    }
    private void refresh(){
        if(report!=null)report.setText(generateReport());
        if(storage!=null){
            File files=getFilesDir();
            storage.setText("Auto image model and partials: "+mb(ModelStoragePolicy.imageBytes(files))+
                "\nAuto WAN weights and partials: "+mb(ModelStoragePolicy.videoBytes(files)));
        }
    }
    @Override protected void onResume(){super.onResume();refresh();}
}
