package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.DebugSwitch;

import java.util.Locale;

/**
 * The glass rim's numbers: one thin, crisp line along the outline that the program
 * ({@link GlassRefraction}) draws on every glass surface. The line both darkens what is behind it
 * (the dark line) and adds white (the light line), lit in linear light, so it adds no colour of
 * its own: a baseline of white ({@link #base}) and of dark ({@link #darkAll}) that every side and
 * corner keeps alike, so no side vanishes under any page transition, and the dark deepest facing
 * away from up and to the left. The white a key light adds facing it ({@link #spec}) ships at 0:
 * the glass has no fixed light, so the line is even all round; a finger still lights it.
 *
 * <p>The line's profile across the depth (BorderBeam's): whole from the outline to
 * {@link #lineDp} in, then off on a smoothstep over {@link #softDp}, plus a short halo of
 * {@link #halo} that fades with an e-fold of {@link #haloDp} ({@link #weight}). On pong
 * (420 dpi) the defaults give a line about four px wide with a faint halo two or three px
 * further.</p>
 *
 * <p>Tuning: the system property {@value #PROP} overrides any of the numbers, as comma-separated
 * {@code key=value} pairs, for example
 * {@code adb shell setprop debug.tl.glass_rim 'line=0.4,soft=0.8,base=0.12'}. Keys: {@code line},
 * {@code soft}, {@code halo_dp} (dp, 0..4; {@code soft} and {@code halo_dp} above 0), {@code halo},
 * {@code base}, {@code spec}, {@code dark}, {@code dark_all} (shares, 0..1). A key not named keeps
 * its default. Any unknown key, unreadable number or value out of range makes the whole value
 * fall back to the defaults. The property is read once, when the first glass program is built
 * ({@link #active}), so a new value takes effect after the launcher restarts
 * ({@code am force-stop}). Unset or empty is the defaults. The program writes the numbers in as
 * constants, so tuning costs nothing per frame.</p>
 */
public final class GlassRim {

    /** The debug system property that overrides the numbers; format in the class comment. */
    public static final String PROP = "debug.tl.glass_rim";

    /** Where the line is whole to, dp in from the outline. {@code kRimLineDp}. */
    static final float LINE_DP = 0.5f;
    /** How far past {@link #LINE_DP} the line fades to nothing, dp. {@code kRimSoftDp}. */
    static final float SOFT_DP = 1f;
    /** The halo's share at the line's inner edge. {@code kRimHalo}. */
    static final float HALO = 0.25f;
    /** The halo's e-fold, dp. {@code kRimHaloDp}. */
    static final float HALO_DP = 0.75f;
    /** The white every side keeps, linear. {@code kRimBase}. */
    static final float BASE = 0.18f;
    /**
     * The white a key light adds where the outline faces it squarely, linear: 0, no fixed light.
     * It also lights the dock shelf's lip. A tuning key. {@code kRimSpec}.
     */
    static final float SPEC = 0f;
    /** The dark line's darkening facing straight away from the key light. {@code kRimDark}. */
    static final float DARK = 0.3f;
    /** The share of {@link #DARK} every side keeps. {@code kRimDarkAll}. */
    static final float DARK_ALL = 0.35f;

    /** The narrowest the whole part gets on a low-density screen, px. {@code kRimMinLinePx}. */
    static final float MIN_LINE_PX = 0.75f;
    /** The narrowest the fade gets, px, so the line is always anti-aliased. {@code kRimMinSoftPx}. */
    static final float MIN_SOFT_PX = 1f;
    /** The most any width key may ask, dp. */
    static final float MAX_DP = 4f;

    /** The shipped numbers. */
    @NonNull public static final GlassRim DEFAULT =
        new GlassRim(LINE_DP, SOFT_DP, HALO, HALO_DP, BASE, SPEC, DARK, DARK_ALL);

    public final float lineDp;
    public final float softDp;
    public final float halo;
    public final float haloDp;
    public final float base;
    public final float spec;
    public final float dark;
    public final float darkAll;

    GlassRim(float lineDp, float softDp, float halo, float haloDp, float base, float spec,
             float dark, float darkAll) {
        this.lineDp = lineDp;
        this.softDp = softDp;
        this.halo = halo;
        this.haloDp = haloDp;
        this.base = base;
        this.spec = spec;
        this.dark = dark;
        this.darkAll = darkAll;
    }

    @Nullable private static volatile GlassRim sActive;

    /**
     * The numbers the glass programs are built with: {@value #PROP} parsed, read once per
     * process; the defaults when unset, unreadable or malformed.
     */
    @NonNull
    public static GlassRim active() {
        GlassRim rim = sActive;
        if (rim == null) {
            rim = parse(DebugSwitch.SYSTEM_PROPERTIES.read(PROP));
            sActive = rim;
        }
        return rim;
    }

    /**
     * A property value as numbers: {@link #DEFAULT} for null or blank, and for any unknown key,
     * unreadable number or value out of range.
     */
    @NonNull
    public static GlassRim parse(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return DEFAULT;
        float line = LINE_DP, soft = SOFT_DP, halo = HALO, haloDp = HALO_DP;
        float base = BASE, spec = SPEC, dark = DARK, darkAll = DARK_ALL;
        for (String pair : value.split(",")) {
            String p = pair.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) return DEFAULT;
            String key = p.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            float v;
            try {
                v = Float.parseFloat(p.substring(eq + 1).trim());
            } catch (NumberFormatException e) {
                return DEFAULT;
            }
            if (Float.isNaN(v) || Float.isInfinite(v)) return DEFAULT;
            switch (key) {
                case "line": if (!inDp(v, false)) return DEFAULT; line = v; break;
                case "soft": if (!inDp(v, true)) return DEFAULT; soft = v; break;
                case "halo_dp": if (!inDp(v, true)) return DEFAULT; haloDp = v; break;
                case "halo": if (!inShare(v)) return DEFAULT; halo = v; break;
                case "base": if (!inShare(v)) return DEFAULT; base = v; break;
                case "spec": if (!inShare(v)) return DEFAULT; spec = v; break;
                case "dark": if (!inShare(v)) return DEFAULT; dark = v; break;
                case "dark_all": if (!inShare(v)) return DEFAULT; darkAll = v; break;
                default: return DEFAULT;
            }
        }
        GlassRim rim = new GlassRim(line, soft, halo, haloDp, base, spec, dark, darkAll);
        return rim.equals(DEFAULT) ? DEFAULT : rim;
    }

    private static boolean inDp(float v, boolean positive) {
        return (positive ? v > 0f : v >= 0f) && v <= MAX_DP;
    }

    private static boolean inShare(float v) {
        return v >= 0f && v <= 1f;
    }

    /** Where the line is whole to, px; never under {@link #MIN_LINE_PX}. */
    public float corePx(float density) {
        return Math.max(lineDp * density, MIN_LINE_PX);
    }

    /** The line's fade, px; never under {@link #MIN_SOFT_PX}. */
    public float softPx(float density) {
        return Math.max(softDp * density, MIN_SOFT_PX);
    }

    /** The halo's e-fold, px; never under a hundredth of a px. */
    public float haloPx(float density) {
        return Math.max(haloDp * density, 0.01f);
    }

    /**
     * How many halo e-folds past the line's inner edge the halo is still worth an 8-bit step
     * ({@code kRimHaloSpan}): {@code ln(255 halo)}, 0 for a halo under a step.
     */
    public float haloSpan() {
        return (float) Math.max(0.0, Math.log(255.0 * halo));
    }

    /**
     * How far in from the outline the line reaches at all, px: past its whole part, the further
     * of its fade and the halo's {@link #haloSpan}. Pixels further in skip the term.
     */
    public float reachPx(float density) {
        return corePx(density) + Math.max(softPx(density), haloPx(density) * haloSpan());
    }

    /**
     * The line's weight {@code insidePx} in from the outline, 0..1: whole up to
     * {@link #corePx}, off on a smoothstep over {@link #softPx}, plus the halo; 0 from
     * {@link #reachPx}. The program's {@code lineW}.
     */
    public float weight(float insidePx, float density) {
        if (insidePx >= reachPx(density)) return 0f;
        float core = corePx(density);
        float soft = softPx(density);
        float t = Math.max(0f, Math.min(1f, (insidePx - core) / soft));
        float line = 1f - t * t * (3f - 2f * t);
        float glow = halo * (float) Math.exp(-Math.max(insidePx - core, 0f) / haloPx(density));
        return Math.min(1f, line + glow);
    }

    /**
     * The white an outline adds, linear, where the key light's lobe is {@code lobe} (0 facing
     * away, 1 facing it squarely; more under a live glint): {@link #base} all round plus
     * {@link #spec} of the lobe.
     */
    public float white(float lobe) {
        return base + spec * Math.max(lobe, 0f);
    }

    /**
     * The darkening an outline takes, where it faces away from the key light by {@code away}
     * (the squared cosine facing away, 0..1): {@link #darkAll} of {@link #dark} all round, all of
     * it facing straight away.
     */
    public float darkening(float away) {
        return dark * (darkAll + (1f - darkAll) * Math.max(0f, Math.min(1f, away)));
    }

    /** The sRGB transfer function: an encoded channel to linear light ({@code toLinearSrgb}). */
    static float toLinear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    /** Linear light to an encoded sRGB channel ({@code fromLinearSrgb}). */
    static float fromLinear(float l) {
        return l <= 0.0031308f ? l * 12.92f : (float) (1.055 * Math.pow(l, 1.0 / 2.4) - 0.055);
    }

    /**
     * One premultiplied pixel {@code (r, g, b, a)} under the line, into {@code out}: unpremultiplied,
     * taken to linear light, darkened by {@code darkening} and given {@code white} at line weight
     * {@code w}, held at 1, back to encoded light and premultiplied again. The same white on every
     * channel, so the line adds no colour; every channel stays under the alpha. The program's rim
     * block.
     */
    static void lit(float r, float g, float b, float a, float w, float white, float darkening,
                    @NonNull float[] out) {
        if (!(a > 0f) || !(w > 0f)) {
            out[0] = r; out[1] = g; out[2] = b;
            return;
        }
        float keep = 1f - darkening * w, add = white * w;
        out[0] = fromLinear(Math.min(1f, toLinear(r / a) * keep + add)) * a;
        out[1] = fromLinear(Math.min(1f, toLinear(g / a) * keep + add)) * a;
        out[2] = fromLinear(Math.min(1f, toLinear(b / a) * keep + add)) * a;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof GlassRim)) return false;
        GlassRim t = (GlassRim) o;
        return lineDp == t.lineDp && softDp == t.softDp && halo == t.halo && haloDp == t.haloDp
            && base == t.base && spec == t.spec && dark == t.dark && darkAll == t.darkAll;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(lineDp);
        h = h * 31 + Float.floatToIntBits(softDp);
        h = h * 31 + Float.floatToIntBits(halo);
        h = h * 31 + Float.floatToIntBits(haloDp);
        h = h * 31 + Float.floatToIntBits(base);
        h = h * 31 + Float.floatToIntBits(spec);
        h = h * 31 + Float.floatToIntBits(dark);
        return h * 31 + Float.floatToIntBits(darkAll);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "line=%s,soft=%s,halo=%s,halo_dp=%s,base=%s,spec=%s,dark=%s,dark_all=%s",
            lineDp, softDp, halo, haloDp, base, spec, dark, darkAll);
    }
}
