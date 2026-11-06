package com.top.engagement_detector.segmentation;

/** Mask-space expansion and feathering, applied after candidate selection, without box clipping. */
public final class SilhouetteRefiner {
    // At 256x256, three pixels are ~1.2% of each source dimension. The five-tap
    // binomial filter adds a two-pixel feather and approximates a Gaussian with sigma=1.
    public static final int MARGIN_PIXELS = 3;
    private static final int[] KERNEL = {1, 4, 6, 4, 1};

    private SilhouetteRefiner() { }

    public static float[] refine(boolean[] selected, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height != selected.length) {
            throw new IllegalArgumentException("Invalid mask dimensions");
        }
        float[] expanded = new float[selected.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!selected[y * width + x]) continue;
                // A disk avoids the square corners introduced by rectangular dilation.
                for (int dy = -MARGIN_PIXELS; dy <= MARGIN_PIXELS; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= height) continue;
                    for (int dx = -MARGIN_PIXELS; dx <= MARGIN_PIXELS; dx++) {
                        int nx = x + dx;
                        if (nx >= 0 && nx < width && dx * dx + dy * dy <= MARGIN_PIXELS * MARGIN_PIXELS) {
                            expanded[ny * width + nx] = 1;
                        }
                    }
                }
            }
        }
        float[] horizontal = new float[selected.length];
        float[] output = new float[selected.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int k = -2; k <= 2; k++) {
                    int nx = Math.max(0, Math.min(width - 1, x + k));
                    horizontal[y * width + x] += expanded[y * width + nx] * KERNEL[k + 2] / 16f;
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int k = -2; k <= 2; k++) {
                    int ny = Math.max(0, Math.min(height - 1, y + k));
                    output[y * width + x] += horizontal[ny * width + x] * KERNEL[k + 2] / 16f;
                }
                // Never erode accepted detail, even if the margin/filter is tuned later.
                if (selected[y * width + x]) output[y * width + x] = 1;
            }
        }
        return output;
    }
}
