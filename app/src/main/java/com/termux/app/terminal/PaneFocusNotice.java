package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the host has to hear after a touch focuses a pane: nothing, the pane alone, or the whole
 * change. A finger landing on the pane that already has focus (every swipe that starts on the
 * Workspace) changes nothing the window bar shows, so the host is told nothing; a tap on another
 * pane of the same window moves only the bar's window chips; a change of window, or Home's float
 * giving the keyboard back, is the full refresh it always was.
 *
 * <p>Compared against what the host was last told, not against the pane before the tap, so a
 * focus change some other path made without telling the host is still passed on by the next tap.
 */
public final class PaneFocusNotice {

    public enum Kind {
        /** The host already shows this pane: nothing to refresh. */
        NONE,
        /** Another pane of the window the host already shows: the window chips, not the bar's glass. */
        PANE,
        /** A different window, or the focus taken from a Home float: everything keyed off the pane. */
        FULL
    }

    private PaneFocusNotice() {}

    /**
     * @param toldSession the session the host was last told was active
     * @param toldWindow  the window it was in
     * @param toldView    the pane view the host holds as active
     * @param session     the session focused now
     * @param window      the window focused now
     * @param view        the pane view focused now
     * @param homeFloatReleased whether this focus took the keyboard back from a Home float
     */
    @NonNull
    public static Kind of(@Nullable Object toldSession, @Nullable Object toldWindow,
                          @Nullable Object toldView, @Nullable Object session,
                          @Nullable Object window, @Nullable Object view,
                          boolean homeFloatReleased) {
        if (homeFloatReleased || window != toldWindow) return Kind.FULL;
        if (session == toldSession && view == toldView) return Kind.NONE;
        return Kind.PANE;
    }
}
