package com.termux.app.terminal;

import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TerminalBindingConfigTest {

    private final LauncherToolRegistry registry = LauncherToolRegistry.getInstance();

    @Test
    public void parsesActionsConditionsAndEscapedText() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "map ctrl+alt+space>s terminal.share_transcript\n"
                + "map --when=splits-on ctrl+alt+space>x pane.kill_focused\n"
                + "map ctrl+alt+space>t send-text \"echo hi\\n\"\n"
                + "map ctrl+alt+space>c send-key ctrl+c\n",
            registry, true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(4, result.mappings.size());
        assertEquals("ctrl+alt+space>s", result.mappings.get(0).sequence);
        assertEquals(TerminalBindingConfig.ActionType.TOOL,
            result.mappings.get(0).actions.get(0).type);
        assertEquals(LauncherToolRegistry.BindingCondition.SPLITS_ON,
            result.mappings.get(1).condition);
        assertEquals("echo hi\n", result.mappings.get(2).actions.get(0).value);
        assertEquals("ctrl+c", result.mappings.get(3).actions.get(0).value);
    }

    @Test
    public void repeatedMapsCreateAnOrderedActionList() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "map ctrl+alt+q terminal.font_size_increase\n"
                + "map ctrl+alt+q terminal.font_size_decrease\n",
            registry, true);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.mappings.size());
        assertEquals(2, result.mappings.get(0).actions.size());
        assertEquals("terminal.font_size_increase", result.mappings.get(0).actions.get(0).value);
        assertEquals("terminal.font_size_decrease", result.mappings.get(0).actions.get(1).value);
    }

    @Test
    public void inlineArgumentsFillRequiredPropertiesInOrder() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "map ctrl+alt+shift+w app.launch com.whatsapp\n"
                + "map ctrl+alt+shift+g pane.layout grid\n"
                + "map ctrl+alt+shift+3 window.select 3\n"
                + "map ctrl+alt+shift+s workspace.save name=project overwrite=true\n",
            registry, true);

        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(4, result.mappings.size());
        assertEquals("com.whatsapp",
            result.mappings.get(0).actions.get(0).arguments.optString("query"));
        assertEquals("grid", result.mappings.get(1).actions.get(0).arguments.optString("layout"));
        assertEquals(3, result.mappings.get(2).actions.get(0).arguments.optInt("index"));
        assertEquals("project",
            result.mappings.get(3).actions.get(0).arguments.optString("name"));
        assertTrue(result.mappings.get(3).actions.get(0).arguments.optBoolean("overwrite"));
    }

    @Test
    public void inlineArgumentsAreValidatedAgainstTheSchema() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "map ctrl+alt+shift+g pane.layout sideways\n"
                + "map ctrl+alt+shift+w window.select 900\n"
                + "map ctrl+alt+shift+e pane.equalize extra\n",
            registry, true);

        assertEquals(3, result.errors.size());
        assertTrue(result.mappings.isEmpty());
    }

    @Test
    public void gestureSequencesMapAndUnmapLikeStrokes() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "unmap kbd:space:swipe-north\n"
                + "map Alt+KBD:space:swipe-east session.previous\n"
                + "map ctrl+alt+q send-key kbd:space:swipe-east\n",
            registry, true);

        assertEquals(1, result.errors.size());
        assertEquals(1, result.mappings.size());
        assertEquals("alt+kbd:space:swipe-east", result.mappings.get(0).sequence);
        assertTrue(result.overriddenSequences.contains("kbd:space:swipe-north"));
    }

    @Test
    public void unmapOverridesADefaultWithoutCreatingAMapping() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "unmap ctrl+alt+v\n", registry, true);
        assertTrue(result.errors.isEmpty());
        assertEquals(0, result.mappings.size());
        assertEquals("ctrl+alt+v", result.overriddenSequences.get(0));
    }

    @Test
    public void badLinesAreReportedButValidLinesSurvive() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "wat ctrl+x no\n"
                + "map nope terminal.reset\n"
                + "map ctrl+alt+q not.a.tool\n"
                + "map ctrl+alt+z terminal.reset\n",
            registry, true);
        assertEquals(3, result.errors.size());
        assertEquals(1, result.mappings.size());
        assertEquals("ctrl+alt+z", result.mappings.get(0).sequence);
    }

    @Test
    public void commentsAndEmptyQuotedTextAreAccepted() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "# comment\nmap ctrl+alt+q send-text \"\" # discard by writing nothing\n",
            registry, true);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("", result.mappings.get(0).actions.get(0).value);
        assertFalse(result.overriddenSequences.isEmpty());
    }

    @Test
    public void sendKeyEncoderHandlesTextControlAndTerminalModes() {
        assertEquals("A", TerminalBindingKeyEncoder.encode("shift+a", false, false));
        assertEquals("\003", TerminalBindingKeyEncoder.encode("ctrl+c", false, false));
        assertEquals("\033x", TerminalBindingKeyEncoder.encode("alt+x", false, false));
        assertEquals("\033[A", TerminalBindingKeyEncoder.encode("up", false, false));
        assertEquals("\033OA", TerminalBindingKeyEncoder.encode("up", true, false));
    }

    @Test
    public void parsesModalMapsAndPolicies() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "map --new-mode panes --timeout 5 --on-unknown passthrough --on-action keep ctrl+alt+g\n"
                + "map --mode panes h send-text left\n"
                + "map --mode=panes escape pop-mode\n",
            registry, true);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.modes.size());
        TerminalBindingConfig.Mode mode = result.modes.get("panes");
        assertEquals(5000L, mode.timeoutMillis);
        assertEquals(TerminalBindingConfig.UnknownKeyPolicy.PASSTHROUGH, mode.onUnknown);
        assertFalse(mode.endOnAction);
        assertEquals(TerminalBindingConfig.ActionType.PUSH_MODE,
            result.mappings.get(0).actions.get(0).type);
        assertEquals("panes", result.mappings.get(1).mode);
        assertEquals(TerminalBindingConfig.ActionType.POP_MODE,
            result.mappings.get(2).actions.get(0).type);
    }

    @Test
    public void invalidOrUndefinedModesAreNonFatalDiagnostics() {
        TerminalBindingConfig.Result result = TerminalBindingConfig.parse(
            "map --mode missing h send-text nope\n"
                + "map --new-mode bad/name ctrl+alt+b\n"
                + "map --new-mode once --on-unknown wat ctrl+alt+o\n"
                + "map ctrl+alt+q send-text ok\n",
            registry, true);
        assertEquals(3, result.errors.size());
        assertEquals(1, result.mappings.size());
        assertEquals("ctrl+alt+q", result.mappings.get(0).sequence);
    }
}
