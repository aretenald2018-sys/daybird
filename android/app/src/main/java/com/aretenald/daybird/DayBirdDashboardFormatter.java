package com.aretenald.daybird;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class DayBirdDashboardFormatter {
    private DayBirdDashboardFormatter() {}

    static String workoutText(JSONObject workout) {
        if (workout == null) return "";
        String detail = text(workout, "detail", "");
        String value = text(workout, "value", "");
        String status = text(workout, "status", "");
        String details = detail.isEmpty() ? join(value, status) : detail;
        String firstLine = workoutStatusText(workout);
        return details.isEmpty() ? firstLine : firstLine + "\n" + details;
    }

    static String workoutStatusText(JSONObject workout) {
        if (workout == null) return "";
        String state = text(workout, "state", "planned");
        String label = text(workout, "sportLabel", text(workout, "label", "운동"));
        String track = trackLabel(text(workout, "track", ""));
        if (!track.isEmpty()) label += " (" + track + ")";
        return stateIcon(state) + " " + stateLabel(state) + " · " + label;
    }

    /**
     * Fits every goal into the widget's fixed set of TextViews. Once there are
     * more goals than slots, details are omitted and the status rows are split
     * as evenly as possible. No goal is replaced by an "외 N개" summary.
     */
    static List<WorkoutGroup> workoutGroups(JSONArray workouts, int slotCount) {
        if (workouts == null || workouts.length() == 0 || slotCount <= 0) {
            return Collections.emptyList();
        }

        int goalCount = workouts.length();
        int groupCount = Math.min(goalCount, slotCount);
        int baseGroupSize = goalCount / groupCount;
        int groupsWithExtraGoal = goalCount % groupCount;
        boolean dense = goalCount > slotCount;
        List<WorkoutGroup> groups = new ArrayList<>(groupCount);
        int workoutIndex = 0;

        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int itemCount = baseGroupSize + (groupIndex < groupsWithExtraGoal ? 1 : 0);
            StringBuilder groupText = new StringBuilder();
            String commonState = null;
            boolean mixedState = false;

            for (int itemIndex = 0; itemIndex < itemCount; itemIndex++, workoutIndex++) {
                JSONObject workout = workouts.optJSONObject(workoutIndex);
                if (groupText.length() > 0) groupText.append('\n');
                groupText.append(dense ? workoutStatusText(workout) : workoutText(workout));

                String state = workout == null ? "" : workout.optString("state", "planned");
                if (commonState == null) commonState = state;
                else if (!commonState.equals(state)) mixedState = true;
            }

            groups.add(new WorkoutGroup(
                groupText.toString(),
                mixedState || commonState == null ? "" : commonState,
                itemCount
            ));
        }
        return groups;
    }

    static float workoutTextSizeSp(int goalCount, boolean compact) {
        if (goalCount > 8) return compact ? 6.5f : 8f;
        if (goalCount > 4) return compact ? 7f : 9f;
        return compact ? 8f : 11f;
    }

    static final class WorkoutGroup {
        final String text;
        final String state;
        final int itemCount;

        WorkoutGroup(String text, String state, int itemCount) {
            this.text = text;
            this.state = state;
            this.itemCount = itemCount;
        }
    }

    static String runningRecordText(JSONObject record) {
        if (record == null || record.optDouble("distanceKm", 0) <= 0) return "";
        String date = dateLabel(record.optString("dateKey", ""));
        String distance = String.format(Locale.KOREAN, "%.1fkm", record.optDouble("distanceKm", 0));
        String firstLine = join(date, distance, paceText(record.optInt("paceSecPerKm", 0)));
        int heartRate = record.optInt("avgHeartRateBpm", record.optInt("heartRateBpm", 0));
        int cadence = record.optInt("cadenceSpm", 0);
        String heartRateText = heartRate > 0 ? "심박 " + heartRate + "bpm" : "심박 —";
        String cadenceText = cadence > 0 ? "케이던스 " + cadence + "spm" : "케이던스 —";
        return firstLine + "\n" + heartRateText + " · " + cadenceText;
    }

    static String runningTitle(JSONObject goal, boolean compact) {
        String base = compact ? "러닝 5회" : "러닝 최근 5회";
        if (goal == null) return base;
        int targetPace = goal.optInt("targetPaceSecPerKm", 0);
        if (targetPace > 0) return base + " · 목표 " + paceText(targetPace);
        double adaptiveRate = goal.optDouble("adaptiveRatePct", Double.NaN);
        if (Double.isFinite(adaptiveRate) && adaptiveRate > 0) {
            return base + String.format(Locale.KOREAN, " · 주간 %.1f%% 개선", adaptiveRate);
        }
        return base;
    }

    static String pointSummaryText(JSONObject points) {
        if (points == null || "missing".equals(points.optString("state"))) {
            return "가계부 포인트를 설정해 주세요";
        }
        String label = text(points, "label", "와인구매 포인트");
        Long canonicalBalance = integer(points, "balance");
        if (canonicalBalance != null) {
            return label + " " + formatNumber(canonicalBalance) + "P";
        }
        if (!"ready".equals(points.optString("state"))) return label + " 집계 대기 중";

        Long legacyBalance = integer(points, "monthPoints");
        if (legacyBalance == null) {
            Long earned = integer(points, "earnedMonthPoints");
            Long spent = integer(points, "spentMonthPoints");
            if (earned != null || spent != null) {
                legacyBalance = (earned == null ? 0 : earned) - (spent == null ? 0 : spent);
            }
        }
        if (legacyBalance != null) return label + " " + formatNumber(legacyBalance) + "P";

        Long earnedTwoWeek = integer(points, "earnedTwoWeek");
        return earnedTwoWeek == null
            ? label + " 집계 대기 중"
            : label + " · 2주 +" + formatNumber(earnedTwoWeek) + "P";
    }

    static String paceText(int seconds) {
        if (seconds <= 0) return "페이스 —";
        return String.format(Locale.KOREAN, "%d'%02d\"/km", seconds / 60, seconds % 60);
    }

    private static String stateIcon(String state) {
        return switch (state) {
            case "achieved", "done" -> "✓";
            case "attempted" -> "△";
            case "missed" -> "!";
            case "future" -> "◇";
            case "inactive" -> "–";
            default -> "○";
        };
    }

    private static String stateLabel(String state) {
        return switch (state) {
            case "achieved", "done" -> "달성";
            case "attempted" -> "시도";
            case "missed" -> "미달";
            case "planned" -> "달성 전";
            case "future" -> "향후";
            case "inactive" -> "비활성";
            default -> "확인";
        };
    }

    private static String trackLabel(String track) {
        return switch (track.toLowerCase(Locale.ROOT)) {
            case "volume" -> "볼륨";
            case "intensity" -> "강도";
            case "frequency" -> "빈도";
            case "pace" -> "페이스";
            case "distance" -> "거리";
            case "duration" -> "시간";
            default -> track;
        };
    }

    private static String dateLabel(String dateKey) {
        if (dateKey != null && dateKey.matches("\\d{4}-\\d{2}-\\d{2}")) {
            int month = Integer.parseInt(dateKey.substring(5, 7));
            int day = Integer.parseInt(dateKey.substring(8, 10));
            return month + "/" + day;
        }
        return "날짜 —";
    }

    private static String join(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(part);
        }
        return result.toString();
    }

    private static String text(JSONObject object, String key, String fallback) {
        if (object == null || object.isNull(key)) return fallback;
        String value = object.optString(key, fallback).trim();
        return value.isEmpty() ? fallback : value;
    }

    private static Long integer(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return null;
        Object value = object.opt(key);
        if (!(value instanceof Number number)) return null;
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)) return null;
        return number.longValue();
    }

    private static String formatNumber(long value) {
        return NumberFormat.getNumberInstance(Locale.KOREAN).format(value);
    }
}
