package studio.realframe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.StatFs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-tap, first-use, HTTPS model installer. The app downloads the licensed
 * SD1.5 Q4 GGUF MODEL WEIGHTS (about 1.75 GB) on the user's behalf and stores
 * them in its private directory. User does not manually download files,
 * manage folders or configure API keys.
 *
 * On inference, no HTTP/cloud is used: the existing MIT stable-diffusion.cpp
 * engine samples locally. An initial download of MODEL WEIGHTS is unavoidable.
 *
 * Model: gpustack/stable-diffusion-v1-5-GGUF, CreativeML Open RAIL-M
 * Downloaded component: stable-diffusion-v1-5-Q4_0.gguf
 * SHA256 (Hugging Face Xet pointer): c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b
 */
public final class AutoModelDownloadService extends Service {
    public static final String START="studio.realframe.AUTO_INSTALL_AI";
    public static final String CANCEL="studio.realframe.CANCEL_AI_INSTALL";
    public static final String MODEL_NAME="SD1.5 Q4 (1.75 GB)";
    public static final String LICENSE="CreativeML Open RAIL-M";
    public static final String MODEL_PAGE="https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF";
    public static final String ENGINE_PAGE="https://github.com/leejet/stable-diffusion.cpp";
    private static final String LINK="https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF/resolve/main/stable-diffusion-v1-5-Q4_0.gguf?download=true";
    private static final String SHA256="c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b";
    private static final int NOTICE=2070;
    private static final String CHANNEL="realframe_model_install";
    public static volatile String state="not_installed";
    public static volatile String message="One-tap AI model setup available";
    public static volatile long downloaded=0,total=1_750_000_000L;
    private static final AtomicBoolean lock=new AtomicBoolean(false);
    private final AtomicBoolean cancel=new AtomicBoolean(false);
    private volatile HttpURLConnection currentConnection;
    private volatile Thread downloadThread;

    public static boolean isDownloading(){return lock.get();}
    public static File modelFile(android.content.Context context){
        return new File(new File(context.getFilesDir(),"model_cache"),"checkpoint_auto_sd15_q4_0.gguf");
    }
    private File partialFile(){
        return new File(new File(getFilesDir(),"model_cache"),"checkpoint_auto_sd15_q4_0.gguf.part");
    }
    public static boolean installed(android.content.Context context){
        File model=modelFile(context);
        return model.isFile() && model.length()>1_000_000_000L &&
            model.getAbsolutePath().equals(context.getSharedPreferences("realframe_local_sd",MODE_PRIVATE)
                .getString("modelPath",""));
    }
    public static long partialBytes(android.content.Context context){
        File file=new File(new File(context.getFilesDir(),"model_cache"),
                            "checkpoint_auto_sd15_q4_0.gguf.part");
        return file.isFile()?file.length():0;
    }
    @Override public void onCreate(){
        super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"RealFrame local model installer",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("One-time offline AI model setup; Internet needed for model weights only");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    private Notification notification(String title,String sub,int percent){
        Intent open=new Intent(this,QuickStartActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,2070,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(sub)
            .setContentIntent(pending).setOngoing(true);
        if(percent<0)b.setProgress(0,0,true);
        else b.setProgress(100,Math.min(100,Math.max(0,percent)),false);
        return b.build();
    }
    private void update(String newState,String detail){
        state=newState;message=detail;
        int percent=total>0?(int)Math.min(100L,downloaded*100L/total):-1;
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(
            NOTICE,notification("RealFrame local image AI",detail,percent));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(CANCEL.equals(intent.getAction())){
            cancel.set(true);
            HttpURLConnection active=currentConnection;
            if(active!=null)active.disconnect();
            Thread worker=downloadThread;
            if(worker!=null)worker.interrupt();
            if(!lock.get())stopSelf(startId);
            return START_NOT_STICKY;
        }
        if(!START.equals(intent.getAction()))return START_NOT_STICKY;
        if(!lock.compareAndSet(false,true))return START_NOT_STICKY;
        cancel.set(false);
        downloaded=partialBytes(this);
        state="starting";message="Checking space and HTTPS connection";
        try{
            startForeground(NOTICE,notification("RealFrame local image AI",
                "Checking AI model",0),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }catch(Exception error){
            state="failed";message="Cannot start model install notification: "+error.getMessage();
            lock.set(false);stopSelf(startId);return START_NOT_STICKY;
        }
        downloadThread=new Thread(()->{
            try{install();}
            catch(InterruptedException stopped){
                state="paused";
                message="Paused. Tap install again to resume.";
            }catch(Throwable error){
                state=cancel.get()?"paused":"failed";
                message=cancel.get()?"Paused. Tap install to resume.":
                    (error.getMessage()==null?error.toString():error.getMessage());
            }finally{
                HttpURLConnection active=currentConnection;
                if(active!=null)active.disconnect();
                currentConnection=null;downloadThread=null;
                lock.set(false);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            }
        },"RealFrame-Model-Downloader");
        downloadThread.start();
        return START_NOT_STICKY;
    }
    private void install() throws Exception{
        File target=modelFile(this);
        File dir=target.getParentFile();
        if(!dir.exists()&&!dir.mkdirs())
            throw new IllegalStateException("Cannot create private model directory");
        if(installed(this)){
            state="installed";message="Local SD image model already installed ✓";return;
        }
        File part=partialFile();
        long existing=part.isFile()?part.length():0;
        long minimumFree=Math.max(550_000_000L,1_950_000_000L-existing);
        long available=new StatFs(dir.getAbsolutePath()).getAvailableBytes();
        if(available<minimumFree)
            throw new IllegalStateException("Storage low. Free at least "+
                ((minimumFree-available)/1_000_000L+500)+" MB more. Image model is about 1.75 GB.");
        update("connecting","Connecting to Hugging Face over HTTPS");
        HttpURLConnection http=(HttpURLConnection)new URL(LINK).openConnection();
        currentConnection=http;
        http.setInstanceFollowRedirects(true);
        http.setConnectTimeout(25_000);
        http.setReadTimeout(35_000);
        http.setRequestProperty("User-Agent","RealFrame-AI-Studio/2.0 (Android open-source model installer)");
        http.setRequestProperty("Accept-Encoding","identity");
        if(existing>0)http.setRequestProperty("Range","bytes="+existing+"-");
        int response=http.getResponseCode();
        if(!"https".equalsIgnoreCase(http.getURL().getProtocol()))
            throw new SecurityException("Model server redirected away from encrypted HTTPS");
        if(response==416){
            part.delete();
            throw new IllegalStateException("Model download range reset. Tap install again to restart.");
        }
        if(response!=200&&response!=206)
            throw new IllegalStateException("Model host returned HTTP "+response+
                ". Internet, provider access or storage quota may be unavailable.");
        boolean append=response==206&&existing>0;
        if(response==206){
            String range=http.getHeaderField("Content-Range");
            if(range==null||!range.startsWith("bytes "+existing+"-"))
                throw new IllegalStateException("Server returned incorrect resume offset");
        }
        if(!append)existing=0;
        long expected=http.getContentLengthLong();
        if(expected>0)total=existing+expected;
        if(total<1_000_000_000L||total>4_000_000_000L)
            throw new IllegalStateException("Unexpected model file length");
        long done=existing;
        downloaded=done;
        long lastNotice=0;
        long lastSpaceCheck=0;
        try(InputStream input=http.getInputStream();
            FileOutputStream file=new FileOutputStream(part,append)){
            byte[] bytes=new byte[1024*1024];
            for(int n;(n=input.read(bytes))!=-1;){
                if(cancel.get()||Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Download paused");
                file.write(bytes,0,n);
                done+=n;
                downloaded=done;
                if(done-lastSpaceCheck>=32L*1024*1024){
                    lastSpaceCheck=done;
                    if(new StatFs(dir.getAbsolutePath()).getAvailableBytes()<225_000_000L)
                        throw new IllegalStateException("Not enough free storage; partially downloaded file saved for resume");
                }
                if(System.currentTimeMillis()-lastNotice>1400){
                    lastNotice=System.currentTimeMillis();
                    update("downloading","Installing AI model: "+(done/1_000_000L)+
                        "/"+(total/1_000_000L)+" MB");
                }
            }
            file.getFD().sync();
        }
        if(cancel.get())throw new InterruptedException("Download paused");
        // Some HTTPS/Xet CDN responses use chunked transfer with unknown
        // Content-Length. In that case the expected SHA-256 (below), not the
        // UI's approximate 1.75 GB counter, is the authoritative integrity test.
        if(expected>0&&part.length()!=existing+expected)
            throw new IllegalStateException("Model file is incomplete; tap install to resume");
        update("verifying","Verifying model SHA-256. Keep RealFrame open.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(part)){
            byte[] buffer=new byte[1024*1024];
            for(int n;(n=in.read(buffer))!=-1;){
                if(cancel.get())throw new InterruptedException("Verification paused");
                digest.update(buffer,0,n);
            }
        }
        StringBuilder actual=new StringBuilder(64);
        for(byte b:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!SHA256.equalsIgnoreCase(actual.toString())){
            part.delete();
            throw new SecurityException("Model SHA-256 mismatch. Corrupt download deleted; try a trusted connection.");
        }
        if(target.exists()&&!target.delete())
            throw new IllegalStateException("Cannot replace older auto-downloaded model");
        if(!part.renameTo(target))
            throw new IllegalStateException("Model copy downloaded but could not be activated");
        // Check .gguf after rename: .gguf.part is intentionally not a valid
        // extension for the generic checkpoint inspector.
        ModelFileInspector.Result header=ModelFileInspector.inspect(target);
        if(!header.plausible){
            target.delete();
            throw new IllegalStateException("Model container header rejected: "+header.detail);
        }
        String previous=getSharedPreferences("realframe_local_sd",MODE_PRIVATE).getString("modelPath","");
        getSharedPreferences("realframe_local_sd",MODE_PRIVATE)
            .edit().putString("modelPath",target.getAbsolutePath()).commit();
        if(!previous.isEmpty()&&!previous.equals(target.getAbsolutePath())){
            File old=new File(previous);
            if(old.getParentFile()!=null&&old.getParentFile().equals(dir)
               &&old.getName().startsWith("checkpoint_"))
                old.delete();
        }
        state="installed";
        message="✓ Local AI image model installed. Generate on this phone with Internet ON or OFF.";
    }
    @Override public void onTimeout(int startId,int foregroundServiceType){
        cancel.set(true);
        HttpURLConnection active=currentConnection;
        if(active!=null)active.disconnect();
        state="paused";message="Android foreground-service limit reached. Resume from Quick Start.";
        stopSelf(startId);
    }
    @Override public void onDestroy(){
        cancel.set(true);
        HttpURLConnection active=currentConnection;
        if(active!=null)active.disconnect();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
