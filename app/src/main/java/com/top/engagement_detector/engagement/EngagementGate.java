package com.top.engagement_detector.engagement;

import com.top.engagement_detector.engagement.EngagementDecision.Reason;
import com.top.engagement_detector.tracking.FaceTracker.TrackedFace;
import com.top.engagement_detector.vision.FaceBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Credits observed qualifying intervals only. A gate belongs to one camera stream. */
public final class EngagementGate {
    private static final class Evidence {
        long qualifiedMs;
        long lastGoodAt = -1;
        long lastSeenAt = -1;
        boolean wasQualifying;
    }

    private final EngagementConfig config;
    private final Map<Integer, Evidence> evidence = new HashMap<>();
    private long previousTimestamp = -1;

    public EngagementGate(EngagementConfig config) {
        this.config = config;
    }

    public EngagementDecision update(long timestamp, List<TrackedFace> faces) {
        List<EngagementDecision.Visitor> visitors = new ArrayList<>();
        List<TrackedFace> ready = new ArrayList<>();
        if (timestamp <= previousTimestamp) {
            evidence.clear();
        }
        previousTimestamp = timestamp;
        evidence.entrySet().removeIf(entry -> timestamp - entry.getValue().lastSeenAt > config.maxGapMs);
        Set<Integer> seen = new HashSet<>();
        for (TrackedFace face : faces) {
            seen.add(face.id);
            Evidence state = evidence.computeIfAbsent(face.id, ignored -> new Evidence());
            state.lastSeenAt = timestamp;
            Reason reason = evaluate(face, state.wasQualifying || state.qualifiedMs >= config.dwellMs);
            if (reason == Reason.DWELLING) {
                if (state.wasQualifying && state.lastGoodAt >= 0) {
                    // A slow sample must not credit an entire unobserved time span.
                    state.qualifiedMs = Math.min(config.dwellMs, state.qualifiedMs
                            + Math.min(200, timestamp - state.lastGoodAt));
                }
                state.lastGoodAt = timestamp;
                state.wasQualifying = true;
                if (state.qualifiedMs >= config.dwellMs) {
                    reason = Reason.READY;
                }
            } else {
                state.wasQualifying = false;
                if (reason != Reason.POSE_UNKNOWN
                        || timestamp - state.lastGoodAt > config.maxGapMs) {
                    state.qualifiedMs = 0;
                    state.lastGoodAt = -1;
                }
            }
            if (reason == Reason.READY) ready.add(face);
            visitors.add(new EngagementDecision.Visitor(face, state.qualifiedMs, reason));
        }
        for (Map.Entry<Integer, Evidence> entry : evidence.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                entry.getValue().wasQualifying = false; // Missing frames preserve briefly, never credit.
            }
        }
        return new EngagementDecision(visitors, ready, config.dwellMs);
    }

    private Reason evaluate(TrackedFace face, boolean staying) {
        FaceBox box = face.observation.box;
        float inset = staying ? Math.max(0, config.horizontalInset - 0.02f) : config.horizontalInset;
        if (box.centerX() < inset || box.centerX() > 1 - inset) {
            return Reason.OUTSIDE_REGION;
        }
        if (box.area() < config.minAreaRatio * (staying ? 0.8f : 1f)) {
            return Reason.TOO_FAR;
        }
        float yaw = face.observation.yawDegrees;
        if (!Float.isFinite(yaw)) {
            return Reason.POSE_UNKNOWN;
        }
        float difference = Math.abs((yaw - config.neutralYawDegrees + 540) % 360 - 180);
        if (difference > config.maxYawDegrees + (staying ? 8f : 0f)) {
            return Reason.LOOKING_AWAY;
        }
        return Reason.DWELLING;
    }
}
