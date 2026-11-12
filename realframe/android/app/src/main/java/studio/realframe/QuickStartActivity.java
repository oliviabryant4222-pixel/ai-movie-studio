package studio.realframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Model setup without manual file downloads, API tokens, or Nightmare Mobile.
 * Model weights have to exist ON DEVICE for 100% local inference; installs the
 * 1.75 GB open-weight model once over HTTPS, then does not use cloud at runtime.
 */
public final class QuickStartActivity extends Activity {
    private static final int BG=0xFF0B1221, CARD=0xFF1A2940,WHITE=0xFFF3F6FF,
        MUTED=0xFFACBED5,CYAN=0xFF66E1D2,VIOLET=0xFFB3A0FC,WARN=0xFFFFD3A3;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView ready,progress;
    private Button install,openLocal;
    private boolean screenActive;
    private final Runnable updater=new Runnable(){
        @Override public void run(){
            if(!screenActive)return;
            refresh();handler.postDelayed(this,1300);
        }
    };
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(17));return d;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String s,int font,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(font);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void put(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout parent,int value){
        parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(value)));
    }
    private LinearLayout panel(LinearLayout parent){
        LinearLayout p=vertical();p.setPadding(dp(17),dp(17),dp(17),dp(17));
        p.setBackground(shape(CARD));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(13);parent.addView(p,lp);return p;
    }
    private Button button(LinearLayout p,String caption,int color,Runnable handler){
        Button button=new Button(this);button.setText(caption);button.setAllCaps(false);
        button.setTextSize(15);button.setTextColor(WHITE);button.setBackground(shape(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));
        lp.topMargin=dp(9);p.addView(button,lp);
        button.setOnClickListener(v->handler.run());
        return button;
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void open(Class<? extends Activity> cls){startActivity(new Intent(this,cls));}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=vertical();root.setBackgroundColor(BG);
        root.setPadding(dp(17),dp(13),dp(17),dp(7));setContentView(root);
        TextView heading=label("←   REALFRAME AI SETUP",23,WHITE,true);
        heading.setOnClickListener(v->finish());put(root,heading);gap(root,6);
        put(root,label("No manual model files • No separate AI app • No token",13,CYAN,false));gap(root,13);
        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=vertical();scroll.addView(body);
        LinearLayout p=panel(body);
        put(p,label("Fully local AI while internet is ON or OFF",20,WHITE,true));
        gap(p,10);
        put(p,label("Once installed, Stable Diffusion uses only your Samsung's CPU and local model. Internet being ON does NOT upload your prompts. Your phone can also host the local AI web portal on trusted Wi-Fi.",14,MUTED,false));
        gap(p,11);
        ready=label("Checking local AI...",15,CYAN,true);put(p,ready);
        gap(p,8);progress=label("",13,WHITE,false);put(p,progress);
        install=button(p,"⬇ Install open-source AI model automatically",0xFF6454B6,this::confirmInstall);
        button(p,"⏸ Pause model setup",0xFF59425F,()->{
            Intent i=new Intent(this,AutoModelDownloadService.class);
            i.setAction(AutoModelDownloadService.CANCEL);
            startService(i);
            toast("Pause requested. Progress stays on phone for resume.");
        });
        openLocal=button(p,"✦ Generate LOCAL AI image",0xFF287B72,()->open(LocalImageActivity.class));
        button(p,"◉ Run LOCAL AI via browser (Internet ON allowed)",0xFF3F4B82,
            ()->open(LocalApiActivity.class));
        LinearLayout note=panel(body);
        put(note,label("What does the first-time setup do?",18,VIOLET,true));
        gap(note,8);
        put(note,label("The existing open-source image engine comes from GitHub: leejet/stable-diffusion.cpp (MIT). This installer automatically obtains an SD1.5 Q4 model from its licensed publisher on Hugging Face. You do NOT have to find, save, move, or import any model file yourself.",13,MUTED,false));
        gap(note,8);
        put(note,label("Model size: about 1.75 GB, once only. Recommended 2.5 GB free storage and private Wi-Fi. The file is installed inside RealFrame; after that generation can work entirely offline. Actual speed/output still needs testing on Samsung.",13,WARN,false));
        gap(note,12);
        button(note,"☁ ONLINE hosted AI image/video (optional)",0xFF4F4A82,()->{
            Intent i=new Intent(this,WebStudioActivity.class);
            i.putExtra("kind","image");startActivity(i);
        });
        button(note,"⇄ HYBRID create",0xFF336C66,()->open(UnifiedCreateActivity.class));
        button(note,"▶ Experimental LOCAL AI video lab",0xFF544171,()->open(LocalVideoActivity.class));
        gap(note,12);
        put(note,label("Video caveat: local Wan video requires additional large model components; the one-tap installer currently sets up LOCAL IMAGE AI, not Wan video. Fully local long videos on a 12GB phone are not validated.",12,WARN,false));
        refresh();
    }
    private boolean wifi(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }catch(Exception ignored){return false;}
    }
    private void confirmInstall(){
        if(AutoModelDownloadService.isDownloading()){
            toast("Model setup already running");return;
        }
        if(AutoModelDownloadService.installed(this)){
            open(LocalImageActivity.class);return;
        }
        String text="RealFrame will automatically download a roughly 1.75 GB licensed AI model INTO THE APP. "+
            "No manual file downloads, no Nightmare and no API token. "+
            "One initial Internet transfer is necessary: local AI cannot operate without model weights. "+
            "After the model is installed, all image generation stays on your phone even with Internet ON.\n\n"+
            "Model: Stable Diffusion 1.5 Q4 GGUF (GPUStack)\n"+
            "License: CreativeML Open RAIL-M (usage restrictions apply).\n"+
            "Engine: MIT-licensed stable-diffusion.cpp from GitHub.\n\n"+
            "Recommended: at least 2.5 GB free internal storage and trusted Wi-Fi.";
        new AlertDialog.Builder(this)
            .setTitle("One-tap LOCAL AI model setup")
            .setMessage(text)
            .setPositiveButton("Accept license & install",(dialog,choice)->startInstall())
            .setNegativeButton("Cancel",null)
            .show();
    }
    private void startInstall(){
        if(!wifi()){
            new AlertDialog.Builder(this)
                .setTitle("Large download without Wi-Fi")
                .setMessage("You are not on Wi-Fi. The one-time AI model uses about 1.75 GB mobile data. Your carrier may charge for this. Proceed?")
                .setPositiveButton("Use mobile data",(d,w)->launchInstall())
                .setNegativeButton("Cancel",null).show();
            return;
        }
        launchInstall();
    }
    private void launchInstall(){
        try{
            Intent i=new Intent(this,AutoModelDownloadService.class);
            i.setAction(AutoModelDownloadService.START);
            startForegroundService(i);
            toast("AI model setup started inside RealFrame.");
            handler.postDelayed(this::refresh,600);
        }catch(Exception e){toast("Model setup could not start: "+e.getMessage());}
    }
    private void refresh(){
        boolean readyModel=AutoModelDownloadService.installed(this);
        boolean running=AutoModelDownloadService.isDownloading();
        ready.setText(readyModel ? "● LOCAL IMAGE AI READY ✓" :
            running ? "● INSTALLING LOCAL IMAGE AI" : "○ LOCAL MODEL NOT INSTALLED");
        if(readyModel){
            progress.setText("Open Local Image and create with Internet ON or OFF.\n"+
                "All model inference stays on the phone CPU.");
        }else{
            long size=AutoModelDownloadService.partialBytes(this);
            progress.setText("Status: "+AutoModelDownloadService.state+"\n"+
                AutoModelDownloadService.message+"\n"+
                (size>0?"Saved setup progress: "+(size/1_000_000L)+" MB":"No model data installed yet."));
        }
        install.setEnabled(!running);
        openLocal.setEnabled(true);
        install.setText(readyModel?"✓ Already installed · Open Local AI":
            running?"Installing model in RealFrame...":
                sizeLabel());
    }
    private String sizeLabel(){return "⬇ Install LOCAL Image AI inside app (~1.75 GB)";}
    @Override protected void onResume(){
        super.onResume();screenActive=true;handler.removeCallbacks(updater);handler.post(updater);
    }
    @Override protected void onPause(){
        screenActive=false;handler.removeCallbacks(updater);super.onPause();
    }
}
