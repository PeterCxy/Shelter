// IUriOpener.aidl
package net.typeblog.shelter.util;

import android.os.ParcelFileDescriptor;

interface IUriOpener {
    ParcelFileDescriptor openFile(in String mode);
}
