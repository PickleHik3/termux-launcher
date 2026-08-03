package com.termux.app.terminal;

import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric because normalizeSequenceSpec reaches KeyEvent, matching
 * {@link TerminalBindingConfigTest}. Only the pure line-editing core is exercised; the temp-file
 * rename around it is a copy of TerminalWorkspaceStore.save in shape.
 */
@RunWith(RobolectricTestRunner.class)
public class TerminalBindingConfigWriterTest {

    private static final String FIREFOX = "org.mozilla.firefox";

    @Test
    public void appendsWithAHeaderToAnEmptyFile() {
        TerminalBindingConfigWriter.Edit edit = bind(Collections.<String>emptyList(), "ctrl+alt+w");

        assertTrue(edit.error, edit.ok());
        assertFalse(edit.replaced);
        assertEquals(Arrays.asList(
            TerminalBindingConfigWriter.MANAGED_HEADER,
            "map ctrl+alt+w app.launch org.mozilla.firefox"), edit.lines);
    }

    @Test
    public void preservesCommentsAndBlankLinesExactly() {
        List<String> original = Arrays.asList(
            "# my bindings",
            "",
            "map ctrl+alt+j pane.focus_direction direction=down   # go down",
            "",
            "# end");

        TerminalBindingConfigWriter.Edit edit = bind(original, "ctrl+alt+w");

        assertTrue(edit.error, edit.ok());
        assertEquals(original, edit.lines.subList(0, original.size()));
    }

    @Test
    public void replacesTheSameStrokeInsteadOfDuplicatingIt() {
        List<String> original = Arrays.asList(
            "# keep me",
            "map ctrl+alt+w app.launch com.old.app",
            "map ctrl+alt+j pane.focus_direction direction=down");

        TerminalBindingConfigWriter.Edit edit = bind(original, "ctrl+alt+w");

        assertTrue(edit.error, edit.ok());
        assertTrue(edit.replaced);
        assertEquals(3, edit.lines.size());
        assertEquals(1, edit.index);
        assertEquals("map ctrl+alt+w app.launch org.mozilla.firefox", edit.lines.get(1));
        assertEquals("# keep me", edit.lines.get(0));
    }

    @Test
    public void oneSequenceIsOneSequenceHoweverItIsSpelled() {
        List<String> original = Collections.singletonList("map   Ctrl+Alt+W   app.launch com.old");

        TerminalBindingConfigWriter.Edit edit = bind(original, "ctrl+alt+w");

        assertTrue(edit.error, edit.ok());
        assertTrue(edit.replaced);
        assertEquals(1, edit.lines.size());
    }

    @Test
    public void aCommentedDirectiveIsNotADirective() {
        // The tokenizer strips #, so a commented mapping is structurally invisible here rather than
        // something a regex has to remember to avoid.
        List<String> original = Arrays.asList(
            "# map ctrl+alt+w app.launch com.example",
            "  #map ctrl+alt+w app.launch com.example");

        TerminalBindingConfigWriter.Edit edit = bind(original, "ctrl+alt+w");

        assertTrue(edit.error, edit.ok());
        assertFalse(edit.replaced);
        assertEquals(original, edit.lines.subList(0, 2));
        assertNull(TerminalBindingConfigWriter.mapLineSequence(original.get(0)));
        assertNull(TerminalBindingConfigWriter.mapLineSequence(original.get(1)));
    }

    @Test
    public void aModalMappingIsLeftAlone() {
        // A modal mapping lives in another keymap; rewriting it would move a binding between modes.
        List<String> original = Arrays.asList(
            "map --mode foo ctrl+alt+w app.launch com.example",
            "map --new-mode bar ctrl+alt+w app.launch com.example",
            "map --mode=foo ctrl+alt+w app.launch com.example");

        TerminalBindingConfigWriter.Edit edit = bind(original, "ctrl+alt+w");

        assertTrue(edit.error, edit.ok());
        assertFalse(edit.replaced);
        assertEquals(original, edit.lines.subList(0, 3));
        for (String line : original) assertNull(TerminalBindingConfigWriter.mapLineSequence(line));
    }

    @Test
    public void aConditionOptionDoesNotHideTheSequence() {
        assertEquals("ctrl+alt+w", TerminalBindingConfigWriter.mapLineSequence(
            "map --when splits-off ctrl+alt+w app.launch com.example"));
        assertEquals("ctrl+alt+w", TerminalBindingConfigWriter.mapLineSequence(
            "map ctrl+alt+w app.launch com.example"));
    }

    @Test
    public void appendsRatherThanReplacingWhenALaterUnmapWouldSilenceTheEdit() {
        // The parser reads directives in order, so an in-place edit above the unmap would parse
        // cleanly and then never fire.
        List<String> original = Arrays.asList(
            "map ctrl+alt+w app.launch com.old",
            "unmap ctrl+alt+w");

        TerminalBindingConfigWriter.Edit edit = bind(original, "ctrl+alt+w");

        assertTrue(edit.error, edit.ok());
        assertFalse(edit.replaced);
        assertEquals("map ctrl+alt+w app.launch org.mozilla.firefox",
            edit.lines.get(edit.lines.size() - 1));
        assertTrue(TerminalBindingConfigWriter.isUnmapOf("unmap Ctrl+Alt+W", "ctrl+alt+w"));
        assertFalse(TerminalBindingConfigWriter.isUnmapOf("unmap --mode foo ctrl+alt+w",
            "ctrl+alt+w"));
    }

    @Test
    public void errorsAndChangesNothingWhenTheFileIsAlreadyAtTheLineLimit() {
        List<String> original = new ArrayList<>();
        for (int i = 0; i < 4096; i++) original.add("# filler " + i);

        TerminalBindingConfigWriter.Edit edit = bind(original, "ctrl+alt+w");

        assertNotNull(edit.error);
        assertFalse(edit.ok());
        assertEquals(original, edit.lines);
    }

    @Test
    public void aWrittenStableIdParsesBackToTheSameArguments() {
        // The highest-value case here: a work-profile stable id contains #userSerial=, and # starts
        // a comment, so writer and parser must agree on quoting or the binding loses its argument.
        String stableId = "com.mail/.Main#userSerial=10";
        TerminalBindingConfigWriter.Edit edit = TerminalBindingConfigWriter.putMapping(
            Collections.<String>emptyList(), "ctrl+alt+m",
            LauncherToolRegistry.TOOL_APP_LAUNCH, Collections.singletonList(stableId));
        assertTrue(edit.error, edit.ok());

        StringBuilder file = new StringBuilder();
        for (String line : edit.lines) file.append(line).append('\n');
        TerminalBindingConfig.Result parsed = TerminalBindingConfig.parse(file.toString(),
            LauncherToolRegistry.getInstance(), true);

        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty());
        assertEquals(1, parsed.mappings.size());
        TerminalBindingConfig.Mapping mapping = parsed.mappings.get(0);
        assertEquals("ctrl+alt+m", mapping.sequence);
        assertEquals(1, mapping.actions.size());
        assertEquals(LauncherToolRegistry.TOOL_APP_LAUNCH, mapping.actions.get(0).value);
        assertEquals(stableId, mapping.actions.get(0).arguments.optString("query"));
    }

    @Test
    public void quoteWord_quotesOnlyWhatTheTokenizerWouldMangle() {
        assertEquals("plain", TerminalBindingConfigWriter.quoteWord("plain"));
        assertEquals("com.example/.Main", TerminalBindingConfigWriter.quoteWord("com.example/.Main"));
        assertEquals("\"has#hash\"", TerminalBindingConfigWriter.quoteWord("has#hash"));
        assertEquals("\"has space\"", TerminalBindingConfigWriter.quoteWord("has space"));
        assertEquals("\"--looks-like-an-option\"",
            TerminalBindingConfigWriter.quoteWord("--looks-like-an-option"));
        assertEquals("\"quote\\\"inside\"", TerminalBindingConfigWriter.quoteWord("quote\"inside"));
        assertEquals("''", TerminalBindingConfigWriter.quoteWord(""));
    }

    @Test
    public void removeMapping_dropsTheMappingAndKeepsEverythingElse() {
        List<String> original = Arrays.asList(
            "# comment",
            "map ctrl+alt+w app.launch com.old",
            "map ctrl+alt+j pane.focus_direction direction=down");

        TerminalBindingConfigWriter.Edit edit =
            TerminalBindingConfigWriter.removeMapping(original, "Ctrl+Alt+W");

        assertTrue(edit.ok());
        assertTrue(edit.replaced);
        assertEquals(Arrays.asList("# comment",
            "map ctrl+alt+j pane.focus_direction direction=down"), edit.lines);
    }

    @Test
    public void anEmptySequenceIsRefused() {
        TerminalBindingConfigWriter.Edit edit = bind(Collections.<String>emptyList(), "");
        assertNotNull(edit.error);
        assertFalse(edit.ok());
    }

    private static TerminalBindingConfigWriter.Edit bind(List<String> lines, String sequence) {
        return TerminalBindingConfigWriter.putMapping(lines, sequence,
            LauncherToolRegistry.TOOL_APP_LAUNCH, Collections.singletonList(FIREFOX));
    }
}
