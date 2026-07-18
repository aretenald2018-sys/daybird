package com.aretenald.daybird;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class DayBirdDashboardFormatterTest {
    @Test
    public void runningRecordUsesTwoLinesWithDateHeartRateAndCadence() throws Exception {
        JSONObject record = new JSONObject()
            .put("dateKey", "2026-07-16")
            .put("distanceKm", 8.24)
            .put("paceSecPerKm", 328)
            .put("avgHeartRateBpm", 151)
            .put("cadenceSpm", 176);

        assertEquals(
            "7/16 · 8.2km · 5'28\"/km\n심박 151bpm · 케이던스 176spm",
            DayBirdDashboardFormatter.runningRecordText(record)
        );
    }

    @Test
    public void runningRecordKeepsMissingMetricsVisibleWithoutClippingTheDate() throws Exception {
        JSONObject record = new JSONObject()
            .put("dateKey", "2026-07-07")
            .put("distanceKm", 5.0);

        assertEquals(
            "7/7 · 5.0km · 페이스 —\n심박 — · 케이던스 —",
            DayBirdDashboardFormatter.runningRecordText(record)
        );
    }

    @Test
    public void workoutStatesHaveDistinctIconsAndLabels() throws Exception {
        assertWorkoutState("achieved", "✓ 달성 · 스쿼트");
        assertWorkoutState("done", "✓ 달성 · 스쿼트");
        assertWorkoutState("attempted", "△ 시도 · 스쿼트");
        assertWorkoutState("missed", "! 미달 · 스쿼트");
        assertWorkoutState("planned", "○ 달성 전 · 스쿼트");
        assertWorkoutState("future", "◇ 향후 · 스쿼트");
        assertWorkoutState("inactive", "– 비활성 · 스쿼트");
    }

    @Test
    public void producerDetailWinsWhileLegacyValueAndStatusRemainSupported() throws Exception {
        JSONObject producer = new JSONObject()
            .put("state", "achieved")
            .put("label", "스쿼트")
            .put("detail", "110kg · 3세트 × 8회")
            .put("value", "stale value")
            .put("status", "stale status");
        assertEquals(
            "✓ 달성 · 스쿼트\n110kg · 3세트 × 8회",
            DayBirdDashboardFormatter.workoutText(producer)
        );

        JSONObject legacy = new JSONObject()
            .put("state", "planned")
            .put("label", "벤치프레스")
            .put("value", "70kg × 10")
            .put("status", "3세트");
        assertEquals(
            "○ 달성 전 · 벤치프레스\n70kg × 10 · 3세트",
            DayBirdDashboardFormatter.workoutText(legacy)
        );
    }

    @Test
    public void fiveGoalsKeepEveryIconStateAndLabelAcrossFourBalancedSlots() throws Exception {
        JSONArray goals = new JSONArray()
            .put(workout("achieved", "스쿼트").put("track", "volume"))
            .put(workout("planned", "스쿼트").put("track", "intensity"))
            .put(workout("missed", "데드리프트"))
            .put(workout("attempted", "오버헤드 프레스"))
            .put(workout("inactive", "바벨 로우"));

        List<DayBirdDashboardFormatter.WorkoutGroup> groups =
            DayBirdDashboardFormatter.workoutGroups(goals, 4);

        assertEquals(4, groups.size());
        assertEquals(2, groups.get(0).itemCount);
        assertEquals(1, groups.get(1).itemCount);
        assertEquals(1, groups.get(2).itemCount);
        assertEquals(1, groups.get(3).itemCount);
        assertEquals("✓ 달성 · 스쿼트 (볼륨)\n○ 달성 전 · 스쿼트 (강도)", groups.get(0).text);
        assertEquals("! 미달 · 데드리프트", groups.get(1).text);
        assertEquals("△ 시도 · 오버헤드 프레스", groups.get(2).text);
        assertEquals("– 비활성 · 바벨 로우", groups.get(3).text);
        assertEquals("", groups.get(0).state);
        assertEquals("missed", groups.get(1).state);
        assertFalse(joinGroupText(groups).contains("외 "));
        assertFalse(joinGroupText(groups).contains("세트 × 8회"));
    }

    @Test
    public void longNamesAndNineGoalsRemainPresentWhileGroupsStayBalanced() throws Exception {
        JSONArray goals = new JSONArray();
        for (int index = 1; index <= 9; index++) {
            goals.put(workout(
                index % 2 == 0 ? "achieved" : "planned",
                "바벨 불가리안 스플릿 스쿼트 고중량 프로그램 " + index
            ));
        }

        List<DayBirdDashboardFormatter.WorkoutGroup> groups =
            DayBirdDashboardFormatter.workoutGroups(goals, 4);
        String rendered = joinGroupText(groups);

        assertEquals(4, groups.size());
        assertEquals(3, groups.get(0).itemCount);
        assertEquals(2, groups.get(1).itemCount);
        assertEquals(2, groups.get(2).itemCount);
        assertEquals(2, groups.get(3).itemCount);
        for (int index = 1; index <= 9; index++) {
            assertTrue(rendered.contains("바벨 불가리안 스플릿 스쿼트 고중량 프로그램 " + index));
        }
        assertEquals(6.5f, DayBirdDashboardFormatter.workoutTextSizeSp(9, true), 0.001f);
        assertEquals(8f, DayBirdDashboardFormatter.workoutTextSizeSp(9, false), 0.001f);
    }

    @Test
    public void canonicalWineBalanceIsDisplayedExactlyAndKeepsItsSign() throws Exception {
        JSONObject points = new JSONObject()
            .put("state", "ready")
            .put("label", "와인구매 포인트")
            .put("balance", -24642)
            .put("earnedTwoWeek", 999999);

        assertEquals(
            "와인구매 포인트 -24,642P",
            DayBirdDashboardFormatter.pointSummaryText(points)
        );
    }

    @Test
    public void legacyPointBucketsStillUseNetMonthPoints() throws Exception {
        JSONObject legacy = new JSONObject()
            .put("state", "ready")
            .put("label", "와인구매 포인트")
            .put("earnedMonthPoints", 25358)
            .put("spentMonthPoints", 50000);

        assertEquals(
            "와인구매 포인트 -24,642P",
            DayBirdDashboardFormatter.pointSummaryText(legacy)
        );
    }

    @Test
    public void runningTitleShowsTargetOrAdaptiveGoal() throws Exception {
        assertEquals(
            "러닝 최근 5회 · 목표 5'30\"/km",
            DayBirdDashboardFormatter.runningTitle(
                new JSONObject().put("mode", "targetPace").put("targetPaceSecPerKm", 330),
                false
            )
        );
        assertEquals(
            "러닝 5회 · 주간 2.5% 개선",
            DayBirdDashboardFormatter.runningTitle(
                new JSONObject().put("mode", "adaptive").put("adaptiveRatePct", 2.5),
                true
            )
        );
    }

    private static void assertWorkoutState(String state, String firstLine) throws Exception {
        JSONObject workout = new JSONObject()
            .put("state", state)
            .put("sportLabel", "스쿼트")
            .put("value", "110kg × 8")
            .put("status", "3세트");
        assertEquals(
            firstLine + "\n110kg × 8 · 3세트",
            DayBirdDashboardFormatter.workoutText(workout)
        );
    }

    private static JSONObject workout(String state, String label) throws Exception {
        return new JSONObject()
            .put("state", state)
            .put("sportLabel", label)
            .put("detail", "110kg · 3세트 × 8회");
    }

    private static String joinGroupText(List<DayBirdDashboardFormatter.WorkoutGroup> groups) {
        StringBuilder text = new StringBuilder();
        for (DayBirdDashboardFormatter.WorkoutGroup group : groups) {
            if (text.length() > 0) text.append('\n');
            text.append(group.text);
        }
        return text.toString();
    }
}
