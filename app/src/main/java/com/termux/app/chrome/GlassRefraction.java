package com.termux.app.chrome;

import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.GlassMaterial;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;

/**
 * The one AGSL glass every surface refracts through (API 33+).
 *
 * <p>What separates real glass from frosted polymer is that glass <em>bends</em> light at its
 * edges and catches a crisp highlight there, instead of only diffusing it. Every surface is a
 * thick rounded slab ({@link GlassSlab}): flat in the middle, curving down to its outline across a
 * lens zone, 16 dp on a large surface and a share of the short side on a small piece. One
 * rounded-box distance field on the drawn corner gives the outline, how far into the zone a pixel
 * is, and the slab's normal (its gradient tipped by the profile's slope). In the zone the ray
 * straight down from the eye is bent by Snell's law and travels the slab's thickness (the Depth),
 * so the pixel shows the picture from further in: strongly at the border, nothing on the face,
 * never past {@code T sqrt(n² - 1)}, and held so a small piece never shows its far edge's picture.
 * The zone shows the sharp picture where one is bound; the face keeps its frost. The zone is lit
 * in linear light: Schlick's Fresnel from the slab's normal and the view against a fixed sky tone,
 * and a white specular from the one room light, up and to the left. Face pixels skip all of it.</p>
 * <p>The outline wears one thin, crisp rim ({@link GlassRim}): a line a few px wide that darkens
 * what is behind it and adds white, lit in linear light so it adds no colour of its own, with a
 * baseline of white and dark every side and corner keeps alike, more white facing one key light up
 * and to the left and more dark facing away. That is the glass at rest, on every surface, and it
 * changes only with the slab's pose ({@link GlassPose}, below). The rim is fixed material, not a knob (a debug property tunes its numbers), and
 * every surface that runs the program wears it. Where a sharp picture of what is behind is handed in
 * ({@link Program#setClarityInput}), the lens zone shows it through the bend in place of the frost,
 * wholly at the rim ({@link #sharpShare}), at rest and in motion alike: a bevel is thin glass, so
 * what is behind it reads sharp. The live route binds none (an effect takes one input), so its
 * edge keeps the frost.
 * Dispersion refracts red and blue at their own index, so the bent picture splits evenly round
 * the outline in step with how far it is bent there, the way a prism fans out white light (the
 * Edge slider). A
 * pressed extra key adds a lens that magnifies toward the key's centre and eases to nothing at its
 * pill rim, and a finger resting on the glass ({@link Program#setTouch}) lights the rim nearest
 * it, scatters a faint glow under it and draws the picture very slightly toward it.</p>
 * <p>Clear's face terms, each a Look field that is 0, and skipped, for every other Look. Face
 * clarity ({@code uFaceClarity}) shows the clearer picture through the face by that share, where
 * one is handed in, so the face is nearly the backdrop itself and the edge carries the glass. Dim
 * ({@code uDim}) tones the face after that: a bright backdrop is darkened toward black, up to 35%
 * over white, so white content stays legible, and a dark one is lifted a touch toward white, 6%
 * over black; both fade out with the surface's size, full at a short side of 200 dp or under and
 * gone at 320 dp, so a large content surface such as a terminal pane keeps its frost (Apple keeps
 * Clear for controls, not content). The rim comes after the tone and is not toned. The tone is a wallpaper's, so it
 * runs only where a frame is the content ({@link Program#setInput}), never under a {@code RenderEffect},
 * whose content is the view's own icons and text. Size lens
 * ({@code uSizeLens}) scales the lens zone and the thickness by the surface's short side against 72 dp, from
 * three quarters to one and a half times, so a larger surface reads thicker; the half-short-side
 * hold still holds over the scaled zone.</p>
 * <p>It used to live in the activity as the dock's own effect. It is one program now, with two
 * ways in: as a {@code RenderEffect} over a view's finished pixels, which is what the dock and
 * the under-pill strip still do (the content is the view, so the aim is identity), and as the
 * paint shader a {@link SharedFrameDrawable} or a pane slab draws the shared frame through, with
 * that frame's {@link BitmapShader} as the child input and the surface's aim — the same matrix
 * the plain draw uses — handed over as uniforms, so the frame is sampled at the surface's live
 * screen position and bent per pixel in one pass, with no offscreen layer per surface.</p>
 *
 * <p>Nothing here animates. Uniforms are written only when what they describe moved — the
 * surface, the parallax, a knob — so a surface at rest costs nothing; a slide costs one uniform
 * write per moved frame and no allocation. Motion is the page's pose, never its speed
 * ({@link Program#setPose}, {@link GlassPoseOptics}): a slide or a fade changes nothing in the
 * glass, since a plate moving in its own plane keeps its optics and the picture beneath already
 * drifts through the aim. A turn (Carousel, the stack's turn, the plank's tip, a press rig) shifts
 * the picture through the slab's thickness, turns the eye in the slab's frame so the bevel going
 * away takes more Fresnel light, raises the face's reflectance and sends one soft glint across the
 * face; a page's scale (Depth, Stack) magnifies the picture a little. A press deforms nothing: a
 * rigid slab does not dent under a finger (the user, 2026-10-20). Flat and unscaled every pose
 * term is skipped, and a slab at rest wears the rest look exactly.</p>
 */
public final class GlassRefraction {

    private GlassRefraction() {}

    /**
     * The blur radius, in dp, of the clearer frame a surface hands in for the bent edge to show
     * ({@link Program#setClarityInput}): a touch of blur so the upscale does not alias, kept under
     * the pre-blur's 25 px script radius so the frame stays full size. A surface whose own frost
     * is this clear or clearer takes none.
     */
    public static final int CLARITY_RADIUS_DP = 2;

    /** True on phones that run an AGSL shader at all. Everything below is reachable only then. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean available() {
        return Build.VERSION.SDK_INT >= FancierGlassPolicy.MIN_SDK;
    }

    // ------------------------------------------------------------------------------ the look

    /**
     * A surface's glass as stored, as one value: how far the picture is bent under the rim, how
     * far in from the rim the bend reaches, how far the colours part at the corners, and how clear
     * the wallpaper shows through the bent edge. It also carries the stored edge light and
     * specular, which the program no longer reads: the edge's light is fixed material. Stored in
     * dp and percent so two reads compare equal whatever the screen's density; the program
     * converts when it is handed one.
     */
    public static final class Look {

        /** The numbers the dock's refraction always ran with; the default mode is exactly this. */
        @NonNull public static final Look DEFAULT = new Look(
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_WIDTH,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_LIGHT);

        /**
         * The least the rim reaches in from the outline: 1 dp and never under 2 px
         * ({@link #rimReachPx}); once the tilted face's ease width.
         */
        static final float RIM_WIDTH_DP = 1f;
        static final float MIN_RIM_WIDTH_PX = 2f;

        // The Edge slider, 0 to 100 over the stored dispersion. 0 is no fringe; 40 is Glint,
        // Clear's shipped edge; past it the split grows on to 100.

        /**
         * {@code uDisp} at 100% ({@code kDispFull}): the program parts red's and blue's index of
         * refraction by {@code uDisp / kDispFull} of {@link GlassSlab#iorRed}'s and
         * {@link GlassSlab#iorBlue}'s spread. Up to Glint's 40% the share grows along
         * {@link #GLINT_SLOPE}; past it, on to this at 100%.
         */
        static final float MAX_DISPERSION_SHARE = 0.08f;
        /**
         * The share up to Glint's 40%, per whole of the percentage: the old cap's 0.05, so every
         * value up to Glint (0.02) draws exactly what it drew.
         */
        static final float GLINT_SLOPE = 0.05f;
        /** The Edge slider's Glint, percent: Clear's shipped edge, where the steeper share starts. */
        static final int GLINT_PERCENT = 40;
        /**
         * Glint's share, exactly as {@link #dispersion} gives it at {@link #GLINT_PERCENT} (a hair
         * over 0.02 in float).
         */
        static final float GLINT_SHARE = GLINT_PERCENT / 100f * GLINT_SLOPE;

        /**
         * Clear's face tone: the darkest share a white backdrop is dimmed by at dim 100 (Apple's
         * HIG 35% dimming layer), the luminance range it eases in across, and the lift toward
         * white over black. The program's {@code kDimMax}, {@code kDimFrom}, {@code kDimTo} and
         * {@code kFaceLift}.
         */
        static final float DIM_MAX = 0.35f;
        static final float DIM_FROM = 0.45f;
        static final float DIM_TO = 0.9f;
        static final float FACE_LIFT = 0.06f;
        /**
         * Size lens: the short side, dp, that scales nothing, and the least and most the lens zone and
         * the thickness are scaled by. The program's {@code kSizeRefDp}, {@code kSizeMin} and
         * {@code kSizeMax}.
         */
        static final float SIZE_REF_DP = 72f;
        /**
         * The short side, dp, up to which the face is wholly Clear's and from which it is wholly
         * the frost's again, smoothstep between ({@code kFadeFromDp}, {@code kFadeToDp}).
         */
        static final float FACE_FADE_FROM_DP = 200f;
        static final float FACE_FADE_TO_DP = 320f;
        static final float SIZE_MIN = 0.75f;
        static final float SIZE_MAX = 1.5f;

        // The rim's key light (GlassRim draws the line). Each is the program's constant of the
        // same name.

        /**
         * The key light's falloff round the outline, a wide lobe ({@code kKeyExp}): the rim's
         * white above its baseline is {@link GlassRim#spec} times the squared cosine toward the
         * key light, all of it at the top-left corner and none facing away
         * ({@link GlassRefraction#rimLobe}). The light is fixed in the room; a narrow glint at
         * rest read on pong as a bright smear in the top-left corner of every glass surface (the
         * user, 2026-10-20), and a turned slab's glint crosses its face instead
         * ({@link GlassPoseOptics#glint}).
         */
        static final float KEY_EXPONENT = 2f;
        public final int bendDp;
        public final int edgeWidthDp;
        /**
         * The stored edge light, a percentage. Kept so a saved look loads and compares as it
         * was; the program ignores it: the edge is the rim ({@link GlassRim}), fixed material.
         */
        public final int edgeLightPercent;
        /**
         * The stored specular, a percentage. Kept so a saved look loads and compares as it was;
         * the program ignores it, as {@link #edgeLightPercent}.
         */
        public final int specularPercent;
        /** The chromatic split at the corners, a percentage; 0 samples r, g and b alike. */
        public final int dispersionPercent;
        /**
         * The stored edge clarity, a percentage. Kept so a saved look loads and compares as it
         * was; the program ignores it, as {@link #specularPercent}: the edge shows the sharp
         * picture wholly wherever one is bound ({@link GlassRefraction#sharpShare}).
         */
        public final int clarityPercent;
        /**
         * Clear's face terms, percentages, 0 for every other Look (the program skips them): how
         * much of the clearer picture the face shows, how far the face is dimmed over a bright
         * backdrop and lifted over a dark one, and how far the lensing scales with the surface's
         * size.
         */
        public final int faceClarityPercent;
        public final int dimPercent;
        public final int sizeLensPercent;
        /**
         * The glass sinks into the page instead of standing out of it: the program draws the
         * picture through the concave lens ({@link GlassRefraction#concavePullPx}) and nothing
         * else, none of the convex rim's bend, rim line or colour split. Not stored; the
         * page bar asks for it ({@code PageBarGlassStyle}).
         */
        public final boolean concave;
        /** Shine, 0..100 ({@link GlassMaterial}); the default is the glass as it ships. */
        public final int shine;

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, 0, 0, 0);
        }

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent,
                    int specularPercent, int dispersionPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, specularPercent, dispersionPercent, 0);
        }

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent,
                    int specularPercent, int dispersionPercent, int clarityPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, specularPercent, dispersionPercent,
                clarityPercent, 0, 0, 0);
        }

        private Look(int bendDp, int edgeWidthDp, int edgeLightPercent, int specularPercent,
                     int dispersionPercent, int clarityPercent,
                     int faceClarityPercent, int dimPercent, int sizeLensPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, specularPercent, dispersionPercent,
                clarityPercent, faceClarityPercent, dimPercent, sizeLensPercent, false);
        }

        private Look(int bendDp, int edgeWidthDp, int edgeLightPercent, int specularPercent,
                     int dispersionPercent, int clarityPercent,
                     int faceClarityPercent, int dimPercent, int sizeLensPercent, boolean concave) {
            this(bendDp, edgeWidthDp, edgeLightPercent, specularPercent, dispersionPercent,
                clarityPercent, faceClarityPercent, dimPercent, sizeLensPercent, concave,
                GlassMaterial.SHINE_DEFAULT);
        }

        private Look(int bendDp, int edgeWidthDp, int edgeLightPercent, int specularPercent,
                     int dispersionPercent, int clarityPercent,
                     int faceClarityPercent, int dimPercent, int sizeLensPercent, boolean concave,
                     int shine) {
            this.shine = Math.max(GlassMaterial.MIN, Math.min(GlassMaterial.MAX, shine));
            this.concave = concave;
            this.faceClarityPercent = Math.max(0, Math.min(100, faceClarityPercent));
            this.dimPercent = Math.max(0, Math.min(100, dimPercent));
            this.sizeLensPercent = Math.max(0, Math.min(100, sizeLensPercent));
            this.bendDp = bendDp;
            this.edgeWidthDp = edgeWidthDp;
            this.edgeLightPercent = edgeLightPercent;
            this.specularPercent = specularPercent;
            this.dispersionPercent = dispersionPercent;
            this.clarityPercent = clarityPercent;
        }

        /** This look sunk into the page ({@link #concave}), or standing out of it as every other glass. */
        @NonNull
        public Look withConcave(boolean concave) {
            if (concave == this.concave) return this;
            return new Look(bendDp, edgeWidthDp, edgeLightPercent, specularPercent,
                dispersionPercent, clarityPercent, faceClarityPercent, dimPercent,
                sizeLensPercent, concave, shine);
        }

        /** This look with Clear's face terms set (percentages, 0 for none). */
        @NonNull
        public Look withFaceTerms(int faceClarity, int dim, int sizeLens) {
            Look next = new Look(bendDp, edgeWidthDp, edgeLightPercent, specularPercent,
                dispersionPercent, clarityPercent, faceClarity, dim, sizeLens, concave, shine);
            return next.equals(this) ? this : next;
        }

        /**
         * This look at {@code shine}: the light's multiple ({@link #shineScale}) and the colour
         * split that rides with it ({@link GlassMaterial#dispersionPercent}).
         */
        @NonNull
        public Look withShine(int shine) {
            int s = Math.max(GlassMaterial.MIN, Math.min(GlassMaterial.MAX, shine));
            Look next = new Look(bendDp, edgeWidthDp, edgeLightPercent, specularPercent,
                GlassMaterial.dispersionPercent(s), clarityPercent, faceClarityPercent,
                dimPercent, sizeLensPercent, concave, s);
            return next.equals(this) ? this : next;
        }

        /** {@code uShine}: the light's multiple of the room light, 0..2.5. */
        public float shineScale() {
            return GlassMaterial.shineScale(shine);
        }

        /**
         * The user's knobs: Depth's thickness and Shine, both app-wide. The edge width, light,
         * specular, clarity and face terms are gone: the lens zone's width is the slab's, the
         * rim is fixed material and the face is the Frost stop's.
         */
        @NonNull
        public static Look of(@NonNull TermuxAppSharedPreferences preferences) {
            return new Look(preferences.getFancierGlassBendDp(),
                TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_WIDTH,
                TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_LIGHT)
                .withShine(preferences.getGlassShine());
        }

        /** A percentage as 0..1, clamped. */
        private static float share(int percent) {
            return Math.max(0, Math.min(100, percent)) / 100f;
        }

        /**
         * {@code uDepth}: the slab's thickness, in px: the Depth ({@link #bendDp}), with no cap of
         * its own (the bend it gives is bounded by Snell's law, {@link GlassSlab#shiftPx}). The
         * stored Edge width ({@link #edgeWidthDp}) no longer reaches the program: the lens zone's
         * width is the slab's ({@link GlassSlab#lensWidthPx}).
         */
        public float thicknessPx(float density) {
            return Math.max(0f, bendDp * density * GlassMaterial.thicknessBoost(bendDp));
        }

        /**
         * {@code uDisp}: how far red's and blue's indices part, 0..{@link #MAX_DISPERSION_SHARE}
         * (a whole spread at the top).
         */
        public float dispersion() {
            int percent = Math.max(0, Math.min(100, dispersionPercent));
            if (percent <= GLINT_PERCENT)
                return share(percent) * GLINT_SLOPE;
            float glint = share(GLINT_PERCENT) * GLINT_SLOPE;
            return glint + (percent - GLINT_PERCENT) / (float) (100 - GLINT_PERCENT)
                * (MAX_DISPERSION_SHARE - glint);
        }

        /** {@code uFaceClarity}: how much of the clearer picture the face shows, 0..1. */
        public float faceClarity() {
            return share(faceClarityPercent);
        }

        /** {@code uDim}: the face tone's share, 0..1; 0 leaves the face as it is. */
        public float dim() {
            return share(dimPercent);
        }

        /** {@code uSizeLens}: how far the lensing follows the surface's size, 0..1. */
        public float sizeLens() {
            return share(sizeLensPercent);
        }

        /** {@link #RIM_WIDTH_DP} in px at {@code density}, never under {@link #MIN_RIM_WIDTH_PX}. */
        public static float rimWidthPx(float density) {
            return Math.max(MIN_RIM_WIDTH_PX, RIM_WIDTH_DP * density);
        }

        /**
         * How far in from the rim the rim's line ({@link GlassRim#reachPx}) and the dock shelf's
         * lip reach, in px: what a seam edge must be pushed past for neither to show along it, or
         * a line shows through one sheet of glass.
         */
        public static float rimReachPx(float density) {
            return Math.max(Math.max(rimWidthPx(density), GlassRim.active().reachPx(density)),
                shelfLipReachDp(GlassRim.active()) * density);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Look)) return false;
            Look that = (Look) other;
            return bendDp == that.bendDp && edgeWidthDp == that.edgeWidthDp
                && edgeLightPercent == that.edgeLightPercent
                && specularPercent == that.specularPercent
                && dispersionPercent == that.dispersionPercent
                && clarityPercent == that.clarityPercent
                && faceClarityPercent == that.faceClarityPercent
                && dimPercent == that.dimPercent
                && sizeLensPercent == that.sizeLensPercent
                && concave == that.concave && shine == that.shine;
        }

        @Override
        public int hashCode() {
            return (((((((((bendDp * 31 + edgeWidthDp) * 31 + edgeLightPercent) * 31 + specularPercent) * 31
                + dispersionPercent) * 31 + clarityPercent) * 31
                + faceClarityPercent) * 31 + dimPercent) * 31 + sizeLensPercent) * 31
                + (concave ? 1 : 0)) * 31 + shine;
        }

        @NonNull
        @Override
        public String toString() {
            return "Look{bend=" + bendDp + "dp, edge=" + edgeWidthDp + "dp, light=" + edgeLightPercent + "%, specular="
                + specularPercent + "%, dispersion=" + dispersionPercent + "%, clarity=" + clarityPercent
                + "%, face=" + faceClarityPercent + "%, dim=" + dimPercent
                + "%, sizeLens=" + sizeLensPercent + "%, shine=" + shine + (concave ? ", concave" : "") + "}";
        }
    }

    // ----------------------------------------------------------------------------- the seams

    /** An edge of the surface another glass surface continues past, so it takes no rim and no bend. */
    public static final int SEAM_LEFT = 1;
    public static final int SEAM_TOP = 1 << 1;
    public static final int SEAM_RIGHT = 1 << 2;
    public static final int SEAM_BOTTOM = 1 << 3;

    /**
     * The rim rect for a surface whose bounds are {@code bounds}: the bounds themselves, pushed
     * out by {@code reachPx} on every seam side so that edge — and the corner arcs that would
     * have met it — lands past the visible surface and reads as interior glass. The keyboard
     * host over the under-pill strip and the status band over the window bar are the cases; a
     * rim drawn at their shared edge is a bright line through one sheet of glass.
     *
     * @param out {@code {left, top, right, bottom}}
     */
    public static void rimRect(@NonNull float[] out, @NonNull Rect bounds, float reachPx, int seams) {
        rimRect(out, bounds.left, bounds.top, bounds.right, bounds.bottom, reachPx, seams);
    }

    /** {@link #rimRect(float[], Rect, float, int)} for a surface drawn at fractional bounds: a corner tab mid-slide. */
    public static void rimRect(@NonNull float[] out, float left, float top, float right, float bottom,
                               float reachPx, int seams) {
        out[0] = left - ((seams & SEAM_LEFT) != 0 ? reachPx : 0f);
        out[1] = top - ((seams & SEAM_TOP) != 0 ? reachPx : 0f);
        out[2] = right + ((seams & SEAM_RIGHT) != 0 ? reachPx : 0f);
        out[3] = bottom + ((seams & SEAM_BOTTOM) != 0 ? reachPx : 0f);
    }

    /**
     * The corner radius the program draws a rim rect at: {@code cornerRadiusPx}, held to half the
     * rect's shorter side, the most a rounded rect can take. The program's distance field
     * ({@code sdRoundRect}) does not hold it itself, and past half the height it goes wrong in a
     * way that shows: the two ends' arcs cross into a point folding in from each end, and the
     * light creases along the centre line. A surface clipped as a capsule (a one-row band under
     * the keyboard, given the dock's Corners before its own height was known) is cut to the same
     * half-height, so the rim lands on its outline again.
     */
    public static float rectRadiusPx(float left, float top, float right, float bottom,
                                     float cornerRadiusPx) {
        float half = Math.min(Math.max(0f, right - left), Math.max(0f, bottom - top)) / 2f;
        return Math.max(0f, Math.min(cornerRadiusPx, half));
    }

    /**
     * How far a seam edge is pushed out: past the lens zone (or the rim's line, where the zone
     * ends inside its reach) and the corner arc, with a little to spare. The zone is the largest
     * any surface takes ({@link GlassSlab#lensPx}), grown by Clear's size lens where the look has
     * it and by a whole shelf, so no lens bends the picture along a seam; past the zone nothing
     * bends at all, whatever the Depth.
     */
    public static float seamReachPx(@NonNull Look look, float density, float cornerRadiusPx) {
        return seamReachPx(look, density, cornerRadiusPx, 0f);
    }

    /**
     * {@link #seamReachPx(Look, float, float)} for a surface wearing the dock's soft rim band
     * {@code softRimPx} wide ({@link Program#setDockSurface}): the zone a seam edge must clear is
     * the wider of the slab's and the soft one.
     */
    public static float seamReachPx(@NonNull Look look, float density, float cornerRadiusPx,
                                    float softRimPx) {
        float size = 1f + (Look.SIZE_MAX - 1f) * look.sizeLens();
        float zone = Math.max(GlassSlab.active().lensPx(density), Math.max(0f, softRimPx)) * size
            * (1f + SHELF_THICKEN);
        return Math.max(zone, Look.rimReachPx(density)) + Math.max(0f, cornerRadiusPx)
            + 2f * density;
    }

    // ---------------------------------------------------------------------------- the program

    /**
     * The AGSL. {@code content} is whatever the surface shows — the view's own pixels under a
     * {@code RenderEffect}, the frame's {@link BitmapShader} as a paint shader — and
     * {@code uFrameScale}/{@code uFrameOffset} are the aim that puts frame pixel {@code (0, 0)}
     * where the frame's rect begins, as {@link SharedFrameDrawable#aim} states it: identity under
     * a {@code RenderEffect}. {@code uLocalX}/{@code uLocalY}/{@code uLocalW} are the rows of
     * {@link GlassAnchor.Aim#local}, from the surface's own px to px from its origin on screen,
     * applied first with its perspective divide: identity everywhere but on a wall page that is
     * sunk or tipped, where it keeps the wallpaper fixed to the screen. {@link #framePx} is the
     * same mapping in Java.
     */
    private static final String AGSL_HEAD =
        "uniform shader content;\n" +
        // The same picture with less frost, aimed exactly as content is; bound to content itself
        // where there is none, so the mix below is then the identity.
        "uniform shader sharp;\n" +
        "uniform float2 uFrameScale;\n" +
        "uniform float2 uFrameOffset;\n" +
        "uniform float3 uLocalX;\n" +
        "uniform float3 uLocalY;\n" +
        "uniform float3 uLocalW;\n" +
        "uniform float2 uRectMin;\n" +
        "uniform float2 uRectMax;\n" +
        "uniform float uRadius;\n" +
        // The slab's thickness, px: the look's Depth (Look.thicknessPx). GlassSlab.
        "uniform float uDepth;\n" +
        // The colour split, 0..kDispFull: Look.dispersion.
        "uniform float uDisp;\n" +
        // The light's multiple of the room light on the rim and the lens, 0..2.5: Look.shine
        // (GlassMaterial.shineScale); 1 is the glass as it ships.
        "uniform float uShine;\n" +
        // 1 while sharp is a clearer picture of its own, 0 while it is the frost bound in its
        // place (or nothing): the lens zone then shows that picture wholly toward the rim.
        "uniform float uSharpEdge;\n" +
        "uniform float uFaceClarity;\n" +
        "uniform float uDim;\n" +
        "uniform float uSizeLens;\n" +
        "uniform float uDensity;\n" +
        // A finger on the glass: where it rests, how far its light reaches, and its 0..1 fade.
        "uniform float uTouchActive;\n" +
        "uniform float2 uTouch;\n" +
        "uniform float uTouchRadius;\n" +
        "uniform float uTouchLens;\n" +
        // The page's pose (GlassPose; GlassPoseOptics.uniforms in Java). uPose: x, y the sine of
        // the slab's turn as a vector in its own px axes pointing at the edge going away from the
        // viewer, z the turn's cosine, w how far the picture shifts through the slab per px of its
        // thickness (tan θi - tan θt). (0, 0, 1, 0) flat.
        "uniform float4 uPose;\n" +
        // uPoseLight: x the magnification of the picture through the glass (1 unscaled), y how
        // much the face's reflectance rises over head-on, z the share of the turn's Fresnel the
        // bevel takes (0 flat), w the most the turn shifts the picture, px.
        "uniform float4 uPoseLight;\n" +
        // uPoseHalf: xyz the half vector between the room light and the eye in the turned slab's
        // frame (kHalf flat), w what the bevel's Schlick reflectance takes on to meet the face's.
        "uniform float4 uPoseHalf;\n" +
        // uGlint: the turn's glint, its centre from the slab's centre, px, its half width, px,
        // and its white (0 flat).
        "uniform float4 uGlint;\n" +
        // The dock surface's own glass (DockSurfaceSpec), 0 on every other surface, which then
        // draws exactly as before: the soft rim band's width in px, how much of the lower shelf
        // the slab wears (0..1). SOFT_RIM_DP and setDockSurface in Java.
        "uniform float uSoftRim;\n" +
        "uniform float uShelf;\n" +
        // Lens beads (DockSurfaceSpec.LENS_BEADS): no slab, one round bead under each icon of the
        // row. uBead holds the row's centre line y, every bead's radius (0: no beads, the slab
        // as always) and half the icon's size, all in the surface's px; uBeadXA, uBeadXB and
        // uBeadXC the beads' centre x, twelve at most (a row at rest holds eight; a page swipe
        // carries the neighbouring page's in beside them), an unused one far off the surface so
        // it is never the nearest. setBeads and nearestBeadX in Java.
        "uniform float4 uBead;\n" +
        "uniform float4 uBeadXA;\n" +
        "uniform float4 uBeadXB;\n" +
        "uniform float4 uBeadXC;\n" +
        // The Search field's capsule under the beads (#84): its left, top, right and bottom in
        // the surface's px, a stadium (its corners half its height); right not past left is
        // none. One more shape of the beads' glass: a pixel is the capsule's where it stands
        // deeper in it than in its nearest bead, so a melt's swelling beads take it over.
        // setBeadCapsule in Java.
        "uniform float4 uBeadCap;\n" +
        // The A–Z row's letter beads under the icons' (com.termux.app.dock.AzBeadRow): uAzBead
        // the first centre x, the pitch, the count and the row's centre line y; uAzLift every
        // letter bead's radius (0: none), the finger's x and the lift's peak, px (up positive).
        // A letter bead rises by the raised cosine AzBeadRow.liftShare about the finger. One
        // more shape of the beads' glass, chosen as the capsule is. setAzBeads in Java.
        "uniform float4 uAzBead;\n" +
        "uniform float4 uAzLift;\n" +
        "const float kAzLiftReach = " + com.termux.app.dock.AzBeadRow.LIFT_REACH_SLOTS + ";\n" +
        // Active extra-key "lens": a rounded-rect that MAGNIFIES (bends) the backdrop strongest from
        // the middle and eases to nothing at its rim, so a pressed key reads as a thick glass pill
        // refracting the wallpaper that shows through the transparent key cell. uLensActive carries
        // the 0..1 fade intensity.
        "uniform float uLensActive;\n" +
        "uniform float2 uLensCenter;\n" +
        "uniform float2 uLensHalf;\n" +
        "uniform float uLensRadius;\n" +
        "uniform float uLensStrength;\n" +
        "uniform float uLensFeather;\n" +
        // The concave lens (Look.concave): x 1 to draw it, 0 for every other surface, which then
        // draws exactly as before; y the deepest its band reaches, px. setLook in Java.
        "uniform float2 uConcave;\n" +
        // Its band as the most of the short side, its pull as a share of the band, and the
        // steepest the pull may rise across the lip. CONCAVE_BAND_MAX_SHARE, CONCAVE_PULL_SHARE
        // and CONCAVE_LIP_MAX_SLOPE in Java.
        "const float kConcaveBandShare = " + GlassRefraction.CONCAVE_BAND_MAX_SHARE + ";\n" +
        "const float kConcavePullShare = " + GlassRefraction.CONCAVE_PULL_SHARE + ";\n" +
        "const float kConcaveLipSlope = " + GlassRefraction.CONCAVE_LIP_MAX_SLOPE + ";\n" +
        // uDisp at Edge 100%: Look.MAX_DISPERSION_SHARE in Java.
        "const float kDispFull = " + Look.MAX_DISPERSION_SHARE + ";\n" +
        // The pressed key's added glow at its centre; KEY_GLOW in Java.
        "const float kKeyGlow = 0.15;\n";

    /**
     * The program from the rim's constants on: the key light, the glint, and everything below
     * them. {@link #agsl} puts the rim's numbers ({@link #rimConstants}) between the two halves.
     */
    private static final String AGSL_BODY =
        // The rim's key light: up and to the left, the wide lobe. KEY_LIGHT_X, KEY_LIGHT_Y,
        // LUMA_R/G/B and Look.KEY_EXPONENT in Java; how much of the lobe a finger adds, TOUCH_RIM.
        "const float2 kKeyLight = float2(" + GlassRefraction.KEY_LIGHT_X + ", " + GlassRefraction.KEY_LIGHT_Y + ");\n" +
        "const float3 kLumaW = float3(0.2126, 0.7152, 0.0722);\n" +
        "const float kKeyExp = " + Look.KEY_EXPONENT + ";\n" +
        "const float kTouchRim = " + GlassRefraction.TOUCH_RIM + ";\n" +
        // Clear's face terms: Look.DIM_MAX, DIM_FROM, DIM_TO, FACE_LIFT, SIZE_REF_DP, SIZE_MIN and
        // SIZE_MAX in Java.
        "const float kDimMax = " + Look.DIM_MAX + ";\n" +
        "const float kDimFrom = " + Look.DIM_FROM + ";\n" +
        "const float kDimTo = " + Look.DIM_TO + ";\n" +
        "const float kFaceLift = " + Look.FACE_LIFT + ";\n" +
        "const float kSizeRefDp = " + Look.SIZE_REF_DP + ";\n" +
        "const float kSizeMin = " + Look.SIZE_MIN + ";\n" +
        "const float kFadeFromDp = " + Look.FACE_FADE_FROM_DP + ";\n" +
        "const float kFadeToDp = " + Look.FACE_FADE_TO_DP + ";\n" +
        "const float kSizeMax = " + Look.SIZE_MAX + ";\n" +
        // The dock surface's glass: how much thicker and wider the lower edge's lens is per whole
        // shelf; the lower lip's light and how fast it fades inward, dp. SHELF_THICKEN,
        // SHELF_LIP and SHELF_LIP_DP in Java.
        "const float kShelfThick = " + GlassRefraction.SHELF_THICKEN + ";\n" +
        "const float kShelfLip = " + GlassRefraction.SHELF_LIP + ";\n" +
        "const float kShelfLipDp = " + GlassRefraction.SHELF_LIP_DP + ";\n" +
        // The most of a bead's radius its lens zone reaches; BEAD_BAND_SHARE in Java.
        "const float kBeadBandShare = " + GlassRefraction.BEAD_BAND_SHARE + ";\n" +
        "float sdRoundRect(float2 p, float2 b, float r) {\n" +
        "    float2 q = abs(p) - b + float2(r, r);\n" +
        "    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;\n" +
        "}\n" +
        // The outward normal of the rounded rect: round the corner arc, straight out along an edge,
        // and on the inner rect, where no arc reaches, the axis of the nearer edge.
        "float2 rectNormal(float2 p, float2 b, float r) {\n" +
        "    float2 q = abs(p) - b + float2(r, r);\n" +
        "    float2 outside = max(q, float2(0.0, 0.0));\n" +
        "    float len = length(outside);\n" +
        "    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);\n" +
        "    if (len > 0.001) {\n" +
        "        return s * (outside / len);\n" +
        "    }\n" +
        "    float useX = step(q.y, q.x);\n" +
        "    return s * float2(useX, 1.0 - useX);\n" +
        "}\n" +
        // A surface pixel to the content's own pixel: the local matrix, then the aim undone. With
        // the local matrix at identity this is exactly the old (c - offset) / scale.
        "float2 framePx(float2 c) {\n" +
        "    float3 p = float3(c, 1.0);\n" +
        "    float3 h = float3(dot(uLocalX, p), dot(uLocalY, p), dot(uLocalW, p));\n" +
        "    return (h.xy / h.z - uFrameOffset) / uFrameScale;\n" +
        "}\n" +
        // The slab's profile slope across the lens zone, d(h/T)/de, e 0 at the outline to 1 where
        // the zone meets the flat face: the superellipse (1 - (1 - e)^p)^(1/p)'s, 0 at the face
        // (C1 with it) and rising toward the outline, where it is read no closer than kSlabEMin.
        // GlassSlab.profileSlope in Java.
        "float slabSlope(float e) {\n" +
        "    float u = 1.0 - max(e, kSlabEMin);\n" +
        "    return pow(u, kSlabP - 1.0) * pow(1.0 - pow(u, kSlabP), 1.0 / kSlabP - 1.0);\n" +
        "}\n" +
        // Snell at a face of slope s at index ni: how far the ray straight down leans once inside,
        // as a tangent, tan(θi - θt) by the tangent difference. 0 flat and never past
        // sqrt(ni² - 1). GlassSlab.snellTan in Java (LiquidGlass's snellShift, Apache-2.0).
        "float snellTan(float s, float ni) {\n" +
        "    float si = s * inversesqrt(1.0 + s * s);\n" +
        "    float st = si / ni;\n" +
        "    float t2 = st * inversesqrt(max(1.0 - st * st, 1.0e-6));\n" +
        "    return (s - t2) / (1.0 + s * t2);\n" +
        "}\n" +
        // The centre x of the bead nearest x: twelve compares, no loop and no array, so the
        // program compiles everywhere AGSL does. nearestBeadX in Java.
        // The concave lens's pull at y px in from the rim, outward: up from nothing at the rim
        // across the lip cL on a smoothstep to cP, then down to nothing at the band's inner edge
        // cB on another. concavePullPx in Java.
        "float concavePull(float y, float cB, float cP, float cL) {\n" +
        "    if (y <= 0.0 || y >= cB) { return 0.0; }\n" +
        "    if (y < cL) { return cP * smoothstep(0.0, cL, y); }\n" +
        "    return cP * (1.0 - smoothstep(cL, cB, y));\n" +
        "}\n" +
        "float nearestBead(float x) {\n" +
        "    float bx = uBeadXA.x;\n" +
        "    float bd = abs(x - uBeadXA.x);\n" +
        "    float d = abs(x - uBeadXA.y);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXA.y; }\n" +
        "    d = abs(x - uBeadXA.z);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXA.z; }\n" +
        "    d = abs(x - uBeadXA.w);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXA.w; }\n" +
        "    d = abs(x - uBeadXB.x);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXB.x; }\n" +
        "    d = abs(x - uBeadXB.y);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXB.y; }\n" +
        "    d = abs(x - uBeadXB.z);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXB.z; }\n" +
        "    d = abs(x - uBeadXB.w);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXB.w; }\n" +
        "    d = abs(x - uBeadXC.x);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXC.x; }\n" +
        "    d = abs(x - uBeadXC.y);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXC.y; }\n" +
        "    d = abs(x - uBeadXC.z);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXC.z; }\n" +
        "    d = abs(x - uBeadXC.w);\n" +
        "    if (d < bd) { bd = d; bx = uBeadXC.w; }\n" +
        "    return bx;\n" +
        "}\n" +
        // The key light's wide lobe on an outline facing kKeyLight by c, the cosine: the rim's
        // white above its baseline at rest, nothing facing away. rimLobe in Java.
        "float rimLobe(float c) {\n" +
        "    return c > 0.0 ? pow(c, kKeyExp) : 0.0;\n" +
        "}\n" +
        "half4 main(float2 fragCoord) {\n" +
        "    float2 center = (uRectMin + uRectMax) * 0.5;\n" +
        "    float2 b = (uRectMax - uRectMin) * 0.5;\n" +
        "    float cr = uRadius;\n" +
        // Lens beads: the surface is the bead nearest this pixel, a circle about its icon, and
        // everything below (the rim, its light, the shelf) is that bead's. A
        // pixel clear of every bead draws nothing and reads no sample.
        "    bool beads = uBead.y > 0.0;\n" +
        "    bool onCap = false;\n" +
        "    if (beads) {\n" +
        "        center = float2(nearestBead(fragCoord.x), uBead.x);\n" +
        "        b = float2(uBead.y, uBead.y);\n" +
        "        cr = uBead.y;\n" +
        "        if (uBeadCap.z > uBeadCap.x) {\n" +
        "            float2 capC = (uBeadCap.xy + uBeadCap.zw) * 0.5;\n" +
        "            float2 capB = (uBeadCap.zw - uBeadCap.xy) * 0.5;\n" +
        "            float capR = min(capB.x, capB.y);\n" +
        "            if (sdRoundRect(fragCoord - capC, capB, capR) < sdRoundRect(fragCoord - center, b, cr)) {\n" +
        "                center = capC;\n" +
        "                b = capB;\n" +
        "                cr = capR;\n" +
        "                onCap = true;\n" +
        "            }\n" +
        "        }\n" +
        // The letter bead nearest this pixel along the row (its slot), risen by its lift; it
        // takes the pixel where the pixel stands deeper in it than in the shape chosen so far.
        "        if (uAzLift.x > 0.0 && uAzBead.y > 0.0) {\n" +
        "            float ai = clamp(floor((fragCoord.x - uAzBead.x) / uAzBead.y + 0.5), 0.0, uAzBead.z - 1.0);\n" +
        "            float ax = uAzBead.x + ai * uAzBead.y;\n" +
        "            float ad = abs(ax - uAzLift.y) / uAzBead.y;\n" +
        "            float al = ad < kAzLiftReach ? uAzLift.z * (0.5 + 0.5 * cos(3.14159265 * ad / kAzLiftReach)) : 0.0;\n" +
        "            float2 aC = float2(ax, uAzBead.w - al);\n" +
        "            float2 aB = float2(uAzLift.x, uAzLift.x);\n" +
        "            if (sdRoundRect(fragCoord - aC, aB, uAzLift.x) < sdRoundRect(fragCoord - center, b, cr)) {\n" +
        "                center = aC;\n" +
        "                b = aB;\n" +
        "                cr = uAzLift.x;\n" +
        "                onCap = false;\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    float2 rMin = center - b;\n" +
        "    float2 rMax = center + b;\n" +
        "    float2 p = fragCoord - center;\n" +
        "    float inside = -sdRoundRect(p, b, cr);\n" +
        "    if (beads && inside < -1.0) {\n" +
        "        return half4(0.0);\n" +
        "    }\n" +
        // The light's normal turns on the drawn corner (held under the half size), the arc the
        // view's clip outline follows, so the light runs round the corner the surface is cut to
        // and joins the straights without a step. It is the slab's normal too (the field's
        // gradient); only the bend's direction below may take a wider arc.
        "    float2 n = rectNormal(p, b, min(cr, min(b.x, b.y)));\n" +
        // Whether the page turns the slab or scales it (GlassPose); neither at rest, and then
        // every pose term below is skipped.
        "    bool turned = uPose.x != 0.0 || uPose.y != 0.0;\n" +
        "    bool scaled = uPoseLight.x != 1.0;\n" +
        // Every lens below is held at half the surface's short side, so a one-row strip still
        // bends to nothing on its centre line: the two halves' bends point opposite ways there,
        // and a bend left at the line tears the picture into a seam.
        "    float hs = min(b.x, b.y);\n" +
        // The concave lens (a well, the page bar): each pixel shows the picture from further out
        // along the rim's normal, so the wallpaper is drawn in toward the centre line and pressed
        // together in the band; nothing on the face and nothing at the outline, which shows the
        // picture right there. The pull never rises faster than kConcaveLipSlope, so the sample
        // never passes the one before it (no fold, no stretch into streaks) and never leaves the
        // outline. One sample; none of the convex rim's light, split or motion. concaveOffset in
        // Java.
        "    if (uConcave.x > 0.0) {\n" +
        "        float cB = min(uConcave.y, kConcaveBandShare * 2.0 * hs);\n" +
        "        float cP = kConcavePullShare * cB;\n" +
        "        float cL = 1.5 * cP / kConcaveLipSlope;\n" +
        "        float2 cn = rectNormal(p, b, min(max(cr * 1.5, cB), hs));\n" +
        "        return content.eval(framePx(fragCoord + cn * concavePull(inside, cB, cP, cL)));\n" +
        "    }\n" +
        // Size lens (Clear): the lens zone, the thickness and the dock's soft band scale with the
        // surface's short side, a thicker slab bending more and reaching deeper; exactly 1 at 0,
        // so the other Looks multiply by one. The holds at hs below still hold. sizeLensK in Java.
        "    float sizeK = uSizeLens > 0.0\n" +
        "        ? mix(1.0, clamp(2.0 * hs / (kSizeRefDp * uDensity), kSizeMin, kSizeMax), uSizeLens) : 1.0;\n" +
        // How much of Clear's face the surface's size leaves it, 1 down to kFadeFromDp of short
        // side and 0 from kFadeToDp: the sharp face and the tone, which a large content surface
        // does not take. faceFade in Java.
        "    float faceFade = (uFaceClarity > 0.0 || uDim > 0.0)\n" +
        "        ? 1.0 - smoothstep(kFadeFromDp * uDensity, kFadeToDp * uDensity, 2.0 * hs) : 1.0;\n" +
        "    float faceMix = uFaceClarity * faceFade;\n" +
        "    float dimK = uDim * faceFade;\n" +
        "    float sSoft = uSoftRim * sizeK;\n" +
        // The slab (GlassSlab): flat in the middle, curving down to the outline across a lens
        // zone w wide. w is kLensDp on a large surface and kLensShare of the short side on a small
        // piece (sizeShare < 1), whose thickness shrinks with it, so a chip is the same slab scaled
        // down. The dock's soft rim widens the zone, gentler (a bead's held under kBeadBandShare
        // of its radius), and its shelf makes the lower edge's zone wider and thicker together
        // (below). GlassSlab.lensWidthPx in Java.
        "    float lensMax = max(kLensDp * uDensity, 0.001);\n" +
        "    float sizeShare = min(kLensShare * 2.0 * hs / lensMax, 1.0);\n" +
        "    float w = lensMax * sizeShare * sizeK;\n" +
        "    if (uSoftRim > 0.0) {\n" +
        "        w = beads ? min(max(sSoft, w), kBeadBandShare * cr) : max(w, sSoft);\n" +
        "    }\n" +
        // Pixels past the deepest zone (a whole shelf's) are the flat face: they skip every lens
        // term below and take one sample where they are. bendReachPx in Java.
        "    float reach = w * (1.0 + kShelfThick * uShelf);\n" +
        // The bend's direction. Where the zone is deeper than the drawn corner (a 3 dp Corners, or
        // Docked's square dock, under a 16 dp zone) the drawn field's gradient mitres along each
        // corner's diagonal, and a bend that snapped there would crease the picture from each
        // corner in. So the bend alone turns on an arc as deep as the zone (bendNormalRadiusPx in
        // Java), which every bent pixel lies outside of; where the drawn corner is deeper it is the
        // drawn arc exactly. The light keeps the drawn normal n.
        "    float2 nb = n;\n" +
        "    float shiftG = 0.0;\n" +
        "    float shiftR = 0.0;\n" +
        "    float shiftB = 0.0;\n" +
        "    float slope = 0.0;\n" +
        "    float e = 1.0;\n" +
        "    if (inside < reach && uDepth > 0.0) {\n" +
        "        nb = rectNormal(p, b, min(max(cr, reach), hs));\n" +
        // The shelf: the lower edge's zone wider and its slab thicker, by how far the bend's
        // direction faces down, so it turns round the lower corners as smoothly as the bend does.
        // The thickness is held so the deepest bend reaches the centre line at most.
        // lensZonePx and lensThicknessPx in Java.
        "        float lower = uShelf > 0.0 ? max(nb.y, 0.0) * uShelf : 0.0;\n" +
        "        float thick = 1.0 + kShelfThick * lower;\n" +
        "        float wr = min(w * thick, hs);\n" +
        "        float slabT = min(uDepth * sizeK * sizeShare * thick, hs * kSlabHold);\n" +
        "        e = clamp(inside / max(wr, 0.001), 0.0, 1.0);\n" +
        "        if (e < 1.0) {\n" +
        // The slab's slope here, rise over run, and the ray straight down bent by Snell's law at
        // it, travelling the thickness: the picture this pixel shows lies shiftG further in. 0 on
        // the face, never past slabT sqrt(n² - 1) at the outline. GlassSlab.shiftPx in Java.
        "            slope = slabT / wr * slabSlope(e);\n" +
        "            shiftG = slabT * snellTan(slope, kIor);\n" +
        "            shiftR = shiftG;\n" +
        "            shiftB = shiftG;\n" +
        // Dispersion (the Edge slider): red and blue refract at their own index, so the colours
        // part in step with the bend, alike on straights and corners and nothing on the face.
        // GlassSlab.iorRed and iorBlue in Java.
        "            if (uDisp > 0.0) {\n" +
        "                float dk = uDisp / kDispFull;\n" +
        "                shiftR = slabT * snellTan(slope, kIor - kDispRed * dk);\n" +
        "                shiftB = slabT * snellTan(slope, kIor + kDispBlue * dk);\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    float2 sampleCoord = fragCoord - nb * shiftG;\n" +
        // The turned slab (GlassPoseOptics.shiftTan): the ray from the eye meets the slab at the
        // turn and refracts through its thickness, so the whole picture shifts toward the near
        // edge by T (tan θi - tan θt), showing it carried toward the edge going away. One shift
        // for the whole slab, so it takes no colour split of its own.
        "    if (turned) {\n" +
        "        float faceT = min(uDepth * sizeK * sizeShare, hs * kSlabHold);\n" +
        "        sampleCoord = sampleCoord - normalize(uPose.xy) * min(faceT * uPose.w, uPoseLight.w);\n" +
        "    }\n" +
        // The scaled page (GlassPoseOptics.magnify): the picture through the glass a little
        // larger or smaller about the slab's centre as the page rises or recedes.
        "    if (scaled) {\n" +
        "        sampleCoord = center + (sampleCoord - center) / uPoseLight.x;\n" +
        "    }\n" +
        // A finger on the glass: its reach, 1 under the fingertip and 0 at uTouchRadius. The
        // picture is drawn a little toward the touch (a shallow lens, uTouchLens of the distance
        // at most), and the glow and the rim light below reuse the same reach.
        "    float touchG = 0.0;\n" +
        "    if (uTouchActive > 0.001) {\n" +
        "        float2 tp = fragCoord - uTouch;\n" +
        "        touchG = (1.0 - smoothstep(0.0, max(uTouchRadius, 1.0), length(tp))) * uTouchActive;\n" +
        "        sampleCoord = sampleCoord - tp * (uTouchLens * touchG);\n" +
        "    }\n" +
        // Per-key lens: magnify the backdrop toward the key centre, fading out to the pill rim.
        "    float lensGlow = 0.0;\n" +
        "    if (uLensActive > 0.001) {\n" +
        "        float2 lp = fragCoord - uLensCenter;\n" +
        "        float ld = -sdRoundRect(lp, uLensHalf, uLensRadius);\n" +
        "        if (ld > 0.0) {\n" +
        "            float2 ln = float2(lp.x / max(uLensHalf.x, 1.0), lp.y / max(uLensHalf.y, 1.0));\n" +
        "            float rr = clamp(length(ln), 0.0, 1.0);\n" +
        "            float kFull = mix(1.0 - uLensStrength, 1.0, smoothstep(0.5, 1.0, rr));\n" +
        "            float k = mix(1.0, kFull, uLensActive);\n" +
        "            float fade = smoothstep(0.0, max(uLensFeather, 1.0), ld) * uLensActive;\n" +
        "            float2 lensCoord = uLensCenter + lp * k;\n" +
        "            float2 lensBent = lensCoord - nb * shiftG;\n" +
        "            sampleCoord = mix(sampleCoord, lensBent, fade);\n" +
        "            lensGlow = fade * (1.0 - rr);\n" +
        "        }\n" +
        "    }\n" +
        // The lens zone's share of the sharp picture: a bevel is thin glass, so it shows what is
        // behind it unblurred (de Rousiers et al. 2011), easing in from the frost where the zone
        // meets the face, half way at its middle, whole at the outline. The soft zone eases on a
        // smootherstep, so the sharp picture has no inner edge there either. Zero on the face,
        // which keeps its frost. sharpShare in Java.
        "    float x = 1.0 - e;\n" +
        "    float edgeK = x > 0.0 ? (uSoftRim > 0.0 ? x * x * x * (x * (x * 6.0 - 15.0) + 10.0)\n" +
        "        : x * x * (3.0 - 2.0 * x)) : 0.0;\n" +
        "    float clarityK = edgeK * uSharpEdge;\n" +
        // Clear's face: the clearer picture shows through by uFaceClarity everywhere, the zone
        // included, where one is handed in (uFaceClarity is 0 where none is). At 0 nothing runs.
        "    if (faceMix > 0.0) {\n" +
        "        clarityK = mix(clarityK, 1.0, faceMix);\n" +
        "    }\n" +
        // Where red and blue read from, against green's sample: their own refraction. Three taps
        // only where a channel parts by more than half a px; every face pixel takes one.
        "    float2 oR = nb * (shiftG - shiftR);\n" +
        "    float2 oB = nb * (shiftG - shiftB);\n" +
        "    half4 col = half4(0.0);\n" +
        "    if (max(dot(oR, oR), dot(oB, oB)) > 0.25) {\n" +
        "        float2 fr = framePx(sampleCoord + oR);\n" +
        "        float2 fg = framePx(sampleCoord);\n" +
        "        float2 fb = framePx(sampleCoord + oB);\n" +
        "        half4 g = content.eval(fg);\n" +
        "        col = half4(content.eval(fr).r, g.g, content.eval(fb).b, g.a);\n" +
        "        if (clarityK > 0.0) {\n" +
        "            half4 sg = sharp.eval(fg);\n" +
        "            half4 clear = half4(sharp.eval(fr).r, sg.g, sharp.eval(fb).b, sg.a);\n" +
        "            col = mix(col, clear, half(clarityK));\n" +
        "        }\n" +
        "    } else {\n" +
        "        float2 f = framePx(sampleCoord);\n" +
        "        col = content.eval(f);\n" +
        "        if (clarityK > 0.0) {\n" +
        "            col = mix(col, sharp.eval(f), half(clarityK));\n" +
        "        }\n" +
        "    }\n" +
        // Clear's face tone, before the light so the light is not toned: a bright backdrop is
        // darkened toward black by up to kDimMax, easing in across kDimFrom..kDimTo of
        // luminance, and the rest lifted toward white by kFaceLift of its darkness. All of it
        // scaled by uDim, so at 0 nothing runs. faceTone in Java.
        "    if (dimK > 0.0) {\n" +
        "        float ca = max(float(col.a), 0.001);\n" +
        "        float3 cc = float3(col.rgb) / ca;\n" +
        "        float cl = dot(cc, kLumaW);\n" +
        "        float dk = dimK * kDimMax * smoothstep(kDimFrom, kDimTo, cl);\n" +
        "        cc = cc * (1.0 - dk);\n" +
        "        cc = cc + (float3(1.0) - cc) * (dimK * kFaceLift * (1.0 - cl));\n" +
        "        col.rgb = half3(cc * ca);\n" +
        "    }\n" +
        // The light, in linear light on the unpremultiplied sample, premultiplied again and held
        // under the alpha. Pixels past the rim's reach and the lens zone skip it.
        // The lens zone (GlassSlab.lit): the picture let through by 1 - F and F of the sky tone,
        // Schlick's F from the slab's normal and the view; Fresnel stands back under the rim's
        // line, which already lights the outline. Then the room light's white specular,
        // Blinn-Phong on the slab's normal (the drawn normal n tipped by the slope) under the one
        // fixed key light, less its value on the flat face so the face takes none.
        // The rim (GlassRim): one thin, crisp line along the outline, the same distance field on
        // every side and round every corner. Its weight across the depth is whole to kRimLineDp,
        // off on a smoothstep over kRimSoftDp, plus a short halo (BorderBeam's profile). The line
        // darkens what is behind it (the dark line) and adds white (the light line), on every
        // channel alike, so it adds no colour of its own: kRimBase of white and kRimDarkAll of
        // the dark on every side alike, more white where the outline faces the key light
        // (kKeyLight, its wide lobe) and more dark facing away. A finger lights the rim near it;
        // the baseline never moves. The dock's shelf adds its lip's white on the lower edge.
        // GlassRim.weight, white, darkening and lit, and rimLobe and rimAway in Java.
        // A turned slab (GlassPoseOptics) is lit from the same fixed room: in its frame the eye
        // leans toward the near edge, so the bevel going away meets it more steeply and takes
        // more Schlick Fresnel, the half vector turns, the flat face reflects more of the sky,
        // and one soft glint crosses the face. All under the alpha, like every light here.
        "    float lineCore = max(kRimLineDp * uDensity, kRimMinLinePx);\n" +
        "    float lineSoft = max(kRimSoftDp * uDensity, kRimMinSoftPx);\n" +
        "    float lineHalo = max(kRimHaloDp * uDensity, 0.01);\n" +
        "    float lineReach = lineCore + max(lineSoft, lineHalo * kRimHaloSpan);\n" +
        "    float shelfW = 0.0;\n" +
        "    if (uShelf > 0.0 && n.y > 0.0 && inside < kShelfLipReachDp * uDensity) {\n" +
        "        shelfW = exp(-max(inside, 0.0) / (kShelfLipDp * uDensity)) * kShelfLip * uShelf\n" +
        "            * smoothstep(0.2, 1.0, n.y);\n" +
        "    }\n" +
        "    if ((inside < lineReach || shelfW > 0.0 || slope > 0.0 || turned) && col.a > 0.0) {\n" +
        "        float lineW = 0.0;\n" +
        "        if (inside < lineReach) {\n" +
        "            lineW = min(1.0 - smoothstep(lineCore, lineCore + lineSoft, inside)\n" +
        "                + kRimHalo * exp(-max(inside - lineCore, 0.0) / lineHalo), 1.0);\n" +
        "        }\n" +
        "        float ca = float(col.a);\n" +
        "        float3 lin = float3(toLinearSrgb(half3(float3(col.rgb) / ca)));\n" +
        "        if (slope > 0.0) {\n" +
        "            float cv = inversesqrt(1.0 + slope * slope);\n" +
        "            float fm = 1.0 - cv;\n" +
        "            float fres = kF0 + (1.0 - kF0) * fm * fm * fm * fm * fm;\n" +
        "            float3 hv = kHalf;\n" +
        "            if (turned) {\n" +
        "                float tm = 1.0 - clamp(dot(float3(n * slope, 1.0) * cv, float3(-uPose.xy, uPose.z)), 0.0, 1.0);\n" +
        "                float tf = kF0 + (1.0 - kF0) * tm * tm * tm * tm * tm + uPoseHalf.w;\n" +
        "                fres = clamp(fres + uPoseLight.z * (tf - fres), 0.0, 1.0);\n" +
        "                hv = uPoseHalf.xyz;\n" +
        "            }\n" +
        "            fres = fres * (1.0 - lineW);\n" +
        "            float nh = max(dot(float3(n * slope, 1.0) * cv, hv), 0.0);\n" +
        "            float spec = max(pow(nh, kSpecPow) - kSpecFloor, 0.0) * kSpecK;\n" +
        "            spec = spec * uShine;\n" +
        "            lin = lin * (1.0 - fres) + float3(fres * kEnv + spec);\n" +
        "        } else if (turned) {\n" +
        "            float ff = uPoseLight.y * (1.0 - lineW);\n" +
        "            lin = lin * (1.0 - ff) + float3(ff * kEnv);\n" +
        "        }\n" +
        "        if (lineW > 0.0 || shelfW > 0.0) {\n" +
        "            float rc = dot(n, kKeyLight);\n" +
        "            float lobe = rimLobe(rc);\n" +
        "            float away = rc < 0.0 ? rc * rc : 0.0;\n" +
        "            lobe = lobe + kTouchRim * touchG;\n" +
        "            float white = (kRimBase + kRimSpec * lobe) * lineW + kRimSpec * shelfW;\n" +
        "            white = white * uShine;\n" +
        "            float keep = 1.0 - kRimDark * (kRimDarkAll + (1.0 - kRimDarkAll) * away) * lineW;\n" +
        "            lin = lin * keep + float3(white);\n" +
        "        }\n" +
        "        if (turned && uGlint.w > 0.0) {\n" +
        "            float2 gd = normalize(uPose.xy);\n" +
        "            float2 gq = p - uGlint.xy;\n" +
        "            float ga = dot(gq, gd) / uGlint.z;\n" +
        "            float gc = dot(gq, float2(-gd.y, gd.x)) / (3.0 * uGlint.z);\n" +
        "            lin = lin + float3(uGlint.w * exp(-ga * ga - gc * gc));\n" +
        "        }\n" +
        "        lin = min(lin, float3(1.0));\n" +
        "        col.rgb = half3(float3(fromLinearSrgb(half3(lin))) * ca);\n" +
        "    }\n" +
        // The pressed key's glow and the faint scatter under a finger stay small additive light.
        // Left unmultiplied on purpose: over a transparent key cell the alpha is near 0, and the
        // glow must still show as added light there.
        "    col.rgb = col.rgb + half3(lensGlow * kKeyGlow + 0.045 * touchG * touchG);\n" +
        // A bead's outline is its own: a pixel wide, anti-aliased, nothing outside it.
        "    if (beads) {\n" +
        "        col = col * half(clamp(inside + 0.5, 0.0, 1.0));\n" +
        "    }\n" +
        "    return col;\n" +
        "}\n";

    /** The program with the shipped rim and slab ({@link GlassRim#DEFAULT}, {@link GlassSlab#DEFAULT}). */
    static final String AGSL = agsl(GlassRim.DEFAULT, GlassSlab.DEFAULT);

    /** The program with the rim {@code rim}'s and the slab {@code slab}'s numbers. */
    @NonNull
    static String agsl(@NonNull GlassRim rim, @NonNull GlassSlab slab) {
        return AGSL_HEAD + rimConstants(rim) + slab.constants() + AGSL_BODY;
    }

    /**
     * The rim's numbers as the program's constants: {@link GlassRim}'s fields, its least widths,
     * its halo's span and the shelf lip's reach under its white.
     */
    @NonNull
    static String rimConstants(@NonNull GlassRim rim) {
        return "const float kRimLineDp = " + agslFloat(rim.lineDp) + ";\n"
            + "const float kRimSoftDp = " + agslFloat(rim.softDp) + ";\n"
            + "const float kRimHalo = " + agslFloat(rim.halo) + ";\n"
            + "const float kRimHaloDp = " + agslFloat(rim.haloDp) + ";\n"
            + "const float kRimHaloSpan = " + agslFloat(rim.haloSpan()) + ";\n"
            + "const float kRimBase = " + agslFloat(rim.base) + ";\n"
            + "const float kRimSpec = " + agslFloat(rim.spec) + ";\n"
            + "const float kRimDark = " + agslFloat(rim.dark) + ";\n"
            + "const float kRimDarkAll = " + agslFloat(rim.darkAll) + ";\n"
            + "const float kRimMinLinePx = " + agslFloat(GlassRim.MIN_LINE_PX) + ";\n"
            + "const float kRimMinSoftPx = " + agslFloat(GlassRim.MIN_SOFT_PX) + ";\n"
            + "const float kShelfLipReachDp = " + agslFloat(shelfLipReachDp(rim)) + ";\n";
    }

    /** A float as an AGSL literal: plain digits, never Java's exponent form. */
    @NonNull
    static String agslFloat(float v) {
        return new java.math.BigDecimal(Float.toString(v)).stripTrailingZeros().toPlainString()
            .replaceFirst("^(-?\\d+)$", "$1.0");
    }

    /** The program, built once per process with the active rim and slab ({@link GlassRim#active}, {@link GlassSlab#active}). */
    @Nullable private static volatile String sSource;

    @NonNull
    private static String source() {
        String source = sSource;
        if (source == null) {
            GlassRim rim = GlassRim.active();
            GlassSlab slab = GlassSlab.active();
            source = rim.equals(GlassRim.DEFAULT) && slab.equals(GlassSlab.DEFAULT) ? AGSL
                : agsl(rim, slab);
            sSource = source;
        }
        return source;
    }

    /** The local matrix's values, row-major as {@link Matrix#getValues} gives them, for none. */
    private static final float[] IDENTITY = {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};

    /**
     * The program's own mapping from a surface pixel {@code (x, y)} to the content's pixel, into
     * {@code out}: {@code local} (a {@link Matrix}'s nine values) with its perspective divide,
     * then the aim undone. The plain draw's shader matrix ({@link GlassAnchor.Aim#shaderMatrix})
     * is its inverse, so the two draws sample the same wallpaper.
     */
    static void framePx(@NonNull float[] local, float scaleX, float scaleY, float offsetX,
                        float offsetY, float x, float y, @NonNull float[] out) {
        float hx = local[0] * x + local[1] * y + local[2];
        float hy = local[3] * x + local[4] * y + local[5];
        float hw = local[6] * x + local[7] * y + local[8];
        out[0] = (hx / hw - offsetX) / Math.max(1e-4f, scaleX);
        out[1] = (hy / hw - offsetY) / Math.max(1e-4f, scaleY);
    }

    /**
     * The rim's key light, {@code kKeyLight}: up and to the left, a unit vector in the
     * slab's own px axes (y down). Fixed in the room: a turned slab's lens zone takes it through
     * the turned half vector ({@link GlassPoseOptics#half}).
     */
    static final float KEY_LIGHT_X = -0.552f;
    static final float KEY_LIGHT_Y = -0.834f;

    /**
     * How high the key light stands over the glass, as the tangent of its elevation: 1, 45
     * degrees. The rim's light is a direction round the outline; the slab's specular
     * ({@link GlassSlab#half}) and a turned slab ({@link GlassPoseOptics#half}) need it in three
     * dimensions, and this is the one number that lifts it there.
     */
    static final float KEY_LIGHT_RISE = 1f;
    /** Rec. 709's luma weights, {@code kLumaW}: how pale a sample is. */
    static final float LUMA_R = 0.2126f;
    static final float LUMA_G = 0.7152f;
    static final float LUMA_B = 0.0722f;

    /** The program's {@code kKeyGlow}: the white a pressed key adds at its centre. */
    static final float KEY_GLOW = 0.15f;

    /** AGSL's {@code smoothstep}. */
    static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    // ------------------------------------------------------------------------ the concave lens

    /**
     * Which way the picture is pulled along the rim's outward normal: a convex rim samples from
     * further in ({@code -n * pull}, the picture swells out to the rim), the concave lens from
     * further out ({@code +n * pull}, the picture is drawn in toward the centre line).
     */
    public static final int CONVEX_SIGN = -1;
    public static final int CONCAVE_SIGN = 1;
    /** How deep the concave band reaches at most, dp: the program's {@code uConcave.y}. */
    public static final float CONCAVE_BAND_DP = 12f;
    /**
     * The most of the surface's short side the concave band takes ({@code kConcaveBandShare}).
     * Under a half, so the top and bottom bands never meet: on a 30 dp slab they leave the middle
     * fifth untouched.
     */
    public static final float CONCAVE_BAND_MAX_SHARE = 0.4f;
    /** The deepest pull, as a share of the band ({@code kConcavePullShare}). */
    public static final float CONCAVE_PULL_SHARE = 0.2f;
    /**
     * The steepest the pull rises across the lip, px per px ({@code kConcaveLipSlope}). Under 1,
     * so a pixel's sample never passes its outer neighbour's: the picture is never folded back,
     * and never stretched by more than {@code 1 / (1 - slope)}, where a steeper bend smears one
     * row of the backdrop down a column (the old convex bend on this slab, whose pull fell
     * 2.7 px per px). At a half the outermost px are stretched at most twice and every sample
     * stays inside the outline ({@link #concavePullPx}).
     */
    public static final float CONCAVE_LIP_MAX_SLOPE = 0.5f;

    /** The concave band on a surface whose short side is {@code shortSidePx}: {@link #CONCAVE_BAND_DP}, held at {@link #CONCAVE_BAND_MAX_SHARE} of it. */
    public static float concaveBandPx(float density, float shortSidePx) {
        return Math.max(0f, Math.min(CONCAVE_BAND_DP * density,
            CONCAVE_BAND_MAX_SHARE * Math.max(0f, shortSidePx)));
    }

    /** The deepest pull in a concave band {@code bandPx} deep. */
    public static float concavePeakPx(float bandPx) {
        return CONCAVE_PULL_SHARE * bandPx;
    }

    /**
     * How far in the pull rises to its peak: just wide enough that a smoothstep's steepest slope
     * (1.5 times its mean) stays at {@link #CONCAVE_LIP_MAX_SLOPE}. Three fifths of the band.
     */
    public static float concaveLipPx(float bandPx) {
        return 1.5f * concavePeakPx(bandPx) / CONCAVE_LIP_MAX_SLOPE;
    }

    /**
     * The program's {@code concavePull}: how far out, px, a pixel {@code insidePx} in from the
     * rim of a concave band {@code bandPx} deep takes its picture from. Nothing at the outline,
     * rising across the lip ({@link #concaveLipPx}) to the peak, then falling to nothing at the
     * band's inner edge, both on a smoothstep, so the pull and its slope are continuous
     * everywhere. Its rise is at most {@link #CONCAVE_LIP_MAX_SLOPE} px per px, so the sample
     * {@code insidePx - pull} only ever moves inward with the pixel (no fold), and since the
     * pull is at most 0.375 of {@code insidePx} the sample stays inside the outline.
     */
    public static float concavePullPx(float insidePx, float bandPx) {
        if (!(insidePx > 0f) || insidePx >= bandPx) return 0f;
        float peak = concavePeakPx(bandPx);
        float lip = concaveLipPx(bandPx);
        if (insidePx < lip) return peak * smoothstep(0f, lip, insidePx);
        return peak * (1f - smoothstep(lip, bandPx, insidePx));
    }

    /**
     * Where a pixel at {@code (px, py)} (from the centre) of a surface of half size
     * {@code (bx, by)} and corner {@code cr} samples from under the concave lens, as an offset
     * into {@code out}: {@code CONCAVE_SIGN * n * pull} along the same eased normal the convex
     * bend uses. The program's concave branch in Java; allocates nothing.
     */
    public static void concaveOffset(@NonNull float[] out, float px, float py, float bx, float by,
                                     float cr, float density) {
        float hs = Math.min(bx, by);
        float band = concaveBandPx(density, 2f * hs);
        float inside = insidePx(px, py, bx, by, cr);
        rectNormal(out, px, py, bx, by, Math.min(Math.max(1.5f * cr, band), hs));
        float pull = CONCAVE_SIGN * concavePullPx(inside, band);
        out[0] *= pull;
        out[1] *= pull;
    }

    /**
     * The program's {@code w}: the lens zone's width, px, at rest, on a surface whose half short
     * side is {@code halfShortPx}: the slab's ({@link GlassSlab#lensWidthPx}) scaled by Clear's
     * size lens {@code sizeK}, widened to the dock's soft band {@code softRimPx} where there is
     * one (a bead's, {@code beadRadiusPx} above 0, held under {@link #BEAD_BAND_SHARE} of its
     * radius), grown by {@code lower} of the shelf and held at the half short side.
     */
    static float lensZonePx(@NonNull GlassSlab slab, float halfShortPx, float density, float sizeK,
                            float softRimPx, float beadRadiusPx, float lower) {
        float hs = Math.max(0f, halfShortPx);
        float lensMax = Math.max(slab.lensPx(density), 0.001f);
        float small = Math.min(slab.share * 2f * hs / lensMax, 1f);
        float w = lensMax * small * sizeK;
        float soft = softRimPx * sizeK;
        if (softRimPx > 0f) {
            w = beadRadiusPx > 0f ? Math.min(Math.max(soft, w), BEAD_BAND_SHARE * beadRadiusPx)
                : Math.max(w, soft);
        }
        return Math.min(w * (1f + SHELF_THICKEN * Math.max(0f, lower)), hs);
    }

    /**
     * The program's {@code slabT}: the slab's thickness, px, for the look's Depth
     * {@code depthPx}: scaled with the lens on a small piece ({@link GlassSlab#thicknessPx}), by
     * Clear's size lens and by {@code lower} of the shelf, held so the deepest bend reaches the
     * centre line at most.
     */
    static float lensThicknessPx(@NonNull GlassSlab slab, float depthPx, float halfShortPx,
                                 float density, float sizeK, float lower) {
        float hs = Math.max(0f, halfShortPx);
        float lensMax = Math.max(slab.lensPx(density), 0.001f);
        float small = Math.min(slab.share * 2f * hs / lensMax, 1f);
        return Math.min(Math.max(0f, depthPx) * sizeK * small * (1f + SHELF_THICKEN * Math.max(0f, lower)),
            hs * slab.holdShare());
    }

    /**
     * The deepest the lens zone {@code zonePx} (before the shelf) reaches: grown by a whole
     * {@code shelf} ({@link #SHELF_THICKEN}). Pixels further in are the flat face and skip every
     * lens term. The program's {@code reach}.
     */
    static float bendReachPx(float zonePx, float shelf) {
        return Math.max(0f, zonePx) * (1f + SHELF_THICKEN * Math.max(0f, shelf));
    }

    /**
     * The radius the bend's direction turns round a corner on: the drawn corner, or the deepest
     * zone ({@code reachPx}) where that is more, held at the half short side. The drawn field's
     * gradient mitres along each corner's diagonal inside the drawn arc; with a zone deeper than
     * the corner (a 3 dp Corners, Docked's square dock, under a 16 dp zone) a bend that snapped
     * there would crease the picture from each corner in, and on a one-row band meet its twin at
     * the centre line as a chevron. On this arc the direction only snaps on the rect inside it,
     * where every pixel is past every zone and is not bent. Where the drawn corner is the deeper
     * it is the drawn arc exactly. The light keeps the drawn normal. The program's
     * {@code min(max(cr, reach), hs)}.
     */
    static float bendNormalRadiusPx(float cornerRadiusPx, float reachPx, float halfShortPx) {
        return Math.min(Math.max(Math.max(0f, cornerRadiusPx), reachPx), Math.max(0f, halfShortPx));
    }

    /**
     * The program's {@code sdRoundRect}, negated: how far {@code (px, py)} (from the centre) is
     * inside a rounded rect of half size {@code (bx, by)} and corner {@code r}; negative outside.
     */
    static float insidePx(float px, float py, float bx, float by, float r) {
        float qx = Math.abs(px) - bx + r;
        float qy = Math.abs(py) - by + r;
        float outside = (float) Math.hypot(Math.max(qx, 0f), Math.max(qy, 0f));
        return -(Math.min(Math.max(qx, qy), 0f) + outside - r);
    }

    /**
     * The program's {@code rectNormal} into {@code out}: round the corner arc of radius {@code r},
     * straight out along an edge, and on the inner rect, where no arc reaches, the axis of the
     * nearer edge. On the drawn corner it is the distance field's gradient.
     */
    static void rectNormal(@NonNull float[] out, float px, float py, float bx, float by, float r) {
        float qx = Math.abs(px) - bx + r;
        float qy = Math.abs(py) - by + r;
        float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
        float len = (float) Math.hypot(ox, oy);
        float sx = px < 0f ? -1f : 1f, sy = py < 0f ? -1f : 1f;
        if (len > 0.001f) {
            out[0] = sx * ox / len;
            out[1] = sy * oy / len;
            return;
        }
        float useX = qx >= qy ? 1f : 0f;
        out[0] = sx * useX;
        out[1] = sy * (1f - useX);
    }

    /**
     * Where a resting pixel at {@code (px, py)} (from the centre) of a surface of half size
     * {@code (bx, by)} and corner {@code cr} samples from, as an offset into {@code out}: the
     * slab's refraction ({@link GlassSlab#shiftPx}) along the bend's direction, the program's
     * {@code -nb * shiftG}, for the look's Depth {@code depthPx}. {@code softRimPx} and
     * {@code shelf} are the dock surface's ({@link Program#setDockSurface}); 0 and 0 is every
     * other surface. No size lens.
     */
    static void restBendOffset(@NonNull float[] out, float px, float py, float bx, float by,
                               float cr, float depthPx, float softRimPx, float shelf,
                               float density) {
        GlassSlab slab = GlassSlab.active();
        float hs = Math.min(bx, by);
        float inside = insidePx(px, py, bx, by, cr);
        out[0] = 0f;
        out[1] = 0f;
        float lensMax = Math.max(slab.lensPx(density), 0.001f);
        float w0 = lensMax * Math.min(slab.share * 2f * hs / lensMax, 1f);
        if (softRimPx > 0f) w0 = Math.max(w0, softRimPx);
        float reach = bendReachPx(w0, shelf);
        if (!(inside < reach) || !(depthPx > 0f)) return;
        float[] n = new float[2];
        rectNormal(n, px, py, bx, by, bendNormalRadiusPx(cr, reach, hs));
        float lower = shelf > 0f ? Math.max(n[1], 0f) * shelf : 0f;
        float w = lensZonePx(slab, hs, density, 1f, softRimPx, 0f, lower);
        float t = lensThicknessPx(slab, depthPx, hs, density, 1f, lower);
        float shift = slab.shiftPx(Math.max(0f, inside), w, t);
        out[0] = -n[0] * shift;
        out[1] = -n[1] * shift;
    }

    /**
     * The program's {@code rimLobe}: the key light's wide lobe on an outline facing the fixed key
     * light by the cosine {@code c}, 0..1, nothing facing away. The rim's white above its baseline
     * at rest ({@link GlassRim#white}).
     */
    static float rimLobe(float c) {
        return c > 0f ? (float) Math.pow(c, Look.KEY_EXPONENT) : 0f;
    }

    /** {@link #rimLobe(float)} for an outline with outward normal {@code (nx, ny)}, at rest. */
    static float rimLobe(float nx, float ny) {
        return rimLobe(nx * KEY_LIGHT_X + ny * KEY_LIGHT_Y);
    }

    /**
     * How far an outline with outward normal {@code (nx, ny)} faces away from the fixed key light,
     * the squared cosine facing away, 0..1: the rim's dark line's share above its baseline
     * ({@link GlassRim#darkening}).
     */
    static float rimAway(float nx, float ny) {
        float c = nx * KEY_LIGHT_X + ny * KEY_LIGHT_Y;
        return c < 0f ? c * c : 0f;
    }

    /**
     * The program's {@code faceFade}: how much of Clear's sharp face and tone a surface whose short
     * side is {@code shortSidePx} keeps: 1 at {@link Look#FACE_FADE_FROM_DP} or under, 0 at
     * {@link Look#FACE_FADE_TO_DP} or over, smoothstep between.
     */
    static float faceFade(float shortSidePx, float density) {
        float d = Math.max(density, 1e-4f);
        float u = Math.max(0f, Math.min(1f, (shortSidePx / d - Look.FACE_FADE_FROM_DP)
            / (Look.FACE_FADE_TO_DP - Look.FACE_FADE_FROM_DP)));
        return 1f - u * u * (3f - 2f * u);
    }

    /**
     * The program's {@code sizeK}: what the lens zone and the thickness are scaled by for a surface whose
     * short side is {@code shortSidePx}, at {@code sizeLens} (0..1): 1 at 0, otherwise the short
     * side over {@link Look#SIZE_REF_DP} held to {@link Look#SIZE_MIN}..{@link Look#SIZE_MAX},
     * mixed in by {@code sizeLens}.
     */
    static float sizeLensK(float shortSidePx, float density, float sizeLens) {
        if (!(sizeLens > 0f)) return 1f;
        float k = Math.max(Look.SIZE_MIN, Math.min(Look.SIZE_MAX,
            shortSidePx / (Look.SIZE_REF_DP * Math.max(density, 1e-4f))));
        return 1f + (k - 1f) * sizeLens;
    }

    /**
     * The program's face tone on an opaque sample of luminance {@code luma} (0..1) at {@code dim}
     * (0..1): the share the sample is darkened by toward black, {@code out[0]}, and the share of
     * its distance to white it is lifted by after, {@code out[1]}. Both 0 at dim 0.
     */
    static void faceTone(float luma, float dim, @NonNull float[] out) {
        float u = Math.max(0f, Math.min(1f, (luma - Look.DIM_FROM) / (Look.DIM_TO - Look.DIM_FROM)));
        out[0] = dim * Look.DIM_MAX * (u * u * (3f - 2f * u));
        out[1] = dim * Look.FACE_LIFT * (1f - luma);
    }

    /** The key lens's own strength and feather, which no knob reaches: the press reads the same in both modes. */
    private static final float LENS_STRENGTH = 0.20f;
    private static final float LENS_FEATHER_DP = 10f;

    /**
     * How far the picture is drawn toward a resting finger, as a share of the distance to it at
     * the touch's centre: a shallow lens, a few px at most on a phone, so the glass answers the
     * finger without the picture swimming.
     */
    static final float TOUCH_LENS = 0.10f;

    /**
     * The program's {@code edgeK}: how much of the sharp picture a pixel at {@code t} across the
     * band (0 at the rim, 1 at its inner edge and on the face) shows where one is bound, on the
     * band's smoothstep, or the soft band's smootherstep when {@code soft}. Whole at the rim, 0
     * on the face, at rest and in motion alike: the face's own share is Clear's face clarity,
     * which this does not touch. Pure.
     */
    static float sharpShare(float t, boolean soft) {
        float x = 1f - Math.max(0f, Math.min(1f, t));
        if (x <= 0f) return 0f;
        return soft ? x * x * x * (x * (x * 6f - 15f) + 10f) : x * x * (3f - 2f * x);
    }

    /** {@code uSharpEdge} for a program whose sharp input is bound and a picture of its own. Pure. */
    static float sharpEdge(boolean sharpBound, boolean sharpDistinct) {
        return sharpBound && sharpDistinct ? 1f : 0f;
    }

    /** {@code uFaceClarity}: Clear's face share, only where a clearer picture of its own is bound. Pure. */
    static float faceClarity(@Nullable Look look, boolean sharpBound, boolean sharpDistinct) {
        return look != null && sharpBound && sharpDistinct ? look.faceClarity() : 0f;
    }

    /**
     * The program's {@code clarityK}: how much of the sharp picture a pixel at {@code t} shows,
     * for {@code uSharpEdge} and the face's share {@code faceMix} ({@code uFaceClarity} after the
     * size fade). The edge's share is {@link #sharpShare} whole; the face mixes toward the sharp
     * picture by its own share and nothing else. Pure.
     */
    static float sharpMix(float t, boolean soft, float sharpEdge, float faceMix) {
        float k = sharpShare(t, soft) * sharpEdge;
        return faceMix > 0f ? k + (1f - k) * faceMix : k;
    }

    // The dock surface's glass (com.termux.app.dock.DockSurfaceSpec): what a Home dock style turns
    // on through {@link Program#setDockSurface}. Every other
    // surface leaves them off, and the program then draws exactly what it drew before. Each is
    // the program's constant of the matching name. The numbers are the Home dock styles spec's
    // shared rules and the research's R10 and R11 (home-dock-ux-research-2026-10-20).

    /**
     * The soft rim band's width, dp ({@code uSoftRim} once converted): "about 24 dp", the low end
     * of the research's 24 to 28 dp budget for a gentle bend (R11). The slab's lens zone widens
     * to it on the dock surface, so the same thickness bends over a gentler slope.
     */
    public static final float SOFT_RIM_DP = 24f;
    /**
     * How much thicker the slab's lower edge is at a whole shelf ({@code kShelfThick}): its lens
     * zone and its thickness grow by this share on the edge facing straight down and by less
     * round the lower corners, so the slab reads as thick glass seen a little from above.
     */
    static final float SHELF_THICKEN = 0.35f;
    /**
     * The lower lip's extra light at a whole shelf ({@code kShelfLip}), as a share of the rim's
     * key-light white ({@link GlassRim#spec}), on an edge facing straight down: the light that
     * came through the slab leaving it at the lower edge.
     */
    static final float SHELF_LIP = 0.55f;
    /** How fast the lip fades inward, dp ({@code kShelfLipDp}): wider than the rim's line. */
    static final float SHELF_LIP_DP = 2.4f;

    /**
     * How far in the lip's white is still worth an 8-bit step under the rim {@code rim}, dp
     * ({@code kShelfLipReachDp}); pixels further in skip it.
     */
    static float shelfLipReachDp(@NonNull GlassRim rim) {
        return SHELF_LIP_DP * (float) Math.max(0.0, Math.log(255.0 * SHELF_LIP * rim.spec));
    }

    /** How much of the key light's lobe a finger's full reach adds to the rim near it ({@code kTouchRim}). */
    static final float TOUCH_RIM = 1.5f;

    /**
     * A Lens bead's own soft rim band, dp ({@code uSoftRim} for a bead): the least its lens zone
     * takes, the slab's own zone where that is wider, held under {@link #BEAD_BAND_SHARE} of its
     * radius, so the bead reads as a small lens with a steeper slope than the tray's 24 dp band.
     */
    public static final float BEAD_SOFT_RIM_DP = 10f;
    /** The most of a bead's radius its lens zone reaches ({@code kBeadBandShare}). */
    static final float BEAD_BAND_SHARE = 0.4f;
    /** Where {@link Program#setBeads} parks a bead slot it does not use: never the nearest. */
    static final float BEAD_UNUSED_X = -1.0e6f;

    /** The program's {@code nearestBead}: the centre in {@code centers} nearest {@code x}, the first on a tie. */
    static float nearestBeadX(float x, @NonNull float[] centers) {
        float bx = centers[0];
        float bd = Math.abs(x - centers[0]);
        for (int i = 1; i < centers.length; i++) {
            float d = Math.abs(x - centers[i]);
            if (d < bd) {
                bd = d;
                bx = centers[i];
            }
        }
        return bx;
    }

    /** How many bead centres the program reads ({@code uBeadXA}, {@code uBeadXB}, {@code uBeadXC}). */
    public static final int BEAD_SLOTS = 12;

    /**
     * The twelve bead slots the program reads ({@code uBeadXA}, {@code uBeadXB}, then
     * {@code uBeadXC}) for {@code count} centres: each centre in order, the rest parked at
     * {@link #BEAD_UNUSED_X}.
     */
    @NonNull
    static float[] beadSlots(@NonNull float[] centers, int count) {
        float[] slots = new float[BEAD_SLOTS];
        Arrays.fill(slots, BEAD_UNUSED_X);
        for (int i = 0; i < Math.min(BEAD_SLOTS, Math.min(count, centers.length)); i++)
            slots[i] = centers[i];
        return slots;
    }

    /**
     * The program's lip ({@code shelfW}), the share of the rim's key-light white the lower edge
     * adds, for an outline whose normal faces down by {@code ny}, {@code insidePx} in, at
     * {@code shelf} (0..1).
     */
    static float shelfLipK(float ny, float insidePx, float density, float shelf) {
        if (!(shelf > 0f) || !(ny > 0f)) return 0f;
        return (float) Math.exp(-Math.max(insidePx, 0f) / (SHELF_LIP_DP * density)) * SHELF_LIP
            * shelf * smoothstep(0.2f, 1f, ny);
    }


    /**
     * One compiled program and the uniforms it was last given. Every setter compares before it
     * writes, so a draw that re-states an unmoved surface pushes nothing to the GPU. Held only on
     * API 33+; the outer class keeps no field of this type, so a phone below that never loads it.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final class Program {

        @NonNull private final RuntimeShader mShader;
        private final float mDensity;
        private float mScaleX = Float.NaN, mScaleY, mOffsetX, mOffsetY;
        /** The local matrix last written, and scratch to read the next one into. */
        private final float[] mLocal = new float[9];
        private final float[] mLocalNext = new float[9];
        private boolean mLocalWritten;
        private float mLeft = Float.NaN, mTop, mRight, mBottom, mRadius;
        @Nullable private Look mLook;
        private boolean mLensOn;
        private float mLensCx, mLensCy, mLensHx, mLensHy, mLensRadius, mLensIntensity;
        /** Whether {@code sharp} has a shader bound: only then may the program read it. */
        private boolean mSharpBound;
        private float mSharpEdgeWritten = Float.NaN;
        private float mFaceClarityWritten = Float.NaN;
        private float mDimWritten = Float.NaN;
        /**
         * Whether {@link #setInput} bound a wallpaper frame as the content: false under a
         * {@code RenderEffect}, where the content is the view's own pixels (icons, text) and the
         * face tone must not touch them.
         */
        private boolean mInputBound;
        private boolean mTouchOn;
        private float mTouchX, mTouchY, mTouchRadius, mTouchIntensity;
        /** The pose as last written, and the pose uniforms as written and next, with scratch. */
        private final GlassPose mPose = new GlassPose();
        private final float[] mPoseWritten = new float[GlassPoseOptics.UNIFORMS];
        private final float[] mPoseNext = new float[GlassPoseOptics.UNIFORMS];
        private final float[] mPoseScratch = new float[4];
        private boolean mPoseWrittenValid;
        /** Whether {@code sharp} is a clearer frame of its own, not the frost bound in its place. */
        private boolean mSharpDistinct;
        /** The dock surface's glass as last written: {@code uSoftRim} in px and {@code uShelf}. */
        private float mSoftRimWritten = Float.NaN, mShelfWritten = Float.NaN;
        /**
         * The beads as last written: {@code uBead}, the twelve slots and {@code uBeadCap};
         * radius 0 is none.
         */
        private final float[] mBeadWritten = new float[3 + BEAD_SLOTS + 4];
        private boolean mBeadWrittenValid;
        private final float[] mBeadNext = new float[3 + BEAD_SLOTS + 4];
        /** The letter beads as last written ({@code uAzBead}, then {@code uAzLift}), and next. */
        private final float[] mAzWritten = new float[7];
        private final float[] mAzNext = new float[7];
        private boolean mAzWrittenValid;

        private Program(float density) {
            mShader = new RuntimeShader(source());
            mDensity = density;
            mShader.setFloatUniform("uDensity", density);
            mShader.setFloatUniform("uShine", 1f);
            mShader.setFloatUniform("uLensStrength", LENS_STRENGTH);
            mShader.setFloatUniform("uLensFeather", density * LENS_FEATHER_DP);
            mShader.setFloatUniform("uTouchLens", TOUCH_LENS);
            setAim(1f, 1f, 0f, 0f);
            setLocal(null);
            setLook(Look.DEFAULT);
            mLensOn = true; // so the clear below is a write, not a skip
            clearLens();
            mTouchOn = true;
            clearTouch();
            clearPose();
            setDockSurface(0f, 0f);
            clearBeads();
        }

        /**
         * Lens beads: no slab, one round bead under each icon. {@code centersX} holds
         * {@code count} bead centres ({@link #BEAD_SLOTS} at most; more is not drawn as beads by
         * the caller),
         * all on the line {@code centerY}, each of {@code radiusPx}, around icons
         * {@code 2 * iconHalfPx} tall, in the surface's own coordinates. The rect set by
         * {@link #setRect} is then not read. Written only on a change.
         */
        public void setBeads(@NonNull float[] centersX, int count, float centerY, float radiusPx,
                             float iconHalfPx) {
            if (count <= 0 || !(radiusPx > 0f)) {
                clearBeads();
                return;
            }
            float[] slots = beadSlots(centersX, count);
            mBeadNext[0] = centerY;
            mBeadNext[1] = radiusPx;
            mBeadNext[2] = Math.max(0f, iconHalfPx);
            System.arraycopy(slots, 0, mBeadNext, 3, BEAD_SLOTS);
            writeBeads();
        }

        /**
         * The Search field's glass capsule under the beads (#84): a stadium at {@code left..right}
         * by {@code top..bottom} in the surface's own coordinates, drawn as one more of the
         * beads' shapes, with their rim, shelf and light.
         * Read only while beads are on; {@link #clearBeads} and {@link #clearBeadCapsule} take it
         * off. Written only on a change.
         */
        public void setBeadCapsule(float left, float top, float right, float bottom) {
            if (!(right > left) || !(bottom > top)) {
                clearBeadCapsule();
                return;
            }
            int at = 3 + BEAD_SLOTS;
            mBeadNext[at] = left;
            mBeadNext[at + 1] = top;
            mBeadNext[at + 2] = right;
            mBeadNext[at + 3] = bottom;
            writeBeads();
        }

        /** No capsule under the beads. */
        public void clearBeadCapsule() {
            Arrays.fill(mBeadNext, 3 + BEAD_SLOTS, mBeadNext.length, 0f);
            writeBeads();
        }

        /** No beads: the slab, as every surface always drew. */
        public void clearBeads() {
            Arrays.fill(mBeadNext, 0f);
            System.arraycopy(beadSlots(new float[0], 0), 0, mBeadNext, 3, BEAD_SLOTS);
            writeBeads();
            clearAzBeads();
        }

        /**
         * The A–Z row's letter beads under the icons' ({@code com.termux.app.dock.AzBeadRow}):
         * {@code count} beads of {@code radiusPx}, the first centred at {@code firstCenterX},
         * {@code pitchPx} apart, on the line {@code centerY}, each risen by the lift about
         * {@code fingerX} (far off every bead for none) at {@code peakPx}. Read only while beads
         * are on. Written only on a change; a lift's frame writes one uniform.
         */
        public void setAzBeads(float firstCenterX, float pitchPx, int count, float centerY,
                               float radiusPx, float fingerX, float peakPx) {
            if (count <= 0 || !(radiusPx > 0f) || !(pitchPx > 0f)) {
                clearAzBeads();
                return;
            }
            float finger = Float.isNaN(fingerX) ? BEAD_UNUSED_X : fingerX;
            float peak = Float.isNaN(peakPx) ? 0f : peakPx;
            mAzNext[0] = firstCenterX;
            mAzNext[1] = pitchPx;
            mAzNext[2] = count;
            mAzNext[3] = centerY;
            mAzNext[4] = radiusPx;
            mAzNext[5] = finger;
            mAzNext[6] = peak;
            writeAzBeads();
        }

        /** No letter beads. */
        public void clearAzBeads() {
            Arrays.fill(mAzNext, 0f);
            writeAzBeads();
        }

        private void writeAzBeads() {
            float[] n = mAzNext;
            float[] w = mAzWritten;
            boolean row = !mAzWrittenValid || n[0] != w[0] || n[1] != w[1] || n[2] != w[2]
                || n[3] != w[3];
            boolean lift = !mAzWrittenValid || n[4] != w[4] || n[5] != w[5] || n[6] != w[6];
            mAzWrittenValid = true;
            if (row) mShader.setFloatUniform("uAzBead", n[0], n[1], n[2], n[3]);
            if (lift) mShader.setFloatUniform("uAzLift", n[4], n[5], n[6], 0f);
            System.arraycopy(n, 0, w, 0, n.length);
        }

        /** Whether beads are on: the dock draws one lens per icon rather than its slab. */
        public boolean hasBeads() {
            return mBeadWrittenValid && mBeadWritten[1] > 0f;
        }

        private void writeBeads() {
            if (mBeadWrittenValid && Arrays.equals(mBeadNext, mBeadWritten)) return;
            mBeadWrittenValid = true;
            System.arraycopy(mBeadNext, 0, mBeadWritten, 0, mBeadNext.length);
            float[] n = mBeadNext;
            mShader.setFloatUniform("uBead", n[0], n[1], n[2], 0f);
            mShader.setFloatUniform("uBeadXA", n[3], n[4], n[5], n[6]);
            mShader.setFloatUniform("uBeadXB", n[7], n[8], n[9], n[10]);
            mShader.setFloatUniform("uBeadXC", n[11], n[12], n[13], n[14]);
            mShader.setFloatUniform("uBeadCap", n[15], n[16], n[17], n[18]);
        }

        /**
         * The dock surface's own glass ({@code com.termux.app.dock.DockSurfaceSpec}): a soft rim
         * band at least {@code softRimDp} wide (0 keeps the narrow ring every other surface
         * wears) and {@code shelf} of the lower shelf, 0..1: the thicker lower edge and the
         * brighter lip along it. 0, 0 is the program as it always was. Written only on a change.
         */
        public void setDockSurface(float softRimDp, float shelf) {
            float soft = softRimDp > 0f ? softRimDp * mDensity : 0f;
            float lower = Math.max(0f, Math.min(1f, Float.isNaN(shelf) ? 0f : shelf));
            if (soft != mSoftRimWritten) {
                mSoftRimWritten = soft;
                mShader.setFloatUniform("uSoftRim", soft);
            }
            if (lower != mShelfWritten) {
                mShelfWritten = lower;
                mShader.setFloatUniform("uShelf", lower);
            }
        }

        /**
         * Compiles the program, or answers null when this phone's driver refuses it — every
         * caller then draws the frame plain, which is the default look.
         */
        @Nullable
        public static Program create(float density) {
            try {
                return new Program(density);
            } catch (Throwable t) {
                Logger.logStackTraceWithMessage("GlassRefraction", "Glass refraction shader failed; drawing plain", t);
                return null;
            }
        }

        /** The shader, for {@code RenderEffect.createRuntimeShaderEffect(shader, "content")}. */
        @NonNull
        public RuntimeShader shader() {
            return mShader;
        }

        /**
         * Makes {@code paint} draw through this program. Here rather than at the caller so that a
         * drawable that runs from API 26 never names {@code RuntimeShader} in its own code.
         */
        public void applyTo(@NonNull Paint paint) {
            paint.setShader(mShader);
        }

        public float density() {
            return mDensity;
        }

        /**
         * The frame this program samples, drawn as a paint shader. The child is sampled by its own
         * pixels — the aim is a uniform, see {@link #setAim} — so its local matrix is cleared, and
         * it is filtered, since the frame is held at blur resolution and scaled up here. Always
         * written: the program captures the child as it is now, and a matrix the plain path set
         * since would otherwise be baked in.
         */
        public void setInput(@NonNull Shader content) {
            content.setLocalMatrix(null);
            if (content instanceof BitmapShader) {
                ((BitmapShader) content).setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            }
            mShader.setInputShader("content", content);
            mInputBound = true;
            // The clearer picture is the frost itself until a caller hands one in, so the mix
            // reads back what it was given; the clarity term is then free to be written.
            mShader.setInputShader("sharp", content);
            mSharpBound = true;
            mSharpDistinct = false;
            syncClarity();
        }

        /**
         * A clearer picture of the same wallpaper, captured for the same frame rect as the
         * content but held at its own resolution: {@code pxScaleX}/{@code pxScaleY} are its
         * pixels per content pixel (its width over the content's), so the program, which reads
         * everything in content pixels, lands on the same spot in it. Null hands the content back
         * as its own clear picture, which is no clarity at all. Under a {@code RenderEffect},
         * where the content is the view and nothing can be bound here, the clarity term stays at
         * zero, so the program never reads an unbound shader.
         */
        public void setClarityInput(@Nullable Shader sharp, float pxScaleX, float pxScaleY) {
            if (sharp == null) {
                mSharpBound = false;
                mSharpDistinct = false;
                syncClarity();
                return;
            }
            Matrix toSharp = new Matrix();
            toSharp.setScale(1f / Math.max(1e-4f, pxScaleX), 1f / Math.max(1e-4f, pxScaleY));
            sharp.setLocalMatrix(toSharp);
            if (sharp instanceof BitmapShader) {
                ((BitmapShader) sharp).setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            }
            mShader.setInputShader("sharp", sharp);
            mSharpBound = true;
            mSharpDistinct = true;
            syncClarity();
        }

        /** True once a shader is bound as the clearer picture, by {@link #setInput} or {@link #setClarityInput}. */
        public boolean hasClarityInput() {
            return mSharpBound;
        }

        /**
         * {@code uSharpEdge}: 1 while the sharp shader is a clearer picture of its own, so the
         * band shows it wholly toward the rim ({@link #sharpShare}); 0 while it is the frost bound
         * in its place, where mixing toward it would read a second sample of the same picture.
         * {@code uFaceClarity}: Clear's face share, where such a picture is bound.
         */
        private void syncClarity() {
            Look look = mLook;
            float sharpEdge = sharpEdge(mSharpBound, mSharpDistinct);
            if (sharpEdge != mSharpEdgeWritten) {
                mSharpEdgeWritten = sharpEdge;
                mShader.setFloatUniform("uSharpEdge", sharpEdge);
            }
            // The face shows the clearer picture only where one is handed in, a distinct frame:
            // under a RenderEffect, or with the frost bound in its place, it stays 0.
            float face = faceClarity(look, mSharpBound, mSharpDistinct);
            if (face != mFaceClarityWritten) {
                mFaceClarityWritten = face;
                mShader.setFloatUniform("uFaceClarity", face);
            }
            // The face tone is a wallpaper's: only where a frame is the content, never under a
            // RenderEffect, whose content is the view's own icons and text.
            float dim = look != null && mInputBound ? look.dim() : 0f;
            if (dim != mDimWritten) {
                mDimWritten = dim;
                mShader.setFloatUniform("uDim", dim);
            }
        }

        /**
         * Where the content's pixel {@code (0, 0)} lands and how big its pixels are, in the
         * coordinates the surface draws in: the {@link SharedFrameDrawable#aim} matrix as four
         * numbers. Identity under a {@code RenderEffect}, where the content is the view itself.
         */
        public void setAim(float scaleX, float scaleY, float offsetX, float offsetY) {
            if (mScaleX == scaleX && mScaleY == scaleY && mOffsetX == offsetX && mOffsetY == offsetY) return;
            mScaleX = scaleX;
            mScaleY = scaleY;
            mOffsetX = offsetX;
            mOffsetY = offsetY;
            mShader.setFloatUniform("uFrameScale", Math.max(1e-4f, scaleX), Math.max(1e-4f, scaleY));
            mShader.setFloatUniform("uFrameOffset", offsetX, offsetY);
        }

        /**
         * What the surface's transform does past {@link #setAim}, from its own px to px from its
         * origin on screen: {@link GlassAnchor.Aim#local}, a scale or a perspective on a wall page
         * that is sunk or tipped. Null, the default, is identity — every surface but the wall's,
         * and the wall's at rest, which then samples exactly as it did before there was one.
         */
        public void setLocal(@Nullable Matrix local) {
            float[] next = mLocalNext;
            if (local == null) System.arraycopy(IDENTITY, 0, next, 0, IDENTITY.length);
            else local.getValues(next);
            if (mLocalWritten && Arrays.equals(next, mLocal)) return;
            mLocalWritten = true;
            System.arraycopy(next, 0, mLocal, 0, next.length);
            mShader.setFloatUniform("uLocalX", next[0], next[1], next[2]);
            mShader.setFloatUniform("uLocalY", next[3], next[4], next[5]);
            mShader.setFloatUniform("uLocalW", next[6], next[7], next[8]);
        }

        /** The rounded rect the rim runs along, in the surface's own coordinates. */
        public void setRect(float left, float top, float right, float bottom, float cornerRadiusPx) {
            if (mLeft == left && mTop == top && mRight == right && mBottom == bottom && mRadius == cornerRadiusPx) return;
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            mRadius = cornerRadiusPx;
            float drawnRight = Math.max(left + 1f, right);
            float drawnBottom = Math.max(top + 1f, bottom);
            mShader.setFloatUniform("uRectMin", left, top);
            mShader.setFloatUniform("uRectMax", drawnRight, drawnBottom);
            mShader.setFloatUniform("uRadius",
                rectRadiusPx(left, top, drawnRight, drawnBottom, cornerRadiusPx));
        }

        /** The knobs, converted at this program's density. The rim is fixed ({@link GlassRim}) and takes none. */
        public void setLook(@NonNull Look look) {
            if (look.equals(mLook)) return;
            mLook = look;
            mShader.setFloatUniform("uDepth", look.thicknessPx(mDensity));
            mShader.setFloatUniform("uDisp", look.dispersion());
            mShader.setFloatUniform("uShine", look.shineScale());
            mShader.setFloatUniform("uSizeLens", look.sizeLens());
            mShader.setFloatUniform("uConcave", look.concave ? 1f : 0f,
                CONCAVE_BAND_DP * mDensity);
            syncClarity();
        }

        /**
         * A finger resting on the glass at {@code (x, y)} in the surface's own coordinates: its
         * light reaches {@code radiusPx}, at {@code intensity} (0..1, the press's fade). Written
         * on every move and every fade frame, so a caller animates it; the program itself never
         * does.
         */
        public void setTouch(float x, float y, float radiusPx, float intensity) {
            radiusPx = Math.max(1f, radiusPx);
            if (mTouchOn && mTouchX == x && mTouchY == y && mTouchRadius == radiusPx
                && mTouchIntensity == intensity) return;
            mTouchOn = true;
            mTouchX = x;
            mTouchY = y;
            mTouchRadius = radiusPx;
            mTouchIntensity = intensity;
            mShader.setFloatUniform("uTouchActive", intensity);
            mShader.setFloatUniform("uTouch", x, y);
            mShader.setFloatUniform("uTouchRadius", radiusPx);
        }

        /** No finger on the glass: the resting look, which is every surface nobody is touching. */
        public void clearTouch() {
            if (!mTouchOn) return;
            mTouchOn = false;
            mTouchIntensity = 0f;
            mShader.setFloatUniform("uTouchActive", 0f);
            mShader.setFloatUniform("uTouch", 0f, 0f);
            mShader.setFloatUniform("uTouchRadius", 1f);
        }

        /**
         * The page's pose ({@link GlassPose}): a turn shifts the picture through the slab and
         * lights it as a turned plate, a scale magnifies the picture a little
         * ({@link GlassPoseOptics}). The rest pose is the rest look; the caller hands a pose only
         * while the glass may move ({@link GlassMotionGate}). Written only when it moved, so a
         * still slab costs nothing; allocates nothing.
         */
        public void setPose(@NonNull GlassPose pose) {
            setPose(pose.tiltX, pose.tiltY, pose.scale);
        }

        /** {@link #setPose(GlassPose)} from its three numbers; non-finite reads as rest. */
        public void setPose(float tiltX, float tiltY, float scale) {
            if (!Float.isFinite(tiltX) || !Float.isFinite(tiltY)) {
                tiltX = 0f;
                tiltY = 0f;
            }
            if (!Float.isFinite(scale) || !(scale > 0f)) scale = 1f;
            if (mPoseWrittenValid && mPose.tiltX == tiltX && mPose.tiltY == tiltY
                && mPose.scale == scale) return;
            mPose.tiltX = tiltX;
            mPose.tiltY = tiltY;
            mPose.scale = scale;
            float[] u = mPoseNext;
            GlassPoseOptics.active().uniforms(mPose, GlassSlab.active(), mDensity, u, mPoseScratch);
            float[] w = mPoseWritten;
            boolean first = !mPoseWrittenValid;
            if (first || u[0] != w[0] || u[1] != w[1] || u[2] != w[2] || u[3] != w[3]) {
                mShader.setFloatUniform("uPose", u[0], u[1], u[2], u[3]);
            }
            if (first || u[4] != w[4] || u[5] != w[5] || u[6] != w[6] || u[7] != w[7]) {
                mShader.setFloatUniform("uPoseLight", u[4], u[5], u[6], u[7]);
            }
            if (first || u[8] != w[8] || u[9] != w[9] || u[10] != w[10] || u[11] != w[11]) {
                mShader.setFloatUniform("uPoseHalf", u[8], u[9], u[10], u[11]);
            }
            if (first || u[12] != w[12] || u[13] != w[13] || u[14] != w[14] || u[15] != w[15]) {
                mShader.setFloatUniform("uGlint", u[12], u[13], u[14], u[15]);
            }
            System.arraycopy(u, 0, w, 0, GlassPoseOptics.UNIFORMS);
            mPoseWrittenValid = true;
        }

        /** The rest pose: the rest look, which is every surface at rest. */
        public void clearPose() {
            setPose(0f, 0f, 1f);
        }

        /**
         * The pressed extra key's lens, in the surface's own coordinates: a stadium pill at
         * {@code (cx, cy)} with half extents {@code (hx, hy)}, held at {@code intensity} (0..1).
         */
        public void setLens(float cx, float cy, float hx, float hy, float cornerRadiusPx, float intensity) {
            hx = Math.max(1f, hx);
            hy = Math.max(1f, hy);
            if (mLensOn && mLensCx == cx && mLensCy == cy && mLensHx == hx && mLensHy == hy
                && mLensRadius == cornerRadiusPx && mLensIntensity == intensity) return;
            mLensOn = true;
            mLensCx = cx;
            mLensCy = cy;
            mLensHx = hx;
            mLensHy = hy;
            mLensRadius = cornerRadiusPx;
            mLensIntensity = intensity;
            mShader.setFloatUniform("uLensActive", intensity);
            mShader.setFloatUniform("uLensCenter", cx, cy);
            mShader.setFloatUniform("uLensHalf", hx, hy);
            mShader.setFloatUniform("uLensRadius", cornerRadiusPx);
        }

        /** No lens: the rim and the bend alone, which is every surface but a dock under a pressed key. */
        public void clearLens() {
            if (!mLensOn) return;
            mLensOn = false;
            mLensIntensity = 0f;
            mShader.setFloatUniform("uLensActive", 0f);
            mShader.setFloatUniform("uLensCenter", 0f, 0f);
            mShader.setFloatUniform("uLensHalf", 1f, 1f);
            mShader.setFloatUniform("uLensRadius", 0f);
        }
    }
}
