package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Keeps a reply typed into a notification from reaching the wrong program: it is written only when
 * the program that asked is still the pane's foreground process. Pure.
 */
public final class TerminalReplyGuard {

    private TerminalReplyGuard() {
    }

    /**
     * Whether the reply may be typed. The captured process group and name are those seen when the
     * notification was posted; a missing capture never matches, so an unknown asker gets nothing.
     */
    public static boolean shouldSend(int capturedPgid, @Nullable String capturedName,
                                     int nowPgid, @Nullable String nowName) {
        return capturedPgid > 0
            && capturedName != null && !capturedName.isEmpty()
            && nowPgid == capturedPgid
            && capturedName.equals(nowName);
    }

    /**
     * What to write for the typed text: line breaks become spaces so it stays one line, other
     * control characters are dropped, and a Return ends it. Empty when nothing is left, in which
     * case nothing is sent.
     */
    @NonNull
    public static String replyText(@Nullable String typed) {
        if (typed == null) return "";
        StringBuilder out = new StringBuilder(typed.length() + 1);
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (c == '\r') {
                out.append(' ');
                if (i + 1 < typed.length() && typed.charAt(i + 1) == '\n') i++;
            } else if (c == '\n') {
                out.append(' ');
            } else if (c == '\t' || (c >= 0x20 && c != 0x7f)) {
                out.append(c);
            }
        }
        int end = out.length();
        while (end > 0 && Character.isWhitespace(out.charAt(end - 1))) end--;
        if (end == 0) return "";
        out.setLength(end);
        return out.append('\r').toString();
    }
}
