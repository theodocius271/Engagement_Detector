package com.top.engagement_detector.capture;

import com.top.engagement_detector.engagement.EngagementDecision;
import com.top.engagement_detector.engagement.EngagementDecision.Reason;
import com.top.engagement_detector.tracking.FaceTracker.TrackedFace;
import java.util.HashMap;
import java.util.Map;

/** Independent image gate: only a complete, locked group may be saved. */
public final class CaptureReadiness {
    public static final class Assessment {
        public final EngagementDecision decision;
        public final boolean canCapture;
        public final Map<Integer, FaceImageQuality.Result> measurements;
        private Assessment(EngagementDecision decision, boolean canCapture,
                           Map<Integer, FaceImageQuality.Result> measurements) {
            this.decision = decision;
            this.canCapture = canCapture;
            this.measurements = java.util.Collections.unmodifiableMap(measurements);
        }
    }
    private final FaceImageQuality quality;

    public CaptureReadiness(FaceImageQuality quality) { this.quality = quality; }

    public Assessment evaluate(EngagementDecision engagement, int width, int height,
                               FaceImageQuality.LumaSampler luminance) {
        if (engagement.phase != EngagementDecision.Phase.CAPTURING
                || engagement.groupSize == 0 || engagement.engagedFaces.size() != engagement.groupSize) {
            return new Assessment(engagement, false, java.util.Collections.emptyMap());
        }
        Map<Integer, FaceImageQuality.Result> measurements = new HashMap<>();
        Map<Integer, Reason> issues = new HashMap<>();
        for (TrackedFace face : engagement.engagedFaces) {
            FaceImageQuality.Result result = quality.evaluate(width, height, face.observation.box, luminance);
            measurements.put(face.id, result);
            switch (result.issue) {
                case TOO_SMALL: issues.put(face.id, Reason.TOO_SMALL); break;
                case EXPOSURE: issues.put(face.id, Reason.EXPOSURE); break;
                case SOFT: issues.put(face.id, Reason.SOFT); break;
                default: break;
            }
        }
        return new Assessment(issues.isEmpty() ? engagement : engagement.withCaptureIssues(issues),
                !engagement.engagedFaces.isEmpty() && issues.isEmpty(), measurements);
    }
}
