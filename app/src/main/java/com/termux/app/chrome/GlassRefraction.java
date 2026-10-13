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
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;

/**
 * The one AGSL glass every surface refracts through (API 33+).
 *
 * <p>What separates real glass from frosted polymer is that glass <em>bends</em> light at its
 * edges and catches a crisp highlight there, instead of only diffusing it. The program samples
 * the blurred wallpaper and, within a band along the surface's rounded-rect rim, displaces the
 * sample inward along the rim's own normal by the curve of a thick slab's rounded bevel: nothing
 * at the band's inner edge, bending ever faster toward the rim, where the picture compresses and
 * at the very edge folds back on itself. It then lays one sharp hairline of light where the edge
 * catches it. Two extras sit on top for the clearest glass, both off unless a Look sets them: a
 * bevel light falling along the diagonal, which the top-left and bottom-right of the rim catch
 * and the other two corners do not, and a split of the bent picture into its colours across the
 * band, the way a prism fans out white light. A pressed extra key adds a lens that magnifies
 * toward the key's centre and eases to nothing at its pill rim.</p>
 *
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
 * write per moved frame and no allocation.</p>
 */
public final class GlassRefraction {

    private GlassRefraction() {}

    /** True on phones that run an AGSL shader at all. Everything below is reachable only then. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean available() {
        return Build.VERSION.SDK_INT >= FancierGlassPolicy.MIN_SDK;
    }

    // ------------------------------------------------------------------------------ the look

    /**
     * The knobs the Appearance editor offers, as one value: how far the picture is bent
     * under the rim, how far in from the rim the bend reaches, how bright the rim's light is, and
     * two optional extras for the clearest glass: a bevel light that the rim catches along the
     * diagonal, and a split of the colours across the band. Both are off unless a Look sets them,
     * and every shipped Look leaves them off.
     * Stored in dp and percent so two reads compare equal whatever the screen's density; the
     * program converts when it is handed one.
     */
    public static final class Look {

        /** The numbers the dock's refraction always ran with; the default mode is exactly this. */
        @NonNull public static final Look DEFAULT = new Look(
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_WIDTH,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_LIGHT);

        /** The brightest the rim can be, added to every channel where the light catches; 100% lands here. */
        static final float MAX_RIM = 0.5f;

        /** The brightest the bevel's lit side can be; 100% lands here. */
        static final float MAX_SPEC = 0.6f;

        /**
         * The widest the red/blue split gets at the rim, in dp; 100% lands here. 6 dp read as
         * nothing on a real phone (the taps sample the blurred wall, so only a wide split leaves
         * a fringe), hence the width.
         */
        static final float MAX_DISPERSION_DP = 16f;

        /**
         * How far in the bevel light reaches, as a share of the edge width, and the narrowest and
         * widest it gets in dp: a thin rounded lip on any band, never a wash over a wide one.
         */
        static final float BEVEL_SHARE = 0.5f;
        static final float MIN_BEVEL_DP = 1f;
        static final float MAX_BEVEL_DP = 12f;

        public final int bendDp;
        public final int edgeWidthDp;
        public final int edgeLightPercent;
        /** The bevel highlight, a percentage; 0 draws none. */
        public final int specularPercent;
        /** The chromatic split at the rim, a percentage; 0 samples r, g and b alike. */
        public final int dispersionPercent;

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, 0, 0);
        }

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent,
                    int specularPercent, int dispersionPercent) {
            this.bendDp = bendDp;
            this.edgeWidthDp = edgeWidthDp;
            this.edgeLightPercent = edgeLightPercent;
            this.specularPercent = specularPercent;
            this.dispersionPercent = dispersionPercent;
        }

        /** The user's knobs, already clamped by their accessors. */
        @NonNull
        public static Look of(@NonNull TermuxAppSharedPreferences preferences) {
            return new Look(preferences.getFancierGlassBendDp(),
                preferences.getFancierGlassEdgeWidthDp(),
                preferences.getFancierGlassEdgeLightPercent(),
                preferences.getFancierGlassSpecularPercent(),
                preferences.getFancierGlassDispersionPercent());
        }

        /** {@code uStrength}: how far a rim pixel's sample is pulled inward, in px. */
        public float strengthPx(float density) {
            return bendDp * density;
        }

        /** {@code uBand}: how far in from the rim the pull reaches, in px; never under a pixel. */
        public float bandPx(float density) {
            return Math.max(1f, edgeWidthDp * density);
        }

        /** {@code uRim}: the light added along the hairline, 0..{@link #MAX_RIM}. */
        public float rim() {
            return Math.max(0, Math.min(100, edgeLightPercent)) / 100f * MAX_RIM;
        }

        /**
         * {@code uSpec}: the light the bevel's lit side adds at the rim, 0..{@link #MAX_SPEC},
         * fading to nothing {@link #bevelPx} in.
         */
        public float specular() {
            return Math.max(0, Math.min(100, specularPercent)) / 100f * MAX_SPEC;
        }

        /**
         * {@code uBevel}: how far in from the rim the bevel light reaches, in px: half the edge
         * width, held between {@link #MIN_BEVEL_DP} and {@link #MAX_BEVEL_DP}, and never past the
         * band, so {@link GlassRefraction#seamReachPx} already pushes it off a seam edge.
         */
        public float bevelPx(float density) {
            float dp = Math.max(MIN_BEVEL_DP, Math.min(MAX_BEVEL_DP, edgeWidthDp * BEVEL_SHARE));
            return Math.min(bandPx(density), dp * density);
        }

        /** {@code uDisp}: how far red and blue part from green at the rim, in px; 100% is {@link #MAX_DISPERSION_DP}. */
        public float dispersionPx(float density) {
            return Math.max(0, Math.min(100, dispersionPercent)) / 100f * MAX_DISPERSION_DP * density;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Look)) return false;
            Look that = (Look) other;
            return bendDp == that.bendDp && edgeWidthDp == that.edgeWidthDp
                && edgeLightPercent == that.edgeLightPercent
                && specularPercent == that.specularPercent
                && dispersionPercent == that.dispersionPercent;
        }

        @Override
        public int hashCode() {
            return (((bendDp * 31 + edgeWidthDp) * 31 + edgeLightPercent) * 31 + specularPercent) * 31
                + dispersionPercent;
        }

        @NonNull
        @Override
        public String toString() {
            return "Look{bend=" + bendDp + "dp, edge=" + edgeWidthDp + "dp, light=" + edgeLightPercent + "%, specular="
                + specularPercent + "%, dispersion=" + dispersionPercent + "%}";
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

    /** How far a seam edge is pushed out: past the band, the pull and the corner arc, with a little to spare. */
    public static float seamReachPx(@NonNull Look look, float density, float cornerRadiusPx) {
        return look.bandPx(density) + look.strengthPx(density) + Math.max(0f, cornerRadiusPx)
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
    static final String AGSL =
        "uniform shader content;\n" +
        "uniform float2 uFrameScale;\n" +
        "uniform float2 uFrameOffset;\n" +
        "uniform float3 uLocalX;\n" +
        "uniform float3 uLocalY;\n" +
        "uniform float3 uLocalW;\n" +
        "uniform float2 uRectMin;\n" +
        "uniform float2 uRectMax;\n" +
        "uniform float uRadius;\n" +
        "uniform float uBand;\n" +
        "uniform float uStrength;\n" +
        "uniform float uRim;\n" +
        "uniform float uDisp;\n" +
        "uniform float uSpec;\n" +
        "uniform float uBevel;\n" +
        "uniform float uDensity;\n" +
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
        "half4 main(float2 fragCoord) {\n" +
        "    float2 center = (uRectMin + uRectMax) * 0.5;\n" +
        "    float2 b = (uRectMax - uRectMin) * 0.5;\n" +
        "    float2 p = fragCoord - center;\n" +
        "    float inside = -sdRoundRect(p, b, uRadius);\n" +
        // The normal eases round the corner on an arc wider than the drawn one (1.5x, held under the
        // half size), so the bend never snaps where two edges meet.
        "    float2 n = rectNormal(p, b, min(uRadius * 1.5, min(b.x, b.y)));\n" +
        // The bevel of a thick slab: a quarter circle, 0 at the band's inner edge, 1 at the rim.
        "    float x = clamp(1.0 - inside / uBand, 0.0, 1.0);\n" +
        "    float e = 1.0 - sqrt(1.0 - x * x);\n" +
        "    float2 sampleCoord = fragCoord - n * (e * uStrength);\n" +
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
        "            sampleCoord = mix(sampleCoord, lensCoord - n * (e * uStrength), fade);\n" +
        "            lensGlow = fade * (1.0 - rr);\n" +
        "        }\n" +
        "    }\n" +
        // The bent sample, read from the content in its own pixels: the aim undone. With dispersion
        // on and the pixel in the band, seven samples spread along the normal stand in for the
        // spectrum: red weighs the three bent least (and a seventh of the one bent most, the violet
        // end), green the middle three (and a seventh of the next one out), blue the three bent
        // most. Each channel's weights sum to 1, as alpha's seven sevenths do, so a flat picture
        // stays exactly as it was. Anywhere else, and on every Look with dispersion off, the one
        // sample is the whole picture. The split grows linearly across the band (x, not the bevel
        // profile e): e is near zero over most of the band and only reaches the full width in the
        // last pixel under the hairline, which left the fringe invisible at any setting.
        "    half4 col = half4(0.0);\n" +
        "    if (uDisp > 0.0 && x > 0.0) {\n" +
        "        float2 split = n * (x * uDisp);\n" +
        "        half4 c = content.eval(framePx(sampleCoord + split));\n" +
        "        col.r += c.r / 3.5; col.a += c.a / 7.0;\n" +
        "        c = content.eval(framePx(sampleCoord + split * (2.0 / 3.0)));\n" +
        "        col.r += c.r / 3.5; col.g += c.g / 7.0; col.a += c.a / 7.0;\n" +
        "        c = content.eval(framePx(sampleCoord + split * (1.0 / 3.0)));\n" +
        "        col.r += c.r / 3.5; col.g += c.g / 3.5; col.a += c.a / 7.0;\n" +
        "        c = content.eval(framePx(sampleCoord));\n" +
        "        col.g += c.g / 3.5; col.a += c.a / 7.0;\n" +
        "        c = content.eval(framePx(sampleCoord - split * (1.0 / 3.0)));\n" +
        "        col.g += c.g / 3.5; col.b += c.b / 3.0; col.a += c.a / 7.0;\n" +
        "        c = content.eval(framePx(sampleCoord - split * (2.0 / 3.0)));\n" +
        "        col.b += c.b / 3.0; col.a += c.a / 7.0;\n" +
        "        c = content.eval(framePx(sampleCoord - split));\n" +
        "        col.r += c.r / 7.0; col.b += c.b / 3.0; col.a += c.a / 7.0;\n" +
        "    } else {\n" +
        "        col = content.eval(framePx(sampleCoord));\n" +
        "    }\n" +
        // One clean, sharp hairline rim where the light catches the glass edge. No dark contour, no
        // wide bevel band, no inner shadow — minimal/zen: a crisp pane with slight edge refraction.
        "    float rim = 1.0 - smoothstep(0.0, 2.0 * uDensity, inside);\n" +
        "    col.rgb = col.rgb + half3(rim * uRim);\n" +
        "    col.rgb = col.rgb + half3(lensGlow * uRim * 0.6);\n" +
        // The bevel light, falling along the diagonal: the top-left and bottom-right of the rim face
        // it and catch it, the other two corners run edge-on to it and stay dark. It rounds off
        // within uBevel of the rim, a lip of light inside the hairline. Off at uSpec 0.
        "    float lit = abs(dot(n, float2(0.70710678, 0.70710678)));\n" +
        "    float bevel = clamp(1.0 - inside / uBevel, 0.0, 1.0);\n" +
        "    bevel = bevel * bevel;\n" +
        "    col.rgb = col.rgb + half3(bevel * lit * uSpec);\n" +
        "    return col;\n" +
        "}\n";

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

    /** The key lens's own strength and feather, which no knob reaches: the press reads the same in both modes. */
    private static final float LENS_STRENGTH = 0.20f;
    private static final float LENS_FEATHER_DP = 10f;

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

        private Program(float density) {
            mShader = new RuntimeShader(AGSL);
            mDensity = density;
            mShader.setFloatUniform("uDensity", density);
            mShader.setFloatUniform("uLensStrength", LENS_STRENGTH);
            mShader.setFloatUniform("uLensFeather", density * LENS_FEATHER_DP);
            setAim(1f, 1f, 0f, 0f);
            setLocal(null);
            setLook(Look.DEFAULT);
            mLensOn = true; // so the clear below is a write, not a skip
            clearLens();
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
            mShader.setFloatUniform("uRectMin", left, top);
            mShader.setFloatUniform("uRectMax", Math.max(left + 1f, right), Math.max(top + 1f, bottom));
            mShader.setFloatUniform("uRadius", Math.max(0f, cornerRadiusPx));
        }

        /** The five knobs, converted at this program's density. */
        public void setLook(@NonNull Look look) {
            if (look.equals(mLook)) return;
            mLook = look;
            mShader.setFloatUniform("uBand", look.bandPx(mDensity));
            mShader.setFloatUniform("uStrength", look.strengthPx(mDensity));
            mShader.setFloatUniform("uRim", look.rim());
            mShader.setFloatUniform("uDisp", look.dispersionPx(mDensity));
            mShader.setFloatUniform("uSpec", look.specular());
            mShader.setFloatUniform("uBevel", look.bevelPx(mDensity));
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
