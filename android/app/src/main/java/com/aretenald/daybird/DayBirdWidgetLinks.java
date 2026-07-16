package com.aretenald.daybird;

final class DayBirdWidgetLinks {
    static final Link FOOD = new Link(
        "tomatofarm://diet/today",
        "com.lifestreak.app",
        "https://aretenald2018-sys.github.io/tomatofarm/?entry=diet"
    );
    static final Link HEALTH = new Link(
        "tomatofarm://workout/season",
        "com.lifestreak.app",
        "https://aretenald2018-sys.github.io/tomatofarm/?entry=season"
    );
    static final Link RUNNING = new Link(
        "tomatofarm://workout/running",
        "com.lifestreak.app",
        "https://aretenald2018-sys.github.io/tomatofarm/?entry=running"
    );
    static final Link SPENDING = new Link(
        "tomatobudget://spending/month",
        "com.aretenald.budget",
        "https://aretenald2018-sys.github.io/budget/?entry=spending"
    );
    static final Link WINE = new Link(
        "tomatobudget://wine/recent",
        "com.aretenald.budget",
        "https://aretenald2018-sys.github.io/budget/?entry=wine"
    );

    private DayBirdWidgetLinks() {}

    static final class Link {
        final String uri;
        final String packageName;
        final String fallbackUrl;

        Link(String uri, String packageName, String fallbackUrl) {
            this.uri = uri;
            this.packageName = packageName;
            this.fallbackUrl = fallbackUrl;
        }
    }
}
