package com.aretenald.daybird;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DayBirdWidget")
public class DayBirdWidgetPlugin extends Plugin {
    @PluginMethod
    public void sync(PluginCall call) {
        String payload = call.getString("payload");
        if (payload == null) {
            call.reject("Widget payload is required");
            return;
        }
        DayBirdWidgetStore.saveAndRefresh(getContext(), payload);
        call.resolve();
    }
}
