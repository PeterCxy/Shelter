package net.typeblog.shelter;

import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import net.typeblog.shelter.services.ShelterService;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.Utility;

public class ShelterApplication extends Application {
    private ServiceConnection mShelterServiceConnection = null;

    @Override
    public void onCreate() {
        super.onCreate();
        LocalStorageManager.initialize(this);

        if (getSystemService(DevicePolicyManager.class).isProfileOwnerApp(getPackageName())) {
            // If we are the profile owner, we enforce all our policies
            // so that we can make sure those are updated with our app
            Utility.enforceWorkProfilePolicies(this);
        }
    }

    public void bindShelterService(ServiceConnection conn) {
        unbindShelterService();
        Intent intent = new Intent(getApplicationContext(), ShelterService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        mShelterServiceConnection = conn;
    }

    public void unbindShelterService() {
        if (mShelterServiceConnection != null) {
            unbindService(mShelterServiceConnection);
        }

        mShelterServiceConnection = null;
    }
}
