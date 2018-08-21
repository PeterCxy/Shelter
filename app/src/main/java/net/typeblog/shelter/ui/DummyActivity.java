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

// DummyActivity does nothing about presenting any UI
// It is a wrapper over various different operations
// that might be required to perform across user profiles
// which is only possible through Intents that are in
// the crossProfileIntentFilter
public class DummyActivity extends Activity {
    public static final String START_SERVICE = "net.typeblog.shelter.action.START_SERVICE";
    public static final String TRY_START_SERVICE = "net.typeblog.shelter.action.TRY_START_SERVICE";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSystemService(DevicePolicyManager.class).isProfileOwnerApp(getPackageName())) {
            // If we are the profile owner, we enforce all our policies
            // so that we can make sure those are updated with our app
            Utility.enforceWorkProfilePolicies(this);
        }

        Intent intent = getIntent();
        if (START_SERVICE.equals(intent.getAction())) {
            actionStartService();
        } else if (TRY_START_SERVICE.equals(intent.getAction())) {
            // Dummy activity with dummy intent won't ever fail :)
            // This is used for testing if work mode is disabled from MainActivity
            setResult(RESULT_OK);
            finish();
        }
    }

    private void actionStartService() {
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
