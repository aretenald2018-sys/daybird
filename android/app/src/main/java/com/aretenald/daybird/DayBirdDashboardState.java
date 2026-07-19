package com.aretenald.daybird;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.getcapacitor.JSObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

final class DayBirdDashboardState {
    private static final String PREFS = "daybird_dashboard_state";
    private static final String DEVICE_ID = "device_id";
    private static final String OWNER_UID = "owner_uid";
    private static final String AUTH_UID = "auth_uid";
    private static final String SNAPSHOT = "snapshot";
    private static final String LAST_SUCCESS = "last_success";
    private static final String LAST_ERROR = "last_error";

    private DayBirdDashboardState() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String deviceId(Context context) {
        SharedPreferences preferences = prefs(context);
        String existing = preferences.getString(DEVICE_ID, "");
        if (existing != null && !existing.isBlank()) return existing;
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String created = androidId == null || androidId.isBlank() || "9774d56d682e549c".equals(androidId)
            ? "android:" + UUID.randomUUID()
            : "android-id:" + androidId;
        preferences.edit().putString(DEVICE_ID, created).commit();
        return created;
    }

    static void saveConnection(Context context, String ownerUid, String authUid) {
        SharedPreferences preferences = prefs(context);
        String nextOwnerUid = ownerUid == null ? "" : ownerUid;
        String nextAuthUid = authUid == null ? "" : authUid;
        boolean changedOwner = !nextOwnerUid.equals(preferences.getString(OWNER_UID, ""))
            || !nextAuthUid.equals(preferences.getString(AUTH_UID, ""));
        SharedPreferences.Editor editor = preferences.edit()
            .putString(OWNER_UID, nextOwnerUid)
            .putString(AUTH_UID, nextAuthUid)
            .remove(LAST_ERROR);
        if (changedOwner) editor.remove(SNAPSHOT).remove(LAST_SUCCESS);
        editor.commit();
        if (changedOwner) DayBirdWidgetStore.clearDashboard(context);
    }

    static void clearConnection(Context context) {
        prefs(context).edit()
            .remove(OWNER_UID)
            .remove(AUTH_UID)
            .remove(SNAPSHOT)
            .remove(LAST_SUCCESS)
            .remove(LAST_ERROR)
            .commit();
        DayBirdWidgetStore.clearDashboard(context);
    }

    static String ownerUid(Context context) {
        return prefs(context).getString(OWNER_UID, "");
    }

    static String authUid(Context context) {
        return prefs(context).getString(AUTH_UID, "");
    }

    static JSONObject snapshot(Context context) {
        SharedPreferences preferences = prefs(context);
        String raw = preferences.getString(SNAPSHOT, "");
        if (raw == null || raw.isBlank()) return null;
        try {
            JSONObject cached = new JSONObject(raw);
            if (DayBirdDashboardContract.hasExpectedSource(cached)) return cached;
            preferences.edit().remove(SNAPSHOT).remove(LAST_SUCCESS).commit();
            DayBirdWidgetStore.clearDashboard(context);
            return null;
        } catch (JSONException ignored) {
            preferences.edit().remove(SNAPSHOT).remove(LAST_SUCCESS).commit();
            DayBirdWidgetStore.clearDashboard(context);
            return null;
        }
    }

    static int revision(Context context) {
        JSONObject snapshot = snapshot(context);
        return snapshot == null ? 0 : snapshot.optInt("revision", 0);
    }

    static boolean saveSnapshot(Context context, JSONObject snapshot) {
        if (!DayBirdDashboardContract.accepts(snapshot, revision(context), ownerUid(context))) return false;
        prefs(context).edit()
            .putString(SNAPSHOT, snapshot.toString())
            .putLong(LAST_SUCCESS, System.currentTimeMillis())
            .remove(LAST_ERROR)
            .commit();
        DayBirdWidgetStore.saveDashboard(context, snapshot.toString());
        return true;
    }

    static void saveError(Context context, Throwable error) {
        String message = error == null ? "unknown error" : String.valueOf(error.getMessage());
        prefs(context).edit().putString(LAST_ERROR, message.length() > 240 ? message.substring(0, 240) : message).apply();
    }

    static JSObject status(Context context) {
        JSONObject snapshot = snapshot(context);
        JSObject result = new JSObject();
        boolean paired = !ownerUid(context).isBlank() && !authUid(context).isBlank();
        boolean snapshotReady = snapshot != null;
        result.put("paired", paired);
        result.put("snapshotReady", snapshotReady);
        result.put("connected", paired && snapshotReady);
        result.put("ownerUid", ownerUid(context));
        result.put("authUid", authUid(context));
        result.put("revision", snapshot == null ? 0 : snapshot.optInt("revision", 0));
        result.put("score", snapshot == null || snapshot.isNull("score") ? JSONObject.NULL : snapshot.opt("score"));
        result.put("lastSuccessEpochMs", prefs(context).getLong(LAST_SUCCESS, 0));
        result.put("lastError", prefs(context).getString(LAST_ERROR, ""));
        result.put("weights", snapshot == null ? new JSONObject() : snapshot.optJSONObject("weights"));
        return result;
    }
}
