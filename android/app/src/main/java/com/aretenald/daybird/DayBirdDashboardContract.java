package com.aretenald.daybird;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class DayBirdDashboardContract {
    static final int SCHEMA_VERSION = 1;
    static final String SOURCE_ENVIRONMENT = "tomatodev";
    private static final long MAX_TOMATODEV_SNAPSHOT_AGE_MS = 24L * 60L * 60L * 1_000L;

    private DayBirdDashboardContract() {}

    static boolean accepts(JSONObject snapshot, int currentRevision) {
        return accepts(snapshot, currentRevision, "");
    }

    static boolean accepts(JSONObject snapshot, int currentRevision, String expectedBudgetUid) {
        if (snapshot == null || snapshot.optInt("schemaVersion", 0) != SCHEMA_VERSION) return false;
        if (!hasExpectedSource(snapshot)) return false;
        Object rawRevision = snapshot.opt("revision");
        if (!isInteger(rawRevision)) return false;
        int nextRevision = ((Number) rawRevision).intValue();
        if (nextRevision <= 0 || nextRevision < currentRevision) return false;
        Object generatedAt = snapshot.opt("generatedAtEpochMs");
        if (!(generatedAt instanceof Number) || !Double.isFinite(((Number) generatedAt).doubleValue()) || ((Number) generatedAt).doubleValue() <= 0) return false;
        String budgetUid = snapshot.optString("budgetUid", "").trim();
        if (budgetUid.isBlank()) return false;
        if (expectedBudgetUid != null && !expectedBudgetUid.isBlank() && !expectedBudgetUid.equals(budgetUid)) return false;
        if (!"Asia/Seoul".equals(snapshot.optString("timezone", ""))) return false;
        return validNutrition(snapshot.optJSONObject("nutrition"))
            && validSeasonGoals(snapshot)
            && validRunning(snapshot)
            && validSpending(snapshot.optJSONObject("spending"))
            && validWine(snapshot)
            && validPoints(snapshot.optJSONObject("points"));
    }

    static boolean hasExpectedSource(JSONObject snapshot) {
        if (snapshot == null || !SOURCE_ENVIRONMENT.equals(snapshot.optString("sourceEnvironment", ""))) return false;
        Object rawDashboardGeneratedAt = snapshot.opt("generatedAtEpochMs");
        if (!(rawDashboardGeneratedAt instanceof Number dashboardGeneratedAt)
            || !Double.isFinite(dashboardGeneratedAt.doubleValue())
            || dashboardGeneratedAt.doubleValue() <= 0) return false;

        JSONObject metadata = snapshot.optJSONObject("tomatoDevSnapshotMetadata");
        if (metadata == null || metadata.optInt("schemaVersion", 0) != SCHEMA_VERSION) return false;
        if (!SOURCE_ENVIRONMENT.equals(metadata.optString("sourceEnvironment", ""))) return false;
        String state = metadata.optString("state", "");
        if (!"ready".equals(state) && !"no-season".equals(state)) return false;
        Object rawTomatoDevGeneratedAt = metadata.opt("generatedAt");
        if (!(rawTomatoDevGeneratedAt instanceof Number tomatoDevGeneratedAt)
            || !Double.isFinite(tomatoDevGeneratedAt.doubleValue())
            || tomatoDevGeneratedAt.doubleValue() <= 0) return false;
        return Math.abs(dashboardGeneratedAt.doubleValue() - tomatoDevGeneratedAt.doubleValue())
            <= MAX_TOMATODEV_SNAPSHOT_AGE_MS;
    }

    static JSONObject currentHealthGoal(JSONObject snapshot, String expectedWeekStart) {
        if (snapshot == null || expectedWeekStart == null || expectedWeekStart.isBlank()) return null;
        JSONArray seasonGoals = snapshot.optJSONArray("seasonGoals");
        if (seasonGoals != null) {
            JSONArray items = new JSONArray();
            JSONObject first = null;
            int matchCount = 0;
            for (int index = 0; index < seasonGoals.length(); index++) {
                JSONObject candidate = seasonGoals.optJSONObject(index);
                if (!trustedSeasonGoal(candidate, expectedWeekStart)) continue;
                JSONObject healthCandidate = withoutRunningItems(candidate);
                if (healthCandidate == null) continue;
                if (first == null) first = healthCandidate;
                matchCount += 1;
                JSONArray candidateItems = healthCandidate.optJSONArray("items");
                for (int itemIndex = 0; itemIndex < candidateItems.length(); itemIndex++) {
                    JSONObject item = candidateItems.optJSONObject(itemIndex);
                    if (item != null) items.put(item);
                }
            }
            if (first != null) {
                if (matchCount == 1) return first;
                try {
                    return new JSONObject()
                        .put("state", items.length() > 0 ? "ready" : "empty")
                        .put("seasonId", "parallel")
                        .put("seasonName", "병행 시즌")
                        .put("weekStart", expectedWeekStart)
                        .put("weekEnd", first.optString("weekEnd", ""))
                        .put("seasonWeek", first.optInt("seasonWeek", 0))
                        .put("items", items);
                } catch (JSONException ignored) {
                    return null;
                }
            }
        }
        JSONObject goal = snapshot.optJSONObject("healthGoal");
        return trustedSeasonGoal(goal, expectedWeekStart) ? withoutRunningItems(goal) : null;
    }

    private static JSONObject withoutRunningItems(JSONObject goal) {
        if (goal == null) return null;
        JSONArray sourceItems = goal.optJSONArray("items");
        if (sourceItems == null) return null;

        JSONArray healthItems = new JSONArray();
        boolean removedRunning = false;
        for (int index = 0; index < sourceItems.length(); index++) {
            Object value = sourceItems.opt(index);
            if (value instanceof JSONObject item
                && "running".equals(item.optString("type", "").trim())) {
                removedRunning = true;
                continue;
            }
            healthItems.put(value);
        }
        if (!removedRunning) return goal;

        try {
            JSONObject filtered = new JSONObject(goal.toString()).put("items", healthItems);
            if (healthItems.length() == 0) filtered.put("state", "empty");
            return filtered;
        } catch (JSONException ignored) {
            return null;
        }
    }

    static JSONObject currentRunningGoal(JSONObject snapshot, String expectedWeekStart) {
        if (snapshot == null) return null;
        JSONObject running = snapshot.optJSONObject("running");
        JSONObject direct = running == null ? null : running.optJSONObject("goal");
        if (validRunningPace(direct)) return direct;
        if (expectedWeekStart == null || expectedWeekStart.isBlank()) return null;

        JSONArray seasonGoals = snapshot.optJSONArray("seasonGoals");
        if (seasonGoals != null) {
            for (int index = 0; index < seasonGoals.length(); index++) {
                JSONObject candidate = seasonGoals.optJSONObject(index);
                if (candidate == null || !expectedWeekStart.equals(candidate.optString("weekStart", ""))) continue;
                JSONObject runningPace = candidate.optJSONObject("runningPace");
                if (validRunningPace(runningPace)) return runningPace;
            }
        }
        JSONObject healthGoal = snapshot.optJSONObject("healthGoal");
        if (healthGoal != null && expectedWeekStart.equals(healthGoal.optString("weekStart", ""))) {
            JSONObject runningPace = healthGoal.optJSONObject("runningPace");
            if (validRunningPace(runningPace)) return runningPace;
        }
        return null;
    }

    private static boolean validRunning(JSONObject snapshot) {
        JSONObject running = snapshot.optJSONObject("running");
        if (running == null) return false;
        JSONObject goal = running.optJSONObject("goal");
        if (!validRunningPace(goal) || goal.optString("status", "").isBlank()) return false;
        if (!(goal.opt("heartRateCaution") instanceof Boolean)) return false;
        boolean hasRecords = running.has("records");
        boolean hasRecent = running.has("recent");
        if (!hasRecords && !hasRecent) return false;
        if (hasRecords && !validRunningRecords(running.optJSONArray("records"))) return false;
        return !hasRecent || validRunningRecords(running.optJSONArray("recent"));
    }

    private static boolean validRunningRecords(JSONArray records) {
        if (records == null || records.length() > 5) return false;
        for (int index = 0; index < records.length(); index++) {
            JSONObject record = records.optJSONObject(index);
            if (record == null || !validDateKey(record.optString("dateKey", ""))) return false;
            if (!validRequiredNumber(record, "distanceKm", 0, 10_000, false)) return false;
            if (!validOptionalNumber(record, "paceSecPerKm", 0, 3_600, false)) return false;
            if (!validOptionalNumber(record, "cadenceSpm", 0, 300, false)) return false;
            if (!validOptionalNumber(record, "avgHeartRateBpm", 0, 240, false)) return false;
        }
        return true;
    }

    private static boolean validSeasonGoals(JSONObject snapshot) {
        JSONArray seasonGoals = snapshot.optJSONArray("seasonGoals");
        if (seasonGoals == null) return false;
        for (int index = 0; index < seasonGoals.length(); index++) {
            JSONObject goal = seasonGoals.optJSONObject(index);
            if (goal == null || !validDateKey(goal.optString("weekStart", "")) || !validDateKey(goal.optString("weekEnd", ""))) return false;
            JSONArray items = goal.optJSONArray("items");
            if (goal.optString("seasonId", "").isBlank() || items == null || !validSeasonGoalItems(items)) return false;
            if (!validSeasonGoalState(goal.optString("state", ""))) return false;
            if (goal.has("runningPace") && !goal.isNull("runningPace")
                && !validRunningPace(goal.optJSONObject("runningPace"))) return false;
        }
        return true;
    }

    private static boolean trustedSeasonGoal(JSONObject goal, String expectedWeekStart) {
        if (goal == null || !expectedWeekStart.equals(goal.optString("weekStart", ""))) return false;
        if (goal.optString("seasonId", "").isBlank()) return false;
        return validSeasonGoalState(goal.optString("state", "")) && goal.optJSONArray("items") != null;
    }

    private static boolean validSeasonGoalState(String state) {
        return switch (state) {
            case "achieved", "attempted", "missed", "planned", "future", "inactive", "ready", "empty" -> true;
            default -> false;
        };
    }

    private static boolean validSeasonGoalItems(JSONArray items) {
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) return false;
            String label = item.optString("sportLabel", item.optString("label", "")).trim();
            if (label.isBlank()) return false;
            String state = item.optString("state", "");
            if (!validSeasonItemState(state)) return false;
            if (item.has("detail") && !item.isNull("detail") && !(item.opt("detail") instanceof String)) return false;
        }
        return true;
    }

    private static boolean validSeasonItemState(String state) {
        return switch (state) {
            case "achieved", "attempted", "missed", "planned", "future", "inactive", "done" -> true;
            default -> false;
        };
    }

    private static boolean validRunningPace(JSONObject goal) {
        if (goal == null) return false;
        String mode = goal.optString("mode", "");
        if (mode.isBlank()) return false;
        return validOptionalNumber(goal, "targetPaceSecPerKm", 0, 3_600, false)
            && validOptionalNumber(goal, "baselinePaceSecPerKm", 0, 3_600, false)
            && validOptionalNumber(goal, "actualPaceSecPerKm", 0, 3_600, false)
            && validOptionalNumber(goal, "adaptiveRatePct", 0, 100, false)
            && validOptionalNumber(goal, "avgHeartRateBpm", 0, 240, false);
    }

    private static boolean validNutrition(JSONObject nutrition) {
        return nutrition != null
            && validRequiredNumber(nutrition, "actualKcal", 0, 1_000_000, false)
            && validRequiredNumber(nutrition, "targetKcal", 0, 1_000_000, false)
            && validRequiredNumber(nutrition, "progress", 0, 100, false)
            && validRequiredNumber(nutrition, "proteinG", 0, 100_000, false)
            && validRequiredNumber(nutrition, "carbsG", 0, 100_000, false)
            && validRequiredNumber(nutrition, "fatG", 0, 100_000, false);
    }

    private static boolean validSpending(JSONObject spending) {
        if (spending == null || !validRequiredNumber(spending, "monthSpent", 0, 1_000_000_000_000L, true)) return false;
        if (!validOptionalNumber(spending, "savings", -1_000_000_000_000L, 1_000_000_000_000L, true)) return false;
        if (!validOptionalNumber(spending, "weeklyChangePct", -10_000, 10_000, false)) return false;
        if (spending.has("trend")) {
            JSONArray trend = spending.optJSONArray("trend");
            if (trend == null) return false;
            for (int index = 0; index < trend.length(); index++) {
                Object value = trend.opt(index);
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0) return false;
            }
        }
        if (!spending.has("twoWeek")) return true;
        JSONObject twoWeek = spending.optJSONObject("twoWeek");
        if (twoWeek == null) return false;
        for (String key : new String[] { "spent", "target", "todaySpent", "todayTarget" }) {
            if (!validRequiredNumber(twoWeek, key, 0, 1_000_000_000_000L, true)) return false;
        }
        return (!twoWeek.has("startDate") || validDateKey(twoWeek.optString("startDate", "")))
            && (!twoWeek.has("endDate") || validDateKey(twoWeek.optString("endDate", "")));
    }

    private static boolean validWine(JSONObject snapshot) {
        if (!snapshot.has("wine")) return false;
        if (snapshot.isNull("wine")) return true;
        JSONObject wine = snapshot.optJSONObject("wine");
        if (wine == null || wine.optString("name", "").trim().isBlank()) return false;
        if (!validOptionalNumber(wine, "rating", 0, 5, false)) return false;
        for (String key : new String[] { "note", "imageThumbnail" }) {
            if (wine.has(key) && !wine.isNull(key) && !(wine.opt(key) instanceof String)) return false;
        }
        return true;
    }

    private static boolean validPoints(JSONObject points) {
        if (points == null || !"budget-canonical".equals(points.optString("source", ""))) return false;
        Object rawSchemaVersion = points.opt("schemaVersion");
        if (!isInteger(rawSchemaVersion) || ((Number) rawSchemaVersion).intValue() <= 0) return false;
        if (points.optString("label", "").trim().isBlank()) return false;
        String state = points.optString("state", "");
        if ("missing".equals(state)) return true;
        if (!"ready".equals(state)) return false;
        if (!validRequiredNumber(points, "balance", -1_000_000_000_000L, 1_000_000_000_000L, true)) return false;
        if (!validRequiredNumber(points, "monthPoints", -1_000_000_000_000L, 1_000_000_000_000L, true)) return false;
        if (points.optLong("balance") != points.optLong("monthPoints")) return false;
        for (String key : new String[] { "earnedMonthPoints", "spentMonthPoints", "todayPoints" }) {
            if (!validOptionalNumber(points, key, -1_000_000_000_000L, 1_000_000_000_000L, true)) return false;
        }
        return !points.has("historyOnly") || points.opt("historyOnly") instanceof Boolean;
    }

    private static boolean validRequiredNumber(JSONObject object, String key, double min, double max, boolean integer) {
        return object.has(key) && !object.isNull(key) && validOptionalNumber(object, key, min, max, integer);
    }

    private static boolean validDateKey(String value) {
        return value != null && value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private static boolean validOptionalNumber(JSONObject object, String key, double min, double max, boolean integer) {
        if (!object.has(key) || object.isNull(key)) return true;
        Object value = object.opt(key);
        if (!(value instanceof Number number)) return false;
        double numeric = number.doubleValue();
        return Double.isFinite(numeric) && numeric >= min && numeric <= max && (!integer || numeric == Math.rint(numeric));
    }

    private static boolean isInteger(Object value) {
        if (!(value instanceof Number number)) return false;
        double numeric = number.doubleValue();
        return Double.isFinite(numeric) && numeric == Math.rint(numeric);
    }

}
