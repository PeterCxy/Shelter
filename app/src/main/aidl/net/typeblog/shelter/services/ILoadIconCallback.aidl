// ILoadIconCallback.aidl
package net.typeblog.shelter.services;

import android.graphics.Bitmap;

interface ILoadIconCallback {
    void callback(in Bitmap icon);
}
