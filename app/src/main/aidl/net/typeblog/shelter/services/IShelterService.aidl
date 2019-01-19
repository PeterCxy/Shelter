// IShelterService.aidl
package net.typeblog.shelter.services;

import android.content.pm.ApplicationInfo;

import net.typeblog.shelter.services.IAppInstallCallback;
import net.typeblog.shelter.services.IGetAppsCallback;
import net.typeblog.shelter.services.ILoadIconCallback;
import net.typeblog.shelter.util.ApplicationInfoWrapper;
import net.typeblog.shelter.util.UriForwardProxy;

interface IShelterService {
    void ping();
    void stopShelterService(boolean kill);
    void getApps(IGetAppsCallback callback);
    void loadIcon(in ApplicationInfoWrapper info, ILoadIconCallback callback);
    void installApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void installApk(in UriForwardProxy uri, IAppInstallCallback callback);
    void uninstallApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void freezeApp(in ApplicationInfoWrapper app);
    void unfreezeApp(in ApplicationInfoWrapper app);
    boolean hasUsageStatsPermission();
    List<String> getCrossProfileWidgetProviders();
    boolean setCrossProfileWidgetProviderEnabled(String pkgName, boolean enabled);
    boolean isShowAll();
    void setShowAll(boolean value);
}
