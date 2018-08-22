package net.typeblog.shelter.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import net.typeblog.shelter.ui.DummyActivity;
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
        // I don't know why setting the policies in this receiver won't work very well
        // Anyway, we delegate it to the DummyActivity
        Intent i = new Intent(context.getApplicationContext(), DummyActivity.class);
        i.setAction(DummyActivity.FINALIZE_PROVISION);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}
