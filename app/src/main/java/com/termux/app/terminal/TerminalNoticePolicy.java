package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.KittyNotification;

/**
 * Whether a terminal event reaches the phone's notification shade, and whether it makes a sound.
 * Pure, so every combination is tested without Android.
 */
public final class TerminalNoticePolicy {

    private TerminalNoticePolicy() {
    }

    public enum Action {
        /** Leave the shade as it is. */
        NOTHING,
        /** The window has nothing up: post. */
        POST,
        /** The window has a notification: update it in place. */
        REPLACE
    }

    /** What to do and whether the update should alert (sound, heads-up) rather than be silent. */
    public static final class Decision {
        public final Action action;
        public final boolean alert;

        Decision(Action action, boolean alert) {
            this.action = action;
            this.alert = alert;
        }
    }

    private static final Decision NOTHING = new Decision(Action.NOTHING, false);
    private static final Decision POST = new Decision(Action.POST, true);
    private static final Decision REPLACE = new Decision(Action.REPLACE, true);

    /**
     * @param terminalShowing the Terminal page is showing at rest (whichever pane is in front)
     * @param onlyWhileLauncherAway the program asked for OSC 99 occasion "invisible"
     * @param current the window's record now, null when it has none
     * @param messageId the OSC 99 id of this notice, empty when none
     * @param bellSpent whether this window's bell has already notified since it was last seen
     */
    @NonNull
    public static Decision decide(@NonNull TerminalNoticeKind kind, boolean launcherVisible,
                                  boolean terminalShowing, boolean onlyWhileLauncherAway,
                                  @Nullable TerminalNoticeRecords.Record current,
                                  @NonNull String messageId, boolean bellSpent) {
        // Seen: with the Terminal page on screen the in-app notice and the window pill are enough,
        // for every pane. An occasion can narrow this (invisible) but "always" never widens it.
        if (launcherVisible && terminalShowing) return NOTHING;
        if (onlyWhileLauncherAway && launcherVisible) return NOTHING;
        if (kind == TerminalNoticeKind.BELL && bellSpent) return NOTHING;
        if (current == null) return POST;
        // The program named this message: the newer one replaces the older whatever their rank.
        boolean message = kind == TerminalNoticeKind.MESSAGE || kind == TerminalNoticeKind.URGENT_MESSAGE;
        if (message && !messageId.isEmpty() && messageId.equals(current.messageId)) return REPLACE;
        if (kind == current.kind || kind.outranks(current.kind)) return REPLACE;
        // A lower kind does not stand in for the one already up.
        return NOTHING;
    }

    /**
     * The OSC 99 occasion rule, for the in-app fallback: "invisible" shows only with the launcher
     * off screen, "unfocused" unless the Terminal page is showing (the same unseen test the shade
     * uses), anything else always.
     */
    public static boolean occasionAllows(@Nullable String occasion, boolean launcherVisible,
                                         boolean terminalShowing) {
        if (KittyNotification.OCCASION_INVISIBLE.equals(occasion)) return !launcherVisible;
        if (KittyNotification.OCCASION_UNFOCUSED.equals(occasion)) return !(launcherVisible && terminalShowing);
        return true;
    }
}
