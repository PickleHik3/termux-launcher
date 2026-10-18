package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.WallRow;

/**
 * What a start does with the arrangement — the row's windows, their split trees and the Home
 * floats — when the activity finds the terminal service (wall rework slice 9). Pure: the activity
 * gathers the facts and does what the answer names.
 *
 * <p>The arrangement is kept twice. While the process lives, the pane layout (keyed by each
 * shell's handle) comes back with the activity's instance state, or from the copy the process
 * holds when the activity went away without one. Across a process kill, the autosave in the
 * Saved sessions directory holds it in the Saved sessions model, which a cold start rebuilds
 * with fresh shells in the saved working directories.
 */
public final class ArrangementRecovery {

    private ArrangementRecovery() {}

    /** What the autosave slot holds. */
    public enum Saved {
        /** No autosave: a first start, or one from before autosave existed. */
        NONE,
        /** The user's last arrangement was empty: everything closed, or Exit. */
        EMPTY,
        /** Windows and floats to rebuild. */
        ARRANGEMENT
    }

    /** What the start does. */
    public enum Start {
        /** Live shells and a saved pane layout: the layout comes back around the same shells. */
        RECREATE,
        /** Live shells and no layout: each shell is adopted as a window of its own, as before. */
        ADOPT,
        /** No live shell on a cold start, and an autosaved arrangement: rebuild it. */
        REBUILD,
        /** No live shell and none wanted: the Workspace stays empty and Home shows. */
        EMPTY,
        /** No live shell: one fresh shell, the bootstrap, Welcome and the tour riding on it. */
        FRESH
    }

    /**
     * The start for these facts.
     *
     * @param hasLayout a pane layout keyed by shell handles is at hand (instance state, or the
     *                  process's copy)
     * @param liveSessions the terminal service holds at least one shell
     * @param coldProcess this process has not settled its first arrangement yet
     * @param launchAsksForSession the launch names a shell of its own (New session, failsafe)
     * @param saved what the autosave slot holds; only read on a cold start with no live shell
     * @param startIn the Start in preference ({@link WallRow#START_IN_HOME} or
     *                {@link WallRow#START_IN_WORKSPACE})
     */
    @NonNull
    public static Start decide(boolean hasLayout, boolean liveSessions, boolean coldProcess,
                               boolean launchAsksForSession, @NonNull Saved saved,
                               @Nullable String startIn) {
        if (liveSessions) return hasLayout ? Start.RECREATE : Start.ADOPT;
        // An empty service in a process that already settled is the user's empty Workspace.
        if (!coldProcess) return launchAsksForSession ? Start.FRESH : Start.EMPTY;
        // A launch that names its shell (and above all the failsafe one) gets just that.
        if (launchAsksForSession) return Start.FRESH;
        switch (saved) {
            case ARRANGEMENT:
                return Start.REBUILD;
            case EMPTY:
                // Exactly what was left: nothing. Start in = Workspace still wants a window.
                return WallRow.START_IN_WORKSPACE.equals(startIn) ? Start.FRESH : Start.EMPTY;
            case NONE:
            default:
                return Start.FRESH;
        }
    }

    /**
     * The Start in a start goes by: the preference, unless there is no Home to show ({@code
     * hasHome} false: no Home pages and not the phone's home app, where closing the last shell
     * ends the app, {@link WallRow#lastShellClosedShowsHome}). An empty Workspace would then leave
     * nothing on screen, so the start goes by the Workspace.
     */
    @Nullable
    public static String effectiveStartIn(@Nullable String startIn, boolean hasHome) {
        return hasHome ? startIn : WallRow.START_IN_WORKSPACE;
    }

    /**
     * Whether a rebuilt arrangement also gets one fresh shell window: Start in = Workspace and
     * the rebuild left the row with no window (its windows were all Home floats).
     */
    public static boolean rebuildWantsFreshWindow(int rowWindows, @Nullable String startIn) {
        return rowWindows <= 0 && WallRow.START_IN_WORKSPACE.equals(startIn);
    }
}
