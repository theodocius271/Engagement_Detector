package com.top.engagement_detector.capture;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Keeps one private capture until Reset, including across activity/process recreation. */
public final class CaptureStore {
    private final File directory;
    private final File capturedFile;
    private final SharedPreferences preferences;

    public CaptureStore(Context context) {
        directory = new File(context.getFilesDir(), "engagement");
        capturedFile = new File(directory, "captured.png");
        preferences = context.getSharedPreferences("engagement_capture", Context.MODE_PRIVATE);
    }

    /** Worker: ownership of the finished image transfers to the result only on success. */
    public CaptureResult prepare(Bitmap image, int visitorCount, long timestamp) throws IOException {
        File pending = null;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create capture directory");
            }
            pending = File.createTempFile("pending-", ".png", directory);
            try (FileOutputStream output = new FileOutputStream(pending)) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IOException("PNG encoding failed");
                }
                output.getFD().sync();
            }
            return new CaptureResult(image, visitorCount, timestamp, pending);
        } catch (IOException | RuntimeException error) {
            if (pending != null) {
                pending.delete();
            }
            throw error;
        }
    }

    /** Main thread: only accept after checking that the source stream is still active. */
    public CaptureResult accept(CaptureResult pending) throws IOException {
        if (capturedFile.exists() || !pending.file.renameTo(capturedFile)) {
            throw new IOException("Cannot commit capture");
        }
        preferences.edit().putInt("count", pending.visitorCount)
                .putLong("timestamp", pending.frameTimestampMs).apply();
        return new CaptureResult(pending.image, pending.visitorCount, pending.frameTimestampMs, capturedFile);
    }

    public CaptureResult load() {
        // Called before opening a stream. Remove unfinished captures left by a killed process.
        File[] abandoned = directory.listFiles((dir, name) -> name.startsWith("pending-")
                && name.endsWith(".png"));
        if (abandoned != null) {
            for (File file : abandoned) {
                file.delete();
            }
        }
        if (!capturedFile.isFile()) {
            return null;
        }
        // Restore the one finished figure image; raw camera frames are never persisted.
        Bitmap bitmap = BitmapFactory.decodeFile(capturedFile.getAbsolutePath());
        if (bitmap == null) {
            Log.e("CaptureStore", "Saved capture cannot be decoded");
            capturedFile.delete();
            return null;
        }
        return new CaptureResult(bitmap, preferences.getInt("count", 1),
                preferences.getLong("timestamp", 0), capturedFile);
    }

    public void reset() throws IOException {
        if (capturedFile.exists() && !capturedFile.delete()) {
            throw new IOException("Cannot delete capture");
        }
        preferences.edit().clear().apply();
    }
}
