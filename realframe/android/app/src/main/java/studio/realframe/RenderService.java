package studio.realframe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started foreground serial render worker. Does not bypass OS foreground time budgets. */
public final class RenderService extends Service {
    public static final String ACTION_RUN = "studio.realframe.action.RUN";
    public static final String ACTION_PAUSE = "studio.realframe.action.PAUSE";
    private static final String CHANNEL = "studio_render_jobs";
    private static final int NOTIFY_ID = 83;
    private final ExecutorService singleWorker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean working = new AtomicBoolean(false);
    private StudioStore store;
    private SecretVault settings;
    private Renderer renderer;

    public static void requestRun(Context context) {
        Intent start = new Intent(context, RenderService.class).setAction(ACTION_RUN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(start);
        else context.startService(start);
    }

    @Override public void onCreate() {
        super.onCreate();
        store = new StudioStore(this);
        store.recoverInterrupted();
        settings = new SecretVault(this);
        renderer = new Renderer(this, settings);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "RealFrame rendering", NotificationManager.IMPORTANCE_LOW));
    }

    private Notification notification(String status) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openApp = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("RealFrame AI Studio")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setContentIntent(openApp)
                .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_RUN.equals(intent.getAction()) ||
                "1".equals(settings.get("queuePaused", "0"))) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFY_ID, notification("Preparing render queue"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else startForeground(NOTIFY_ID, notification("Preparing render queue"));
        } catch (RuntimeException e) {
            Log.e("RealFrame", "Cannot start foreground render service", e);
            stopSelf(); return START_NOT_STICKY;
        }
        if (working.compareAndSet(false, true)) singleWorker.execute(this::pump);
        return START_NOT_STICKY;
    }

    private void update(JSONObject job, String status, String message, String result) throws Exception {
        job.put("status", status).put("message", message);
        if (result != null) job.put("resultUri", result);
        store.putJob(job);
    }

    private void pump() {
        try {
            while (!Thread.currentThread().isInterrupted() &&
                    "0".equals(settings.get("queuePaused", "0"))) {
                JSONObject job = store.nextReadyJob();
                if (job == null) break;
                try {
                    // A user may cancel a queued job while the worker is selecting it.
                    // Recheck persisted status immediately before starting the API call.
                    JSONObject current = store.findJob(job.optString("id"));
                    if (current == null || !"queued".equals(current.optString("status"))) continue;
                    NotificationManager nm = getSystemService(NotificationManager.class);
                    nm.notify(NOTIFY_ID, notification("Rendering " + job.optString("kind") + "…"));
                    update(job, "running", "Sending request", null);
                    String media = renderer.generate(job);
                    update(job, "done", "Demo".equals(job.optString("backend")) ? "Diagnostic only (NOT AI) saved to Gallery" : "Saved to Gallery", media);
                    if ("video".equals(job.optString("kind"))) {
                        String scene = job.optString("sceneIndex", "");
                        String summary = job.optString("prompt");
                        store.addClip(media, (scene.isEmpty() ? "Video " : "Scene " + scene + " · ") +
                                summary.substring(0, Math.min(36, summary.length())));
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    try { update(job, "error", msg, null); }
                    catch (Exception saveError) { Log.e("RealFrame", "Saving job error", saveError); }
                }
            }
        } finally {
            working.set(false);
            // Stop only when there is no other runnable work; a later user action restarts it.
            stopSelf();
        }
    }

    @Override public void onTimeout(int startId, int fgsType) {
        settings.set("queuePaused", "1");
        stopSelf();
    }

    @Override public void onDestroy() {
        singleWorker.shutdownNow();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
