package studio.realframe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

/**
 * User-started foreground data-sync service for the PHONE-HOSTED AI web server.
 * The Android OS may stop this under battery/foreground-service restrictions;
 * Android 15 imposes a time budget on dataSync foreground services.
 *
 * This does not create an internet-accessible endpoint: it binds loopback or
 * the phone's private Wi-Fi IP only, with a bearer token for every API call.
 */
public final class LocalAiHostService extends Service {
    public static final String ACTION_START="studio.realframe.START_PHONE_AI_SERVER";
    public static final String EXTRA_LAN="trusted_wifi_access";
    private static final String CHANNEL="rf_local_web_server";
    private static final int NOTIFICATION=1823;
    private static volatile LocalAiWebServer active;
    private static volatile String error="";
    private LocalAiWebServer own;

    public static boolean isRunning(){return active!=null;}
    public static boolean acceptsLan(){return active!=null&&active.acceptsLan();}
    public static String address(){
        LocalAiWebServer server=active;
        return server==null?"":server.lanAddress();
    }
    public static String failure(){return error;}

    @Override public void onCreate(){
        super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "RealFrame phone AI web access",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Active local AI browser server on this phone");
        ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    private Notification notification(boolean lan){
        Intent open=new Intent(this,LocalWebSettingsActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setContentTitle("RealFrame AI — phone web server")
            .setContentText(lan?"LAN mode: trusted private Wi-Fi only":"Phone-only: 127.0.0.1")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||!ACTION_START.equals(intent.getAction()))
            return START_NOT_STICKY;
        boolean lan=intent.getBooleanExtra(EXTRA_LAN,false);
        if(own!=null){
            own.close();own=null;active=null;
        }
        try {
            // App starts this service from an Activity; no boot-time startup.
            startForeground(NOTIFICATION,notification(lan),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            own=new LocalAiWebServer(this,lan);
            active=own;error="";
        }catch(Throwable failure){
            error=failure.getMessage()==null?failure.toString():failure.getMessage();
            if(own!=null)own.close();
            own=null;active=null;
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int fgsType) {
        // Android 15: dataSync foreground service duration is bounded.
        error="Android stopped the server after its foreground-service time limit. Reopen RealFrame.";
        stopSelf(startId);
    }
    @Override public void onDestroy(){
        if(own!=null){own.close();own=null;}
        active=null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
