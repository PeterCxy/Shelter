package net.typeblog.shelter.services;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.support.annotation.Nullable;

import net.typeblog.shelter.ShelterApplication;

import java.util.List;

public class ShelterService extends Service {
    private DevicePolicyManager mPolicyManager = null;
    private boolean mIsWorkProfile = false;
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
        public List<ResolveInfo> getApps() {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            return getPackageManager().queryIntentActivities(mainIntent, 0);
        }
    };

    @Override
    public void onCreate() {
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mIsWorkProfile = mPolicyManager.isProfileOwnerApp(getPackageName());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
