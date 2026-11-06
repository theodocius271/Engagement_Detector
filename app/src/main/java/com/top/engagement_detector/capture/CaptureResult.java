package com.top.engagement_detector.capture;

import android.graphics.Bitmap;

import java.io.File;

/** Owns its bitmap. A pending file is committed only after the UI accepts this stream's result. */
public final class CaptureResult {
    public final Bitmap image;
    public final int visitorCount;
    public final long frameTimestampMs;
    final File file;

    CaptureResult(Bitmap image, int visitorCount, long frameTimestampMs, File file) {
        this.image = image;
        this.visitorCount = visitorCount;
        this.frameTimestampMs = frameTimestampMs;
        this.file = file;
    }

    public void releaseImage() {
        if (!image.isRecycled()) {
            image.recycle();
        }
    }

    public void discardPending() {
        file.delete();
        releaseImage();
    }
}
