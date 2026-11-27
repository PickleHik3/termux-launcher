package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;

import org.junit.Test;

/** The Home card's settings in the edit session: each applies at once and is one Undo step. */
public class EditSessionHomeTest {

    private static final class Fake implements EditSession.Home {
        boolean available = true;
        int columns = 4;
        String labels = LABEL_DEFAULT;
        boolean dots = true;
        String az = AZ_BOTTOM;

        @Override public boolean available() { return available; }
        @Override public int columns() { return columns; }
        @Override public void setColumns(int c) { columns = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, c)); }
        @NonNull @Override public String labels() { return labels; }
        @Override public void setLabels(@NonNull String size) { labels = size; }
        @Override public boolean pageDots() { return dots; }
        @Override public void setPageDots(boolean on) { dots = on; }
        @NonNull @Override public String azEdge() { return az; }
        @Override public void setAzEdge(@NonNull String edge) { az = edge; }
        @Override public void onEmptyTap(@Nullable Runnable tap) { }
        @Override public void change() { }
    }

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());
    private final Fake fake = new Fake();

    private EditSession begin() {
        return EditSession.begin(store, null, null, fake);
    }

    @Test
    public void columnsApplyAtOnceAndOneDragIsOneStep() {
        EditSession session = begin();
        session.dragStarted(EditSession.Setting.COLUMNS);
        session.set(EditSession.Setting.COLUMNS, 5);
        session.set(EditSession.Setting.COLUMNS, 6);
        assertEquals(6, fake.columns);
        session.dragEnded(EditSession.Setting.COLUMNS);
        session.undo();
        assertEquals(4, fake.columns);
        assertFalse(session.canUndo());
    }

    @Test
    public void columnsAreClampedAndCountedOnce() {
        EditSession session = begin();
        session.set(EditSession.Setting.COLUMNS, 99);
        assertEquals(EditSession.Home.MAX_COLUMNS, fake.columns);
        assertEquals(1, session.changeCount());
    }

    @Test
    public void labelsDotsAndAzEdgeAreOneStepEachAndUndo() {
        EditSession session = begin();
        session.setLabels(EditSession.Home.LABEL_LARGE);
        session.setPageDots(false);
        session.setAzEdge(EditSession.Home.AZ_LEFT);
        assertEquals(3, session.changeCount());
        assertEquals("large", fake.labels);
        assertFalse(fake.dots);
        assertEquals("left", fake.az);
        session.undo();
        assertEquals("bottom", fake.az);
        session.undo();
        assertTrue(fake.dots);
        session.undo();
        assertEquals("default", fake.labels);
    }

    @Test
    public void aSettingChosenAgainChangesNothing() {
        EditSession session = begin();
        session.setAzEdge(EditSession.Home.AZ_BOTTOM);
        session.setPageDots(true);
        assertFalse(session.canUndo());
    }

    @Test
    public void revertPutsHomeBack() {
        EditSession session = begin();
        session.setAzEdge(EditSession.Home.AZ_OFF);
        session.set(EditSession.Setting.COLUMNS, 3);
        session.revert();
        assertEquals("bottom", fake.az);
        assertEquals(4, fake.columns);
    }

    @Test
    public void aWriteFromOutsideIsTheOutsidesAndUndoSkipsIt() {
        EditSession session = begin();
        session.setAzEdge(EditSession.Home.AZ_RIGHT);
        fake.az = EditSession.Home.AZ_LEFT; // Settings or launcherctl
        session.undo();
        assertEquals("left", fake.az);
        assertEquals(1, session.keptElsewhereCount());
    }

    @Test
    public void withoutAHomeTheSettingsAreLeftAlone() {
        fake.available = false;
        EditSession session = begin();
        session.setAzEdge(EditSession.Home.AZ_OFF);
        session.set(EditSession.Setting.COLUMNS, 2);
        assertEquals("bottom", fake.az);
        assertEquals(4, fake.columns);
        assertFalse(session.canUndo());
    }

    @Test
    public void aSavedSessionComesBackWithHomeInIt() {
        EditSession session = begin();
        session.setAzEdge(EditSession.Home.AZ_RIGHT);
        session.set(EditSession.Setting.COLUMNS, 6);
        String saved = session.save();
        assertNotNull(saved);
        EditSession again = EditSession.restore(saved, store, null, null, fake);
        assertNotNull(again);
        assertEquals(2, again.changeCount());
        again.undo();
        assertEquals(4, fake.columns);
        again.revert();
        assertEquals("bottom", fake.az);
    }
}
