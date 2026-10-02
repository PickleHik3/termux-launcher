package com.termux.app.chrome.wallpaper;

/**
 * Light on a pool floor: a sine-warped plane folds ridged value noise into a bright net, two
 * scales of it, over a floor that deepens towards the bottom. The warp phases ride closed loops.
 */
final class Caustics implements AnimatedWallpaper {

    static final float PERIOD = 80f;

    private static final String SCENE =
        "float ridge(float2 q) {\n" +
        "    float r = 1.0 - abs(2.0 * vnoise(q) - 1.0);\n" +
        "    r = r * r;\n" +
        "    return r * r * r;\n" +
        "}\n" +
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution.y * 6.0;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float e = uEnergy;\n" +
        "    float2 q = s;\n" +
        "    q += 0.55 * sin(q.yx * 1.3 + float2(1.0, 2.0) + e * 0.9 * float2(sin(ph), cos(ph)));\n" +
        "    q += 0.35 * sin(q.yx * 2.1 + float2(4.0, 0.5) + e * 0.8 * float2(cos(2.0 * ph + 1.0), sin(ph + 3.0)));\n" +
        "    float net = ridge(q) + 0.6 * ridge(q * 1.9 + float2(3.0, 7.0));\n" +
        "    float depth = p.y / uResolution.y;\n" +
        "    float3 col = mix(0.16 * float3(uPalette0.rgb), 0.07 * float3(uPalette3.rgb), depth);\n" +
        "    col += min(net, 1.2) * (0.13 - 0.06 * depth) * mix(float3(uPalette1.rgb), float3(uPalette2.rgb), depth);\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFF1E6E8C, 0xFF7FD6E0, 0xFF3FA0B8, 0xFF04121C};

    @Override public String id() { return "caustics"; }
    @Override public String label() { return "Caustics"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
