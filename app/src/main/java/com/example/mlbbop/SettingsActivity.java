package com.example.mlbbop;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.mlbbop.managers.SettingsManager;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etApiKey1, etApiKey2, etApiKey3, etModelName;
    private RadioGroup rgApiKeys;
    private RadioButton rbKey1, rbKey2, rbKey3;
    private TextView tvStatus;
    private Button btnSave, btnHealthCheck;
    private Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        hideSystemUI();
        setupUI();
    }

    private void setupUI() {
        etApiKey1 = findViewById(R.id.et_api_key_1);
        etApiKey2 = findViewById(R.id.et_api_key_2);
        etApiKey3 = findViewById(R.id.et_api_key_3);
        etModelName = findViewById(R.id.et_model_name);
        rgApiKeys = findViewById(R.id.rg_api_keys);
        rbKey1 = findViewById(R.id.rb_key_1);
        rbKey2 = findViewById(R.id.rb_key_2);
        rbKey3 = findViewById(R.id.rb_key_3);
        tvStatus = findViewById(R.id.tv_settings_status);
        btnSave = findViewById(R.id.btn_save_settings);
        btnHealthCheck = findViewById(R.id.btn_check_health);

        loadSettings();

        btnSave.setOnClickListener(v -> saveSettings());
        btnHealthCheck.setOnClickListener(v -> checkApiHealth());
    }

    private void loadSettings() {
        etApiKey1.setText(SettingsManager.getApiKey(this, 1));
        etApiKey2.setText(SettingsManager.getApiKey(this, 2));
        etApiKey3.setText(SettingsManager.getApiKey(this, 3));
        etModelName.setText(SettingsManager.getModelName(this));

        int activeIndex = SettingsManager.getActiveKeyIndex(this);
        if (activeIndex == 2)
            rbKey2.setChecked(true);
        else if (activeIndex == 3)
            rbKey3.setChecked(true);
        else
            rbKey1.setChecked(true);
    }

    private void saveSettings() {
        String key1 = etApiKey1.getText().toString().trim();
        String key2 = etApiKey2.getText().toString().trim();
        String key3 = etApiKey3.getText().toString().trim();
        String modelName = etModelName.getText().toString().trim();

        SettingsManager.saveApiKey(this, 1, key1);
        SettingsManager.saveApiKey(this, 2, key2);
        SettingsManager.saveApiKey(this, 3, key3);

        if (!modelName.isEmpty()) {
            SettingsManager.saveModelName(this, modelName);
        }

        int activeIndex = 1;
        if (rbKey2.isChecked())
            activeIndex = 2;
        else if (rbKey3.isChecked())
            activeIndex = 3;
        SettingsManager.saveActiveKeyIndex(this, activeIndex);

        tvStatus.setText("Settings saved successfully!");
        Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();
    }

    private void checkApiHealth() {
        tvStatus.setText("Checking API Health...");

        // Determine which key is currently selected in UI (not saved yet necessarily)
        String activeKey = "";
        if (rbKey1.isChecked())
            activeKey = etApiKey1.getText().toString().trim();
        else if (rbKey2.isChecked())
            activeKey = etApiKey2.getText().toString().trim();
        else if (rbKey3.isChecked())
            activeKey = etApiKey3.getText().toString().trim();

        String modelName = etModelName.getText().toString().trim();
        if (modelName.isEmpty())
            modelName = "gemini-2.5-flash";

        if (activeKey.isEmpty()) {
            tvStatus.setText("Error: Selected API Key is empty.");
            return;
        }

        GenerativeModel gm = new GenerativeModel(modelName, activeKey);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        Content content = new Content.Builder()
                .addText("Ping")
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                runOnUiThread(() -> {
                    tvStatus.setText("Health Check Passed! Response received.");
                    Toast.makeText(SettingsActivity.this, "API is Healthy", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    tvStatus.setText("Health Check Failed: " + t.getMessage());
                    Toast.makeText(SettingsActivity.this, "API Error", Toast.LENGTH_LONG).show();
                });
            }
        }, executor);
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        windowInsetsController
                .setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }
}
