package com.example.mlbbop.managers;

public interface OverlayActionListener {
    void onCaptureRequest();

    void onSaveRequest();

    void onSendRequest();

    void onHomeRequest();

    void onSettingsRequest();

    void onCloseRequest();

    void onChatRequest(String message); // For follow-up or quick actions
}
