package com.termux.app.chrome.scene;

import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The Contour scene as one AGSL program (API 33+): the contour lines of a drifting three-octave
 * noise heightfield, drawn at a fixed pixel width from the field's analytic gradient (no
 * {@code fwidth}), with the reactions of {@link SceneEvents} as uniform slots.
 *
 * <p>Compiled once per instance; the full-resolution layer and the low-resolution glass pass hold
 * one each, at their own {@code scale} (the pixels per full-resolution pixel: 1 and 1/8). Static
 * uniforms are set when the size changes, the time uniform per drawn frame, the reaction arrays
 * only while a reaction animates or the basins moved. Every array is preallocated.</p>
 *
 * <p>Time is wrapped to {@link #PERIOD_S} in Java and every drift term is a whole multiple of the
 * base angular speed, so the wrap is seamless and the shader never sees a large number.</p>
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class ContourProgram implements SceneProgram {

    /** The wrap of the time uniform, seconds. Drift octaves run at 1x, 2x and 3x the base speed. */
    static final long PERIOD_MS = 120_000L;
    /** One map unit (one noise cell) in dp. */
    static final float UNIT_DP = 140f;
    /** Contour line width in px at full resolution (dp * density). */
    static final float LINE_DP = 1.1f;

    static final String AGSL =
        "uniform float2 uRes;\n"
        + "uniform float uTime;\n"
        + "uniform float uUnit;\n"
        + "uniform float uLineW;\n"
        + "uniform float uDp;\n"
        + "uniform float4 uHill[3];\n"
        + "uniform float4 uBasin[4];\n"
        + "uniform float4 uSpark[4];\n"
        + "uniform float4 uPulseRect[2];\n"
        + "uniform float2 uPulseT[2];\n"
        + "uniform half3 uBg0;\n"
        + "uniform half3 uBg1;\n"
        + "uniform half3 uInk;\n"
        + "const float LEVELS = 10.0;\n"
        + "const float W0 = 0.05235988;\n" // 2*pi/120
        + "float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }\n"
        // value noise with analytic derivative (quintic fade): x = value, yz = d/dp
        + "float3 noised(float2 p) {\n"
        + "  float2 i = floor(p);\n"
        + "  float2 f = fract(p);\n"
        + "  float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);\n"
        + "  float2 du = 30.0 * f * f * (f * (f - 2.0) + 1.0);\n"
        + "  float a = hash(i);\n"
        + "  float b = hash(i + float2(1.0, 0.0));\n"
        + "  float c = hash(i + float2(0.0, 1.0));\n"
        + "  float d = hash(i + float2(1.0, 1.0));\n"
        + "  float k = a - b - c + d;\n"
        + "  return float3(a + (b - a) * u.x + (c - a) * u.y + k * u.x * u.y,\n"
        + "                du * (float2(b - a, c - a) + k * u.yx));\n"
        + "}\n"
        // height (x) and its gradient in map units (yz) at a pixel position
        + "float3 heightAt(float2 px) {\n"
        + "  float2 q = px / uUnit;\n"
        + "  float h = 0.0;\n"
        + "  float2 g = float2(0.0);\n"
        + "  float amp = 0.5;\n"
        + "  float freq = 1.0;\n"
        + "  for (int i = 0; i < 3; i++) {\n"
        + "    float k = float(i) + 1.0;\n"
        + "    float ph = uTime * W0 * k;\n"
        + "    float2 off = float2(sin(ph + k * 1.7), cos(ph + k * 2.3)) * (0.8 + 0.7 * k);\n"
        + "    float3 n = noised(q * freq + off + float2(k * 17.0, k * 5.0));\n"
        + "    h += amp * n.x;\n"
        + "    g += amp * freq * n.yz;\n"
        + "    amp *= 0.5;\n"
        + "    freq *= 2.03;\n"
        + "  }\n"
        + "  for (int i = 0; i < 3; i++) {\n"
        + "    float a = uHill[i].z;\n"
        + "    if (a > 0.0) {\n"
        + "      float r = uHill[i].w;\n"
        + "      float2 d = (px - uHill[i].xy) / r;\n"
        + "      float e = a * exp(-2.0 * dot(d, d));\n"
        + "      h += e;\n"
        + "      g += e * (-4.0 * d / r) * uUnit;\n"
        + "    }\n"
        + "  }\n"
        + "  for (int i = 0; i < 4; i++) {\n"
        + "    if (uBasin[i].z > 0.0) {\n"
        + "      float2 d = (px - uBasin[i].xy) / (uBasin[i].zw * 1.1);\n"
        + "      float t = dot(d, d);\n"
        + "      float e = 0.2 * exp(-0.7 * t * t);\n"
        + "      h -= e;\n"
        + "      g -= e * (-1.4 * t) * (2.0 * d / (uBasin[i].zw * 1.1)) * uUnit;\n"
        + "    }\n"
        + "  }\n"
        + "  return float3(h, g);\n"
        + "}\n"
        + "half4 main(float2 fc) {\n"
        + "  float3 hg = heightAt(fc);\n"
        + "  float v = hg.x * LEVELS;\n"
        + "  float gl = max(length(hg.yz) * LEVELS, 0.001);\n"
        + "  float fr = fract(v);\n"
        + "  float dPx = min(fr, 1.0 - fr) / gl * uUnit;\n"
        + "  float line = clamp(uLineW * 0.5 + 0.5 - dPx, 0.0, 1.0);\n"
        + "  float idx = step(mod(floor(v + 0.5), 5.0), 0.5);\n"
        + "  float ink = line * mix(0.6, 1.0, idx);\n"
        + "  float spark = 0.0;\n"
        + "  for (int i = 0; i < 4; i++) {\n"
        + "    if (uSpark[i].w > 0.0) {\n"
        + "      float lvl = floor(heightAt(uSpark[i].xy).x * LEVELS + 0.5);\n"
        + "      float dl = abs(v - lvl) / gl * uUnit;\n"
        + "      float onLine = clamp(uLineW * 1.2 + 0.5 - dl, 0.0, 1.0);\n"
        + "      float ring = 1.0 - clamp(abs(length(fc - uSpark[i].xy) - uSpark[i].z) / (30.0 * uDp), 0.0, 1.0);\n"
        + "      spark += onLine * ring * uSpark[i].w;\n"
        + "    }\n"
        + "  }\n"
        + "  float pulse = 0.0;\n"
        + "  for (int i = 0; i < 2; i++) {\n"
        + "    if (uPulseT[i].y > 0.0) {\n"
        + "      float2 q = abs(fc - uPulseRect[i].xy) - uPulseRect[i].zw;\n"
        + "      float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);\n"
        + "      pulse += (1.0 - clamp(abs(sd - uPulseT[i].x) / (70.0 * uDp), 0.0, 1.0)) * uPulseT[i].y;\n"
        + "    }\n"
        + "  }\n"
        + "  half3 col = mix(uBg0, uBg1, half(fc.y / uRes.y));\n"
        + "  col += uInk * half(0.05 * hg.x);\n"
        + "  col += uInk * half(0.08 * pulse);\n"
        + "  col = mix(col, uInk, half(clamp(ink * (1.0 + 1.5 * pulse), 0.0, 1.0)));\n"
        + "  col = mix(col, half3(1.0), half(clamp(spark, 0.0, 1.0)));\n"
        + "  return half4(col, 1.0);\n"
        + "}\n";

    @NonNull final RuntimeShader shader = new RuntimeShader(AGSL);

    @NonNull @Override
    public RuntimeShader shader() {
        return shader;
    }
    private final float scale;
    private final float[] mHills = new float[SceneEvents.MAX_HILLS * 4];
    private final float[] mSparks = new float[SceneEvents.MAX_SPARKS * 4];
    private final float[] mPulseRect = new float[SceneEvents.MAX_PULSES * 4];
    private final float[] mPulseT = new float[SceneEvents.MAX_PULSES * 2];
    private final float[] mBasins = new float[SceneEvents.MAX_BASINS * 4];
    private int mWidth = -1, mHeight = -1;
    private float mDensity = -1f;

    /** @param scale this program's pixels per full-resolution pixel: 1, or 1/8 for the glass pass */
    ContourProgram(float scale) {
        this.scale = scale;
        // Deep blue, light ink: the "light-on-deep-blue" palette, brighter than the Steam original.
        shader.setFloatUniform("uBg0", 0.020f, 0.050f, 0.125f);
        shader.setFloatUniform("uBg1", 0.050f, 0.120f, 0.250f);
        shader.setFloatUniform("uInk", 0.78f, 0.90f, 1.0f);
    }

    /** The static uniforms, only when the size or density changed. {@code width}/{@code height} at this scale. */
    @Override
    public void setSize(int width, int height, float density) {
        if (width == mWidth && height == mHeight && density == mDensity) return;
        mWidth = width;
        mHeight = height;
        mDensity = density;
        float d = density * scale;
        shader.setFloatUniform("uRes", width, height);
        shader.setFloatUniform("uUnit", UNIT_DP * d);
        // A low-resolution pass keeps at least one pixel of line, so it blurs to an even tone.
        shader.setFloatUniform("uLineW", Math.max(1f, LINE_DP * d));
        shader.setFloatUniform("uDp", d);
    }

    /** Time wrapped to {@link #PERIOD_MS} so the shader only ever sees a small number. */
    @Override
    public void setTime(long uptimeMs) {
        shader.setFloatUniform("uTime", (uptimeMs % PERIOD_MS) / 1000f);
    }

    /** The reaction arrays for {@code nowMs}, scaled to this program's resolution. */
    @Override
    public void setEvents(@NonNull SceneEvents events, long nowMs, float density) {
        events.fill(nowMs, density, mHills, mSparks, mPulseRect, mPulseT, mBasins);
        if (scale != 1f) {
            scaleRange(mHills, 4, 0, 2);
            scaleRange(mHills, 4, 3, 1);
            scaleRange(mSparks, 4, 0, 3); // x, y, travel
            scaleRange(mPulseRect, 4, 0, 4);
            scaleRange(mPulseT, 2, 0, 1);
            scaleRange(mBasins, 4, 0, 4);
        }
        shader.setFloatUniform("uHill", mHills);
        shader.setFloatUniform("uSpark", mSparks);
        shader.setFloatUniform("uPulseRect", mPulseRect);
        shader.setFloatUniform("uPulseT", mPulseT);
        shader.setFloatUniform("uBasin", mBasins);
    }

    /** Scales {@code count} entries from {@code first} of each {@code stride}-sized group. */
    private void scaleRange(float[] a, int stride, int first, int count) {
        for (int i = 0; i + stride <= a.length; i += stride) {
            for (int k = first; k < first + count; k++) a[i + k] *= scale;
        }
    }
}
