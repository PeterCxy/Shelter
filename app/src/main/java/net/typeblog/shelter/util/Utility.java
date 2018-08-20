package net.typeblog.shelter.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;
import java.util.stream.Collectors;

public class Utility {
    // Affiliate an Intent to another profile (i.e. the Work profile that we manage)
    public static void transferIntentToProfile(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> info = pm.queryIntentActivities(intent, 0);
        ResolveInfo i = info.stream()
                .filter((r) -> !r.activityInfo.packageName.equals(context.getPackageName()))
                .collect(Collectors.toList()).get(0);
        intent.setComponent(new ComponentName(i.activityInfo.packageName, i.activityInfo.name));
    }
}
