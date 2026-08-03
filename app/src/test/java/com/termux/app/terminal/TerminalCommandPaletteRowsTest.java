package com.termux.app.terminal;

import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
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
    public void withArgument_keepsAnEmptyNameRatherThanDroppingTheKey() {
        // An explicit empty name clears the label; the dispatcher errors only on an absent key.
        CommandPaletteFilter.Entry entry = TerminalCommandPaletteController.withArgument(
            TerminalCommandPalette.renameSessionEntry(0, "Rename Session 1", "hint"), "");

        assertNotNull(entry.arguments);
        assertTrue(entry.arguments.has("name"));
        assertEquals("", entry.arguments.optString("name"));
    }
}
