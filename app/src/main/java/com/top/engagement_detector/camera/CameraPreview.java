package com.top.engagement_detector.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceView;

/** SurfaceView handles sensor/display rotation and front-camera mirroring; we preserve aspect. */
public final class CameraPreview extends SurfaceView {
    private int imageWidth;
    private int imageHeight;
    private int rotationDegrees;
    private boolean mirrored;

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void configure(CameraConfiguration configuration) {
        mirrored = configuration.frontFacing;
        int rotation = getDisplay() == null ? Surface.ROTATION_0 : getDisplay().getRotation();
        int displayDegrees = rotation * 90;
        int relativeRotation = (configuration.sensorOrientation
                + (configuration.frontFacing ? displayDegrees : -displayDegrees) + 360) % 360;
        rotationDegrees = relativeRotation;
        boolean swap = relativeRotation == 90 || relativeRotation == 270;
        imageWidth = swap ? configuration.previewSize.getHeight()
                : configuration.previewSize.getWidth();
        imageHeight = swap ? configuration.previewSize.getWidth()
                : configuration.previewSize.getHeight();
        getHolder().setFixedSize(configuration.previewSize.getWidth(),
                configuration.previewSize.getHeight());
        requestLayout();
    }

    int getAnalysisRotationDegrees() {
        return rotationDegrees;
    }

    /** Camera2's default SurfaceView preview mirrors cameras reported as front-facing. */
    public boolean isPreviewMirrored() {
        return mirrored;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (imageWidth == 0 || imageHeight == 0) {
            return;
        }
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        // Fit the whole image, with black bars when needed, rather than stretching or cropping it.
        if ((long) width * imageHeight > (long) height * imageWidth) {
            width = height * imageWidth / imageHeight;
        } else {
            height = width * imageHeight / imageWidth;
        }
        setMeasuredDimension(width, height);
    }
}
