package com.termux.app.chrome.wallpaper;

/**
 * The AGSL every built-in shares: the uniform contract, cheap float hashes and value noise, the
 * moment effects, and the {@code main} that ties a background's {@code scene} to them.
 *
 * <p>A built-in's source is {@code HEAD + its own code + TAIL}. Its own code defines
 * {@code float3 scene(float2 p)}, the base picture in full-frame pixels (top-left origin), and
 * must either multiply everything {@code uTime}-dependent by {@code uEnergy} (swaying motion) or
 * drive one-way motion from {@code uPhase} alone, which the director integrates over energy and
 * zeroes once a lock settles. AGSL has no preprocessor, no
 * unsigned or bitwise operators, so the hashes are sin/fract floats.</p>
 *
 * <p>Moments never touch {@code scene}'s maths. A page change warps the sample position (a
 * horizontal flow shift, eased out); every other kind adds a soft lift, together capped at +15 %,
 * on top of the finished colour. Slot layout: {@code uMomentState = (kind, progress)}; for
 * PANE_OPEN, PANE_CLOSE and BELL the rect is (l, t, r, b); for TOUCH the point is (rect.x,
 * rect.y); for PAGE_CHANGE the direction (-1 or +1) is rect.x.</p>
 */
final class MomentAgsl {

    private MomentAgsl() {}

    static final String HEAD =
        "uniform float2 uResolution;\n" +
        "uniform float uTime;\n" +
        "uniform float uPhase;\n" +
        "uniform float uEnergy;\n" +
        "uniform float uDim;\n" +
        "layout(color) uniform half4 uPalette0;\n" +
        "layout(color) uniform half4 uPalette1;\n" +
        "layout(color) uniform half4 uPalette2;\n" +
        "layout(color) uniform half4 uPalette3;\n" +
        "uniform float4 uMomentRect0;\n" +
        "uniform float2 uMomentState0;\n" +
        "uniform float4 uMomentRect1;\n" +
        "uniform float2 uMomentState1;\n" +
        "const float PI = 3.14159265;\n" +
        "const float TAU = 6.2831853;\n" +
        "float hash21(float2 p) {\n" +
        "    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);\n" +
        "}\n" +
        "float hash11(float n) {\n" +
        "    return fract(sin(n * 12.9898) * 43758.5453);\n" +
        "}\n" +
        "float vnoise(float2 p) {\n" +
        "    float2 i = floor(p);\n" +
        "    float2 f = fract(p);\n" +
        "    f = f * f * (3.0 - 2.0 * f);\n" +
        "    float a = hash21(i);\n" +
        "    float b = hash21(i + float2(1.0, 0.0));\n" +
        "    float c = hash21(i + float2(0.0, 1.0));\n" +
        "    float d = hash21(i + float2(1.0, 1.0));\n" +
        "    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);\n" +
        "}\n" +
        "float easeOut(float t) {\n" +
        "    float u = 1.0 - clamp(t, 0.0, 1.0);\n" +
        "    return 1.0 - u * u * u;\n" +
        "}\n" +
        // Page change: the flow is nudged along the slide, out and back, eased.
        "float2 warpOne(float4 r, float2 s, float2 p) {\n" +
        "    if (s.x > 2.5 && s.x < 3.5) {\n" +
        "        float bump = sin(PI * easeOut(s.y));\n" +
        "        float shift = r.x * bump * 0.08 * uResolution.x;\n" +
        "        p.x -= shift * (0.75 + 0.25 * sin(p.y / uResolution.y * 6.0));\n" +
        "    }\n" +
        "    return p;\n" +
        "}\n" +
        "float2 momWarp(float2 p) {\n" +
        "    return warpOne(uMomentRect1, uMomentState1, warpOne(uMomentRect0, uMomentState0, p));\n" +
        "}\n" +
        // Ring waves, touch ripple and bell glow, 0..1 each.
        "float liftOne(float4 r, float2 s, float2 p) {\n" +
        "    float k = s.x;\n" +
        "    float g = clamp(s.y, 0.0, 1.0);\n" +
        "    float H = uResolution.y;\n" +
        "    float v = 0.0;\n" +
        "    if (k > 0.5 && k < 2.5) {\n" +
        "        float2 c = (r.xy + r.zw) * 0.5;\n" +
        "        float2 h = (r.zw - r.xy) * 0.5;\n" +
        "        float maxR = max(h.x, h.y) + 0.3 * H;\n" +
        "        float R = k < 1.5 ? g * maxR : (1.0 - g) * maxR;\n" +
        "        float d = (length(p - c) - R) / (0.05 * H);\n" +
        "        v = exp(-d * d) * sin(PI * g);\n" +
        "    } else if (k > 3.5 && k < 4.5) {\n" +
        "        float d = (length(p - r.xy) - g * 0.3 * H) / (0.018 * H);\n" +
        "        v = exp(-d * d) * (1.0 - g);\n" +
        "    } else if (k > 4.5 && k < 5.5) {\n" +
        "        float2 c = (r.xy + r.zw) * 0.5;\n" +
        "        float2 q = abs(p - c) - (r.zw - r.xy) * 0.5;\n" +
        "        float sd = length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0);\n" +
        "        v = (1.0 - smoothstep(0.0, 0.06 * H, sd)) * (1.0 - g) * smoothstep(0.0, 0.12, g) * 0.9;\n" +
        "    }\n" +
        "    return v;\n" +
        "}\n" +
        "float momLift(float2 p) {\n" +
        "    float v = liftOne(uMomentRect0, uMomentState0, p) + liftOne(uMomentRect1, uMomentState1, p);\n" +
        "    return min(v, 1.0) * 0.15;\n" +
        "}\n";

    static final String TAIL =
        "half4 main(float2 p0) {\n" +
        "    float2 p = momWarp(p0);\n" +
        "    float3 c = scene(p);\n" +
        "    float lift = momLift(p0);\n" +
        "    float3 acc = float3(uPalette2.rgb);\n" +
        "    c += lift * (c * 0.6 + acc * 0.6);\n" +
        "    c = min(c, float3(0.6, 0.6, 0.6));\n" +
        "    c += (hash21(p0) - 0.5) / 255.0;\n" +
        "    c *= 1.0 - clamp(uDim, 0.0, 1.0);\n" +
        "    return half4(half3(max(c, float3(0.0, 0.0, 0.0))), 1.0);\n" +
        "}\n";

    /** Formats a Java float as an AGSL float literal. */
    static String lit(float v) {
        String s = Float.toString(v);
        return s.indexOf('.') >= 0 || s.indexOf('E') >= 0 ? s : s + ".0";
    }

    static String assemble(float period, String scene) {
        return HEAD + "const float PERIOD = " + lit(period) + ";\n" + scene + TAIL;
    }
}
