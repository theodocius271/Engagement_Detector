package com.top.engagement_detector.camera;

import java.nio.ByteBuffer;

/** YUV_420_888 planes to ARGB; handles planar/interleaved chroma and row padding. */
final class Yuv420Converter {
    private Yuv420Converter() { }

    static void convert(int width, int height, int cropLeft, int cropTop,
                        ByteBuffer y, int yRow, int yPixel,
                        ByteBuffer u, int uRow, int uPixel,
                        ByteBuffer v, int vRow, int vPixel, int[] output) {
        convertScaled(width, height, width, height, cropLeft, cropTop,
                y, yRow, yPixel, u, uRow, uPixel, v, vRow, vPixel, output);
    }

    /** Sample directly into the analysis size instead of converting all source pixels first. */
    static void convertScaled(int sourceWidth, int sourceHeight, int width, int height,
                              int cropLeft, int cropTop,
                              ByteBuffer y, int yRow, int yPixel,
                              ByteBuffer u, int uRow, int uPixel,
                              ByteBuffer v, int vRow, int vPixel, int[] output) {
        int yBase = y.position();
        int uBase = u.position();
        int vBase = v.position();
        for (int row = 0; row < height; row++) {
            int sourceY = (int) ((2L * row + 1) * sourceHeight / (2L * height)) + cropTop;
            for (int col = 0; col < width; col++) {
                int sourceX = (int) ((2L * col + 1) * sourceWidth / (2L * width)) + cropLeft;
                int luminance = (y.get(yBase + sourceY * yRow + sourceX * yPixel) & 255) - 16;
                int blue = (u.get(uBase + (sourceY / 2) * uRow + (sourceX / 2) * uPixel) & 255) - 128;
                int red = (v.get(vBase + (sourceY / 2) * vRow + (sourceX / 2) * vPixel) & 255) - 128;
                int scaled = 298 * Math.max(0, luminance);
                int r = clamp((scaled + 409 * red + 128) >> 8);
                int g = clamp((scaled - 100 * blue - 208 * red + 128) >> 8);
                int b = clamp((scaled + 516 * blue + 128) >> 8);
                output[row * width + col] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
