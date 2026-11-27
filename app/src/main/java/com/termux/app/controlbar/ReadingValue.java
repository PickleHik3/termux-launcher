package com.termux.app.controlbar;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * One reading as the slot draws it: a number and its unit. The one-row bar and the rail show the
 * number alone with the reading's icon; the two rows show the number with its unit. A reading with
 * no number (media) is an icon alone.
 */
public final class ReadingValue {

    /** What stands where a reading is known to exist but has no number yet. */
    public static final String UNKNOWN_NUMBER = "--";

    @NonNull public final ReadingKind kind;
    /** The number as written ("42", "21", "--"), or empty for an icon alone. */
    @NonNull public final String number;
    /** What follows the number in the two rows: "%", "°", or empty. */
    @NonNull public final String unit;

    public ReadingValue(@NonNull ReadingKind kind, @NonNull String number, @NonNull String unit) {
        this.kind = kind;
        this.number = number;
        this.unit = number.isEmpty() ? "" : unit;
    }

    /** The reading as the one-row bar and the rail show it: the number alone. */
    @NonNull
    public String compact() {
        return number;
    }

    /** The reading as the two rows show it: the number and its unit. */
    @NonNull
    public String full() {
        return number + unit;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ReadingValue)) return false;
        ReadingValue that = (ReadingValue) other;
        return kind == that.kind && number.equals(that.number) && unit.equals(that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, number, unit);
    }
}
