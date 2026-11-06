package com.top.engagement_detector.ui;

/** Fits upright normalized image coordinates into a preview rectangle, including its letterbox. */
public final class MeshProjection {
    private final float left;
    private final float top;
    private final float width;
    private final float height;
    private final boolean mirrored;

    public MeshProjection(int imageWidth, int imageHeight, float previewLeft, float previewTop,
                          float previewWidth, float previewHeight, boolean mirrored) {
        if (imageWidth <= 0 || imageHeight <= 0 || previewWidth < 0 || previewHeight < 0) {
            throw new IllegalArgumentException("Invalid image or preview dimensions");
        }
        float scale = Math.min(previewWidth / imageWidth, previewHeight / imageHeight);
        width = imageWidth * scale;
        height = imageHeight * scale;
        left = previewLeft + (previewWidth - width) / 2f;
        top = previewTop + (previewHeight - height) / 2f;
        this.mirrored = mirrored;
    }

    public float x(float normalizedX) {
        return left + (mirrored ? 1f - normalizedX : normalizedX) * width;
    }

    public float y(float normalizedY) {
        return top + normalizedY * height;
    }
}
