package net.typeblog.shelter.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import net.typeblog.shelter.R;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.Utility;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// This service simply registers a screen-off listener that will be called
// when the user locks the screen. When this happens, this service
// will freeze all the apps that the user launched through Unfreeze & Launch
// during the last session.
public class FreezeService extends Service {
    // Use a static variable and static methods to store the current list to be frozen
    // We don't need to run this service in another process, so the static context should
    // be sufficient for this. DummyActivity will use these static methods to add more apps
    // to the list
    private static List<String> sAppToFreeze = new ArrayList<>();
    public static synchronized void registerAppToFreeze(String app) {
        if (!sAppToFreeze.contains(app)) {
            sAppToFreeze.add(app);
        }
    }

    public static synchronized boolean hasPendingAppToFreeze() {
        return sAppToFreeze.size() > 0;
    }

    // An app being inactive for this amount of time will be frozen
    private static long APP_INACTIVE_TIMEOUT = 1000;

    // Notification ID
    private static int NOTIFICATION_ID = 0xe49c0;

    // The actual receiver of the screen-off event
    private BroadcastReceiver mLockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Save usage statistics right now!
            // We need to use the statics at this moment
            // for "skipping foreground apps"
            // No app is foreground after the screen is locked.
            mScreenLockTime = new Date().getTime();
            if (SettingsManager.getInstance().getSkipForegroundEnabled() &&
                    Utility.checkUsageStatsPermission(FreezeService.this)) {
                UsageStatsManager usm = getSystemService(UsageStatsManager.class);
                mUsageStats = usm.queryAndAggregateUsageStats(mScreenLockTime - APP_INACTIVE_TIMEOUT, mScreenLockTime);
            }

            // Delay the work so that it can be canceled if the screen
            // gets unlocked before the delay passes
            mHandler.postDelayed(mFreezeWork,
                    ((long) SettingsManager.getInstance().getAutoFreezeDelay()) * 1000);
            registerReceiver(mUnlockReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        }
    };

    // The receiver of the screen-on event
    // Cancels the freeze job if the designated delay has not passed
    private BroadcastReceiver mUnlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.removeCallbacks(mFreezeWork);
        }
    };

    // Usage statistics when the screen was locked
    // We keep it here since we need the data AT THE MOMENT when screen gets locked
    // If we don't have the permission to use UsageStats
    // or "do not freeze foreground apps" is not enabled,
    // then we won't need any usage stats, so we just keep
    // it empty in those cases
    private Map<String, UsageStats> mUsageStats = new HashMap<>();
    private long mScreenLockTime = -1;

    // The handler and the delayed work to handle
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mFreezeWork = () -> {
        synchronized (FreezeService.class) {
            // Cancel the unlock receiver first - the delay has passed if this work is executed
            unregisterReceiver(mUnlockReceiver);

            if (sAppToFreeze.size() > 0) {
                DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
                ComponentName adminComponent = new ComponentName(FreezeService.this, ShelterDeviceAdminReceiver.class);
                for (String app : sAppToFreeze) {
                    boolean shouldFreeze = true;
                    UsageStats stats =  mUsageStats.get(app);
                    if (stats != null && mScreenLockTime - stats.getLastTimeUsed() <= APP_INACTIVE_TIMEOUT &&
                            stats.getTotalTimeInForeground() >= APP_INACTIVE_TIMEOUT) {
                        // Don't freeze foreground apps if requested
                        shouldFreeze = false;
                    }

                    if (shouldFreeze) {
                        dpm.setApplicationHidden(adminComponent, app, true);
                    }
                }
                sAppToFreeze.clear();
            }
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // This is the only thing that we do
        registerReceiver(mLockReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        // Use foreground notification to keep this service alive until screen is locked
        setForeground();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mLockReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setForeground() {
        Notification notification = Utility.buildNotification(this,
                getString(R.string.service_auto_freeze_title),
                getString(R.string.service_auto_freeze_title),
                getString(R.string.service_auto_freeze_desc),
                R.drawable.ic_lock_open_white_24dp
        );

        // Add a quick action to freeze all applications in list right now
        // by just reusing the intent for the "freeze all" desktop shortcut
        Intent intentFreeze = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
        // The intent for the shortcut lives in the main profile, while this
        // service runs in the work profile.
        Utility.transferIntentToProfileUnsigned(this, intentFreeze);
        notification.actions = new Notification.Action[] {
                new Notification.Action.Builder(
                        null, getString(R.string.service_auto_freeze_now),
                        PendingIntent.getActivity(this, 0, intentFreeze, 0)
                ).build()
        };

        // Show the notification and begin foreground operation
        startForeground(NOTIFICATION_ID, notification);
    }
}
