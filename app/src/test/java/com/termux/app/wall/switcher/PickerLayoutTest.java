package com.termux.app.wall.switcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;

import com.termux.app.wall.switcher.PickerModel.Cell;
import com.termux.app.wall.switcher.PickerModel.Input;
import com.termux.app.wall.switcher.PickerModel.Kind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/** The numbers are at density 1, so a dp is a px and the arithmetic reads off the constants. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class PickerLayoutTest {

    private static final float W = 400f;
    private static final float H = 800f;
    private static final float D = 28f;
    private static final float EPS = 0.001f;

    private static PickerLayout right(float cy, float top, float bottom, int wanted) {
        return new PickerLayout(W, H, 1f, 380f, cy, D, Edge.RIGHT, top, bottom, wanted, 4);
    }

    private static PickerLayout left(float cy, float top, float bottom, int wanted) {
        return new PickerLayout(W, H, 1f, 20f, cy, D, Edge.LEFT, top, bottom, wanted, 4);
    }

    /** One Home page, two terminal windows, no display running: Terminal has a 2-cell stack. */
    private static PickerModel model() {
        Input in = new Input();
        in.homePageIds = Collections.singletonList(1);
        in.terminalPageId = 2;
        in.terminalWindowLabels = Arrays.asList("zsh", "vim");
        return PickerModel.build(in);
    }

    private static RectF rect(PickerLayout layout, PickerModel model, Cell cell) {
        RectF out = new RectF();
        layout.cellRect(model, cell, out);
        return out;
    }

    @Test public void theRowStandsInwardFromTheDotInFixedOrder() {
        PickerModel model = model();
        PickerLayout r = right(400f, 50f, 750f, 4);
        PickerLayout l = left(400f, 50f, 750f, 4);
        // RIGHT: row ends dot-radius + gap short of the dot; LEFT: starts that far past it.
        assertEquals(138f, rect(r, model, model.column(Kind.HOME).glyph).left, EPS);
        assertEquals(38f, rect(l, model, model.column(Kind.HOME).glyph).left, EPS);
        for (PickerLayout layout : new PickerLayout[] {r, l}) {
            float prev = -1f;
            for (PickerModel.Column col : model.columns()) {
                RectF g = rect(layout, model, col.glyph);
                assertTrue(g.left > prev);
                assertEquals(56f, g.width(), EPS);
                assertEquals(52f, g.height(), EPS);
                prev = g.left;
            }
        }
    }

    @Test public void theRowStaysAtTheDotsHeightWhenThereIsRoom() {
        PickerModel model = model();
        RectF g = rect(right(400f, 50f, 750f, 4), model, model.column(Kind.HOME).glyph);
        assertEquals(374f, g.top, EPS);
        assertEquals(426f, g.bottom, EPS);
    }

    @Test public void nearTheTopTheRowMovesDownAndKeepsTheWantedRows() {
        PickerModel model = model();
        PickerLayout layout = right(60f, 50f, 750f, 4);
        RectF g = rect(layout, model, model.column(Kind.HOME).glyph);
        assertEquals(230f, g.top, EPS);
        assertEquals(4, layout.capacity());
    }

    @Test public void nearTheBottomLimitTheRowMovesUp() {
        PickerModel model = model();
        PickerLayout layout = right(900f, 50f, 750f, 4);
        RectF g = rect(layout, model, model.column(Kind.HOME).glyph);
        assertEquals(692f, g.top, EPS);
        assertEquals(750f - 6f, g.bottom, EPS);
        assertEquals(15, layout.capacity());
    }

    @Test public void aShortBandCapsTheRowsTheBandCanHold() {
        // Band 150 tall: one stack row fits (150 - 12 - 52 - 6 = 80 -> 1).
        PickerLayout layout = right(100f, 50f, 200f, 9);
        assertEquals(1, layout.capacity());
    }

    @Test public void aBandTooShortForTheRowStillGivesTheLowerBoundUp() {
        PickerLayout layout = right(100f, 50f, 100f, 3);
        assertEquals(0, layout.capacity());
    }

    @Test public void capacityIsWholeStackCellsAboveTheRow() {
        // rowTop 374: (374 - 6 - 50 - 6) / 42 = 7.4.
        assertEquals(7, right(400f, 50f, 750f, 4).capacity());
    }

    @Test public void thePillWrapsTheDotAndTheRow() {
        RectF out = new RectF();
        right(400f, 50f, 750f, 4).pillRect(out);
        assertEquals(132f, out.left, EPS);
        assertEquals(402f, out.right, EPS);
        assertEquals(368f, out.top, EPS);
        assertEquals(432f, out.bottom, EPS);
        left(400f, 50f, 750f, 4).pillRect(out);
        assertEquals(-2f, out.left, EPS);
        assertEquals(268f, out.right, EPS);
    }

    @Test public void stackCellsRiseFromTheRowAndTheCardWrapsThem() {
        PickerModel model = model();
        PickerLayout layout = right(400f, 50f, 750f, 4);
        Cell first = model.column(Kind.TERMINAL).cells.get(0);
        Cell second = model.column(Kind.TERMINAL).cells.get(1);
        RectF a = rect(layout, model, first);
        RectF b = rect(layout, model, second);
        assertEquals(326f, a.top, EPS);
        assertEquals(368f, a.bottom, EPS);
        assertEquals(284f, b.top, EPS);
        assertEquals(326f, b.bottom, EPS);
        assertEquals(194f, a.left, EPS);
        RectF card = new RectF();
        assertTrue(layout.stackRect(model, Kind.TERMINAL, card));
        assertEquals(283f, card.top, EPS);
        assertEquals(369f, card.bottom, EPS);
        assertFalse(layout.stackRect(model, Kind.HOME, card));
        assertFalse(layout.stackRect(model, Kind.ASSISTANT, card));
    }

    @Test public void hitTestFindsGlyphStackCellGapAndOutside() {
        PickerModel model = model();
        PickerLayout layout = right(400f, 50f, 750f, 4);
        // Terminal column spans x 194..250.
        assertSame(model.column(Kind.TERMINAL).glyph, layout.cellAt(model, 220f, 400f));
        assertSame(model.column(Kind.TERMINAL).cells.get(0), layout.cellAt(model, 220f, 340f));
        assertSame(model.column(Kind.TERMINAL).cells.get(1), layout.cellAt(model, 220f, 300f));
        assertNull(layout.cellAt(model, 220f, 370f));   // the gap above the glyph
        assertNull(layout.cellAt(model, 220f, 250f));   // above the stack
        assertNull(layout.cellAt(model, 150f, 340f));   // Home has no stack
        assertNull(layout.cellAt(model, 100f, 400f));   // left of the row
        assertNull(layout.cellAt(model, 370f, 400f));   // right of the row, on the dot
        assertNull(layout.cellAt(model, 220f, 440f));   // below the row
    }

    @Test public void staggerRunsFromTheDotOutwards() {
        PickerModel model = model();
        PickerLayout r = right(400f, 50f, 750f, 4);
        PickerLayout l = left(400f, 50f, 750f, 4);
        Cell homeGlyph = model.column(Kind.HOME).glyph;
        Cell assistantGlyph = model.column(Kind.ASSISTANT).glyph;
        Cell terminalWindow = model.column(Kind.TERMINAL).cells.get(0);
        assertEquals(3, r.staggerIndex(model, homeGlyph));
        assertEquals(0, r.staggerIndex(model, assistantGlyph));
        assertEquals(0, l.staggerIndex(model, homeGlyph));
        assertEquals(3, l.staggerIndex(model, assistantGlyph));
        assertEquals(3, r.staggerIndex(model, terminalWindow));
        assertEquals(2, l.staggerIndex(model, terminalWindow));
    }

    @Test public void theLabelStaysOnScreenAndRisesAboveTheFinger() {
        PickerLayout layout = right(400f, 50f, 750f, 4);
        assertEquals(4f, layout.labelX(10f, 100f), EPS);
        assertEquals(296f, layout.labelX(390f, 100f), EPS);
        assertEquals(150f, layout.labelX(200f, 100f), EPS);
        assertEquals(236f, layout.labelTop(300f, 20f), EPS);
        assertEquals(4f, layout.labelTop(30f, 20f), EPS);
    }

    // ---- first-run hint ----

    private static final float HINT_H = 30f;

    private static void assertHintClear(PickerLayout layout, PickerModel model, int rows) {
        float top = layout.hintTop(HINT_H, rows);
        float bottom = top + HINT_H;
        assertTrue(top >= 4f);
        assertTrue(bottom <= H - 4f + EPS);
        RectF r = new RectF();
        layout.pillRect(r);
        assertTrue(bottom <= r.top + EPS || top >= r.bottom - EPS);
        for (Cell cell : model.columns().get(1).cells) {
            layout.cellRect(model, cell, r);
            assertTrue(bottom <= r.top + EPS || top >= r.bottom - EPS);
        }
    }

    @Test public void hintGoesBelowTheRowWithNoStacks() {
        PickerLayout layout = right(400f, 0f, H, 0);
        RectF pill = new RectF();
        layout.pillRect(pill);
        assertEquals(pill.bottom + 4f, layout.hintTop(HINT_H, 0), EPS);
    }

    @Test public void hintClearsATallStack() {
        PickerLayout layout = left(400f, 0f, H, 2);
        assertHintClear(layout, model(), 2);
        PickerLayout tall = right(400f, 0f, H, 6);
        Input in = new Input();
        in.terminalWindowLabels = Arrays.asList("a", "b", "c", "d", "e", "f");
        assertHintClear(tall, PickerModel.build(in), 6);
    }

    @Test public void hintNearTheTopGoesBelow() {
        PickerLayout layout = right(40f, 0f, H, 2);
        assertHintClear(layout, model(), 2);
    }

    @Test public void hintNearTheBottomGoesAboveTheStacks() {
        PickerLayout layout = left(780f, 0f, H, 2);
        assertHintClear(layout, model(), 2);
        RectF pill = new RectF();
        layout.pillRect(pill);
        assertTrue(layout.hintTop(HINT_H, 2) + HINT_H < pill.top);
    }

    @Test public void hintStaysOnScreenWhenNeitherSideHasRoom() {
        PickerLayout layout = right(100f, 0f, 200f, 0);
        float top = layout.hintTop(HINT_H, 0);
        assertTrue(top >= 4f);
        assertTrue(top + HINT_H <= 200f + 600f);
    }

    // ---- Use as Terminal only: three columns ----

    private static PickerModel noHomeModel() {
        Input in = new Input();
        in.homeOffered = false;
        in.terminalPageId = 2;
        in.displayPageId = 3;
        in.assistantPageId = 4;
        in.currentKind = Kind.TERMINAL;
        in.currentPageId = 2;
        in.terminalWindowLabels = Arrays.asList("zsh", "vim");
        return PickerModel.build(in);
    }

    private static final class Calls implements PickerTargets.Navigator {
        final StringBuilder log = new StringBuilder();
        @Override public void goToPage(int pageId) { log.append("page:").append(pageId); }
        @Override public void goHome() { log.append("home"); }
        @Override public void showTerminalWindow(int index) { log.append("window:").append(index); }
        @Override public void goToTerminal() { log.append("terminal"); }
        @Override public void showDisplayApp(int pageId, int windowId) { log.append("app"); }
        @Override public void startDisplay() { log.append("start"); }
        @Override public void addAssistantPage() { log.append("addAssistant"); }
        @Override public void addDisplayPage() { log.append("addDisplay"); }
        @Override public void openSessionBrowser() { log.append("sessions"); }
        @Override public void openDisplayAppList() { log.append("apps"); }
        @Override public void openPageOverview() { log.append("overview"); }
    }

    private static String slideOnto(PickerLayout layout, PickerModel model, Cell cell) {
        RectF r = rect(layout, model, cell);
        Cell under = layout.cellAt(model, r.centerX(), r.centerY());
        assertSame(cell, under);
        Calls calls = new Calls();
        PickerTargets.carryOut(under.target, calls);
        return calls.log.toString();
    }

    @Test public void threeColumnsNarrowTheRowAndKeepItNextToTheDot() {
        PickerModel model = noHomeModel();
        PickerLayout r = new PickerLayout(W, H, 1f, 380f, 400f, D, Edge.RIGHT, 50f, 750f, 2, 3);
        PickerLayout l = new PickerLayout(W, H, 1f, 20f, 400f, D, Edge.LEFT, 50f, 750f, 2, 3);
        assertEquals(194f, rect(r, model, model.column(Kind.TERMINAL).glyph).left, EPS);
        assertEquals(194f + 112f, rect(r, model, model.column(Kind.ASSISTANT).glyph).left, EPS);
        assertEquals(38f, rect(l, model, model.column(Kind.TERMINAL).glyph).left, EPS);
        RectF pill = new RectF();
        r.pillRect(pill);
        assertTrue(pill.left < 194f);
        assertNull(r.cellAt(model, 193f, 410f));
        assertNull(l.cellAt(model, 38f + 168f + 1f, 410f));
    }

    @Test public void staggerRunsFromTheDotOverThreeColumns() {
        PickerModel model = noHomeModel();
        PickerLayout r = new PickerLayout(W, H, 1f, 380f, 400f, D, Edge.RIGHT, 50f, 750f, 2, 3);
        PickerLayout l = new PickerLayout(W, H, 1f, 20f, 400f, D, Edge.LEFT, 50f, 750f, 2, 3);
        assertEquals(2, r.staggerIndex(model, model.column(Kind.TERMINAL).glyph));
        assertEquals(0, r.staggerIndex(model, model.column(Kind.ASSISTANT).glyph));
        assertEquals(0, l.staggerIndex(model, model.column(Kind.TERMINAL).glyph));
        assertEquals(2, l.staggerIndex(model, model.column(Kind.ASSISTANT).glyph));
    }

    @Test public void aSlideOntoEachRemainingCellReachesItsTarget() {
        PickerModel model = noHomeModel();
        PickerLayout layout = new PickerLayout(W, H, 1f, 380f, 400f, D, Edge.RIGHT, 50f, 750f, 2, 3);
        assertEquals("", slideOnto(layout, model, model.column(Kind.TERMINAL).glyph));
        assertEquals("page:3", slideOnto(layout, model, model.column(Kind.DISPLAY).glyph));
        assertEquals("page:4", slideOnto(layout, model, model.column(Kind.ASSISTANT).glyph));
        assertEquals("window:0", slideOnto(layout, model, model.column(Kind.TERMINAL).cells.get(0)));
        assertEquals("window:1", slideOnto(layout, model, model.column(Kind.TERMINAL).cells.get(1)));
    }
}
