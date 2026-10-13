package com.termux.app.terminal;

import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The keycap strip's choice of rows: decayed usage across every row kind, one chip per row, the
 * defaults behind it, scrollback search never; and the labels that tell rows of one tool apart.
 */
public class CommandPaletteStripTest {

    private static final Predicate<String> EVERYTHING = key -> true;
    private static final List<String> NO_FALLBACKS = Collections.emptyList();
    private static final int LIMIT = CommandPaletteView.KEYCAP_COUNT;

    private static final String SPLIT = "keyboard.set_form {\"form\":\"split\"}";
    private static final String DOCKED = "keyboard.set_form {\"form\":\"docked\"}";
    private static final String FLOATING = "keyboard.set_form {\"form\":\"floating\"}";
    private static final String SCROLLBACK = LauncherToolRegistry.TOOL_TERMINAL_SEARCH_SCROLLBACK;

    private static String app(String stableId) {
        return LauncherToolRegistry.TOOL_APP_LAUNCH + " {\"query\":\"" + stableId + "\"}";
    }

    private static String contact(long id, String lookupKey, String number) {
        return LauncherToolRegistry.TOOL_CONTACT_OPEN + " {\"contact_id\":" + id
            + ",\"lookup_key\":\"" + lookupKey + "\",\"number\":\"" + number + "\"}";
    }

    private static Map<String, Double> usage(Object... keyThenScore) {
        Map<String, Double> out = new HashMap<>();
        for (int i = 0; i < keyThenScore.length; i += 2)
            out.put((String) keyThenScore[i], ((Number) keyThenScore[i + 1]).doubleValue());
        return out;
    }

    // ------------------------------------------------------------------ choosing rows

    @Test
    public void ranksActionsAppsAndContactsTogetherByDecayedUsage() {
        Map<String, Double> usage = usage(
            SPLIT, 2.0,
            app("shizuku"), 6.0,
            contact(7, "k7", "+1 555"), 2.5,
            app("steam"), 3.0,
            app("firefox"), 1.0);
        assertEquals(Arrays.asList(app("shizuku"), app("steam"), contact(7, "k7", "+1 555"), SPLIT),
            CommandPaletteStrip.pickKeys(usage, EVERYTHING, NO_FALLBACKS, LIMIT));
    }

    /** The phone's own blob: three keyboard types, scrollback search, five apps, a contact. */
    @Test
    public void thePhonesUsage_fillsTheStripWithAppsAndOneKeyboardType() {
        Map<String, Double> usage = usage(
            SPLIT, 2.0, DOCKED, 2.0, FLOATING, 1.0,
            SCROLLBACK, 1.0,
            app("shizuku"), 6.0, app("solid"), 4.0, app("steam"), 3.0,
            app("firefox"), 1.0, app("discord"), 1.0,
            contact(7, "k7", "+1 555"), 1.0);
        // split and docked tie; the key breaks it, so the strip is the same on every open.
        assertEquals(Arrays.asList(app("shizuku"), app("solid"), app("steam"), DOCKED),
            CommandPaletteStrip.pickKeys(usage, EVERYTHING, NO_FALLBACKS, LIMIT));
    }

    @Test
    public void scrollbackSearch_isNeverOffered() {
        Map<String, Double> usage = usage(SCROLLBACK, 50.0, SPLIT, 1.0);
        List<String> keys = CommandPaletteStrip.pickKeys(usage, EVERYTHING,
            Arrays.asList(SCROLLBACK, "session.new"), LIMIT);
        assertEquals(Arrays.asList(SPLIT, "session.new"), keys);
    }

    @Test
    public void fallbacksFillTheEmptySlots_inTheirOrder_withoutRepeatingARow() {
        Map<String, Double> usage = usage("pane.split_vertical", 3.0);
        List<String> fallbacks = Arrays.asList("terminal.toggle_soft_keyboard",
            "pane.split_vertical", "terminal.hints", "terminal.font_size_increase",
            "session.new");
        assertEquals(Arrays.asList("pane.split_vertical", "terminal.toggle_soft_keyboard",
                "terminal.hints", "terminal.font_size_increase"),
            CommandPaletteStrip.pickKeys(usage, EVERYTHING, fallbacks, LIMIT));
    }

    /** Fewer usable defaults than slots is a short strip, not a padded one. */
    @Test
    public void shortOfRows_isAShortStrip() {
        assertEquals(Collections.singletonList("session.new"),
            CommandPaletteStrip.pickKeys(Collections.<String, Double>emptyMap(), EVERYTHING,
                Arrays.asList(SCROLLBACK, "session.new"), LIMIT));
    }

    /** An uninstalled app (or a contact not loaded) gives its slot to the next row. */
    @Test
    public void unresolvableRows_giveTheirSlotToTheNext() {
        Map<String, Double> usage = usage(app("gone"), 9.0, app("steam"), 3.0, SPLIT, 2.0);
        Set<String> resolvable = new HashSet<>(Arrays.asList(app("steam"), SPLIT, "session.new"));
        assertEquals(Arrays.asList(app("steam"), SPLIT, "session.new"),
            CommandPaletteStrip.pickKeys(usage, resolvable::contains,
                Arrays.asList("session.new"), LIMIT));
    }

    /** A contact found by name and again by number is two keys, and one chip. */
    @Test
    public void oneContact_isOneChip() {
        Map<String, Double> usage = usage(
            contact(7, "k7", "+1 555"), 3.0,
            contact(7, "k7", "+1 777"), 2.0,
            contact(8, "k8", "+1 888"), 1.0);
        assertEquals(Arrays.asList(contact(7, "k7", "+1 555"), contact(8, "k8", "+1 888")),
            CommandPaletteStrip.pickKeys(usage, EVERYTHING, NO_FALLBACKS, LIMIT));
    }

    @Test
    public void unusedKeysAndTheLimit() {
        Map<String, Double> usage = usage(SPLIT, 0.0, DOCKED, 1.0, FLOATING, 0.5);
        assertEquals(Collections.singletonList(DOCKED),
            CommandPaletteStrip.pickKeys(usage, EVERYTHING, NO_FALLBACKS, 1));
        assertEquals(Arrays.asList(DOCKED, FLOATING),
            CommandPaletteStrip.pickKeys(usage, EVERYTHING, NO_FALLBACKS, LIMIT));
    }

    @Test
    public void keyParts() {
        assertEquals("keyboard.set_form", CommandPaletteStrip.toolOf(SPLIT));
        assertEquals("session.new", CommandPaletteStrip.toolOf("session.new"));
        assertEquals("split", CommandPaletteStrip.argumentsOf(SPLIT).optString("form"));
        assertEquals(null, CommandPaletteStrip.argumentsOf("session.new"));
        assertEquals(CommandPaletteStrip.identityOf(contact(7, "k7", "+1")),
            CommandPaletteStrip.identityOf(contact(7, "k7", "+2")));
        assertEquals(SPLIT, CommandPaletteStrip.identityOf(SPLIT));
    }

    /** The strip resolves keys the way the palette writes them. */
    @Test
    public void keysMatchTheRowsThePaletteBuilds() {
        CommandPaletteFilter.Entry split = TerminalCommandPalette.keyboardFormEntry(
            KeyboardForm.SPLIT, "Split keyboard", "");
        assertEquals(SPLIT, CommandPaletteActionStats.keyFor(split));
    }

    // ------------------------------------------------------------------ labels

    @Test
    public void keyboardTypes_areNamedByTheirOwnValue() {
        assertEquals("split", CommandPaletteStrip.actionLabel(
            TerminalCommandPalette.keyboardFormEntry(KeyboardForm.SPLIT, "Split keyboard", ""),
            "set"));
        assertEquals("docked", CommandPaletteStrip.actionLabel(
            TerminalCommandPalette.keyboardFormEntry(KeyboardForm.DOCKED, "Docked keyboard", ""),
            "set"));
        assertEquals("floating", CommandPaletteStrip.actionLabel(
            TerminalCommandPalette.keyboardFormEntry(KeyboardForm.FLOATING, "Floating keyboard",
                ""), "set"));
    }

    @Test
    public void otherArgumentRows_areNamedByTheirTitle() {
        assertEquals("Dvorak", CommandPaletteStrip.actionLabel(
            row("keyboard.select_layout", "Layout: Dvorak", "layout", "latn_dvorak"), "select"));
        assertEquals("Session 2", CommandPaletteStrip.actionLabel(
            row("session.activate_by_index", "Session 2 · current", "index", 1), "activate"));
    }

    @Test
    public void bareTools_takeTheirBareLabel() {
        CommandPaletteFilter.Entry bare = new CommandPaletteFilter.Entry("pane.split_vertical",
            "Split vertically", "", "pane", Collections.<String>emptyList(), true, null, false,
            LauncherToolRegistry.ToolRisk.LOW);
        assertEquals("split", CommandPaletteStrip.actionLabel(bare, "split"));
    }

    @Test
    public void contacts_goByFirstName() {
        assertEquals("Sam", CommandPaletteStrip.firstName("Sam Rivera"));
        assertEquals("Cher", CommandPaletteStrip.firstName("  Cher "));
        assertTrue(CommandPaletteStrip.firstName("").isEmpty());
    }

    private static CommandPaletteFilter.Entry row(String tool, String title, String argument,
                                                  Object value) {
        JSONObject arguments = new JSONObject();
        try {
            arguments.put(argument, value);
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
        return new CommandPaletteFilter.Entry(tool, title, "", "keyboard",
            Collections.<String>emptyList(), true, null, false, LauncherToolRegistry.ToolRisk.LOW,
            arguments);
    }
}
