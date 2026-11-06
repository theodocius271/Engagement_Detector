package com.top.engagement_detector.camera;

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** One open/close cycle. All Camera2 operations are serialized on a private worker thread. */
final class CameraSession {
    interface Listener {
        void onStreaming(CameraSession source);
        void onError(CameraSession source);
        void onClosed(CameraSession source);
    }

    private static final String TAG = "CameraSession";
    private final CameraManager manager;
    private final CameraConfiguration configuration;
    private final Surface surface;
    private final Listener listener;
    private final CameraStream stream;
    private final CameraFrameAnalyzer.Factory analyzerFactory;
    private CameraAnalysis analysis;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("CameraPreview");
    private final Handler handler;
    private volatile boolean stopped;
    // Remaining fields belong exclusively to the worker thread.
    private boolean opening;
    private boolean closingDevice;
    private boolean finished;
    private CameraDevice device;
    private CameraCaptureSession captureSession;

    CameraSession(CameraManager manager, CameraConfiguration configuration, Surface surface,
                  int rotationDegrees, CameraFrameAnalyzer.Factory analyzerFactory, Listener listener) {
        this.manager = manager;
        this.configuration = configuration;
        this.surface = surface;
        this.listener = listener;
        this.analyzerFactory = analyzerFactory;
        stream = new CameraStream(configuration.id, rotationDegrees);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    void start() {
        handler.post(this::open);
    }

    void close() {
        stream.invalidate();
        if (stopped) {
            return;
        }
        stopped = true; // Invalidate pending callbacks immediately, even while openCamera blocks.
        handler.post(this::closeResources);
    }

    @SuppressLint("MissingPermission") // Controller checks permission; revocation is caught below.
    private void open() {
        if (stopped) {
            finish();
            return;
        }
        try {
            if (analyzerFactory != null) {
                analysis = new CameraAnalysis(configuration.analysisSize, stream, analyzerFactory,
                        error -> handler.post(() -> fail("Camera analysis failed", error)));
            }
            opening = true;
            manager.openCamera(configuration.id, deviceCallback, handler);
        } catch (CameraAccessException | RuntimeException e) {
            opening = false;
            fail("Opening camera failed", e);
        }
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            opening = false;
            device = camera;
            if (stopped) {
                closeResources();
                return;
            }
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            opening = false;
            device = camera;
            fail("Camera disconnected", null);
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            opening = false;
            device = camera;
            fail("Camera error: " + error, null);
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            device = null;
            finish();
        }
    };

    @SuppressWarnings("deprecation") // This overload supports the project's API 24 minimum.
    private void createCaptureSession() {
        try {
            List<Surface> outputs = new ArrayList<>();
            outputs.add(surface);
            if (analysis != null) {
                outputs.add(analysis.getSurface());
            }
            device.createCaptureSession(outputs,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (stopped || device == null) {
                                session.close();
                                return;
                            }
                            captureSession = session;
                            startRepeating();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            session.close();
                            fail("Preview session configuration failed", null);
                        }
                    }, handler);
        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException e) {
            fail("Creating preview session failed", e);
        }
    }

    private void startRepeating() {
        try {
            Log.d(TAG, "camera=" + configuration.id + " size=" + configuration.analysisSize
                    + " afMode=" + configuration.autofocusMode);
            CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            if (analysis != null) {
                request.addTarget(analysis.getSurface());
            }
            if (configuration.autofocusMode != null) {
                request.set(CaptureRequest.CONTROL_AF_MODE, configuration.autofocusMode);
            }
            captureSession.setRepeatingRequest(request.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        private boolean reported;

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            if (!stopped && !reported) {
                                reported = true;
                                mainHandler.post(() -> listener.onStreaming(CameraSession.this));
                            }
                        }
                    }, handler);
        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException e) {
            fail("Starting preview failed", e);
        }
    }

    private void fail(String message, Exception exception) {
        Log.e(TAG, message, exception);
        stream.invalidate();
        if (!stopped) {
            mainHandler.post(() -> listener.onError(this));
        }
        stopped = true;
        closeResources();
    }

    private void closeResources() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (device != null) {
            if (!closingDevice) {
                closingDevice = true;
                device.close();
            }
        } else if (!opening) {
            finish();
        }
        // If openCamera is pending, keep the looper alive to close its eventual device callback.
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        stream.invalidate();
        if (analysis != null) {
            analysis.close();
            analysis = null;
        }
        mainHandler.post(() -> listener.onClosed(this));
        thread.quitSafely();
        // SurfaceHolder owns the Surface; it must not be released here.
    }
}
