package com.top.engagement_detector.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import java.util.function.Supplier;

/** Borrowed analysis bitmap and camera luminance. Valid only during synchronous analyze(). */
public final class CameraFrame {
    public final Bitmap bitmap;
    public final long timestampMs;
    public final CameraStream stream;
    public final int captureWidth, captureHeight;
    private final YuvLumaSampler luminance;
    private final Supplier<Bitmap> fullResolution;

    CameraFrame(Bitmap bitmap, long timestampMs, CameraStream stream,
                YuvLumaSampler luminance, Supplier<Bitmap> fullResolution) {
        this.bitmap = bitmap;
        this.timestampMs = timestampMs;
        this.stream = stream;
        this.luminance = luminance;
        captureWidth = luminance.width;
        captureHeight = luminance.height;
        this.fullResolution = fullResolution;
    }

    public double sampleCaptureLuma(double x, double y) {
        return luminance.sample(x, y);
    }

    /** Expensive conversion is deferred until a usable candidate exists. Caller owns the copy. */
    public Bitmap copyUprightCapture() {
        Bitmap sensor = fullResolution.get();
        if (stream.rotationDegrees == 0) return sensor.copy(Bitmap.Config.ARGB_8888, false);
        Matrix rotation = new Matrix();
        rotation.postRotate(stream.rotationDegrees);
        return Bitmap.createBitmap(sensor, 0, 0, sensor.getWidth(), sensor.getHeight(), rotation, true);
    }
}
