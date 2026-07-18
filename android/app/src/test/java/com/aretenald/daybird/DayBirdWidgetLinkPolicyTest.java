package com.aretenald.daybird;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DayBirdWidgetLinkPolicyTest {
    @Test
    public void installedAppWithLauncherAlwaysReceivesTheLink() {
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.TARGET_APP,
            DayBirdWidgetLinkPolicy.destination(false, true, true)
        );
    }

    @Test
    public void installedAppWithoutUsableLauncherNeverFallsBackToWeb() {
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.DAYBIRD,
            DayBirdWidgetLinkPolicy.destination(false, true, false)
        );
    }

    @Test
    public void webFallbackIsReservedForAnAbsentPackage() {
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.WEB_FALLBACK,
            DayBirdWidgetLinkPolicy.destination(false, false, false)
        );
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.WEB_FALLBACK,
            DayBirdWidgetLinkPolicy.destination(false, false, true)
        );
    }

    @Test
    public void explicitlyWebOnlyLinksAlwaysUseTheirWebFallback() {
        assertEquals(
            DayBirdWidgetLinkPolicy.Destination.WEB_FALLBACK,
            DayBirdWidgetLinkPolicy.destination(true, true, true)
        );
    }
}
