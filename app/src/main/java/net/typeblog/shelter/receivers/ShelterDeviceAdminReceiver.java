package net.typeblog.shelter.receivers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.Utility;

public class ShelterDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final int NOTIFICATION_ID = 114514;

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
        // Delegate starting activity to notification so we won't break on Android 10
        // And also maybe this will fix bugs on stupid custom OSes like MIUI / EMUI
        Notification notification = Utility.buildNotification(context, true,
                "shelter-finish-provision",
                context.getString(R.string.finish_provision_title),
                context.getString(R.string.finish_provision_desc),
                R.drawable.ic_notification_white_24dp);
        notification.contentIntent = PendingIntent.getActivity(context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.flags |= Notification.FLAG_AUTO_CANCEL | Notification.FLAG_ONGOING_EVENT;
        context.getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification);
    }
}
