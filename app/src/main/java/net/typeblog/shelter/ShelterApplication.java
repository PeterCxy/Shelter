package net.typeblog.shelter;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import net.typeblog.shelter.services.ShelterService;
import net.typeblog.shelter.util.LocalStorageManager;

public class ShelterApplication extends Application {
    private ServiceConnection mShelterServiceConnection = null;

    @Override
    public void onCreate() {
        super.onCreate();
        LocalStorageManager.initialize(this);
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
