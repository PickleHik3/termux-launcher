package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Which session's row a Linux window goes into (#392), apart from where in that row
 * ({@link SessionRow}). Pure.
 */
public final class LinuxWindowHoming {

    private LinuxWindowHoming() {}

    /**
     * Where a Linux window was in its session's row when it was minimised: the session it left
     * and its place in that row. Carried by the window until it is restored.
     */
    public static final class Minimised<S, T> {
        @NonNull public final S session;
        @Nullable public final SessionRow.Place<T> place;

        public Minimised(@NonNull S session, @Nullable SessionRow.Place<T> place) {
            this.session = session;
            this.place = place;
        }
    }

    /**
     * The index of the session a newly opened Linux window joins: the current one, else (no
     * current session) the first, which is the one shown next; -1 when there is no session at
     * all, and the window waits for one.
     */
    public static int sessionForNew(int currentIndex, int sessionCount) {
        if (sessionCount <= 0) return -1;
        return currentIndex >= 0 && currentIndex < sessionCount ? currentIndex : 0;
    }

    /**
     * The session a minimised window is restored into: the one it was minimised from while it
     * is still among {@code sessions}, else {@code current}. Null when there is neither.
     */
    @Nullable
    public static <S> S sessionForRestore(@Nullable S origin, @NonNull List<S> sessions,
                                          @Nullable S current) {
        if (origin != null && sessions.contains(origin)) return origin;
        return current;
    }

    /** Whether the old place may be used: the window goes back to the session it came from. */
    public static <S> boolean atOldPlace(@Nullable S origin, @Nullable S target) {
        return origin != null && origin == target;
    }
}
