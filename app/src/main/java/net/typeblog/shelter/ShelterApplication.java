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

    public void bindShelterService(ServiceConnection conn, boolean foreground) {
        unbindShelterService();
        Intent intent = new Intent(getApplicationContext(), ShelterService.class);
        intent.putExtra("foreground", foreground);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        mShelterServiceConnection = conn;
    }

    public void unbindShelterService() {
        if (mShelterServiceConnection != null) {
            try {
                unbindService(mShelterServiceConnection);
            } catch (Exception e) {
                // This method call might fail if the service is already unbound
                // just ignore anything that might happen.
                // We will be stopping already if this would ever happen.
            }
        }

        mShelterServiceConnection = null;
    }
}
