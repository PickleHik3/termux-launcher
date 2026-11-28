package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.io.ExtraKeyEligibility.Band;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

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

    /**
     * A crossing from Home to the Terminal that stands the Terminal's rows draws them for the
     * Terminal from that frame: every key is as live, and every place switch as far in front, as
     * at the settle on the Terminal, so the toolbar rises in the look it lands in. At rest, and
     * before the stack stands the other half's rows, it is the resting place's.
     */
    @Test
    public void aRisingRowIsDressedForThePlaceItLandsOn() throws Exception {
        PageKind rising = ExtraKeyEligibility.rowPlace(PageKind.HOME,
            PageKind.TERMINAL);
        PageKind landed = ExtraKeyEligibility.rowPlace(PageKind.TERMINAL, null);
        assertSame(landed, rising);
        for (String key : everyKeyValue())
            assertEquals(key, ExtraKeyEligibility.isUsable(key, landed),
                ExtraKeyEligibility.isUsable(key, rising));
        assertSame(PageKind.HOME, ExtraKeyEligibility.rowPlace(PageKind.HOME, null));
    }

    @Test
    public void everythingIsUsableOnTheTerminal() throws Exception {
        for (String key : everyKeyValue())
            assertTrue(key, ExtraKeyEligibility.isUsable(key, PageKind.TERMINAL));
    }

    @Test
    public void typingAndModifiersGoWhereverTypingGoes() {
        for (String key : ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.keySet()) {
            assertSame(key, Band.TERMINAL_INPUT, ExtraKeyEligibility.bandOf(key));
            assertFalse(key, ExtraKeyEligibility.isUsable(key, PageKind.HOME));
            assertTrue(key, ExtraKeyEligibility.isUsable(key, PageKind.DISPLAY));
        }
        for (String modifier : new String[] {"CTRL", "ALT", "SHIFT", "FN"}) {
            assertSame(modifier, Band.TERMINAL_INPUT, ExtraKeyEligibility.bandOf(modifier));
            assertFalse(modifier, ExtraKeyEligibility.isUsable(modifier, PageKind.HOME));
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
        assertTrue(ExtraKeyEligibility.isUsable("KEYBOARD", PageKind.HOME));
        assertTrue(ExtraKeyEligibility.isUsable("DRAWER", PageKind.HOME));
        assertFalse(ExtraKeyEligibility.isUsable("PASTE", PageKind.HOME));
        assertFalse(ExtraKeyEligibility.isUsable("SCROLL", PageKind.HOME));
    }

    @Test
    public void thePlaceSwitchesWorkFromEveryPlace() {
        for (String tool : new String[] {
            LauncherToolRegistry.TOOL_WALL_GO,
            LauncherToolRegistry.TOOL_WALL_WIDGETS,
            LauncherToolRegistry.TOOL_WALL_TERMINAL,
            LauncherToolRegistry.TOOL_WALL_DISPLAY,
            LauncherToolRegistry.TOOL_WALL_ASSISTANT,
        }) {
            for (PageKind place : PageKind.values())
                assertTrue(tool + " on " + place, ExtraKeyEligibility.isUsable(key(tool), place));
        }
    }

    @Test
    public void thePageSwitcherKeyWorksOnEveryBar() {
        String switcher = key(LauncherToolRegistry.TOOL_APP_PAGE_SWITCHER);
        assertSame(Band.LAUNCHER, ExtraKeyEligibility.bandOf(switcher));
        for (PageKind place : PageKind.values())
            assertTrue(place.toString(), ExtraKeyEligibility.isUsable(switcher, place));
    }

    @Test
    public void noShippedDefaultRowHoldsThePageSwitcherKey() throws Exception {
        for (Field field : TermuxPropertyConstants.class.getDeclaredFields()) {
            if (!field.getName().startsWith("DEFAULT_IVALUE_") || field.getType() != String.class)
                continue;
            field.setAccessible(true);
            String value = (String) field.get(null);
            assertFalse(field.getName(), value != null
                && value.contains(LauncherToolRegistry.TOOL_APP_PAGE_SWITCHER));
        }
    }

    @Test
    public void theDictateKeyWorksOnEveryPlace() {
        for (PageKind place : PageKind.values())
            assertTrue(place.name(), ExtraKeyEligibility.isUsable(
                key(LauncherToolRegistry.TOOL_VOICE_DICTATE), place));
    }

    @Test
    public void theDisplayKeepsEverythingButMultiplexingAndSessions() {
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_PANE_SPLIT), PageKind.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_PANE_KILL_FOCUSED), PageKind.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_PANE_FOCUS_DIRECTION), PageKind.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_SESSION_BROWSER), PageKind.DISPLAY));
        assertFalse(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_SESSION_PANEL), PageKind.DISPLAY));
        // But its own pointer, its app switching and every launcher screen stay.
        assertTrue(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_MOUSE_TOGGLE), PageKind.DISPLAY));
        assertTrue(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_WINDOW_NEXT), PageKind.DISPLAY));
        assertTrue(ExtraKeyEligibility.isUsable(
            key(LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE), PageKind.DISPLAY));
    }

    @Test
    public void theHomePlaceKeepsOnlyWhatNeedsNoTerminal() {
        for (String tool : new String[] {
            LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS,
            LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE,
            LauncherToolRegistry.TOOL_APP_LAUNCH,
            LauncherToolRegistry.TOOL_APP_OPEN_APP_DRAWER,
            LauncherToolRegistry.TOOL_APPEARANCE_SET_WALLPAPER,
            LauncherToolRegistry.TOOL_APPEARANCE_SURFACE_EDITOR,
            LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_FORM,
            LauncherToolRegistry.TOOL_EXTRA_KEYS_EDIT,
        })
            assertTrue(tool, ExtraKeyEligibility.isUsable(key(tool), PageKind.HOME));

        for (String tool : new String[] {
            LauncherToolRegistry.TOOL_PANE_SPLIT,
            LauncherToolRegistry.TOOL_CLIPBOARD_PASTE,
            LauncherToolRegistry.TOOL_TERMINAL_SEARCH_SCROLLBACK,
            LauncherToolRegistry.TOOL_MOUSE_TOGGLE,
            LauncherToolRegistry.TOOL_WINDOW_NEXT,
        })
            assertFalse(tool, ExtraKeyEligibility.isUsable(key(tool), PageKind.HOME));
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
            "tool:wall.widgets tool:wall.terminal", PageKind.HOME));
        // One keystroke in it and the whole macro is typing.
        assertFalse(ExtraKeyEligibility.isUsable(
            "tool:wall.widgets HOME", PageKind.HOME));
        assertFalse(ExtraKeyEligibility.isUsable("HOME RIGHT", PageKind.HOME));
        assertTrue(ExtraKeyEligibility.isUsable("HOME RIGHT", PageKind.TERMINAL));
    }

    @Test
    public void anEmptyOrUnknownKeyIsLeftAlone() {
        for (PageKind place : PageKind.values()) {
            assertTrue(ExtraKeyEligibility.isUsable(null, place));
            assertTrue(ExtraKeyEligibility.isUsable("", place));
            // Greying a tool we cannot place would be a guess at the user's expense.
            assertTrue(ExtraKeyEligibility.isUsable("tool:something.new", place));
        }
        assertFalse(ExtraKeyEligibility.classifies("something.new"));
    }

    // ------------------------------------------------------------------------- the place switches

    @Test
    public void thePlaceSwitchesNameTheirPlaceAndNothingElseDoes() {
        assertSame(PageKind.HOME,
            ExtraKeyEligibility.placeSwitchTarget(key(LauncherToolRegistry.TOOL_WALL_WIDGETS)));
        assertSame(PageKind.TERMINAL,
            ExtraKeyEligibility.placeSwitchTarget(key(LauncherToolRegistry.TOOL_WALL_TERMINAL)));
        assertSame(PageKind.DISPLAY,
            ExtraKeyEligibility.placeSwitchTarget(key(LauncherToolRegistry.TOOL_WALL_DISPLAY)));
        assertSame(PageKind.ASSISTANT,
            ExtraKeyEligibility.placeSwitchTarget(key(LauncherToolRegistry.TOOL_WALL_ASSISTANT)));
        // The Assistant key adds the page when the wall has none, so it is never drawn dead.
        assertTrue(ExtraKeyEligibility.isUsable(key(LauncherToolRegistry.TOOL_WALL_ASSISTANT),
            PageKind.HOME, java.util.Arrays.asList(PageKind.HOME)));
        // wall.go counts only when it says where it is going.
        assertSame(PageKind.DISPLAY, ExtraKeyEligibility.placeSwitchTarget(
            key(LauncherToolRegistry.TOOL_WALL_GO) + ":place=display"));
        assertNull(ExtraKeyEligibility.placeSwitchTarget(
            key(LauncherToolRegistry.TOOL_WALL_GO)));
        assertNull(ExtraKeyEligibility.placeSwitchTarget(
            key(LauncherToolRegistry.TOOL_PANE_SPLIT)));
        assertNull(ExtraKeyEligibility.placeSwitchTarget("ESC"));
        assertNull(ExtraKeyEligibility.placeSwitchTarget(null));
    }

    /** A switch towards a place the wall does not have is dead, wherever the wall stands. */
    @Test
    public void aPlaceSwitchIsUsableOnlyTowardsAPlaceTheWallHas() {
        List<PageKind> homeScreen = com.termux.app.wall.PaneWallPolicy.availablePages(false, true, false);
        List<PageKind> terminalOnly = com.termux.app.wall.PaneWallPolicy.availablePages(true, false, false);
        String widgets = key(LauncherToolRegistry.TOOL_WALL_WIDGETS);
        String display = key(LauncherToolRegistry.TOOL_WALL_DISPLAY);
        String goDisplay = key(LauncherToolRegistry.TOOL_WALL_GO) + ":place=display";

        assertTrue(ExtraKeyEligibility.isUsable(widgets, PageKind.TERMINAL, homeScreen));
        assertFalse(ExtraKeyEligibility.isUsable(display, PageKind.TERMINAL, homeScreen));
        assertFalse(ExtraKeyEligibility.isUsable(goDisplay, PageKind.HOME, homeScreen));
        assertFalse(ExtraKeyEligibility.isUsable(widgets, PageKind.TERMINAL, terminalOnly));
        // The switch for the one place the wall has stays live: it is a row the user wrote.
        assertTrue(ExtraKeyEligibility.isUsable(key(LauncherToolRegistry.TOOL_WALL_TERMINAL),
            PageKind.TERMINAL, terminalOnly));
        // Nothing else reads the wall: keys that were usable stay usable, dead ones stay dead.
        assertTrue(ExtraKeyEligibility.isUsable("ESC", PageKind.TERMINAL, terminalOnly));
        assertFalse(ExtraKeyEligibility.isUsable("ESC", PageKind.HOME, homeScreen));
        assertTrue(ExtraKeyEligibility.isUsable(key(LauncherToolRegistry.TOOL_WALL_GO),
            PageKind.TERMINAL, terminalOnly));
        assertTrue(ExtraKeyEligibility.isUsable(null, PageKind.TERMINAL, terminalOnly));
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
        // window.move and its left/right pair are multiplexing, beside window.select, and so are
        // pane.consume and pane.expel.
        assertEquals(119, declared);
        assertEquals(Integer.valueOf(0), counts.get(Band.TERMINAL_INPUT));
        assertEquals(Integer.valueOf(36), counts.get(Band.LAUNCHER));
        assertEquals(Integer.valueOf(4), counts.get(Band.SESSION_OVERLAY));
        assertEquals(Integer.valueOf(58), counts.get(Band.MULTIPLEX));
        assertEquals(Integer.valueOf(21), counts.get(Band.TERMINAL_TOOL));
    }

    // -------------------------------------------------------------------------------- helpers

    private static String key(String toolName) {
        return TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX + toolName;
    }

    private static void assertBand(Band band, boolean widgets, boolean terminal, boolean display) {
        assertEquals(band + " on WIDGETS", widgets, band.worksOn(PageKind.HOME));
        assertEquals(band + " on TERMINAL", terminal, band.worksOn(PageKind.TERMINAL));
        assertEquals(band + " on DISPLAY", display, band.worksOn(PageKind.DISPLAY));
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
