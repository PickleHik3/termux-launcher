package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.KeyboardMode;

/**
 * What the display Input page's "Keyboard over display" row shows. The mode is stored per
 * orientation, but the row is one choice: it shows the choice when both orientations agree and
 * names each when the touchpad's button has left them apart.
 */
public final class KeyboardOverDisplayPolicy {

    private KeyboardOverDisplayPolicy() {}

    /** The row's checked value, or null when portrait and landscape differ. */
    @Nullable
    public static String value(@NonNull KeyboardMode portrait, @NonNull KeyboardMode landscape) {
        return portrait == landscape ? portrait.storageValue() : null;
    }

    /**
     * The row's summary: the label of the shared choice, or both orientations spelled out. The
     * format takes the portrait label, then the landscape one.
     */
    @NonNull
    public static String summary(@NonNull KeyboardMode portrait, @NonNull KeyboardMode landscape,
                                 @NonNull String overlayLabel, @NonNull String resizeLabel,
                                 @NonNull String splitFormat) {
        String portraitLabel = label(portrait, overlayLabel, resizeLabel);
        if (portrait == landscape) return portraitLabel;
        return String.format(splitFormat, portraitLabel, label(landscape, overlayLabel, resizeLabel));
    }

    @NonNull
    private static String label(@NonNull KeyboardMode mode, @NonNull String overlayLabel,
                                @NonNull String resizeLabel) {
        return mode == KeyboardMode.OVERLAY ? overlayLabel : resizeLabel;
    }
}
