package net.typeblog.shelter.util;

import android.app.Activity;
import android.content.pm.PackageInstaller;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;

import net.typeblog.shelter.R;

public class InstallationProgressListener extends PackageInstaller.SessionCallback {
    private AlertDialog mDialog;
    private ProgressBar mProgress;
    private int mSessionId;
    private PackageInstaller mPi;

    // Create a listener from an activity, and show a progress dialog for the sessionId
    // Only cares about the one sessionId provided here.
    // The caller is responsible for registering the callback;
    // however, this class will remove itself once the session has been finished.
    public InstallationProgressListener(Activity activity, PackageInstaller pi, int sessionId) {
        mPi = pi;

        ViewGroup layout = (ViewGroup) LayoutInflater.from(activity)
                .inflate(R.layout.progress_dialog, (ViewGroup) activity.getWindow().getDecorView(), false);
        mProgress = layout.findViewById(R.id.progress);

        mDialog = new AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle(R.string.app_installing)
                .setView(layout)
                .create();
        mDialog.show();
    }

    @Override
    public void onCreated(int sessionId) {

    }

    @Override
    public void onBadgingChanged(int sessionId) {

    }

    @Override
    public void onActiveChanged(int sessionId, boolean active) {

    }

    @Override
    public void onProgressChanged(int sessionId, float progress) {
        mProgress.setProgress((int) (progress * 100));
    }

    @Override
    public void onFinished(int sessionId, boolean success) {
        if (sessionId != mSessionId) {
            return;
        }

        mDialog.hide();
        mPi.unregisterSessionCallback(this);
    }
}
