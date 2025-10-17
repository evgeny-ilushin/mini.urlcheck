package mini.urlcheck;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.net.HttpURLConnection;
import java.net.URL;

public class MainAct extends AppCompatActivity {
    private static final String NOTIFICATION_CHANNEL_ID = "mini.chan";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final int MAIN_NOTIFICATION_ID = 100;
    private static final int MSECONDS_BETWEEN_PROBES = 3000; // 3s
    private static final int MSECONDS_CONN_TIMEOUT = 2000; // 2s
    private static final int MSECONDS_CONN_READ_TIMEOUT = 1000; // 1s

    public static final String PREFS_NAME = "app_minipref";
    public static final String US_HOST = "HOST";
    public static final String US_CODE = "CODE";
    public static final String US_HOST_DEFAULT = "https://www.google.com";
    public static final String US_CODE_DEFAULT = "200";

    private String host = US_HOST_DEFAULT;
    private String code = US_CODE_DEFAULT;

    private EditText etHost;
    private EditText etCode;
    private TextView tvStatus;

    private ImageView imageView;

    private Thread prober = null;
    private boolean terminated = false;

    private NotificationManager notificationManager = null;
    private NotificationCompat.Builder notificationBuilder = null;


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (prober != null) {
            terminated = true;
        }
        saveSettings();
        if (notificationManager != null) {
            notificationManager.cancel(MAIN_NOTIFICATION_ID);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createNotificationChannel();
        setContentView(R.layout.layout);

        Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(v -> finish());

        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> resetSettings());

        Button applyButton = findViewById(R.id.applyButton);
        applyButton.setOnClickListener(v -> applySettings());

        loadSettings();

        etHost = findViewById(R.id.inputAddress);
        etHost.setText(host);

        etCode = findViewById(R.id.inputRespCode);
        etCode.setText(code);

        tvStatus = findViewById(R.id.statusText);
        imageView = findViewById(R.id.imageView);

        // Bg thread
        Runnable probeLoop = () -> {
            try {
                runProbeLoop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        prober = new Thread(probeLoop);
        prober.start();
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this.
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
        } else {
            Intent intent = new Intent(this, MainAct.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

            notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_ni_connected)
                    .setContentTitle("mini UrlCheck")
                    .setContentText("Offline - disconnected")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    // Set the intent that fires when the user taps the notification.
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(false)
                    .setSilent(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted by the user
                // ...
            } else {
                // Permission denied by the user
                // ...
            }
        }
    }

    private void runProbeLoop() throws InterruptedException {
        while (!terminated) {
            probe();
            Thread.sleep(MSECONDS_BETWEEN_PROBES);
        }
    }

    private void probe() {
        final String[] uiMessage = {"Connecting to " + host + "..."};
        HttpURLConnection connection = null;
        String reason = null;
        boolean success = false;
        try {
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
            uiMessage[0] = probeResult(success, reason);
            runOnUiThread(() -> applyStatus(uiMessage[0]));
        }
    }

    private String probeResult(boolean success, String reason) {
        runOnUiThread(() -> {
            notificationBuilder.setContentText(success ? "Online" : "Offline: " + reason);
            notificationBuilder.setSmallIcon(success ? R.drawable.ic_ni_connected : R.drawable.ic_ni_disconnected);
            notificationBuilder.setSilent(true);
            imageView.setImageResource(success ? R.drawable.connected : R.drawable.disconnected);
            if (notificationManager != null) {
                notificationManager.notify(MAIN_NOTIFICATION_ID, notificationBuilder.build());
            }
        });
        return success ? "Success!" : "Failed: " + reason;
    }

    private void saveSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(US_HOST, host);
        editor.putString(US_CODE, code);
        editor.apply();
    }

    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        host = settings.getString(US_HOST, US_HOST_DEFAULT);
        code = settings.getString(US_CODE, US_CODE_DEFAULT);
    }

    private void resetSettings() {
        etHost.setText(host = US_HOST_DEFAULT);
        etCode.setText(code = US_CODE_DEFAULT);
        saveSettings();
    }

    private void applySettings() {
        host = etHost.getText().toString();
        code = etCode.getText().toString();
        applyStatus("Connecting to " + host + "...");
        saveSettings();
    }

    private void applyStatus(String text) {
        tvStatus.setText(text);
    }
}
