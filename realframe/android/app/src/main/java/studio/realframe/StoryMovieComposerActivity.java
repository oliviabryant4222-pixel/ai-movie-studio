package studio.realframe;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fully local Photo-Motion Movie composer. Turns actual selected image
 * assets (e.g. RealFrame's AI-generated images) into a crossfaded/panned
 * H.264 movie with Android hardware MediaCodec and MediaMuxer.
 *
 * NO false claim of video diffusion: motion is 2D cinematic image animation.
 * This provides a genuinely functioning offline video workflow independent
 * of the oversized/experimental Wan T2V backend.
 */
public final class StoryMovieComposerActivity extends Activity {
    private static final int PICK=7931,BG=0xFF0C1422,CARD=0xFF1B2940,WHITE=0xFFF3F7FF,
        MUTED=0xFFAEBFD4,TEAL=0xFF6DE4D2,PURPLE=0xFFAC9AF5;
    private static final int FPS=16;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private final List<Uri> selected=new ArrayList<>();
    private TextView status,photos;
    private Spinner duration,resolution;
    private Button make,pick;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(16));return d;
    }
    private LinearLayout column(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);return p;}
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void put(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void gap(LinearLayout p,int size){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private Button action(LinearLayout p,String title,int color,Runnable task){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(15);
        b.setTextColor(WHITE);b.setBackground(shape(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));
        lp.topMargin=dp(9);p.addView(b,lp);b.setOnClickListener(v->task.run());return b;
    }
    private Spinner choose(LinearLayout p,String title,String[] choices){
        put(p,label(title,13,MUTED,true));gap(p,5);
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));
        spinner.setBackground(shape(0xFFE1EAF4));put(p,spinner);
        return spinner;
    }
    private void message(String msg){runOnUiThread(()->{if(status!=null)status.setText(msg);});}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    private void refresh(){
        if(photos!=null)photos.setText(selected.size()==0 ?
            "No photos chosen. Pick 2–16 images in the desired scene order." :
            selected.size()+" image(s) selected. MP4 length depends on seconds/image.");
        if(make!=null)make.setEnabled(!busy.get()&&selected.size()>=2);
        if(pick!=null)pick.setEnabled(!busy.get());
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setPadding(dp(17),dp(14),dp(17),dp(9));setContentView(root);
        TextView title=label("←   LOCAL MOVIE COMPOSER",23,WHITE,true);
        title.setOnClickListener(v->finish());put(root,title);gap(root,6);
        put(root,label("Offline crossfades + cinematic pan/zoom → real MP4 • no cloud",13,TEAL,false));
        gap(root,12);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout p=column();p.setBackground(shape(CARD));
        p.setPadding(dp(15),dp(16),dp(15),dp(16));scroll.addView(p);
        put(p,label("Use RealFrame's AI pictures to make a film",18,PURPLE,true));
        gap(p,10);
        put(p,label("Select 2–16 existing photos (including AI images from Gallery). RealFrame will animate each one with subtle camera pan/zoom, crossfade between scenes, and export an actual H.264 MP4 entirely on your phone. No additional app or model download is required for this editing tool.",13,MUTED,false));
        pick=action(p,"▣ Select 2–16 images from Gallery",0xFF494385,this::choosePhotos);
        gap(p,8);
        photos=label("",13,WHITE,false);put(p,photos);gap(p,13);
        duration=choose(p,"Duration per image",new String[]{"2 seconds","3 seconds","4 seconds","5 seconds"});
        gap(p,12);
        resolution=choose(p,"Movie resolution",new String[]{"256 × 256 • Fast","384 × 384 • Higher quality"});
        gap(p,12);
        make=action(p,"🎬 CREATE OFFLINE MP4 NOW",0xFF2C7D75,this::render);
        action(p,"⏹ Cancel movie encoding",0xFF69455B,()->{
            cancelled.set(true);message("Cancel requested, stopping after current encoded frame.");
        });
        gap(p,15);
        status=label("Choose images, then create your offline movie.",13,MUTED,false);
        put(p,status);gap(p,13);
        action(p,"✦ Create more AI pictures",0xFF4B4580,()->{
            startActivity(new Intent(this,LocalImageActivity.class));
        });
        action(p,"◈ Reference-image consistency studio",0xFF4B4580,()->{
            startActivity(new Intent(this,ReferenceStudioActivity.class));
        });
        gap(p,9);
        put(p,label("Important: This is REAL offline cinematic photo animation, NOT text-to-video diffusion or real movement of a character's limbs. For AI motion use experimental Wan Local Video or a hosted online model. Your photos remain on your device.",12,MUTED,false));
        refresh();
    }
    private void choosePhotos(){
        if(busy.get())return;
        Intent select=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        select.addCategory(Intent.CATEGORY_OPENABLE);
        select.setType("image/*");select.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(select,PICK);
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code!=PICK||result!=RESULT_OK||data==null)return;
        List<Uri> refs=new ArrayList<>();
        ClipData clip=data.getClipData();
        if(clip!=null){
            int count=Math.min(16,clip.getItemCount());
            for(int i=0;i<count;i++)refs.add(clip.getItemAt(i).getUri());
        }else if(data.getData()!=null)refs.add(data.getData());
        if(refs.size()<2){toast("Please choose at least two pictures together.");return;}
        selected.clear();
        for(Uri uri:refs){
            if(uri==null)continue;
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(Exception ignored){}
            selected.add(uri);
        }
        refresh();message(selected.size()+" scene images selected. Ready to encode.");
    }
    private Bitmap decode(Uri uri,int size)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalArgumentException("Image cannot be opened");
            BitmapFactory.decodeStream(in,null,bounds);
        }
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("Invalid photo");
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=Math.max(1,(int)Math.ceil(
            Math.max(bounds.outWidth,bounds.outHeight)/1024.0));
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalArgumentException("Photo is not available");
            Bitmap bmp=BitmapFactory.decodeStream(in,null,opts);
            if(bmp==null)throw new IllegalArgumentException("Unsupported photo format");
            return bmp;
        }
    }
    private void paintPhoto(Canvas canvas,Bitmap picture,int size,float progress,float alpha,int sceneIndex){
        if(picture==null||alpha<=0)return;
        float zoom=1.02f+0.12f*progress;
        int sw=picture.getWidth(),sh=picture.getHeight();
        double imageRatio=(double)sw/sh;
        double target=1.0;
        float cropW=sw,cropH=sh;
        if(imageRatio>target)cropW=(float)(sh*target);
        else cropH=(float)(sw/target);
        cropW/=zoom;cropH/=zoom;
        float pan=0.07f*(sceneIndex%2==0?progress:1f-progress);
        float x=(sw-cropW)*0.5f+(sceneIndex%2==0?pan:-pan)*sw*0.5f;
        float y=(sh-cropH)*0.5f;
        x=Math.max(0,Math.min(sw-cropW,x));y=Math.max(0,Math.min(sh-cropH,y));
        Rect from=new Rect(Math.round(x),Math.round(y),Math.round(x+cropW),Math.round(y+cropH));
        Paint painter=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
        painter.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));
        canvas.drawBitmap(picture,from,new Rect(0,0,size,size),painter);
    }
    private void render(){
        if(!busy.compareAndSet(false,true))return;
        List<Uri> shots=new ArrayList<>(selected);
        if(shots.size()<2){busy.set(false);refresh();return;}
        cancelled.set(false);
        final int seconds=2+duration.getSelectedItemPosition();
        final int size=resolution.getSelectedItemPosition()==0?256:384;
        final int sceneFrames=seconds*FPS;
        final int fadeFrames=Math.min(12,sceneFrames/3);
        refresh();message("Starting offline H.264 movie encoder...");
        worker.execute(()->{
            LocalVideoEncoder encoder=null;
            Bitmap current=null,next=null,canvasBitmap=null;
            try{
                encoder=new LocalVideoEncoder(getApplicationContext(),size,size,FPS);
                canvasBitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(canvasBitmap);
                int[] pixels=new int[size*size];
                current=decode(shots.get(0),size);
                next=decode(shots.get(1),size);
                int total=shots.size()*sceneFrames;
                int count=0;
                for(int scene=0;scene<shots.size();scene++){
                    if(cancelled.get())throw new InterruptedException("Movie creation cancelled");
                    for(int frame=0;frame<sceneFrames;frame++){
                        if(cancelled.get())throw new InterruptedException("Movie creation cancelled");
                        float t=sceneFrames<=1?0:(float)frame/(sceneFrames-1);
                        canvas.drawColor(Color.BLACK);
                        paintPhoto(canvas,current,size,t,1f,scene);
                        if(scene<shots.size()-1 && frame>=sceneFrames-fadeFrames){
                            float blend=(float)(frame-(sceneFrames-fadeFrames)+1)/fadeFrames;
                            paintPhoto(canvas,next,size,0f,blend,scene+1);
                        }
                        canvasBitmap.getPixels(pixels,0,size,0,0,size,size);
                        encoder.append(pixels);
                        count++;
                        if(count%8==0){
                            final int progress=count;
                            message("Encoding on device: "+progress+"/"+total+" frames • "+(100*progress/total)+"%");
                        }
                    }
                    current.recycle();
                    current=next;next=null;
                    if(scene+2<shots.size())next=decode(shots.get(scene+2),size);
                }
                Uri movie=encoder.finish();
                message("✓ OFFLINE movie exported: "+shots.size()+" scenes, "+(total/FPS)+" seconds. Check Gallery → Movies/RealFrameStudio.");
            }catch(Throwable error){
                message("Movie encoding stopped: "+error.getMessage()+". No incomplete MP4 will remain in Gallery.");
            }finally{
                if(encoder!=null)encoder.close();
                if(current!=null&&!current.isRecycled())current.recycle();
                if(next!=null&&!next.isRecycled())next.recycle();
                if(canvasBitmap!=null)canvasBitmap.recycle();
                busy.set(false);runOnUiThread(this::refresh);
            }
        });
    }
    @Override protected void onDestroy(){
        cancelled.set(true);worker.shutdown();super.onDestroy();
    }
}
