package com.termux.app.chrome.wallpaper;

/**
 * Folded cloth: two rounds of domain warping fold value noise into soft drapes, with a thin sheen
 * along the folds. The first warp's offsets ride a closed circle, so the folds slide and return.
 */
final class Silk implements AnimatedWallpaper {

    static final float PERIOD = 110f;

    private static final String SCENE =
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution.y * 1.6;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float2 o = uEnergy * 0.5 * float2(cos(ph), sin(ph));\n" +
        "    float2 q = float2(vnoise(s + o), vnoise(s + float2(5.2, 1.3) - o));\n" +
        "    float2 r = float2(vnoise(s + 2.2 * q + float2(1.7, 9.2) + 0.6 * o.yx),\n" +
        "                      vnoise(s + 2.2 * q + float2(8.3, 2.8)));\n" +
        "    float n = vnoise(s + 2.4 * r);\n" +
        "    float3 col = mix(0.22 * float3(uPalette3.rgb), 0.28 * float3(uPalette0.rgb), smoothstep(0.2, 0.8, n));\n" +
        "    col = mix(col, 0.30 * float3(uPalette1.rgb), 0.6 * smoothstep(0.4, 1.0, r.y));\n" +
        "    float sheen = smoothstep(0.65, 0.97, 0.5 + 0.5 * sin(TAU * (2.2 * n + q.x)));\n" +
        "    col += 0.09 * sheen * float3(uPalette2.rgb);\n" +
        "    return col * (0.70 + 0.30 * (1.0 - p.y / uResolution.y));\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFF5A3E78, 0xFF2E4F7A, 0xFFD6B4E8, 0xFF0C0A14};

    @Override public String id() { return "silk"; }
    @Override public String label() { return "Silk"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
