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
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional one-tap WAN T2V model pack installer. Downloads licensed weights
 * from Hugging Face into Android private storage, without separate apps or
 * asking the user to handle model files. Installation != successful inference.
 *
 * Total compressed weights: approx. 4.73 GB (Q4 diffusion + Q4 UMT5 + VAE).
 * Phone CPU memory/thermal budget can prevent Wan video even after install.
 * Source and hash are pinned to publishers' Hugging Face Xet SHA-256 pointers.
 */
public final class AutoVideoModelDownloadService extends Service {
    public static final String START="studio.realframe.START_AUTO_WAN";
    public static final String CANCEL="studio.realframe.CANCEL_AUTO_WAN";
    public static final String LICENSE="Apache-2.0 (original Wan2.1, UMT5 and companion files)";
    public static volatile String state="not_installed";
    public static volatile String message="Wan Q4 pack is not installed";
    public static volatile long downloaded=0,total=0;
    private static final String PREFS="realframe_wan_local_v18";
    private static final String CHANNEL="realframe_wan_installer";
    private static final int NOTICE=2188;
    private static final AtomicBoolean lock=new AtomicBoolean(false);
    private final AtomicBoolean cancel=new AtomicBoolean(false);
    private volatile HttpURLConnection connection;
    private volatile Thread thread;

    static final class Model {
        final String role,name,url,sha;
        final long minSize;
        Model(String role,String name,String url,String sha,long minSize) {
            this.role=role;this.name=name;this.url=url;this.sha=sha;this.minSize=minSize;
        }
    }
    // SHA256 values read from publicly listed Xet pointers for these exact files.
    private static final Model[] MODELS={
        new Model("diffusion","Wan2.1-T2V-1.3B-Q4_K_S.gguf",
            "https://huggingface.co/off-grid-ai/Wan2.1-T2V-1.3B-GGUF/resolve/main/Wan2.1-T2V-1.3B-Q4_K_S.gguf",
            "65181afff758fba25cf311399ccb0638a746f8e0e6533e07a84a5a23e0c12318",750_000_000L),
        new Model("t5","umt5-xxl-encoder-Q4_K_M.gguf",
            "https://huggingface.co/city96/umt5-xxl-encoder-gguf/resolve/main/umt5-xxl-encoder-Q4_K_M.gguf",
            "17cf97a5bbbc60a646d6105b832b6f657ce904a8a1ad970e4b59df0c67584a40",3_500_000_000L),
        new Model("vae","wan_2.1_vae.safetensors",
            "https://huggingface.co/Comfy-Org/Wan-2.1/resolve/main/split_files/vae/wan_2.1_vae.safetensors",
            "2fc39d31359a4b0a64f55876d8ff7fa8d780956ae2cb13463b0223e15148976b",200_000_000L)
    };
    private File folder(){return new File(getFilesDir(),"wan_video_models");}
    private File target(Model m){return new File(folder(),m.role+"_auto_"+m.name);}
    private File part(Model m){return new File(folder(),m.role+"_auto_"+m.name+".part");}
    public static boolean isDownloading(){return lock.get();}
    public static boolean installed(android.content.Context context) {
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,MODE_PRIVATE);
        for(Model m:MODELS) {
            String path=prefs.getString(m.role+"Path","");
            if(path.isEmpty()||!new File(path).isFile()||new File(path).length()<m.minSize)return false;
        }
        return true;
    }
    public static long partialBytes(android.content.Context context){
        File base=new File(context.getFilesDir(),"wan_video_models");
        long n=0;
        for(Model m:MODELS){
            File done=new File(base,m.role+"_auto_"+m.name);
            File partial=new File(base,m.role+"_auto_"+m.name+".part");
            n+=done.isFile()?done.length():(partial.isFile()?partial.length():0);
        }
        return n;
    }
    private Notification notification(String label){
        PendingIntent open=PendingIntent.getActivity(this,NOTICE,
            new Intent(this,LocalVideoActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setContentTitle("RealFrame WAN video model setup")
            .setContentText(label).setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).setContentIntent(open).build();
    }
    private void show(String newState,String detail) {
        state=newState;message=detail;
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notification(detail));
    }
    @Override public void onCreate(){
        super.onCreate();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
            new NotificationChannel(CHANNEL,"RealFrame local video model setup",
                NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null)return START_NOT_STICKY;
        if(CANCEL.equals(intent.getAction())) {
            cancel.set(true);
            HttpURLConnection active=connection;
            if(active!=null)active.disconnect();
            Thread t=thread;if(t!=null)t.interrupt();
            if(!lock.get())stopSelf(startId);
            return START_NOT_STICKY;
        }
        if(!START.equals(intent.getAction()))return START_NOT_STICKY;
        if(!lock.compareAndSet(false,true))return START_NOT_STICKY;
        cancel.set(false);
        try{
            startForeground(NOTICE,notification("Checking local model pack"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }catch(Exception failed){
            state="failed";message="Cannot launch installer: "+failed.getMessage();
            lock.set(false);stopSelf(startId);return START_NOT_STICKY;
        }
        thread=new Thread(()->{
            try{install();}
            catch(InterruptedException interrupted){
                state="paused";message="Paused; tap Install again to resume.";
            }catch(Throwable err){
                state=cancel.get()?"paused":"failed";
                message=cancel.get()?"Paused; tap Install again to resume.":
                    (err.getMessage()==null?err.toString():err.getMessage());
            }finally{
                HttpURLConnection live=connection;if(live!=null)live.disconnect();
                connection=null;thread=null;lock.set(false);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            }
        },"RealFrame-Wan-Installer");
        thread.start();
        return START_NOT_STICKY;
    }
    private void ensureSpace(File directory,long required) {
        long available=new StatFs(directory.getAbsolutePath()).getAvailableBytes();
        if(available<required+350_000_000L)
            throw new IllegalStateException("Free at least "+
                ((required+350_000_000L-available)/1_000_000L+250L)+
                " MB storage, then resume this automatic video model install");
    }
    private static String sha256(File file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[1024*1024];
            int count;
            while((count=in.read(buffer))!=-1) digest.update(buffer,0,count);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private void downloadModel(Model m)throws Exception {
        if(cancel.get())throw new InterruptedException("Download cancelled");
        File to=target(m),partial=part(m);
        if(to.isFile()){
            show("verifying","Verifying existing "+m.role+" model...");
            if(to.length()>=m.minSize&&m.sha.equalsIgnoreCase(sha256(to)))return;
            if(!to.delete())throw new IllegalStateException("Cannot discard corrupted "+m.role+" model");
        }
        long existing=partial.isFile()?partial.length():0;
        long likely=m.minSize+(m.minSize/8L);
        ensureSpace(folder(),Math.max(0L,likely-existing));
        HttpURLConnection http=(HttpURLConnection)new URL(m.url).openConnection();
        connection=http;
        http.setConnectTimeout(25_000);http.setReadTimeout(35_000);
        http.setInstanceFollowRedirects(true);
        http.setRequestProperty("User-Agent","RealFrame-AI-Studio/2.2 model setup");
        http.setRequestProperty("Accept-Encoding","identity");
        if(existing>0)http.setRequestProperty("Range","bytes="+existing+"-");
        show("connecting","Connecting to "+m.role+" weights over HTTPS...");
        int status=http.getResponseCode();
        if(!"https".equalsIgnoreCase(http.getURL().getProtocol()))
            throw new SecurityException("Refusing unencrypted model download");
        if(status==416){
            if(!partial.delete())throw new IllegalStateException("Cannot reset download range");
            throw new IllegalStateException(m.role+" resume offset reset. Tap install again.");
        }
        if(status!=200&&status!=206)
            throw new IllegalStateException(m.role+" model host HTTP "+status+
                "; check Wi-Fi, connection and provider access");
        boolean append=existing>0&&status==206;
        if(append){
            String range=http.getHeaderField("Content-Range");
            if(range==null||!range.startsWith("bytes "+existing+"-"))
                throw new IllegalStateException("Invalid "+m.role+" resume offset");
        }else existing=0;
        long remaining=http.getContentLengthLong();
        if(remaining>0&&existing+remaining<m.minSize)
            throw new IllegalStateException("Host returned a truncated "+m.role+" model");
        if(remaining>0)ensureSpace(folder(),remaining);
        total=remaining>0?existing+remaining:Math.max(m.minSize,existing);
        downloaded=existing;
        show("downloading","Downloading "+m.role+" ("+(existing/1_000_000L)+" MB received)");
        long copied=existing;
        try(InputStream in=http.getInputStream();
            FileOutputStream out=new FileOutputStream(partial,append)) {
            byte[] buffer=new byte[1024*1024];int len;long lastNotice=0,lastSpace=0;
            while((len=in.read(buffer))!=-1) {
                if(cancel.get()||Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Model installation paused");
                out.write(buffer,0,len);
                copied+=len;downloaded=copied;
                if(copied-lastSpace>=32L*1024*1024){
                    lastSpace=copied;
                    ensureSpace(folder(),0);
                }
                if(System.currentTimeMillis()-lastNotice>1700) {
                    lastNotice=System.currentTimeMillis();
                    show("downloading",m.role+": "+(copied/1_000_000L)+" / "+
                        (total/1_000_000L)+" MB");
                }
            }
            out.getFD().sync();
        }finally{http.disconnect();connection=null;}
        if(remaining>0&&partial.length()!=existing+remaining)
            throw new IllegalStateException("Incomplete "+m.role+" model. Tap install to resume.");
        if(partial.length()<m.minSize)
            throw new IllegalStateException(m.role+" model content smaller than expected");
        show("verifying","Checking "+m.role+" model integrity (SHA-256)...");
        if(!m.sha.equalsIgnoreCase(sha256(partial))){
            partial.delete();
            throw new SecurityException(m.role+" SHA-256 mismatch. Corrupt model deleted.");
        }
        if(!partial.renameTo(to))
            throw new IllegalStateException("Cannot finalize verified "+m.role+" weights");
        ModelFileInspector.Result check=ModelFileInspector.inspect(to);
        if(!check.plausible){
            to.delete();
            throw new IllegalStateException(m.role+" model header invalid: "+check.detail);
        }
    }
    private void install()throws Exception{
        File base=folder();
        if(!base.exists()&&!base.mkdirs())
            throw new IllegalStateException("Cannot create video model folder");
        if(installed(this)) {
            show("installed","Video model components already selected. Test real Wan generation on phone.");
            return;
        }
        // Download one at a time; never need duplicate copies of 5GB weights.
        for(int i=0;i<MODELS.length;i++){
            show("installing","Installing component "+(i+1)+"/"+MODELS.length+": "+MODELS[i].role);
            downloadModel(MODELS[i]);
        }
        // Atomic activation: mixed half-installed sets do not become configured.
        android.content.SharedPreferences.Editor editor=getSharedPreferences(PREFS,MODE_PRIVATE).edit();
        for(Model m:MODELS)editor.putString(m.role+"Path",target(m).getAbsolutePath());
        if(!editor.commit())throw new IllegalStateException("Cannot activate downloaded model paths");
        state="installed";
        message="Wan 1.3B Q4, UMT5 Q4 and VAE installed. CPU video rendering STILL needs real device test.";
    }
    @Override public void onTimeout(int startId,int serviceType){
        cancel.set(true);HttpURLConnection active=connection;
        if(active!=null)active.disconnect();
        state="paused";message="Android installer time limit; resume from Video Lab.";
        stopSelf(startId);
    }
    @Override public void onDestroy(){
        cancel.set(true);
        HttpURLConnection active=connection;if(active!=null)active.disconnect();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
