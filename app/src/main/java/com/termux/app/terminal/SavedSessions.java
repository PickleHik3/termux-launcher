package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a Saved session is, as pure policy (#359). A new save is a file of one session of one
 * window; an old save (Save sessions…) holds several windows, in several sessions. Either way a
 * saved session opens as one Workspace whose sessions are the file's windows in file order, the
 * first in front; windows saved as Home floats are left out. Nothing is converted on disk.
 */
public final class SavedSessions {

    private SavedSessions() {}

    /** The windows a saved file opens as sessions: every window but the Home floats, in order. */
    @NonNull
    public static List<TerminalWorkspace.Window> windows(@NonNull TerminalWorkspace saved) {
        List<TerminalWorkspace.Window> out = new ArrayList<>();
        for (TerminalWorkspace.Session session : saved.sessions) {
            for (TerminalWorkspace.Window window : session.windows) {
                if (window.homeFloat == null) out.add(window);
            }
        }
        return out;
    }

    /** One session holding {@code window}: what Save writes for a session. */
    @NonNull
    public static TerminalWorkspace of(@NonNull String name, long savedAtEpochMs,
                                       @NonNull TerminalWorkspace.Window window) {
        return new TerminalWorkspace(name, savedAtEpochMs, 0, Collections.singletonList(
            new TerminalWorkspace.Session(null, 0, Collections.singletonList(window))));
    }

    /**
     * {@code saved} as it is opened: one session of {@link #windows}, the first in front; null
     * when the file holds no window to open.
     */
    @Nullable
    public static TerminalWorkspace forOpening(@NonNull TerminalWorkspace saved) {
        List<TerminalWorkspace.Window> windows = windows(saved);
        if (windows.isEmpty()) return null;
        return new TerminalWorkspace(saved.name, saved.savedAtEpochMs, 0, Collections.singletonList(
            new TerminalWorkspace.Session(null, 0, windows)));
    }
}
