package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.LauncherCtlNotificationEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the Notifications widget shows, cut from notification rows: one entry per notification
 * still in the shade, newest first, from the last day. A notification that carries several
 * messages arrives as several rows sharing a key; the newest of them stands for it. Pure, so the
 * selection is tested without a listener.
 */
public final class NotificationsWidgetData {
    /** How far back the widget looks. */
    public static final long WINDOW_MS = 24L * 60L * 60L * 1000L;

    /** One notification as a row of the widget. */
    public static final class Item {
        @NonNull public final String key;
        @NonNull public final String packageName;
        @NonNull public final String app;
        @NonNull public final String who;
        @NonNull public final String text;
        public final long time;
        /** A Nerd glyph drawn in place of the app icon; only the picker's sample sets it. */
        @Nullable public final String glyph;
        public final int glyphColor;

        public Item(@NonNull String key, @NonNull String packageName, @NonNull String app,
                    @NonNull String who, @NonNull String text, long time,
                    @Nullable String glyph, int glyphColor) {
            this.key = key; this.packageName = packageName; this.app = app; this.who = who;
            this.text = text; this.time = time; this.glyph = glyph; this.glyphColor = glyphColor;
        }

        boolean sameAs(@NonNull Item other) {
            return key.equals(other.key) && time == other.time && who.equals(other.who)
                && text.equals(other.text) && app.equals(other.app);
        }
    }

    /** The widget's whole state while access is on. */
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(Collections.emptyList(), 0);

        /** Every notification, newest first. */
        @NonNull public final List<Item> items;
        /** How many apps they come from. */
        public final int apps;

        public Snapshot(@NonNull List<Item> items, int apps) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.apps = apps;
        }

        public int count() { return items.size(); }

        @Nullable public Item newest() { return items.isEmpty() ? null : items.get(0); }

        /** Distinct packages after the newest one's, in order: the stacked icons of the 4×1. */
        @NonNull public List<Item> otherApps(int max) {
            List<Item> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Item first = newest();
            if (first != null) seen.add(first.packageName);
            for (Item item : items) {
                if (out.size() >= max) break;
                if (seen.add(item.packageName)) out.add(item);
            }
            return out;
        }

        /** The packages whose icons the layouts can show, newest first. */
        @NonNull public List<String> iconPackages(int max) {
            Set<String> packages = new LinkedHashSet<>();
            for (Item item : items) {
                if (packages.size() >= max) break;
                packages.add(item.packageName);
            }
            return new ArrayList<>(packages);
        }

        /** Whether redrawing for {@code other} would change nothing. */
        public boolean sameAs(@Nullable Snapshot other) {
            if (other == null || other.apps != apps || other.items.size() != items.size()) return false;
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).sameAs(other.items.get(i))) return false;
            }
            return true;
        }
    }

    private NotificationsWidgetData() { }

    /**
     * The snapshot for {@code rows} (any order) at {@code nowMs}: rows from the last
     * {@link #WINDOW_MS} whose notification is still posted, newest per key, newest first, at
     * most {@code limit} of them.
     */
    @NonNull
    public static Snapshot select(@NonNull List<LauncherCtlNotificationEvent> rows, long nowMs,
                                  int limit) {
        List<LauncherCtlNotificationEvent> sorted = new ArrayList<>(rows.size());
        for (LauncherCtlNotificationEvent row : rows) {
            if (row != null && row.key != null && row.packageName != null) sorted.add(row);
        }
        sorted.sort((a, b) -> Long.compare(b.messageTime, a.messageTime));
        long since = nowMs - WINDOW_MS;
        Set<String> keys = new HashSet<>();
        Set<String> packages = new HashSet<>();
        List<Item> items = new ArrayList<>();
        for (LauncherCtlNotificationEvent row : sorted) {
            if (row.removedTime != 0 || row.messageTime < since) continue;
            if (items.size() >= limit) break;
            if (!keys.add(row.key)) continue;
            packages.add(row.packageName);
            items.add(toItem(row));
        }
        return new Snapshot(items, packages.size());
    }

    @NonNull
    static Item toItem(@NonNull LauncherCtlNotificationEvent row) {
        String app = firstNonBlank(row.appLabel, row.packageName);
        String who = firstNonBlank(row.sender, row.title, row.conversation, app);
        String text = oneLine(firstNonBlank(row.text, ""));
        return new Item(row.key, row.packageName, app, oneLine(who), text, row.messageTime, null, 0);
    }

    @NonNull
    private static String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    @NonNull
    private static String oneLine(@NonNull String value) {
        return value.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
    }
}
