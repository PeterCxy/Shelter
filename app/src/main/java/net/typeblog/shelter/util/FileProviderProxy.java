package net.typeblog.shelter.util;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.FileNotFoundException;

// A simple and naïve FileProvider which forwards content Uris
// to a given Uri from another profile through UriForwardProxy
// This class can, for now, only forward one Uri each time.
// It forwards all requests to one Uri assigned through
// static methods.
public class FileProviderProxy extends FileProvider {
    private static final String AUTHORITY_NAME = "net.typeblog.shelter.files";
    private static final String FORWARD_PATH_PREFIX = "/forward/"; // All content Uris pointing to this path indicates forwarded Fd.
    private static UriForwardProxy sProxy = null;

    // Register the Uri to be forwarded
    // Returns an automatically generated temporary Uri for this request
    public static Uri setUriForwardProxy(UriForwardProxy proxy, String suffix) {
        sProxy = proxy;
        return Uri.parse("content://" + AUTHORITY_NAME + FORWARD_PATH_PREFIX + "temp." + suffix);
    }

    // Close and delete the current forwarder
    public static void clearForwardProxy() {
        sProxy = null;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (uri.getPath().startsWith(FORWARD_PATH_PREFIX) && sProxy != null) {
            // If we are now in the FORWARD_PATH_PREFIX
            // We just forward the request to the UriForwardProxy
            return sProxy.open(mode);
        } else {
            return super.openFile(uri, mode);
        }
    }
}
