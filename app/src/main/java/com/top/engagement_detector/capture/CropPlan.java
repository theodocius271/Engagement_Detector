package com.top.engagement_detector.capture;

import com.top.engagement_detector.vision.FaceBox;

import java.util.List;

/** Pixel-exact crop of the qualifying visitors, followed by white square padding, without scaling. */
public final class CropPlan {
    public final int left, top, right, bottom, size, offsetX, offsetY;

    private CropPlan(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        size = Math.max(right - left, bottom - top);
        offsetX = (size - (right - left)) / 2;
        offsetY = (size - (bottom - top)) / 2;
    }

    public static CropPlan forFaces(int width, int height, List<FaceBox> faces) {
        if (width <= 0 || height <= 0 || faces.isEmpty()) {
            throw new IllegalArgumentException("A capture needs an image and qualifying faces");
        }
        float left = 1, top = 1, right = 0, bottom = 0;
        for (FaceBox face : faces) {
            left = Math.min(left, face.left - face.width() * 0.4f);
            top = Math.min(top, face.top - face.height() * 0.4f);
            right = Math.max(right, face.right + face.width() * 0.4f);
            bottom = Math.max(bottom, face.bottom + face.height() * 0.4f);
        }
        int l = Math.max(0, (int) Math.floor(left * width));
        int t = Math.max(0, (int) Math.floor(top * height));
        int r = Math.min(width, (int) Math.ceil(right * width));
        int b = Math.min(height, (int) Math.ceil(bottom * height));
        if (r <= l || b <= t) {
            throw new IllegalArgumentException("Faces are outside the image");
        }
        return new CropPlan(l, t, r, b);
    }

    /** Visible segmented extent with a small white margin, retaining source resolution. */
    public static CropPlan forBounds(int width, int height, FaceBox bounds) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid image size");
        int margin = Math.max(1, Math.round(Math.max(width, height) * 0.02f));
        int left = Math.max(0, (int) Math.floor(bounds.left * width) - margin);
        int top = Math.max(0, (int) Math.floor(bounds.top * height) - margin);
        int right = Math.min(width, (int) Math.ceil(bounds.right * width) + margin);
        int bottom = Math.min(height, (int) Math.ceil(bounds.bottom * height) + margin);
        if (right <= left || bottom <= top) throw new IllegalArgumentException("Bounds outside image");
        return new CropPlan(left, top, right, bottom);
    }
}
