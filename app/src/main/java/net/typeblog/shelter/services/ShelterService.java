package net.typeblog.shelter.services;

import android.app.Activity;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

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
    public static final int RESULT_CANNOT_INSTALL_SYSTEM_APP = 100001;

    private static final int NOTIFICATION_ID = 0x49a11;
    private DevicePolicyManager mPolicyManager = null;
    private boolean mIsProfileOwner = false;
    private PackageManager mPackageManager = null;
    private ComponentName mAdminComponent = null;
    // When we need to start an activity, we need something else to do it for us
    // as per background limitation of Android 10
    // We mostly need this for app cloning / installation
    // (we could probably just invoke DummyActivity directly from the other side,
    //  but there are cases where DummyActivity isn't needed, e.g. when cloning
    //  system applications. These cases can be handled without DummyActivity
    //  and without any visual interference.)
    // Note that this proxy can only start activity that is accessible to the
    // main profile and within the application itself.
    private IStartActivityProxy mStartActivityProxy = null;
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
        public void getApps(IGetAppsCallback callback, boolean showAll) {
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

                            return showAll || (!isSystem && isInstalled) || isHidden || canLaunch;
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    intent.putExtra("split_apks", app.getSplitApks());

                // Send the callback to the DummyActivity
                Bundle callbackExtra = new Bundle();
                callbackExtra.putBinder("callback", callback.asBinder());
                intent.putExtra("callback", callbackExtra);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                DummyActivity.registerSameProcessRequest(intent);
                if (mStartActivityProxy != null)
                    mStartActivityProxy.startActivity(intent);
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
        public void installApk(UriForwardProxy uriForwarder, IAppInstallCallback callback) throws RemoteException {
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
            if (mStartActivityProxy != null)
                mStartActivityProxy.startActivity(intent);
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

                if (mStartActivityProxy != null)
                    mStartActivityProxy.startActivity(intent);
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

        @Override
        public boolean hasSystemAlertPermission() {
            return Utility.checkSystemAlertPermission(ShelterService.this);
        }

        @Override
        public boolean hasAllFileAccessPermission() {
            return Utility.checkAllFileAccessPermission();
        }

        @Override
        public List<String> getCrossProfileWidgetProviders() {
            if (!mIsProfileOwner)
                throw new IllegalStateException("Cannot access cross-profile widget providers without being profile owner");
            return mPolicyManager.getCrossProfileWidgetProviders(mAdminComponent);
        }

        @Override
        public boolean setCrossProfileWidgetProviderEnabled(String pkgName, boolean enabled) {
            if (!mIsProfileOwner)
                throw new IllegalStateException("Cannot access cross-profile widget providers without being profile owner");
            if (enabled) {
                return mPolicyManager.addCrossProfileWidgetProvider(mAdminComponent, pkgName);
            } else {
                return mPolicyManager.removeCrossProfileWidgetProvider(mAdminComponent, pkgName);
            }
        }

        @Override
        public void setStartActivityProxy(IStartActivityProxy proxy) {
            mStartActivityProxy = proxy;
        }
    };

    @Override
    public void onCreate() {
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mPackageManager = getPackageManager();
        mIsProfileOwner = mPolicyManager.isProfileOwnerApp(getPackageName());
        mAdminComponent = new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent.getBooleanExtra("foreground", false)) {
            setForeground();
        }
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Stop our foreground notification (if it was created at all) when
        // all clients have disconnected.
        // This helps to ensure no notification is left when the Shelter activity
        // is closed.
        stopForeground(true);
        return false;
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
