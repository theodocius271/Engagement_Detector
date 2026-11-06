package com.top.engagement_detector.segmentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.top.engagement_detector.vision.FaceBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lazy EfficientDet-Lite0 person detection, called only on qualified capture frames. */
public final class PersonDetector implements AutoCloseable {
    private final Context context;
    private ObjectDetector detector;

    public PersonDetector(Context context) { this.context = context.getApplicationContext(); }

    /** Borrows upright unmirrored pixels; returns normalized boxes in the same coordinates. */
    public List<FaceBox> detect(Bitmap frame) {
        if (detector == null) {
            detector = ObjectDetector.createFromOptions(context,
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setBaseOptions(BaseOptions.builder()
                                    .setModelAssetPath("efficientdet_lite0_fp16.tflite")
                                    .setDelegate(Delegate.CPU).build())
                            .setRunningMode(RunningMode.IMAGE)
                            .setCategoryAllowlist(Collections.singletonList("person"))
                            .setScoreThreshold(0.35f).build());
        }
        float scale = Math.min(1f, 640f / Math.max(frame.getWidth(), frame.getHeight()));
        Bitmap owned = Bitmap.createScaledBitmap(frame, Math.max(1, Math.round(frame.getWidth() * scale)),
                Math.max(1, Math.round(frame.getHeight() * scale)), true);
        // MPImage.close() recycles its bitmap, so never give it the borrowed capture.
        if (owned == frame) owned = frame.copy(Bitmap.Config.ARGB_8888, false);
        MPImage input = null;
        try {
            input = new BitmapImageBuilder(owned).build();
            List<FaceBox> boxes = new ArrayList<>();
            for (Detection detection : detector.detect(input).detections()) {
                RectF box = detection.boundingBox();
                float left = Math.max(0, box.left / owned.getWidth());
                float top = Math.max(0, box.top / owned.getHeight());
                float right = Math.min(1, box.right / owned.getWidth());
                float bottom = Math.min(1, box.bottom / owned.getHeight());
                if (Float.isFinite(left) && Float.isFinite(top) && Float.isFinite(right)
                        && Float.isFinite(bottom) && right > left && bottom > top) {
                    boxes.add(new FaceBox(left, top, right, bottom));
                }
            }
            return boxes;
        } finally {
            if (input != null) input.close();
            if (!owned.isRecycled()) owned.recycle();
        }
    }

    @Override
    public void close() {
        if (detector != null) {
            try { detector.close(); } finally { detector = null; }
        }
    }
}
