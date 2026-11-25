package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.KeyboardForm;

/**
 * The keyboard's column across Home and the Terminal (#307, the developer's decision of
 * 2026-11-25): one keyboard, drawn once, laid out alike wherever it is up, so a crossing between
 * two keyboard-up rests moves no key.
 *
 * <p>With its keyboard up, Home lays its column out the way the Terminal does: the keys over the
 * Terminal's bands under the keyboard, at the Terminal's height ({@code RestCards} takes the
 * Terminal's keyboard-up column for Home's rest). Home's own rows stay at their keyboard-down rest
 * under the keyboard's cover ({@link HomeKeyboardCover}), so its dock is hidden while the
 * keyboard is up.
 *
 * <p>A keyboard that is up at the leaving rest and down at the arriving one leaves with the page
 * it belongs to: it slides out with that page, held at its rest height, and never vanishes on the
 * landing frame.
 */
public final class KeyboardColumn {

    private KeyboardColumn() {}

    /**
     * Whether Home's rest lays its keyboard's column out as the Terminal's keyboard-up column:
     * while Home's keyboard is up under its cover (a keyboard always on lifts Home's rows instead,
     * {@link HomeKeyboardCover#liftsRows}) and the keyboard stands in the column (a floating-form
     * keyboard is a frame of its own).
     */
    public static boolean homeTakesTerminals(boolean homeKeyboardUp, boolean homeLiftsRows,
                                             @NonNull KeyboardForm form) {
        return homeKeyboardUp && !homeLiftsRows && form != KeyboardForm.FLOATING;
    }

    /**
     * Whether Home, in front, stands the Terminal's bands under its keyboard (#307, 2026-11-25):
     * Home takes the Terminal's column ({@link #homeTakesTerminals}) and the Terminal's own rest
     * has its keyboard up too. The one answer the per-rest cards ({@code RestCards}) and the
     * views (the under-keyboard stack's hosts, so the column's margin and the keyboard's foot
     * follow) both read; when Home's keyboard goes down the stack goes back to Home's own.
     */
    public static boolean homeStandsTerminalsBands(boolean homeFront, boolean homeKeyboardUp,
                                                   boolean homeLiftsRows,
                                                   @NonNull KeyboardForm form,
                                                   boolean terminalKeyboardUp) {
        return homeFront && terminalKeyboardUp
            && homeTakesTerminals(homeKeyboardUp, homeLiftsRows, form);
    }

    /**
     * Whether the keyboard leaves with the leaving page: up at the leaving rest, down at the
     * arriving one (the arriving page has nothing to type into).
     */
    public static boolean leavesWithPage(boolean leavingKeyboardUp, boolean arrivingKeyboardUp) {
        return leavingKeyboardUp && !arrivingKeyboardUp;
    }

    /** The leaving page slides out to the left (the arriving page comes in from the right). */
    public static final int LEAVES_LEFT = -1;
    /** The leaving page slides out to the right. */
    public static final int LEAVES_RIGHT = 1;
    /** The crossing's direction is not known: the keyboard keeps the scene's own pose. */
    public static final int LEAVES_UNKNOWN = 0;

    /**
     * The way the leaving page slides out for a wall moving toward {@code towardSide} (1 the
     * arriving page is to the right, -1 to the left, 0 unknown;
     * {@code PlaceChromeTravel.Frame#towardSide}): the opposite way.
     */
    public static int leavesDirectionOf(int towardSide) {
        return towardSide > 0 ? LEAVES_LEFT : towardSide < 0 ? LEAVES_RIGHT : LEAVES_UNKNOWN;
    }

    /**
     * How far across the keyboard's column is drawn at progress {@code p} while it leaves with
     * its page ({@link #leavesWithPage}): the leaving page's own slide, a wall {@code widthPx}
     * wide in {@code direction} ({@link #LEAVES_LEFT} or {@link #LEAVES_RIGHT}). 0 at p = 0 (the
     * leaving rest) and a whole wall at p = 1, where it is off screen and the arriving rest has it
     * down.
     */
    public static float leavingTxPx(int direction, float p, int widthPx) {
        if (direction == LEAVES_UNKNOWN) return 0f;
        float q = p < 0f ? 0f : p > 1f ? 1f : p;
        return Integer.signum(direction) * q * Math.max(0, widthPx);
    }
}
