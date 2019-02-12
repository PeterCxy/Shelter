package net.typeblog.shelter.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import net.typeblog.shelter.ui.CameraProxyActivity;
import net.typeblog.shelter.ui.DummyActivity;

public class SettingsManager {
    private static SettingsManager sInstance = null;

    public static void initialize(Context context) {
        sInstance = new SettingsManager(context);
    }

    public static SettingsManager getInstance() {
        return sInstance;
    }

    private LocalStorageManager mStorage = LocalStorageManager.getInstance();
    private Context mContext;

    private SettingsManager(Context context) {
        mContext = context;
    }

    private void syncSettingsToProfileBool(String name, boolean value) {
        Intent intent = new Intent(DummyActivity.SYNCHRONIZE_PREFERENCE);
        intent.putExtra("name", name);
        intent.putExtra("boolean", value);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utility.transferIntentToProfile(mContext, intent);
        mContext.startActivity(intent);
    }

    private void syncSettingsToProfileInt(String name, int value) {
        Intent intent = new Intent(DummyActivity.SYNCHRONIZE_PREFERENCE);
        intent.putExtra("name", name);
        intent.putExtra("int", value);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utility.transferIntentToProfile(mContext, intent);
        mContext.startActivity(intent);
    }

    // Enforce all settings
    public void applyAll() {
        applyCrossProfileFileChooser();
        applyCameraProxy();
    }

    // Read and apply the enabled state of the cross profile file chooser
    public void applyCrossProfileFileChooser() {
        boolean enabled = mStorage.getBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER);
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, CrossProfileDocumentsProvider.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    // Read and apply the enabled state of the camera proxy
    public void applyCameraProxy() {
        boolean enabled = mStorage.getBoolean(LocalStorageManager.PREF_CAMERA_PROXY);
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, CameraProxyActivity.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    // Set the enabled state of the cross profile file chooser
    public void setCrossProfileFileChooserEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER, enabled);
        applyCrossProfileFileChooser();
        syncSettingsToProfileBool(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER, enabled);
    }

    // Get the enabled state of the cross profile file chooser
    public boolean getCrossProfileFileChooserEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER);
    }

    // Set the enabled state of the cross profile file chooser
    public void setCameraProxyEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_CAMERA_PROXY, enabled);
        applyCameraProxy();
        syncSettingsToProfileBool(LocalStorageManager.PREF_CAMERA_PROXY, enabled);
    }

    // Get the enabled state of the cross profile file chooser
    public boolean getCameraProxyEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_CAMERA_PROXY);
    }

    // Set the enabled state of the auto freeze service
    // This does NOT need to be synchronized nor applied across profile
    public void setAutoFreezeServiceEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_AUTO_FREEZE_SERVICE, enabled);
    }

    // Get the enabled state of the auto freeze service
    public boolean getAutoFreezeServiceEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_AUTO_FREEZE_SERVICE);
    }

    // Set the delay for auto freeze service (in seconds)
    public void setAutoFreezeDelay(int seconds) {
        mStorage.setInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY, seconds);
        syncSettingsToProfileInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY, seconds);
    }

    // Get the delay for auto freeze service
    public int getAutoFreezeDelay() {
        int ret = mStorage.getInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY);
        if (ret == Integer.MIN_VALUE) {
            // Default delay is 0 seconds
            ret = 0;
        }
        return ret;
    }

    // Set the enabled state of "skip foreground"
    public void setSkipForegroundEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND, enabled);
        syncSettingsToProfileBool(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND, enabled);
    }

    // Get the enabled state of "skip foreground"
    public boolean getSkipForegroundEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND);
    }

    // Set the enabled state of "Fingerprint Authentication"
    // This does NOT need to be synchronized nor applied across profile
    public void setFingerprintAuthEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_FINGERPRINT_AUTH, enabled);
    }

    // Get the enabled state of "Fingerprint Authentication"
    public boolean getFingerprintAuthEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_FINGERPRINT_AUTH);
    }
}
