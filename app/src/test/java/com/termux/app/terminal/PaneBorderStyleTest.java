package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The border decision: one rim for a lone pane, the focus colour only in a split, attention on top. */
public class PaneBorderStyleTest {

    private static final int FOCUS = 0xFF3366FF;
    private static final int ATTENTION = 0xFFFF5544;
    private static final PaneBorderStyle.Palette PALETTE =
        new PaneBorderStyle.Palette(FOCUS, ATTENTION);

    @Test
    public void aLonePaneWearsTheSharedRimEvenWhenFocused() {
        PaneBorderStyle.Decision d = PaneBorderStyle.decide(1, true, false, PALETTE);
        assertEquals(PaneBorderStyle.Kind.RIM, d.kind);
        assertEquals("the rim owns its colours", PaneBorderStyle.NO_COLOUR, d.colour);
    }

    @Test
    public void inASplitTheFocusedPaneTakesTheActiveColour() {
        PaneBorderStyle.Decision d = PaneBorderStyle.decide(2, true, false, PALETTE);
        assertEquals(PaneBorderStyle.Kind.FOCUS, d.kind);
        assertEquals(FOCUS, d.colour);
    }

    @Test
    public void inASplitTheOthersKeepTheSharedRim() {
        assertEquals(PaneBorderStyle.Kind.RIM,
            PaneBorderStyle.decide(3, false, false, PALETTE).kind);
    }

    @Test
    public void attentionOverridesFocusAndRim() {
        PaneBorderStyle.Decision lone = PaneBorderStyle.decide(1, true, true, PALETTE);
        PaneBorderStyle.Decision focused = PaneBorderStyle.decide(2, true, true, PALETTE);
        PaneBorderStyle.Decision other = PaneBorderStyle.decide(2, false, true, PALETTE);
        for (PaneBorderStyle.Decision d : new PaneBorderStyle.Decision[] {lone, focused, other}) {
            assertEquals(PaneBorderStyle.Kind.ATTENTION, d.kind);
            assertEquals(ATTENTION, d.colour);
        }
    }

    @Test
    public void attentionColourIsNotTheFocusColour() {
        assertTrue(PALETTE.attention != PALETTE.focus);
    }

    @Test
    public void whenAttentionClearsOnFocusTheBorderFallsBackToWhatFocusSays() {
        PaneAttention attention = new PaneAttention();
        attention.set(7, PaneAttention.Cause.BELL, true);
        assertEquals(PaneBorderStyle.Kind.ATTENTION,
            PaneBorderStyle.decide(2, true, attention.isSet(7), PALETTE).kind);
        attention.clear(7);
        assertEquals(PaneBorderStyle.Kind.FOCUS,
            PaneBorderStyle.decide(2, true, attention.isSet(7), PALETTE).kind);
        assertEquals(PaneBorderStyle.Kind.RIM,
            PaneBorderStyle.decide(1, true, attention.isSet(7), PALETTE).kind);
    }
}
