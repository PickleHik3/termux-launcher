package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.ProgramStatus;

import java.util.List;

/**
 * What a window's pill says when a program in it reports its own status: which pane's record wins
 * across a window's panes, and the words. Pure, so both are tested without a view.
 */
public final class ProgramStatusLabel {

    /** The longest name shown before the cut, in code points. */
    static final int NAME_MAX = 12;

    private ProgramStatusLabel() {}

    /**
     * The index of the record to show across a window's panes (null entries are panes with none):
     * blocked, error, working, done in that order, the later update breaking a tie. Idle records
     * never win. Returns -1 when no pane has anything to show.
     */
    public static int pickMostUrgent(@NonNull List<ProgramStatus> perPane) {
        int best = -1;
        for (int i = 0; i < perPane.size(); i++) {
            ProgramStatus r = perPane.get(i);
            if (r == null || urgency(r.state) == 0) continue;
            if (best < 0) { best = i; continue; }
            ProgramStatus b = perPane.get(best);
            int a = urgency(r.state), c = urgency(b.state);
            if (a > c || (a == c && r.updateSeq > b.updateSeq)) best = i;
        }
        return best;
    }

    private static int urgency(@NonNull ProgramStatus.State s) {
        switch (s) {
            case BLOCKED: return 4;
            case ERROR: return 3;
            case WORKING: return 2;
            case DONE: return 1;
            default: return 0;
        }
    }

    /** The pill's reading of {@code record}, or null for an idle one. */
    @Nullable
    public static TerminalWindowBar.WindowItem.Status statusFor(
        @NonNull ProgramStatus record, @Nullable String resolvedApp, @NonNull String windowTitle) {
        TerminalWindowBar.WindowItem.Status.Kind kind = kindOf(record.state);
        if (kind == null) return null;
        return new TerminalWindowBar.WindowItem.Status(kind, text(record, resolvedApp, windowTitle));
    }

    @Nullable
    static TerminalWindowBar.WindowItem.Status.Kind kindOf(@NonNull ProgramStatus.State state) {
        switch (state) {
            case WORKING: return TerminalWindowBar.WindowItem.Status.Kind.WORKING;
            case BLOCKED: return TerminalWindowBar.WindowItem.Status.Kind.BLOCKED;
            case DONE: return TerminalWindowBar.WindowItem.Status.Kind.DONE;
            case ERROR: return TerminalWindowBar.WindowItem.Status.Kind.ERROR;
            default: return null;
        }
    }

    /** "name word", plus " NN%" for working and blocked records that carry a percentage. */
    @NonNull
    public static String text(@NonNull ProgramStatus record, @Nullable String resolvedApp,
                              @NonNull String windowTitle) {
        String name = cleanName(firstNonEmpty(resolvedApp, record.title, windowTitle));
        StringBuilder out = new StringBuilder(name);
        if (out.length() > 0) out.append(' ');
        out.append(word(record.state));
        boolean percent = record.state == ProgramStatus.State.WORKING
            || record.state == ProgramStatus.State.BLOCKED;
        if (percent && record.progress >= 0) out.append(' ').append(record.progress).append('%');
        return out.toString();
    }

    /** The lower-case word that follows a name. */
    @NonNull
    static String word(@NonNull ProgramStatus.State state) {
        switch (state) {
            case WORKING: return "working";
            case BLOCKED: return "needs input";
            case DONE: return "done";
            case ERROR: return "error";
            default: return "";
        }
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String a, @Nullable String b, @Nullable String c) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return c == null ? "" : c;
    }

    /** Drops control characters, then cuts to {@link #NAME_MAX} code points with a trailing ellipsis. */
    @NonNull
    static String cleanName(@NonNull String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x20 || (cp >= 0x7f && cp <= 0x9f)) continue;
            out.appendCodePoint(cp);
        }
        String clean = out.toString();
        if (clean.codePointCount(0, clean.length()) <= NAME_MAX) return clean;
        return clean.substring(0, clean.offsetByCodePoints(0, NAME_MAX)) + "…";
    }

    /** A message as one line of at most {@code max} code points, an ellipsis marking a cut. */
    @NonNull
    static String oneLine(@Nullable String message, int max) {
        if (message == null) return "";
        StringBuilder out = new StringBuilder(message.length());
        boolean gap = false;
        for (int i = 0; i < message.length(); ) {
            int cp = message.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x20 || (cp >= 0x7f && cp <= 0x9f) || cp == 0x2028 || cp == 0x2029) {
                gap = out.length() > 0;
                continue;
            }
            if (gap) { out.append(' '); gap = false; }
            out.appendCodePoint(cp);
        }
        String line = out.toString().trim();
        if (line.codePointCount(0, line.length()) <= max) return line;
        return line.substring(0, line.offsetByCodePoints(0, max)) + "…";
    }
}
