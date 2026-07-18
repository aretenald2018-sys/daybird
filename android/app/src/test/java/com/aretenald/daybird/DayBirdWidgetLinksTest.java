package com.aretenald.daybird;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DayBirdWidgetLinksTest {
    @Test
    public void tomatoCardsUsePublicTomatoRoutes() {
        assertEquals("tomatofarm://diet/today", DayBirdWidgetLinks.FOOD.uri);
        assertEquals("tomatofarm://workout/season", DayBirdWidgetLinks.HEALTH.uri);
        assertEquals("tomatofarm://workout/running", DayBirdWidgetLinks.RUNNING.uri);
        assertEquals("com.lifestreak.app", DayBirdWidgetLinks.FOOD.packageName);
        assertEquals("com.lifestreak.app", DayBirdWidgetLinks.HEALTH.packageName);
        assertEquals("com.lifestreak.app", DayBirdWidgetLinks.RUNNING.packageName);
        assertEquals("widgetAction", DayBirdWidgetLinks.FOOD.entryExtra);
        assertEquals("diet", DayBirdWidgetLinks.FOOD.entry);
        assertEquals("season", DayBirdWidgetLinks.HEALTH.entry);
        assertEquals("running", DayBirdWidgetLinks.RUNNING.entry);
    }

    @Test
    public void budgetCardsUsePublicBudgetRoutes() {
        assertEquals("tomatobudget://spending/month", DayBirdWidgetLinks.SPENDING.uri);
        assertEquals("tomatobudget://wine/recent", DayBirdWidgetLinks.WINE.uri);
        assertEquals("com.aretenald.budget", DayBirdWidgetLinks.SPENDING.packageName);
        assertEquals("com.aretenald.budget", DayBirdWidgetLinks.WINE.packageName);
        assertEquals("entry", DayBirdWidgetLinks.SPENDING.entryExtra);
        assertEquals("spending", DayBirdWidgetLinks.SPENDING.entry);
        assertEquals("wine", DayBirdWidgetLinks.WINE.entry);
    }

    @Test
    public void pendingIntentIdsResolveOnlyAllowlistedLinks() {
        assertSame(DayBirdWidgetLinks.FOOD, DayBirdWidgetLinks.find("food"));
        assertSame(DayBirdWidgetLinks.HEALTH, DayBirdWidgetLinks.find("health"));
        assertSame(DayBirdWidgetLinks.RUNNING, DayBirdWidgetLinks.find("running"));
        assertSame(DayBirdWidgetLinks.SPENDING, DayBirdWidgetLinks.find("spending"));
        assertSame(DayBirdWidgetLinks.WINE, DayBirdWidgetLinks.find("wine"));
        assertNull(DayBirdWidgetLinks.find("https://example.com"));
        assertNull(DayBirdWidgetLinks.find(null));
    }

    @Test
    public void everyCardHasAWebFallback() {
        for (DayBirdWidgetLinks.Link link : new DayBirdWidgetLinks.Link[] {
            DayBirdWidgetLinks.FOOD,
            DayBirdWidgetLinks.HEALTH,
            DayBirdWidgetLinks.RUNNING,
            DayBirdWidgetLinks.SPENDING,
            DayBirdWidgetLinks.WINE,
        }) {
            assertEquals(true, link.fallbackUrl.startsWith("https://"));
        }
    }
}
