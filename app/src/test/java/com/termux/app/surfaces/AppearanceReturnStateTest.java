package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.activities.SettingsBackStackState;
import com.termux.app.surfaces.AppearanceSurfaceController.PageId;

import org.junit.Test;

/** Where the person left the editor, written down and read back. */
public class AppearanceReturnStateTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void theCardAndThePickersCardComeBack() {
        AppearanceReturnState state = new AppearanceReturnState.Builder(PageId.EDITOR)
            .card(EditorCard.DOCK).wallpaperSlot("HOME").build(NOW);
        AppearanceReturnState back = AppearanceReturnState.parse(state.serialize());
        assertNotNull(back);
        assertEquals(PageId.EDITOR, back.page);
        assertEquals(EditorCard.DOCK, back.editorCard());
        assertEquals("HOME", back.wallpaperSlot);
        assertEquals(NOW, back.leftAtEpochMs);
    }

    @Test
    public void noCardReadsAsNone() {
        AppearanceReturnState state = new AppearanceReturnState.Builder(PageId.WALLPAPER)
            .card(null).build(NOW);
        AppearanceReturnState back = AppearanceReturnState.parse(state.serialize());
        assertNotNull(back);
        assertEquals(PageId.WALLPAPER, back.page);
        assertNull(back.editorCard());
    }

    @Test
    public void thePagesBeforeTheMergeReadAsTheEditor() {
        for (String old : new String[] {"LOOK", "LAYOUT", "ICONS", "OVERVIEW"}) {
            AppearanceReturnState back = AppearanceReturnState.parse(
                "{\"page\":\"" + old + "\",\"target\":\"DOCK\",\"left_at_epoch_ms\":" + NOW + "}");
            assertNotNull(old, back);
            assertEquals(old, PageId.EDITOR, back.page);
            assertNull("an old selection is not a card", back.editorCard());
        }
    }

    @Test
    public void somethingUnreadableIsNothing() {
        assertNull(AppearanceReturnState.parse(null));
        assertNull(AppearanceReturnState.parse(""));
        assertNull(AppearanceReturnState.parse("{not json"));
        assertNull(AppearanceReturnState.parse("{\"page\":\"SOMEWHERE\"}"));
    }

    @Test
    public void itIsKeptForSettingsWindowAndNoLonger() {
        AppearanceReturnState state = new AppearanceReturnState.Builder(PageId.EDITOR)
            .card(EditorCard.HOME).build(NOW);
        assertTrue(state.isFresh(NOW + 1000));
        assertFalse(state.isFresh(NOW + SettingsBackStackState.RETAIN_WINDOW_MS));
        assertFalse("a clock moved back is not trusted", state.isFresh(NOW - 1000));
        assertNull(AppearanceReturnState.freshFrom(state.serialize(),
            NOW + SettingsBackStackState.RETAIN_WINDOW_MS + 1));
        assertEquals(EditorCard.HOME, AppearanceReturnState.freshFrom(state.serialize(),
            NOW + 5).editorCard());
    }
}
