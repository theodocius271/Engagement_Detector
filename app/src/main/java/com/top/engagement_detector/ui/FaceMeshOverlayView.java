package com.top.engagement_detector.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.tasks.components.containers.Connection;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.top.engagement_detector.camera.CameraPreview;
import com.top.engagement_detector.vision.FaceExpressionResult;
import com.top.engagement_detector.vision.FaceMesh;
import com.top.engagement_detector.engagement.EngagementDecision;
import com.top.engagement_detector.vision.FaceBox;

import java.util.Set;

/** Transparent sibling of CameraPreview. Draws MediaPipe's tessellation and face contours. */
public final class FaceMeshOverlayView extends View {
    private static final int[] COLORS = {0xff6ee7d4, 0xffffbc77, 0xffc6a4ff};
    private static final int[] IRIS_CENTERS = {468, 473};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] lines = new float[Math.max(FaceLandmarker.FACE_LANDMARKS_TESSELATION.size(),
            FaceLandmarker.FACE_LANDMARKS_CONNECTORS.size()) * 4];
    private final Runnable expire = this::clear;
    private CameraPreview preview;
    private FaceExpressionResult result;
    private EngagementDecision decision;
    private long updatedAt;

    public FaceMeshOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** The preview and this overlay must have the same parent. */
    public void setPreview(CameraPreview preview) {
        this.preview = preview;
    }

    public void show(FaceExpressionResult result) {
        this.result = result;
        updatedAt = SystemClock.elapsedRealtime();
        removeCallbacks(expire);
        postDelayed(expire, 1000);
        invalidate();
    }

    public void clear() {
        removeCallbacks(expire);
        result = null;
        decision = null;
        invalidate();
    }

    public void showVisitors(EngagementDecision decision) {
        this.decision = decision;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (result == null || preview == null || result.imageWidth <= 0 || result.imageHeight <= 0
                || preview.getWidth() == 0 || preview.getHeight() == 0
                || SystemClock.elapsedRealtime() - updatedAt >= 1000) {
            return;
        }
        float left = preview.getX() - getX();
        float top = preview.getY() - getY();
        MeshProjection projection = new MeshProjection(result.imageWidth, result.imageHeight,
                left, top, preview.getWidth(), preview.getHeight(), preview.isPreviewMirrored());
        float density = getResources().getDisplayMetrics().density;
        canvas.save();
        canvas.clipRect(left, top, left + preview.getWidth(), top + preview.getHeight());
        for (int i = 0; i < result.faces.size(); i++) {
            FaceMesh mesh = result.faces.get(i).mesh;
            paint.setColor(COLORS[i % COLORS.length]);
            paint.setAlpha(125);
            paint.setStrokeWidth(Math.max(1f, density * 0.65f));
            drawConnections(canvas, mesh, projection, FaceLandmarker.FACE_LANDMARKS_TESSELATION);
            paint.setAlpha(240);
            paint.setStrokeWidth(Math.max(1.5f, density * 1.2f));
            drawConnections(canvas, mesh, projection, FaceLandmarker.FACE_LANDMARKS_CONNECTORS);
            drawConnections(canvas, mesh, projection, FaceLandmarker.FACE_LANDMARKS_LEFT_IRIS);
            drawConnections(canvas, mesh, projection, FaceLandmarker.FACE_LANDMARKS_RIGHT_IRIS);
            // Iris centers complete the 478-point output; contour connections draw their rims.
            for (int center : IRIS_CENTERS) {
                if (center < mesh.size() && finite(mesh, center)) {
                    canvas.drawCircle(projection.x(mesh.x(center)), projection.y(mesh.y(center)),
                            Math.max(1.5f, density), paint);
                }
            }
        }
        if (decision != null) {
            paint.setAlpha(255);
            paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
            for (EngagementDecision.Visitor visitor : decision.visitors) {
                FaceBox box = visitor.face.observation.box;
                float x = Math.min(projection.x(box.left), projection.x(box.right));
                float right = Math.max(projection.x(box.left), projection.x(box.right));
                paint.setColor(visitor.reason == EngagementDecision.Reason.DWELLING
                        || visitor.reason == EngagementDecision.Reason.READY ? 0xff6ee7d4 : 0xffffbc77);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(density);
                canvas.drawRect(x, projection.y(box.top), right, projection.y(box.bottom), paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawText("#" + visitor.face.id, x,
                        Math.max(top + paint.getTextSize(), projection.y(box.top) - 4 * density), paint);
            }
        }
        canvas.restore();
    }

    private void drawConnections(Canvas canvas, FaceMesh mesh, MeshProjection projection,
                                 Set<Connection> connections) {
        int count = 0;
        for (Connection connection : connections) {
            int a = connection.start();
            int b = connection.end();
            if (a >= mesh.size() || b >= mesh.size() || !finite(mesh, a) || !finite(mesh, b)) {
                continue;
            }
            lines[count++] = projection.x(mesh.x(a));
            lines[count++] = projection.y(mesh.y(a));
            lines[count++] = projection.x(mesh.x(b));
            lines[count++] = projection.y(mesh.y(b));
        }
        canvas.drawLines(lines, 0, count, paint);
    }

    private static boolean finite(FaceMesh mesh, int point) {
        return Float.isFinite(mesh.x(point)) && Float.isFinite(mesh.y(point));
    }
}
