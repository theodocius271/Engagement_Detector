package com.top.engagement_detector.engagement;

/** Immutable per-camera calibration. Thresholds are starting values, not measured accuracy claims. */
public final class EngagementConfig {
    public final float minAreaRatio, maxYawDegrees, neutralYawDegrees, horizontalInset;
    public final long dwellMs, maxGapMs;

    public EngagementConfig(float minAreaRatio, float maxYawDegrees, float neutralYawDegrees,
                            float horizontalInset, long dwellMs, long maxGapMs) {
        if (!Float.isFinite(minAreaRatio) || minAreaRatio < 0.0001f || minAreaRatio > 0.5f
                || !Float.isFinite(maxYawDegrees) || maxYawDegrees < 5 || maxYawDegrees > 80
                || !Float.isFinite(neutralYawDegrees) || Math.abs(neutralYawDegrees) > 90
                || !Float.isFinite(horizontalInset) || horizontalInset < 0 || horizontalInset > 0.4f
                || dwellMs < 500 || dwellMs > 10000 || maxGapMs < 100 || maxGapMs > 1500) {
            throw new IllegalArgumentException("Calibration outside supported ranges");
        }
        this.minAreaRatio = minAreaRatio;
        this.maxYawDegrees = maxYawDegrees;
        this.neutralYawDegrees = neutralYawDegrees;
        this.horizontalInset = horizontalInset;
        this.dwellMs = dwellMs;
        this.maxGapMs = maxGapMs;
    }

    /** One-time upgrade for profiles saved before the responsive capture policy. */
    static long responsiveDwellMs(long savedDwellMs) {
        return Math.min(savedDwellMs, defaults().dwellMs);
    }

    public static EngagementConfig defaults() {
        return new EngagementConfig(0.008f, 35f, 0f, 0.05f, 900, 350);
    }
}
