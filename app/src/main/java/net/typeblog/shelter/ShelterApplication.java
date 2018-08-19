package net.typeblog.shelter;

import android.app.Application;

import net.typeblog.shelter.util.LocalStorageManager;

public class ShelterApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LocalStorageManager.initialize(this);
    }
}
