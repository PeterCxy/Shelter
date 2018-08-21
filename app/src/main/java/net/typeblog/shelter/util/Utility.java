package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.ui.MainActivity;

import java.util.List;
import java.util.stream.Collectors;

public class Utility {
    // Affiliate an Intent to another profile (i.e. the Work profile that we manage)
    // This method cares nothing about if the other profile even exists.
    // When there is no other profile, this method would just simply throw
    // an IndexOutOfBoundException
    // which can be caught and resolved.
    public static void transferIntentToProfile(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> info = pm.queryIntentActivities(intent, 0);
        ResolveInfo i = info.stream()
                .filter((r) -> !r.activityInfo.packageName.equals(context.getPackageName()))
                .collect(Collectors.toList()).get(0);
        intent.setComponent(new ComponentName(i.activityInfo.packageName, i.activityInfo.name));
    }

    // Enforce policies and configurations in the work profile
    public static void enforceWorkProfilePolicies(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        ComponentName adminComponent = new ComponentName(context.getApplicationContext(), ShelterDeviceAdminReceiver.class);

        // Hide this app in the work profile
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context.getApplicationContext(), MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);

        // Allow cross-profile intents for START_SERVICE
        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.START_SERVICE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.TRY_START_SERVICE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);
    }

    // From <https://stackoverflow.com/questions/3035692/how-to-convert-a-drawable-to-a-bitmap>
    public static Bitmap drawableToBitmap (Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static void killShelterServices(IShelterService serviceMain, IShelterService serviceWork) {
        // Ensure that all our other services are killed at this point
        try {
            serviceWork.stopShelterService(true);
        } catch (Exception e) {
            // We are stopping anyway
        }

        try {
            serviceMain.stopShelterService(false);
        } catch (Exception e) {
            // We are stopping anyway
        }
    }
}
