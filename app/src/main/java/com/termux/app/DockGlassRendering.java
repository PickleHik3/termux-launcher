package com.termux.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shared dock-glass math and texture generation used by both launcher and Settings preview. */
public final class DockGlassRendering {
    private static Bitmap grainBitmap;
    private DockGlassRendering() {}

    public static int grainAlpha(int percent) {
        return Math.round(Math.max(0, Math.min(100, percent)) / 100f * 60f);
    }

    /** Model stop positions for the vertical glass light model, matched to {@link #lightModelColorAt}. */
    private static final float[] LIGHT_MODEL_STOPS = {0f, 0.33f, 0.67f, 1f};

    /**
     * Samples the vertical glass light model over {@code [sliceStart, sliceEnd]} (fractions of the
     * full model height) and returns the colors for a top-to-bottom gradient across that slice. The
     * slice's own model stops are included so the sheen/foot shape is preserved rather than reduced
     * to a straight two-color ramp.
     */
    @NonNull
    public static int[] lightModelSlice(int accent, int topSheenAlpha, int midSheenAlpha,
                                        int bottomFootAlpha, float sliceStart, float sliceEnd) {
        float start = Math.max(0f, Math.min(1f, sliceStart));
        float end = Math.max(start, Math.min(1f, sliceEnd));
        List<Integer> colors = new ArrayList<>();
        colors.add(lightModelColorAt(start, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha));
        for (float stop : LIGHT_MODEL_STOPS) {
            if (stop > start && stop < end) {
                colors.add(lightModelColorAt(stop, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha));
            }
        }
        colors.add(lightModelColorAt(end, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha));
        int[] result = new int[colors.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = colors.get(i);
        }
        return result;
    }

    /**
     * Color of the vertical glass light model at {@code pos} in [0,1]: accent sheen at the top
     * ([0,0.33]), fading to a clear see-through middle ([0.33,0.67]), then to a dark foot at the
     * bottom ([0.67,1]). No broad white wash — a near-white sheen reads as frosted plastic.
     */
    public static int lightModelColorAt(float pos, int accent, int topSheenAlpha,
                                        int midSheenAlpha, int bottomFootAlpha) {
        int sheenTop = withAlpha(accent, topSheenAlpha);
        int sheenMid = withAlpha(accent, midSheenAlpha);
        int clear = Color.TRANSPARENT;
        int foot = withAlpha(Color.BLACK, bottomFootAlpha);
        if (pos <= 0.33f) {
            return lerpArgb(sheenTop, sheenMid, pos / 0.33f);
        }
        if (pos <= 0.67f) {
            return lerpArgb(sheenMid, clear, (pos - 0.33f) / 0.34f);
        }
        return lerpArgb(clear, foot, (pos - 0.67f) / 0.33f);
    }

    /** Straight ARGB interpolation (alpha included) between two colors. */
    public static int lerpArgb(int a, int b, float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        int aa = Color.alpha(a), ab = Color.alpha(b);
        int ra = Color.red(a), rb = Color.red(b);
        int ga = Color.green(a), gb = Color.green(b);
        int ba = Color.blue(a), bb = Color.blue(b);
        return Color.argb(
            Math.round(aa + (ab - aa) * t),
            Math.round(ra + (rb - ra) * t),
            Math.round(ga + (gb - ga) * t),
            Math.round(ba + (bb - ba) * t));
    }

    /** Literal opacity endpoint: 100% is an opaque material and 0% is fully transparent. */
    private static final int BASE_MAX_ALPHA = 255;

    public static int baseAlpha(float opacity) {
        float clampedOpacity = Math.max(0f, Math.min(1f, opacity));
        return Math.round(clampedOpacity * BASE_MAX_ALPHA);
    }

    public static boolean blurEnabled(int blurRadiusDp) {
        return blurRadiusDp > 0;
    }

    /**
     * Builds the complete Material glass light model used by compact Settings specimens. The
     * launcher can split this model into slices for its dock/keyboard stack; specimens always draw
     * the complete slab and therefore use the same base, sheen, grain, and dark-foot math.
     */
    @NonNull public static Drawable createGlassSurface(@NonNull Resources resources,
                                                       int baseColor, int accentColor,
                                                       float opacity, int grainPercent,
                                                       boolean withFoot) {
        float amount = Math.max(0f, Math.min(1f, opacity));
        GradientDrawable base = new GradientDrawable();
        base.setColor(withAlpha(baseColor, Math.round(255f * amount)));
        base.setDither(true);

        int topAlpha = Math.round(16f * amount);
        int middleAlpha = Math.round(8f * amount);
        int footAlpha = withFoot ? Math.round(20f * amount) : 0;
        GradientDrawable light = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {
                withAlpha(accentColor, topAlpha),
                withAlpha(accentColor, middleAlpha),
                Color.TRANSPARENT,
                withAlpha(Color.BLACK, footAlpha)
            });
        light.setDither(true);

        if (grainPercent <= 0)
            return new LayerDrawable(new Drawable[] {base, light});
        return new LayerDrawable(new Drawable[] {
            base, light, createGrainLayer(resources, grainPercent)
        });
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    @NonNull public static synchronized Drawable createGrainLayer(
            @NonNull Resources resources, int percent) {
        if (grainBitmap == null) {
            int size = 110;
            int[] pixels = new int[size * size];
            Random random = new Random(0x6A11E);
            for (int i = 0; i < pixels.length; i++) {
                int value = random.nextInt(256);
                int alpha = random.nextInt(256);
                pixels[i] = (alpha << 24) | (value << 16) | (value << 8) | value;
            }
            grainBitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        }
        BitmapDrawable drawable = new BitmapDrawable(resources, grainBitmap);
        drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        drawable.setDither(true);
        drawable.setAlpha(grainAlpha(percent));
        return drawable;
    }
}
