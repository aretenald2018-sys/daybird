package com.aretenald.daybird;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DayBirdWidgetLinksTest {
    @Test
    public void tomatoCardsTargetOnlyTheIsolatedTomatoDevPackage() {
        assertEquals(false, DayBirdWidgetLinks.FOOD.webOnly);
        assertEquals(false, DayBirdWidgetLinks.HEALTH.webOnly);
        assertEquals(false, DayBirdWidgetLinks.RUNNING.webOnly);
        assertEquals("tomatodev://diet/today", DayBirdWidgetLinks.FOOD.uri);
        assertEquals("tomatodev://workout/season", DayBirdWidgetLinks.HEALTH.uri);
        assertEquals("tomatodev://workout/running", DayBirdWidgetLinks.RUNNING.uri);
        assertEquals("com.lifestreak.dev", DayBirdWidgetLinks.FOOD.packageName);
        assertEquals("com.lifestreak.dev", DayBirdWidgetLinks.HEALTH.packageName);
        assertEquals("com.lifestreak.dev", DayBirdWidgetLinks.RUNNING.packageName);
        assertEquals("widgetAction", DayBirdWidgetLinks.FOOD.entryExtra);
        assertEquals("diet", DayBirdWidgetLinks.FOOD.entry);
        assertEquals("season", DayBirdWidgetLinks.HEALTH.entry);
        assertEquals("running", DayBirdWidgetLinks.RUNNING.entry);
        assertEquals("https://aretenald2018-sys.github.io/tomatodev/?entry=diet", DayBirdWidgetLinks.FOOD.fallbackUrl);
        assertEquals("https://aretenald2018-sys.github.io/tomatodev/?entry=season", DayBirdWidgetLinks.HEALTH.fallbackUrl);
        assertEquals("https://aretenald2018-sys.github.io/tomatodev/?entry=running", DayBirdWidgetLinks.RUNNING.fallbackUrl);
    }

    @Test
    public void budgetCardsUsePublicBudgetRoutes() {
        assertEquals(false, DayBirdWidgetLinks.SPENDING.webOnly);
        assertEquals(false, DayBirdWidgetLinks.WINE.webOnly);
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
