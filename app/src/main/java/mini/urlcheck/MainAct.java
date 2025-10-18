package mini.urlcheck;

import static mini.urlcheck.BgService.UI_KEY_ADDRESS;
import static mini.urlcheck.BgService.UI_KEY_CODE;
import static mini.urlcheck.BgService.UI_KEY_MESSAGE;
import static mini.urlcheck.BgService.UI_KEY_SUCCESS;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainAct extends AppCompatActivity {
    public static final String NOTIFICATION_CHANNEL_ID = "mini.chan";
    public static final String BG_UI_UPDATE_ACTION = NOTIFICATION_CHANNEL_ID + ".action";
    public static final String BG_THREAD_NAME = NOTIFICATION_CHANNEL_ID + ".thread";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    public static final int MAIN_NOTIFICATION_ID = 100;
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
    private NotificationManager notificationManager = null;
    private BgService bgService;

    private ServiceConnection bgServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BgService.LocalBinder binder = (BgService.LocalBinder) service;
            bgService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bgService = null;
        }
    };

    class UiBroadcastReceiver extends BroadcastReceiver {
        private final MainAct mainAct;

        public UiBroadcastReceiver(MainAct mainAct) {
            this.mainAct = mainAct;
        }

        public void onReceive(Context context, Intent intent) {
            String successStr = intent.getStringExtra(UI_KEY_SUCCESS);
            String message = intent.getStringExtra(UI_KEY_MESSAGE);
            if (message != null) {
                mainAct.applyStatus(successStr != null && Boolean.parseBoolean(successStr), message);
            }
        }
    }

    private UiBroadcastReceiver broadcastReceiver = new UiBroadcastReceiver(this);

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(BG_UI_UPDATE_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopService(new Intent(this, BgService.class));
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
        startWorkerService();
    }

    private void startWorkerService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.FOREGROUND_SERVICE}, NOTIFICATION_PERMISSION_REQUEST_CODE);
        } else {
            Intent startIntent = new Intent(this, BgService.class);
            startIntent.putExtra(UI_KEY_ADDRESS, host);
            startIntent.putExtra(UI_KEY_CODE, code);
            startService(startIntent);
        }

        Intent bindIntent = new Intent(this, BgService.class);
        bindService(bindIntent, bgServiceConnection, Context.BIND_AUTO_CREATE);
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
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // createNotification();
            } else {
                // Permission denied by the user
            }
        }
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
        bgService.applySettings(host, code);
        saveSettings();
    }

    private void applySettings() {
        host = etHost.getText().toString();
        code = etCode.getText().toString();
        bgService.applySettings(host, code);
        saveSettings();
    }

    public void applyStatus(boolean success, String text) {
        runOnUiThread(() -> {
            tvStatus.setText(text);
            imageView.setImageResource(success ? R.drawable.connected : R.drawable.disconnected);
        });
    }
}
