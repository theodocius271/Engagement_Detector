package com.top.engagement_detector.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.top.engagement_detector.R;
import com.top.engagement_detector.vision.FaceExpression;
import com.top.engagement_detector.vision.FaceExpressionResult;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Illustrative 2D avatars driven by actual blendshape scores, not a face mesh. */
public final class BlendshapeView extends View {
    private static final int[] COLORS = {0xff6ee7d4, 0xffffbc77, 0xffc6a4ff};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mouth = new Path();
    private List<FaceExpression> faces = Collections.emptyList();
    private long updatedAt;
    private final Runnable expire = this::clear;

    public BlendshapeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(0xff131b2b);
        setContentDescription(context.getString(R.string.face_visualization));
    }

    public void show(FaceExpressionResult result) {
        faces = result.faces;
        updatedAt = SystemClock.elapsedRealtime();
        removeCallbacks(expire);
        postDelayed(expire, 1000);
        invalidate();
    }

    public void clear() {
        removeCallbacks(expire);
        faces = Collections.emptyList();
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
        if (faces.isEmpty() || SystemClock.elapsedRealtime() - updatedAt >= 1000) {
            paint.setColor(0xffb7c8de);
            paint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getContext().getString(R.string.face_invitation),
                    getWidth() / 2f, getHeight() / 2f, paint);
            return;
        }
        int count = Math.min(3, faces.size());
        float lane = getWidth() / (float) count;
        float scale = Math.min(lane / 240f, getHeight() / 310f);
        for (int i = 0; i < count; i++) {
            canvas.save();
            canvas.translate(i * lane + (lane - 240 * scale) / 2f,
                    (getHeight() - 310 * scale) / 2f);
            canvas.scale(scale, scale);
            drawFace(canvas, faces.get(i), i);
            canvas.restore();
        }
    }

    private void drawFace(Canvas canvas, FaceExpression face, int index) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(14);
        paint.setColor(0xffe7efff);
        canvas.drawText(getContext().getString(R.string.face_number, index + 1), 120, 20, paint);
        paint.setColor(COLORS[index]);
        canvas.drawOval(40, 34, 200, 213, paint);
        // Left/right coefficient names refer to the subject: subject-left is viewer-right.
        drawEye(canvas, 86, face.score("eyeBlinkRight"),
                face.score("browOuterUpRight"), face.score("browDownRight"), face.score("browInnerUp"));
        drawEye(canvas, 154, face.score("eyeBlinkLeft"),
                face.score("browOuterUpLeft"), face.score("browDownLeft"), face.score("browInnerUp"));
        float smile = face.average("mouthSmileLeft", "mouthSmileRight");
        float frown = face.average("mouthFrownLeft", "mouthFrownRight");
        float open = face.score("jawOpen");
        float halfWidth = 23 + 17 * smile - 12 * face.score("mouthPucker");
        paint.setColor(0xff172337);
        if (open > 0.08f) {
            canvas.drawOval(120 - halfWidth, 154 - 6 * smile,
                    120 + halfWidth, 163 + 36 * open, paint);
            paint.setColor(0xffff7d96);
            canvas.drawOval(120 - halfWidth * 0.6f, 161 + 22 * open,
                    120 + halfWidth * 0.6f, 162 + 32 * open, paint);
        } else {
            mouth.reset();
            mouth.moveTo(120 - halfWidth, 162);
            mouth.quadTo(120, 163 + 25 * smile - 18 * frown, 120 + halfWidth, 162);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawPath(mouth, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        drawBar(canvas, R.string.expression_smile, smile, 239, COLORS[index]);
        drawBar(canvas, R.string.expression_mouth, open, 259, COLORS[index]);
        drawBar(canvas, R.string.expression_blink,
                face.average("eyeBlinkLeft", "eyeBlinkRight"), 279, COLORS[index]);
        drawBar(canvas, R.string.expression_brow, face.score("browInnerUp"), 299, COLORS[index]);
    }

    private void drawEye(Canvas canvas, float x, float blink, float browUp, float browDown,
                         float innerUp) {
        float opening = 13 * (1 - 0.96f * blink);
        paint.setColor(Color.WHITE);
        canvas.drawOval(x - 18, 107 - opening, x + 18, 107 + opening, paint);
        paint.setColor(0xff172337);
        canvas.drawOval(x - 6, 107 - opening * 0.8f, x + 6, 107 + opening * 0.8f, paint);
        float browY = 80 - 13 * browUp - 8 * innerUp + 9 * browDown;
        paint.setStrokeWidth(5);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x - 17, browY, x + 17, browY - 2, paint);
    }

    private void drawBar(Canvas canvas, int label, float value, float y, int color) {
        paint.setTextSize(12);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xffb7c8de);
        canvas.drawText(getContext().getString(label), 14, y, paint);
        paint.setColor(0xff334259);
        canvas.drawRoundRect(88, y - 9, 196, y - 1, 4, 4, paint);
        paint.setColor(color);
        canvas.drawRoundRect(88, y - 9, 88 + 108 * value, y - 1, 4, 4, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.ROOT, "%.2f", value), 229, y, paint);
    }
}
