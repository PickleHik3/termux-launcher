package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The interface for communication between {@link TerminalSession} and its client. It is used to
 * send callbacks to the client when {@link TerminalSession} changes or for sending other
 * back data to the client like logs.
 */
public interface TerminalSessionClient {

    void onTextChanged(@NonNull TerminalSession changedSession);

    void onTitleChanged(@NonNull TerminalSession changedSession);

    void onSessionFinished(@NonNull TerminalSession finishedSession);

    void onCopyTextToClipboard(@NonNull TerminalSession session, String text);

    void onPasteTextFromClipboard(@Nullable TerminalSession session);

    /**
     * Answer an OSC 52 clipboard read query. The default has nothing to offer, so it answers as
     * if the clipboard were empty rather than leaving the query unanswered.
     */
    default String onReadTextFromClipboard(@NonNull TerminalSession session) {
        return null;
    }

    void onBell(@NonNull TerminalSession session);

    /**
     * The program asked for the user with a message (a terminal notification escape). Like a bell
     * with words: the default ignores it, so clients that only care about bells need not change.
     */
    default void onNotification(@NonNull TerminalSession session, String title, String body) {
    }

    /**
     * The program asked for the user with a whole notification request — a name to close or
     * replace it by, how urgent it is, whether it wants to hear that the user tapped it.
     *
     * <p>The default passes the words on to {@link #onNotification}, so a client that only knows
     * how to show a title and a body needs no change.
     */
    default void onKittyNotification(@NonNull TerminalSession session,
                                     @NonNull KittyNotification notification) {
        onNotification(session, notification.getTitle(), notification.getBody());
    }

    /** The program asked for the notification it named earlier to be taken down. */
    default void onKittyNotificationClose(@NonNull TerminalSession session, @NonNull String id) {
    }

    /**
     * The program asked for a mouse pointer shape, by its CSS/X11 name, or with null for the
     * terminal's own again. The default has no pointer to change.
     */
    default void onPointerShapeChanged(@NonNull TerminalSession session, @Nullable String shape) {
    }

    void onColorsChanged(@NonNull TerminalSession session);

    void onTerminalCursorStateChange(boolean state);

    void setTerminalShellPid(@NonNull TerminalSession session, int pid);

    Integer getTerminalCursorStyle();

    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);
}
