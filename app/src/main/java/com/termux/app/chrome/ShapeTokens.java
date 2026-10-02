package com.termux.app.chrome;

import android.content.Context;
import android.graphics.RectF;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;

import com.google.android.material.shape.CornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.termux.R;

/**
 * Reads the hand-drawn views' corner radii and elevations from the theme's Material 3 shape scale
 * instead of per-view literals. The pure parts ({@link #nearest}, {@link #elevationLevel},
 * {@link #elevationDp}) carry the mapping from today's literals; {@link #cornerPx} resolves a
 * token against a context and falls back to the literal when the theme does not define it.
 */
public final class ShapeTokens {

    /** The M3 shape scale. {@code FULL} is a pill: callers use half the view's short side. */
    public enum Corner {
        SMALL(com.google.android.material.R.attr.shapeAppearanceCornerSmall, 8f),
        MEDIUM(com.google.android.material.R.attr.shapeAppearanceCornerMedium, 12f),
        LARGE(com.google.android.material.R.attr.shapeAppearanceCornerLarge, 16f),
        EXTRA_LARGE(com.google.android.material.R.attr.shapeAppearanceCornerExtraLarge, 28f);

        @AttrRes public final int attr;
        public final float defaultDp;

        Corner(int attr, float defaultDp) {
            this.attr = attr;
            this.defaultDp = defaultDp;
        }
    }

    /** M3 elevation levels 0..5 in dp. */
    public static final float ELEVATION_LEVEL0_DP = 0f;
    public static final float ELEVATION_LEVEL1_DP = 1f;
    public static final float ELEVATION_LEVEL2_DP = 3f;
    public static final float ELEVATION_LEVEL3_DP = 6f;
    public static final float ELEVATION_LEVEL4_DP = 8f;
    public static final float ELEVATION_LEVEL5_DP = 12f;

    private ShapeTokens() {}

    /** The token nearest a literal radius in dp (7-8 small, 10-12 medium, 14-20 large, 22+ extra large). */
    @NonNull
    public static Corner nearest(float dp) {
        if (dp < 9f) return Corner.SMALL;
        if (dp < 13f) return Corner.MEDIUM;
        if (dp < 21f) return Corner.LARGE;
        return Corner.EXTRA_LARGE;
    }

    /** The M3 elevation level (0..5) nearest a literal elevation in dp. */
    public static int elevationLevel(float dp) {
        int best = 0;
        for (int i = 1; i <= 5; i++) {
            if (Math.abs(elevationDp(i) - dp) < Math.abs(elevationDp(best) - dp)) best = i;
        }
        return best;
    }

    public static float elevationDp(int level) {
        switch (level) {
            case 0: return ELEVATION_LEVEL0_DP;
            case 1: return ELEVATION_LEVEL1_DP;
            case 2: return ELEVATION_LEVEL2_DP;
            case 3: return ELEVATION_LEVEL3_DP;
            case 4: return ELEVATION_LEVEL4_DP;
            default: return ELEVATION_LEVEL5_DP;
        }
    }

    /** Corner radius in px for a shape attr; {@code fallbackDp} when the theme does not define it. */
    public static float cornerPx(@NonNull Context context, @AttrRes int shapeAttr, float fallbackDp) {
        float fallback = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fallbackDp,
            context.getResources().getDisplayMetrics());
        try {
            TypedValue value = new TypedValue();
            if (!context.getTheme().resolveAttribute(shapeAttr, value, true) || value.resourceId == 0) {
                return fallback;
            }
            CornerSize size = ShapeAppearanceModel.builder(context, value.resourceId, 0).build()
                .getTopLeftCornerSize();
            float px = size.getCornerSize(new RectF(0f, 0f, 0f, 0f));
            return px > 0f ? px : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Corner radius in px for the token nearest the literal {@code dp}. */
    public static float cornerPxNearest(@NonNull Context context, float dp) {
        Corner corner = nearest(dp);
        return cornerPx(context, corner.attr, corner.defaultDp);
    }

    /** Elevation in px for an M3 level. */
    public static float elevationPx(@NonNull Context context, int level) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, elevationDp(level),
            context.getResources().getDisplayMetrics());
    }
}
