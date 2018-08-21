// IShelterService.aidl
package net.typeblog.shelter.services;

import android.content.pm.ApplicationInfo;

import net.typeblog.shelter.services.IGetAppsCallback;
import net.typeblog.shelter.services.ILoadIconCallback;

interface IShelterService {
    void stopShelterService(boolean kill);
    void getApps(IGetAppsCallback callback);
    void loadIcon(in ApplicationInfo info, ILoadIconCallback callback);
}
