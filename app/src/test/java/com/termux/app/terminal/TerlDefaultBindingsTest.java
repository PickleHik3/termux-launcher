package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.KeyEvent;

import com.termux.app.leader.Leader;
import com.termux.launcherctl.TerlDefaultBindings;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Terl's default bindings (#439, ADR 0042): the table under each leader, and v1's with the switch off. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TerlDefaultBindingsTest {

    private static final int SHIFT = KeyEvent.META_SHIFT_ON;
    private static final LauncherToolRegistry.ActionContext CONTEXT =
        new LauncherToolRegistry.ActionContext() {
            @Override public boolean isSplitPanesEnabled() { return true; }
            @Override public boolean hasCurrentSession() { return true; }
            @Override public boolean hasSelectedText() { return false; }
        };

    /** Key code, Shift held, the tool the Terl table binds. */
    private static final Object[][] EXPECTED = {
        {KeyEvent.KEYCODE_ENTER, false, "window.new"},
        {KeyEvent.KEYCODE_Q, false, "window.close"},
        {KeyEvent.KEYCODE_DPAD_LEFT, false, "window.previous"},
        {KeyEvent.KEYCODE_DPAD_RIGHT, false, "window.next"},
        {KeyEvent.KEYCODE_DPAD_UP, false, "session.previous"},
        {KeyEvent.KEYCODE_DPAD_DOWN, false, "session.next"},
        {KeyEvent.KEYCODE_DPAD_LEFT, true, "window.move_left"},
        {KeyEvent.KEYCODE_DPAD_RIGHT, true, "window.move_right"},
        {KeyEvent.KEYCODE_DPAD_UP, true, "window.move_up"},
        {KeyEvent.KEYCODE_DPAD_DOWN, true, "window.move_down"},
        {KeyEvent.KEYCODE_1, false, "workspace.go.1"},
        {KeyEvent.KEYCODE_9, false, "workspace.go.9"},
        {KeyEvent.KEYCODE_1, true, "window.send.1"},
        {KeyEvent.KEYCODE_9, true, "window.send.9"},
        {KeyEvent.KEYCODE_V, false, "window.float"},
        {KeyEvent.KEYCODE_F, false, "window.fullscreen"},
        {KeyEvent.KEYCODE_SPACE, false, "bar.search"},
        {KeyEvent.KEYCODE_TAB, false, "session.browser"},
        {KeyEvent.KEYCODE_W, false, "window.tab"},
        {KeyEvent.KEYCODE_COMMA, false, "edit_mode.open"},
    };

    @Before public void setUp() { TerminalKeyBindingResolver.resetForTesting(); }
    @After public void tearDown() { TerminalKeyBindingResolver.resetForTesting(); }

    private static KeyEvent key(int keyCode, int meta) {
        return new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta);
    }

    private static TerminalKeyBindingResolver under(String choice, String chord, boolean terl) {
        TerminalKeyBindingResolver.getInstance();
        TerminalKeyBindingResolver.setLeader(Leader.of(choice, chord), terl);
        return TerminalKeyBindingResolver.getInstance();
    }

    private static String tool(TerminalKeyBindingResolver resolver, int keyCode, int meta) {
        TerminalKeyBindingResolver.Match match = resolver.resolve(key(keyCode, meta), CONTEXT);
        return match == null ? null : match.toolName;
    }

    @Test
    public void everyTableToolIsARegistryToolTheSettingsPageLists() {
        for (String tool : TerlDefaultBindings.tools()) {
            LauncherToolRegistry.ToolMetadata metadata = LauncherToolRegistry.getInstance().getTool(tool);
            assertNotNull(tool, metadata);
            assertTrue(tool + " is listed by Settings and the palette", metadata.hasUiMetadata());
        }
    }

    @Test
    public void ctrlAltTypesTheTable() {
        TerminalKeyBindingResolver resolver = under("ctrl+alt", null, true);
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        for (Object[] row : EXPECTED)
            assertEquals(row[2].toString(), row[2],
                tool(resolver, (Integer) row[0], meta | ((Boolean) row[1] ? SHIFT : 0)));
        assertTrue(resolver.getConflicts().toString(), resolver.getConflicts().isEmpty());
    }

    @Test
    public void theTerlKeyAsksForTheTable() {
        TerminalKeyBindingResolver resolver = under("terl", null, true);
        for (Object[] row : EXPECTED) {
            TerminalKeyBindingResolver.Step step = resolver.advanceLeaderKey(
                key((Integer) row[0], (Boolean) row[1] ? SHIFT : 0), CONTEXT);
            assertEquals(row[2].toString(), TerminalKeyBindingResolver.Step.Kind.MATCH, step.kind);
            assertEquals(row[2], step.match.toolName);
        }
    }

    @Test
    public void aCustomLeaderTypesTheTableExceptWhatAndroidTakes() {
        // Ctrl+Shift: leader+Space is Ctrl+Shift+Space, which Android takes; leader+Shift+N is
        // the same keys as leader+N, so the sends clash with the goes and the goes keep them.
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+shift", true);
        int meta = KeyEvent.META_CTRL_ON | SHIFT;
        assertEquals("window.new", tool(resolver, KeyEvent.KEYCODE_ENTER, meta));
        assertEquals("window.fullscreen", tool(resolver, KeyEvent.KEYCODE_F, meta));
        assertEquals("session.browser", tool(resolver, KeyEvent.KEYCODE_TAB, meta));
        assertEquals("workspace.go.3", tool(resolver, KeyEvent.KEYCODE_3, meta));
        assertNull(tool(resolver, KeyEvent.KEYCODE_SPACE, meta));
        assertTrue(resolver.getConflicts().containsKey("ctrl+shift+space"));
    }

    @Test
    public void ctrlAltShiftIsNotAltTabSoOverviewKeepsItsKey() {
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+alt+shift", true);
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | SHIFT;
        assertEquals("session.browser", tool(resolver, KeyEvent.KEYCODE_TAB, meta));
        assertEquals("window.tab", tool(resolver, KeyEvent.KEYCODE_W, meta));
    }

    @Test
    public void aClashIsShownInSettingsAndLeftOutOfTheTable() {
        // Custom Ctrl+Shift: leader+Space is Ctrl+Shift+Space, which Android takes.
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+shift", true);
        TerminalKeyBindingResolver.DroppedDefault dropped = resolver.droppedDefault("bar.search");
        assertNotNull(dropped);
        assertEquals("ctrl+alt+space", dropped.stored);
        assertFalse(dropped.reason.isEmpty());
        assertFalse("the hint card lists only what is bound",
            resolver.holdersByStoredStroke().containsKey("ctrl+alt+space"));
        assertNull(resolver.droppedDefault("window.new"));
    }

    @Test
    public void aPrefixCustomLeaderTypesTheTable() {
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+b", true);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON), CONTEXT).kind);
        TerminalKeyBindingResolver.Step step = resolver.advance(key(KeyEvent.KEYCODE_F, 0), CONTEXT);
        assertEquals("window.fullscreen", step.match.toolName);
    }

    @Test
    public void v1sTableIsUnchangedWithTheSwitchOff() {
        TerminalKeyBindingResolver resolver = under("terl", null, false);
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        assertEquals("pane.split_vertical", tool(resolver, KeyEvent.KEYCODE_V, meta));
        assertEquals("pane.toggle_float", tool(resolver, KeyEvent.KEYCODE_F, meta));
        assertEquals("window.new", tool(resolver, KeyEvent.KEYCODE_C, meta));
        assertEquals("window.close", tool(resolver, KeyEvent.KEYCODE_X, meta));
        assertNull(tool(resolver, KeyEvent.KEYCODE_Q, meta));
        assertNull(tool(resolver, KeyEvent.KEYCODE_TAB, meta));
    }

    @Test
    public void switchingTheDesktopOnRebuildsEvenWithCtrlAltStored() {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        assertEquals("pane.split_vertical", tool(under("ctrl+alt", null, false), KeyEvent.KEYCODE_V, meta));
        assertEquals("window.float", tool(under("ctrl+alt", null, true), KeyEvent.KEYCODE_V, meta));
        assertEquals("pane.split_vertical", tool(under("ctrl+alt", null, false), KeyEvent.KEYCODE_V, meta));
    }

    @Test
    public void theTerlTableReplacesV1sCtrlAltDefaultsWholly() {
        TerminalKeyBindingResolver resolver = under("ctrl+alt", null, true);
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        assertNull("v1's scratchpad is not bound under Terl", tool(resolver, KeyEvent.KEYCODE_GRAVE, meta));
        assertNull(tool(resolver, KeyEvent.KEYCODE_H, meta));
        assertNull(tool(resolver, KeyEvent.KEYCODE_C, meta));
        for (java.util.Map.Entry<String, TerminalKeyBindingResolver.Hint> held
                : resolver.holdersByStoredStroke().entrySet())
            if (held.getKey().startsWith("ctrl+alt+"))
                assertTrue(held.getValue().toolName,
                    TerlDefaultBindings.tools().contains(held.getValue().toolName));
        assertEquals("clipboard.paste", tool(resolver, KeyEvent.KEYCODE_V,
            KeyEvent.META_CTRL_ON | SHIFT));
    }

    @Test
    public void aUsersFileStillAddsToTheTerlTable() {
        under("ctrl+alt", null, true);
        TerminalKeyBindingResolver.installConfigForTesting(TerminalBindingConfig.parse(
            "map ctrl+alt+h pane.split_vertical\n", LauncherToolRegistry.getInstance(), true));
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        assertEquals("pane.split_vertical", tool(resolver, KeyEvent.KEYCODE_H, meta));
        assertEquals("window.close", tool(resolver, KeyEvent.KEYCODE_Q, meta));
    }

    @Test
    public void theTerlToolsAreListedOnlyWhileTheSwitchIsOn() {
        under("ctrl+alt", null, false);
        assertFalse(LauncherToolRegistry.getInstance().getUiTools().stream()
            .anyMatch(t -> t.name.equals("workspace.go.1")));
        under("ctrl+alt", null, true);
        assertTrue(LauncherToolRegistry.getInstance().getUiTools().stream()
            .anyMatch(t -> t.name.equals("workspace.go.1")));
    }

    @Test
    public void theTableFollowsTheRunningDesktopNotTheSwitchAlone() {
        TerminalKeyBindingResolver.getInstance();
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        TerminalKeyBindingResolver.setLeader(Leader.CTRL_ALT, true, false);
        assertEquals("pane.split_vertical", tool(TerminalKeyBindingResolver.getInstance(), KeyEvent.KEYCODE_V, meta));
        TerminalKeyBindingResolver.setLeader(Leader.CTRL_ALT, true, true);
        assertEquals("window.float", tool(TerminalKeyBindingResolver.getInstance(), KeyEvent.KEYCODE_V, meta));
    }

    @Test
    public void rebindingCloseToAnotherKeyLeavesOnlyThatKeyAndNeverUnmapsV1sDefault() {
        under("ctrl+alt", null, true);
        java.util.List<String> lines = new java.util.ArrayList<>();
        TerminalBindingConfigWriter.Edit moved = TerminalBindingConfigWriter.move(lines,
            "ctrl+alt+k", "window.close", java.util.Collections.singletonList("ctrl+alt+q"));
        assertTrue(moved.lines.toString(), moved.lines.contains("unmap ctrl+alt+q"));
        TerminalKeyBindingResolver.installConfigForTesting(TerminalBindingConfig.parse(
            String.join("\n", moved.lines), LauncherToolRegistry.getInstance(), true));
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        assertEquals("window.close", tool(resolver, KeyEvent.KEYCODE_K, meta));
        assertNull(tool(resolver, KeyEvent.KEYCODE_Q, meta));
        // Moving Float off leader+V unmaps the Terl default on that key (the table in force).
        TerminalBindingConfigWriter.Edit float_ = TerminalBindingConfigWriter.move(new java.util.ArrayList<>(),
            "ctrl+alt+j", "window.float", java.util.Collections.singletonList("ctrl+alt+v"));
        assertTrue(float_.lines.toString(), float_.lines.contains("unmap ctrl+alt+v"));
    }

    @Test
    public void reservationIsOnlyForCustomLeaders() {
        assertNull(TerlDefaultBindings.reservation(Leader.TERL_KEY, "ctrl+alt+tab"));
        assertNull(TerlDefaultBindings.reservation(Leader.CTRL_ALT, "ctrl+alt+tab"));
        assertNull(TerlDefaultBindings.reservation(Leader.CTRL_ALT, "ctrl+alt+space"));
    }
}
