package net.typeblog.shelter.ui;

import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.IAppInstallCallback;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.services.KillerService;
import net.typeblog.shelter.util.AuthenticationUtility;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.UriForwardProxy;
import net.typeblog.shelter.util.Utility;

public class MainActivity extends AppCompatActivity {
    public static final String BROADCAST_CONTEXT_MENU_CLOSED = "net.typeblog.shelter.broadcast.CONTEXT_MENU_CLOSED";

    private static final int REQUEST_PROVISION_PROFILE = 1;
    private static final int REQUEST_START_SERVICE_IN_WORK_PROFILE = 2;
    private static final int REQUEST_SET_DEVICE_ADMIN = 3;
    private static final int REQUEST_TRY_START_SERVICE_IN_WORK_PROFILE = 4;
    private static final int REQUEST_DOCUMENTS_CHOOSE_APK = 5;

    private LocalStorageManager mStorage = null;
    private DevicePolicyManager mPolicyManager = null;

    // The "please wait" dialog when creating profile
    private ProgressDialog mProgressDialog = null;

    // Flag to avoid double-killing our services while restarting
    private boolean mRestarting = false;

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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mProgressDialog != null && isWorkProfileAvailable()) {
            mProgressDialog.dismiss();
            init();
        }
    }

    private void init() {
        if (mStorage.getBoolean(LocalStorageManager.PREF_IS_SETTING_UP) && !isWorkProfileAvailable()) {
            // Provision is still going on...
            Toast.makeText(this, R.string.provision_still_pending, Toast.LENGTH_SHORT).show();
            finish();
        } else if (!mStorage.getBoolean(LocalStorageManager.PREF_HAS_SETUP)) {
            // Reset the authentication key first
            AuthenticationUtility.reset();
            // If not set up yet, we have to provision the profile first
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage(R.string.first_run_alert)
                    .setPositiveButton(R.string.first_run_alert_continue,
                            (dialog, which) -> setupProfile())
                    .setNegativeButton(R.string.first_run_alert_cancel,
                            (dialog, which) -> finish())
                    .show();
        } else {
            // Initialize the settings
            SettingsManager.getInstance().applyAll();
            // Initialize the app (start by binding the services)
            bindServices();
        }
    }

    private void setupProfile() {
        // Build the provisioning intent first
        ComponentName admin = new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin);

        // Check if provisioning is allowed
        if (!mPolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                || getPackageManager().resolveActivity(intent, 0) == null) {
            Toast.makeText(this,
                    getString(R.string.msg_device_unsupported), Toast.LENGTH_LONG).show();
            finish();
        }

        // Start provisioning
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
                tryStartWorkService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        }, false);
    }

    private void tryStartWorkService() {
        // Send a dummy intent to the work profile first
        // to determine if work mode is enabled and we CAN start something in that profile.
        // If work mode is disabled when starting this app, we will receive RESULT_CANCELED
        // in the activity result, and we can then prompt the user to enable it
        Intent intent = new Intent(DummyActivity.TRY_START_SERVICE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            Utility.transferIntentToProfile(this, intent);
        } catch (IllegalStateException e) {
            // This exception implies a missing work profile, NOT a disabled work profile
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

    private boolean isWorkProfileAvailable() {
        // Determine if the work profile is already available
        // If so, return true and set all the corresponding flags to true
        // This is for scenarios where the asynchronous part of the
        // setup process might be finished before the synchronous part
        Intent intent = new Intent(DummyActivity.TRY_START_SERVICE);
        try {
            // DO NOT sign this request, because this won't be actually sent to work profile
            // If this is signed, and is the first request to be signed,
            // then the other side would never receive the auth_key
            Utility.transferIntentToProfileUnsigned(this, intent);
            mStorage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, false);
            mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);
            return true;
        } catch (IllegalStateException e) {
            // If any exception is thrown, this means that the profile is not available
            return false;
        }
    }

    // Get the service on the other side
    // remote (work) -> main
    // main -> remote (work)
    IShelterService getOtherService(boolean isRemote) {
        return isRemote ? mServiceMain : mServiceWork;
    }

    boolean servicesAlive() {
        try {
            mServiceMain.ping();
        } catch (Exception e) {
            return false;
        }

        try {
            mServiceWork.ping();
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mServiceMain != null && mServiceWork != null && !servicesAlive()) {
            // First, ensure that the services are killed before we restart
            // Otherwise, the system will reuse the services and the new activity
            // will end up depending on those old services that we are going to kill
            // in onDestroy()
            doOnDestroy();

            // Tell the onDestroy() logic that we are restarting. Do not kill the
            // KillerService again because the new activity will be starting a new one
            mRestarting = true;

            // Restart the activity if the services are no longer alive
            // This might be caused by KillerService being destroyed and
            // bringing all the other services with it
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // DO NOT kill anything if we are restarting
        // by the time this method is called, the new
        // activity could have started those services
        // again. We will mess up the new activity
        // if we kill again.
        if (!mRestarting)
            doOnDestroy();
    }

    private void doOnDestroy() {
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
            case R.id.main_menu_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                Bundle extras = new Bundle();
                extras.putBinder("profile_service", mServiceWork.asBinder());
                settingsIntent.putExtra("extras", extras);
                startActivity(settingsIntent);
                return true;
            case R.id.main_menu_create_freeze_all_shortcut:
                Intent launchIntent = new Intent(DummyActivity.PUBLIC_FREEZE_ALL);
                launchIntent.setComponent(new ComponentName(this, DummyActivity.class));
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                Utility.createLauncherShortcut(this, launchIntent,
                        Icon.createWithResource(this, R.mipmap.ic_freeze),
                        "shelter-freeze-all", getString(R.string.freeze_all_shortcut));
                return true;
            case R.id.main_menu_install_app_to_profile:
                Intent openApkIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                openApkIntent.addCategory(Intent.CATEGORY_OPENABLE);
                openApkIntent.setType("application/vnd.android.package-archive");
                startActivityForResult(openApkIntent, REQUEST_DOCUMENTS_CHOOSE_APK);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_PROVISION_PROFILE) {
            if (resultCode == RESULT_OK) {
                if (isWorkProfileAvailable()) {
                    // For pre-Oreo, or post-Oreo on some circumstances,
                    // by the time this is received, the whole process
                    // should have completed.
                    recreate();
                    return;
                }
                // The sync part of the setup process is completed
                // Wait for the provisioning to complete
                mStorage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, true);

                // However, we still have to wait for DummyActivity in work profile to finish
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setMessage(getString(R.string.provision_still_pending));
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
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
        } else if (requestCode == REQUEST_DOCUMENTS_CHOOSE_APK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            UriForwardProxy proxy = new UriForwardProxy(getApplicationContext(), uri);

            try {
                mServiceWork.installApk(proxy, new IAppInstallCallback.Stub() {
                    @Override
                    public void callback(int result) {
                        runOnUiThread(() -> {
                            // The other side will have closed the Fd for us
                            if (result == RESULT_OK)
                                Toast.makeText(MainActivity.this,
                                        R.string.install_app_to_profile_success, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (RemoteException e) {
                // Well, I don't know what to do then
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
