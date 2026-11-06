package com.top.engagement_detector.segmentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.top.engagement_detector.capture.CropPlan;
import com.top.engagement_detector.vision.FaceBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FigureCaptureInstrumentedTest {
    @Test public void compositorPreservesSourceDetailAndMakesBackgroundAndPaddingOpaqueWhite() {
        Bitmap source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.BLUE);
        List<FaceBox> faces = Collections.singletonList(new FaceBox(.1f, .1f, .3f, .3f));
        List<FaceBox> bodies = Collections.singletonList(new FaceBox(0, 0, .4f, 1));
        float[] confidence = new float[10000];
        Arrays.fill(confidence, 1);
        ForegroundSelection selection = ForegroundSelection.select(confidence, 100, 100, faces, bodies);
        CropPlan plan = CropPlan.forBounds(120, 80, selection.bounds);
        Bitmap output = FigureCompositor.compose(source, selection);
        try {
            assertFalse(source.isRecycled());
            assertEquals(Color.BLUE, output.getPixel(plan.offsetX + 24 - plan.left,
                    plan.offsetY + 40 - plan.top));
            // The old body box ends at x=48. Expansion must preserve pixels beyond it.
            assertEquals(Color.BLUE, output.getPixel(plan.offsetX + 48 - plan.left,
                    plan.offsetY + 40 - plan.top));
            int feather = output.getPixel(plan.offsetX + 51 - plan.left, plan.offsetY + 40 - plan.top);
            assertTrue(Color.red(feather) > 0 && Color.red(feather) < 255);
            assertEquals(255, Color.alpha(feather));
            assertEquals(Color.WHITE, output.getPixel(plan.offsetX + 55 - plan.left,
                    plan.offsetY + 40 - plan.top));
            assertEquals(Color.WHITE, output.getPixel(0, 0));
            assertEquals(plan.size, output.getWidth());
            assertEquals(plan.size, output.getHeight());
        } finally {
            output.recycle();
            source.recycle();
        }
    }

    @Test public void bundledModelsRunAndNeverRecycleBorrowedSquareCapture() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap source = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.GRAY);
        Bitmap output = null;
        try (PersonDetector detector = new PersonDetector(context);
             SelfieFigureSegmenter segmenter = new SelfieFigureSegmenter(context)) {
            assertNotNull(detector.detect(source));
            assertFalse(source.isRecycled());
            // Blank input tests asset/native graph compatibility, not recognition accuracy.
            output = segmenter.segment(source,
                    Collections.singletonList(new FaceBox(.2f, .1f, .4f, .3f)),
                    Collections.singletonList(new FaceBox(0, 0, 1, 1)));
            assertFalse(source.isRecycled());
            assertEquals(Color.GRAY, source.getPixel(0, 0));
        } finally {
            if (output != null) output.recycle();
            source.recycle();
        }
    }
}
