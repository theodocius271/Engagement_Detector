package com.top.engagement_detector.camera;

/** Identity and validity of one camera open cycle; safe to check from result callbacks. */
public final class CameraStream {
    public final String cameraId;
    public final int rotationDegrees;
    private volatile boolean active = true;

    CameraStream(String cameraId, int rotationDegrees) {
        this.cameraId = cameraId;
        this.rotationDegrees = rotationDegrees;
    }

    public boolean isActive() {
        return active;
    }

    void invalidate() {
        active = false;
    }
}
