package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.KeyEvent;

import com.termux.app.terminal.keybind.Leader;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Bindings are leader-relative (ADR 0042): the same table answers under the Terl key, Ctrl+Alt
 * and a Custom leader, typed as that leader is typed, and the Terl key asks for it directly.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TerminalKeyBindingLeaderTest {

    private static final int CTRL_ALT = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
    private static final int CTRL_SHIFT = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON;

    private static final LauncherToolRegistry.ActionContext SPLITS_ON =
        new LauncherToolRegistry.ActionContext() {
            @Override public boolean isSplitPanesEnabled() { return true; }
            @Override public boolean hasCurrentSession() { return true; }
            @Override public boolean hasSelectedText() { return false; }
        };

    @Before
    public void setUp() {
        TerminalKeyBindingResolver.resetForTesting();
    }

    @After
    public void tearDown() {
        TerminalKeyBindingResolver.resetForTesting();
    }

    private static KeyEvent key(int keyCode, int meta) {
        return new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta);
    }

    private static TerminalKeyBindingResolver under(String choice, String chord) {
        TerminalKeyBindingResolver.getInstance();
        TerminalKeyBindingResolver.setLeader(Leader.of(choice, chord));
        return TerminalKeyBindingResolver.getInstance();
    }

    private static String tool(TerminalKeyBindingResolver resolver, int keyCode, int meta) {
        TerminalKeyBindingResolver.Match match = resolver.resolve(key(keyCode, meta), SPLITS_ON);
        return match == null ? null : match.toolName;
    }

    @Test
    public void theTerlKeyLeavesHardwareOnCtrlAlt() {
        TerminalKeyBindingResolver resolver = under("terl", null);
        assertEquals("pane.split_vertical", tool(resolver, KeyEvent.KEYCODE_V, CTRL_ALT));
        assertTrue(resolver.isLeaderStroke(key(KeyEvent.KEYCODE_V, CTRL_ALT)));
        assertFalse(resolver.isLeaderStroke(key(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)));
    }

    @Test
    public void aHeldCustomLeaderTakesOverEveryBinding() {
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+shift");
        assertEquals("pane.split_horizontal", tool(resolver, KeyEvent.KEYCODE_H, CTRL_SHIFT));
        // Ctrl+Alt is no longer the leader, so it is left to the app in front.
        assertNull(tool(resolver, KeyEvent.KEYCODE_H, CTRL_ALT));
        // A chord the table already held keeps it; the leader's binding there is a recorded clash.
        assertEquals("clipboard.paste", tool(resolver, KeyEvent.KEYCODE_V, CTRL_SHIFT));
        assertTrue(resolver.getConflicts().containsKey("ctrl+shift+v"));
        assertTrue(resolver.isLeaderStroke(key(KeyEvent.KEYCODE_V, CTRL_SHIFT)));
        assertFalse(resolver.isLeaderStroke(key(KeyEvent.KEYCODE_V, CTRL_ALT)));
    }

    @Test
    public void aPrefixCustomLeaderIsPressedThenTheKey() {
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+b");
        assertTrue(resolver.isLeaderStroke(key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)));
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step step = resolver.advance(key(KeyEvent.KEYCODE_V, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, step.kind);
        assertEquals("pane.split_vertical", step.match.toolName);
        assertEquals("ctrl+b>v", step.match.stroke);
    }

    @Test
    public void changingTheLeaderKeepsEveryBinding() {
        TerminalKeyBindingResolver terl = TerminalKeyBindingResolver.getInstance();
        int count = terl.getBindings().size();
        TerminalKeyBindingResolver prefix = under("custom", "ctrl+b");
        assertEquals(terl.getConflicts(), prefix.getConflicts());
        assertEquals(count, prefix.getBindings().size());
        for (String stroke : terl.getBindings().keySet()) {
            String typed = prefix.leader().physicalSequence(stroke);
            assertEquals(stroke, terl.getBindings().get(stroke).get(0).toolName,
                prefix.getBindings().get(typed).get(0).toolName);
        }
        assertEquals(count, under("ctrl+alt", null).getBindings().size());
        assertEquals(count, under("terl", null).getBindings().size());
    }

    @Test
    public void theTerlKeyFiresTheLeaderBindingUnderAnyLeader() {
        for (String chord : new String[] {null, "ctrl+shift", "ctrl+b"}) {
            TerminalKeyBindingResolver resolver = under(chord == null ? "terl" : "custom", chord);
            TerminalKeyBindingResolver.Step step =
                resolver.advanceLeaderKey(key(KeyEvent.KEYCODE_V, 0), SPLITS_ON);
            assertEquals(String.valueOf(chord), TerminalKeyBindingResolver.Step.Kind.MATCH, step.kind);
            assertEquals("pane.split_vertical", step.match.toolName);
            TerminalKeyBindingResolver.Step shifted = resolver.advanceLeaderKey(
                key(KeyEvent.KEYCODE_X, KeyEvent.META_SHIFT_ON), SPLITS_ON);
            assertEquals("session.close_current", shifted.match.toolName);
        }
    }

    @Test
    public void anUnboundTerlKeyChordReachesNoApp() {
        TerminalKeyBindingResolver resolver = under("terl", null);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.CANCELLED,
            resolver.advanceLeaderKey(key(KeyEvent.KEYCODE_F12, 0), SPLITS_ON).kind);
    }

    @Test
    public void theTerlKeyContinuesASequenceUnderTheLeader() {
        TerminalKeyBindingResolver resolver = under("terl", null);
        TerminalKeyBindingResolver.Step first =
            resolver.advanceLeaderKey(key(KeyEvent.KEYCODE_SPACE, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING, first.kind);
        TerminalKeyBindingResolver.Step second = resolver.advance(key(KeyEvent.KEYCODE_P, 0), SPLITS_ON);
        assertEquals("app.command_palette", second.match.toolName);
    }

    @Test
    public void theHintCardListsTheLeadersBindingsUnderAnyLeader() {
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+b");
        TerminalKeyBindingResolver.Hint hint = resolver.hintsForPrefix(
            TerminalKeyBindingResolver.LEADER_HINT_PREFIX, SPLITS_ON).get("v");
        assertNotNull(hint);
        assertEquals("pane.split_vertical", hint.toolName);
        assertNotNull(resolver.hintsForPrefix(
            TerminalKeyBindingResolver.LEADER_HINT_PREFIX + "shift+", SPLITS_ON).get("x"));
        assertNotNull(under("custom", "ctrl+shift").hintsForPrefix("ctrl+shift+", SPLITS_ON).get("v"));
    }
}
