package com.top.engagement_detector.segmentation;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.ByteBufferExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;
import com.top.engagement_detector.vision.FaceBox;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;

/** Lazy per-stream IMAGE task. Create, infer and close on the camera analysis worker. */
public final class SelfieFigureSegmenter implements AutoCloseable {
    private final Context context;
    private ImageSegmenter segmenter;

    public SelfieFigureSegmenter(Context context) { this.context = context.getApplicationContext(); }

    /** Borrows an upright, unmirrored qualified capture. Null asks for a fresh qualified frame. */
    public Bitmap segment(Bitmap frame, List<FaceBox> accepted, List<FaceBox> bodies) {
        if (segmenter == null) {
            segmenter = ImageSegmenter.createFromOptions(context,
                    ImageSegmenter.ImageSegmenterOptions.builder()
                            .setBaseOptions(BaseOptions.builder()
                                    .setModelAssetPath("selfie_segmenter_fp16_256x256.tflite")
                                    .setDelegate(Delegate.CPU).build())
                            .setRunningMode(RunningMode.IMAGE)
                            .setOutputConfidenceMasks(true).setOutputCategoryMask(false).build());
        }
        // Explicit full-frame resize: no crop, letterbox, rotation or reflection. Normalized
        // face coordinates therefore map directly to the mask, including non-square captures.
        Bitmap scaled = Bitmap.createScaledBitmap(frame, 256, 256, true);
        if (scaled == frame) scaled = frame.copy(Bitmap.Config.ARGB_8888, false);
        MPImage input = null;
        List<MPImage> masks = Collections.emptyList();
        try {
            input = new BitmapImageBuilder(scaled).build();
            ImageSegmenterResult result = segmenter.segment(input);
            masks = result.confidenceMasks().orElse(Collections.emptyList());
            // This supplied binary model has ONE output channel, labeled "selfie" in its
            // embedded labels.txt; it is person confidence, not class-0 background confidence.
            if (masks.size() != 1) throw new IllegalStateException("Expected one selfie confidence mask");
            MPImage mask = masks.get(0);
            FloatBuffer values = ByteBufferExtractor.extract(mask).order(ByteOrder.nativeOrder()).asFloatBuffer();
            float[] confidence = new float[mask.getWidth() * mask.getHeight()];
            if (values.remaining() != confidence.length) throw new IllegalStateException("Invalid selfie mask");
            values.get(confidence);
            ForegroundSelection selection = ForegroundSelection.select(confidence,
                    mask.getWidth(), mask.getHeight(), accepted, bodies);
            return selection == null ? null : FigureCompositor.compose(frame, selection);
        } finally {
            for (MPImage mask : masks) mask.close();
            if (input != null) input.close();
            if (scaled != frame && !scaled.isRecycled()) scaled.recycle();
        }
    }

    @Override
    public void close() {
        if (segmenter != null) {
            try { segmenter.close(); } finally { segmenter = null; }
        }
    }
}
