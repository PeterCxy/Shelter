package net.typeblog.shelter.ui;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.typeblog.shelter.R;
import net.typeblog.shelter.services.ILoadIconCallback;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.util.ApplicationInfoWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    class ViewHolder extends RecyclerView.ViewHolder {
        private ViewGroup mView;
        private ImageView mIcon;
        private TextView mTitle;
        private TextView mPackage;
        // This text view shows the order of all selected items
        private TextView mSelectOrder;
        int mIndex = -1;
        ViewHolder(ViewGroup view) {
            super(view);
            mView = view;
            mIcon = view.findViewById(R.id.list_app_icon);
            mTitle = view.findViewById(R.id.list_app_title);
            mPackage = view.findViewById(R.id.list_app_package);
            mSelectOrder = view.findViewById(R.id.list_app_select_order);
            view.setOnClickListener((v) -> onClick());
            if (mAllowMultiSelect) {
                view.setOnLongClickListener((v) -> onLongClick());
            }
        }

        void onClick() {
            if (mIndex == -1) return;

            if (!mMultiSelectMode) {
                // Show available operations via the Fragment
                // pass the full info to it, since we can't be sure
                // the index won't change
                if (mContextMenuHandler != null) {
                    mContextMenuHandler.showContextMenu(mList.get(mIndex), mView);
                }
            } else {
                // In multi-select mode, single clicks just adds to the selection
                // or cancels the selection if already selected
                if (!mSelectedIndices.contains(mIndex)) {
                    select();
                } else {
                    deselect();
                }
            }
        }

        boolean onLongClick() {
            if (mIndex == -1) return false;

            // If we have an action mode handler, we notify it to enter
            // action mode on long click, and register this adapter
            // to be in multi-select mode
            if (!mMultiSelectMode && mActionModeHandler != null && mActionModeHandler.createActionMode()) {
                mMultiSelectMode = true;
                select();
                return true;
            } else {
                return false;
            }
        }

        // When the user selects the item
        // we need to play the animation of the "select order" appearing
        // on the right side of the item view
        void select() {
            mSelectedIndices.add(mIndex);
            mSelectOrder.clearAnimation();
            mSelectOrder.startAnimation(AnimationUtils.loadAnimation(mView.getContext(), R.anim.scale_appear));
            showSelectOrder();
        }

        // When the user deselects the item
        void deselect() {
            mSelectedIndices.remove((Integer) mIndex);
            mSelectOrder.clearAnimation();
            mView.setBackgroundResource(android.R.color.transparent);
            Animation anim = AnimationUtils.loadAnimation(mView.getContext(), R.anim.scale_hide);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (mIndex != -1) {
                        // The selection index of items other than this one
                        // can be changed because of the removal of the current one
                        // Thus, we just notify that the data set has been changed,
                        // to force redraw all of them.
                        notifyDataSetChanged();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            mSelectOrder.startAnimation(anim);
        }

        // When an item should be displayed in selected state
        // (not necessarily when the user clicked on it; the view might have been recycled)
        void showSelectOrder() {
            if (!mList.get(mIndex).isHidden()) {
                mView.setBackgroundResource(R.color.selectedAppBackground);
            } else {
                // The app is both frozen and selected
                // we use a blended color of the two for its background
                mView.setBackgroundResource(R.color.selectedAndDisabledAppBackground);
            }
            mSelectOrder.setVisibility(View.VISIBLE);
            mSelectOrder.setText(String.valueOf(mSelectedIndices.indexOf(mIndex) + 1));
        }

        // When an item should be displayed in deselected state
        void hideSelectOrder() {
            // First, determine the hidden (frozen) state
            if (!mList.get(mIndex).isHidden()) {
                mView.setBackground(null);
            } else {
                mView.setBackgroundResource(R.color.disabledAppBackground);
            }
            mSelectOrder.setVisibility(View.GONE);
        }

        void setIndex(final int index) {
            mIndex = index;

            if (mIndex >= 0) {
                // Clear all animations first
                mSelectOrder.clearAnimation();

                ApplicationInfoWrapper info = mList.get(mIndex);
                mPackage.setText(info.getPackageName());

                if (info.isHidden()) {
                    String label = String.format(mLabelDisabled, info.getLabel());
                    mTitle.setText(label);
                } else {
                    mTitle.setText(info.getLabel());
                }

                // Special logic when in multi-select mode and this item is selected
                if (mMultiSelectMode && mSelectedIndices.contains(mIndex)) {
                    showSelectOrder();
                } else {
                    hideSelectOrder();
                }

                // Load the application icon from cache
                // or populate the cache through the service
                if (mIconCache.containsKey(info.getPackageName())) {
                    mIcon.setImageBitmap(mIconCache.get(info.getPackageName()));
                } else {
                    mIcon.setImageDrawable(mDefaultIcon);

                    try {
                        mService.loadIcon(info, new ILoadIconCallback.Stub() {
                            @Override
                            public void callback(Bitmap icon) {
                                if (index == mIndex) {
                                    mHandler.post(() -> mIcon.setImageBitmap(icon));
                                }

                                synchronized (AppListAdapter.class) {
                                    mIconCache.put(info.getPackageName(), icon);
                                }
                            }
                        });
                    } catch (RemoteException e) {
                        // Do Nothing
                    }
                }
            }
        }
    }

    interface ContextMenuHandler {
        void showContextMenu(ApplicationInfoWrapper info, View view);
    }

    interface ActionModeHandler {
        boolean createActionMode();
    }

    private List<ApplicationInfoWrapper> mList = new ArrayList<>();
    private IShelterService mService;
    private Drawable mDefaultIcon;
    private String mLabelDisabled;
    private Map<String, Bitmap> mIconCache = new HashMap<>();
    private ContextMenuHandler mContextMenuHandler = null;
    private ActionModeHandler mActionModeHandler = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    // Multi-selection mode
    private boolean mAllowMultiSelect = false;
    private boolean mMultiSelectMode = false;
    private List<Integer> mSelectedIndices = new ArrayList<>();

    AppListAdapter(IShelterService service, Drawable defaultIcon) {
        mService = service;
        mDefaultIcon = defaultIcon;
    }

    void setContextMenuHandler(ContextMenuHandler handler) {
        mContextMenuHandler = handler;
    }

    // When we enter multi-select mode, we have to notify our parent fragment
    // to enter action mode, in order to show the specific menus
    void setActionModeHandler(ActionModeHandler handler) {
        mActionModeHandler = handler;
    }

    void allowMultiSelect() {
        mAllowMultiSelect = true;
    }

    boolean isMultiSelectMode() {
        return mMultiSelectMode;
    }

    void cancelMultiSelectMode() {
        mMultiSelectMode = false;
        mSelectedIndices.clear();
        notifyDataSetChanged();
    }

    List<ApplicationInfoWrapper> getSelectedItems() {
        if (!mMultiSelectMode) return null;
        if (mSelectedIndices.size() == 0) return null;

        return mSelectedIndices.stream()
                .map((idx) -> mList.get(idx))
                .collect(Collectors.toList());
    }

    void setData(List<ApplicationInfoWrapper> apps) {
        mList.clear();
        mIconCache.clear();
        mList.addAll(apps);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        if (mLabelDisabled == null) {
            mLabelDisabled = viewGroup.getContext().getString(R.string.list_item_disabled);
        }
        LayoutInflater inflater = viewGroup.getContext().getSystemService(LayoutInflater.class);
        ViewGroup view = (ViewGroup) inflater.inflate(R.layout.app_list_item, viewGroup, false);
        ViewHolder vh = new ViewHolder(view);
        vh.setIndex(i);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.setIndex(i);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.setIndex(-1);
    }
}
