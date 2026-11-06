package com.top.engagement_detector.vision;

/** Immutable normalized XYZ landmarks in the upright, unmirrored inference image. */
public final class FaceMesh {
    private final float[] coordinates;

    public FaceMesh(float[] coordinates) {
        if (coordinates.length % 3 != 0) {
            throw new IllegalArgumentException("Expected XYZ landmark triples");
        }
        this.coordinates = coordinates.clone();
    }

    public int size() {
        return coordinates.length / 3;
    }

    public float x(int index) {
        return coordinates[index * 3];
    }

    public float y(int index) {
        return coordinates[index * 3 + 1];
    }

    public float z(int index) {
        return coordinates[index * 3 + 2];
    }
}
