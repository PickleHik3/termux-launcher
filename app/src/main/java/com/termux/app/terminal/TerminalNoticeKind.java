package com.termux.app.terminal;

/**
 * What a terminal event is worth to the user, most urgent first. A window's Android notification
 * shows its most urgent unseen notice, so the order here is the order that decides who wins.
 */
public enum TerminalNoticeKind {
    /** A program is blocked on the user (OSC 7501 blocked). */
    NEEDS_INPUT,
    /** A program's message it marked critical (OSC 99 urgency critical). */
    URGENT_MESSAGE,
    /** A program reports an error (OSC 7501 error). */
    ERROR,
    /** A program reports it is done (OSC 7501 done). */
    FINISHED,
    /** A program's own message (OSC 9, OSC 777, OSC 99). */
    MESSAGE,
    /** The bell. */
    BELL;

    /** Whether this kind is strictly more urgent than {@code other}. */
    public boolean outranks(TerminalNoticeKind other) {
        return ordinal() < other.ordinal();
    }

    /** Whether the notification offers a reply box: only where typing an answer makes sense. */
    public boolean takesReply() {
        return this == NEEDS_INPUT || this == BELL;
    }
}
