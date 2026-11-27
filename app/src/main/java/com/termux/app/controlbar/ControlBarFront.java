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

        public Window(long id, @NonNull WindowKind kind) {
            this.id = id;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Window)) return false;
            Window that = (Window) other;
            return id == that.id && kind == that.kind;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id) * 31 + kind.hashCode();
        }
    }

    final boolean home;
    final int homePageCount;
    final int homePage;
    final int workspaceNumber;
    @Nullable final WindowKind mainKind;
    @NonNull final List<Window> windows;
    final long focusedId;

    private ControlBarFront(boolean home, int homePageCount, int homePage, int workspaceNumber,
                            @Nullable WindowKind mainKind, @NonNull List<Window> windows, long focusedId) {
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
}
