package com.aretenald.daybird;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(DayBirdWidgetPlugin.class);
        registerPlugin(DayBirdDashboardPlugin.class);
        super.onCreate(savedInstanceState);
        DayBirdDashboardSync.schedule(this);
        DayBirdDashboardSync.startListener(this);
    }
}
