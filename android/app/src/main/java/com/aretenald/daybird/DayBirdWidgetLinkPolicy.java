package com.aretenald.daybird;

final class DayBirdWidgetLinkPolicy {
    enum Destination {
        TARGET_APP,
        DAYBIRD,
        WEB_FALLBACK
    }

    private DayBirdWidgetLinkPolicy() {}

    static Destination destination(boolean webOnly, boolean packageInstalled, boolean launchIntentAvailable) {
        if (webOnly) return Destination.WEB_FALLBACK;
        if (!packageInstalled) return Destination.WEB_FALLBACK;
        return launchIntentAvailable ? Destination.TARGET_APP : Destination.DAYBIRD;
    }
}
