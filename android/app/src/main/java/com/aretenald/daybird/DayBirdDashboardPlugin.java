package com.aretenald.daybird;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

@CapacitorPlugin(name = "DayBirdDashboard")
public class DayBirdDashboardPlugin extends Plugin {
    @Override
    public void load() {
        DayBirdDashboardSync.schedule(getContext());
        DayBirdDashboardSync.startListener(getContext());
    }

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(DayBirdDashboardState.status(getContext()));
    }

    @PluginMethod
    public void exchangePairing(PluginCall call) {
        String code = call.getString("code", "").trim();
        if (code.isEmpty()) {
            call.reject("Pairing code is required");
            return;
        }
        DayBirdDashboardSync.runAsync(() -> {
            try {
                JSObject result = DayBirdDashboardSync.exchangePairingBlocking(getContext(), code);
                call.resolve(result);
            } catch (Exception error) {
                DayBirdDashboardState.saveError(getContext(), error);
                call.reject(error.getMessage(), error);
            }
        });
    }

    @PluginMethod
    public void refresh(PluginCall call) {
        DayBirdDashboardSync.runAsync(() -> {
            try {
                DayBirdDashboardSync.requestRefreshBlocking(getContext());
                call.resolve(DayBirdDashboardState.status(getContext()));
            } catch (Exception error) {
                DayBirdDashboardState.saveError(getContext(), error);
                call.reject(error.getMessage(), error);
            }
        });
    }

    @PluginMethod
    public void saveWeights(PluginCall call) {
        JSObject weights = call.getObject("weights");
        if (weights == null) {
            call.reject("Weights are required");
            return;
        }
        DayBirdDashboardSync.runAsync(() -> {
            try {
                DayBirdDashboardSync.saveWeightsBlocking(getContext(), new JSONObject(weights.toString()));
                call.resolve(DayBirdDashboardState.status(getContext()));
            } catch (Exception error) {
                DayBirdDashboardState.saveError(getContext(), error);
                call.reject(error.getMessage(), error);
            }
        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        DayBirdDashboardSync.runAsync(() -> {
            try {
                DayBirdDashboardSync.disconnectBlocking(getContext());
                call.resolve(DayBirdDashboardState.status(getContext()));
            } catch (Exception error) {
                DayBirdDashboardState.saveError(getContext(), error);
                call.reject(error.getMessage(), error);
            }
        });
    }
}
