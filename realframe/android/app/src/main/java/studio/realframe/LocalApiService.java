package studio.realframe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * On-device AI API: run native local image or experimental Wan video through
 * Android loopback (default) or opt-in private Wi-Fi. HTTP does NOT upload to
 * a cloud provider, even when the phone is connected to the Internet.
 *
 * Privacy: bearer token required on all API endpoints. LAN HTTP is NOT TLS.
 * Only start LAN sharing on a trusted private network. Service does not
 * expose a public Internet endpoint and is stopped explicitly in the UI.
 */
public final class LocalApiService extends Service {
    public static final int PORT=8820;
    public static final String ACTION_START_LOOPBACK="realframe.START_LOOPBACK";
    public static final String ACTION_START_LAN="realframe.START_LAN";
    public static final String ACTION_STOP="realframe.STOP";
    private static final String CHANNEL="realframe_local_api";
    private static final String PREFS="realframe_local_api";
    private static final int MAX_BODY=16_384;
    private static final int NOTIFICATION_ID=8020;
    private static volatile boolean running=false;
    private static volatile boolean lan=false;

    private final AtomicBoolean activeJob=new AtomicBoolean(false);
    private final Map<String,Job> jobs=new ConcurrentHashMap<>();
    private final ExecutorService clients=Executors.newFixedThreadPool(4);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closing;
    private volatile ServerSocket socket;
    private Thread acceptThread;

    public static boolean isRunning(){return running;}
    public static boolean isLan(){return running && lan;}

    public static String getOrCreateToken(android.content.Context context) {
        android.content.SharedPreferences p=context.getSharedPreferences(PREFS,MODE_PRIVATE);
        String token=p.getString("secret","");
        if(token.length()>=40)return token;
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        token=android.util.Base64.encodeToString(bytes,
            android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING|android.util.Base64.NO_WRAP);
        p.edit().putString("secret",token).apply();
        return token;
    }
    public static void rotateToken(android.content.Context context){
        context.getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove("secret").apply();
        getOrCreateToken(context);
    }

    private static final class Job {
        final String id,kind;
        final long createdAt=System.currentTimeMillis();
        final List<Uri> saved=new ArrayList<>();
        volatile String state="queued",error="";
        volatile int completed,total;
        Job(String key,String kind,int total) {
            this.id=key;this.kind=kind;this.total=total;
        }
        JSONObject info()throws Exception {
            JSONObject json=new JSONObject();
            json.put("id",id).put("kind",kind).put("state",state)
                .put("completed",completed).put("total",total)
                .put("error",error).put("created_at",createdAt);
            JSONArray list=new JSONArray();
            synchronized(saved){
                for(int i=0;i<saved.size();i++)
                    list.put("/v1/jobs/"+id+"/media/"+i);
            }
            json.put("download_paths",list);
            return json;
        }
    }
    @Override public void onCreate(){
        super.onCreate();
        getOrCreateToken(this);
        NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        n.createNotificationChannel(new NotificationChannel(CHANNEL,
            "RealFrame on-device AI API",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(){
        Intent home=new Intent(this,LocalApiActivity.class);
        PendingIntent intent=PendingIntent.getActivity(this,8020,home,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("RealFrame local AI API")
            .setContentText(lan?"On private Wi-Fi • token required":"On this phone only • no cloud")
            .setContentIntent(intent)
            .setOngoing(true)
            .build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){
            shutdown();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;
        }
        boolean requestLan=intent!=null&&ACTION_START_LAN.equals(intent.getAction());
        // Never open the network accidentally after a system-initiated restart.
        if(intent==null)return START_NOT_STICKY;
        startForeground(NOTIFICATION_ID,notification());
        if(running && lan==requestLan)return START_NOT_STICKY;
        restartSocket(requestLan);
        return START_NOT_STICKY;
    }
    private synchronized void restartSocket(boolean share){
        try{if(socket!=null)socket.close();}catch(Exception ignored){}
        closing=false;
        try{
            InetAddress address=InetAddress.getByName(share?"0.0.0.0":"127.0.0.1");
            ServerSocket opened=new ServerSocket(PORT,8,address);
            opened.setReuseAddress(true);
            socket=opened;
            lan=share;running=true;
            // Display an up-to-date notification showing the chosen privacy scope.
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID,notification());
            acceptThread=new Thread(()->acceptLoop(opened),"RealFrame-Local-API");
            acceptThread.setDaemon(true);acceptThread.start();
        }catch(Exception e){
            socket=null;running=false;lan=false;
            android.util.Log.e("RealFrame", "Could not bind local API port",e);
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }
    private void acceptLoop(ServerSocket server){
        while(!closing && !server.isClosed()){
            try {
                Socket incoming=server.accept();
                incoming.setSoTimeout(7_000);
                clients.execute(()->serve(incoming));
            }catch(Exception error){
                if(!server.isClosed())android.util.Log.w("RealFrame","Local API connection failed",error);
                if(server.isClosed())break;
            }
        }
    }
    private static String line(InputStream input,int max)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        int c;
        while((c=input.read())!=-1){
            if(c=='\n')break;
            if(c!='\r')out.write(c);
            if(out.size()>max)throw new IOException("Header line too long");
        }
        if(c==-1 && out.size()==0)throw new IOException("Connection closed");
        return out.toString("UTF-8");
    }
    private static byte[] body(InputStream input,int len)throws IOException{
        byte[] data=new byte[len];int index=0;
        while(index<len){
            int n=input.read(data,index,len-index);
            if(n<0)throw new IOException("Incomplete request body");
            index+=n;
        }
        return data;
    }
    private static String lower(String s){return s.toLowerCase(Locale.ROOT);}
    private static void response(OutputStream output,int status,String type,byte[] bytes)throws IOException{
        String reason=status==200?"OK":status==202?"Accepted":status==400?"Bad Request":
            status==401?"Unauthorized":status==404?"Not Found":status==405?"Method Not Allowed":
            status==409?"Conflict":status==413?"Payload Too Large":status==422?"Unprocessable Entity":
            status==503?"Service Unavailable":"Error";
        String headers="HTTP/1.1 "+status+" "+reason+"\r\n"+
            "Content-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\n"+
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n"+
            "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);output.flush();
    }
    private static void reply(OutputStream out,int status,JSONObject body)throws IOException{
        response(out,status,"application/json; charset=utf-8",
            body.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void error(OutputStream out,int status,String reason)throws IOException {
        JSONObject obj=new JSONObject();
        try{obj.put("error",reason);}catch(Exception ignored){}
        reply(out,status,obj);
    }
    private static boolean sameToken(String expected,String supplied){
        byte[] a=expected.getBytes(StandardCharsets.US_ASCII);
        byte[] b=supplied.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(a,b);
    }
    private void serve(Socket connection){
        try(Socket conn=connection;
            InputStream input=new BufferedInputStream(conn.getInputStream());
            OutputStream output=new BufferedOutputStream(conn.getOutputStream())){
            String request=line(input,2048);
            String[] parts=request.split(" ");
            if(parts.length!=3||!parts[2].startsWith("HTTP/1.")){
                error(output,400,"Malformed HTTP request");return;
            }
            String method=parts[0],path=parts[1];
            if(path.contains("?"))path=path.substring(0,path.indexOf('?'));
            if(!path.startsWith("/")||path.contains("..")||path.length()>180){
                error(output,400,"Invalid path");return;
            }
            Map<String,String> headers=new java.util.HashMap<>();
            int lines=0;
            for(;;){
                String header=line(input,2048);
                if(header.isEmpty())break;
                if(++lines>30||!header.contains(":")){
                    error(output,400,"Invalid headers");return;
                }
                int colon=header.indexOf(':');
                String key=lower(header.substring(0,colon).trim());
                if(headers.containsKey(key)){
                    error(output,400,"Duplicate HTTP header");return;
                }
                headers.put(key,header.substring(colon+1).trim());
            }
            if(headers.containsKey("transfer-encoding")){
                error(output,400,"Chunked requests not supported");return;
            }
            int length=0;
            if(headers.containsKey("content-length")){
                try{length=Integer.parseInt(headers.get("content-length"));}
                catch(Exception ignored){error(output,400,"Bad Content-Length");return;}
            }
            if(length<0||length>MAX_BODY){error(output,413,"JSON request too large");return;}
            if("GET".equals(method)&&"/".equals(path)){
                response(output,200,"text/html; charset=utf-8",
                    LocalApiWebPage.html().getBytes(StandardCharsets.UTF_8));
                return;
            }
            String token=headers.getOrDefault("x-realframe-token","");
            if(!sameToken(getOrCreateToken(this),token)){
                error(output,401,"Use X-RealFrame-Token from Local API settings");return;
            }
            byte[] data=body(input,length);
            dispatch(method,path,data,output);
        }catch(Exception e){
            android.util.Log.w("RealFrame", "Local client disconnected",e);
        }
    }
    private void dispatch(String method,String path,byte[] bytes,OutputStream output)throws Exception{
        if("GET".equals(method)&&"/v1/status".equals(path)){
            JSONObject status=new JSONObject();
            status.put("service","RealFrame on-device native AI API");
            status.put("privacy","local native inference only; no cloud fallback");
            status.put("scope",lan?"wifi-opt-in":"loopback-only");
            status.put("image_engine",LocalImageEngine.isAvailable());
            status.put("video_engine_experimental",LocalVideoEngine.isAvailable());
            status.put("image_model",imageModel().isFile());
            status.put("video_models",videoModelsPresent());
            status.put("busy",activeJob.get());
            status.put("max_image_batch",4);
            status.put("device_tested",false);
            reply(output,200,status);return;
        }
        if("POST".equals(method)&&"/v1/images/generations".equals(path)){
            enqueue("image",bytes,output);return;
        }
        if("POST".equals(method)&&"/v1/videos/generations".equals(path)){
            enqueue("video",bytes,output);return;
        }
        if(path.startsWith("/v1/jobs/")){
            String[] split=path.split("/");
            if(split.length<4){error(output,404,"Job not found");return;}
            Job job=jobs.get(split[3]);
            if(job==null){error(output,404,"Unknown job");return;}
            if("GET".equals(method)&&split.length==4){
                reply(output,200,job.info());return;
            }
            if("POST".equals(method)&&split.length==5&&"cancel".equals(split[4])){
                if(!"running".equals(job.state)&&!"queued".equals(job.state)){
                    error(output,409,"Job already finished");return;
                }
                job.state="cancelling";
                if("image".equals(job.kind))LocalImageEngine.cancel();
                if("video".equals(job.kind))LocalVideoEngine.cancel();
                reply(output,202,job.info());return;
            }
            if("GET".equals(method)&&split.length==6&&"media".equals(split[4])){
                int index;
                try{index=Integer.parseInt(split[5]);}catch(Exception e){error(output,400,"Invalid media index");return;}
                Uri saved;
                synchronized(job.saved){
                    if(index<0||index>=job.saved.size()){error(output,404,"Media not ready");return;}
                    saved=job.saved.get(index);
                }
                download(output,saved,"video".equals(job.kind)?"video/mp4":"image/png");
                return;
            }
        }
        error(output,404,"Unknown API route");
    }
    private File imageModel(){
        String path=getSharedPreferences("realframe_local_sd",MODE_PRIVATE).getString("modelPath","");
        return path.isEmpty()?new File("/not-installed"):new File(path);
    }
    private String videoPath(String role){
        return getSharedPreferences("realframe_wan_local_v18",MODE_PRIVATE).getString(role+"Path","");
    }
    private boolean videoModelsPresent(){
        for(String role:new String[]{"diffusion","t5","vae"}){
            String path=videoPath(role);
            if(path.isEmpty()||!new File(path).isFile())return false;
        }
        return true;
    }
    private void enqueue(String kind,byte[] request,OutputStream output)throws Exception {
        if(!activeJob.compareAndSet(false,true)){
            error(output,409,"The local API already has an active job. Retry when it finishes.");return;
        }
        try{
            JSONObject options=new JSONObject(new String(request,StandardCharsets.UTF_8));
            String prompt=options.optString("prompt","").trim();
            if(prompt.isEmpty()||prompt.length()>1600)throw new IllegalArgumentException("Prompt must be 1-1600 characters");
            String negative=options.optString("negative_prompt","").trim();
            if(negative.length()>600)throw new IllegalArgumentException("Negative prompt too long");
            int width=options.optInt("width",256),height=options.optInt("height",256);
            int steps=options.optInt("steps",4);
            long seed=options.optLong("seed",13901L);
            int count="image".equals(kind)?options.optInt("n",1):options.optInt("frames",5);
            if(seed<1||seed>2147483647L||steps<1||steps>30||
               width<192||height<192||width>512||height>512||
               width%64!=0||height%64!=0)
                throw new IllegalArgumentException("Bad resolution, steps or seed");
            if("image".equals(kind)){
                if(count<1||count>4)throw new IllegalArgumentException("Image batch must be 1-4");
                if(!LocalImageEngine.isAvailable()||!imageModel().isFile())
                    throw new IllegalArgumentException("Local image engine/model not ready");
            }else{
                if(count<5||count>33||(count-1)%4!=0)
                    throw new IllegalArgumentException("Video frames must be 5,9,...33");
                if(!LocalVideoEngine.isAvailable()||!videoModelsPresent())
                    throw new IllegalArgumentException("Wan CPU model pack not imported");
            }
            if(jobs.size()>=12) {
                jobs.entrySet().removeIf(e->!e.getValue().state.equals("running")&&
                    !e.getValue().state.equals("queued"));
            }
            if(jobs.size()>=12)throw new IllegalArgumentException("Too many completed API jobs; restart local server");
            String id=UUID.randomUUID().toString().replace("-","");
            Job job=new Job(id,kind,count);jobs.put(id,job);
            worker.execute(()->process(job,prompt,negative,width,height,steps,seed,count));
            reply(output,202,job.info());
        }catch(Exception e){
            activeJob.set(false);
            error(output,422,e.getMessage()==null?"Invalid local AI job":e.getMessage());
        }
    }
    private Uri saveImage(int[] pixels,int width,int height,int number)throws Exception {
        Bitmap image=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
        Uri saved=null;
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "realframe_api_"+System.currentTimeMillis()+"_"+number+".png");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RealFrameStudio");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            saved=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(saved==null)throw new IOException("Cannot create Gallery image");
            try(OutputStream out=getContentResolver().openOutputStream(saved,"w")){
                if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))
                    throw new IOException("Failed to write AI PNG to Gallery");
            }
            ContentValues complete=new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING,0);
            getContentResolver().update(saved,complete,null,null);
            return saved;
        }catch(Exception error){
            if(saved!=null)getContentResolver().delete(saved,null,null);
            throw error;
        }finally{image.recycle();}
    }
    private void process(Job job,String prompt,String negative,int width,int height,
                         int steps,long seed,int count) {
        job.state="running";
        try{
            if("image".equals(job.kind)) {
                int delivered=LocalImageEngine.generateBatch(imageModel().getAbsolutePath(),
                    prompt,negative,width,height,steps,seed,4,count,(index,pixels)->{
                        try{
                            Uri media=saveImage(pixels,width,height,index+1);
                            synchronized(job.saved){job.saved.add(media);}
                            job.completed=index+1;
                        }catch(Exception e){throw new IllegalStateException("Gallery save failed",e);}
                    });
                if(delivered==count)job.state="completed";
                else{job.state="cancelled";job.error="Partial batch: "+delivered+" images saved";}
            }else{
                final LocalVideoEncoder[] video={null};
                try{
                    int delivered=LocalVideoEngine.generate(videoPath("diffusion"),videoPath("t5"),
                        videoPath("vae"),prompt,negative,width,height,count,steps,seed,4,
                        (index,w,h,fps,pixels)->{
                            try{
                                if(video[0]==null)video[0]=new LocalVideoEncoder(this,w,h,fps);
                                video[0].append(pixels);job.completed=index+1;
                            }catch(Exception e){throw new IllegalStateException("MP4 encoding failure",e);}
                        });
                    if(delivered<1||video[0]==null)
                        throw new IllegalStateException("No native Wan video frames completed");
                    // A partial clip can still be saved, but mark it cancelled.
                    Uri completed=video[0].finish();
                    synchronized(job.saved){job.saved.add(completed);}
                    job.state=delivered==count?"completed":"cancelled";
                    if(delivered!=count)job.error="Only "+delivered+" video frames were generated";
                }finally{if(video[0]!=null)video[0].close();}
            }
        }catch(Throwable ex){
            job.state="failed";
            job.error=ex.getClass().getSimpleName()+": "+
                (ex.getMessage()==null?"unknown local inference error":ex.getMessage());
        }finally{activeJob.set(false);}
    }
    private void download(OutputStream output,Uri source,String mime)throws IOException {
        try(InputStream data=getContentResolver().openInputStream(source)){
            if(data==null){error(output,404,"Gallery media no longer exists");return;}
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: "+mime+
                "\r\nTransfer-Encoding: chunked\r\n"+
                "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n"+
                "Content-Disposition: attachment; filename=\"realframe_output."+
                ("video/mp4".equals(mime)?"mp4":"png")+"\"\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            byte[] buf=new byte[8192];int read;long total=0;
            while((read=data.read(buf))!=-1){
                if((total+=read)>200L*1024L*1024L)
                    throw new IOException("Media too large to stream through local API");
                output.write((Integer.toHexString(read)+"\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(buf,0,read);output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            output.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }
    }
    private synchronized void shutdown(){
        closing=true;running=false;lan=false;
        try{if(socket!=null)socket.close();}catch(Exception ignored){}
        socket=null;
        LocalImageEngine.cancel();LocalVideoEngine.cancel();
        clients.shutdownNow();worker.shutdownNow();
    }
    @Override public void onDestroy(){shutdown();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
