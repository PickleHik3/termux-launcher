package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.KeyEvent;

import com.termux.app.leader.Leader;
import com.termux.app.terminal.keybind.LeaderSetting;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

/**
 * Under Terl every shell is a window (ADR 0037): v1's pane splits do not resolve, whether they come
 * from v1's defaults or from a bindings file written before the switch, and leader+Enter opens a
 * window. With the switch off v1 is unchanged.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TerlRetiresPaneSplitsTest {

    private static final int CTRL_ALT = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
    private static final LauncherToolRegistry.ActionContext SPLITS_ON =
        new LauncherToolRegistry.ActionContext() {
            @Override public boolean isSplitPanesEnabled() { return true; }
            @Override public boolean hasCurrentSession() { return true; }
            @Override public boolean hasSelectedText() { return false; }
        };

    /** v1's split bindings as a file written before the switch holds them. */
    private static final String V1_FILE =
        "map ctrl+alt+enter pane.split\n"
            + "map ctrl+alt+v pane.split_vertical\n"
            + "map ctrl+alt+h pane.split_horizontal\n"
            + "map ctrl+alt+g app.launch com.example\n";

    @Before public void setUp() { TerminalKeyBindingResolver.resetForTesting(); }
    @After public void tearDown() { TerminalKeyBindingResolver.resetForTesting(); }

    private static KeyEvent key(int keyCode, int meta) {
        return new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta);
    }

    private static TerminalKeyBindingResolver install(String file, boolean terl) {
        TerminalKeyBindingResolver.getInstance();
        if (file != null) {
            TerminalBindingConfig.Result config = TerminalBindingConfig.parse(file,
                LauncherToolRegistry.getInstance(), true);
            assertTrue(config.errors.toString(), config.errors.isEmpty());
            TerminalKeyBindingResolver.installConfigForTesting(config);
        }
        TerminalKeyBindingResolver.setLeader(Leader.TERL_KEY, terl);
        return TerminalKeyBindingResolver.getInstance();
    }

    private static String hardware(TerminalKeyBindingResolver resolver, int keyCode) {
        TerminalKeyBindingResolver.Match match = resolver.resolve(key(keyCode, CTRL_ALT), SPLITS_ON);
        resolver.cancelPendingSequence();
        return match == null ? null : match.toolName;
    }

    private static String terlKey(TerminalKeyBindingResolver resolver, int keyCode) {
        TerminalKeyBindingResolver.Step step = resolver.advanceLeaderKey(key(keyCode, 0), SPLITS_ON);
        resolver.cancelPendingSequence();
        return step.kind == TerminalKeyBindingResolver.Step.Kind.MATCH ? step.match.toolName : null;
    }

    @Test
    public void aFreshInstallUnderTerlOpensAWindowOnLeaderEnter() {
        TerminalKeyBindingResolver resolver = install(null, true);
        assertEquals("window.new", hardware(resolver, KeyEvent.KEYCODE_ENTER));
        assertEquals("window.new", terlKey(resolver, KeyEvent.KEYCODE_ENTER));
        assertNull(hardware(resolver, KeyEvent.KEYCODE_H));
    }

    @Test
    public void aStoredV1FileDoesNotSplitUnderTerl() {
        TerminalKeyBindingResolver resolver = install(V1_FILE, true);
        assertEquals("window.new", hardware(resolver, KeyEvent.KEYCODE_ENTER));
        assertEquals("window.new", terlKey(resolver, KeyEvent.KEYCODE_ENTER));
        assertEquals("window.float", hardware(resolver, KeyEvent.KEYCODE_V));
        assertEquals("window.float", terlKey(resolver, KeyEvent.KEYCODE_V));
        assertNull(hardware(resolver, KeyEvent.KEYCODE_H));
        // The file's other lines are still the user's.
        assertEquals("app.launch", hardware(resolver, KeyEvent.KEYCODE_G));
    }

    @Test
    public void anUnmapBesideTheSplitLineDoesNotTakeLeaderEnterAway() {
        TerminalKeyBindingResolver resolver = install(
            "unmap ctrl+alt+enter\nmap ctrl+alt+enter pane.split\n", true);
        assertEquals("window.new", hardware(resolver, KeyEvent.KEYCODE_ENTER));
    }

    @Test
    public void theHintCardListsWhatFires() {
        TerminalKeyBindingResolver resolver = install(V1_FILE, true);
        Map<String, TerminalKeyBindingResolver.Hint> hints = resolver.hintsForPrefix(
            TerminalKeyBindingResolver.LEADER_HINT_PREFIX, SPLITS_ON);
        assertEquals("window.new", hints.get("enter").toolName);
        assertEquals("window.float", hints.get("v").toolName);
        for (TerminalKeyBindingResolver.Hint hint : hints.values())
            assertFalse(hint.toolName, LauncherToolRegistry.isRetiredUnderTerl(hint.toolName));
    }

    @Test
    public void withTheSwitchOffV1StillSplits() {
        TerminalKeyBindingResolver fresh = install(null, false);
        assertEquals("pane.split", hardware(fresh, KeyEvent.KEYCODE_ENTER));
        TerminalKeyBindingResolver.resetForTesting();
        TerminalKeyBindingResolver stored = install(V1_FILE, false);
        assertEquals("pane.split", hardware(stored, KeyEvent.KEYCODE_ENTER));
        assertEquals("pane.split_vertical", hardware(stored, KeyEvent.KEYCODE_V));
    }

    @Test
    public void thePaletteDoesNotOfferSplitsUnderTerl() {
        LauncherToolRegistry registry = LauncherToolRegistry.getInstance();
        boolean terl = false;
        boolean v1 = false;
        for (LauncherToolRegistry.ToolMetadata tool : registry.getUiTools(true))
            terl |= LauncherToolRegistry.isRetiredUnderTerl(tool.name);
        for (LauncherToolRegistry.ToolMetadata tool : registry.getUiTools(false))
            v1 |= LauncherToolRegistry.TOOL_PANE_SPLIT.equals(tool.name);
        assertFalse("no split under Terl", terl);
        assertTrue("v1 lists pane.split", v1);
    }

    @Test
    public void aLateDestroyOfTheOldActivityKeepsTheNewDesktopsTable() {
        Object old = new Object();
        Object fresh = new Object();
        LeaderSetting.desktopRunning(old, true);
        LeaderSetting.desktopRunning(fresh, true);
        // A finished activity is destroyed after the next one started its desktop.
        LeaderSetting.desktopRunning(old, false);
        assertTrue(LeaderSetting.isDesktopRunning());
        LeaderSetting.desktopRunning(fresh, false);
        assertFalse(LeaderSetting.isDesktopRunning());
    }
}
