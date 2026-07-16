package com.aretenald.daybird;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class DayBirdMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (!"dashboard_snapshot".equals(message.getData().get("type"))) return;
        DayBirdDashboardSync.refreshAsync(this, (value, error) -> {});
    }

    @Override
    public void onNewToken(String token) {
        DayBirdDashboardSync.runAsync(() -> {
            try {
                DayBirdDashboardSync.registerFcmTokenBlocking(this, token);
            } catch (Exception error) {
                DayBirdDashboardState.saveError(this, error);
            }
        });
    }
}
