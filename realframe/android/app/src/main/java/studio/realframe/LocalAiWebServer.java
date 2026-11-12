package studio.realframe;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.StatFs;
import android.provider.MediaStore;

import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone, password-protected web UI and local REST endpoint. The phone
 * performs stable-diffusion.cpp inference; visitors can connect via loopback
 * or a private Wi-Fi address. No cloud relay, no automatically public internet.
 *
 * SECURITY: LAN traffic is ordinary HTTP, not TLS. Only a TRUSTED private
 * Wi-Fi is appropriate. No port forwarding. Never reveal the API access token.
 */
public final class LocalAiWebServer implements AutoCloseable {
    public static final int PORT=8765;
    private static final int MAX_BODY=16*1024, MAX_HEADERS=16*1024;
    private static final long RETAIN_MS=30L*60*1000;
    private final Context app;
    private final boolean lan;
    private final String boundIp;
    private final File cache;
    private final ServerSocket listener;
    private final Thread acceptThread;
    private final ThreadPoolExecutor peers = new ThreadPoolExecutor(
        2,4,20L,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12));
    private final ExecutorService inference = Executors.newSingleThreadExecutor();
    private final Map<String,Job> jobs=new ConcurrentHashMap<>();
    private final AtomicReference<Job> active=new AtomicReference<>();
    private volatile boolean stopping;

    public static final class Job {
        public final String id=UUID.randomUUID().toString().replace("-","");
        public final String kind;
        public final long created=System.currentTimeMillis();
        public volatile String state="queued",message="Waiting for local CPU";
        public volatile boolean cancelRequested;
        public volatile File result;
        Job(String kind){this.kind=kind;}
    }

    public static String trustedWifiIPv4(){
        try{
            Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
            while(all!=null&&all.hasMoreElements()){
                NetworkInterface network=all.nextElement();
                if(!network.isUp()||network.isLoopback())continue;
                String name=network.getName().toLowerCase(Locale.ROOT);
                if(!(name.startsWith("wlan")||name.startsWith("ap0")||name.startsWith("swlan")
                        ||name.startsWith("wifi")))continue;
                Enumeration<InetAddress> addresses=network.getInetAddresses();
                while(addresses.hasMoreElements()){
                    InetAddress address=addresses.nextElement();
                    if(address instanceof Inet4Address && address.isSiteLocalAddress())
                        return address.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return null;
    }

    public LocalAiWebServer(Context context,boolean lanAccess) throws Exception{
        app=context.getApplicationContext();
        lan=lanAccess;
        String address=lan?trustedWifiIPv4():"127.0.0.1";
        if(address==null)throw new IllegalStateException(
            "Trusted Wi-Fi IPv4 address not found. Connect to private Wi-Fi or choose Phone only.");
        boundIp=address;
        listener=new ServerSocket(PORT,8,InetAddress.getByName(boundIp));
        cache=new File(app.getCacheDir(),"local_web_results");
        if(!cache.exists()&&!cache.mkdirs()){
            listener.close();throw new IllegalStateException("Cannot create result cache");
        }
        acceptThread=new Thread(this::acceptLoop,"RealFrame-Local-AI-Web");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public String localAddress(){return "http://127.0.0.1:"+PORT+"/";}
    public String lanAddress(){return "http://"+boundIp+":"+PORT+"/";}
    public boolean acceptsLan(){return lan;}
    public static String token(Context context) {
        android.content.SharedPreferences p=context.getSharedPreferences("rf_local_web_auth",Context.MODE_PRIVATE);
        String result=p.getString("access_token","");
        if(result.length()==64)return result;
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);
        StringBuilder sb=new StringBuilder(64);
        for(byte b:key)sb.append(String.format(Locale.ROOT,"%02x",b&255));
        String generated=sb.toString();
        p.edit().putString("access_token",generated).commit();
        return generated;
    }
    public static void rotateToken(Context context){
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);
        StringBuilder sb=new StringBuilder(64);
        for(byte b:key)sb.append(String.format(Locale.ROOT,"%02x",b&255));
        context.getSharedPreferences("rf_local_web_auth",Context.MODE_PRIVATE)
            .edit().putString("access_token",sb.toString()).commit();
    }

    private void acceptLoop(){
        while(!stopping){
            try{
                Socket client=listener.accept();
                client.setSoTimeout(12_000);
                // IP is bound exclusively to loopback or Wi-Fi, not mobile data.
                if(!client.getInetAddress().isLoopbackAddress() &&
                    (!lan||!client.getInetAddress().isSiteLocalAddress())){
                    client.close();continue;
                }
                try{peers.execute(()->handle(client));}
                catch(java.util.concurrent.RejectedExecutionException busy){client.close();}
            }catch(Exception error){
                if(!stopping)android.util.Log.w("RealFrameWeb","Accept failed",error);
            }
        }
    }

    private static String line(InputStream input,int max) throws Exception {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        int next;
        while((next=input.read())!=-1){
            if(next=='\n')break;
            if(next==0||buffer.size()>=max)throw new IllegalArgumentException("Request header too large");
            if(next!='\r')buffer.write(next);
        }
        if(next==-1 && buffer.size()==0)return null;
        return buffer.toString(StandardCharsets.US_ASCII.name());
    }
    private static final class Request {
        String method,path,host,auth,contentType;
        byte[] body=new byte[0];
    }

    private Request readRequest(InputStream input) throws Exception{
        String first=line(input,4096);
        if(first==null)throw new IllegalArgumentException("Empty HTTP request");
        String[] pieces=first.split(" ",3);
        if(pieces.length!=3||!(pieces[2].equals("HTTP/1.1")||pieces[2].equals("HTTP/1.0")))
            throw new IllegalArgumentException("Invalid HTTP request line");
        if(!pieces[1].startsWith("/")||pieces[1].length()>512||pieces[1].contains("%")
           ||pieces[1].contains("?")||pieces[1].contains("#"))
            throw new IllegalArgumentException("Unsupported URL");
        Request request=new Request();
        request.method=pieces[0];request.path=pieces[1];
        int contentLength=-1,headerBytes=0;
        while(true){
            String header=line(input,2048);
            if(header==null)throw new IllegalArgumentException("Missing end of headers");
            headerBytes+=header.length()+2;
            if(headerBytes>MAX_HEADERS)throw new IllegalArgumentException("Headers too large");
            if(header.isEmpty())break;
            int sep=header.indexOf(':');
            if(sep<=0)throw new IllegalArgumentException("Malformed request header");
            String name=header.substring(0,sep).trim().toLowerCase(Locale.ROOT);
            String value=header.substring(sep+1).trim();
            switch(name){
                case "host": request.host=value;break;
                case "authorization": request.auth=value;break;
                case "content-type": request.contentType=value;break;
                case "transfer-encoding":throw new IllegalArgumentException("Chunked uploads not supported");
                case "content-length":
                    if(contentLength>=0)throw new IllegalArgumentException("Duplicate content length");
                    try{contentLength=Integer.parseInt(value);}
                    catch(NumberFormatException e){throw new IllegalArgumentException("Invalid content length");}
                    if(contentLength<0||contentLength>MAX_BODY)
                        throw new IllegalArgumentException("Request is too large");
                    break;
                default:break;
            }
        }
        if(!("GET".equals(request.method)||"POST".equals(request.method)))
            throw new IllegalArgumentException("Only GET and POST are supported");
        if(!hostAllowed(request.host))
            throw new IllegalArgumentException("Host not allowed (use the phone IP, not a domain)");
        if("POST".equals(request.method)){
            if(contentLength<0)throw new IllegalArgumentException("Content-Length required");
            if(request.contentType==null||!request.contentType.toLowerCase(Locale.ROOT).startsWith("application/json"))
                throw new IllegalArgumentException("POST requires JSON");
            request.body=new byte[contentLength];
            int offset=0;
            while(offset<contentLength){
                int n=input.read(request.body,offset,contentLength-offset);
                if(n<0)throw new IllegalArgumentException("Truncated JSON request");
                offset+=n;
            }
        }else if(contentLength>0)throw new IllegalArgumentException("GET with body unsupported");
        return request;
    }
    private boolean hostAllowed(String host){
        if(host==null)return false;
        String trimmed=host.toLowerCase(Locale.ROOT);
        return trimmed.equals(boundIp+":"+PORT) ||
            (trimmed.equals("127.0.0.1:"+PORT)||trimmed.equals("localhost:"+PORT))
             && boundIp.equals("127.0.0.1");
    }
    private static boolean authorized(String supplied,String expected){
        if(supplied==null||!supplied.startsWith("Bearer "))return false;
        byte[] a=supplied.substring(7).getBytes(StandardCharsets.US_ASCII);
        byte[] b=expected.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(a,b);
    }
    private static void respond(OutputStream out,int code,String type,byte[] data) throws Exception{
        String reason=code==200?"OK":code==202?"Accepted":code==400?"Bad Request":
             code==401?"Unauthorized":code==404?"Not Found":code==409?"Conflict":
             code==413?"Payload Too Large":code==503?"Unavailable":"Error";
        String headers="HTTP/1.1 "+code+" "+reason+"\r\n"+
             "Content-Type: "+type+"\r\n"+
             "Content-Length: "+data.length+"\r\n"+
             "Cache-Control: no-store\r\n"+
             "X-Content-Type-Options: nosniff\r\n"+
             "Referrer-Policy: no-referrer\r\n"+
             "Access-Control-Allow-Origin: null\r\n"+
             "Connection: close\r\n\r\n";
        // No permissive CORS: authentication requires Authorization header,
        // and cross-origin JavaScript cannot retrieve this response.
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(data);
        out.flush();
    }
    private static void text(OutputStream out,int code,String type,String value)throws Exception{
        respond(out,code,type,value.getBytes(StandardCharsets.UTF_8));
    }
    private static void json(OutputStream out,int code,JSONObject obj)throws Exception{
        text(out,code,"application/json; charset=utf-8",obj.toString());
    }
    private static JSONObject error(String message) throws Exception{
        return new JSONObject().put("error",message);
    }
    private void handle(Socket client) {
        try(Socket socket=client;
            InputStream input=new BufferedInputStream(socket.getInputStream());
            OutputStream output=new BufferedOutputStream(socket.getOutputStream())){
            Request req;
            try{req=readRequest(input);}
            catch(Exception bad){json(output,400,error(bad.getMessage()));return;}
            if(req.path.equals("/")&&req.method.equals("GET")){
                try(InputStream file=app.getAssets().open("local_studio.html")){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    byte[] chunk=new byte[4096];int read;
                    while((read=file.read(chunk))!=-1)bytes.write(chunk,0,read);
                    respond(output,200,"text/html; charset=utf-8",bytes.toByteArray());
                }
                return;
            }
            if(req.path.equals("/api/health") && req.method.equals("GET")){
                json(output,200,new JSONObject().put("ok",true));return;
            }
            if(!authorized(req.auth,token(app))){
                json(output,401,error("Invalid access token"));return;
            }
            route(req,output);
        }catch(Exception e){
            android.util.Log.w("RealFrameWeb","Request failed safely: "+e.getMessage());
        }
    }

    private void route(Request req,OutputStream out)throws Exception{
        prune();
        if(req.path.equals("/api/status")&&req.method.equals("GET")){
            String model=app.getSharedPreferences("realframe_local_sd",Context.MODE_PRIVATE)
                .getString("modelPath","");
            json(out,200,new JSONObject()
                .put("imageEngine",LocalImageEngine.isAvailable())
                .put("imageModel",new File(model).isFile())
                .put("videoEngine",LocalVideoEngine.isAvailable())
                .put("videoModels",LocalVideoActivity.isConfigured(app))
                .put("renderLocation","phone_cpu")
                .put("trustedLan",lan));
            return;
        }
        if(req.path.equals("/api/generate/image")&&req.method.equals("POST")){
            queue("image",new JSONObject(new String(req.body,StandardCharsets.UTF_8)),out);
            return;
        }
        if(req.path.equals("/api/generate/video")&&req.method.equals("POST")){
            queue("video",new JSONObject(new String(req.body,StandardCharsets.UTF_8)),out);
            return;
        }
        if(req.path.startsWith("/api/jobs/")){
            String tail=req.path.substring("/api/jobs/".length());
            String[] pieces=tail.split("/",-1);
            if(pieces.length>2||pieces[0].length()!=32||!pieces[0].matches("[0-9a-f]{32}")){
                json(out,404,error("Unknown job"));return;
            }
            Job job=jobs.get(pieces[0]);
            if(job==null){json(out,404,error("Job expired or unknown"));return;}
            if(pieces.length==1 && req.method.equals("GET")){
                json(out,200,new JSONObject()
                    .put("jobId",job.id).put("kind",job.kind)
                    .put("state",job.state).put("message",job.message));
                return;
            }
            if(pieces.length==2&&pieces[1].equals("cancel")&&req.method.equals("POST")){
                job.cancelRequested=true;
                if("queued".equals(job.state))job.message="Cancellation requested";
                if("image".equals(job.kind))LocalImageEngine.cancel();
                else LocalVideoEngine.cancel();
                json(out,202,new JSONObject().put("cancelRequested",true));return;
            }
            if(pieces.length==2&&pieces[1].equals("result")&&req.method.equals("GET")){
                File result=job.result;
                if(!"finished".equals(job.state)||result==null||!result.isFile()){
                    json(out,409,error("Job has no completed output"));return;
                }
                sendFile(out,result,"image".equals(job.kind)?"image/png":"video/mp4");
                return;
            }
        }
        json(out,404,error("Endpoint not found"));
    }
    private void queue(String kind,JSONObject body,OutputStream out)throws Exception{
        if(active.get()!=null){json(out,409,error("A local render is already queued or running"));return;}
        if(!"image".equals(kind)&&!"video".equals(kind)){
            json(out,400,error("Unsupported generation type"));return;
        }
        String prompt=body.optString("prompt","").trim();
        String negative=body.optString("negative","");
        int size=body.optInt("size",256);
        int steps=body.optInt("steps",4);
        long seed=body.optLong("seed",13901);
        int frames=body.optInt("frames",5);
        if(prompt.isEmpty()||prompt.length()>2000||negative.length()>1000||
           size<192||size>384||size%64!=0||steps<1||steps>30||
           seed<1||seed>2147483647L ||
           ("video".equals(kind)&&(frames<5||frames>33||(frames-1)%4!=0))){
            json(out,400,error("Invalid prompt, size, seed, steps or frames"));return;
        }
        String imageModel=app.getSharedPreferences("realframe_local_sd",Context.MODE_PRIVATE)
            .getString("modelPath","");
        if("image".equals(kind)&&(!LocalImageEngine.isAvailable()||!new File(imageModel).isFile())){
            json(out,409,error("Import an SD checkpoint in RealFrame → Local AI Image first"));return;
        }
        if("video".equals(kind)&&(!LocalVideoEngine.isAvailable()||!LocalVideoActivity.isConfigured(app))){
            json(out,409,error("Import Wan diffusion, T5 and VAE in RealFrame → Local AI Video first"));return;
        }
        Job job=new Job(kind);
        if(!active.compareAndSet(null,job)){json(out,409,error("Another render started"));return;}
        jobs.put(job.id,job);
        try{
            inference.execute(()->render(job,prompt,negative,size,steps,seed,frames,imageModel));
        }catch(Exception e){
            active.compareAndSet(job,null);jobs.remove(job.id);
            json(out,503,error("Local inference worker unavailable"));return;
        }
        json(out,202,new JSONObject().put("jobId",job.id).put("state",job.state));
    }

    private void render(Job job,String prompt,String negative,int size,int steps,long seed,int frames,String imageModel){
        File output=null;
        try{
            if(job.cancelRequested)throw new InterruptedException("Cancelled before start");
            job.state="running";
            job.message="Loading local AI model on the phone CPU";
            if("image".equals(job.kind)){
                int[] pixels=LocalImageEngine.generate(imageModel,prompt,negative,size,size,steps,seed,4);
                if(job.cancelRequested)throw new InterruptedException("Cancelled after inference");
                if(pixels==null||pixels.length!=size*size)
                    throw new IllegalStateException("Native image renderer returned no pixels");
                Bitmap image=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
                output=new File(cache,job.id+".png");
                try(FileOutputStream file=new FileOutputStream(output)){
                    if(!image.compress(Bitmap.CompressFormat.PNG,100,file))
                        throw new IllegalStateException("Local PNG encoder failed");
                }finally{image.recycle();}
                copyImageToGallery(output);
            }else{
                android.content.SharedPreferences p=app.getSharedPreferences("realframe_wan_local_v18",Context.MODE_PRIVATE);
                String diffusion=p.getString("diffusionPath","");
                String t5=p.getString("t5Path","");
                String vae=p.getString("vaePath","");
                if(!new File(diffusion).isFile()||!new File(t5).isFile()||!new File(vae).isFile())
                    throw new IllegalStateException("Wan model pack files are missing");
                final LocalVideoEncoder[] writers={null};
                Uri movie;
                try{
                    int count=LocalVideoEngine.generate(diffusion,t5,vae,prompt,negative,
                        size,size,frames,steps,seed,4,
                        (index,width,height,fps,pixels)->{
                            try{
                                if(job.cancelRequested)throw new IllegalStateException("Cancelled by user");
                                if(writers[0]==null)
                                    writers[0]=new LocalVideoEncoder(app,width,height,fps);
                                writers[0].append(pixels);
                                job.message="Encoding on phone: "+(index+1)+"/"+frames+" frames";
                            }catch(Exception e){throw new IllegalStateException("Video encoder: "+e.getMessage(),e);}
                        });
                    if(job.cancelRequested)throw new InterruptedException("Video job cancelled");
                    if(count<1||writers[0]==null)throw new IllegalStateException("Wan video produced no frames");
                    movie=writers[0].finish();
                }finally{if(writers[0]!=null)writers[0].close();}
                // Duplicate a small encoded movie into job cache for token-authenticated
                // browser download; Gallery copy is retained independently on device.
                output=new File(cache,job.id+".mp4");
                try(InputStream src=app.getContentResolver().openInputStream(movie);
                    FileOutputStream dst=new FileOutputStream(output)){
                    if(src==null)throw new IllegalStateException("Cannot read generated MP4 from Gallery");
                    byte[] chunk=new byte[65536];int n;
                    while((n=src.read(chunk))!=-1)dst.write(chunk,0,n);
                }
            }
            if(job.cancelRequested)throw new InterruptedException("Cancelled by user");
            job.result=output;
            job.state="finished";
            job.message="Completed locally on phone. Download the result or find it in Gallery.";
        }catch(Throwable failure){
            if(output!=null)output.delete();
            job.state=job.cancelRequested?"cancelled":"failed";
            job.message=(job.cancelRequested?"Stopped: ":"Local inference failed: ")+failure.getMessage();
        }finally{active.compareAndSet(job,null);}
    }
    private void copyImageToGallery(File png) throws Exception{
        ContentResolver resolver=app.getContentResolver();
        Uri target=null;
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,"realframe_web_"+System.currentTimeMillis()+".png");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RealFrameStudio");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            target=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(target==null)throw new IllegalStateException("Cannot save image to Gallery");
            try(InputStream src=new FileInputStream(png);OutputStream dst=resolver.openOutputStream(target,"w")){
                if(dst==null)throw new IllegalStateException("Cannot open Gallery image");
                byte[] chunk=new byte[65536];int read;
                while((read=src.read(chunk))!=-1)dst.write(chunk,0,read);
            }
            ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);
            resolver.update(target,done,null,null);
        }catch(Exception e){
            if(target!=null)resolver.delete(target,null,null);
            throw e;
        }
    }
    private static void sendFile(OutputStream out,File file,String mime)throws Exception{
        long length=file.length();
        String headers="HTTP/1.1 200 OK\r\nContent-Type: "+mime+"\r\n"+
            "Content-Length: "+length+"\r\n"+
            "Cache-Control: no-store\r\n"+
            "X-Content-Type-Options: nosniff\r\n"+
            "Content-Disposition: attachment; filename=realframe_output."+
                 ("image/png".equals(mime)?"png":"mp4")+"\r\n"+
            "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        try(InputStream content=new FileInputStream(file)){
            byte[] block=new byte[65536];int read;
            while((read=content.read(block))!=-1)out.write(block,0,read);
        }
        out.flush();
    }
    private void prune(){
        long threshold=System.currentTimeMillis()-RETAIN_MS;
        for(Job job:new ArrayList<>(jobs.values())){
            if(job.created<threshold && active.get()!=job){
                if(jobs.remove(job.id,job)&&job.result!=null)job.result.delete();
            }
        }
    }
    @Override public void close(){
        stopping=true;
        try{listener.close();}catch(Exception ignored){}
        Job job=active.get();
        if(job!=null){
            job.cancelRequested=true;
            if("image".equals(job.kind))LocalImageEngine.cancel();
            else LocalVideoEngine.cancel();
        }
        inference.shutdownNow();
        peers.shutdownNow();
        try{acceptThread.interrupt();}catch(Exception ignored){}
    }
}