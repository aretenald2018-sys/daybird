package com.aretenald.daybird;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class DayBirdDashboardLayoutTest {
    @Test
    public void dashboardLayoutsRemoveScoreCardAndAllowEveryWorkoutGroupToWrap() throws Exception {
        String full = layout("daybird_widget_dashboard.xml");
        String compact = layout("daybird_widget_dashboard_compact.xml");

        for (String xml : new String[] { full, compact }) {
            assertFalse(xml.contains("dashboard_score"));
            assertFalse(xml.contains("dashboard_domain_"));
            assertTrue(xml.contains("dashboard_running_title"));
            for (String id : new String[] {
                "dashboard_workout_one",
                "dashboard_workout_two",
                "dashboard_workout_three",
                "dashboard_workout_four",
            }) {
                String tag = textViewTag(xml, id);
                assertTrue(id + " must use its natural multi-line height", tag.contains("android:layout_height=\"wrap_content\""));
                assertTrue(id + " must reserve enough lines for grouped goals", tag.contains("android:maxLines=\"8\""));
                assertTrue(id + " must balance wrapped long names", tag.contains("android:breakStrategy=\"balanced\""));
                assertTrue(id + " must exclude extra font padding", tag.contains("android:includeFontPadding=\"false\""));
                assertFalse(id + " must not use equal-height slots", tag.contains("android:layout_weight"));
                assertFalse(id + " must not ellipsize", tag.contains("android:ellipsize"));
            }
            for (String id : new String[] {
                "dashboard_running_record_1",
                "dashboard_running_record_2",
                "dashboard_running_record_3",
                "dashboard_running_record_4",
                "dashboard_running_record_5",
            }) {
                String tag = textViewTag(xml, id);
                assertTrue(id + " must allow two lines", tag.contains("android:maxLines=\"2\""));
                assertFalse(id + " must not ellipsize", tag.contains("android:ellipsize"));
            }
        }
        assertTrue(full.contains("android:layout_height=\"174dp\""));
        assertTrue(full.contains("android:layout_height=\"214dp\""));
    }

    @Test
    public void compactDashboardLeavesFontScaleHeadroomAtFourHundredTwentyDp() throws Exception {
        String compact = layout("daybird_widget_dashboard_compact.xml");
        String root = tagForId(compact, "dashboard_widget_root");
        String top = tagForId(compact, "dashboard_compact_top_row");
        String middle = tagForId(compact, "dashboard_compact_middle_row");
        String wine = tagForId(compact, "dashboard_wine_card");

        int fixedCards = dpAttribute(top, "android:layout_height")
            + dpAttribute(middle, "android:layout_height")
            + dpAttribute(wine, "android:layout_height");
        int rowMargins = dpAttribute(top, "android:layout_marginTop")
            + dpAttribute(middle, "android:layout_marginTop")
            + dpAttribute(wine, "android:layout_marginTop");
        int rootPadding = dpAttribute(root, "android:padding") * 2;
        int headerAndFontScaleHeadroom = 420 - fixedCards - rowMargins - rootPadding;

        assertTrue("compact widget must reserve at least 48dp for its scaled header", headerAndFontScaleHeadroom >= 48);
        assertTrue(tagForId(compact, "dashboard_compact_title").contains("android:textSize=\"18sp\""));
        assertTrue(tagForId(compact, "dashboard_compact_title").contains("android:includeFontPadding=\"false\""));
        assertTrue(tagForId(compact, "dashboard_date").contains("android:includeFontPadding=\"false\""));
        for (String id : new String[] {
            "dashboard_workout_one",
            "dashboard_workout_two",
            "dashboard_workout_three",
            "dashboard_workout_four",
            "dashboard_running_title",
            "dashboard_running_record_1",
            "dashboard_running_record_2",
            "dashboard_running_record_3",
            "dashboard_running_record_4",
            "dashboard_running_record_5",
        }) {
            assertTrue(id + " must exclude extra font padding", textViewTag(compact, id).contains("android:includeFontPadding=\"false\""));
        }
    }

    private static String layout(String name) throws Exception {
        Path working = Path.of(System.getProperty("user.dir"));
        for (Path candidate : new Path[] {
            working.resolve("app/src/main/res/layout").resolve(name),
            working.resolve("src/main/res/layout").resolve(name),
            working.resolve("android/app/src/main/res/layout").resolve(name),
        }) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("layout not found from " + working + ": " + name);
    }

    private static String textViewTag(String xml, String id) {
        int idIndex = xml.indexOf("@+id/" + id);
        if (idIndex < 0) return "";
        int start = xml.lastIndexOf("<TextView", idIndex);
        int end = xml.indexOf("/>", idIndex);
        return start >= 0 && end > start ? xml.substring(start, end + 2) : "";
    }

    private static String tagForId(String xml, String id) {
        int idIndex = xml.indexOf("@+id/" + id);
        if (idIndex < 0) return "";
        int start = xml.lastIndexOf('<', idIndex);
        int end = xml.indexOf('>', idIndex);
        return start >= 0 && end > start ? xml.substring(start, end + 1) : "";
    }

    private static int dpAttribute(String tag, String attribute) {
        String marker = attribute + "=\"";
        int start = tag.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException(attribute + " missing from " + tag);
        start += marker.length();
        int end = tag.indexOf("dp\"", start);
        if (end < 0) throw new IllegalArgumentException(attribute + " is not dp in " + tag);
        return Integer.parseInt(tag.substring(start, end));
    }
}
