package studio.realframe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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
 * Optional local web server controls: LAN web UI from a laptop/tablet while
 * all diffusion inference remains on the Android PHONE. Cloud AI elsewhere
 * in RealFrame continues to use external hosted providers.
 */
public final class LocalWebSettingsActivity extends Activity {
    private static final int BG=0xFF0A1020,PANEL=0xFF192840,WHITE=0xFFF2F6FF,
        MUTED=0xFFACBDD2,TEAL=0xFF65E3D1,PURPLE=0xFFB5A2FC,WARN=0xFFFFCF92;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status,urls,modelStatus;
    private boolean shown;
    private final Runnable update=new Runnable(){
        @Override public void run(){
            if(!shown)return;
            refresh();
            main.postDelayed(this,1700);
        }
    };
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(16));return shape;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void put(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout parent,int space){
        parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(space)));
    }
    private Button button(LinearLayout p,String title,int color,Runnable task){
        Button b=new Button(this);b.setText(title);b.setTextSize(15);b.setAllCaps(false);
        b.setBackground(rounded(color));b.setTextColor(WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.topMargin=dp(9);p.addView(b,lp);
        b.setOnClickListener(v->task.run());return b;
    }
    private LinearLayout section(LinearLayout parent,String title){
        LinearLayout card=column();card.setPadding(dp(17),dp(17),dp(17),dp(16));
        card.setBackground(rounded(PANEL));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(13);parent.addView(card,lp);
        put(card,label(title,18,WHITE,true));gap(card,10);return card;
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void copy(String label,String value){
        if(value==null||value.isEmpty()){toast("Server is not running");return;}
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label,value));
        toast(label+" copied to clipboard.");
    }

    @Override public void onCreate(Bundle savedState){
        super.onCreate(savedState);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setPadding(dp(17),dp(15),dp(17),dp(8));setContentView(root);
        TextView title=label("←   PHONE AI WEB SERVER",22,WHITE,true);
        title.setOnClickListener(v->finish());put(root,title);gap(root,7);
        put(root,label("Host locally. Access online from another device over private Wi-Fi.",13,TEAL,false));
        gap(root,15);
        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout mainContent=column();scroll.addView(mainContent);

        LinearLayout server=section(mainContent,"Phone-hosted AI portal");
        put(server,label("Run your own AI image and experimental video models on this phone, then use a browser on the phone or a trusted device. Local jobs never use cloud GPUs.",13,MUTED,false));
        gap(server,10);
        status=label("Server stopped",16,TEAL,true);put(server,status);
        urls=label("No server address yet",13,WHITE,false);gap(server,8);put(server,urls);
        button(server,"▶ Start phone-only web UI",0xFF4B4385,()->start(false));
        button(server,"◉ Start on trusted Wi-Fi (LAN)",0xFF286E6A,()->start(true));
        button(server,"⏹ Stop web server",0xFF673B52,()->{
            stopService(new Intent(this,LocalAiHostService.class));main.postDelayed(this::refresh,400);
        });
        button(server,"🌐 Open phone web UI",0xFF34518A,()->{
            if(!LocalAiHostService.isRunning()){toast("Start the server first");return;}
            String address=LocalAiHostService.address();
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}
            catch(Exception e){toast("Browser not available: "+e.getMessage());}
        });

        LinearLayout access=section(mainContent,"Authenticated access");
        put(access,label("A private 256-bit token protects image and video rendering. The token is stored only in the app and is not embedded in web pages.",13,MUTED,false));
        button(access,"📋 Copy LAN/phone address",0xFF3B4F70,()->
            copy("RealFrame phone server address",LocalAiHostService.address()));
        button(access,"🔐 Copy secret token",0xFF4B4385,()->{
            new AlertDialog.Builder(this)
                .setTitle("Private server access token")
                .setMessage("This secret grants anyone who has it permission to run AI jobs on your phone. Share it only with your own trusted devices. Local Wi-Fi HTTP traffic is NOT encrypted.")
                .setPositiveButton("Copy token",(dialog,which)->
                    copy("RealFrame secret token",LocalAiWebServer.token(this)))
                .setNegativeButton("Cancel",null).show();
        });
        button(access,"↻ Rotate token / revoke old access",0xFF673B52,()->{
            new AlertDialog.Builder(this)
                .setTitle("Revoke previous web access?")
                .setMessage("Generate a new token and invalidate the previous token immediately. Existing browser jobs may continue until completed.")
                .setPositiveButton("Rotate now",(d,w)->{
                    LocalAiWebServer.rotateToken(this);toast("Token changed; reconnect in browser.");
                })
                .setNegativeButton("Cancel",null).show();
        });

        LinearLayout model=section(mainContent,"Local model readiness");
        modelStatus=label("",13,MUTED,false);put(model,modelStatus);
        button(model,"✦ Import SD local image model",0xFF51438A,()->
            startActivity(new Intent(this,LocalImageActivity.class)));
        button(model,"▶ Import Wan local video components",0xFF51438A,()->
            startActivity(new Intent(this,LocalVideoActivity.class)));
        button(model,"☁ Online cloud AI (separate option)",0xFF246A64,()->{
            new AlertDialog.Builder(this)
                .setTitle("Online mode is separate")
                .setMessage("Online mode uses third-party hosted models and their GPU quotas. Phone-hosted web mode uses your own Android hardware and never uploads inference prompts to those providers.")
                .setPositiveButton("Open online AI",(d,w)->{
                    Intent open=new Intent(this,WebStudioActivity.class);
                    open.putExtra("kind","image");startActivity(open);
                })
                .setNegativeButton("Cancel",null).show();
        });
        LinearLayout privacy=section(mainContent,"Security & network limits");
        put(privacy,label("PHONE ONLY: http://127.0.0.1:8765 works on the phone itself.\n\nWI-FI LAN: server binds only to a private Wi-Fi IP. Other devices on the same trusted network can use its address and a token. Never enable on untrusted/public Wi-Fi.\n\nREMOTE INTERNET: not automatically exposed. Safe access from outside your Wi-Fi needs your own encrypted VPN or authenticated HTTPS reverse proxy. Do not forward TCP 8765 to the public internet.\n\nAndroid may stop foreground servers because of battery or a time limit; reopening is sometimes necessary. No offline model checkpoint weights are bundled. Wan CPU T2V may not fit 12GB RAM.",13,WARN,false));
        refresh();
    }

    private void start(boolean lan){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},2709);
        }
        if(lan && LocalAiWebServer.trustedWifiIPv4()==null){
            toast("Connect this phone to trusted private Wi-Fi first.");return;
        }
        try{
            Intent start=new Intent(this,LocalAiHostService.class);
            start.setAction(LocalAiHostService.ACTION_START);
            start.putExtra(LocalAiHostService.EXTRA_LAN,lan);
            startForegroundService(start);
            main.postDelayed(this::refresh,600);
        }catch(Exception e){toast("Could not start phone AI server: "+e.getMessage());}
    }
    private void refresh(){
        if(status==null)return;
        boolean running=LocalAiHostService.isRunning();
        status.setText(running?(LocalAiHostService.acceptsLan()?
            "● ACTIVE • Trusted Wi-Fi" : "● ACTIVE • Phone only")
            :"● OFF"+(LocalAiHostService.failure().isEmpty()?"":" • "+LocalAiHostService.failure()));
        urls.setText(running?"Web address: "+LocalAiHostService.address()+
            "\nAuth: personal bearer token required for every API request.":
            "Start the phone server to receive a local browser address.");
        boolean image=LocalImageEngine.isAvailable();
        boolean video=LocalVideoEngine.isAvailable();
        String path=getSharedPreferences("realframe_local_sd",MODE_PRIVATE)
                     .getString("modelPath","");
        modelStatus.setText("Image CPU engine: "+(image?"compiled":"unavailable")+
            "\nImage model: "+(new java.io.File(path).isFile()?"imported":"missing")+
            "\nWan video engine: "+(video?"compiled (experimental)":"unavailable")+
            "\nWan model pack: "+(LocalVideoActivity.isConfigured(this)?"imported":"missing")+
            "\nNo device inference speed/quality guarantee.");
    }
    @Override protected void onResume(){
        super.onResume();shown=true;main.removeCallbacks(update);main.post(update);
    }
    @Override protected void onPause(){
        shown=false;main.removeCallbacks(update);super.onPause();
    }
}