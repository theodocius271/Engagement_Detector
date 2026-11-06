package com.top.engagement_detector.engagement;

import com.top.engagement_detector.engagement.EngagementDecision.Phase;
import com.top.engagement_detector.engagement.EngagementDecision.Reason;
import com.top.engagement_detector.engagement.EngagementDecision.Visitor;
import com.top.engagement_detector.tracking.FaceTracker.TrackedFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-stream attention and group membership. A departure releases capture membership, not dwell. */
public final class EngagementGroup {
    private final EngagementConfig config;
    private EngagementGate attention;
    // Last observed qualifying pose during formation, or last seen observation during capture.
    private final Map<Integer, Long> members = new LinkedHashMap<>();
    private Phase phase = Phase.IDLE;
    private long formationStarted;
    private boolean openAdmission;
    private long previousTimestamp = -1;

    public EngagementGroup(EngagementConfig config) {
        this.config = config;
        attention = new EngagementGate(config);
    }

    public EngagementDecision update(long timestamp, List<TrackedFace> faces) {
        if (timestamp <= previousTimestamp) {
            // Do not use a duplicate/reversed frame for capture or credit unobserved attention.
            previousTimestamp = timestamp;
            return decision(Collections.emptyList(), Collections.emptyList(), 0);
        }
        previousTimestamp = timestamp;
        // Keep independent attention evidence for visible visitors. An intact capture group
        // still excludes outsiders; they can only be admitted after a departure opens a group.
        EngagementDecision observed = attention.update(timestamp, faces);
        Map<Integer, Visitor> current = new LinkedHashMap<>();
        for (Visitor visitor : observed.visitors) current.put(visitor.face.id, visitor);

        if (phase == Phase.CAPTURING) {
            boolean departed = members.entrySet().removeIf(entry -> {
                Visitor visitor = current.get(entry.getKey());
                if (visitor == null) return timestamp - entry.getValue() > config.maxGapMs;
                if (visitor.reason == Reason.TOO_FAR || visitor.reason == Reason.OUTSIDE_REGION) return true;
                entry.setValue(timestamp); // Looking away/unknown pose is not physical departure.
                return false;
            });
            if (departed) {
                if (members.isEmpty()) return idle();
                if (members.size() >= 2) {
                    phase = Phase.FORMING;
                    openAdmission = true;
                    formationStarted = timestamp;
                }
                // A single survivor remains accepted and may capture in this same frame.
            }
        }

        if (phase == Phase.IDLE || phase == Phase.DETECTING) {
            if (observed.visitors.isEmpty()) return idle();
            phase = Phase.DETECTING;
            if (observed.engagedFaces.isEmpty()) return observed;
            admitFacing(current, timestamp);
            formationStarted = timestamp;
            openAdmission = false;
            phase = Phase.FORMING;
        }
        if (phase == Phase.FORMING) {
            if (openAdmission) admitFacing(current, timestamp);
            members.entrySet().removeIf(entry -> {
                Visitor visitor = current.get(entry.getKey());
                if (visitor != null && isFacing(visitor)) {
                    entry.setValue(timestamp);
                    return false;
                }
                if (visitor != null && visitor.reason != Reason.POSE_UNKNOWN) return true;
                return timestamp - entry.getValue() > config.maxGapMs;
            });
            int ready = 0;
            for (int id : members.keySet()) {
                Visitor visitor = current.get(id);
                if (visitor != null && visitor.reason == Reason.READY) ready++;
            }
            // A rebuilt group stays open for one bounded admission window, even if its
            // survivors are already ready. This gives a replacement visitor a chance to join.
            if (!members.isEmpty() && ready == members.size() && (!openAdmission || members.size() == 1)) {
                phase = Phase.CAPTURING;
                openAdmission = false;
            } else if (timestamp - formationStarted >= config.dwellMs) {
                members.keySet().removeIf(id -> current.get(id) == null || current.get(id).reason != Reason.READY);
                if (!members.isEmpty()) phase = Phase.CAPTURING;
                openAdmission = false;
            }
            if (members.isEmpty()) return idle();
        }

        List<Visitor> visitors = new ArrayList<>();
        List<TrackedFace> readyFaces = new ArrayList<>();
        int readyCount = 0;
        for (int id : members.keySet()) {
            Visitor visitor = current.get(id);
            if (visitor == null) continue;
            boolean acceptedPose = phase == Phase.CAPTURING && isFacing(visitor);
            if (acceptedPose || visitor.reason == Reason.READY) readyCount++;
            if (acceptedPose) readyFaces.add(visitor.face);
            visitors.add(phase == Phase.CAPTURING
                    ? new Visitor(visitor.face, config.dwellMs, acceptedPose ? Reason.READY : visitor.reason)
                    : visitor);
        }
        if (phase != Phase.CAPTURING || readyFaces.size() != members.size()) readyFaces.clear();
        return decision(visitors, readyFaces, readyCount);
    }

    private void admitFacing(Map<Integer, Visitor> current, long timestamp) {
        for (Visitor visitor : current.values()) {
            if (isFacing(visitor)) members.putIfAbsent(visitor.face.id, timestamp);
        }
    }

    private static boolean isFacing(Visitor visitor) {
        return visitor.reason == Reason.DWELLING || visitor.reason == Reason.READY;
    }

    private EngagementDecision decision(List<Visitor> visitors, List<TrackedFace> ready, int readyCount) {
        return new EngagementDecision(visitors, ready, config.dwellMs, phase, members.size(), readyCount);
    }

    private EngagementDecision idle() {
        members.clear();
        openAdmission = false;
        phase = Phase.IDLE;
        // No members remain. Forget departed people, but never recreate the gate when a
        // survivor exists: group transitions must not reset their independent attention.
        attention = new EngagementGate(config);
        return decision(Collections.emptyList(), Collections.emptyList(), 0);
    }
}
