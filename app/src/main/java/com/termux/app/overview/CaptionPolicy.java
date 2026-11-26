package com.termux.app.overview;

import androidx.annotation.NonNull;

/**
 * What a card's caption says when its room is short. The glyph beside it already tells the kind
 * of page, so a numbered label ("Terminal 4") that would be cut gives up its kind word and keeps
 * the number, which is what tells two cards apart. A label that is not "word number" (a name the
 * user chose) is kept whole. Pure.
 */
public final class CaptionPolicy {

    private CaptionPolicy() { }

    /** How wide a text draws; the caption's paint in the view, a fixed width in tests. */
    public interface Measure {
        float width(@NonNull String text);
    }

    /** {@code label} when it fits {@code room}, else its number alone when it has one. */
    @NonNull
    public static String caption(@NonNull String label, float room, @NonNull Measure measure) {
        if (measure.width(label) <= room) return label;
        String number = number(label);
        return number == null ? label : number;
    }

    /** The trailing number of "word number", or null for any other label. */
    static String number(@NonNull String label) {
        int space = label.lastIndexOf(' ');
        if (space <= 0 || space == label.length() - 1) return null;
        for (int i = space + 1; i < label.length(); i++) {
            if (!Character.isDigit(label.charAt(i))) return null;
        }
        return label.substring(space + 1);
    }
}
