package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlLooks;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.junit.Test;

/** The Global card's history: a Look pick, Custom, Corners, Tint colour, text size and icon pack. */
public class EditSessionGlobalTest {

    private static final TerlSurface KEYBOARD = TerlSurface.KEYBOARD;
    private static final TerlProperty OPACITY = TerlProperty.OPACITY;
    private static final TerlProperty BLUR = TerlProperty.BLUR;

    /** The launcher's text size and icon pack, as the activity keeps them; clamped the same way. */
    private static final class FakeLauncher implements EditSession.Launcher {
        int textSize = 100;
        String iconPack = "";

        @Override public int textSizePercent() {
            return textSize;
        }

        @Override public void setTextSizePercent(int percent) {
            textSize = Math.max(80, Math.min(150, percent));
        }

        @NonNull @Override public String iconPack() {
            return iconPack;
        }

        @Override public void setIconPack(@NonNull String pack) {
            iconPack = pack;
        }
    }

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());
    private final FakeLauncher launcher = new FakeLauncher();

    private EditSession begin() {
        return EditSession.begin(store, launcher);
    }

    private static void drag(EditSession session, EditSession.Setting setting, int... values) {
        session.dragStarted(setting);
        for (int value : values) session.set(setting, value);
        session.dragEnded(setting);
    }

    // ---------------------------------------------------------------- Look pick

    @Test
    public void aPickWithNoOwnValuesWearsTheLookAsOneStep() {
        EditSession session = begin();
        String before = store.look();

        TerlLookStore.Pick pick = session.pick(TerlLooks.SOLID, false);

        assertTrue(pick.applied);
        assertEquals(TerlLooks.SOLID, store.look());
        assertEquals(1, session.changeCount());
        session.undo();
        assertEquals(before, store.look());
        assertFalse(session.canUndo());
    }

    @Test
    public void aPickWithOwnValuesAsksFirstAndChangesNothing() {
        store.setOwn(KEYBOARD, OPACITY, 33);
        EditSession session = begin();
        String before = store.look();

        TerlLookStore.Pick pick = session.pick(TerlLooks.SOLID, false);

        assertFalse(pick.applied);
        assertEquals(1, pick.clears.size());
        assertEquals(before, store.look());
        assertTrue(store.holdsOwn(KEYBOARD, OPACITY));
        assertFalse(session.canUndo());
        assertEquals(0, session.changeCount());
    }

    @Test
    public void undoAfterAConfirmedPickRestoresTheOldLookAndEveryOwnValueItCleared() {
        store.pick(TerlLooks.FROSTED, true);
        store.setOwn(KEYBOARD, OPACITY, 33);
        store.setOwn(TerlSurface.WINDOWS, BLUR, 7);
        EditSession session = begin();

        TerlLookStore.Pick pick = session.pick(TerlLooks.SOLID, true);
        assertTrue(pick.applied);
        assertEquals(2, pick.clears.size());
        assertFalse(store.holdsOwn(KEYBOARD, OPACITY));
        assertEquals(TerlLooks.SOLID, store.look());

        session.undo();

        assertEquals(TerlLooks.FROSTED, store.look());
        assertEquals(33, store.value(KEYBOARD, OPACITY));
        assertEquals(7, store.value(TerlSurface.WINDOWS, BLUR));
        assertFalse(session.canUndo());
        assertEquals(0, session.changeCount());
    }

    @Test
    public void picksAreAStepEach() {
        String before = store.look();
        EditSession session = begin();
        session.pick(TerlLooks.SOLID, false);
        session.pick(TerlLookStore.CUSTOM, false);

        session.undo();
        assertEquals(TerlLooks.SOLID, store.look());
        session.undo();
        assertEquals(before, store.look());
    }

    @Test
    public void pickingTheLookAlreadyWornIsNoStep() {
        EditSession session = begin();
        session.pick(store.look(), false);
        assertFalse(session.canUndo());
    }

    @Test
    public void anUnknownLookIsNoStep() {
        EditSession session = begin();
        assertFalse(session.pick("no-such-look", true).applied);
        assertFalse(session.canUndo());
    }

    @Test
    public void undoAfterADragThenAPickGoesBackOneAtATime() {
        EditSession session = begin();
        session.set(KEYBOARD, BLUR, 12);
        session.pick(TerlLooks.SOLID, true);

        session.undo();
        assertEquals(12, store.value(KEYBOARD, BLUR));
        assertTrue(store.holdsOwn(KEYBOARD, BLUR));
        session.undo();
        assertFalse(store.holdsOwn(KEYBOARD, BLUR));
    }

    @Test
    public void revertPutsTheLookAndTheClearedOwnValuesBack() {
        String before = store.look();
        store.setOwn(KEYBOARD, OPACITY, 40);
        EditSession session = begin();
        session.pick(TerlLooks.LIQUID, true);

        session.revert();

        assertEquals(before, store.look());
        assertEquals(40, store.value(KEYBOARD, OPACITY));
        assertEquals(0, session.changeCount());
        session.undo();
        assertEquals(TerlLooks.LIQUID, store.look());
        assertFalse(store.holdsOwn(KEYBOARD, OPACITY));
    }

    @Test
    public void aLookPickedFromOutsideAfterIsTheOutsidesAndUndoStillGivesOwnValuesBack() {
        store.setOwn(KEYBOARD, OPACITY, 40);
        EditSession session = begin();
        session.pick(TerlLooks.SOLID, true);
        store.wear(TerlLooks.CLEAR); // terl.conf

        session.undo();

        assertEquals(TerlLooks.CLEAR, store.look());
        assertEquals(40, store.value(KEYBOARD, OPACITY));
        assertEquals(1, session.keptElsewhereCount());
    }

    // ---------------------------------------------------------------- Custom, Corners, Tint

    @Test
    public void aCustomDragIsOneStepAndLeavesTheLookAlone() {
        store.pick(TerlLookStore.CUSTOM, true);
        EditSession session = begin();
        int before = store.customValue(OPACITY);

        drag(session, EditSession.Setting.custom(OPACITY), 20, 30, 44);

        assertEquals(44, store.customValue(OPACITY));
        assertEquals(44, store.global(OPACITY));
        assertEquals(TerlLookStore.CUSTOM, store.look());
        assertEquals(1, session.changeCount());
        session.undo();
        assertEquals(before, store.customValue(OPACITY));
        assertFalse(session.canUndo());
    }

    @Test
    public void customKeepsItsValuesWhileOtherLooksAreTried() {
        EditSession session = begin();
        session.pick(TerlLookStore.CUSTOM, false);
        drag(session, EditSession.Setting.custom(BLUR), 21);
        session.pick(TerlLooks.SOLID, false);
        session.pick(TerlLookStore.CUSTOM, false);
        assertEquals(21, store.global(BLUR));
    }

    @Test
    public void aCornersDragIsOneStep() {
        int before = store.cornerRadiusDp();
        EditSession session = begin();
        drag(session, EditSession.Setting.CORNERS, 10, 12);
        assertEquals(12, store.cornerRadiusDp());
        session.undo();
        assertEquals(before, store.cornerRadiusDp());
    }

    @Test
    public void aTintIsOneStep() {
        EditSession session = begin();
        session.setTint(TerlLookStore.TINT_TERTIARY);
        assertEquals(TerlLookStore.TINT_TERTIARY, store.tint());
        assertEquals(1, session.changeCount());
        session.undo();
        assertEquals(TerlLookStore.TINT_PRIMARY, store.tint());
    }

    @Test
    public void theTintAlreadyWornIsNoStep() {
        EditSession session = begin();
        session.setTint(store.tint());
        assertFalse(session.canUndo());
    }

    @Test
    public void aCornersWriteFromOutsideIsTheOutsides() {
        EditSession session = begin();
        drag(session, EditSession.Setting.CORNERS, 10);
        store.setCornerRadiusDp(30);
        assertEquals(1, session.keptElsewhereCount());
        session.undo();
        assertEquals(30, store.cornerRadiusDp());
    }

    // ---------------------------------------------------------------- outside the look store

    @Test
    public void aTextSizeDragIsOneStep() {
        EditSession session = begin();
        drag(session, EditSession.Setting.TEXT_SIZE, 105, 110, 120);
        assertEquals(120, launcher.textSize);
        assertEquals(1, session.changeCount());
        session.undo();
        assertEquals(100, launcher.textSize);
        assertFalse(session.canUndo());
    }

    @Test
    public void aTextSizeSetFromOutsideIsTheOutsides() {
        EditSession session = begin();
        drag(session, EditSession.Setting.TEXT_SIZE, 120);
        launcher.textSize = 90; // Settings, launcherctl
        assertEquals(0, session.changeCount());
        assertEquals(1, session.keptElsewhereCount());
        session.undo();
        assertEquals(90, launcher.textSize);
    }

    @Test
    public void anIconPackIsOneStep() {
        EditSession session = begin();
        session.setIconPack("org.example.pack");
        assertEquals("org.example.pack", launcher.iconPack);
        session.setIconPack("org.example.other");
        session.undo();
        assertEquals("org.example.pack", launcher.iconPack);
        session.undo();
        assertEquals("", launcher.iconPack);
    }

    @Test
    public void revertTakesBackEveryGlobalRow() {
        String look = store.look();
        int corners = store.cornerRadiusDp();
        EditSession session = begin();
        session.pick(TerlLooks.SOLID, true);
        drag(session, EditSession.Setting.CORNERS, 4);
        session.setTint(TerlLookStore.TINT_SECONDARY);
        drag(session, EditSession.Setting.TEXT_SIZE, 130);
        session.setIconPack("org.example.pack");
        assertEquals(5, session.changeCount());

        session.revert();

        assertEquals(look, store.look());
        assertEquals(corners, store.cornerRadiusDp());
        assertEquals(TerlLookStore.TINT_PRIMARY, store.tint());
        assertEquals(100, launcher.textSize);
        assertEquals("", launcher.iconPack);
        assertEquals(0, session.changeCount());
    }

    @Test
    public void withoutALauncherTextSizeAndIconPackAreLeftAlone() {
        EditSession session = EditSession.begin(store);
        drag(session, EditSession.Setting.TEXT_SIZE, 130);
        session.setIconPack("org.example.pack");
        assertFalse(session.canUndo());
    }
}
