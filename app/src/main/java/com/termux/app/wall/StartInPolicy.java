package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which page is Home, where a cold start and a home press land: the one place Start in is decided.
 * Start in is the only store — Edit pages' Home marker is read from it, and choosing a card as
 * Home writes it — so the marker and Settings can never disagree. Start in names Home ({@link WallRow#START_IN_HOME}, the Home page Home opens
 * on), the Terminal ({@link WallRow#START_IN_TERMINAL}, its first terminal window) or the
 * display window ({@link WallRow#START_IN_DISPLAY}).
 *
 * <p>Display is Start in only while the display starts with the launcher: choosing it turns that
 * on, and turning that off (or removing the display's card in Edit pages) falls back to the
 * Terminal when a terminal window is open, else Home. Removing the last terminal window while
 * the Terminal is Home falls back to Home. Pure; the overview, Settings and the activity apply it.
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
        if (WallRow.START_IN_TERMINAL.equals(stored)) return WallRow.START_IN_TERMINAL;
        if (WallRow.START_IN_DISPLAY.equals(stored)) return WallRow.START_IN_DISPLAY;
        if (WallRow.START_IN_ASSISTANT.equals(stored)) return WallRow.START_IN_ASSISTANT;
        return WallRow.START_IN_HOME;
    }

    /** Where Start in goes when its page is gone: the Terminal if it has a terminal, else Home. */
    @NonNull
    public static String fallback(boolean hasTerminal) {
        return hasTerminal ? WallRow.START_IN_TERMINAL : WallRow.START_IN_HOME;
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
            case TERMINAL: return choose(WallRow.START_IN_TERMINAL);
            case DISPLAY: return choose(WallRow.START_IN_DISPLAY);
            case HOME_PAGE:
            default: return choose(WallRow.START_IN_HOME);
        }
    }

    /**
     * The Home page the Home half opens on under Start in {@code effective}, counting from 0: the
     * page Edit pages chose ({@code defaultPage}) while Home is Start in, else page 1. A choice
     * made on a Home page that no longer wears the marker is not kept behind it: with a window as
     * Home, the Home key on the extra keys and a cold start's Home half both open on page 1.
     */
    public static int homePage(int defaultPage, @Nullable String effective) {
        return WallRow.START_IN_HOME.equals(read(effective)) ? Math.max(0, defaultPage) : 0;
    }

    /**
     * Whether a card wears the Home marker under Start in {@code effective}
     * ({@link #effective}): the Home page Home opens on ({@code defaultPage}), the Terminal page,
     * or the display window.
     */
    public static boolean isHome(@NonNull PageOverviewPolicy.Kind kind, boolean defaultPage,
                                 @Nullable String effective) {
        String value = read(effective);
        switch (kind) {
            case HOME_PAGE: return defaultPage && WallRow.START_IN_HOME.equals(value);
            case TERMINAL: return WallRow.START_IN_TERMINAL.equals(value);
            case DISPLAY: return WallRow.START_IN_DISPLAY.equals(value);
            default: return false;
        }
    }
}
