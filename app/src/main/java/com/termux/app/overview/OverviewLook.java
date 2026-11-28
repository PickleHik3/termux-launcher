package com.termux.app.overview;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What Overview wears, read once when it opens: the sheet material its screen stands on and the
 * launcher's ink. The cards are tinted panes of the ink on that material, with nothing sampled.
 */
public final class OverviewLook {

    /**
     * The card face's alpha over the sheet material: the ink at a fifth, enough to read as a pane
     * on the light and the dark ground alike without competing with the names under it.
     */
    static final int CARD_FACE_ALPHA = 0x33;

    /** The sheet's material (Global's glass at the sheets' floor, ADR 0031). */
    @NonNull public final Drawable ground;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int accent;

    /**
     * The glass the Workspace list's card wears (the sheets' glass), cut for a view at a radius:
     * Overview's sheet wears it under Terl. Null draws {@link #ground} instead.
     */
    public interface Glass {
        @Nullable Drawable glass(float radiusPx, @NonNull android.view.View anchor);
    }

    @Nullable public final Glass glass;

    public OverviewLook(@NonNull Drawable ground, int onSurface, int onSurfaceVariant, int accent) {
        this(ground, onSurface, onSurfaceVariant, accent, null);
    }

    public OverviewLook(@NonNull Drawable ground, int onSurface, int onSurfaceVariant, int accent,
                        @Nullable Glass glass) {
        this.glass = glass;
        this.ground = ground;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.accent = accent;
    }

    /** A card's face: {@code ink} at {@link #CARD_FACE_ALPHA}. */
    static int cardFace(int ink) {
        return (ink & 0x00FFFFFF) | (CARD_FACE_ALPHA << 24);
    }
}
