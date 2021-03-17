package net.typeblog.shelter.ui;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.setupwizardlib.SetupWizardLayout;
import com.android.setupwizardlib.view.NavigationBar;

import net.typeblog.shelter.R;

public class SetupWizardActivity extends AppCompatActivity {
    private DevicePolicyManager mPolicyManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        // Don't use switchToFragment for the first time
        // because we don't want animation for the first fragment
        // (it would have nothing to animate upon, resulting in a black background)
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.setup_wizard_container, new WelcomeFragment())
                .commit();
    }

    private<T extends BaseWizardFragment> void switchToFragment(T fragment, boolean reverseAnimation) {
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
        // Placeholder
        switchToFragment(new FailedFragment(), false);
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

    // ==== SetupWizard steps ====
    private static abstract class BaseWizardFragment extends Fragment implements NavigationBar.NavigationBarListener {
        protected SetupWizardActivity mActivity = null;
        protected SetupWizardLayout mWizard = null;

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
            mWizard = view.findViewById(R.id.wizard);
            mWizard.getNavigationBar().setNavigationBarListener(this);
            mWizard.setLayoutBackground(ContextCompat.getDrawable(inflater.getContext(), R.color.colorAccent));
            return view;
        }
    }

    protected static abstract class TextWizardFragment extends BaseWizardFragment {
        protected abstract int getTextRes();

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            TextView tv = view.findViewById(R.id.setup_wizard_generic_text);
            tv.setText(getTextRes());
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
            mWizard.setHeaderText(R.string.setup_wizard_welcome);
            mWizard.getNavigationBar().getBackButton().setVisibility(View.GONE);
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
            mWizard.setHeaderText(R.string.setup_wizard_permissions);
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
            mWizard.setHeaderText(R.string.setup_wizard_compatibility);
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
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            mWizard.setHeaderText(R.string.setup_wizard_ready);
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
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            mActivity.setupProfile();
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            mWizard.setHeaderText(R.string.setup_wizard_please_wait);
            mWizard.setProgressBarColor(view.getContext().getColorStateList(R.color.setup_wizard_progress_bar));
            mWizard.setProgressBarShown(true);
            mWizard.getNavigationBar().getBackButton().setVisibility(View.GONE);
            mWizard.getNavigationBar().getNextButton().setVisibility(View.GONE);
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
            mWizard.setHeaderText(R.string.setup_wizard_failed);
            mWizard.getNavigationBar().getBackButton().setVisibility(View.GONE);
        }
    }
}