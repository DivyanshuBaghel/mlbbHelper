package com.example.mlbbop;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        hideSystemUI();
        setupLauncher();
        handleIntent(getIntent());
    }

    public static final String ACTION_REQUEST_CAPTURE = "com.example.mlbbop.ACTION_REQUEST_CAPTURE";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && ACTION_REQUEST_CAPTURE.equals(intent.getAction())) {
            startMediaProjectionRequest();
        }
    }

    private static final int REQUEST_CODE_OVERLAY = 1001;
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1002;
    private Intent pendingGameIntent;

    private void setupLauncher() {
        // Setup Settings Button
        findViewById(R.id.fab_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        ImageButton launchBtn = findViewById(R.id.btn_launch_mlbb);
        String[] packages = {
                "com.mobile.legends", // Global
                "com.mobile.legends.vng", // Vietnam
                "com.mobile.legends.korea", // Korea
                "com.mobiin.gp" // India
        };

        PackageManager pm = getPackageManager();
        for (String pkg : packages) {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                // App found!
                try {
                    Drawable icon = pm.getApplicationIcon(pkg);
                    launchBtn.setImageDrawable(icon);
                    launchBtn.setScaleType(ImageButton.ScaleType.FIT_CENTER);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }

                launchBtn.setOnClickListener(v -> {
                    pendingGameIntent = intent;
                    if (checkOverlayPermission()) {
                        if (MlbbOverlayService.isProjectionActive) {
                            startOverlayService(0, null); // Ensure service is running/foreground
                            startActivity(intent);
                        } else {
                            startMediaProjectionRequest();
                        }
                    }
                });
                return;
            }
        }

        // Fallback: No app found
        launchBtn.setOnClickListener(v -> {
        });
    }

    private boolean checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY);
            return false;
        }
        return true;
    }

    private void startMediaProjectionRequest() {
        android.media.projection.MediaProjectionManager mediaProjectionManager = (android.media.projection.MediaProjectionManager) getSystemService(
                MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                // Check if we already have projection, otherwise ask
                if (MlbbOverlayService.isProjectionActive) {
                    startOverlayService(0, null);
                } else {
                    startMediaProjectionRequest();
                }
            } else {
                Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                startOverlayService(resultCode, data);
                if (pendingGameIntent != null) {
                    startActivity(pendingGameIntent);
                }
                // Return to previous task (Game/Overlay) if not launching
                if (pendingGameIntent == null) {
                    finish();
                }
            } else {
                Toast.makeText(this, "Screen capture permission required!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startOverlayService(int resultCode, Intent resultData) {
        Intent serviceIntent = new Intent(this, MlbbOverlayService.class);
        if (resultCode != 0 && resultData != null) {
            serviceIntent.putExtra("EXTRA_RESULT_CODE", resultCode);
            serviceIntent.putExtra("EXTRA_RESULT_DATA", resultData);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }
}