package net.typeblog.shelter.services;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import androidx.annotation.Nullable;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
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

    // The actual receiver of the screen-off event
    private BroadcastReceiver mLockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (FreezeService.class) {
                if (sAppToFreeze.size() > 0) {
                    long now = new Date().getTime();

                    // Initialize with empty usage stats
                    // If we don't have the permission to use UsageStats
                    // or "do not freeze foreground apps" is not enabled,
                    // then we won't need any usage stats, so we just keep
                    // it empty in those cases
                    Map<String, UsageStats> allStats = new HashMap<>();

                    if (SettingsManager.getInstance().getSkipForegroundEnabled() &&
                            Utility.checkUsageStatsPermission(FreezeService.this)) {
                        UsageStatsManager usm = getSystemService(UsageStatsManager.class);
                        allStats = usm.queryAndAggregateUsageStats(now - APP_INACTIVE_TIMEOUT, now);
                    }
                    DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
                    ComponentName adminComponent = new ComponentName(FreezeService.this, ShelterDeviceAdminReceiver.class);
                    for (String app : sAppToFreeze) {
                        boolean shouldFreeze = true;
                        UsageStats stats =  allStats.get(app);
                        if (stats != null && now - stats.getLastTimeUsed() <= APP_INACTIVE_TIMEOUT &&
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
                unregisterReceiver(this);
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // This is the only thing that we do
        registerReceiver(mLockReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
