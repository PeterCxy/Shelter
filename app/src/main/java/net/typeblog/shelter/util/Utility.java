package net.typeblog.shelter.util;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.Toast;

import net.typeblog.shelter.R;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.ui.MainActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Utility {
    // Polyfill for String.join
    public static String stringJoin(String delimiter, String[] list) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return String.join(delimiter, list);
        } else {
            if (list.length == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.length - 1; i++) {
                sb.append(list[i]).append(delimiter);
            }
            sb.append(list[list.length - 1]);
            return sb.toString();
        }
    }

    // Affiliate an Intent to another profile (i.e. the Work profile that we manage)
    // This method cares nothing about if the other profile even exists.
    // When there is no other profile, this method would just simply throw
    // an IndexOutOfBoundException
    // which can be caught and resolved.
    public static void transferIntentToProfile(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> info = pm.queryIntentActivities(intent, 0);
        Optional<ResolveInfo> i = info.stream()
                .filter((r) -> !r.activityInfo.packageName.equals(context.getPackageName()))
                .findFirst();
        if (i.isPresent()) {
            intent.setComponent(new ComponentName(i.get().activityInfo.packageName, i.get().activityInfo.name));
        } else {
            throw new IllegalStateException("Cannot find an intent in other profile");
        }
    }

    // Enforce policies and configurations in the work profile
    public static void enforceWorkProfilePolicies(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        ComponentName adminComponent = new ComponentName(context.getApplicationContext(), ShelterDeviceAdminReceiver.class);

        // Hide this app in the work profile
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context.getApplicationContext(), MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);

        // Clear everything first to ensure our policies are set properly
        manager.clearCrossProfileIntentFilters(adminComponent);

        // Allow cross-profile intents for START_SERVICE
        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.START_SERVICE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.TRY_START_SERVICE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.UNFREEZE_AND_LAUNCH),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.FREEZE_ALL_IN_LIST),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.FINALIZE_PROVISION),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        // Browser intents are allowed from work profile to parent
        // TODO: Make this configurable, just as ALLOW_PARENT_PROFILE_APP_LINKING in the next function
        IntentFilter i = new IntentFilter(Intent.ACTION_VIEW);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.addDataScheme("http");
        i.addDataScheme("https");
        manager.addCrossProfileIntentFilter(
                adminComponent,
                i,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        manager.setProfileEnabled(adminComponent);
    }

    public static void enforceUserRestrictions(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        ComponentName adminComponent = new ComponentName(context.getApplicationContext(), ShelterDeviceAdminReceiver.class);
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS);
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Polyfill for UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES
            // Don't use this on Android Oreo and later, it will crash
            manager.setSecureSetting(adminComponent, Settings.Secure.INSTALL_NON_MARKET_APPS, "1");
        }

        // TODO: This should be configured by the user, instead of being enforced each time Shelter starts
        // TODO: But we should also have some default restrictions that are set the first time Shelter starts
        manager.addUserRestriction(adminComponent, UserManager.ALLOW_PARENT_PROFILE_APP_LINKING);
    }

    // From <https://stackoverflow.com/questions/3035692/how-to-convert-a-drawable-to-a-bitmap>
    public static Bitmap drawableToBitmap(Drawable drawable) {
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

    // Delete apps that no longer exist from the auto freeze list
    public static void deleteMissingApps(String pref, List<ApplicationInfoWrapper> apps) {
        List<String> list = new ArrayList<>(
                Arrays.asList(LocalStorageManager.getInstance().getStringList(pref)));
        list.removeIf((it) -> apps.stream().noneMatch((x) -> x.getPackageName().equals(it)));
        LocalStorageManager.getInstance().setStringList(pref, list.toArray(new String[]{}));
    }

    public static void createLauncherShortcut(Context context, Intent launchIntent, Icon icon, String id, String label) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);

            if (shortcutManager.isRequestPinShortcutSupported()) {
                ShortcutInfo info = new ShortcutInfo.Builder(context, id)
                        .setIntent(launchIntent)
                        .setIcon(icon)
                        .setShortLabel(label)
                        .setLongLabel(label)
                        .build();
                Intent addIntent = shortcutManager.createShortcutResultIntent(info);
                shortcutManager.requestPinShortcut(info,
                        PendingIntent.getBroadcast(context, 0, addIntent, 0).getIntentSender());
            } else {
                // TODO: Maybe implement this for launchers without pin shortcut support?
                // TODO: Should be the same with the fallback for Android < O
                // for now just show unsupported
                Toast.makeText(context, context.getString(R.string.unsupported_launcher), Toast.LENGTH_LONG).show();
            }
        } else {
            Intent shortcutIntent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
            shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
            shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
            shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, drawableToBitmap(icon.loadDrawable(context)));
            context.sendBroadcast(shortcutIntent);
            Toast.makeText(context, R.string.shortcut_create_success, Toast.LENGTH_SHORT).show();
        }
    }
}
