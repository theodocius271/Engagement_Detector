package com.top.engagement_detector.vision;

/** Coarse horizontal head direction from MediaPipe's affine canonical-face transform. */
public final class HeadPose {
    private HeadPose() { }

    public static float yawDegrees(float[] matrix) {
        if (matrix == null || matrix.length != 16) {
            return Float.NaN;
        }
        for (float value : matrix) {
            if (!Float.isFinite(value)) {
                return Float.NaN;
            }
        }
        // MediaPipe uses column-major matrices. Accept row-major affine exports as well;
        // their translation column distinguishes them from the zero affine bottom row.
        boolean columnMajor = Math.abs(matrix[3]) + Math.abs(matrix[7]) + Math.abs(matrix[11]) < 0.001f;
        boolean rowMajor = Math.abs(matrix[12]) + Math.abs(matrix[13]) + Math.abs(matrix[14]) < 0.001f;
        if ((!columnMajor && !rowMajor) || Math.abs(matrix[15] - 1f) > 0.01f) {
            return Float.NaN;
        }
        float x = matrix[columnMajor ? 8 : 2];
        float y = matrix[columnMajor ? 9 : 6];
        float z = matrix[10];
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 0.0001 || Math.hypot(x, z) / length < 0.1) {
            return Float.NaN; // Near vertical: horizontal direction is indeterminate.
        }
        // Project the face normal onto the horizontal plane; pitch is not an acceptance gate.
        return (float) Math.toDegrees(Math.atan2(x, z));
    }
}
