package com.termux.app.surfaces;

import android.content.res.Configuration;

import androidx.annotation.Nullable;

/**
 * Which arrangement the Appearance editor takes. Phones in either orientation and tablets in
 * portrait keep the bottom sheet under the preview; a tablet in landscape stands the preview on
 * the left and the sheet as a side pane on the right ({@link AppearancePreviewArea#SIDE_PANE_DP}).
 * Every branch of that arrangement asks this one question.
 */
public final class EditorFormFactor {

    /** The smallest width, in dp, from which Android itself treats a screen as a tablet's. */
    static final int TABLET_SMALLEST_WIDTH_DP = 600;

    private EditorFormFactor() {}

    /** Whether {@code config} is a tablet held in landscape: the side-pane arrangement. */
    public static boolean tabletLandscape(@Nullable Configuration config) {
        return config != null
            && config.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
            && config.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
}
