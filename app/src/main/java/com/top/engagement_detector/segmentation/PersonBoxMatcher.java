package com.top.engagement_detector.segmentation;

import com.top.engagement_detector.vision.FaceBox;

import java.util.ArrayList;
import java.util.List;

/** Geometric face/body association, independent of Android and model APIs. */
public final class PersonBoxMatcher {
    private PersonBoxMatcher() { }

    /**
     * Each body nominates its most plausible visible face, including excluded visitors.
     * Each accepted face then takes its best nominated body. Null means a member is missing.
     * This is approximate association, not identity recognition or instance segmentation.
     */
    public static List<FaceBox> match(List<FaceBox> accepted, List<FaceBox> excluded,
                                      List<FaceBox> bodies) {
        if (accepted.isEmpty()) throw new IllegalArgumentException("No accepted faces");
        List<FaceBox> faces = new ArrayList<>(accepted);
        faces.addAll(excluded);
        FaceBox[] chosen = new FaceBox[accepted.size()];
        double[] costs = new double[accepted.size()];
        java.util.Arrays.fill(costs, Double.POSITIVE_INFINITY);
        for (FaceBox body : bodies) {
            int owner = -1;
            double best = Double.POSITIVE_INFINITY;
            boolean tied = false;
            for (int i = 0; i < faces.size(); i++) {
                double cost = cost(faces.get(i), body);
                if (!Double.isFinite(cost)) continue;
                if (cost < best - 0.00001) {
                    owner = i;
                    best = cost;
                    tied = false;
                } else if (Math.abs(cost - best) <= 0.00001) {
                    tied = true;
                }
            }
            if (!tied && owner >= 0 && owner < accepted.size() && best < costs[owner]) {
                costs[owner] = best;
                chosen[owner] = body;
            }
        }
        List<FaceBox> result = new ArrayList<>();
        for (FaceBox body : chosen) {
            if (body == null) return null;
            result.add(body);
        }
        return result;
    }

    private static double cost(FaceBox face, FaceBox body) {
        float overlap = Math.max(0, Math.min(face.right, body.right) - Math.max(face.left, body.left))
                * Math.max(0, Math.min(face.bottom, body.bottom) - Math.max(face.top, body.top));
        float coverage = overlap / face.area();
        float relativeY = (face.centerY() - body.top) / body.height();
        if (coverage < 0.6f || face.centerX() < body.left || face.centerX() > body.right
                || relativeY < 0 || relativeY > 0.6f || body.height() < face.height() * 1.5f) {
            return Double.POSITIVE_INFINITY;
        }
        // Heads tend to be near the top and horizontal center, for full or upper bodies.
        return Math.abs(face.centerX() - body.centerX()) / body.width()
                + Math.abs(relativeY - 0.15f) + (1 - coverage);
    }
}
