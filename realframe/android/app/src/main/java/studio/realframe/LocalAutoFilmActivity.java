package studio.realframe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.view.View;

import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A real local AI prompt -> keyframe -> animated MP4 pipeline.
 * stable-diffusion.cpp generates REAL AI stills for every distinct scene;
 * Android creates conventional camera-pan/fade animation between them.
 * Not a true motion-diffusion video model; that stays experimental in Wan Lab.
 * Entire pipeline, including image inference and H.264 encoding, is offline.
 */
public final class LocalAutoFilmActivity extends Activity {
    private static final int BG=0xFF0B1324,PANEL=0xFF1A2940,WHITE=0xFFF2F5FF,
        DIM=0xFFB2C2D4,TEAL=0xFF72E1CD,PURPLE=0xFFBBA6FF,FPS=16;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private EditText story,seed,negative;
    private Spinner scenes,seconds,steps,size,continuity,character;
    private final List<JSONObject> cast=new ArrayList<>();
    private TextView status,modelInfo;
    private ImageView preview;
    private Button run;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(15));return d;
    }
    private TextView label(String message,int font,int color,boolean bold){
        TextView t=new TextView(this);t.setText(message);t.setTextSize(font);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void put(LinearLayout p,View child){p.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private EditText entry(LinearLayout p,String title,String value){
        put(p,label(title,13,DIM,true));gap(p,5);
        EditText text=new EditText(this);text.setText(value);text.setTextColor(WHITE);
        text.setTextSize(14);text.setBackground(shape(0xFF293D56));
        text.setPadding(dp(11),dp(9),dp(11),dp(9));put(p,text);gap(p,9);
        return text;
    }
    private Spinner select(LinearLayout p,String title,String[] values){
        put(p,label(title,13,DIM,true));gap(p,5);
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));
        spinner.setBackground(shape(0xFFE8EDF6));put(p,spinner);gap(p,10);
        return spinner;
    }
    private Button action(LinearLayout p,String name,int color,Runnable callback){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);
        b.setTextSize(14);b.setTextColor(WHITE);b.setBackground(shape(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(49));
        lp.topMargin=dp(9);p.addView(b,lp);b.setOnClickListener(v->callback.run());
        return b;
    }
    private File model(){
        return new File(getSharedPreferences("realframe_local_sd",MODE_PRIVATE)
            .getString("modelPath",""));
    }
    private void info(String msg){runOnUiThread(()->status.setText(msg));}
    private void refresh(){
        if(modelInfo!=null)modelInfo.setText(model().isFile() ?
            "✓ SD image model installed on this device":
            "No local SD checkpoint installed. One-Tap AI Setup required.");
        if(run!=null)run.setEnabled(!running.get()&&model().isFile()&&LocalImageEngine.isAvailable());
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=col();root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(14),dp(16),dp(8));setContentView(root);
        TextView title=label("←   AUTO AI STORY MOVIE",22,WHITE,true);
        title.setOnClickListener(v->finish());put(root,title);gap(root,6);
        put(root,label("Real local AI scene keyframes → animated H.264 MP4 • Internet not required",12,TEAL,false));
        gap(root,12);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout panel=col();panel.setPadding(dp(15),dp(16),dp(15),dp(18));
        panel.setBackground(shape(PANEL));scroll.addView(panel);
        put(panel,label("Automatic local scene workflow",18,PURPLE,true));
        gap(panel,7);
        put(panel,label("Enter a story theme. RealFrame uses Stable Diffusion to generate a unique AI picture for each scene and the previous frame as reference. Then it adds camera pan/fades and saves an MP4 completely on your phone.",13,DIM,false));gap(panel,11);
        modelInfo=label("",13,TEAL,true);put(panel,modelInfo);
        action(panel,"✦ One-Tap Install Local AI Model",0xFF30786F,()->
            startActivity(new Intent(this,QuickStartActivity.class)));
        story=entry(panel,"Your story / setting","A fictional adult exploring a sunny Mediterranean town; same wardrobe and face throughout");
        negative=entry(panel,"Negative prompt","watermark, distortion, plastic skin, extra fingers, blurry");
        seed=entry(panel,"Seed (repeatable)","13901");
        cast.clear();cast.add(new JSONObject());cast.addAll(new StudioStore(this).characters());
        String[] names=new String[cast.size()];names[0]="Custom fictional adult";
        for(int i=1;i<names.length;i++)names[i]=cast.get(i).optString("name","Character "+i);
        character=select(panel,"Character Vault preset",names);
        scenes=select(panel,"Number of AI-generated scenes",new String[]{"2","4","6","8"});
        seconds=select(panel,"Animated seconds per AI scene",new String[]{"1","2","3"});
        steps=select(panel,"SD sampling steps (each keyframe)",new String[]{"4","8","12"});
        size=select(panel,"Movie size (CPU)",new String[]{"256 × 256","384 × 384"});
        continuity=select(panel,"Keep previous AI scene reference",new String[]{
            "0.35 • preserve previous strongly",
            "0.50 • moderate visual change",
            "0.70 • more creative change"});
        run=action(panel,"🎬 GENERATE LOCAL AI STORY MP4",0xFF6351B4,this::produce);
        action(panel,"⏹ Cancel current local story",0xFF694556,()->{
            LocalImageEngine.cancel();info("Cancellation requested; model loading may continue briefly.");
        });
        gap(panel,14);
        status=label("Select a local model and create 2 AI scenes to begin.",13,DIM,false);
        put(panel,status);gap(panel,12);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(shape(0xFF101D30));
        panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(235)));gap(panel,12);
        action(panel,"◈ Reference Photo Studio",0xFF4A5377,()->
            startActivity(new Intent(this,ReferenceStudioActivity.class)));
        action(panel,"▤ Join additional MP4 clips for longer films",0xFF4A5377,()->{
            Intent i=new Intent(this,MainActivity.class);
            i.putExtra("open_tab","Clips");startActivity(i);
        });
        gap(panel,10);
        put(panel,label("IMPORTANT: Each scene is a genuinely AI-generated STILL keyframe, not a sequence of AI-predicted moving frames. Cinematic movements are camera pan/zoom + fades. Real motion diffusion remains experimental in the separate Wan Video Lab. On phone CPU, multiple AI scenes may take a long time, and Gallery MP4 output is unverified on your exact Samsung.",12,DIM,false));
        refresh();
    }
    private String[] prompts(int count){
        String theme=story.getText().toString().trim();
        if(theme.isEmpty()||theme.length()>1500)throw new IllegalArgumentException("Story must contain 1–1500 characters");
        JSONObject preset=cast.get(character.getSelectedItemPosition());
        String identity=preset.length()==0?
            "Same fictional adult, natural skin texture, consistent face, hair, and clothing":
            "Same fictional adult, "+preset.optString("appearance")+"; outfit: "+preset.optString("wardrobe");
        String[] beats={
            "Wide establishing scene, character visible in setting",
            "Medium shot, character turns toward the camera, subtle expression",
            "Cinematic closer view, maintain face, skin detail and clothing",
            "Walking through the environment, believable scene continuity",
            "Over-the-shoulder perspective while exploring",
            "Natural close-up, consistent appearance and lighting",
            "Dramatic wider scene with visible environment",
            "Final cinematic portrait, closing frame"
        };
        String[] all=new String[count];
        for(int i=0;i<count;i++){
            all[i]="Photorealistic documentary cinematography of "+identity+
                ". Story setting: "+theme+". Scene "+(i+1)+" of "+count+
                ". "+beats[i]+". Realistic anatomy, exact same outfit and hairstyle, stable identity.";
        }
        return all;
    }
    private void appendAnimated(LocalVideoEncoder encoder, int[] argb,int w,int h,
                                int seconds,int scene,int totalScenes)throws Exception{
        Bitmap input=Bitmap.createBitmap(argb,w,h,Bitmap.Config.ARGB_8888);
        Bitmap canvasImage=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(canvasImage);
            int[] pixels=new int[w*h];
            int frameCount=seconds*FPS;
            Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
            for(int f=0;f<frameCount;f++){
                float progress=frameCount<=1?0:(float)f/(frameCount-1);
                float zoom=1.0f+0.10f*progress;
                int cropped=(int)(w/zoom);
                int pan=scene%2==0?(int)(progress*(w-cropped)):(int)((1-progress)*(w-cropped));
                Rect src=new Rect(pan,(h-cropped)/2,pan+cropped,(h+cropped)/2);
                canvas.drawColor(Color.BLACK);
                int fade=Math.min(4,frameCount/4);
                float opacity=1f;
                if(f<fade)opacity=(float)(f+1)/fade;
                if(f>=frameCount-fade)opacity=Math.min(opacity,(float)(frameCount-f)/fade);
                paint.setAlpha(Math.max(0,Math.min(255,(int)(255*opacity))));
                canvas.drawBitmap(input,src,new Rect(0,0,w,h),paint);
                canvasImage.getPixels(pixels,0,w,0,0,w,h);
                encoder.append(pixels);
            }
        }finally{input.recycle();canvasImage.recycle();}
    }
    private void produce(){
        if(!running.compareAndSet(false,true))return;
        final String[] scenesText;
        final long userSeed;
        try{
            scenesText=prompts(Integer.parseInt(scenes.getSelectedItem().toString()));
            userSeed=Long.parseLong(seed.getText().toString().trim());
            if(userSeed<1||userSeed>2147483647L)throw new IllegalArgumentException("Seed outside range");
        }catch(Exception error){running.set(false);refresh();toast(error.getMessage());return;}
        final int secondsPer=1+seconds.getSelectedItemPosition();
        final int countSteps=Integer.parseInt(steps.getSelectedItem().toString());
        final int w=size.getSelectedItemPosition()==0?256:384;
        final float[] strengths={0.35f,0.5f,0.7f};
        final float strength=strengths[continuity.getSelectedItemPosition()];
        final String modelPath=model().getAbsolutePath();
        final String bad=negative.getText().toString();
        refresh();info("Loading SD model locally. Scene 1/"+scenesText.length+"...");
        worker.execute(()->{
            final LocalVideoEncoder[] encoder={null};
            try{
                int generated=LocalImageEngine.generateStoryboard(modelPath,scenesText,bad,
                    w,w,countSteps,userSeed,4,strength,(index,pixels)->{
                        try{
                            if(encoder[0]==null)encoder[0]=new LocalVideoEncoder(getApplicationContext(),w,w,FPS);
                            appendAnimated(encoder[0],pixels,w,w,secondsPer,index,scenesText.length);
                            Bitmap frame=Bitmap.createBitmap(pixels,w,w,Bitmap.Config.ARGB_8888);
                            runOnUiThread(()->{
                                preview.setImageBitmap(frame);
                                status.setText("AI keyframe "+(index+1)+"/"+scenesText.length+
                                    " generated locally and animated into MP4");
                            });
                        }catch(Exception e){
                            throw new IllegalStateException("Could not encode scene "+(index+1)+": "+e.getMessage(),e);
                        }
                    });
                if(generated<scenesText.length||encoder[0]==null)
                    throw new IllegalStateException("AI storyboard incomplete or cancelled");
                Uri output=encoder[0].finish();
                info("✓ AI story keyframes generated and locally animated: "+
                    (generated*secondsPer)+"s MP4 saved in Gallery → Movies/RealFrameStudio.");
            }catch(Throwable error){
                info("Local AI story movie failed: "+error.getMessage()+
                    " (No fake video, incomplete MP4 removed).");
            }finally{
                if(encoder[0]!=null)encoder[0].close();
                running.set(false);runOnUiThread(this::refresh);
            }
        });
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){
        if(running.get())LocalImageEngine.cancel();
        worker.shutdown();super.onDestroy();
    }
}
