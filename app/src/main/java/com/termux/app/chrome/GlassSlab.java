package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.DebugSwitch;

import java.util.Locale;

/**
 * The glass slab's numbers and its optics: every glass surface the program
 * ({@link GlassRefraction}) draws is a thick rounded slab, flat in the middle and curving down to
 * its outline across a lens zone, the same everywhere. These are the Java mirrors of the
 * program's lens, one function per expression, and the numbers it is built with.
 *
 * <ul>
 * <li><b>Outline and depth.</b> One rounded-box distance field on the drawn corner radius (the
 * clip outline's) gives how far a pixel is in from the outline; the lens zone is the outermost
 * {@link #lensWidthPx W} of it.</li>
 * <li><b>Profile.</b> Across the zone the slab's height is a superellipse,
 * {@code h(e) = T (1 - (1 - e)^p)^(1/p)} with {@code e} 0 at the outline and 1 where the zone
 * meets the face ({@link #profile}). It is convex, meets the flat face with no step in its slope
 * (C1) and stands vertical at the outline. Its slope ({@link #profileSlope}) times the distance
 * field's gradient is the surface's normal.</li>
 * <li><b>Refraction.</b> A ray straight down from the eye bends at the sloped face by Snell's law
 * and travels down through the slab's thickness {@code T}; where it lands, measured inward from
 * the pixel, is {@code T tan(θi - θt)} ({@link #snellTan}, {@link #shiftPx}). It is 0 on the
 * flat face and never past {@code T sqrt(n² - 1)} at the outline, so it needs no cap. No colour
 * gain: the picture is moved, never brightened.</li>
 * <li><b>Size.</b> {@code W} is {@link #lensDp} on large surfaces and {@link #share} of the
 * short side on small ones (chips, pills, keys, beads), and the thickness shrinks with it, so a
 * small piece is the same slab scaled down and stays readable ({@link #thicknessPx}).</li>
 * <li><b>Light.</b> In linear light: the picture let through times {@code 1 - F}, plus
 * {@code F} of a faint sky tone ({@link #env}), Schlick's {@code F} from the slab's normal and
 * the view ({@link #fresnel}); and the wallpaper around the surface: the zone reflects the frost
 * a little past its outline, mirrored out across it along the field's gradient
 * ({@link #envTapPx}), weighted by {@link #envWeight} and {@link #reflect}, so a bright patch next
 * to a surface brings its own colour into that edge and a dark one brings nothing. It reflects,
 * it never multiplies the picture beneath. There is no fixed light: a white specular from a room
 * light up and to the left ({@link GlassRefraction#KEY_LIGHT_X}, Blinn-Phong on the slab's
 * normal, {@link #specular}) stays only as a tuning key and ships at 0. The thin rim ({@link GlassRim}) stays the visible outline; Fresnel
 * stands back where the rim line is drawn, so the outline is lit once.</li>
 * <li><b>Dispersion.</b> Only where the look's Edge asks: red and blue refract at their own
 * index ({@link #iorRed}, {@link #iorBlue}), so the split follows the bend, equal on straights
 * and round corners and nothing on the face.</li>
 * </ul>
 *
 * <p>Tuning: the system property {@value #PROP} overrides any of the numbers, as comma-separated
 * {@code key=value} pairs in {@link GlassRim}'s format, for example
 * {@code adb shell setprop debug.tl.glass_slab 'lens=20,ior=1.6,reflect=3'}. Keys: {@code lens}
 * (dp, 0..48), {@code share} (0..0.5), {@code ior} (1..2.5), {@code f0} (0..1), {@code env}
 * (0..1), {@code spec} (0..2), {@code power} (1..512), {@code profile} (1.1..8), {@code disp}
 * (0..10), {@code reflect} (0..8). A key not named keeps its default; any unknown key, unreadable number or value out of
 * range makes the whole value fall back to the defaults. Read once, when the first glass program
 * is built ({@link #active}), and baked into the program as constants, so a new value takes effect
 * after the launcher restarts and tuning costs nothing per frame.</p>
 */
public final class GlassSlab {

    /** The debug system property that overrides the numbers; format in the class comment. */
    public static final String PROP = "debug.tl.glass_slab";

    /** The lens zone's width on a large surface, dp: wide, so the eased profile meets the face gently. {@code kLensDp}. */
    static final float LENS_DP = 32f;
    /** The lens zone's width on a small piece, as a share of its short side. {@code kLensShare}. */
    static final float SHARE = 0.25f;
    /** Glass's index of refraction. {@code kIor}. */
    static final float IOR = 1.7f;
    /** Glass's reflectance head-on, Schlick's F0: {@code ((n - 1) / (n + 1))²} at 1.5. {@code kF0}. */
    static final float F0 = 0.04f;
    /**
     * The faint sky tone the slab reflects under the wallpaper it reflects, linear white: a floor,
     * low so the zone does not read as a plain grey border. {@code kEnv}.
     */
    static final float ENV = 0.1f;
    /**
     * A room light's white at the specular's peak, linear: 0, the glass has no fixed light (it
     * drew a milky line under every top border). A tuning key only; the program skips it at 0.
     */
    static final float SPEC = 0f;
    /** The specular's Blinn-Phong exponent: wide enough to run along the lit edges, not a corner spot. */
    static final float POWER = 24f;
    /**
     * The profile's superellipse exponent: 2 is a quarter ellipse, which met the face too
     * abruptly; 4 eases in across the zone and steepens only toward the outline. {@code kSlabP}.
     */
    static final float PROFILE = 4f;
    /** How far the indices of red and blue part from {@link #IOR} at Edge 100%, as a multiple of 1.49 / 1.52. */
    static final float DISP = 1f;
    /**
     * How much of the wallpaper outside the outline the zone reflects, as a multiple of what
     * Fresnel alone gives: 1 is physical, more lets the surroundings' light read past realism.
     * The app-wide Shine scales it. {@code kEnvK}.
     */
    static final float REFLECT = 2f;
    /** How far past the pixel's mirror across the outline the reflected patch lies, dp. {@code kEnvReachDp}. */
    static final float ENV_REACH_DP = 8f;
    /**
     * The share of the deepest bend ({@code T sqrt(n² - 1)}) by which the zone counts as wholly
     * steep ({@link #steepness}): from there out the zone takes its head-on reflectance of the
     * wallpaper outside; the nearly flat inner part takes none. {@code kBendFull}.
     */
    static final float BEND_FULL = 0.2f;

    /** How far red's index falls under {@link #ior} and blue's rises over it at Edge 100% and {@link #disp} 1. */
    static final float DISP_RED = 0.01f;
    static final float DISP_BLUE = 0.02f;
    /** How close to the outline the slope is read, as a share of the zone: it is vertical at 0. {@code kSlabEMin}. */
    static final float E_MIN = 1.0e-4f;

    /** The shipped numbers. */
    @NonNull public static final GlassSlab DEFAULT =
        new GlassSlab(LENS_DP, SHARE, IOR, F0, ENV, SPEC, POWER, PROFILE, DISP, REFLECT);

    public final float lensDp;
    public final float share;
    public final float ior;
    public final float f0;
    public final float env;
    public final float spec;
    public final float power;
    public final float profile;
    public final float disp;
    public final float reflect;

    GlassSlab(float lensDp, float share, float ior, float f0, float env, float spec, float power,
              float profile, float disp, float reflect) {
        this.lensDp = lensDp;
        this.share = share;
        this.ior = ior;
        this.f0 = f0;
        this.env = env;
        this.spec = spec;
        this.power = power;
        this.profile = profile;
        this.disp = disp;
        this.reflect = reflect;
    }

    @Nullable private static volatile GlassSlab sActive;

    /**
     * The numbers the glass programs are built with: {@value #PROP} parsed, read once per
     * process; the defaults when unset, unreadable or malformed.
     */
    @NonNull
    public static GlassSlab active() {
        GlassSlab slab = sActive;
        if (slab == null) {
            slab = parse(DebugSwitch.SYSTEM_PROPERTIES.read(PROP));
            sActive = slab;
        }
        return slab;
    }

    /**
     * A property value as numbers: {@link #DEFAULT} for null or blank, and for any unknown key,
     * unreadable number or value out of range.
     */
    @NonNull
    public static GlassSlab parse(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return DEFAULT;
        float lens = LENS_DP, share = SHARE, ior = IOR, f0 = F0, env = ENV, spec = SPEC;
        float power = POWER, profile = PROFILE, disp = DISP, reflect = REFLECT;
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
                case "lens": if (!in(v, 0f, 48f)) return DEFAULT; lens = v; break;
                case "share": if (!in(v, 0f, 0.5f)) return DEFAULT; share = v; break;
                case "ior": if (!in(v, 1f, 2.5f)) return DEFAULT; ior = v; break;
                case "f0": if (!in(v, 0f, 1f)) return DEFAULT; f0 = v; break;
                case "env": if (!in(v, 0f, 1f)) return DEFAULT; env = v; break;
                case "spec": if (!in(v, 0f, 2f)) return DEFAULT; spec = v; break;
                case "power": if (!in(v, 1f, 512f)) return DEFAULT; power = v; break;
                case "profile": if (!in(v, 1.1f, 8f)) return DEFAULT; profile = v; break;
                case "disp": if (!in(v, 0f, 10f)) return DEFAULT; disp = v; break;
                case "reflect": if (!in(v, 0f, 8f)) return DEFAULT; reflect = v; break;
                default: return DEFAULT;
            }
        }
        GlassSlab slab = new GlassSlab(lens, share, ior, f0, env, spec, power, profile, disp,
            reflect);
        return slab.equals(DEFAULT) ? DEFAULT : slab;
    }

    private static boolean in(float v, float min, float max) {
        return v >= min && v <= max;
    }

    // ------------------------------------------------------------------------- size

    /** The lens zone's width on a large surface, px. */
    public float lensPx(float density) {
        return Math.max(0f, lensDp * density);
    }

    /**
     * How much of a large surface's lens a piece whose short side is {@code shortSidePx} takes,
     * 0..1: its {@link #share} of the short side over {@link #lensPx}, whole on large surfaces.
     * The program's {@code sizeShare}.
     */
    public float sizeShare(float shortSidePx, float density) {
        float lens = lensPx(density);
        if (!(lens > 0f)) return 0f;
        return Math.max(0f, Math.min(1f, share * Math.max(0f, shortSidePx) / lens));
    }

    /**
     * {@code W}: the lens zone's width, px, on a surface whose short side is {@code shortSidePx}:
     * {@link #lensDp} on a large surface, {@link #share} of the short side on a small piece.
     */
    public float lensWidthPx(float shortSidePx, float density) {
        return lensPx(density) * sizeShare(shortSidePx, density);
    }

    /**
     * {@code T}: the slab's thickness, px, for the look's Depth {@code depthPx} on a surface whose
     * short side is {@code shortSidePx}: the Depth on a large surface, scaled with the lens on a
     * small piece ({@link #sizeShare}), so the slab keeps its shape and a chip's bend stays a
     * chip's. Held so the deepest bend ({@code T sqrt(n² - 1)}, at the outline) reaches the centre
     * line at most: past it a small piece would show its far edge's picture.
     */
    public float thicknessPx(float depthPx, float shortSidePx, float density) {
        float t = Math.max(0f, depthPx) * sizeShare(shortSidePx, density);
        return Math.min(t, holdPx(shortSidePx / 2f));
    }

    /** The thickest slab whose deepest bend stays inside {@code halfShortPx}. */
    float holdPx(float halfShortPx) {
        float k = (float) Math.sqrt(Math.max(0f, ior * ior - 1f));
        return k > 0f ? Math.max(0f, halfShortPx) / k : Float.MAX_VALUE;
    }

    /** The program's {@code kSlabHold}: {@code 1 / sqrt(n² - 1)}, the thickness hold per px of half short side. */
    float holdShare() {
        float k = (float) Math.sqrt(Math.max(0f, ior * ior - 1f));
        return k > 0f ? 1f / k : 1.0e6f;
    }

    // ------------------------------------------------------------------------- the profile

    /** The slab's height across the zone as a share of {@code T}, {@code e} 0 at the outline to 1 at the face. */
    static float profile(float e, float p) {
        float c = Math.max(0f, Math.min(1f, e));
        return (float) Math.pow(1.0 - Math.pow(1.0 - c, p), 1.0 / p);
    }

    /**
     * The profile's slope, {@code d(h/T)/de}: 0 at the face ({@code e} 1), rising without bound
     * toward the outline, read no closer than {@link #E_MIN}. The program's {@code slabSlope}.
     */
    static float profileSlope(float e, float p) {
        float c = Math.max(E_MIN, Math.min(1f, e));
        double u = 1.0 - c;
        if (u <= 0.0) return 0f;
        return (float) (Math.pow(u, p - 1.0) * Math.pow(1.0 - Math.pow(u, p), 1.0 / p - 1.0));
    }

    /**
     * How far a ray straight down leans after entering a face of slope {@code slope} (rise over
     * run) at index {@code n}, as a tangent: {@code tan(θi - θt)} by the tangent difference, with
     * {@code tan θi = slope}. 0 flat, rising to {@code sqrt(n² - 1)} as the face stands up. The
     * program's {@code snellTan}; LiquidGlass's {@code snellShift} (Apache-2.0).
     */
    static float snellTan(float slope, float n) {
        float s = Math.max(0f, slope);
        float si = s / (float) Math.sqrt(1f + s * s);
        float st = si / Math.max(n, 1f);
        float t2 = st / (float) Math.sqrt(Math.max(1e-12f, 1f - st * st));
        return (s - t2) / (1f + s * t2);
    }

    /** The slope, rise over run, of a slab {@code tPx} thick across a zone {@code wPx} wide, {@code insidePx} in. */
    float slopeAt(float insidePx, float wPx, float tPx) {
        if (!(wPx > 0f) || !(tPx > 0f) || insidePx >= wPx) return 0f;
        return tPx / wPx * profileSlope(Math.max(0f, insidePx) / wPx, profile);
    }

    /**
     * How far inward, px, a pixel {@code insidePx} in from the outline samples the picture under a
     * slab {@code tPx} thick with a lens zone {@code wPx} wide, at index {@code n}: the refracted
     * ray's travel across the thickness. 0 on the face; never past {@code T sqrt(n² - 1)}.
     */
    float shiftPx(float insidePx, float wPx, float tPx, float n) {
        float slope = slopeAt(insidePx, wPx, tPx);
        return slope > 0f ? tPx * snellTan(slope, n) : 0f;
    }

    /** {@link #shiftPx(float, float, float, float)} at the slab's own index. */
    public float shiftPx(float insidePx, float wPx, float tPx) {
        return shiftPx(insidePx, wPx, tPx, ior);
    }

    // ------------------------------------------------------------------------- dispersion

    /** Red's index at the look's dispersion share {@code dispersion} (0..1, Edge 100% is 1). */
    public float iorRed(float dispersion) {
        return ior - DISP_RED * disp * Math.max(0f, dispersion);
    }

    /** Blue's index; see {@link #iorRed}. */
    public float iorBlue(float dispersion) {
        return ior + DISP_BLUE * disp * Math.max(0f, dispersion);
    }

    // ------------------------------------------------------------------------- light

    /** Schlick's reflectance where the face's slope is {@code slope}, seen straight on. */
    public float fresnel(float slope) {
        float cv = 1f / (float) Math.sqrt(1f + slope * slope);
        float m = 1f - cv;
        return f0 + (1f - f0) * m * m * m * m * m;
    }

    /**
     * The half vector between the room light and the eye straight above, into {@code out}
     * ({@code x, y, z}): the light {@code (KEY_LIGHT_X, KEY_LIGHT_Y, KEY_LIGHT_RISE)}, the eye
     * {@code (0, 0, 1)}. The program's {@code kHalf}.
     */
    static void half(@NonNull float[] out) {
        float lx = GlassRefraction.KEY_LIGHT_X, ly = GlassRefraction.KEY_LIGHT_Y;
        float lz = GlassRefraction.KEY_LIGHT_RISE;
        float l = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        float hx = lx / l, hy = ly / l, hz = lz / l + 1f;
        float h = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        out[0] = hx / h;
        out[1] = hy / h;
        out[2] = hz / h;
    }

    /** What the specular's lobe is on the flat face, subtracted so it is exactly 0 there. {@code kSpecFloor}. */
    float specFloor() {
        float[] h = new float[3];
        half(h);
        return (float) Math.pow(h[2], power);
    }

    /**
     * The room light's white, linear, on the slab where its face slopes by {@code slope} toward
     * the outline whose outward normal is {@code (nx, ny)}: Blinn-Phong on the slab's normal
     * {@code normalize(n * slope, 1)}, less its value on the flat face, so the face takes none and
     * the light is all the lens zone's, brightest where the zone faces the light.
     */
    public float specular(float nx, float ny, float slope) {
        float[] h = new float[3];
        half(h);
        float cv = 1f / (float) Math.sqrt(1f + slope * slope);
        float nh = Math.max(0f, (nx * slope * h[0] + ny * slope * h[1] + h[2]) * cv);
        float floor = specFloor();
        return Math.max(0f, (float) Math.pow(nh, power) - floor) * spec / (1f - floor);
    }

    /**
     * How far the slab bends at slope {@code slope} as a share of its deepest bend, 0..1:
     * {@code tan(θi - θt) / sqrt(n² - 1)}, the refraction shift over its bound.
     */
    public float bendShare(float slope) {
        float k = (float) Math.sqrt(Math.max(0f, ior * ior - 1f));
        return k > 0f ? Math.min(1f, snellTan(slope, ior) / k) : 0f;
    }

    /**
     * How steep the zone is at slope {@code slope}, 0..1: a smoothstep of {@link #bendShare} up to
     * {@link #BEND_FULL}. Nothing where the zone meets the face, rising with no step (the bend
     * itself starts there with zero slope), whole across the steep outer part. The program's
     * {@code steepK}.
     */
    public float steepness(float slope) {
        float x = Math.max(0f, Math.min(1f, bendShare(slope) / BEND_FULL));
        return x * x * (3f - 2f * x);
    }

    /**
     * The share of the wallpaper outside the outline the zone reflects at slope {@code slope},
     * where the rim's line weighs {@code lineW}, before {@link #reflect}: Schlick's {@code F},
     * less its head-on {@link #f0} where the zone is barely steep ({@link #steepness}), so it is
     * nothing where the zone meets the face (no ring there) and strongest at the outline. It
     * stands back under the rim's line, as Fresnel does.
     */
    public float envWeight(float slope, float lineW) {
        float f = fresnel(slope) - f0 * (1f - steepness(slope));
        return Math.max(0f, f) * (1f - Math.max(0f, Math.min(1f, lineW)));
    }

    /**
     * How far out along the outline's normal, px, a pixel {@code insidePx} in reads the wallpaper
     * it reflects: its mirror across the outline, then {@link #ENV_REACH_DP} further.
     */
    public float envTapPx(float insidePx, float density) {
        return 2f * Math.max(0f, insidePx) + ENV_REACH_DP * density;
    }

    /**
     * One linear channel {@code lin} under the lens at slope {@code slope}, where the rim's line
     * weighs {@code lineW}: let through by {@code 1 - F}, reflecting {@code F} of the sky and the
     * wallpaper outside the outline ({@code outside}, linear, premultiplied) by
     * {@link #envWeight} times {@link #reflect} at Shine {@code shine}, and the white
     * {@code specular} added. Fresnel stands back under the rim's line, which already lights the
     * outline.
     */
    public float lit(float lin, float slope, float lineW, float specular, float outside,
                     float shine) {
        // The same share as the wallpaper's: no head-on F0 where the zone meets the face, which
        // takes none, so the two meet with no step.
        float f = envWeight(slope, lineW);
        float mirror = envWeight(slope, lineW) * reflect * Math.max(0f, shine)
            * Math.max(0f, outside);
        return Math.min(1f, lin * (1f - f) + f * env + mirror + specular);
    }

    /** {@link #lit(float, float, float, float, float, float)} with a black surround. */
    public float lit(float lin, float slope, float lineW, float specular) {
        return lit(lin, slope, lineW, specular, 0f, 1f);
    }

    // ------------------------------------------------------------------------- the program

    /** The slab's numbers as the program's constants. */
    @NonNull
    String constants() {
        float[] h = new float[3];
        half(h);
        float floor = specFloor();
        return "const float kLensDp = " + GlassRefraction.agslFloat(lensDp) + ";\n"
            + "const float kLensShare = " + GlassRefraction.agslFloat(share) + ";\n"
            + "const float kIor = " + GlassRefraction.agslFloat(ior) + ";\n"
            + "const float kSlabHold = " + GlassRefraction.agslFloat(holdShare()) + ";\n"
            + "const float kSlabP = " + GlassRefraction.agslFloat(profile) + ";\n"
            + "const float kSlabEMin = " + GlassRefraction.agslFloat(E_MIN) + ";\n"
            + "const float kF0 = " + GlassRefraction.agslFloat(f0) + ";\n"
            + "const float kEnv = " + GlassRefraction.agslFloat(env) + ";\n"
            + "const float kEnvK = " + GlassRefraction.agslFloat(reflect) + ";\n"
            + "const float kEnvReachDp = " + GlassRefraction.agslFloat(ENV_REACH_DP) + ";\n"
            + "const float kBendFull = " + GlassRefraction.agslFloat(BEND_FULL) + ";\n"
            + "const float kSpecPow = " + GlassRefraction.agslFloat(power) + ";\n"
            + "const float kSpecFloor = " + GlassRefraction.agslFloat(floor) + ";\n"
            + "const float kSpecK = " + GlassRefraction.agslFloat(spec / (1f - floor)) + ";\n"
            + "const float3 kHalf = float3(" + GlassRefraction.agslFloat(h[0]) + ", "
            + GlassRefraction.agslFloat(h[1]) + ", " + GlassRefraction.agslFloat(h[2]) + ");\n"
            + "const float kDispRed = " + GlassRefraction.agslFloat(DISP_RED * disp) + ";\n"
            + "const float kDispBlue = " + GlassRefraction.agslFloat(DISP_BLUE * disp) + ";\n";
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof GlassSlab)) return false;
        GlassSlab t = (GlassSlab) o;
        return lensDp == t.lensDp && share == t.share && ior == t.ior && f0 == t.f0
            && env == t.env && spec == t.spec && power == t.power && profile == t.profile
            && disp == t.disp && reflect == t.reflect;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(lensDp);
        h = h * 31 + Float.floatToIntBits(share);
        h = h * 31 + Float.floatToIntBits(ior);
        h = h * 31 + Float.floatToIntBits(f0);
        h = h * 31 + Float.floatToIntBits(env);
        h = h * 31 + Float.floatToIntBits(spec);
        h = h * 31 + Float.floatToIntBits(power);
        h = h * 31 + Float.floatToIntBits(profile);
        h = h * 31 + Float.floatToIntBits(disp);
        return h * 31 + Float.floatToIntBits(reflect);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "lens=%s,share=%s,ior=%s,f0=%s,env=%s,spec=%s,power=%s,profile=%s,disp=%s,reflect=%s",
            lensDp, share, ior, f0, env, spec, power, profile, disp, reflect);
    }
}
