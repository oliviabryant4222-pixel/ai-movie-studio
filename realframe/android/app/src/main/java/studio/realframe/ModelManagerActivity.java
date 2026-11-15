package studio.realframe;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Model manager for the included native CPU image renderer.
 *
 * Model-manager picker saves a SAF reference only; the working local
 * renderer/one-tap installer are accessible through LocalImageActivity/QuickStartActivity.
 * Avoids costly multi-GB copies and respects Android scoped storage.
 */
public final class ModelManagerActivity extends Activity {
    private static final int PICK_FILE=6101, PICK_FOLDER=6102;
    private static final int BACK=0xFF0B1120, CARD=0xFF1A273D, TEXT=0xFFEFF4FE,
            MUTED=0xFFAAB9CD, ACCENT=0xFF76DED3, PURPLE=0xFFB5A3FF;
    private final String prefsName="realframe_model_manager";
    private SharedPreferences prefs;
    private LinearLayout body;
    private TextView sourceStatus, engineStatus, stats, modeStatus;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private GradientDrawable box(int fill){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(fill);shape.setCornerRadius(dp(17));return shape;
    }
    private LinearLayout card(String heading){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16),dp(17),dp(16),dp(15));c.setBackground(box(CARD));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(14);body.addView(c,lp);
        TextView h=label(heading,18,TEXT,true);c.addView(h);
        return c;
    }
    private void gap(LinearLayout parent,int dp){
        View g=new View(this);parent.addView(g,new LinearLayout.LayoutParams(1,dp(dp)));
    }
    private void message(LinearLayout c,String text){
        TextView t=label(text,13,MUTED,false);t.setLineSpacing(0,1.1f);c.addView(t);
    }
    private void button(LinearLayout c,String text,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(14);
        b.setTextColor(TEXT);b.setBackground(box(0xFF34345C));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(47));
        lp.topMargin=dp(8);c.addView(b,lp);
        StudioUi.styleButton(this,b,0xFF484181,text,action);
    }
    private void pickModel(boolean directory){
        Intent intent=new Intent(directory?Intent.ACTION_OPEN_DOCUMENT_TREE:Intent.ACTION_OPEN_DOCUMENT);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if(!directory){intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");}
        try{startActivityForResult(intent,directory?PICK_FOLDER:PICK_FILE);}
        catch(Exception e){Toast.makeText(this,"File picker unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void setMode(String value){
        prefs.edit().putString("mode",value).apply(); refresh();
    }
    private boolean backendReady(){
        // CPU-native engine ships in the APK. A SAF reference is not sufficient:
        // install one-tap weights or import through LocalImageActivity.
        return LocalImageEngine.isAvailable();
    }
    private String readableMemory(){
        ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        StatFs disk=new StatFs(getFilesDir().getAbsolutePath());
        return "RAM available: "+Formatter.formatFileSize(this,info.availMem)
            +" / "+Formatter.formatFileSize(this,info.totalMem)
            +"\nStorage available: "+Formatter.formatFileSize(this,disk.getAvailableBytes())
            +"\nABIs: "+String.join(", ",Build.SUPPORTED_ABIS);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);getWindow().setNavigationBarColor(BACK);
        prefs=getSharedPreferences(prefsName,MODE_PRIVATE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(17),dp(15),dp(17),dp(9));root.setBackgroundColor(BACK);setContentView(root);
        TextView title=label("←   Model & Mode Manager",23,TEXT,true);StudioUi.bind(this,title,"Back to studio",this::finish);
        root.addView(title);
        TextView subtitle=label("Choose online, local or hybrid workflows · model file imports",13,MUTED,false);
        subtitle.setPadding(0,dp(8),0,dp(14));root.addView(subtitle);
        ScrollView sc=new ScrollView(this);root.addView(sc);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);

        LinearLayout modes=card("Generation preference");
        gap(modes,8);
        message(modes,"Hybrid prefers on-device image generation when the local model and enough RAM are available. Hosted AI asks consent.");
        button(modes,"☁  Online only",()->setMode("online"));
        button(modes,"📱  Local only (native CPU image AI; model needed)",()->setMode("local"));
        button(modes,"⚡  Hybrid / automatic",()->setMode("hybrid"));
        modeStatus=label("",13,ACCENT,true);gap(modes,7);modes.addView(modeStatus);

        LinearLayout files=card("Local model files");
        gap(files,9);
        message(files,"Advanced source catalog: selecting a file or folder here saves its reference. For usable image AI, choose one-tap setup or import into Local AI Image.");
        button(files,"Select model FILE (MNN / SD / GGUF etc.)",()->pickModel(false));
        button(files,"Select model FOLDER / checkpoint pack",()->pickModel(true));
        button(files,"Clear selected model reference",()->{
            prefs.edit().remove("uri").remove("kind").remove("display").remove("size").apply();refresh();
        });
        sourceStatus=label("",13,TEXT,false);gap(files,9);files.addView(sourceStatus);

        LinearLayout nativeCard=card("Native AI engine");
        gap(nativeCard,8);
        message(nativeCard,"RealFrame includes an arm64 CPU-native Stable Diffusion runtime. A compatible local checkpoint is required. Qualcomm QNN/NPU is not yet supported.");
        engineStatus=label("",14,ACCENT,true);gap(nativeCard,8);nativeCard.addView(engineStatus);
        button(nativeCard,"One-tap install AI model (no manual file)",()->
            startActivity(new Intent(this,QuickStartActivity.class)));
        button(nativeCard,"Open local AI image renderer",()->startActivity(new Intent(this,LocalImageActivity.class)));
        button(nativeCard,"Read compatibility details",()->new AlertDialog.Builder(this)
            .setTitle("What does fully local require?")
            .setMessage("RealFrame contains stable-diffusion.cpp for arm64 CPU image inference. One-tap setup obtains a licensed SD1.5 Q4 model in the app after consent; no manual model download. Separate native Wan T2V is experimental and needs large additional weights; it is NOT verified on this device. NPU support is unavailable and hosted generation has provider quotas.")
            .setPositiveButton("OK",null).show());

        LinearLayout device=card("Device capability");
        gap(device,7);stats=label("",13,MUTED,false);device.addView(stats);
        LinearLayout note=card("Privacy and costs");
        gap(note,7);
        message(note,"Imported model references stay on-device. Online generation sends prompts/uploads to the provider. Hosted GPU quotas and provider content policies apply; switching to Hybrid does not create free unlimited compute.");
        refresh();
    }
    private void refresh(){
        if(modeStatus!=null){
            String mode=prefs.getString("mode","hybrid");
            modeStatus.setText("Selected: "+mode.toUpperCase()+"  ·  Native CPU image engine: "+(backendReady()?"Ready":"Not installed"));
        }
        if(engineStatus!=null)engineStatus.setText("CPU image engine: "+(backendReady()?"available":"unavailable")+
            " · One-tap model: "+(AutoModelDownloadService.installed(this)?"installed":"not installed"));
        if(stats!=null)stats.setText(readableMemory());
        if(sourceStatus!=null){
            String uri=prefs.getString("uri","");
            if(uri.isEmpty()){sourceStatus.setText("No local model source selected.");return;}
            sourceStatus.setText("Selected "+prefs.getString("kind","file")+": "+
                prefs.getString("display",Uri.parse(uri).getLastPathSegment())+
                "\nSource reference saved only. Open Local AI Image to import a usable checkpoint.");
        }
    }
    @Override protected void onActivityResult(int request,int resultCode,Intent data){
        super.onActivityResult(request,resultCode,data);
        if((request!=PICK_FILE && request!=PICK_FOLDER)||resultCode!=RESULT_OK
            ||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String display=uri.getLastPathSegment();
            if(request==PICK_FILE) {
                try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
                    if(c!=null&&c.moveToFirst()){
                        int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if(idx>=0)display=c.getString(idx);
                    }
                }
            }
            prefs.edit().putString("uri",uri.toString())
                .putString("kind",request==PICK_FOLDER?"folder":"file")
                .putString("display",display).apply();
            refresh();
            Toast.makeText(this,"Reference saved. For inference, use Local AI Image or one-tap setup.",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"Could not keep file permission: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
