package com.top.engagement_detector.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.top.engagement_detector.R;
import com.top.engagement_detector.engagement.CalibrationStore;
import com.top.engagement_detector.engagement.EngagementConfig;
import com.top.engagement_detector.vision.FaceObservation;

import java.util.Locale;

/** Small installer-facing calibration form; editing pauses detection via its activity host. */
public final class DetectionSettingsDialog {
    private DetectionSettingsDialog() { }

    public static AlertDialog show(Context context, String cameraId, CalibrationStore store,
                                   FaceObservation currentFace, Runnable onDismiss) {
        EngagementConfig config = store.load(cameraId);
        LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        TextView help = new TextView(context);
        help.setText(R.string.calibration_help);
        fields.addView(help);
        EditText area = field(context, fields, R.string.calibration_area, config.minAreaRatio * 100);
        EditText yaw = field(context, fields, R.string.calibration_yaw, config.maxYawDegrees);
        EditText neutral = field(context, fields, R.string.calibration_neutral, config.neutralYawDegrees);
        EditText dwell = field(context, fields, R.string.calibration_dwell, config.dwellMs / 1000f);
        EditText gap = field(context, fields, R.string.calibration_gap, config.maxGapMs);
        EditText inset = field(context, fields, R.string.calibration_inset, config.horizontalInset * 100);
        Button useCurrent = new Button(context);
        useCurrent.setText(R.string.calibration_use_current);
        useCurrent.setEnabled(currentFace != null && Float.isFinite(currentFace.yawDegrees));
        useCurrent.setOnClickListener(v -> {
            set(area, Math.max(0.01f, Math.min(50f, currentFace.box.area() * 80)));
            set(neutral, Math.max(-90f, Math.min(90f, currentFace.yawDegrees)));
        });
        fields.addView(useCurrent);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.calibration_title, cameraId))
                .setView(scroll).setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.calibration_defaults, null)
                .setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnDismissListener(ignored -> onDismiss.run());
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                EngagementConfig defaults = EngagementConfig.defaults();
                set(area, defaults.minAreaRatio * 100);
                set(yaw, defaults.maxYawDegrees);
                set(neutral, defaults.neutralYawDegrees);
                set(dwell, defaults.dwellMs / 1000f);
                set(gap, defaults.maxGapMs);
                set(inset, defaults.horizontalInset * 100);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    EngagementConfig updated = new EngagementConfig(number(area) / 100,
                            number(yaw), number(neutral), number(inset) / 100,
                            Math.round(number(dwell) * 1000), Math.round(number(gap)));
                    store.save(cameraId, updated);
                    dialog.dismiss();
                } catch (IllegalArgumentException error) {
                    Toast.makeText(context, R.string.calibration_invalid, Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
        return dialog;
    }

    private static EditText field(Context context, LinearLayout parent, int label, float value) {
        TextView title = new TextView(context);
        title.setText(label);
        parent.addView(title);
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setContentDescription(context.getString(label));
        set(input, value);
        parent.addView(input);
        return input;
    }

    private static void set(EditText field, float value) {
        field.setText(String.format(Locale.ROOT, "%.2f", value));
    }

    private static float number(EditText field) {
        float value = Float.parseFloat(field.getText().toString().trim().replace(',', '.'));
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite setting");
        }
        return value;
    }
}
