package com.aretenald.daybird;

final class DayBirdWidgetLinks {
    static final Link FOOD = new Link(
        "food",
        "tomatofarm://diet/today",
        "com.lifestreak.app",
        "widgetAction",
        "diet",
        "https://aretenald2018-sys.github.io/tomatofarm/?entry=diet"
    );
    static final Link HEALTH = new Link(
        "health",
        "tomatofarm://workout/season",
        "com.lifestreak.app",
        "widgetAction",
        "season",
        "https://aretenald2018-sys.github.io/tomatofarm/?entry=season"
    );
    static final Link RUNNING = new Link(
        "running",
        "tomatofarm://workout/running",
        "com.lifestreak.app",
        "widgetAction",
        "running",
        "https://aretenald2018-sys.github.io/tomatofarm/?entry=running"
    );
    static final Link SPENDING = new Link(
        "spending",
        "tomatobudget://spending/month",
        "com.aretenald.budget",
        "entry",
        "spending",
        "https://aretenald2018-sys.github.io/budget/?entry=spending"
    );
    static final Link WINE = new Link(
        "wine",
        "tomatobudget://wine/recent",
        "com.aretenald.budget",
        "entry",
        "wine",
        "https://aretenald2018-sys.github.io/budget/?entry=wine"
    );

    private static final Link[] ALL = { FOOD, HEALTH, RUNNING, SPENDING, WINE };

    private DayBirdWidgetLinks() {}

    static Link find(String id) {
        if (id == null || id.isBlank()) return null;
        for (Link link : ALL) {
            if (link.id.equals(id)) return link;
        }
        return null;
    }

    static final class Link {
        final String id;
        final String uri;
        final String packageName;
        final String entryExtra;
        final String entry;
        final String fallbackUrl;

        Link(
            String id,
            String uri,
            String packageName,
            String entryExtra,
            String entry,
            String fallbackUrl
        ) {
            this.id = id;
            this.uri = uri;
            this.packageName = packageName;
            this.entryExtra = entryExtra;
            this.entry = entry;
            this.fallbackUrl = fallbackUrl;
        }
    }
}
