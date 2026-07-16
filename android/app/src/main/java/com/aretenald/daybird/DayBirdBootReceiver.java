package com.aretenald.daybird;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DayBirdBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DayBirdDashboardSync.schedule(context);
        DayBirdWidgetStore.refreshDashboard(context);
        DayBirdDashboardSync.refreshAsync(context, (value, error) -> {});
    }
}
