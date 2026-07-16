package com.aretenald.daybird;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
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
        try {
            JSONObject next = new JSONObject(payload);
            if (!next.has("dashboard")) {
                JSONObject existingDashboard = payload(context).optJSONObject("dashboard");
                if (existingDashboard != null) next.put("dashboard", existingDashboard);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PAYLOAD, next.toString()).apply();
        } catch (JSONException ignored) {
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        refreshProvider(context, manager, DayBirdAgendaWidgetProvider.class);
        refreshProvider(context, manager, DayBirdTimelineWidgetProvider.class);
        refreshProvider(context, manager, DayBirdFocusWidgetProvider.class);
        refreshProvider(context, manager, DayBirdDashboardWidgetProvider.class);
    }

    private static void refreshProvider(Context context, AppWidgetManager manager, Class<?> provider) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int id : ids) {
            if (provider == DayBirdAgendaWidgetProvider.class) renderAgenda(context, manager, id);
            if (provider == DayBirdTimelineWidgetProvider.class) renderTimeline(context, manager, id);
            if (provider == DayBirdFocusWidgetProvider.class) renderFocus(context, manager, id);
            if (provider == DayBirdDashboardWidgetProvider.class) renderDashboard(context, manager, id);
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
        bindRows(context, views, upcoming, maxRows, AGENDA_ROWS, AGENDA_COLORS, AGENDA_TIMES, AGENDA_TITLES);
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
        bindRows(context, views, events, maxRows, TIMELINE_ROWS, TIMELINE_COLORS, TIMELINE_TIMES, TIMELINE_TITLES);
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

    static void saveDashboard(Context context, String rawDashboard) {
        try {
            JSONObject dashboard = new JSONObject(rawDashboard);
            JSONObject current = payload(context);
            current.put("dashboard", dashboard.optJSONObject("dashboard") != null ? dashboard.getJSONObject("dashboard") : dashboard);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PAYLOAD, current.toString()).apply();
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            refreshProvider(context, manager, DayBirdDashboardWidgetProvider.class);
        } catch (JSONException ignored) {
            // Ignore malformed external data and retain the last valid dashboard snapshot.
        }
    }

    static void clearDashboard(Context context) {
        JSONObject current = payload(context);
        current.remove("dashboard");
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PAYLOAD, current.toString()).apply();
        refreshDashboard(context);
    }

    static void refreshDashboard(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        refreshProvider(context, manager, DayBirdDashboardWidgetProvider.class);
    }

    static void renderDashboard(Context context, AppWidgetManager manager, int widgetId) {
        int minHeight = widgetOptions(manager, widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 500);
        boolean compact = minHeight < 450;
        int layoutId = compact
            ? R.layout.daybird_widget_dashboard_compact
            : R.layout.daybird_widget_dashboard;
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        JSONObject dashboard = payload(context).optJSONObject("dashboard");
        boolean ready = dashboard != null;
        JSONObject domains = ready ? dashboard.optJSONObject("domains") : null;
        JSONObject nutrition = ready ? dashboard.optJSONObject("nutrition") : null;
        JSONObject healthGoal = ready ? dashboard.optJSONObject("healthGoal") : null;
        JSONArray workouts = healthGoal == null ? (ready ? dashboard.optJSONArray("workouts") : null) : healthGoal.optJSONArray("items");
        JSONObject running = ready ? dashboard.optJSONObject("running") : null;
        JSONObject spending = ready ? dashboard.optJSONObject("spending") : null;
        JSONObject wine = ready ? dashboard.optJSONObject("wine") : null;

        views.setTextViewText(R.id.dashboard_date, new SimpleDateFormat("M월 d일 · 오늘 상태 요약", Locale.KOREAN).format(new Date()));
        views.setTextViewText(R.id.dashboard_score, ready ? numberText(dashboard, "score", "—") : "—");
        JSONObject streak = ready ? dashboard.optJSONObject("streak") : null;
        views.setTextViewText(R.id.dashboard_streak, ready ? text(streak, "label", "데이터 연결 대기") : "데이터 연결 대기");
        bindDomain(views, R.id.dashboard_domain_food, domains, "food", compact);
        bindDomain(views, R.id.dashboard_domain_health, domains, "health", compact);
        bindDomain(views, R.id.dashboard_domain_running, domains, "running", compact);
        bindDomain(views, R.id.dashboard_domain_spending, domains, "spending", compact);
        bindDomain(views, R.id.dashboard_domain_wine, domains, "wine", compact);

        int progress = ready && nutrition != null ? Math.max(0, Math.min(100, nutrition.optInt("progress", 0))) : 0;
        views.setProgressBar(R.id.dashboard_food_progress, 100, Math.max(0, Math.min(100, progress)), false);
        views.setTextViewText(R.id.dashboard_food_percent, ready && nutrition != null ? progress + "%" : "—");
        views.setTextViewText(R.id.dashboard_food_calories, ready && nutrition != null
            ? number(nutrition, "actualKcal") + " / " + number(nutrition, "targetKcal") + " kcal"
            : "연결 대기");
        views.setTextViewText(R.id.dashboard_food_macro, ready && nutrition != null
            ? "단백질 " + number(nutrition, "proteinG") + "g · 탄수 " + number(nutrition, "carbsG") + "g · 지방 " + number(nutrition, "fatG") + "g"
            : "식단 데이터 필요");

        String healthTitle = compact ? "헬스 목표" : "주간 헬스 목표";
        if (healthGoal != null && healthGoal.optInt("seasonWeek", 0) > 0) healthTitle += " · " + healthGoal.optInt("seasonWeek") + "주차";
        views.setTextViewText(R.id.dashboard_health_title, healthTitle);
        String emptyHealthGoal = healthGoal == null || "missing".equals(healthGoal.optString("state")) ? "시즌 목표를 설정해 주세요" : "이번 주 목표 없음";
        bindWorkout(views, R.id.dashboard_workout_one, workouts, 0, emptyHealthGoal);
        bindWorkout(views, R.id.dashboard_workout_two, workouts, 1, emptyHealthGoal);
        bindWorkout(views, R.id.dashboard_workout_three, workouts, 2, emptyHealthGoal);

        views.setTextViewText(R.id.dashboard_pace_change, ready && running != null ? signedPercent(running, "paceChangePct") : "—");
        views.setTextViewText(R.id.dashboard_cadence_change, ready && running != null ? signedPercent(running, "cadenceChangePct") : "—");
        views.setImageViewBitmap(R.id.dashboard_running_chart, lineChart(context, running == null ? null : running.optJSONArray("trend"), Color.parseColor("#3187F5")));
        views.setTextViewText(R.id.dashboard_running_detail, ready && running != null
            ? paceText(running.optInt("paceSecPerKm", 0)) + " · " + (running.optInt("cadenceSpm", 0) > 0 ? running.optInt("cadenceSpm") + " spm" : "케이던스 —")
            : "러닝 데이터 필요");

        long samePeriodDifference = spending == null ? 0 : spending.optLong("samePeriodDifference", 0);
        boolean hasComparison = ready && spending != null && !spending.isNull("samePeriodDifference");
        views.setTextViewText(R.id.dashboard_savings, hasComparison ? comparedWon(samePeriodDifference) : "—");
        views.setTextColor(R.id.dashboard_savings, Color.parseColor(samePeriodDifference >= 0 ? "#26AF68" : "#E05252"));
        views.setImageViewBitmap(R.id.dashboard_spending_chart, spendingComparisonChart(
            context,
            spending == null ? null : spending.optJSONArray("currentCumulativeTrend"),
            spending == null ? null : spending.optJSONArray("previousCumulativeTrend")
        ));
        views.setTextViewText(R.id.dashboard_spending_detail, ready && spending != null ? "이번달 " + wonAmount(spending.optLong("monthSpent")) : "가계부 데이터 필요");
        views.setTextViewText(R.id.dashboard_week_change, ready && spending != null ? spendingChangeText(spending) : "—");

        views.setTextViewText(R.id.dashboard_wine_name, ready && wine != null ? text(wine, "name", "와인 기록 없음") : "와인 데이터 필요");
        views.setTextViewText(R.id.dashboard_wine_note, ready && wine != null ? text(wine, "note", "최근 테이스팅을 기록해 보세요") : "테이스팅 노트 필요");
        views.setTextViewText(R.id.dashboard_wine_rating, ready && wine != null && !wine.isNull("rating") ? "★ " + wine.optDouble("rating") + " / 5.0" : "— / 5.0");
        Bitmap wineImage = wine == null ? null : dataUrlBitmap(wine.optString("imageThumbnail", ""));
        views.setViewVisibility(R.id.dashboard_wine_image, wineImage == null ? View.GONE : View.VISIBLE);
        if (wineImage != null) views.setImageViewBitmap(R.id.dashboard_wine_image, wineImage);
        views.setOnClickPendingIntent(R.id.dashboard_widget_root, openApp(context, 204));
        manager.updateAppWidget(widgetId, views);
    }

    private static void bindDomain(RemoteViews views, int viewId, JSONObject domains, String key, boolean compact) {
        JSONObject domain = domains == null ? null : domains.optJSONObject(key);
        String value = domain == null ? "—" : text(domain, "score", "—");
        if (compact) {
            String label = switch (key) {
                case "food" -> "음식";
                case "health" -> "헬스";
                case "running" -> "러닝";
                case "spending" -> "소비";
                case "wine" -> "와인";
                default -> key;
            };
            views.setTextViewText(viewId, label + " " + value);
        } else {
            views.setTextViewText(viewId, value + "/100");
        }
    }

    private static void bindWorkout(RemoteViews views, int viewId, JSONArray workouts, int index, String emptyMessage) {
        JSONObject workout = workouts == null ? null : workouts.optJSONObject(index);
        if (workout == null) {
            views.setTextViewText(viewId, index == 0 ? emptyMessage : "—");
            return;
        }
        views.setTextViewText(viewId, text(workout, "label", "운동") + "   " + text(workout, "value", "—") + "   " + text(workout, "status", ""));
    }

    private static String number(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return "—";
        double value = object.optDouble(key, Double.NaN);
        if (!Double.isFinite(value)) return "—";
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.KOREAN, "%.1f", value);
    }

    private static String signedPercent(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return "—";
        double value = object.optDouble(key, Double.NaN);
        if (!Double.isFinite(value)) return "—";
        return String.format(Locale.KOREAN, "%+.1f%% %s", value, value >= 0 ? "↗" : "↘");
    }

    private static String spendingChangeText(JSONObject spending) {
        if (spending == null || spending.isNull("samePeriodChangePct")) return "비교 기록 없음";
        double value = spending.optDouble("samePeriodChangePct", Double.NaN);
        if (!Double.isFinite(value)) return "비교 기록 없음";
        if (Math.abs(value) < 0.05) return "지난달과 동일";
        return String.format(Locale.KOREAN, "%.1f%% %s", Math.abs(value), value < 0 ? "덜 씀" : "더 씀");
    }

    private static String paceText(int seconds) {
        if (seconds <= 0) return "페이스 —";
        return String.format(Locale.KOREAN, "%d'%02d\"/km", seconds / 60, seconds % 60);
    }

    private static String comparedWon(long value) {
        if (value == 0) return "지난달과 동일";
        return wonAmount(Math.abs(value)) + (value > 0 ? " 덜 씀" : " 더 씀");
    }

    private static String wonAmount(long value) {
        return NumberFormat.getNumberInstance(Locale.KOREAN).format(value) + "원";
    }

    private static Bitmap dataUrlBitmap(String value) {
        if (value == null || value.isBlank() || !value.startsWith("data:image/")) return null;
        int comma = value.indexOf(',');
        if (comma < 0) return null;
        try {
            byte[] decoded = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String text(JSONObject object, String key, String fallback) {
        if (object == null || object.isNull(key)) return fallback;
        String value = object.optString(key, fallback).trim();
        return value.isEmpty() ? fallback : value;
    }

    private static String numberText(JSONObject object, String key, String fallback) {
        if (object == null || !object.has(key)) return fallback;
        Object value = object.opt(key);
        return value == null || JSONObject.NULL.equals(value) ? fallback : String.valueOf(value);
    }

    private static Bitmap lineChart(Context context, JSONArray values, int color) {
        int width = dp(context, 140);
        int height = dp(context, 22);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (values == null || values.length() < 2) return bitmap;
        Canvas canvas = new Canvas(bitmap);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int index = 0; index < values.length(); index++) {
            double value = values.optDouble(index, 0);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double range = Math.max(0.0001, max - min);
        float inset = dp(context, 2);
        Path line = new Path();
        Path fill = new Path();
        for (int index = 0; index < values.length(); index++) {
            float x = inset + (width - inset * 2) * index / (values.length() - 1f);
            float y = (float) (inset + (height - inset * 2) * (1d - (values.optDouble(index, 0) - min) / range));
            if (index == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, height - inset);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
        }
        fill.lineTo(width - inset, height - inset);
        fill.close();
        Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
        area.setColor(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawPath(fill, area);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(context, 2));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(line, stroke);
        return bitmap;
    }

    private static Bitmap spendingComparisonChart(Context context, JSONArray current, JSONArray previous) {
        int width = dp(context, 140);
        int height = dp(context, 22);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int count = Math.min(current == null ? 0 : current.length(), previous == null ? 0 : previous.length());
        if (count < 2) return bitmap;
        Canvas canvas = new Canvas(bitmap);
        double max = 1;
        for (int index = 0; index < count; index++) max = Math.max(max, Math.max(current.optDouble(index, 0), previous.optDouble(index, 0)));
        drawComparisonLine(context, canvas, current, count, max, width, height, Color.parseColor("#3187F5"), dp(context, 2));
        drawComparisonLine(context, canvas, previous, count, max, width, height, Color.parseColor("#B9C0CC"), dp(context, 1));
        return bitmap;
    }

    private static void drawComparisonLine(Context context, Canvas canvas, JSONArray values, int count, double max, int width, int height, int color, float strokeWidth) {
        Path path = new Path();
        float inset = dp(context, 2);
        for (int index = 0; index < count; index++) {
            float x = inset + (width - inset * 2) * index / Math.max(1, count - 1);
            float y = (float) (height - inset - (height - inset * 2) * values.optDouble(index, 0) / max);
            if (index == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(path, paint);
    }

    private static int dp(Context context, int value) {
        return Math.max(1, Math.round(value * context.getResources().getDisplayMetrics().density));
    }

    private static void bindRows(Context context, RemoteViews views, List<JSONObject> events, int maxRows, int[] rows, int[] colors, int[] times, int[] titles) {
        float density = context.getResources().getDisplayMetrics().density;
        for (int index = 0; index < rows.length; index++) {
            boolean visible = index < events.size() && index < maxRows;
            views.setViewVisibility(rows[index], visible ? View.VISIBLE : View.GONE);
            if (!visible) continue;
            JSONObject event = events.get(index);
            boolean completed = event.optBoolean("completed", false);
            views.setTextViewText(times[index], formatMinute(event.optInt("startMinute")));
            views.setTextViewText(titles[index], eventLabel(event));
            views.setInt(titles[index], "setMaxLines", 2);
            views.setInt(rows[index], "setBackgroundResource", completed ? R.drawable.widget_completed_row : android.R.color.transparent);
            views.setViewPadding(rows[index], completed ? Math.round(6 * density) : 0, completed ? Math.round(2 * density) : 0, completed ? Math.round(6 * density) : 0, completed ? Math.round(2 * density) : 0);
            views.setTextColor(times[index], Color.parseColor(completed ? "#4F6494" : "#73737B"));
            views.setTextColor(titles[index], Color.parseColor(completed ? "#203A72" : "#1C1C1E"));
            views.setInt(colors[index], "setBackgroundColor", completed ? Color.parseColor("#6F8FE8") : parseColor(event.optString("color", "#8D94A0")));
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
        StringBuilder label = new StringBuilder(event.optBoolean("completed", false) ? "✓ " : "").append(event.optString("title", "일정"));
        JSONArray subtasks = event.optJSONArray("subtasks");
        if (subtasks == null || subtasks.length() == 0) return label.toString();
        StringBuilder details = new StringBuilder();
        for (int index = 0; index < subtasks.length(); index++) {
            String item = subtasks.optString(index).trim();
            if (item.isEmpty()) continue;
            boolean checked = item.matches("^(?:☑|✅).*");
            boolean checkbox = checked || item.matches("^(?:ㅁ|□|☐).*");
            String text = item.replaceFirst("^(?:☑|✅|ㅁ|□|☐|ㅡ|[-–—•])\\s*", "").trim();
            if (text.isEmpty()) continue;
            if (details.length() > 0) details.append("  ");
            details.append(checkbox ? (checked ? "☑ " : "☐ ") : "• ").append(text);
        }
        if (details.length() > 0) label.append('\n').append(details);
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
