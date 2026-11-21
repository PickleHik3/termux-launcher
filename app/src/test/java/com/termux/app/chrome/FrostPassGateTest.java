package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The frost pass is skipped only when every input is what the last landed pass saw. */
public class FrostPassGateTest {

    private static void key(FrostPassGate gate, int left, int top, int w, int h, boolean shown,
                            float corner) {
        gate.begin();
        gate.add(left).add(top).add(w).add(h).add(shown).add(corner);
    }

    @Test
    public void theFirstPassRunsAndAnIdenticalOneAfterItIsSkipped() {
        FrostPassGate gate = new FrostPassGate();
        key(gate, 0, 0, 1080, 120, true, 26f);
        assertFalse(gate.unchanged(false));
        gate.landed();
        key(gate, 0, 0, 1080, 120, true, 26f);
        assertTrue(gate.unchanged(false));
    }

    @Test
    public void aMovedRectCornerOrVisibilityRunsThePass() {
        FrostPassGate gate = new FrostPassGate();
        key(gate, 0, 0, 1080, 120, true, 26f);
        gate.landed();
        key(gate, 0, 40, 1080, 120, true, 26f);
        assertFalse("pane moved (keyboard up/down, camera split)", gate.unchanged(false));
        key(gate, 0, 0, 1080, 160, true, 26f);
        assertFalse("pane resized", gate.unchanged(false));
        key(gate, 0, 0, 1080, 120, false, 26f);
        assertFalse("visibility changed", gate.unchanged(false));
        key(gate, 0, 0, 1080, 120, true, 12f);
        assertFalse("corner changed", gate.unchanged(false));
    }

    @Test
    public void aDirtyMarkRunsThePassWhateverTheKey() {
        FrostPassGate gate = new FrostPassGate();
        key(gate, 0, 0, 1080, 120, true, 26f);
        gate.landed();
        key(gate, 0, 0, 1080, 120, true, 26f);
        assertFalse("wallpaper, scheme or a landed frame marks the frost dirty",
            gate.unchanged(true));
    }

    @Test
    public void anInvalidatedGateRunsTheNextPass() {
        FrostPassGate gate = new FrostPassGate();
        key(gate, 0, 0, 1080, 120, true, 26f);
        gate.landed();
        gate.invalidate();
        key(gate, 0, 0, 1080, 120, true, 26f);
        assertFalse(gate.unchanged(false));
    }

    @Test
    public void theOrderOfTheInputsIsPartOfTheKey() {
        FrostPassGate gate = new FrostPassGate();
        gate.begin();
        gate.add(1).add(2);
        gate.landed();
        gate.begin();
        gate.add(2).add(1);
        assertFalse(gate.unchanged(false));
    }
}
