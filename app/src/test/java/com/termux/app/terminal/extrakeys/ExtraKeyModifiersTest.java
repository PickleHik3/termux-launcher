package com.termux.app.terminal.extrakeys;

import static com.termux.app.terminal.extrakeys.ExtraKeyModifiers.State.ARMED;
import static com.termux.app.terminal.extrakeys.ExtraKeyModifiers.State.LOCKED;
import static com.termux.app.terminal.extrakeys.ExtraKeyModifiers.State.OFF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.extrakeys.SpecialButton;

import org.junit.Test;

public class ExtraKeyModifiersTest {

    private final ExtraKeyModifiers m = new ExtraKeyModifiers();

    @Test
    public void tapTogglesOffAndArmed() {
        m.tap(SpecialButton.CTRL);
        assertEquals(ARMED, m.state(SpecialButton.CTRL));
        m.tap(SpecialButton.CTRL);
        assertEquals(OFF, m.state(SpecialButton.CTRL));
    }

    @Test
    public void tapUnlocks() {
        m.hold(SpecialButton.ALT);
        assertEquals(LOCKED, m.state(SpecialButton.ALT));
        m.tap(SpecialButton.ALT);
        assertEquals(OFF, m.state(SpecialButton.ALT));
    }

    @Test
    public void holdTransitions() {
        m.hold(SpecialButton.SHIFT);
        assertEquals(LOCKED, m.state(SpecialButton.SHIFT));
        m.hold(SpecialButton.SHIFT);
        assertEquals(OFF, m.state(SpecialButton.SHIFT));
        m.tap(SpecialButton.SHIFT);
        m.hold(SpecialButton.SHIFT);
        assertEquals(OFF, m.state(SpecialButton.SHIFT));
    }

    @Test
    public void readConsumesArmedOnly() {
        m.tap(SpecialButton.CTRL);
        assertTrue(m.read(SpecialButton.CTRL, false));
        assertEquals(ARMED, m.state(SpecialButton.CTRL));
        assertTrue(m.read(SpecialButton.CTRL, true));
        assertEquals(OFF, m.state(SpecialButton.CTRL));
        assertFalse(m.read(SpecialButton.CTRL, true));

        m.hold(SpecialButton.FN);
        assertTrue(m.read(SpecialButton.FN, true));
        assertEquals(LOCKED, m.state(SpecialButton.FN));
    }

    @Test
    public void modifiersAreIndependent() {
        m.tap(SpecialButton.CTRL);
        assertEquals(OFF, m.state(SpecialButton.ALT));
    }

    @Test
    public void listenerHearsRealChangesOnly() {
        final int[] calls = {0};
        ExtraKeyModifiers.Listener l = (b, s) -> calls[0]++;
        m.addListener(l);
        m.tap(SpecialButton.CTRL);
        m.read(SpecialButton.CTRL, false);
        m.read(SpecialButton.ALT, true);
        assertEquals(1, calls[0]);
        m.removeListener(l);
        m.tap(SpecialButton.CTRL);
        assertEquals(1, calls[0]);
    }

    @Test
    public void nullIsTolerated() {
        m.tap(null);
        m.hold(null);
        m.addListener(null);
        assertEquals(OFF, m.state(null));
        assertFalse(m.read(null, true));
        assertFalse(ExtraKeyModifiers.isModifierKey(null));
    }

    @Test
    public void modifierKeys() {
        assertTrue(ExtraKeyModifiers.isModifierKey("CTRL"));
        assertTrue(ExtraKeyModifiers.isModifierKey("FN"));
        assertFalse(ExtraKeyModifiers.isModifierKey("TAB"));
    }
}
