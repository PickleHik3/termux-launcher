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

import com.google.android.material.bottomsheet.BottomSheetDialog;

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
        fields.add(BuiltinWidgetView.ConfigField.toggle("seconds", "Show seconds", false)
            .withIcon(R.drawable.ic_clock_sheet_stopwatch)
            .withSummary("Display seconds on the clock"));
        fields.add(BuiltinWidgetView.ConfigField.zones("zone1", "zone2", "Time zones")
            .withIcon(R.drawable.ic_clock_sheet_globe)
            .withSummary("Add additional clocks to your widget"));
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
        BottomSheetDialog dialog = BuiltinWidgetConfigSheet.show(activity(), "Clock settings",
            fields(), new Bundle(), config -> false);
        View save = find(dialog.getWindow().getDecorView(), BuiltinWidgetConfigSheet.SAVE_TAG);
        assertNotNull(save);
        save.performClick();
        assertTrue(dialog.isShowing());
        assertTrue("Save is available again", save.isEnabled());
    }

    @Test public void aStoredDraftDismissesTheSheet() {
        int[] calls = {0};
        BottomSheetDialog dialog = BuiltinWidgetConfigSheet.show(activity(), "Clock settings",
            fields(), new Bundle(), config -> { calls[0]++; return true; });
        View save = find(dialog.getWindow().getDecorView(), BuiltinWidgetConfigSheet.SAVE_TAG);
        save.performClick();
        assertEquals(1, calls[0]);
        assertFalse(dialog.isShowing());
    }

    @Test public void theGlassParagraphIsBackAndThereIsNoInfoButtonButCancelAndSave() {
        BottomSheetDialog dialog = BuiltinWidgetConfigSheet.show(activity(), "Clock widget settings",
            fields(), new Bundle(), config -> true);
        View root = dialog.getWindow().getDecorView();
        List<String> shown = new ArrayList<>();
        texts(root, shown);
        String summary = dialog.getContext().getString(R.string.builtin_widget_glass_summary);
        assertTrue(shown.contains(summary));
        assertTrue(shown.contains("Glass"));
        assertNotNull(find(root, BuiltinWidgetConfigSheet.CANCEL_TAG));
        assertNotNull(find(root, BuiltinWidgetConfigSheet.SAVE_TAG));
        assertNotNull(find(root, BuiltinWidgetConfigSheet.GLASS_CHOICE_TAG));
    }

    @Test public void cancelDismissesWithoutSaving() {
        int[] calls = {0};
        BottomSheetDialog dialog = BuiltinWidgetConfigSheet.show(activity(), "Clock widget settings",
            fields(), new Bundle(), config -> { calls[0]++; return true; });
        find(dialog.getWindow().getDecorView(), BuiltinWidgetConfigSheet.CANCEL_TAG)
            .performClick();
        assertEquals(0, calls[0]);
        assertFalse(dialog.isShowing());
    }

    @Test public void theTimeZonesSummaryJoinsTheCities() {
        Activity activity = activity();
        List<String> none = new ArrayList<>();
        assertEquals("None", BuiltinWidgetConfigSheet.zonesSummary(activity, none));
        List<String> two = new ArrayList<>();
        two.add("Tokyo");
        two.add("Kolkata");
        assertEquals("Tokyo, Kolkata", BuiltinWidgetConfigSheet.zonesSummary(activity, two));
    }

    /** Writes the Clock sheet at 411x914dp in the dark theme to app/build/sheet-preview.png. */
    @Config(sdk = Build.VERSION_CODES.P, application = Application.class,
        qualifiers = "w411dp-h914dp-xxhdpi")
    @Test public void sheetPreview() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_Dark);
        List<BuiltinWidgetView.ConfigField> fields = new ArrayList<>();
        List<BuiltinWidgetView.ConfigField.Option> faces = new ArrayList<>();
        for (String face : new String[] {"Analog", "Numerals", "Slab", "Flip"}) {
            faces.add(new BuiltinWidgetView.ConfigField.Option(face, face));
        }
        fields.add(BuiltinWidgetView.ConfigField.choice("face", "Clock face", "Flip", faces,
            (context, value) -> {
                TextView tile = new TextView(context);
                tile.setText(value.substring(0, 1));
                tile.setGravity(android.view.Gravity.CENTER);
                return tile;
            }));
        fields.add(BuiltinWidgetView.ConfigField.toggle("seconds", "Show seconds", true)
            .withIcon(R.drawable.ic_clock_sheet_stopwatch)
            .withSummary("Display seconds on the clock"));
        fields.add(BuiltinWidgetView.ConfigField.toggle("h24", "24-hour time", false)
            .withIcon(R.drawable.ic_clock_sheet_hours_24)
            .withSummary("Use 0\u201323 for hours instead of AM/PM"));
        fields.add(BuiltinWidgetView.ConfigField.zones("zone1", "zone2", "Time zones")
            .withIcon(R.drawable.ic_clock_sheet_globe)
            .withSummary("Add additional clocks to your widget"));
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = BuiltinWidgetConfigSheet.build(dialog.getContext(), "Clock widget settings",
            fields, new Bundle(), config -> true, BuiltinWidgetKind.CLOCK, dialog);
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(411 * density);
        int height = Math.round(914 * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height * 2, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width,
            view.getMeasuredHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(0xFF1B1B1F);
        view.draw(canvas);
        java.io.File out = new java.io.File("build/sheet-preview.png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(out)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        }
        assertTrue(out.length() > 0);
    }
}
