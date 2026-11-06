package com.top.engagement_detector.engagement;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.top.engagement_detector.camera.CameraFrame;
import com.top.engagement_detector.camera.CameraFrameAnalyzer;
import com.top.engagement_detector.camera.CameraStream;
import com.top.engagement_detector.capture.CaptureReadiness;
import com.top.engagement_detector.capture.CaptureResult;
import com.top.engagement_detector.capture.CaptureStore;
import com.top.engagement_detector.capture.FaceImageQuality;
import com.top.engagement_detector.segmentation.FigureCaptureProcessor;
import com.top.engagement_detector.tracking.FaceTracker;
import com.top.engagement_detector.tracking.FaceTracker.TrackedFace;
import com.top.engagement_detector.vision.FaceBox;
import com.top.engagement_detector.vision.FaceExpressionResult;
import com.top.engagement_detector.vision.FaceLandmarkerAnalyzer;
import com.top.engagement_detector.vision.FaceObservation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Attention → bounded group formation → locked group → first usable current frame. */
public final class EngagementAnalyzer implements CameraFrameAnalyzer {
    public interface Listener {
        void onResult(FaceExpressionResult faces, EngagementDecision decision);
        void onCapture(CaptureResult pending);
        void onError(boolean captureError);
    }

    private final FaceLandmarkerAnalyzer vision;
    private final FigureCaptureProcessor figures;
    private final CaptureReadiness readiness = new CaptureReadiness(FaceImageQuality.defaults());
    private final Handler main = new Handler(Looper.getMainLooper());
    private CameraFrame currentFrame;
    private long lastDiagnosticAt = -1;
    private boolean completed;
    private boolean waitingForFigure;
    private long nextFigureAttemptAt;

    public EngagementAnalyzer(Context context, CameraStream stream, EngagementConfig config,
                              CaptureStore captures, Listener listener) {
        FaceTracker tracker = new FaceTracker(Math.max(600, config.maxGapMs));
        EngagementGroup group = new EngagementGroup(config);
        figures = new FigureCaptureProcessor(context);
        vision = new FaceLandmarkerAnalyzer(context, stream, new FaceLandmarkerAnalyzer.Listener() {
            @Override
            public void onResult(FaceExpressionResult faces, Bitmap modelFrame) {
                List<TrackedFace> tracked = tracker.update(faces.timestampMs, FaceObservation.from(faces));
                EngagementDecision engagement = group.update(faces.timestampMs, tracked);
                CaptureReadiness.Assessment result = readiness.evaluate(engagement,
                        currentFrame.captureWidth, currentFrame.captureHeight, currentFrame::sampleCaptureLuma);
                EngagementDecision display = waitingForFigure && result.canCapture
                        ? withFigureIssue(result.decision) : result.decision;
                main.post(() -> {
                    if (stream.isActive()) listener.onResult(faces, display);
                });
                if (Log.isLoggable("CaptureQuality", Log.DEBUG)
                        && (lastDiagnosticAt < 0 || faces.timestampMs - lastDiagnosticAt >= 1000)) {
                    for (Map.Entry<Integer, FaceImageQuality.Result> entry : result.measurements.entrySet()) {
                        FaceImageQuality.Result measurement = entry.getValue();
                        Log.d("CaptureQuality", "camera=" + stream.cameraId + " frame=" + faces.timestampMs
                                + " visitor=" + entry.getKey() + " sharpness=" + measurement.sharpness
                                + " luma=" + measurement.meanLuma + " clipped=" + measurement.clippedFraction
                                + " issue=" + measurement.issue);
                    }
                    if (!result.measurements.isEmpty()) lastDiagnosticAt = faces.timestampMs;
                }
                if (result.canCapture && stream.isActive()
                        && SystemClock.elapsedRealtime() >= nextFigureAttemptAt) {
                    Bitmap capture = null;
                    Bitmap figure = null;
                    try {
                        capture = currentFrame.copyUprightCapture();
                        if (capture == null) throw new IOException("Cannot copy capture frame");
                        List<FaceBox> accepted = new ArrayList<>(), excluded = new ArrayList<>();
                        for (TrackedFace face : tracked) {
                            (engagement.engagedFaces.contains(face) ? accepted : excluded).add(face.observation.box);
                        }
                        figure = figures.process(capture, accepted, excluded);
                        if (!stream.isActive()) return;
                        if (figure == null) {
                            waitingForFigure = true;
                            // Continue tracking/group recovery and quality checks between attempts.
                            nextFigureAttemptAt = SystemClock.elapsedRealtime() + 750;
                            main.post(() -> {
                                if (stream.isActive()) listener.onResult(faces, withFigureIssue(result.decision));
                            });
                            return;
                        }
                        CaptureResult pending = captures.prepare(figure, accepted.size(), faces.timestampMs);
                        figure = null; // Pending result now owns the finished bitmap.
                        completed = true;
                        main.post(() -> {
                            if (stream.isActive()) listener.onCapture(pending);
                            else pending.discardPending();
                        });
                    } catch (IOException | RuntimeException | LinkageError error) {
                        completed = true;
                        Log.e("EngagementAnalyzer", "Capture failed", error);
                        main.post(() -> {
                            if (stream.isActive()) listener.onError(true);
                        });
                    } finally {
                        if (capture != null) capture.recycle();
                        if (figure != null) figure.recycle();
                    }
                }
            }

            @Override
            public void onError() {
                main.post(() -> {
                    if (stream.isActive()) listener.onError(false);
                });
            }
        });
    }

    private static EngagementDecision withFigureIssue(EngagementDecision decision) {
        Map<Integer, EngagementDecision.Reason> issues = new HashMap<>();
        for (TrackedFace face : decision.engagedFaces) {
            issues.put(face.id, EngagementDecision.Reason.SEGMENTATION);
        }
        return decision.withCaptureIssues(issues);
    }

    @Override
    public void analyze(CameraFrame frame) {
        if (!completed) {
            currentFrame = frame;
            try {
                vision.analyze(frame);
            } finally {
                currentFrame = null;
            }
        }
    }

    @Override
    public void close() {
        try {
            vision.close();
        } finally {
            try { figures.close(); }
            catch (RuntimeException error) { Log.w("EngagementAnalyzer", "Closing figure tasks failed", error); }
        }
        // Pending capture callbacks still discard invalidated streams' files/images.
    }
}
