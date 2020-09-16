package net.typeblog.shelter.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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

import mobi.upod.timedurationpicker.TimeDurationPicker;
import mobi.upod.timedurationpicker.TimeDurationPickerDialogFragment;
import mobi.upod.timedurationpicker.TimeDurationUtil;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
    private static final String SETTINGS_VERSION = "settings_version";
    private static final String SETTINGS_SOURCE_CODE = "settings_source_code";
    private static final String SETTINGS_TRANSLATE = "settings_translate";
    private static final String SETTINGS_BUG_REPORT = "settings_bug_report";
    private static final String SETTINGS_PATREON = "settings_patreon";
    private static final String SETTINGS_CROSS_PROFILE_FILE_CHOOSER = "settings_cross_profile_file_chooser";
    private static final String SETTINGS_CAMERA_PROXY = "settings_camera_proxy";
    private static final String SETTINGS_BLOCK_CONTACTS_SEARCHING = "settings_block_contacts_searching";
    private static final String SETTINGS_AUTO_FREEZE_SERVICE = "settings_auto_freeze_service";
    private static final String SETTINGS_AUTO_FREEZE_DELAY = "settings_auto_freeze_delay";
    private static final String SETTINGS_SKIP_FOREGROUND = "settings_dont_freeze_foreground";

    private SettingsManager mManager = SettingsManager.getInstance();
    private IShelterService mServiceWork = null;

    private CheckBoxPreference mPrefCrossProfileFileChooser = null;
    private CheckBoxPreference mPrefCameraProxy = null;
    private CheckBoxPreference mPrefBlockContactsSearching = null;
    private CheckBoxPreference mPrefAutoFreezeService = null;
    private CheckBoxPreference mPrefSkipForeground = null;

    private Preference mPrefAutoFreezeDelay = null;

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
        findPreference(SETTINGS_PATREON)
                .setOnPreferenceClickListener(this::openSummaryUrl);
        findPreference(SETTINGS_TRANSLATE)
                .setOnPreferenceClickListener(this::openSummaryUrl);

        // === Interactions ===
        mPrefCrossProfileFileChooser = (CheckBoxPreference) findPreference(SETTINGS_CROSS_PROFILE_FILE_CHOOSER);
        mPrefCrossProfileFileChooser.setChecked(mManager.getCrossProfileFileChooserEnabled());
        mPrefCrossProfileFileChooser.setOnPreferenceChangeListener(this);
        mPrefCameraProxy = (CheckBoxPreference) findPreference(SETTINGS_CAMERA_PROXY);
        mPrefCameraProxy.setChecked(mManager.getCameraProxyEnabled());
        mPrefCameraProxy.setOnPreferenceChangeListener(this);
        mPrefBlockContactsSearching = (CheckBoxPreference) findPreference(SETTINGS_BLOCK_CONTACTS_SEARCHING);
        mPrefBlockContactsSearching.setChecked(mManager.getBlockContactsSearchingEnabled());
        mPrefBlockContactsSearching.setOnPreferenceChangeListener(this);

        // === Services ===
        mPrefAutoFreezeService = (CheckBoxPreference) findPreference(SETTINGS_AUTO_FREEZE_SERVICE);
        mPrefAutoFreezeService.setChecked(mManager.getAutoFreezeServiceEnabled());
        mPrefAutoFreezeService.setOnPreferenceChangeListener(this);
        mPrefAutoFreezeDelay = findPreference(SETTINGS_AUTO_FREEZE_DELAY);
        mPrefAutoFreezeDelay.setOnPreferenceClickListener(this::openAutoFreezeDelayPicker);
        updateAutoFreezeDelay();
        mPrefSkipForeground = (CheckBoxPreference) findPreference(SETTINGS_SKIP_FOREGROUND);
        mPrefSkipForeground.setChecked(mManager.getSkipForegroundEnabled());
        mPrefSkipForeground.setOnPreferenceChangeListener(this);

        // Disable FileSuttle on Q for now
        // Supported on R and beyond
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            mPrefCrossProfileFileChooser.setEnabled(false);
        }

        // Disable fake camera on R because third-party camera activities are now unsupported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mPrefCameraProxy.setEnabled(false);
        }

        // Disable FileShuttle for Android Go
        // as it requires SYSTEM_ALERT_WINDOW which
        // is not allowed on Go devices
        ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am.isLowRamDevice()) {
            mPrefCrossProfileFileChooser.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update all preferences that may change when returning
        // i.e. preferences that open another dialog for picking
        updateAutoFreezeDelay();
    }

    private void updateAutoFreezeDelay() {
        mPrefAutoFreezeDelay.setSummary(TimeDurationUtil.formatMinutesSeconds(
                ((long) mManager.getAutoFreezeDelay()) * 1000
        ));
    }

    private boolean openSummaryUrl(Preference pref) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(pref.getSummary().toString()));
        startActivity(intent);
        return true;
    }

    private boolean openAutoFreezeDelayPicker(Preference pref) {
        new AutoFreezeDelayPickerFragment().show(getActivity().getFragmentManager(), "dialog");
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newState) {
        if (preference == mPrefCrossProfileFileChooser) {
            boolean enabled = (boolean) newState;
            if (!enabled) {
                mManager.setCrossProfileFileChooserEnabled(false);
                return true;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Request all files permission on R and beyond
                boolean hasPermission = ensureSpecialAccessPermission(() -> {
                    try {
                        return mServiceWork.hasAllFileAccessPermission() && Utility.checkAllFileAccessPermission();
                    } catch (RemoteException e) {
                        return false;
                    }
                }, R.string.request_storage_manager, Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);

                if (!hasPermission) {
                    return false;
                }

                // Also needs system alert window permission
                // because File Shuttle needs to start activities in the background
                // We cannot do the same notification trick as in initial setup
                // because it would be too annoying having to click a notification
                // every time a user tries to use File Shuttle.
                // NOTE: Enabling this permission may mask some bugs with background
                // activities. Always test with this disabled.
                hasPermission = ensureSpecialAccessPermission(() -> {
                    try {
                        return mServiceWork.hasSystemAlertPermission() && Utility.checkSystemAlertPermission(getContext());
                    } catch (RemoteException e) {
                        return false;
                    }
                }, R.string.request_system_alert, Settings.ACTION_MANAGE_OVERLAY_PERMISSION);

                if (!hasPermission) {
                    return false;
                }
            }

            mManager.setCrossProfileFileChooserEnabled(true);
            return true;
        } else if (preference == mPrefCameraProxy) {
            mManager.setCameraProxyEnabled(((boolean) newState));
            return true;
        } else if (preference == mPrefBlockContactsSearching) {
            mManager.setBlockContactsSearchingEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefAutoFreezeService) {
            mManager.setAutoFreezeServiceEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefSkipForeground) {
            boolean enabled = (boolean) newState;
            if (!enabled) {
                mManager.setSkipForegroundEnabled(false);
                return true;
            }

            boolean hasPermission = ensureSpecialAccessPermission(() -> {
                try {
                    return mServiceWork.hasUsageStatsPermission() && Utility.checkUsageStatsPermission(getContext());
                } catch (RemoteException e) {
                    return false;
                }
            }, R.string.request_usage_stats, Settings.ACTION_USAGE_ACCESS_SETTINGS);

            if (!hasPermission)
                return false;

            mManager.setSkipForegroundEnabled(true);
            return true;
        } else {
            return false;
        }
    }

    private interface CheckPermissionCallback {
        boolean check();
    }

    private boolean ensureSpecialAccessPermission(CheckPermissionCallback checkPermission, int alertRes, String settingsAction) {
        if (!checkPermission.check()) {
            new AlertDialog.Builder(getContext())
                    .setMessage(alertRes)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> startActivity(new Intent(settingsAction)))
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> dialog.dismiss())
                    .show();
            return false;
        } else {
            return true;
        }
    }

    public static class AutoFreezeDelayPickerFragment extends TimeDurationPickerDialogFragment {
        @Override
        protected long getInitialDuration() {
            return ((long) SettingsManager.getInstance().getAutoFreezeDelay()) * 1000;
        }

        @Override
        protected int setTimeUnits() {
            return TimeDurationPicker.MM_SS;
        }

        @Override
        public void onDurationSet(TimeDurationPicker view, long duration) {
            long seconds = duration / 1000;
            if (seconds >= Integer.MAX_VALUE) return;
            SettingsManager.getInstance().setAutoFreezeDelay((int) seconds);
        }
    }
}
