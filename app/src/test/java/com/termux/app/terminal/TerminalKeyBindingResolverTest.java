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
import static org.junit.Assert.assertFalse;
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
        // Ctrl+Alt+R names the window with panes on and the pane with them off, while the shifted
        // stroke names the session — three targets, never two names for one thing.
        assertEquals("window.rename_prompt", tool(KeyEvent.KEYCODE_R, CTRL_ALT, SPLITS_ON));
        assertEquals("pane.rename_prompt", tool(KeyEvent.KEYCODE_R, CTRL_ALT, SPLITS_OFF));
        assertEquals("session.rename_prompt", tool(KeyEvent.KEYCODE_R, CTRL_ALT_SHIFT, SPLITS_ON));
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
            assertEquals("terminal.hints", tool(KeyEvent.KEYCODE_U, CTRL_ALT, ctx));
            assertEquals("terminal.search_scrollback", tool(KeyEvent.KEYCODE_S, CTRL_ALT, ctx));
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

    @Test
    public void commandPaletteLeaderChord_waitsThenMatches() {
        TerminalKeyBindingResolver.Step first = resolver.advance(
            key(KeyEvent.KEYCODE_SPACE, CTRL_ALT), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING, first.kind);
        assertEquals("ctrl+alt+space", first.pendingSequence);
        assertTrue(resolver.hasPendingSequence());

        TerminalKeyBindingResolver.Step second = resolver.advance(key(KeyEvent.KEYCODE_P, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, second.kind);
        assertNotNull(second.match);
        assertEquals("app.command_palette", second.match.toolName);
        assertEquals("ctrl+alt+space>p", second.match.stroke);
        org.junit.Assert.assertFalse(resolver.hasPendingSequence());
    }

    @Test
    public void leaderDeclaration_mirrorsEveryCtrlAltStroke() {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "leader ctrl+space\n", LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        assertEquals("ctrl+space", config.leader);
        TerminalKeyBindingResolver.installConfigForTesting(config);
        resolver = TerminalKeyBindingResolver.getInstance();
        assertEquals("ctrl+space", resolver.getLeaderStroke());

        // Prefix, then the same key the Ctrl+Alt stroke uses.
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step sheet =
            resolver.advance(key(KeyEvent.KEYCODE_M, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, sheet.kind);
        assertEquals("terminal.action_sheet", sheet.match.toolName);

        // Shift is part of the second stroke, exactly as it is part of the Ctrl+Alt one.
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step palette =
            resolver.advance(key(KeyEvent.KEYCODE_P, KeyEvent.META_SHIFT_ON), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, palette.kind);
        assertEquals("app.command_palette", palette.match.toolName);
        assertEquals("ctrl+space>shift+p", palette.match.stroke);

        // The chord the prefix mirrors keeps working untouched.
        assertEquals("terminal.action_sheet", tool(KeyEvent.KEYCODE_M, CTRL_ALT, SPLITS_ON));
        // And the legend the hint slab draws lists the prefixed table.
        assertTrue(resolver.hintsForPrefix("ctrl+space>", SPLITS_ON).containsKey("m"));
    }

    @Test
    public void modifierPressAfterALeader_doesNotCancelTheSequence() {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "leader ctrl+space\n", LauncherToolRegistry.getInstance(), true);
        TerminalKeyBindingResolver.installConfigForTesting(config);
        resolver = TerminalKeyBindingResolver.getInstance();

        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
        // Reaching for Shift is how the *next* stroke is spelled, not an unknown continuation.
        assertEquals(TerminalKeyBindingResolver.Step.Kind.NONE,
            resolver.advance(key(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON), SPLITS_ON).kind);
        assertTrue(resolver.hasPendingSequence());

        TerminalKeyBindingResolver.Step step =
            resolver.advance(key(KeyEvent.KEYCODE_P, KeyEvent.META_SHIFT_ON), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, step.kind);
        assertEquals("app.command_palette", step.match.toolName);
        assertEquals("ctrl+space>shift+p", step.match.stroke);
    }

    @Test
    public void leaderAliases_neverOverwriteASequenceTheFileSpellsOut() {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "leader ctrl+space\n"
                + "map ctrl+space>m terminal.font_size_increase\n",
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
        resolver = TerminalKeyBindingResolver.getInstance();

        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step step =
            resolver.advance(key(KeyEvent.KEYCODE_M, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, step.kind);
        assertEquals("terminal.font_size_increase", step.match.toolName);
    }

    @Test
    public void secondLeaderLine_isRejectedSoTheFirstKeepsItsTable() {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "leader ctrl+space\nleader ctrl+b\nleader nonsense+\n",
            LauncherToolRegistry.getInstance(), true);
        assertEquals("ctrl+space", config.leader);
        assertEquals(2, config.errors.size());
    }

    @Test
    public void noLeaderDeclared_leavesTheTableAsItWas() {
        assertNull(resolver.getLeaderStroke());
        assertEquals(TerminalKeyBindingResolver.Step.Kind.NONE,
            resolver.advance(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), SPLITS_ON).kind);
    }

    @Test
    public void userConfigOverridesDefaultsAndRunsMultipleActions() {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "unmap ctrl+alt+v\n"
                + "map ctrl+alt+space>q terminal.font_size_increase\n"
                + "map ctrl+alt+space>q terminal.font_size_decrease\n",
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
        resolver = TerminalKeyBindingResolver.getInstance();

        assertNull(tool(KeyEvent.KEYCODE_V, CTRL_ALT, SPLITS_ON));
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_SPACE, CTRL_ALT), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step result = resolver.advance(key(KeyEvent.KEYCODE_Q, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, result.kind);
        assertEquals(2, result.match.actions.size());
        assertEquals("terminal.font_size_increase", result.match.actions.get(0).value);
        assertEquals("terminal.font_size_decrease", result.match.actions.get(1).value);
        assertTrue(resolver.getStrokesForTool("terminal.font_size_increase", SPLITS_ON)
            .contains("ctrl+alt+space>q"));
    }

    @Test
    public void argumentStrokes_mapEachAppLaunchQueryToItsOwnStroke() {
        // One tool backs many rows, so getStrokesForTool cannot tell the palette which app a
        // stroke launches. Only the argument distinguishes them.
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "map ctrl+alt+w app.launch org.mozilla.firefox\n"
                + "map ctrl+alt+m app.launch com.mail\n"
                + "map ctrl+alt+n app.launch org.mozilla.firefox\n",
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
        resolver = TerminalKeyBindingResolver.getInstance();

        Map<String, String> strokes =
            resolver.getArgumentStrokesForTool("app.launch", "query", SPLITS_ON);
        assertEquals(2, strokes.size());
        // The first stroke wins per app, matching what the rest of the palette shows.
        assertEquals("ctrl+alt+w", strokes.get("org.mozilla.firefox"));
        assertEquals("ctrl+alt+m", strokes.get("com.mail"));
    }

    @Test
    public void argumentStrokes_excludeABindingWhoseConditionDoesNotHold() {
        // Same false-promise rule as strokesFor: a stroke that cannot fire in this mode must not be
        // advertised on a row.
        Map<String, String> pasteOn =
            resolver.getArgumentStrokesForTool("app.launch", "query", SPLITS_ON);
        assertTrue(pasteOn.isEmpty());

        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(
            "map --when splits-off ctrl+alt+w app.launch org.mozilla.firefox\n",
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
        resolver = TerminalKeyBindingResolver.getInstance();

        assertTrue(resolver.getArgumentStrokesForTool("app.launch", "query", SPLITS_ON).isEmpty());
        assertEquals("ctrl+alt+w", resolver
            .getArgumentStrokesForTool("app.launch", "query", SPLITS_OFF)
            .get("org.mozilla.firefox"));
    }

    @Test
    public void unknownContinuation_cancelsAndConsumesTheSequence() {
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PENDING,
            resolver.advance(key(KeyEvent.KEYCODE_SPACE, CTRL_ALT), SPLITS_ON).kind);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.CANCELLED,
            resolver.advance(key(KeyEvent.KEYCODE_Q, 0), SPLITS_ON).kind);
        org.junit.Assert.assertFalse(resolver.hasPendingSequence());
    }

    @Test
    public void escapeAndTimeoutCancellation_discardPendingKeys() {
        resolver.advance(key(KeyEvent.KEYCODE_SPACE, CTRL_ALT), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.CANCELLED,
            resolver.advance(key(KeyEvent.KEYCODE_ESCAPE, 0), SPLITS_ON).kind);
        resolver.advance(key(KeyEvent.KEYCODE_SPACE, CTRL_ALT), SPLITS_ON);
        assertTrue(resolver.cancelPendingSequence());
        org.junit.Assert.assertFalse(resolver.cancelPendingSequence());
        assertEquals(TerminalKeyBindingResolver.Step.Kind.NONE,
            resolver.advance(key(KeyEvent.KEYCODE_P, 0), SPLITS_ON).kind);
    }

    @Test
    public void modalMapPushesMatchesAndPops() {
        installModal("map --new-mode panes --timeout 5 --on-unknown passthrough ctrl+alt+g\n"
            + "map --mode panes h send-text left\n"
            + "map --mode panes escape pop-mode\n");
        TerminalKeyBindingResolver.Step enter = resolver.advance(key(KeyEvent.KEYCODE_G, CTRL_ALT), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, enter.kind);
        assertEquals(TerminalBindingConfig.ActionType.PUSH_MODE, enter.match.actions.get(0).type);
        assertTrue(resolver.pushMode(enter.match.actions.get(0).value));
        assertEquals("panes", resolver.getCurrentMode());
        assertEquals(5000L, resolver.getCurrentModeTimeoutMillis());

        TerminalKeyBindingResolver.Step action = resolver.advance(key(KeyEvent.KEYCODE_H, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, action.kind);
        assertEquals("left", action.match.actions.get(0).value);
        assertEquals("panes", action.match.mode);
        resolver.afterMatch(action.match);
        assertEquals("panes", resolver.getCurrentMode());

        TerminalKeyBindingResolver.Step exit = resolver.advance(key(KeyEvent.KEYCODE_ESCAPE, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, exit.kind);
        assertTrue(resolver.popMode());
        assertEquals("", resolver.getCurrentMode());
    }

    @Test
    public void modalUnknownPoliciesAndEndOnAction() {
        installModal("map --new-mode pass --on-unknown passthrough ctrl+alt+g\n"
            + "map --mode pass h send-text x\n"
            + "map --new-mode once --on-unknown ignore --on-action end ctrl+alt+o\n"
            + "map --mode once q send-text q\n");
        TerminalKeyBindingResolver.Step enterPass = resolver.advance(key(KeyEvent.KEYCODE_G, CTRL_ALT), SPLITS_ON);
        resolver.pushMode(enterPass.match.actions.get(0).value);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.PASSTHROUGH,
            resolver.advance(key(KeyEvent.KEYCODE_Z, 0), SPLITS_ON).kind);
        resolver.popMode();

        TerminalKeyBindingResolver.Step enterOnce = resolver.advance(key(KeyEvent.KEYCODE_O, CTRL_ALT), SPLITS_ON);
        resolver.pushMode(enterOnce.match.actions.get(0).value);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.IGNORED,
            resolver.advance(key(KeyEvent.KEYCODE_Z, 0), SPLITS_ON).kind);
        TerminalKeyBindingResolver.Step q = resolver.advance(key(KeyEvent.KEYCODE_Q, 0), SPLITS_ON);
        assertEquals(TerminalKeyBindingResolver.Step.Kind.MATCH, q.kind);
        assertTrue(resolver.afterMatch(q.match));
        assertEquals("", resolver.getCurrentMode());
    }

    @Test
    public void modalModesStackAndTimeoutOneLevelAtATime() {
        installModal("map --new-mode outer --timeout 0 ctrl+alt+g\n"
            + "map --mode outer h send-text h\n"
            + "map --new-mode inner --timeout 1 ctrl+alt+i\n"
            + "map --mode inner q send-text q\n");
        assertTrue(resolver.pushMode("outer"));
        assertTrue(resolver.pushMode("inner"));
        assertEquals("inner", resolver.getCurrentMode());
        assertEquals(1000L, resolver.getCurrentModeTimeoutMillis());
        assertTrue(resolver.popCurrentModeOnTimeout());
        assertEquals("outer", resolver.getCurrentMode());
        assertEquals(0L, resolver.getCurrentModeTimeoutMillis());
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

    private void installModal(String configText) {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(configText,
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
        resolver = TerminalKeyBindingResolver.getInstance();
    }

    @Test
    public void strokeSpecNormalization_isModifierCaseAndOrderInsensitive() {
        assertEquals("ctrl+alt+v", TerminalKeyBindingResolver.normalizeStrokeSpec("CTRL+Alt+v"));
        assertEquals("ctrl+alt+v", TerminalKeyBindingResolver.normalizeStrokeSpec("alt+ctrl+v"));
        assertEquals("ctrl+alt+shift+left", TerminalKeyBindingResolver.normalizeStrokeSpec("SHIFT+ALT+CTRL+left"));
        assertEquals("ctrl+alt+]", TerminalKeyBindingResolver.normalizeStrokeSpec("Control+Alt+]"));
        // A multi-character key name has no shifted spelling, so case there is still just case.
        assertEquals("ctrl+alt+pageup",
            TerminalKeyBindingResolver.normalizeStrokeSpec("Ctrl+Alt+PageUp"));
    }

    @Test
    public void strokeSpecNormalization_readsAnUpperCaseLetterAsShift() {
        // What makes `map Ctrl+Alt+R` and `map Ctrl+Alt+r` two bindings a config file can tell
        // apart, the way it already could with an explicit shift+.
        assertEquals("ctrl+alt+shift+r", TerminalKeyBindingResolver.normalizeStrokeSpec("Ctrl+Alt+R"));
        assertEquals("ctrl+alt+r", TerminalKeyBindingResolver.normalizeStrokeSpec("Ctrl+Alt+r"));
        assertEquals("ctrl+alt+shift+r",
            TerminalKeyBindingResolver.normalizeStrokeSpec("ctrl+alt+shift+R"));
    }

    @Test
    public void sequenceSpecNormalization_normalizesEveryStroke() {
        assertEquals("ctrl+alt+space>shift+p",
            TerminalKeyBindingResolver.normalizeSequenceSpec("Alt+Ctrl+Space > Shift+P"));
    }

    @Test
    public void pendingOverlay_formatsASequenceForPeople() {
        assertEquals("Ctrl+Alt+Space  ›  P",
            TerminalKeyChordOverlay.displaySequence("ctrl+alt+space>p"));
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
