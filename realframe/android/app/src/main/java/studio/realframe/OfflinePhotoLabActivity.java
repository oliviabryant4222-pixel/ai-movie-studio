package studio.realframe;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline, real on-device photo editing and MediaStore JPG export. Not an AI generator. */
public final class OfflinePhotoLabActivity extends Activity {
    private static final int PICK_PHOTO=5533;
    private static final int BG=0xFF0B1120, SURFACE=0xFF192538, WHITE=0xFFF5F6FA,
            DIM=0xFFA1B0C5, AQUA=0xFF6BE5D4;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private Bitmap original;
    private ImageView preview;
    private TextView status;
    private SeekBar brightness,contrast,saturation;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void msg(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private TextView label(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(null,1);return t;
    }
    private GradientDrawable rounded(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void slider(LinearLayout parent,String name,int initial,int max,int which){
        TextView title=label(name,13,DIM,true);title.setPadding(0,dp(13),0,dp(4));add(parent,title);
        SeekBar seek=new SeekBar(this);seek.setMax(max);seek.setProgress(initial);
        if(android.os.Build.VERSION.SDK_INT>=21){
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(AQUA));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(AQUA));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){updatePreview();}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        add(parent,seek);
        if(which==0)brightness=seek;else if(which==1)contrast=seek;else saturation=seek;
    }
    private Button action(String s,int color,LinearLayout parent,Runnable work){
        Button b=new Button(this);
        b.setText(s);b.setAllCaps(false);b.setTextColor(BG);b.setBackground(rounded(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);
        lp.rightMargin=dp(8);parent.addView(b,lp);
        b.setOnClickListener(v->work.run());return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout outer=column();outer.setBackgroundColor(BG);outer.setPadding(dp(18),dp(14),dp(18),dp(10));
        setContentView(outer);
        TextView title=label("←   Offline Photo Lab",23,WHITE,true);title.setPadding(0,dp(4),0,dp(10));title.setOnClickListener(v->finish());
        add(outer,title);
        TextView subtitle=label("Enhance your photos privately, without internet or a model download.",13,DIM,false);
        add(outer,subtitle);
        ScrollView sc=new ScrollView(this);
        outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout inside=column();inside.setPadding(0,dp(16),0,0);sc.addView(inside);
        preview=new ImageView(this);
        preview.setBackground(rounded(SURFACE));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setContentDescription("Selected photo with live color adjustments");
        inside.addView(preview,new LinearLayout.LayoutParams(-1,dp(325)));
        TextView tip=label("Choose a photo to preview it here",13,DIM,false);
        tip.setGravity(Gravity.CENTER);tip.setPadding(0,dp(13),0,dp(12));add(inside,tip);
        slider(inside,"Brightness",100,200,0);
        slider(inside,"Contrast",100,200,1);
        slider(inside,"Saturation",100,200,2);
        status=label("No image loaded",12,DIM,false);status.setPadding(0,dp(13),0,dp(7));add(inside,status);
        LinearLayout actions=new LinearLayout(this);
        actions.setPadding(0,dp(12),0,dp(4));
        add(outer,actions);
        action("Choose photo",0xFFB6A1FF,actions,this::pick);
        action("Save JPG",AQUA,actions,this::saveImage);
        TextView reset=label("Reset adjustments",13,AQUA,true);
        reset.setGravity(Gravity.CENTER);reset.setPadding(0,dp(10),0,dp(8));
        reset.setOnClickListener(v->{brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);});
        add(outer,reset);
    }
    private void pick(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_PHOTO);
    }
    @Override protected void onActivityResult(int req,int code,Intent data){
        super.onActivityResult(req,code,data);
        if(req!=PICK_PHOTO || code!=RESULT_OK || data==null || data.getData()==null)return;
        Uri image=data.getData();status.setText("Loading photo…");
        io.execute(()->{
            try{
                ImageDecoder.Source src=ImageDecoder.createSource(getContentResolver(),image);
                Bitmap bmp=ImageDecoder.decodeBitmap(src,(decoder,info,source)->{
                    int w=info.getSize().getWidth(), h=info.getSize().getHeight();
                    float scale=Math.min(1f,2048f/Math.max(w,h));
                    int newW=Math.max(1,(int)(w*scale));
                    int newH=Math.max(1,(int)(h*scale));
                    decoder.setTargetSize(newW,newH);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                runOnUiThread(()->{
                    original=bmp;
                    preview.setImageBitmap(bmp);
                    status.setText("Ready • "+bmp.getWidth()+" × "+bmp.getHeight()+" • all edits local");
                    updatePreview();
                });
            }catch(Exception e){runOnUiThread(()->{status.setText("Could not open photo");msg(e.toString());});}
        });
    }
    private ColorMatrix makeMatrix(){
        float contrastVal=contrast.getProgress()/100f;
        float brighten=(brightness.getProgress()-100)*0.7f;
        float sat=saturation.getProgress()/100f;
        ColorMatrix m=new ColorMatrix(new float[]{
            contrastVal,0,0,0,brighten,
            0,contrastVal,0,0,brighten,
            0,0,contrastVal,0,brighten,
            0,0,0,1,0
        });
        ColorMatrix s=new ColorMatrix();s.setSaturation(sat);m.postConcat(s);
        return m;
    }
    private void updatePreview(){
        if(preview==null || original==null || brightness==null || contrast==null || saturation==null)return;
        preview.setColorFilter(new ColorMatrixColorFilter(makeMatrix()));
    }
    private void saveImage(){
        if(original==null){msg("Choose a photo first.");return;}
        Bitmap bitmap=original;
        ColorMatrix matrix=makeMatrix();
        status.setText("Saving photo locally…");
        io.execute(()->{
            Uri uri=null;
            try{
                Bitmap edited=Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas c=new Canvas(edited);
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                paint.setColorFilter(new ColorMatrixColorFilter(matrix));
                c.drawBitmap(bitmap,0,0,paint);
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,"realframe_edit_"+System.currentTimeMillis()+".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RealFrameStudio");
                values.put(MediaStore.Images.Media.IS_PENDING,1);
                uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(uri==null)throw new Exception("Cannot create gallery image");
                try(OutputStream output=getContentResolver().openOutputStream(uri,"w")){
                    if(output==null || !edited.compress(Bitmap.CompressFormat.JPEG,94,output))
                        throw new Exception("JPEG writer failed");
                }
                ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);
                getContentResolver().update(uri,done,null,null);
                runOnUiThread(()->{status.setText("Saved to Pictures / RealFrameStudio");msg("Exported! Photo is in your gallery.");});
                edited.recycle();
            }catch(Exception error){
                if(uri!=null)getContentResolver().delete(uri,null,null);
                runOnUiThread(()->{status.setText("Export failed");msg(error.getMessage());});
            }
        });
    }
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
