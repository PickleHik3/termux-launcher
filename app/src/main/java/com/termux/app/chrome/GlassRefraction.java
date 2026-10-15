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
 * the blurred wallpaper and, within a band along the surface's rounded-rect rim, pulls the sample
 * inward along the rim's own normal on Haze's edge profile: the whole bend at the rim, under a
 * third of it a quarter of the way in, under a tenth half way and nothing at the band's inner
 * edge, so the bend reads as a narrow ring round a face that samples exactly where it is. How far
 * the rim reaches in for its picture is held at half the surface's short side, Haze's plain cap,
 * so a small pane never shows the other edge's picture. Across the outermost dp the pull eases
 * back to nothing, as Haze's edge softness does, so the outline's own pixel shows the picture just
 * outside it and the glass is continuous with the wallpaper there.</p>
 * <p>Along that same dp the rim is a hairline, about as wide and as bright as Haze's and Kyant's:
 * a little darker all round, so the edge reads on a light wallpaper, and over that mixed toward
 * white where it faces the room's one light straight above, fading round the corners and gone
 * down the sides, so it can never outline the whole surface or push a pixel past white. The
 * hairline is fixed material, not a knob. Where a clearer picture of the wallpaper is handed in
 * ({@link Program#setClarityInput}), the band shows it through the bend in place of the frost,
 * growing clearer toward the rim, which is what a thick glass edge does to what is behind it.
 * Dispersion splits the bent picture into red and blue at the corners, in step with how far it is
 * bent there, the way a prism fans out white light; the middle of a straight edge takes none. A
 * pressed extra key adds a lens that magnifies toward the key's centre and eases to nothing at its
 * pill rim, and a finger resting on the glass ({@link Program#setTouch}) lights the rim nearest
 * it, scatters a faint glow under it and draws the picture very slightly toward it.</p>
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
     * specular, which the program no longer reads: the rim's hairline is fixed material. Stored in
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
         * The most white the rim's hairline lays over the picture where it faces the light
         * squarely, as an alpha (Haze's rim light runs 0.38 to 0.55, Kyant's 0.38 to 0.5). The
         * light is mixed toward white, never added, so a lit pixel moves at most this share of
         * the way from its own colour to white. The program's {@code kRim}.
         */
        static final float LIGHT_ALPHA = 0.45f;

        /**
         * How much the hairline takes off the picture all round: it is multiplied by 1 minus
         * this at the outline (Haze's edge shadow runs 10 to 14% black on light glass). The
         * program's {@code kShade}.
         */
        static final float SHADE = 0.12f;

        /**
         * The light's falloff round the rim, the exponent on how squarely the rim faces it. The
         * light is straight above, so the top edge is lit along its length, the corner's 45
         * degrees keeps a sixteenth and the sides none: the arc Haze's 3D lobe at k 16 to 24
         * draws. The program's {@code kLobeK}.
         */
        static final float LOBE_K = 8f;

        /**
         * The hairline's width: 1 dp and never under 2 px (Haze's edge softness and its sample
         * step). Full at the outline, gone this far in. The pull eases in across the same width.
         * The program's {@code kRimDp} and {@code kMinRimPx}.
         */
        static final float RIM_WIDTH_DP = 1f;
        static final float MIN_RIM_WIDTH_PX = 2f;

        /**
         * The deepest the pull may reach at the rim, as a share of the band: one and a half times
         * it. Every Look the editor writes sits under it; it only holds an odd stored pair.
         */
        static final float MAX_PULL_SHARE = 1.5f;

        /**
         * How far red and blue part from green at 100%, as a share of the local pull, at a corner;
         * nothing at the middle of a straight edge ({@link GlassRefraction#cornerWeight}). Clear's
         * 40% is Haze Clear's 0.02, a glint at the corners, not a rainbow.
         */
        static final float MAX_DISPERSION_SHARE = 0.05f;

        /** How much of the clearer picture the band shows at the rim at 100%: all of it. */
        static final float MAX_CLARITY = 1f;

        public final int bendDp;
        public final int edgeWidthDp;
        /**
         * The stored edge light, a percentage. Kept so a saved look loads and compares as it
         * was; the program ignores it ({@link #LIGHT_ALPHA}).
         */
        public final int edgeLightPercent;
        /**
         * The stored specular, a percentage. Kept so a saved look loads and compares as it was;
         * the program ignores it ({@link #LOBE_K}).
         */
        public final int specularPercent;
        /** The chromatic split at the corners, a percentage; 0 samples r, g and b alike. */
        public final int dispersionPercent;
        /** How clear the wallpaper shows through the bent edge, a percentage; 0 keeps the frost. */
        public final int clarityPercent;

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, 0, 0, 0);
        }

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent,
                    int specularPercent, int dispersionPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, specularPercent, dispersionPercent, 0);
        }

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent,
                    int specularPercent, int dispersionPercent, int clarityPercent) {
            this.bendDp = bendDp;
            this.edgeWidthDp = edgeWidthDp;
            this.edgeLightPercent = edgeLightPercent;
            this.specularPercent = specularPercent;
            this.dispersionPercent = dispersionPercent;
            this.clarityPercent = clarityPercent;
        }

        /** The user's knobs, already clamped by their accessors. */
        @NonNull
        public static Look of(@NonNull TermuxAppSharedPreferences preferences) {
            return new Look(preferences.getFancierGlassBendDp(),
                preferences.getFancierGlassEdgeWidthDp(),
                preferences.getFancierGlassEdgeLightPercent(),
                preferences.getFancierGlassSpecularPercent(),
                preferences.getFancierGlassDispersionPercent(),
                preferences.getFancierGlassClarityPercent());
        }

        /** A percentage as 0..1, clamped. */
        private static float share(int percent) {
            return Math.max(0, Math.min(100, percent)) / 100f;
        }

        /**
         * {@code uStrength}: how far a rim pixel's sample is pulled inward, in px: the bend, held
         * under {@link #MAX_PULL_SHARE} of the band. The program holds it again under half the
         * surface's short side.
         */
        public float strengthPx(float density) {
            return Math.max(0f, Math.min(bendDp * density, MAX_PULL_SHARE * bandPx(density)));
        }

        /** {@code uBand}: how far in from the rim the pull reaches, in px; never under a pixel. */
        public float bandPx(float density) {
            return Math.max(1f, edgeWidthDp * density);
        }

        /**
         * {@code uDisp}: how far red and blue part from green, as a share of the local pull at a
         * corner, 0..{@link #MAX_DISPERSION_SHARE}.
         */
        public float dispersion() {
            return share(dispersionPercent) * MAX_DISPERSION_SHARE;
        }

        /** {@code uClarity}: how much of the clearer picture the rim shows, 0..{@link #MAX_CLARITY}. */
        public float clarity() {
            return share(clarityPercent) * MAX_CLARITY;
        }

        /** The hairline's width in px at {@code density}, as the program computes it; see {@link #RIM_WIDTH_DP}. */
        public static float rimWidthPx(float density) {
            return Math.max(MIN_RIM_WIDTH_PX, RIM_WIDTH_DP * density);
        }

        /**
         * How far in from the rim the hairline and the eased pull reach, in px: what a seam edge
         * must be pushed past for neither to show along it.
         */
        public static float rimReachPx(float density) {
            return rimWidthPx(density);
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
                && clarityPercent == that.clarityPercent;
        }

        @Override
        public int hashCode() {
            return ((((bendDp * 31 + edgeWidthDp) * 31 + edgeLightPercent) * 31 + specularPercent) * 31
                + dispersionPercent) * 31 + clarityPercent;
        }

        @NonNull
        @Override
        public String toString() {
            return "Look{bend=" + bendDp + "dp, edge=" + edgeWidthDp + "dp, light=" + edgeLightPercent + "%, specular="
                + specularPercent + "%, dispersion=" + dispersionPercent + "%, clarity=" + clarityPercent + "%}";
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
     * How far a seam edge is pushed out: past the band (or the hairline, where a narrow band ends
     * inside it), the pull and the corner arc, with a little to spare.
     */
    public static float seamReachPx(@NonNull Look look, float density, float cornerRadiusPx) {
        return Math.max(look.bandPx(density), Look.rimReachPx(density)) + look.strengthPx(density)
            + Math.max(0f, cornerRadiusPx) + 2f * density;
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
        "uniform float uBand;\n" +
        "uniform float uStrength;\n" +
        "uniform float uDisp;\n" +
        "uniform float uClarity;\n" +
        "uniform float uDensity;\n" +
        // A finger on the glass: where it rests, how far its light reaches, and its 0..1 fade.
        "uniform float uTouchActive;\n" +
        "uniform float2 uTouch;\n" +
        "uniform float uTouchRadius;\n" +
        "uniform float uTouchLens;\n" +
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
        // The room's one light: straight above, so the top edge is lit along its length and the
        // sides fade. A unit vector toward it; LIGHT_X and LIGHT_Y in Java.
        "const float2 kLight = float2(0.0, -1.0);\n" +
        // The hairline, fixed material: its most white where it faces the light, how much darker
        // it is all round, the light's falloff, and its width, a dp never under two px.
        // Look.LIGHT_ALPHA, SHADE, LOBE_K, RIM_WIDTH_DP and MIN_RIM_WIDTH_PX in Java.
        "const float kRim = 0.45;\n" +
        "const float kShade = 0.12;\n" +
        "const float kLobeK = 8.0;\n" +
        "const float kRimDp = 1.0;\n" +
        "const float kMinRimPx = 2.0;\n" +
        // How fast Haze's edge profile decays into the band; EDGE_DECAY in Java.
        "const float kDecay = 0.225;\n" +
        // The pressed key's added glow at its centre; KEY_GLOW in Java.
        "const float kKeyGlow = 0.15;\n" +
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
        // The bend's share across the band, t 0 at the rim to 1 at the band's inner edge: Haze's
        // edge profile, 1 at the rim, falling off steeply and exactly 0 at t 1, so the bend is a
        // narrow ring and the face beyond it is untouched. edgeProfile in Java.
        "float edgeProfile(float t) {\n" +
        "    float u = 1.0 - t * t;\n" +
        "    return exp(-t / kDecay) * u * u;\n" +
        "}\n" +
        // How much of the colour split a pixel at p (from the centre) of a surface of half size b
        // takes: nothing on the two axes, so nothing at the middle of a straight edge, all of it
        // at a corner (Kyant's and Haze's weight). cornerWeight in Java.
        "float cornerWeight(float2 p, float2 b) {\n" +
        "    return min(abs(p.x * p.y) / max(b.x * b.y, 1.0), 1.0);\n" +
        "}\n" +
        // How squarely the rim with outward normal n faces the light, to the power k: 1 along the
        // top edge, a sixteenth 45 degrees round, nothing facing away. lobe in Java.
        "float lobe(float2 n, float k) {\n" +
        "    float c = max(dot(n, kLight), 0.0);\n" +
        "    return c > 0.0 ? pow(c, k) : 0.0;\n" +
        "}\n" +
        "half4 main(float2 fragCoord) {\n" +
        "    float2 center = (uRectMin + uRectMax) * 0.5;\n" +
        "    float2 b = (uRectMax - uRectMin) * 0.5;\n" +
        "    float2 p = fragCoord - center;\n" +
        "    float inside = -sdRoundRect(p, b, uRadius);\n" +
        // The normal eases round the corner on an arc wider than the drawn one (1.5x, held under the
        // half size), so the bend never snaps where two edges meet.
        "    float2 n = rectNormal(p, b, min(uRadius * 1.5, min(b.x, b.y)));\n" +
        // t runs 0 at the rim to 1 at the band's inner edge, and stays 1 across the face.
        "    float t = clamp(inside / uBand, 0.0, 1.0);\n" +
        // The hairline's width, and how far in from the outline this pixel is across it: 0 at the
        // outline, 1 from a hairline in.
        "    float rimW = max(kMinRimPx, kRimDp * uDensity);\n" +
        "    float edge = smoothstep(0.0, rimW, inside);\n" +
        // The pull: inward along the rim's normal by the profile's share of the bend, the bend held
        // at half the surface's short side (Haze's plain cap) so a small pane never reaches across
        // to the other edge's picture, and eased to nothing across the outermost hairline (Haze's
        // edge softness) so the outline's pixel shows the picture just outside it. An offset that
        // vanishes at t 1: a face pixel samples exactly where it is. A seam edge lies past the band
        // (rimRect), so it takes none. pullPx in Java is the same arithmetic.
        "    float pull = edgeProfile(t) * min(uStrength, min(b.x, b.y)) * edge;\n" +
        "    float2 sampleCoord = fragCoord - n * pull;\n" +
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
        "            float2 lensBent = lensCoord - n * pull;\n" +
        "            sampleCoord = mix(sampleCoord, lensBent, fade);\n" +
        "            lensGlow = fade * (1.0 - rr);\n" +
        "        }\n" +
        "    }\n" +
        // How far the clearer picture takes over: easing in from the frost at the band's inner
        // edge, half way at the band's middle, uClarity under the rim. Zero on the face.
        "    float x = 1.0 - t;\n" +
        "    float clarityK = uClarity > 0.0 && x > 0.0 ? x * x * (3.0 - 2.0 * x) * uClarity : 0.0;\n" +
        // The colour split, at the corners only and in step with the pull there: red reads the
        // picture bent a little less, blue a little more, green as bent (three taps, Haze's
        // Simple). Where a clearer picture shows through, it is split the same way, so the fringe
        // survives at the rim. A pixel with no split, every straight-edge middle and every face
        // pixel among them, takes the one sample.
        "    float split = uDisp > 0.0 ? pull * uDisp * cornerWeight(p, b) : 0.0;\n" +
        "    half4 col = half4(0.0);\n" +
        "    if (split > 0.0) {\n" +
        "        float2 fr = framePx(sampleCoord + n * split);\n" +
        "        float2 fg = framePx(sampleCoord);\n" +
        "        float2 fb = framePx(sampleCoord - n * split);\n" +
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
        // The hairline: full at the outline, gone a hairline in. Darker all round by kShade, so the
        // edge reads on a light wallpaper, then lit where it faces the light: mixed toward white by
        // at most kRim on the lobe, so it can never outline the whole surface or push a pixel past
        // white. A finger lights the rim nearest it the same way. edgeLit in Java.
        "    float rim = 1.0 - edge;\n" +
        "    col.rgb = col.rgb * half(1.0 - rim * kShade);\n" +
        "    float light = rim * kRim * (lobe(n, kLobeK) + 1.5 * touchG);\n" +
        "    col.rgb = mix(col.rgb, half3(1.0), half(clamp(light, 0.0, 1.0)));\n" +
        // The pressed key's glow and the faint scatter under a finger stay small additive light.
        "    col.rgb = col.rgb + half3(lensGlow * kKeyGlow + 0.045 * touchG * touchG);\n" +
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

    /** The program's light direction, {@code kLight}: a unit vector straight up. */
    static final float LIGHT_X = 0f;
    static final float LIGHT_Y = -1f;

    /** The program's {@code kDecay}: Haze's edge profile's decay length, as a share of the band. */
    static final float EDGE_DECAY = 0.225f;

    /** The program's {@code kKeyGlow}: the white a pressed key adds at its centre. */
    static final float KEY_GLOW = 0.15f;

    /** AGSL's {@code smoothstep}. */
    static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    /**
     * The program's {@code edgeProfile}: the bend's share across the band, {@code t} 0 at the rim
     * to 1 at the band's inner edge, Haze's {@code exp(-t / 0.225) (1 - t²)²}.
     */
    static float edgeProfile(float t) {
        float u = 1f - t * t;
        return (float) Math.exp(-t / EDGE_DECAY) * u * u;
    }

    /**
     * The program's pull, in px, for a pixel {@code insidePx} in from the rim of a surface whose
     * half short side is {@code halfShortPx}: the profile across the band times the strength held
     * at that half side, eased to nothing across the outermost {@code rimWidthPx}.
     */
    static float pullPx(float insidePx, float bandPx, float strengthPx, float halfShortPx, float rimWidthPx) {
        float t = Math.max(0f, Math.min(1f, insidePx / bandPx));
        return edgeProfile(t) * Math.min(strengthPx, halfShortPx) * smoothstep(0f, rimWidthPx, insidePx);
    }

    /** The program's {@code cornerWeight}, for a pixel at {@code (px, py)} from the centre of a surface of half size {@code (bx, by)}. */
    static float cornerWeight(float px, float py, float bx, float by) {
        return Math.min(Math.abs(px * py) / Math.max(bx * by, 1f), 1f);
    }

    /** The program's {@code lobe}: how squarely the rim with outward normal {@code (nx, ny)} faces the light, to the power {@code k}. */
    static float lobe(float nx, float ny, float k) {
        float c = Math.max(nx * LIGHT_X + ny * LIGHT_Y, 0f);
        return c > 0f ? (float) Math.pow(c, k) : 0f;
    }

    /**
     * The program's hairline on one colour channel {@code face} (0..1), with no finger on the
     * glass, for a pixel {@code insidePx} in from the outline whose rim faces the light by
     * {@code lobe}: darker by {@link Look#SHADE} at the outline, then mixed toward white by
     * {@link Look#LIGHT_ALPHA} times the lobe, both gone a hairline in.
     */
    static float edgeLit(float face, float insidePx, float rimWidthPx, float lobe) {
        float rim = 1f - smoothstep(0f, rimWidthPx, insidePx);
        float shaded = face * (1f - rim * Look.SHADE);
        float light = Math.max(0f, Math.min(1f, rim * Look.LIGHT_ALPHA * lobe));
        return shaded + (1f - shaded) * light;
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
        private float mClarityWritten = Float.NaN;
        private boolean mTouchOn;
        private float mTouchX, mTouchY, mTouchRadius, mTouchIntensity;

        private Program(float density) {
            mShader = new RuntimeShader(AGSL);
            mDensity = density;
            mShader.setFloatUniform("uDensity", density);
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
            // The clearer picture is the frost itself until a caller hands one in, so the mix
            // reads back what it was given; the clarity term is then free to be written.
            mShader.setInputShader("sharp", content);
            mSharpBound = true;
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
            syncClarity();
        }

        /** True once a shader is bound as the clearer picture, by {@link #setInput} or {@link #setClarityInput}. */
        public boolean hasClarityInput() {
            return mSharpBound;
        }

        /** {@code uClarity}: the look's clarity while a sharp shader is bound, zero otherwise. */
        private void syncClarity() {
            Look look = mLook;
            float clarity = look != null && mSharpBound ? look.clarity() : 0f;
            if (clarity == mClarityWritten) return;
            mClarityWritten = clarity;
            mShader.setFloatUniform("uClarity", clarity);
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

        /** The knobs, converted at this program's density. The hairline is fixed and takes none. */
        public void setLook(@NonNull Look look) {
            if (look.equals(mLook)) return;
            mLook = look;
            mShader.setFloatUniform("uBand", look.bandPx(mDensity));
            mShader.setFloatUniform("uStrength", look.strengthPx(mDensity));
            mShader.setFloatUniform("uDisp", look.dispersion());
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
