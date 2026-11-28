package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.tiling.desktop.AppOpen;
import com.termux.app.x11.X11Apps;
import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The rows the palette leads with when the Terl control bar's search opens it
 * ({@link CommandPaletteContext#CONTROL_BAR}, #435): with nothing typed an All apps row, then
 * Terminal (Terminal itself, then the launcher's Assistant), Linux apps (Display among them, as in
 * the drawer) and Android apps; typing filters all three with the drawer's ranking, drops All apps,
 * and offers Edit look for "edit" or "appearance". Each app row says at its end what a tap does
 * ({@link AppOpen.Hint}), and a tap opens the app as the desktop does ({@link AppOpen}): the open
 * window when there is one.
 *
 * <p>The arrangement is pure; the projection to palette rows reads only strings.
 */
final class CommandPaletteTerlSet {

    /** Display-only grouping keys, not registry categories. */
    static final String CATEGORY_LEAD = "terl";
    static final String CATEGORY_TERMINAL = "terl_terminal";
    static final String CATEGORY_ANDROID_APPS = "android_apps";

    /** Rows the Terl host runs, under names the registry does not know. */
    static final String TOOL_ALL_APPS = "terl.all_apps";
    static final String TOOL_EDIT_LOOK = "terl.edit_look";

    /** The fuzzy-match floor the drawer's search and the palette's Apps section use. */
    static final int TOLERANCE = 70;

    /** The sections, in the order they are listed. */
    enum Section {
        EDIT_LOOK(false), ALL_APPS(false), TERMINAL(true), LINUX(true), ANDROID(true);

        /** Whether the section is introduced by a header (the app sections are). */
        final boolean headed;

        Section(boolean headed) {
            this.headed = headed;
        }
    }

    /** One listed row: Edit look and All apps have no app. */
    static final class Row {
        @NonNull final Section section;
        @Nullable final LauncherAppEntry entry;

        Row(@NonNull Section section, @Nullable LauncherAppEntry entry) {
            this.section = section;
            this.entry = entry;
        }
    }

    private CommandPaletteTerlSet() {}

    /** The section an app is listed in. */
    @NonNull
    static Section sectionOf(@NonNull LauncherAppEntry entry) {
        if (LauncherEntries.isEntry(entry.appRef)) {
            return LauncherEntries.DISPLAY.equals(LauncherEntries.idOf(entry.appRef))
                ? Section.LINUX : Section.TERMINAL;
        }
        return X11Apps.isLinuxApp(entry.appRef) ? Section.LINUX : Section.ANDROID;
    }

    /**
     * Whether typing {@code query} offers the Edit look row (#442): at least two letters starting
     * "edit" or "appearance" (so "ed", "edit", "appear" do, "editor" and "dit" do not).
     */
    static boolean offersEditLook(@NonNull String query) {
        String typed = query.trim().toLowerCase(Locale.ROOT);
        return typed.length() >= 2 && ("edit".startsWith(typed) || "appearance".startsWith(typed));
    }

    /**
     * The rows for {@code query} over {@code apps}: Edit look when offered, All apps with nothing
     * typed, then the sections in order. With nothing typed each section keeps {@code apps}' order
     * (the caller's usage ranking); typed, the drawer's ranking decides. Terminal leads its
     * section; the Linux and Android sections hold at most {@code limit} rows each.
     */
    @NonNull
    static List<Row> arrange(@NonNull List<LauncherAppEntry> apps, @NonNull String query,
                             boolean editLook, int limit) {
        String typed = query.trim();
        List<LauncherAppEntry> ranked = typed.isEmpty() ? apps
            : LauncherRankingEngine.filterAndRank(apps, typed, TOLERANCE);
        List<Row> rows = new ArrayList<>();
        if (editLook && offersEditLook(typed)) rows.add(new Row(Section.EDIT_LOOK, null));
        if (typed.isEmpty()) rows.add(new Row(Section.ALL_APPS, null));
        for (LauncherAppEntry entry : ranked)
            if (isTerminal(entry)) rows.add(new Row(Section.TERMINAL, entry));
        for (LauncherAppEntry entry : ranked)
            if (sectionOf(entry) == Section.TERMINAL && !isTerminal(entry))
                rows.add(new Row(Section.TERMINAL, entry));
        for (Section section : new Section[] {Section.LINUX, Section.ANDROID}) {
            int count = 0;
            for (LauncherAppEntry entry : ranked) {
                if (count >= limit) break;
                if (sectionOf(entry) != section) continue;
                rows.add(new Row(section, entry));
                count++;
            }
        }
        return rows;
    }

    /** What a tap on {@code row} does to a window, as its end says it. */
    @NonNull
    static AppOpen.Hint hintOf(@NonNull Row row, @NonNull WindowOpen windowOpen) {
        if (row.section == Section.EDIT_LOOK) return AppOpen.Hint.NONE;
        if (row.entry == null) return AppOpen.hint(true, AppOpen.Action.NONE);
        AppOpen.Target target = AppOpen.targetOf(row.entry.appRef);
        return AppOpen.hint(false, AppOpen.tap(target, windowOpen.test(target, row.entry)));
    }

    /** Whether a window of an app is open on the desktop. */
    interface WindowOpen {
        boolean test(@NonNull AppOpen.Target target, @NonNull LauncherAppEntry entry);
    }

    /** The words a row's end says for {@code hint}; empty for none. */
    @NonNull
    static String hintText(@NonNull Context context, @NonNull AppOpen.Hint hint) {
        switch (hint) {
            case OPEN_DRAWER: return context.getString(R.string.palette_terl_hint_open_drawer);
            case NEW_WINDOW: return context.getString(R.string.palette_terl_hint_new_window);
            case OPEN: return context.getString(R.string.palette_terl_hint_open);
            default: return "";
        }
    }

    /** The category label a section's header reads, upper-cased by the caller. */
    @StringRes
    static int headerOf(@NonNull Section section) {
        switch (section) {
            case TERMINAL: return R.string.palette_category_terl_terminal;
            case LINUX: return R.string.palette_category_linux_apps;
            default: return R.string.palette_category_android_apps;
        }
    }

    @NonNull
    static String categoryOf(@NonNull Section section) {
        switch (section) {
            case TERMINAL: return CATEGORY_TERMINAL;
            case LINUX: return CommandPaletteHomeSet.CATEGORY_LINUX_APPS;
            case ANDROID: return CATEGORY_ANDROID_APPS;
            default: return CATEGORY_LEAD;
        }
    }

    /**
     * {@code row} as a palette row ending in {@code hint}. An app row is an app row in every other
     * way ({@code app.launch} by stable id, its icon, its bound chord, held to bind a key); only
     * its tap goes through the Terl host ({@link #runHostRow}).
     */
    @NonNull
    static CommandPaletteFilter.Entry entryFor(@NonNull Context context, @NonNull Row row,
                                               @NonNull String hint,
                                               @NonNull Map<String, String> shortcuts) {
        if (row.section == Section.EDIT_LOOK)
            return hostRow(TOOL_EDIT_LOOK, context.getString(R.string.palette_terl_edit_look),
                context.getString(R.string.palette_terl_edit_look_hint), hint);
        if (row.entry == null)
            return hostRow(TOOL_ALL_APPS, context.getString(R.string.palette_terl_all_apps),
                context.getString(R.string.palette_terl_all_apps_hint), hint);
        LauncherAppEntry app = row.entry;
        String stableId = app.appRef.stableId();
        JSONObject arguments = new JSONObject();
        try {
            arguments.put("query", stableId);
        } catch (JSONException ignored) {
        }
        String subtitle;
        if (LauncherEntries.isEntry(app.appRef))
            subtitle = context.getString(R.string.palette_launcher_entry_row_subtitle);
        else if (row.section == Section.LINUX)
            subtitle = context.getString(R.string.palette_linux_app_row_subtitle);
        else subtitle = context.getString(R.string.palette_app_row_subtitle, app.appRef.packageName);
        String stroke = shortcuts.get(stableId);
        return new CommandPaletteFilter.Entry(LauncherToolRegistry.TOOL_APP_LAUNCH,
            app.label == null ? "" : app.label, subtitle, categoryOf(row.section),
            stroke == null ? Collections.<String>emptyList() : Collections.singletonList(stroke),
            true, null, false, LauncherToolRegistry.ToolRisk.MEDIUM, arguments, null, null,
            stableId).withHint(hint);
    }

    @NonNull
    private static CommandPaletteFilter.Entry hostRow(@NonNull String tool, @NonNull String title,
                                                      @NonNull String subtitle,
                                                      @NonNull String hint) {
        return new CommandPaletteFilter.Entry(tool, title, subtitle, CATEGORY_LEAD,
            Collections.<String>emptyList(), true, null, false,
            LauncherToolRegistry.ToolRisk.LOW, null).withHint(hint);
    }

    /** Whether {@code toolName} is a row only the Terl host runs. */
    static boolean isHostRow(@NonNull String toolName) {
        return TOOL_ALL_APPS.equals(toolName) || TOOL_EDIT_LOOK.equals(toolName);
    }

    /** Finds the app a row's stable id names, or null. */
    interface AppLookup {
        @Nullable LauncherAppEntry find(@NonNull String stableId);
    }

    /**
     * Runs a row the Terl host owns: Edit look, All apps, and every app row (the bar's search and
     * its strip open an app as the desktop does). False for any other row, and for an app the
     * catalogue no longer has, which the caller hands to the dispatcher as before.
     */
    static boolean runHostRow(@NonNull CommandPaletteFilter.Entry entry,
                              @NonNull CommandPaletteTerlHost host, @NonNull AppLookup apps) {
        if (TOOL_EDIT_LOOK.equals(entry.toolName)) {
            host.openEditMode();
            return true;
        }
        if (TOOL_ALL_APPS.equals(entry.toolName)) {
            host.openDrawer();
            return true;
        }
        if (!entry.isAppRow() || entry.arguments == null) return false;
        String stableId = entry.arguments.optString("query", "");
        LauncherAppEntry app = stableId.isEmpty() ? null : apps.find(stableId);
        if (app == null) return false;
        host.open(app);
        return true;
    }

    private static boolean isTerminal(@NonNull LauncherAppEntry entry) {
        return LauncherEntries.TERMINAL.equals(LauncherEntries.idOf(entry.appRef));
    }
}
