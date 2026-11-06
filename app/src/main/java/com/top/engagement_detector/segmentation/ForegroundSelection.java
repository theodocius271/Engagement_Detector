package com.top.engagement_detector.segmentation;

import com.top.engagement_detector.vision.FaceBox;

import java.util.List;

/** Selects foreground using person boxes, then expands and smooths beyond those boxes. */
public final class ForegroundSelection {
    public final int width, height;
    public final FaceBox bounds;
    private final float[] opacity;

    private ForegroundSelection(int width, int height, float[] opacity, FaceBox bounds) {
        this.width = width;
        this.height = height;
        this.opacity = opacity;
        this.bounds = bounds;
    }

    /** Null means a missing figure; retry on a fresh qualified frame without saving a partial group. */
    public static ForegroundSelection select(float[] confidence, int width, int height,
                                             List<FaceBox> accepted, List<FaceBox> bodies) {
        if (width <= 0 || height <= 0 || (long) width * height != confidence.length
                || accepted.isEmpty() || bodies.size() != accepted.size()) {
            throw new IllegalArgumentException("A mask and one body per accepted face are required");
        }
        boolean[] selected = new boolean[confidence.length];
        for (int i = 0; i < selected.length; i++) {
            int x = i % width, y = i / width;
            if (!foreground(confidence[i]) || !inside(bodies, (x + 0.5f) / width, (y + 0.5f) / height)) continue;
            selected[i] = true;
        }
        for (FaceBox face : accepted) {
            int[] region = faceRegion(face, width, height, 0.2f);
            int area = (region[2] - region[0]) * (region[3] - region[1]);
            int hits = 0;
            for (int y = region[1]; y < region[3]; y++) {
                for (int x = region[0]; x < region[2]; x++) {
                    if (selected[y * width + x]) hits++;
                }
            }
            if (area <= 0 || hits * 2 <= area) return null;
        }
        // Validate original evidence before expansion: a margin cannot rescue a missing face.
        float[] opacity = SilhouetteRefiner.refine(selected, width, height);
        int left = width, top = height, right = 0, bottom = 0;
        for (int i = 0; i < opacity.length; i++) {
            if (opacity[i] <= 0) continue;
            left = Math.min(left, i % width);
            top = Math.min(top, i / width);
            right = Math.max(right, i % width + 1);
            bottom = Math.max(bottom, i / width + 1);
        }
        // Bound the entire feather plus bilinear support, rather than the original body boxes.
        FaceBox bounds = new FaceBox(Math.max(0, left - 1) / (float) width,
                Math.max(0, top - 1) / (float) height,
                Math.min(width, right + 1) / (float) width,
                Math.min(height, bottom + 1) / (float) height);
        return new ForegroundSelection(width, height, opacity, bounds);
    }

    private static boolean inside(List<FaceBox> bodies, float x, float y) {
        for (FaceBox body : bodies) {
            if (x >= body.left && x < body.right && y >= body.top && y < body.bottom) return true;
        }
        return false;
    }

    private static boolean foreground(float value) {
        return Float.isFinite(value) && value >= 0.5f && value <= 1f;
    }

    private static int[] faceRegion(FaceBox box, int width, int height, float inset) {
        return new int[] {
                clamp((int) Math.floor((box.left + box.width() * inset) * width), width),
                clamp((int) Math.floor((box.top + box.height() * inset) * height), height),
                clamp((int) Math.ceil((box.right - box.width() * inset) * width), width),
                clamp((int) Math.ceil((box.bottom - box.height() * inset) * height), height)
        };
    }

    private static int clamp(int value, int limit) { return Math.max(0, Math.min(limit, value)); }

    /** Bilinear mask lookup at normalized upright source pixel centers; never mirror here. */
    public float alpha(float normalizedX, float normalizedY) {
        if (normalizedX < 0 || normalizedX > 1 || normalizedY < 0 || normalizedY > 1) return 0;
        float x = Math.max(0, Math.min(width - 1, normalizedX * width - 0.5f));
        float y = Math.max(0, Math.min(height - 1, normalizedY * height - 0.5f));
        int x0 = (int) x, y0 = (int) y;
        int x1 = Math.min(width - 1, x0 + 1), y1 = Math.min(height - 1, y0 + 1);
        float dx = x - x0, dy = y - y0;
        float upper = value(x0, y0) * (1 - dx) + value(x1, y0) * dx;
        float lower = value(x0, y1) * (1 - dx) + value(x1, y1) * dx;
        return upper * (1 - dy) + lower * dy;
    }

    private float value(int x, int y) { return opacity[y * width + x]; }
}
