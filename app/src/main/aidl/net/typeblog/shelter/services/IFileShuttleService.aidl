// IFileShuttleService.aidl
package net.typeblog.shelter.services;

import android.graphics.Point;
import android.os.ParcelFileDescriptor;

interface IFileShuttleService {
    void ping();
    List loadFiles(String path);
    Map loadFileMeta(String path);
    ParcelFileDescriptor openFile(String path, String mode);
    ParcelFileDescriptor openThumbnail(String path, in Point sizeHint);
    String createFile(String path, String mimeType, String displayName);
    String deleteFile(String path);
    boolean isChildOf(String parentPath, String childPath);
}
