package com.aretenald.daybird;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class WidgetLinkActivity extends Activity {
    private static final String EXTRA_URI = "widgetLinkUri";
    private static final String EXTRA_PACKAGE = "widgetLinkPackage";
    private static final String EXTRA_FALLBACK = "widgetLinkFallback";

    static PendingIntent pendingIntent(
        Context context,
        int requestCode,
        DayBirdWidgetLinks.Link link
    ) {
        Intent intent = new Intent(context, WidgetLinkActivity.class)
            .putExtra(EXTRA_URI, link.uri)
            .putExtra(EXTRA_PACKAGE, link.packageName)
            .putExtra(EXTRA_FALLBACK, link.fallbackUrl)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openTarget(getIntent());
        finish();
    }

    private void openTarget(Intent source) {
        String uri = source == null ? "" : source.getStringExtra(EXTRA_URI);
        String packageName = source == null ? "" : source.getStringExtra(EXTRA_PACKAGE);
        String fallbackUrl = source == null ? "" : source.getStringExtra(EXTRA_FALLBACK);
        if (uri == null || uri.isEmpty() || packageName == null || packageName.isEmpty()) {
            openDayBird();
            return;
        }

        Intent target = new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(target);
        } catch (ActivityNotFoundException error) {
            openFallback(fallbackUrl);
        }
    }

    private void openFallback(String fallbackUrl) {
        if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
                return;
            } catch (ActivityNotFoundException ignored) {
                // Fall through to DayBird when the device has no browser handler either.
            }
        }
        openDayBird();
    }

    private void openDayBird() {
        startActivity(new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
