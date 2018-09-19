// IFileShuttleService.aidl
package net.typeblog.shelter.services;

import android.os.ParcelFileDescriptor;

interface IFileShuttleService {
    void ping();
    List loadFiles(String path);
    Map loadFileMeta(String path);
    ParcelFileDescriptor openFile(String path, String mode);
}
