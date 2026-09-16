package com.termux.app.help;

import com.termux.R;
import com.termux.app.wall.PaneWallPage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every control help explains, once: the topic chooser and the all-controls overview both read
 * this list, so nothing is described in two places. Pure — string resources are carried as ids
 * and resolved by whoever draws them.
 *
 * <p>An entry's {@link Entry#id} is the id {@code HelpTargets} measures the control under, so a
 * topic and its box are the same thing to the renderer. Ids repeat across places (every place has
 * a status bar), so a lookup always names the place.
 */
public final class HelpTopics {
    private HelpTopics() {}

    /** How visibly optional a topic is: the chooser lists Everyday first and last. */
    public enum Group {
        EVERYDAY(R.string.help_topic_group_everyday),
        KEYBOARD(R.string.help_topic_group_keyboard),
        MULTITASKING(R.string.help_topic_group_multitasking);

        /** The section label shown over this group. */
        public final int labelRes;
        Group(int labelRes) { this.labelRes = labelRes; }
    }

    /** Tap a corner, then ? — the lesson that teaches where help lives. */
    public static final String LESSON_FIND_HELP = "find_help";
    /** Pull down the dock, open an app, come back. */
    public static final String LESSON_FIND_APPS = "find_apps";
    /** Show and hide the keyboard with the keyboard button. */
    public static final String LESSON_KEYBOARD = "keyboard";
    /** Open the command palette and pick an action. */
    public static final String LESSON_FIND_ACTION = "find_action";
    /** The only lessons a topic may hand practice to. */
    public static final List<String> LESSON_IDS = Collections.unmodifiableList(Arrays.asList(
        LESSON_FIND_HELP, LESSON_FIND_APPS, LESSON_KEYBOARD, LESSON_FIND_ACTION));

    /** One control, and everything help knows to say about it. */
    public static final class Entry {
        /** Stable within its place, and the id its control is measured under. */
        public final String id;
        public final PaneWallPage place;
        public final Group group;
        /** The control's name, the same in the chooser and on its card whatever else is on screen. */
        public final int titleRes;
        /** What the thing is, before any gesture. */
        public final int purposeRes;
        /** How to use it. */
        public final int actionRes;
        /** How to bring the control back when it is not on screen. */
        public final int revealRes;
        /** A lesson from {@link #LESSON_IDS}, or null when this topic has nothing to practise. */
        public final String lessonId;
        /** Another topic of the same place worth reading when this control is away, or null. */
        public final String relatedId;
        /** This entry's place in its place's full catalogue, so its colour never moves. */
        public final int identityIndex;

        Entry(String id, PaneWallPage place, Group group, int titleRes, int purposeRes,
              int actionRes, int revealRes, String lessonId, String relatedId, int identityIndex) {
            this.id = id;
            this.place = place;
            this.group = group;
            this.titleRes = titleRes;
            this.purposeRes = purposeRes;
            this.actionRes = actionRes;
            this.revealRes = revealRes;
            this.lessonId = lessonId;
            this.relatedId = relatedId;
            this.identityIndex = identityIndex;
        }

        @Override public String toString() { return place + "/" + id; }
    }

    private static final Map<PaneWallPage, List<Entry>> BY_PLACE = build();

    /** The place's whole catalogue, in chooser order: Everyday, then Keyboard, then Multitasking. */
    public static List<Entry> forPlace(PaneWallPage place) {
        List<Entry> entries = BY_PLACE.get(place);
        return entries == null ? Collections.<Entry>emptyList() : entries;
    }

    /** The entry with this id on this place, or null. */
    public static Entry entry(PaneWallPage place, String id) {
        if (id == null) return null;
        for (Entry entry : forPlace(place)) if (entry.id.equals(id)) return entry;
        return null;
    }

    /** How many entries the place has, measurable or not — the count colours are spread over. */
    public static int sizeFor(PaneWallPage place) { return forPlace(place).size(); }

    /**
     * Topics the chooser offers but the overview leaves out: the extra keys row keeps its per-key
     * labels and no box of its own, and the corner tab is the thing the reader opened help from.
     */
    public static boolean topicOnly(String id) {
        return "keys".equals(id) || "corners".equals(id);
    }

    /** Every entry of every place. */
    public static List<Entry> all() {
        List<Entry> all = new ArrayList<>();
        for (PaneWallPage place : PaneWallPage.values()) all.addAll(forPlace(place));
        return Collections.unmodifiableList(all);
    }

    private static Map<PaneWallPage, List<Entry>> build() {
        Map<PaneWallPage, List<Entry>> map = new EnumMap<>(PaneWallPage.class);
        Builder terminal = new Builder(PaneWallPage.TERMINAL);
        terminal.add("dock", Group.EVERYDAY, R.string.help_dock_title, R.string.help_topic_dock_purpose,
            R.string.help_topic_dock_action, R.string.help_topic_dock_reveal, LESSON_FIND_APPS, null);
        terminal.add("az", Group.EVERYDAY, R.string.help_az_title, R.string.help_topic_az_purpose,
            R.string.help_topic_az_action, R.string.help_topic_az_reveal, null, "dock");
        terminal.add("status", Group.EVERYDAY, R.string.help_status_title, R.string.help_topic_status_purpose,
            R.string.help_topic_status_action, R.string.help_topic_status_reveal, null, null);
        terminal.add("stats", Group.EVERYDAY, R.string.help_topic_stats_title, R.string.help_topic_stats_purpose,
            R.string.help_topic_stats_action, R.string.help_topic_stats_reveal, null, null);
        terminal.add("terminal", Group.EVERYDAY, R.string.help_topic_terminal_title, R.string.help_topic_terminal_purpose,
            R.string.help_topic_terminal_action, R.string.help_topic_terminal_reveal, null, "windows");
        terminal.add("corners", Group.EVERYDAY, R.string.help_topic_corners_title, R.string.help_topic_corners_purpose,
            R.string.help_topic_corners_action, R.string.help_topic_corners_reveal, LESSON_FIND_HELP, null);
        terminal.add("sessions", Group.EVERYDAY, R.string.help_sessions_title, R.string.help_topic_sessions_purpose,
            R.string.help_topic_sessions_action, R.string.help_topic_sessions_reveal, null, "windows");
        terminal.add("windows", Group.EVERYDAY, R.string.help_windows_title, R.string.help_topic_windows_purpose,
            R.string.help_topic_windows_action, R.string.help_topic_windows_reveal, null, "sessions");
        terminal.add("keys", Group.KEYBOARD, R.string.help_topic_keys_title, R.string.help_topic_keys_purpose,
            R.string.help_topic_keys_action, R.string.help_topic_keys_reveal, LESSON_KEYBOARD, null);
        terminal.add("prefix", Group.KEYBOARD, R.string.help_prefix_title, R.string.help_topic_prefix_purpose,
            R.string.help_topic_prefix_action, R.string.help_topic_prefix_reveal, null, "shortcuts");
        terminal.add("space", Group.KEYBOARD, R.string.help_space_title, R.string.help_topic_space_purpose,
            R.string.help_topic_space_action, R.string.help_topic_space_reveal, LESSON_FIND_ACTION, null);
        terminal.add("settings", Group.KEYBOARD, R.string.help_launcher_settings_title, R.string.help_topic_settings_purpose,
            R.string.help_topic_settings_action, R.string.help_topic_settings_reveal, null, "space");
        terminal.add("divider", Group.MULTITASKING, R.string.help_divider_title, R.string.help_topic_divider_purpose,
            R.string.help_topic_divider_action, R.string.help_topic_divider_reveal, null, "shortcuts");
        terminal.add("shortcuts", Group.MULTITASKING, R.string.help_topic_shortcuts_title, R.string.help_topic_shortcuts_purpose,
            R.string.help_topic_shortcuts_action, R.string.help_topic_shortcuts_reveal, null, "prefix");
        map.put(PaneWallPage.TERMINAL, terminal.done());

        Builder display = new Builder(PaneWallPage.DISPLAY);
        display.add("status", Group.EVERYDAY, R.string.help_status_title, R.string.help_topic_status_purpose,
            R.string.help_topic_status_action, R.string.help_topic_status_reveal, null, null);
        display.add("stats", Group.EVERYDAY, R.string.help_topic_stats_title, R.string.help_topic_stats_purpose,
            R.string.help_topic_stats_action, R.string.help_topic_stats_reveal, null, null);
        display.add("windows", Group.EVERYDAY, R.string.help_display_apps_title, R.string.help_topic_display_apps_purpose,
            R.string.help_topic_display_apps_action, R.string.help_topic_display_apps_reveal, null, "start");
        display.add("start", Group.EVERYDAY, R.string.help_start_title, R.string.help_topic_start_purpose,
            R.string.help_topic_start_action, R.string.help_topic_start_reveal, null, "windows");
        display.add("scale", Group.EVERYDAY, R.string.help_scale_title, R.string.help_topic_scale_purpose,
            R.string.help_topic_scale_action, R.string.help_topic_scale_reveal, null, "start");
        display.add("touchpad", Group.EVERYDAY, R.string.help_pad_title, R.string.help_topic_touchpad_purpose,
            R.string.help_topic_touchpad_action, R.string.help_topic_touchpad_reveal, null, null);
        display.add("settings", Group.KEYBOARD, R.string.help_launcher_settings_title, R.string.help_topic_settings_purpose,
            R.string.help_topic_settings_action, R.string.help_topic_settings_reveal, null, null);
        map.put(PaneWallPage.DISPLAY, display.done());

        Builder home = new Builder(PaneWallPage.WIDGETS);
        home.add("status", Group.EVERYDAY, R.string.help_status_title, R.string.help_topic_status_purpose,
            R.string.help_topic_status_action, R.string.help_topic_status_reveal, null, null);
        home.add("widget", Group.EVERYDAY, R.string.help_widget_title, R.string.help_topic_widget_purpose,
            R.string.help_topic_widget_action, R.string.help_topic_widget_reveal, null, "empty");
        home.add("empty", Group.EVERYDAY, R.string.help_empty_title, R.string.help_topic_empty_purpose,
            R.string.help_topic_empty_action, R.string.help_topic_empty_reveal, null, "widget");
        home.add("settings", Group.KEYBOARD, R.string.help_launcher_settings_title, R.string.help_topic_settings_purpose,
            R.string.help_topic_settings_action, R.string.help_topic_settings_reveal, null, null);
        map.put(PaneWallPage.WIDGETS, home.done());
        return Collections.unmodifiableMap(map);
    }

    private static final class Builder {
        private final PaneWallPage place;
        private final List<Entry> entries = new ArrayList<>();
        Builder(PaneWallPage place) { this.place = place; }
        void add(String id, Group group, int title, int purpose, int action, int reveal,
                 String lessonId, String relatedId) {
            entries.add(new Entry(id, place, group, title, purpose, action, reveal,
                lessonId, relatedId, entries.size()));
        }
        List<Entry> done() { return Collections.unmodifiableList(entries); }
    }
}
