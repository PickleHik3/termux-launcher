package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.LauncherCtlNotificationEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the Notifications widget shows, cut from notification rows: one entry per notification
 * still in the shade, newest first, from the last day, without the system's own and the silent
 * ones. A notification that carries several messages arrives as several rows sharing a key; the
 * newest of them stands for it in {@link Snapshot#items}, and every one counts in the
 * {@link Snapshot#groups}, which put messages and people first. Pure, so the selection is tested
 * without a listener.
 */
public final class NotificationsWidgetData {
    /** How far back the widget looks. */
    public static final long WINDOW_MS = 24L * 60L * 60L * 1000L;

    /** The categories the system posts about itself: left out of the widget. */
    private static final Set<String> SYSTEM_CATEGORIES = new HashSet<>(Arrays.asList(
        "sys", "service", "err", "status", "progress", "transport"));

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
        /** Whether it is a message from a person: a sender is named, or the app says it is one. */
        public final boolean person;

        public Item(@NonNull String key, @NonNull String packageName, @NonNull String app,
                    @NonNull String who, @NonNull String text, long time,
                    @Nullable String glyph, int glyphColor) {
            this(key, packageName, app, who, text, time, glyph, glyphColor, false);
        }

        public Item(@NonNull String key, @NonNull String packageName, @NonNull String app,
                    @NonNull String who, @NonNull String text, long time,
                    @Nullable String glyph, int glyphColor, boolean person) {
            this.key = key; this.packageName = packageName; this.app = app; this.who = who;
            this.text = text; this.time = time; this.glyph = glyph; this.glyphColor = glyphColor;
            this.person = person;
        }

        boolean sameAs(@NonNull Item other) {
            return key.equals(other.key) && time == other.time && who.equals(other.who)
                && text.equals(other.text) && app.equals(other.app);
        }
    }

    /**
     * The notifications of one app, or of one person in an app, as one row: the newest of them
     * and how many there are.
     */
    public static final class Group {
        @NonNull public final Item newest;
        public final int count;

        Group(@NonNull Item newest, int count) { this.newest = newest; this.count = count; }

        public boolean person() { return newest.person; }

        boolean sameAs(@NonNull Group other) {
            return count == other.count && newest.sameAs(other.newest);
        }
    }

    /** The widget's whole state while access is on. */
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(Collections.emptyList(), 0);

        /** Every notification, newest first. */
        @NonNull public final List<Item> items;
        /** How many apps they come from. */
        public final int apps;
        /** The rows of the layouts: people first, then apps, each newest first. */
        @NonNull public final List<Group> groups;

        public Snapshot(@NonNull List<Item> items, int apps) {
            this(items, apps, group(items));
        }

        Snapshot(@NonNull List<Item> items, int apps, @NonNull List<Group> groups) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.apps = apps;
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        }

        public int count() { return items.size(); }

        /** How many notifications the groups hold between them. */
        public int groupedCount() {
            int total = 0;
            for (Group group : groups) total += group.count;
            return total;
        }

        @Nullable public Item newest() { return items.isEmpty() ? null : items.get(0); }

        /** Distinct packages after the newest one's, in order. */
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

        /** The packages whose icons the layouts can show, in the groups' order. */
        @NonNull public List<String> iconPackages(int max) {
            Set<String> packages = new LinkedHashSet<>();
            for (Group group : groups) {
                if (packages.size() >= max) break;
                packages.add(group.newest.packageName);
            }
            return new ArrayList<>(packages);
        }

        /** Whether redrawing for {@code other} would change nothing. */
        public boolean sameAs(@Nullable Snapshot other) {
            if (other == null || other.apps != apps || other.items.size() != items.size()
                || other.groups.size() != groups.size()) return false;
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).sameAs(other.items.get(i))) return false;
            }
            for (int i = 0; i < groups.size(); i++) {
                if (!groups.get(i).sameAs(other.groups.get(i))) return false;
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
        return select(rows, nowMs, limit, Collections.emptySet());
    }

    /**
     * As {@link #select(List, long, int)}, without the system's own notifications and without
     * those whose key is in {@code silentKeys}: the ones the shade shows with no sound and no
     * peek. Several messages of one notification stay several for the groups to count.
     */
    @NonNull
    public static Snapshot select(@NonNull List<LauncherCtlNotificationEvent> rows, long nowMs,
                                  int limit, @NonNull Set<String> silentKeys) {
        List<LauncherCtlNotificationEvent> sorted = new ArrayList<>(rows.size());
        for (LauncherCtlNotificationEvent row : rows) {
            if (row != null && row.key != null && row.packageName != null && !isSystem(row)
                && !silentKeys.contains(row.key)) {
                sorted.add(row);
            }
        }
        sorted.sort((a, b) -> Long.compare(b.messageTime, a.messageTime));
        long since = nowMs - WINDOW_MS;
        Set<String> keys = new HashSet<>();
        Set<String> packages = new HashSet<>();
        List<Item> items = new ArrayList<>();
        List<Item> every = new ArrayList<>();
        for (LauncherCtlNotificationEvent row : sorted) {
            if (row.removedTime != 0 || row.messageTime < since) continue;
            if (every.size() >= limit) break;
            Item item = toItem(row);
            every.add(item);
            if (!keys.add(row.key)) continue;
            packages.add(row.packageName);
            items.add(item);
        }
        return new Snapshot(items, packages.size(), group(every));
    }

    /** Whether the system posted {@code row} about itself rather than an app about its user. */
    static boolean isSystem(@NonNull LauncherCtlNotificationEvent row) {
        String pkg = row.packageName;
        return pkg.equals("android") || pkg.equals("com.android.systemui")
            || pkg.startsWith("com.android.providers.")
            || (row.category != null && SYSTEM_CATEGORIES.contains(row.category));
    }

    /**
     * {@code newestFirst} grouped into rows: one person's messages in one app are a row, every
     * other notification of an app is a row. People come first, then apps; each side is newest
     * first.
     */
    @NonNull
    static List<Group> group(@NonNull List<Item> newestFirst) {
        Map<String, Item> newest = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Item item : newestFirst) {
            String id = item.packageName + (item.person ? "\u0000" + item.who : "");
            if (!newest.containsKey(id)) newest.put(id, item);
            Integer seen = counts.get(id);
            counts.put(id, seen == null ? 1 : seen + 1);
        }
        List<Group> people = new ArrayList<>();
        List<Group> others = new ArrayList<>();
        for (Map.Entry<String, Item> entry : newest.entrySet()) {
            Group group = new Group(entry.getValue(), counts.get(entry.getKey()));
            (group.person() ? people : others).add(group);
        }
        people.addAll(others);
        return people;
    }

    @NonNull
    static Item toItem(@NonNull LauncherCtlNotificationEvent row) {
        String app = firstNonBlank(row.appLabel, row.packageName);
        String who = firstNonBlank(row.sender, row.title, row.conversation, app);
        String text = oneLine(firstNonBlank(row.text, ""));
        boolean person = !firstNonBlank(row.sender).isEmpty() || "msg".equals(row.category);
        return new Item(row.key, row.packageName, app, oneLine(who), text, row.messageTime, null, 0,
            person);
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
