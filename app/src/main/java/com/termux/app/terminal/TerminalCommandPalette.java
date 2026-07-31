package com.termux.app.terminal;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Row sources for the command palette, and its entry point.
 *
 * <p>The palette itself is {@link TerminalCommandPaletteController}, an in-activity glass
 * overlay. This class stays the seam the registry calls into ({@code app.command_palette}) and
 * owns the projection from data sources to rows, so what appears in the palette is decided in
 * one place and stays unit-testable without inflating anything.
 *
 * <p>Entries come from {@link LauncherToolRegistry#getUiToolsByCategory()} and run through
 * {@link TerminalActionDispatcher}, so a palette selection and a keystroke
 * share one execution path.
 *
 * <p>Which tools appear, and how:
 *
 * <ul>
 *   <li>A tool needs a {@code titleRes}. That is what marks it user-facing, which is why
 *       {@code terminal.state} (an introspection tool) stays out.
 *   <li>A tool with no required arguments is a plain row.
 *   <li>A tool whose single required argument is a free-text string becomes an
 *       argument-mode row ({@code session.rename}); one whose single required argument is an
 *       enum becomes a submenu row ({@code pane.focus_direction}).
 *   <li>Anything else — a required integer, or several required arguments — stays out, because
 *       the palette has no way to ask for it. That still excludes {@code window.select} and
 *       {@code session.activate_by_index}, which only make sense from a directional or digit
 *       key; the Sessions rows supply that index themselves.
 * </ul>
 *
 * <p>Installed apps and live sessions are the two row sources that are not tool projections.
 * Apps are rebuilt per query — usage-ranked with no query, fuzzy-ranked with one — and each row
 * runs {@code app.launch} with its own stable id; sessions run
 * {@code session.activate_by_index} with their own index. Either way a run from here takes the
 * same path as a run from a keybind.
 */
public final class TerminalCommandPalette {

    /** Installed apps shown before the user narrows the list. */
    private static final int APP_ROWS_UNFILTERED = 8;
    /** Upper bound on ranked app matches for a query, so the list stays scannable. */
    private static final int APP_ROWS_FILTERED = 20;
    /** Minimum fuzzy score for an app row, matching the suggestion bar. */
    private static final int APP_MATCH_TOLERANCE = 70;

    /** Display-only grouping key for live sessions; not a registry category. */
    public static final String CATEGORY_SESSIONS = "sessions";

    private TerminalCommandPalette() {
    }

    /** Shows the palette. Must be called on the main thread. */
    public static void show(@NonNull TermuxActivity activity) {
        activity.getCommandPaletteController().toggle();
    }

    /**
     * Builds the palette's tool rows, grouped by category in registration order.
     * Visible for testing the inclusion rules without inflating a view.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> buildEntries(@NonNull TermuxActivity activity) {
        LauncherToolRegistry.ActionContext context =
            TerminalActionDispatcher.getInstance().actionContext();

        List<CommandPaletteFilter.Entry> entries = new ArrayList<>();
        Map<String, List<LauncherToolRegistry.ToolMetadata>> grouped =
            LauncherToolRegistry.getInstance().getUiToolsByCategory();
        for (Map.Entry<String, List<LauncherToolRegistry.ToolMetadata>> group : grouped.entrySet()) {
            for (LauncherToolRegistry.ToolMetadata tool : group.getValue()) {
                if (tool.titleRes == 0) continue;          // not user-facing
                String argumentName = promptableArgument(tool);
                if (hasRequiredArguments(tool) && argumentName == null) continue;

                LauncherToolRegistry.Availability availability = tool.availabilityIn(context);
                String reason = availability.available || availability.reasonRes == 0
                    ? null
                    : activity.getString(availability.reasonRes);

                entries.add(new CommandPaletteFilter.Entry(
                    tool.name,
                    activity.getString(tool.titleRes),
                    subtitleFor(activity, tool),
                    tool.category,
                    strokesFor(tool, context),
                    availability.available,
                    reason,
                    tool.requiresConfirmation,
                    tool.risk,
                    null,
                    argumentName,
                    argumentChoices(tool, argumentName)));
            }
        }
        return entries;
    }

    /**
     * Localized description of a row. The stroke is no longer appended: the ledger gives the
     * shortcut its own right-aligned column, so repeating it in the description would show it
     * twice on the focused row.
     */
    private static String subtitleFor(@NonNull Context context,
                                      @NonNull LauncherToolRegistry.ToolMetadata tool) {
        return tool.descriptionRes != 0
            ? context.getString(tool.descriptionRes)
            : tool.description;
    }

    /**
     * Strokes that actually apply in the current mode. A binding whose condition
     * does not hold would be a false promise on the row — Ctrl+Alt+V must not be
     * advertised next to Paste while split panes are on.
     */
    @NonNull
    static List<String> strokesFor(@NonNull LauncherToolRegistry.ToolMetadata tool,
                                   @NonNull LauncherToolRegistry.ActionContext context) {
        return TerminalKeyBindingResolver.getInstance().getStrokesForTool(tool.name, context);
    }

    /**
     * One row per live session, activating it by index. Uses the session browser's projection
     * so the palette and the sessions panel always agree on what exists.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> buildSessionEntries(@NonNull TermuxActivity activity) {
        List<SessionBrowserModel.Session> sessions = activity.getSessionBrowserSessions();
        if (sessions.isEmpty()) return Collections.emptyList();
        List<CommandPaletteFilter.Entry> entries = new ArrayList<>(sessions.size());
        for (SessionBrowserModel.Session session : sessions) {
            JSONObject arguments = new JSONObject();
            try {
                arguments.put("index", session.index);
            } catch (JSONException ignored) {
            }
            String title = session.name == null
                ? activity.getString(R.string.session_browser_unnamed, session.index + 1)
                : activity.getString(R.string.session_browser_named, session.index + 1,
                    session.name);
            entries.add(new CommandPaletteFilter.Entry(
                LauncherToolRegistry.TOOL_SESSION_ACTIVATE_BY_INDEX,
                session.current ? title + " · "
                    + activity.getString(R.string.session_browser_current) : title,
                activity.getResources().getQuantityString(R.plurals.session_browser_pane_count,
                    session.paneCount(), session.paneCount()),
                CATEGORY_SESSIONS,
                Collections.<String>emptyList(),
                true,
                null,
                false,
                LauncherToolRegistry.ToolRisk.LOW,
                arguments));
        }
        return entries;
    }

    /**
     * App rows for the current query, built from the provider's warm cache only —
     * the palette must never block the main thread on a PackageManager sweep.
     * Without a query the rows are usage-ranked, so the apps actually used land on
     * top; with one, the launcher's own fuzzy ranking decides.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> buildAppEntries(
        @NonNull LauncherAppDataProvider provider,
        @NonNull LauncherUsageStatsStore usageStats,
        @NonNull String query
    ) {
        return buildAppEntries(provider, usageStats, query, null);
    }

    /**
     * As above, and additionally fills {@code iconsOut} with the artwork for the rows it returns,
     * keyed by {@link CommandPaletteFilter.Entry#iconKey}. Only these rows carry icons, so the
     * map stays as short as the Apps section rather than the whole installed set.
     */
    @NonNull
    static List<CommandPaletteFilter.Entry> buildAppEntries(
        @NonNull LauncherAppDataProvider provider,
        @NonNull LauncherUsageStatsStore usageStats,
        @NonNull String query,
        @Nullable Map<String, Drawable> iconsOut
    ) {
        List<LauncherAppEntry> apps = provider.getAllApps();
        if (apps.isEmpty()) return Collections.emptyList();
        String trimmed = query.trim();
        List<LauncherAppEntry> ranked = trimmed.isEmpty()
            ? usageStats.rankForAz(apps)
            : LauncherRankingEngine.filterAndRank(apps, trimmed, APP_MATCH_TOLERANCE);
        int limit = Math.min(ranked.size(),
            trimmed.isEmpty() ? APP_ROWS_UNFILTERED : APP_ROWS_FILTERED);
        List<CommandPaletteFilter.Entry> entries = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            LauncherAppEntry app = ranked.get(i);
            String stableId = app.appRef.stableId();
            JSONObject arguments = new JSONObject();
            try {
                // The stable id targets one activity exactly, so a row cannot drift
                // to a different app of the same package.
                arguments.put("query", stableId);
            } catch (JSONException ignored) {
            }
            if (iconsOut != null && app.icon != null) iconsOut.put(stableId, app.icon);
            entries.add(new CommandPaletteFilter.Entry(
                LauncherToolRegistry.TOOL_APP_LAUNCH,
                app.label,
                app.appRef.packageName,
                LauncherToolRegistry.CATEGORY_APPS,
                Collections.<String>emptyList(),
                true,
                null,
                false,
                LauncherToolRegistry.ToolRisk.MEDIUM,
                arguments,
                null,
                null,
                stableId));
        }
        return entries;
    }

    static boolean hasRequiredArguments(@NonNull LauncherToolRegistry.ToolMetadata tool) {
        JSONArray required = tool.schema.optJSONArray("required");
        return required != null && required.length() > 0;
    }

    /**
     * The single string argument this tool can be asked for in the palette, or {@code null}
     * when it takes none or takes something the palette cannot prompt for.
     *
     * <p>{@code app.launch} is excluded on purpose: its required query is already supplied per
     * row by the Apps section, so an extra "type a package name" row would be noise.
     */
    @Nullable
    static String promptableArgument(@NonNull LauncherToolRegistry.ToolMetadata tool) {
        if (LauncherToolRegistry.TOOL_APP_LAUNCH.equals(tool.name)) return null;
        JSONArray required = tool.schema.optJSONArray("required");
        if (required == null || required.length() != 1) return null;
        String name = required.optString(0, "");
        if (name.isEmpty()) return null;
        JSONObject properties = tool.schema.optJSONObject("properties");
        JSONObject property = properties == null ? null : properties.optJSONObject(name);
        if (property == null) return null;
        return "string".equals(property.optString("type")) ? name : null;
    }

    /** Allowed values for a promptable argument, or {@code null} when it is free text. */
    @Nullable
    static List<String> argumentChoices(@NonNull LauncherToolRegistry.ToolMetadata tool,
                                        @Nullable String argumentName) {
        if (argumentName == null) return null;
        JSONObject properties = tool.schema.optJSONObject("properties");
        JSONObject property = properties == null ? null : properties.optJSONObject(argumentName);
        JSONArray values = property == null ? null : property.optJSONArray("enum");
        if (values == null || values.length() == 0) return null;
        List<String> choices = new ArrayList<>(values.length());
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) choices.add(value);
        }
        return choices.isEmpty() ? null : choices;
    }

    @NonNull
    static String categoryLabel(@NonNull Context context, @NonNull String category) {
        switch (category) {
            case LauncherToolRegistry.CATEGORY_SESSION: return context.getString(R.string.palette_category_session);
            case LauncherToolRegistry.CATEGORY_WINDOW: return context.getString(R.string.palette_category_window);
            case LauncherToolRegistry.CATEGORY_PANE: return context.getString(R.string.palette_category_pane);
            case LauncherToolRegistry.CATEGORY_TERMINAL: return context.getString(R.string.palette_category_terminal);
            case LauncherToolRegistry.CATEGORY_CLIPBOARD: return context.getString(R.string.palette_category_clipboard);
            case LauncherToolRegistry.CATEGORY_APPEARANCE: return context.getString(R.string.palette_category_appearance);
            case LauncherToolRegistry.CATEGORY_APP: return context.getString(R.string.palette_category_app);
            case LauncherToolRegistry.CATEGORY_APPS: return context.getString(R.string.palette_category_apps);
            case CATEGORY_SESSIONS: return context.getString(R.string.palette_category_sessions);
            default: return category;
        }
    }
}
