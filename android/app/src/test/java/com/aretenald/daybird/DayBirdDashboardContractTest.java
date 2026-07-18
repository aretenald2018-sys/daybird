package com.aretenald.daybird;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DayBirdDashboardContractTest {
    private JSONObject fixture() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("dashboard-v1.json")) {
            if (stream == null) throw new IllegalStateException("dashboard fixture missing");
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void acceptsDashboardV1Fixture() throws Exception {
        JSONObject snapshot = fixture();
        assertTrue(DayBirdDashboardContract.accepts(snapshot, 0));
        assertFalse(snapshot.has("score"));
        assertFalse(snapshot.has("domains"));
        assertFalse(snapshot.has("weights"));
        assertEquals(
            "110kg · 3세트 × 8회",
            DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13")
                .getJSONArray("items")
                .getJSONObject(0)
                .getString("detail")
        );
    }

    @Test
    public void rejectsUnknownSchemaAndOlderRevision() throws Exception {
        JSONObject unknown = fixture().put("schemaVersion", 2);
        assertFalse(DayBirdDashboardContract.accepts(unknown, 0));
        JSONObject older = fixture().put("revision", 6);
        assertFalse(DayBirdDashboardContract.accepts(older, 7));
    }

    @Test
    public void rejectsUnlabeledOrProductionTomatoSnapshots() throws Exception {
        JSONObject unlabeled = fixture();
        unlabeled.remove("sourceEnvironment");
        assertFalse(DayBirdDashboardContract.accepts(unlabeled, 0));
        assertFalse(DayBirdDashboardContract.hasExpectedSource(unlabeled));

        JSONObject production = fixture().put("sourceEnvironment", "tomatofarm");
        assertFalse(DayBirdDashboardContract.accepts(production, 0));
        assertFalse(DayBirdDashboardContract.hasExpectedSource(production));
        assertTrue(DayBirdDashboardContract.hasExpectedSource(fixture()));
    }

    @Test
    public void requiresFreshValidatedTomatoDevSnapshotMetadata() throws Exception {
        JSONObject missing = fixture();
        missing.remove("tomatoDevSnapshotMetadata");
        assertFalse(DayBirdDashboardContract.accepts(missing, 0));

        JSONObject wrongSchema = fixture();
        wrongSchema.getJSONObject("tomatoDevSnapshotMetadata").put("schemaVersion", 2);
        assertFalse(DayBirdDashboardContract.accepts(wrongSchema, 0));

        JSONObject wrongState = fixture();
        wrongState.getJSONObject("tomatoDevSnapshotMetadata").put("state", "stale");
        assertFalse(DayBirdDashboardContract.accepts(wrongState, 0));

        JSONObject nonNumericTime = fixture();
        nonNumericTime.getJSONObject("tomatoDevSnapshotMetadata").put("generatedAt", "NaN");
        assertFalse(DayBirdDashboardContract.accepts(nonNumericTime, 0));

        JSONObject stale = fixture();
        long dashboardTime = stale.getLong("generatedAtEpochMs");
        stale.getJSONObject("tomatoDevSnapshotMetadata")
            .put("generatedAt", dashboardTime - (24L * 60L * 60L * 1_000L) - 1L);
        assertFalse(DayBirdDashboardContract.accepts(stale, 0));

        JSONObject noSeason = fixture();
        noSeason.getJSONObject("tomatoDevSnapshotMetadata").put("state", "no-season");
        assertTrue(DayBirdDashboardContract.accepts(noSeason, 0));
    }

    @Test
    public void rejectsWrongOwnerOrMissingDisplayedCanonicalFields() throws Exception {
        assertFalse(DayBirdDashboardContract.accepts(fixture(), 0, "another-owner"));
        for (String field : new String[] { "nutrition", "seasonGoals", "running", "spending", "wine", "points" }) {
            JSONObject missing = fixture();
            missing.remove(field);
            assertFalse(field, DayBirdDashboardContract.accepts(missing, 0));
        }
        assertFalse(DayBirdDashboardContract.accepts(fixture().put("budgetUid", ""), 0));
    }

    @Test
    public void validatesCanonicalDisplayFieldsAndSupportsProducerNulls() throws Exception {
        JSONObject badNutrition = fixture();
        badNutrition.getJSONObject("nutrition").put("progress", 101);
        assertFalse(DayBirdDashboardContract.accepts(badNutrition, 0));

        JSONObject badSpending = fixture();
        badSpending.getJSONObject("spending").getJSONObject("twoWeek").put("todaySpent", -1);
        assertFalse(DayBirdDashboardContract.accepts(badSpending, 0));

        JSONObject mismatchedPoints = fixture();
        mismatchedPoints.getJSONObject("points").put("monthPoints", 999);
        assertFalse(DayBirdDashboardContract.accepts(mismatchedPoints, 0));

        JSONObject wrongPointSource = fixture();
        wrongPointSource.getJSONObject("points").put("source", "legacy-estimate");
        assertFalse(DayBirdDashboardContract.accepts(wrongPointSource, 0));

        JSONObject nullWine = fixture().put("wine", JSONObject.NULL);
        assertTrue(DayBirdDashboardContract.accepts(nullWine, 0));

        JSONObject minimalBudgetBase = fixture()
            .put("spending", new JSONObject().put("monthSpent", 456000))
            .put("wine", new JSONObject().put("name", "Budget wine"));
        assertTrue(DayBirdDashboardContract.accepts(minimalBudgetBase, 0));
    }

    @Test
    public void rendersOnlyTheCurrentSeasonWeekHealthGoal() throws Exception {
        JSONObject snapshot = fixture();
        snapshot.remove("seasonGoals");
        JSONObject current = new JSONObject()
            .put("state", "ready")
            .put("seasonId", "summer-2026")
            .put("weekStart", "2026-07-13")
            .put("items", new JSONArray()
                .put(new JSONObject().put("type", "strength").put("label", "squat"))
                .put(new JSONObject().put("type", "running").put("label", "running")));
        snapshot.put("healthGoal", current);
        JSONObject filtered = DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13");
        assertEquals(1, filtered.getJSONArray("items").length());
        assertEquals("squat", filtered.getJSONArray("items").getJSONObject(0).getString("label"));
        assertEquals(2, current.getJSONArray("items").length());

        current.put("weekStart", "2026-07-06");
        assertNull(DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13"));
    }

    @Test
    public void filtersRunningItemsFromSingleSeasonHealthGoal() throws Exception {
        JSONObject snapshot = fixture();
        JSONObject season = seasonGoal("summer-2026", "스쿼트", "planned");
        season.getJSONArray("items").put(new JSONObject()
            .put("type", "running")
            .put("label", "러닝 페이스")
            .put("state", "planned")
            .put("detail", "5:30/km"));
        snapshot.put("seasonGoals", new JSONArray().put(season));

        JSONObject filtered = DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13");
        assertEquals(1, filtered.getJSONArray("items").length());
        assertEquals("strength", filtered.getJSONArray("items").getJSONObject(0).getString("type"));
        assertEquals(2, season.getJSONArray("items").length());

        JSONObject runningOnly = seasonGoal("running-summer", "러닝", "planned");
        runningOnly.getJSONArray("items").getJSONObject(0).put("type", "running");
        snapshot.put("seasonGoals", new JSONArray().put(runningOnly));
        JSONObject empty = DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13");
        assertEquals("empty", empty.getString("state"));
        assertEquals(0, empty.getJSONArray("items").length());
    }

    @Test
    public void neverUsesLegacyOrUntrustedHealthGoals() throws Exception {
        JSONObject snapshot = fixture();
        snapshot.remove("seasonGoals");
        snapshot.put(
            "workouts",
            new JSONArray().put(new JSONObject().put("label", "stale legacy goal"))
        );
        assertNull(DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13"));

        snapshot.put("healthGoal", new JSONObject()
            .put("state", "missing")
            .put("seasonId", "summer-2026")
            .put("weekStart", "2026-07-13")
            .put("items", new JSONArray()));
        assertNull(DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13"));
    }

    @Test
    public void acceptsAdditiveParallelSeasonRunningGoalAndHeartRateFields() throws Exception {
        JSONObject snapshot = fixture();
        snapshot.getJSONObject("running")
            .put("goal", new JSONObject()
                .put("mode", "targetPace")
                .put("targetPaceSecPerKm", 330)
                .put("baselinePaceSecPerKm", 345)
                .put("heartRateCaution", false)
                .put("status", "on-track"))
            .getJSONArray("recent")
            .getJSONObject(0)
            .put("avgHeartRateBpm", 151);
        JSONObject runningSeason = seasonGoal("running-summer", "러닝", "planned");
        runningSeason.getJSONArray("items").getJSONObject(0).put("type", "running");
        runningSeason.put("runningPace", new JSONObject()
            .put("mode", "adaptive")
            .put("targetPaceSecPerKm", 330)
            .put("baselinePaceSecPerKm", 345)
            .put("adaptiveRatePct", 2.5)
            .put("status", "onTrack")
            .put("actualPaceSecPerKm", 338)
            .put("avgHeartRateBpm", 151));
        snapshot.put("seasonGoals", new JSONArray()
            .put(seasonGoal("strength-summer", "스쿼트", "achieved"))
            .put(runningSeason));

        assertTrue(DayBirdDashboardContract.accepts(snapshot, 0));
        JSONObject combined = DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13");
        assertTrue(combined != null && combined.getJSONArray("items").length() == 1);
        assertEquals("strength", combined.getJSONArray("items").getJSONObject(0).getString("type"));
        assertSame(
            snapshot.getJSONObject("running").getJSONObject("goal"),
            DayBirdDashboardContract.currentRunningGoal(snapshot, "2026-07-13")
        );
    }

    @Test
    public void runningPaceFallsBackToTheCurrentSeasonGoal() throws Exception {
        JSONObject snapshot = fixture();
        snapshot.getJSONObject("running").remove("goal");
        JSONObject pace = new JSONObject()
            .put("mode", "adaptive")
            .put("targetPaceSecPerKm", 330)
            .put("adaptiveRatePct", 2.5);
        snapshot.put("seasonGoals", new JSONArray()
            .put(seasonGoal("running-summer", "러닝", "planned").put("runningPace", pace)));

        assertSame(pace, DayBirdDashboardContract.currentRunningGoal(snapshot, "2026-07-13"));
        assertNull(DayBirdDashboardContract.currentRunningGoal(snapshot, "2026-07-06"));
    }

    @Test
    public void rejectsMalformedOptionalDashboardDetailsWithoutChangingSchemaVersion() throws Exception {
        JSONObject badHeartRate = fixture();
        badHeartRate.getJSONObject("running").getJSONArray("recent").getJSONObject(0).put("avgHeartRateBpm", 500);
        assertFalse(DayBirdDashboardContract.accepts(badHeartRate, 0));

        JSONObject malformedSeason = seasonGoal("running-summer", "러닝", "planned");
        malformedSeason.remove("weekEnd");
        JSONObject badSeason = fixture().put("seasonGoals", new JSONArray().put(malformedSeason));
        assertFalse(DayBirdDashboardContract.accepts(badSeason, 0));
        assertTrue(DayBirdDashboardContract.SCHEMA_VERSION == 1);
    }

    @Test
    public void acceptsProducerWeeklyStatesAndLegacyReadyEmptyStates() throws Exception {
        for (String state : new String[] {
            "achieved", "attempted", "missed", "planned", "future", "inactive", "ready", "empty"
        }) {
            JSONObject goal = seasonGoal("summer-2026", "스쿼트", state);
            if ("ready".equals(state)) {
                goal.getJSONArray("items").getJSONObject(0).put("state", "planned");
            } else if ("empty".equals(state)) {
                goal.put("items", new JSONArray());
            }
            JSONObject snapshot = fixture().put(
                "seasonGoals",
                new JSONArray().put(goal)
            );
            assertTrue(state, DayBirdDashboardContract.accepts(snapshot, 0));
            assertEquals(
                state,
                DayBirdDashboardContract.currentHealthGoal(snapshot, "2026-07-13").getString("state")
            );
        }

        JSONObject invalid = fixture().put(
            "seasonGoals",
            new JSONArray().put(seasonGoal("summer-2026", "스쿼트", "unknown"))
        );
        assertFalse(DayBirdDashboardContract.accepts(invalid, 0));
    }

    @Test
    public void acceptsEitherCanonicalRunningAliasAndRejectsMalformedGoal() throws Exception {
        JSONObject recentOnly = fixture();
        recentOnly.getJSONObject("running").remove("records");
        assertTrue(DayBirdDashboardContract.accepts(recentOnly, 0));

        JSONObject recordsOnly = fixture();
        recordsOnly.getJSONObject("running").remove("recent");
        assertTrue(DayBirdDashboardContract.accepts(recordsOnly, 0));

        JSONObject noRuns = fixture();
        noRuns.getJSONObject("running").remove("records");
        noRuns.getJSONObject("running").remove("recent");
        assertFalse(DayBirdDashboardContract.accepts(noRuns, 0));

        JSONObject missingGoalStatus = fixture();
        missingGoalStatus.getJSONObject("running").getJSONObject("goal").remove("status");
        assertFalse(DayBirdDashboardContract.accepts(missingGoalStatus, 0));
    }

    private static JSONObject seasonGoal(String seasonId, String label, String state) throws Exception {
        return new JSONObject()
            .put("state", state)
            .put("seasonId", seasonId)
            .put("seasonName", seasonId)
            .put("weekStart", "2026-07-13")
            .put("weekEnd", "2026-07-19")
            .put("seasonWeek", 3)
            .put("items", new JSONArray().put(new JSONObject()
                .put("type", "strength")
                .put("sportKey", label.toLowerCase())
                .put("label", label)
                .put("state", state)
                .put("detail", "110kg · 3세트 × 8회")));
    }
}
