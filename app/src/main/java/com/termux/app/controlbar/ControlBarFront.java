package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The place in front as the control bar reads it (#424): Home with its pages, or a Workspace with
 * its number, its main window's kind, its windows in order and the focused one. Immutable.
 */
public final class ControlBarFront {

    /** One window of the Workspace in front: what its mark needs. */
    public static final class Window {
        public final long id;
        @NonNull public final WindowKind kind;
        /** The window's name, for its mark's spoken label; null for none. */
        @Nullable public final String name;
        /** Minimised (#425): its mark is dimmed, and a tap on it shows the window again. */
        public final boolean minimised;

        public Window(long id, @NonNull WindowKind kind) {
            this(id, kind, null);
        }

        public Window(long id, @NonNull WindowKind kind, @Nullable String name) {
            this(id, kind, name, false);
        }

        public Window(long id, @NonNull WindowKind kind, @Nullable String name, boolean minimised) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.minimised = minimised;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Window)) return false;
            Window that = (Window) other;
            return id == that.id && kind == that.kind && java.util.Objects.equals(name, that.name)
                && minimised == that.minimised;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, kind, name, minimised);
        }
    }

    final boolean home;
    final int homePageCount;
    final int homePage;
    final int workspaceNumber;
    @Nullable final WindowKind mainKind;
    @NonNull final List<Window> windows;
    final long focusedId;
    /** The Workspace's own name; null on Home or while it has none. */
    @Nullable final String workspaceName;

    private ControlBarFront(boolean home, int homePageCount, int homePage, int workspaceNumber,
                            @Nullable WindowKind mainKind, @NonNull List<Window> windows, long focusedId) {
        this(home, homePageCount, homePage, workspaceNumber, mainKind, windows, focusedId, null);
    }

    private ControlBarFront(boolean home, int homePageCount, int homePage, int workspaceNumber,
                            @Nullable WindowKind mainKind, @NonNull List<Window> windows, long focusedId,
                            @Nullable String workspaceName) {
        this.workspaceName = workspaceName == null || workspaceName.trim().isEmpty() ? null : workspaceName.trim();
        this.home = home;
        this.homePageCount = homePageCount;
        this.homePage = homePage;
        this.workspaceNumber = workspaceNumber;
        this.mainKind = mainKind;
        this.windows = windows;
        this.focusedId = focusedId;
    }

    /** Home, showing page {@code page} (from 0) of {@code pageCount}. */
    @NonNull
    public static ControlBarFront home(int pageCount, int page) {
        return new ControlBarFront(true, pageCount, page, 0, null, Collections.emptyList(), -1);
    }

    /**
     * Workspace {@code number}: {@code mainKind} the kind of the window whose icon stands for it
     * (null when empty), {@code windows} in the order they stand, {@code focusedId} the focused one.
     */
    @NonNull
    public static ControlBarFront workspace(int number, @Nullable WindowKind mainKind,
                                            @NonNull List<Window> windows, long focusedId) {
        return new ControlBarFront(false, 0, 0, number, mainKind,
            Collections.unmodifiableList(new ArrayList<>(windows)), focusedId);
    }

    /** This Workspace front with its own name {@code name} (blank or null: none); Home keeps none. */
    @NonNull
    public ControlBarFront named(@Nullable String name) {
        if (home) return this;
        return new ControlBarFront(false, homePageCount, homePage, workspaceNumber, mainKind, windows,
            focusedId, name);
    }
}
