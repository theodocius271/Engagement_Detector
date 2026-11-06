package com.top.engagement_detector.engagement;

import com.top.engagement_detector.tracking.FaceTracker.TrackedFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EngagementDecision {
    public enum Phase { IDLE, DETECTING, FORMING, CAPTURING }

    public enum Reason { TOO_FAR, OUTSIDE_REGION, LOOKING_AWAY, POSE_UNKNOWN, TOO_SMALL, EXPOSURE, SOFT, SEGMENTATION, DWELLING, READY }

    public static final class Visitor {
        public final TrackedFace face;
        public final long qualifiedMs;
        public final Reason reason;

        Visitor(TrackedFace face, long qualifiedMs, Reason reason) {
            this.face = face;
            this.qualifiedMs = qualifiedMs;
            this.reason = reason;
        }
    }

    /** Capture feedback does not feed back into the engagement clock. */
    public EngagementDecision withCaptureIssues(java.util.Map<Integer, Reason> issues) {
        List<Visitor> annotated = new ArrayList<>();
        for (Visitor visitor : visitors) {
            annotated.add(new Visitor(visitor.face, visitor.qualifiedMs,
                    issues.getOrDefault(visitor.face.id, visitor.reason)));
        }
        return new EngagementDecision(annotated, engagedFaces, requiredMs, phase, groupSize, groupReadyCount);
    }

    public final List<Visitor> visitors;
    /** Current engaged visitors; capture still requires independent frame-quality selection. */
    public final List<TrackedFace> engagedFaces;
    public final long requiredMs;
    public final Phase phase;
    public final int groupSize, groupReadyCount;

    EngagementDecision(List<Visitor> visitors, List<TrackedFace> engagedFaces, long requiredMs) {
        this(visitors, engagedFaces, requiredMs, Phase.DETECTING, 0, 0);
    }

    EngagementDecision(List<Visitor> visitors, List<TrackedFace> engagedFaces, long requiredMs,
                       Phase phase, int groupSize, int groupReadyCount) {
        this.phase = phase;
        this.groupSize = groupSize;
        this.groupReadyCount = groupReadyCount;
        this.visitors = Collections.unmodifiableList(new ArrayList<>(visitors));
        this.engagedFaces = Collections.unmodifiableList(new ArrayList<>(engagedFaces));
        this.requiredMs = requiredMs;
    }
}
