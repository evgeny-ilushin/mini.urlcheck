package mini.urlcheck;

import android.content.SharedPreferences;

public class Settings {
    public static final String APP_ID = "mini.urlcheck";

    public static final String US_HOST = "host";
    public static final String US_CODE = "code";
    public static final String US_CYCLE = "cycle";
    public static final String US_TIMEOUT = "timeout";

    public static final int MSEC_SCALE_FACTOR = 1000;
    public static final int MSEC_SLEEP_DEFAULT = 100;

    public static final String US_HOST_DEFAULT = "https://www.google.com";
    public static final int US_CODE_DEFAULT = 200;
    public static final int US_CYCLE_DEFAULT = 5000;
    public static final int US_CYCLE_MIN = 1000;
    public static final int US_CYCLE_MAX = 3600000;
    public static final int US_TIMEOUT_DEFAULT = 5000;
    public static final int US_TIMEOUT_MIN = 1000;
    public static final int US_TIMEOUT_MAX = 60000;

    private String host;
    private int code;
    private int cycleDuration;
    private int networkTimeout;

    public Settings(String host, int code, int cycleDuration, int networkTimeout) {
        this.host = host;
        this.code = code;
        this.cycleDuration = cycleDuration;
        this.networkTimeout = networkTimeout;

        if (this.cycleDuration < US_CYCLE_MIN)
            this.cycleDuration = US_CYCLE_MIN;
        if (this.cycleDuration > US_CYCLE_MAX)
            this.cycleDuration = US_CYCLE_MAX;

        if (this.networkTimeout < US_TIMEOUT_MIN)
            this.networkTimeout = US_TIMEOUT_MIN;
        if (this.networkTimeout > US_TIMEOUT_MAX)
            this.networkTimeout = US_TIMEOUT_MAX;
    }

    public static Settings readFromSharedPreferences(SharedPreferences sharedPreferences) {
        return new Settings(
                sharedPreferences.getString(US_HOST, US_HOST_DEFAULT),
                sharedPreferences.getInt(US_CODE, US_CODE_DEFAULT),
                sharedPreferences.getInt(US_CYCLE, US_CYCLE_DEFAULT),
                sharedPreferences.getInt(US_TIMEOUT, US_TIMEOUT_DEFAULT));
    }

    public void saveToSharedPreferences(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(US_HOST, host);
        editor.putInt(US_CODE, code);
        editor.putInt(US_CYCLE, cycleDuration);
        editor.putInt(US_TIMEOUT, networkTimeout);
        editor.apply();
    }

    public void resetDefaults() {
        host = US_HOST_DEFAULT;
        code = US_CODE_DEFAULT;
        cycleDuration = US_CYCLE_DEFAULT;
        networkTimeout = US_TIMEOUT_DEFAULT;
    }

    public String getHost() {
        return host;
    }

    public int getCode() {
        return code;
    }

    public int getCycleDuration() {
        return cycleDuration;
    }

    public int getNetworkTimeout() {
        return networkTimeout;
    }
}
