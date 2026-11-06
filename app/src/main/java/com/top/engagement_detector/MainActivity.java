package com.top.engagement_detector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.top.engagement_detector.camera.CameraController;
import com.top.engagement_detector.ui.BlendshapeView;
import com.top.engagement_detector.ui.FaceMeshOverlayView;
import com.top.engagement_detector.vision.FaceExpressionResult;
import com.top.engagement_detector.vision.FaceObservation;
import com.top.engagement_detector.capture.CaptureResult;
import com.top.engagement_detector.capture.CaptureStore;
import com.top.engagement_detector.engagement.CalibrationStore;
import com.top.engagement_detector.engagement.EngagementAnalyzer;
import com.top.engagement_detector.engagement.EngagementDecision;
import com.top.engagement_detector.ui.DetectionSettingsDialog;
import com.top.engagement_detector.ui.DetectionStatusFormatter;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private CameraController cameraController;
    private TextView status;
    private Button action;
    private Button switchCamera;
    private Button faceRetry;
    private TextView faceStatus;
    private BlendshapeView blendshapeView;
    private FaceMeshOverlayView meshOverlay;
    private CaptureStore captures;
    private CalibrationStore calibration;
    private CaptureResult captured;
    private ImageView capturedImage;
    private View liveStage;
    private View cameraControls;
    private Button reset;
    private Button settings;
    private TextView engagementStatus;
    private FaceExpressionResult latestFaces;
    private long latestFacesAt;
    private AlertDialog settingsDialog;
    private boolean resumed;
    private boolean permissionRequestInFlight;

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                permissionRequestInFlight = false;
                if (resumed) {
                    updateCamera();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        status = findViewById(R.id.camera_status);
        action = findViewById(R.id.camera_action);
        switchCamera = findViewById(R.id.camera_switch);
        faceRetry = findViewById(R.id.face_retry);
        faceStatus = findViewById(R.id.face_status);
        blendshapeView = findViewById(R.id.blendshape_view);
        meshOverlay = findViewById(R.id.face_mesh_overlay);
        meshOverlay.setPreview(findViewById(R.id.camera_preview));
        capturedImage = findViewById(R.id.captured_image);
        liveStage = findViewById(R.id.live_stage);
        cameraControls = findViewById(R.id.camera_controls);
        engagementStatus = findViewById(R.id.engagement_status);
        reset = findViewById(R.id.detection_reset);
        settings = findViewById(R.id.detection_settings);
        captures = new CaptureStore(getApplicationContext());
        calibration = new CalibrationStore(getApplicationContext());
        captured = captures.load();
        cameraController = new CameraController(findViewById(R.id.camera_preview), (message, retry, canSwitch, streaming) -> {
            status.setText(message);
            if (!streaming) {
                clearFaceEffects();
            }
            switchCamera.setEnabled(canSwitch && captured == null);
            settings.setEnabled(captured == null && cameraController.getSelectedCameraId() != null);
            action.setText(R.string.camera_retry);
            action.setVisibility(retry ? View.VISIBLE : View.GONE);
            action.setOnClickListener(v -> {
                cameraController.stop();
                updateCamera();
            });
        }, stream -> new EngagementAnalyzer(getApplicationContext(), stream,
                calibration.load(stream.cameraId), captures, new EngagementAnalyzer.Listener() {
                    @Override
                    public void onResult(FaceExpressionResult result, EngagementDecision decision) {
                        latestFaces = result;
                        latestFacesAt = SystemClock.elapsedRealtime();
                        boolean hasBlendshapes = result.faces.stream()
                                .anyMatch(face -> !face.coefficients.isEmpty());
                        blendshapeView.setVisibility(hasBlendshapes ? View.VISIBLE : View.GONE);
                        if (hasBlendshapes) {
                            blendshapeView.show(result);
                        }
                        meshOverlay.show(result);
                        meshOverlay.showVisitors(decision);
                        faceStatus.setText(result.faces.isEmpty()
                                ? getString(R.string.face_not_found)
                                : getString(R.string.face_result, result.faces.size(), result.inferenceMs));
                        engagementStatus.setText(DetectionStatusFormatter.format(MainActivity.this, decision));
                        faceRetry.setVisibility(View.GONE);
                    }

                    @Override
                    public void onCapture(CaptureResult pending) {
                        try {
                            captured = captures.accept(pending);
                            cameraController.stop();
                            clearFaceEffects();
                            showCaptured();
                        } catch (IOException error) {
                            pending.discardPending();
                            onError(true);
                        }
                    }

                    @Override
                    public void onError(boolean captureError) {
                        clearFaceEffects();
                        faceStatus.setText(captureError ? R.string.capture_error : R.string.face_error);
                        faceRetry.setVisibility(View.VISIBLE);
                    }
                }));
        reset.setOnClickListener(v -> resetDetection());
        settings.setOnClickListener(v -> openDetectionSettings());
        blendshapeView.setVisibility(View.GONE);
        switchCamera.setOnClickListener(v -> cameraController.switchCamera());
        faceRetry.setOnClickListener(v -> {
            cameraController.stop();
            updateCamera();
        });
        permissionRequestInFlight = savedInstanceState != null
                && savedInstanceState.getBoolean("permissionRequestInFlight");
        if (captured == null && !hasCameraPermission() && !hasRequestedPermission()) {
            requestCameraPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        updateCamera();
    }

    @Override
    protected void onPause() {
        resumed = false;
        switchCamera.setEnabled(false);
        cameraController.stop();
        if (settingsDialog != null) {
            settingsDialog.dismiss();
        }
        clearFaceEffects();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cameraController.release();
        capturedImage.setImageDrawable(null);
        if (captured != null) {
            captured.releaseImage();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("permissionRequestInFlight", permissionRequestInFlight);
        super.onSaveInstanceState(outState);
    }

    private void updateCamera() {
        if (!resumed || settingsDialog != null) {
            return;
        }
        if (captured != null) {
            showCaptured();
            return;
        }
        if (hasCameraPermission()) {
            cameraController.start();
            return;
        }
        cameraController.stop();
        switchCamera.setEnabled(false);
        clearFaceEffects();
        boolean settingsRequired = hasRequestedPermission() && !permissionRequestInFlight
                && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        status.setText(settingsRequired ? R.string.camera_permission_settings
                : R.string.camera_permission_required);
        action.setText(settingsRequired ? R.string.camera_settings : R.string.camera_allow);
        action.setVisibility(permissionRequestInFlight ? View.GONE : View.VISIBLE);
        action.setOnClickListener(v -> {
            if (settingsRequired) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } else {
                requestCameraPermission();
            }
        });
    }

    private void clearFaceEffects() {
        blendshapeView.clear();
        meshOverlay.clear();
        latestFaces = null;
        latestFacesAt = 0;
        if (captured == null) {
            faceStatus.setText(R.string.face_waiting);
            engagementStatus.setText(R.string.engagement_waiting);
        }
        faceRetry.setVisibility(View.GONE);
    }

    private void showCaptured() {
        liveStage.setVisibility(View.GONE);
        capturedImage.setImageBitmap(captured.image);
        capturedImage.setVisibility(View.VISIBLE);
        cameraControls.setVisibility(View.GONE);
        settings.setEnabled(false);
        reset.setVisibility(View.VISIBLE);
        faceStatus.setText(getString(R.string.capture_complete, captured.visitorCount));
        engagementStatus.setText(R.string.capture_reset_hint);
        status.setText(R.string.capture_camera_stopped);
    }

    private void resetDetection() {
        try {
            captures.reset();
        } catch (IOException error) {
            Toast.makeText(this, R.string.capture_reset_error, Toast.LENGTH_LONG).show();
            return;
        }
        capturedImage.setImageDrawable(null);
        capturedImage.setVisibility(View.GONE);
        if (captured != null) {
            captured.releaseImage();
            captured = null;
        }
        liveStage.setVisibility(View.VISIBLE);
        cameraControls.setVisibility(View.VISIBLE);
        reset.setVisibility(View.GONE);
        clearFaceEffects();
        updateCamera();
    }

    private void openDetectionSettings() {
        String cameraId = cameraController.getSelectedCameraId();
        if (cameraId == null || captured != null || settingsDialog != null) {
            return;
        }
        FaceObservation current = null;
        if (latestFaces != null && SystemClock.elapsedRealtime() - latestFacesAt <= 500) {
            for (FaceObservation face : FaceObservation.from(latestFaces)) {
                if (Float.isFinite(face.yawDegrees) && (current == null
                        || face.box.area() > current.box.area())) {
                    current = face;
                }
            }
        }
        cameraController.stop();
        clearFaceEffects();
        settingsDialog = DetectionSettingsDialog.show(this, cameraId, calibration, current, () -> {
            settingsDialog = null;
            updateCamera();
        });
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRequestedPermission() {
        return getPreferences(MODE_PRIVATE).getBoolean("cameraPermissionRequested", false);
    }

    private void requestCameraPermission() {
        permissionRequestInFlight = true;
        getPreferences(MODE_PRIVATE).edit().putBoolean("cameraPermissionRequested", true).apply();
        action.setVisibility(View.GONE);
        cameraPermission.launch(Manifest.permission.CAMERA);
    }
}
