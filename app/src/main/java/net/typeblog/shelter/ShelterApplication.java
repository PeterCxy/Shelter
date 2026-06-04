package net.typeblog.shelter;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import net.typeblog.shelter.services.FileShuttleService;
import net.typeblog.shelter.services.ShelterService;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.SettingsManager;

public class ShelterApplication extends Application {
    private ServiceConnection mShelterServiceConnection = null;
    private ServiceConnection mFileShuttleServiceConnection = null;
    private IBinder mShelterServiceBinder = null;

    @Override
    public void onCreate() {
        super.onCreate();
        LocalStorageManager.initialize(this);
        SettingsManager.initialize(this);
    }

    public void bindShelterService(ServiceConnection conn, boolean foreground) {
        unbindShelterService();
        Intent intent = new Intent(getApplicationContext(), ShelterService.class);
        intent.putExtra("foreground", foreground);

        ServiceConnection wrapper = new ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name, IBinder service) {
                mShelterServiceBinder = service;
                conn.onServiceConnected(name, service);
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {
                mShelterServiceBinder = null;
                conn.onServiceDisconnected(name);
            }
        };

        bindService(intent, wrapper, Context.BIND_AUTO_CREATE);
        mShelterServiceConnection = wrapper;
    }

    public IBinder getShelterServiceBinder() {
        return mShelterServiceBinder;
    }

    public void bindFileShuttleService(ServiceConnection conn) {
        unbindFileShuttleService();;
        Intent intent = new Intent(getApplicationContext(), FileShuttleService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        mFileShuttleServiceConnection = conn;
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

    public void unbindFileShuttleService() {
        if (mFileShuttleServiceConnection != null) {
            try {
                unbindService(mFileShuttleServiceConnection);
            } catch (Exception e) {
                // ...
            }
        }

        mFileShuttleServiceConnection = null;
    }
}
