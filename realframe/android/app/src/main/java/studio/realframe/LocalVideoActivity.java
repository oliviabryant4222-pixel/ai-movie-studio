package studio.realframe;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EXPERIMENTAL fully on-device Wan T2V adapter. Imports three files, executes
 * stable-diffusion.cpp generate_video() and locally encodes frames as MP4.
 * A successful APK build is NOT proof that any Wan model fits in phone RAM.
 */
public final class LocalVideoActivity extends Activity {
    private static final int BG=0xFF0C1424,PANEL=0xFF182841,WHITE=0xFFF3F5FF,
        MUTED=0xFFAFC0D5,PURPLE=0xFFAD9AFF,TEAL=0xFF6BE4D3;
    private static final int PICK_DIFFUSION=2301,PICK_T5=2302,PICK_VAE=2303;
    private static final String PREFS="realframe_wan_local_v18";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile boolean cancelImport;
    private boolean lowRamAcknowledged;
    private TextView status,modelInfo,engineInfo;
    private EditText prompt,negative,seed;
    private Spinner resolution,frameCount,steps;
    private Button generate;
    private SharedPreferences prefs;
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private TextView setupStatus;
    private Button autoInstall;
    private boolean screenVisible=false;
    private final Runnable installerUpdate=new Runnable(){
        @Override public void run(){
            if(!screenVisible)return;
            refresh();uiHandler.postDelayed(this,1600);
        }
    };

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int col) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(col);shape.setCornerRadius(dp(15));return shape;
    }
    private TextView label(String text,int sz,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(text);t.setTextSize(sz);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private LinearLayout column(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);return p;}
    private void put(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout p,int height){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private Button button(LinearLayout p,String title,Runnable action,int color){
        Button btn=new Button(this);btn.setAllCaps(false);btn.setTextSize(14);
        btn.setText(title);btn.setTextColor(WHITE);btn.setBackground(rounded(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(49));
        lp.topMargin=dp(9);p.addView(btn,lp);
        StudioUi.styleButton(this,btn,color,title,action);
        return btn;
    }
    private Spinner select(LinearLayout p,String title,String[] values) {
        TextView cap=label(title,13,MUTED,true);
        cap.setPadding(0,dp(12),0,dp(5));put(p,cap);
        Spinner out=new Spinner(this);
        out.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));
        out.setBackground(rounded(0xFFE6ECF6));put(p,out);return out;
    }
    private EditText entry(LinearLayout p,String title,String value){
        TextView cap=label(title,13,MUTED,true);
        cap.setPadding(0,dp(12),0,dp(5));put(p,cap);
        EditText box=new EditText(this);box.setText(value);
        box.setTextColor(WHITE);box.setTextSize(14);box.setBackground(rounded(0xFF283B56));
        box.setPadding(dp(12),dp(10),dp(12),dp(10));put(p,box);return box;
    }
    private void note(String message) {
        runOnUiThread(()->{if(status!=null)status.setText(message);});
    }
    private File dir(){return new File(getFilesDir(),"wan_video_models");}
    private String path(String role){return prefs.getString(role+"Path","");}
    private boolean allImported(){
        return new File(path("diffusion")).isFile()&&
               new File(path("t5")).isFile()&&new File(path("vae")).isFile();
    }
    public static boolean isConfigured(android.content.Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,MODE_PRIVATE);
        return new File(p.getString("diffusionPath","")).isFile() &&
               new File(p.getString("t5Path","")).isFile() &&
               new File(p.getString("vaePath","")).isFile();
    }
    private String readable(String role) {
        File model=new File(path(role));
        return model.isFile()?role.toUpperCase(Locale.ROOT)+": "+model.getName()+
            " • "+(model.length()/1048576L)+" MB":role.toUpperCase(Locale.ROOT)+": not imported";
    }
    private void refresh() {
        if(modelInfo!=null)modelInfo.setText(readable("diffusion")+"\n"+
            readable("t5")+"\n"+readable("vae"));
        if(engineInfo!=null)engineInfo.setText(LocalVideoEngine.isAvailable()
            ? "Native Wan CPU engine compiled ✓ (device inference unverified)"
            : "Native video engine missing: "+LocalVideoEngine.issue());
        // Surface missing-weight / missing-native guidance on tap rather than
        // presenting a disabled button that looks broken.
        if(generate!=null)generate.setEnabled(!busy.get());
        if(setupStatus!=null)
            setupStatus.setText("WAN auto setup: "+AutoVideoModelDownloadService.state+
                "\n"+AutoVideoModelDownloadService.message+
                "\nDownloaded on this phone: "+(AutoVideoModelDownloadService.partialBytes(this)/1_000_000L)+" MB");
        if(autoInstall!=null)autoInstall.setEnabled(!AutoVideoModelDownloadService.isDownloading());
    }

    @Override public void onCreate(Bundle savedState){
        super.onCreate(savedState);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setPadding(dp(15),dp(15),dp(15),dp(12));setContentView(root);
        TextView header=label("←  LOCAL AI VIDEO • LAB",22,WHITE,true);
        StudioUi.bind(this,header,"Back to studio",this::finish);put(root,header);gap(root,8);
        put(root,label("Experimental text → video • all inference and MP4 encoding on this device",12,TEAL,false));
        gap(root,12);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout panel=column();panel.setPadding(dp(16),dp(16),dp(16),dp(16));
        panel.setBackground(rounded(PANEL));scroll.addView(panel);

        put(panel,label("GPU-free, not model-free",18,PURPLE,true));gap(panel,9);
        put(panel,label("Needs separate compatible Wan2.1 T2V files (smallest 1.3B), a T5 encoder and Wan2.1 VAE. They may require MANY GB of storage/RAM; a 12GB phone is not guaranteed to run it. No CPU speed guarantees.",13,MUTED,false));
        gap(panel,10);
        engineInfo=label("",13,TEAL,true);put(panel,engineInfo);gap(panel,9);
        modelInfo=label("",13,WHITE,false);put(panel,modelInfo);
        setupStatus=label("",13,TEAL,false);gap(panel,10);put(panel,setupStatus);
        autoInstall=button(panel,"⬇ ONE-TAP INSTALL Wan Q4 PACK (~4.73 GB)",this::confirmAutoInstall,0xFF277669);
        button(panel,"⏸ Pause automatic WAN download",()->{
            Intent i=new Intent(this,AutoVideoModelDownloadService.class);
            i.setAction(AutoVideoModelDownloadService.CANCEL);
            startService(i);
            toast("Pause requested. Resume later without starting from zero.");
        },0xFF694B55);
        put(panel,label("Automatic setup obtains 3 Apache-2.0 open-weight models in the app. No manual model downloads. Requires Internet once and about 5.5 GB free storage. An installed checkpoint does NOT prove Wan video will fit in RAM.",12,MUTED,false));
        button(panel,"1 • Import Wan T2V diffusion model",()->pick(PICK_DIFFUSION),0xFF4E4286);
        button(panel,"2 • Import UMT5 / T5 text encoder",()->pick(PICK_T5),0xFF4E4286);
        button(panel,"3 • Import Wan VAE",()->pick(PICK_VAE),0xFF4E4286);
        button(panel,"Model requirements & download pages",this::docs,0xFF28685F);
        gap(panel,15);
        put(panel,label("TEXT-TO-VIDEO GENERATION",17,PURPLE,true));
        prompt=entry(panel,"Prompt","Short cinematic clip of a fictional adult walking in a park, realistic movement and natural sunlight");
        negative=entry(panel,"Negative prompt","watermark, text, blurry, glitch, distorted face");
        seed=entry(panel,"Seed (1–2147483647)","13901");
        resolution=select(panel,"Resolution (CPU experiment)",new String[]{"192×192 (low RAM)","256×256","320×320","384×384"});
        frameCount=select(panel,"Video frames (16fps)",new String[]{"5 frames","9 frames","17 frames","33 frames"});
        steps=select(panel,"Sampling steps",new String[]{"1","4","8","12"});
        if(getIntent()!=null) {
            String incoming=getIntent().getStringExtra("prompt");
            if(incoming!=null&&!incoming.isEmpty())prompt.setText(incoming);
            incoming=getIntent().getStringExtra("negative");
            if(incoming!=null)negative.setText(incoming);
            incoming=getIntent().getStringExtra("seed");
            if(incoming!=null&&!incoming.isEmpty())seed.setText(incoming);
        }
        gap(panel,10);
        generate=button(panel,"▶  GENERATE REAL LOCAL AI VIDEO",this::generate,0xFF4E579E);
        button(panel,"✓ TEST NATIVE WAN (256px • 5 frames • 4 steps)",()->{
            if(!allImported()||!LocalVideoEngine.isAvailable()){
                toast("First install WAN Q4 models inside this app.");return;
            }
            resolution.setSelection(1);frameCount.setSelection(0);steps.setSelection(1);
            prompt.setText("A colorful kite gently moving in a sunny park, natural cinematic motion");
            negative.setText("text, watermark, glitch");
            seed.setText("13901");
            generate();
        },0xFF267B72);
        button(panel,"⚡ LOW-RAM WAN DIAGNOSTIC (192px • 5 frames • 1 step)",()->{
            if(!allImported()){confirmAutoInstall();return;}
            resolution.setSelection(0);frameCount.setSelection(0);steps.setSelection(0);
            prompt.setText("An orange kite over a field in daylight, gentle camera movement");
            negative.setText("text, watermark, glitch");
            seed.setText("13901");
            generate();
        },0xFF286B70);
        button(panel,"⏹  Cancel inference",()->{
            cancelImport=true;
            LocalVideoEngine.cancel();
            note("Cancellation requested. Native model loading may not stop immediately.");
        },0xFF633B55);
        gap(panel,12);
        status=label("Import all 3 model files first. Generated video saves in Movies/RealFrameStudio.",13,MUTED,false);
        put(panel,status);gap(panel,10);
        put(panel,label("This is NOT a prebuilt full movie model. Wan CPU T2V may be too slow or fail due to RAM. Cloud AI video remains available from Online. Local photo→MP4 and clip joining work without AI weights.",12,MUTED,false));
        refresh();
    }
    private boolean isWifi(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }catch(Exception ignored){return false;}
    }
    private void confirmAutoInstall(){
        if(AutoVideoModelDownloadService.isDownloading()){
            toast("WAN setup already running");return;
        }
        if(AutoVideoModelDownloadService.installed(this)){
            toast("WAN model components are configured. Try a small render to test hardware.");return;
        }
        String details="RealFrame downloads Wan2.1 T2V 1.3B Q4 (816 MB), UMT5-XXL Q4 (3.66 GB), "+
            "and Wan2.1 VAE (254 MB) from Hugging Face, approximately 4.73 GB in total. "+
            "Models are Apache-2.0 licensed. Downloaded weights are verified by SHA-256. "+
            "No external app, manually managed files or API tokens. "+
            "After setup, local video inference uses your phone CPU with Internet ON or OFF. "+
            "WARNING: this is an experimental engine. 12 GB RAM might be insufficient, "+
            "and generation can be extremely slow. Not a guaranteed full local movie generator. "+
            "Recommended: at least 5.5 GB available internal storage.";
        new AlertDialog.Builder(this)
            .setTitle("One-tap WAN Q4 local video models")
            .setMessage(details)
            .setPositiveButton("Accept licenses & install",(dialog,which)->{
                if(!isWifi()){
                    new AlertDialog.Builder(this).setTitle("Large mobile data usage")
                        .setMessage("About 4.73 GB may download over mobile data. Continue?")
                        .setPositiveButton("Use mobile data",(d,w)->launchAutoInstall())
                        .setNegativeButton("Cancel",null).show();
                }else launchAutoInstall();
            }).setNegativeButton("Cancel",null).show();
    }
    private void launchAutoInstall(){
        try{
            Intent i=new Intent(this,AutoVideoModelDownloadService.class);
            i.setAction(AutoVideoModelDownloadService.START);
            startForegroundService(i);
            toast("Automatic Wan Q4 install started inside RealFrame");
            uiHandler.postDelayed(this::refresh,700);
        }catch(Exception ex){toast("Video setup could not start: "+ex.getMessage());}
    }
    private void docs(){
        new AlertDialog.Builder(this).setTitle("Wan2.1 T2V 1.3B local model")
            .setMessage("To use this experimental mode, obtain these compatible components with their original licenses:\n\n"+
                "• Wan2.1 T2V 1.3B diffusion checkpoint\n"+
                "• UMT5 XXL text encoder (quantized GGUF may help memory)\n"+
                "• wan_2.1_vae.safetensors\n\n"+
                "Source guidance: stable-diffusion.cpp docs/wan.md. Even the smaller model may exceed your phone's free RAM when combined with its T5 encoder. The app imports all 3 files into private storage. Models are NOT included.")
            .setPositiveButton("Model guide",(d,w)->{
                Intent link=new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/wan.md"));
                startActivity(link);
            }).setNegativeButton("Close",null).show();
    }
    private void pick(int role){
        if(busy.get())return;
        Intent select=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        select.addCategory(Intent.CATEGORY_OPENABLE);select.setType("*/*");
        select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(select,role);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(req!=PICK_DIFFUSION&&req!=PICK_T5&&req!=PICK_VAE)return;
        if(!busy.compareAndSet(false,true))return;
        String role=req==PICK_DIFFUSION?"diffusion":req==PICK_T5?"t5":"vae";
        cancelImport=false;refresh();
        Uri uri=data.getData();
        worker.execute(()->importFile(role,uri));
    }
    private void importFile(String role,Uri uri) {
        File staging=null;
        try{
            long reportedSize=-1;
            String name=role+".safetensors";
            try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
                if(c!=null&&c.moveToFirst()){
                    int nameColumn=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeColumn=c.getColumnIndex(OpenableColumns.SIZE);
                    if(nameColumn>=0&&!c.isNull(nameColumn))name=c.getString(nameColumn);
                    if(sizeColumn>=0&&!c.isNull(sizeColumn))reportedSize=c.getLong(sizeColumn);
                }
            }
            String extension=name.toLowerCase(Locale.ROOT).endsWith(".gguf")?".gguf":
                name.toLowerCase(Locale.ROOT).endsWith(".safetensors")?".safetensors":
                name.toLowerCase(Locale.ROOT).endsWith(".ckpt")?".ckpt":null;
            if(extension==null)throw new IllegalArgumentException("Model component needs .gguf or .safetensors extension");
            File folder=dir();
            if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("Cannot create model storage");
            if(reportedSize>0 && new StatFs(folder.getAbsolutePath()).getAvailableBytes()<reportedSize+300L*1024*1024)
                throw new IllegalArgumentException("Not enough device storage for model + 300MB buffer");
            staging=new File(folder,role+"_staging"+extension);
            if(staging.exists()&&!staging.delete())throw new IllegalStateException("Cannot replace partial import");
            note("Importing "+role+" model into private storage...");
            long copied=0;
            try(InputStream in=getContentResolver().openInputStream(uri);
                FileOutputStream out=new FileOutputStream(staging)) {
                if(in==null)throw new IllegalStateException("File provider returned no readable content");
                byte[] buffer=new byte[1024*1024];
                int read;
                long lastCheck=0;
                while((read=in.read(buffer))!=-1){
                    if(cancelImport)throw new InterruptedException("Import cancelled");
                    out.write(buffer,0,read);copied+=read;
                    if(copied-lastCheck>=32L*1024*1024){
                        lastCheck=copied;
                        if(new StatFs(folder.getAbsolutePath()).getAvailableBytes()<300L*1024*1024)
                            throw new IllegalStateException("Storage almost full; import stopped safely");
                        final long sizeMB=copied/1048576L;
                        note("Importing "+role+": "+sizeMB+" MB copied...");
                    }
                }
                out.getFD().sync();
            }
            ModelFileInspector.Result format=ModelFileInspector.inspect(staging);
            if(!format.plausible)throw new IllegalArgumentException("Checkpoint header rejected: "+format.detail);
            File selected=new File(folder,role+"_"+System.currentTimeMillis()+extension);
            if(!staging.renameTo(selected))throw new IllegalStateException("Cannot finalize model copy");
            String prior=path(role);
            prefs.edit().putString(role+"Path",selected.getAbsolutePath()).apply();
            if(!prior.isEmpty()){
                File old=new File(prior);
                if(old.getParentFile()!=null&&old.getParentFile().equals(folder))old.delete();
            }
            note(role+" model imported; compatibility must still be tested by inference.");
        }catch(Throwable e){
            if(staging!=null)staging.delete();
            note("Import of "+role+" failed: "+e.getMessage());
        }finally{
            busy.set(false);runOnUiThread(this::refresh);
        }
    }
    /** Verified Internet access, not simply "Wi-Fi connected" or radio enabled. */
    private boolean internetAvailable(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Exception ignored){return false;}
    }
    private void recordVideoEvidence(String state,String detail,int frames,long duration){
        // A claim of success requires actual native frames AND a reopened valid MP4.
        getSharedPreferences("realframe_video_proof",MODE_PRIVATE).edit()
            .putString("last_status",state)
            .putString("last_detail",detail.length()>340?detail.substring(0,340):detail)
            .putLong("last_duration_sec",duration)
            .putInt("last_frames",frames)
            .putLong("last_test_ms",System.currentTimeMillis()).apply();
    }
    private void generate(){
        if(!allImported()){
            new AlertDialog.Builder(this)
                .setTitle("WAN video weights are missing")
                .setMessage("Real on-device text-to-video needs three licensed components: Wan 1.3B Q4, UMT5 encoder and VAE (~4.73 GB total). Installation is automatic IN this app. This is an experimental CPU renderer; 12 GB physical RAM may still be insufficient.")
                .setPositiveButton("ONE-TAP WAN MODEL SETUP",(d,w)->confirmAutoInstall())
                .setNeutralButton("Make cinematic MP4 instead",(d,w)->
                    startActivity(new Intent(this,StoryMovieComposerActivity.class)))
                .setNegativeButton("Cancel",null).show();
            return;
        }
        if(!LocalVideoEngine.isAvailable()){
            new AlertDialog.Builder(this)
                .setTitle("Native WAN engine cannot load")
                .setMessage(LocalVideoEngine.issue())
                .setPositiveButton("Device Health",(d,w)->
                    startActivity(new Intent(this,StudioHealthActivity.class)))
                .setNegativeButton("Close",null).show();
            return;
        }
        if(!busy.compareAndSet(false,true))return;
        String phrase=prompt.getText().toString().trim();
        String bad=negative.getText().toString();
        if(phrase.isEmpty()){busy.set(false);toast("Enter a video prompt");return;}
        long seedNum;
        try{seedNum=Long.parseLong(seed.getText().toString().trim());}
        catch(Exception e){busy.set(false);toast("Seed must be a number");return;}
        if(seedNum<1||seedNum>2147483647L){busy.set(false);toast("Seed out of range");return;}
        int[] dimensions={192,256,320,384};
        int[] frames={5,9,17,33};
        int w=dimensions[resolution.getSelectedItemPosition()];
        int n=frames[frameCount.getSelectedItemPosition()];
        int step=Integer.parseInt(steps.getSelectedItem().toString());
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        if(info.availMem<6_000_000_000L && !lowRamAcknowledged){
            busy.set(false);
            refresh();
            new AlertDialog.Builder(this)
                .setTitle("WAN might exceed available Samsung RAM")
                .setMessage("Only "+(info.availMem/1_000_000_000L)+
                    " GB free RAM is currently available. Even the Q4 Wan 1.3B model plus UMT5 and the video decoder can use many gigabytes. A 12 GB PHONE is not the same as 12 GB free model RAM. The Android process may be killed before rendering a frame.")
                .setPositiveButton("Try experimental WAN anyway",(d,w)->{
                    lowRamAcknowledged=true;generate();
                })
                .setNeutralButton("Use LOCAL story movie",(d,w)->
                    startActivity(new Intent(this,LocalAutoFilmActivity.class)))
                .setNegativeButton("Cancel",null).show();
            return;
        }
        refresh();
        note("Native video model loading. May use multiple GB RAM and take a long time...");
        final String diffusion=path("diffusion"),t5=path("t5"),vae=path("vae");
        final boolean internetAtStart=internetAvailable();
        worker.execute(()->{
            final long startedAt=SystemClock.elapsedRealtime();
            final LocalVideoEncoder[] writer={null};
            int resultCount=0;
            try {
                resultCount=LocalVideoEngine.generate(diffusion,t5,vae,phrase,bad,
                    w,w,n,step,seedNum,4,
                    (index,width,height,fps,pixels)->{
                        try{
                            if(writer[0]==null)
                                writer[0]=new LocalVideoEncoder(getApplicationContext(),width,height,fps);
                            writer[0].append(pixels);
                            note("Encoding local AI frame "+(index+1)+"/"+n+" • "+fps+"fps");
                        }catch(Exception ex){
                            throw new IllegalStateException("H.264 encoding failed: "+ex.getMessage(),ex);
                        }
                    });
                if(resultCount<1||writer[0]==null)
                    throw new IllegalStateException("No AI video frames were generated");
                Uri movie=writer[0].finish();
                final VideoOutputVerifier.Result inspection=VideoOutputVerifier.verify(this,movie,
                    Math.min(resultCount,5));
                if(!inspection.valid){
                    // A corrupt or unreadable file must NOT be labelled a local success.
                    getContentResolver().delete(movie,null,null);
                    throw new IllegalStateException("Encoded MP4 failed validation: "+inspection.detail);
                }
                long elapsed=(SystemClock.elapsedRealtime()-startedAt)/1000;
                boolean connectedAtEnd=internetAvailable();
                String privacy=internetAtStart==connectedAtEnd ?
                    (connectedAtEnd?"Local while Internet ON":"Local while Internet OFF"):
                    "Network changed during native inference";
                String outcome=resultCount==n?"SUCCESS":"PARTIAL";
                String details=inspection.detail+"; "+resultCount+"/"+n+
                    " actual native Wan frames; "+privacy+"; "+elapsed+"s";
                recordVideoEvidence(outcome,details,resultCount,elapsed);
                note((resultCount==n?"✓ VERIFIED LOCAL WAN VIDEO: ":"PARTIAL WAN VIDEO: ")+
                    details+". Saved in Gallery → Movies/RealFrameStudio.");
            }catch(Throwable ex){
                long elapsed=(SystemClock.elapsedRealtime()-startedAt)/1000;
                String detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                String nativeIssue=LocalVideoEngine.lastNativeIssue();
                if(nativeIssue!=null&&!nativeIssue.isEmpty())detail+=" | native: "+nativeIssue;
                recordVideoEvidence("FAILED",detail,resultCount,elapsed);
                note("LOCAL VIDEO NOT VERIFIED: "+detail+
                    ". Open Device Health → Copy diagnostics. CPU Wan may exceed phone RAM. For a usable offline MP4 from images, choose Cinematic Movie Composer.");
            }finally{
                if(writer[0]!=null)writer[0].close();
                busy.set(false);
                runOnUiThread(this::refresh);
            }
        });
    }
    @Override protected void onResume(){
        super.onResume();screenVisible=true;uiHandler.removeCallbacks(installerUpdate);
        uiHandler.post(installerUpdate);refresh();
    }
    @Override protected void onPause(){
        screenVisible=false;uiHandler.removeCallbacks(installerUpdate);super.onPause();
    }
    @Override protected void onDestroy(){
        if(busy.get()){cancelImport=true;LocalVideoEngine.cancel();}
        worker.shutdown();
        super.onDestroy();
    }
}
