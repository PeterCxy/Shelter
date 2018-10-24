package net.typeblog.shelter.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import net.typeblog.shelter.util.Utility;

import java.io.IOException;
import java.io.OutputStream;

// This activity forwards ACTION_CAPTURE_IMAGE to ACTION_OPEN_DOCUMENT with image files
// which allows users to use Documents UI, and of course, Shelter's File Shuttle
// to pick images within apps that do not support ACTION_OPEN_DOCUMENT directly
// This will make cross-profile picture sharing a ton more easier.
// But here is the catch: sending images through this method will re-compress
// the image because Android's ACTION_CAPTURE_IMAGE requires returning a JPEG image
// this will in turn affect the quality of the image.
public class CameraProxyActivity extends Activity {
    private static final int REQUEST_OPEN_IMAGE = 1001;
    private Uri mOutputUri = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MediaStore.ACTION_IMAGE_CAPTURE.equals(getIntent().getAction())) {
            finish();
            return;
        }

        if (getIntent().hasExtra(MediaStore.EXTRA_OUTPUT)) {
            // The calling app may or may not request for the full output image
            mOutputUri = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);
        }

        // Launch Documents UI for picking images
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*"); // Only allow images
        startActivityForResult(intent, REQUEST_OPEN_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_OPEN_IMAGE && resultCode == RESULT_OK && data != null) {
            // The image is now opened. We should now read the data and send it to the other Uri
            Uri imageUri = data.getData();
            try {
                ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(imageUri, "r");
                // Thumbnail is required by the definition of ACTION_CAPTURE_IMAGE
                Bitmap thumbnail = Utility.decodeSampledBitmap(fd.getFileDescriptor(), 128, 128);

                if (mOutputUri != null) {
                    // The calling app may or may not request for the full image
                    // If requested, we write the image, in JPEG format, to the provided URI
                    // Note that JPEG is required by the interface.
                    Bitmap bmp = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
                    OutputStream out = getContentResolver().openOutputStream(mOutputUri);

                    // Re-compress the image to another JPEG image through the output URI
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    out.flush();
                    out.close();
                }

                fd.close();
                Intent resultIntent = new Intent();
                resultIntent.putExtra("data", thumbnail);
                setResult(RESULT_OK, resultIntent);
                finish();
                return;
            } catch (IOException e) {
                // Just silently fail
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        // We exit the activity anyway
        setResult(RESULT_CANCELED); // If we succeeded, we should have returned earlier
        finish();
    }
}
