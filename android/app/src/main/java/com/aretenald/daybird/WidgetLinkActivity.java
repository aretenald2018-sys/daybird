package com.aretenald.daybird;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

public class WidgetLinkActivity extends Activity {
    private static final String EXTRA_LINK_ID = "widgetLinkId";

    static PendingIntent pendingIntent(
        Context context,
        int requestCode,
        DayBirdWidgetLinks.Link link
    ) {
        Intent intent = new Intent(context, WidgetLinkActivity.class)
            .putExtra(EXTRA_LINK_ID, link.id)
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
        String linkId = source == null ? "" : source.getStringExtra(EXTRA_LINK_ID);
        DayBirdWidgetLinks.Link link = DayBirdWidgetLinks.find(linkId);
        if (link == null) {
            openDayBird();
            return;
        }

        PackageManager packageManager = getPackageManager();
        boolean packageInstalled = isPackageInstalled(packageManager, link.packageName);
        Intent launchIntent = packageInstalled
            ? packageManager.getLaunchIntentForPackage(link.packageName)
            : null;
        DayBirdWidgetLinkPolicy.Destination destination = DayBirdWidgetLinkPolicy.destination(
            packageInstalled,
            launchIntent != null && launchIntent.getComponent() != null
        );
        if (destination == DayBirdWidgetLinkPolicy.Destination.WEB_FALLBACK) {
            openFallback(link.fallbackUrl);
            return;
        }
        if (destination == DayBirdWidgetLinkPolicy.Destination.DAYBIRD) {
            openDayBird();
            return;
        }

        // Launch the app's normal document and pass only the allowlisted entry.
        // Keeping the legacy custom URI here makes older Budget shells reload a
        // query-string URL, which is the broken-CSS path this bridge replaces.
        Intent target = new Intent(launchIntent)
            .setComponent(launchIntent.getComponent())
            .putExtra(link.entryExtra, link.entry)
            .putExtra("widgetSource", "daybird")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(target);
        } catch (ActivityNotFoundException | SecurityException error) {
            // The package is installed, so never leak this navigation into a browser.
            openDayBird();
        }
    }

    private boolean isPackageInstalled(PackageManager packageManager, String packageName) {
        try {
            packageManager.getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
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
