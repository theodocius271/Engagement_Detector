package com.top.engagement_detector.vision;

/** Upright normalized bounds. No Android geometry dependency. */
public final class FaceBox {
    public final float left, top, right, bottom;

    public FaceBox(float left, float top, float right, float bottom) {
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right)
                || !Float.isFinite(bottom) || right <= left || bottom <= top) {
            throw new IllegalArgumentException("Invalid face bounds");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float width() { return right - left; }
    public float height() { return bottom - top; }
    public float area() { return width() * height(); }
    public float centerX() { return (left + right) / 2f; }
    public float centerY() { return (top + bottom) / 2f; }
}
