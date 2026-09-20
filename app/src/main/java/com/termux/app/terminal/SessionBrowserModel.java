package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable session/window/pane projection the sessions drawer is built from. */
public final class SessionBrowserModel {

    public static final class Pane {
        @Nullable public final String cwd;
        @Nullable public final String foreground;
        /** What the AI coding agent in this pane is doing, or null when it is not running one. */
        @Nullable public final AgentStatus.State agentState;

        public Pane(@Nullable String cwd, @Nullable String foreground) {
            this(cwd, foreground, null);
        }

        public Pane(@Nullable String cwd, @Nullable String foreground,
                    @Nullable AgentStatus.State agentState) {
            this.cwd = emptyToNull(cwd);
            this.foreground = emptyToNull(foreground);
            this.agentState = agentState;
        }
    }

    /**
     * Home-relative display form of a working directory: paths inside the Termux home render as
     * {@code ~} or {@code ~/sub}, while anything above home — the user walked backward out of it —
     * keeps its full {@code /data/data/com.termux/...} prefix. Display-only; the model keeps the
     * raw path.
     */
    @NonNull
    public static String displayCwd(@NonNull String cwd) {
        String home = com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH;
        if (cwd.equals(home)) return "~";
        if (cwd.startsWith(home + "/")) return "~" + cwd.substring(home.length());
        return cwd;
    }

    public static final class Window {
        public final long id;
        public final int index;
        public final boolean current;
        /** Index into {@link #panes} of the focused pane; 0 when unknown. */
        public final int activePane;
        @NonNull public final List<Pane> panes;
        @Nullable public final String label;

        public Window(int index, boolean current, int activePane, @NonNull List<Pane> panes) {
            this(index, index, current, activePane, panes, null);
        }

        public Window(long id, int index, boolean current, int activePane,
                      @NonNull List<Pane> panes, @Nullable String label) {
            this.id = id;
            this.index = index;
            this.current = current;
            this.activePane = activePane;
            this.panes = Collections.unmodifiableList(new ArrayList<>(panes));
            this.label = emptyToNull(label);
        }
    }

    public static final class Session {
        public final long id;
        public final int index;
        public final boolean current;
        @Nullable public final String name;
        @NonNull public final List<Window> windows;
        /**
         * The rolled-up agent reading over every pane of every window: blocked outranks working
         * outranks idle, and null means no pane here is running an agent. Derived, never passed in,
         * so the header row can never disagree with the pane lines under it.
         */
        @Nullable public final AgentStatus.State agentState;

        public Session(int index, boolean current, @Nullable String name,
                       @NonNull List<Window> windows) {
            this(index, index, current, name, windows);
        }

        public Session(long id, int index, boolean current, @Nullable String name,
                       @NonNull List<Window> windows) {
            this.id = id;
            this.index = index;
            this.current = current;
            this.name = emptyToNull(name);
            this.windows = Collections.unmodifiableList(new ArrayList<>(windows));
            AgentStatus.State folded = null;
            for (Window window : this.windows) {
                for (Pane pane : window.panes) folded = AgentStatus.rollUp(folded, pane.agentState);
            }
            this.agentState = folded;
        }

        public int paneCount() {
            int count = 0;
            for (Window window : windows) count += window.panes.size();
            return count;
        }
    }

    private SessionBrowserModel() {}

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
