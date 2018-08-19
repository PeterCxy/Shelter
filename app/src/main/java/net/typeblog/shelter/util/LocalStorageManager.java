package net.typeblog.shelter.util;

import android.content.Context;
import android.content.SharedPreferences;

public class LocalStorageManager {
    public static final String PREF_IS_DEVICE_ADMIN = "is_device_admin";
    public static final String PREF_HAS_SETUP = "has_setup";

    private static LocalStorageManager sInstance = null;
    private SharedPreferences mPrefs = null;

    private LocalStorageManager(Context context) {
        mPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    // Should be called in Application class
    public static void initialize(Context context) {
        sInstance = new LocalStorageManager(context);
    }

    public static LocalStorageManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("LocalStorageManager must be initialized at start-up");
        }
        return sInstance;
    }

    // === Wrapper methods from SharedPreferences ===
    public boolean getBoolean(String pref) {
        return mPrefs.getBoolean(pref, false);
    }

    public void setBoolean(String pref, boolean value) {
        mPrefs.edit().putBoolean(pref, value).apply();
    }
}
