package net.typeblog.shelter.util;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import androidx.annotation.Nullable;

import net.typeblog.shelter.R;
import net.typeblog.shelter.services.FileShuttleService;
import net.typeblog.shelter.services.IFileShuttleService;
import net.typeblog.shelter.services.IFileShuttleServiceCallback;
import net.typeblog.shelter.ui.DummyActivity;

import java.util.List;
import java.util.Map;

// A document provider to show files across the profile boundary
// in the system's Documents UI.
// This is an interface to FileShuttleService
public class CrossProfileDocumentsProvider extends DocumentsProvider {
    // The dummy root path that will be replaced by the real path to external storage on the other side
    public static final String DUMMY_ROOT = "/shelter_storage_root/";
    private static final String AUTHORITY = "net.typeblog.shelter.documents";
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS
    };
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private IFileShuttleService mService = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    // Periodic task to release the handle to the service
    // Since DocumentsProvider may persist for a long time,
    // We just release the service when idle, thus enabling the
    // system to release memory
    private Runnable mReleaseServiceTask = this::releaseService;
    private Object mLock = new Object();

    private void doBindService() {
        // Call DummyActivity on the other side to bind the service for us
        Intent intent = new Intent(DummyActivity.START_FILE_SHUTTLE);
        Bundle extra = new Bundle();
        extra.putBinder("callback", new IFileShuttleServiceCallback.Stub() {
            @Override
            public void callback(IFileShuttleService service) {
                mService = service;
                synchronized (mLock) {
                    mLock.notifyAll();
                }
            }
        });
        intent.putExtra("extra", extra);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            Utility.transferIntentToProfile(getContext(), intent);
        } catch (IllegalStateException e) {
            // Try with the other action.
            // We use distinct intent for parent -> profile and profile -> parent,
            // to avoid the action chooser dialog
            // so as a dirty hack here, we just try the other if one is not found.
            intent.setAction(DummyActivity.START_FILE_SHUTTLE_2);
            Utility.transferIntentToProfile(getContext(), intent);
        }
        getContext().startActivity(intent);

        // A hack to convert the asynchronous process of starting service to synchronous
        synchronized (mLock) {
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                // ???
            }
        }
    }

    private void ensureServiceBound() {
        if (mService == null) {
            doBindService();
        } else {
            try {
                mService.ping();
                resetReleaseService();
            } catch (RemoteException e) {
                doBindService();
            }
        }
    }

    private void releaseService() {
        mService = null;
    }

    private void resetReleaseService() {
        mHandler.removeCallbacks(mReleaseServiceTask);
        mHandler.postDelayed(mReleaseServiceTask, FileShuttleService.TIMEOUT / 2);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        final MatrixCursor result = new MatrixCursor(projection == null ? DEFAULT_ROOT_PROJECTION : projection);
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, DUMMY_ROOT);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DUMMY_ROOT);
        row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher_egg);
        row.add(DocumentsContract.Root.COLUMN_TITLE,
                Utility.isProfileOwner(getContext()) ?
                        getContext().getString(R.string.fragment_profile_main) :
                        getContext().getString(R.string.fragment_profile_work));
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_LOCAL_ONLY |
                        DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD); // SUPPORTS_IS_CHILD is required for OPEN_DOCUMENT_TREE
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Cursor queryDocument(String documentId, String[] projection) {
        ensureServiceBound();
        final MatrixCursor result = new MatrixCursor(projection == null ? DEFAULT_DOCUMENT_PROJECTION : projection);
        Map<String, Object> fileInfo = null;
        try {
            fileInfo = mService.loadFileMeta(documentId);
        } catch (RemoteException e) {
            return null;
        }
        includeFile(result, fileInfo);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        ensureServiceBound();
        List<Map<String, Object>> files = null;
        try {
            files = mService.loadFiles(parentDocumentId);
        } catch (RemoteException e) {
            return null;
        }
        final MatrixCursor result = new MatrixCursor(projection == null ? DEFAULT_DOCUMENT_PROJECTION : projection);
        // Allow receiving notification on create / delete
        result.setNotificationUri(getContext().getContentResolver(),
                DocumentsContract.buildDocumentUri(AUTHORITY, parentDocumentId));

        for (Map<String, Object> file : files) {
            includeFile(result, file);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, @Nullable CancellationSignal signal) {
        ensureServiceBound();
        try {
            return mService.openFile(documentId, mode);
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal) {
        ensureServiceBound();
        try {
            return new AssetFileDescriptor(
                    mService.openThumbnail(documentId, sizeHint), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName) {
        ensureServiceBound();
        try {
            String ret = mService.createFile(parentDocumentId, mimeType, displayName);
            getContext().getContentResolver().notifyChange(
                    DocumentsContract.buildDocumentUri(AUTHORITY, parentDocumentId), null);
            return ret;
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public void deleteDocument(String documentId) {
        ensureServiceBound();
        try {
            String parent = mService.deleteFile(documentId);
            getContext().getContentResolver().notifyChange(
                    DocumentsContract.buildDocumentUri(AUTHORITY, parent), null);
        } catch (RemoteException e) {

        }
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        ensureServiceBound();
        try {
            return mService.isChildOf(parentDocumentId, documentId);
        } catch (RemoteException e) {
            return false;
        }
    }

    private void includeFile(MatrixCursor cursor, Map<String, Object> fileInfo) {
        final MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : DEFAULT_DOCUMENT_PROJECTION) {
            row.add(col, fileInfo.get(col));
        }
    }
}
