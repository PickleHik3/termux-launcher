package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.KittyNotification;

/** Everything the Android side needs to build one window's notification. Immutable. */
public final class TerminalNoticeContent {
    public final TerminalNoticeKind kind;
    public final String windowKey;
    public final String paneHandle;
    /** The pane, for the Android side to route a tap or reply back to. */
    public final Object pane;
    /** The program or window name for the title; may be empty. */
    public final String name;
    /** OSC 99 / 777 title, empty when none. */
    public final String messageTitle;
    /** The program's message, empty when none. */
    public final String text;
    /** The window's label, or empty. */
    public final String subText;
    /** Whether this update should make a sound rather than be silent. */
    public final boolean alert;
    /** The foreground process group a reply is guarded by; 0 when the notice takes no reply. */
    public final int replyPgid;
    /** That process's name; null when the notice takes no reply. */
    @Nullable public final String replyProgramName;
    /** The OSC 99 request (buttons, timeout, urgency, focus, id), else null. */
    @Nullable public final KittyNotification kitty;

    public TerminalNoticeContent(@NonNull TerminalNoticeKind kind, @NonNull String windowKey,
                                 @NonNull String paneHandle, @NonNull Object pane, @NonNull String name,
                                 @NonNull String messageTitle, @NonNull String text,
                                 @NonNull String subText, boolean alert, int replyPgid,
                                 @Nullable String replyProgramName, @Nullable KittyNotification kitty) {
        this.kind = kind;
        this.windowKey = windowKey;
        this.paneHandle = paneHandle;
        this.pane = pane;
        this.name = name;
        this.messageTitle = messageTitle;
        this.text = text;
        this.subText = subText;
        this.alert = alert;
        this.replyPgid = replyPgid;
        this.replyProgramName = replyProgramName;
        this.kitty = kitty;
    }
}
