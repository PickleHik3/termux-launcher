package com.termux.app.surfaces;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Which sections a card left open (appearance-cards overhaul §3): on a first visit only the
 * first; then as left, per card, across a new store (a restart).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P)
public class EditorSectionStoreTest {

    @Test
    public void aFirstVisitOpensOnlyTheFirstSection() {
        EditorSectionStore store = new EditorSectionStore(context());
        assertTrue(store.isOpen("HOME", "_first", 0));
        assertFalse(store.isOpen("HOME", "SECTION_GRID", 1));
        assertFalse(store.isOpen("HOME", "SECTION_LOOK", 2));
    }

    @Test
    public void aCardKeepsWhatWasLeftOpenAcrossARestart() {
        EditorSectionStore store = new EditorSectionStore(context());
        store.setOpen("HOME", "_first", false);
        store.setOpen("HOME", "SECTION_GRID", true);
        store.markVisited("HOME");
        EditorSectionStore again = new EditorSectionStore(context());
        assertFalse(again.isOpen("HOME", "_first", 0));
        assertTrue(again.isOpen("HOME", "SECTION_GRID", 1));
        // A section never touched on a visited card stands closed, even a first one.
        assertFalse(again.isOpen("HOME", "SECTION_NEW", 0));
        // Another card is its own.
        assertTrue(again.isOpen("DOCK", "_first", 0));
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }
}
