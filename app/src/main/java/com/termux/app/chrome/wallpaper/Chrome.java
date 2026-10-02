package com.termux.app.chrome.wallpaper;

/**
 * Liquid metal: a two-octave sine warp bends banded reflections into molten folds, with a slow
 * diagonal sheen. The warp follows LiquidChromeEffect in mejdi14/Android-AGSL-Shader-Playground
 * (MIT, Copyright (c) 2025 Mejdi Hafiene); here it bends a palette, not a picture, and its phases
 * oscillate on whole cycles so it loops and rests.
 */
final class Chrome implements AnimatedWallpaper {

    static final float PERIOD = 96f;

    private static final String SCENE =
        "float3 scene(float2 p) {\n" +
        "    float2 uv = p / uResolution.y;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float e = uEnergy;\n" +
        "    float t1 = e * 2.0 * sin(ph);\n" +
        "    float t2 = e * 1.6 * sin(2.0 * ph + 1.0);\n" +
        "    float t3 = e * 1.4 * cos(ph + 2.0);\n" +
        "    float wx = sin(uv.y * 4.0 + t1) + 0.5 * sin(uv.y * 9.2 - t2);\n" +
        "    float wy = cos(uv.x * 4.0 - t1) + 0.5 * cos(uv.x * 7.6 + t3);\n" +
        "    float2 q = uv + 0.09 * float2(wx, wy);\n" +
        "    float b = 0.5 + 0.5 * sin((1.3 * q.x + q.y) * 7.0);\n" +
        "    float b2 = 0.5 + 0.5 * sin((q.x - 0.6 * q.y) * 11.0 + 1.7);\n" +
        "    float3 col = mix(0.06 * float3(uPalette3.rgb), 0.30 * float3(uPalette0.rgb), b * b);\n" +
        "    col = mix(col, 0.34 * float3(uPalette1.rgb), 0.5 * smoothstep(0.75, 1.0, b2));\n" +
        "    float sheen = 0.5 + 0.5 * sin((uv.x + uv.y) * 4.0 + 2.0 * t1);\n" +
        "    col += 0.06 * sheen * sheen * sheen * float3(uPalette2.rgb);\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFF8C95A3, 0xFF4E5D73, 0xFFE3E8F0, 0xFF0A0C10};

    @Override public String id() { return "chrome"; }
    @Override public String label() { return "Chrome"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
