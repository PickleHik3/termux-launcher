package com.termux.app.terminal;

import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Robolectric because the colour maths goes through androidx ColorUtils. */
@RunWith(RobolectricTestRunner.class)
public class KeybindGroupPaletteTest {

    private static final int GLASS = 0xFF14171A;

    @Test
    public void groupComesFromTheActionIdNamespace() {
        assertEquals(KeybindGroupPalette.Group.PANES,
            KeybindGroupPalette.groupFor("pane.split_vertical"));
        assertEquals(KeybindGroupPalette.Group.WINDOWS,
            KeybindGroupPalette.groupFor("window.rename_prompt"));
        assertEquals(KeybindGroupPalette.Group.SESSION,
            KeybindGroupPalette.groupFor("session.rename_prompt"));
        assertEquals(KeybindGroupPalette.Group.WORKSPACE,
            KeybindGroupPalette.groupFor("workspace.save"));
        assertEquals(KeybindGroupPalette.Group.TERMINAL,
            KeybindGroupPalette.groupFor("terminal.toggle_scratchpad"));
        assertEquals(KeybindGroupPalette.Group.CLIPBOARD,
            KeybindGroupPalette.groupFor("clipboard.paste"));
        assertEquals(KeybindGroupPalette.Group.APP,
            KeybindGroupPalette.groupFor("app.command_palette"));
        // Fonts ride with appearance rather than earning a legend header of their own.
        assertEquals(KeybindGroupPalette.Group.APPEARANCE,
            KeybindGroupPalette.groupFor("fonts.pick"));
        assertEquals(KeybindGroupPalette.Group.APPEARANCE,
            KeybindGroupPalette.groupFor("appearance.set_wallpaper"));
    }

    @Test
    public void anUnknownNamespaceStillGetsAGroup() {
        // Every bound key must be colourable: a config file can name an action this table has
        // never heard of, and a cap with no colour would light as a hole in the keyboard.
        assertEquals(KeybindGroupPalette.Group.VIEW, KeybindGroupPalette.groupFor("unmap"));
        assertEquals(KeybindGroupPalette.Group.VIEW, KeybindGroupPalette.groupFor("send_text"));
    }

    @Test
    public void everyGroupGetsItsOwnColour() {
        Set<Integer> colors = new HashSet<>();
        for (KeybindGroupPalette.Group group : KeybindGroupPalette.Group.values()) {
            colors.add(KeybindGroupPalette.colorFor(group, 0xFF7FCFFF, GLASS));
        }
        assertEquals(KeybindGroupPalette.Group.values().length, colors.size());
    }

    @Test
    public void adjacentLegendGroupsAreToldApartByHue() {
        // The legend stacks groups in enum order, so neighbours are what a reader compares.
        KeybindGroupPalette.Group[] groups = KeybindGroupPalette.Group.values();
        for (int i = 1; i < groups.length; i++) {
            float previous = groups[i - 1].hueOffset;
            float current = groups[i].hueOffset;
            float gap = Math.abs(previous - current);
            gap = Math.min(gap, 360f - gap);
            assertTrue(groups[i - 1] + " vs " + groups[i] + " differ by only " + gap,
                gap >= 40f);
        }
    }

    @Test
    public void aDesaturatedThemeStillYieldsDistinguishableColours() {
        // A greyscale primary rotates into nothing without the saturation floor, which would leave
        // every group the same grey on the keyboard.
        int first = KeybindGroupPalette.colorFor(KeybindGroupPalette.Group.WINDOWS,
            Color.rgb(128, 128, 128), GLASS);
        int second = KeybindGroupPalette.colorFor(KeybindGroupPalette.Group.SESSION,
            Color.rgb(128, 128, 128), GLASS);
        assertNotEquals(first, second);
    }
}
