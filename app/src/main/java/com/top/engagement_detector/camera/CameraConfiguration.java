package com.top.engagement_detector.camera;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.List;

/** Camera discovery and supported stream settings; no device or UI lifecycle ownership. */
final class CameraConfiguration {
    final String id;
    final boolean frontFacing;
    final int sensorOrientation;
    final Size previewSize;
    final Size analysisSize;
    final Integer autofocusMode;

    private CameraConfiguration(String id, CameraCharacteristics characteristics, Size size,
                                Size analysisSize) {
        this.id = id;
        frontFacing = Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT)
                .equals(characteristics.get(CameraCharacteristics.LENS_FACING));
        Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        sensorOrientation = orientation == null ? 0 : orientation;
        previewSize = size;
        this.analysisSize = analysisSize;
        autofocusMode = chooseAutofocus(characteristics.get(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES));

    }

    static List<CameraConfiguration> findAll(CameraManager manager) throws CameraAccessException {
        List<CameraConfiguration> cameras = new ArrayList<>();
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics;
            try {
                characteristics = manager.getCameraCharacteristics(id);
            } catch (IllegalArgumentException e) {
                // An external camera may disappear between enumeration and this query.
                continue;
            }
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map == null ? null : map.getOutputSizes(SurfaceHolder.class);
            if (sizes == null || sizes.length == 0) {
                continue;
            }
            Size[] analysisSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            List<Size> sharedSizes = new ArrayList<>();
            if (analysisSizes != null) {
                for (Size analysis : analysisSizes) {
                    for (Size preview : sizes) {
                        if (analysis.equals(preview)) {
                            sharedSizes.add(analysis);
                            break;
                        }
                    }
                }
            }
            // Equal stream sizes avoid differing aspect-ratio crops between mesh and preview.
            Size shared = sharedSizes.isEmpty() ? null
                    : chooseSize(sharedSizes.toArray(new Size[0]), 1280, 960);
            cameras.add(new CameraConfiguration(id, characteristics,
                    shared == null ? chooseSize(sizes, 1280, 720) : shared, shared));
        }
        return cameras;
    }

    private static Size chooseSize(Size[] sizes, int targetWidth, int targetHeight) {
        // Select near the requested target; never request an unreported resolution.
        Size best = sizes[0];
        double bestScore = Double.MAX_VALUE;
        for (Size size : sizes) {
            int width = Math.max(size.getWidth(), size.getHeight());
            int height = Math.min(size.getWidth(), size.getHeight());
            double score = Math.abs(Math.log((double) width / height
                    / ((double) targetWidth / targetHeight))) * 4
                    + Math.abs(Math.log((double) width * height / (targetWidth * targetHeight)));
            if (score < bestScore) {
                bestScore = score;
                best = size;
            }
        }
        return best;
    }

    private static Integer chooseAutofocus(int[] modes) {
        if (modes != null) {
            for (int mode : modes) {
                if (mode == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                    return mode;
                }
            }
            for (int mode : modes) {
                if (mode == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                    return mode;
                }
            }
        }
        // Keep TEMPLATE_PREVIEW's defaults on fixed-focus and other cameras.
        return null;
    }
}
