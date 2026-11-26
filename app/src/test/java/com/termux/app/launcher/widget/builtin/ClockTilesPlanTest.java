package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.ClockTilesPlan.Shows;
import com.termux.app.launcher.widget.builtin.ClockTilesPlan.Tile;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Flip face's glass tiles ("C1 · Glass tiles clock"): the design's own numbers at its
 * 366×154 dp room, the build's rules where it is silent (24-hour, Seconds off, two columns, 1×1),
 * and the card cut around the date and the zones.
 */
public class ClockTilesPlanTest {
    private static final float EPS = 0.01f;
    /** The Label role at density 1. */
    private static final float LABEL = 12f;
    /** The Look's corner in the design (26 px). */
    private static final float LOOK = 26f;

    /** Digits a little over half as wide as tall, a period as Libron's caps about are. */
    private static final ClockTilesPlan.Measure MEASURE = new ClockTilesPlan.Measure() {
        @Override public float pairWidthPx(float sizePx) { return 1.1f * sizePx; }
        @Override public float figureHeightPx(float sizePx) { return .7f * sizePx; }
        @Override public float periodWidthPx(float sizePx) { return 1.4f * sizePx; }
    };

    @NonNull private static ClockTilesPlan design(@NonNull BuiltinWidgetSpan span, boolean is24,
                                                  boolean seconds) {
        return ClockTilesPlan.plan(366f, 154f, 1f, 1f, LABEL, LOOK, span, is24, seconds, MEASURE);
    }

    @Test public void theDesignsThreeTiles() {
        ClockTilesPlan plan = design(BuiltinWidgetSpan.FOUR_BY_TWO, false, true);
        assertEquals(3, plan.tiles.length);
        Tile hours = plan.tiles[0], minutes = plan.tiles[1], side = plan.tiles[2];
        assertEquals(Shows.HOURS, hours.shows);
        assertEquals(Shows.MINUTES, minutes.shows);
        assertEquals(Shows.SIDE, side.shows);
        assertEquals("the third tile is 60 wide", 60f, side.width(), EPS);
        assertEquals("the pairs share the rest", hours.width(), minutes.width(), EPS);
        assertEquals("10 apart", 10f, minutes.left - hours.right, EPS);
        assertEquals("10 apart", 10f, side.left - minutes.right, EPS);
        assertEquals(0f, hours.left, EPS);
        assertEquals(366f, side.right, EPS);
        assertEquals("100 px digits on the 154", 100f, hours.digitPx, EPS);
        assertEquals(100f, minutes.digitPx, EPS);
        assertEquals("the seam across the middle", 77f, hours.seamY(), EPS);
        assertEquals(6f, hours.notchWidth, EPS);
        assertEquals(12f, hours.notchHeight, EPS);
        assertEquals(4f, side.notchWidth, EPS);
        assertEquals(8f, side.notchHeight, EPS);
        assertEquals("the Look's corner", LOOK, hours.radius, EPS);
        assertEquals("the narrow tile's corner held to a third of it (the design's 20)", 20f,
            side.radius, EPS);
        assertTrue("PM over the seam", plan.periodOnSide);
        assertEquals(18f, plan.periodPx, EPS);
        assertEquals(154f / 4f, plan.periodY, EPS);
        assertEquals(side.centerX(), plan.periodX, EPS);
        assertTrue("the seconds under it", plan.seconds);
        assertEquals(30f, plan.secondsPx, EPS);
        assertEquals(154f * 3f / 4f, plan.secondsCenterY, EPS);
        assertFalse(plan.periodInCorner);
    }

    @Test public void twentyFourHourSecondsStandAloneOnTheSeam() {
        ClockTilesPlan plan = design(BuiltinWidgetSpan.FOUR_BY_TWO, true, true);
        assertEquals(3, plan.tiles.length);
        assertFalse(plan.periodOnSide);
        assertFalse(plan.periodInCorner);
        assertEquals(plan.tiles[2].seamY(), plan.secondsCenterY, EPS);
    }

    @Test public void secondsOffIsTwoTilesWithThePeriodInTheHoursCorner() {
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.FOUR_BY_TWO,
            BuiltinWidgetSpan.FOUR_BY_ONE}) {
            ClockTilesPlan plan = design(span, false, false);
            assertEquals(2, plan.tiles.length);
            assertFalse(plan.seconds);
            Tile hours = plan.tiles[0];
            assertEquals(hours.width(), plan.tiles[1].width(), EPS);
            assertEquals(10f, plan.tiles[1].left - hours.right, EPS);
            assertTrue(span + ": the period in the corner", plan.periodInCorner);
            assertTrue("inside the hours tile", plan.periodX > hours.left
                && plan.periodX + MEASURE.periodWidthPx(plan.periodPx) < hours.right);
            float periodBottom = plan.periodY + MEASURE.figureHeightPx(plan.periodPx);
            float periodRight = plan.periodX + MEASURE.periodWidthPx(plan.periodPx);
            float inkTop = hours.seamY() - MEASURE.figureHeightPx(hours.digitPx) / 2f;
            float inkLeft = hours.centerX() - MEASURE.pairWidthPx(hours.digitPx) / 2f;
            assertTrue(span + ": clear of the hours' ink",
                periodBottom <= inkTop + EPS || periodRight <= inkLeft + EPS);
            assertFalse(design(span, true, false).periodInCorner);
        }
    }

    @Test public void twoColumnsNeverShowTheThirdTile() {
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {BuiltinWidgetSpan.TWO_BY_ONE,
            BuiltinWidgetSpan.TWO_BY_TWO}) {
            assertFalse(ClockTilesPlan.showsSeconds(span, true));
            ClockTilesPlan plan = ClockTilesPlan.plan(160f, 120f, 1f, .78f, LABEL, LOOK, span,
                false, true, MEASURE);
            assertEquals(span + ": two tiles", 2, plan.tiles.length);
            assertFalse(plan.seconds);
        }
        assertTrue(ClockTilesPlan.showsSeconds(BuiltinWidgetSpan.FOUR_BY_ONE, true));
        assertFalse(ClockTilesPlan.showsSeconds(BuiltinWidgetSpan.FOUR_BY_ONE, false));
    }

    @Test public void oneByOneIsOneTileTheHoursOverTheMinutes() {
        ClockTilesPlan plan = ClockTilesPlan.plan(88f, 92f, 1f, ClockTilesPlan.scale(92f), LABEL,
            LOOK, BuiltinWidgetSpan.ONE_BY_ONE, true, true, MEASURE);
        assertEquals(1, plan.tiles.length);
        Tile tile = plan.tiles[0];
        assertEquals(Shows.STACK, tile.shows);
        assertEquals(0f, tile.left, EPS);
        assertEquals(88f, tile.right, EPS);
        assertEquals(92f, tile.bottom, EPS);
        assertFalse(plan.seconds);
        assertTrue("a pair fits its half", MEASURE.figureHeightPx(tile.digitPx) < 46f);
        assertTrue("and the tile's width", MEASURE.pairWidthPx(tile.digitPx) < 88f);
    }

    @Test public void everyTileStaysInItsBoxAndOffTheOthers() {
        float[][] rooms = {{366f, 154f}, {350f, 70f}, {160f, 60f}, {160f, 150f}, {80f, 80f},
            {120f, 40f}};
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float[] room : rooms) {
                for (boolean is24 : new boolean[] {false, true}) {
                    for (boolean seconds : new boolean[] {false, true}) {
                        ClockTilesPlan plan = ClockTilesPlan.plan(room[0] * 2f, room[1] * 2f, 2f,
                            ClockTilesPlan.scale(room[1]), LABEL * 2f, LOOK * 2f, span, is24,
                            seconds, MEASURE);
                        String where = span + " " + Arrays.toString(room) + (is24 ? " 24h" : "")
                            + (seconds ? " seconds" : "");
                        Tile last = null;
                        for (Tile tile : plan.tiles) {
                            assertTrue(where, tile.left >= -EPS && tile.right <= room[0] * 2f + EPS
                                && tile.top >= -EPS && tile.bottom <= room[1] * 2f + EPS);
                            assertTrue(where + ": a corner of at most a third",
                                // Held against the tile's whole pixels, as a pane's is.
                                tile.radius <= Math.min(tile.width(), tile.height()) / 3f + .5f);
                            if (last != null) assertTrue(where, tile.left >= last.right - EPS);
                            if (tile.digitPx > 0f) {
                                assertTrue(where + ": digits clear the sides",
                                    MEASURE.pairWidthPx(tile.digitPx) <= tile.width() + EPS);
                            }
                            last = tile;
                        }
                    }
                }
            }
        }
    }

    @Test public void theScaleIsTheCardsHeightOverTheDesigns() {
        assertEquals(1f, ClockTilesPlan.scale(192f), EPS);
        assertEquals(92f / 154f, ClockTilesPlan.scale(92f), EPS);
        ClockTilesPlan plan = ClockTilesPlan.plan(350f, 92f, 1f, ClockTilesPlan.scale(92f), LABEL,
            LOOK, BuiltinWidgetSpan.FOUR_BY_ONE, false, true, MEASURE);
        assertEquals("the gap scales", 10f * 92f / 154f,
            plan.tiles[1].left - plan.tiles[0].right, EPS);
    }

    // ----- the card -------------------------------------------------------------------------

    private static final WidgetLayoutPlan.Measure TEXT =
        (text, role, numeric, sizeDp) -> text.length() * sizeDp * 0.55f;
    private static final ClockLayoutPlanner.FaceMeasure FILL = (widthDp, maxHeightDp) -> maxHeightDp;

    @NonNull private static WidgetLayoutPlan clockPlan(@NonNull BuiltinWidgetSpan span,
                                                       boolean date,
                                                       @NonNull List<ClockLayoutPlanner.ZoneColumn> zones) {
        List<String> dates = date ? Collections.singletonList("Wed 21 Oct")
            : Collections.<String>emptyList();
        return ClockLayoutPlanner.plan(span, span.widthDp, span.heightDp, 1f, ClockFace.FLIP,
            dates, "", zones, FILL, TEXT);
    }

    @Test public void theFaceAloneTakesTheWholeCard() {
        WidgetLayoutPlan plan = clockPlan(BuiltinWidgetSpan.FOUR_BY_TWO, false,
            Collections.<ClockLayoutPlanner.ZoneColumn>emptyList());
        List<Box> ground = new ArrayList<>();
        Box room = ClockTilesPlan.room(plan, 1f, ground);
        assertTrue(ground.isEmpty());
        assertEquals(plan.card.width, room.width, EPS);
        assertEquals(plan.card.height, room.height, EPS);
    }

    @Test public void theDateStandsOnATileUnderTheFace() {
        WidgetLayoutPlan plan = clockPlan(BuiltinWidgetSpan.FOUR_BY_TWO, true,
            Collections.<ClockLayoutPlanner.ZoneColumn>emptyList());
        List<Box> ground = new ArrayList<>();
        float scale = ClockTilesPlan.scale(plan.card.height);
        Box room = ClockTilesPlan.room(plan, scale, ground);
        assertEquals(1, ground.size());
        Box under = ground.get(0);
        Box date = plan.part(ClockLayoutPlanner.DATE).box;
        assertTrue("the date on its tile", under.contains(date));
        assertEquals("a gap over it", ClockTilesPlan.GAP_DP * scale, under.top - room.bottom(), EPS);
        assertEquals(plan.card.height, under.bottom(), EPS);
        assertTrue("the face keeps room", room.height > plan.card.height / 2f);
    }

    @Test public void theZonesStandOnATileBesideTheFaceWithNoHairline() {
        List<ClockLayoutPlanner.ZoneColumn> zones = Collections.singletonList(
            new ClockLayoutPlanner.ZoneColumn("Tokyo", "05:40", "+8h"));
        WidgetLayoutPlan plan = clockPlan(BuiltinWidgetSpan.FOUR_BY_TWO, true, zones);
        for (WidgetLayoutPlan.Part part : plan.parts) {
            assertFalse("no hairline between tiles: " + part.id, part.id.endsWith(".rule"));
        }
        List<Box> ground = new ArrayList<>();
        float scale = ClockTilesPlan.scale(plan.card.height);
        Box room = ClockTilesPlan.room(plan, scale, ground);
        assertEquals(2, ground.size());
        Box beside = ground.get(0);
        assertEquals(plan.card.width, beside.right(), EPS);
        assertEquals(plan.card.height, beside.height, EPS);
        assertEquals(ClockTilesPlan.GAP_DP * scale, beside.left - room.right(), EPS);
        for (WidgetLayoutPlan.Part part : plan.parts) {
            if (part.id.startsWith("zone.")) assertTrue(part.id, beside.contains(part.box));
        }
        assertEquals("the date's tile stops at the zones' gap", room.right(),
            ground.get(1).right(), EPS);
    }
}
