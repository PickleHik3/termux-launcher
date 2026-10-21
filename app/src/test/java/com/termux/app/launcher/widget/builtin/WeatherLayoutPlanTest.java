package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Seam 4 of spec #101, for Weather: at every span bucket, at the design size, the platform's
 * minimum and the square cells of the real grid, at font scales 1.0, 1.3 and 2.0, with no
 * forecast, a typical one and one with long words: no part overlaps another, every part sits
 * inside the inset, no text is under 11 sp, and the card is a 48 dp target. Each span adds its
 * layer and nothing past the 4×2.
 */
public class WeatherLayoutPlanTest {
    private static final float[] FONT_SCALES = { 1.0f, 1.3f, 2.0f };

    /** Roboto-like advances per pixel of size: tabular digits, a space, everything else. */
    private static final WidgetLayoutPlan.Measure MEASURE = (text, role, numeric, size) -> {
        float em = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            em += Character.isDigit(c) ? 0.57f : c == ' ' ? 0.28f : 0.6f;
        }
        return em * size;
    };

    private static final WeatherLayoutPlanner.Words WORDS = new WeatherLayoutPlanner.Words() {
        @NonNull @Override public String placeUnknown() { return "Current location"; }
        @NonNull @Override public String range(@NonNull String high, @NonNull String low) {
            return high + " / " + low;
        }
        @NonNull @Override public String highLow(@NonNull String high, @NonNull String low) {
            return "H " + high + " · L " + low;
        }
        @NonNull @Override public String rain(int percent) { return percent + "% rain"; }
        @NonNull @Override public String sunrise(@NonNull String time) { return "Sunrise " + time; }
        @NonNull @Override public String sunset(@NonNull String time) { return "Sunset " + time; }
        @NonNull @Override public String nextDays(int count) { return "Next " + count + " days"; }
        @NonNull @Override public String tapToRefresh() { return "Tap to refresh"; }
        @NonNull @Override public String updatedAgo(int minutes) {
            return "Updated " + minutes + " min ago";
        }
        @NonNull @Override public String setAPlace() { return "Set a place"; }
    };

    private static final String EMPTY = "Weather needs a location fix";

    // ----- content --------------------------------------------------------------------------

    @NonNull private static WeatherWidgetModel typical() {
        List<WeatherWidgetModel.Hour> hours = new ArrayList<>();
        int[] temps = { 22, 23, 23, 22, 20 };
        for (int i = 0; i < temps.length; i++) {
            hours.add(new WeatherWidgetModel.Hour(String.valueOf(15 + i), (15 + i) + ":00",
                "", WeatherWidgetModel.Tone.SUN, temps[i] + "°"));
        }
        List<WeatherWidgetModel.Day> days = new ArrayList<>();
        String[] names = { "Thu", "Fri", "Sat", "Sun", "Mon" };
        int[][] range = { {15, 24}, {16, 23}, {15, 25}, {16, 24}, {14, 22} };
        for (int i = 0; i < names.length; i++) {
            days.add(new WeatherWidgetModel.Day(names[i], "", WeatherWidgetModel.Tone.CLOUD,
                range[i][0] + "°", range[i][1] + "°", 0.1f, 0.8f));
        }
        return new WeatherWidgetModel("Lisbon", "21°", "21", "Partly cloudy", "",
            WeatherWidgetModel.Tone.SUN, "24", "15", 60, 12, 10, WeatherWidgetModel.Sun.SET,
            "19:42", hours, days);
    }

    @NonNull private static WeatherWidgetModel longWords() {
        List<WeatherWidgetModel.Hour> hours = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            hours.add(new WeatherWidgetModel.Hour(String.valueOf(10 + i), (10 + i) + ":00",
                "", WeatherWidgetModel.Tone.CLOUD, "-1" + i + "°"));
        }
        List<WeatherWidgetModel.Day> days = new ArrayList<>();
        String[] names = { "Thu.", "Fri.", "Sat.", "Sun.", "Mon." };
        for (String name : names) {
            days.add(new WeatherWidgetModel.Day(name, "", WeatherWidgetModel.Tone.CLOUD,
                "-18°", "-10°", 0f, 1f));
        }
        return new WeatherWidgetModel("Santa Cruz de la Sierra", "-12°", "-12",
            "Thunderstorm with hail", "", WeatherWidgetModel.Tone.CLOUD, "-10", "-18", 99, 88,
            100, WeatherWidgetModel.Sun.RISE, "12:59", hours, days);
    }

    @Nullable private static WeatherWidgetModel content(int index) {
        return index == 0 ? null : index == 1 ? typical() : longWords();
    }

    private static final String[] CONTENT = { "empty", "typical", "long" };

    /** The sizes each bucket is tested at: its design, the platform's minimum, square cells. */
    @NonNull private static List<float[]> sizes(@NonNull BuiltinWidgetSpan span) {
        List<float[]> sizes = new ArrayList<>();
        sizes.add(new float[] { span.widthDp, span.heightDp });
        if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
            sizes.add(new float[] { 80f, 80f });
        } else {
            sizes.add(new float[] { span.minWidthDp, span.minHeightDp });
        }
        float cell = BuiltinWidgetSpan.CELL_WIDTH_DP;
        float gap = BuiltinWidgetSpan.GAP_DP;
        sizes.add(new float[] { span.columns * cell + (span.columns - 1) * gap,
            span.rows * cell + (span.rows - 1) * gap });
        return sizes;
    }

    @NonNull private static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float width,
                                                  float height, float fontScale,
                                                  @Nullable WeatherWidgetModel model) {
        return WeatherLayoutPlanner.plan(span, width, height, fontScale, model, EMPTY, WORDS,
            MEASURE);
    }

    @NonNull private static WidgetLayoutPlan design(@NonNull BuiltinWidgetSpan span,
                                                    @Nullable WeatherWidgetModel model) {
        return plan(span, span.widthDp, span.heightDp, 1f, model);
    }

    // ----- the invariants -------------------------------------------------------------------

    @Test public void everySpanScaleAndContentKeepsItsPartsApartInsideTheInsetAndLegible() {
        List<String> failures = new ArrayList<>();
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float[] size : sizes(span)) {
                for (float scale : FONT_SCALES) {
                    for (int c = 0; c < CONTENT.length; c++) {
                        WidgetLayoutPlan plan = plan(span, size[0], size[1], scale, content(c));
                        String where = String.format(Locale.ROOT, "%s %.0fx%.0f @%.1f %s", span,
                            size[0], size[1], scale, CONTENT[c]);
                        check(plan, where, failures);
                    }
                }
            }
        }
        if (!failures.isEmpty()) fail(String.join("\n", failures));
    }

    private static void check(@NonNull WidgetLayoutPlan plan, @NonNull String where,
                              @NonNull List<String> failures) {
        List<Part> drawn = new ArrayList<>();
        boolean touch = false;
        for (Part part : plan.parts) {
            if (part.type == WidgetLayoutPlan.Type.TOUCH) {
                touch = true;
                if (part.box.width < WidgetTokens.TOUCH_TARGET_DP
                    || part.box.height < WidgetTokens.TOUCH_TARGET_DP) {
                    failures.add(where + ": touch target " + part);
                }
                continue;
            }
            drawn.add(part);
            if (!plan.inset.contains(part.box)) {
                failures.add(where + ": outside the inset " + plan.inset + ": " + part);
            }
            if (part.type == WidgetLayoutPlan.Type.TEXT
                && part.sizeDp < WidgetTokens.TEXT_FLOOR_SP * plan.fontScale - 0.01f) {
                failures.add(where + ": under 11 sp: " + part + " at " + part.sizeDp + " dp");
            }
        }
        if (!touch) failures.add(where + ": no touch target");
        for (int i = 0; i < drawn.size(); i++) {
            for (int j = i + 1; j < drawn.size(); j++) {
                if (drawn.get(i).box.overlaps(drawn.get(j).box)) {
                    failures.add(where + ": overlap " + drawn.get(i) + " / " + drawn.get(j));
                }
            }
        }
        if (plan.parts.size() <= 1) failures.add(where + ": nothing drawn");
    }

    // ----- tokens ---------------------------------------------------------------------------

    @Test public void theInsetIsTwelveOnOneRowAndAtOneByOneAndSixteenOnTwoRows() {
        assertEquals(12f, design(BuiltinWidgetSpan.ONE_BY_ONE, typical()).inset.left, 0f);
        assertEquals(12f, design(BuiltinWidgetSpan.TWO_BY_ONE, typical()).inset.top, 0f);
        assertEquals(12f, design(BuiltinWidgetSpan.FOUR_BY_ONE, typical()).inset.left, 0f);
        assertEquals(16f, design(BuiltinWidgetSpan.TWO_BY_TWO, typical()).inset.left, 0f);
        assertEquals(16f, design(BuiltinWidgetSpan.FOUR_BY_TWO, typical()).inset.top, 0f);
    }

    @Test public void theTemperatureIsTheHeroAndGrowsOnTallSpans() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            Part temp = design(span, typical()).part("temp");
            assertNotNull(span.name(), temp);
            assertEquals(WidgetTokens.Role.HERO, temp.role);
            assertTrue(span + " " + temp.sizeDp, temp.sizeDp >= WidgetTokens.HERO_MIN_SP);
            assertTrue(span + " " + temp.sizeDp, temp.sizeDp <= WidgetTokens.HERO_MAX_SP);
        }
        assertTrue(design(BuiltinWidgetSpan.TWO_BY_TWO, typical()).part("temp").sizeDp
            >= WidgetTokens.HERO_TALL_MIN_SP);
    }

    // ----- layers ---------------------------------------------------------------------------

    @Test public void theOneByOneIsTheSkyTheTemperatureAndTodaysRange() {
        WidgetLayoutPlan plan = design(BuiltinWidgetSpan.ONE_BY_ONE, typical());
        assertTrue(plan.has("glyph"));
        assertTrue(plan.has("temp"));
        assertEquals("24 / 15", plan.part("range").text);
        assertFalse(plan.has("sun"));
        assertTrue(plan.partsStartingWith("hour.").isEmpty());
    }

    @Test public void theTwoByOneAddsTheConditionTheDetailsAndTheNextSunTime() {
        WidgetLayoutPlan plan = design(BuiltinWidgetSpan.TWO_BY_ONE, typical());
        assertEquals("Partly cloudy", plan.part("condition").text);
        assertEquals("H 24 · L 15 · 10% rain", plan.part("details").text);
        assertEquals("Sunset 19:42", plan.part("sun").text);
        assertTrue(plan.partsStartingWith("hour.").isEmpty());
        assertTrue(plan.partsStartingWith("day.").isEmpty());
    }

    @Test public void theTwoByTwoAndTheFourByOneAddTheNextHours() {
        for (BuiltinWidgetSpan span : Arrays.asList(BuiltinWidgetSpan.TWO_BY_TWO,
            BuiltinWidgetSpan.FOUR_BY_ONE)) {
            WidgetLayoutPlan plan = design(span, typical());
            assertTrue(span.name(), plan.partsStartingWith("hour.").size() >= 3 * 3);
            assertTrue(span.name(), plan.has("condition"));
            assertTrue(span.name(), plan.partsStartingWith("day.").isEmpty());
        }
    }

    @Test public void theFourByTwoAddsTheComingDays() {
        WidgetLayoutPlan plan = design(BuiltinWidgetSpan.FOUR_BY_TWO, typical());
        assertTrue(plan.has("hour.0.temp"));
        assertTrue(plan.has("day.0.name"));
        assertTrue(plan.has("day.0.high"));
        assertTrue(plan.has("rule.days"));
    }

    @Test public void aTallerCardShowsMoreLinesNotNewLayersPastTheFourByTwo() {
        assertEquals(BuiltinWidgetSpan.FOUR_BY_TWO, design(BuiltinWidgetSpan.TWO_BY_ONE, typical()).maxSpan);
        WidgetLayoutPlan tall = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 376f, 420f, 1f, typical());
        List<String> roots = Arrays.asList("place", "temp", "glyph", "condition", "details", "sun",
            "rule", "hour", "day", "days", "touch");
        for (Part part : tall.parts) {
            String root = part.id.split("\\.")[0];
            assertTrue(part.id, roots.contains(root));
        }
        assertTrue(tall.has("place"));
        assertTrue(tall.has("sun"));
        assertEquals(5, tall.partsStartingWith("day.").stream()
            .filter(p -> p.id.endsWith(".name")).count());
    }

    @Test public void aShortCardLeavesOutTheLeastUsefulLineFirst() {
        // The 2×2 on square cells is shorter than its design: the place goes before the details.
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, 184f, 184f, 1f, typical());
        assertTrue(plan.has("hour.0.temp"));
        assertTrue(plan.has("condition"));
        if (plan.has("place")) assertTrue(plan.has("details"));
    }

    @Test public void aDryHourSaysNothingAboutRain() {
        WeatherWidgetModel t = typical();
        WeatherWidgetModel dry = new WeatherWidgetModel(t.place, t.temp, t.tempBare, t.condition,
            t.glyph, t.tone, t.high, t.low, t.humidityPct, t.windKmh, 0, t.sun, t.sunTime, t.hours,
            t.days);
        assertEquals("H 24 · L 15", design(BuiltinWidgetSpan.TWO_BY_ONE, dry).part("details").text);
    }

    @Test public void theEmptyCardSaysWhyAndHowToRetry() {
        WidgetLayoutPlan plan = design(BuiltinWidgetSpan.TWO_BY_ONE, null);
        assertTrue(plan.has("message.0"));
        assertTrue(plan.has("hint"));
        assertFalse(plan.has("temp"));
        assertFalse(design(BuiltinWidgetSpan.ONE_BY_ONE, null).has("hint"));
    }

    @NonNull private static WeatherWidgetModel stale(@NonNull WeatherWidgetModel t, int minutes) {
        return new WeatherWidgetModel(t.place, t.temp, t.tempBare, t.condition, t.glyph, t.tone,
            t.high, t.low, t.humidityPct, t.windKmh, t.rainPct, t.sun, t.sunTime, t.hours, t.days,
            minutes);
    }

    @Test public void staleDataSaysHowOldItIsOnTheTallSpans() {
        for (BuiltinWidgetSpan span : Arrays.asList(
            BuiltinWidgetSpan.TWO_BY_TWO,
            BuiltinWidgetSpan.FOUR_BY_TWO)) {
            WidgetLayoutPlan plan = design(span, stale(typical(), 40));
            assertNotNull(span.name(), plan.part("updated"));
            assertEquals(span.name(), "Updated 40 min ago", plan.part("updated").text);
            assertEquals(span.name(), WidgetTokens.Role.LABEL, plan.part("updated").role);
        }
        assertFalse(design(BuiltinWidgetSpan.TWO_BY_TWO, typical()).has("updated"));
    }

    @Test public void theTwoByTwoShowsTheAgeUnderTheHoursAndKeepsTheHours() {
        WidgetLayoutPlan plan = design(BuiltinWidgetSpan.TWO_BY_TWO, stale(typical(), 40));
        assertTrue(plan.has("hour.0.temp"));
        Part updated = plan.part("updated");
        assertTrue(updated.box.top >= plan.part("hour.0.temp").box.bottom());
        assertTrue(updated.box.bottom() <= plan.inset.bottom() + WidgetLayoutPlan.EPSILON);
    }

    @Test public void theAgeNeverOverlapsAnythingAtAnySize() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float[] size : sizes(span)) {
                WidgetLayoutPlan plan = plan(span, size[0], size[1], 1f, stale(typical(), 75));
                Part updated = plan.part("updated");
                if (updated == null) continue;
                for (Part other : plan.parts) {
                    if (other == updated || other.type == WidgetLayoutPlan.Type.TOUCH) continue;
                    assertFalse(span + " " + other.id, other.box.overlaps(updated.box));
                }
            }
        }
    }

    @Test public void withNoLocationTheEmptyCardOffersToSetAPlace() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            WidgetLayoutPlan plan = WeatherLayoutPlanner.plan(span, span.widthDp, span.heightDp,
                1f, null, EMPTY, true, WORDS, MEASURE);
            assertNotNull(span.name(), plan.part("setplace"));
            assertEquals("Set a place", plan.part("setplace").text);
            assertFalse(span.name(), plan.has("hint"));
        }
        assertFalse(design(BuiltinWidgetSpan.TWO_BY_ONE, null).has("setplace"));
    }

    @Test public void longWordsAreCutShortInsideTheirBoxRatherThanPushingPastIt() {
        WidgetLayoutPlan plan = design(BuiltinWidgetSpan.TWO_BY_TWO, longWords());
        Part condition = plan.part("condition");
        assertNotNull(condition);
        assertEquals("Thunderstorm with hail", condition.text);
        assertTrue(condition.box.width <= plan.inset.width + WidgetLayoutPlan.EPSILON);
    }
}
