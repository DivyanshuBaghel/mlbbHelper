package com.example.mlbbop.managers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.mlbbop.R;

public class OverlayWindowManager {
    private static final String TAG = "OverlayWindowManager";

    private final Context context;
    private final WindowManager windowManager;
    private final OverlayActionListener listener;

    private View overlayHandle;
    private View overlayExpanded;
    private boolean isExpanded = false;
    private WindowManager.LayoutParams expandedParams;
    private String cachedChatHistory = "";

    // References to active UI elements in expanded view
    private ImageView resultImageView;
    private View progressContainer;
    private android.widget.ProgressBar pbUpload;
    private android.widget.ProgressBar pbReceive;

    public OverlayWindowManager(Context context, WindowManager windowManager, OverlayActionListener listener) {
        // Wrap context to ensure Material 3 theme application
        this.context = new android.view.ContextThemeWrapper(context, R.style.Theme_MlbbOp);
        this.windowManager = windowManager;
        this.listener = listener;
    }

    public void addHandleView() {
        if (overlayHandle != null)
            return; // Already added

        Log.d(TAG, "addHandleView: Adding handle view");
        overlayHandle = LayoutInflater.from(context).inflate(R.layout.overlay_handle, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        params.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        params.x = 0;
        params.y = 0;

        overlayHandle.setOnTouchListener(new View.OnTouchListener() {
            private float initialTouchX;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d(TAG, "Handle onTouch: ACTION_DOWN");
                        initialTouchX = event.getRawX();
                        return true;
                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "Handle onTouch: ACTION_UP");
                        float finalTouchX = event.getRawX();
                        float diffX = initialTouchX - finalTouchX; // Positive if dragged left (assuming right edge)

                        Log.d(TAG, "Touch diffX: " + diffX);

                        if (diffX > 30) {
                            // Swipe Left (Pull out)
                            Log.d(TAG, "Action: Swipe Triggered");
                            showExpandedView();
                        } else if (Math.abs(diffX) < 25) {
                            // Click (Tap with slight movement tolerance)
                            Log.d(TAG, "Action: Click Triggered");
                            showExpandedView();
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(overlayHandle, params);
        } catch (Exception e) {
            Log.e(TAG, "Error adding handle view", e);
        }
    }

    public void showExpandedView() {
        if (isExpanded)
            return;

        if (overlayExpanded == null) {
            overlayExpanded = LayoutInflater.from(context).inflate(R.layout.overlay_expanded, null);
            overlayExpanded.setOnClickListener(v -> hideExpandedView());

            View mainContainer = overlayExpanded.findViewById(R.id.overlay_main_container);
            if (mainContainer != null) {
                mainContainer.setOnClickListener(v -> {
                    /* Consume click */ });
            }

            // Navigation
            overlayExpanded.findViewById(R.id.nav_home)
                    .setOnClickListener(v -> switchContent(R.layout.view_overlay_home));
            overlayExpanded.findViewById(R.id.nav_data)
                    .setOnClickListener(v -> switchContent(R.layout.view_overlay_data));

            overlayExpanded.findViewById(R.id.nav_settings)
                    .setOnClickListener(v -> switchContent(R.layout.view_overlay_settings));

            View navClose = overlayExpanded.findViewById(R.id.nav_close);
            if (navClose != null)
                navClose.setOnClickListener(v -> hideExpandedView());

            switchContent(R.layout.view_overlay_home);
        }

        expandedParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            expandedParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        expandedParams.gravity = Gravity.RIGHT;

        try {
            windowManager.addView(overlayExpanded, expandedParams);
            if (overlayHandle != null && overlayHandle.isAttachedToWindow()) {
                overlayHandle.setVisibility(View.GONE);
            }
            isExpanded = true;
        } catch (Exception e) {
            Log.e(TAG, "Error showing expanded view", e);
        }
    }

    public void hideExpandedView() {
        if (!isExpanded)
            return;

        if (overlayExpanded != null && overlayExpanded.isAttachedToWindow()) {
            windowManager.removeView(overlayExpanded);
        }

        if (overlayHandle != null) {
            overlayHandle.setVisibility(View.VISIBLE);
        }

        isExpanded = false;
    }

    private Bitmap cachedBitmap;

    public void switchContent(int layoutId) {
        if (overlayExpanded == null)
            return;

        FrameLayout contentContainer = overlayExpanded.findViewById(R.id.content_container);
        if (contentContainer != null) {
            contentContainer.removeAllViews();
            View contentView = LayoutInflater.from(context).inflate(layoutId, contentContainer, true);

            if (layoutId == R.layout.view_overlay_home) {
                View btnCapture = contentView.findViewById(R.id.btn_capture);
                View btnSave = contentView.findViewById(R.id.btn_next);
                View btnSend = contentView.findViewById(R.id.btn_send);
                resultImageView = contentView.findViewById(R.id.img_result);
                progressContainer = contentView.findViewById(R.id.progress_container);
                pbUpload = contentView.findViewById(R.id.pb_upload);
                pbReceive = contentView.findViewById(R.id.pb_receive);

                // Restore cached bitmap if available
                if (cachedBitmap != null && resultImageView != null) {
                    resultImageView.setImageBitmap(cachedBitmap);
                }

                if (btnCapture != null)
                    btnCapture.setOnClickListener(v -> listener.onCaptureRequest());
                if (btnSave != null) {
                    btnSave.setOnClickListener(v -> {
                        Log.d(TAG, "Save button clicked in UI");
                        listener.onSaveRequest();
                    });
                }
                if (btnSend != null) {
                    btnSend.setOnClickListener(v -> {
                        Log.d(TAG, "Send button clicked in UI");
                        listener.onSendRequest();
                    });
                    btnSend.setEnabled(isSendEnabled); // Restore state
                    Log.d(TAG, "Restored Send button state: " + isSendEnabled);
                }
            } else if (layoutId == R.layout.view_overlay_data) {
                resultImageView = null;
                progressContainer = null;
                setupDataView(contentView);
            } else if (layoutId == R.layout.view_overlay_settings) {
                resultImageView = null;
                progressContainer = null;
                setupSettingsView(contentView);
            } else {
                resultImageView = null; // clear reference when not in home view
                progressContainer = null;
            }
        }
    }

    private void setupSettingsView(View view) {
        com.google.android.material.textfield.TextInputEditText etApiKey1 = view.findViewById(R.id.et_api_key_1);
        com.google.android.material.textfield.TextInputEditText etApiKey2 = view.findViewById(R.id.et_api_key_2);
        com.google.android.material.textfield.TextInputEditText etApiKey3 = view.findViewById(R.id.et_api_key_3);
        com.google.android.material.textfield.TextInputEditText etModelName = view.findViewById(R.id.et_model_name);
        android.widget.RadioGroup rgApiKeys = view.findViewById(R.id.rg_api_keys);
        android.widget.RadioButton rbKey1 = view.findViewById(R.id.rb_key_1);
        android.widget.RadioButton rbKey2 = view.findViewById(R.id.rb_key_2);
        android.widget.RadioButton rbKey3 = view.findViewById(R.id.rb_key_3);
        android.widget.TextView tvStatus = view.findViewById(R.id.tv_settings_status);
        View btnSave = view.findViewById(R.id.btn_save_settings);
        View btnHealthCheck = view.findViewById(R.id.btn_check_health);

        etApiKey1.setText(SettingsManager.getApiKey(context, 1));
        etApiKey2.setText(SettingsManager.getApiKey(context, 2));
        etApiKey3.setText(SettingsManager.getApiKey(context, 3));
        etModelName.setText(SettingsManager.getModelName(context));

        int activeIndex = SettingsManager.getActiveKeyIndex(context);
        if (activeIndex == 2)
            rbKey2.setChecked(true);
        else if (activeIndex == 3)
            rbKey3.setChecked(true);
        else
            rbKey1.setChecked(true);

        // Make edit texts focusable in overlay
        View.OnTouchListener focusListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                setFocusable(true);
            }
            return false;
        };
        etApiKey1.setOnTouchListener(focusListener);
        etApiKey2.setOnTouchListener(focusListener);
        etApiKey3.setOnTouchListener(focusListener);
        etModelName.setOnTouchListener(focusListener);

        btnSave.setOnClickListener(v -> {
            setFocusable(false); // Hide keyboard
            String key1 = etApiKey1.getText().toString().trim();
            String key2 = etApiKey2.getText().toString().trim();
            String key3 = etApiKey3.getText().toString().trim();
            String model = etModelName.getText().toString().trim();

            SettingsManager.saveApiKey(context, 1, key1);
            SettingsManager.saveApiKey(context, 2, key2);
            SettingsManager.saveApiKey(context, 3, key3);

            if (!model.isEmpty()) {
                SettingsManager.saveModelName(context, model);
            }

            int newIndex = 1;
            if (rbKey2.isChecked())
                newIndex = 2;
            else if (rbKey3.isChecked())
                newIndex = 3;
            SettingsManager.saveActiveKeyIndex(context, newIndex);

            tvStatus.setText("Settings saved!");
            Toast.makeText(context, "Settings Saved!", Toast.LENGTH_SHORT).show();
        });

        btnHealthCheck.setOnClickListener(v -> {
            setFocusable(false);
            tvStatus.setText("Checking API Health...");

            String activeKey = "";
            if (rbKey1.isChecked())
                activeKey = etApiKey1.getText().toString().trim();
            else if (rbKey2.isChecked())
                activeKey = etApiKey2.getText().toString().trim();
            else if (rbKey3.isChecked())
                activeKey = etApiKey3.getText().toString().trim();

            String model = etModelName.getText().toString().trim();
            if (model.isEmpty())
                model = "gemini-2.5-flash";

            if (activeKey.isEmpty()) {
                tvStatus.setText("Error: Key is empty.");
                return;
            }

            com.google.ai.client.generativeai.GenerativeModel gm = new com.google.ai.client.generativeai.GenerativeModel(
                    model, activeKey);
            com.google.ai.client.generativeai.java.GenerativeModelFutures modelFutures = com.google.ai.client.generativeai.java.GenerativeModelFutures
                    .from(gm);

            com.google.ai.client.generativeai.type.Content content = new com.google.ai.client.generativeai.type.Content.Builder()
                    .addText("Ping")
                    .build();

            com.google.common.util.concurrent.ListenableFuture<com.google.ai.client.generativeai.type.GenerateContentResponse> response = modelFutures
                    .generateContent(content);

            com.google.common.util.concurrent.Futures.addCallback(response,
                    new com.google.common.util.concurrent.FutureCallback<com.google.ai.client.generativeai.type.GenerateContentResponse>() {
                        @Override
                        public void onSuccess(com.google.ai.client.generativeai.type.GenerateContentResponse result) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                tvStatus.setText("Health Check Passed!");
                                Toast.makeText(context, "API is Healthy", Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                tvStatus.setText("Failed: " + t.getMessage());
                                Toast.makeText(context, "API Error", Toast.LENGTH_LONG).show();
                            });
                        }
                    }, java.util.concurrent.Executors.newSingleThreadExecutor());
        });
    }

    public void setFocusable(boolean focusable) {
        if (overlayExpanded != null && expandedParams != null && isExpanded) {
            if (focusable) {
                expandedParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            } else {
                expandedParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            }
            windowManager.updateViewLayout(overlayExpanded, expandedParams);
        }
    }

    private boolean isSendEnabled = false;

    public void setSendEnabled(boolean enabled) {
        this.isSendEnabled = enabled;
        Log.d(TAG, "setSendEnabled: " + enabled + ", overlayExpanded=" + overlayExpanded);
        if (overlayExpanded != null) {
            View btnSend = overlayExpanded.findViewById(R.id.btn_send);
            Log.d(TAG, "setSendEnabled: btnSend found=" + (btnSend != null));
            if (btnSend != null) {
                btnSend.setEnabled(enabled);
            }
        }
    }

    private void setupDataView(View view) {
        android.widget.EditText etInput = view.findViewById(R.id.et_chat_input);
        View btnSend = view.findViewById(R.id.btn_chat_send);
        View btnBuild = view.findViewById(R.id.btn_build);
        View btnTactics = view.findViewById(R.id.btn_tactics);
        View btnWin = view.findViewById(R.id.btn_win_condition);

        android.widget.TextView tvChat = view.findViewById(R.id.tv_chat_response);
        if (tvChat != null && !cachedChatHistory.isEmpty()) {
            tvChat.setText(TextWithImageHelper.getSpannedText(context, cachedChatHistory));
        }

        if (btnSend != null && etInput != null) {
            etInput.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    setFocusable(true);
                }
                return false;
            });

            btnSend.setOnClickListener(v -> {
                String text = etInput.getText().toString().trim();
                setFocusable(false); // Hide keyboard
                if (!text.isEmpty()) {
                    listener.onChatRequest(text);
                    etInput.setText("");
                    appendChatMessage("Me: " + text); // Optimistic update
                }
            });
        }

        View.OnClickListener quickAction = v -> listener
                .onChatRequest(((android.widget.Button) v).getText().toString());
        if (btnBuild != null)
            btnBuild.setOnClickListener(quickAction);
        if (btnTactics != null)
            btnTactics.setOnClickListener(quickAction);
        if (btnWin != null)
            btnWin.setOnClickListener(quickAction);

    }

    public void appendChatMessage(String message) {
        if (overlayExpanded == null)
            return;
        android.widget.TextView tvChat = overlayExpanded.findViewById(R.id.tv_chat_response);
        if (tvChat != null) {
            String spacer = "";
            // Avoid double newlines if cached
            if (!cachedChatHistory.isEmpty() && !message.isEmpty()) {
                spacer = "\n\n";
            }
            String newValue = cachedChatHistory + spacer + message;
            tvChat.setText(TextWithImageHelper.getSpannedText(context, newValue));
            cachedChatHistory = newValue;
            // Scroll to bottom (simple implementation)
            View scrollView = (View) tvChat.getParent();
            if (scrollView instanceof android.widget.ScrollView) {
                ((android.widget.ScrollView) scrollView).fullScroll(View.FOCUS_DOWN);
            }
        }
    }

    public void resetDataCache() {
        cachedChatHistory = "";

    }

    public void updateResultImage(Bitmap bitmap) {
        this.cachedBitmap = bitmap; // Cache it
        if (resultImageView != null && bitmap != null) {
            resultImageView.setImageBitmap(bitmap);
            resultImageView.setVisibility(View.VISIBLE);
        }
    }

    public void showLoading() {
        if (progressContainer != null) {
            progressContainer.setVisibility(View.VISIBLE);
        }
        // Stage 1: Uploading
        if (pbUpload != null) {
            pbUpload.setIndeterminate(true);
            pbUpload.setVisibility(View.VISIBLE);
        }
        if (pbReceive != null) {
            // Wait for upload to finish
            pbReceive.setIndeterminate(false);
            pbReceive.setProgress(0);
        }
    }

    public void onUploadComplete() {
        // Stage 2: Receiving
        if (pbUpload != null) {
            pbUpload.setIndeterminate(false);
            pbUpload.setProgress(100);
        }
        if (pbReceive != null) {
            pbReceive.setIndeterminate(true);
        }
    }

    public void hideLoading() {
        if (progressContainer != null) {
            progressContainer.setVisibility(View.GONE);
        }
    }

    public void destroy() {
        if (overlayHandle != null && overlayHandle.isAttachedToWindow()) {
            windowManager.removeView(overlayHandle);
        }
        if (overlayExpanded != null && overlayExpanded.isAttachedToWindow()) {
            windowManager.removeView(overlayExpanded);
        }
    }

    public void setInvisible() {
        if (overlayExpanded != null)
            overlayExpanded.setVisibility(View.GONE);
        if (overlayHandle != null)
            overlayHandle.setVisibility(View.GONE);
    }

    public void setVisible() {
        if (overlayExpanded != null && isExpanded)
            overlayExpanded.setVisibility(View.VISIBLE);
        else if (overlayHandle != null)
            overlayHandle.setVisibility(View.VISIBLE); // Only show handle if not expanded really, but simplifies
    }
}
