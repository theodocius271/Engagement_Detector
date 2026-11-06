package com.top.engagement_detector.segmentation;

import android.content.Context;
import android.graphics.Bitmap;

import com.top.engagement_detector.vision.FaceBox;

import java.util.List;

/** Transferable post-quality pipeline; all operations run on one background worker. */
public final class FigureCaptureProcessor implements AutoCloseable {
    private final PersonDetector detector;
    private final SelfieFigureSegmenter segmenter;

    public FigureCaptureProcessor(Context context) {
        detector = new PersonDetector(context);
        segmenter = new SelfieFigureSegmenter(context);
    }

    /** Borrows source; returns an owned white-background image, or null to retry a fresh frame. */
    public Bitmap process(Bitmap source, List<FaceBox> accepted, List<FaceBox> excluded) {
        List<FaceBox> bodies = PersonBoxMatcher.match(accepted, excluded, detector.detect(source));
        return bodies == null ? null : segmenter.segment(source, accepted, bodies);
    }

    @Override
    public void close() {
        try { detector.close(); } finally { segmenter.close(); }
    }
}
