package com.top.engagement_detector.vision;

import java.util.ArrayList;
import java.util.List;

public final class FaceObservation {
    public final FaceBox box;
    public final float yawDegrees;

    public FaceObservation(FaceBox box, float yawDegrees) {
        this.box = box;
        this.yawDegrees = yawDegrees;
    }

    public static List<FaceObservation> from(FaceExpressionResult result) {
        List<FaceObservation> observations = new ArrayList<>();
        for (FaceExpression face : result.faces) {
            if (face.mesh.size() < 468) {
                continue;
            }
            float left = 1f, top = 1f, right = 0f, bottom = 0f;
            boolean valid = true;
            for (int i = 0; i < 468; i++) {
                float x = face.mesh.x(i), y = face.mesh.y(i);
                if (!Float.isFinite(x) || !Float.isFinite(y)) {
                    valid = false;
                    break;
                }
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
            left = Math.max(0, left);
            top = Math.max(0, top);
            right = Math.min(1, right);
            bottom = Math.min(1, bottom);
            if (valid && right > left && bottom > top) {
                observations.add(new FaceObservation(new FaceBox(left, top, right, bottom),
                        face.yawDegrees));
            }
        }
        return observations;
    }
}
