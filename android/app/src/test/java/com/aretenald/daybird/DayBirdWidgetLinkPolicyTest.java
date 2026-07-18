package com.aretenald.daybird;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DayBirdWidgetLinkPolicyTest {
    @Test
    public void installedAppWithLauncherAlwaysReceivesTheLink() {
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.TARGET_APP,
            DayBirdWidgetLinkPolicy.destination(true, true)
        );
    }

    @Test
    public void installedAppWithoutUsableLauncherNeverFallsBackToWeb() {
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.DAYBIRD,
            DayBirdWidgetLinkPolicy.destination(true, false)
        );
    }

    @Test
    public void webFallbackIsReservedForAnAbsentPackage() {
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.WEB_FALLBACK,
            DayBirdWidgetLinkPolicy.destination(false, false)
        );
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.WEB_FALLBACK,
            DayBirdWidgetLinkPolicy.destination(false, true)
        );
    }
}
