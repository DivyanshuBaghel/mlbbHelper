package com.example.mlbbop;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.mlbbop.managers.OverlayActionListener;
import com.example.mlbbop.managers.OverlayWindowManager;
import com.example.mlbbop.managers.ScreenCaptureManager;
import com.example.mlbbop.managers.GeminiHelper;

public class MlbbOverlayService extends Service implements OverlayActionListener {
    private static final String TAG = "MlbbOverlayService";
    private static final String CHANNEL_ID = "MlbbOverlayChannel";
    public static boolean isProjectionActive = false;

    private OverlayWindowManager overlayManager;
    private ScreenCaptureManager captureManager;
    private GeminiHelper geminiHelper;
    private Bitmap currentScreenshot;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayManager = new OverlayWindowManager(this, wm, this);
        captureManager = new ScreenCaptureManager(this);
        geminiHelper = new GeminiHelper(getApplicationContext());

        overlayManager.addHandleView();

        overlayManager.addHandleView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("EXTRA_RESULT_CODE", 0);
            Intent resultData = intent.getParcelableExtra("EXTRA_RESULT_DATA");

            if (resultCode != 0 && resultData != null) {
                captureManager.startProjection(resultCode, resultData);
                isProjectionActive = true;
                // Ensure handle is visible if it was missing
                overlayManager.addHandleView(); // Safe to call multiple times as it checks null
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    // --- OverlayActionListener Implementation ---

    @Override
    public void onCaptureRequest() {
        if (!captureManager.isProjectionActive()) {
            Toast.makeText(this, "Screen recording permission required", Toast.LENGTH_SHORT).show();
            hideOverlayTemporary();
            requestCapturePermission();
            return;
        }

        Toast.makeText(this, "Capturing...", Toast.LENGTH_SHORT).show();

        // Hide UI
        overlayManager.setInvisible();

        // Delay to ensure UI is gone from frame
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            captureManager.captureScreenshot(bitmap -> {
                currentScreenshot = bitmap;
                // Restore UI
                overlayManager.setVisible(); // Restore state
                overlayManager.updateResultImage(bitmap);
                overlayManager.setSendEnabled(false); // Disable send until saved or validated? Plan said disable until
                                                      // saved.
            });
        }, 300);
    }

    @Override
    public void onSaveRequest() {
        Log.d(TAG, "onSaveRequest called in Service");
        if (currentScreenshot == null) {
            Toast.makeText(this, "No screenshot to save!", Toast.LENGTH_SHORT).show();
            return;
        }
        saveScreenshotToAppFolder();
        overlayManager.setSendEnabled(true);
    }

    @Override
    public void onSendRequest() {
        if (currentScreenshot == null)
            return;

        overlayManager.resetDataCache();
        overlayManager.showLoading();
        // Fake progress
        // Fake progress for upload visualization
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            overlayManager.onUploadComplete(); // Visually switch to "Receiving" phase

            geminiHelper.startNewSession(currentScreenshot, new GeminiHelper.GeminiCallback() {
                @Override
                public void onSuccess(String response) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        overlayManager.hideLoading();
                        overlayManager.switchContent(R.layout.view_overlay_data);
                        overlayManager.appendChatMessage("Gemini: " + response);
                        // Parse response to find hero list if possible, or just dump it.
                        // "Step A (Detection): Upon image upload, list the 10 hero names."
                        // We can simple-mindedly set the first line or so to the hero list text view.
                        // For now, let's just put it all in chat or try to split.
                        // Ideally, we'd parse.

                    });
                }

                @Override
                public void onError(Throwable t) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        overlayManager.hideLoading();
                        Toast.makeText(MlbbOverlayService.this, "Gemini Error: " + t.getMessage(), Toast.LENGTH_LONG)
                                .show();
                    });
                }
            });
        }, 1000); // 1s delay to show loading bar starting (or we can animate it if we had access
                  // to the view object's progress property)
    }

    @Override
    public void onChatRequest(String message) {
        overlayManager.appendChatMessage("Me: " + message); // Should be handled by UI optimistically ideally, but
                                                            // service redundancy is fine.
        // Actually UI handles optimistic update.

        geminiHelper.sendMessage(message, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    overlayManager.appendChatMessage("Gemini: " + response);
                });
            }

            @Override
            public void onError(Throwable t) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    overlayManager.appendChatMessage("System: Error - " + t.getMessage());
                });
            }
        });
    }

    @Override
    public void onHomeRequest() {
        // Handled internally by OverlayWindowManager usually, or we can add logic here
        // if needed
    }

    @Override
    public void onSettingsRequest() {
    }

    @Override
    public void onCloseRequest() {
        overlayManager.hideExpandedView();
    }

    // --- Helpers ---

    private void extractDataFromScreenshot() {
        Toast.makeText(this, "Extraction logic reverted.", Toast.LENGTH_SHORT).show();
    }

    private void requestCapturePermission() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("com.example.mlbbop.ACTION_REQUEST_CAPTURE");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void hideOverlayTemporary() {
        overlayManager.setInvisible();
    }

    private void saveScreenshotToAppFolder() {
        if (currentScreenshot == null)
            return;

        new Thread(() -> {
            java.io.File directory = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            if (directory != null) {
                if (!directory.exists())
                    directory.mkdirs();
                java.io.File file = new java.io.File(directory, "capture_result.png");

                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    currentScreenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
                    new Handler(Looper.getMainLooper()).post(
                            () -> Toast.makeText(this, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
                    Log.d(TAG, "Screenshot saved to: " + file.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Error saving screenshot", e);
                    new Handler(Looper.getMainLooper())
                            .post(() -> Toast.makeText(this, "Failed to save screenshot", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MLBB Overlay Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null)
                manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MLBB Overlay Running")
                .setContentText("Overlay is active.")
                .setSmallIcon(R.drawable.ic_mlbb_launcher)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int foregroundServiceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                foregroundServiceType |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            }
            startForeground(1, notification, foregroundServiceType);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isProjectionActive = false;
        if (overlayManager != null)
            overlayManager.destroy();
        if (captureManager != null)
            captureManager.stopProjection();
    }
}
