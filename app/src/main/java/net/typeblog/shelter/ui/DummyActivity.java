package net.typeblog.shelter.ui;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.widget.Toast;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.services.IAppInstallCallback;
import net.typeblog.shelter.util.Utility;

import java.io.File;

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

    private static final int REQUEST_INSTALL_PACKAGE = 1;

    private boolean mIsProfileOwner = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIsProfileOwner = getSystemService(DevicePolicyManager.class).isProfileOwnerApp(getPackageName());
        if (mIsProfileOwner) {
            // If we are the profile owner, we enforce all our policies
            // so that we can make sure those are updated with our app
            Utility.enforceWorkProfilePolicies(this);
            Utility.enforceUserRestrictions(this);
        }

        Intent intent = getIntent();
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
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_INSTALL_PACKAGE) {
            appInstallFinished(resultCode);
        }
    }

    private void actionFinalizeProvision() {
        // This is the action used by DeviceAdminReceiver to finalize the setup
        // The work has been finished in onCreate(), now we just have to
        // inform the user to restart the main activity
        Toast.makeText(this, getString(R.string.provision_finished), Toast.LENGTH_LONG).show();
        finish();
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
        Uri uri = Uri.fromParts("package", getIntent().getStringExtra("package"), null);
        StrictMode.VmPolicy policy = StrictMode.getVmPolicy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // I really have no idea about why the "package:" uri do not work
            // after Android O, anyway we fall back to using the apk path...
            // Since I have plan to support pre-O in later versions, I keep this
            // branch in case that we reduce minSDK in the future.
            uri = Uri.fromFile(new File(getIntent().getStringExtra("apk")));

            // A permissive VmPolicy must be set to work around
            // the limitation on cross-application Uri
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        }

        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri);
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, getPackageName());
        intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        startActivityForResult(intent, REQUEST_INSTALL_PACKAGE);

        // Restore the VmPolicy anyway
        StrictMode.setVmPolicy(policy);
    }

    private void actionUninstallPackage() {
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

    private void appInstallFinished(int resultCode) {
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
}
