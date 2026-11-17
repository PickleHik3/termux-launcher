package com.termux.app.place.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.place.PlaceOrientation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.HashMap;
import java.util.Map;

/**
 * The Appearance editor's draft over the layouts (#254): its edits show at once and stay out of
 * the file until Done; Discard drops them; a write from outside meanwhile reaches the file and
 * survives both.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutStoreDraftTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final String EDGE = LayoutsJson.STATUS_EDGE;
    private static final String FORM = LayoutsJson.KEYBOARD_FORM;

    private final Map<String, String> file = new HashMap<>();
    private int writes;
    private int notices;

    private LayoutStore store() {
        LayoutStore store = new LayoutStore(new LayoutStore.Storage() {
            @Override public String read(String key) { return file.get(key); }
            @Override public void write(Map<String, String> documents) {
                writes++;
                file.putAll(documents);
            }
        }, false);
        store.addListener(layouts -> notices++);
        return store;
    }

    private static Object field(LayoutStore store, LayoutKind kind, String field) {
        return store.current().kind(kind).orientation(PORTRAIT).get(field);
    }

    /** What the file holds, read afresh by a store of its own. */
    private Object stored(LayoutKind kind, String field) {
        return store().current().kind(kind).orientation(PORTRAIT).get(field);
    }

    @Test
    public void anEditShowsAtOnceAndStaysOutOfTheFile() {
        LayoutStore store = store();
        store.beginDraft();
        int before = notices;
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
        assertEquals("floating", field(store, LayoutKind.TERMINAL, FORM));
        assertTrue("readers are told", notices > before);
        assertEquals("nothing written", 0, writes);
        assertNull(stored(LayoutKind.TERMINAL, FORM));
        assertTrue(store.isDraftDirty());
    }

    @Test
    public void doneWritesTheDraftOnce() {
        LayoutStore store = store();
        store.beginDraft();
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
        store.updateDraft(LayoutKind.HOME, k -> k.with(PORTRAIT, EDGE, "bottom"));
        store.updateSizesDraft(s -> s.with(PORTRAIT, LayoutsJson.DOCK_HEIGHT, 1.2f));
        store.commitDraft();
        assertEquals("one write", 1, writes);
        assertFalse(store.isDraftOpen());
        assertEquals("floating", stored(LayoutKind.TERMINAL, FORM));
        assertEquals("bottom", stored(LayoutKind.HOME, EDGE));
        assertEquals(1.2f, store().current().sizes().orientation(PORTRAIT)
            .decimal(LayoutsJson.DOCK_HEIGHT, 0f), 1e-6f);
    }

    @Test
    public void discardLeavesTheFileAsItWasAndTheReadersBackOnIt() {
        LayoutStore store = store();
        store.beginDraft();
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
        int before = notices;
        store.discardDraft();
        assertEquals(0, writes);
        assertNull(field(store, LayoutKind.TERMINAL, FORM));
        assertTrue("readers are told the edit went", notices > before);
        assertFalse(store.isDraftOpen());
    }

    @Test
    public void settingAValueBackToItsStoredValueIsNotAnUnsavedChange() {
        LayoutStore store = store();
        store.update(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "docked"));
        store.beginDraft();
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
        assertTrue(store.isDraftDirty());
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "docked"));
        assertFalse(store.isDraftDirty());
    }

    @Test
    public void anOutsideWriteToAnotherCellSurvivesDoneAndDiscard() {
        for (boolean done : new boolean[]{true, false}) {
            file.clear();
            LayoutStore store = store();
            store.beginDraft();
            store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
            // launcherctl, say: the same document, another field.
            store.update(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, EDGE, "bottom"));
            assertEquals("shows at once", "bottom", field(store, LayoutKind.TERMINAL, EDGE));
            assertEquals("stored at once", "bottom", stored(LayoutKind.TERMINAL, EDGE));
            assertNull("without the draft's edit", stored(LayoutKind.TERMINAL, FORM));
            if (done) store.commitDraft();
            else store.discardDraft();
            assertEquals("bottom", field(store, LayoutKind.TERMINAL, EDGE));
            assertEquals("bottom", stored(LayoutKind.TERMINAL, EDGE));
            assertEquals(done ? "floating" : null, stored(LayoutKind.TERMINAL, FORM));
        }
    }

    @Test
    public void aCellBothTouchedShowsTheDraftUntilDone() {
        LayoutStore store = store();
        store.beginDraft();
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
        store.update(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "split"));
        assertEquals("floating", field(store, LayoutKind.TERMINAL, FORM));
        assertEquals("split", stored(LayoutKind.TERMINAL, FORM));
        store.commitDraft();
        assertEquals("floating", stored(LayoutKind.TERMINAL, FORM));
    }

    @Test
    public void resetDropsTheEditsAndKeepsTheDraftOpen() {
        LayoutStore store = store();
        store.beginDraft();
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
        store.resetDraft();
        assertTrue(store.isDraftOpen());
        assertFalse(store.isDraftDirty());
        assertNull(field(store, LayoutKind.TERMINAL, FORM));
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "split"));
        assertEquals("still drafted", 0, writes);
    }

    @Test
    public void withNoDraftOpenADraftWriteGoesStraightToTheFile() {
        LayoutStore store = store();
        store.updateDraft(LayoutKind.TERMINAL, k -> k.with(PORTRAIT, FORM, "floating"));
        assertEquals(1, writes);
        assertEquals("floating", stored(LayoutKind.TERMINAL, FORM));
        assertFalse(store.isDraftDirty());
    }
}
