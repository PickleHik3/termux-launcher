package com.termux.app.x11;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Whether the display's own view may hold the focus, and so the keys. Typing anywhere else in the
 * launcher — the terminal, and every focusless overlay over it, the palette included — reaches the
 * app only through a focused terminal view. A display view that keeps the focus after its window
 * has left the screen, or that is removed or hidden while holding it (Android then hands the
 * focus to the first focusable view it finds), leaves typed keys with nowhere to go.
 *
 * <p>The display view also asks for the focus on its own whenever the activity's window regains
 * focus — a dialog closing, the Stop confirmation among them — even from a hidden page. It may
 * therefore be focusable only while its window is the one at rest: {@link #admit} on arrival,
 * {@link #release} when the wall leaves it and before the window is taken down.
 */
public final class DisplayViewFocus {

    private DisplayViewFocus() {}

    /** The display window is the one at rest: its view may take the focus and the keys. */
    public static void admit(@NonNull View display) {
        display.setFocusable(true);
        display.setFocusableInTouchMode(true);
    }

    /**
     * The display window left the screen, or is about to go. Unless a view that types — a
     * terminal — already holds the focus, {@code handBack} gives it to the active one first, so
     * the keys never stay with the display or with whatever view Android found; then the display
     * view stops being able to take them back.
     *
     * @param typesHere whether a focused view is one the keys belong to
     * @param handBack  focuses the active terminal pane
     */
    public static void release(@NonNull View display, @NonNull Predicate<View> typesHere,
                               @NonNull Runnable handBack) {
        View focused = display.getRootView().findFocus();
        if (focused == null || display.hasFocus() || !typesHere.test(focused)) handBack.run();
        display.setFocusableInTouchMode(false);
        display.setFocusable(false);
        // A display that still held the keys — the hand-back did not land — loses them just now,
        // and Android gives them to the first focusable view it finds, or to nobody: once more.
        View now = display.getRootView().findFocus();
        if (now == null || !typesHere.test(now)) handBack.run();
    }

    /**
     * Whether {@code view} really holds its window's focus: attached, and the one view the
     * window's root finds focused. Its own focused flag is not enough — a view focused while it
     * was out of the window says so too, and no key ever reaches it.
     */
    public static boolean holdsKeys(@Nullable View view) {
        return view != null && view.isAttachedToWindow() && view.getRootView().findFocus() == view;
    }

    /**
     * Gives the keys to {@code terminal} through {@code request} (its quiet focus request) and
     * says whether they landed. At the moment the display goes they may not: the terminal can be
     * out of the window, or its page about to change under it.
     */
    public static boolean handBackTo(@Nullable View terminal, @NonNull Runnable request) {
        if (terminal == null) return false;
        if (holdsKeys(terminal)) return true;
        request.run();
        return holdsKeys(terminal);
    }

    /**
     * Runs {@code attempt} after {@code anchor}'s next layout pass, and after the one after that
     * while it answers false, {@code attempts} times at most; {@code exhausted} runs when the last
     * one answered false too. A layout is asked for each time, so a retry does not wait for
     * something else to move.
     */
    public static void retryAfterLayout(@NonNull View anchor, int attempts,
                                        @NonNull BooleanSupplier attempt,
                                        @NonNull Runnable exhausted) {
        if (attempts <= 0) {
            exhausted.run();
            return;
        }
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    ViewTreeObserver live = anchor.getViewTreeObserver();
                    if (live.isAlive()) live.removeOnGlobalLayoutListener(this);
                    if (!attempt.getAsBoolean())
                        retryAfterLayout(anchor, attempts - 1, attempt, exhausted);
                }
            });
        anchor.requestLayout();
    }

    /** Who holds the window's focus when a key arrives. */
    public enum Holder {
        /** Nobody: the key would fall through to the activity and be lost. */
        NONE,
        /** The display's view, while its window is not the one at rest (or is gone). */
        DISPLAY_AWAY,
        /** The display's view on its own window at rest: the keys are X's. */
        DISPLAY_SHOWING,
        /** Any other view: a terminal on screen, a text field. */
        OTHER,
        /** A terminal view that is not on screen (its window minimized): it types nowhere. */
        TERMINAL_HIDDEN
    }

    /** Where the activity sends a key event. */
    public enum Route {
        /** The ordinary path: the focused view, else the activity. */
        PASS,
        /**
         * The active terminal view's key handler, which offers the key to the palette and every
         * other focusless overlay first — the path it takes when the terminal holds the focus.
         */
        TERMINAL,
        /** No terminal to type into: the overlays alone (the palette, a sheet, the drawer). */
        OVERLAYS
    }

    /**
     * The safety net under {@link #release}: when no view holds the keys, or the one that does is
     * a display view whose window is not on screen, a typed key still reaches what is in front.
     * Back keeps its own route — the activity's, which closes the overlays in order — always.
     *
     * @param terminalTypes whether there is an active terminal view with an emulator to type into
     */
    @NonNull
    public static Route routeFor(@NonNull Holder holder, int keyCode, boolean terminalTypes) {
        if (holder == Holder.OTHER || holder == Holder.DISPLAY_SHOWING) return Route.PASS;
        if (keyCode == KeyEvent.KEYCODE_BACK) return Route.PASS;
        return terminalTypes ? Route.TERMINAL : Route.OVERLAYS;
    }

    /**
     * {@link #routeFor(Holder, int, boolean)} with the two things a hidden terminal changes. An
     * open search surface (the drawer's search, the palette) owns the keys whatever holds the
     * focus and whether or not a terminal is on screen: it is offered them directly, never through
     * a terminal that a minimized window keeps out of sight. With no search open and the terminal
     * hidden the overlays are still offered the key (a sheet, Home's typed text); a key none of
     * them takes is left to the activity, as before.
     *
     * @param terminalShown whether the active terminal view is on screen
     * @param searchOpen    whether a search surface is open
     */
    @NonNull
    public static Route routeFor(@NonNull Holder holder, int keyCode, boolean terminalTypes,
                                 boolean terminalShown, boolean searchOpen) {
        if (holder == Holder.OTHER || holder == Holder.DISPLAY_SHOWING) return Route.PASS;
        if (keyCode == KeyEvent.KEYCODE_BACK) return Route.PASS;
        if (searchOpen || !terminalShown) return Route.OVERLAYS;
        return terminalTypes ? Route.TERMINAL : Route.OVERLAYS;
    }
}
