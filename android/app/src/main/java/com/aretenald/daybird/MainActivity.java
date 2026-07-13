package com.aretenald.daybird;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(DayBirdWidgetPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
