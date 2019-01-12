package net.typeblog.shelter.services;

import android.app.Activity;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.util.ApplicationInfoWrapper;
import net.typeblog.shelter.util.FileProviderProxy;
import net.typeblog.shelter.util.UriForwardProxy;
import net.typeblog.shelter.util.Utility;

import java.util.List;
import java.util.stream.Collectors;

public class ShelterService extends Service {
    private static final String LOG_TAG = "Shelter";

    public static final int RESULT_CANNOT_INSTALL_SYSTEM_APP = 100001;

    private static final int NOTIFICATION_ID = 0x49a11;
    private DevicePolicyManager mPolicyManager = null;
    private boolean mIsProfileOwner = false;
    private PackageManager mPackageManager = null;
    private ComponentName mAdminComponent = null;

    private static final String[] mSystemGoolgleApps = {
        "com.google.android.gsf",
        "com.google.android.gms",
        "com.google.android.feedback",
        "com.google.android.backuptransport",
        "com.android.vending"
    };

    private IShelterService.Stub mBinder = new IShelterService.Stub() {
        @Override
        public void ping() {
            // Do nothing, just let the other side know we are alive
        }

        @Override
        public void stopShelterService(boolean kill) {
            // dirty: just wait for some time and kill this service itself
            new Thread(() -> {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {

                }

                ((ShelterApplication) getApplication()).unbindShelterService();

                if (kill && !(mIsProfileOwner && FreezeService.hasPendingAppToFreeze())) {
                    // Just kill the entire process if this signal is received and the process has nothing to do
                    System.exit(0);
                }
            }).start();
        }

        @Override
        public void getApps(IGetAppsCallback callback) {
            new Thread(() -> {
                int pmFlags = PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES;
                List<ApplicationInfoWrapper> list = mPackageManager.getInstalledApplications(pmFlags)
                        .stream()
                        .filter((it) -> !it.packageName.equals(getPackageName()))
                        .filter((it) -> {
                            boolean isSystem = (it.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                            boolean isHidden = isHidden(it.packageName);
                            boolean isInstalled = (it.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
                            boolean canLaunch = mPackageManager.getLaunchIntentForPackage(it.packageName) != null;

                            return (!isSystem && isInstalled) || isHidden || canLaunch;
                        })
                        .map(ApplicationInfoWrapper::new)
                        .map((it) -> it.loadLabel(mPackageManager)
                                .setHidden(isHidden(it.getPackageName())))
                        .sorted((x, y) -> {
                            // Sort hidden apps at the last
                            if (x.isHidden() && !y.isHidden()) {
                                return 1;
                            } else if (!x.isHidden() && y.isHidden()) {
                                return -1;
                            } else {
                                return x.getLabel().compareTo(y.getLabel());
                            }
                        })
                        .collect(Collectors.toList());

                try {
                    callback.callback(list);
                } catch (RemoteException e) {
                    // Do Nothing
                }
            }).start();
        }

        @Override
        public void loadIcon(ApplicationInfoWrapper info, ILoadIconCallback callback) {
            new Thread(() -> {
                Bitmap icon = Utility.drawableToBitmap(info.getInfo().loadUnbadgedIcon(mPackageManager));

                try {
                    callback.callback(icon);
                } catch (RemoteException e) {
                    // Do Nothing
                }
            }).start();
        }

        @Override
        public void installApp(ApplicationInfoWrapper app, IAppInstallCallback callback) throws RemoteException {
            if (!app.isSystem()) {
                // Installing a non-system app requires firing up PackageInstaller
                // Delegate this operation to DummyActivity because
                // Only it can receive a result
                Intent intent = new Intent(DummyActivity.INSTALL_PACKAGE);
                intent.setComponent(new ComponentName(ShelterService.this, DummyActivity.class));
                intent.putExtra("package", app.getPackageName());
                intent.putExtra("apk", app.getSourceDir());

                // Send the callback to the DummyActivity
                Bundle callbackExtra = new Bundle();
                callbackExtra.putBinder("callback", callback.asBinder());
                intent.putExtra("callback", callbackExtra);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                DummyActivity.registerSameProcessRequest(intent);
                startActivity(intent);
            } else {
                if (mIsProfileOwner) {
                    // We can only enable system apps in our own profile
                    mPolicyManager.enableSystemApp(
                            mAdminComponent,
                            app.getPackageName());

                    // Also set the hidden state to false.
                    mPolicyManager.setApplicationHidden(
                            mAdminComponent,
                            app.getPackageName(), false);

                    callback.callback(Activity.RESULT_OK);
                } else {
                    callback.callback(RESULT_CANNOT_INSTALL_SYSTEM_APP);
                }
            }
        }

        @Override
        public void installApk(UriForwardProxy uriForwarder, IAppInstallCallback callback) {
            // Directly install an APK through a given Fd
            // instead of installing an existing one
            Intent intent = new Intent(DummyActivity.INSTALL_PACKAGE);
            intent.setComponent(new ComponentName(ShelterService.this, DummyActivity.class));
            // Generate a content Uri pointing to the Fd
            // DummyActivity is expected to release the Fd after finishing
            Uri uri = FileProviderProxy.setUriForwardProxy(uriForwarder, "apk");
            intent.putExtra("direct_install_apk", uri);

            // Send the callback to the DummyActivity
            Bundle callbackExtra = new Bundle();
            callbackExtra.putBinder("callback", callback.asBinder());
            intent.putExtra("callback", callbackExtra);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            DummyActivity.registerSameProcessRequest(intent);
            startActivity(intent);
        }

        @Override
        public void uninstallApp(ApplicationInfoWrapper app, IAppInstallCallback callback) throws RemoteException {
            if (!app.isSystem()) {
                // Similarly, fire up DummyActivity to do uninstallation for us
                Intent intent = new Intent(DummyActivity.UNINSTALL_PACKAGE);
                intent.setComponent(new ComponentName(ShelterService.this, DummyActivity.class));
                intent.putExtra("package", app.getPackageName());

                // Send the callback to the DummyActivity
                Bundle callbackExtra = new Bundle();
                callbackExtra.putBinder("callback", callback.asBinder());
                intent.putExtra("callback", callbackExtra);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                DummyActivity.registerSameProcessRequest(intent);
                startActivity(intent);
            } else {
                if (mIsProfileOwner) {
                    // This is essentially the same as disabling the system app
                    // There is no way to reverse the "enableSystemApp" operation here
                    mPolicyManager.setApplicationHidden(
                            mAdminComponent,
                            app.getPackageName(), true);
                    callback.callback(Activity.RESULT_OK);
                } else {
                    callback.callback(RESULT_CANNOT_INSTALL_SYSTEM_APP);
                }
            }
        }

        @Override
        public void freezeApp(ApplicationInfoWrapper app) {
            if (!mIsProfileOwner)
                throw new IllegalArgumentException("Cannot freeze app without being profile owner");

            mPolicyManager.setApplicationHidden(
                    mAdminComponent,
                    app.getPackageName(), true);
        }

        @Override
        public void unfreezeApp(ApplicationInfoWrapper app) {
            if (!mIsProfileOwner)
                throw new IllegalArgumentException("Cannot unfreeze app without being profile owner");

            mPolicyManager.setApplicationHidden(
                    mAdminComponent,
                    app.getPackageName(), false);
        }

        @Override
        public boolean hasUsageStatsPermission() {
            return Utility.checkUsageStatsPermission(ShelterService.this);
        }
    };

    @Override
    public void onCreate() {
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mPackageManager = getPackageManager();
        mIsProfileOwner = mPolicyManager.isProfileOwnerApp(getPackageName());
        mAdminComponent = new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class);
        installGAppsCore(false);
    }

    public void installGAppsCore(boolean force) {
        String PREFS_NAME = "pref_system_app_enabled";
        String KEY_NAME = "system_app_enabled";
        SharedPreferences prefSystemAppEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefSystemAppEnabled.getBoolean(KEY_NAME, false) && !force) {
            Log.e(LOG_TAG, "installGAppsCore: GApps Core always installed in Shelter.");
            return;
        }
        if(!mPolicyManager.isProfileOwnerApp(getPackageName())) {
            Log.e(LOG_TAG, "installGAppsCore: GApps Core install to Shelter failed, the Shelter app is not profile Owner app.");
            return;
        }

        for (String packageName : mSystemGoolgleApps) {
            try {
                mPolicyManager.enableSystemApp(mAdminComponent, packageName);
            } catch (Exception e) {
                Log.e(LOG_TAG, "enable System App " + " failed (" + packageName + "), Exception thrown:" + e);
            }
        }
        SharedPreferences.Editor editor = prefSystemAppEnabled.edit();
        editor.putBoolean(KEY_NAME, true);
        editor.commit();
        Log.d(LOG_TAG, "installGAppsCore: GApps Core install to Shelter successed.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent.getBooleanExtra("foreground", false)) {
            setForeground();
        }
        return mBinder;
    }

    private boolean isHidden(String packageName) {
        return mIsProfileOwner && mPolicyManager.isApplicationHidden(mAdminComponent, packageName);
    }

    private void setForeground() {
        startForeground(NOTIFICATION_ID, Utility.buildNotification(this,
                getString(R.string.app_name),
                getString(R.string.service_title),
                getString(R.string.service_desc),
                R.drawable.ic_notification_white_24dp));
    }
}
