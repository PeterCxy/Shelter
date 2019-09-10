package net.typeblog.shelter.util;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.UserManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

import net.typeblog.shelter.R;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.ui.DummyActivity;
import net.typeblog.shelter.ui.MainActivity;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Utility {
    // Determine if the current app is the owner of the current profile
    // TODO: Replace all occurrences of duplicated code to call this function instead
    public static boolean isProfileOwner(Context context) {
        return context.getSystemService(DevicePolicyManager.class)
                .isProfileOwnerApp(context.getPackageName());
    }

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
        transferIntentToProfileUnsigned(context, intent);
        // Add signature
        AuthenticationUtility.signIntent(intent);
    }

    public static void transferIntentToProfileUnsigned(Context context, Intent intent) {
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
                new IntentFilter(DummyActivity.PUBLIC_FREEZE_ALL),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED); // Used by FreezeService in profile

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.FINALIZE_PROVISION),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.START_FILE_SHUTTLE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.START_FILE_SHUTTLE_2),
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        manager.addCrossProfileIntentFilter(
                adminComponent,
                new IntentFilter(DummyActivity.SYNCHRONIZE_PREFERENCE),
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);

        // Allow ACTION_SEND and ACTION_SEND_MULTIPLE to cross from managed to parent
        // TODO: Make this configurable
        IntentFilter actionSendFilter = new IntentFilter();
        actionSendFilter.addAction(Intent.ACTION_SEND);
        actionSendFilter.addAction(Intent.ACTION_SEND_MULTIPLE);
        try {
            actionSendFilter.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException ignored) {
            // WTF?
        }
        actionSendFilter.addCategory(Intent.CATEGORY_DEFAULT);
        manager.addCrossProfileIntentFilter(
                adminComponent,
                actionSendFilter,
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

    // Detect if the device is MIUI
    public static boolean isMIUI() {
        try {
            Process proc = Runtime.getRuntime().exec("getprop ro.miui.ui.version.name");
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = reader.readLine().trim();
            return !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
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

    public static int getMediaStoreId(Context context, String path) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DATA + " LIKE ? ",
                new String[]{path}, null);
        if (cursor == null || cursor.getCount() == 0) {
            return -1;
        } else {
            cursor.moveToFirst();
            return cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
        }
    }

    // Functions to load scaled down version of Bitmap
    // from <https://developer.android.com/topic/performance/graphics/load-bitmap?hl=es#java>
    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmap(String filePath,
                                             int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    public static Bitmap decodeSampledBitmap(FileDescriptor fd,
                                             int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    // Get file's extension name
    public static String getFileExtension(String filePath) {
        int index = filePath.lastIndexOf(".");
        if (index > 0) {
            return filePath.substring(index + 1);
        } else {
            return null;
        }
    }

    // Check if USAGE_STATS is granted
    public static boolean checkUsageStatsPermission(Context context) {
        return checkSpecialAccessPermission(context, AppOpsManager.OPSTR_GET_USAGE_STATS);
    }

    // Check special access permission through AppOps
    public static boolean checkSpecialAccessPermission(Context context, String name) {
        AppOpsManager appops = context.getSystemService(AppOpsManager.class);
        int mode = appops.checkOpNoThrow(name, android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // Pipe an InputStream to OutputStream
    public static void pipe(InputStream is, OutputStream os) throws IOException {
        int n;
        byte[] buffer = new byte[65536];
        while ((n = is.read(buffer)) > -1) {
            os.write(buffer, 0, n);
        }
    }

    // Utilities to build notifications for cross-version compatibility
    private static final String NOTIFICATION_CHANNEL_ID = "ShelterService";
    private static final String NOTIFICATION_CHANNEL_IMPORTANT = "ShelterService-Important";
    public static Notification buildNotification(Context context, String ticker, String title, String desc, int icon) {
        return buildNotification(context, false, ticker, title, desc, icon);
    }

    public static Notification buildNotification(Context context, boolean important, String ticker, String title, String desc, int icon) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return buildNotificationOreo(context, important, ticker, title, desc, icon);
        } else {
            return buildNotificationLollipop(context, important, ticker, title, desc, icon);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static Notification buildNotificationLollipop(Context context, boolean important, String ticker, String title, String desc, int icon) {
        return new Notification.Builder(context)
                .setTicker(ticker)
                .setContentTitle(title)
                .setContentText(desc)
                .setSmallIcon(icon)
                .setPriority(important ? Notification.PRIORITY_MAX : Notification.PRIORITY_MIN)
                .build();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static Notification buildNotificationOreo(Context context, boolean important, String ticker, String title, String desc, int icon) {
        String id = important ? NOTIFICATION_CHANNEL_IMPORTANT : NOTIFICATION_CHANNEL_ID;
        // Android O and later: Notification Channel
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(id) == null) {
            NotificationChannel chan = new NotificationChannel(
                    id,
                    important ? context.getString(R.string.notifications_important)
                            : context.getString(R.string.app_name),
                    important ? NotificationManager.IMPORTANCE_HIGH
                            : NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(chan);
        }

        // Disable everything: do not disturb the user
        NotificationChannel chan = nm.getNotificationChannel(id);
        if (!important) {
            chan.enableVibration(false);
            chan.enableLights(false);
            chan.setImportance(NotificationManager.IMPORTANCE_MIN);
        } else {
            chan.enableVibration(true);
            chan.setImportance(NotificationManager.IMPORTANCE_HIGH);
        }
        nm.createNotificationChannel(chan);

        // Create foreground notification to keep the service alive
        return new Notification.Builder(context, id)
                .setTicker(ticker)
                .setContentTitle(title)
                .setContentText(desc)
                .setSmallIcon(icon)
                .build();
    }
}
