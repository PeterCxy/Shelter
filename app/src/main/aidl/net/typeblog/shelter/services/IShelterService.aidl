// IShelterService.aidl
package net.typeblog.shelter.services;

import android.content.pm.ResolveInfo;

interface IShelterService {
    void stopShelterService(boolean kill);
    List<ResolveInfo> getApps();
}
