package com.top.engagement_detector.ui;

import android.content.Context;

import com.top.engagement_detector.R;
import com.top.engagement_detector.engagement.EngagementDecision;

public final class DetectionStatusFormatter {
    private DetectionStatusFormatter() { }

    public static String format(Context context, EngagementDecision decision) {
        if (decision.visitors.isEmpty() && (decision.phase == EngagementDecision.Phase.IDLE
                || decision.phase == EngagementDecision.Phase.DETECTING)) {
            return context.getString(R.string.engagement_waiting);
        }
        StringBuilder text = new StringBuilder();
        if (decision.phase == EngagementDecision.Phase.FORMING) {
            text.append(context.getString(R.string.group_forming, decision.groupReadyCount, decision.groupSize));
        } else if (decision.phase == EngagementDecision.Phase.CAPTURING) {
            text.append(decision.groupReadyCount < decision.groupSize
                    ? context.getString(R.string.group_waiting, decision.groupReadyCount, decision.groupSize)
                    : context.getString(R.string.group_locked, decision.groupSize));
        }
        for (EngagementDecision.Visitor visitor : decision.visitors) {
            if (text.length() > 0) {
                text.append('\n');
            }
            int reason;
            switch (visitor.reason) {
                case TOO_FAR: reason = R.string.engagement_too_far; break;
                case OUTSIDE_REGION: reason = R.string.engagement_outside; break;
                case LOOKING_AWAY: reason = R.string.engagement_away; break;
                case POSE_UNKNOWN: reason = R.string.engagement_unknown; break;
                case TOO_SMALL: reason = R.string.quality_small; break;
                case EXPOSURE: reason = R.string.quality_exposure; break;
                case SOFT: reason = R.string.quality_soft; break;
                case SEGMENTATION: reason = R.string.capture_separate; break;
                case READY: reason = R.string.engagement_ready; break;
                default: reason = R.string.engagement_dwelling;
            }
            text.append(context.getString(R.string.engagement_visitor, visitor.face.id,
                    context.getString(reason), visitor.qualifiedMs / 1000f, decision.requiredMs / 1000f));
        }
        return text.toString();
    }
}
