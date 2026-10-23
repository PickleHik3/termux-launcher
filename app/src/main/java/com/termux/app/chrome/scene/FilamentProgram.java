package com.termux.app.chrome.scene;

import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The Filament scene as one AGSL program (API 33+): nixie tubes and neon tubes drifting through a
 * dark warm haze, in three depth bands. One full-screen pass.
 *
 * <p>The motion is simulated in Java ({@link FilamentField}, normalised positions, shared by the
 * full-resolution program and the glass pass) and arrives as two uniform arrays of
 * {@link FilamentField#POOL}: centre, size and rotation of each object, and its kind, glyph or
 * colour, brightness and softness. Per pixel: the two-octave haze, then one loop over the pool
 * in far-to-near order whose first act is a squared-distance test against the object's bounding
 * circle; only an object whose circle holds the pixel is evaluated. A nixie is a glass
 * envelope with its halo, a brass base and, inside the glyph box only, one sample of the glyph
 * atlas ({@link FilamentSource}). A neon is the distance to a curve (a bar, an arc, a sine, a
 * triangle wave) turned into a core, a white-hot centre and an analytic halo; no blurs anywhere.
 * Reactions arrive as {@link SceneEvents}: the focus change runs the cathode cycle in Java (the
 * glyph each nixie shows is a uniform), a bell raises {@code uFlare} with a limited slew, a touch
 * repels the nearby objects in the field.</p>
 *
 * <p>Like {@link ContourProgram}: compiled once per instance, static uniforms only when the size
 * changes, time wrapped to {@link #PERIOD_MS} with every periodic term a whole multiple of the base
 * angular speed, every array preallocated.</p>
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class FilamentProgram implements SceneProgram {

    static final long PERIOD_MS = 120_000L;
    /** One noise cell of the haze, dp. */
    static final float HAZE_UNIT_DP = 260f;
    /** How fast the bell's flare may rise, per second (it fades by itself over the pulse). */
    static final float FLARE_RISE_PER_S = 3f;

    static final String AGSL =
        "uniform shader uAtlas;\n"
        + "uniform float2 uRes;\n"
        + "uniform float uTime;\n"
        + "uniform float uHazeUnit;\n"
        + "uniform float uFlare;\n"
        + "uniform float4 uObjA[" + FilamentField.POOL + "];\n" // centre x, y, size px, unused
        + "uniform float2 uRot[" + FilamentField.POOL + "];\n"  // cos, sin
        + "uniform float4 uObjB[" + FilamentField.POOL + "];\n" // kind, glyph|colour, brightness, softness
        + "const float W0 = 0.05235988;\n" // 2*pi/120
        + "const float COLS = " + FilamentSource.COLS + ".0;\n"
        + "const float CELLW = " + FilamentSource.CELL_W + ".0;\n"
        + "const float CELLH = " + FilamentSource.CELL_H + ".0;\n"
        + "const half3 BG0 = half3(0.030, 0.022, 0.020);\n"
        + "const half3 BG1 = half3(0.060, 0.040, 0.030);\n"
        + "const half3 AMBER = half3(1.0, 0.56, 0.20);\n"
        + "const half3 HOT = half3(1.0, 0.86, 0.58);\n"
        + "const half3 BRASS = half3(0.34, 0.22, 0.10);\n"
        + "const half3 PINK = half3(1.0, 0.22, 0.52);\n"
        + "const half3 CYAN = half3(0.12, 0.82, 1.0);\n"
        + "const half3 ORANGE = half3(1.0, 0.46, 0.10);\n"
        + "float hash12(float2 p) {\n"
        + "  float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);\n"
        + "  p3 += dot(p3, float3(p3.y, p3.z, p3.x) + 33.33);\n"
        + "  return fract((p3.x + p3.y) * p3.z);\n"
        + "}\n"
        + "float vnoise(float2 p) {\n"
        + "  float2 i = floor(p);\n"
        + "  float2 f = fract(p);\n"
        + "  float2 u = f * f * (3.0 - 2.0 * f);\n"
        + "  float a = hash12(i);\n"
        + "  float b = hash12(i + float2(1.0, 0.0));\n"
        + "  float c = hash12(i + float2(0.0, 1.0));\n"
        + "  float d = hash12(i + float2(1.0, 1.0));\n"
        + "  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n"
        + "}\n"
        // two octaves, each orbiting slowly on its own circle (whole multiples of W0: seamless wrap)
        + "float haze(float2 px) {\n"
        + "  float2 q = px / uHazeUnit;\n"
        + "  float h = 0.0;\n"
        + "  float amp = 0.7;\n"
        + "  float freq = 1.0;\n"
        + "  for (int i = 0; i < 2; i++) {\n"
        + "    float k = float(i) + 1.0;\n"
        + "    float ph = uTime * W0 * k;\n"
        + "    float2 off = float2(sin(ph + k * 1.3), cos(ph + k * 2.1)) * (0.25 + 0.15 * k);\n"
        + "    h += amp * vnoise(q * freq + off + float2(k * 13.0, k * 7.0));\n"
        + "    amp *= 0.5;\n"
        + "    freq *= 2.1;\n"
        + "  }\n"
        + "  return h;\n"
        + "}\n"
        + "half4 main(float2 fc) {\n"
        + "  half3 col = mix(BG0, BG1, half(fc.y / uRes.y));\n"
        + "  col += AMBER * half(haze(fc) * 0.11);\n"
        + "  for (int i = 0; i < " + FilamentField.POOL + "; i++) {\n"
        + "    float4 ob = uObjB[i];\n"
        + "    float4 oa = uObjA[i];\n"
        + "    float2 d = fc - oa.xy;\n"
        // the bounding circle first: a nixie's halo reaches 2.2 units, a neon's 1.7
        + "    float rb = (ob.x < 0.5 ? 2.2 : 1.7) * oa.z;\n"
        + "    if (dot(d, d) < rb * rb) {\n"
        + "      float fi = float(i);\n"
        + "      float flick = 1.0 + 0.03 * sin(uTime * W0 * 40.0 + fi * 1.7);\n"
        + "      float b = ob.z * flick * (1.0 + 0.5 * uFlare);\n"
        + "      float2 rc = uRot[i];\n"
        + "      float2 q = float2(rc.x * d.x + rc.y * d.y, -rc.y * d.x + rc.x * d.y) / oa.z;\n"
        + "      float px = ob.w / oa.z;\n"
        + "      if (ob.x < 0.5) {\n"
        // nixie: the origin is the tube's centre; the glass is 1 wide and 1.9 high, the base 0.28
        + "        float a = 0.5;\n"
        + "        float th = 1.9;\n"
        + "        float lx = q.x;\n"
        + "        float ly = q.y + 1.09;\n"
        + "        col += AMBER * half(b * exp(-dot(q, q)) * 0.22);\n"
        + "        float dG = ly < a ? length(float2(lx, ly - a)) - a : abs(lx) - a;\n"
        + "        dG = max(dG, ly - th);\n"
        + "        float glass = clamp(0.5 - dG / px, 0.0, 1.0);\n"
        + "        float dB = max(abs(lx) - 0.86 * a, max(th - ly, ly - (th + 0.28)));\n"
        + "        float baseCov = clamp(0.5 - dB / px, 0.0, 1.0);\n"
        + "        half3 brass = BRASS * half(0.45 + 0.55 * (1.0 - abs(lx) / a) + 0.25 * b);\n"
        + "        col = mix(col, brass * half(0.4 + 0.6 * b), half(baseCov));\n"
        + "        float depth = clamp(-dG / (0.5 * a), 0.0, 1.0);\n"
        + "        half3 glassCol = half3(0.025, 0.017, 0.012) + AMBER * half(0.045 * b * (0.4 + 0.6 * depth));\n"
        + "        col = mix(col, col * 0.35 + glassCol, half(glass * 0.9));\n"
        + "        float rim = clamp(1.0 - abs(dG + 0.04) / (1.5 * px), 0.0, 1.0) * glass;\n"
        + "        col += half3(1.0, 0.82, 0.60) * half(rim * (0.07 + 0.10 * b));\n"
        + "        float streak = clamp(1.0 - abs(lx + 0.55 * a) / (0.10 * a), 0.0, 1.0)"
        + " * clamp((ly - 0.5 * a) / a, 0.0, 1.0) * step(ly, 0.8 * th);\n"
        + "        col += half3(1.0, 0.9, 0.75) * half(0.05 * streak * glass * b);\n"
        + "        if (ob.y >= 0.0) {\n"
        + "          float bw = 0.86;\n"
        + "          float bh = bw * CELLH / CELLW;\n"
        + "          float u = (lx + 0.5 * bw) / bw;\n"
        + "          float v = (ly - (0.52 * th - 0.5 * bh)) / bh;\n"
        + "          if (u > 0.0 && u < 1.0 && v > 0.0 && v < 1.0) {\n"
        + "            float colI = mod(ob.y, COLS);\n"
        + "            float rowI = floor(ob.y / COLS);\n"
        + "            half4 s = uAtlas.eval(float2((colI + u) * CELLW, (rowI + v) * CELLH));\n"
        + "            float core = float(s.r);\n"
        + "            float gl = float(s.g);\n"
        + "            half3 lit = mix(AMBER, HOT, half(core * core)) * half(core * 1.1);\n"
        + "            col += (lit * half(b) + AMBER * half(gl * 0.5 * b)) * half(glass);\n"
        + "          }\n"
        + "        } else {\n"
        + "          col += AMBER * half(0.012 * glass * b);\n"
        + "        }\n"
        + "      } else {\n"
        // neon: distance to y = f(x) on |x| <= 1 (bar, arc, sine, triangle wave), slope-corrected
        + "        float f = 0.0;\n"
        + "        float sl = 0.0;\n"
        + "        if (ob.x > 3.5) {\n"
        + "          f = 0.22 * (abs(4.0 * fract(q.x) - 2.0) - 1.0);\n"
        + "          sl = 0.88;\n"
        + "        } else if (ob.x > 2.5) {\n"
        + "          f = 0.4 * sin(3.14159 * q.x);\n"
        + "          sl = 1.2566 * cos(3.14159 * q.x);\n"
        + "        } else if (ob.x > 1.5) {\n"
        + "          f = 0.3 * (1.0 - 2.0 * q.x * q.x);\n"
        + "          sl = -1.2 * q.x;\n"
        + "        }\n"
        + "        float dy = (q.y - f) * inversesqrt(1.0 + sl * sl);\n"
        + "        float dn = length(float2(max(abs(q.x) - 1.0, 0.0), dy));\n"
        + "        float t = 0.055 * ob.w;\n"
        + "        float cap = smoothstep(0.92, 1.0, abs(q.x));\n"
        + "        float core = (1.0 - smoothstep(0.5 * t, 1.5 * t, dn)) * (1.0 - 0.6 * cap);\n"
        + "        float hot = (1.0 - smoothstep(0.0, 0.8 * t, dn)) * (1.0 - cap);\n"
        + "        float glow = exp(-dn * dn * 11.0) * 0.40;\n"
        + "        half3 neon = ob.y < 0.5 ? PINK : (ob.y < 1.5 ? CYAN : ORANGE);\n"
        + "        col += neon * half(b * (core * 0.95 + glow));\n"
        + "        col += half3(1.0, 0.95, 0.9) * half(b * hot * 0.5);\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "  return half4(col, 1.0);\n"
        + "}\n";

    @NonNull private final RuntimeShader mShader = new RuntimeShader(AGSL);
    private final float mScale;
    @NonNull private final FilamentSource mSource;
    @NonNull private final FilamentField mField;
    private final float[] mHills = new float[SceneEvents.MAX_HILLS * 4];
    private final float[] mSparks = new float[SceneEvents.MAX_SPARKS * 4];
    private final float[] mPulseRect = new float[SceneEvents.MAX_PULSES * 4];
    private final float[] mPulseT = new float[SceneEvents.MAX_PULSES * 2];
    private final float[] mBasins = new float[SceneEvents.MAX_BASINS * 4];
    private final float[] mObjA = new float[FilamentField.POOL * 4];
    private final float[] mRot = new float[FilamentField.POOL * 2];
    private final float[] mObjB = new float[FilamentField.POOL * 4];
    private final int[] mTarget = new int[FilamentLayout.MAX_TUBES];
    private final int[] mGlyphs = new int[FilamentField.NIXIE_COUNT];
    private String mShownName;
    private int mWidth = -1, mHeight = -1;
    private float mDensity = -1f;
    private float mFlare;
    private long mFlareAtMs = Long.MIN_VALUE;

    /** @param scale this program's pixels per full-resolution pixel: 1, or 1/8 for the glass pass */
    FilamentProgram(float scale, @NonNull FilamentSource source) {
        mScale = scale;
        mSource = source;
        mField = source.field();
        BitmapShader atlas = new BitmapShader(source.atlas(), Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP);
        atlas.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        mShader.setInputShader("uAtlas", atlas);
        for (int i = 0; i < FilamentField.POOL; i++) {
            mRot[i * 2] = 1f;
            int slot = FilamentField.nixieSlot(i);
            int kind = FilamentField.kind(i);
            mObjB[i * 4] = kind;
            mObjB[i * 4 + 1] = slot >= 0 ? FilamentLayout.UNLIT : FilamentField.colour(i);
            mObjB[i * 4 + 2] = FilamentField.BRIGHT[FilamentField.band(i)];
            mObjB[i * 4 + 3] = FilamentField.SOFT[FilamentField.band(i)];
        }
        mShader.setFloatUniform("uRot", mRot);
        mShader.setFloatUniform("uObjA", mObjA);
        mShader.setFloatUniform("uObjB", mObjB);
        mShader.setFloatUniform("uFlare", 0f);
        applyName(null);
    }

    @NonNull @Override
    public RuntimeShader shader() {
        return mShader;
    }

    @Override
    public void setSize(int width, int height, float density) {
        if (width == mWidth && height == mHeight && density == mDensity) return;
        mWidth = width;
        mHeight = height;
        mDensity = density;
        mShader.setFloatUniform("uRes", width, height);
        mShader.setFloatUniform("uHazeUnit", HAZE_UNIT_DP * density * mScale);
        mField.setAspect(width / (float) height);
    }

    @Override
    public void setTime(long uptimeMs) {
        mShader.setFloatUniform("uTime", (uptimeMs % PERIOD_MS) / 1000f);
        // The scene clock stands still while typing is held, so the objects stand still too and
        // carry on from where they were; the field ignores a time it has already reached.
        mField.advanceTo(uptimeMs);
        if (mWidth <= 0) return;
        float dp = mDensity * mScale;
        for (int i = 0; i < FilamentField.POOL; i++) {
            float ang = mField.angle(i);
            mObjA[i * 4] = mField.x(i) * mWidth;
            mObjA[i * 4 + 1] = mField.y(i) * mHeight;
            mObjA[i * 4 + 2] = FilamentField.SIZE_DP[FilamentField.band(i)] * dp;
            mRot[i * 2] = (float) Math.cos(ang);
            mRot[i * 2 + 1] = (float) Math.sin(ang);
        }
        mShader.setFloatUniform("uObjA", mObjA);
        mShader.setFloatUniform("uRot", mRot);
    }

    @Override
    public void setEvents(@NonNull SceneEvents events, long nowMs, float density) {
        String name = mSource.name();
        if (!java.util.Objects.equals(name, mShownName)) applyName(name);
        // The cathode cycle: what each nixie shows now, and its brightness.
        long age = events.focusAgeMs(nowMs);
        float dim = FilamentLayout.cycleBrightness(age);
        for (int i = 0; i < FilamentField.POOL; i++) {
            int slot = FilamentField.nixieSlot(i);
            if (slot < 0) continue;
            mObjB[i * 4 + 1] = FilamentLayout.shownGlyph(slot, FilamentField.NIXIE_COUNT, age,
                mGlyphs[slot]);
            mObjB[i * 4 + 2] = FilamentField.BRIGHT[FilamentField.band(i)] * dim;
        }
        mShader.setFloatUniform("uObjB", mObjB);
        // Reactions of the shared slots: a hill (a touch) repels the objects near it, the pulses
        // are the bell's flare.
        events.fill(nowMs, density, mHills, mSparks, mPulseRect, mPulseT, mBasins);
        if (mWidth > 0) {
            float fw = mWidth / mScale, fh = mHeight / mScale;
            for (int i = 0; i < SceneEvents.MAX_HILLS; i++) {
                mField.setRepel(i, mHills[i * 4] / fw, mHills[i * 4 + 1] / fh, mHills[i * 4 + 2],
                    mHills[i * 4 + 3] / fh);
            }
        }
        float target = Math.min(1f, mPulseT[1] + mPulseT[3]);
        float dt = mFlareAtMs == Long.MIN_VALUE ? 0f : Math.max(0, nowMs - mFlareAtMs) / 1000f;
        mFlareAtMs = nowMs;
        mFlare = Math.min(target, mFlare + FLARE_RISE_PER_S * dt);
        mShader.setFloatUniform("uFlare", mFlare);
    }

    /** A new name: its letters dealt across the nixies, shown from the next events pass. */
    private void applyName(String name) {
        mShownName = name;
        int n = FilamentLayout.layout(name, mTarget);
        FilamentLayout.spread(mTarget, n, FilamentField.NIXIE_COUNT, mGlyphs);
        for (int i = 0; i < FilamentField.POOL; i++) {
            int slot = FilamentField.nixieSlot(i);
            if (slot >= 0) mObjB[i * 4 + 1] = mGlyphs[slot];
        }
        mShader.setFloatUniform("uObjB", mObjB);
    }
}
