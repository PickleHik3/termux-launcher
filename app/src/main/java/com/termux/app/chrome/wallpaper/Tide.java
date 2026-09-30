package com.termux.app.chrome.wallpaper;

/** Soft horizontal swells: two layers of slowly rolling crest lines over a deep-to-shallow gradient. */
final class Tide implements AnimatedWallpaper {

    static final float PERIOD = 75f;

    private static final String SCENE =
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution.y;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float e = uEnergy;\n" +
        "    float swell = 0.5 + 0.5 * sin(s.y * 3.0 + s.x * 1.2 + e * sin(ph));\n" +
        "    float y1 = s.y * 9.0 + 0.45 * sin(s.x * 2.4 + 1.0 + e * 1.5 * sin(ph));\n" +
        "    float y2 = s.y * 15.0 + 0.35 * sin(s.x * 3.7 + 4.0 + e * 1.2 * sin(2.0 * ph + 1.0));\n" +
        "    float crest1 = smoothstep(0.55, 1.0, 0.5 + 0.5 * sin(y1 * TAU));\n" +
        "    float crest2 = smoothstep(0.65, 1.0, 0.5 + 0.5 * sin(y2 * TAU));\n" +
        "    float depth = smoothstep(0.0, 1.0, s.y);\n" +
        "    float3 col = mix(float3(uPalette3.rgb) * 0.5, float3(uPalette0.rgb) * 0.4, depth) * (0.5 + 0.5 * swell);\n" +
        "    col += 0.20 * crest1 * float3(uPalette1.rgb) * (0.35 + 0.65 * depth);\n" +
        "    col += 0.13 * crest2 * float3(uPalette2.rgb) * (0.35 + 0.65 * depth);\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFF1D5C7A, 0xFF4BA3A8, 0xFF7FC4C0, 0xFF050E16};

    @Override public String id() { return "tide"; }
    @Override public String label() { return "Tide"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
