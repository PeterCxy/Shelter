package net.typeblog.shelter.ui;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import net.typeblog.shelter.R;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.util.AuthenticationUtility;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.Utility;

public class SetupWizardActivity extends AppCompatActivity {
    public static final String ACTION_RESUME_SETUP = "net.typeblog.shelter.RESUME_SETUP";
    public static final String ACTION_PROFILE_PROVISIONED = "net.typeblog.shelter.PROFILE_PROVISIONED";

    private DevicePolicyManager mPolicyManager = null;
    private LocalStorageManager mStorage = null;

    private final ActivityResultLauncher<Void> mProvisionProfile =
            registerForActivityResult(new ProfileProvisionContract(), this::setupProfileCb);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        // The user could click on the "finish provisioning" notification while having removed
        // this activity from the recents stack, in which case the notification will start a new
        // instance of activity
        if (ACTION_PROFILE_PROVISIONED.equals(getIntent().getAction()) && Utility.isWorkProfileAvailable(this)) {
            // ...in which case we should finish immediately and go back to MainActivity
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_setup_wizard);
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mStorage = LocalStorageManager.getInstance();
        // Don't use switchToFragment for the first time
        // because we don't want animation for the first fragment
        // (it would have nothing to animate upon, resulting in a black background)
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.setup_wizard_container,
                        ACTION_RESUME_SETUP.equals(getIntent().getAction()) ?
                                new ActionRequiredFragment() : new WelcomeFragment())
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // DummyActivity will start this activity with an empty intent
        // once the provision is finalized
        if (ACTION_PROFILE_PROVISIONED.equals(intent.getAction()) && Utility.isWorkProfileAvailable(this))
            finishWithResult(true);
    }

    private <T extends BaseWizardFragment> void switchToFragment(T fragment, boolean reverseAnimation) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        reverseAnimation ? R.anim.slide_in_from_left : R.anim.slide_in_from_right,
                        reverseAnimation ? R.anim.slide_out_to_right : R.anim.slide_out_to_left
                )
                .replace(R.id.setup_wizard_container, fragment)
                .commit();
    }

    private void finishWithResult(boolean succeeded) {
        setResult(succeeded ? RESULT_OK : RESULT_CANCELED);
        finish();
    }

    private void setupProfile() {
        if (!mPolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            switchToFragment(new FailedFragment(), false);
            return;
        }

        // The user may have aborted provisioning before without clearing data
        // This can cause issues if the authentication utility thinks we
        // could do authentication due to the presence of keys
        AuthenticationUtility.reset();

        try {
            mProvisionProfile.launch(null);
        } catch (ActivityNotFoundException e) {
            // How could this fail???
            switchToFragment(new FailedFragment(), false);
        }
    }

    private void setupProfileCb(Boolean result) {
        if (result) {
            if (Utility.isWorkProfileAvailable(this)) {
                // On Oreo and later versions, since we make use of the activity intent
                // ACTION_PROVISIONING_SUCCESSFUL, the provisioning UI will not finish
                // until that activity returns. In this case, there is really no need for us
                // to do anything else here (and this callback may not even be called because
                // the activity will likely be already finished by this point).
                // There is no need for more action
                finishWithResult(true);
                return;
            }

            // Provisioning finished, but we still need to tell the user
            // to click on the notification to bring up Shelter inside the
            // profile. Otherwise, the setup will not be complete
            mStorage.setBoolean(LocalStorageManager.PREF_IS_SETTING_UP, true);
            switchToFragment(new ActionRequiredFragment(), false);
        } else {
            switchToFragment(new FailedFragment(), false);
        }
    }

    public static class SetupWizardContract extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            return new Intent(context, SetupWizardActivity.class);
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }

    public static class ResumeSetupContract extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            Intent intent = new Intent(context, SetupWizardActivity.class);
            intent.setAction(ACTION_RESUME_SETUP);
            return intent;
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }

    private static class ProfileProvisionContract extends ActivityResultContract<Void, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            ComponentName admin = new ComponentName(context.getApplicationContext(), ShelterDeviceAdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin);
            return intent;
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }

    // ==== SetupWizard steps ====
    public interface WizardNavigationListener {
        void onNavigateBack();
        void onNavigateNext();
    }

    private static abstract class BaseWizardFragment extends Fragment implements WizardNavigationListener {
        protected SetupWizardActivity mActivity = null;
        protected TextView mTitleView = null;
        protected LinearProgressIndicator mProgressBar = null;
        protected Button mBackButton = null;
        protected Button mNextButton = null;

        protected abstract int getLayoutResource();

        @Override
        public void onNavigateBack() {
            // For sub-classes to implement
        }

        @Override
        public void onNavigateNext() {
            // For sub-classes to implement
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            mActivity = (SetupWizardActivity) getActivity();
        }

        @Override
        public void onDetach() {
            super.onDetach();
            mActivity = null;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(getLayoutResource(), container, false);
            mTitleView = view.findViewById(R.id.wizard_title);
            mProgressBar = view.findViewById(R.id.wizard_progress);
            mBackButton = view.findViewById(R.id.wizard_btn_back);
            mNextButton = view.findViewById(R.id.wizard_btn_next);

            if (mBackButton != null) {
                mBackButton.setOnClickListener(v -> onNavigateBack());
            }
            if (mNextButton != null) {
                mNextButton.setOnClickListener(v -> onNavigateNext());
            }
            return view;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                View header = v.findViewById(R.id.wizard_header);
                View nav = v.findViewById(R.id.wizard_navigation_bar);
                if (header != null) {
                    header.setPadding(header.getPaddingLeft(), insets.top + 16, header.getPaddingRight(), header.getPaddingBottom());
                }
                if (nav != null) {
                    nav.setPadding(nav.getPaddingLeft(), nav.getPaddingTop(), nav.getPaddingRight(), insets.bottom + 12);
                }
                return WindowInsetsCompat.CONSUMED;
            });
        }

        protected void setHeaderText(int textRes) {
            if (mTitleView != null) {
                mTitleView.setText(textRes);
            }
        }

        protected void setProgressBarShown(boolean shown) {
            if (mProgressBar != null) {
                mProgressBar.setVisibility(shown ? View.VISIBLE : View.GONE);
            }
        }

        protected void setNextButtonText(int textRes) {
            if (mNextButton != null) {
                mNextButton.setText(textRes);
            }
        }

        protected void setBackButtonText(int textRes) {
            if (mBackButton != null) {
                mBackButton.setText(textRes);
            }
        }
    }

    protected static abstract class TextWizardFragment extends BaseWizardFragment {
        protected abstract int getTextRes();

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            TextView tv = view.findViewById(R.id.setup_wizard_generic_text);
            if (tv != null) {
                tv.setText(getTextRes());
            }
        }
    }

    public static class WelcomeFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_welcome_text;
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new PermissionsFragment(), false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setHeaderText(R.string.setup_wizard_welcome);
            setNextButtonText(R.string.wizard_next);
            if (mBackButton != null) {
                mBackButton.setVisibility(View.GONE);
            }
        }
    }

    public static class PermissionsFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_permissions_text;
        }

        @Override
        public void onNavigateBack() {
            super.onNavigateBack();
            mActivity.switchToFragment(new WelcomeFragment(), true);
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new CompatibilityFragment(), false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setHeaderText(R.string.setup_wizard_permissions);
            setNextButtonText(R.string.wizard_next);
            setBackButtonText(R.string.wizard_back);
            if (mBackButton != null) mBackButton.setVisibility(View.VISIBLE);
        }
    }

    public static class CompatibilityFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_compatibility_text;
        }

        @Override
        public void onNavigateBack() {
            super.onNavigateBack();
            mActivity.switchToFragment(new PermissionsFragment(), true);
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new ReadyFragment(), false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setHeaderText(R.string.setup_wizard_compatibility);
            setNextButtonText(R.string.wizard_next);
            setBackButtonText(R.string.wizard_back);
            if (mBackButton != null) mBackButton.setVisibility(View.VISIBLE);
        }
    }

    public static class ReadyFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_ready_text;
        }

        @Override
        public void onNavigateBack() {
            super.onNavigateBack();
            mActivity.switchToFragment(new CompatibilityFragment(), true);
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.switchToFragment(new PleaseWaitFragment(), false);
            mActivity.setupProfile();
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setHeaderText(R.string.setup_wizard_ready);
            setNextButtonText(R.string.wizard_next);
            setBackButtonText(R.string.wizard_back);
            if (mBackButton != null) mBackButton.setVisibility(View.VISIBLE);
        }
    }

    public static class PleaseWaitFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_please_wait_text;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setHeaderText(R.string.setup_wizard_please_wait);
            setProgressBarShown(true);
            if (mBackButton != null) mBackButton.setVisibility(View.GONE);
            if (mNextButton != null) mNextButton.setVisibility(View.GONE);
        }
    }

    public static class ActionRequiredFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_action_required_text;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setHeaderText(R.string.setup_wizard_action_required);
            setProgressBarShown(true);
            if (mBackButton != null) mBackButton.setVisibility(View.GONE);
            if (mNextButton != null) mNextButton.setVisibility(View.GONE);
        }
    }

    public static class FailedFragment extends TextWizardFragment {
        @Override
        protected int getLayoutResource() {
            return R.layout.fragment_setup_wizard_generic_text;
        }

        @Override
        protected int getTextRes() {
            return R.string.setup_wizard_failed_text;
        }

        @Override
        public void onNavigateNext() {
            super.onNavigateNext();
            mActivity.finishWithResult(false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setHeaderText(R.string.setup_wizard_failed);
            setNextButtonText(R.string.wizard_next);
            if (mBackButton != null) mBackButton.setVisibility(View.GONE);
        }
    }
}
