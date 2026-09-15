package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.io.ExtraKeyEligibility.Band;
import com.termux.app.wall.PaneWallPage;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Which keys can act on which place, and the promise that no tool we ship goes unclassified. */
public class ExtraKeyEligibilityTest {

    // ----------------------------------------------------------------------------- the table

    @Test
    public void everyBandWorksWhereTheTableSaysItDoes() {
        assertBand(Band.TERMINAL_INPUT, false, true, true);
        assertBand(Band.TERMINAL_TOOL, false, true, true);
        assertBand(Band.MULTIPLEX, false, true, false);
        assertBand(Band.SESSION_OVERLAY, true, true, false);
        assertBand(Band.LAUNCHER, true, true, true);
    }

    @Test
    public void everythingIsUsableOnTheTerminal() throws Exception {
        for (String key : everyKeyValue())
            assertTrue(key, ExtraKeyEligibility.isUsable(key, PaneWallPage.TERMINAL));
    }

    @Test
    public void typingAndModifiersGoWhereverTypingGoes() {
        for (String key : ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.keySet()) {
            assertSame(key, Band.TERMINAL_INPUT, ExtraKeyEligibility.bandOf(key));
            assertFalse(key, ExtraKeyEligibility.isUsable(key, PaneWallPage.WIDGETS));
            assertTrue(key, ExtraKeyEligibility.isUsable(key, PaneWallPage.DISPLAY));
        }
        for (String modifier : new String[] {"CTRL", "ALT", "SHIFT", "FN"}) {
            assertSame(modifier, Band.TERMINAL_INPUT, ExtraKeyEligibility.bandOf(modifier));
            assertFalse(modifier, ExtraKeyEligibility.isUsable(modifier, PaneWallPage.WIDGETS));
        }
        // Literal text a key types is input like any other.
        assertSame(Band.TERMINAL_INPUT, ExtraKeyEligibility.bandOf("~"));
        assertSame(Band.TERMINAL_INPUT, ExtraKeyEligibility.bandOf("-_-"));
    }

    @Test
    public void theRowsOwnKeysLandInTheirBands() {
        assertSame(Band.LAUNCHER, ExtraKeyEligibility.bandOf("KEYBOARD"));
        assertSame(Band.SESSION_OVERLAY, ExtraKeyEligibility.bandOf("DRAWER"));
        assertSame(Band.TERMINAL_TOOL, ExtraKeyEligibility.bandOf("PASTE"));
        assertSame(Band.TERMINAL_TOOL, ExtraKeyEligibility.bandOf("SCROLL"));
        // The home place keeps the keyboard and the drawer and loses the rest.
        assertTrue(ExtraKeyEligibility.isUsable("KEYBOARD", PaneWallPage.WIDGETS));
        assertTrue(ExtraKeyEligibility.isUsable("DRAWER", PaneWallPage.WIDGETS));
        assertFalse(ExtraKeyEligibility.isUsable("PASTE", PaneWallPage.WIDGETS));
        assertFalse(ExtraKeyEligibility.isUsable("SCROLL", PaneWallPage.WIDGETS));
    }

    @Test
    public void thePlaceSwitchesWorkFromEveryPlace() {
        for (String tool : new String[] {
            LauncherToolRegistry.TOOL_WALL_GO,
            LauncherToolRegistry.TOOL_WALL_WIDGETS,
            LauncherToolRegistry.TOOL_WALL_TERMINAL,
            LauncherToolRegistry.TOOL_WALL_DISPLAY,
        }) {
            for (PaneWallPage place : PaneWallPage.values())
                assertTrue(tool + " on " + place, ExtraKeyEligibility.isUsable(key(tool), place));
        }
    }

    @Test
    public void theDisplayKeepsEverythingButMultiplexingAndSessions() {
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_PANE_SPLIT), PaneWallPage.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_PANE_KILL_FOCUSED), PaneWallPage.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_PANE_FOCUS_DIRECTION), PaneWallPage.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_SESSION_BROWSER), PaneWallPage.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_SESSION_PANEL), PaneWallPage.DISPLAY));
        // But its own pointer, its app switching and every launcher screen stay.
        assertTrue(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_MOUSE_TOGGLE), PaneWallPage.DISPLAY));
        assertTrue(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_WINDOW_NEXT), PaneWallPage.DISPLAY));
        assertTrue(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE), PaneWallPage.DISPLAY));
    }

    @Test
    public void theHomePlaceKeepsOnlyWhatNeedsNoTerminal() {
        for (String tool : new String[] {
            LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS,
            LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE,
            LauncherToolRegistry.TOOL_APP_LAUNCH,
            LauncherToolRegistry.TOOL_APPEARANCE_SET_WALLPAPER,
            LauncherToolRegistry.TOOL_APPEARANCE_SURFACE_EDITOR,
            LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_FORM,
            LauncherToolRegistry.TOOL_EXTRA_KEYS_EDIT,
        })
            assertTrue(tool, ExtraKeyEligibility.isUsable(key(tool), PaneWallPage.WIDGETS));

        for (String tool : new String[] {
            LauncherToolRegistry.TOOL_PANE_SPLIT,
            LauncherToolRegistry.TOOL_CLIPBOARD_PASTE,
            LauncherToolRegistry.TOOL_TERMINAL_SEARCH_SCROLLBACK,
            LauncherToolRegistry.TOOL_MOUSE_TOGGLE,
            LauncherToolRegistry.TOOL_WINDOW_NEXT,
        })
            assertFalse(tool, ExtraKeyEligibility.isUsable(key(tool), PaneWallPage.WIDGETS));
    }

    @Test
    public void aToolKeyIsReadWithoutItsArguments() {
        assertEquals("pane.move_to_edge",
            ExtraKeyEligibility.toolNameOf("tool:pane.move_to_edge:edge=left"));
        assertEquals("wall.go", ExtraKeyEligibility.toolNameOf("tool:wall.go"));
        assertSame(Band.MULTIPLEX, ExtraKeyEligibility.bandOf("tool:pane.move_to_edge:edge=left"));
    }

    @Test
    public void aMacroIsOnlyAsUsableAsItsLeastUsableStep() {
        // Two place switches: still a place switch.
        assertTrue(ExtraKeyEligibility.isUsable(
            "tool:wall.widgets tool:wall.terminal", PaneWallPage.WIDGETS));
        // One keystroke in it and the whole macro is typing.
        assertFalse(ExtraKeyEligibility.isUsable(
            "tool:wall.widgets HOME", PaneWallPage.WIDGETS));
        assertFalse(ExtraKeyEligibility.isUsable("HOME RIGHT", PaneWallPage.WIDGETS));
        assertTrue(ExtraKeyEligibility.isUsable("HOME RIGHT", PaneWallPage.TERMINAL));
    }

    @Test
    public void anEmptyOrUnknownKeyIsLeftAlone() {
        for (PaneWallPage place : PaneWallPage.values()) {
            assertTrue(ExtraKeyEligibility.isUsable(null, place));
            assertTrue(ExtraKeyEligibility.isUsable("", place));
            // Greying a tool we cannot place would be a guess at the user's expense.
            assertTrue(ExtraKeyEligibility.isUsable("tool:something.new", place));
        }
        assertFalse(ExtraKeyEligibility.classifies("something.new"));
    }

    // ------------------------------------------------------------------------ the coverage gate

    /**
     * Every tool the registry declares must be in the table. A new {@code TOOL_*} that nobody
     * classified would silently fall through to "usable everywhere", which is the one answer that
     * is never wrong but is often useless — so it has to be a deliberate choice, made here.
     */
    @Test
    public void everyDeclaredToolIsClassified() throws Exception {
        List<String> unclassified = new ArrayList<>();
        int declared = 0;
        for (Field field : LauncherToolRegistry.class.getDeclaredFields()) {
            if (!field.getName().startsWith("TOOL_")) continue;
            if (field.getType() != String.class) continue;
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            String tool = (String) field.get(null);
            declared++;
            if (!ExtraKeyEligibility.classifies(tool))
                unclassified.add(field.getName() + " (\"" + tool + "\")");
        }
        assertTrue("LauncherToolRegistry declares no TOOL_* constants — the gate is not looking"
            + " at the right class", declared > 50);
        assertEquals("every TOOL_* must be given a band in ExtraKeyEligibility",
            "[]", unclassified.toString());
    }

    /** The counts the classification comes to, so a band silently changing size is noticed. */
    @Test
    public void theBandsHoldTheToolsTheyAreMeantTo() throws Exception {
        Map<Band, Integer> counts = new EnumMap<>(Band.class);
        for (Band band : Band.values()) counts.put(band, 0);
        int declared = 0;
        for (Field field : LauncherToolRegistry.class.getDeclaredFields()) {
            if (!field.getName().startsWith("TOOL_") || field.getType() != String.class) continue;
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            String tool = (String) field.get(null);
            declared++;
            Band band = ExtraKeyEligibility.bandOfTool(tool);
            counts.put(band, counts.get(band) + 1);
        }
        assertEquals(90, declared);
        assertEquals(Integer.valueOf(0), counts.get(Band.TERMINAL_INPUT));
        assertEquals(Integer.valueOf(25), counts.get(Band.LAUNCHER));
        assertEquals(Integer.valueOf(4), counts.get(Band.SESSION_OVERLAY));
        assertEquals(Integer.valueOf(41), counts.get(Band.MULTIPLEX));
        assertEquals(Integer.valueOf(20), counts.get(Band.TERMINAL_TOOL));
    }

    // -------------------------------------------------------------------------------- helpers

    private static String key(String toolName) {
        return TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX + toolName;
    }

    private static void assertBand(Band band, boolean widgets, boolean terminal, boolean display) {
        assertEquals(band + " on WIDGETS", widgets, band.worksOn(PaneWallPage.WIDGETS));
        assertEquals(band + " on TERMINAL", terminal, band.worksOn(PaneWallPage.TERMINAL));
        assertEquals(band + " on DISPLAY", display, band.worksOn(PaneWallPage.DISPLAY));
    }

    /** A sample of every shape of key value the row can carry. */
    private static List<String> everyKeyValue() throws Exception {
        List<String> values = new ArrayList<>(ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS
            .keySet());
        values.add("CTRL");
        values.add("ALT");
        values.add("SHIFT");
        values.add("FN");
        values.add("KEYBOARD");
        values.add("DRAWER");
        values.add("PASTE");
        values.add("SCROLL");
        values.add("~");
        for (Field field : LauncherToolRegistry.class.getDeclaredFields()) {
            if (!field.getName().startsWith("TOOL_") || field.getType() != String.class) continue;
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            values.add(key((String) field.get(null)));
        }
        return values;
    }
}
