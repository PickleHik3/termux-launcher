package com.termux.app.terminal;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;

/**
 * Retro monitor looks (API 33+): scanlines, phosphor glow and tint, or a TFT pixel grid, drawn
 * through a view as its render effect. One family of shaders for every surface on the home screen:
 * a pane card bends the picture like the tube it stands for, the chrome draws it flat, and both
 * take their rows from the screen ({@link RetroUniforms#phase}), so a scanline runs on unbroken
 * from the status bar through the panes into the dock and the keyboard.
 *
 * <p>Each owner ({@link RetroEffectBinder}) holds one instance, which compiles each shader family
 * once and only re-writes uniforms (and re-wraps the effect) when what it draws moved.
 */
public final class PaneRetroEffect {

    private static final String LOG_TAG = "PaneRetroEffect";

    /** The CRT's barrel bend on a pane card; the shader and {@link #displayedPoint} share it. */
    static final float CRT_BEND = 0.04f;

    /**
     * How dark the picture's edge goes under the vignette (a share of full brightness lost at the
     * edge, full at the middle third): a CRT's raster is lit from the middle, and the edge is where
     * the tube face begins. Steep near the edge, as sam1am/termux_crt draws it (0.45 there).
     */
    static final float PANE_VIGNETTE = 0.30f;

    /**
     * The tube face a pane's picture sits in: the dark glass the picture's barrel outline leaves
     * at the corners, {@code r, g, b} and its alpha over the pane's glass.
     */
    static final String TUBE_RGB = "0.030, 0.030, 0.036";
    static final float TUBE_ALPHA = 0.92f;

    /** The lit phosphor's own haze at the middle of the picture (additive, in the tint's colour). */
    static final float AMBIENT = 0.04f;

    /**
     * Where content at ({@code x}, {@code y}) of a {@code w}×{@code h} card shows on screen once
     * {@code style} bends it: the inverse of the CRT shader's sample map, so things drawn above the
     * card (the cursor trail) land on the cell the user sees. Identity for every other style.
     */
    public static void displayedPoint(@NonNull PaneRetroStyle style, float w, float h, float x, float y,
                                      @NonNull float[] out) {
        displayedPoint(style, 0f, 0f, w, h, x, y, out);
    }

    /**
     * {@link #displayedPoint(PaneRetroStyle, float, float, float, float, float[])} for a bend
     * worked out over the rect {@code left, top, right, bottom} of the view (a pane's screen
     * inside its bezel, {@link RetroUniforms#bendLeft}); {@code x}, {@code y} and the answer are in
     * the view's own px.
     */
    public static void displayedPoint(@NonNull PaneRetroStyle style, float left, float top,
                                      float right, float bottom, float x, float y,
                                      @NonNull float[] out) {
        float w = right - left;
        float h = bottom - top;
        if (!style.usesCrt() || w <= 0f || h <= 0f) {
            out[0] = x;
            out[1] = y;
            return;
        }
        float sx = (x - left) / w * 2f - 1f;
        float sy = (y - top) / h * 2f - 1f;
        float cx = sx;
        float cy = sy;
        // Fixed point of c = s / f(c); f stays within 4 % of 1, so a few steps settle it.
        for (int i = 0; i < 5; i++) {
            float f = (1f + CRT_BEND * (cx * cx + cy * cy)) / (1f + CRT_BEND);
            cx = sx / f;
            cy = sy / f;
        }
        out[0] = left + (cx + 1f) * 0.5f * w;
        out[1] = top + (cy + 1f) * 0.5f * h;
    }

    /** How far the phosphor triad darkens each channel outside its own third of the period. */
    static final float TRIAD_DEPTH = 0.10f;

    /** The glass recess's inner shadow on a pane: its strength at the lip and its width in dp. */
    static final float RECESS_SHADE = 0.25f;
    static final float RECESS_DP = 6f;

    /**
     * The sheen on a pane's glass: its peak (additive white), where it crosses the screen's
     * top-left to bottom-right diagonal (as a share of it) and its Gaussian sigma (likewise).
     */
    static final float SHEEN_PEAK = 0.06f;
    static final float SHEEN_AT = 0.35f;
    static final float SHEEN_SIGMA = 0.12f;

    /** At uDim 1, the share of saturation and of brightness the picture loses. */
    static final float DIM_SATURATION = 0.20f;
    static final float DIM_BRIGHTNESS = 0.10f;

    /**
     * The 1 px unsharp mask on a bent screen: the bend reads every pixel from between two of
     * kitty's, which splits a 1 px stem across two; this gives the stem its weight back. Off on
     * the flat chrome, whose samples are exact and would only ring.
     */
    static final float SHARPEN = 0.20f;

    /**
     * The glow blooms only from pixels brighter than this (the share of full white it is
     * measured from); under it, an anti-aliased edge or a faint pixel adds nothing, so the
     * halos stay on the glyphs instead of filling the counters of dense text.
     */
    static final float BLOOM_FLOOR = 0.45f;

    /** How much of the glow a lit pixel adds back to itself. */
    static final float BLOOM_WEIGHT = 0.35f;

    /**
     * Extra bloom weight a pixel gets as it nears white (smoothstep over {@link #BLOOM_PEAK_FROM}
     * to {@link #BLOOM_PEAK_TO} of its luma), so a lone bright pixel glints and ordinary text
     * keeps {@link #BLOOM_WEIGHT}.
     */
    static final float BLOOM_PEAK_EXTRA = 0.10f;
    static final float BLOOM_PEAK_FROM = 0.70f;
    static final float BLOOM_PEAK_TO = 0.95f;

    /**
     * The colour signal's reach in dp: the glow taps sideways (a beam smears along the row) and
     * up and down; their mean carries the picture's chroma, the luma stays at full bandwidth.
     */
    static final float CHROMA_SIDE_DP = 2.0f;
    static final float CHROMA_UP_DP = 1.0f;

    /**
     * The scanline gap's floor: a dark pixel's rows dip to the first, a bright one's beam spills
     * into the gap and dips only to the second (smoothstep over {@link #SCAN_BRIGHT_FROM} to
     * {@link #SCAN_BRIGHT_TO} of its luma).
     */
    static final float SCAN_FLOOR_DARK = 0.70f;
    static final float SCAN_FLOOR_BRIGHT = 0.92f;
    static final float SCAN_BRIGHT_FROM = 0.15f;
    static final float SCAN_BRIGHT_TO = 0.85f;

    /** The period every shader takes its rows and cells from, in px: {@link RetroUniforms#PERIOD_DP}. */
    private static final String PERIOD = "(" + RetroUniforms.PERIOD_DP + " * uDensity)";

    /** Both programs declare the same uniforms so one binder sets them. */
    private static final String HEAD =
        "uniform shader content;\n"
        + "uniform float2 uSize;\n"
        // Where this view's 0 falls in the screen's period (RetroUniforms#phase): added to every
        // coordinate before the period is taken, so one row of the screen is one row of every
        // surface on it.
        + "uniform float2 uOrigin;\n"
        + "uniform float uDensity;\n"
        // The card, left/top/right/bottom, and its corners' radius.
        + "uniform float4 uCard;\n"
        + "uniform float uRadius;\n"
        + "uniform half4 uTint;\n"
        // 1 keeps a pixel's own colour where it has one (PaneRetroStyle#keepsColour): the tint
        // then takes the greys and whites and leaves coloured text its colour. 0 tints everything.
        + "uniform float uTintKeep;\n"
        + "uniform float uBend;\n"
        + "uniform float uVignette;\n"
        // The screen inside the card the effect is drawn on (a pane's bezel stands between the
        // two, drawn as it is), its radius, and the rect the bend is worked out over.
        + "uniform float4 uScreen;\n"
        + "uniform float uScreenRadius;\n"
        + "uniform float4 uBendBox;\n"
        // The red/blue split at the screen's edge in px (RetroUniforms#aberr); with uAberrFlat 1
        // it is that split everywhere instead of growing from the centre (the chrome).
        + "uniform float uAberr;\n"
        + "uniform float uAberrFlat;\n"
        // 1 draws the glass's recess and sheen (a pane), 0 leaves them out (the chrome).
        + "uniform float uRecess;\n"
        // 0..1: how far the picture loses brightness and colour (an unfocused pane).
        + "uniform float uDim;\n"
        // 1 blends neighbouring pixels sideways like a video cable (Composite signal setting):
        // luma over three taps, chroma over five, no sharpen. Always 0 on the chrome and on TFT.
        + "uniform float uComposite;\n"
        // The live screen event (PaneScreenEvent): its kind id (0 none, 1 bell tear, 2 power on,
        // 3 power off, 4 focus wobble, 5 death snow, 6 ambient tear), its progress 0..1 and a
        // random 0..1.
        // Always 0 on the chrome and on TFT, which never play events.
        + "uniform float uEvent;\n"
        + "uniform float uEventT;\n"
        + "uniform float uSeed;\n"
        // The ambient flicker (PaneAmbient, under Idle animations): its depth 0..0.03 and its
        // clock in seconds. Always 0 on the chrome and on TFT.
        + "uniform float uFlicker;\n"
        + "uniform float uTime;\n"
        // Signed distance from the rounded rect r's edge, negative inside.
        + "float roundDist(float2 p, float4 r, float radius) {\n"
        + "    float2 h = (r.zw - r.xy) * 0.5;\n"
        + "    float2 q = abs(p - r.xy - h) - (h - radius);\n"
        + "    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - radius;\n"
        + "}\n"
        + "float roundMask(float2 p, float4 r, float radius) {\n"
        + "    return 1.0 - smoothstep(-0.5, 0.5, roundDist(p, r, radius));\n"
        + "}\n"
        // The card's own rounded corners, redrawn here: a view's outline clip does not reach what
        // its own render effect puts out. A container whose children clip themselves is cut to
        // its bounds, which this leaves whole.
        + "float cardMask(float2 p) {\n"
        + "    return roundMask(p, uCard, uRadius);\n"
        + "}\n";

    static final String CRT_AGSL = HEAD
        + "const float PI = 3.14159265;\n"
        + "half4 main(float2 p) {\n"
        + "    float m = cardMask(p);\n"
        + "    float s = roundMask(p, uScreen, uScreenRadius);\n"
        + "    half4 plain = content.eval(p);\n"
        // The bezel: the view as it is, no bend and no scanlines.
        + "    if (s <= 0.0) {\n"
        + "        return plain * half(m);\n"
        + "    }\n"
        + "    float2 lo = uBendBox.xy;\n"
        + "    float2 size = max(uBendBox.zw - uBendBox.xy, float2(1.0, 1.0));\n"
        + "    float2 c = (p - lo) / size * 2.0 - 1.0;\n"
        // Barrel bend pinned at the middle of each edge: the picture touches the glass there, its
        // corners pull inward and the tube face shows in between (TUBE_RGB); no cell ever leaves
        // the screen. Flat (uBend 0) on the chrome, whose keys and icons have to stay under the
        // finger, where the picture is the whole screen. Read only from inside the screen, so the
        // bezel never shows in the picture.
        + "    float2 w = c * (1.0 + uBend * dot(c, c)) / (1.0 + uBend);\n"
        + "    float2 wp = (w * 0.5 + 0.5) * size + lo;\n"
        + "    float pic = 1.0 - smoothstep(-0.5, 0.5, roundDist(wp, uScreen, uScreenRadius));\n"
        + "    float2 inMin = uScreen.xy + 0.5;\n"
        + "    float2 inMax = max(uScreen.zw - 0.5, inMin);\n"
        + "    float2 at = clamp(wp, inMin, inMax);\n"
        // Screen events that move the picture: they only shift where it is read from, so they
        // cost no extra sample.
        + "    float2 scr = max(uScreen.zw - uScreen.xy, float2(1.0, 1.0));\n"
        + "    float cy = (uScreen.y + uScreen.w) * 0.5;\n"
        + "    float evShift = 0.0;\n"
        + "    float evSplit = 0.0;\n"
        // Bell tear: one band 4.5 rows tall at a y from the seed, shifted sideways (direction
        // from the seed too) and split red/blue, both fading out. The ambient tear (6) is the
        // same band at half the shift and a 1 px split.
        + "    if ((uEvent > 0.5 && uEvent < 1.5) || (uEvent > 5.5 && uEvent < 6.5)) {\n"
        + "        float amb = uEvent > 5.5 ? 1.0 : 0.0;\n"
        + "        float bandH = 4.5 * " + PERIOD + ";\n"
        + "        float bandY = uScreen.y + fract(uSeed * 7.13) * max(scr.y - bandH, 0.0);\n"
        + "        float inBand = step(bandY, p.y) * step(p.y, bandY + bandH);\n"
        + "        float fade = 1.0 - uEventT;\n"
        + "        float dir = uSeed < 0.5 ? -1.0 : 1.0;\n"
        + "        evShift = inBand * dir * mix(6.0, 3.0, amb) * uDensity * fade;\n"
        + "        evSplit = inBand * mix(1.5, 1.0, amb) * fade;\n"
        + "    }\n"
        // Focus wobble: a damped sideways shear, nothing on the centre row, full at the edges.
        + "    if (uEvent > 3.5 && uEvent < 4.5) {\n"
        + "        float d = clamp(abs(p.y - cy) / (scr.y * 0.5), 0.0, 1.0);\n"
        + "        evShift = 4.0 * uDensity * sin(2.0 * PI * 3.0 * uEventT) * (1.0 - uEventT) * d;\n"
        + "    }\n"
        + "    at = clamp(at + float2(evShift, 0.0), inMin, inMax);\n"
        + "    float gx = " + CHROMA_SIDE_DP + " * uDensity;\n"
        + "    float gy = " + CHROMA_UP_DP + " * uDensity;\n"
        + "    half4 col = content.eval(at);\n"
        // Chromatic aberration: red and blue read a little outward and inward along the bent
        // radius, 0 at the centre and uAberr px at the edge; a constant sideways split when flat.
        // A bell tear adds its own sideways split inside the band.
        + "    float2 split = mix(w, float2(1.0, 0.0), uAberrFlat) * uAberr + float2(evSplit, 0.0);\n"
        + "    col.r = content.eval(clamp(at + split, inMin, inMax)).r;\n"
        + "    col.b = content.eval(clamp(at - split, inMin, inMax)).b;\n"
        + "    float3 gl = float3(content.eval(clamp(at + float2(gx, 0.0), inMin, inMax)).rgb);\n"
        + "    float3 gr = float3(content.eval(clamp(at - float2(gx, 0.0), inMin, inMax)).rgb);\n"
        + "    float3 glow = (gl + gr\n"
        + "                + float3(content.eval(clamp(at + float2(0.0, gy), inMin, inMax)).rgb)\n"
        + "                + float3(content.eval(clamp(at - float2(0.0, gy), inMin, inMax)).rgb)) * 0.25;\n"
        + "    float a = float(col.a);\n"
        // Sharpen (SHARPEN): a 1 px unsharp mask on the bent screen only, where every sample
        // lands between two of kitty's pixels; the chrome's samples are exact and get none. Off
        // under the composite signal, which blends the same neighbours instead.
        + "    float k = " + SHARPEN + " * step(0.001, uBend) * (1.0 - uComposite);\n"
        + "    float3 nl = float3(content.eval(clamp(at - float2(1.0, 0.0), inMin, inMax)).rgb);\n"
        + "    float3 nr = float3(content.eval(clamp(at + float2(1.0, 0.0), inMin, inMax)).rgb);\n"
        + "    float3 nb = nl + nr\n"
        + "              + float3(content.eval(clamp(at + float2(0.0, 1.0), inMin, inMax)).rgb)\n"
        + "              + float3(content.eval(clamp(at - float2(0.0, 1.0), inMin, inMax)).rgb);\n"
        + "    float3 c0 = float3(col.rgb);\n"
        + "    float3 rgb = clamp(c0 * (1.0 + 4.0 * k) - k * nb, 0.0, a);\n"
        // The composite cable: luma keeps its bandwidth (or, under uComposite, a [1 2 1] blend
        // over the row) and the chroma is the mean of the taps along the row, so colour smears
        // sideways and a dither fuses into its shade. Before the tint, which sees the blend.
        + "    float3 lw = float3(0.299, 0.587, 0.114);\n"
        + "    float3 avg3 = (c0 + gl + gr) * (1.0 / 3.0);\n"
        + "    float3 avg5 = (c0 + gl + gr + nl + nr) * 0.2;\n"
        + "    float3 avg = mix(avg3, avg5, uComposite);\n"
        + "    float yPic = mix(dot(rgb, lw), dot(0.25 * nl + 0.5 * c0 + 0.25 * nr, lw), uComposite);\n"
        + "    rgb = clamp(float3(yPic) + avg - float3(dot(avg, lw)), 0.0, a);\n"
        // The glow blooms from bright pixels only (BLOOM_FLOOR), so it haloes the glyphs
        // without lifting their anti-aliased edges into the gaps between them; a pixel near white
        // blooms a little harder (BLOOM_PEAK_EXTRA).
        + "    float3 bloom = max(glow - " + BLOOM_FLOOR + ", 0.0) / (1.0 - " + BLOOM_FLOOR + ");\n"
        + "    float peak = smoothstep(" + BLOOM_PEAK_FROM + ", " + BLOOM_PEAK_TO + ", dot(rgb, lw));\n"
        + "    rgb = rgb + (" + BLOOM_WEIGHT + " + " + BLOOM_PEAK_EXTRA + " * peak) * bloom;\n"
        + "    float lum = dot(rgb, lw);\n"
        // The phosphor tint. With uTintKeep, a pixel keeps its own colour in proportion to how
        // much it has (its HSV saturation), so the greys and whites glow in the phosphor while
        // a prompt or an error keeps the colour that tells it apart.
        + "    float mx = max(rgb.r, max(rgb.g, rgb.b));\n"
        + "    float sat = (mx - min(rgb.r, min(rgb.g, rgb.b))) / max(mx, 0.001);\n"
        + "    float tw = float(uTint.a) * (1.0 - uTintKeep * smoothstep(0.15, 0.5, sat));\n"
        + "    rgb = mix(rgb, lum * float3(uTint.rgb) * 1.3, tw);\n"
        // The screen's own rows, straight, as kitty draws them: the bend moves the picture, never
        // the scanlines, so they run on into the surface beside this one.
        // A bright pixel's beam spills into the gap, so its rows dip less (SCAN_FLOOR_*).
        + "    float scanFloor = mix(" + SCAN_FLOOR_DARK + ", " + SCAN_FLOOR_BRIGHT + ", smoothstep("
        + SCAN_BRIGHT_FROM + ", " + SCAN_BRIGHT_TO + ", lum));\n"
        + "    float scan = mix(scanFloor, 1.0, 0.5 + 0.5 * cos(2.0 * PI * (p.y + uOrigin.y) / " + PERIOD + "));\n"
        // Vignette over the picture's own space: full across its middle third, uVignette lost at
        // its edge, steep on the way (after sam1am/termux_crt's uv(1-uv) curve).
        + "    float2 uv = w * 0.5 + 0.5;\n"
        + "    float2 vv = uv * (1.0 - uv);\n"
        + "    float vig = mix(1.0 - uVignette, 1.0, pow(clamp(vv.x * vv.y * 18.0, 0.0, 1.0), 0.4));\n"
        // Phosphor triad: vertical R, G, B stripes over the period, each channel full in its own
        // third and TRIAD_DEPTH down elsewhere; on the screen's columns, like the scanlines.
        + "    float tx = fract((p.x + uOrigin.x) / " + PERIOD + ");\n"
        + "    float3 third = float3(1.0 - step(1.0 / 3.0, tx),\n"
        + "                          step(1.0 / 3.0, tx) * (1.0 - step(2.0 / 3.0, tx)),\n"
        + "                          step(2.0 / 3.0, tx));\n"
        + "    float3 triad = (1.0 - " + TRIAD_DEPTH + ") + " + TRIAD_DEPTH + " * third;\n"
        // The glass sits in the bezel: an inner shadow darkest at the lip, gone RECESS_DP in.
        + "    float inside = -roundDist(p, uScreen, uScreenRadius);\n"
        + "    float recess = uRecess * " + RECESS_SHADE + "\n"
        + "        * (1.0 - smoothstep(0.0, " + RECESS_DP + " * uDensity, inside));\n"
        // One fixed specular band across the screen's top-left to bottom-right diagonal.
        + "    float2 diag = max(uScreen.zw - uScreen.xy, float2(1.0, 1.0));\n"
        + "    float along = dot(p - uScreen.xy, diag) / dot(diag, diag);\n"
        + "    float off = (along - " + SHEEN_AT + ") / " + SHEEN_SIGMA + ";\n"
        + "    float sheen = uRecess * " + SHEEN_PEAK + " * exp(-0.5 * off * off);\n"
        // Ambient flicker: two slow sines (1.3 Hz and 0.37 Hz), never a flash.
        + "    float flick = sin(uTime * 2.0 * PI * 1.3) * 0.6 + sin(uTime * 2.0 * PI * 0.37 + 1.7) * 0.4;\n"
        + "    rgb *= 1.0 - uFlicker * (0.5 + 0.5 * flick);\n"
        + "    float dimLum = dot(rgb, float3(0.299, 0.587, 0.114));\n"
        + "    rgb = mix(rgb, float3(dimLum), " + DIM_SATURATION + " * uDim)\n"
        + "        * (1.0 - " + DIM_BRIGHTNESS + " * uDim);\n"
        // Death snow: grey hash noise in 1.5 px grains, new every frame, fading in over the
        // first fifth and then holding; under the scanlines and the mask like any picture.
        + "    if (uEvent > 4.5) {\n"
        + "        float2 grain = floor(p / 1.5) + float2(uSeed * 97.0, floor(uEventT * 18.0) * 13.0);\n"
        + "        float n = fract(sin(dot(grain, float2(12.9898, 78.233))) * 43758.5453);\n"
        + "        rgb = mix(rgb, float3(n) * a, smoothstep(0.0, 0.2, uEventT));\n"
        + "    }\n"
        // The lit phosphor's haze at the middle of the picture, in the tint's colour (grey-white
        // on the plain CRT); the glass's glow pass stays clamped to the picture's own alpha.
        + "    float3 haze = mix(float3(1.0, 1.0, 1.0), float3(uTint.rgb), float(uTint.a));\n"
        + "    rgb = rgb * scan * vig * triad + haze * " + AMBIENT + " * exp(-6.0 * dot(c, c)) * a;\n"
        // Outside the picture's barrel outline the tube face shows, dark and nearly opaque, with
        // no scanlines or mask; the glass's recess and sheen lie over both.
        + "    rgb = mix(float3(" + TUBE_RGB + ") * " + TUBE_ALPHA + ", rgb, pic);\n"
        + "    a = mix(" + TUBE_ALPHA + ", a, pic);\n"
        + "    rgb = clamp(rgb * (1.0 - recess) + sheen * a, 0.0, a);\n"
        // Power on: a bright one-row line at the centre opening to the whole screen over the
        // first 60 %, the picture 1.6x bright fading to 1; black outside the open part.
        + "    if (uEvent > 1.5 && uEvent < 2.5) {\n"
        + "        float open = smoothstep(0.0, 0.6, uEventT);\n"
        + "        float halfH = max(scr.y * 0.5 * open, 0.5 * " + PERIOD + ");\n"
        + "        float inside = 1.0 - smoothstep(halfH - 0.5, halfH + 0.5, abs(p.y - cy));\n"
        + "        float line = (1.0 - smoothstep(0.0, " + PERIOD + ", abs(p.y - cy))) * (1.0 - open);\n"
        + "        rgb = clamp(rgb * mix(1.6, 1.0, uEventT) * inside + line * a, 0.0, a);\n"
        + "    }\n"
        // Power off: the picture squashes into a bright line over the first 70 %, and the line
        // fades to nothing over the last 30 %.
        + "    if (uEvent > 2.5 && uEvent < 3.5) {\n"
        + "        float shut = smoothstep(0.0, 0.7, uEventT);\n"
        + "        float fadeOut = 1.0 - smoothstep(0.7, 1.0, uEventT);\n"
        + "        float halfH = max(scr.y * 0.5 * (1.0 - shut), 0.5 * " + PERIOD + ");\n"
        + "        float inside = 1.0 - smoothstep(halfH - 0.5, halfH + 0.5, abs(p.y - cy));\n"
        + "        float line = (1.0 - smoothstep(0.0, " + PERIOD + ", abs(p.y - cy))) * shut;\n"
        + "        rgb = clamp((rgb * (1.0 + 0.6 * shut) * inside + line * a) * fadeOut, 0.0, a);\n"
        + "    }\n"
        + "    half4 fx = half4(half3(rgb), half(a));\n"
        + "    return mix(plain, fx, half(s)) * half(m);\n"
        + "}\n";

    static final String TFT_AGSL = HEAD
        + "half4 main(float2 p) {\n"
        + "    float m = cardMask(p);\n"
        + "    float s = roundMask(p, uScreen, uScreenRadius);\n"
        + "    half4 col = content.eval(p);\n"
        + "    float2 f = fract((p + uOrigin) / " + PERIOD + ");\n"
        + "    float gap = max(step(f.x, 0.22), step(f.y, 0.22));\n"
        // Inside the screen only: the bezel is drawn as it is.
        + "    float shade = 1.0 - 0.45 * gap * s;\n"
        + "    return half4(col.rgb * half(shade * m), col.a * half(m));\n"
        + "}\n";

    private Program mProgram;
    private boolean mFailed;

    /** True on phones that run an AGSL shader at all. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean available() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /**
     * The effect for {@code style} drawn with {@code uniforms}, or null for no effect (NONE,
     * unsupported phone, empty size, or a shader that failed to compile).
     */
    @Nullable
    public RenderEffect effectFor(@NonNull PaneRetroStyle style, @NonNull RetroUniforms uniforms) {
        if (style == PaneRetroStyle.NONE || !available() || mFailed
            || uniforms.width <= 0f || uniforms.height <= 0f)
            return null;
        android.os.Trace.beginSection("Effect.retro.build");
        try {
            if (mProgram == null) mProgram = new Program();
            return mProgram.build(style, uniforms);
        } catch (RuntimeException e) {
            mFailed = true;
            Logger.logError(LOG_TAG, "retro shader failed, effect disabled: " + e);
            return null;
        } finally {
            android.os.Trace.endSection();
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Program {
        private RuntimeShader mCrt;
        private RuntimeShader mTft;

        RenderEffect build(PaneRetroStyle style, RetroUniforms u) {
            RuntimeShader shader;
            if (style.usesCrt()) {
                if (mCrt == null) mCrt = new RuntimeShader(CRT_AGSL);
                shader = mCrt;
            } else {
                if (mTft == null) mTft = new RuntimeShader(TFT_AGSL);
                shader = mTft;
            }
            float[] tint = style.tint();
            shader.setFloatUniform("uSize", u.width, u.height);
            shader.setFloatUniform("uOrigin", u.phaseX, u.phaseY);
            shader.setFloatUniform("uDensity", u.density);
            shader.setFloatUniform("uCard", u.cardLeft, u.cardTop, u.cardRight, u.cardBottom);
            shader.setFloatUniform("uRadius", u.radius);
            shader.setFloatUniform("uTint", tint[0], tint[1], tint[2], tint[3]);
            shader.setFloatUniform("uTintKeep", style.keepsColour() ? 1f : 0f);
            shader.setFloatUniform("uBend", u.bend);
            shader.setFloatUniform("uVignette", u.vignette);
            shader.setFloatUniform("uScreen", u.screenLeft, u.screenTop, u.screenRight,
                u.screenBottom);
            shader.setFloatUniform("uScreenRadius", u.screenRadius);
            shader.setFloatUniform("uBendBox", u.bendLeft, u.bendTop, u.bendRight, u.bendBottom);
            shader.setFloatUniform("uAberr", u.aberr);
            shader.setFloatUniform("uAberrFlat", u.aberrFlat);
            shader.setFloatUniform("uRecess", u.recess);
            shader.setFloatUniform("uDim", u.dim);
            shader.setFloatUniform("uComposite", u.composite);
            shader.setFloatUniform("uEvent", u.event);
            shader.setFloatUniform("uEventT", u.eventT);
            shader.setFloatUniform("uSeed", u.seed);
            shader.setFloatUniform("uFlicker", u.flicker);
            shader.setFloatUniform("uTime", u.time);
            // The effect keeps the uniforms it was made with; the next write starts a new copy.
            return RenderEffect.createRuntimeShaderEffect(shader, "content");
        }
    }
}
