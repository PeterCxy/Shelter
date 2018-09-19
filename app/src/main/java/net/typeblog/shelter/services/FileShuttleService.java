package net.typeblog.shelter.services;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.webkit.MimeTypeMap;

import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.util.CrossProfileDocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A service to forward file information across the profile boundary
public class FileShuttleService extends Service {
    public static final long TIMEOUT = 10000;
    // Periodic task to stop the service when idle.
    // This service does not need to persist.
    private Runnable mSuicideTask = this::suicide;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private IFileShuttleService.Stub mStub = new IFileShuttleService.Stub() {
        @Override
        public void ping() {
            // Dummy method
            resetSuicideTask();
        }

        @Override
        public List<Map> loadFiles(String path) {
            resetSuicideTask();
            ArrayList<Map> ret = new ArrayList<>();
            File f = new File(resolvePath(path));
            if (f.listFiles() != null) {
                for (File child : f.listFiles()) {
                    ret.add(loadFileMeta(child.getPath()));
                }
            }
            return ret;
        }

        @Override
        public Map loadFileMeta(String path) {
            resetSuicideTask();
            File f = new File(resolvePath(path));
            HashMap<String, Object> map = new HashMap<>();
            map.put(DocumentsContract.Document.COLUMN_DOCUMENT_ID, f.getAbsolutePath());
            map.put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, f.getName());
            map.put(DocumentsContract.Document.COLUMN_SIZE, f.length());
            map.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, f.lastModified());
            map.put(DocumentsContract.Document.COLUMN_FLAGS, 0);

            if (f.isDirectory()) {
                map.put(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
            } else {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl("file://" + f.getAbsolutePath()));
                map.put(DocumentsContract.Document.COLUMN_MIME_TYPE, mime);
            }
            return map;
        }

        @Override
        public ParcelFileDescriptor openFile(String path, String mode) {
            resetSuicideTask();
            File f = new File(resolvePath(path));

            try {
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
            } catch (FileNotFoundException e) {
                return null;
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        resetSuicideTask();
        return mStub;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.d("FileShuttleService", "being destroyed");
    }

    private String resolvePath(String path) {
        if (path.startsWith(CrossProfileDocumentsProvider.DUMMY_ROOT)) {
            return path.replaceFirst(CrossProfileDocumentsProvider.DUMMY_ROOT,
                    Environment.getExternalStorageDirectory().getAbsolutePath());
        } else {
            return path;
        }
    }

    private void resetSuicideTask() {
        mHandler.removeCallbacks(mSuicideTask);
        mHandler.postDelayed(mSuicideTask, TIMEOUT);
    }

    private void suicide() {
        mHandler.removeCallbacks(mSuicideTask);
        ((ShelterApplication) getApplication()).unbindFileShuttleService();
        stopSelf();
    }
}
