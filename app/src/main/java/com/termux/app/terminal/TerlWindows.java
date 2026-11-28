package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

import java.util.List;

/**
 * Terl's windows as the local API's window routes read them (terl-integration-1): under the switch
 * {@code window list}, {@code window focus} and {@code window open}'s answer speak of the stack's
 * windows (the ids {@code window open --print-id} hands out), not v1's row. Plain types only, so the
 * terminal package names nothing of the tiling desktop.
 */
public interface TerlWindows {

    /** One window of the stack. */
    final class Window {
        /** The stack's window id: what {@code window focus} takes. */
        public final long id;
        /** Where it stands: "Workspace 2", "Home page 1". */
        @NonNull public final String place;
        /** What its strip calls it. */
        @Nullable public final String name;
        /** Its kind: shell, linux_app, display, assistant. */
        @NonNull public final String kind;
        /** It is the focused window of the place in front. */
        public final boolean current;
        /** Its shell, for a terminal whose shell runs; null otherwise. */
        @Nullable public final TerminalSession shell;

        public Window(long id, @NonNull String place, @Nullable String name, @NonNull String kind,
                      boolean current, @Nullable TerminalSession shell) {
            this.id = id;
            this.place = place;
            this.name = name;
            this.kind = kind;
            this.current = current;
            this.shell = shell;
        }
    }

    /** Every window of the stack, in stack order. */
    @NonNull List<Window> windows();

    /** Focuses window {@code id}, going to its place; false when there is none. */
    boolean focus(long id);

    /**
     * Closes shell window {@code id} at once and kills its shell (it may have no process yet);
     * true only when the window has left the stack.
     */
    boolean close(long id);
}
