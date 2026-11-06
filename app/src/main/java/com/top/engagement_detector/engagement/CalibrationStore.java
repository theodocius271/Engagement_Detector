package com.top.engagement_detector.engagement;

import android.content.Context;
import android.content.SharedPreferences;

public final class CalibrationStore {
    private final SharedPreferences preferences;

    public CalibrationStore(Context context) {
        preferences = context.getSharedPreferences("engagement_calibration", Context.MODE_PRIVATE);
    }

    public EngagementConfig load(String cameraId) {
        EngagementConfig defaults = EngagementConfig.defaults();
        String p = cameraId + ":";
        try {
            boolean migrate = preferences.getInt(p + "capturePolicy", 0) < 1;
            long dwell = preferences.getLong(p + "dwell", defaults.dwellMs);
            if (migrate) dwell = EngagementConfig.responsiveDwellMs(dwell);
            EngagementConfig config = new EngagementConfig(preferences.getFloat(p + "area", defaults.minAreaRatio),
                    preferences.getFloat(p + "yaw", defaults.maxYawDegrees),
                    preferences.getFloat(p + "neutral", defaults.neutralYawDegrees),
                    preferences.getFloat(p + "inset", defaults.horizontalInset),
                    dwell,
                    preferences.getLong(p + "gap", defaults.maxGapMs));
            if (migrate) save(cameraId, config);
            return config;
        } catch (IllegalArgumentException error) {
            return defaults;
        }
    }

    public void save(String cameraId, EngagementConfig config) {
        String p = cameraId + ":";
        preferences.edit().putInt(p + "capturePolicy", 1).putFloat(p + "area", config.minAreaRatio)
                .putFloat(p + "yaw", config.maxYawDegrees)
                .putFloat(p + "neutral", config.neutralYawDegrees)
                .putFloat(p + "inset", config.horizontalInset)
                .putLong(p + "dwell", config.dwellMs).putLong(p + "gap", config.maxGapMs).apply();
    }
}
