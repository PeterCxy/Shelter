package net.typeblog.shelter.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.Utility;

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
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        ComponentName adminComponent = new ComponentName(context.getApplicationContext(), ShelterDeviceAdminReceiver.class);

        // Enable the profile
        manager.setProfileEnabled(adminComponent);

        Utility.enforceWorkProfilePolicies(context);
    }
}
