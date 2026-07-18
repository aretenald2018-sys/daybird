package com.aretenald.daybird;

final class DayBirdWidgetLinks {
    static final Link FOOD = new Link(
        "food",
        false,
        "tomatodev://diet/today",
        "com.lifestreak.dev",
        "widgetAction",
        "diet",
        "https://aretenald2018-sys.github.io/tomatodev/?entry=diet"
    );
    static final Link HEALTH = new Link(
        "health",
        false,
        "tomatodev://workout/season",
        "com.lifestreak.dev",
        "widgetAction",
        "season",
        "https://aretenald2018-sys.github.io/tomatodev/?entry=season"
    );
    static final Link RUNNING = new Link(
        "running",
        false,
        "tomatodev://workout/running",
        "com.lifestreak.dev",
        "widgetAction",
        "running",
        "https://aretenald2018-sys.github.io/tomatodev/?entry=running"
    );
    static final Link SPENDING = new Link(
        "spending",
        false,
        "tomatobudget://spending/month",
        "com.aretenald.budget",
        "entry",
        "spending",
        "https://aretenald2018-sys.github.io/budget/?entry=spending"
    );
    static final Link WINE = new Link(
        "wine",
        false,
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
        final boolean webOnly;
        final String uri;
        final String packageName;
        final String entryExtra;
        final String entry;
        final String fallbackUrl;

        Link(
            String id,
            boolean webOnly,
            String uri,
            String packageName,
            String entryExtra,
            String entry,
            String fallbackUrl
        ) {
            this.id = id;
            this.webOnly = webOnly;
            this.uri = uri;
            this.packageName = packageName;
            this.entryExtra = entryExtra;
            this.entry = entry;
            this.fallbackUrl = fallbackUrl;
        }
    }
}
