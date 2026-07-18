package com.aretenald.daybird;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class DayBirdDashboardRefreshPolicyTest {
    @Test
    public void connectedPeriodicRefreshRequestsTheTomatoDevOverlay() {
        assertTrue(DayBirdDashboardSync.shouldRequestOverlay(true, "budget-owner", "daybird-device"));
    }

    @Test
    public void incompleteOrSignedOutConnectionsKeepTheLegacyNoopPath() {
        assertFalse(DayBirdDashboardSync.shouldRequestOverlay(false, "budget-owner", "daybird-device"));
        assertFalse(DayBirdDashboardSync.shouldRequestOverlay(true, "", "daybird-device"));
        assertFalse(DayBirdDashboardSync.shouldRequestOverlay(true, "budget-owner", ""));
    }

    @Test
    public void successfulConnectedRefreshDoesNotFetchTheSameSnapshotTwice() throws Exception {
        int[] calls = { 0, 0 };
        DayBirdDashboardSync.runPeriodicRefresh(
            true,
            () -> calls[0]++,
            () -> calls[1]++
        );
        assertEquals(1, calls[0]);
        assertEquals(0, calls[1]);
    }

    @Test
    public void apiFailureUsesTheLastValidSnapshotWithoutRetryWhenFallbackSucceeds() throws Exception {
        int[] calls = { 0, 0 };
        Exception apiFailure = new Exception("overlay unavailable");
        DayBirdDashboardSync.runPeriodicRefresh(
            true,
            () -> {
                calls[0]++;
                throw apiFailure;
            },
            () -> calls[1]++
        );
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
    }

    @Test
    public void disconnectedRefreshUsesSnapshotFetchOnly() throws Exception {
        int[] calls = { 0, 0 };
        DayBirdDashboardSync.runPeriodicRefresh(
            false,
            () -> calls[0]++,
            () -> calls[1]++
        );
        assertEquals(0, calls[0]);
        assertEquals(1, calls[1]);
    }

    @Test
    public void workerRetriesOnlyWhenOverlayAndSnapshotFallbackBothFail() throws Exception {
        Exception apiFailure = new Exception("overlay unavailable");
        Exception snapshotFailure = new Exception("snapshot unavailable");
        try {
            DayBirdDashboardSync.runPeriodicRefresh(
                true,
                () -> { throw apiFailure; },
                () -> { throw snapshotFailure; }
            );
            fail("both failures must reach WorkManager retry policy");
        } catch (Exception error) {
            assertSame(apiFailure, error);
            assertEquals(1, error.getSuppressed().length);
            assertSame(snapshotFailure, error.getSuppressed()[0]);
        }
    }
}
