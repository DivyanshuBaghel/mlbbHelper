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
            "    Step A (Detection): Upon image upload, list the 10 hero names.\n" +
            "    Step B (The Menu): After I identify my hero, provide three \"Two-Word\" quick-action questions (Build, Tactics, Win Condition).\n"
            +
            "    Step C (Flexible Input): After providing the menu, wait for my input. I will either:\n" +
            "        Tap/Select one of your suggested two-word questions.\n" +
            "        Manually type a custom, specific question regarding the match.\n" +
            "\n" +
            "3. Response Style: > * Whether I use a suggested question or a manual one, keep your analysis concise, data-driven, and focused on the screenshot's stats (Items, Gold, KDA).\n"
            +
            "    If I ask a manual question that requires info not in the image (like \"Best build for next patch?\"), answer based on your general MLBB knowledge but prioritize the current match context first.";

    public GeminiHelper(android.content.Context context) {
        String storedKey = SettingsManager.getApiKey(context);
        String key = (storedKey != null && !storedKey.isEmpty()) ? storedKey : API_KEY;

        if (key == null || key.isEmpty()) {
            Log.e(TAG, "API Key is missing! Please set it in Settings.");
        }

        GenerativeModel gm = new GenerativeModel("gemini-2.5-flash", key != null ? key : "");
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
