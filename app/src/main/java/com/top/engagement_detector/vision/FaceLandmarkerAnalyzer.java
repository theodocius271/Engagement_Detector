package com.top.engagement_detector.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.top.engagement_detector.camera.CameraFrame;
import com.top.engagement_detector.camera.CameraFrameAnalyzer;
import com.top.engagement_detector.camera.CameraStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-stream CPU task. Creation, VIDEO inference and close all use the analysis worker. */
public final class FaceLandmarkerAnalyzer implements CameraFrameAnalyzer {
    public interface Listener {
        /** Analysis-worker callback; the upright bitmap is borrowed only until this returns. */
        void onResult(FaceExpressionResult result, Bitmap uprightImage);
        void onError();
    }

    private final Context context;
    private final CameraStream stream;
    private final Listener listener;
    private FaceLandmarker landmarker;
    private boolean failed;

    public FaceLandmarkerAnalyzer(Context context, CameraStream stream, Listener listener) {
        this.context = context.getApplicationContext();
        this.stream = stream;
        this.listener = listener;
    }

    @Override
    public void analyze(CameraFrame frame) {
        if (failed || !stream.isActive()) {
            return;
        }
        try {
            if (landmarker == null) {
                BaseOptions base = BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task")
                        .setDelegate(Delegate.CPU).build();
                landmarker = FaceLandmarker.createFromOptions(context,
                        FaceLandmarker.FaceLandmarkerOptions.builder()
                                .setBaseOptions(base)
                                .setRunningMode(RunningMode.VIDEO)
                                .setNumFaces(3)
                                .setMinFaceDetectionConfidence(0.5f)
                                .setMinFacePresenceConfidence(0.5f)
                                .setMinTrackingConfidence(0.5f)
                                .setOutputFaceBlendshapes(false)
                                .setOutputFacialTransformationMatrixes(true)
                                .build());
            }
            if (!stream.isActive()) {
                return;
            }
            // Keep camera pixels borrowed; own the upright analysis image and model input here.
            Matrix rotation = new Matrix();
            rotation.postRotate(stream.rotationDegrees);
            Bitmap owned = stream.rotationDegrees == 0
                    ? frame.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    : Bitmap.createBitmap(frame.bitmap, 0, 0, frame.bitmap.getWidth(),
                            frame.bitmap.getHeight(), rotation, true);
            Bitmap modelBitmap = owned;
            MPImage input = null;
            try {
                // Bound model input even when another camera adapter supplies a larger analysis image.
                float scale = Math.min(1f, 640f / Math.max(owned.getWidth(), owned.getHeight()));
                if (scale < 1f) {
                    modelBitmap = Bitmap.createScaledBitmap(owned,
                            Math.max(1, Math.round(owned.getWidth() * scale)),
                            Math.max(1, Math.round(owned.getHeight() * scale)), true);
                }
                input = new BitmapImageBuilder(modelBitmap).build();
                long start = SystemClock.elapsedRealtime();
                FaceLandmarkerResult result = landmarker.detectForVideo(input, frame.timestampMs);
                FaceExpressionResult expressions = toExpressions(result, frame.timestampMs,
                        SystemClock.elapsedRealtime() - start, owned.getWidth(), owned.getHeight());
                if (stream.isActive()) {
                    listener.onResult(expressions, owned);
                }
            } finally {
                if (input != null) input.close();
                if (modelBitmap != owned && !modelBitmap.isRecycled()) {
                    modelBitmap.recycle();
                }
                if (!owned.isRecycled()) {
                    owned.recycle();
                }
            }
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            Log.e("FaceLandmarker", "Face effects failed", error);
            if (stream.isActive()) {
                listener.onError();
            }
        }
    }

    private static FaceExpressionResult toExpressions(FaceLandmarkerResult result,
                                                       long timestampMs, long inferenceMs,
                                                       int imageWidth, int imageHeight) {
        List<FaceExpression> faces = new ArrayList<>();
        List<List<Category>> expressions = result.faceBlendshapes()
                .orElse(java.util.Collections.emptyList());
        List<List<NormalizedLandmark>> landmarks = result.faceLandmarks();
        List<float[]> matrices = result.facialTransformationMatrixes()
                .orElse(java.util.Collections.emptyList());
        for (int i = 0; i < landmarks.size(); i++) {
            Map<String, Float> scores = new HashMap<>();
            if (i < expressions.size()) {
                for (Category category : expressions.get(i)) {
                    scores.put(category.categoryName(), category.score());
                }
            }
            float centerX = 0f;
            float[] xyz = new float[landmarks.get(i).size() * 3];
            int offset = 0;
            for (NormalizedLandmark point : landmarks.get(i)) {
                centerX += point.x();
                xyz[offset++] = point.x();
                xyz[offset++] = point.y();
                xyz[offset++] = point.z();
            }
            centerX /= Math.max(1, landmarks.get(i).size());
            float yaw = HeadPose.yawDegrees(i < matrices.size() ? matrices.get(i) : null);
            faces.add(new FaceExpression(centerX, scores, new FaceMesh(xyz), yaw));
        }
        return new FaceExpressionResult(timestampMs, inferenceMs, faces, imageWidth, imageHeight);
    }

    @Override
    public void close() {
        if (landmarker != null) {
            try {
                landmarker.close();
            } catch (RuntimeException error) {
                Log.w("FaceLandmarker", "Closing face task failed", error);
            }
            landmarker = null;
        }
    }
}
