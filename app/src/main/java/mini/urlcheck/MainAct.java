package mini.urlcheck;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.HttpURLConnection;
import java.net.URL;

public class MainAct extends AppCompatActivity {
    private static final String IOLA_CHANNEL_ID = "iola.chan";
    private static final int IOLA_NOTIFICATION_ID = 100;
    private static final int IOLA_PERMISSION_REQUEST_CODE = 1001;
    private static final int MSECONDS_BETWEEN_PROBES = 3000; // 3s
    private static final int MSECONDS_CONN_TIMEOUT = 2000; // 2s
    private static final int MSECONDS_CONN_READ_TIMEOUT = 1000; // 1s

    public static final String PREFS_NAME = "app_minipref";
    public static final String KEY_p_id = "KEY_test";
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




    @Override
    public void onDestroy() {
        super.onDestroy();
        if (prober != null) {
            terminated = true;
        }
        saveSettings();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createNotificationChannel();

        setContentView(R.layout.layout);

        Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(v -> finish());

        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetSettings();
            }
        });

        Button applyButton = findViewById(R.id.applyButton);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applySettings();
            }
        });

        loadSettings();

        etHost = findViewById(R.id.inputAddress);
        etHost.setText(host);

        etCode = findViewById(R.id.inputRespCode);
        etCode.setText(code);

        tvStatus = findViewById(R.id.statusText);
        imageView = findViewById(R.id.imageView);


        // Bg thread
        Runnable probeLoop = new Runnable() {
            @Override
            public void run() {
                // Perform background work here
                // Example: long-running computation, network request
                // Do NOT interact directly with UI elements from here
                System.out.println("Performing work in background thread.");
                // If you need to update the UI, use a Handler or runOnUiThread()
                try {
                    runProbeLoop();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        prober = new Thread(probeLoop);
        prober.start();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(IOLA_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this.
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void runProbeLoop() throws InterruptedException {
        while (!terminated) {
            probe();
            Thread.sleep(MSECONDS_BETWEEN_PROBES);
        }
    }

    private void probe() {
        final String[] uiMessage = {  "Connecting to " + host + "..." };
        HttpURLConnection connection = null;
        String reason = null;
        boolean success = false;
        try {
            // runOnUiThread(() -> applyStatus(uiMessage[0]));
            URL url = new URL(host);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(MSECONDS_CONN_TIMEOUT);
            connection.setReadTimeout(MSECONDS_CONN_READ_TIMEOUT);
            connection.setRequestMethod("GET");
            String responseCode = "" + connection.getResponseCode();
            success = responseCode.equalsIgnoreCase(code);
            if (!success) {
                reason = responseCode + " <> " + code;
            }
        }
        catch (Exception ex) {
            reason = ex.getMessage();
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
            uiMessage[0] = probeResult(success, reason);
            runOnUiThread(() -> applyStatus(uiMessage[0]));
        }
    }

    private String probeResult(boolean success, String reason) {
        runOnUiThread(() -> imageView.setImageResource(success? R.drawable.connected : R.drawable.disconnected));
        return success? "Success!" : "Failed: " + reason;
    }

    private void saveSettings() {
        SharedPreferences settings;
        SharedPreferences.Editor editor;
        settings = getSharedPreferences(PREFS_NAME, 0);
        editor = settings.edit();
        editor.putString(US_HOST, host);
        editor.putString(US_CODE, code);
        editor.apply();
    }

    private void loadSettings() {
        SharedPreferences settings;
        SharedPreferences.Editor editor;
        settings = getSharedPreferences(PREFS_NAME, 0);
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
