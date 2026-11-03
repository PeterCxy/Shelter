package net.typeblog.shelter.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class ShelterDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        // On Android 8.0+ (API 26+), finalization is handled via
        // FinalizeActivity responding to ACTION_PROVISIONING_SUCCESSFUL.
    }
}
