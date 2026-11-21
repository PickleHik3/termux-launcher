package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * Under the Appearance editor's draft the arrangement waits for Done, while what a place
 * remembers is written at once: the wall sliding under the editor leaves the terminal's keyboard
 * memory behind, which is no unsaved edit and survives Discard.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutDraftMemoryTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private PlaceLayoutStore places;
    private LayoutStore documents;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-draft-memory-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
        documents = places.layouts();
        documents.beginDraft();
    }

    /** What the layouts file holds, read afresh. */
    private LayoutStore file() {
        return new LayoutStore(new LayoutStore.PrefsStorage(
            LayoutStore.prefs(RuntimeEnvironment.getApplication())), false);
    }

    @Test
    public void theKeyboardMemoryIsWrittenPastTheDraft() {
        places.setKeyboardOpen(PageKind.TERMINAL, true);
        assertTrue(places.wasKeyboardOpen(PageKind.TERMINAL));
        assertFalse("no unsaved edit", documents.isDraftDirty());
        assertEquals(Boolean.TRUE, file().current().kind(LayoutKind.TERMINAL).wide()
            .get(LayoutsJson.KEYBOARD_OPEN));
        documents.discardDraft();
        assertTrue("Discard leaves it as the place left it",
            places.wasKeyboardOpen(PageKind.TERMINAL));
    }

    @Test
    public void theStatusBarsRestIsWrittenPastTheDraft() {
        boolean compact = !places.isStatusCompact(PORTRAIT);
        places.setStatusCompact(PORTRAIT, compact);
        assertFalse(documents.isDraftDirty());
        documents.discardDraft();
        assertEquals(compact, places.isStatusCompact(PORTRAIT));
    }

    @Test
    public void theArrangementWaitsForDone() {
        places.setKeyboardForm(PORTRAIT, PlaceLayout.KeyboardForm.FLOATING);
        assertEquals(PlaceLayout.KeyboardForm.FLOATING, places.keyboardForm(PORTRAIT));
        assertTrue(documents.isDraftDirty());
        documents.discardDraft();
        assertEquals(PlaceLayout.KeyboardForm.DOCKED, places.keyboardForm(PORTRAIT));
    }
}
