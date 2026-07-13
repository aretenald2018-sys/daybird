package com.aretenald.daybird;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class DayBirdWidgetStore {
    private static final String PREFS = "daybird_widget";
    private static final String PAYLOAD = "payload";
    private static final int[] AGENDA_ROWS = { R.id.agenda_row_1, R.id.agenda_row_2, R.id.agenda_row_3, R.id.agenda_row_4 };
    private static final int[] AGENDA_COLORS = { R.id.agenda_color_1, R.id.agenda_color_2, R.id.agenda_color_3, R.id.agenda_color_4 };
    private static final int[] AGENDA_TIMES = { R.id.agenda_time_1, R.id.agenda_time_2, R.id.agenda_time_3, R.id.agenda_time_4 };
    private static final int[] AGENDA_TITLES = { R.id.agenda_title_1, R.id.agenda_title_2, R.id.agenda_title_3, R.id.agenda_title_4 };
    private static final int[] TIMELINE_ROWS = { R.id.timeline_row_1, R.id.timeline_row_2, R.id.timeline_row_3, R.id.timeline_row_4, R.id.timeline_row_5, R.id.timeline_row_6 };
    private static final int[] TIMELINE_COLORS = { R.id.timeline_color_1, R.id.timeline_color_2, R.id.timeline_color_3, R.id.timeline_color_4, R.id.timeline_color_5, R.id.timeline_color_6 };
    private static final int[] TIMELINE_TIMES = { R.id.timeline_time_1, R.id.timeline_time_2, R.id.timeline_time_3, R.id.timeline_time_4, R.id.timeline_time_5, R.id.timeline_time_6 };
    private static final int[] TIMELINE_TITLES = { R.id.timeline_title_1, R.id.timeline_title_2, R.id.timeline_title_3, R.id.timeline_title_4, R.id.timeline_title_5, R.id.timeline_title_6 };

    private DayBirdWidgetStore() {}

    static void saveAndRefresh(Context context, String payload) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PAYLOAD, payload).apply();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        refreshProvider(context, manager, DayBirdAgendaWidgetProvider.class);
        refreshProvider(context, manager, DayBirdTimelineWidgetProvider.class);
        refreshProvider(context, manager, DayBirdFocusWidgetProvider.class);
    }

    private static void refreshProvider(Context context, AppWidgetManager manager, Class<?> provider) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int id : ids) {
            if (provider == DayBirdAgendaWidgetProvider.class) renderAgenda(context, manager, id);
            if (provider == DayBirdTimelineWidgetProvider.class) renderTimeline(context, manager, id);
            if (provider == DayBirdFocusWidgetProvider.class) renderFocus(context, manager, id);
        }
    }

    static void renderAgenda(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.daybird_widget_agenda);
        List<JSONObject> todayEvents = eventsForToday(context);
        int nowMinute = currentMinute();
        List<JSONObject> upcoming = new ArrayList<>();
        for (JSONObject event : todayEvents) {
            if (event.optInt("startMinute") + event.optInt("durationMinute") >= nowMinute) upcoming.add(event);
        }
        int minHeight = widgetOptions(manager, widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        int maxRows = minHeight < 150 ? 2 : 4;
        views.setTextViewText(R.id.agenda_date, new SimpleDateFormat("M월 d일 E", Locale.KOREAN).format(new Date()));
        views.setTextViewText(R.id.agenda_summary, todayEvents.size() + "개 일정 · 다음 순서");
        bindRows(views, upcoming, maxRows, AGENDA_ROWS, AGENDA_COLORS, AGENDA_TIMES, AGENDA_TITLES);
        views.setViewVisibility(R.id.agenda_empty, upcoming.isEmpty() ? View.VISIBLE : View.GONE);
        views.setOnClickPendingIntent(R.id.agenda_root, openApp(context, 201));
        manager.updateAppWidget(widgetId, views);
    }

    static void renderTimeline(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.daybird_widget_timeline);
        List<JSONObject> events = eventsForToday(context);
        int minHeight = widgetOptions(manager, widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180);
        int maxRows = minHeight < 220 ? 4 : 6;
        views.setTextViewText(R.id.timeline_date, new SimpleDateFormat("M월 d일 E요일", Locale.KOREAN).format(new Date()));
        bindRows(views, events, maxRows, TIMELINE_ROWS, TIMELINE_COLORS, TIMELINE_TIMES, TIMELINE_TITLES);
        views.setViewVisibility(R.id.timeline_empty, events.isEmpty() ? View.VISIBLE : View.GONE);
        views.setOnClickPendingIntent(R.id.timeline_widget_root, openApp(context, 202));
        manager.updateAppWidget(widgetId, views);
    }

    static void renderFocus(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.daybird_widget_focus);
        JSONObject focus = payload(context).optJSONObject("focus");
        long elapsed = SystemClock.elapsedRealtime();
        if (focus == null) {
            views.setTextViewText(R.id.focus_widget_title, "집중할 준비가 됐어요");
            views.setTextViewText(R.id.focus_widget_status, "대기");
            views.setChronometer(R.id.focus_widget_timer, elapsed + 25 * 60 * 1000L, null, false);
            views.setChronometerCountDown(R.id.focus_widget_timer, true);
        } else {
            boolean running = "running".equals(focus.optString("status"));
            long remainingMillis = running
                ? Math.max(0, focus.optLong("targetEndAt") - System.currentTimeMillis())
                : Math.max(0, focus.optLong("remainingSeconds") * 1000L);
            views.setTextViewText(R.id.focus_widget_title, focus.optString("title", "집중 세션"));
            views.setTextViewText(R.id.focus_widget_status, running ? "진행 중" : "일시 정지");
            views.setChronometer(R.id.focus_widget_timer, elapsed + remainingMillis, null, running);
            views.setChronometerCountDown(R.id.focus_widget_timer, true);
        }
        views.setOnClickPendingIntent(R.id.focus_widget_root, openApp(context, 203));
        manager.updateAppWidget(widgetId, views);
    }

    private static void bindRows(RemoteViews views, List<JSONObject> events, int maxRows, int[] rows, int[] colors, int[] times, int[] titles) {
        for (int index = 0; index < rows.length; index++) {
            boolean visible = index < events.size() && index < maxRows;
            views.setViewVisibility(rows[index], visible ? View.VISIBLE : View.GONE);
            if (!visible) continue;
            JSONObject event = events.get(index);
            views.setTextViewText(times[index], formatMinute(event.optInt("startMinute")));
            views.setTextViewText(titles[index], eventLabel(event));
            views.setInt(titles[index], "setMaxLines", 2);
            views.setInt(colors[index], "setBackgroundColor", parseColor(event.optString("color", "#8D94A0")));
        }
    }

    private static List<JSONObject> eventsForToday(Context context) {
        JSONArray events = payload(context).optJSONArray("events");
        List<JSONObject> result = new ArrayList<>();
        if (events == null) return result;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        for (int index = 0; index < events.length(); index++) {
            JSONObject event = events.optJSONObject(index);
            if (event != null && today.equals(event.optString("date"))) result.add(event);
        }
        Collections.sort(result, Comparator.comparingInt(event -> event.optInt("startMinute")));
        return result;
    }

    private static JSONObject payload(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PAYLOAD, "{}");
        try {
            return new JSONObject(raw == null ? "{}" : raw);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private static Bundle widgetOptions(AppWidgetManager manager, int widgetId) {
        Bundle options = manager.getAppWidgetOptions(widgetId);
        return options == null ? new Bundle() : options;
    }

    private static PendingIntent openApp(Context context, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int currentMinute() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private static String formatMinute(int minute) {
        return String.format(Locale.US, "%02d:%02d", Math.max(0, minute) / 60, Math.max(0, minute) % 60);
    }

    private static String eventLabel(JSONObject event) {
        StringBuilder label = new StringBuilder(event.optString("title", "일정"));
        JSONArray subtasks = event.optJSONArray("subtasks");
        if (subtasks == null || subtasks.length() == 0) return label.toString();
        label.append('\n');
        for (int index = 0; index < subtasks.length(); index++) {
            String item = subtasks.optString(index).trim();
            if (item.isEmpty()) continue;
            if (label.charAt(label.length() - 1) != '\n') label.append("  ");
            label.append("• ").append(item);
        }
        return label.toString();
    }

    private static int parseColor(String value) {
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return Color.parseColor("#8D94A0");
        }
    }
}
