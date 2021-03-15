package net.typeblog.shelter.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.setupwizardlib.SetupWizardLayout;
import com.android.setupwizardlib.view.NavigationBar;

import net.typeblog.shelter.R;

public class SetupWizardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);
        switchToFragment(new WelcomeFragment());
    }

    private<T extends BaseWizardFragment> void switchToFragment(T fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.setup_wizard_container, fragment)
                .commit();
    }

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
            mActivity.switchToFragment(new PermissionsFragment());
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
            mActivity.switchToFragment(new WelcomeFragment());
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            mWizard.setHeaderText(R.string.setup_wizard_permissions);
        }
    }
}