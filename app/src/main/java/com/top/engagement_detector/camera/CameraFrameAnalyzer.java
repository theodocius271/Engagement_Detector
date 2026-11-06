package com.top.engagement_detector.camera;

/** All methods run on one dedicated analysis thread per stream, never the UI/camera thread. */
public interface CameraFrameAnalyzer extends AutoCloseable {
    interface Factory {
        CameraFrameAnalyzer create(CameraStream stream);
    }

    /** Do not retain or recycle the borrowed frame bitmap. Copy any data needed afterwards. */
    void analyze(CameraFrame frame);

    @Override
    void close();
}
