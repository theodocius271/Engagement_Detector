package com.top.engagement_detector.camera;

import java.nio.ByteBuffer;

/** Bilinear full-range luminance in upright pixel coordinates, without an RGB conversion. */
final class YuvLumaSampler {
    final int width, height;
    private final ByteBuffer plane;
    private final int base, rowStride, pixelStride, cropLeft, cropTop, sourceWidth, sourceHeight, rotation;

    YuvLumaSampler(ByteBuffer plane, int rowStride, int pixelStride, int cropLeft, int cropTop,
                   int sourceWidth, int sourceHeight, int rotation) {
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
            throw new IllegalArgumentException("Unsupported camera rotation");
        }
        this.plane = plane;
        base = plane.position();
        this.rowStride = rowStride;
        this.pixelStride = pixelStride;
        this.cropLeft = cropLeft;
        this.cropTop = cropTop;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.rotation = rotation;
        width = rotation % 180 == 0 ? sourceWidth : sourceHeight;
        height = rotation % 180 == 0 ? sourceHeight : sourceWidth;
    }

    double sample(double x, double y) {
        x = Math.max(0, Math.min(width - 1, x));
        y = Math.max(0, Math.min(height - 1, y));
        double sx, sy;
        switch (rotation) {
            case 90: sx = y; sy = sourceHeight - 1 - x; break;
            case 180: sx = sourceWidth - 1 - x; sy = sourceHeight - 1 - y; break;
            case 270: sx = sourceWidth - 1 - y; sy = x; break;
            default: sx = x; sy = y;
        }
        int x0 = (int) sx, y0 = (int) sy;
        int x1 = Math.min(sourceWidth - 1, x0 + 1), y1 = Math.min(sourceHeight - 1, y0 + 1);
        double dx = sx - x0, dy = sy - y0;
        return (luma(x0, y0) * (1 - dx) + luma(x1, y0) * dx) * (1 - dy)
                + (luma(x0, y1) * (1 - dx) + luma(x1, y1) * dx) * dy;
    }

    private double luma(int x, int y) {
        int value = plane.get(base + (cropTop + y) * rowStride + (cropLeft + x) * pixelStride) & 255;
        // Match the existing converter's limited-range BT.601 assumption.
        return Math.max(0, Math.min(255, (value - 16) * (255.0 / 219)));
    }
}
