package com.aretenald.daybird;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class DayBirdDashboardHttp {
    private DayBirdDashboardHttp() {}

    static JSONObject post(Context context, String path, JSONObject body, String idToken) throws Exception {
        String base = context.getString(R.string.dashboard_api_base).replaceAll("/+$", "");
        String action = URLEncoder.encode(path, StandardCharsets.UTF_8.name());
        HttpURLConnection connection = (HttpURLConnection) new URL(base + "/api/daybird?action=" + action).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(25000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (idToken != null && !idToken.isBlank()) connection.setRequestProperty("Authorization", "Bearer " + idToken);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder raw = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
            }
        }
        connection.disconnect();
        JSONObject response = raw.length() == 0 ? new JSONObject() : new JSONObject(raw.toString());
        if (status < 200 || status >= 300 || !response.optBoolean("ok", true)) {
            throw new IllegalStateException(response.optString("error", "DayBird API " + status));
        }
        return response;
    }
}
