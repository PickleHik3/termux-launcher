package com.termux.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.chrome.OnGlass;

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

    // ------------------------------------------------------------ the light mode's counterpart
    //
    // The model above pushes one way only: accent sheen at the top, clear middle, black foot at the
    // bottom. That is the right shape over a dark backdrop, which is what a dark theme always has —
    // Android dims the wallpaper for it. Nothing lightens the wallpaper for a light theme, so in
    // light mode the same model lands on a glass that is already too dark for the mode's own ink,
    // and the foot makes the worst row of the band darker still.
    //
    // The counterpart is not a second gradient and emphatically not a white wash — the note on
    // lightModelColorAt still holds, a near-white sheen reads as frosted plastic. It is the other
    // half of the same arithmetic: say what the model actually leaves an ink standing on, and say
    // which row of the band is the worst one. A band measured that way can be veiled in its own
    // base colour by exactly as much as it needs (OnGlass does that sum), and the foot is then part
    // of what was measured instead of something that quietly undoes it. See ChromeInk, which is the
    // only caller, and OnGlass.Resolution#veil for the per-pixel promise these keep.

    /** The vertical light model's own stop positions, for a caller that has to visit every row. */
    @NonNull
    public static float[] lightModelStops() {
        return LIGHT_MODEL_STOPS.clone();
    }

    /** The top sheen's alpha at {@code opacity}; the same curve {@link #createGlassSurface} draws. */
    public static int topSheenAlpha(float opacity) {
        return Math.round(16f * clamp01(opacity));
    }

    /** The mid sheen's alpha at {@code opacity}. */
    public static int midSheenAlpha(float opacity) {
        return Math.round(8f * clamp01(opacity));
    }

    /** The dark foot's alpha at {@code opacity}, or 0 when the surface drops the foot. */
    public static int footAlpha(float opacity, boolean withFoot) {
        return withFoot ? Math.round(20f * clamp01(opacity)) : 0;
    }

    /**
     * The whole tint a band lays over the wallpaper at model position {@code pos}: its base layer
     * at {@code baseAlpha} with the vertical light model over it, in the order the surface draws
     * them. Alpha is kept, because this is a tint and not a surface — it is what a caller hands
     * {@link OnGlass#backdrop} as {@code glassTint} so that what was measured is what is drawn.
     */
    @ColorInt
    public static int glassTintAt(float pos, @ColorInt int baseColor, int baseAlpha,
                                  @ColorInt int accent, int topSheenAlpha, int midSheenAlpha,
                                  int bottomFootAlpha) {
        return OnGlass.composite(
            lightModelColorAt(pos, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha),
            withAlpha(baseColor, Math.max(0, Math.min(255, baseAlpha))));
    }

    /**
     * The opaque surface a band's glass leaves at model position {@code pos}, composed in exactly
     * the order the band draws it: the base layer on what is under the glass, then the vertical
     * light model on that.
     *
     * <p>The one definition of "the band, as drawn". It exists because there was briefly a second:
     * {@link #glassTintAt} pre-combines the base and the model into a single tint, and compositing
     * that over the backdrop is the same arithmetic in real numbers but not in 8-bit channels —
     * each composite rounds, and the two chains landed a unit apart. A unit of RGB is 0.002 of a
     * contrast ratio, which is nothing to look at and everything to a promise: the band measured
     * 4.53 and drew 4.4978 against a floor of 4.5. So the measurement composes the stack the way
     * the {@code LayerDrawable} does, and there is nothing left for the two to disagree about.</p>
     *
     * @param under what the glass is laid on, opaque: the wallpaper under the launcher's dim
     */
    @ColorInt
    public static int glassSurfaceAt(float pos, @ColorInt int under, @ColorInt int baseColor,
                                     int baseAlpha, @ColorInt int accent, int topSheenAlpha,
                                     int midSheenAlpha, int bottomFootAlpha) {
        int lit = OnGlass.composite(withAlpha(baseColor, Math.max(0, Math.min(255, baseAlpha))),
            OnGlass.opaque(under));
        return OnGlass.opaque(OnGlass.composite(
            lightModelColorAt(pos, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha), lit));
    }

    /**
     * The model position, within the slice this surface actually renders, at which the glass works
     * hardest against {@code ink} — the row a contrast promise has to be made at.
     *
     * <p>Measured rather than assumed. The foot is the obvious answer for a dark ink and the accent
     * sheen for a pale one, but the accent is the wallpaper's Material-You primary and can be
     * either, so both ends are evaluated along with the model's interior stops and the lowest ratio
     * wins. A band that renders a slice of the model (the status bar takes {@code [0, f]} and the
     * window bar {@code [f, 1]} of one model) only answers for its own rows.</p>
     *
     * @param under what the band's glass is drawn on, opaque: wallpaper under the launcher's dim
     */
    public static float worstLightModelStop(@ColorInt int ink, @ColorInt int under,
                                            @ColorInt int baseColor, int baseAlpha,
                                            @ColorInt int accent, int topSheenAlpha,
                                            int midSheenAlpha, int bottomFootAlpha,
                                            float sliceStart, float sliceEnd) {
        float start = clamp01(sliceStart);
        float end = Math.max(start, clamp01(sliceEnd));
        float worst = start;
        double worstRatio = Double.MAX_VALUE;
        for (float stop : candidateStops(start, end)) {
            double ratio = OnGlass.ratio(ink, glassSurfaceAt(stop, under, baseColor, baseAlpha,
                accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha));
            if (ratio < worstRatio) {
                worstRatio = ratio;
                worst = stop;
            }
        }
        return worst;
    }

    /** The slice's own ends plus every model stop inside it — the rows {@link #lightModelSlice} draws. */
    @NonNull
    private static float[] candidateStops(float start, float end) {
        List<Float> stops = new ArrayList<>();
        stops.add(start);
        for (float stop : LIGHT_MODEL_STOPS) {
            if (stop > start && stop < end) stops.add(stop);
        }
        stops.add(end);
        float[] result = new float[stops.size()];
        for (int i = 0; i < result.length; i++) result[i] = stops.get(i);
        return result;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
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
