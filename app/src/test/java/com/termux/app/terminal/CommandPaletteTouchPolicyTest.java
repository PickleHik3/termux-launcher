package com.termux.app.terminal;

import com.termux.app.terminal.CommandPaletteTouchPolicy.Target;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Who a touch on the open palette belongs to, on the Surface's landscape Home (2736x1824): the
 * page's room (0,56)-(2736,1760), the dock (0,1623)-(2736,1748) and so the Home modal bounds
 * (0,56)-(2736,1623), and a floating keyboard whose frame, handle row included, stands at
 * (1778,1123)-(2736,1582).
 */
public class CommandPaletteTouchPolicyTest {

    private static final float[] HOME_MODAL = {0f, 56f, 2736f, 1623f};
    private static final float[] FLOATING_FRAME = {1778f, 1123f, 2736f, 1582f};
    private static final float[] DOCK = {0f, 1623f, 2736f, 1748f};
    private static final float[] KEYBOARD_AND_DOCK = {
        1778f, 1123f, 2736f, 1582f,
        0f, 1623f, 2736f, 1748f,
    };

    @Test
    public void aKeyOnTheFloatingKeyboard_reachesTheKeyboard() {
        assertEquals(Target.PASS_THROUGH,
            CommandPaletteTouchPolicy.classify(2257f, 1364f, false, HOME_MODAL, KEYBOARD_AND_DOCK));
    }

    @Test
    public void theFloatingKeyboardsDragHandle_reachesTheKeyboard() {
        assertEquals(Target.PASS_THROUGH,
            CommandPaletteTouchPolicy.classify(2257f, 1135f, false, HOME_MODAL, KEYBOARD_AND_DOCK));
    }

    @Test
    public void theDock_reachesTheDock() {
        assertEquals(Target.PASS_THROUGH,
            CommandPaletteTouchPolicy.classify(1368f, 1685f, false, HOME_MODAL, KEYBOARD_AND_DOCK));
    }

    @Test
    public void theFreePage_closesThePalette() {
        assertEquals(Target.OUTSIDE,
            CommandPaletteTouchPolicy.classify(900f, 1300f, false, HOME_MODAL, KEYBOARD_AND_DOCK));
    }

    @Test
    public void withoutTheKeyboardNamed_aKeyTapWouldCloseThePalette() {
        // The bug seen on the Surface: the floating keyboard lies inside the modal bounds.
        assertEquals(Target.OUTSIDE,
            CommandPaletteTouchPolicy.classify(2257f, 1364f, false, HOME_MODAL, DOCK));
    }

    @Test
    public void theGlass_winsOverAKeyboardUnderIt() {
        assertEquals(Target.PALETTE,
            CommandPaletteTouchPolicy.classify(2257f, 1364f, true, HOME_MODAL, KEYBOARD_AND_DOCK));
    }

    @Test
    public void aKeyboardOnASideEdge_inTheWorkspace_reachesTheKeyboard() {
        float[] terminal = {0f, 56f, 2638f, 1623f};
        float[] sideKeyboard = {0f, 400f, 700f, 1600f};
        assertEquals(Target.PASS_THROUGH,
            CommandPaletteTouchPolicy.classify(300f, 900f, false, terminal, sideKeyboard));
        assertEquals(Target.OUTSIDE,
            CommandPaletteTouchPolicy.classify(1300f, 900f, false, terminal, sideKeyboard));
    }

    @Test
    public void belowTheModalBounds_passesThrough() {
        assertEquals(Target.PASS_THROUGH,
            CommandPaletteTouchPolicy.classify(100f, 1700f, false, HOME_MODAL, null));
    }

    @Test
    public void noBoundsYet_isAFullScrim() {
        assertEquals(Target.OUTSIDE,
            CommandPaletteTouchPolicy.classify(100f, 1700f, false, null, null));
        assertEquals(Target.OUTSIDE,
            CommandPaletteTouchPolicy.classify(100f, 1700f, false, new float[] {0f, 0f, 0f, 0f},
                null));
    }

    @Test
    public void edgesAreHalfOpen_andEmptyRectsPassNothing() {
        assertEquals(Target.PASS_THROUGH,
            CommandPaletteTouchPolicy.classify(1778f, 1123f, false, HOME_MODAL, FLOATING_FRAME));
        assertEquals(Target.OUTSIDE,
            CommandPaletteTouchPolicy.classify(1777f, 1123f, false, HOME_MODAL, FLOATING_FRAME));
        assertEquals(Target.OUTSIDE,
            CommandPaletteTouchPolicy.classify(500f, 500f, false, HOME_MODAL,
                new float[] {600f, 400f, 400f, 600f}));
    }
}
