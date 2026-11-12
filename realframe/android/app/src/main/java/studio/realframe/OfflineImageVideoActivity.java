package studio.realframe;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Generate a REAL silent MP4 locally from an existing photo. This is NOT generative AI. */
public final class OfflineImageVideoActivity extends Activity {
    private static final int PICK=6123, BG=0xFF0A1020, FORE=0xFFF6F7FC, SUB=0xFF9DAAC0;
    private Uri selected;
    private ImageView preview;
    private TextView status, durationLabel;
    private SeekBar duration;
    private Button render;
    private Transformer transformer;
    private File tempFile;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private boolean exporting;
    private int dp(int i){return Math.round(i*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color,boolean strong){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(strong)t.setTypeface(null,1);return t;
    }
    private GradientDrawable shape(int color){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(14));return g;
    }
    private Button action(String s,LinearLayout root,int color,Runnable run) {
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(BG);
        b.setBackground(shape(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.topMargin=dp(12);root.addView(b,lp);
        b.setOnClickListener(v->run.run());return b;
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);root.setPadding(dp(18),dp(24),dp(18),dp(14));
        setContentView(root);
        TextView heading=text("←   Offline Photo → MP4",22,FORE,true);
        heading.setOnClickListener(v->finish());root.addView(heading);
        TextView info=text("Convert a still image into a silent video clip entirely on your phone. No AI motion generation or internet.",13,SUB,false);
        info.setPadding(0,dp(10),0,dp(19));root.addView(info);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(shape(0xFF18253A));
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        durationLabel=text("Video duration: 5 seconds",15,FORE,true);durationLabel.setPadding(0,dp(16),0,dp(5));root.addView(durationLabel);
        duration=new SeekBar(this);duration.setMax(12);duration.setProgress(3);
        duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){durationLabel.setText("Video duration: "+(p+2)+" seconds");}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });
        root.addView(duration);
        status=text("Choose an image to begin.",12,SUB,false);status.setPadding(0,dp(7),0,dp(3));root.addView(status);
        action("Choose image",root,0xFFBBA5FF,()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent,PICK);
        });
        render=action("Create MP4 offline",root,0xFF70E5D6,this::startExport);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
        selected=data.getData();preview.setImageURI(selected);
        status.setText("Image selected. Ready to export.");
    }
    private void startExport(){
        if(selected==null){Toast.makeText(this,"Choose a photo first",Toast.LENGTH_SHORT).show();return;}
        if(exporting)return;
        exporting=true;render.setEnabled(false);
        int seconds=duration.getProgress()+2;
        try{
            tempFile=new File(getCacheDir(),"still_"+System.currentTimeMillis()+".mp4");
            MediaItem image=new MediaItem.Builder()
                .setUri(selected)
                .setImageDurationMs(seconds*1000L)
                .build();
            EditedMediaItem item=new EditedMediaItem.Builder(image)
                .setFrameRate(24)
                .build();
            transformer=new Transformer.Builder(this)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition composition,ExportResult result){
                        status.setText("Saving MP4 to gallery…");
                        File file=tempFile;
                        io.execute(()->publish(file));
                    }
                    @Override public void onError(Composition composition,ExportResult result,ExportException ex){
                        status.setText("Export failed: "+ex.getMessage());
                        exporting=false;render.setEnabled(true);
                        if(tempFile!=null)tempFile.delete();
                    }
                })
                .build();
            status.setText("Encoding "+seconds+"-second silent MP4 locally…");
            transformer.start(item,tempFile.getAbsolutePath());
        }catch(Exception ex){
            status.setText("Could not start export: "+ex.getMessage());
            exporting=false;render.setEnabled(true);
        }
    }
    private void publish(File file) {
        Uri output=null;
        try{
            if(file==null||!file.isFile()||file.length()==0)throw new Exception("No rendered file");
            ContentValues v=new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME,"realframe_still_"+System.currentTimeMillis()+".mp4");
            v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/RealFrameStudio");
            v.put(MediaStore.Video.Media.IS_PENDING,1);
            output=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);
            if(output==null)throw new Exception("Gallery writer is unavailable");
            try(FileInputStream in=new FileInputStream(file);
                OutputStream out=getContentResolver().openOutputStream(output,"w")){
                if(out==null)throw new Exception("Gallery output stream failed");
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            }
            ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);
            getContentResolver().update(output,done,null,null);
            new StudioStore(this).addClip(output.toString(),"Offline image clip");
            runOnUiThread(()->{
                status.setText("Done! Saved to Movies / RealFrameStudio and added to Clip Timeline.");
                exporting=false;render.setEnabled(true);
                Toast.makeText(this,"MP4 exported and added to timeline!",Toast.LENGTH_LONG).show();
            });
        }catch(Exception e){
            if(output!=null)getContentResolver().delete(output,null,null);
            runOnUiThread(()->{status.setText("Could not save: "+e.getMessage());exporting=false;render.setEnabled(true);});
        }finally{
            if(file!=null)file.delete();
        }
    }
    @Override protected void onDestroy(){
        if(transformer!=null&&exporting) transformer.cancel();
        io.shutdown();super.onDestroy();
    }
}
