package com.aretenald.daybird;

import org.json.JSONObject;

final class DayBirdDashboardContract {
    static final int SCHEMA_VERSION = 1;

    private DayBirdDashboardContract() {}

    static boolean accepts(JSONObject snapshot, int currentRevision) {
        return accepts(snapshot, currentRevision, "");
    }

    static boolean accepts(JSONObject snapshot, int currentRevision, String expectedBudgetUid) {
        if (snapshot == null || snapshot.optInt("schemaVersion", 0) != SCHEMA_VERSION) return false;
        Object rawRevision = snapshot.opt("revision");
        if (!isInteger(rawRevision)) return false;
        int nextRevision = ((Number) rawRevision).intValue();
        if (nextRevision <= 0 || nextRevision < currentRevision) return false;
        Object generatedAt = snapshot.opt("generatedAtEpochMs");
        if (!(generatedAt instanceof Number) || !Double.isFinite(((Number) generatedAt).doubleValue()) || ((Number) generatedAt).doubleValue() <= 0) return false;
        if (expectedBudgetUid != null && !expectedBudgetUid.isBlank()
            && !expectedBudgetUid.equals(snapshot.optString("budgetUid", ""))) return false;
        if (!validScore(snapshot, "score")) return false;
        JSONObject domains = snapshot.optJSONObject("domains");
        if (domains == null) return false;
        for (String domain : new String[] { "food", "health", "running", "spending", "wine" }) {
            JSONObject value = domains.optJSONObject(domain);
            if (value == null) return false;
            if (!validScore(value, "score")) return false;
            String freshness = value.optString("freshness", "");
            if (!freshness.equals("fresh") && !freshness.equals("delayed") && !freshness.equals("stale") && !freshness.equals("missing")) {
                return false;
            }
        }
        JSONObject weights = snapshot.optJSONObject("weights");
        if (weights == null) return false;
        int total = 0;
        for (String domain : new String[] { "food", "health", "running", "spending", "wine" }) {
            if (!weights.has(domain)) return false;
            Object rawWeight = weights.opt(domain);
            if (!isInteger(rawWeight)) return false;
            int weight = ((Number) rawWeight).intValue();
            if (weight < 0 || weight > 100) return false;
            total += weight;
        }
        return total == 100;
    }

    private static boolean isInteger(Object value) {
        if (!(value instanceof Number number)) return false;
        double numeric = number.doubleValue();
        return Double.isFinite(numeric) && numeric == Math.rint(numeric);
    }

    private static boolean validScore(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return true;
        Object value = object.opt(key);
        if (!(value instanceof Number number)) return false;
        double score = number.doubleValue();
        return Double.isFinite(score) && score >= 0 && score <= 100;
    }
}
