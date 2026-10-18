package com.termux.app.terminal;

import android.content.Context;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The palette's Home set: which actions Home leaves out, the Home section and its order, the jump
 * rows for windows and Home floats, the Linux apps section, the host routing and the typed-key
 * opening rule — all without an activity, through the no-op host by default.
 */
@RunWith(RobolectricTestRunner.class)
public class CommandPaletteHomeContextTest {

    private static final List<String> HOME_SECTION = Arrays.asList(
        CommandPaletteHomeSet.TOOL_OPEN_TERMINAL_HERE,
        CommandPaletteHomeSet.TOOL_GO_TO_WORKSPACE,
        LauncherToolRegistry.TOOL_WINDOW_NEW,
        CommandPaletteHomeSet.TOOL_MINIMISE_FLOATS,
        CommandPaletteHomeSet.TOOL_RESTORE_FLOATS,
        CommandPaletteHomeSet.TOOL_ADD_WIDGET,
        LauncherToolRegistry.TOOL_APPEARANCE_SET_WALLPAPER,
        CommandPaletteHomeSet.TOOL_LAYOUT_EDITOR,
        LauncherToolRegistry.TOOL_APPEARANCE_SURFACE_EDITOR,
        LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS);

    private Context context;
    private LauncherToolRegistry registry;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        registry = LauncherToolRegistry.getInstance();
    }

    @Test
    public void terminalOnly_coversThePaneCategoriesAndTheFewToolsFiledElsewhere() {
        for (String tool : Arrays.asList(LauncherToolRegistry.TOOL_PANE_SPLIT_VERTICAL,
            LauncherToolRegistry.TOOL_TERMINAL_SELECT_URL,
            LauncherToolRegistry.TOOL_TERMINAL_SHARE_TRANSCRIPT,
            LauncherToolRegistry.TOOL_CLIPBOARD_PASTE,
            LauncherToolRegistry.TOOL_MOUSE_TOGGLE,
            LauncherToolRegistry.TOOL_WINDOW_CLOSE,
            LauncherToolRegistry.TOOL_WINDOW_SELECT))
            assertTrue(tool, registry.getTool(tool).terminalOnly);
        for (String tool : Arrays.asList(LauncherToolRegistry.TOOL_WINDOW_NEW,
            LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS,
            LauncherToolRegistry.TOOL_SESSION_NEW,
            LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_LAYOUT,
            LauncherToolRegistry.TOOL_APPEARANCE_SET_WALLPAPER))
            assertFalse(tool, registry.getTool(tool).terminalOnly);
    }

    @Test
    public void terminalContext_isTheDefaultProjectionUnchanged() {
        List<CommandPaletteFilter.Entry> plain = TerminalCommandPalette.buildEntries(context);
        List<CommandPaletteFilter.Entry> terminal =
            TerminalCommandPalette.buildEntries(context, CommandPaletteContext.TERMINAL);
        assertEquals(keys(plain), keys(terminal));
        assertTrue(toolNames(terminal).contains(LauncherToolRegistry.TOOL_PANE_SPLIT_VERTICAL));
        for (CommandPaletteFilter.Entry entry : terminal)
            assertFalse(entry.toolName, CommandPaletteHomeSet.isHostAction(entry.toolName));
    }

    @Test
    public void homeContext_leadsWithTheHomeSectionInOrderAndDropsTerminalOnlyTools() {
        List<CommandPaletteFilter.Entry> home =
            TerminalCommandPalette.buildEntries(context, CommandPaletteContext.HOME);

        assertEquals(HOME_SECTION, toolNames(home.subList(0, HOME_SECTION.size())));
        for (CommandPaletteFilter.Entry entry : home.subList(0, HOME_SECTION.size()))
            assertEquals(CommandPaletteHomeSet.CATEGORY_HOME, entry.category);
        for (CommandPaletteFilter.Entry entry : home) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(entry.toolName);
            assertFalse(entry.toolName, tool != null && tool.terminalOnly);
        }
        // A claimed registry tool shows once, in the Home section, under its Home name.
        assertEquals(1, Collections.frequency(toolNames(home),
            LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS));
        assertEquals("Settings", home.get(9).title);
        assertEquals("Wallpaper", home.get(6).title);
        // What is neither terminal-only nor claimed stays, in its own section.
        assertTrue(toolNames(home).contains(LauncherToolRegistry.TOOL_SESSION_NEW));
    }

    @Test
    public void jumpEntries_aWindowSelectsByIndexAndAFloatRestoresWithItsPage() {
        SessionBrowserModel.Window plain = new SessionBrowserModel.Window(11L, 0, true, 0,
            Collections.<SessionBrowserModel.Pane>emptyList(), null);
        SessionBrowserModel.Window floating = new SessionBrowserModel.Window(12L, 1, false, 0,
            Collections.<SessionBrowserModel.Pane>emptyList(), "notes");
        SessionBrowserModel.Window elsewhere = new SessionBrowserModel.Window(13L, 0, true, 0,
            Collections.<SessionBrowserModel.Pane>emptyList(), "other");
        List<SessionBrowserModel.Session> sessions = Arrays.asList(
            new SessionBrowserModel.Session(1L, 0, false, "bg",
                Collections.singletonList(elsewhere)),
            new SessionBrowserModel.Session(2L, 1, true, "main", Arrays.asList(plain, floating)));
        CommandPaletteHomeHost host = new CommandPaletteHomeHost() {
            @Override public boolean isHomeFloat(long windowId) { return windowId == 12L; }
            @Override public int homeFloatPage(long windowId) { return 1; }
        };

        List<CommandPaletteFilter.Entry> rows =
            CommandPaletteHomeSet.jumpEntries(context, sessions, host);

        assertEquals("only the current session's windows", 2, rows.size());
        CommandPaletteFilter.Entry window = rows.get(0);
        assertEquals(LauncherToolRegistry.TOOL_WINDOW_SELECT, window.toolName);
        assertEquals(0, window.arguments.optInt("index", -1));
        assertEquals("Window 1", window.title);
        assertEquals("Window 1 · current", window.subtitle);
        CommandPaletteFilter.Entry floatRow = rows.get(1);
        assertEquals(CommandPaletteHomeSet.TOOL_RESTORE_FLOAT, floatRow.toolName);
        assertEquals(12L, floatRow.arguments.optLong("window_id", -1));
        assertEquals("notes", floatRow.title);
        assertEquals("Float · page 2", floatRow.subtitle);
        assertEquals(CommandPaletteHomeSet.CATEGORY_WINDOWS, floatRow.category);
    }

    @Test
    public void jumpEntries_aFloatWithNoKnownPageSaysFloat() {
        SessionBrowserModel.Window floating = new SessionBrowserModel.Window(5L, 0, true, 0,
            Collections.<SessionBrowserModel.Pane>emptyList(), "f");
        CommandPaletteHomeHost host = new CommandPaletteHomeHost() {
            @Override public boolean isHomeFloat(long windowId) { return true; }
        };
        List<CommandPaletteFilter.Entry> rows = CommandPaletteHomeSet.jumpEntries(context,
            Collections.singletonList(new SessionBrowserModel.Session(1L, 0, true, null,
                Collections.singletonList(floating))), host);
        assertEquals("Float", rows.get(0).subtitle);
    }

    @Test
    public void noneHost_knowsNoFloatSoEveryWindowIsAWindowRow() {
        assertFalse(CommandPaletteHomeHost.NONE.isHomeFloat(1L));
        assertEquals(CommandPaletteHomeHost.UNKNOWN_PAGE, CommandPaletteHomeHost.NONE.homeFloatPage(1L));
    }

    @Test
    public void runHostAction_routesEachHomeRowToItsHostCallAndLeavesToolsAlone() {
        List<String> calls = new ArrayList<>();
        CommandPaletteHomeHost host = new CommandPaletteHomeHost() {
            @Override public void openTerminalHere() { calls.add("terminal"); }
            @Override public void goToWorkspace() { calls.add("workspace"); }
            @Override public void minimiseAllFloats() { calls.add("minimise"); }
            @Override public void restoreFloats() { calls.add("restore"); }
            @Override public void addWidget() { calls.add("widget"); }
            @Override public void openLayoutEditor() { calls.add("layout"); }
            @Override public void restoreFloat(long windowId) { calls.add("float " + windowId); }
        };
        List<CommandPaletteFilter.Entry> home =
            TerminalCommandPalette.buildEntries(context, CommandPaletteContext.HOME);
        for (CommandPaletteFilter.Entry entry : home.subList(0, HOME_SECTION.size()))
            assertEquals(entry.toolName, CommandPaletteHomeSet.isHostAction(entry.toolName),
                CommandPaletteHomeSet.runHostAction(entry, host));
        assertEquals(Arrays.asList("terminal", "workspace", "minimise", "restore", "widget",
            "layout"), calls);

        SessionBrowserModel.Window floating = new SessionBrowserModel.Window(9L, 0, true, 0,
            Collections.<SessionBrowserModel.Pane>emptyList(), "f");
        CommandPaletteFilter.Entry floatRow = CommandPaletteHomeSet.jumpEntries(context,
            Collections.singletonList(new SessionBrowserModel.Session(1L, 0, true, null,
                Collections.singletonList(floating))), new CommandPaletteHomeHost() {
                @Override public boolean isHomeFloat(long windowId) { return true; }
            }).get(0);
        assertTrue(CommandPaletteHomeSet.runHostAction(floatRow, host));
        assertEquals("float 9", calls.get(calls.size() - 1));
    }

    @Test
    public void orderedForHome_putsHomeThenJumpsThenSessionsThenTheRest() {
        CommandPaletteFilter.Entry homeRow = row("home.a", CommandPaletteHomeSet.CATEGORY_HOME);
        CommandPaletteFilter.Entry other = row("session.new", LauncherToolRegistry.CATEGORY_SESSION);
        CommandPaletteFilter.Entry jump = row("window.select", CommandPaletteHomeSet.CATEGORY_WINDOWS);
        CommandPaletteFilter.Entry session = row("session.activate_by_index",
            TerminalCommandPalette.CATEGORY_SESSIONS);
        List<CommandPaletteFilter.Entry> ordered = CommandPaletteHomeSet.orderedForHome(
            Arrays.asList(other, homeRow), Collections.singletonList(jump),
            Collections.singletonList(session));
        assertEquals(Arrays.asList(homeRow, jump, session, other), ordered);
    }

    @Test
    public void opensOnTyped_onlyAVisibleFirstCharacterWithThePaletteShut() {
        assertTrue(CommandPaletteHomeSet.opensOnTyped(false, "f"));
        assertTrue(CommandPaletteHomeSet.opensOnTyped(false, "é"));
        assertFalse("already open", CommandPaletteHomeSet.opensOnTyped(true, "f"));
        assertFalse(CommandPaletteHomeSet.opensOnTyped(false, " "));
        assertFalse(CommandPaletteHomeSet.opensOnTyped(false, "\n"));
        assertFalse(CommandPaletteHomeSet.opensOnTyped(false, ""));
        assertFalse(CommandPaletteHomeSet.opensOnTyped(false, null));
    }

    @Test
    public void appSections_splitLinuxAppsFromAndroidAppsAndAllKeepsBoth() {
        LauncherAppEntry android = new LauncherAppEntry(new AppRef("com.example.mail", "Main"),
            "Mail", null);
        LauncherAppEntry linux = new LauncherAppEntry(X11Apps.ref("org.gimp.GIMP.desktop"),
            "GIMP", null);
        List<LauncherAppEntry> apps = Arrays.asList(android, linux);

        assertEquals(apps, TerminalCommandPalette.appsIn(apps, TerminalCommandPalette.AppSection.ALL));
        assertEquals(Collections.singletonList(android),
            TerminalCommandPalette.appsIn(apps, TerminalCommandPalette.AppSection.ANDROID));
        assertEquals(Collections.singletonList(linux),
            TerminalCommandPalette.appsIn(apps, TerminalCommandPalette.AppSection.LINUX));
    }

    @Test
    public void appSections_fileTheDisplayEntryUnderLinuxAppsAsTheDrawerDoes() {
        LauncherAppEntry android = new LauncherAppEntry(new AppRef("com.example.mail", "Main"),
            "Mail", null);
        LauncherAppEntry display = new LauncherAppEntry(
            com.termux.app.launcher.LauncherEntries.display(), "Display", null);
        List<LauncherAppEntry> apps = Arrays.asList(android, display);

        assertEquals(Collections.singletonList(display),
            TerminalCommandPalette.appsIn(apps, TerminalCommandPalette.AppSection.LINUX));
        assertEquals(Collections.singletonList(android),
            TerminalCommandPalette.appsIn(apps, TerminalCommandPalette.AppSection.ANDROID));

        CommandPaletteFilter.Entry row = TerminalCommandPalette.linuxAppEntry(context, display,
            Collections.<String, String>emptyMap());
        assertEquals(CommandPaletteHomeSet.CATEGORY_LINUX_APPS, row.category);
        assertEquals(display.appRef.stableId(), row.arguments.optString("query"));
        assertEquals(context.getString(com.termux.R.string.palette_launcher_entry_row_subtitle),
            row.subtitle);
    }

    @Test
    public void linuxAppEntry_launchesByStableIdUnderLinuxAppsAndIsBindable() {
        LauncherAppEntry linux = new LauncherAppEntry(X11Apps.ref("org.gimp.GIMP.desktop"),
            "GIMP", null);
        CommandPaletteFilter.Entry row = TerminalCommandPalette.linuxAppEntry(context, linux,
            Collections.<String, String>emptyMap());

        assertEquals(LauncherToolRegistry.TOOL_APP_LAUNCH, row.toolName);
        assertEquals(linux.appRef.stableId(), row.arguments.optString("query"));
        assertEquals(CommandPaletteHomeSet.CATEGORY_LINUX_APPS, row.category);
        assertEquals(linux.appRef.stableId(), row.iconKey);
        assertTrue(row.isAppRow());
        assertEquals("app", row.shortcutLabel());
        assertTrue(TerminalCommandPalette.isBindable(row));
        assertEquals("Linux apps", TerminalCommandPalette.categoryLabel(context, row.category));
    }

    @Test
    public void strip_neverOffersAJumpRow() {
        assertTrue(CommandPaletteStrip.EXCLUDED_TOOLS.contains(
            LauncherToolRegistry.TOOL_WINDOW_SELECT));
        assertTrue(CommandPaletteStrip.EXCLUDED_TOOLS.contains(
            CommandPaletteHomeSet.TOOL_RESTORE_FLOAT));
    }

    private static CommandPaletteFilter.Entry row(String tool, String category) {
        return new CommandPaletteFilter.Entry(tool, tool, "", category,
            Collections.<String>emptyList(), true, null, false,
            LauncherToolRegistry.ToolRisk.LOW);
    }

    private static List<String> toolNames(List<CommandPaletteFilter.Entry> entries) {
        List<String> names = new ArrayList<>(entries.size());
        for (CommandPaletteFilter.Entry entry : entries) names.add(entry.toolName);
        return names;
    }

    private static List<String> keys(List<CommandPaletteFilter.Entry> entries) {
        List<String> keys = new ArrayList<>(entries.size());
        for (CommandPaletteFilter.Entry entry : entries)
            keys.add(entry.toolName + "|" + entry.category + "|" + entry.title);
        return keys;
    }
}
