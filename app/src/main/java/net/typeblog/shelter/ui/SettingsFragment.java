package net.typeblog.shelter.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.DeadObjectException;
import android.util.Log;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.util.LocalStorageManager;

import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.typeblog.shelter.R;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.util.SettingsManager;
import net.typeblog.shelter.util.Utility;

import java.util.Arrays;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
    private static final String SETTINGS_VERSION = "settings_version";
    private static final String SETTINGS_SOURCE_CODE = "settings_source_code";
    private static final String SETTINGS_TRANSLATE = "settings_translate";
    private static final String SETTINGS_BUG_REPORT = "settings_bug_report";
    private static final String SETTINGS_PATREON = "settings_patreon";
    private static final String SETTINGS_CROSS_PROFILE_FILE_CHOOSER = "settings_cross_profile_file_chooser";
    private static final String SETTINGS_BLOCK_CONTACTS_SEARCHING = "settings_block_contacts_searching";
    private static final String SETTINGS_AUTO_FREEZE_SERVICE = "settings_auto_freeze_service";
    private static final String SETTINGS_AUTO_FREEZE_DELAY = "settings_auto_freeze_delay";
    private static final String SETTINGS_SKIP_FOREGROUND = "settings_dont_freeze_foreground";
    private static final String SETTINGS_PAYMENT_STUB = "settings_payment_stub";
    private static final String SETTINGS_DESTROY_PROFILE = "settings_destroy_profile";


    private static final int[] AUTO_FREEZE_DELAY_SECONDS = new int[]{0, 60, 2 * 60, 5 * 60};

    private SettingsManager mManager = SettingsManager.getInstance();
    private IShelterService mServiceWork = null;

    private CheckBoxPreference mPrefCrossProfileFileChooser = null;
    private CheckBoxPreference mPrefBlockContactsSearching = null;
    private CheckBoxPreference mPrefAutoFreezeService = null;
    private CheckBoxPreference mPrefSkipForeground = null;
    private CheckBoxPreference mPrefPaymentStub = null;

    private DropDownPreference mPrefAutoFreezeDelay = null;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(androidx.preference.R.id.recycler_view), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPaddingRelative(0, 0, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences_settings);
        Bundle extras = getActivity() != null && getActivity().getIntent() != null ?
                getActivity().getIntent().getParcelableExtra("extras") : null;
        if (extras != null && extras.getBinder("profile_service") != null) {
            mServiceWork = IShelterService.Stub.asInterface(extras.getBinder("profile_service"));
        }

        Preference destroyProfilePref = findPreference(SETTINGS_DESTROY_PROFILE);
        if (destroyProfilePref != null) {
            destroyProfilePref.setOnPreferenceClickListener(pref -> {
                showDestroyProfileDialog();
                return true;
            });
        }

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
        mPrefBlockContactsSearching = (CheckBoxPreference) findPreference(SETTINGS_BLOCK_CONTACTS_SEARCHING);
        mPrefBlockContactsSearching.setChecked(mManager.getBlockContactsSearchingEnabled());
        mPrefBlockContactsSearching.setOnPreferenceChangeListener(this);
        mPrefPaymentStub = (CheckBoxPreference) findPreference(SETTINGS_PAYMENT_STUB);
        mPrefPaymentStub.setChecked(mManager.getPaymentStubEnabled());
        mPrefPaymentStub.setOnPreferenceChangeListener(this);

        // === Services ===
        mPrefAutoFreezeService = (CheckBoxPreference) findPreference(SETTINGS_AUTO_FREEZE_SERVICE);
        mPrefAutoFreezeService.setChecked(mManager.getAutoFreezeServiceEnabled());
        mPrefAutoFreezeService.setOnPreferenceChangeListener(this);
        mPrefAutoFreezeDelay = findPreference(SETTINGS_AUTO_FREEZE_DELAY);
        mPrefAutoFreezeDelay.setOnPreferenceChangeListener(this);
        mPrefAutoFreezeDelay.setEntries(Arrays.stream(AUTO_FREEZE_DELAY_SECONDS).mapToObj((it) -> getString(R.string.format_minutes, it / 60)).toArray(String[]::new));
        mPrefAutoFreezeDelay.setEntryValues(Arrays.stream(AUTO_FREEZE_DELAY_SECONDS).mapToObj(String::valueOf).toArray(String[]::new));
        updateAutoFreezeDelay();
        mPrefSkipForeground = (CheckBoxPreference) findPreference(SETTINGS_SKIP_FOREGROUND);
        mPrefSkipForeground.setChecked(mManager.getSkipForegroundEnabled());
        mPrefSkipForeground.setOnPreferenceChangeListener(this);

        // Disable FileSuttle on Q for now
        // Supported on R and beyond
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            mPrefCrossProfileFileChooser.setEnabled(false);
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
        mPrefAutoFreezeDelay.setSummary(getString(R.string.format_minutes, mManager.getAutoFreezeDelay() / 60));
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
        } else if (preference == mPrefBlockContactsSearching) {
            mManager.setBlockContactsSearchingEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefAutoFreezeService) {
            mManager.setAutoFreezeServiceEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefAutoFreezeDelay) {
            mManager.setAutoFreezeDelay(Integer.parseInt((String) newState));
            updateAutoFreezeDelay();
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
        } else if (preference == mPrefPaymentStub) {
            mManager.setPaymentStubEnabled((boolean) newState);
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

    private void showDestroyProfileDialog() {
        if (getContext() == null) return;

        if (mServiceWork == null) {
            // Cannot contact work profile service
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_destroy_profile_failed_title)
                    .setMessage(R.string.settings_destroy_profile_failed_message)
                    .setPositiveButton(R.string.settings_destroy_profile_reset_state, (dialog, which) -> {
                        resetShelterAndSetupAgain();
                    })
                    .setNeutralButton(R.string.settings_open_system_settings, (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_destroy_profile_dialog_title)
                .setMessage(R.string.settings_destroy_profile_dialog_message)
                .setPositiveButton(R.string.settings_destroy_profile_confirm, (dialog, which) -> {
                    performDestroyProfile();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performDestroyProfile() {
        if (getContext() == null || getActivity() == null) return;

        AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_destroy_profile_title)
                .setMessage(R.string.settings_destroying_profile_progress)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            if (mServiceWork != null) {
                try {
                    mServiceWork.destroyProfile();
                } catch (DeadObjectException e) {
                    // Profile owner process was killed upon profile wipe; expected behavior
                    Log.d("SettingsFragment", "Remote service terminated on profile wipe");
                } catch (Exception e) {
                    Log.e("SettingsFragment", "Error calling destroyProfile", e);
                }
            }

            // Clean up local preferences
            LocalStorageManager storage = LocalStorageManager.getInstance();
            storage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, false);
            storage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, false);
            storage.remove(LocalStorageManager.PREF_AUTH_KEY);
            storage.remove(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE);

            // Unbind application services
            try {
                if (getActivity() != null && getActivity().getApplication() instanceof ShelterApplication) {
                    ((ShelterApplication) getActivity().getApplication()).unbindShelterService();
                    ((ShelterApplication) getActivity().getApplication()).unbindFileShuttleService();
                }
            } catch (Exception ignored) {}

            try {
                Thread.sleep(1200); // Give the system time to finalize user profile removal
            } catch (InterruptedException ignored) {}

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    } catch (Exception ignored) {}

                    showSetupAgainDialog();
                });
            }
        }).start();
    }

    private void showSetupAgainDialog() {
        if (getContext() == null || getActivity() == null || getActivity().isFinishing()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_destroy_profile_success_title)
                .setMessage(R.string.settings_destroy_profile_success_message)
                .setCancelable(false)
                .setPositiveButton(R.string.settings_destroy_profile_setup_again, (dialog, which) -> {
                    resetShelterAndSetupAgain();
                })
                .setNegativeButton(R.string.settings_destroy_profile_close, (dialog, which) -> {
                    getActivity().finishAffinity();
                })
                .show();
    }

    private void resetShelterAndSetupAgain() {
        LocalStorageManager storage = LocalStorageManager.getInstance();
        storage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, false);
        storage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, false);
        storage.remove(LocalStorageManager.PREF_AUTH_KEY);
        storage.remove(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE);

        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }
}
