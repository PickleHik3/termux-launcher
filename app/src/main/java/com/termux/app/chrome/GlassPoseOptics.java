package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.DebugSwitch;

import java.util.Locale;

/**
 * How a glass slab answers its page's pose ({@link GlassPose}): the turn-response numbers and the
 * optics that turn a pose into the program's pose uniforms ({@link #uniforms}). The slab itself
 * (thickness, index, reflectance, sky tone) is {@link GlassSlab}'s; nothing here is a second
 * optics model, only what a turn or a scale does to that slab.
 *
 * <ul>
 * <li><b>Slid or faded</b> (Slide, Fade): nothing. A plate moving in its own plane does not change
 * its optics; the picture beneath already drifts through the aim.</li>
 * <li><b>Turned</b> (Carousel, Stack's turn, the plank's tip, a press): the picture through the
 * face shifts by the slab's thickness times {@code tan θi - tan θt} ({@link #shiftTan}); the
 * bevel facing away meets the eye more steeply and takes more Schlick Fresnel light from the
 * slab's own normal (the program turns the eye in the slab's frame); the face's reflectance rises
 * from {@link GlassSlab#f0} toward about 9% at 60 degrees ({@link #faceReflectanceRise}, the
 * dielectric Fresnel equations), and the bevel going away sees the wallpaper further out
 * ({@link GlassRefraction}). A soft glint that crossed the face as its normal passed a fixed
 * light's reflection ({@link #glint}) stays as a tuning key and ships at 0: the glass has no fixed
 * light.</li>
 * <li><b>Scaled</b> (Depth's sink, Stack's rise): the picture is magnified a little with the
 * page's scale ({@link #magnify}). The bevel's width in px scales with the page by itself: the
 * slab draws in its own coordinates, under the page's transform.</li>
 * </ul>
 * Flat and unscaled every term is exactly nothing, so the rest look is the program's own.
 *
 * <p>Tuning: the system property {@value #PROP} overrides any number, as comma-separated
 * {@code key=value} pairs, for example
 * {@code adb shell setprop debug.tl.glass_pose 'shift=1.5,glint=0.15'}. Keys: {@code shift}
 * (the turn's shift as a share of the physical one, 0..4), {@code maxshift} (the most it moves
 * the picture, dp, 0..64), {@code fresnel} (the turn's Fresnel, bevel and face, as a share of the
 * physical one, 0..4), {@code glint} (the glint's peak white, linear, 0..1), {@code glintw} (its
 * half width across its travel, dp, 1..200), {@code onset} (the sine of turn by which it is whole,
 * 0.001..1), {@code view} (how far the viewer stands, dp, 50..5000), {@code spread} (the light's
 * horizontal reach over its height, 0..4), {@code zoom} (the magnification per unit of scale
 * change, 0..1). A key not named keeps its default; any unknown key, unreadable number or value
 * out of range makes the whole value fall back to the defaults. Read once per process
 * ({@link #active}); the numbers feed uniforms, so nothing is rebuilt per frame.</p>
 */
public final class GlassPoseOptics {

    /** The debug system property that overrides the numbers; format in the class comment. */
    public static final String PROP = "debug.tl.glass_pose";

    /** The turn's shift as a share of the physical {@code T (tan θi - tan θt)}. */
    static final float SHIFT = 1f;
    /** The most the turn shifts the picture, dp, near edge-on. */
    static final float MAX_SHIFT_DP = 24f;
    /** The turn's Fresnel, on the bevel and the face, as a share of the physical change. */
    static final float FRESNEL = 1f;
    /** The glint's white at its peak, linear: 0, no fixed light to glint. A tuning key. */
    static final float GLINT = 0f;
    /** The glint's half width across its travel, dp; three times that along its length. */
    static final float GLINT_WIDTH_DP = 36f;
    /** The sine of turn over which the glint fades in: none flat, all of it from about 4 degrees. */
    static final float ONSET = 0.07f;
    /** How far the viewer stands from the glass, dp: the glint's travel per degree of turn. */
    static final float VIEW_DP = 600f;
    /** The light's horizontal reach over its height: a light high above, up and to the left. */
    static final float SPREAD = 0.25f;
    /** How much of a scale change the picture through the glass is magnified by. */
    static final float ZOOM = 0.06f;
    /** The sine of turn the shift is read at most: past it the tangent runs off. */
    static final float MAX_SINE = 0.99f;

    /** The shipped numbers. */
    @NonNull public static final GlassPoseOptics DEFAULT = new GlassPoseOptics(SHIFT, MAX_SHIFT_DP,
        FRESNEL, GLINT, GLINT_WIDTH_DP, ONSET, VIEW_DP, SPREAD, ZOOM);

    public final float shift;
    public final float maxShiftDp;
    public final float fresnel;
    public final float glint;
    public final float glintWidthDp;
    public final float onset;
    public final float viewDp;
    public final float spread;
    public final float zoom;

    GlassPoseOptics(float shift, float maxShiftDp, float fresnel, float glint, float glintWidthDp,
                    float onset, float viewDp, float spread, float zoom) {
        this.shift = shift;
        this.maxShiftDp = maxShiftDp;
        this.fresnel = fresnel;
        this.glint = glint;
        this.glintWidthDp = glintWidthDp;
        this.onset = onset;
        this.viewDp = viewDp;
        this.spread = spread;
        this.zoom = zoom;
    }

    @Nullable private static volatile GlassPoseOptics sActive;

    /** The numbers every program answers poses with: {@value #PROP} parsed, read once per process. */
    @NonNull
    public static GlassPoseOptics active() {
        GlassPoseOptics optics = sActive;
        if (optics == null) {
            optics = parse(DebugSwitch.SYSTEM_PROPERTIES.read(PROP));
            sActive = optics;
        }
        return optics;
    }

    /**
     * A property value as numbers: {@link #DEFAULT} for null or blank, and for any unknown key,
     * unreadable number or value out of range.
     */
    @NonNull
    public static GlassPoseOptics parse(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return DEFAULT;
        float shift = SHIFT, maxShift = MAX_SHIFT_DP, fresnel = FRESNEL, glint = GLINT;
        float glintW = GLINT_WIDTH_DP, onset = ONSET, view = VIEW_DP, spread = SPREAD, zoom = ZOOM;
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
                case "shift": if (!in(v, 0f, 4f)) return DEFAULT; shift = v; break;
                case "maxshift": if (!in(v, 0f, 64f)) return DEFAULT; maxShift = v; break;
                case "fresnel": if (!in(v, 0f, 4f)) return DEFAULT; fresnel = v; break;
                case "glint": if (!in(v, 0f, 1f)) return DEFAULT; glint = v; break;
                case "glintw": if (!in(v, 1f, 200f)) return DEFAULT; glintW = v; break;
                case "onset": if (!in(v, 0.001f, 1f)) return DEFAULT; onset = v; break;
                case "view": if (!in(v, 50f, 5000f)) return DEFAULT; view = v; break;
                case "spread": if (!in(v, 0f, 4f)) return DEFAULT; spread = v; break;
                case "zoom": if (!in(v, 0f, 1f)) return DEFAULT; zoom = v; break;
                default: return DEFAULT;
            }
        }
        GlassPoseOptics optics = new GlassPoseOptics(shift, maxShift, fresnel, glint, glintW, onset,
            view, spread, zoom);
        return optics.equals(DEFAULT) ? DEFAULT : optics;
    }

    private static boolean in(float v, float min, float max) {
        return v >= min && v <= max;
    }

    // ------------------------------------------------------------------------- the turn

    /**
     * How far the picture shifts through the slab per px of its thickness at a turn of sine
     * {@code sine}, at index {@code ior}: {@code tan θi - tan θt}, Snell's law with {@code θi} the
     * turn, times {@link #shift}. 0 flat; read no further than {@link #MAX_SINE}.
     */
    public float shiftTan(float sine, float ior) {
        float s = Math.min(Math.abs(sine), MAX_SINE);
        if (!(s > 0f)) return 0f;
        double inc = Math.asin(s);
        double ref = Math.asin(s / Math.max(ior, 1f));
        return (float) ((Math.tan(inc) - Math.tan(ref)) * shift);
    }

    /** Schlick's reflectance at the cosine {@code cos} for a head-on {@code f0}. */
    static float schlick(float cos, float f0) {
        float m = 1f - Math.max(0f, Math.min(1f, cos));
        return f0 + (1f - f0) * m * m * m * m * m;
    }

    /**
     * A dielectric's reflectance for unpolarised light at the incidence whose cosine is
     * {@code cos}, index {@code ior}: the Fresnel equations, the mean of s and p. 4% head-on at
     * 1.5, 9% at 60 degrees, whole at grazing.
     */
    static float fresnelExact(float cos, float ior) {
        double ci = Math.max(0.0, Math.min(1.0, cos));
        double n = Math.max(ior, 1f);
        double si2 = 1.0 - ci * ci;
        double st2 = si2 / (n * n);
        if (st2 >= 1.0) return 1f;
        double ct = Math.sqrt(1.0 - st2);
        double rs = (ci - n * ct) / (ci + n * ct);
        double rp = (n * ci - ct) / (n * ci + ct);
        return (float) (0.5 * (rs * rs + rp * rp));
    }

    /**
     * How much the flat face's reflectance rises over head-on at a turn of sine {@code sine}, on a
     * slab of index {@code ior}, times {@link #fresnel}: the program lets the picture through by
     * one less this and reflects this of the sky. 0 flat.
     */
    public float faceReflectanceRise(float sine, float ior) {
        float s = Math.min(Math.abs(sine), 1f);
        if (!(s > 0f)) return 0f;
        float cos = (float) Math.sqrt(1f - s * s);
        return Math.max(0f, fresnelExact(cos, ior) - fresnelExact(1f, ior)) * fresnel;
    }

    /**
     * What the program adds to Schlick's reflectance on the turned bevel so that it meets the
     * face's ({@link #fresnelExact}) where the bevel flattens into it: the exact reflectance at the
     * turn less Schlick's. 0 flat.
     */
    static float schlickCorrection(float sine, float ior, float f0) {
        float s = Math.min(Math.abs(sine), 1f);
        if (!(s > 0f)) return 0f;
        float cos = (float) Math.sqrt(1f - s * s);
        return (fresnelExact(cos, ior) - fresnelExact(1f, ior)) - (schlick(cos, f0) - f0);
    }

    /**
     * {@code w} turned into the frame of a slab turned by {@code (tiltX, tiltY)}
     * ({@link GlassPose#tiltX}): about the in-plane axis across the tilt, so that the edge the tilt
     * points at goes away from the viewer. The eye straight above, {@code (0, 0, 1)}, comes out
     * as {@code (-tiltX, -tiltY, cos)}: in the slab's frame the viewer stands toward the near edge.
     * Into {@code out} ({@code x, y, z}).
     */
    static void toSlab(float wx, float wy, float wz, float tiltX, float tiltY, @NonNull float[] out) {
        float s = (float) Math.hypot(tiltX, tiltY);
        if (!(s > 0f)) {
            out[0] = wx;
            out[1] = wy;
            out[2] = wz;
            return;
        }
        float dx = tiltX / s, dy = tiltY / s;
        float c = (float) Math.sqrt(Math.max(0f, 1f - Math.min(s * s, 1f)));
        float along = wx * dx + wy * dy;
        float turned = along * c - wz * s;
        out[0] = wx + dx * (turned - along);
        out[1] = wy + dy * (turned - along);
        out[2] = along * s + wz * c;
    }

    /**
     * The half vector between the room light and the eye in the frame of a slab turned by
     * {@code (tiltX, tiltY)}, into {@code out}: the program's {@code kHalf} flat
     * ({@link GlassSlab#half}), turned with the slab otherwise, so the lens zone's specular stays
     * where a light fixed in the room puts it.
     */
    static void half(float tiltX, float tiltY, @NonNull float[] out) {
        float lx = GlassRefraction.KEY_LIGHT_X, ly = GlassRefraction.KEY_LIGHT_Y;
        float lz = GlassRefraction.KEY_LIGHT_RISE;
        float l = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        float[] v = out;
        toSlab(lx / l, ly / l, lz / l, tiltX, tiltY, v);
        float ax = v[0], ay = v[1], az = v[2];
        toSlab(0f, 0f, 1f, tiltX, tiltY, v);
        float hx = ax + v[0], hy = ay + v[1], hz = az + v[2];
        float h = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (!(h > 1e-6f)) {
            GlassSlab.half(out);
            return;
        }
        out[0] = hx / h;
        out[1] = hy / h;
        out[2] = hz / h;
    }

    /**
     * Where the glint stands on a slab turned by {@code (tiltX, tiltY)}, into {@code out}: its
     * centre in px from the slab's centre, its half width in px, and its white. The face's normal
     * leans toward the edge going away; the glint sits where that normal reflects the light (high,
     * up and to the left) to a viewer {@link #viewDp} above, so it travels across the face as the
     * slab turns. White 0 flat.
     */
    public void glint(float tiltX, float tiltY, float density, @NonNull float[] out) {
        float s = (float) Math.hypot(tiltX, tiltY);
        out[2] = glintWidthDp * density;
        if (!(s > 0f) || !(glint > 0f)) {
            out[0] = 0f;
            out[1] = 0f;
            out[3] = 0f;
            return;
        }
        float nz = (float) Math.sqrt(Math.max(0f, 1f - Math.min(s * s, 1f)));
        float lx = GlassRefraction.KEY_LIGHT_X * spread;
        float ly = GlassRefraction.KEY_LIGHT_Y * spread;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + 1f);
        lx /= ll;
        ly /= ll;
        float lz = 1f / ll;
        float nl = tiltX * lx + tiltY * ly + nz * lz;
        // The light reflected about the normal: the way the glint's ray leaves toward the eye.
        float vx = 2f * nl * tiltX - lx;
        float vy = 2f * nl * tiltY - ly;
        float vz = Math.max(2f * nl * nz - lz, 0.05f);
        float view = viewDp * density;
        out[0] = -view * vx / vz;
        out[1] = -view * vy / vz;
        float u = Math.min(s / onset, 1f);
        out[3] = glint * u * u * (3f - 2f * u);
    }

    // ------------------------------------------------------------------------- the scale

    /** How much the picture through the glass is magnified at page scale {@code scale}; 1 unscaled. */
    public float magnify(float scale) {
        if (!(scale > 0f) || scale == 1f) return 1f;
        return Math.max(0.9f, Math.min(1.1f, 1f + zoom * (scale - 1f)));
    }

    // ------------------------------------------------------------------------- the uniforms

    /** How many floats {@link #uniforms} writes. */
    public static final int UNIFORMS = 16;

    /**
     * The program's pose uniforms for {@code pose} on a slab {@code slab}, at {@code density},
     * into {@code out} ({@link #UNIFORMS} floats): {@code uPose} (the turn's sine as a vector, its
     * cosine, the shift per px of thickness), {@code uPoseLight} (the magnification, the face's
     * reflectance rise, the turn's Fresnel share, the most shift in px), {@code uPoseHalf} (the
     * turned half vector and the bevel's Schlick correction) and {@code uGlint}. Flat and
     * unscaled: {@code (0, 0, 1, 0)}, {@code (1, 0, 0, maxShift)}, {@code (kHalf, 0)} and no glint,
     * whatever the numbers. Allocates nothing: {@code scratch} holds four floats along the way.
     */
    public void uniforms(@NonNull GlassPose pose, @NonNull GlassSlab slab, float density,
                         @NonNull float[] out, @NonNull float[] scratch) {
        float tx = pose.tiltX, ty = pose.tiltY;
        float s = (float) Math.hypot(tx, ty);
        boolean turned = s > 0f;
        float c = (float) Math.sqrt(Math.max(0f, 1f - Math.min(s * s, 1f)));
        out[0] = turned ? tx : 0f;
        out[1] = turned ? ty : 0f;
        out[2] = turned ? c : 1f;
        out[3] = turned ? shiftTan(s, slab.ior) : 0f;
        out[4] = magnify(pose.scale);
        out[5] = turned ? faceReflectanceRise(s, slab.ior) : 0f;
        out[6] = turned ? fresnel : 0f;
        out[7] = maxShiftDp * density;
        if (turned) half(tx, ty, scratch);
        else GlassSlab.half(scratch);
        out[8] = scratch[0];
        out[9] = scratch[1];
        out[10] = scratch[2];
        out[11] = turned ? schlickCorrection(s, slab.ior, slab.f0) : 0f;
        if (turned) {
            glint(tx, ty, density, scratch);
            out[12] = scratch[0];
            out[13] = scratch[1];
            out[14] = scratch[2];
            out[15] = scratch[3];
        } else {
            out[12] = 0f;
            out[13] = 0f;
            out[14] = glintWidthDp * density;
            out[15] = 0f;
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof GlassPoseOptics)) return false;
        GlassPoseOptics t = (GlassPoseOptics) o;
        return shift == t.shift && maxShiftDp == t.maxShiftDp && fresnel == t.fresnel
            && glint == t.glint && glintWidthDp == t.glintWidthDp && onset == t.onset
            && viewDp == t.viewDp && spread == t.spread && zoom == t.zoom;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(shift);
        h = h * 31 + Float.floatToIntBits(maxShiftDp);
        h = h * 31 + Float.floatToIntBits(fresnel);
        h = h * 31 + Float.floatToIntBits(glint);
        h = h * 31 + Float.floatToIntBits(glintWidthDp);
        h = h * 31 + Float.floatToIntBits(onset);
        h = h * 31 + Float.floatToIntBits(viewDp);
        h = h * 31 + Float.floatToIntBits(spread);
        return h * 31 + Float.floatToIntBits(zoom);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "shift=%s,maxshift=%s,fresnel=%s,glint=%s,glintw=%s,onset=%s,view=%s,spread=%s,zoom=%s",
            shift, maxShiftDp, fresnel, glint, glintWidthDp, onset, viewDp, spread, zoom);
    }
}
