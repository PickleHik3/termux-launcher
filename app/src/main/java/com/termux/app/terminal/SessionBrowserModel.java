package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable session/window/pane projection used by the searchable session browser. */
public final class SessionBrowserModel {

    public static final class Pane {
        @Nullable public final String cwd;
        @Nullable public final String foreground;

        public Pane(@Nullable String cwd, @Nullable String foreground) {
            this.cwd = emptyToNull(cwd);
            this.foreground = emptyToNull(foreground);
        }
    }

    public static final class Window {
        public final int index;
        public final boolean current;
        /** Index into {@link #panes} of the focused pane; 0 when unknown. */
        public final int activePane;
        @NonNull public final List<Pane> panes;

        public Window(int index, boolean current, int activePane, @NonNull List<Pane> panes) {
            this.index = index;
            this.current = current;
            this.activePane = activePane;
            this.panes = Collections.unmodifiableList(new ArrayList<>(panes));
        }
    }

    public static final class Session {
        public final int index;
        public final boolean current;
        @Nullable public final String name;
        @NonNull public final List<Window> windows;

        public Session(int index, boolean current, @Nullable String name,
                       @NonNull List<Window> windows) {
            this.index = index;
            this.current = current;
            this.name = emptyToNull(name);
            this.windows = Collections.unmodifiableList(new ArrayList<>(windows));
        }

        public int paneCount() {
            int count = 0;
            for (Window window : windows) count += window.panes.size();
            return count;
        }
    }

    private SessionBrowserModel() {}

    /** Case-insensitive search over session name plus every pane's CWD and foreground label. */
    @NonNull
    public static List<Session> filter(@NonNull List<Session> sessions, @Nullable String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return new ArrayList<>(sessions);
        List<Session> filtered = new ArrayList<>();
        for (Session session : sessions) {
            if (contains(session.name, needle)) {
                filtered.add(session);
                continue;
            }
            boolean matched = false;
            for (Window window : session.windows) {
                for (Pane pane : window.panes) {
                    if (contains(pane.cwd, needle) || contains(pane.foreground, needle)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (matched) filtered.add(session);
        }
        return filtered;
    }

    private static boolean contains(@Nullable String value, @NonNull String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
