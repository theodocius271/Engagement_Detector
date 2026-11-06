package com.top.engagement_detector.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** One frame's output. Positions in faces are display order, not persistent tracking IDs. */
public final class FaceExpressionResult {
    public final long timestampMs;
    public final long inferenceMs;
    public final List<FaceExpression> faces;
    public final int imageWidth;
    public final int imageHeight;

    public FaceExpressionResult(long timestampMs, long inferenceMs, List<FaceExpression> faces) {
        this(timestampMs, inferenceMs, faces, 0, 0);
    }

    public FaceExpressionResult(long timestampMs, long inferenceMs, List<FaceExpression> faces,
                                int imageWidth, int imageHeight) {
        this.timestampMs = timestampMs;
        this.inferenceMs = inferenceMs;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        List<FaceExpression> sorted = new ArrayList<>(faces);
        sorted.sort(Comparator.comparingDouble(face -> face.centerX));
        this.faces = Collections.unmodifiableList(sorted);
    }
}
