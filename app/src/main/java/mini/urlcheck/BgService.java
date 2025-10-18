package mini.urlcheck;

import static mini.urlcheck.MainAct.BG_THREAD_NAME;
import static mini.urlcheck.MainAct.BG_UI_UPDATE_ACTION;
import static mini.urlcheck.MainAct.MAIN_NOTIFICATION_ID;
import static mini.urlcheck.MainAct.NOTIFICATION_CHANNEL_ID;
import static mini.urlcheck.MainAct.US_CODE_DEFAULT;
import static mini.urlcheck.MainAct.US_HOST_DEFAULT;

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
    public static final String UI_KEY_REASON = "reason";
    public static final String UI_KEY_MESSAGE = "message";
    public static final String UI_KEY_ADDRESS = "host";
    public static final String UI_KEY_CODE = "code";

    public static final int MSECONDS_BETWEEN_PROBES = 3000; // 3s
    public static final int MSECONDS_CONN_TIMEOUT = 2000; // 2s
    public static final int MSECONDS_CONN_READ_TIMEOUT = 1000; // 1s

    private String host = US_HOST_DEFAULT;
    private String code = US_CODE_DEFAULT;

    private HandlerThread handlerThread = null;
    private boolean terminated = false;

    private AtomicBoolean lastState = null;

    private NotificationManager notificationManager = null;
    private NotificationCompat.Builder notificationBuilder = null;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        BgService getService() {
            return BgService.this;
        }
    }

    public void applySettings(String host, String code) {
        this.host = host;
        this.code = code;
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
        host = intent.getStringExtra(UI_KEY_ADDRESS);
        code = intent.getStringExtra(UI_KEY_CODE);
        notificationBuilder = createNotificationBuilder();
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(MAIN_NOTIFICATION_ID, notificationBuilder.build());
        int type = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        }
        ServiceCompat.startForeground(this, MAIN_NOTIFICATION_ID, notificationBuilder.build(), type);
        return START_NOT_STICKY; // super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        handlerThread = new HandlerThread(BG_THREAD_NAME, android.os.Process.THREAD_PRIORITY_BACKGROUND) {
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

    private void runProbeLoop() throws InterruptedException {
        while (!terminated) {
            probe();
            Thread.sleep(MSECONDS_BETWEEN_PROBES);
        }
    }

    private void probe() {
        String uiMessage = "Connecting to " + host + "...";
        HttpURLConnection connection = null;
        String reason = null;
        boolean success = false;
        try {
            System.out.println("probing...");
            // runOnUiThread(() -> applyStatus(uiMessage[0]));
            URL url = new URL(host);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(MSECONDS_CONN_TIMEOUT);
            connection.setReadTimeout(MSECONDS_CONN_READ_TIMEOUT);
            connection.setRequestMethod("HEAD");
            String responseCode = "" + connection.getResponseCode();
            success = responseCode.equalsIgnoreCase(code);
            if (!success) {
                reason = responseCode + " <> " + code;
            }
        } catch (Exception ex) {
            reason = ex.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            uiMessage = probeResult(success, reason);
        }
        Intent intent = new Intent(BG_UI_UPDATE_ACTION);
        intent.putExtra(UI_KEY_SUCCESS,  "" + success);
        intent.putExtra(UI_KEY_REASON, reason);
        intent.putExtra(UI_KEY_MESSAGE, uiMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private String probeResult(boolean success, String reason) {
        boolean silent = lastState == null || lastState.get() == success;
        if (notificationBuilder != null) {
            notificationBuilder.setContentTitle(success ? "Connected" : "Disconnected");
            notificationBuilder.setContentText(success ? (host + " is available (" + code + ")") : reason);
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
        return success ? "Success!" : "Failed: " + reason;
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
