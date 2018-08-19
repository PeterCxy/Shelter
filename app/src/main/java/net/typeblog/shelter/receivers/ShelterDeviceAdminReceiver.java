package net.typeblog.shelter.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import net.typeblog.shelter.ui.MainActivity;
import net.typeblog.shelter.util.LocalStorageManager;

public class ShelterDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        LocalStorageManager.getInstance().setBoolean(LocalStorageManager.PREF_IS_DEVICE_ADMIN, true);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        LocalStorageManager.getInstance().setBoolean(LocalStorageManager.PREF_IS_DEVICE_ADMIN, false);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);

        // Enable the profile
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        manager.setProfileEnabled(new ComponentName(context.getApplicationContext(), ShelterDeviceAdminReceiver.class));

        // Hide this app in the work profile
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context.getApplicationContext(), MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
    }
}
