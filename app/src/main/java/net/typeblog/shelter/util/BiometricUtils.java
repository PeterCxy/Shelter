package net.typeblog.shelter.util;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;

public class BiometricUtils {

    public static boolean isBiometricPromptEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_BIOMETRIC) == PackageManager.PERMISSION_GRANTED;
        return false;
    }

    /* Condition 1: Check if the device has fingerprint sensors. */
    public static boolean isHardwareSupported(Context context) {
        FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(context);
        return fingerprintManager.isHardwareDetected();
    }

    /* Condition 2: Fingerprint authentication can be matched with a
     * registered fingerprint of the user. So we need to perform this check
     * in order to enable fingerprint authentication*/
    public static boolean isFingerprintAvailable(Context context) {
        FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(context);
        return fingerprintManager.hasEnrolledFingerprints();
    }

    /* Condition 3: Check if the permission has been added to
     * the app. This permission will be granted as soon as the user
     * installs the app on their device.*/
    public static boolean isPermissionGranted(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) ==
                PackageManager.PERMISSION_GRANTED;
    }
}
