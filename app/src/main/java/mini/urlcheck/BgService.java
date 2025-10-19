package mini.urlcheck;

import static mini.urlcheck.MainAct.BG_THREAD_NAME;
import static mini.urlcheck.MainAct.BG_UI_UPDATE_ACTION;
import static mini.urlcheck.MainAct.MAIN_NOTIFICATION_ID;
import static mini.urlcheck.MainAct.NOTIFICATION_CHANNEL_ID;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.HandlerThread;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class BgService extends Service {
    public static final String UI_KEY_SUCCESS = "success";
    public static final String UI_KEY_MESSAGE = "message";

    private boolean terminated = false;
    private AtomicBoolean lastState = null;

    private NotificationManager notificationManager = null;
    private NotificationCompat.Builder notificationBuilder = null;
    private Settings settings;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        BgService getService() {
            return BgService.this;
        }
    }

    public void applySettings(Settings settings) {
        this.settings = settings;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent,
                               int flags,
                               int startId) {
        notificationBuilder = createNotificationBuilder();
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(MAIN_NOTIFICATION_ID, notificationBuilder.build());
        ServiceCompat.startForeground(this, MAIN_NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        HandlerThread handlerThread = new HandlerThread(BG_THREAD_NAME, android.os.Process.THREAD_PRIORITY_BACKGROUND) {
            @Override
            public void run() {
                try {
                    runProbeLoop();
                } catch (InterruptedException e) {
                    stopSelf();
                    throw new RuntimeException(e);
                }
            }
        };
        handlerThread.start();
    }

    @SuppressWarnings("BusyWait")
    private void runProbeLoop() throws InterruptedException {
        while (!terminated && settings == null) {
            Thread.sleep(Settings.MSEC_SLEEP_DEFAULT);
        }
        while (!terminated) {
            probe();
            Thread.sleep(settings.getCycleDuration());
        }
    }

    private void probe() {
        HttpURLConnection connection = null;
        String reason = null;
        boolean success = false;
        long reqTime = System.currentTimeMillis();
        try {
            URL url = new URL(settings.getHost());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(settings.getNetworkTimeout());
            connection.setReadTimeout(settings.getNetworkTimeout());
            connection.setRequestMethod("HEAD");
            success = connection.getResponseCode() == settings.getCode();
            if (!success) {
                reason = connection.getResponseCode() + " <> " + settings.getCode();
            }
        } catch (Exception ex) {
            reason = ex.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            reqTime = System.currentTimeMillis() - reqTime;
        }
        String uiMessage = probeResult(success, reason, reqTime);
        Intent intent = new Intent(BG_UI_UPDATE_ACTION);
        intent.putExtra(UI_KEY_SUCCESS,  "" + success);
        intent.putExtra(UI_KEY_MESSAGE, uiMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private String probeResult(boolean success, String reason, long reqTime) {
        boolean silent = lastState == null || lastState.get() == success;
        if (notificationBuilder != null) {
            notificationBuilder.setContentTitle(success ? "Connected" : "Disconnected");
            notificationBuilder.setContentText(success ? (settings.getHost() + " is available (" + settings.getCode() + ")") : reason);
            notificationBuilder.setSmallIcon(success ? R.drawable.ic_ni_connected : R.drawable.ic_ni_disconnected);
            notificationBuilder.setSilent(silent);
            if (notificationManager != null) {
                notificationManager.notify(MAIN_NOTIFICATION_ID, notificationBuilder.build());
            }
        }
        if (lastState == null) {
            lastState = new AtomicBoolean(success);
        } else {
            lastState.set(success);
        }
        return success ? "Success, " + reqTime + " ms" : "Failed after " + reqTime + " ms: " + reason;
    }


    private NotificationCompat.Builder createNotificationBuilder() {
        Intent notificationIntent = new Intent(this, MainAct.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ni_connected)
                .setContentTitle("Loading...")
                .setContentText("please wait :)")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setSilent(true);
    }

    @Override
    public void onDestroy() {
        terminated = true;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (notificationManager != null) {
            notificationManager.cancel(MAIN_NOTIFICATION_ID);
        }
        Toast.makeText(this, "service stopped", Toast.LENGTH_SHORT).show();
    }
}
