package com.termux.app.terminal;

import androidx.annotation.Nullable;

/**
 * Naming policy for the three things the terminal lets a user name, in the vocabulary the UI uses:
 * a <b>session</b> (a drawer row, holding windows), a <b>window</b> (a window-bar tab, holding
 * panes) and a <b>pane</b> (one shell).
 *
 * <p>The caps differ because the surfaces do. A session name rides in the collapsed status bar's
 * indicator, which has room for a word; a window name shares its tab with a glyph and sits beside
 * every sibling tab; a pane name is only ever read in a list. All three are code-point caps, so a
 * name is never cut through a surrogate pair.
 */
public final class TerminalNamePolicy {

    /**
     * Two copy mirrors quote this number and must move in lockstep with it:
     * {@code R.string.title_rename_session} and the {@code session.rename} tool
     * description/argument text in {@code LauncherToolRegistry}.
     */
    public static final int SESSION_MAX_CODE_POINTS = 8;
    public static final int WINDOW_MAX_CODE_POINTS = 14;
    public static final int PANE_MAX_CODE_POINTS = 32;

    private TerminalNamePolicy() {}

    @Nullable
    public static String normalizeSession(@Nullable CharSequence value) {
        return normalize(value, SESSION_MAX_CODE_POINTS);
    }

    @Nullable
    public static String normalizeWindow(@Nullable CharSequence value) {
        return normalize(value, WINDOW_MAX_CODE_POINTS);
    }

    @Nullable
    public static String normalizePane(@Nullable CharSequence value) {
        return normalize(value, PANE_MAX_CODE_POINTS);
    }

    /** Trim user input and cap it without splitting a Unicode surrogate pair. Blank becomes null. */
    @Nullable
    public static String normalize(@Nullable CharSequence value, int maxCodePoints) {
        if (value == null) return null;
        String name = value.toString().trim();
        if (name.isEmpty()) return null;
        int codePointCount = name.codePointCount(0, name.length());
        if (codePointCount <= maxCodePoints) return name;
        return name.substring(0, name.offsetByCodePoints(0, maxCodePoints));
    }

    /** The cap that applies to {@code target}, for the editor's own length limit. */
    public static int maxCodePointsFor(@Nullable TerminalRenameTarget target) {
        if (target == null) return PANE_MAX_CODE_POINTS;
        switch (target) {
            case SESSION: return SESSION_MAX_CODE_POINTS;
            case WINDOW: return WINDOW_MAX_CODE_POINTS;
            case PANE:
            default: return PANE_MAX_CODE_POINTS;
        }
    }
}
