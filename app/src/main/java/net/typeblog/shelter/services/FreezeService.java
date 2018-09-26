package net.typeblog.shelter.services;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

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

    // The actual receiver of the screen-off event
    private BroadcastReceiver mLockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (FreezeService.class) {
                if (sAppToFreeze.size() > 0) {
                    DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
                    ComponentName adminComponent = new ComponentName(FreezeService.this, ShelterDeviceAdminReceiver.class);
                    for (String app : sAppToFreeze) {
                        dpm.setApplicationHidden(adminComponent, app, true);
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
