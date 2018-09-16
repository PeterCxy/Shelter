package net.typeblog.shelter.util;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;

import java.io.FileNotFoundException;
import java.io.IOException;

// A simple and naïve FileProvider which forwards content Uris
// to a given Fd, or fallback to default FileProvider if no Fd is present.
// This is used to work around the limitations of content Uris, which
// can only be opened from the process that was granted the read permission.
// We can send the Fd through AIDL and use this FileProviderProxy to re-generate
// another content Uri that points to the same Fd.
public class FileProviderProxy extends FileProvider {
    private static final String AUTHORITY_NAME = "net.typeblog.shelter.files";
    private static final String FORWARD_PATH_PREFIX = "/forward/"; // All content Uris pointing to this path indicates forwarded Fd.
    private static ParcelFileDescriptor sFd = null;

    // Register the fd to be forwarded
    // This will close the last Fd that we have set
    // Returns the content Uri to be used.
    public static Uri setFd(ParcelFileDescriptor fd, String suffix) {
        clearFd();
        sFd = fd;
        return Uri.parse("content://" + AUTHORITY_NAME + FORWARD_PATH_PREFIX + "temp." + suffix);
    }

    // Close and delete the current Fd to be forwarded.
    public static void clearFd() {
        if (sFd == null) return;

        try {
            sFd.close();
        } catch (IOException e) {
            // ...
        }

        sFd = null;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (uri.getPath().startsWith(FORWARD_PATH_PREFIX) && sFd != null) {
            // If we are now in the FORWARD_PATH_PREFIX
            // We just return the Fd that was registered to be forwarded
            ParcelFileDescriptor fd = sFd;
            sFd = null;
            return fd;
        } else {
            return super.openFile(uri, mode);
        }
    }
}
