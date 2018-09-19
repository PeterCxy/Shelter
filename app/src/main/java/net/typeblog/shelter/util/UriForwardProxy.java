package net.typeblog.shelter.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;

import java.io.FileNotFoundException;

// A wrapper over Uri to remotely open an Uri through AIDL
// This is used to forward Content URIs through the profile
// boundary.
public class UriForwardProxy implements Parcelable {
    public static final Parcelable.Creator<UriForwardProxy> CREATOR = new Parcelable.Creator<UriForwardProxy>() {
        @Override
        public UriForwardProxy[] newArray(int size) {
            return new UriForwardProxy[0];
        }

        @Override
        public UriForwardProxy createFromParcel(Parcel source) {
            IBinder[] arr = new IBinder[1];
            source.readBinderArray(arr);
            IUriOpener opener = IUriOpener.Stub.asInterface(arr[0]);
            return new UriForwardProxy(opener);
        }
    };

    private IUriOpener mOpener;

    private UriForwardProxy(IUriOpener opener) {
        mOpener = opener;
    }

    public UriForwardProxy(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        mOpener = new IUriOpener.Stub() {
            @Override
            public ParcelFileDescriptor openFile(String mode) {
                try {
                    return resolver.openFileDescriptor(uri, mode);
                } catch (FileNotFoundException e) {
                    return null;
                }
            }
        };
    }

    public ParcelFileDescriptor open(String mode) {
        try {
            return mOpener.openFile(mode);
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBinderArray(new IBinder[]{mOpener.asBinder()});
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
