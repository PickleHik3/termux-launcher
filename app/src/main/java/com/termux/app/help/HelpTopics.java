package com.termux.app.help;

import com.termux.R;
import com.termux.app.tour.TourGesture;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The help centre's whole catalogue, once: home, search, the guide, the glossary's topic links and
 * the screen explorer all read these entries, so nothing is described in two places.
 *
 * <p>A topic is global — "Copy and paste" is the same topic wherever the reader opened help from.
 * What varies per place is only where a topic's <em>control</em> is: {@link Entry#targetId} names
 * the id {@code HelpTargets} measures that control under, and {@link Entry#places} says where that
 * control exists at all. A topic with no control ({@code targetId == null}) is read, never pointed
 * at, and is never hidden for it.
 *
 * <p>Pure: string resources are carried as ids and resolved by whoever draws them.
 */
public final class HelpTopics {
    private HelpTopics() {}

    /** Resolves a string resource, so the pure model can be read in a plain JUnit test. */
    public interface Text {
        String get(int res);
    }

    /** The seven sections of the guide; the last section is read as two shelves. */
    public enum Group {
        FIND_YOUR_WAY(R.string.help_group_find_your_way, R.string.help_group_find_your_way),
        APPS(R.string.help_group_apps, R.string.help_group_apps),
        TERMINAL(R.string.help_group_terminal, R.string.help_group_terminal),
        KEYBOARD(R.string.help_group_keyboard, R.string.help_group_keyboard),
        MULTITASKING(R.string.help_group_multitasking, R.string.help_group_multitasking),
        YOURS(R.string.help_group_yours, R.string.help_group_yours),
        DISPLAY(R.string.help_group_display, R.string.help_group_display_section),
        MISSING(R.string.help_group_missing, R.string.help_group_display_section);

        /** The label over this group's own list. */
        public final int labelRes;
        /** The section heading the group belongs under; the last two share one. */
        public final int sectionRes;

        Group(int labelRes, int sectionRes) {
            this.labelRes = labelRes;
            this.sectionRes = sectionRes;
        }
    }

    /** Whether a topic explains how something works, or repairs something the reader has lost. */
    public enum Kind { GUIDE, FIX }

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

    /** One topic, and everything help knows to say about it. */
    public static final class Entry {
        /** Stable and unique across the whole catalogue. */
        public final String id;
        public final Group group;
        public final Kind kind;
        /** A short name, the same in every list and at the top of the topic. */
        public final int titleRes;
        /** One sentence: what this is. */
        public final int summaryRes;
        /** The one primary instruction. */
        public final int actionRes;
        /** Up to three short steps; empty when the action is the whole of it. */
        public final List<Integer> stepsRes;
        /** The way back or undo, or 0 when the action changes nothing to come back from. */
        public final int wayBackRes;
        /** How to bring a hidden control back, or 0 when there is no hidden control. */
        public final int revealRes;
        /** The id {@code HelpTargets} measures this topic's control under, or null. */
        public final String targetId;
        /** The places whose layout has that control; empty when the topic has no control. */
        public final List<PaneWallPage> places;
        /** The gesture a demonstration would trace, or null when none is implemented. */
        public final TourGesture gesture;
        /** A lesson from {@link #LESSON_IDS}, or null when there is nothing to practise. */
        public final String lessonId;
        /** Other topics worth reading next. */
        public final List<String> relatedIds;
        /** Glossary terms this topic's words rely on. */
        public final List<String> termIds;
        /** Comma-separated search aliases, or 0 when the title and body are enough. */
        public final int aliasesRes;
        /** A path under {@code docs/en/}, anchor included, or null when there is no deeper page. */
        public final String docPath;

        Entry(Builder b) {
            this.id = b.id;
            this.group = b.group;
            this.kind = b.kind;
            this.titleRes = b.titleRes;
            this.summaryRes = b.summaryRes;
            this.actionRes = b.actionRes;
            this.stepsRes = Collections.unmodifiableList(new ArrayList<>(b.stepsRes));
            this.wayBackRes = b.wayBackRes;
            this.revealRes = b.revealRes;
            this.targetId = b.targetId;
            this.places = Collections.unmodifiableList(new ArrayList<>(b.places));
            this.gesture = b.gesture;
            this.lessonId = b.lessonId;
            this.relatedIds = Collections.unmodifiableList(new ArrayList<>(b.relatedIds));
            this.termIds = Collections.unmodifiableList(new ArrayList<>(b.termIds));
            this.aliasesRes = b.aliasesRes;
            this.docPath = b.docPath;
        }

        /** Whether this topic's control exists on that place at all. */
        public boolean onPlace(PaneWallPage place) {
            return targetId != null && places.contains(place);
        }

        @Override public String toString() { return id; }
    }

    private static final List<Entry> ALL = build();
    private static final Map<String, Entry> BY_ID = index(ALL);
    private static final Map<PaneWallPage, List<Entry>> BY_PLACE = byPlace(ALL);

    /** Every topic, in catalogue order: group by group, as the guide lists them. */
    public static List<Entry> all() { return ALL; }

    /** The topic with this id, or null. */
    public static Entry entry(String id) { return id == null ? null : BY_ID.get(id); }

    /** One group's topics, in catalogue order. */
    public static List<Entry> inGroup(Group group) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : ALL) if (entry.group == group) out.add(entry);
        return Collections.unmodifiableList(out);
    }

    /** The topic explaining the control measured under this id on this place, or null. */
    public static Entry forTarget(PaneWallPage place, String targetId) {
        if (targetId == null) return null;
        for (Entry entry : forPlace(place)) if (targetId.equals(entry.targetId)) return entry;
        return null;
    }

    // ---- per-place views ---------------------------------------------------------------------

    /** The topics whose control exists on this place, in catalogue order. */
    public static List<Entry> forPlace(PaneWallPage place) {
        List<Entry> entries = BY_PLACE.get(place);
        return entries == null ? Collections.<Entry>emptyList() : entries;
    }

    /**
     * A topic by its own id or by the target id it is bound to, on this place. The explorer names
     * controls by target id; a list names topics by their own, and one lookup answers both.
     */
    public static Entry entry(PaneWallPage place, String id) {
        Entry byId = entry(id);
        if (byId != null && (byId.targetId == null || byId.onPlace(place))) return byId;
        return forTarget(place, id);
    }

    /** How many topics the place has a control for — the count colours are spread over. */
    public static int sizeFor(PaneWallPage place) { return forPlace(place).size(); }

    /** A control's place in its place's catalogue, so its colour never moves. */
    public static int identityIndex(PaneWallPage place, String id) {
        Entry entry = entry(place, id);
        int index = entry == null ? -1 : forPlace(place).indexOf(entry);
        return index < 0 ? 0 : index;
    }

    /**
     * Controls the lists explain but the old overview leaves unboxed: the extra keys row keeps its
     * per-key labels, and the corner tab is the thing the reader opened help from.
     */
    public static boolean topicOnly(String id) {
        return "keys".equals(id) || "corners".equals(id);
    }

    // ---- the catalogue -----------------------------------------------------------------------

    private static List<Entry> build() {
        List<Builder> b = new ArrayList<>();

        // 1. Find your way.
        b.add(topic("places", Group.FIND_YOUR_WAY, R.string.help_topic_places_title,
                R.string.help_topic_places_summary, R.string.help_topic_places_action)
            .steps(R.string.help_topic_places_step1)
            .wayBack(R.string.help_topic_places_back)
            .related("status", "corners").terms("place")
            .aliases(R.string.help_topic_places_aliases)
            .doc("Launcher_Usage.md#move-between-the-terminal-and-the-widget-grid"));
        b.add(topic("corners", Group.FIND_YOUR_WAY, R.string.help_topic_corners_title,
                R.string.help_topic_corners_purpose, R.string.help_topic_corners_action)
            .wayBack(R.string.help_topic_corners_back)
            .reveal(R.string.help_topic_corners_reveal)
            .target("corners", PaneWallPage.TERMINAL)
            .lesson(LESSON_FIND_HELP)
            .related("palette", "layout_editor", "appearance_editor").terms("pane")
            .aliases(R.string.help_topic_corners_aliases)
            .doc("Launcher_Usage.md#get-help"));
        b.add(topic("palette", Group.FIND_YOUR_WAY, R.string.help_topic_palette_title,
                R.string.help_topic_space_purpose, R.string.help_topic_palette_action)
            .steps(R.string.help_topic_palette_step1, R.string.help_topic_palette_step2)
            .wayBack(R.string.help_topic_palette_back)
            .lesson(LESSON_FIND_ACTION)
            .related("space", "fix_action").terms("command_palette")
            .aliases(R.string.help_topic_palette_aliases)
            .doc("Launcher_Usage.md#use-the-command-palette"));
        b.add(topic("status", Group.FIND_YOUR_WAY, R.string.help_status_title,
                R.string.help_topic_status_purpose, R.string.help_topic_status_action)
            .steps(R.string.help_topic_status_step1, R.string.help_topic_status_step2,
                R.string.help_topic_status_step3)
            .wayBack(R.string.help_topic_status_back)
            .reveal(R.string.help_topic_status_reveal)
            .target("status", PaneWallPage.TERMINAL, PaneWallPage.DISPLAY, PaneWallPage.WIDGETS)
            .gesture(TourGesture.SWIPE_RIGHT)
            .related("places", "windows").terms("place", "window")
            .aliases(R.string.help_topic_status_aliases)
            .doc("Launcher_Usage.md#use-the-status-row"));
        b.add(topic("stats", Group.FIND_YOUR_WAY, R.string.help_topic_stats_title,
                R.string.help_topic_stats_purpose, R.string.help_topic_stats_action)
            .reveal(R.string.help_topic_stats_reveal)
            .target("stats", PaneWallPage.TERMINAL, PaneWallPage.DISPLAY)
            .related("status", "fix_stats")
            .aliases(R.string.help_topic_stats_aliases)
            .doc("Launcher_Settings.md#status-bar"));

        // 2. Apps and widgets.
        b.add(topic("dock", Group.APPS, R.string.help_dock_title,
                R.string.help_topic_dock_purpose, R.string.help_topic_dock_action)
            .steps(R.string.help_topic_dock_step1, R.string.help_topic_dock_step2,
                R.string.help_topic_dock_step3)
            .wayBack(R.string.help_topic_dock_back)
            .reveal(R.string.help_topic_dock_reveal)
            .target("dock", PaneWallPage.TERMINAL)
            .gesture(TourGesture.DRAG_DOWN)
            .lesson(LESSON_FIND_APPS)
            .related("az", "organize_apps", "fix_dock").terms("dock", "app_drawer")
            .aliases(R.string.help_topic_dock_aliases)
            .doc("Launcher_Usage.md#pinned-apps"));
        b.add(topic("az", Group.APPS, R.string.help_az_title,
                R.string.help_topic_az_purpose, R.string.help_topic_az_action)
            .steps(R.string.help_topic_az_step1)
            .reveal(R.string.help_topic_az_reveal)
            .target("az", PaneWallPage.TERMINAL)
            .gesture(TourGesture.SCRUB)
            .related("dock", "organize_apps").terms("app_drawer")
            .aliases(R.string.help_topic_az_aliases)
            .doc("Launcher_Usage.md#launch-android-apps"));
        b.add(topic("organize_apps", Group.APPS, R.string.help_topic_organize_title,
                R.string.help_topic_organize_summary, R.string.help_topic_organize_action)
            .steps(R.string.help_topic_organize_step1, R.string.help_topic_organize_step2)
            .related("dock", "az").terms("dock")
            .aliases(R.string.help_topic_organize_aliases)
            .doc("Launcher_Usage.md#pinned-apps"));
        b.add(topic("widget", Group.APPS, R.string.help_topic_widget_title,
                R.string.help_topic_widget_purpose, R.string.help_topic_widget_action)
            .steps(R.string.help_topic_widget_step1, R.string.help_topic_widget_step2,
                R.string.help_topic_widget_step3)
            .reveal(R.string.help_topic_widget_reveal)
            .target("widget", PaneWallPage.WIDGETS)
            .related("pages", "places")
            .aliases(R.string.help_topic_widget_aliases)
            .doc("Launcher_Usage.md#move-between-the-terminal-and-the-widget-grid"));
        b.add(topic("pages", Group.APPS, R.string.help_topic_pages_title,
                R.string.help_topic_pages_summary, R.string.help_topic_empty_action)
            .steps(R.string.help_topic_pages_step1, R.string.help_topic_pages_step2)
            .reveal(R.string.help_topic_empty_reveal)
            .target("empty", PaneWallPage.WIDGETS)
            .related("widget")
            .aliases(R.string.help_topic_pages_aliases)
            .doc("Launcher_Settings.md#layout"));

        // 3. Terminal basics.
        b.add(topic("copy_paste", Group.TERMINAL, R.string.help_topic_copy_title,
                R.string.help_topic_copy_summary, R.string.help_topic_copy_action)
            .steps(R.string.help_topic_copy_step1, R.string.help_topic_copy_step2,
                R.string.help_topic_copy_step3)
            .related("mouse_mode", "find_text")
            .aliases(R.string.help_topic_copy_aliases)
            .doc("Launcher_Usage.md#touch-works-like-a-mouse"));
        b.add(topic("mouse_mode", Group.TERMINAL, R.string.help_topic_mouse_title,
                R.string.help_topic_mouse_summary, R.string.help_topic_mouse_action)
            .wayBack(R.string.help_topic_mouse_back)
            .related("keys", "touchpad").terms("mouse_mode", "extra_keys")
            .aliases(R.string.help_topic_mouse_aliases)
            .doc("Launcher_Usage.md#touch-works-like-a-mouse"));
        b.add(topic("text_size", Group.TERMINAL, R.string.help_topic_size_title,
                R.string.help_topic_size_summary, R.string.help_topic_size_action)
            .wayBack(R.string.help_topic_size_back)
            .related("panes", "themes").terms("pane")
            .aliases(R.string.help_topic_size_aliases)
            .doc("Launcher_Usage.md#resize-panes-and-text"));
        b.add(topic("find_text", Group.TERMINAL, R.string.help_topic_find_title,
                R.string.help_topic_find_summary, R.string.help_topic_find_action)
            .steps(R.string.help_topic_find_step1, R.string.help_topic_find_step2)
            .related("copy_paste", "corners")
            .aliases(R.string.help_topic_find_aliases)
            .doc("Launcher_Usage.md#touch-works-like-a-mouse"));
        b.add(topic("hierarchy", Group.TERMINAL, R.string.help_topic_hierarchy_title,
                R.string.help_topic_hierarchy_summary, R.string.help_topic_sessions_action)
            .steps(R.string.help_topic_hierarchy_step1, R.string.help_topic_hierarchy_step2,
                R.string.help_topic_hierarchy_step3)
            .reveal(R.string.help_topic_sessions_reveal)
            .target("sessions", PaneWallPage.TERMINAL)
            .related("windows", "panes").terms("session", "window", "pane")
            .aliases(R.string.help_topic_hierarchy_aliases)
            .doc("Launcher_Usage.md#understand-the-terminal-hierarchy"));

        // 4. Keyboard and shortcuts.
        b.add(topic("keyboard", Group.KEYBOARD, R.string.help_topic_keyboard_title,
                R.string.help_topic_keyboard_summary, R.string.help_topic_keyboard_action)
            .steps(R.string.help_topic_keyboard_step1)
            .wayBack(R.string.help_topic_keyboard_back)
            .lesson(LESSON_KEYBOARD)
            .related("keys", "keyboard_layouts", "fix_keyboard").terms("extra_keys")
            .aliases(R.string.help_topic_keyboard_aliases)
            .doc("Launcher_Usage.md#use-the-built-in-keyboard-and-action-row"));
        b.add(topic("keys", Group.KEYBOARD, R.string.help_topic_keys_title,
                R.string.help_topic_keys_purpose, R.string.help_topic_keys_action)
            .steps(R.string.help_topic_keys_step1, R.string.help_topic_keys_step2,
                R.string.help_topic_keys_step3)
            .reveal(R.string.help_topic_keys_reveal)
            .target("keys", PaneWallPage.TERMINAL)
            .lesson(LESSON_KEYBOARD)
            .related("keyboard", "shortcuts", "fix_dock").terms("extra_keys")
            .aliases(R.string.help_topic_keys_aliases)
            .doc("Launcher_Usage.md#use-the-built-in-keyboard-and-action-row"));
        b.add(topic("keyboard_layouts", Group.KEYBOARD, R.string.help_topic_layouts_title,
                R.string.help_topic_layouts_summary, R.string.help_topic_layouts_action)
            .wayBack(R.string.help_topic_layouts_back)
            .related("keyboard", "float_pane").terms("docked_floating")
            .aliases(R.string.help_topic_layouts_aliases)
            .doc("Launcher_Settings.md#keyboard"));
        b.add(topic("shortcuts", Group.KEYBOARD, R.string.help_topic_shortcuts_title,
                R.string.help_topic_shortcuts_purpose, R.string.help_topic_prefix_action)
            .steps(R.string.help_topic_shortcuts_action, R.string.help_topic_shortcuts_step2)
            .reveal(R.string.help_topic_prefix_reveal)
            .target("prefix", PaneWallPage.TERMINAL)
            .related("keys", "panes", "fix_shortcuts")
            .aliases(R.string.help_topic_shortcuts_aliases)
            .doc("Launcher_Usage.md#use-the-built-in-keyboard-and-action-row"));
        b.add(topic("space", Group.KEYBOARD, R.string.help_space_title,
                R.string.help_topic_space_summary, R.string.help_topic_space_action)
            .steps(R.string.help_topic_space_step1)
            .reveal(R.string.help_topic_space_reveal)
            .target("space", PaneWallPage.TERMINAL)
            .gesture(TourGesture.SWIPE_UP)
            .lesson(LESSON_FIND_ACTION)
            .related("palette", "windows").terms("command_palette")
            .aliases(R.string.help_topic_space_aliases)
            .doc("Launcher_Usage.md#use-the-command-palette"));
        b.add(topic("settings", Group.KEYBOARD, R.string.help_launcher_settings_title,
                R.string.help_topic_settings_purpose, R.string.help_topic_settings_action)
            .steps(R.string.help_topic_settings_step1)
            .reveal(R.string.help_topic_settings_reveal)
            .target("settings", PaneWallPage.TERMINAL, PaneWallPage.DISPLAY, PaneWallPage.WIDGETS)
            .related("palette", "layout_editor", "themes")
            .aliases(R.string.help_topic_settings_aliases)
            .doc("Launcher_Settings.md"));

        // 5. Multitasking and workspaces.
        b.add(topic("panes", Group.MULTITASKING, R.string.help_topic_panes_title,
                R.string.help_topic_panes_summary, R.string.help_topic_panes_action)
            .steps(R.string.help_topic_panes_step1, R.string.help_topic_panes_step2,
                R.string.help_topic_panes_step3)
            .reveal(R.string.help_topic_divider_reveal)
            .target("divider", PaneWallPage.TERMINAL)
            .related("move_panes", "float_pane", "hierarchy").terms("pane", "window")
            .aliases(R.string.help_topic_panes_aliases)
            .doc("Launcher_Usage.md#work-with-panes-and-windows"));
        b.add(topic("move_panes", Group.MULTITASKING, R.string.help_topic_move_title,
                R.string.help_topic_move_summary, R.string.help_topic_move_action)
            .steps(R.string.help_topic_move_step1)
            .related("panes", "float_pane").terms("pane")
            .aliases(R.string.help_topic_move_aliases)
            .doc("Launcher_Usage.md#automatic-layouts"));
        b.add(topic("float_pane", Group.MULTITASKING, R.string.help_topic_float_title,
                R.string.help_topic_float_summary, R.string.help_topic_float_action)
            .wayBack(R.string.help_topic_float_back)
            .related("panes", "keyboard_layouts").terms("pane", "docked_floating")
            .aliases(R.string.help_topic_float_aliases)
            .doc("Launcher_Usage.md#floating-panes"));
        b.add(topic("windows", Group.MULTITASKING, R.string.help_topic_windows_title,
                R.string.help_topic_windows_purpose, R.string.help_topic_windows_action)
            .steps(R.string.help_topic_windows_step1, R.string.help_topic_windows_step2,
                R.string.help_topic_windows_step3)
            .reveal(R.string.help_topic_windows_reveal)
            .target("windows", PaneWallPage.TERMINAL)
            .related("hierarchy", "workspaces", "panes").terms("window", "session")
            .aliases(R.string.help_topic_windows_aliases)
            .doc("Launcher_Usage.md#use-sessions"));
        b.add(topic("workspaces", Group.MULTITASKING, R.string.help_topic_workspace_title,
                R.string.help_topic_workspace_summary, R.string.help_topic_workspace_action)
            .steps(R.string.help_topic_workspace_step1, R.string.help_topic_workspace_step2)
            .related("windows", "panes").terms("workspace", "session")
            .aliases(R.string.help_topic_workspace_aliases)
            .doc("Launcher_Usage.md#save-and-load-workspaces"));

        // 6. Make it yours.
        b.add(topic("layout_editor", Group.YOURS, R.string.help_topic_layout_title,
                R.string.help_topic_layout_summary, R.string.help_topic_layout_action)
            .steps(R.string.help_topic_layout_step1, R.string.help_topic_layout_step2)
            .wayBack(R.string.help_topic_layout_back)
            .related("appearance_editor", "corners", "fix_dock").terms("editors")
            .aliases(R.string.help_topic_layout_aliases)
            .doc("Launcher_Settings.md#layout"));
        b.add(topic("appearance_editor", Group.YOURS, R.string.help_topic_appearance_title,
                R.string.help_topic_appearance_summary, R.string.help_topic_appearance_action)
            .steps(R.string.help_topic_appearance_step1, R.string.help_topic_appearance_step2)
            .wayBack(R.string.help_topic_appearance_back)
            .related("base_values", "themes", "layout_editor").terms("surface", "editors")
            .aliases(R.string.help_topic_appearance_aliases)
            .doc("Launcher_Settings.md#look"));
        b.add(topic("base_values", Group.YOURS, R.string.help_topic_base_title,
                R.string.help_topic_base_summary, R.string.help_topic_base_action)
            .wayBack(R.string.help_topic_base_back)
            .related("appearance_editor", "themes")
            .terms("base", "independent_value", "surface")
            .aliases(R.string.help_topic_base_aliases)
            .doc("Launcher_Settings.md#look"));
        b.add(topic("themes", Group.YOURS, R.string.help_topic_themes_title,
                R.string.help_topic_themes_summary, R.string.help_topic_themes_action)
            .related("appearance_editor", "base_values", "settings")
            .aliases(R.string.help_topic_themes_aliases)
            .doc("Launcher_Settings.md#theming-from-a-color-scheme"));

        // 7a. Linux display.
        b.add(topic("setup", Group.DISPLAY, R.string.help_setup_title,
                R.string.help_topic_setup_summary, R.string.help_topic_setup_action)
            .reveal(R.string.help_topic_setup_reveal)
            .target("setup", PaneWallPage.DISPLAY)
            .related("start", "fix_display")
            .aliases(R.string.help_topic_setup_aliases)
            .doc("X11_Display.md#turn-it-on"));
        b.add(topic("start", Group.DISPLAY, R.string.help_start_title,
                R.string.help_topic_start_purpose, R.string.help_topic_start_action)
            .steps(R.string.help_topic_start_step1)
            .reveal(R.string.help_topic_start_reveal)
            .target("start", PaneWallPage.DISPLAY)
            .related("display_apps", "setup", "fix_display").terms("place")
            .aliases(R.string.help_topic_start_aliases)
            .doc("X11_Display.md#every-day"));
        b.add(topic("display_apps", Group.DISPLAY, R.string.help_display_apps_title,
                R.string.help_topic_display_apps_purpose, R.string.help_topic_display_apps_action)
            .reveal(R.string.help_topic_display_apps_reveal)
            .target("windows", PaneWallPage.DISPLAY)
            .related("start", "display_keys", "scale")
            .aliases(R.string.help_topic_display_apps_aliases)
            .doc("X11_Display.md#apps-on-the-display"));
        b.add(topic("scale", Group.DISPLAY, R.string.help_scale_title,
                R.string.help_topic_scale_purpose, R.string.help_topic_scale_action)
            .reveal(R.string.help_topic_scale_reveal)
            .target("scale", PaneWallPage.DISPLAY)
            .related("start", "display_apps")
            .aliases(R.string.help_topic_scale_aliases)
            .doc("X11_Display.md#every-day"));
        b.add(topic("touchpad", Group.DISPLAY, R.string.help_pad_title,
                R.string.help_topic_touchpad_purpose, R.string.help_topic_touchpad_action)
            .steps(R.string.help_pad_one, R.string.help_pad_two, R.string.help_pad_three)
            .reveal(R.string.help_topic_touchpad_reveal)
            .target("touchpad", PaneWallPage.DISPLAY)
            .related("mouse_mode", "display_apps").terms("mouse_mode")
            .aliases(R.string.help_topic_touchpad_aliases)
            .doc("X11_Display.md#every-day"));
        b.add(topic("display_keys", Group.DISPLAY, R.string.help_topic_display_keys_title,
                R.string.help_topic_display_keys_summary, R.string.help_topic_display_keys_action)
            .steps(R.string.help_topic_display_keys_step1)
            .related("display_apps", "keys", "shortcuts").terms("extra_keys")
            .aliases(R.string.help_topic_display_keys_aliases)
            .doc("X11_Display.md#the-keyboard-follows-text-fields"));

        // 7b. Something missing?
        b.add(fix("fix_keyboard", R.string.help_fix_keyboard_title,
                R.string.help_fix_keyboard_summary, R.string.help_fix_keyboard_action)
            .related("keyboard", "keys").terms("extra_keys")
            .aliases(R.string.help_fix_keyboard_aliases)
            .doc("Launcher_Troubleshooting.md#the-keyboard-is-missing-or-the-wrong-keyboard-opens"));
        b.add(fix("fix_dock", R.string.help_fix_dock_title,
                R.string.help_fix_dock_summary, R.string.help_fix_dock_action)
            .related("layout_editor", "dock", "keys").terms("editors")
            .aliases(R.string.help_fix_dock_aliases)
            .doc("Launcher_Settings.md#layout"));
        b.add(fix("fix_action", R.string.help_fix_action_title,
                R.string.help_fix_action_summary, R.string.help_fix_action_action)
            .related("palette", "panes").terms("command_palette")
            .aliases(R.string.help_fix_action_aliases)
            .doc("Launcher_Troubleshooting.md#pane-or-window-actions-are-unavailable"));
        b.add(fix("fix_shortcuts", R.string.help_fix_shortcuts_title,
                R.string.help_fix_shortcuts_summary, R.string.help_fix_shortcuts_action)
            .related("shortcuts", "keys")
            .aliases(R.string.help_fix_shortcuts_aliases)
            .doc("Launcher_Troubleshooting.md#a-shortcut-reaches-the-shell-instead-of-the-launcher"));
        b.add(fix("fix_stats", R.string.help_fix_stats_title,
                R.string.help_fix_stats_summary, R.string.help_fix_stats_action)
            .related("stats", "status")
            .aliases(R.string.help_fix_stats_aliases)
            .doc("Launcher_Troubleshooting.md#cpu-memory-weather-media-or-notifications-are-missing"));
        b.add(fix("fix_display", R.string.help_fix_display_title,
                R.string.help_fix_display_summary, R.string.help_fix_display_action)
            .related("setup", "start")
            .aliases(R.string.help_fix_display_aliases)
            .doc("Launcher_Troubleshooting.md#the-linux-display-will-not-start-or-apps-cannot-reach-it"));
        b.add(fix("fix_support", R.string.help_fix_support_title,
                R.string.help_fix_support_summary, R.string.help_fix_support_action)
            .aliases(R.string.help_fix_support_aliases)
            .doc("Launcher_Troubleshooting.md#collect-useful-diagnostics"));

        List<Entry> entries = new ArrayList<>(b.size());
        for (Builder builder : b) entries.add(new Entry(builder));
        return Collections.unmodifiableList(entries);
    }

    private static Builder topic(String id, Group group, int title, int summary, int action) {
        return new Builder(id, group, Kind.GUIDE, title, summary, action);
    }

    private static Builder fix(String id, int title, int summary, int action) {
        return new Builder(id, Group.MISSING, Kind.FIX, title, summary, action);
    }

    private static Map<String, Entry> index(List<Entry> entries) {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry entry : entries) map.put(entry.id, entry);
        return Collections.unmodifiableMap(map);
    }

    private static Map<PaneWallPage, List<Entry>> byPlace(List<Entry> entries) {
        Map<PaneWallPage, List<Entry>> map = new EnumMap<>(PaneWallPage.class);
        for (PaneWallPage place : PaneWallPage.values()) {
            List<Entry> out = new ArrayList<>();
            for (Entry entry : entries) if (entry.onPlace(place)) out.add(entry);
            map.put(place, Collections.unmodifiableList(out));
        }
        return Collections.unmodifiableMap(map);
    }

    private static final class Builder {
        private final String id;
        private final Group group;
        private final Kind kind;
        private final int titleRes;
        private final int summaryRes;
        private final int actionRes;
        private final List<Integer> stepsRes = new ArrayList<>();
        private final List<PaneWallPage> places = new ArrayList<>();
        private final List<String> relatedIds = new ArrayList<>();
        private final List<String> termIds = new ArrayList<>();
        private int wayBackRes;
        private int revealRes;
        private String targetId;
        private TourGesture gesture;
        private String lessonId;
        private int aliasesRes;
        private String docPath;

        Builder(String id, Group group, Kind kind, int titleRes, int summaryRes, int actionRes) {
            this.id = id;
            this.group = group;
            this.kind = kind;
            this.titleRes = titleRes;
            this.summaryRes = summaryRes;
            this.actionRes = actionRes;
        }

        Builder steps(int... res) {
            for (int one : res) stepsRes.add(one);
            if (stepsRes.size() > 3) throw new IllegalStateException(id + ": more than three steps");
            return this;
        }
        Builder wayBack(int res) { wayBackRes = res; return this; }
        Builder reveal(int res) { revealRes = res; return this; }
        Builder target(String targetId, PaneWallPage... on) {
            this.targetId = targetId;
            places.addAll(Arrays.asList(on));
            return this;
        }
        Builder gesture(TourGesture value) { gesture = value; return this; }
        Builder lesson(String id) { lessonId = id; return this; }
        Builder related(String... ids) { relatedIds.addAll(Arrays.asList(ids)); return this; }
        Builder terms(String... ids) { termIds.addAll(Arrays.asList(ids)); return this; }
        Builder aliases(int res) { aliasesRes = res; return this; }
        Builder doc(String path) { docPath = path; return this; }
    }
}
