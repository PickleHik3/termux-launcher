package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The built-in settings sheet: its Save footer, the Glass help and the Additional clocks row. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BuiltinWidgetConfigSheetTest {
    private static Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_Light);
        return activity;
    }

    private static List<BuiltinWidgetView.ConfigField> fields() {
        List<BuiltinWidgetView.ConfigField> fields = new ArrayList<>();
        fields.add(BuiltinWidgetView.ConfigField.toggle("seconds", "Show seconds", false));
        fields.add(BuiltinWidgetView.ConfigField.zones("zone1", "zone2", "Additional clocks"));
        return fields;
    }

    private static View find(View root, Object tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View hit = find(group.getChildAt(i), tag);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static void texts(View root, List<String> out) {
        if (root instanceof TextView) out.add(String.valueOf(((TextView) root).getText()));
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) texts(group.getChildAt(i), out);
        }
    }

    @Test public void savedValuesStillSkipAnUnansweredSwitchAtItsFallback() {
        List<BuiltinWidgetView.ConfigField> fields = new ArrayList<>();
        fields.add(BuiltinWidgetView.ConfigField.toggle("h24", "24-hour format", true));
        Map<String, String> chosen = new LinkedHashMap<>();
        chosen.put("h24", "on");
        Map<String, String> saved = BuiltinWidgetConfigSheet.savedValues(fields, new Bundle(),
            null, chosen, new LinkedHashMap<>());
        assertFalse("not pinned", saved.containsKey("h24"));
        chosen.put("h24", "off");
        saved = BuiltinWidgetConfigSheet.savedValues(fields, new Bundle(), null, chosen,
            new LinkedHashMap<>());
        assertEquals("off", saved.get("h24"));
    }

    @Test public void aFailingListenerKeepsTheSheetOpenAndSaveAvailable() {
        AlertDialog dialog = BuiltinWidgetConfigSheet.show(activity(), "Clock settings",
            fields(), new Bundle(), config -> false);
        View save = find(dialog.getWindow().getDecorView(), BuiltinWidgetConfigSheet.SAVE_TAG);
        assertNotNull(save);
        save.performClick();
        assertTrue(dialog.isShowing());
        assertTrue("Save is available again", save.isEnabled());
    }

    @Test public void aStoredDraftDismissesTheSheet() {
        int[] calls = {0};
        AlertDialog dialog = BuiltinWidgetConfigSheet.show(activity(), "Clock settings",
            fields(), new Bundle(), config -> { calls[0]++; return true; });
        View save = find(dialog.getWindow().getDecorView(), BuiltinWidgetConfigSheet.SAVE_TAG);
        save.performClick();
        assertEquals(1, calls[0]);
        assertFalse(dialog.isShowing());
    }

    @Test public void theGlassParagraphIsOnlyBehindTheHelpButton() {
        AlertDialog dialog = BuiltinWidgetConfigSheet.show(activity(), "Clock settings",
            fields(), new Bundle(), config -> true);
        View root = dialog.getWindow().getDecorView();
        List<String> shown = new ArrayList<>();
        texts(root, shown);
        String summary = dialog.getContext().getString(R.string.builtin_widget_glass_summary);
        assertFalse(shown.contains(summary));
        assertTrue(shown.contains("Glass appearance"));
        assertNotNull(find(root, BuiltinWidgetConfigSheet.GLASS_HELP_TAG));
    }

    @Test public void theAdditionalClocksSummaryCountsWhatResolves() {
        Activity activity = activity();
        assertEquals("None", BuiltinWidgetConfigSheet.zonesSummary(activity, 0));
        assertEquals("1 configured", BuiltinWidgetConfigSheet.zonesSummary(activity, 1));
        assertEquals("2 configured", BuiltinWidgetConfigSheet.zonesSummary(activity, 2));
    }
}
