package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.junit.Test;

/** The edit session's history over an in-memory store (spec "Edit mode and Settings", History). */
public class EditSessionTest {

    private static final TerlSurface KEYBOARD = TerlSurface.KEYBOARD;
    private static final TerlProperty OPACITY = TerlProperty.OPACITY;
    private static final TerlProperty BLUR = TerlProperty.BLUR;

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());

    private void drag(EditSession session, TerlProperty property, int... values) {
        session.dragStarted(KEYBOARD, property);
        for (int value : values) session.set(KEYBOARD, property, value);
        session.dragEnded(KEYBOARD, property);
    }

    @Test
    public void everyValueOfADragAppliesAtOnce() {
        EditSession session = EditSession.begin(store);
        session.dragStarted(KEYBOARD, OPACITY);
        session.set(KEYBOARD, OPACITY, 40);
        assertTrue(store.holdsOwn(KEYBOARD, OPACITY));
        assertEquals(40, store.value(KEYBOARD, OPACITY));
        session.set(KEYBOARD, OPACITY, 55);
        assertEquals(55, store.value(KEYBOARD, OPACITY));
    }

    @Test
    public void oneDragIsOneUndoStep() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 40, 50, 60);
        assertEquals(1, session.changeCount());
        assertTrue(session.canUndo());

        session.undo();

        assertFalse(store.holdsOwn(KEYBOARD, OPACITY));
        assertFalse(session.canUndo());
        assertEquals(0, session.changeCount());
    }

    @Test
    public void undoGoesBackOneDragAtATime() {
        store.setOwn(KEYBOARD, OPACITY, 30);
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 50);
        drag(session, OPACITY, 70);

        session.undo();
        assertEquals(50, store.value(KEYBOARD, OPACITY));
        session.undo();
        assertEquals(30, store.value(KEYBOARD, OPACITY));
        assertTrue(store.holdsOwn(KEYBOARD, OPACITY));
    }

    @Test
    public void aDragThatEndsWhereItStartedIsNoStep() {
        store.setOwn(KEYBOARD, BLUR, 12);
        EditSession session = EditSession.begin(store);
        drag(session, BLUR, 20, 12);
        assertFalse(session.canUndo());
        assertEquals(0, session.changeCount());
    }

    @Test
    public void aSetOutsideADragIsAStepOfItsOwn() {
        EditSession session = EditSession.begin(store);
        session.set(KEYBOARD, BLUR, 9);
        assertTrue(session.canUndo());
        session.undo();
        assertFalse(store.holdsOwn(KEYBOARD, BLUR));
    }

    @Test
    public void theCountIsTheRowsTheSessionChanged() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 40);
        drag(session, OPACITY, 45);
        assertEquals(1, session.changeCount());
        drag(session, BLUR, 10);
        assertEquals(2, session.changeCount());
    }

    @Test
    public void revertDropsEverythingSinceTheSessionOpened() {
        store.setOwn(KEYBOARD, BLUR, 20);
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 40);
        drag(session, BLUR, 5);
        session.followGlobal(KEYBOARD, BLUR);

        session.revert();

        assertFalse(store.holdsOwn(KEYBOARD, OPACITY));
        assertTrue(store.holdsOwn(KEYBOARD, BLUR));
        assertEquals(20, store.value(KEYBOARD, BLUR));
        assertEquals(0, session.changeCount());
    }

    @Test
    public void undoRightAfterRevertBringsTheSessionBack() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 40);
        drag(session, BLUR, 7);
        session.revert();

        session.undo();

        assertEquals(40, store.value(KEYBOARD, OPACITY));
        assertEquals(7, store.value(KEYBOARD, BLUR));
        assertEquals(2, session.changeCount());
        // The steps before Revert are still there.
        session.undo();
        assertFalse(store.holdsOwn(KEYBOARD, BLUR));
        assertEquals(40, store.value(KEYBOARD, OPACITY));
    }

    @Test
    public void revertWithNothingChangedIsNoStep() {
        EditSession session = EditSession.begin(store);
        session.revert();
        assertFalse(session.canUndo());
    }

    @Test
    public void followGlobalIsOneStepAndUndoGivesTheOwnValueBack() {
        store.setOwn(KEYBOARD, OPACITY, 66);
        EditSession session = EditSession.begin(store);

        session.followGlobal(KEYBOARD, OPACITY);
        assertFalse(store.holdsOwn(KEYBOARD, OPACITY));
        assertEquals(store.global(OPACITY), store.value(KEYBOARD, OPACITY));
        assertEquals(1, session.changeCount());

        session.undo();
        assertTrue(store.holdsOwn(KEYBOARD, OPACITY));
        assertEquals(66, store.value(KEYBOARD, OPACITY));
    }

    @Test
    public void followGlobalOnARowAlreadyFollowingIsNoStep() {
        EditSession session = EditSession.begin(store);
        session.followGlobal(KEYBOARD, OPACITY);
        assertFalse(session.canUndo());
    }

    @Test
    public void anOutsideWriteAfterTheSessionIsSkippedByUndo() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 60);
        store.setOwn(KEYBOARD, OPACITY, 20); // launcherctl

        assertEquals(0, session.changeCount());
        assertEquals(1, session.keptElsewhereCount());
        session.undo();

        assertEquals(20, store.value(KEYBOARD, OPACITY));
        assertFalse(session.canUndo());
    }

    @Test
    public void anOutsideFollowGlobalCountsAsAnOutsideWrite() {
        EditSession session = EditSession.begin(store);
        drag(session, BLUR, 30);
        store.followGlobal(KEYBOARD, BLUR);
        session.undo();
        assertFalse(store.holdsOwn(KEYBOARD, BLUR));
        assertEquals(1, session.keptElsewhereCount());
    }

    @Test
    public void anOutsideWriteIsSkippedByRevertAndTheRestReverts() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 60);
        drag(session, BLUR, 9);
        store.setOwn(KEYBOARD, OPACITY, 20);

        session.revert();

        assertEquals(20, store.value(KEYBOARD, OPACITY));
        assertFalse(store.holdsOwn(KEYBOARD, BLUR));
        assertEquals(0, session.changeCount());
        assertEquals(1, session.keptElsewhereCount());
    }

    @Test
    public void undoAfterAnOutsideWriteReversesTheLatestChangeStillTheSessions() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 60);
        drag(session, BLUR, 9);
        store.setOwn(KEYBOARD, BLUR, 3);

        session.undo();

        assertFalse(store.holdsOwn(KEYBOARD, OPACITY));
        assertEquals(3, store.value(KEYBOARD, BLUR));
    }

    @Test
    public void undoOfARevertSkipsARowWrittenFromOutsideAfterIt() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 60);
        drag(session, BLUR, 9);
        session.revert();
        store.setOwn(KEYBOARD, BLUR, 4);

        session.undo();

        assertEquals(60, store.value(KEYBOARD, OPACITY));
        assertEquals(4, store.value(KEYBOARD, BLUR));
        assertEquals(1, session.changeCount());
        assertEquals(1, session.keptElsewhereCount());
    }

    @Test
    public void aRowTheSessionChangesAgainAfterAnOutsideWriteIsTheSessionsOnce() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 60);
        store.setOwn(KEYBOARD, OPACITY, 20);
        drag(session, OPACITY, 35);

        assertEquals(0, session.keptElsewhereCount());
        assertEquals(1, session.changeCount());
        session.undo();
        // Back to the outside's value, which the session started this change from.
        assertEquals(20, store.value(KEYBOARD, OPACITY));
        assertFalse(session.canUndo());
    }

    @Test
    public void aRowTheSessionNeverTouchedIsNeverCountedAsKeptElsewhere() {
        EditSession session = EditSession.begin(store);
        store.setOwn(KEYBOARD, OPACITY, 20);
        assertEquals(0, session.keptElsewhereCount());
        assertEquals(0, session.changeCount());
    }

    @Test
    public void theHistoryEndsWithTheSession() {
        EditSession session = EditSession.begin(store);
        drag(session, OPACITY, 60);
        session.end();

        assertFalse(session.canUndo());
        session.undo();
        session.revert();
        assertEquals(60, store.value(KEYBOARD, OPACITY));
        assertEquals(0, session.changeCount());
    }

    @Test
    public void aSurfaceThatWearsTheLookIsNeverWritten() {
        EditSession session = EditSession.begin(store);
        session.dragStarted(TerlSurface.SHEETS, OPACITY);
        session.set(TerlSurface.SHEETS, OPACITY, 40);
        session.dragEnded(TerlSurface.SHEETS, OPACITY);
        assertFalse(session.canUndo());
        assertEquals(0, session.changeCount());
    }
}
