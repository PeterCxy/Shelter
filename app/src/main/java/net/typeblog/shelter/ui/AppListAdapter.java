package net.typeblog.shelter.ui;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.typeblog.shelter.R;
import net.typeblog.shelter.services.IGetAppsCallback;
import net.typeblog.shelter.services.ILoadIconCallback;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.util.ApplicationInfoWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    class ViewHolder extends RecyclerView.ViewHolder {
        private ViewGroup mView;
        private ImageView mIcon;
        private TextView mTitle;
        private TextView mPackage;
        int mIndex = -1;
        ViewHolder(ViewGroup view) {
            super(view);
            mView = view;
            mIcon = view.findViewById(R.id.list_app_icon);
            mTitle = view.findViewById(R.id.list_app_title);
            mPackage = view.findViewById(R.id.list_app_package);
            view.setOnClickListener((v) -> onClick());
        }

        void onClick() {
            if (mIndex == -1) return;

            // Show available operations via the Fragment
            // pass the full info to it, since we can't be sure
            // the index won't change
            if (mContextMenuHandler != null) {
                mContextMenuHandler.showContextMenu(mList.get(mIndex), mView);
            }
        }

        void setIndex(final int index) {
            mIndex = index;

            if (mIndex >= 0) {
                ApplicationInfoWrapper info = mList.get(mIndex);
                mPackage.setText(info.mInfo.packageName);

                if (!info.mInfo.enabled) {
                    String label = String.format(mLabelDisabled, info.mLabel);
                    mTitle.setText(label);
                    mView.setBackgroundResource(R.color.disabledAppBackground);
                } else {
                    mTitle.setText(info.mLabel);
                    mView.setBackground(null);
                }

                // Load the application icon from cache
                // or populate the cache through the service
                if (mIconCache.containsKey(info.mInfo.packageName)) {
                    mIcon.setImageBitmap(mIconCache.get(info.mInfo.packageName));
                } else {
                    mIcon.setImageDrawable(mDefaultIcon);

                    try {
                        mService.loadIcon(info.mInfo, new ILoadIconCallback.Stub() {
                            @Override
                            public void callback(Bitmap icon) {
                                if (index == mIndex) {
                                    mHandler.post(() -> mIcon.setImageBitmap(icon));
                                }

                                synchronized (AppListAdapter.class) {
                                    mIconCache.put(info.mInfo.packageName, icon);
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

    private List<ApplicationInfoWrapper> mList = new ArrayList<>();
    private IShelterService mService;
    private Drawable mDefaultIcon;
    private String mLabelDisabled;
    private boolean mRefreshing = false;
    private Map<String, Bitmap> mIconCache = new HashMap<>();
    private ContextMenuHandler mContextMenuHandler = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private SwipeRefreshLayout mSwipeRefresh;

    AppListAdapter(IShelterService service, Drawable defaultIcon, SwipeRefreshLayout swipeRefresh) {
        mService = service;
        mDefaultIcon = defaultIcon;
        mSwipeRefresh = swipeRefresh;
    }

    void setContextMenuHandler(ContextMenuHandler handler) {
        mContextMenuHandler = handler;
    }

    void refresh() {
        if (mRefreshing) return;
        mRefreshing = true;
        mSwipeRefresh.setRefreshing(true);

        try {
            mService.getApps(new IGetAppsCallback.Stub() {
                @Override
                public void callback(List<ApplicationInfoWrapper> apps) {
                    mList.clear();
                    mIconCache.clear();
                    mList.addAll(apps);
                    mHandler.post(() -> {
                        mSwipeRefresh.setRefreshing(false);
                        notifyDataSetChanged();
                        mRefreshing = false;
                    });
                }
            });
        } catch (RemoteException e) {
            // Just... do nothing for now
        }
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
