package net.typeblog.shelter.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.typeblog.shelter.R;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.Utility;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
    private static final String SETTINGS_VERSION = "settings_version";
    private static final String SETTINGS_SOURCE_CODE = "settings_source_code";
    private static final String SETTINGS_BUG_REPORT = "settings_bug_report";
    private static final String SETTINGS_CROSS_PROFILE_FILE_CHOOSER = "settings_cross_profile_file_chooser";
    private static final String SETTINGS_CAMERA_PROXY = "settings_camera_proxy";
    private static final String SETTINGS_AUTO_FREEZE_SERVICE = "settings_auto_freeze_service";
    private static final String SETTINGS_SKIP_FOREGROUND = "settings_dont_freeze_foreground";

    private SettingsManager mManager = SettingsManager.getInstance();
    private IShelterService mServiceWork = null;

    private CheckBoxPreference mPrefCrossProfileFileChooser = null;
    private CheckBoxPreference mPrefCameraProxy = null;
    private CheckBoxPreference mPrefAutoFreezeService = null;
    private CheckBoxPreference mPrefSkipForeground = null;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences_settings);
        mServiceWork = IShelterService.Stub.asInterface(
                ((Bundle) getActivity().getIntent().getParcelableExtra("extras")).getBinder("profile_service"));

        // Set the displayed version
        try {
            findPreference(SETTINGS_VERSION).setSummary(
                    getContext().getPackageManager().getPackageInfo(
                            getContext().getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            // WTF?
        }

        // Open source code url on click
        findPreference(SETTINGS_SOURCE_CODE)
                .setOnPreferenceClickListener(this::openSummaryUrl);
        findPreference(SETTINGS_BUG_REPORT)
                .setOnPreferenceClickListener(this::openSummaryUrl);

        // === Interactions ===
        mPrefCrossProfileFileChooser = (CheckBoxPreference) findPreference(SETTINGS_CROSS_PROFILE_FILE_CHOOSER);
        mPrefCrossProfileFileChooser.setChecked(mManager.getCrossProfileFileChooserEnabled());
        mPrefCrossProfileFileChooser.setOnPreferenceChangeListener(this);
        mPrefCameraProxy = (CheckBoxPreference) findPreference(SETTINGS_CAMERA_PROXY);
        mPrefCameraProxy.setChecked(mManager.getCameraProxyEnabled());
        mPrefCameraProxy.setOnPreferenceChangeListener(this);

        // === Services ===
        mPrefAutoFreezeService = (CheckBoxPreference) findPreference(SETTINGS_AUTO_FREEZE_SERVICE);
        mPrefAutoFreezeService.setChecked(mManager.getAutoFreezeServiceEnabled());
        mPrefAutoFreezeService.setOnPreferenceChangeListener(this);
        mPrefSkipForeground = (CheckBoxPreference) findPreference(SETTINGS_SKIP_FOREGROUND);
        mPrefSkipForeground.setChecked(mManager.getSkipForegroundEnabled());
        mPrefSkipForeground.setOnPreferenceChangeListener(this);
    }

    private boolean openSummaryUrl(Preference pref) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(pref.getSummary().toString()));
        startActivity(intent);
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newState) {
        if (preference == mPrefCrossProfileFileChooser) {
            mManager.setCrossProfileFileChooserEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefCameraProxy) {
            mManager.setCameraProxyEnabled(((boolean) newState));
            return true;
        } else if (preference == mPrefAutoFreezeService) {
            mManager.setAutoFreezeServiceEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefSkipForeground) {
            boolean enabled = (boolean) newState;
            boolean hasPermission = false;
            try {
                hasPermission = mServiceWork.hasUsageStatsPermission() && Utility.checkUsageStatsPermission(getContext());
            } catch (RemoteException e) {

            }
            if (!enabled || hasPermission) {
                mManager.setSkipForegroundEnabled(enabled);
                return true;
            } else {
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.request_usage_stats)
                        .setPositiveButton(android.R.string.ok,
                                (dialog, which) -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                        .setNegativeButton(android.R.string.cancel,
                                (dialog, which) -> dialog.dismiss())
                        .show();
                return false;
            }
        } else {
            return false;
        }
    }
}
