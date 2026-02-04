package com.example.mlbbop.managers;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.mlbbop.BuildConfig;
import com.example.mlbbop.MlbbOverlayService;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiHelper {
    private static final String TAG = "GeminiHelper";
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;

    private GenerativeModelFutures model;
    private ChatFutures chatSession;
    private Executor executor = Executors.newSingleThreadExecutor();

    private static final String SYSTEM_INSTRUCTION = "Act as a High-Level MLBB Strategic Analyst.\n" +
            "\n" +
            "1. Session & Memory Protocol: > * Fresh Start: Every new image upload triggers a total memory wipe of previous matches.\n"
            +
            "    Active Match: Maintain context of the current image and my hero until the next image is sent.\n" +
            "\n" +
            "2. Step-by-Step Execution:\n" +
            "    Step A (Detection): Upon image upload, list the 10 hero names. CRITICAL: Also provide these names as options so I can select one. Format: `[OPTIONS: Hero1 | Hero2 | ...]`\n"
            +
            "    Step B (The Menu): After I identify my hero (or if I just ask for general help), provide actionable next steps as \"Options\" (e.g., Build | Tactics | Win Condition).\n"
            +
            "    CRITICAL: You must provide these options in a strict format at the end of your response: `[OPTIONS: Option 1 | Option 2 | Option 3]`.\n"
            +
            "    Example: `...analysis complete. [OPTIONS: Build Info | Tactics | Win Condition]`\n" +
            "    If no specific options are relevant, do not output the tag.\n" +
            "\n" +
            "3. Response Style: > * Keep analysis hard-hitting, concise, and data-driven.\n" +
            "    If I click an option, I will send you that exact text. Respond specifically to it.";

    public GeminiHelper(android.content.Context context) {
        String key = SettingsManager.getActiveApiKey(context);
        String safeKey = (key != null && !key.isEmpty()) ? key : API_KEY;

        if (safeKey == null || safeKey.isEmpty()) {
            Log.e(TAG, "API Key is missing! Please set it in Settings.");
        }

        String modelName = SettingsManager.getModelName(context);
        GenerativeModel gm = new GenerativeModel(modelName, safeKey != null ? safeKey : "");
        model = GenerativeModelFutures.from(gm);
    }

    public interface GeminiCallback {
        void onSuccess(String response);

        void onError(Throwable t);
    }

    public void startNewSession(Bitmap screenshot, GeminiCallback callback) {
        // Fresh start logic managed by creating new chat or just sending fresh prompt
        // with image
        chatSession = model.startChat();

        Content content = new Content.Builder()
                .addText(SYSTEM_INSTRUCTION + "\n\n[Attached Image for analysis]")
                .addImage(screenshot)
                .build();

        ListenableFuture<GenerateContentResponse> response = chatSession.sendMessage(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                callback.onSuccess(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini Error", t);
                callback.onError(t);
            }
        }, executor);
    }

    public void sendMessage(String message, GeminiCallback callback) {
        if (chatSession == null) {
            callback.onError(new IllegalStateException("Session not started"));
            return;
        }

        Content content = new Content.Builder()
                .addText(message)
                .build();

        ListenableFuture<GenerateContentResponse> response = chatSession.sendMessage(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                callback.onSuccess(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini Error", t);
                callback.onError(t);
            }
        }, executor);
    }
}
