package com.top.engagement_detector.vision;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Snapshot of named expression coefficients, independent of MediaPipe and Android views. */
public final class FaceExpression {
    public final float centerX;
    public final Map<String, Float> coefficients;
    public final FaceMesh mesh;
    public final float yawDegrees;

    public FaceExpression(float centerX, Map<String, Float> coefficients) {
        this(centerX, coefficients, new FaceMesh(new float[0]));
    }

    public FaceExpression(float centerX, Map<String, Float> coefficients, FaceMesh mesh) {
        this(centerX, coefficients, mesh, Float.NaN);
    }

    public FaceExpression(float centerX, Map<String, Float> coefficients, FaceMesh mesh, float yawDegrees) {
        this.centerX = centerX;
        this.coefficients = Collections.unmodifiableMap(new HashMap<>(coefficients));
        this.mesh = mesh;
        this.yawDegrees = yawDegrees;
    }

    public float score(String name) {
        Float score = coefficients.get(name);
        return score == null || !Float.isFinite(score) ? 0f : Math.max(0f, Math.min(1f, score));
    }

    public float average(String left, String right) {
        return (score(left) + score(right)) / 2f;
    }
}
