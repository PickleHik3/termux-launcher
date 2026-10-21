package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Clock's time zones (#113) in its layout plan at 4×1 and 4×2 with none, one and two zones, at
 * font scales 1.0, 1.3 and 2.0: nothing overlaps, every part sits inside the inset, no text is
 * under the 11 sp floor; and the settings that feed it (a migrated Analog keeps its cities).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClockZonesPlanTest {
    private static final float[] FONT_SCALES = {1.0f, 1.3f, 2.0f};
    private static final WidgetLayoutPlan.Measure MEASURE =
        (text, role, numeric, sizeDp) -> text.length() * sizeDp * 0.55f;
    private static final ClockLayoutPlanner.FaceMeasure FACE =
        (widthDp, maxHeightDp) -> Math.min(maxHeightDp, widthDp * 0.3f);

    private static final ClockLayoutPlanner.ZoneColumn TOKYO =
        new ClockLayoutPlanner.ZoneColumn("Tokyo", "22:21", "+8 h");
    private static final ClockLayoutPlanner.ZoneColumn NEW_YORK =
        new ClockLayoutPlanner.ZoneColumn("New York", "09:21", "−5 h");

    private static WidgetLayoutPlan plan(BuiltinWidgetSpan span, float fs, ClockFace face,
                                         List<ClockLayoutPlanner.ZoneColumn> zones) {
        return ClockLayoutPlanner.plan(span, span.widthDp, span.heightDp, fs, face, "Wed 21 Oct",
            "Alarm 07:00", zones, FACE, MEASURE);
    }

    private static void check(WidgetLayoutPlan plan, String what) {
        List<Part> drawn = new ArrayList<>();
        for (Part part : plan.parts) {
            if (part.type == WidgetLayoutPlan.Type.TOUCH) continue;
            drawn.add(part);
            assertTrue(what + ": " + part + " inside " + plan.inset, plan.inset.contains(part.box));
            if (part.role != null) {
                assertTrue(what + ": " + part + " text floor",
                    part.sizeDp >= 11f * plan.fontScale - 0.01f);
            }
        }
        for (int i = 0; i < drawn.size(); i++) {
            for (int j = i + 1; j < drawn.size(); j++) {
                assertFalse(what + ": " + drawn.get(i) + " overlaps " + drawn.get(j),
                    drawn.get(i).box.overlaps(drawn.get(j).box));
            }
        }
    }

    private static int count(WidgetLayoutPlan plan, String prefix, String suffix) {
        int n = 0;
        for (Part part : plan.parts) {
            if (part.id.startsWith(prefix) && part.id.endsWith(suffix)) n++;
        }
        return n;
    }

    @Test public void zoneSpansFitWithNoneOneAndTwoZones() {
        List<List<ClockLayoutPlanner.ZoneColumn>> sets = Arrays.asList(
            new ArrayList<ClockLayoutPlanner.ZoneColumn>(), Arrays.asList(TOKYO),
            Arrays.asList(TOKYO, NEW_YORK));
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.FOUR_BY_ONE,
            BuiltinWidgetSpan.FOUR_BY_TWO}) {
            for (float fs : FONT_SCALES) {
                for (ClockFace face : new ClockFace[] {ClockFace.MINIMAL, ClockFace.ANALOG}) {
                    for (List<ClockLayoutPlanner.ZoneColumn> zones : sets) {
                        check(plan(span, fs, face, zones),
                            span + " " + fs + " " + face + " zones=" + zones.size());
                    }
                }
            }
        }
    }

    @Test public void twoZonesShowAtFourByOneAndOffsetsOnlyAtFourByTwo() {
        List<ClockLayoutPlanner.ZoneColumn> both = Arrays.asList(TOKYO, NEW_YORK);
        WidgetLayoutPlan wide = plan(BuiltinWidgetSpan.FOUR_BY_ONE, 1f, ClockFace.MINIMAL, both);
        assertEquals(2, count(wide, "zone.", ".time"));
        assertEquals(2, count(wide, "zone.", ".rule"));
        assertEquals("no offsets in one row", 0, count(wide, "zone.", ".offset"));
        assertNotNull(wide.part(ClockLayoutPlanner.FACE));
        WidgetLayoutPlan tall = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 1f, ClockFace.MINIMAL, both);
        assertEquals(2, count(tall, "zone.", ".offset"));
        assertEquals("Tokyo", tall.part("zone.0.city").text);
        assertEquals("22:21", tall.part("zone.0.time").text);
        assertEquals("the alarm stays at 4x2", "Alarm 07:00", tall.part("alarm").text);
        assertTrue(tall.part("zone.0.time").box.left > tall.part("face").box.right() - 0.01f);
    }

    @Test public void oneZoneAndNoZonesShowAsSet() {
        WidgetLayoutPlan one = plan(BuiltinWidgetSpan.FOUR_BY_ONE, 1f, ClockFace.SLAB,
            Arrays.asList(TOKYO));
        assertEquals(1, count(one, "zone.", ".time"));
        assertNull(one.part("zone.1.time"));
        WidgetLayoutPlan none = plan(BuiltinWidgetSpan.FOUR_BY_ONE, 1f, ClockFace.SLAB,
            new ArrayList<ClockLayoutPlanner.ZoneColumn>());
        assertEquals(0, count(none, "zone.", ""));
    }

    @Test public void zonesNeverShowBelowFourWide() {
        WidgetLayoutPlan two = plan(BuiltinWidgetSpan.TWO_BY_TWO, 1f, ClockFace.SLAB,
            Arrays.asList(TOKYO, NEW_YORK));
        assertEquals(0, count(two, "zone.", ""));
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, ClockLayoutPlanner.MAX_SPAN);
    }

    @Test public void aMigratedAnalogKeepsItsZonesAndAClearedOneStaysCleared() {
        Bundle opened = BuiltinWidgetAliases.settings("clock.analog", null);
        assertEquals("Europe/London", opened.getString(ClockWidgetSettings.KEY_ZONE1));
        assertEquals(2, ClockWidgetSettings.zones(opened).size());
        Bundle own = new Bundle();
        own.putString(ClockWidgetSettings.KEY_ZONE1, "Tokyo");
        own.putString(ClockWidgetSettings.KEY_ZONE2, "");
        Bundle kept = BuiltinWidgetAliases.settings("clock.analog", own);
        assertEquals(1, ClockWidgetSettings.zones(kept).size());
        assertEquals("Asia/Tokyo", ClockWidgetSettings.zones(kept).get(0).getId());
        assertTrue("a Clock with no zone stored shows none",
            ClockWidgetSettings.zones(new Bundle()).isEmpty());
    }
}
