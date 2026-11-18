package com.termux.app.chrome;

import androidx.annotation.NonNull;

import com.termux.app.glass.Argb;

/**
 * The bottom stack's material as values rather than modes: what one region of the stack (the
 * dock's rows, the keyboard, the bands under it, the foot under the system pill) wears, as a
 * {@link RegionLook} every one of today's materials maps onto, and {@link #lerp} between any two.
 *
 * <p>Blur is not interpolated numerically: a crossing between two radii draws the two cached
 * frames crossfaded, so a blended look carries both radii and the share of the second one
 * ({@link RegionLook#blurDp}, {@link RegionLook#blurDpOther}, {@link RegionLook#blurMix}). Pure and
 * allocation-free; {@code StackScene} composes the looks a frame shows.
 */
public final class StackMaterial {

    private StackMaterial() {}

    /** One region's look. Mutable so a frame reuses its looks; {@link #set} copies one. */
    public static final class RegionLook {
        /** The wallpaper frame's blur radius, and the radius crossfaded in at {@link #blurMix}. */
        public float blurDp;
        public float blurDpOther;
        public float blurMix;
        /** How much of the blurred wallpaper frame shows (0 for a see-through or solid region). */
        public float frameAlpha;
        /** The tint over the frame and its alpha. */
        public int tintArgb;
        public float tintAlpha;
        public float grain;
        /** The veil over everything, ARGB (alpha 0 for none). */
        public int veilArgb;
        /** Which edges carry the rim (a bit set; 0 for none). */
        public int rimEdges;
        /** The solid fill over the frame, ARGB: its alpha is how solid the region is. */
        public int solidArgb;
        /** The deck slab's alpha. */
        public float slabAlpha;
        /** The compositor's blur behind a see-through region, in px (0 for none). */
        public float compositorBlurPx;

        public void set(@NonNull RegionLook o) {
            blurDp = o.blurDp;
            blurDpOther = o.blurDpOther;
            blurMix = o.blurMix;
            frameAlpha = o.frameAlpha;
            tintArgb = o.tintArgb;
            tintAlpha = o.tintAlpha;
            grain = o.grain;
            veilArgb = o.veilArgb;
            rimEdges = o.rimEdges;
            solidArgb = o.solidArgb;
            slabAlpha = o.slabAlpha;
            compositorBlurPx = o.compositorBlurPx;
        }

        public boolean sameAs(@NonNull RegionLook o) {
            return blurDp == o.blurDp && blurDpOther == o.blurDpOther && blurMix == o.blurMix
                && frameAlpha == o.frameAlpha && tintArgb == o.tintArgb
                && tintAlpha == o.tintAlpha && grain == o.grain && veilArgb == o.veilArgb
                && rimEdges == o.rimEdges && solidArgb == o.solidArgb
                && slabAlpha == o.slabAlpha && compositorBlurPx == o.compositorBlurPx;
        }

        /** How solid the region is: its solid fill's alpha, 0 to 1. */
        public float solidness() {
            return (solidArgb >>> 24) / 255f;
        }

        @NonNull @Override public String toString() {
            return String.format(java.util.Locale.ROOT,
                "blur=%.1f/%.1f@%.2f frame=%.2f tint=%08x@%.2f grain=%.2f veil=%08x rim=%d"
                    + " solid=%08x slab=%.2f comp=%.1f", blurDp, blurDpOther, blurMix, frameAlpha,
                tintArgb, tintAlpha, grain, veilArgb, rimEdges, solidArgb, slabAlpha,
                compositorBlurPx);
        }
    }

    /** The glass: a blurred wallpaper frame under a tint, its solid fill clear in the tint's colour. */
    public static void glass(float blurDp, float opacity, int tintArgb, float grain,
                             int panelArgb, @NonNull RegionLook out) {
        clear(out);
        out.blurDp = blurDp;
        out.blurDpOther = blurDp;
        out.frameAlpha = 1f;
        out.tintArgb = tintArgb;
        out.tintAlpha = clamp01(opacity);
        out.grain = grain;
        // The panel's colour with no alpha, so a blend toward the panel moves alpha alone.
        out.solidArgb = panelArgb & 0x00FFFFFF;
    }

    /** The opaque panel ({@link KeyboardMaterialPolicy.Material#SOLID}). */
    public static void solid(int panelArgb, @NonNull RegionLook out) {
        clear(out);
        out.solidArgb = panelArgb | 0xFF000000;
    }

    /**
     * The Display page's see-through glass: no wallpaper frame of its own, the compositor's blur
     * of the live display behind it, and the tint.
     */
    public static void displaySeeThrough(float compositorBlurPx, int tintArgb, float opacity,
                                         int panelArgb, @NonNull RegionLook out) {
        clear(out);
        out.compositorBlurPx = Math.max(0f, compositorBlurPx);
        out.tintArgb = tintArgb;
        out.tintAlpha = clamp01(opacity);
        out.solidArgb = panelArgb & 0x00FFFFFF;
    }

    /**
     * The keyboard host's material ({@link KeyboardMaterialPolicy#hostMaterial}) as a look: the
     * glass, the Display page's see-through glass, or the panel. False for
     * {@link KeyboardMaterialPolicy.Material#NONE}, where the region wears nothing.
     */
    public static boolean forKeyboard(@NonNull KeyboardMaterialPolicy.Material material,
                                      boolean seesThroughToDisplay, float blurDp, float opacity,
                                      int tintArgb, float grain, int panelArgb,
                                      float compositorBlurPx, @NonNull RegionLook out) {
        switch (material) {
            case SOLID:
                solid(panelArgb, out);
                return true;
            case GLASS:
                if (seesThroughToDisplay) {
                    displaySeeThrough(compositorBlurPx, tintArgb, opacity, panelArgb, out);
                } else {
                    glass(blurDp, opacity, tintArgb, grain, panelArgb, out);
                }
                return true;
            default:
                clear(out);
                return false;
        }
    }

    /**
     * {@code a} toward {@code b} by {@code t}: exactly {@code a} at 0 and exactly {@code b} at 1.
     * Colours blend per channel, numbers linearly; the blur crossfades {@code b}'s frame in over
     * {@code a}'s; the rim, a set of edges, is {@code a}'s until halfway.
     */
    public static void lerp(@NonNull RegionLook a, @NonNull RegionLook b, float t,
                            @NonNull RegionLook out) {
        if (t <= 0f) {
            out.set(a);
            return;
        }
        if (t >= 1f) {
            out.set(b);
            return;
        }
        float blurA = a.blurDp;
        float blurB = b.blurDp;
        out.blurDp = blurA;
        out.blurDpOther = blurB;
        out.blurMix = blurA == blurB ? 0f : t;
        out.frameAlpha = mix(a.frameAlpha, b.frameAlpha, t);
        out.tintArgb = mixArgb(a.tintArgb, b.tintArgb, t);
        out.tintAlpha = mix(a.tintAlpha, b.tintAlpha, t);
        out.grain = mix(a.grain, b.grain, t);
        out.veilArgb = mixArgb(a.veilArgb, b.veilArgb, t);
        out.rimEdges = t < 0.5f ? a.rimEdges : b.rimEdges;
        out.solidArgb = mixArgb(a.solidArgb, b.solidArgb, t);
        out.slabAlpha = mix(a.slabAlpha, b.slabAlpha, t);
        out.compositorBlurPx = mix(a.compositorBlurPx, b.compositorBlurPx, t);
    }

    public static void clear(@NonNull RegionLook out) {
        out.blurDp = 0f;
        out.blurDpOther = 0f;
        out.blurMix = 0f;
        out.frameAlpha = 0f;
        out.tintArgb = 0;
        out.tintAlpha = 0f;
        out.grain = 0f;
        out.veilArgb = 0;
        out.rimEdges = 0;
        out.solidArgb = 0;
        out.slabAlpha = 0f;
        out.compositorBlurPx = 0f;
    }

    /** Two ARGB colours blended per channel. */
    public static int mixArgb(int a, int b, float t) {
        return Argb.lerp(a, b, t);
    }

    private static float mix(float a, float b, float t) {
        return a == b ? a : a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}