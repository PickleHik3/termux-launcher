package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The Control bar card's settings in the edit session: each applies at once and is one Undo step. */
public class EditSessionBarTest {

    private static final class Fake implements EditSession.Bar {
        int margin = 6;
        boolean reading = true;
        String rail = RAIL_RIGHT;
        String home = "drawer,pinned_apps";
        String terminal = "keyboard,split,new_terminal,window_menu";
        String linux = "mouse_mode,keyboard,window_menu";

        @Override public int marginDp() { return margin; }
        @Override public void setMarginDp(int dp) { margin = Math.max(MIN_MARGIN, Math.min(MAX_MARGIN, dp)); }
        @Override public boolean reading() { return reading; }
        @Override public void setReading(boolean on) { reading = on; }
        @NonNull @Override public String railSide() { return rail; }
        @Override public void setRailSide(@NonNull String side) { rail = side; }
        @NonNull @Override public List<String> toolOptions(@NonNull String place) {
            return Arrays.asList(tools(place).split(","));
        }
        @NonNull @Override public String tools(@NonNull String place) {
            return PLACE_HOME.equals(place) ? home : PLACE_TERMINAL.equals(place) ? terminal : linux;
        }
        @Override public void setTools(@NonNull String place, @NonNull String tools) {
            if (PLACE_HOME.equals(place)) home = tools;
            else if (PLACE_TERMINAL.equals(place)) terminal = tools;
            else linux = tools;
        }
    }

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());
    private final Fake fake = new Fake();

    private EditSession begin() {
        return EditSession.begin(store, null, null, null, fake);
    }

    @Test
    public void marginAppliesAtOnceAndOneDragIsOneStep() {
        EditSession session = begin();
        session.dragStarted(EditSession.Setting.BAR_MARGIN);
        session.set(EditSession.Setting.BAR_MARGIN, 3);
        session.set(EditSession.Setting.BAR_MARGIN, 0);
        assertEquals(0, fake.margin);
        session.dragEnded(EditSession.Setting.BAR_MARGIN);
        assertEquals(1, session.changeCount());
        session.undo();
        assertEquals(6, fake.margin);
        assertFalse(session.canUndo());
    }

    @Test
    public void marginIsHeldInsideItsRange() {
        EditSession session = begin();
        session.set(EditSession.Setting.BAR_MARGIN, 99);
        assertEquals(EditSession.Bar.MAX_MARGIN, fake.margin);
        assertEquals(1, session.changeCount());
    }

    @Test
    public void readingAndRailSideAreOneStepEach() {
        EditSession session = begin();
        session.setBarReading(false);
        session.setBarRailSide(EditSession.Bar.RAIL_LEFT);
        assertFalse(fake.reading);
        assertEquals("left", fake.rail);
        session.undo();
        assertEquals("right", fake.rail);
        assertFalse(fake.reading);
        session.undo();
        assertTrue(fake.reading);
    }

    @Test
    public void aToolDroppedIntoANewPlaceIsOneStepAndUndoPutsTheOrderBack() {
        EditSession session = begin();
        session.setBarTools(EditSession.Bar.PLACE_TERMINAL, "split,keyboard,new_terminal,window_menu");
        assertEquals("split,keyboard,new_terminal,window_menu", fake.terminal);
        session.setBarTools(EditSession.Bar.PLACE_TERMINAL, "split,keyboard");
        session.undo();
        assertEquals("split,keyboard,new_terminal,window_menu", fake.terminal);
        session.undo();
        assertEquals("keyboard,split,new_terminal,window_menu", fake.terminal);
        assertFalse(session.canUndo());
    }

    @Test
    public void eachPlaceKeepsItsOwnTools() {
        EditSession session = begin();
        session.setBarTools(EditSession.Bar.PLACE_HOME, "pinned_apps");
        assertEquals("pinned_apps", fake.home);
        assertEquals("mouse_mode,keyboard,window_menu", fake.linux);
        assertEquals(1, session.changeCount());
    }

    @Test
    public void withoutTheBarNothingChanges() {
        EditSession session = EditSession.begin(store);
        session.setBarReading(false);
        session.setBarTools(EditSession.Bar.PLACE_HOME, "pinned_apps");
        assertEquals(0, session.changeCount());
        assertFalse(session.canUndo());
    }

    @Test
    public void theBarsOpacityAndBlurAreItsOwnValuesLikeTheKeyboards() {
        EditSession session = begin();
        session.set(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY, 40);
        assertEquals(40, store.value(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY));
        session.followGlobal(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY);
        assertFalse(store.holdsOwn(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY));
    }

    @Test
    public void theHistorySurvivesARebuildOfTheWindow() {
        EditSession session = begin();
        session.setBarReading(false);
        session.set(EditSession.Setting.BAR_MARGIN, 0);
        String saved = session.save();
        assertNotNull(saved);
        EditSession restored = EditSession.restore(saved, store, null, null, null, fake);
        assertNotNull(restored);
        assertEquals(2, restored.changeCount());
        restored.undo();
        assertEquals(6, fake.margin);
        restored.undo();
        assertTrue(fake.reading);
    }

    @Test
    public void aValueWrittenFromOutsideIsTheOutsidesAndUndoSkipsIt() {
        EditSession session = begin();
        session.setBarRailSide(EditSession.Bar.RAIL_LEFT);
        fake.rail = EditSession.Bar.RAIL_RIGHT;
        assertFalse(session.canUndo());
        assertEquals(1, session.keptElsewhereCount());
    }
}
