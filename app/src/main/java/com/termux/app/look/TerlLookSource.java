package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.GlassMaterial;

import java.util.List;

/**
 * A supplier of named Looks for {@link TerlLookStore}: the shipped Looks
 * ({@link TerlLooks#shipped()}). Custom is the store's own, never a source's.
 */
public interface TerlLookSource {

    /**
     * One Look's numbers for every surface that wears it: Opacity and Blur, and the physical
     * parameters its ADR 0033 preset sets app-wide (the slab's thickness, Shine, and how much
     * accent it mixes in under Auto).
     */
    final class Look {
        @NonNull public final String id;
        public final int opacity;
        public final int blur;
        /** The slab's thickness, dp (what Depth writes). */
        public final int bendDp;
        /** Shine, 0 to 100. */
        public final int shine;
        /** The accent it mixes in while the Tint colour is Auto, percent. */
        public final int tintPercent;

        public Look(@NonNull String id, int opacity, int blur) {
            this(id, opacity, blur, GlassMaterial.REALISTIC_BEND_DP, GlassMaterial.SHINE_DEFAULT, 0);
        }

        public Look(@NonNull String id, int opacity, int blur, int bendDp, int shine, int tintPercent) {
            this.id = id;
            this.opacity = TerlProperty.OPACITY.clamp(opacity);
            this.blur = TerlProperty.BLUR.clamp(blur);
            this.bendDp = Math.max(0, Math.min(GlassMaterial.MAX_BEND_DP, bendDp));
            this.shine = Math.max(GlassMaterial.MIN, Math.min(GlassMaterial.MAX, shine));
            this.tintPercent = Math.max(0, Math.min(100, tintPercent));
        }
    }

    /** The Looks this source has now, in filmstrip order. */
    @NonNull
    List<Look> looks();

    @Nullable
    default Look find(@NonNull String id) {
        for (Look look : looks()) if (look.id.equals(id)) return look;
        return null;
    }
}
