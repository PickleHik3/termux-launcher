package com.termux.app.terminal;

import androidx.annotation.Nullable;

/** Naming policy for the app's tmux-style sessions. */
public final class WindowSessionName {

    /**
     * Two copy mirrors quote this number and must move in lockstep with it:
     * {@code R.string.title_rename_window_session} and the {@code window.rename} tool
     * description/argument text in {@code LauncherToolRegistry}.
     */
    public static final int MAX_CODE_POINTS = 8;

    private WindowSessionName() {}

    /** Trim user input and cap it without splitting a Unicode surrogate pair. */
    @Nullable
    public static String normalize(@Nullable CharSequence value) {
        if (value == null) return null;
        String name = value.toString().trim();
        if (name.isEmpty()) return null;
        int codePointCount = name.codePointCount(0, name.length());
        if (codePointCount <= MAX_CODE_POINTS) return name;
        return name.substring(0, name.offsetByCodePoints(0, MAX_CODE_POINTS));
    }
}
