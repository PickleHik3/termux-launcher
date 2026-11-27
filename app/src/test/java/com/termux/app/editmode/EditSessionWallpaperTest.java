package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.junit.Test;

/** The Wallpaper and colours card's settings in the edit session, and the session surviving a recreate. */
public class EditSessionWallpaperTest {

    private static final class Fake implements EditSession.Wallpaper {
        String theme = "system";
        int dim = 30;
        boolean drift = true;
        boolean colours = false;

        @Override public void changePicture() { }
        @NonNull @Override public String theme() { return theme; }
        @Override public void setTheme(@NonNull String theme) { this.theme = theme; }
        @Override public int dim() { return dim; }
        @Override public void setDim(int percent) { dim = Math.max(0, Math.min(100, percent)); }
        @Override public boolean drift() { return drift; }
        @Override public void setDrift(boolean on) { drift = on; }
        @Override public boolean coloursFromWallpaper() { return colours; }
        @Override public void setColoursFromWallpaper(boolean on) { colours = on; }
    }

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());
    private final Fake fake = new Fake();

    private EditSession begin() {
        return EditSession.begin(store, null, fake);
    }

    @Test
    public void everyChangeAppliesAtOnceAndUndoesOneStepAtATime() {
        EditSession session = begin();
        session.setTheme("true");
        session.setDrift(false);
        session.setColoursFromWallpaper(true);
        session.dragStarted(EditSession.Setting.DIM);
        session.set(EditSession.Setting.DIM, 50);
        session.set(EditSession.Setting.DIM, 70);
        session.dragEnded(EditSession.Setting.DIM);
        assertEquals("true", fake.theme);
        assertFalse(fake.drift);
        assertTrue(fake.colours);
        assertEquals(70, fake.dim);
        assertEquals(4, session.changeCount());

        session.undo();
        assertEquals(30, fake.dim);
        session.undo();
        assertFalse(fake.colours);
        session.undo();
        assertTrue(fake.drift);
        session.undo();
        assertEquals("system", fake.theme);
        assertFalse(session.canUndo());
    }

    @Test
    public void revertPutsAllFourBack() {
        EditSession session = begin();
        session.setTheme("false");
        session.setDrift(false);
        session.revert();
        assertEquals("system", fake.theme);
        assertTrue(fake.drift);
    }

    @Test
    public void withoutTheWallpaperSeamTheyAreLeftAlone() {
        EditSession session = EditSession.begin(store);
        session.setTheme("true");
        session.setDrift(false);
        assertFalse(session.canUndo());
    }

    @Test
    public void aSavedSessionComesBackWithItsHistoryAndBaselines() {
        EditSession session = begin();
        session.setTheme("true");
        session.set(TerlSurface.KEYBOARD, TerlProperty.OPACITY, 55);
        session.setColoursFromWallpaper(true);
        String saved = session.save();
        assertNotNull(saved);

        EditSession again = EditSession.restore(saved, store, null, fake);
        assertNotNull(again);
        assertEquals(3, again.changeCount());
        assertTrue(again.canUndo());
        again.undo();
        assertFalse(fake.colours);
        again.revert();
        assertEquals("system", fake.theme);
        assertFalse(store.holdsOwn(TerlSurface.KEYBOARD, TerlProperty.OPACITY));
    }

    @Test
    public void aSettingWrittenElsewhereWhileTheWindowWasRebuiltIsTheOutsides() {
        EditSession session = begin();
        session.setTheme("true");
        String saved = session.save();
        fake.theme = "false";

        EditSession again = EditSession.restore(saved, store, null, fake);

        assertNotNull(again);
        assertEquals(0, again.changeCount());
        assertEquals(1, again.keptElsewhereCount());
        assertFalse(again.canUndo());
    }

    @Test
    public void anEndedSessionSavesNothingAndGarbageRestoresNothing() {
        EditSession session = begin();
        session.end();
        assertNull(session.save());
        assertNull(EditSession.restore("not json", store, null, fake));
        assertNull(EditSession.restore("{\"baseline\":[[\"NOPE\",1]],\"written\":[],\"kept\":[],\"undo\":[]}",
            store, null, fake));
    }
}
