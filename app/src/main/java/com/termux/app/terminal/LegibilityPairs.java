package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The terminal palette's legibility contract as data: which ink is drawn on which ground, and the
 * contrast it must reach there. {@link MaterialTerminalColorScheme} solves the palette against
 * this table and the tests assert the solved palette against the same table, so a pair added here
 * is both enforced and checked.
 *
 * <p>The first rows are the ordinary ones: text and the accents on the terminal's own background
 * (or, on glass, on what the pane really stands on), at the level's own floors. The last rows are
 * the panel pairs. TUIs such as dialog and whiptail paint a box in ANSI white (slot 7) and draw
 * black, red and blue on it, and the palette used to hold those inks only to the background, so
 * the dark recipe's pale red read on a pale grey panel at 1.00:1. Green, yellow, magenta and cyan
 * on slot 7 are exempt, as they are in kitty (its green is 1.61:1 and its yellow 1.27:1 on its
 * own white): holding them to 3:1 as well would push all six accents to a tone where the palette
 * stops looking like one.
 */
public final class LegibilityPairs {

    /** The ink is the terminal's foreground rather than an ANSI slot. */
    public static final int FOREGROUND = -1;
    /** The ground is what the text stands on: the palette background, or the measured glass. */
    public static final int BACKGROUND = -2;

    /** Where a pair's floor comes from. */
    public enum Kind { BODY, ANSI, DIM, FIXED }

    /** One ink on one ground, with its floor. */
    public static final class Pair {
        /** An ANSI slot, or {@link #FOREGROUND}. */
        public final int ink;
        /** An ANSI slot, or {@link #BACKGROUND}. */
        public final int ground;
        public final Kind kind;
        /** The floor of a {@link Kind#FIXED} pair, at every level. */
        final double fixed;

        private Pair(int ink, int ground, @NonNull Kind kind, double fixed) {
            this.ink = ink;
            this.ground = ground;
            this.kind = kind;
            this.fixed = fixed;
        }

        public double floor(@NonNull TerminalContrastLevel level) {
            switch (kind) {
                case BODY: return level.bodyTarget;
                case ANSI: return level.ansiRatio;
                case DIM: return level.dimRatio;
                default: return fixed;
            }
        }

        @NonNull
        public String label() {
            return name(ink) + " on " + name(ground);
        }

        private static String name(int slot) {
            if (slot == FOREGROUND) return "foreground";
            if (slot == BACKGROUND) return "background";
            return "color" + slot;
        }
    }

    /** Black, red and blue on ANSI white: dialog's items, tag hotkeys and bold tags. */
    public static final double PANEL_INK_FLOOR = 3.0d;
    /** White and bright white on black: the panel's own two fills must read against each other. */
    static final double PANEL_FILL_FLOOR = 4.5d;

    public static final List<Pair> ALL;

    static {
        List<Pair> rows = new ArrayList<>();
        rows.add(new Pair(FOREGROUND, BACKGROUND, Kind.BODY, 0d));
        for (int slot = 1; slot <= 6; slot++) {
            rows.add(new Pair(slot, BACKGROUND, Kind.ANSI, 0d));
            rows.add(new Pair(slot + 8, BACKGROUND, Kind.ANSI, 0d));
        }
        rows.add(new Pair(8, BACKGROUND, Kind.DIM, 0d));
        for (int slot : new int[] {0, 1, 4}) {
            rows.add(new Pair(slot, 7, Kind.FIXED, PANEL_INK_FLOOR));
        }
        for (int slot : new int[] {7, 15}) {
            rows.add(new Pair(slot, 0, Kind.FIXED, PANEL_FILL_FLOOR));
        }
        ALL = Collections.unmodifiableList(rows);
    }

    private LegibilityPairs() {}

    /** The floor {@code slot} is held to on the background, or 0 when it has no such row. */
    static double backgroundFloor(int slot, @NonNull TerminalContrastLevel level) {
        for (Pair pair : ALL) {
            if (pair.ink == slot && pair.ground == BACKGROUND) return pair.floor(level);
        }
        return 0d;
    }

    /**
     * How close to the ideal bound a panel ink lands when the panel outranks the background. The
     * search moves in whole tones and keeps most of the colour's chroma, so the tone it takes sits
     * up to one step under the exact bound.
     */
    static final double PANEL_BOUND_KEPT = 0.95d;

    /**
     * The floor {@code slot} really answers to on the background once the panel outranks it
     * (ADR 0014). Where reading at {@link #PANEL_INK_FLOOR} on {@code white} and at the level's
     * ratio on {@code background} cannot both hold — Harder over very dark glass, where 6:1 on a
     * tone-10 ground would need a white brighter than white — the background floor drops to the
     * most that still leaves the panel pair, which is white's own ratio on the ground over the
     * panel floor. Every other slot, and every feasible case, keeps {@link #backgroundFloor}.
     */
    static double backgroundFloor(int slot, @NonNull TerminalContrastLevel level,
                                  int white, int background) {
        double floor = backgroundFloor(slot, level);
        if (onWhiteFloor(slot) <= 0d) return floor;
        double bound = MaterialTerminalColorScheme.contrastRatio(white, background) / PANEL_INK_FLOOR;
        return bound >= floor ? floor : bound * PANEL_BOUND_KEPT;
    }

    /** The floor {@code slot} is held to on ANSI white, or 0 when it is exempt. */
    public static double onWhiteFloor(int slot) {
        for (Pair pair : ALL) {
            if (pair.ink == slot && pair.ground == 7) return pair.fixed;
        }
        return 0d;
    }
}
