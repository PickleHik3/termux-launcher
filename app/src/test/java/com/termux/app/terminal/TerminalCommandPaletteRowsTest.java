package com.termux.app.terminal;

import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The hand-built palette rows, which the projection in {@code buildEntries} never touches. Building
 * the whole palette needs a live {@code TermuxActivity}; these cover the row shape and the argument
 * merge, which is where a per-session rename row could silently lose its index.
 */
@RunWith(RobolectricTestRunner.class)
public class TerminalCommandPaletteRowsTest {

    private LauncherToolRegistry registry;

    @Before
    public void setUp() {
        registry = LauncherToolRegistry.getInstance();
    }

    @Test
    public void promptableArgument_skipsTheTwoArgumentToolAndKeepsTheOneArgumentOne() {
        // Two required arguments means the projection cannot prompt for it, so the tool is not
        // offered as a generic row — the per-session rows supply the index themselves.
        assertNull(TerminalCommandPalette.promptableArgument(
            registry.getTool(LauncherToolRegistry.TOOL_SESSION_RENAME_AT_INDEX)));
        assertEquals("name", TerminalCommandPalette.promptableArgument(
            registry.getTool(LauncherToolRegistry.TOOL_SESSION_RENAME)));
    }

    /**
     * A submenu of three storage values could not say which type is in use, so the Keyboard
     * section supplies a row per type instead and the projection leaves the tool alone.
     */
    @Test
    public void promptableArgument_leavesTheKeyboardTypeToItsOwnRows() {
        assertNull(TerminalCommandPalette.promptableArgument(
            registry.getTool(LauncherToolRegistry.TOOL_KEYBOARD_SET_FORM)));
    }

    @Test
    public void keyboardFormEntry_carriesItsOwnTypeAndAsksForNothing() {
        CommandPaletteFilter.Entry entry = TerminalCommandPalette.keyboardFormEntry(
            KeyboardForm.FLOATING, "Floating keyboard", "Current type");

        assertEquals(LauncherToolRegistry.TOOL_KEYBOARD_SET_FORM, entry.toolName);
        assertEquals(LauncherToolRegistry.CATEGORY_KEYBOARD, entry.category);
        assertNotNull(entry.arguments);
        assertEquals("floating", entry.arguments.optString("form"));
        assertNull("the row supplies the value, so it must not prompt", entry.argumentName);
        assertTrue(entry.enabled);
    }

    /** Every type gets a row, and each names itself the way the Layout page's pill does. */
    @Test
    public void everyKeyboardTypeHasALabelAndAValueTheDispatcherAccepts() {
        for (KeyboardForm form : KeyboardForm.values()) {
            assertTrue(form.name(), TerminalCommandPalette.formLabel(form) != 0);
            CommandPaletteFilter.Entry entry =
                TerminalCommandPalette.keyboardFormEntry(form, form.name(), "");
            assertEquals(form, KeyboardForm.parse(
                entry.arguments.optString("form"), KeyboardForm.DOCKED));
        }
    }

    @Test
    public void renameSessionEntry_promptsForANameWhileCarryingItsIndex() {
        CommandPaletteFilter.Entry entry =
            TerminalCommandPalette.renameSessionEntry(2, "Rename Session 3", "up to 8 characters");

        assertEquals(LauncherToolRegistry.TOOL_SESSION_RENAME_AT_INDEX, entry.toolName);
        assertEquals(TerminalCommandPalette.CATEGORY_SESSIONS, entry.category);
        assertTrue(entry.isArgumentPrompt());
        assertEquals("name", entry.argumentName);
        assertNotNull(entry.arguments);
        assertEquals(2, entry.arguments.optInt("index", -1));
        assertTrue(entry.enabled);
    }

    @Test
    public void withArgument_mergesTheTypedNameIntoTheRowsOwnArguments() {
        // The single riskiest line in this change: if the merge replaced the row's arguments
        // instead of adding to them, every rename would silently target session 0.
        CommandPaletteFilter.Entry entry = TerminalCommandPaletteController.withArgument(
            TerminalCommandPalette.renameSessionEntry(2, "Rename Session 3", "hint"), "work");

        assertNotNull(entry.arguments);
        assertEquals(2, entry.arguments.optInt("index", -1));
        assertEquals("work", entry.arguments.optString("name"));
        // The merged row is ready to run, so it must not ask for the argument again.
        assertNull(entry.argumentName);
    }

    @Test
    public void aBoundContactShowsItsChordAndAnUnboundOneShowsNone() {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "map --label Ana ctrl+alt+shift+a contact.open 0r12-ANA contact_id=42\n",
            registry, true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);

        List<CommandPaletteContacts.Match> matches = Arrays.asList(
            new CommandPaletteContacts.Match(
                new CommandPaletteContacts.Contact("Ana", 42, "0r12-ANA"), "555 0100"),
            // A namesake is another contact: the chord stays with the key it was bound to.
            new CommandPaletteContacts.Match(
                new CommandPaletteContacts.Contact("Ana", 77, "0r12-OTHER"), "555 0199"));
        List<CommandPaletteFilter.Entry> rows = TerminalCommandPalette.buildContactEntries(
            RuntimeEnvironment.getApplication(), matches,
            TerminalCommandPalette.buildContactShortcuts());

        assertEquals("C-A-A", rows.get(0).shortcutLabel());
        assertEquals("", rows.get(1).shortcutLabel());
        assertTrue(TerminalCommandPalette.isBindable(rows.get(0)));
        assertEquals(Arrays.asList("0r12-ANA", "contact_id=42"),
            TerminalCommandPalette.contactBindingArguments(rows.get(0).arguments));
    }

    @Test
    public void isBindable_takesActionsThatRunAsTheyStandAndRefusesTheRest() {
        assertTrue(TerminalCommandPalette.isBindable(toolRow("pane.equalize", true, null, null)));
        assertFalse("a disabled action",
            TerminalCommandPalette.isBindable(toolRow("pane.equalize", false, null, null)));
        assertFalse("a submenu", TerminalCommandPalette.isBindable(toolRow("pane.layout", true,
            "layout", Arrays.asList("grid", "stack"))));
        assertFalse("an argument prompt",
            TerminalCommandPalette.isBindable(toolRow("session.rename", true, "name", null)));
        assertFalse("a row that supplies its own value", TerminalCommandPalette.isBindable(
            TerminalCommandPalette.renameSessionEntry(0, "Rename", "")));
        assertFalse("the allow row", TerminalCommandPalette.isBindable(
            TerminalCommandPalette.buildContactsAllowEntry(RuntimeEnvironment.getApplication())));
    }

    @After
    public void tearDown() {
        TerminalKeyBindingResolver.resetForTesting();
    }

    private static CommandPaletteFilter.Entry toolRow(String tool, boolean enabled,
                                                      String argumentName, List<String> choices) {
        return new CommandPaletteFilter.Entry(tool, tool, "", "pane",
            Collections.<String>emptyList(), enabled, enabled ? null : "Unavailable", false,
            LauncherToolRegistry.ToolRisk.LOW, null, argumentName, choices);
    }

    @Test
    public void withArgument_keepsAnEmptyNameRatherThanDroppingTheKey() {
        // An explicit empty name clears the label; the dispatcher errors only on an absent key.
        CommandPaletteFilter.Entry entry = TerminalCommandPaletteController.withArgument(
            TerminalCommandPalette.renameSessionEntry(0, "Rename Session 1", "hint"), "");

        assertNotNull(entry.arguments);
        assertTrue(entry.arguments.has("name"));
        assertEquals("", entry.arguments.optString("name"));
    }
}
