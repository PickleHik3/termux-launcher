package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which page is Home: the one place Start in is decided. Start in is the only store — Edit pages'
 * Home marker is read from it, and choosing a card as Home writes it — so the marker and Settings
 * can never disagree. Start in names Home ({@link WallRow#START_IN_HOME}, the Home page Home opens
 * on), the Workspace ({@link WallRow#START_IN_WORKSPACE}, its first terminal window) or the
 * display window ({@link WallRow#START_IN_DISPLAY}).
 *
 * <p>Display is Start in only while the display starts with the launcher: choosing it turns that
 * on, and turning that off (or removing the display's card in Edit pages) falls back to the
 * Workspace when a terminal window is open, else Home. Removing the last terminal window while
 * the Workspace is Home falls back to Home. Pure; the overview, Settings and the activity apply it.
 */
public final class StartInPolicy {

    private StartInPolicy() {}

    /** What choosing Home writes: Start in, and whether the display starts with the launcher. */
    public static final class Choice {
        @NonNull public final String startIn;
        /** The display must start with the launcher for Start in to hold. */
        public final boolean startsDisplay;

        Choice(@NonNull String startIn, boolean startsDisplay) {
            this.startIn = startIn;
            this.startsDisplay = startsDisplay;
        }
    }

    /** A stored value as this version reads it; anything unknown is Home, as it always was. */
    @NonNull
    public static String read(@Nullable String stored) {
        if (WallRow.START_IN_WORKSPACE.equals(stored)) return WallRow.START_IN_WORKSPACE;
        if (WallRow.START_IN_DISPLAY.equals(stored)) return WallRow.START_IN_DISPLAY;
        return WallRow.START_IN_HOME;
    }

    /** Where Start in goes when its page is gone: the Workspace if it has a terminal, else Home. */
    @NonNull
    public static String fallback(boolean hasTerminal) {
        return hasTerminal ? WallRow.START_IN_WORKSPACE : WallRow.START_IN_HOME;
    }

    /**
     * Start in as it stands: what is stored, except Display while the display does not start with
     * the launcher ({@code displayStarts} false: switched off, or a build with no display), which
     * falls back.
     */
    @NonNull
    public static String effective(@Nullable String stored, boolean displayStarts,
                                   boolean hasTerminal) {
        String startIn = read(stored);
        if (WallRow.START_IN_DISPLAY.equals(startIn) && !displayStarts) {
            return fallback(hasTerminal);
        }
        return startIn;
    }

    /** What Start in = {@code startIn} writes beside it: Display turns the display's start on. */
    @NonNull
    public static Choice choose(@Nullable String startIn) {
        String value = read(startIn);
        return new Choice(value, WallRow.START_IN_DISPLAY.equals(value));
    }

    /** What choosing a card of {@code kind} as Home writes. */
    @NonNull
    public static Choice choose(@NonNull PageOverviewPolicy.Kind kind) {
        switch (kind) {
            case TERMINAL: return choose(WallRow.START_IN_WORKSPACE);
            case DISPLAY: return choose(WallRow.START_IN_DISPLAY);
            case HOME_PAGE:
            default: return choose(WallRow.START_IN_HOME);
        }
    }

    /**
     * Start in after Edit pages really removed a card of {@code removed}, with
     * {@code terminalsLeft} terminal windows still open; null when it stands. Removing the display
     * while it is Home falls back; removing a terminal window falls back only when none is left
     * and the Workspace was Home.
     */
    @Nullable
    public static String afterRemoval(@Nullable String startIn,
                                      @NonNull PageOverviewPolicy.Kind removed, int terminalsLeft) {
        String value = read(startIn);
        boolean hasTerminal = terminalsLeft > 0;
        switch (removed) {
            case DISPLAY:
                return WallRow.START_IN_DISPLAY.equals(value) ? fallback(hasTerminal) : null;
            case TERMINAL:
                return WallRow.START_IN_WORKSPACE.equals(value) && !hasTerminal
                    ? fallback(false) : null;
            case HOME_PAGE:
            default:
                // Home always keeps a page; the Home page it opens on is clamped where it is read.
                return null;
        }
    }

    /**
     * Whether a card wears the Home marker under Start in {@code effective}
     * ({@link #effective}): the Home page Home opens on ({@code defaultPage}), the first terminal
     * window ({@code firstTerminal}), or the display window.
     */
    public static boolean isHome(@NonNull PageOverviewPolicy.Kind kind, boolean defaultPage,
                                 boolean firstTerminal, @Nullable String effective) {
        String value = read(effective);
        switch (kind) {
            case HOME_PAGE: return defaultPage && WallRow.START_IN_HOME.equals(value);
            case TERMINAL: return firstTerminal && WallRow.START_IN_WORKSPACE.equals(value);
            case DISPLAY: return WallRow.START_IN_DISPLAY.equals(value);
            default: return false;
        }
    }
}
