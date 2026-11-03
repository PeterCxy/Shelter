package net.typeblog.shelter.util;

import android.graphics.BitmapFactory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UtilityTest {

    @Test
    public void testGetFileExtension() {
        assertEquals("apk", Utility.getFileExtension("my_app.apk"));
        assertEquals("gz", Utility.getFileExtension("archive.tar.gz"));
        assertEquals("png", Utility.getFileExtension("/sdcard/image.png"));
        assertNull(Utility.getFileExtension("no_extension"));
    }

    @Test
    public void testCalculateInSampleSize() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.outWidth = 1024;
        options.outHeight = 1024;

        int sampleSize = Utility.calculateInSampleSize(options, 256, 256);
        assertEquals(4, sampleSize);

        options.outWidth = 200;
        options.outHeight = 200;
        int sampleSizeSame = Utility.calculateInSampleSize(options, 200, 200);
        assertEquals(1, sampleSizeSame);
    }
}
