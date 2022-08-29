package net.typeblog.shelter.ui;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.FreezeService;
import net.typeblog.shelter.services.IAppInstallCallback;
import net.typeblog.shelter.services.IFileShuttleService;
import net.typeblog.shelter.services.IFileShuttleServiceCallback;
import net.typeblog.shelter.util.AuthenticationUtility;
import net.typeblog.shelter.util.FileProviderProxy;
import net.typeblog.shelter.util.InstallationProgressListener;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.Utility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

// DummyActivity does nothing about presenting any UI
// It is a wrapper over various different operations
// that might be required to perform across user profiles
// which is only possible through Intents that are in
// the crossProfileIntentFilter
public class DummyActivity extends Activity {
    public static final String FINALIZE_PROVISION = "net.typeblog.shelter.action.FINALIZE_PROVISION";
    public static final String START_SERVICE = "net.typeblog.shelter.action.START_SERVICE";
    public static final String TRY_START_SERVICE = "net.typeblog.shelter.action.TRY_START_SERVICE";
    public static final String INSTALL_PACKAGE = "net.typeblog.shelter.action.INSTALL_PACKAGE";
    public static final String UNINSTALL_PACKAGE = "net.typeblog.shelter.action.UNINSTALL_PACKAGE";
    public static final String UNFREEZE_AND_LAUNCH = "net.typeblog.shelter.action.UNFREEZE_AND_LAUNCH";
    public static final String PUBLIC_UNFREEZE_AND_LAUNCH = "net.typeblog.shelter.action.PUBLIC_UNFREEZE_AND_LAUNCH";
    public static final String PUBLIC_FREEZE_ALL = "net.typeblog.shelter.action.PUBLIC_FREEZE_ALL";
    public static final String FREEZE_ALL_IN_LIST = "net.typeblog.shelter.action.FREEZE_ALL_IN_LIST";
    // If we use the same intent for parent -> profile and profile -> parent, the user will
    // be prompted with the action chooser with only one choice in it when the intent is
    // forwarded by Utility.transferIntentToProfile()
    // This is a bad experience, so we use two to avoid this.
    public static final String START_FILE_SHUTTLE = "net.typeblog.shelter.action.START_FILE_SHUTTLE";
    public static final String START_FILE_SHUTTLE_2 = "net.typeblog.shelter.action.START_FILE_SHUTTLE_2";
    public static final String SYNCHRONIZE_PREFERENCE = "net.typeblog.shelter.action.SYNCHRONIZE_PREFERENCE";
    public static final String PACKAGEINSTALLER_CALLBACK = "net.typeblog.shelter.action.PACKAGEINSTALLER_CALLBACK";

    // Only these actions are allowed without a valid signature
    private static final List<String> ACTIONS_ALLOWED_WITHOUT_SIGNATURE = Arrays.asList(
            FINALIZE_PROVISION,
            PUBLIC_FREEZE_ALL,
            PUBLIC_UNFREEZE_AND_LAUNCH);

    // Only these actions are allowed to be called from the same process (pre-registered)
    // without a valid signature
    private static final List<String> ACTIONS_ALLOWED_WITHOUT_SIGNATURE_SAME_PROCESS = Arrays.asList(
            INSTALL_PACKAGE,
            UNINSTALL_PACKAGE,
            UNFREEZE_AND_LAUNCH);

    private static final int REQUEST_INSTALL_PACKAGE = 1;
    private static final int REQUEST_PERMISSION_EXTERNAL_STORAGE= 2;
    private static final int REQUEST_PERMISSION_POST_NOTIFICATIONS = 3;

    private static boolean sHasRequestedPermission = false;

    // A state variable to record the last time DummyActivity was informed that someone
    // in the same process needs to call an action without signature
    // Since they must be in the same process as DummyActivity, it will be totally fine
    // to share a memory state
    private static volatile long sLastSameProcessRequest = -1;

    // Register that an intent will be sent to this Activity without signature
    // from the same process. Each registration is allowed for at most 5 seconds.
    public static synchronized void registerSameProcessRequest(Intent intent) {
        sLastSameProcessRequest = new Date().getTime();
        intent.putExtra("is_same_process", true);
    }

    private static synchronized boolean checkSameProcessRequest(Intent intent) {
        if (!intent.getBooleanExtra("is_same_process", false)) return false;
        if (sLastSameProcessRequest == -1) return false;

        boolean ret = new Date().getTime() - sLastSameProcessRequest <= 5000 // Timeout 5s
                && ACTIONS_ALLOWED_WITHOUT_SIGNATURE_SAME_PROCESS.contains(intent.getAction());
        if (ret) {
            sLastSameProcessRequest = -1; // Revoke the registered request
        }

        return ret;
    }

    private boolean mIsProfileOwner = false;
    private DevicePolicyManager mPolicyManager = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mIsProfileOwner = mPolicyManager.isProfileOwnerApp(getPackageName());
        if (mIsProfileOwner) {
            // If we are the profile owner, we enforce all our policies
            // so that we can make sure those are updated with our app
            Utility.enforceWorkProfilePolicies(this);
            Utility.enforceUserRestrictions(this);
            SettingsManager.getInstance().applyAll();

            synchronized (DummyActivity.class) {
                // Do not show permission dialog during finalization -- it will conflict with the provisioning UI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !sHasRequestedPermission
                        && !FINALIZE_PROVISION.equals(getIntent().getAction())) {
                    // Avoid requesting permission multiple times in one session
                    // This also prevents multiple instances of DummyActivity from being blocked on each other
                    sHasRequestedPermission = true;
                    // We pretty much only send notifications to keep the process inside work profile alive
                    // as such, only request the notification permission from inside the profile
                    // This will ideally be shown and done when the user sees the app list UI for the first time
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSION_POST_NOTIFICATIONS);
                        // Continue once the request has been completed (see onRequestPermissionResult)
                        return;
                    }
                }
            }
        }

        init();
    }

    private void init() {
        Intent intent = getIntent();

        // First check if we have a registered request from the same process
        // if it passes, we don't have to check if it has proper signature any more
        if (!checkSameProcessRequest(getIntent())) {
            // Check the intent signature first
            // Call checkIntent() first, because we might receive an auth_key from the other side any time.
            // Calling checkIntent() will ensure that the first auth_key is properly received.
            // ONLY the first received one should be stored and trusted.
            if (!AuthenticationUtility.checkIntent(intent)) {
                // If check failed and not in allowed-without-signature list
                if (!ACTIONS_ALLOWED_WITHOUT_SIGNATURE.contains(intent.getAction())) {
                    // Unauthenticated! Just exit IMMEDIATELY
                    finish();
                    return;
                }
            }
        }

        if (START_SERVICE.equals(intent.getAction())) {
            actionStartService();
        } else if (TRY_START_SERVICE.equals(intent.getAction())) {
            // Dummy activity with dummy intent won't ever fail :)
            // This is used for testing if work mode is disabled from MainActivity
            setResult(RESULT_OK);
            finish();
        } else if (INSTALL_PACKAGE.equals(intent.getAction())) {
            actionInstallPackage();
        } else if (UNINSTALL_PACKAGE.equals(intent.getAction())) {
            actionUninstallPackage();
        } else if (FINALIZE_PROVISION.equals(intent.getAction())) {
            actionFinalizeProvision();
        } else if (UNFREEZE_AND_LAUNCH.equals(intent.getAction()) || PUBLIC_UNFREEZE_AND_LAUNCH.equals(intent.getAction())) {
            actionUnfreezeAndLaunch();
        } else if (PUBLIC_FREEZE_ALL.equals(intent.getAction())) {
            actionPublicFreezeAll();
        } else if (FREEZE_ALL_IN_LIST.equals(intent.getAction())) {
            actionFreezeAllInList();
        } else if (START_FILE_SHUTTLE.equals(intent.getAction()) || START_FILE_SHUTTLE_2.equals(intent.getAction())) {
            actionStartFileShuttle();
        } else if (SYNCHRONIZE_PREFERENCE.equals(intent.getAction())) {
            actionSynchronizePreference();
        } else {
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getAction().equals(PACKAGEINSTALLER_CALLBACK)) {
            int status = intent.getExtras().getInt(PackageInstaller.EXTRA_STATUS);

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    startActivity((Intent) intent.getExtras().get(Intent.EXTRA_INTENT));
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    appInstallFinished(Activity.RESULT_OK);
                    break;
                default:
                    appInstallFinished(Activity.RESULT_CANCELED);
                    break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_INSTALL_PACKAGE) {
            appInstallFinished(resultCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doStartFileShuttle();
            } else {
                finish();
            }
        } else if (requestCode == REQUEST_PERMISSION_POST_NOTIFICATIONS) {
            // Regardless of the result, continue initialization
            // This is fine because most functionalities will work anyway; it will just be a bit buggy
            // and unreliable.
            init();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void actionFinalizeProvision() {
        if (mIsProfileOwner) {
            // Only notify the main profile on pre-Oreo
            // After Oreo, since we use the activity-based finalization flow,
            // the setup wizard will wait until we finish finalization before returning
            // (Note: the actual finalization is done by common code in onCreate)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // This is the action used by DeviceAdminReceiver to finalize the setup
                // The work has been finished in onCreate(), now we just have to
                // inform the main profile about this
                Intent intent = new Intent(FINALIZE_PROVISION);
                // We don't need signature for this intent
                Utility.transferIntentToProfileUnsigned(this, intent);
                startActivity(intent);
            }
            finish();
        } else {
            // Set the flag telling MainActivity that we have now finished provisioning
            LocalStorageManager.getInstance()
                    .setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);
            LocalStorageManager.getInstance()
                    .setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, false);
            Intent intent = new Intent(SetupWizardActivity.ACTION_PROFILE_PROVISIONED);
            intent.setComponent(new ComponentName(this, SetupWizardActivity.class));
            startActivity(intent);
            Toast.makeText(this, getString(R.string.provision_finished), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void actionStartService() {
        // This needs to be foreground because this activity won't be able to hold
        // the ServiceConnection to it.
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
        }, true);
    }

    private void actionInstallPackage() {
        Uri uri = null;
        if (getIntent().hasExtra("package")) {
            uri = Uri.fromParts("package", getIntent().getStringExtra("package"), null);
        }
        StrictMode.VmPolicy policy = StrictMode.getVmPolicy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || getIntent().hasExtra("direct_install_apk")) {
            if (getIntent().hasExtra("apk")) {
                // I really have no idea about why the "package:" uri do not work
                // after Android O, anyway we fall back to using the apk path...
                // Since I have plan to support pre-O in later versions, I keep this
                // branch in case that we reduce minSDK in the future.
                uri = Uri.fromFile(new File(getIntent().getStringExtra("apk")));
            } else if (getIntent().hasExtra("direct_install_apk")) {
                // Directly install an APK inside the profile
                // The APK will be an Uri from our own FileProviderProxy
                // which points to an opened Fd in another profile.
                // We must close the Fd when we finish.
                uri = getIntent().getParcelableExtra("direct_install_apk");
            }

            // A permissive VmPolicy must be set to work around
            // the limitation on cross-application Uri
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // For Q, since we use the more "manual" method of installation,
                // we have to also pass the split APKs ("Configuration APKs" as Google calls it)
                // Although these are available since API 26, we don't need to
                // take care of them for versions before Q since we don't actually
                // install the APKs before Q.
                actionInstallPackageQ(uri, getIntent().getStringArrayExtra("split_apks"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, getPackageName());
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_INSTALL_PACKAGE);
        }

        // Restore the VmPolicy anyway
        StrictMode.setVmPolicy(policy);
    }

    // On Android Q, ACTION_INSTALL_PACKAGE has been deprecated.
    // We have to switch to using PackageInstaller for the job, which isn't quite
    // as elegant because now we really need to read the entire apk and write to it
    // Keep this case only for Q for now.
    private void actionInstallPackageQ(Uri uri, String[] split_apks) throws IOException {
        PackageInstaller pi = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = pi.createSession(params);

        // Show the progress dialog first
        pi.registerSessionCallback(new InstallationProgressListener(this, pi, sessionId));

        PackageInstaller.Session session = pi.openSession(sessionId);
        doInstallPackageQ(uri, split_apks, session, () -> {
            // We have finished piping the streams, show the progress as 10%
            session.setStagingProgress(0.1f);

            // Commit the session
            Intent intent = new Intent(this, DummyActivity.class);
            intent.setAction(PACKAGEINSTALLER_CALLBACK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_MUTABLE);
            session.commit(pendingIntent.getIntentSender());
        });
    }

    // The background part of the installation process on Q (reading APKs etc)
    // that must be executed on another thread
    // Put them in background to avoid stalling the UI thread
    private void doInstallPackageQ(Uri baseUri, String[] split_apks, PackageInstaller.Session session, Runnable callback) {
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(baseUri);
        if (split_apks != null && split_apks.length > 0) {
            for (String apk : split_apks) {
                uris.add(Uri.fromFile(new File(apk)));
            }
        }

        new Thread(() -> {
            for (Uri uri : uris) {
                try (InputStream is = getContentResolver().openInputStream(uri);
                     OutputStream os = session.openWrite(UUID.randomUUID().toString(), 0, is.available())
                ) {
                    Utility.pipe(is, os);
                    session.fsync(os);
                } catch (IOException e) {

                }
            }

            runOnUiThread(callback);
        }).start();
    }

    private void actionUninstallPackage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            actionUninstallPackageQ();
            return;
        }

        Uri uri = Uri.fromParts("package", getIntent().getStringExtra("package"), null);
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        // Currently, Install & Uninstall share the same logic
        // after starting the system PackageInstaller
        // because the only thing to do is to call the callback
        // with the result code.
        // If ANY separate logic is added for any of them,
        // the request code should be separated.
        startActivityForResult(intent, REQUEST_INSTALL_PACKAGE);
    }

    private void actionUninstallPackageQ() {
        PackageInstaller pi = getPackageManager().getPackageInstaller();
        Intent intent = new Intent(this, DummyActivity.class);
        intent.setAction(PACKAGEINSTALLER_CALLBACK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_MUTABLE);
        pi.uninstall(getIntent().getStringExtra("package"), pendingIntent.getIntentSender());
    }

    private void appInstallFinished(int resultCode) {
        // Clear the fd anyway since we have finished installation.
        // Because we might have been installing an APK opened from
        // the other profile. We don't know, but just clean it.
        FileProviderProxy.clearForwardProxy();

        if (!getIntent().hasExtra("callback")) return;

        // Send the result code back to the caller
        Bundle callbackExtra = getIntent().getBundleExtra("callback");
        IAppInstallCallback callback = IAppInstallCallback.Stub
                .asInterface(callbackExtra.getBinder("callback"));

        try {
            callback.callback(resultCode);
        } catch (RemoteException e) {
            // do nothing
        }

        finish();
    }

    private void actionUnfreezeAndLaunch() {
        // Unfreeze and launch an app
        // (actually this also works if the app is not frozen at all)
        // For now we only support apps in Work profile,
        // so we just check if we are profile owner here
        if (!mIsProfileOwner) {
            // Forward it to work profile
            Intent intent = new Intent(UNFREEZE_AND_LAUNCH);
            Utility.transferIntentToProfile(this, intent);
            String packageName = getIntent().getStringExtra("packageName");
            intent.putExtra("packageName", packageName);
            intent.putExtra("shouldFreeze",
                    SettingsManager.getInstance().getAutoFreezeServiceEnabled() &&
                            LocalStorageManager.getInstance()
                                .stringListContains(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, packageName));
            if (getIntent().hasExtra("linkedPackages")) {
                // Multiple apps should be unfrozen here
                String[] packages = getIntent().getStringExtra("linkedPackages").split(",");
                boolean[] packagesShouldFreeze = new boolean[packages.length];

                for (int i = 0; i < packages.length; i++) {
                    // Apps in linkedPackages may also need to be auto-frozen
                    // thus, we loop through them and fetch the settings
                    packagesShouldFreeze[i] = SettingsManager.getInstance().getAutoFreezeServiceEnabled() &&
                            LocalStorageManager.getInstance()
                                    .stringListContains(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, packages[i]);
                }
                intent.putExtra("linkedPackages", packages);
                intent.putExtra("linkedPackagesShouldFreeze", packagesShouldFreeze);
            }
            startActivity(intent);
            finish();
            return;
        }

        // If we have multiple linked apps to unfreeze before launching the main one
        if (getIntent().hasExtra("linkedPackages")) {
            String[] packages = getIntent().getStringArrayExtra("linkedPackages");
            boolean[] packagesShouldFreeze = getIntent().getBooleanArrayExtra("linkedPackagesShouldFreeze");

            for (int i = 0; i < packages.length; i++) {
                // Unfreeze everything
                mPolicyManager.setApplicationHidden(
                        new ComponentName(this, ShelterDeviceAdminReceiver.class),
                        packages[i], false);
                // Register freeze service
                if (packagesShouldFreeze[i]) {
                    registerAppToFreeze(packages[i]);
                }
            }
        }

        // Here is the main package to launch
        String packageName = getIntent().getStringExtra("packageName");

        // Unfreeze the app first
        mPolicyManager.setApplicationHidden(
                new ComponentName(this, ShelterDeviceAdminReceiver.class),
                packageName, false);

        // Query the start intent
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);

        if (launchIntent != null) {
            if (getIntent().getBooleanExtra("shouldFreeze", false)) {
                registerAppToFreeze(packageName);
            }
            startActivity(launchIntent);
        } else {
            // Acknowledge the user that the application cannot be launched
            Toast.makeText(this, getString(R.string.launch_app_fail, packageName), Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private void registerAppToFreeze(String packageName) {
        FreezeService.registerAppToFreeze(packageName);
        startService(new Intent(this, FreezeService.class));
    }

    private void actionPublicFreezeAll() {
        // For now we only support freezing apps in work profile
        // so forward this to DummyActivity in work profile
        // after loading the full list to freeze
        if (!mIsProfileOwner) {
            Intent intent = new Intent(FREEZE_ALL_IN_LIST);
            Utility.transferIntentToProfile(this, intent);
            String[] list = LocalStorageManager.getInstance()
                    .getStringList(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE);
            intent.putExtra("list", list);
            startActivity(intent);
            finish();
        } else {
            throw new RuntimeException("unimplemented");
        }
    }

    private void actionFreezeAllInList() {
        if (mIsProfileOwner) {
            String[] list = getIntent().getStringArrayExtra("list");
            for (String pkg : list) {
                mPolicyManager.setApplicationHidden(
                        new ComponentName(this, ShelterDeviceAdminReceiver.class),
                        pkg, true);
            }
            stopService(new Intent(this, FreezeService.class)); // Stop the auto-freeze service
            Toast.makeText(this, R.string.freeze_all_success, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            finish();
        }
    }

    private void actionStartFileShuttle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // This requires the permission WRITE_EXTERNAL_STORAGE
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                doStartFileShuttle();
            } else {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_EXTERNAL_STORAGE);
            }
        } else {
            // The all file access permission should have been granted when enabling File Shuttle
            // since Android R.
            if (Utility.checkAllFileAccessPermission() && Utility.checkSystemAlertPermission(this)) {
                doStartFileShuttle();
            } else {
                finish();
            }
        }
    }

    private void doStartFileShuttle() {
        ((ShelterApplication) getApplication()).bindFileShuttleService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IFileShuttleService shuttle = IFileShuttleService.Stub.asInterface(service);
                IFileShuttleServiceCallback callback = IFileShuttleServiceCallback.Stub.asInterface(
                        getIntent().getBundleExtra("extra").getBinder("callback"));
                try {
                    callback.callback(shuttle);
                } catch (RemoteException e) {
                    // Do Nothing
                }

                finish();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // Do Nothing
            }
        });
    }

    private void actionSynchronizePreference() {
        String name = getIntent().getStringExtra("name");
        if (getIntent().hasExtra("boolean")) {
            LocalStorageManager.getInstance()
                    .setBoolean(name, getIntent().getBooleanExtra("boolean", false));
        } else if (getIntent().hasExtra("int")) {
            LocalStorageManager.getInstance()
                    .setInt(name, getIntent().getIntExtra("int", Integer.MIN_VALUE));
        }
        // TODO: Cases for other types
        SettingsManager.getInstance().applyAll();
        if (mIsProfileOwner) {
            // Refresh profile policies because
            // settings may have been changed
            Utility.enforceWorkProfilePolicies(this);
        }
        finish();
    }
}
