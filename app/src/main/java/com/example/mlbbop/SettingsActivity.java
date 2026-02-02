package com.example.mlbbop;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        hideSystemUI();
        setupUI();
    }

    private void setupUI() {
        com.google.android.material.textfield.TextInputEditText etApiKey = findViewById(R.id.et_api_key);
        android.widget.TextView tvStatus = findViewById(R.id.tv_settings_status);
        android.widget.Button btnSave = findViewById(R.id.btn_save_settings);

        // Load existing key (masked or just show status)
        String currentKey = com.example.mlbbop.managers.SettingsManager.getApiKey(this);
        if (!currentKey.isEmpty()) {
            etApiKey.setText(currentKey);
            tvStatus.setText("API Key is set.");
        } else {
            tvStatus.setText("No API Key found.");
        }

        btnSave.setOnClickListener(v -> {
            String newKey = etApiKey.getText().toString().trim();
            if (!newKey.isEmpty()) {
                com.example.mlbbop.managers.SettingsManager.saveApiKey(this, newKey);
                tvStatus.setText("API Key saved successfully!");
                android.widget.Toast.makeText(this, "Saved!", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, "API Key cannot be empty", android.widget.Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }
}
