package com.example.mlbbop.managers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import java.nio.ByteBuffer;

public class ScreenCaptureManager {
    private static final String TAG = "ScreenCaptureManager";

    private final Context context;
    private final MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private boolean isProjectionActive = false;

    public interface ScreenshotCallback {
        void onScreenshotCaptured(Bitmap bitmap);
    }

    public ScreenCaptureManager(Context context) {
        this.context = context;
        this.projectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void setupVirtualDisplay() {
        if (mediaProjection == null)
            return;

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        if (imageReader == null) {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        }

        if (virtualDisplay == null) {
            Log.d(TAG, "Creating Persistent VirtualDisplay (Paused state)");
            // Start with null surface (Paused)
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    null, null, null);
        }
    }

    public void startProjection(int resultCode, Intent data) {
        if (projectionManager == null)
            return;

        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        isProjectionActive = true;

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                stopProjection();
            }
        }, new Handler(Looper.getMainLooper()));

        setupVirtualDisplay();
    }

    public void stopProjection() {
        isProjectionActive = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private final java.util.concurrent.ExecutorService backgroundExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor();

    public void captureScreenshot(ScreenshotCallback callback) {
        if (mediaProjection == null) {
            callback.onScreenshotCaptured(null);
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Ensure resources exist (in case of weird lifecycle kill)
                setupVirtualDisplay();

                if (virtualDisplay == null || imageReader == null) {
                    Log.e(TAG, "Failed to setup resources");
                    new Handler(Looper.getMainLooper()).post(() -> callback.onScreenshotCaptured(null));
                    return;
                }

                // 1. RESUME: Attach surface to start capturing
                Log.d(TAG, "Resuming VirtualDisplay (Attach Surface)");
                virtualDisplay.setSurface(imageReader.getSurface());

                // 2. Wait for frame
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {
                }

                // 3. Acquire Image
                Image image = null;
                for (int i = 0; i < 3; i++) {
                    image = imageReader.acquireLatestImage();
                    if (image != null)
                        break;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }

                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int width = image.getWidth();
                    int height = image.getHeight();
                    int rowPadding = rowStride - pixelStride * width;

                    Bitmap bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    Bitmap finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                    image.close();

                    new Handler(Looper.getMainLooper()).post(() -> callback.onScreenshotCaptured(finalBitmap));
                } else {
                    Log.e(TAG, "Image is null after resume");
                    new Handler(Looper.getMainLooper()).post(() -> callback.onScreenshotCaptured(null));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error capturing screenshot", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onScreenshotCaptured(null));
            } finally {
                // 4. PAUSE: Detach surface to stop capturing
                Log.d(TAG, "Pausing VirtualDisplay (Detach Surface)");
                if (virtualDisplay != null) {
                    virtualDisplay.setSurface(null);
                }
            }
        });
    }

    public boolean isProjectionActive() {
        return isProjectionActive;
    }
}
