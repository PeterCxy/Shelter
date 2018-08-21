package net.typeblog.shelter.services;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

// KillerService is a dirty fix to the fact that
// Activities cannot receive any event about their
// removal from recent tasks.
// Since we need to kill the process inside work profile
// when the app is closed by any means, we have to ensure
// its being killed in every possible circumstance.
public class KillerService extends Service {
    private IShelterService mServiceMain = null;
    private IShelterService mServiceWork = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getBundleExtra("extra");
        mServiceMain = IShelterService.Stub.asInterface(extras.getBinder("main"));
        mServiceWork = IShelterService.Stub.asInterface(extras.getBinder("work"));
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Ensure that all our other services are killed at this point
        try {
            mServiceWork.stopShelterService(true);
        } catch (Exception e) {
            // We are stopping anyway
        }

        try {
            mServiceMain.stopShelterService(false);
        } catch (Exception e) {
            // We are stopping anyway
        }

        // Kill this service itself
        stopSelf();
    }
}
