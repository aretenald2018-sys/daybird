package com.aretenald.daybird;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertTrue(DayBirdDashboardContract.accepts(fixture(), 0));
    }

    @Test
    public void rejectsUnknownSchemaAndOlderRevision() throws Exception {
        JSONObject unknown = fixture().put("schemaVersion", 2);
        assertFalse(DayBirdDashboardContract.accepts(unknown, 0));
        JSONObject older = fixture().put("revision", 6);
        assertFalse(DayBirdDashboardContract.accepts(older, 7));
    }

    @Test
    public void rejectsWeightsThatDoNotTotalOneHundred() throws Exception {
        JSONObject invalid = fixture();
        invalid.getJSONObject("weights").put("wine", 11);
        assertFalse(DayBirdDashboardContract.accepts(invalid, 0));
    }

    @Test
    public void rejectsWrongOwnerFractionalWeightsAndOutOfRangeScores() throws Exception {
        assertFalse(DayBirdDashboardContract.accepts(fixture(), 0, "another-owner"));
        JSONObject fractional = fixture();
        fractional.getJSONObject("weights").put("food", 24.5).put("health", 25.5);
        assertFalse(DayBirdDashboardContract.accepts(fractional, 0));
        JSONObject score = fixture();
        score.getJSONObject("domains").getJSONObject("wine").put("score", 101);
        assertFalse(DayBirdDashboardContract.accepts(score, 0));
    }
}
