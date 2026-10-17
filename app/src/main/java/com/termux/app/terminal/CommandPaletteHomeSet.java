package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows the palette adds or reshapes on Home ({@link CommandPaletteContext#HOME}): the Home
 * actions section, and a jump row per window and Home float of the current session.
 *
 * <p>The Home actions are one ordered list. Four of them are registry tools (New window,
 * Wallpaper, Appearance, Settings) lifted out of their own sections into this one under their Home
 * names, so a keystroke and a tap still share the dispatcher; the rest need the wall and run
 * through {@link CommandPaletteHomeHost}, under {@code home.*} names the registry does not know
 * and the dispatcher is never asked to run.
 *
 * <p>Pure projection over already-built rows and the session browser's model, so what Home shows
 * is testable without an activity.
 */
final class CommandPaletteHomeSet {

    /** Display-only grouping keys, not registry categories. */
    static final String CATEGORY_HOME = "home";
    static final String CATEGORY_LINUX_APPS = "linux_apps";
    /** The jump rows: the current session's windows and Home floats. */
    static final String CATEGORY_WINDOWS = "windows";

    static final String TOOL_OPEN_TERMINAL_HERE = "home.open_terminal_here";
    static final String TOOL_GO_TO_WORKSPACE = "home.go_to_workspace";
    static final String TOOL_MINIMISE_FLOATS = "home.minimise_floats";
    static final String TOOL_RESTORE_FLOATS = "home.restore_floats";
    static final String TOOL_ADD_WIDGET = "home.add_widget";
    static final String TOOL_LAYOUT_EDITOR = "home.layout_editor";
    /** A float's jump row; carries {@code window_id}. */
    static final String TOOL_RESTORE_FLOAT = "home.restore_float";

    /** One Home action: the tool it runs, its Home title, and its own subtitle or 0 for the tool's. */
    private static final class Action {
        final String tool;
        @StringRes final int titleRes;
        @StringRes final int subtitleRes;

        Action(String tool, @StringRes int titleRes, @StringRes int subtitleRes) {
            this.tool = tool;
            this.titleRes = titleRes;
            this.subtitleRes = subtitleRes;
        }
    }

    /** The Home section, in the order it shows. */
    private static final Action[] HOME_ACTIONS = {
        new Action(TOOL_OPEN_TERMINAL_HERE, R.string.palette_home_open_terminal,
            R.string.palette_home_open_terminal_hint),
        new Action(TOOL_GO_TO_WORKSPACE, R.string.palette_home_go_to_workspace,
            R.string.palette_home_go_to_workspace_hint),
        new Action(LauncherToolRegistry.TOOL_WINDOW_NEW, R.string.palette_home_new_window, 0),
        new Action(TOOL_MINIMISE_FLOATS, R.string.palette_home_minimise_floats,
            R.string.palette_home_minimise_floats_hint),
        new Action(TOOL_RESTORE_FLOATS, R.string.palette_home_restore_floats,
            R.string.palette_home_restore_floats_hint),
        new Action(TOOL_ADD_WIDGET, R.string.widget_add, R.string.palette_home_add_widget_hint),
        new Action(LauncherToolRegistry.TOOL_APPEARANCE_SET_WALLPAPER,
            R.string.palette_home_wallpaper, 0),
        new Action(TOOL_LAYOUT_EDITOR, R.string.palette_home_layout,
            R.string.palette_home_layout_hint),
        new Action(LauncherToolRegistry.TOOL_APPEARANCE_SURFACE_EDITOR,
            R.string.palette_home_appearance, 0),
        new Action(LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS, R.string.palette_home_settings, 0),
    };

    private CommandPaletteHomeSet() {
    }

    /** Whether the row runs through the Home host rather than the dispatcher. */
    static boolean isHostAction(@NonNull String toolName) {
        return toolName.startsWith("home.");
    }

    /**
     * Home's tool rows: the Home section first, in its order, then every other projected row that
     * the Home section did not claim. {@code projected} is the registry projection with the
     * terminal-only tools already left out; a claimed registry tool that is absent from it (none
     * today) simply has no Home row.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> withHomeSection(
        @NonNull Context context, @NonNull List<CommandPaletteFilter.Entry> projected) {
        Map<String, CommandPaletteFilter.Entry> byTool = new HashMap<>();
        for (CommandPaletteFilter.Entry entry : projected)
            if (entry.arguments == null && !byTool.containsKey(entry.toolName))
                byTool.put(entry.toolName, entry);

        List<CommandPaletteFilter.Entry> out = new ArrayList<>(projected.size() + 6);
        List<String> claimed = new ArrayList<>();
        for (Action action : HOME_ACTIONS) {
            String title = context.getString(action.titleRes);
            if (isHostAction(action.tool)) {
                out.add(hostRow(action.tool, title, context.getString(action.subtitleRes), null));
                continue;
            }
            CommandPaletteFilter.Entry tool = byTool.get(action.tool);
            if (tool == null) continue;
            claimed.add(action.tool);
            out.add(new CommandPaletteFilter.Entry(tool.toolName, title,
                action.subtitleRes != 0 ? context.getString(action.subtitleRes) : tool.subtitle,
                CATEGORY_HOME, tool.bindings, tool.enabled, tool.disabledReason,
                tool.requiresConfirmation, tool.risk, null, tool.argumentName,
                tool.argumentChoices));
        }
        for (CommandPaletteFilter.Entry entry : projected)
            if (entry.arguments != null || !claimed.contains(entry.toolName)) out.add(entry);
        return out;
    }

    /**
     * Home's rows in the order they browse: the Home section, the jump rows, the sessions, then the
     * remaining actions. The controller slots the Apps and Linux apps sections in after the Home
     * section when it browses.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> orderedForHome(
        @NonNull List<CommandPaletteFilter.Entry> tools,
        @NonNull List<CommandPaletteFilter.Entry> jumps,
        @NonNull List<CommandPaletteFilter.Entry> sessions) {
        List<CommandPaletteFilter.Entry> out =
            new ArrayList<>(tools.size() + jumps.size() + sessions.size());
        for (CommandPaletteFilter.Entry entry : tools)
            if (CATEGORY_HOME.equals(entry.category)) out.add(entry);
        out.addAll(jumps);
        out.addAll(sessions);
        for (CommandPaletteFilter.Entry entry : tools)
            if (!CATEGORY_HOME.equals(entry.category)) out.add(entry);
        return out;
    }

    /**
     * Whether a key typed on Home opens the palette with it: only while the palette is shut, and
     * only for text that starts with something visible — a space or a control character is the
     * caller's to handle, never the first character of a query.
     */
    static boolean opensOnTyped(boolean paletteOpen, @Nullable String text) {
        if (paletteOpen || text == null || text.isEmpty()) return false;
        int first = text.codePointAt(0);
        return !Character.isWhitespace(first) && !Character.isISOControl(first);
    }

    /**
     * A jump row per window of the current session, in its order. A window the host reports as a
     * Home float jumps there (paging Home and restoring it) and says so with its page when the host
     * knows it; any other window runs {@code window.select} with its own index, as Ctrl+Alt+digit
     * does.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> jumpEntries(
        @NonNull Context context, @NonNull List<SessionBrowserModel.Session> sessions,
        @NonNull CommandPaletteHomeHost host) {
        SessionBrowserModel.Session current = null;
        for (SessionBrowserModel.Session session : sessions)
            if (session.current) current = session;
        if (current == null || current.windows.isEmpty()) return Collections.emptyList();

        List<CommandPaletteFilter.Entry> out = new ArrayList<>(current.windows.size());
        for (SessionBrowserModel.Window window : current.windows) {
            int number = window.index + 1;
            String title = window.label != null ? window.label
                : context.getString(R.string.palette_jump_window_unnamed, number);
            if (host.isHomeFloat(window.id)) {
                int page = host.homeFloatPage(window.id);
                String subtitle = page >= 0
                    ? context.getString(R.string.palette_jump_float_page, page + 1)
                    : context.getString(R.string.palette_jump_float);
                JSONObject arguments = new JSONObject();
                try {
                    arguments.put("window_id", window.id);
                } catch (JSONException ignored) {
                }
                out.add(hostRow(TOOL_RESTORE_FLOAT, title, subtitle, arguments, CATEGORY_WINDOWS));
                continue;
            }
            JSONObject arguments = new JSONObject();
            try {
                arguments.put("index", window.index);
            } catch (JSONException ignored) {
            }
            out.add(new CommandPaletteFilter.Entry(
                LauncherToolRegistry.TOOL_WINDOW_SELECT,
                title,
                context.getString(window.current ? R.string.palette_jump_window_current
                    : R.string.palette_jump_window, number),
                CATEGORY_WINDOWS,
                Collections.<String>emptyList(),
                true,
                null,
                false,
                LauncherToolRegistry.ToolRisk.LOW,
                arguments));
        }
        return out;
    }

    /**
     * Runs a Home host row. Returns false for a row that is not one, so the caller hands it to the
     * dispatcher instead.
     */
    static boolean runHostAction(@NonNull CommandPaletteFilter.Entry entry,
                                 @NonNull CommandPaletteHomeHost host) {
        switch (entry.toolName) {
            case TOOL_OPEN_TERMINAL_HERE: host.openTerminalHere(); return true;
            case TOOL_GO_TO_WORKSPACE: host.goToWorkspace(); return true;
            case TOOL_MINIMISE_FLOATS: host.minimiseAllFloats(); return true;
            case TOOL_RESTORE_FLOATS: host.restoreFloats(); return true;
            case TOOL_ADD_WIDGET: host.addWidget(); return true;
            case TOOL_LAYOUT_EDITOR: host.openLayoutEditor(); return true;
            case TOOL_RESTORE_FLOAT: {
                long id = entry.arguments == null ? -1 : entry.arguments.optLong("window_id", -1);
                if (id >= 0) host.restoreFloat(id);
                return true;
            }
            default: return false;
        }
    }

    @NonNull
    private static CommandPaletteFilter.Entry hostRow(@NonNull String tool, @NonNull String title,
                                                      @NonNull String subtitle,
                                                      @Nullable JSONObject arguments) {
        return hostRow(tool, title, subtitle, arguments, CATEGORY_HOME);
    }

    @NonNull
    private static CommandPaletteFilter.Entry hostRow(@NonNull String tool, @NonNull String title,
                                                      @NonNull String subtitle,
                                                      @Nullable JSONObject arguments,
                                                      @NonNull String category) {
        return new CommandPaletteFilter.Entry(tool, title, subtitle, category,
            Collections.<String>emptyList(), true, null, false,
            LauncherToolRegistry.ToolRisk.LOW, arguments);
    }
}
