package com.termux.app.launcher.popup;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which rows the App menu shows, and in which groups, as a pure answer.
 *
 * <p>The card is the same on the dock, on Home and in the drawer; only the inputs differ. Groups run
 * top to bottom: the notifications row (only while the app is badged), the app's shortcuts, then the
 * actions in Launcher3's order — Widgets, App info, Add to home, Pin or Unpin, Change icon (with
 * Reset icon beside it once a custom icon is set), Uninstall. An app in a dock folder trades Pin for
 * Move to dock and Remove from folder, the two things only a folder member can do. An icon on Home
 * has no Add to home (it is there already) and offers Remove from Home before Uninstall: the same
 * thing its drag onto the Remove target does. An icon on the dock adds Edit dock, alone in the last
 * group: the dock's card, two taps away (editor-redesign §3).
 */
public final class AppMenuPlan {

    /** Most shortcuts the card lists before the rest collapse behind a More row. */
    public static final int MAX_SHORTCUT_ROWS = 6;

    public enum Row {
        NOTIFICATIONS,
        SHORTCUT,
        MORE_SHORTCUTS,
        WIDGETS,
        APP_INFO,
        ADD_TO_HOME,
        PIN,
        UNPIN,
        MOVE_TO_DOCK,
        REMOVE_FROM_FOLDER,
        REMOVE_FROM_HOME,
        CHANGE_ICON,
        RESET_ICON,
        UNINSTALL,
        EDIT_DOCK
    }

    /** Where the long-pressed icon lives, which decides the Pin slot. */
    public enum Source {
        /** A drawer cell for an app not on the dock. */
        UNPINNED,
        /** A dock icon, or a drawer cell for an app that is on the dock. */
        PINNED,
        /** An app inside a dock folder. */
        FOLDER_MEMBER,
        /** An app icon on a Home page; {@link Inputs#pinnedToDock} picks Pin or Unpin. */
        HOME
    }

    /** What the host knows about the app; every flag defaults to "not offered". */
    public static final class Inputs {
        @NonNull public Source source = Source.UNPINNED;
        /** Shortcut-like actions the app offers, the launcher entries' own first. */
        public int shortcutCount;
        public boolean badged;
        /** False for a Linux app or a launcher entry: no Android package to ask about. */
        public boolean androidPackage;
        public boolean hasWidgets;
        public boolean canAddToHome;
        public boolean customIcon;
        /** For {@link Source#HOME}: the app is also on the dock, so the row is Unpin. */
        public boolean pinnedToDock;
        /** The icon was pressed on the dock itself (not the drawer, Home or a folder's popup). */
        public boolean onDock;
    }

    private AppMenuPlan() {
    }

    /** How many shortcut rows the card lists for {@code count} shortcuts. */
    public static int visibleShortcutCount(int count) {
        if (count <= MAX_SHORTCUT_ROWS) return Math.max(0, count);
        // Past the cap the last slot becomes the More row, so the group never grows past six.
        return MAX_SHORTCUT_ROWS - 1;
    }

    /** The groups, top to bottom; never an empty group. */
    @NonNull
    public static List<List<Row>> groups(@NonNull Inputs in) {
        List<List<Row>> groups = new ArrayList<>(3);
        if (in.badged) groups.add(Collections.singletonList(Row.NOTIFICATIONS));

        int visible = visibleShortcutCount(in.shortcutCount);
        if (visible > 0) {
            List<Row> shortcuts = new ArrayList<>(visible + 1);
            for (int i = 0; i < visible; i++) shortcuts.add(Row.SHORTCUT);
            if (visible < in.shortcutCount) shortcuts.add(Row.MORE_SHORTCUTS);
            groups.add(shortcuts);
        }

        List<Row> actions = new ArrayList<>(8);
        if (in.androidPackage && in.hasWidgets) actions.add(Row.WIDGETS);
        if (in.androidPackage) actions.add(Row.APP_INFO);
        if (in.canAddToHome && in.source != Source.FOLDER_MEMBER && in.source != Source.HOME) {
            actions.add(Row.ADD_TO_HOME);
        }
        switch (in.source) {
            case FOLDER_MEMBER:
                actions.add(Row.MOVE_TO_DOCK);
                actions.add(Row.REMOVE_FROM_FOLDER);
                break;
            case PINNED:
                actions.add(Row.UNPIN);
                break;
            case HOME:
                actions.add(in.pinnedToDock ? Row.UNPIN : Row.PIN);
                break;
            default:
                actions.add(Row.PIN);
                break;
        }
        actions.add(Row.CHANGE_ICON);
        if (in.customIcon) actions.add(Row.RESET_ICON);
        if (in.source == Source.HOME) actions.add(Row.REMOVE_FROM_HOME);
        if (in.androidPackage) actions.add(Row.UNINSTALL);
        groups.add(actions);
        if (in.onDock) groups.add(Collections.singletonList(Row.EDIT_DOCK));
        return groups;
    }
}
