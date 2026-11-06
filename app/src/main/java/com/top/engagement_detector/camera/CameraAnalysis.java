package com.top.engagement_detector.camera;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.function.Consumer;

/** Latest-frame analysis with small RGB input and lazy full-resolution conversion for capture. */
final class CameraAnalysis {
    private final CameraStream stream;
    private final CameraFrameAnalyzer.Factory factory;
    private final Consumer<RuntimeException> onError;
    private final HandlerThread thread = new HandlerThread("CameraAnalysis");
    private final Handler handler;
    private final ImageReader reader;
    private CameraFrameAnalyzer analyzer;
    private Bitmap bitmap, captureBitmap;
    private int[] pixels, capturePixels;
    private long nextFrameAt;
    private long lastTimestamp = -1;
    private long lastDiagnosticAt;

    CameraAnalysis(Size size, CameraStream stream, CameraFrameAnalyzer.Factory factory,
                   Consumer<RuntimeException> onError) {
        this.stream = stream;
        this.factory = factory;
        this.onError = onError;
        // One borrowed image may be held during inference; room for latest-frame acquisition remains.
        reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.YUV_420_888, 3);
        thread.start();
        handler = new Handler(thread.getLooper());
        reader.setOnImageAvailableListener(this::onImageAvailable, handler);
    }

    Surface getSurface() { return reader.getSurface(); }

    private void onImageAvailable(ImageReader source) {
        if (!stream.isActive()) return;
        try (Image image = source.acquireLatestImage()) {
            long start = SystemClock.elapsedRealtime();
            if (image == null || start < nextFrameAt) return;
            nextFrameAt = start + 100;
            if (analyzer == null) analyzer = factory.create(stream);
            Rect crop = image.getCropRect();
            float scale = Math.min(1f, 640f / Math.max(crop.width(), crop.height()));
            int width = Math.max(1, Math.round(crop.width() * scale));
            int height = Math.max(1, Math.round(crop.height() * scale));
            if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
                if (bitmap != null) bitmap.recycle();
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                pixels = new int[width * height];
            }
            Image.Plane[] planes = image.getPlanes();
            Yuv420Converter.convertScaled(crop.width(), crop.height(), width, height, crop.left, crop.top,
                    planes[0].getBuffer(), planes[0].getRowStride(), planes[0].getPixelStride(),
                    planes[1].getBuffer(), planes[1].getRowStride(), planes[1].getPixelStride(),
                    planes[2].getBuffer(), planes[2].getRowStride(), planes[2].getPixelStride(), pixels);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            long converted = SystemClock.elapsedRealtime();
            long timestamp = Math.max(lastTimestamp + 1, image.getTimestamp() / 1_000_000L);
            lastTimestamp = timestamp;
            YuvLumaSampler luminance = new YuvLumaSampler(planes[0].getBuffer(),
                    planes[0].getRowStride(), planes[0].getPixelStride(), crop.left, crop.top,
                    crop.width(), crop.height(), stream.rotationDegrees);
            if (stream.isActive()) {
                analyzer.analyze(new CameraFrame(bitmap, timestamp, stream, luminance,
                        () -> fullResolution(image)));
            }
            long end = SystemClock.elapsedRealtime();
            if (end - lastDiagnosticAt >= 2000 && Log.isLoggable("CaptureTiming", Log.DEBUG)) {
                lastDiagnosticAt = end;
                Log.d("CaptureTiming", "camera=" + stream.cameraId + " input=" + width + "x" + height
                        + " conversionMs=" + (converted - start) + " analysisAndCaptureMs=" + (end - converted));
            }
        } catch (RuntimeException error) {
            if (stream.isActive()) {
                stream.invalidate();
                onError.accept(error);
            }
        }
    }

    private Bitmap fullResolution(Image image) {
        Rect crop = image.getCropRect();
        int width = crop.width(), height = crop.height();
        if (captureBitmap == null || captureBitmap.getWidth() != width || captureBitmap.getHeight() != height) {
            if (captureBitmap != null) captureBitmap.recycle();
            captureBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            capturePixels = new int[width * height];
        }
        Image.Plane[] planes = image.getPlanes();
        Yuv420Converter.convert(width, height, crop.left, crop.top,
                planes[0].getBuffer(), planes[0].getRowStride(), planes[0].getPixelStride(),
                planes[1].getBuffer(), planes[1].getRowStride(), planes[1].getPixelStride(),
                planes[2].getBuffer(), planes[2].getRowStride(), planes[2].getPixelStride(), capturePixels);
        captureBitmap.setPixels(capturePixels, 0, width, 0, 0, width, height);
        return captureBitmap;
    }

    /** Reader and buffers are released after any in-flight synchronous frame access completes. */
    void close() {
        stream.invalidate();
        handler.post(() -> {
            try {
                if (analyzer != null) analyzer.close();
            } finally {
                reader.close();
                if (bitmap != null) bitmap.recycle();
                if (captureBitmap != null) captureBitmap.recycle();
                thread.quitSafely();
            }
        });
    }
}
