package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a Display page is on the wall (ADR 0016): each Display page is a cell of its own, and the
 * one display view is bound to one of them at a time ({@link PaneWallLayout#boundPageId}). Only the
 * bound page, with the display running, is live; every other Display page stands as a still: its
 * last picture once it has one, else a card naming what it shows ({@link PageStillPolicy}, the
 * one rule for every kind). Landing on a Display page starts the display; leaving keeps it
 * running.
 *
 * <p>Pure and tested; the activity and the display frame apply the answers.
 */
public final class DisplayPagePolicy {

    private DisplayPagePolicy() {}

    /** The card's name for an unbound page, or a bound one whose app could not be named. */
    public static final String UNBOUND_NAME = "Display";

    /**
     * What Display page {@code pageId} shows, with the display view bound to {@code boundPageId}
     * (-1: none) and the display {@code running} or not.
     *
     * @param hasPicture the page has a picture kept from when it was last live
     */
    @NonNull
    public static PageStillPolicy.Shows shows(int pageId, int boundPageId, boolean running,
                                              boolean hasPicture) {
        return PageStillPolicy.shows(pageId, boundPageId, running, hasPicture);
    }

    /**
     * The name on a Display page's card: the bound app's ({@code appName}, as the caller resolved
     * the binding), else {@link #UNBOUND_NAME}. The page's own name is the chip's, not the card's.
     */
    @NonNull
    public static String cardName(@NonNull WallPages.Page page, @Nullable String appName) {
        if (page.binding != null && appName != null && !appName.trim().isEmpty()) return appName;
        return UNBOUND_NAME;
    }

    /**
     * Whether landing on a page of {@code kind} starts the display: any Display page, while the
     * display is neither running nor already on its way up.
     */
    public static boolean startsOnLanding(@NonNull WallPages.PageKind kind, boolean running,
                                          boolean starting) {
        return kind == WallPages.PageKind.DISPLAY && !running && !starting;
    }

    /** What landing on a Display page does (A3c). */
    public enum Landing {
        /** Nothing: the display is on its way, the page is routed, or its app is gone. */
        NOTHING,
        /** An unbound page: start the display as before. */
        START_DISPLAY,
        /** The bound app has a window on the running display: bring it forward. */
        RAISE_APP,
        /** The bound app has no window yet: launch it, starting the display first if need be. */
        RUN_APP
    }

    /**
     * What landing on a Display page does. A page with no binding only starts the display. A
     * bound page raises its app's window, or runs the app when it has none (the runner queues it
     * behind a display that is not up yet). A binding whose app is gone, a landing the app drawer
     * itself routed here ({@code routedByRunner}: the runner is already running the app), and a
     * display that is on its way up with nothing queued all do nothing.
     */
    @NonNull
    public static Landing landing(@Nullable String binding, boolean appFound, boolean running,
                                  boolean starting, boolean appHasWindow, boolean routedByRunner) {
        if (binding == null) {
            return startsOnLanding(WallPages.PageKind.DISPLAY, running, starting)
                ? Landing.START_DISPLAY : Landing.NOTHING;
        }
        if (!appFound || routedByRunner) return Landing.NOTHING;
        if (running) return appHasWindow ? Landing.RAISE_APP : Landing.RUN_APP;
        return starting ? Landing.NOTHING : Landing.RUN_APP;
    }

    /**
     * How long after the app drawer routes a Linux app to a page the landing there leaves the
     * app to the runner, in ms: past it a landing is the user's own.
     */
    public static final long ROUTED_LANDING_MS = 5000L;

    /** Whether a landing on {@code pageId} is the one the runner routed at {@code routedAtMs}. */
    public static boolean isRoutedLanding(int pageId, int routedPageId, long routedAtMs,
                                          long nowMs) {
        return routedPageId >= 0 && pageId == routedPageId
            && nowMs - routedAtMs <= ROUTED_LANDING_MS && nowMs >= routedAtMs;
    }

    /**
     * The Display page the home press and the cold start land on: home itself when it is a
     * Display page; -1 when home is anything else, or the old display window.
     */
    public static int homeDisplayPage(@Nullable WallPages pages) {
        WallPages.Page home = pages == null ? null : pages.home();
        return home != null && home.kind == WallPages.PageKind.DISPLAY ? home.id : -1;
    }
}
