package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.PaneBorderColours.ColourChoice;
import com.termux.app.terminal.PaneBorderColours.Mode;
import com.termux.app.terminal.PaneBorderColours.Roles;

import org.junit.Test;

/** Pane border colour choices: every mode, the defaults that change nothing, and the glow. */
public class PaneBorderColoursTest {

    private static final Roles ROLES = new Roles(0x96112233, 0xFF0000FF, 0xFF00FF00, 0xFFFF0000);
    private static final int ATTENTION = 0xFFFF5544;
    private static final int CUSTOM = 0x80ABCDEF;

    private static ColourChoice c(Mode m) {
        return new ColourChoice(m, CUSTOM);
    }

    private static PaneBorderStyle.Palette palette(Mode border, Mode focus, boolean glow, Mode glowMode) {
        return PaneBorderColours.palette(c(border), c(focus), glow, c(glowMode), ROLES, ATTENTION);
    }

    @Test
    public void everyModeResolves() {
        int border = 0xFF123456;
        assertEquals(ROLES.outline, PaneBorderColours.resolve(c(Mode.OUTLINE), ROLES, border));
        assertEquals(ROLES.primary, PaneBorderColours.resolve(c(Mode.PRIMARY), ROLES, border));
        assertEquals(ROLES.secondary, PaneBorderColours.resolve(c(Mode.SECONDARY), ROLES, border));
        assertEquals(ROLES.tertiary, PaneBorderColours.resolve(c(Mode.TERTIARY), ROLES, border));
        assertEquals("custom keeps its alpha", CUSTOM,
            PaneBorderColours.resolve(c(Mode.CUSTOM), ROLES, border));
        assertEquals(border, PaneBorderColours.resolve(c(Mode.SAME_AS_BORDER), ROLES, border));
    }

    @Test
    public void defaultsDecideAsBefore() {
        PaneBorderStyle.Palette p = palette(Mode.OUTLINE, Mode.PRIMARY, false, Mode.SAME_AS_BORDER);
        PaneBorderStyle.Decision lone = PaneBorderStyle.decide(1, true, false, p);
        assertEquals(PaneBorderStyle.Kind.RIM, lone.kind);
        assertEquals(PaneBorderStyle.NO_COLOUR, lone.colour);
        assertFalse(lone.glow);
        PaneBorderStyle.Decision focus = PaneBorderStyle.decide(2, true, false, p);
        assertEquals(PaneBorderStyle.Kind.FOCUS, focus.kind);
        assertEquals(ROLES.primary, focus.colour);
        assertFalse(focus.glow);
        assertEquals(PaneBorderStyle.decide(2, true, false,
            new PaneBorderStyle.Palette(ROLES.primary, ATTENTION)), focus);
    }

    @Test
    public void aCustomBorderReachesTheRimAndASameAsBorderFocus() {
        PaneBorderStyle.Palette p = palette(Mode.CUSTOM, Mode.SAME_AS_BORDER, false, Mode.SAME_AS_BORDER);
        assertEquals(CUSTOM, PaneBorderStyle.decide(1, false, false, p).colour);
        assertEquals(CUSTOM, PaneBorderStyle.decide(3, true, false, p).colour);
    }

    @Test
    public void sameAsBorderOnTheOutlineBorderUsesTheOutlineColour() {
        PaneBorderStyle.Palette p = palette(Mode.OUTLINE, Mode.SAME_AS_BORDER, true, Mode.SAME_AS_BORDER);
        assertEquals(PaneBorderStyle.NO_COLOUR, p.rim);
        assertEquals(ROLES.outline, p.focus);
        assertEquals(ROLES.outline, p.glowColour);
    }

    @Test
    public void glowRidesRimAndFocusButNeverAttention() {
        PaneBorderStyle.Palette p = palette(Mode.OUTLINE, Mode.PRIMARY, true, Mode.TERTIARY);
        PaneBorderStyle.Decision rim = PaneBorderStyle.decide(1, false, false, p);
        PaneBorderStyle.Decision focus = PaneBorderStyle.decide(2, true, false, p);
        PaneBorderStyle.Decision attention = PaneBorderStyle.decide(2, true, true, p);
        assertTrue(rim.glow);
        assertEquals(ROLES.tertiary, rim.glowColour);
        assertTrue(focus.glow);
        assertFalse(attention.glow);
        assertEquals(ATTENTION, attention.colour);
        assertNotEquals(rim, PaneBorderStyle.decide(1, false, false,
            palette(Mode.OUTLINE, Mode.PRIMARY, false, Mode.TERTIARY)));
        assertNotEquals(rim, PaneBorderStyle.decide(1, false, false,
            palette(Mode.OUTLINE, Mode.PRIMARY, true, Mode.SECONDARY)));
    }

    @Test
    public void garbageIdsFallBackToTheDefault() {
        assertEquals(Mode.OUTLINE, ColourChoice.fromId("nonsense", 7, Mode.OUTLINE).mode);
        assertEquals(Mode.PRIMARY, ColourChoice.fromId(null, 7, Mode.PRIMARY).mode);
        assertEquals(Mode.CUSTOM, ColourChoice.fromId("custom", 7, Mode.OUTLINE).mode);
        assertEquals(Mode.SAME_AS_BORDER,
            ColourChoice.fromId("same_as_border", 0, Mode.OUTLINE).mode);
    }
}
