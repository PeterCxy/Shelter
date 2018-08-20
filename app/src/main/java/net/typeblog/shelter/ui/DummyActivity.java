package net.typeblog.shelter.ui;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.util.Utility;

public class DummyActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSystemService(DevicePolicyManager.class).isProfileOwnerApp(getPackageName())) {
            // If we are the profile owner, we enforce all our policies
            // so that we can make sure those are updated with our app
            Utility.enforceWorkProfilePolicies(this);
        }

        ((ShelterApplication) getApplication()).bindShelterService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Intent data = new Intent();
                Bundle bundle = new Bundle();
                bundle.putBinder("service", service);
                data.putExtra("extra", bundle);
                setResult(RESULT_OK, data);
                finish();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        });
    }
}
