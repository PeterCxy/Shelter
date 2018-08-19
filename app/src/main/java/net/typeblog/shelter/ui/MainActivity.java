package net.typeblog.shelter.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import net.typeblog.shelter.R;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.util.LocalStorageManager;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PROVISION_PROFILE = 1;

    private LocalStorageManager mStorage = null;
    private DevicePolicyManager mPolicyManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStorage = LocalStorageManager.getInstance();
        mPolicyManager = getSystemService(DevicePolicyManager.class);

        if (mPolicyManager.isProfileOwnerApp(getPackageName())) {
            // We are now in our own profile
            // We should never start the main activity here.
            finish();
        } else {
            if (!mStorage.getBoolean(LocalStorageManager.PREF_IS_DEVICE_ADMIN)) {
                // TODO: Navigate to the Device Admin settings page
                throw new IllegalStateException("TODO");
            }

            if (!mStorage.getBoolean(LocalStorageManager.PREF_HAS_SETUP)) {
                setupProfile();
            } else {
                // Initialize the app
                // we should bind to a service running in the work profile
                // in order to get the application lists etc.
            }
        }

    }

    private boolean setupProfile() {
        // Check if provisioning is allowed
        if (!mPolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            Toast.makeText(this,
                    getString(R.string.msg_device_unsupported), Toast.LENGTH_LONG).show();
            finish();
        }

        // Start provisioning
        Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);

        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class));
        startActivityForResult(intent, REQUEST_PROVISION_PROFILE);

        // We should continue the setup process later when provision completed
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == REQUEST_PROVISION_PROFILE) {
            // Provisioning finished.
            // Set the HAS_SETUP flag
            mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);

            // Initialize the app just as if the activity was started.
        }
    }
}
