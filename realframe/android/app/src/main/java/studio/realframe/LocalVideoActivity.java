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
    private TextView status,modelInfo,engineInfo;
    private EditText prompt,negative,seed;
    private Spinner resolution,frameCount,steps;
    private Button generate;
    private SharedPreferences prefs;

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
        btn.setOnClickListener(v->{try{action.run();}catch(Exception error){toast(error.getMessage());}});
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
        if(generate!=null)generate.setEnabled(!busy.get() && allImported() && LocalVideoEngine.isAvailable());
    }

    @Override public void onCreate(Bundle savedState){
        super.onCreate(savedState);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setPadding(dp(15),dp(15),dp(15),dp(12));setContentView(root);
        TextView header=label("←  LOCAL AI VIDEO • LAB",22,WHITE,true);
        header.setOnClickListener(v->finish());put(root,header);gap(root,8);
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
        button(panel,"1 • Import Wan T2V diffusion model",()->pick(PICK_DIFFUSION),0xFF4E4286);
        button(panel,"2 • Import UMT5 / T5 text encoder",()->pick(PICK_T5),0xFF4E4286);
        button(panel,"3 • Import Wan VAE",()->pick(PICK_VAE),0xFF4E4286);
        button(panel,"Model requirements & download pages",this::docs,0xFF28685F);
        gap(panel,15);
        put(panel,label("TEXT-TO-VIDEO GENERATION",17,PURPLE,true));
        prompt=entry(panel,"Prompt","Short cinematic clip of a fictional adult walking in a park, realistic movement and natural sunlight");
        negative=entry(panel,"Negative prompt","watermark, text, blurry, glitch, distorted face");
        seed=entry(panel,"Seed (1–2147483647)","13901");
        resolution=select(panel,"Resolution (CPU experiment)",new String[]{"256×256","320×320","384×384"});
        frameCount=select(panel,"Video frames (16fps)",new String[]{"5 frames","9 frames","17 frames","33 frames"});
        steps=select(panel,"Sampling steps",new String[]{"4","8","12"});
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
    private void generate(){
        if(!allImported()){toast("Import all 3 compatible video model components first");return;}
        if(!LocalVideoEngine.isAvailable()){toast("Native CPU video engine unavailable");return;}
        if(!busy.compareAndSet(false,true))return;
        String phrase=prompt.getText().toString().trim();
        String bad=negative.getText().toString();
        if(phrase.isEmpty()){busy.set(false);toast("Enter a video prompt");return;}
        long seedNum;
        try{seedNum=Long.parseLong(seed.getText().toString().trim());}
        catch(Exception e){busy.set(false);toast("Seed must be a number");return;}
        if(seedNum<1||seedNum>2147483647L){busy.set(false);toast("Seed out of range");return;}
        int[] dimensions={256,320,384};
        int[] frames={5,9,17,33};
        int w=dimensions[resolution.getSelectedItemPosition()];
        int n=frames[frameCount.getSelectedItemPosition()];
        int step=Integer.parseInt(steps.getSelectedItem().toString());
        refresh();
        note("Native video model loading. May use multiple GB RAM and take a long time...");
        final String diffusion=path("diffusion"),t5=path("t5"),vae=path("vae");
        worker.execute(()->{
            final LocalVideoEncoder[] writer={null};
            try {
                int resultCount=LocalVideoEngine.generate(diffusion,t5,vae,phrase,bad,
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
                note("Saved "+resultCount+" AI-generated frames as MP4 in Movies/RealFrameStudio. "+
                    "Open your Gallery to play the file: "+movie.getLastPathSegment());
            }catch(Throwable ex){
                note("Local AI video failed: "+ex.getMessage());
            }finally{
                if(writer[0]!=null)writer[0].close();
                busy.set(false);
                runOnUiThread(this::refresh);
            }
        });
    }
    @Override protected void onDestroy(){
        if(busy.get()){cancelImport=true;LocalVideoEngine.cancel();}
        worker.shutdown();
        super.onDestroy();
    }
}
