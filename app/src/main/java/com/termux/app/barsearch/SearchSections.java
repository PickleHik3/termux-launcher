package com.termux.app.barsearch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.LauncherEntries;
import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;

import java.util.ArrayList;
import java.util.List;

/**
 * What the control bar's search lists (#435): with nothing typed an All apps row first, then
 * Terminal, then Linux apps, then Android apps; typing filters all three with the drawer's ranking
 * and drops All apps. Pure.
 *
 * <p>The launcher's own Assistant entry stands with Terminal (it is neither a Linux nor an Android
 * app); Display stands with the Linux apps, as it does in the drawer.
 */
public final class SearchSections {

    /** The sections, in the order they are listed. */
    public enum Section { ALL_APPS, TERMINAL, LINUX, ANDROID }

    /** The fuzzy-match floor the drawer's search and the palette's Apps section use. */
    public static final int TOLERANCE = 70;

    /** One listed row: the All apps row has no entry. */
    public static final class Row {
        @NonNull public final Section section;
        @Nullable public final LauncherAppEntry entry;

        Row(@NonNull Section section, @Nullable LauncherAppEntry entry) {
            this.section = section;
            this.entry = entry;
        }
    }

    private SearchSections() {}

    /** The section an app is listed in. */
    @NonNull
    public static Section sectionOf(@NonNull LauncherAppEntry entry) {
        if (LauncherEntries.isEntry(entry.appRef)) {
            return LauncherEntries.DISPLAY.equals(LauncherEntries.idOf(entry.appRef))
                ? Section.LINUX : Section.TERMINAL;
        }
        return X11Apps.isLinuxApp(entry.appRef) ? Section.LINUX : Section.ANDROID;
    }

    /**
     * The rows for {@code query} over {@code catalogue}: sections in order, each in the ranking's
     * order (the catalogue's with nothing typed), Terminal ahead of the other launcher entries.
     */
    @NonNull
    public static List<Row> arrange(@NonNull List<LauncherAppEntry> catalogue, @NonNull String query) {
        List<LauncherAppEntry> ranked = LauncherRankingEngine.filterAndRank(catalogue, query, TOLERANCE);
        List<Row> rows = new ArrayList<>();
        if (query.trim().isEmpty()) rows.add(new Row(Section.ALL_APPS, null));
        for (Section section : new Section[] {Section.TERMINAL, Section.LINUX, Section.ANDROID}) {
            if (section == Section.TERMINAL) {
                for (LauncherAppEntry entry : ranked) {
                    if (isTerminal(entry)) rows.add(new Row(section, entry));
                }
            }
            for (LauncherAppEntry entry : ranked) {
                if (sectionOf(entry) == section && !isTerminal(entry)) rows.add(new Row(section, entry));
            }
        }
        return rows;
    }

    private static boolean isTerminal(@NonNull LauncherAppEntry entry) {
        return LauncherEntries.TERMINAL.equals(LauncherEntries.idOf(entry.appRef));
    }
}
