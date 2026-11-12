package studio.realframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reference-conditioned image-to-image using local stable-diffusion.cpp.
 * Real input image changes latent conditioning, rather than merely adding
 * a textual description. This improves continuity, but cannot guarantee
 * exact facial identity and is not an installed FaceID/IP-Adapter.
 */
public final class ReferenceStudioActivity extends Activity {
    private static final int PICK_REFERENCE=8421, BG=0xFF0C1425, CARD=0xFF1A2A43,
        WHITE=0xFFF2F6FF,MUTED=0xFFACBBD0,TEAL=0xFF76E6CF,PURPLE=0xFFB7A0FC;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private EditText prompt,negative,seed;
    private Spinner dimension,steps,strength,batch,character;
    private TextView status,modelStatus;
    private ImageView sourceView,outputView;
    private Bitmap sourceBitmap;
    private Button generate,choosePhoto;
    private Uri sourceUri;
    private final List<JSONObject> characterPresets=new ArrayList<>();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable g=new GradientDrawable();g.setColor(color);
        g.setCornerRadius(dp(15));return g;
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);
        t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private LinearLayout col(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);return p;}
    private void put(LinearLayout p,View child){p.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private Button button(LinearLayout p,String title,int color,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(WHITE);b.setBackground(shape(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.topMargin=dp(9);p.addView(b,lp);b.setOnClickListener(v->action.run());return b;
    }
    private EditText input(LinearLayout p,String caption,String value){
        TextView label=label(caption,13,MUTED,true);label.setPadding(0,dp(11),0,dp(4));put(p,label);
        EditText e=new EditText(this);e.setText(value);e.setTextColor(WHITE);
        e.setPadding(dp(11),dp(10),dp(11),dp(10));e.setBackground(shape(0xFF293B56));
        put(p,e);return e;
    }
    private Spinner choose(LinearLayout p,String caption,String[] options){
        TextView c=label(caption,13,MUTED,true);c.setPadding(0,dp(11),0,dp(4));put(p,c);
        Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(
            this,android.R.layout.simple_spinner_dropdown_item,options));
        s.setBackground(shape(0xFFE2EAF4));put(p,s);return s;
    }
    private void notifyUser(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    private void report(String text){runOnUiThread(()->status.setText(text));}
    private File model(){
        return new File(getSharedPreferences("realframe_local_sd",MODE_PRIVATE)
            .getString("modelPath",""));
    }
    private void refresh(){
        if(modelStatus!=null){
            modelStatus.setText(model().isFile()?"Local model: "+model().getName():
                "Local model missing: use One-Tap AI Setup first");
        }
        if(generate!=null)generate.setEnabled(!busy.get() && model().isFile() &&
            sourceBitmap!=null && LocalImageEngine.isAvailable());
        if(choosePhoto!=null)choosePhoto.setEnabled(!busy.get());
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=col();root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(15),dp(16),dp(8));setContentView(root);
        TextView title=label("←   REALFRAME · REFERENCE AI",22,WHITE,true);
        title.setOnClickListener(v->finish());put(root,title);gap(root,6);
        put(root,label("True on-device img2img • character continuity aid • no photo uploads",12,TEAL,false));
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout p=col();p.setBackground(shape(CARD));
        p.setPadding(dp(14),dp(14),dp(14),dp(16));scroll.addView(p);
        modelStatus=label("",13,TEAL,true);put(p,modelStatus);gap(p,7);
        button(p,"✦ One-Tap AI Model Setup",0xFF3B7770,()->
            startActivity(new Intent(this,QuickStartActivity.class)));
        put(p,label("Choose a fictional adult character photo from Gallery. Unlike prompt-only consistency, this image is fed into the actual SD img2img generation pipeline. Lower edit strength preserves more of the reference.",13,MUTED,false));
        choosePhoto=button(p,"▣ Choose reference photo (private, on device)",0xFF4F468A,this::pick);
        sourceView=new ImageView(this);sourceView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sourceView.setBackground(shape(0xFF101B30));gap(p,8);
        p.addView(sourceView,new LinearLayout.LayoutParams(-1,dp(170)));
        characterPresets.clear();characterPresets.add(new JSONObject());
        characterPresets.addAll(new StudioStore(this).characters());
        String[] names=new String[characterPresets.size()];names[0]="No saved character profile";
        for(int i=1;i<names.length;i++)
            names[i]=characterPresets.get(i).optString("name","Character "+i);
        character=choose(p,"CHARACTER VAULT (optional)",names);
        prompt=input(p,"Prompt","Realistic portrait of the same fictional adult character, natural skin and hair, studio lighting");
        negative=input(p,"Negative prompt","watermark, blurry, broken anatomy, distorted face");
        seed=input(p,"Seed 1–2147483647","13901");
        if(getIntent()!=null){
            String incoming=getIntent().getStringExtra("prompt");
            if(incoming!=null&&!incoming.isEmpty())prompt.setText(incoming);
        }
        dimension=choose(p,"RESOLUTION (CPU)",new String[]{"256 × 256","384 × 384","512 × 512"});
        steps=choose(p,"SAMPLING STEPS",new String[]{"4","8","12","20"});
        strength=choose(p,"EDIT STRENGTH",new String[]{
            "0.25 — preserve reference more","0.40 — moderate changes",
            "0.55 — stronger changes","0.75 — reinterpret reference"});
        batch=choose(p,"BATCH SIZE (sequential)",new String[]{"1","2","3","4"});
        generate=button(p,"✦ GENERATE USING ACTUAL REFERENCE",0xFF6751B3,this::render);
        button(p,"⏹ Cancel local model sampling",0xFF5C3D53,()->{
            LocalImageEngine.cancel();report("Cancel requested; loading may not stop immediately.");
        });
        gap(p,12);status=label("Choose a reference image to begin.",13,MUTED,false);
        put(p,status);gap(p,8);
        outputView=new ImageView(this);outputView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        outputView.setBackground(shape(0xFF101B30));
        p.addView(outputView,new LinearLayout.LayoutParams(-1,dp(280)));gap(p,10);
        put(p,label("Images save to Pictures/RealFrameStudio automatically. Same photo + lower strength may improve likeness but cannot guarantee the exact identity, scene, or pose. CPU-only engine; no NPU or remote API. A compatible downloaded SD1.5 model is required.",12,MUTED,false));
        refresh();
        String savedUri=getPreferences(MODE_PRIVATE).getString("referenceUri","");
        if(!savedUri.isEmpty())loadReference(Uri.parse(savedUri));
    }
    private void pick(){
        if(busy.get())return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_REFERENCE);
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(request!=PICK_REFERENCE||code!=RESULT_OK||data==null||data.getData()==null)return;
        sourceUri=data.getData();
        try{getContentResolver().takePersistableUriPermission(sourceUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception ignored){}
        getPreferences(MODE_PRIVATE).edit().putString("referenceUri",sourceUri.toString()).apply();
        loadReference(sourceUri);
    }
    private void loadReference(Uri uri){
        if(busy.get())return;
        worker.execute(()->{
            try{
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IllegalArgumentException("Photo not readable");
                    BitmapFactory.decodeStream(in,null,bounds);
                }
                if(bounds.outWidth<1||bounds.outHeight<1)
                    throw new IllegalArgumentException("Invalid photo dimensions");
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=Math.max(1,(int)Math.ceil(
                    Math.max(bounds.outWidth,bounds.outHeight)/1024.0));
                options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                Bitmap decoded;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IllegalArgumentException("Photo provider is offline");
                    decoded=BitmapFactory.decodeStream(in,null,options);
                }
                if(decoded==null)throw new IllegalArgumentException("Android cannot decode photo");
                runOnUiThread(()->{
                    if(sourceBitmap!=null&&sourceBitmap!=decoded)sourceBitmap.recycle();
                    sourceBitmap=decoded;
                    sourceView.setImageBitmap(decoded);
                    status.setText("Reference selected. Lower strength better preserves original identity.");
                    refresh();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    status.setText("Could not open reference: "+error.getMessage());
                    getPreferences(MODE_PRIVATE).edit().remove("referenceUri").apply();
                });
            }
        });
    }
    private String fullPrompt(){
        String text=prompt.getText().toString().trim();
        if(text.isEmpty())throw new IllegalArgumentException("Add an AI prompt");
        JSONObject selected=characterPresets.get(character.getSelectedItemPosition());
        if(selected.length()>0)
            text="Fictional adult character: "+selected.optString("appearance","")+
                ". Wardrobe: "+selected.optString("wardrobe","")+". "+text;
        return text;
    }
    private int[] centerCrop(Bitmap src,int w,int h){
        Bitmap prepared=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(prepared);canvas.drawColor(Color.BLACK);
        int sw=src.getWidth(),sh=src.getHeight();
        double targetRatio=(double)w/h;
        int srcW=sw,srcH=sh;
        if((double)sw/sh>targetRatio)srcW=(int)Math.round(sh*targetRatio);
        else srcH=(int)Math.round(sw/targetRatio);
        Rect from=new Rect((sw-srcW)/2,(sh-srcH)/2,(sw+srcW)/2,(sh+srcH)/2);
        canvas.drawBitmap(src,from,new Rect(0,0,w,h),new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] pixels=new int[w*h];
        prepared.getPixels(pixels,0,w,0,0,w,h);prepared.recycle();
        return pixels;
    }
    private void render(){
        if(!busy.compareAndSet(false,true))return;
        if(sourceBitmap==null||!model().isFile()){
            busy.set(false);notifyUser("Import model and choose a reference first");refresh();return;
        }
        final String text;
        try{text=fullPrompt();}
        catch(Exception error){busy.set(false);notifyUser(error.getMessage());refresh();return;}
        final long userSeed;
        try{userSeed=Long.parseLong(seed.getText().toString().trim());
            if(userSeed<1||userSeed>2147483647L)throw new NumberFormatException();
        }catch(Exception error){
            busy.set(false);notifyUser("Seed must be 1 to 2147483647");refresh();return;
        }
        final int[] dims={256,384,512};
        final float[] values={0.25f,0.40f,0.55f,0.75f};
        final int size=dims[dimension.getSelectedItemPosition()];
        final int n=Integer.parseInt(batch.getSelectedItem().toString());
        final int countSteps=Integer.parseInt(steps.getSelectedItem().toString());
        final float edit=values[strength.getSelectedItemPosition()];
        final String path=model().getAbsolutePath();
        final String unwanted=negative.getText().toString();
        // Native inference cannot run on UI thread, and batching reuses the model.
        final Bitmap photo=sourceBitmap;refresh();
        report("Preparing reference photo locally. No uploads.");
        worker.execute(()->{
            int completed=0;
            try{
                int[] pixels=centerCrop(photo,size,size);
                completed=LocalImageEngine.generateReferenceBatch(path,text,unwanted,
                    size,size,countSteps,userSeed,4,n,pixels,edit,(index,argb)->{
                    Bitmap image=Bitmap.createBitmap(argb,size,size,Bitmap.Config.ARGB_8888);
                    try {
                        save(image,index+1);
                    }catch(Exception error){
                        image.recycle();
                        throw new IllegalStateException("Could not save reference output: "+error.getMessage(),error);
                    }
                    runOnUiThread(()->{
                        outputView.setImageBitmap(image);
                        status.setText("Saved reference-conditioned image "+(index+1)+"/"+n+
                            " • Strength "+edit+" • On-device CPU");
                    });
                });
                final int done=completed;
                report("Reference batch done: "+done+"/"+n+" images. Look in Gallery.");
            }catch(Throwable failure){report("Reference generation stopped: "+failure.getMessage());}
            finally{busy.set(false);runOnUiThread(this::refresh);}
        });
    }
    private Uri save(Bitmap image,int index) throws Exception{
        ContentValues values=new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
            "realframe_ref_"+System.currentTimeMillis()+"_"+index+".png");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RealFrameStudio");
        values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri result=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(result==null)throw new IllegalStateException("Could not create gallery image");
        try{
            try(OutputStream out=getContentResolver().openOutputStream(result,"w")){
                if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))
                    throw new IllegalStateException("Failed to write reference output image");
            }
            ContentValues complete=new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING,0);
            getContentResolver().update(result,complete,null,null);
            return result;
        }catch(Exception e){
            getContentResolver().delete(result,null,null);
            throw e;
        }
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){
        if(busy.get())LocalImageEngine.cancel();
        worker.shutdown();super.onDestroy();
    }
}
