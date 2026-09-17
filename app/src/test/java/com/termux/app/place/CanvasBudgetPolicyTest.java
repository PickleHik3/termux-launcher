package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

/**
 * The vertical budget: what the canvas is held to, and what gives way to hold it there.
 *
 * <p>Driven at the two densities the round was reviewed on — a 180dpi screen at 1.0× text and a
 * 260dpi one at 1.3× — with the keyboard up and down. Text scale is deliberately not an input: a
 * bigger font makes each terminal row taller, not the screen shorter, so the budget is a function
 * of the screen and the bands standing on it and nothing else.
 *
 * <p>The 1300×600 landscape screen the round was measured on is the case at the centre of this
 * file: a status bar, a dock, an A–Z row, an extra-keys row and a keyboard sharing 600px with a
 * terminal reading four lines.
 */
public class CanvasBudgetPolicyTest {

    /** 180dpi. */
    private static final float D180 = 1.125f;
    /** 260dpi, where the review's 1.3× text scale was also set. */
    private static final float D260 = 1.625f;

    /** The measured landscape screen. */
    private static final int LANDSCAPE_HEIGHT_180 = 600;

    private static int px(float dp, float density) {
        return Math.round(dp * density);
    }

    /** Every band at its shipped dp, so a wrong term cannot cancel out against a round number. */
    private static EdgeStackPolicy.Metrics metrics(float density, boolean statusExpanded) {
        return EdgeStackPolicy.Metrics.builder()
            .cutout(0, 0)
            .status(px(statusExpanded ? 96f : 32f, density), px(76f, density))
            .apps(px(56f, density), px(64f, density))
            .az(px(20f, density), px(30f, density))
            .extraKeys(px(40f, density), px(50f, density))
            .build();
    }

    private static PlaceLayout layout(Edge status, RowPlacement apps, RowPlacement keys) {
        return new PlaceLayout(status, apps, true, Edge.BOTTOM, keys, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
    }

    /** The arrangement the review was taken on: everything along the top and bottom. */
    private static PlaceLayout stackedLayout() {
        return layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM);
    }

    // ------------------------------------------------------------------ the floor

    @Test
    public void portraitIsHeldToTheFlatSliceItHasAlwaysBeenHeldTo() {
        // Unchanged, to the pixel, at every screen height: this is the number the accessory
        // stack's ceiling has always reserved, and nothing in portrait is allowed to move.
        for (int height : new int[] {800, 1600, 1920, 2400, 3200}) {
            assertEquals("portrait " + height + " @180",
                px(72f, D180), CanvasBudgetPolicy.canvasFloorPx(height, PlaceOrientation.PORTRAIT, D180));
            assertEquals("portrait " + height + " @260",
                px(72f, D260), CanvasBudgetPolicy.canvasFloorPx(height, PlaceOrientation.PORTRAIT, D260));
        }
    }

    @Test
    public void landscapeIsHeldToAShareOfWhatTheScreenActuallyHas() {
        // 35% of the measured screen, which is more than twice what it was left with.
        assertEquals(210, CanvasBudgetPolicy.canvasFloorPx(
            LANDSCAPE_HEIGHT_180, PlaceOrientation.LANDSCAPE, D180));
        // A 1080px landscape screen at 260dpi wants 378px of that share, which is past the point
        // where more terminal stops being what the screen is short of: it is held at 200dp.
        assertEquals(px(200f, D260), CanvasBudgetPolicy.canvasFloorPx(
            1080, PlaceOrientation.LANDSCAPE, D260));
        assertEquals(Math.round(500 * 0.35f), CanvasBudgetPolicy.canvasFloorPx(
            500, PlaceOrientation.LANDSCAPE, D180));
        // Never below the flat slice portrait keeps, and never past the point where the screen has
        // stopped being short of terminal.
        assertEquals(px(72f, D180), CanvasBudgetPolicy.canvasFloorPx(
            180, PlaceOrientation.LANDSCAPE, D180));
        assertEquals(px(200f, D260), CanvasBudgetPolicy.canvasFloorPx(
            4000, PlaceOrientation.LANDSCAPE, D260));
        // And never more than there is.
        assertEquals(40, CanvasBudgetPolicy.canvasFloorPx(40, PlaceOrientation.LANDSCAPE, D260));
        assertEquals(0, CanvasBudgetPolicy.canvasFloorPx(0, PlaceOrientation.PORTRAIT, D180));
    }

    // ------------------------------------------------------------------ the budget

    @Test
    public void theMeasuredLandscapeScreenGetsItsCanvasBackFromTheKeyboard() {
        int keyboard = 250;
        CanvasBudgetPolicy.Budget budget = CanvasBudgetPolicy.compute(LANDSCAPE_HEIGHT_180,
            PlaceOrientation.LANDSCAPE, keyboard, stackedLayout(), metrics(D180, true), D180);
        int top = px(96f, D180);
        int bottom = px(56f, D180) + px(20f, D180) + px(40f, D180);
        // Everything asked for adds up to more screen than there is once the canvas has its floor,
        // and the keyboard is what hands the difference back.
        assertEquals(top, budget.topPx);
        assertEquals(bottom, budget.bottomPx);
        assertTrue("the keyboard gave way", budget.keyboardPx < keyboard);
        assertEquals(210, budget.canvasPx);
        assertEquals(210, budget.floorPx);
        assertTrue(budget.fits());
        assertEquals(0, budget.shortfallPx);
        assertEquals(LANDSCAPE_HEIGHT_180,
            budget.topPx + budget.bottomPx + budget.keyboardPx + budget.canvasPx);
    }

    @Test
    public void aCompactBarInLandscapeIsRoomTheKeyboardKeeps() {
        int keyboard = 250;
        CanvasBudgetPolicy.Budget expanded = CanvasBudgetPolicy.compute(LANDSCAPE_HEIGHT_180,
            PlaceOrientation.LANDSCAPE, keyboard, stackedLayout(), metrics(D180, true), D180);
        CanvasBudgetPolicy.Budget compact = CanvasBudgetPolicy.compute(LANDSCAPE_HEIGHT_180,
            PlaceOrientation.LANDSCAPE, keyboard, stackedLayout(), metrics(D180, false), D180);
        // The canvas is on its floor either way — that is what the floor is for — and the height
        // the compact bar gives up goes to the keyboard rather than to nothing.
        assertEquals(210, expanded.canvasPx);
        assertEquals(210, compact.canvasPx);
        assertTrue(compact.keyboardPx > expanded.keyboardPx);
        assertEquals(expanded.topPx - compact.topPx, compact.keyboardPx - expanded.keyboardPx);
    }

    @Test
    public void aKeyboardThatIsDownTakesNothingAndNothingGivesWay() {
        CanvasBudgetPolicy.Budget budget = CanvasBudgetPolicy.compute(LANDSCAPE_HEIGHT_180,
            PlaceOrientation.LANDSCAPE, 0, stackedLayout(), metrics(D180, true), D180);
        assertEquals(px(96f, D180), budget.topPx);
        assertEquals(px(56f, D180) + px(20f, D180) + px(40f, D180), budget.bottomPx);
        assertEquals(0, budget.keyboardPx);
        assertTrue("well clear of the floor", budget.canvasPx > budget.floorPx);
        assertTrue(budget.fits());
    }

    @Test
    public void aKeyboardNeverGivesUpMoreThanItsShare() {
        // A short screen with a tall keyboard: the keyboard keeps 60% of what it asked for however
        // far under the floor the canvas is, because a keyboard shrunk past that is not one.
        int keyboard = 400;
        CanvasBudgetPolicy.Budget budget = CanvasBudgetPolicy.compute(500,
            PlaceOrientation.LANDSCAPE, keyboard, stackedLayout(), metrics(D260, true), D260);
        assertTrue(budget.keyboardPx >= Math.round(keyboard * CanvasBudgetPolicy.KEYBOARD_MIN_SHARE));
    }

    @Test
    public void aScreenTooShortForItsArrangementSaysSoInsteadOfPretending() {
        CanvasBudgetPolicy.Budget budget = CanvasBudgetPolicy.compute(300,
            PlaceOrientation.LANDSCAPE, 0, stackedLayout(), metrics(D260, true), D260);
        assertFalse(budget.fits());
        assertTrue(budget.shortfallPx > 0);
        assertEquals(budget.floorPx - budget.canvasPx, budget.shortfallPx);
        // The bottom stack gave what it could; the top stack — the bar the wall's pager rides —
        // gave nothing.
        int bottomAsked = px(56f, D260) + px(20f, D260) + px(40f, D260);
        assertEquals(px(96f, D260), budget.topPx);
        assertTrue(budget.bottomPx < bottomAsked);
        assertTrue(budget.bottomPx
            >= Math.round(bottomAsked * CanvasBudgetPolicy.BOTTOM_STACK_MIN_SHARE));
    }

    @Test
    public void portraitAtBothDensitiesNeverGivesAnythingUp() {
        for (float density : new float[] {D180, D260}) {
            for (int keyboard : new int[] {0, px(300f, density)}) {
                CanvasBudgetPolicy.Budget budget = CanvasBudgetPolicy.compute(px(800f, density),
                    PlaceOrientation.PORTRAIT, keyboard, stackedLayout(), metrics(density, true),
                    density);
                assertEquals("top @" + density, px(96f, density), budget.topPx);
                assertEquals("bottom @" + density,
                    px(56f, density) + px(20f, density) + px(40f, density), budget.bottomPx);
                assertEquals("keyboard @" + density, keyboard, budget.keyboardPx);
                assertTrue("fits @" + density, budget.fits());
            }
        }
    }

    // ------------------------------------------------------------------ panes

    @Test
    public void theBudgetIsTheCanvasWholeSoASplitDividesTheAnswerRatherThanChangingIt() {
        // Panes are not an input, on purpose: a split cuts up the canvas the budget hands back, so
        // the floor is a floor on the whole terminal region and a pane's share is that region
        // divided. What has to hold is that the floor leaves a split worth splitting — the worst
        // case being the keyboard up, which is when a split pane is shortest.
        for (float density : new float[] {D180, D260}) {
            int height = density == D180 ? LANDSCAPE_HEIGHT_180 : 1080;
            CanvasBudgetPolicy.Budget budget = CanvasBudgetPolicy.compute(height,
                PlaceOrientation.LANDSCAPE, px(160f, density), stackedLayout(),
                metrics(density, false), density);
            assertTrue("one pane keeps its floor @" + density, budget.canvasPx >= budget.floorPx);
            // Two panes along the short axis, plus the gap between them: each still holds rows.
            int pane = (budget.canvasPx - px(8f, density)) / 2;
            assertTrue("a split pane keeps rows @" + density, pane >= px(60f, density));
        }
    }

    @Test
    public void aBarStandingDownASideCostsTheCanvasNoHeightAtAll() {
        // The same bar, moved from the top edge to the left one: it is a column there, so the
        // vertical budget stops counting it and the canvas grows by exactly what it took.
        PlaceLayout onTop = stackedLayout();
        PlaceLayout onSide = layout(Edge.LEFT, RowPlacement.BOTTOM, RowPlacement.BOTTOM);
        CanvasBudgetPolicy.Budget top = CanvasBudgetPolicy.compute(LANDSCAPE_HEIGHT_180,
            PlaceOrientation.LANDSCAPE, 0, onTop, metrics(D180, true), D180);
        CanvasBudgetPolicy.Budget side = CanvasBudgetPolicy.compute(LANDSCAPE_HEIGHT_180,
            PlaceOrientation.LANDSCAPE, 0, onSide, metrics(D180, true), D180);
        assertEquals(0, side.topPx);
        assertEquals(top.canvasPx + px(96f, D180), side.canvasPx);
    }
}
