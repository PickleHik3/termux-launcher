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
        TerminalKeyBindingResolver.setLeader(Leader.of(choice, chord), true);
        return TerminalKeyBindingResolver.getInstance();
    }

    private static TerminalKeyBindingResolver install(String configText, String chord) {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(configText,
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
        return under("custom", chord);
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

    /** ADR 0042: "Terl takes a leader chord only when it is bound". */
    @Test
    public void anUnboundTerlKeyChordIsNotTerls() {
        TerminalKeyBindingResolver resolver = under("terl", null);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.NONE,
            resolver.advanceLeaderKey(key(KeyEvent.KEYCODE_F12, 0), SPLITS_ON).kind);
        assertFalse(TerminalKeyBindingResolver.swallowsUnboundLeaderChords());
    }

    @Test
    public void withTheTerlDesktopOffTheLeaderIsV1sCtrlAlt() {
        TerminalKeyBindingResolver.getInstance();
        TerminalKeyBindingResolver.setLeader(Leader.of("custom", "ctrl+b"), false);
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        assertEquals(Leader.CTRL_ALT, resolver.leader());
        assertEquals("pane.split_vertical", tool(resolver, KeyEvent.KEYCODE_V, CTRL_ALT));
        assertTrue("v1 swallows unbound Ctrl+Alt", TerminalKeyBindingResolver.swallowsUnboundLeaderChords());
        TerminalKeyBindingResolver.setLeader(Leader.of("custom", "ctrl+b"), true);
        assertEquals("ctrl+b", TerminalKeyBindingResolver.getInstance().leader().chord.spec());
    }

    @Test
    public void underAShiftedLeaderTheUnshiftedBindingIsKept() {
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+shift");
        // leader+Left (window.previous? whatever the table holds) and leader+Shift+Left meet.
        String unshifted = TerminalKeyBindingResolver.getInstance().leader()
            .physicalSequence("ctrl+alt+left");
        assertEquals("ctrl+shift+left", unshifted);
        TerminalKeyBindingResolver terl = under("terl", null);
        String expected = terl.getBindings().get("ctrl+alt+left").get(0).toolName;
        resolver = under("custom", "ctrl+shift");
        assertEquals(expected, resolver.getBindings().get("ctrl+shift+left").get(0).toolName);
        assertTrue(resolver.getConflicts().containsKey("ctrl+shift+left"));
    }

    @Test
    public void aModesLeaderBindingsFollowTheLeaderAndTheTerlKey() {
        TerminalKeyBindingResolver resolver = install(
            "map --new-mode panes ctrl+alt+g\nmap --mode panes ctrl+alt+z send-text zz\n",
            "ctrl+b");
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step enter = resolver.advance(key(KeyEvent.KEYCODE_G, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, enter.kind);
        assertTrue(resolver.pushMode(enter.match.actions.get(0).value));

        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step typed = resolver.advance(key(KeyEvent.KEYCODE_Z, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, typed.kind);
        assertEquals("zz", typed.match.actions.get(0).value);

        TerminalKeyBindingResolver.Step terl =
            resolver.advanceLeaderKey(key(KeyEvent.KEYCODE_Z, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, terl.kind);
        assertEquals("panes", terl.match.mode);
    }

    @Test
    public void theLeaderAndBindingsAreClaimedBeforeAChordsOwnMeaning() {
        TerminalKeyBindingResolver resolver = under("custom", "ctrl+shift+c");
        assertTrue("a prefix leader", resolver.claims(
            key(KeyEvent.KEYCODE_C, CTRL_SHIFT), SPLITS_ON));
        assertFalse(resolver.claims(key(KeyEvent.KEYCODE_F12, CTRL_SHIFT), SPLITS_ON));
        resolver = under("terl", null);
        assertTrue("a binding", resolver.claims(key(KeyEvent.KEYCODE_V, CTRL_ALT), SPLITS_ON));
    }

    @Test
    public void v1sCustomisedBindingsAreRecognised() {
        assertFalse(TerminalKeyBindingResolver.getInstance().hasCustomisedBindings());
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "map ctrl+alt+z send-text zz\n", LauncherToolRegistry.getInstance(), true);
        TerminalKeyBindingResolver.installConfigForTesting(config);
        assertTrue(TerminalKeyBindingResolver.getInstance().hasCustomisedBindings());
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
