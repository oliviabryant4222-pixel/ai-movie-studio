package studio.realframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Native control center for the device's local inference API.
 * No address/token is needed to run the main app's local Creator.
 * Optional browser API token is generated automatically and never uploaded.
 */
public final class LocalApiActivity extends Activity {
    private static final int BG=0xFF0C1425,CARD=0xFF1D2B42,WHITE=0xFFF3F6FF,
        MUTED=0xFFB2C2D5,TEAL=0xFF65E0D0,VIOLET=0xFFAB9AF2;
    private LinearLayout page;
    private TextView state,connection,engine;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(15));return g;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void put(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout parent,int n){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout card(String title){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(17),dp(17),dp(17),dp(17));c.setBackground(rounded(CARD));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(13);page.addView(c,lp);
        put(c,text(title,18,WHITE,true));gap(c,9);
        return c;
    }
    private Button action(LinearLayout area,String name,int bg,Runnable work){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(name);b.setTextColor(WHITE);
        b.setBackground(rounded(bg));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.topMargin=dp(9);area.addView(b,lp);
        b.setOnClickListener(v->{
            try{work.run();}catch(Exception e){toast(e.getMessage());}
        });
        return b;
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void copy(String title,String value){
        ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clip.setPrimaryClip(ClipData.newPlainText(title,value));
        toast("Copied "+title+". Don't share your private API token.");
    }
    private String wifiIp(){
        try{
            for(NetworkInterface ni:Collections.list(NetworkInterface.getNetworkInterfaces())){
                if(!ni.isUp()||ni.isLoopback()||ni.isVirtual())continue;
                for(java.net.InetAddress addr:Collections.list(ni.getInetAddresses())){
                    if(addr instanceof Inet4Address && addr.isSiteLocalAddress())
                        return addr.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return "<phone-local-network-IP>";
    }
    private boolean activeWifi(){
        ConnectivityManager c=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(c==null)return false;
        NetworkCapabilities caps=c.getNetworkCapabilities(c.getActiveNetwork());
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
    private void start(boolean shareLan){
        Intent intent=new Intent(this,LocalApiService.class);
        intent.setAction(shareLan?LocalApiService.ACTION_START_LAN:LocalApiService.ACTION_START_LOOPBACK);
        startForegroundService(intent);
        toast(shareLan?"Requested local Wi-Fi sharing":"Requested on-phone local server");
        refresh();
    }
    private void shareWifi(){
        if(!activeWifi()){
            new AlertDialog.Builder(this)
                .setTitle("Wi-Fi network needed")
                .setMessage("Connect to your own trusted private Wi-Fi before sharing the local CPU inference API. You can always run it on this phone without Wi-Fi.")
                .setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Share local AI on your Wi-Fi?")
            .setMessage("The local AI model stays on your phone. Other devices on this private Wi-Fi can send prompts and receive outputs if they know your secret token. The connection uses HTTP and is NOT encrypted. Never use untrusted/public Wi-Fi. This does NOT make your phone a public Internet cloud service.")
            .setPositiveButton("I trust this Wi-Fi",(d,w)->start(true))
            .setNegativeButton("Cancel",null).show();
    }
    private void openLocal(){
        if(!LocalApiService.isRunning()){
            toast("Start on-phone local server first");
            return;
        }
        startActivity(new Intent(this,LocalWebClientActivity.class));
    }
    private void copyAddress(){
        if(!LocalApiService.isRunning()){toast("Start the server first");return;}
        copy("local API address",LocalApiService.isLan()?
            "http://"+wifiIp()+":"+LocalApiService.PORT+"/":
            "http://127.0.0.1:"+LocalApiService.PORT+"/");
    }
    private void rotate(){
        new AlertDialog.Builder(this)
            .setTitle("Change private API token?")
            .setMessage("All other browser sessions and clients using the previous token will lose access. This does not affect native generation in the app.")
            .setPositiveButton("Regenerate",(d,w)->{
                LocalApiService.rotateToken(this);
                toast("Access token regenerated; old clients are disconnected.");
            })
            .setNegativeButton("Cancel",null).show();
    }
    private void refresh(){
        if(state==null)return;
        boolean live=LocalApiService.isRunning();
        state.setText(live?
            (LocalApiService.isLan()?"● LOCAL Wi-Fi API ACTIVE":"● ON-PHONE LOCAL API ACTIVE"):
            "○ Device API stopped");
        connection.setText(live?
            (LocalApiService.isLan()?
                "Private Wi-Fi URL: http://"+wifiIp()+":"+LocalApiService.PORT+
                "/\nAlso on-phone: http://127.0.0.1:"+LocalApiService.PORT+"/":
                "Local URL: http://127.0.0.1:"+LocalApiService.PORT+"/"):
            "Server OFF. Offline image/video features are still available directly in RealFrame without the API server.");
        engine.setText("On-device image engine: "+(LocalImageEngine.isAvailable()?"compiled":"unavailable")+
            "\nExperimental native Wan T2V: "+(LocalVideoEngine.isAvailable()?"compiled":"unavailable")+
            "\nInternet access does NOT change where local inference runs. A compatible licensed model must be imported in Local Image/Video Lab first.");
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(13),dp(16),dp(7));
        root.setBackgroundColor(BG);setContentView(root);
        TextView header=text("←  LOCAL + ONLINE SERVER",22,WHITE,true);
        header.setOnClickListener(v->finish());put(root,header);
        gap(root,7);
        put(root,text("Run native RealFrame AI on this phone. Optionally control it from a browser on trusted Wi-Fi.",12,TEAL,false));
        gap(root,14);
        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);
        LinearLayout main=card("Device-native AI service");
        state=text("",16,TEAL,true);put(main,state);
        gap(main,9);
        connection=text("",13,WHITE,false);put(main,connection);
        gap(main,12);
        action(main,"📱 Start LOCAL on this phone (internet on/off)",0xFF315F63,()->start(false));
        action(main,"📶 Share LOCAL AI on trusted Wi-Fi",0xFF514683,this::shareWifi);
        action(main,"⏹ Stop local API server",0xFF6C3D55,()->{
            Intent stop=new Intent(this,LocalApiService.class);
            stop.setAction(LocalApiService.ACTION_STOP);
            startService(stop);toast("Local API stop requested");refresh();
        });
        action(main,"✦ Open local browser studio IN THIS APP",0xFF5768A7,this::openLocal);
        LinearLayout identity=card("Local address and access security");
        put(identity,text("The app automatically manages a random private token. You never need to enter it for native use of RealFrame. Only browser/API access requires it.",13,MUTED,false));
        action(identity,"Copy local API URL",0xFF286F72,this::copyAddress);
        action(identity,"Copy access token for browser/API",0xFF4C4585,()->copy("local API token",LocalApiService.getOrCreateToken(this)));
        action(identity,"Regenerate access token",0xFF583D53,this::rotate);
        LinearLayout statusCard=card("Mode separation");
        engine=text("",13,WHITE,false);put(statusCard,engine);gap(statusCard,11);
        put(statusCard,text("LOCAL WHILE ONLINE: Your phone remains connected to the Internet, but AI computation and model weights stay local. The browser API runs at 127.0.0.1 (or explicitly shared Wi-Fi).",13,MUTED,false));
        gap(statusCard,11);
        put(statusCard,text("CLOUD ONLINE: Separately open hosted AI models. Cloud usage requires consent and uses external GPU quota. HYBRID: choose local automatically where ready, otherwise ask before sending to cloud.",13,MUTED,false));
        action(statusCard,"☁ Open online AI in RealFrame",0xFF6454A2,()->{
            Intent open=new Intent(this,WebStudioActivity.class);
            open.putExtra("kind","image");startActivity(open);
        });
        action(statusCard,"⇄ Open Hybrid Creator",0xFF376F64,()->startActivity(new Intent(this,UnifiedCreateActivity.class)));
        refresh();
    }
    @Override protected void onResume(){super.onResume();refresh();}
}
