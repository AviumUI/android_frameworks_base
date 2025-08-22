package android.view;

import android.graphics.Bitmap;

/**
 * {@hide}
 */
oneway interface IViewCaptureCallback {
    void onViewsCaptured(in List<Bitmap> bitmaps);
}