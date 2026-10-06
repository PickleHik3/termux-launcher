package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.activities.SettingsBackStackState;
import com.termux.app.surfaces.AppearanceSurfaceController.PageId;

import org.junit.Test;

/**
 * Where the Appearance surface was left: the window it is kept for (Settings' own), what each
 * page keeps, the round trip through the preferences string, and that anything unreadable falls
 * back to the Overview (null). Plain JUnit: the class touches no Android framework class.
 */
public class AppearanceReturnStateTest {

    private static final long LEFT_AT = 1_700_000_000_000L;

    @Test
    public void freshInsideTheWindowAndStaleAtItsEnd() {
        AppearanceReturnState state = new AppearanceReturnState.Builder(PageId.LOOK).build(LEFT_AT);
        assertTrue("at once", state.isFresh(LEFT_AT));
        assertTrue("a minute short of the window",
            state.isFresh(LEFT_AT + SettingsBackStackState.RETAIN_WINDOW_MS - 60_000L));
        assertFalse("at the window's end", state.isFresh(LEFT_AT + SettingsBackStackState.RETAIN_WINDOW_MS));
        assertFalse("a moment in the future is not trusted", state.isFresh(LEFT_AT - 1L));
        assertFalse("an unknown moment is never fresh",
            new AppearanceReturnState.Builder(PageId.LOOK).build(0L).isFresh(LEFT_AT));
    }

    @Test
    public void freshFromDropsAStaleState() {
        String saved = new AppearanceReturnState.Builder(PageId.LAYOUT).place("WIDGETS")
            .build(LEFT_AT).serialize();
        assertNotNull(AppearanceReturnState.freshFrom(saved, LEFT_AT + 1_000L));
        assertNull("after the window it opens as it always has", AppearanceReturnState.freshFrom(saved,
            LEFT_AT + SettingsBackStackState.RETAIN_WINDOW_MS + 1L));
    }

    @Test
    public void theOverviewKeepsItsCentredCardAndNothingElse() {
        AppearanceReturnState state = roundTrip(new AppearanceReturnState.Builder(PageId.OVERVIEW)
            .wallpaperSlot("LOCK").look("TERMINAL", true).place("WIDGETS").iconsScroll(120));
        assertEquals(PageId.OVERVIEW, state.page);
        assertNull(state.editorMode());
        assertEquals("LOCK", state.wallpaperSlot);
        assertNull(state.target);
        assertFalse(state.custom);
        assertNull(state.place);
        assertEquals(0, state.iconsScrollPx);
    }

    @Test
    public void lookKeepsItsSelectionAndTheCustomStop() {
        AppearanceReturnState state = roundTrip(new AppearanceReturnState.Builder(PageId.LOOK)
            .look("TERMINAL", true).wallpaperSlot("HOME").place("WIDGETS").iconsScroll(40));
        assertEquals(PageId.LOOK, state.page);
        assertEquals(EditorMode.LOOK, state.editorMode());
        assertEquals("TERMINAL", state.target);
        assertTrue(state.custom);
        assertEquals("HOME", state.wallpaperSlot);
        assertNull("Layout's place is not Look's", state.place);
        assertEquals(0, state.iconsScrollPx);
    }

    @Test
    public void lookAtALookStopKeepsNoSelection() {
        AppearanceReturnState state = roundTrip(new AppearanceReturnState.Builder(PageId.LOOK)
            .look(null, false));
        assertNull(state.target);
        assertFalse("the stop is the stored look's own", state.custom);
    }

    @Test
    public void layoutKeepsItsPlace() {
        AppearanceReturnState state = roundTrip(new AppearanceReturnState.Builder(PageId.LAYOUT)
            .place("DISPLAY").look("DOCK", true));
        assertEquals(PageId.LAYOUT, state.page);
        assertEquals(EditorMode.LAYOUT, state.editorMode());
        assertEquals("DISPLAY", state.place);
        assertNull(state.target);
        assertFalse(state.custom);
    }

    @Test
    public void iconPackKeepsTheRowsScroll() {
        AppearanceReturnState state = roundTrip(new AppearanceReturnState.Builder(PageId.ICONS)
            .iconsScroll(312));
        assertEquals(PageId.ICONS, state.page);
        assertEquals(EditorMode.ICONS, state.editorMode());
        assertEquals(312, state.iconsScrollPx);
        assertEquals("a negative scroll is the row's start", 0, roundTrip(
            new AppearanceReturnState.Builder(PageId.ICONS).iconsScroll(-8)).iconsScrollPx);
    }

    @Test
    public void theRoundTripKeepsTheMomentLeft() {
        AppearanceReturnState state = roundTrip(new AppearanceReturnState.Builder(PageId.ICONS));
        assertEquals(LEFT_AT, state.leftAtEpochMs);
    }

    @Test
    public void unreadableStatesFallBackToTheOverview() {
        assertNull(AppearanceReturnState.parse(null));
        assertNull(AppearanceReturnState.parse(""));
        assertNull(AppearanceReturnState.parse("   "));
        assertNull("not JSON", AppearanceReturnState.parse("{page:"));
        assertNull("not an object", AppearanceReturnState.parse("[1, 2]"));
        assertNull("no page", AppearanceReturnState.parse("{\"left_at_epoch_ms\": 5}"));
        assertNull("a page this build does not have",
            AppearanceReturnState.parse("{\"page\": \"SESSIONS\", \"left_at_epoch_ms\": 5}"));
        assertNull("freshFrom of corrupt JSON", AppearanceReturnState.freshFrom("{]", LEFT_AT));
    }

    @Test
    public void aStateWithoutAMomentIsReadButNeverFresh() {
        AppearanceReturnState state = AppearanceReturnState.parse("{\"page\": \"LOOK\"}");
        assertNotNull(state);
        assertEquals(PageId.LOOK, state.page);
        assertFalse(state.isFresh(LEFT_AT));
        assertNull(AppearanceReturnState.freshFrom("{\"page\": \"LOOK\"}", LEFT_AT));
    }

    @Test
    public void blankNamesReadAsNone() {
        AppearanceReturnState state = roundTrip(new AppearanceReturnState.Builder(PageId.LOOK)
            .look(" ", false).wallpaperSlot(""));
        assertNull(state.target);
        assertNull(state.wallpaperSlot);
    }

    private static AppearanceReturnState roundTrip(AppearanceReturnState.Builder builder) {
        AppearanceReturnState parsed = AppearanceReturnState.parse(builder.build(LEFT_AT).serialize());
        assertNotNull(parsed);
        return parsed;
    }
}
