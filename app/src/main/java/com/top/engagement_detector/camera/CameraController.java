package com.top.engagement_detector.camera;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.top.engagement_detector.R;

import java.util.List;

/** Main-thread coordinator for activity, surface, and display lifecycles. */
public final class CameraController implements SurfaceHolder.Callback,
        DisplayManager.DisplayListener, CameraSession.Listener {
    public interface Listener {
        void onCameraStatus(CharSequence message, boolean canRetry, boolean canSwitch,
                            boolean streaming);
    }

    private final Context context;
    private final CameraPreview preview;
    private final Listener listener;
    private final CameraManager cameraManager;
    private final DisplayManager displayManager;
    private final SharedPreferences preferences;
    private final CameraFrameAnalyzer.Factory analyzerFactory;
    private String selectedCameraId;
    private int cameraCount;
    private CameraConfiguration configuration;
    private CameraSession session;
    private boolean resumed;
    private boolean surfaceReady;
    private boolean closing;
    private boolean failed;
    private boolean released;

    public CameraController(CameraPreview preview, Listener listener) {
        this(preview, listener, null);
    }

    public CameraController(CameraPreview preview, Listener listener,
                            CameraFrameAnalyzer.Factory analyzerFactory) {
        this.preview = preview;
        this.listener = listener;
        this.analyzerFactory = analyzerFactory;
        context = preview.getContext().getApplicationContext();
        cameraManager = context.getSystemService(CameraManager.class);
        displayManager = context.getSystemService(DisplayManager.class);
        preferences = context.getSharedPreferences("camera", Context.MODE_PRIVATE);
        selectedCameraId = preferences.getString("selectedCameraId", null);
        preview.getHolder().addCallback(this);
    }

    /** Call only after camera permission is granted, from the resumed activity. */
    public void start() {
        if (released || resumed) {
            return;
        }
        resumed = true;
        failed = false;
        displayManager.registerDisplayListener(this, new Handler(Looper.getMainLooper()));
        selectCamera(false);
    }

    /** Selected camera identity for per-camera calibration; null before initial discovery. */
    public String getSelectedCameraId() {
        return selectedCameraId;
    }

    /** Cycle through usable camera IDs in CameraManager order; call on the main thread. */
    public void switchCamera() {
        if (!resumed || released) {
            return;
        }
        selectCamera(true);
    }

    private void selectCamera(boolean advance) {
        // Invalidate the old session before changing selection, including pending opens.
        closeSession();
        failed = false;
        configuration = null;
        cameraCount = 0;
        try {
            List<CameraConfiguration> cameras = CameraConfiguration.findAll(cameraManager);
            cameraCount = cameras.size();
            if (cameras.isEmpty()) {
                showError(R.string.camera_unavailable);
                return;
            }
            int selected = 0;
            for (int i = 0; i < cameras.size(); i++) {
                if (cameras.get(i).id.equals(selectedCameraId)) {
                    selected = advance ? (i + 1) % cameras.size() : i;
                    break;
                }
            }
            configuration = cameras.get(selected);
            selectedCameraId = configuration.id;
            preferences.edit().putString("selectedCameraId", selectedCameraId).apply();
            reportStatus(R.string.camera_starting, false);
            preparePreview();
        } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
            Log.e("CameraController", "Camera discovery failed", e);
            showError(R.string.camera_error);
        }
    }

    public void stop() {
        resumed = false;
        displayManager.unregisterDisplayListener(this);
        closeSession();
    }

    public void release() {
        stop();
        released = true;
        preview.getHolder().removeCallback(this);
    }

    private void preparePreview() {
        if (resumed && !released && !failed && session == null && configuration != null) {
            // Resize only after the previous camera has released this surface.
            preview.configure(configuration);
            maybeOpen();
        }
    }

    private void maybeOpen() {
        if (!resumed || released || failed || !surfaceReady || session != null
                || configuration == null) {
            return;
        }
        SurfaceHolder holder = preview.getHolder();
        if (!holder.getSurface().isValid()
                || holder.getSurfaceFrame().width() != configuration.previewSize.getWidth()
                || holder.getSurfaceFrame().height() != configuration.previewSize.getHeight()) {
            return; // setFixedSize is asynchronous: wait for surfaceChanged with the actual size.
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            showError(R.string.camera_permission_required);
            return;
        }
        if (analyzerFactory != null && configuration.analysisSize == null) {
            showError(R.string.camera_analysis_unavailable);
            return;
        }
        session = new CameraSession(cameraManager, configuration, holder.getSurface(),
                preview.getAnalysisRotationDegrees(), analyzerFactory, this);
        session.start();
    }

    private void closeSession() {
        if (session != null && !closing) {
            closing = true;
            session.close();
        }
    }

    private void showError(@StringRes int message) {
        failed = true;
        reportStatus(message, true);
    }

    private void reportStatus(@StringRes int message, boolean canRetry) {
        listener.onCameraStatus(context.getString(message, selectedCameraId), canRetry,
                resumed && !released && cameraCount > 1, message == R.string.camera_live);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Wait for surfaceChanged so the buffer's dimensions are known.
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        surfaceReady = true;
        maybeOpen();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        surfaceReady = false;
        closeSession();
        if (resumed && !failed) {
            reportStatus(R.string.camera_starting, false);
        }
    }

    @Override
    public void onDisplayChanged(int displayId) {
        if (resumed && !closing && configuration != null && preview.getDisplay() != null
                && preview.getDisplay().getDisplayId() == displayId) {
            int previousRotation = preview.getAnalysisRotationDegrees();
            preview.configure(configuration);
            if (previousRotation != preview.getAnalysisRotationDegrees()) {
                // Restart analysis tracking when its coordinate system changes (including 180°).
                closeSession();
                reportStatus(R.string.camera_starting, false);
                preparePreview();
            }
        }
    }

    @Override
    public void onDisplayAdded(int displayId) { }

    @Override
    public void onDisplayRemoved(int displayId) { }

    @Override
    public void onStreaming(CameraSession source) {
        if (source == session && resumed && !closing && !released) {
            reportStatus(R.string.camera_live, false);
        }
    }

    @Override
    public void onError(CameraSession source) {
        if (source == session && resumed && !closing && !released) {
            showError(R.string.camera_error);
        }
    }

    @Override
    public void onClosed(CameraSession source) {
        if (source == session) {
            session = null;
            closing = false;
            // Serialize reopen after shutdown, including rapid pause/resume or surface recreation.
            preparePreview();
        }
    }
}
