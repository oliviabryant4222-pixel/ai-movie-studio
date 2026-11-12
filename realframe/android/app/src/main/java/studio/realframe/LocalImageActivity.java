package studio.realframe;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real on-device stable-diffusion.cpp CPU image generator, no cloud upload.
 * Fully offline after a compatible model checkpoint has been imported.
 * Heavy local inference is experimental on mobile; NPU/QNN is not enabled.
 */
public final class LocalImageActivity extends Activity {
    private static final int CHOOSE_MODEL = 9177, BG=0xFF0A1220, SURFACE=0xFF1D2940,
            WHITE=0xFFF2F5FF, MUTED=0xFFB3C2D6, TEAL=0xFF69E8D1, PURPLE=0xFFAB9DF9;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private volatile boolean importCancelled=false;
    private EditText prompt,negative,seed;
    private Spinner resolution,stepList,batchList;
    private TextView status,modelStatus,engineStatus;
    private ImageView preview;
    private Button generate,importModel;
    private Bitmap result;
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void put(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private EditText input(LinearLayout root,String title,String value){
        TextView cap=label(title,13,MUTED,true);cap.setPadding(0,dp(10),0,dp(5));put(root,cap);
        EditText edit=new EditText(this);edit.setText(value);edit.setTextColor(WHITE);
        edit.setHintTextColor(MUTED);edit.setBackground(shape(0xFF293951));
        edit.setPadding(dp(12),dp(8),dp(12),dp(8));
        put(root,edit);return edit;
    }
    private Button button(LinearLayout parent,String text,Runnable task,int color){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setBackground(shape(color));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));
        params.topMargin=dp(9);parent.addView(b,params);
        b.setOnClickListener(v->{try{task.run();}catch(Exception e){notifyUser(e.getMessage());}});
        return b;
    }
    private Spinner options(LinearLayout parent,String title,String[] choices){
        TextView cap=label(title,13,MUTED,true);cap.setPadding(0,dp(10),0,dp(5));put(parent,cap);
        Spinner sp=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
        sp.setAdapter(adapter);sp.setBackground(shape(0xFFE4E9F1));
        put(parent,sp);return sp;
    }
    private void notifyUser(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void message(String text){runOnUiThread(()->{if(status!=null)status.setText(text);});}
    private String modelPath(){
        return getSharedPreferences("realframe_local_sd",MODE_PRIVATE).getString("modelPath","");
    }
    private void refresh(){
        String path=modelPath();
        File model=new File(path);
        if(modelStatus!=null) modelStatus.setText(model.isFile()
            ? "Model ready: "+model.getName()+" ("+(model.length()/(1024L*1024L))+" MB)"
            : "No model imported. Choose a SINGLE-FILE compatible SD1.5 checkpoint.");
        if(engineStatus!=null)engineStatus.setText(LocalImageEngine.isAvailable()
            ? "Native CPU inference library: INSTALLED ✓"
            : "Native CPU inference library unavailable: "+LocalImageEngine.unavailableReason());
        if(generate!=null)generate.setEnabled(!running.get() && LocalImageEngine.isAvailable() && model.isFile());
        if(importModel!=null)importModel.setEnabled(!running.get());
    }
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(16),dp(12),dp(16),dp(8));main.setBackgroundColor(BG);setContentView(main);
        TextView title=label("←   LOCAL AI IMAGE STUDIO",21,WHITE,true);title.setOnClickListener(v->finish());put(main,title);
        gap(main,6);
        put(main,label("REAL stable-diffusion.cpp CPU inference · runs on this phone without internet",12,TEAL,false));
        gap(main,10);
        ScrollView scroll=new ScrollView(this);main.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14),dp(15),dp(14),dp(15));form.setBackground(shape(SURFACE));scroll.addView(form);

        engineStatus=label("",13,TEAL,true);put(form,engineStatus);gap(form,9);
        modelStatus=label("",13,MUTED,false);put(form,modelStatus);
        importModel=button(form,"📁 Import single-file SD checkpoint",()->chooseModel(),0xFF484180);
        gap(form,7);
        put(form,label("Use a legitimately downloaded SD1.5 / compatible single-file checkpoint. The model may be several GB and copying uses extra storage. Splitted FLUX/Wan model packs and unsupported formats cannot be loaded through this simple adapter.",12,MUTED,false));
        gap(form,12);
        prompt=input(form,"Image prompt","Photorealistic portrait of a fictional adult, natural sunlight, realistic skin and hair, documentary photography");
        negative=input(form,"Negative prompt","watermark, jpeg artifacts, blurry, plastic texture");
        seed=input(form,"Seed (integer)","13901");
        if(getIntent()!=null){
            String incoming=getIntent().getStringExtra("prompt");
            if(incoming!=null&&!incoming.isEmpty())prompt.setText(incoming);
            incoming=getIntent().getStringExtra("negative");
            if(incoming!=null)negative.setText(incoming);
            incoming=getIntent().getStringExtra("seed");
            if(incoming!=null&&!incoming.isEmpty())seed.setText(incoming);
        }
        resolution=options(form,"Resolution (CPU preview)",new String[]{"256 × 256","384 × 384","512 × 512"});
        stepList=options(form,"Sampling steps",new String[]{"4","8","12","20"});
        batchList=options(form,"Local batch (sequential, one model load)",new String[]{"1","2","3","4"});
        put(form,label("Each finished image saves automatically to Pictures/RealFrameStudio. Batch runs sequentially to conserve RAM; it is not parallel GPU processing.",12,MUTED,false));
        gap(form,11);
        generate=button(form,"✦  GENERATE LOCAL IMAGE BATCH",this::generateImage,0xFF4A438E);
        button(form,"⏹  Cancel active local render",()->{importCancelled=true;LocalImageEngine.cancel();message("Cancellation requested; model loading may not stop immediately.");},0xFF4B3851);
        gap(form,10);
        status=label("Ready. Import a supported checkpoint and generate.",13,MUTED,false);
        put(form,status);
        gap(form,14);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(shape(0xFF172135));
        form.addView(preview,new LinearLayout.LayoutParams(-1,dp(310)));
        gap(form,12);
        button(form,"💾  Save preview image again",this::export,0xFF247F73);
        gap(form,9);
        put(form,label("CPU-only beta: image generation can be very slow, use lots of RAM, or fail for incompatible models. No QNN/NPU or offline video renderer is included. Keep the app open while sampling. Hosted generation is available separately under Online.",12,MUTED,false));
        refresh();
    }
    private void chooseModel(){
        if(running.get())return;
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(pick,CHOOSE_MODEL);
    }
    @Override protected void onActivityResult(int code,int resultCode,Intent data){
        super.onActivityResult(code,resultCode,data);
        if(code!=CHOOSE_MODEL||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        if(!running.compareAndSet(false,true))return;
        Uri uri=data.getData();
        generate.setEnabled(false);importModel.setEnabled(false);
        importCancelled=false;
        message("Checking model size and local storage...");
        executor.execute(()->importCheckpoint(uri));
    }
    private void importCheckpoint(Uri uri){
        File pending=null;
        try{
            long bytes=-1;
            String filename="model.safetensors";
            try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
                if(c!=null && c.moveToFirst()){
                    int sizeColumn=c.getColumnIndex(OpenableColumns.SIZE);
                    int nameColumn=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(sizeColumn>=0&&!c.isNull(sizeColumn))bytes=c.getLong(sizeColumn);
                    if(nameColumn>=0&&!c.isNull(nameColumn))filename=c.getString(nameColumn);
                }
            }
            String ext=filename.toLowerCase(Locale.ROOT).endsWith(".gguf")?".gguf":
                filename.toLowerCase(Locale.ROOT).endsWith(".safetensors")?".safetensors":
                filename.toLowerCase(Locale.ROOT).endsWith(".ckpt")?".ckpt":null;
            if(ext==null)throw new IllegalArgumentException("Select .safetensors, .gguf or .ckpt checkpoint file");
            if(bytes>0&&bytes<8L*1024*1024)
                throw new IllegalArgumentException("Selected file is too small to be a diffusion checkpoint");
            File dir=new File(getFilesDir(),"model_cache");
            if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot make model cache");
            StatFs stat=new StatFs(dir.getAbsolutePath());
            if(bytes>0&&stat.getAvailableBytes()<bytes+250L*1024*1024)
                throw new IllegalArgumentException("Not enough storage. Need model size plus 250MB free.");
            pending=new File(dir,"staging"+ext);
            if(pending.exists()&&!pending.delete())throw new IllegalStateException("Cannot replace incomplete import");
            long copied=0;
            try(InputStream in=getContentResolver().openInputStream(uri);
                FileOutputStream out=new FileOutputStream(pending)){
                if(in==null)throw new IllegalArgumentException("File is no longer readable");
                byte[] buffer=new byte[1024*1024];
                int count;long lastUpdate=0;
                while((count=in.read(buffer))!=-1){
                    if(importCancelled||Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Model import cancelled");
                    out.write(buffer,0,count);copied+=count;
                    if(copied-lastUpdate>=32L*1024*1024){
                        lastUpdate=copied;
                        message("Importing model: "+(copied/(1024*1024))+" MB"+(bytes>0?" / "+bytes/(1024*1024)+" MB":""));
                    }
                }
                out.getFD().sync();
            }
            if(copied<8L*1024*1024)throw new IllegalArgumentException("Model file appears incomplete");
            File ready=new File(dir,"checkpoint_"+System.currentTimeMillis()+ext);
            if(!pending.renameTo(ready))throw new IllegalStateException("Could not activate imported model");
            String prior=modelPath();
            getSharedPreferences("realframe_local_sd",MODE_PRIVATE).edit().putString("modelPath",ready.getAbsolutePath()).apply();
            if(!prior.isEmpty()&&!prior.equals(ready.getAbsolutePath())){
                File old=new File(prior);
                if(old.getParentFile()!=null && old.getParentFile().equals(dir))old.delete();
            }
            message("Checkpoint imported. Ready for local generation.");
        }catch(Throwable e){
            if(pending!=null)pending.delete();
            message("Import failed: "+e.getMessage());
        }finally{
            running.set(false);runOnUiThread(this::refresh);
        }
    }

    /**
     * A single native context is kept alive across the selected batch. Every
     * image is persisted in MediaStore before generating the next scene.
     * Outputs from a cancelled/failed batch remain in Gallery.
     */
    private void generateImage(){
        if(!running.compareAndSet(false,true))return;
        result=null;
        String model=modelPath(), text=prompt.getText().toString(), neg=negative.getText().toString();
        long seedValue;
        try{seedValue=Long.parseLong(seed.getText().toString().trim());}
        catch(Exception e){running.set(false);notifyUser("Seed must be an integer");refresh();return;}
        int[] sizes={256,384,512};
        int width=sizes[resolution.getSelectedItemPosition()];
        int steps=Integer.parseInt(stepList.getSelectedItem().toString());
        int count=Integer.parseInt(batchList.getSelectedItem().toString());
        final int[] saved={0};
        refresh();
        message("Loading model once for "+count+" local image(s). Keep app open...");
        executor.execute(()->{
            try{
                int finished=LocalImageEngine.generateBatch(model,text,neg,width,width,steps,seedValue,4,count,
                    (index,argb)->{
                        if(argb==null || argb.length!=width*width)
                            throw new IllegalStateException("Native frame size mismatch");
                        Bitmap bmp=Bitmap.createBitmap(argb,width,width,Bitmap.Config.ARGB_8888);
                        try {
                            Uri gallery=saveLocalPng(bmp,index+1);
                            saved[0]++;
                            runOnUiThread(()->{
                                result=bmp;
                                preview.setImageBitmap(bmp);
                                status.setText("Saved local image "+(index+1)+"/"+count+
                                    " to Gallery. No cloud upload.");
                            });
                        } catch(Exception error) {
                            bmp.recycle();
                            throw new IllegalStateException("Image "+(index+1)+" could not save: "+error.getMessage(),error);
                        }
                    });
                message("Local batch finished: "+finished+"/"+count+" images. "+saved[0]+
                        " saved to Gallery."+(finished<count?" Rendering cancelled.":""));
            }catch(Throwable e){
                message("Local batch stopped after "+saved[0]+" image(s): "+e.getMessage());
            }finally{
                running.set(false);runOnUiThread(this::refresh);
            }
        });
    }

    private Uri saveLocalPng(Bitmap bmp,int number) throws Exception {
        Uri output=null;
        try {
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "realframe_local_"+System.currentTimeMillis()+"_"+number+".png");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RealFrameStudio");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            output=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(output==null)throw new IllegalStateException("Gallery unavailable");
            try(OutputStream stream=getContentResolver().openOutputStream(output,"w")){
                if(stream==null||!bmp.compress(Bitmap.CompressFormat.PNG,100,stream))
                    throw new IllegalStateException("PNG encoder failed");
            }
            ContentValues done=new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING,0);
            getContentResolver().update(output,done,null,null);
            return output;
        } catch(Exception error){
            if(output!=null) getContentResolver().delete(output,null,null);
            throw error;
        }
    }
    private void export(){
        if(result==null){notifyUser("Generate a local image first.");return;}
        Bitmap copy=result;
        executor.execute(()->{
            Uri output=null;
            try{
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,"realframe_local_"+System.currentTimeMillis()+".png");
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RealFrameStudio");
                values.put(MediaStore.Images.Media.IS_PENDING,1);
                output=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(output==null)throw new IllegalStateException("Could not create Gallery file");
                try(OutputStream stream=getContentResolver().openOutputStream(output,"w")){
                    if(stream==null||!copy.compress(Bitmap.CompressFormat.PNG,100,stream))
                        throw new IllegalStateException("PNG write failed");
                }
                ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);
                getContentResolver().update(output,done,null,null);
                message("Saved locally to Pictures/RealFrameStudio.");
            }catch(Exception e){
                if(output!=null)getContentResolver().delete(output,null,null);
                message("Gallery save failed: "+e.getMessage());
            }
        });
    }
    @Override protected void onDestroy(){
        if(running.get()){importCancelled=true;LocalImageEngine.cancel();}
        executor.shutdown();
        super.onDestroy();
    }
}
