package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.chrome.WallpaperPicture;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * What the card carries under its header.
 *
 * <p>Every card is the same shape now — the surface's look rows, and nothing above them. The one
 * thing that still differs is the strip between the header and the rows: the presets and the two
 * style pills belong to the shared layer, which is what "all surfaces" means, and a card open on
 * one surface has no business offering them.
 *
 * <p>Pure: a surface in, an answer out, so every case is testable without a window.
 */
public final class SurfaceEditorCardPlan {

    private SurfaceEditorCardPlan() {}

    /**
     * Whether the card carries the shared layer's own strip: the preset mocks and the style and
     * material pills.
     *
     * @param slot the surface the card is open on, or null for the shared layer
     */
    public static boolean sharedStripShown(@Nullable SurfaceSlot slot) {
        return slot == null;
    }

    /**
     * The caption a Blur row shows under its slider, or null for a row that gets none.
     *
     * <p>Every Blur row — a surface's own and the shared layer's "all surfaces" row alike — carries
     * one whenever the picture the glass is blurring may not be what is actually on the screen
     * (issue #37: the slider moved and nothing on screen changed, with no clue why). A row that is
     * not about blur, or a picture the editor already matches the screen, gets nothing.
     *
     * @param controlId the row's {@link SurfaceEditorProperties} id
     * @param picture   what the glass can currently read and how sure it is that matches the screen
     */
    @Nullable
    @StringRes
    public static Integer blurHintTextRes(@NonNull String controlId, @NonNull WallpaperPicture picture) {
        if (!isBlurRow(controlId) || !picture.showsHint())
            return null;
        return picture == WallpaperPicture.NO_STILL
            ? R.string.termux_surface_editor_blur_hint_no_still
            : R.string.termux_surface_editor_blur_hint_best_effort;
    }

    /** Whether a row id is one of the two Blur rows: a surface's own, or the shared layer's. */
    public static boolean isBlurRow(@NonNull String controlId) {
        return SurfaceEditorProperties.ID_BLUR.equals(controlId)
            || SurfaceEditorProperties.ID_ALL_BLUR.equals(controlId);
    }
}
