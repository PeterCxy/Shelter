package net.typeblog.shelter.util;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
            return info;
        }
    };

    public ApplicationInfo mInfo = null;
    public String mLabel = null;

    private ApplicationInfoWrapper() {}

    public ApplicationInfoWrapper(ApplicationInfo info) {
        mInfo = info;
    }

    public ApplicationInfoWrapper loadLabel(PackageManager pm) {
        mLabel = pm.getApplicationLabel(mInfo).toString();
        return this;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mInfo, flags);
        dest.writeString(mLabel);
    }

    @Override
    public int describeContents() {
        return mInfo.packageName.hashCode();
    }
}
