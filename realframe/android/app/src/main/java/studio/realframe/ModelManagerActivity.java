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
 * Model/reference manager for the forthcoming local renderer.
 *
 * Importing models only persists their SAF URIs. It does NOT enable inference:
 * the APK must additionally contain a compatible native diffusion runtime and
 * a model loader able to consume the selected model's format/architecture.
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
        b.setOnClickListener(v->action.run());
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
        // IMPORTANT: The current APK deliberately does not ship or expose a native AI renderer.
        // Loading a model URI is not equivalent to having an inference pipeline.
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
        TextView title=label("←   Model & Mode Manager",23,TEXT,true);title.setOnClickListener(v->finish());
        root.addView(title);
        TextView subtitle=label("Choose online, local or hybrid workflows · model file imports",13,MUTED,false);
        subtitle.setPadding(0,dp(8),0,dp(14));root.addView(subtitle);
        ScrollView sc=new ScrollView(this);root.addView(sc);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);

        LinearLayout modes=card("Generation preference");
        gap(modes,8);
        message(modes,"Auto/Hybrid routes generation online until a native offline AI engine is genuinely installed. Offline editing always stays on this device.");
        button(modes,"☁  Online only",()->setMode("online"));
        button(modes,"📱  Local only (editing works; AI not installed)",()->setMode("local"));
        button(modes,"⚡  Hybrid / automatic",()->setMode("hybrid"));
        modeStatus=label("",13,ACCENT,true);gap(modes,7);modes.addView(modeStatus);

        LinearLayout files=card("Local model files");
        gap(files,9);
        message(files,"You may select a downloaded model file or folder without duplicating gigabytes of data. This registers the reference, not a functional neural renderer.");
        button(files,"Select model FILE (MNN / SD / GGUF etc.)",()->pickModel(false));
        button(files,"Select model FOLDER / checkpoint pack",()->pickModel(true));
        button(files,"Clear selected model reference",()->{
            prefs.edit().remove("uri").remove("kind").remove("display").remove("size").apply();refresh();
        });
        sourceStatus=label("",13,TEXT,false);gap(files,9);files.addView(sourceStatus);

        LinearLayout nativeCard=card("Native AI engine");
        gap(nativeCard,8);
        message(nativeCard,"A Snapdragon processor and model file alone do not generate images. A matching SDXL/SD1.5 native runtime with Qualcomm QNN or MNN integration must be compiled into this APK.");
        engineStatus=label("",14,ACCENT,true);gap(nativeCard,8);nativeCard.addView(engineStatus);
        button(nativeCard,"One-tap install AI model (no manual file)",()->
            startActivity(new Intent(this,QuickStartActivity.class)));
        button(nativeCard,"Open local AI image renderer",()->startActivity(new Intent(this,LocalImageActivity.class)));
        button(nativeCard,"Read compatibility details",()->new AlertDialog.Builder(this)
            .setTitle("What does fully local require?")
            .setMessage("RealFrame now builds stable-diffusion.cpp as an arm64 CPU-native engine and includes a local single-file image checkpoint importer. It does not bundle multi-GB model weights. Qualcomm NPU acceleration is NOT configured and offline video diffusion is NOT available. Use Local AI Image for a compatible checkpoint; the file reference in this manager alone does not activate a model. Cloud demos remain quota-limited.")
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
        if(engineStatus!=null)engineStatus.setText("Status: CPU text-to-image runtime installed if APK native library loaded; image model still required");
        if(stats!=null)stats.setText(readableMemory());
        if(sourceStatus!=null){
            String uri=prefs.getString("uri","");
            if(uri.isEmpty()){sourceStatus.setText("No local model source selected.");return;}
            sourceStatus.setText("Selected "+prefs.getString("kind","file")+": "+
                prefs.getString("display",Uri.parse(uri).getLastPathSegment())+
                "\nReference saved for future compatible model loader.");
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
            Toast.makeText(this,"Model reference saved. Runtime integration is still required.",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"Could not keep file permission: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
