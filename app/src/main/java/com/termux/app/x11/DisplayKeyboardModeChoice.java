package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.KeyboardMode;

import java.util.Locale;

/**
 * What the Settings row "Keyboard over display" shows and stores. The mode is kept per
 * orientation (the touchpad's switch flips one, the Layout editor sets either); the row speaks
 * for both, so it has one value only while they agree and says so in its summary while they do
 * not. A pick writes the same mode to both.
 */
public final class DisplayKeyboardModeChoice {

    private DisplayKeyboardModeChoice() {}

    /** The ListPreference value for [mode]. */
    @NonNull
    public static String valueOf(@NonNull KeyboardMode mode) {
        return mode.storageValue();
    }

    /** The mode a ListPreference [value] stands for; Overlay, the default, for anything else. */
    @NonNull
    public static KeyboardMode modeOf(@Nullable String value) {
        return KeyboardMode.parse(value, KeyboardMode.OVERLAY);
    }

    /** The row's value: the shared mode, or null while the two orientations differ. */
    @Nullable
    public static String commonValue(@NonNull KeyboardMode portrait, @NonNull KeyboardMode landscape) {
        return portrait == landscape ? valueOf(portrait) : null;
    }

    /**
     * The row's summary. [format] takes the portrait and landscape labels, in that order
     * ("Portrait: %1$s · Landscape: %2$s"); it is used only when the orientations differ.
     */
    @NonNull
    public static String summary(@NonNull KeyboardMode portrait, @NonNull KeyboardMode landscape,
                                 @NonNull String overlayLabel, @NonNull String resizeLabel,
                                 @NonNull String format) {
        String portraitLabel = label(portrait, overlayLabel, resizeLabel);
        if (portrait == landscape) return portraitLabel;
        return String.format(Locale.ROOT, format, portraitLabel,
            label(landscape, overlayLabel, resizeLabel));
    }

    @NonNull
    private static String label(@NonNull KeyboardMode mode, @NonNull String overlayLabel,
                                @NonNull String resizeLabel) {
        return mode == KeyboardMode.RESIZE ? resizeLabel : overlayLabel;
    }
}
