package mini.urlcheck;

import static mini.urlcheck.BgService.UI_KEY_MESSAGE;
import static mini.urlcheck.BgService.UI_KEY_SUCCESS;
import static mini.urlcheck.Settings.APP_ID;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainAct extends AppCompatActivity {
    public static final String NOTIFICATION_CHANNEL_ID = APP_ID + ".chan";
    public static final String BG_UI_UPDATE_ACTION = APP_ID + ".action";
    public static final String BG_THREAD_NAME = APP_ID + ".thread";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    public static final int MAIN_NOTIFICATION_ID = 100;
    private EditText etHost;
    private EditText etCode;
    private EditText etCycle;
    private TextView etTimeout;
    private TextView tvStatus;
    private ImageView imageView;
    private LinearLayout moreSettingsLayout;
    private Button moreLessButton;
    private NotificationManager notificationManager = null;
    private BgService bgService;
    private Settings settings;
    private boolean moreSettings = false;

    private final ServiceConnection bgServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BgService.LocalBinder binder = (BgService.LocalBinder) service;
            bgService = binder.getService();
            bgService.applySettings(settings);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bgService = null;
        }
    };

    static class UiBroadcastReceiver extends BroadcastReceiver {
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

    private final UiBroadcastReceiver broadcastReceiver = new UiBroadcastReceiver(this);

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
        loadSettings();
        setContentView(R.layout.layout);

        Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(v -> finish());

        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> resetSettings());

        moreLessButton = findViewById(R.id.moreLessButton);
        moreLessButton.setOnClickListener(v -> showHideMoreSettings());

        Button applyButton = findViewById(R.id.applyButton);
        applyButton.setOnClickListener(v -> updateSettingsFromUI());

        moreSettingsLayout = findViewById(R.id.settingsLayout);
        etHost = findViewById(R.id.inputAddress);
        etCode = findViewById(R.id.inputRespCode);
        etCycle = findViewById(R.id.inputCycle);
        etTimeout = findViewById(R.id.inputTimeout);
        tvStatus = findViewById(R.id.statusText);
        imageView = findViewById(R.id.imageView);

        setMoreSettingsVisibility();
        updateUiFromSettings();
        startWorkerService();
    }

    private void showHideMoreSettings() {
        moreSettings = !moreSettings;
        setMoreSettingsVisibility();
    }

    private void setMoreSettingsVisibility() {
        moreSettingsLayout.setVisibility(moreSettings? View.VISIBLE : View.GONE);
        moreLessButton.setText(moreSettings? R.string.lessButton_text : R.string.moreButton_text);
    }

    private void startWorkerService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.FOREGROUND_SERVICE}, NOTIFICATION_PERMISSION_REQUEST_CODE);
        } else {
            Intent startIntent = new Intent(this, BgService.class);
//            startIntent.putExtra(UI_KEY_ADDRESS, host);
//            startIntent.putExtra(UI_KEY_CODE, code);
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        /*
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
            }
        }
        */
    }

    private void saveSettings() {
        settings.saveToSharedPreferences(getSharedPreferences(APP_ID, 0));
    }

    private void loadSettings() {
        settings = Settings.readFromSharedPreferences(
                getSharedPreferences(APP_ID, 0));
    }
    private void updateUiFromSettings() {
        etHost.setText(settings.getHost());
        etCode.setText(getString(R.string.inputNum_text, settings.getCode()));
        etCycle.setText(getString(R.string.inputNum_text, settings.getCycleDuration()/Settings.MSEC_SCALE_FACTOR));
        etTimeout.setText(getString(R.string.inputNum_text, settings.getNetworkTimeout()/Settings.MSEC_SCALE_FACTOR));
    }

    private void resetSettings() {
        settings.resetDefaults();
        updateUiFromSettings();
        saveSettings();
    }

    private void updateSettingsFromUI() {
        try {
            settings = new Settings(
                    etHost.getText().toString(),
                    Integer.parseInt(etCode.getText().toString()),
                    Settings.MSEC_SCALE_FACTOR * Integer.parseInt(etCycle.getText().toString()),
                    Settings.MSEC_SCALE_FACTOR * Integer.parseInt(etTimeout.getText().toString()));
            bgService.applySettings(settings);
            updateUiFromSettings();
            saveSettings();
        }
        catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void applyStatus(boolean success, String text) {
        runOnUiThread(() -> {
            tvStatus.setText(text);
            imageView.setImageResource(success ? R.drawable.connected : R.drawable.disconnected);
        });
    }
}
