package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** The Windows card's settings in the edit session (#451): each applies at once and undoes. */
public class EditSessionKeyedTest {

    private static final class Fake implements EditSession.Keyed {
        final Map<String, Object> values = new HashMap<>();

        Fake() {
            values.put(GAP, 8);
            values.put(UNFOCUSED_DIM, 0);
            values.put(RIM_COLOUR, "accent");
            values.put(CONTRAST, "default");
            values.put(CURSOR_TRAIL, "off");
            values.put(EFFECT, "none");
        }

        @Nullable @Override public Object get(@NonNull String key) { return values.get(key); }
        @Override public void set(@NonNull String key, @NonNull Object value) { values.put(key, value); }
    }

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());
    private final Fake fake = new Fake();

    private EditSession begin() {
        return EditSession.begin(store, null, null, null, null, fake);
    }

    @Test
    public void gapAppliesAtOnceAndOneDragIsOneStep() {
        EditSession session = begin();
        EditSession.Setting gap = EditSession.Setting.keyedNumber(EditSession.Keyed.GAP);
        session.dragStarted(gap);
        session.set(gap, 12);
        session.set(gap, 20);
        assertEquals(20, fake.values.get(EditSession.Keyed.GAP));
        session.dragEnded(gap);
        assertEquals(1, session.changeCount());
        session.undo();
        assertEquals(8, fake.values.get(EditSession.Keyed.GAP));
        assertFalse(session.canUndo());
    }

    @Test
    public void aChoiceIsOneStepAndRevertPutsEveryWindowSettingBack() {
        EditSession session = begin();
        session.setKeyedChoice(EditSession.Keyed.RIM_COLOUR, "red");
        session.setKeyedChoice(EditSession.Keyed.EFFECT, "crt");
        session.setKeyedChoice(EditSession.Keyed.CURSOR_TRAIL, "comet");
        assertEquals("red", fake.values.get(EditSession.Keyed.RIM_COLOUR));
        assertEquals(3, session.changeCount());
        session.undo();
        assertEquals("off", fake.values.get(EditSession.Keyed.CURSOR_TRAIL));
        session.revert();
        assertEquals("accent", fake.values.get(EditSession.Keyed.RIM_COLOUR));
        assertEquals("none", fake.values.get(EditSession.Keyed.EFFECT));
        assertEquals(0, session.changeCount());
    }

    @Test
    public void aWriteFromOutsideIsTheOutsidesAndUndoLeavesIt() {
        EditSession session = begin();
        session.setKeyedChoice(EditSession.Keyed.CONTRAST, "harder");
        fake.values.put(EditSession.Keyed.CONTRAST, "softer");
        session.revert();
        assertEquals("softer", fake.values.get(EditSession.Keyed.CONTRAST));
        assertEquals(1, session.keptElsewhereCount());
    }

    @Test
    public void withoutTheSeamNothingIsWritten() {
        EditSession session = EditSession.begin(store);
        session.setKeyedChoice(EditSession.Keyed.RIM_COLOUR, "red");
        session.set(EditSession.Setting.keyedNumber(EditSession.Keyed.GAP), 3);
        assertEquals(0, session.changeCount());
        assertEquals("accent", fake.values.get(EditSession.Keyed.RIM_COLOUR));
    }

    @Test
    public void theHistorySurvivesARecreate() {
        EditSession session = begin();
        session.setKeyedChoice(EditSession.Keyed.RIM_COLOUR, "blue");
        session.set(EditSession.Setting.keyedNumber(EditSession.Keyed.UNFOCUSED_DIM), 30);
        String saved = session.save();
        assertNotNull(saved);
        EditSession again = EditSession.restore(saved, store, null, null, null, null, fake);
        assertNotNull(again);
        assertEquals(2, again.changeCount());
        again.undo();
        assertEquals(0, fake.values.get(EditSession.Keyed.UNFOCUSED_DIM));
        again.revert();
        assertEquals("accent", fake.values.get(EditSession.Keyed.RIM_COLOUR));
    }

    @Test
    public void theWindowsOpacityAndBlurAreTheirOwnWithFollowGlobal() {
        EditSession session = begin();
        assertFalse(store.holdsOwn(TerlSurface.WINDOWS, TerlProperty.OPACITY));
        session.set(TerlSurface.WINDOWS, TerlProperty.OPACITY, 40);
        assertEquals(40, store.value(TerlSurface.WINDOWS, TerlProperty.OPACITY));
        session.followGlobal(TerlSurface.WINDOWS, TerlProperty.OPACITY);
        assertFalse(store.holdsOwn(TerlSurface.WINDOWS, TerlProperty.OPACITY));
    }
}
