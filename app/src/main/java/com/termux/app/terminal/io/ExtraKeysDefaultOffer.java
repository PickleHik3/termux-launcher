package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * Whether to offer this release's key row to someone who already has a row of their own.
 *
 * <p>The launcher ships a new set of keys under the terminal, but a value for {@code extra-keys}
 * in {@code termux.properties} replaces it, so anyone who ever edited their row — in the editor or
 * by hand — would keep the old one forever without being asked. This decides who is asked: only a
 * user whose file really holds a row, and only while that row is not already the shipped one. A
 * fresh install has no value at all, so it is never asked.
 *
 * <p>Pure decisions, no Android and no storage, so the rules are testable on their own; the
 * activity supplies the property value and the two flags and owns the card.
 */
public final class ExtraKeysDefaultOffer {

    private ExtraKeysDefaultOffer() {}

    /**
     * Whether the card should go up now.
     *
     * @param extraKeysValue the {@code extra-keys} value in the user's properties file, or null
     *                       when the file does not set it.
     * @param alreadyOffered whether the card has been answered once already.
     * @param tourInTheWay   whether the first-launch run is going or still waiting to be offered;
     *                       the card is held until it is neither, so the two never overlap.
     */
    public static boolean shouldOffer(@Nullable String extraKeysValue, boolean alreadyOffered,
                                      boolean tourInTheWay) {
        if (alreadyOffered || tourInTheWay) return false;
        return isCustomRow(extraKeysValue);
    }

    /**
     * Whether the file holds a row of the user's own: a value that is there and is not the row the
     * launcher ships. This is what makes the card worth showing at all, held or not.
     */
    public static boolean isCustomRow(@Nullable String extraKeysValue) {
        if (isBlank(extraKeysValue)) return false;
        return !isSameRow(extraKeysValue, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS);
    }

    /**
     * Whether two property values draw the same row. Compared through the editor's model rather
     * than as text, because a hand-written file says the same row in many ways — quotes, spacing,
     * {@code key:} spelled out — and a user who already types the shipped row must not be asked to
     * take it.
     */
    public static boolean isSameRow(@Nullable String one, @Nullable String other) {
        return normalise(one).equals(normalise(other));
    }

    /** One value's rows and keys in the editor's own spelling, for comparing. */
    @NonNull
    public static String normalise(@Nullable String value) {
        return ExtraKeysLayoutModel.parse(value).serialize();
    }

    /** The name of the key that shows and hides the keyboard. */
    private static final String KEYBOARD_KEY = "KEYBOARD";

    /**
     * Whether a row carries the key that shows and hides the keyboard. The shipped row opens with
     * it, and one lesson of the first-launch run is that key both ways round; a row of the user's
     * own often has nothing of the kind, and a lesson pointing at a key that is not there teaches
     * nobody anything.
     */
    public static boolean hasKeyboardKey(@Nullable String extraKeysValue) {
        if (isBlank(extraKeysValue)) return false;
        for (java.util.List<ExtraKeysLayoutModel.Key> row
                : ExtraKeysLayoutModel.parse(extraKeysValue).rows())
            for (ExtraKeysLayoutModel.Key key : row)
                if (KEYBOARD_KEY.equalsIgnoreCase(key.key)) return true;
        return false;
    }

    /** Whether a saved previous row has anything in it to offer back. */
    public static boolean hasPreviousRow(@Nullable String previousValue) {
        if (isBlank(previousValue)) return false;
        return !ExtraKeysLayoutModel.parse(previousValue).isEmpty();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
