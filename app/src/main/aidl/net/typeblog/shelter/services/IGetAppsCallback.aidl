// IGetAppsCallback.aidl
package net.typeblog.shelter.services;

import net.typeblog.shelter.util.ApplicationInfoWrapper;

interface IGetAppsCallback {
    void callback(in List<ApplicationInfoWrapper> apps);
}
