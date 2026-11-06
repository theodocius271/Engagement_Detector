package com.top.engagement_detector.capture;

import com.top.engagement_detector.vision.FaceBox;

/** Per-face pixel checks. Thresholds are initial tuning values, not a blur guarantee. */
public final class FaceImageQuality {
    public enum Issue { NONE, TOO_SMALL, EXPOSURE, SOFT }
    public static final class Result {
        public final Issue issue;
        public final double sharpness, meanLuma, clippedFraction;
        private Result(Issue issue, double sharpness, double meanLuma, double clippedFraction) {
            this.issue = issue;
            this.sharpness = sharpness;
            this.meanLuma = meanLuma;
            this.clippedFraction = clippedFraction;
        }
    }
    private static final int GRID = 64;
    @FunctionalInterface
    public interface LumaSampler { double sample(double x, double y); }

    private final double[] gray = new double[GRID * GRID];
    private final double[] smooth = new double[GRID * GRID];
    private final int minFacePixels;
    private final double minSharpness;

    public FaceImageQuality(int minFacePixels, double minSharpness) {
        this.minFacePixels = minFacePixels;
        this.minSharpness = minSharpness;
    }

    public static FaceImageQuality defaults() {
        return new FaceImageQuality(64, 80);
    }

    /** Input is the original upright capture frame, never the resized model input. */
    public Result evaluate(int[] argb, int width, int height, FaceBox box) {
        if (width <= 0 || height <= 0 || argb.length < (long) width * height) {
            throw new IllegalArgumentException("Invalid image pixels");
        }
        return evaluate(width, height, box, (x, y) -> sample(argb, width, height, x, y));
    }

    /** Samples only face regions. One instance belongs to one analysis worker. */
    public Result evaluate(int width, int height, FaceBox box, LumaSampler sampler) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid image dimensions");
        double left = Math.max(0, box.left) * width, top = Math.max(0, box.top) * height;
        double right = Math.min(1, box.right) * width, bottom = Math.min(1, box.bottom) * height;
        if (Math.min(right - left, bottom - top) < minFacePixels) {
            return new Result(Issue.TOO_SMALL, 0, 0, 0);
        }
        // Normalize spatial scale and exclude the outer bounds, hair and background corners.
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                double px = left + (0.1 + 0.8 * (x + 0.5) / GRID) * (right - left);
                double py = top + (0.1 + 0.8 * (y + 0.5) / GRID) * (bottom - top);
                gray[y * GRID + x] = sampler.sample(px, py);
            }
        }
        // A small low-pass filter reduces the tendency to reward isolated sensor noise.
        for (int y = 1; y < GRID - 1; y++) {
            for (int x = 1; x < GRID - 1; x++) {
                int i = y * GRID + x;
                smooth[i] = (gray[i] * 4 + (gray[i - 1] + gray[i + 1]
                        + gray[i - GRID] + gray[i + GRID]) * 2 + gray[i - GRID - 1]
                        + gray[i - GRID + 1] + gray[i + GRID - 1] + gray[i + GRID + 1]) / 16;
            }
        }
        double sum = 0, square = 0, luma = 0;
        int count = 0, clipped = 0;
        for (int y = 2; y < GRID - 2; y++) {
            for (int x = 2; x < GRID - 2; x++) {
                double nx = (x - 31.5) / 29, ny = (y - 31.5) / 29;
                if (nx * nx + ny * ny > 1) continue;
                int i = y * GRID + x;
                double lap = smooth[i - 1] + smooth[i + 1] + smooth[i - GRID]
                        + smooth[i + GRID] - 4 * smooth[i];
                sum += lap;
                square += lap * lap;
                luma += gray[i];
                if (gray[i] < 10 || gray[i] > 245) clipped++;
                count++;
            }
        }
        double sharpness = Math.max(0, square / count - Math.pow(sum / count, 2));
        double mean = luma / count, fraction = (double) clipped / count;
        Issue issue = mean < 25 || mean > 235 || fraction > 0.35 ? Issue.EXPOSURE
                : sharpness < minSharpness ? Issue.SOFT : Issue.NONE;
        return new Result(issue, sharpness, mean, fraction);
    }

    private static double sample(int[] pixels, int width, int height, double x, double y) {
        x = Math.max(0, Math.min(width - 1, x));
        y = Math.max(0, Math.min(height - 1, y));
        int x0 = (int) x, y0 = (int) y;
        int x1 = Math.min(width - 1, x0 + 1), y1 = Math.min(height - 1, y0 + 1);
        double dx = x - x0, dy = y - y0;
        return (luma(pixels[y0 * width + x0]) * (1 - dx) + luma(pixels[y0 * width + x1]) * dx) * (1 - dy)
                + (luma(pixels[y1 * width + x0]) * (1 - dx) + luma(pixels[y1 * width + x1]) * dx) * dy;
    }

    private static double luma(int color) {
        return 0.299 * ((color >> 16) & 255) + 0.587 * ((color >> 8) & 255) + 0.114 * (color & 255);
    }
}
