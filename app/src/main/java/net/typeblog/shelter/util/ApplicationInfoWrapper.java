package net.typeblog.shelter.util;

import android.annotation.TargetApi;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

public class ApplicationInfoWrapper implements Parcelable {
    public static final Parcelable.Creator<ApplicationInfoWrapper> CREATOR = new Parcelable.Creator<ApplicationInfoWrapper>() {
        @Override
        public ApplicationInfoWrapper[] newArray(int size) {
            return new ApplicationInfoWrapper[size];
        }

        @Override
        public ApplicationInfoWrapper createFromParcel(Parcel source) {
            ApplicationInfoWrapper info = new ApplicationInfoWrapper();
            info.mInfo = source.readParcelable(ApplicationInfo.class.getClassLoader());
            info.mLabel = source.readString();
            info.mIsHidden = source.readByte() != 0;
            return info;
        }
    };

    private ApplicationInfo mInfo = null;
    private String mLabel = null;
    private boolean mIsHidden = false;

    private ApplicationInfoWrapper() {}

    public ApplicationInfoWrapper(ApplicationInfo info) {
        mInfo = info;
    }

    public ApplicationInfoWrapper loadLabel(PackageManager pm) {
        mLabel = pm.getApplicationLabel(mInfo).toString();
        return this;
    }

    // Only used from ShelterService
    public ApplicationInfoWrapper setHidden(boolean hidden) {
        mIsHidden = hidden;
        return this;
    }

    public String getPackageName() {
        return mInfo.packageName;
    }

    public String getLabel() {
        return mLabel;
    }

    public String getSourceDir() {
        return mInfo.sourceDir;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public String[] getSplitApks() {
        return mInfo.splitSourceDirs;
    }

    // NOTE: This does not relate to the "freezing" feature in Shelter
    public boolean getEnabled() {
        return mInfo.enabled;
    }

    public boolean isHidden() {
        return mIsHidden;
    }

    public ApplicationInfo getInfo() {
        return mInfo;
    }

    public boolean isSystem() {
        return (mInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mInfo, flags);
        dest.writeString(mLabel);
        dest.writeByte((byte) (mIsHidden ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return mInfo.packageName.hashCode();
    }
}
