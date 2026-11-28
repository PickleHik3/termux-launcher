package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.tiling.desktop.AppOpen;
import com.termux.app.x11.X11Apps;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The rows the palette leads with from the Terl control bar's search (#435, Terl decisions
 * 2026-11-28): the sections, their order and the filter (moved from the retired bar search), the
 * words at a row's end, and the rows the Terl host runs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class CommandPaletteTerlSetTest {

    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private static LauncherAppEntry android(String label) {
        return new LauncherAppEntry(new AppRef("com.example." + label.toLowerCase(), "Main"), label, null);
    }

    private static LauncherAppEntry linux(String label) {
        return new LauncherAppEntry(X11Apps.ref(label.toLowerCase() + ".desktop"), label, null);
    }

    private static LauncherAppEntry terminal() {
        return new LauncherAppEntry(LauncherEntries.terminal(), "Terminal", null);
    }

    private static LauncherAppEntry display() {
        return new LauncherAppEntry(LauncherEntries.display(), "Display", null);
    }

    private static LauncherAppEntry assistant() {
        return new LauncherAppEntry(LauncherEntries.assistant(), "Assistant", null);
    }

    private static List<String> labels(List<CommandPaletteTerlSet.Row> rows) {
        List<String> out = new ArrayList<>();
        for (CommandPaletteTerlSet.Row row : rows)
            out.add(row.section == CommandPaletteTerlSet.Section.EDIT_LOOK ? "<edit>"
                : row.entry == null ? "<all>" : row.entry.label);
        return out;
    }

    @Test
    public void nothingTypedListsAllAppsThenTerminalAndAssistantThenLinuxThenAndroid() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Maps"), linux("Gimp"), assistant(),
            display(), terminal(), android("Clock"), linux("Firefox"));
        List<CommandPaletteTerlSet.Row> rows = CommandPaletteTerlSet.arrange(catalogue, "", true, NO_LIMIT);
        assertEquals(Arrays.asList("<all>", "Terminal", "Assistant", "Gimp", "Display", "Firefox",
            "Maps", "Clock"), labels(rows));
        assertEquals(CommandPaletteTerlSet.Section.ALL_APPS, rows.get(0).section);
        assertEquals(CommandPaletteTerlSet.Section.TERMINAL, rows.get(2).section);
        assertEquals(CommandPaletteTerlSet.Section.LINUX, rows.get(3).section);
        assertEquals(CommandPaletteTerlSet.Section.ANDROID, rows.get(6).section);
        assertFalse("All apps carries no header", CommandPaletteTerlSet.Section.ALL_APPS.headed);
        assertTrue(CommandPaletteTerlSet.Section.TERMINAL.headed);
    }

    @Test
    public void typingFiltersAllThreeAndDropsAllApps() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Terminus"), linux("Terminator"),
            terminal(), android("Maps"), linux("Gimp"));
        List<String> labels = labels(CommandPaletteTerlSet.arrange(catalogue, "term", true, NO_LIMIT));
        assertEquals("Terminal", labels.get(0));
        assertTrue(labels.contains("Terminator"));
        assertTrue(labels.contains("Terminus"));
        assertFalse(labels.contains("Maps"));
        assertFalse(labels.contains("<all>"));
        assertTrue("Linux apps before Android apps",
            labels.indexOf("Terminator") < labels.indexOf("Terminus"));
    }

    @Test
    public void aSectionHoldsAtMostTheLimitAndTerminalAlwaysShows() {
        List<LauncherAppEntry> catalogue = Arrays.asList(terminal(), android("A1"), android("A2"),
            android("A3"), linux("L1"), linux("L2"), linux("L3"));
        assertEquals(Arrays.asList("<all>", "Terminal", "L1", "L2", "A1", "A2"),
            labels(CommandPaletteTerlSet.arrange(catalogue, "", false, 2)));
    }

    @Test
    public void aSectionWithNoMatchIsAbsent() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Maps"), terminal(), linux("Gimp"));
        List<CommandPaletteTerlSet.Row> rows = CommandPaletteTerlSet.arrange(catalogue, "maps", true, NO_LIMIT);
        assertEquals(Collections.singletonList("Maps"), labels(rows));
        assertEquals(CommandPaletteTerlSet.Section.ANDROID, rows.get(0).section);
    }

    @Test
    public void editAndAppearanceOfferEditLookFirst() {
        List<LauncherAppEntry> catalogue = Arrays.asList(android("Editor"), terminal());
        for (String typed : new String[] {"edit", "Edit ", "appearance", "appear", "ed"}) {
            List<CommandPaletteTerlSet.Row> rows = CommandPaletteTerlSet.arrange(catalogue, typed, true, NO_LIMIT);
            assertEquals(typed, CommandPaletteTerlSet.Section.EDIT_LOOK, rows.get(0).section);
            assertNull(rows.get(0).entry);
        }
        assertEquals(Collections.singletonList("Editor"),
            labels(CommandPaletteTerlSet.arrange(catalogue, "edit", false, NO_LIMIT)));
        assertFalse(CommandPaletteTerlSet.offersEditLook("e"));
        assertFalse(CommandPaletteTerlSet.offersEditLook("editor"));
        assertFalse(CommandPaletteTerlSet.offersEditLook("maps"));
    }

    /** The right-side words: Open the drawer, New window, Open; a plain launch says nothing. */
    @Test
    public void aRowsEndSaysWhatItsTapDoes() {
        Context context = RuntimeEnvironment.getApplication();
        CommandPaletteTerlSet.WindowOpen open = (target, entry) -> !"Gimp".equals(entry.label);
        assertEquals("Open the drawer", text(context, row(CommandPaletteTerlSet.Section.ALL_APPS, null), open));
        assertEquals("Open", text(context, row(CommandPaletteTerlSet.Section.TERMINAL, terminal()), open));
        assertEquals("New window", text(context, row(CommandPaletteTerlSet.Section.TERMINAL, terminal()),
            (target, entry) -> false));
        assertEquals("Open", text(context, row(CommandPaletteTerlSet.Section.LINUX, linux("Firefox")), open));
        assertEquals("", text(context, row(CommandPaletteTerlSet.Section.LINUX, linux("Gimp")), open));
        assertEquals("", text(context, row(CommandPaletteTerlSet.Section.ANDROID, android("Maps")), open));
        assertEquals("", text(context, row(CommandPaletteTerlSet.Section.TERMINAL, assistant()), open));
    }

    @Test
    public void anAppRowIsAnAppRowEndingInItsHint() {
        Context context = RuntimeEnvironment.getApplication();
        CommandPaletteFilter.Entry gimp = CommandPaletteTerlSet.entryFor(context,
            row(CommandPaletteTerlSet.Section.LINUX, linux("Gimp")), "", Collections.emptyMap());
        assertTrue(gimp.isAppRow());
        assertTrue("held to bind a key, as every app row", TerminalCommandPalette.isBindable(gimp));
        assertEquals(LauncherToolRegistry.TOOL_APP_LAUNCH, gimp.toolName);
        assertEquals("a plain launch shows no ledger marker", "", gimp.shortcutLabel());
        CommandPaletteFilter.Entry term = CommandPaletteTerlSet.entryFor(context,
            row(CommandPaletteTerlSet.Section.TERMINAL, terminal()), "Open",
            Collections.singletonMap(LauncherEntries.terminal().stableId(), "ctrl+alt+t"));
        assertEquals("the words win over the chord", "Open", term.shortcutLabel());
        assertEquals(CommandPaletteTerlSet.CATEGORY_TERMINAL, term.category);
        assertTrue(term.isAppRow());
        assertEquals("Android apps", TerminalCommandPalette.categoryLabel(context,
            CommandPaletteTerlSet.CATEGORY_ANDROID_APPS));
        assertEquals("v1's app rows keep their marker", "app",
            TerminalCommandPalette.appEntry(context, android("Maps"), Collections.emptyMap()).shortcutLabel());
    }

    @Test
    public void theTerlHostRunsEditLookAllAppsAndEveryAppRow() {
        Context context = RuntimeEnvironment.getApplication();
        RecordingHost host = new RecordingHost();
        LauncherAppEntry firefox = linux("Firefox");
        CommandPaletteTerlSet.AppLookup apps =
            id -> firefox.appRef.stableId().equals(id) ? firefox : null;

        assertTrue(CommandPaletteTerlSet.runHostRow(CommandPaletteTerlSet.entryFor(context,
            row(CommandPaletteTerlSet.Section.EDIT_LOOK, null), "", Collections.emptyMap()), host, apps));
        assertTrue(CommandPaletteTerlSet.runHostRow(CommandPaletteTerlSet.entryFor(context,
            row(CommandPaletteTerlSet.Section.ALL_APPS, null), "", Collections.emptyMap()), host, apps));
        assertTrue(CommandPaletteTerlSet.runHostRow(CommandPaletteTerlSet.entryFor(context,
            row(CommandPaletteTerlSet.Section.LINUX, firefox), "", Collections.emptyMap()), host, apps));
        // The strip's app chips are v1 app rows; they open the same way.
        assertTrue(CommandPaletteTerlSet.runHostRow(
            TerminalCommandPalette.appEntry(context, firefox, Collections.emptyMap()), host, apps));
        assertEquals(Arrays.asList("edit", "drawer", "open Firefox", "open Firefox"), host.calls);

        assertFalse("an app gone from the catalogue goes to the dispatcher",
            CommandPaletteTerlSet.runHostRow(TerminalCommandPalette.appEntry(context, android("Maps"),
                Collections.emptyMap()), host, apps));
        assertTrue(CommandPaletteShape.dismissesInPlace(CommandPaletteTerlSet.TOOL_ALL_APPS));
    }

    private static CommandPaletteTerlSet.Row row(CommandPaletteTerlSet.Section section, LauncherAppEntry entry) {
        return new CommandPaletteTerlSet.Row(section, entry);
    }

    private static String text(Context context, CommandPaletteTerlSet.Row row,
                               CommandPaletteTerlSet.WindowOpen open) {
        return CommandPaletteTerlSet.hintText(context, CommandPaletteTerlSet.hintOf(row, open));
    }

    private static final class RecordingHost implements CommandPaletteTerlHost {
        final List<String> calls = new ArrayList<>();

        @Override public void open(@NonNull LauncherAppEntry entry) { calls.add("open " + entry.label); }

        @Override public void openEditMode() { calls.add("edit"); }

        @Override public void openDrawer() { calls.add("drawer"); }

        @Override public boolean windowOpen(@NonNull AppOpen.Target target, @NonNull LauncherAppEntry entry) {
            assertNotNull(target);
            return false;
        }
    }
}
