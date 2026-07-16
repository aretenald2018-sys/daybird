package com.aretenald.daybird;

import static org.junit.Assert.assertEquals;

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
    }

    @Test
    public void budgetCardsUsePublicBudgetRoutes() {
        assertEquals("tomatobudget://spending/month", DayBirdWidgetLinks.SPENDING.uri);
        assertEquals("tomatobudget://wine/recent", DayBirdWidgetLinks.WINE.uri);
        assertEquals("com.aretenald.budget", DayBirdWidgetLinks.SPENDING.packageName);
        assertEquals("com.aretenald.budget", DayBirdWidgetLinks.WINE.packageName);
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
