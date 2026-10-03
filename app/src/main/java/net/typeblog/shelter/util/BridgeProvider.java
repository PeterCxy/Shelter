package net.typeblog.shelter.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.typeblog.shelter.ShelterApplication;

/**
 * BridgeProvider is a silent handshake mechanism.
 * It allows the Personal Profile to obtain the ShelterService binder from the 
 * Work Profile without triggering cross-profile Activity intents, thus avoiding
 * the system's "Using app in work profile" toast notification.
 */
public class BridgeProvider extends ContentProvider {
    public static final String AUTHORITY = "net.typeblog.shelter.bridge";
    public static final String METHOD_GET_BINDER = "get_binder";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (METHOD_GET_BINDER.equals(method)) {
            IBinder binder = ((ShelterApplication) getContext().getApplicationContext()).getShelterServiceBinder();
            if (binder != null) {
                Bundle result = new Bundle();
                result.putBinder("service", binder);
                return result;
            }
        }
        return null;
    }

    // Required overrides but unused
    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
