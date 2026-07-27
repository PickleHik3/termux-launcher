package com.termux.app.terminal;

import android.view.KeyEvent;

import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Parity tests for the registry-driven binding table.
 *
 * <p>Every expectation below is the behavior the two hand-written chains had before
 * they were replaced: the multiplexer {@code switch} (split panes on) and the legacy
 * {@code Ctrl+Alt}+character sequence (both modes). The point of these tests is that
 * moving the table into the registry did not change what any stroke does.
 */
@RunWith(RobolectricTestRunner.class)
public class TerminalKeyBindingResolverTest {

    private static final int CTRL_ALT = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
    private static final int CTRL_ALT_SHIFT = CTRL_ALT | KeyEvent.META_SHIFT_ON;

    private TerminalKeyBindingResolver resolver;

    /** Split panes on, a session present — the normal case. */
    private static final LauncherToolRegistry.ActionContext SPLITS_ON = context(true, true);
    /** Compatibility mode: split panes off. */
    private static final LauncherToolRegistry.ActionContext SPLITS_OFF = context(false, true);

    private static LauncherToolRegistry.ActionContext context(boolean splits, boolean session) {
        return new LauncherToolRegistry.ActionContext() {
            @Override public boolean isSplitPanesEnabled() { return splits; }
            @Override public boolean hasCurrentSession() { return session; }
            @Override public boolean hasSelectedText() { return false; }
        };
    }

    @Before
    public void setUp() {
        // The registry is immutable once built, so only the resolver needs resetting.
        TerminalKeyBindingResolver.resetForTesting();
        resolver = TerminalKeyBindingResolver.getInstance();
    }

    private static KeyEvent key(int keyCode, int meta) {
        return new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta);
    }

    private String tool(int keyCode, int meta, LauncherToolRegistry.ActionContext ctx) {
        TerminalKeyBindingResolver.Match match = resolver.resolve(key(keyCode, meta), ctx);
        return match == null ? null : match.toolName;
    }

    // ---------------------------------------------------------------- splits on

    @Test
    public void multiplexerBinds_matchPreviousSwitch() {
        assertEquals("pane.split_vertical", tool(KeyEvent.KEYCODE_V, CTRL_ALT, SPLITS_ON));
        assertEquals("pane.split_horizontal", tool(KeyEvent.KEYCODE_H, CTRL_ALT, SPLITS_ON));
        assertEquals("window.new", tool(KeyEvent.KEYCODE_C, CTRL_ALT, SPLITS_ON));
        assertEquals("session.new", tool(KeyEvent.KEYCODE_C, CTRL_ALT_SHIFT, SPLITS_ON));
        assertEquals("window.close", tool(KeyEvent.KEYCODE_X, CTRL_ALT, SPLITS_ON));
        assertEquals("session.close_current", tool(KeyEvent.KEYCODE_X, CTRL_ALT_SHIFT, SPLITS_ON));
        assertEquals("window.previous", tool(KeyEvent.KEYCODE_LEFT_BRACKET, CTRL_ALT, SPLITS_ON));
        assertEquals("window.next", tool(KeyEvent.KEYCODE_RIGHT_BRACKET, CTRL_ALT, SPLITS_ON));
        assertEquals("window.rename_prompt", tool(KeyEvent.KEYCODE_R, CTRL_ALT, SPLITS_ON));
    }

    @Test
    public void arrowsBelongToTheMultiplexerWhenSplitsAreOn() {
        int[] arrows = {KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN};
        for (int arrow : arrows) {
            assertEquals("pane.focus_direction", tool(arrow, CTRL_ALT, SPLITS_ON));
            assertEquals("pane.resize", tool(arrow, CTRL_ALT_SHIFT, SPLITS_ON));
        }
    }

    // ---------------------------------------------------------------- splits off

    @Test
    public void arrowsFallBackToSessionAndDrawerWhenSplitsAreOff() {
        // The legacy chain: Down/Up switched sessions, Right/Left worked the drawer.
        assertEquals("session.next", tool(KeyEvent.KEYCODE_DPAD_DOWN, CTRL_ALT, SPLITS_OFF));
        assertEquals("session.previous", tool(KeyEvent.KEYCODE_DPAD_UP, CTRL_ALT, SPLITS_OFF));
        assertEquals("app.open_drawer", tool(KeyEvent.KEYCODE_DPAD_RIGHT, CTRL_ALT, SPLITS_OFF));
        assertEquals("app.close_drawer", tool(KeyEvent.KEYCODE_DPAD_LEFT, CTRL_ALT, SPLITS_OFF));
    }

    @Test
    public void conditionalStrokes_meanDifferentThingsPerMode() {
        // Ctrl+Alt+V: split with panes on, paste with them off.
        assertEquals("pane.split_vertical", tool(KeyEvent.KEYCODE_V, CTRL_ALT, SPLITS_ON));
        assertEquals("clipboard.paste", tool(KeyEvent.KEYCODE_V, CTRL_ALT, SPLITS_OFF));
        // Ctrl+Alt+C: new window with panes on, new session with them off.
        assertEquals("window.new", tool(KeyEvent.KEYCODE_C, CTRL_ALT, SPLITS_ON));
        assertEquals("session.new", tool(KeyEvent.KEYCODE_C, CTRL_ALT, SPLITS_OFF));
        // Ctrl+Alt+R renamed the window session with panes on, the shell with them off.
        assertEquals("window.rename_prompt", tool(KeyEvent.KEYCODE_R, CTRL_ALT, SPLITS_ON));
        assertEquals("session.rename_prompt", tool(KeyEvent.KEYCODE_R, CTRL_ALT, SPLITS_OFF));
    }

    @Test
    public void splitBindsAreInertInCompatibilityMode() {
        // Ctrl+Alt+H had no legacy meaning, so it must resolve to nothing.
        assertNull(tool(KeyEvent.KEYCODE_H, CTRL_ALT, SPLITS_OFF));
        assertNull(tool(KeyEvent.KEYCODE_LEFT_BRACKET, CTRL_ALT, SPLITS_OFF));
        assertNull(tool(KeyEvent.KEYCODE_RIGHT_BRACKET, CTRL_ALT, SPLITS_OFF));
    }

    // ---------------------------------------------------------------- mode-free

    @Test
    public void legacyCharacterBinds_workInBothModes() {
        for (LauncherToolRegistry.ActionContext ctx : new LauncherToolRegistry.ActionContext[]{SPLITS_ON, SPLITS_OFF}) {
            assertEquals("session.next", tool(KeyEvent.KEYCODE_N, CTRL_ALT, ctx));
            assertEquals("session.previous", tool(KeyEvent.KEYCODE_P, CTRL_ALT, ctx));
            assertEquals("terminal.toggle_soft_keyboard", tool(KeyEvent.KEYCODE_K, CTRL_ALT, ctx));
            assertEquals("terminal.action_sheet", tool(KeyEvent.KEYCODE_M, CTRL_ALT, ctx));
            assertEquals("terminal.select_url", tool(KeyEvent.KEYCODE_U, CTRL_ALT, ctx));
            assertEquals("terminal.font_size_decrease", tool(KeyEvent.KEYCODE_MINUS, CTRL_ALT, ctx));
            assertEquals("terminal.font_size_increase", tool(KeyEvent.KEYCODE_PLUS, CTRL_ALT, ctx));
            assertEquals("session.new", tool(KeyEvent.KEYCODE_C, CTRL_ALT_SHIFT, ctx));
        }
    }

    @Test
    public void shiftedEqualsAlsoIncreasesFontSize() {
        // The legacy chain accepted the shifted '+' because layouts differ.
        assertEquals("terminal.font_size_increase",
            tool(KeyEvent.KEYCODE_EQUALS, CTRL_ALT_SHIFT, SPLITS_ON));
    }

    @Test
    public void digitsSwitchSessionsByIndex() {
        for (int digit = 1; digit <= 9; digit++) {
            int keyCode = KeyEvent.KEYCODE_0 + digit;
            TerminalKeyBindingResolver.Match match = resolver.resolve(key(keyCode, CTRL_ALT), SPLITS_ON);
            assertNotNull("digit " + digit, match);
            assertEquals("session.activate_by_index", match.toolName);
            assertEquals("digit " + digit + " is a one-based label for a zero-based index",
                digit - 1, match.arguments.optInt("index", -1));
        }
    }

    @Test
    public void commandPaletteHasABindingNow() {
        // Ctrl+Alt+Shift+P is unambiguous once shift is part of the stroke: the
        // legacy previous-session bind is Ctrl+Alt+P without shift.
        assertEquals("app.command_palette", tool(KeyEvent.KEYCODE_P, CTRL_ALT_SHIFT, SPLITS_ON));
        assertEquals("app.command_palette", tool(KeyEvent.KEYCODE_P, CTRL_ALT_SHIFT, SPLITS_OFF));
        assertEquals("session.previous", tool(KeyEvent.KEYCODE_P, CTRL_ALT, SPLITS_ON));
    }

    // ---------------------------------------------------------------- arguments

    @Test
    public void arrows_carryDirectionArgument() throws Exception {
        assertEquals("left", resolver.resolve(key(KeyEvent.KEYCODE_DPAD_LEFT, CTRL_ALT), SPLITS_ON)
            .arguments.getString("direction"));
        assertEquals("down", resolver.resolve(key(KeyEvent.KEYCODE_DPAD_DOWN, CTRL_ALT_SHIFT), SPLITS_ON)
            .arguments.getString("direction"));
    }

    @Test
    public void nonArgumentBinds_carryNoArguments() {
        assertEquals(0, resolver.resolve(key(KeyEvent.KEYCODE_V, CTRL_ALT), SPLITS_ON).arguments.length());
        assertEquals(0, resolver.resolve(key(KeyEvent.KEYCODE_K, CTRL_ALT), SPLITS_ON).arguments.length());
    }

    // ---------------------------------------------------------------- guards

    @Test
    public void requiresBothCtrlAndAlt() {
        assertNull(tool(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON, SPLITS_ON));
        assertNull(tool(KeyEvent.KEYCODE_V, KeyEvent.META_ALT_ON, SPLITS_ON));
        assertNull(tool(KeyEvent.KEYCODE_V, 0, SPLITS_ON));
        assertNull(tool(KeyEvent.KEYCODE_V, KeyEvent.META_SHIFT_ON, SPLITS_ON));
    }

    @Test
    public void unmappableAndUnboundStrokesResolveToNothing() {
        assertNull(tool(KeyEvent.KEYCODE_F1, CTRL_ALT, SPLITS_ON));
        assertNull(tool(KeyEvent.KEYCODE_CAMERA, CTRL_ALT, SPLITS_ON));
        assertNull(tool(KeyEvent.KEYCODE_0, CTRL_ALT, SPLITS_ON)); // only 1-9 are bound
    }

    @Test
    public void bindingTable_hasNoOverlappingConflicts() {
        assertTrue("unexpected binding conflicts: " + resolver.getConflicts(),
            resolver.getConflicts().isEmpty());
    }

    @Test
    public void everyRegistryBindingIsReachable() {
        Map<String, List<TerminalKeyBindingResolver.Claim>> table = resolver.getBindings();
        for (LauncherToolRegistry.ToolMetadata t : LauncherToolRegistry.getInstance().getUiTools()) {
            for (LauncherToolRegistry.Binding binding : t.defaultBindings) {
                String stroke = TerminalKeyBindingResolver.normalizeStrokeSpec(binding.stroke);
                List<TerminalKeyBindingResolver.Claim> claims = table.get(stroke);
                assertNotNull("stroke " + stroke + " missing from the table", claims);
                boolean found = false;
                for (TerminalKeyBindingResolver.Claim claim : claims) {
                    if (claim.toolName.equals(t.name) && claim.condition == binding.condition) {
                        found = true;
                        break;
                    }
                }
                assertTrue(t.name + " lost its " + stroke + " claim", found);
            }
        }
    }

    @Test
    public void conditionsAreEvaluatedPerResolveNotCached() {
        assertEquals("pane.split_vertical", tool(KeyEvent.KEYCODE_V, CTRL_ALT, SPLITS_ON));
        assertEquals("clipboard.paste", tool(KeyEvent.KEYCODE_V, CTRL_ALT, SPLITS_OFF));
        assertEquals("pane.split_vertical", tool(KeyEvent.KEYCODE_V, CTRL_ALT, SPLITS_ON));
    }

    @Test
    public void matchReportsStrokeAndCondition() {
        TerminalKeyBindingResolver.Match match = resolver.resolve(key(KeyEvent.KEYCODE_V, CTRL_ALT), SPLITS_OFF);
        assertNotNull(match);
        assertEquals("ctrl+alt+v", match.stroke);
        assertEquals("clipboard.paste", match.toolName);
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_OFF, match.condition);
    }

    // ---------------------------------------------------------------- helpers

    @Test
    public void strokeSpecNormalization_isCaseAndOrderInsensitive() {
        assertEquals("ctrl+alt+v", TerminalKeyBindingResolver.normalizeStrokeSpec("Ctrl+Alt+V"));
        assertEquals("ctrl+alt+v", TerminalKeyBindingResolver.normalizeStrokeSpec("alt+ctrl+v"));
        assertEquals("ctrl+alt+shift+left", TerminalKeyBindingResolver.normalizeStrokeSpec("SHIFT+ALT+CTRL+left"));
        assertEquals("ctrl+alt+]", TerminalKeyBindingResolver.normalizeStrokeSpec("Control+Alt+]"));
    }

    @Test
    public void keyToken_mapsLettersByPhysicalPosition() {
        assertEquals("a", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_A));
        assertEquals("z", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_Z));
        assertEquals("[", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_LEFT_BRACKET));
        assertEquals("left", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals("7", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_7));
        // Spelled out so '+' never collides with the stroke separator.
        assertEquals("plus", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_PLUS));
        assertEquals("minus", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_MINUS));
        assertEquals("equals", TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_EQUALS));
        assertNull(TerminalKeyBindingResolver.keyToken(KeyEvent.KEYCODE_CAMERA));
    }

    @Test
    public void strokeFor_buildsModifiersInCanonicalOrder() {
        assertEquals("ctrl+alt+v", TerminalKeyBindingResolver.strokeFor(key(KeyEvent.KEYCODE_V, CTRL_ALT)));
        assertEquals("ctrl+alt+shift+v",
            TerminalKeyBindingResolver.strokeFor(key(KeyEvent.KEYCODE_V, CTRL_ALT_SHIFT)));
        assertNull(TerminalKeyBindingResolver.strokeFor(key(KeyEvent.KEYCODE_CAMERA, CTRL_ALT)));
    }

    @Test
    public void bindingConditionOverlapRules() {
        LauncherToolRegistry.BindingCondition always = LauncherToolRegistry.BindingCondition.ALWAYS;
        LauncherToolRegistry.BindingCondition on = LauncherToolRegistry.BindingCondition.SPLITS_ON;
        LauncherToolRegistry.BindingCondition off = LauncherToolRegistry.BindingCondition.SPLITS_OFF;
        assertTrue(always.overlaps(on));
        assertTrue(on.overlaps(always));
        assertTrue(on.overlaps(on));
        org.junit.Assert.assertFalse(on.overlaps(off));
        org.junit.Assert.assertFalse(off.overlaps(on));
    }
}
