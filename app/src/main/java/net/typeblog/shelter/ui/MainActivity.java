package net.typeblog.shelter.ui;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.Utility;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PROVISION_PROFILE = 1;
    private static final int REQUEST_START_SERVICE_IN_WORK_PROFILE = 2;
    private static final int REQUEST_SET_DEVICE_ADMIN = 3;

    private LocalStorageManager mStorage = null;
    private DevicePolicyManager mPolicyManager = null;

    // Two services running in main / work profile
    private IShelterService mServiceMain = null;
    private IShelterService mServiceWork = null;

    // Views
    private ViewPager mPager = null;
    private TabLayout mTabs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.main_toolbar));
        mStorage = LocalStorageManager.getInstance();
        mPolicyManager = getSystemService(DevicePolicyManager.class);

        if (mPolicyManager.isProfileOwnerApp(getPackageName())) {
            // We are now in our own profile
            // We should never start the main activity here.
            android.util.Log.d("MainActivity", "started in user profile. stopping.");
            finish();
        } else {
            if (!mStorage.getBoolean(LocalStorageManager.PREF_IS_DEVICE_ADMIN)) {
                mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, false);
                // Navigate to the Device Admin settings page
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(
                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class));
                intent.putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.device_admin_explanation));
                startActivityForResult(intent, REQUEST_SET_DEVICE_ADMIN);
                return;
            }

            init();
        }

    }

    private void init() {
        if (!mStorage.getBoolean(LocalStorageManager.PREF_HAS_SETUP)) {
            // If not set up yet, we have to provision the profile first
            setupProfile();
        } else {
            // Initialize the app (start by binding the services)
            bindServices();
        }
    }

    private void setupProfile() {
        // Check if provisioning is allowed
        if (!mPolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            Toast.makeText(this,
                    getString(R.string.msg_device_unsupported), Toast.LENGTH_LONG).show();
            finish();
        }

        // Start provisioning
        ComponentName admin = new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin);
        startActivityForResult(intent, REQUEST_PROVISION_PROFILE);
    }

    private void bindServices() {
        // Bind to the service provided by this app in main user
        ((ShelterApplication) getApplication()).bindShelterService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceMain = IShelterService.Stub.asInterface(service);
                bindWorkService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        });
    }

    private void bindWorkService() {
        // Bind to the ShelterService in work profile
        Intent intent = new Intent(DummyActivity.START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            Utility.transferIntentToProfile(this, intent);
        } catch (IndexOutOfBoundsException e) {
            // This exception implies a missing work profile
            mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, false);
            Toast.makeText(this, getString(R.string.work_profile_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startActivityForResult(intent, REQUEST_START_SERVICE_IN_WORK_PROFILE);
    }

    private void buildView() {
        // Finally we can build the view
        // Find all the views
        mPager = findViewById(R.id.main_pager);
        mTabs = findViewById(R.id.main_tablayout);

        // Initialize the ViewPager and the tab
        // All the remaining work will be done in the fragments
        mPager.setAdapter(new AppListFragmentAdapter(getSupportFragmentManager()));
        mTabs.setupWithViewPager(mPager);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            // For the work instance, we just kill it entirely
            // We don't need it to be awake to do anything useful
            mServiceWork.stopShelterService(true);
        } catch (Exception e) {
            // We are stopping anyway
        }

        try {
            mServiceMain.stopShelterService(false);
        } catch (Exception e) {
            // We are stopping anyway
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PROVISION_PROFILE) {
            if (resultCode == RESULT_OK) {
                // The sync part of the setup process is completed
                // We register a receiver to wait for the async part
                registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        unregisterReceiver(this); // We only want to receive this once
                        mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);
                        bindServices();
                    }
                }, new IntentFilter(DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED));
            } else {
                Toast.makeText(this,
                        getString(R.string.work_profile_provision_failed), Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQUEST_START_SERVICE_IN_WORK_PROFILE && resultCode == RESULT_OK) {
            Bundle extra = data.getBundleExtra("extra");
            IBinder binder = extra.getBinder("service");
            mServiceWork = IShelterService.Stub.asInterface(binder);
            buildView();
        } else if (requestCode == REQUEST_SET_DEVICE_ADMIN) {
            if (resultCode == RESULT_OK) {
                // Device Admin is now set, go ahead to provisioning (or initialization)
                init();
            } else {
                Toast.makeText(this, getString(R.string.device_admin_toast), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private class AppListFragmentAdapter extends FragmentPagerAdapter {
        AppListFragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Fragment getItem(int i) {
            if (i == 0) {
                return AppListFragment.newInstance(mServiceMain, false);
            } else if (i == 1) {
                return AppListFragment.newInstance(mServiceWork, true);
            } else {
                return null;
            }
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int i) {
            if (i == 0) {
                return getString(R.string.fragment_profile_main);
            } else if (i == 1) {
                return getString(R.string.fragment_profile_work);
            } else {
                return null;
            }
        }
    }
}
