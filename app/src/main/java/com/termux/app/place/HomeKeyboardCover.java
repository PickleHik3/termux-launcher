package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.wall.PaneWallPolicy;

/**
 * The one owner of "does the keyboard lift this page's rows", and of the numbers that follow from
 * it (#309). On Home the keyboard, the in-app one in any form or the system's, draws over the
 * dock: the rows stand exactly where they stand with the keyboard down. On the Terminal (and the
 * Display, which is the Terminal with a display window) the rows keep riding the keyboard.
 *
 * <p>The in-app keyboard stays laid out while it is down and the container is drawn
 * {@code keyboardSharePx} lower then ({@link StackScene#keyboardTranslationPx}), so "rows at their
 * keyboard-down rect" is a draw offset and no layout: the renderer and the rest model both read
 * these answers, so the frame a crossing draws and the rest it lands on are one number.
 *
 * <p>Pure, so the answers are tested without a window.
 */
public final class HomeKeyboardCover {

    private HomeKeyboardCover() {}

    /**
     * Whether a keyboard lifts the rows {@code page} stands, for a Home whose keyboard is raised
     * on demand: every page but Home. Same as {@link #liftsRows(PageKind, boolean)} with
     * {@code false}.
     */
    public static boolean liftsRows(@NonNull PageKind page) {
        return liftsRows(page, false);
    }

    /**
     * Whether a keyboard lifts the rows {@code page} stands. Every page but Home does. A Home
     * whose keyboard is always on ({@code homeKeyboardAlwaysOn}) keeps the dock riding it, as
     * before #309: the cover applies only when Home's keyboard is raised on demand.
     */
    public static boolean liftsRows(@NonNull PageKind page, boolean homeKeyboardAlwaysOn) {
        return homeKeyboardAlwaysOn || !PaneWallPolicy.isHomePlace(page);
    }

    /**
     * How far below the container a side's rows and glass are drawn so they stand at their
     * keyboard-down rect: the keyboard's laid-out height less the share the container already
     * sits lower by. 0 for a side whose rows the keyboard lifts.
     */
    public static float rowsDropPx(boolean lifts, int keyboardLaidOutPx, float keyboardSharePx) {
        if (lifts) return 0f;
        return Math.max(0f, keyboardLaidOutPx - Math.max(0f, keyboardSharePx));
    }

    /**
     * {@link #rowsDropPx(boolean, int, float)} for a keyboard laid out with its foot under the
     * keys ({@code keyboardFootPx}): only the keys count ({@link BottomInsets#keyboardKeysPx}),
     * since a down keyboard sinks the container by its keys alone and the rest's answer drops
     * Home's rows by the keys ({@code RestCards#rowsDropPx}). With the keyboard down the drop is
     * 0, so nothing of Home's rows or host is cut at the keyboard's top.
     */
    public static float rowsDropPx(boolean lifts, int keyboardLaidOutPx, int keyboardFootPx,
                                   float keyboardSharePx) {
        return rowsDropPx(lifts, BottomInsets.keyboardKeysPx(keyboardLaidOutPx, keyboardFootPx),
            keyboardSharePx);
    }

    /**
     * The drop for rows both sides share: Home's {@code homeShare} of the scene (0 on the
     * Terminal, 1 on Home) of Home's {@code homeDropPx}, so they blend through a crossing.
     */
    public static float sharedDropPx(float homeShare, float homeDropPx) {
        return Math.max(0f, Math.min(1f, homeShare)) * Math.max(0f, homeDropPx);
    }

    /**
     * How far the container is drawn back down on Home so the system keyboard does not raise it:
     * the root's padding by the keyboard ({@code systemImeTakePx}), the stack's margin lift
     * ({@code marginImeLiftPx}) and the ride's animated offset ({@code rideOffsetPx}), which
     * together are what raises it. 0 for a page whose rows the keyboard lifts.
     */
    public static float imeStandPx(boolean lifts, int systemImeTakePx, int marginImeLiftPx,
                                   float rideOffsetPx) {
        if (lifts) return 0f;
        return Math.max(0f, systemImeTakePx + marginImeLiftPx + rideOffsetPx);
    }

    /**
     * The part of a covered Home layer under the keyboard's drawn top, which is cut off so only
     * one coat of glass shows there.
     */
    public static int coverCutPx(float layerBottomPx, float keyboardTopPx) {
        return Math.max(0, Math.round(layerBottomPx - keyboardTopPx));
    }
}
