package com.top.engagement_detector.segmentation;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.top.engagement_detector.capture.CropPlan;

/** Full-resolution figure pixels on opaque white, cropped together and square padded. */
public final class FigureCompositor {
    private FigureCompositor() { }

    /** Borrows source; the caller owns the returned bitmap. */
    public static Bitmap compose(Bitmap source, ForegroundSelection selection) {
        CropPlan plan = CropPlan.forBounds(source.getWidth(), source.getHeight(), selection.bounds);
        Bitmap output = Bitmap.createBitmap(plan.size, plan.size, Bitmap.Config.ARGB_8888);
        try {
            output.eraseColor(Color.WHITE);
            int width = plan.right - plan.left;
            int[] row = new int[width];
            for (int y = plan.top; y < plan.bottom; y++) {
                source.getPixels(row, 0, width, plan.left, y, width, 1);
                for (int x = 0; x < width; x++) {
                    float alpha = selection.alpha((plan.left + x + 0.5f) / source.getWidth(),
                            (y + 0.5f) / source.getHeight());
                    int color = row[x];
                    row[x] = Color.rgb(Math.round(255 + (Color.red(color) - 255) * alpha),
                            Math.round(255 + (Color.green(color) - 255) * alpha),
                            Math.round(255 + (Color.blue(color) - 255) * alpha));
                }
                output.setPixels(row, 0, width, plan.offsetX, plan.offsetY + y - plan.top, width, 1);
            }
            return output;
        } catch (RuntimeException | Error error) {
            output.recycle();
            throw error;
        }
    }
}
