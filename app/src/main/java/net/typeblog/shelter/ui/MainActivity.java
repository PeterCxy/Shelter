package net.typeblog.shelter.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.services.KillerService;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.Utility;

public class MainActivity extends AppCompatActivity {
    public static final String BROADCAST_CONTEXT_MENU_CLOSED = "net.typeblog.shelter.broadcast.CONTEXT_MENU_CLOSED";

    private static final int REQUEST_PROVISION_PROFILE = 1;
    private static final int REQUEST_START_SERVICE_IN_WORK_PROFILE = 2;
    private static final int REQUEST_SET_DEVICE_ADMIN = 3;
    private static final int REQUEST_TRY_START_SERVICE_IN_WORK_PROFILE = 4;

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
        if (mStorage.getBoolean(LocalStorageManager.PREF_IS_SETTING_UP)) {
            // Provision is still going on...
            Toast.makeText(this, R.string.provision_still_pending, Toast.LENGTH_SHORT).show();
            finish();
        } else if (!mStorage.getBoolean(LocalStorageManager.PREF_HAS_SETUP)) {
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
        // The service in main profile doesn't need to be foreground
        // because this activity will hold a ServiceConnection to the service
        ((ShelterApplication) getApplication()).bindShelterService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceMain = IShelterService.Stub.asInterface(service);
                detectWorkProfileAvailability();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        }, false);
    }

    private void detectWorkProfileAvailability() {
        // Send a dummy intent to the work profile first
        // to determine if work mode is enabled.
        // If work mode is disabled when starting this app, we will receive RESULT_CANCELED
        // in the activity result.
        Intent intent = new Intent(DummyActivity.TRY_START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            Utility.transferIntentToProfile(this, intent);
        } catch (IllegalStateException e) {
            // This exception implies a missing work profile
            // which means that the work profile does not even exist
            // in the first place.
            mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, false);
            Toast.makeText(this, getString(R.string.work_profile_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startActivityForResult(intent, REQUEST_TRY_START_SERVICE_IN_WORK_PROFILE);
    }

    private void bindWorkService() {
        // Bind to the ShelterService in work profile
        Intent intent = new Intent(DummyActivity.START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Utility.transferIntentToProfile(this, intent);
        startActivityForResult(intent, REQUEST_START_SERVICE_IN_WORK_PROFILE);
    }

    private void startKiller() {
        // Start the sticky KillerService to kill the ShelterService
        // for us when we are removed from tasks
        // This is a dirty hack because no lifecycle events will be
        // called when task is removed from recents
        Intent intent = new Intent(this, KillerService.class);
        Bundle bundle = new Bundle();
        bundle.putBinder("main", mServiceMain.asBinder());
        bundle.putBinder("work", mServiceWork.asBinder());
        intent.putExtra("extra", bundle);
        startService(intent);
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

    // Get the service on the other side
    // remote (work) -> main
    // main -> remote (work)
    IShelterService getOtherService(boolean isRemote) {
        return isRemote ? mServiceMain : mServiceWork;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // If the activity is stopped first, then kill the KillerService
        // to avoid double-free
        stopService(new Intent(this, KillerService.class));

        Utility.killShelterServices(mServiceMain, mServiceWork);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND && mServiceMain != null) {
            // We actually do not need to be in the background at all
            // (except when we are still waiting for provision to finish)
            // Just.. do not keep me at all.. please.
            // This is a dirty hack to ensure that the foreground service in work profile
            // will be killed along with this activity
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(BROADCAST_CONTEXT_MENU_CLOSED));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.main_menu_freeze_all:
                // This is the same as clicking on the batch freeze shortcut
                // so we just forward the request to DummyActivity
                Intent intent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
                intent.setComponent(new ComponentName(this, DummyActivity.class));
                startActivity(intent);
                return true;
            case R.id.main_menu_create_freeze_all_shortcut:
                Intent launchIntent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
                launchIntent.setComponent(new ComponentName(this, DummyActivity.class));
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                Utility.createLauncherShortcut(this, launchIntent,
                        Icon.createWithResource(this, R.mipmap.ic_freeze),
                        "shelter-freeze-all", getString(R.string.freeze_all_shortcut));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_PROVISION_PROFILE) {
            if (resultCode == RESULT_OK) {
                // The sync part of the setup process is completed
                // Wait for the provisioning to complete
                mStorage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, true);

                // However, we still have to wait for DummyActivity in work profile to finish
                Toast.makeText(this,
                        getString(R.string.provision_still_pending), Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this,
                        getString(R.string.work_profile_provision_failed), Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQUEST_TRY_START_SERVICE_IN_WORK_PROFILE) {
            if (resultCode == RESULT_OK) {
                // RESULT_OK is from DummyActivity. The work profile is enabled!
                bindWorkService();
            } else {
                // In this case, the user has been presented with a prompt
                // to enable work mode, but we have no means to distinguish
                // "ok" and "cancel", so the only way is to tell the user
                // to start again.
                Toast.makeText(this,
                        getString(R.string.work_mode_disabled), Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQUEST_START_SERVICE_IN_WORK_PROFILE && resultCode == RESULT_OK) {
            Bundle extra = data.getBundleExtra("extra");
            IBinder binder = extra.getBinder("service");
            mServiceWork = IShelterService.Stub.asInterface(binder);
            startKiller();
            buildView();
        } else if (requestCode == REQUEST_SET_DEVICE_ADMIN) {
            if (resultCode == RESULT_OK) {
                // Device Admin is now set, go ahead to provisioning (or initialization)
                init();
            } else {
                Toast.makeText(this, getString(R.string.device_admin_toast), Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
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
