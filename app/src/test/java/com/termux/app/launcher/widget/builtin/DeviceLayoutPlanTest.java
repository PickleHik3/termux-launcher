package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.DeviceLayoutPlanner.Content;
import com.termux.app.launcher.widget.builtin.DeviceLayoutPlanner.Reading;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Seam 4 of spec #101, for Device (#117): at every span, at the design size, the smallest room
 * Android gives the span and a tall card, at font scales 1.0, 1.3 and 2.0, for an empty, a
 * typical and a long reading, with and without the CPU: nothing overlaps, every part sits inside
 * the inset, no text is under 11 sp, no line is cut short, and every bar is labelled, at least
 * 6 dp high. The emulator showed a 2×1 at font 2.0 with a huge "100%" and its temperature cut to
 * "25.…"; the hero here never passes the hero's largest size and no line is ever wider than its
 * box.
 */
public class DeviceLayoutPlanTest {
    private static final float[] FONT_SCALES = {1.0f, 1.3f, 2.0f};
    /** Proportional enough: digits a little narrower than letters, as in the sans. */
    private static final WidgetLayoutPlan.Measure MEASURE = (text, role, numeric, size) -> {
        float em = 0f;
        for (int i = 0; i < text.length(); i++) {
            em += Character.isDigit(text.charAt(i)) ? 0.57f : 0.6f;
        }
        return em * size;
    };
    private static final List<String> AXIS = Arrays.asList("15:00", "21:00", "03:00", "09:00",
        "now");

    @NonNull private static List<Reading> readings(@NonNull String storage, @NonNull String ram,
                                                   @NonNull String temp) {
        return Arrays.asList(
            new Reading("storage", StatGlyphs.DISK, "Storage", storage),
            new Reading("ram", StatGlyphs.MEMORY, "RAM", ram),
            new Reading("temp", StatGlyphs.TEMPERATURE, "Temp", temp));
    }

    /** Charging, as the mockup: 84 %, full in 1 h 10 m, the CPU's history on. */
    @NonNull private static Content typical(boolean cpu) {
        return new Content("84%", true, "Full in 1 h 10 m", "1 h 10 m",
            readings("41/128 GB", "3.1/7.7 GB", "34°C"), "Battery · 24 h", "24 h", AXIS,
            cpu ? "CPU" : null);
    }

    /** Nothing read yet: no level, no values. */
    @NonNull private static Content empty() {
        return new Content("", false, "No battery reading", "No battery reading",
            readings("", "", ""), "Battery · 24 h", "24 h", AXIS, null);
    }

    /** The widest words and values a phone gives. */
    @NonNull private static Content longest(boolean cpu) {
        return new Content("100%", true, "Full in 10 h 59 m", "10 h 59 m",
            Arrays.asList(
                new Reading("storage", StatGlyphs.DISK, "Speicherplatz", "1024/1024 GB"),
                new Reading("ram", StatGlyphs.MEMORY, "Arbeitsspeicher", "11.9/15.6 GB"),
                new Reading("temp", StatGlyphs.TEMPERATURE, "Temperatur", "45°C")),
            "Akku in den letzten 24 Stunden", "24 h", AXIS, cpu ? "CPU" : null);
    }

    @NonNull private static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float w, float h,
                                                  float fs, @NonNull Content content) {
        return DeviceLayoutPlanner.plan(span, w, h, fs, content, MEASURE);
    }

    /** The rooms a span is checked at: its design, Android's minimum for it, and a tall card. */
    @NonNull private static float[][] rooms(@NonNull BuiltinWidgetSpan span) {
        float minW = span == BuiltinWidgetSpan.ONE_BY_ONE ? 56f : span.minWidthDp;
        float minH = span == BuiltinWidgetSpan.ONE_BY_ONE ? 56f : span.minHeightDp;
        return new float[][] {{span.widthDp, span.heightDp}, {minW, minH},
            {span.widthDp, span.heightDp * 1.6f}};
    }

    private static void check(@NonNull WidgetLayoutPlan plan, @NonNull String what) {
        assertEquals(what + ": the max span", BuiltinWidgetSpan.FOUR_BY_TWO, plan.maxSpan);
        List<Part> parts = plan.parts;
        for (Part part : parts) {
            assertTrue(what + ": " + part + " inside " + plan.inset, plan.inset.contains(part.box));
            if (part.type == WidgetLayoutPlan.Type.TEXT) {
                assertNotNull(what + ": " + part + " has a role", part.role);
                assertTrue(what + ": " + part + " is at least 11 sp",
                    part.sizeDp >= WidgetTokens.TEXT_FLOOR_SP * plan.fontScale - 0.01f);
                float drawn = MEASURE.widthDp(part.text, part.role, part.numeric, part.sizeDp);
                assertTrue(what + ": " + part + " is not cut short (" + drawn + " dp)",
                    drawn <= part.box.width + 0.01f);
                assertFalse(what + ": " + part + " is never a dash", part.text.contains("—"));
                if (part.role == WidgetTokens.Role.HERO) {
                    assertTrue(what + ": the hero " + part.sizeDp + " dp is no larger than its max",
                        part.sizeDp <= WidgetTokens.heroMaxDp() + 0.01f);
                }
            }
            if (part.id.endsWith(".bar")) {
                String reading = part.id.substring(0, part.id.length() - 4);
                assertTrue(what + ": " + part + " at least 6 dp", part.box.height >= 6f - 0.01f);
                assertTrue(what + ": " + part + " has a word label", plan.has(reading + ".label"));
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                Part a = parts.get(i), b = parts.get(j);
                assertFalse(what + ": " + a + " overlaps " + b, a.box.overlaps(b.box));
            }
        }
    }

    // ----- every span, font scale and content -----------------------------------------------

    @Test public void everySpanFontScaleAndContentFitsItsInset() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float[] room : rooms(span)) {
                for (float fs : FONT_SCALES) {
                    Content[] contents = {typical(false), typical(true), empty(), longest(false),
                        longest(true)};
                    String[] names = {"typical", "typical+cpu", "empty", "long", "long+cpu"};
                    for (int c = 0; c < contents.length; c++) {
                        check(plan(span, room[0], room[1], fs, contents[c]),
                            span + " " + room[0] + "x" + room[1] + " font " + fs + " "
                                + names[c]);
                    }
                }
            }
        }
    }

    @Test public void theHeroIsFitToTheSpanNotTheFontScale() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            if (span == BuiltinWidgetSpan.ONE_BY_ONE) continue;
            for (float fs : FONT_SCALES) {
                Part hero = plan(span, span.widthDp, span.heightDp, fs, longest(true)).part("hero");
                assertNotNull(span + " font " + fs + " keeps the level", hero);
                assertTrue(span + " font " + fs + ": " + hero.sizeDp,
                    hero.sizeDp >= WidgetTokens.heroMinDp(span, fs) - 0.01f
                        && hero.sizeDp <= WidgetTokens.heroMaxDp() + 0.01f);
            }
        }
    }

    /** The emulator's case: a 2×1 at font 2.0. */
    @Test public void aTwoByOneAtFontTwoKeepsItsLevelAndAShortLine() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_ONE, 184f, 92f, 2.0f, longest(false));
        Part hero = plan.part("hero");
        assertNotNull(hero);
        assertEquals("100%", hero.text);
        assertTrue("not huge: " + hero.sizeDp, hero.sizeDp <= WidgetTokens.heroMaxDp());
        Part detail = plan.part("detail");
        assertNotNull("the line under the level stays, in its short form", detail);
        assertEquals("10 h 59 m", detail.text);
        assertTrue(plan.has("bolt"));
    }

    // ----- what each span shows -------------------------------------------------------------

    @Test public void theOneByOneIsTheRingFillingTheCardToTheInset() {
        for (float[] card : new float[][] {{88f, 92f}, {89f, 149f}, {120f, 120f}, {110f, 80f}}) {
            WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.ONE_BY_ONE, card[0], card[1], 2f,
                typical(true));
            assertEquals("only the ring", 1, plan.parts.size());
            Part ring = plan.part("ring");
            assertNotNull(ring);
            float side = Math.min(card[0], card[1]) - 2f * OneByOneFit.INSET_DP;
            assertEquals(side, ring.box.width, 0.01f);
            assertEquals(side, ring.box.height, 0.01f);
            assertEquals(plan.card.centerX(), ring.box.centerX(), 0.01f);
            assertEquals(plan.card.centerY(), ring.box.centerY(), 0.01f);
        }
    }

    @Test public void theTwoByOneIsTheLevelAndFullIn() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_ONE, 184f, 92f, 1f, typical(true));
        assertEquals("84%", plan.part("hero").text);
        assertEquals("Full in 1 h 10 m", plan.part("detail").text);
        assertTrue(plan.has("bolt"));
        assertTrue("no bars at 2x1", plan.partsStartingWith("ram").isEmpty());
        assertFalse(plan.has("chart"));
        assertFalse(plan.has("cpu"));
        assertTrue("the bolt leads on one row",
            plan.part("bolt").box.right() <= plan.part("hero").box.left);
    }

    @Test public void theTwoByTwoAddsStorageRamAndTemp() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, 184f, 192f, 1f, typical(true));
        assertTrue(plan.has("hero") && plan.has("detail"));
        for (String id : new String[] {"storage", "ram", "temp"}) {
            assertTrue(id + " bar", plan.has(id + ".bar"));
            assertTrue(id + " value", plan.has(id + ".value"));
        }
        assertEquals("RAM", plan.part("ram.label").text);
        assertEquals("3.1/7.7 GB", plan.part("ram.value").text);
        assertFalse("no chart below 4x2", plan.has("chart"));
        assertFalse("no CPU below 4x2", plan.has("cpu"));
        assertTrue("the bolt follows the level when tall",
            plan.part("bolt").box.left >= plan.part("hero").box.right());
    }

    @Test public void theFourByTwoAddsTheDayAndTheCpuWithAccess() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 376f, 192f, 1f, typical(true));
        assertTrue(plan.has("chart"));
        assertEquals("Battery · 24 h", plan.part("chart.title").text);
        assertTrue(plan.has("cpu") && plan.has("cpu.label") && plan.has("cpu.value"));
        assertTrue(plan.part("chart").box.height >= DeviceLayoutPlanner.CHART_MIN_DP);
        assertTrue(plan.part("cpu").box.height >= DeviceLayoutPlanner.CPU_MIN_DP);
        assertTrue("the newest hour is labelled", plan.has("axis.4"));
        for (String id : new String[] {"storage", "ram", "temp"}) {
            assertTrue(id + " bar", plan.has(id + ".bar"));
        }
        assertTrue(plan.has("rule"));
    }

    @Test public void withoutAccessTheCpuIsAbsentNeverADash() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float fs : FONT_SCALES) {
                WidgetLayoutPlan plan = plan(span, span.widthDp, span.heightDp, fs, typical(false));
                assertTrue(span + " font " + fs, plan.partsStartingWith("cpu").isEmpty());
            }
        }
        WidgetLayoutPlan big = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 376f, 192f, 1f, typical(false));
        assertTrue("the chart takes the room", big.part("chart").box.height
            > plan(BuiltinWidgetSpan.FOUR_BY_TWO, 376f, 192f, 1f, typical(true)).part("chart")
                .box.height);
    }

    @Test public void theFourByOneKeepsTheLevelAndAddsTheBarsThatFit() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_ONE, 376f, 92f, 1f, typical(false));
        assertTrue(plan.has("hero") && plan.has("detail"));
        assertTrue("at least one bar beside it", !plan.partsStartingWith("storage").isEmpty());
        assertFalse(plan.has("chart"));
    }

    @Test public void anUnknownReadingKeepsItsLabelAndTrackWithoutAValue() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, 184f, 192f, 1f, empty());
        assertFalse("no level", plan.has("hero"));
        assertEquals("No battery reading", plan.part("detail").text);
        for (String id : new String[] {"storage", "ram", "temp"}) {
            assertTrue(id, plan.has(id + ".label") && plan.has(id + ".bar"));
            assertFalse(id + " shows no value", plan.has(id + ".value"));
        }
    }

    @Test public void eachSpanAddsToTheOneBelow() {
        WidgetLayoutPlan two = plan(BuiltinWidgetSpan.TWO_BY_ONE, 184f, 92f, 1f, typical(true));
        WidgetLayoutPlan square = plan(BuiltinWidgetSpan.TWO_BY_TWO, 184f, 192f, 1f, typical(true));
        WidgetLayoutPlan wide = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 376f, 192f, 1f, typical(true));
        for (Part part : two.parts) assertTrue("2x2 keeps " + part.id, square.has(part.id));
        for (Part part : square.parts) assertTrue("4x2 keeps " + part.id, wide.has(part.id));
        assertTrue(square.parts.size() > two.parts.size());
        assertTrue(wide.parts.size() > square.parts.size());
        // Past its max span a Device only gains room: a 4x2 twice as tall shows the same parts.
        WidgetLayoutPlan tall = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 376f, 384f, 1f, typical(true));
        assertEquals(ids(wide), ids(tall));
    }

    @NonNull private static List<String> ids(@NonNull WidgetLayoutPlan plan) {
        String[] out = new String[plan.parts.size()];
        for (int i = 0; i < out.length; i++) out[i] = plan.parts.get(i).id;
        List<String> list = Arrays.asList(out);
        Collections.sort(list);
        return list;
    }

    @Test public void theSignatureFollowsEveryWordOnTheCard() {
        assertEquals(typical(true).signature(), typical(true).signature());
        assertFalse(typical(true).signature().equals(typical(false).signature()));
        Content other = new Content("85%", true, "Full in 1 h 10 m", "1 h 10 m",
            readings("41/128 GB", "3.1/7.7 GB", "34°C"), "Battery · 24 h", "24 h", AXIS, "CPU");
        assertFalse(typical(true).signature().equals(other.signature()));
        assertNull(empty().cpuLabel);
    }
}
