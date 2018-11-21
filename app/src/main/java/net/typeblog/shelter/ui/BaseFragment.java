package net.typeblog.shelter.ui;

import androidx.fragment.app.Fragment;

// Some utility extensions to the Fragment class...
public abstract class BaseFragment extends Fragment {
    // Convenience method to call getActivity().runOnUiThread()
    // without bothering about NPEs
    protected void runOnUiThread(Runnable task) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(task);
    }
}
