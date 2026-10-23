package com.termux.app.chrome.scene;

import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The Filament scene as one AGSL program (API 33+): a bench of nixie tubes in warm amber, glowing
 * in a drifting industrial haze, spelling the focused window's name. One full-screen pass.
 *
 * <p>Per pixel: a dark warm gradient, a three-octave value-noise haze lit around the tube row, the
 * touch blooms, and (only in the band around the bench, about a third of the screen) a loop over
 * at most five neighbouring tubes for their halo and the light pooling on the bench, then the one
 * tube under the pixel: glass, brass base and, inside the glyph box only, one sample of the glyph
 * atlas ({@link FilamentSource}). Tubes breathe with two slow sines (about +-6 percent, no hard
 * edges). Reactions arrive as {@link SceneEvents}: the focus change runs the cathode cycle in
 * Java (the glyph each tube shows is a uniform), a bell raises {@code uFlare} with a limited slew,
 * a touch is a bloom of the hill slots.</p>
 *
 * <p>Like {@link ContourProgram}: compiled once per instance, static uniforms only when the size
 * changes, time wrapped to {@link #PERIOD_MS} with every periodic term a whole multiple of the base
 * angular speed, every array preallocated.</p>
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class FilamentProgram implements SceneProgram {

    static final long PERIOD_MS = 120_000L;
    /** One noise cell of the haze, dp. */
    static final float HAZE_UNIT_DP = 220f;
    /** How fast the bell's flare may rise, per second (it fades by itself over the pulse). */
    static final float FLARE_RISE_PER_S = 3f;

    static final String AGSL =
        "uniform shader uAtlas;\n"
        + "uniform float2 uRes;\n"
        + "uniform float uTime;\n"
        + "uniform float uDp;\n"
        + "uniform float uHazeUnit;\n"
        + "uniform float4 uRow;\n"   // x0, pitch, tubeW, glassH
        + "uniform float4 uRow2;\n"  // glassTop, count, benchTop, mean brightness
        + "uniform float uFlare;\n"
        + "uniform float2 uTube[10];\n" // glyph (-1 unlit, -2 none), brightness
        + "uniform float4 uHill[3];\n"
        + "const float W0 = 0.05235988;\n" // 2*pi/120
        + "const float COLS = " + FilamentSource.COLS + ".0;\n"
        + "const float CELLW = " + FilamentSource.CELL_W + ".0;\n"
        + "const float CELLH = " + FilamentSource.CELL_H + ".0;\n"
        + "const half3 BG0 = half3(0.030, 0.022, 0.020);\n"
        + "const half3 BG1 = half3(0.060, 0.040, 0.030);\n"
        + "const half3 AMBER = half3(1.0, 0.56, 0.20);\n"
        + "const half3 HOT = half3(1.0, 0.86, 0.58);\n"
        + "const half3 BRASS = half3(0.34, 0.22, 0.10);\n"
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
        // three octaves, each orbiting slowly on its own circle (whole multiples of W0: seamless wrap)
        + "float haze(float2 px) {\n"
        + "  float2 q = px / uHazeUnit;\n"
        + "  float h = 0.0;\n"
        + "  float amp = 0.55;\n"
        + "  float freq = 1.0;\n"
        + "  for (int i = 0; i < 3; i++) {\n"
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
        + "  float dp = uDp;\n"
        + "  float cnt = uRow2.y;\n"
        + "  float x0 = uRow.x;\n"
        + "  float pitch = uRow.y;\n"
        + "  float tw = uRow.z;\n"
        + "  float th = uRow.w;\n"
        + "  float top = uRow2.x;\n"
        + "  float base = 0.28 * tw;\n"
        + "  float benchTop = uRow2.z;\n"
        + "  float rowC = top + 0.55 * th;\n"
        + "  float gain = uRow2.w * (1.0 + 0.5 * uFlare);\n"
        + "  half3 col = mix(BG0, BG1, half(fc.y / uRes.y));\n"
        + "  float hz = haze(fc);\n"
        + "  float lv = (fc.y - rowC) / (260.0 * dp);\n"
        + "  col += AMBER * half(hz * (0.06 + 0.30 * gain * exp(-lv * lv)));\n"
        + "  for (int i = 0; i < 3; i++) {\n"
        + "    if (uHill[i].z > 0.0) {\n"
        + "      float2 d = (fc - uHill[i].xy) / uHill[i].w;\n"
        + "      col += AMBER * half(0.7 * uHill[i].z * exp(-2.0 * dot(d, d)) * (0.35 + 0.9 * hz));\n"
        + "    }\n"
        + "  }\n"
        + "  float bandTop = top - 2.0 * th;\n"
        + "  float bandBot = benchTop + 70.0 * dp;\n"
        + "  if (fc.y > bandTop && fc.y < bandBot) {\n"
        + "    float idxF = floor((fc.x - x0) / pitch);\n"
        + "    float idxC = clamp(idxF, 0.0, cnt - 1.0);\n"
        + "    float2 tb = float2(-2.0, 0.0);\n"
        + "    float cxSel = 0.0;\n"
        + "    float halo = 0.0;\n"
        + "    float pool = 0.0;\n"
        + "    float invR2 = 1.0 / (0.64 * th * th);\n"
        + "    float rx = 0.9 * pitch;\n"
        + "    float dyP = (fc.y - (benchTop + 8.0 * dp)) / (28.0 * dp);\n"
        + "    for (int i = 0; i < 10; i++) {\n"
        + "      float fi = float(i);\n"
        + "      if (fi < cnt && abs(fi - idxC) < 2.5) {\n"
        + "        float2 t = uTube[i];\n"
        + "        float cx = x0 + fi * pitch + 0.5 * tw;\n"
        + "        float flick = 1.0 + 0.035 * sin(uTime * W0 * 40.0 + fi * 1.7)"
        + " + 0.025 * sin(uTime * W0 * 101.0 + fi * 3.1);\n"
        + "        float b = t.y * flick * (1.0 + 0.5 * uFlare);\n"
        + "        float dx = fc.x - cx;\n"
        + "        float dy = fc.y - rowC;\n"
        + "        halo += b * exp(-(dx * dx + dy * dy) * invR2);\n"
        + "        float px = dx / rx;\n"
        + "        pool += b * exp(-(px * px + dyP * dyP));\n"
        + "        if (fi == idxF) {\n"
        + "          tb = float2(t.x, b);\n"
        + "          cxSel = cx;\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "    col += AMBER * half(halo * 0.22);\n"
        // the bench: a dark surface fading into the haze, a warm lip, light pooled under each tube
        + "    float by = fc.y - benchTop;\n"
        + "    float bm = clamp(by / dp + 1.0, 0.0, 1.0);\n"
        + "    float fall = clamp(1.0 - by / (70.0 * dp), 0.0, 1.0);\n"
        + "    col = mix(col, half3(0.085, 0.055, 0.035), half(0.75 * bm * fall * fall));\n"
        + "    col += AMBER * half(pool * 0.30 * bm * fall);\n"
        + "    float lip = clamp(1.0 - abs(by - 0.5 * dp) / (1.3 * dp), 0.0, 1.0);\n"
        + "    col += BRASS * half(lip * (0.5 + 1.2 * clamp(pool, 0.0, 1.0)));\n"
        + "    if (tb.x > -1.5) {\n"
        + "      float a = 0.5 * tw;\n"
        + "      float lx = fc.x - cxSel;\n"
        + "      float ly = fc.y - top;\n"
        + "      float b = tb.y;\n"
        + "      float dG = ly < a ? length(float2(lx, ly - a)) - a : abs(lx) - a;\n"
        + "      dG = max(dG, ly - th);\n"
        + "      float glass = clamp(0.5 - dG, 0.0, 1.0);\n"
        + "      float dB = max(abs(lx) - 0.86 * a, max(th - ly, ly - (th + base)));\n"
        + "      float baseCov = clamp(0.5 - dB, 0.0, 1.0);\n"
        + "      half3 brass = BRASS * half(0.45 + 0.55 * (1.0 - abs(lx) / a) + 0.25 * b);\n"
        + "      col = mix(col, brass, half(baseCov));\n"
        + "      float depth = clamp(-dG / (0.5 * a), 0.0, 1.0);\n"
        + "      half3 glassCol = half3(0.025, 0.017, 0.012) + AMBER * half(0.045 * b * (0.4 + 0.6 * depth));\n"
        + "      col = mix(col, col * 0.35 + glassCol, half(glass * 0.9));\n"
        + "      float rim = clamp(1.0 - abs(dG + 1.2 * dp) / (1.5 * dp), 0.0, 1.0) * glass;\n"
        + "      col += half3(1.0, 0.82, 0.60) * half(rim * (0.07 + 0.10 * b));\n"
        + "      float streak = clamp(1.0 - abs(lx + 0.55 * a) / (0.10 * a), 0.0, 1.0)"
        + " * clamp((ly - 0.5 * a) / a, 0.0, 1.0) * step(ly, 0.8 * th);\n"
        + "      col += half3(1.0, 0.9, 0.75) * half(0.05 * streak * glass);\n"
        + "      if (tb.x >= 0.0) {\n"
        + "        float bw = 0.86 * tw;\n"
        + "        float bh = bw * CELLH / CELLW;\n"
        + "        float u = (lx + 0.5 * bw) / bw;\n"
        + "        float v = (ly - (0.52 * th - 0.5 * bh)) / bh;\n"
        + "        if (u > 0.0 && u < 1.0 && v > 0.0 && v < 1.0) {\n"
        + "          float colI = mod(tb.x, COLS);\n"
        + "          float rowI = floor(tb.x / COLS);\n"
        + "          half4 s = uAtlas.eval(float2((colI + u) * CELLW, (rowI + v) * CELLH));\n"
        + "          float core = float(s.r);\n"
        + "          float gl = float(s.g);\n"
        + "          half3 lit = mix(AMBER, HOT, half(core * core)) * half(core * 1.1);\n"
        + "          col += (lit * half(b) + AMBER * half(gl * 0.5 * b)) * half(glass);\n"
        + "        }\n"
        + "      } else {\n"
        + "        col += AMBER * half(0.012 * glass);\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "  return half4(col, 1.0);\n"
        + "}\n";

    @NonNull private final RuntimeShader mShader = new RuntimeShader(AGSL);
    private final float mScale;
    @NonNull private final FilamentSource mSource;
    private final float[] mHills = new float[SceneEvents.MAX_HILLS * 4];
    private final float[] mSparks = new float[SceneEvents.MAX_SPARKS * 4];
    private final float[] mPulseRect = new float[SceneEvents.MAX_PULSES * 4];
    private final float[] mPulseT = new float[SceneEvents.MAX_PULSES * 2];
    private final float[] mBasins = new float[SceneEvents.MAX_BASINS * 4];
    private final float[] mTube = new float[FilamentLayout.MAX_TUBES * 2];
    private final int[] mTarget = new int[FilamentLayout.MAX_TUBES];
    private final float[] mGeo = new float[7];
    private final float[] mRow = new float[4];
    private final float[] mRow2 = new float[4];
    private String mShownName;
    private int mCount = FilamentLayout.MIN_TUBES;
    private int mWidth = -1, mHeight = -1;
    private float mDensity = -1f;
    private boolean mGeoDirty = true;
    private float mMean = 0.35f;
    private float mFlare;
    private long mFlareAtMs = Long.MIN_VALUE;

    /** @param scale this program's pixels per full-resolution pixel: 1, or 1/8 for the glass pass */
    FilamentProgram(float scale, @NonNull FilamentSource source) {
        mScale = scale;
        mSource = source;
        BitmapShader atlas = new BitmapShader(source.atlas(), Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP);
        atlas.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        mShader.setInputShader("uAtlas", atlas);
        for (int i = 0; i < FilamentLayout.MAX_TUBES; i++) {
            mTube[i * 2] = -2f;
        }
        mShader.setFloatUniform("uTube", mTube);
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
        float d = density * mScale;
        mShader.setFloatUniform("uRes", width, height);
        mShader.setFloatUniform("uDp", d);
        mShader.setFloatUniform("uHazeUnit", HAZE_UNIT_DP * d);
        mGeoDirty = true;
        pushRow();
    }

    @Override
    public void setTime(long uptimeMs) {
        mShader.setFloatUniform("uTime", (uptimeMs % PERIOD_MS) / 1000f);
    }

    @Override
    public void setEvents(@NonNull SceneEvents events, long nowMs, float density) {
        String name = mSource.name();
        if (!java.util.Objects.equals(name, mShownName)) applyName(name);
        // The cathode cycle: what each tube shows now, and the bench's mean light.
        long age = events.focusAgeMs(nowMs);
        float dim = FilamentLayout.cycleBrightness(age);
        float lit = 0f;
        for (int i = 0; i < mCount; i++) {
            int g = FilamentLayout.shownGlyph(i, mCount, age, mTarget[i]);
            mTube[i * 2] = g;
            mTube[i * 2 + 1] = g == FilamentLayout.UNLIT ? 0f : dim;
            if (g != FilamentLayout.UNLIT) lit += dim;
        }
        for (int i = mCount; i < FilamentLayout.MAX_TUBES; i++) {
            mTube[i * 2] = -2f;
            mTube[i * 2 + 1] = 0f;
        }
        mShader.setFloatUniform("uTube", mTube);
        mMean = Math.max(0.35f, lit / mCount);
        // Reactions of the shared slots: hills are the touch blooms, the pulses the bell's flare.
        events.fill(nowMs, density, mHills, mSparks, mPulseRect, mPulseT, mBasins);
        for (int i = 0; i < mHills.length; i += 4) {
            mHills[i] *= mScale;
            mHills[i + 1] *= mScale;
            mHills[i + 3] *= mScale;
        }
        mShader.setFloatUniform("uHill", mHills);
        float target = Math.min(1f, mPulseT[1] + mPulseT[3]);
        float dt = mFlareAtMs == Long.MIN_VALUE ? 0f : Math.max(0, nowMs - mFlareAtMs) / 1000f;
        mFlareAtMs = nowMs;
        mFlare = Math.min(target, mFlare + FLARE_RISE_PER_S * dt);
        mShader.setFloatUniform("uFlare", mFlare);
        pushRow();
    }

    /** A new name: its glyphs, its tube count, and so the bench's geometry. */
    private void applyName(String name) {
        mShownName = name;
        int n = FilamentLayout.layout(name, mTarget);
        if (n != mCount) mGeoDirty = true;
        mCount = n;
        for (int i = 0; i < n; i++) {
            mTube[i * 2] = mTarget[i];
            mTube[i * 2 + 1] = mTarget[i] == FilamentLayout.UNLIT ? 0f : 1f;
        }
        mShader.setFloatUniform("uTube", mTube);
        pushRow();
    }

    /** The bench's rows: re-derived when the size or the tube count changed, the mean light always. */
    private void pushRow() {
        if (mWidth <= 0) return;
        if (mGeoDirty) {
            mGeoDirty = false;
            FilamentLayout.geometry(mCount, mWidth / mScale, mHeight / mScale, mDensity, mGeo);
            for (int i = 0; i < mGeo.length; i++) mGeo[i] *= mScale;
            mRow[0] = mGeo[0];
            mRow[1] = mGeo[2];
            mRow[2] = mGeo[3];
            mRow[3] = mGeo[4];
            mRow2[0] = mGeo[1];
            mRow2[2] = mGeo[6];
            mShader.setFloatUniform("uRow", mRow);
        }
        mRow2[1] = mCount;
        mRow2[3] = mMean;
        mShader.setFloatUniform("uRow2", mRow2);
    }
}
