package net.typeblog.shelter.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.util.ApplicationInfoWrapper;
import net.typeblog.shelter.util.Utility;

import java.util.List;
import java.util.stream.Collectors;

public class ShelterService extends Service {
    private static final String NOTIFICATION_CHANNEL_ID = "ShelterService";
    private DevicePolicyManager mPolicyManager = null;
    private boolean mIsWorkProfile = false;
    private PackageManager mPackageManager = null;
    private IShelterService.Stub mBinder = new IShelterService.Stub() {
        @Override
        public void stopShelterService(boolean kill) {
            // dirty: just wait for some time and kill this service itself
            new Thread(() -> {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {

                }

                ((ShelterApplication) getApplication()).unbindShelterService();

                if (kill) {
                    // Just kill the entire process if this signal is received
                    System.exit(0);
                }
            }).start();
        }

        @Override
        public void getApps(IGetAppsCallback callback) {
            new Thread(() -> {
                List<ApplicationInfoWrapper> list = mPackageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                        .stream()
                        .filter((it) -> !it.packageName.equals(getPackageName()))
                        .filter((it) -> !it.enabled || mPackageManager.getLaunchIntentForPackage(it.packageName) != null)
                        .map(ApplicationInfoWrapper::new)
                        .map((it) -> it.loadLabel(mPackageManager))
                        .collect(Collectors.toList());

                try {
                    callback.callback(list);
                } catch (RemoteException e) {
                    // Do Nothing
                }
            }).start();
        }

        @Override
        public void loadIcon(ApplicationInfo info, ILoadIconCallback callback) {
            new Thread(() -> {
                Bitmap icon = Utility.drawableToBitmap(info.loadUnbadgedIcon(mPackageManager));

                try {
                    callback.callback(icon);
                } catch (RemoteException e) {
                    // Do Nothing
                }
            }).start();
        }
    };

    @Override
    public void onCreate() {
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mPackageManager = getPackageManager();
        mIsWorkProfile = mPolicyManager.isProfileOwnerApp(getPackageName());

        if (mIsWorkProfile) {
            // In work profile, only this service will be running
            // so we have to keep it alive
            setForeground();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void setForeground() {
        // Android O and later: Notification Channel
        // TODO: Maybe backport to pre-O?
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            NotificationChannel chan = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(chan);
        }

        // Create foreground notification to keep the service alive
        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setTicker(getString(R.string.app_name))
                .setContentTitle(getString(R.string.service_title))
                .setContentText(getString(R.string.service_desc))
                .setSmallIcon(R.drawable.ic_notification_white_24dp)
                .build();
        startForeground(1, notification);
    }
}
