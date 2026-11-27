package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the control bar's two rows hold for the place in front (#424, spec "The control bar"). Top
 * row: the place badge (Home, or the Workspace's number and its main window's icon), a mark per
 * window with the focused one lit (on Home a dot per Home page, the one in front lit), then the
 * reading slot. Bottom row: the tools for what is in front, then navigation mode and search.
 * Pure; the view only draws the answer and builds it again only when the answer changes.
 */
public final class ControlBarRows {

    /** Navigation mode and search: the end of the bottom row in every place. */
    public static final List<ControlBarTool> TRAILING = Collections.unmodifiableList(
        Arrays.asList(ControlBarTool.NAVIGATION, ControlBarTool.SEARCH));

    private static final List<ControlBarTool> HOME_TOOLS =
        Collections.singletonList(ControlBarTool.PINNED_APPS);
    private static final List<ControlBarTool> TERMINAL_TOOLS = Collections.unmodifiableList(
        Arrays.asList(ControlBarTool.KEYBOARD, ControlBarTool.SPLIT, ControlBarTool.NEW_TERMINAL,
            ControlBarTool.WINDOW_MENU));
    private static final List<ControlBarTool> LINUX_APP_TOOLS = Collections.unmodifiableList(
        Arrays.asList(ControlBarTool.MOUSE_MODE, ControlBarTool.KEYBOARD, ControlBarTool.WINDOW_MENU));
    private static final List<ControlBarTool> ASSISTANT_TOOLS = Collections.unmodifiableList(
        Arrays.asList(ControlBarTool.KEYBOARD, ControlBarTool.WINDOW_MENU));
    private static final List<ControlBarTool> EMPTY_TOOLS =
        Collections.singletonList(ControlBarTool.NEW_TERMINAL);

    /** One mark of the top row. */
    public static final class Mark {
        /** The window's id on a Workspace; the Home page's index on Home. */
        public final long target;
        /** The window's kind; null for a Home page's dot. */
        @Nullable public final WindowKind kind;
        /** The focused window, or the Home page in front. */
        public final boolean lit;
        /** The window's name, for its spoken label; null for a dot or a window with none. */
        @Nullable public final String name;

        Mark(long target, @Nullable WindowKind kind, boolean lit, @Nullable String name) {
            this.target = target;
            this.kind = kind;
            this.lit = lit;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Mark)) return false;
            Mark that = (Mark) other;
            return target == that.target && kind == that.kind && lit == that.lit
                && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(target, kind, lit, name);
        }
    }

    /** Whether Home is in front. */
    public final boolean home;
    /** The Workspace's number; 0 on Home. */
    public final int workspaceNumber;
    /** The kind of the Workspace's main window, whose icon the badge wears; null on Home or empty. */
    @Nullable public final WindowKind badgeKind;
    @NonNull public final List<Mark> marks;
    /** The tools for what is in front, before {@link #trailing}. */
    @NonNull public final List<ControlBarTool> tools;
    /** {@link #TRAILING}. */
    @NonNull public final List<ControlBarTool> trailing = TRAILING;

    private ControlBarRows(boolean home, int workspaceNumber, @Nullable WindowKind badgeKind,
                           @NonNull List<Mark> marks, @NonNull List<ControlBarTool> tools) {
        this.home = home;
        this.workspaceNumber = workspaceNumber;
        this.badgeKind = badgeKind;
        this.marks = Collections.unmodifiableList(marks);
        this.tools = tools;
    }

    @NonNull
    public static ControlBarRows of(@NonNull ControlBarFront front) {
        if (front.home) {
            int count = Math.max(1, front.homePageCount);
            int page = Math.max(0, Math.min(count - 1, front.homePage));
            List<Mark> dots = new ArrayList<>(count);
            for (int i = 0; i < count; i++) dots.add(new Mark(i, null, i == page, null));
            return new ControlBarRows(true, 0, null, dots, HOME_TOOLS);
        }
        List<Mark> marks = new ArrayList<>(front.windows.size());
        WindowKind focusedKind = null;
        for (ControlBarFront.Window window : front.windows) {
            boolean lit = window.id == front.focusedId;
            if (lit) focusedKind = window.kind;
            marks.add(new Mark(window.id, window.kind, lit, window.name));
        }
        // With no window focused the first one's tools stand, as focus falls there.
        if (focusedKind == null && !front.windows.isEmpty()) focusedKind = front.windows.get(0).kind;
        return new ControlBarRows(false, front.workspaceNumber, front.mainKind, marks,
            toolsFor(focusedKind));
    }

    /** The tools for a window of {@code kind} in front; an empty Workspace offers a new terminal. */
    @NonNull
    private static List<ControlBarTool> toolsFor(@Nullable WindowKind kind) {
        if (kind == null) return EMPTY_TOOLS;
        switch (kind) {
            case SHELL: return TERMINAL_TOOLS;
            case LINUX_APP:
            case DISPLAY: return LINUX_APP_TOOLS;
            case ASSISTANT: return ASSISTANT_TOOLS;
            default: return EMPTY_TOOLS;
        }
    }

    /** The lit mark's index, or -1. */
    public int litIndex() {
        for (int i = 0; i < marks.size(); i++) if (marks.get(i).lit) return i;
        return -1;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ControlBarRows)) return false;
        ControlBarRows that = (ControlBarRows) other;
        return home == that.home && workspaceNumber == that.workspaceNumber
            && badgeKind == that.badgeKind && marks.equals(that.marks) && tools.equals(that.tools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(home, workspaceNumber, badgeKind, marks, tools);
    }
}
