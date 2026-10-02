package com.termux.app.chrome.wallpaper;

/**
 * A lava lamp: five soft metaballs rising and sinking on closed paths, merging where they meet,
 * with a warm rim and a hotter core. The blobs take the palette by their share of the field.
 */
final class Lava implements AnimatedWallpaper {

    static final float PERIOD = 100f;

    private static final String SCENE =
        "float blob(float2 s, float2 c, float r) {\n" +
        "    float2 d = s - c;\n" +
        "    return r * r / (dot(d, d) + 0.0001);\n" +
        "}\n" +
        // s: x in 0..W, y in 0..1 top-down. Each centre is its rest spot plus an energy-scaled orbit.
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution.y;\n" +
        "    float W = uResolution.x / uResolution.y;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float e = uEnergy;\n" +
        "    float b0 = blob(s, float2(W * 0.22 + e * 0.05 * sin(ph + 0.5), 0.30 + e * 0.22 * sin(ph)), 0.13);\n" +
        "    float b1 = blob(s, float2(W * 0.48 + e * 0.06 * sin(2.0 * ph + 1.0), 0.62 + e * 0.26 * sin(ph + 2.1)), 0.16);\n" +
        "    float b2 = blob(s, float2(W * 0.76 + e * 0.05 * cos(ph + 2.0), 0.40 + e * 0.24 * sin(2.0 * ph + 4.0)), 0.12);\n" +
        "    float b3 = blob(s, float2(W * 0.34 + e * 0.07 * cos(2.0 * ph), 0.86 + e * 0.10 * sin(ph + 3.3)), 0.11);\n" +
        "    float b4 = blob(s, float2(W * 0.62 + e * 0.04 * sin(ph + 5.0), 0.13 + e * 0.09 * cos(ph + 1.7)), 0.09);\n" +
        "    float f = b0 + b1 + b2 + b3 + b4;\n" +
        "    float3 tint = (b0 * float3(uPalette0.rgb) + b1 * float3(uPalette1.rgb) + b2 * float3(uPalette0.rgb)\n" +
        "                 + b3 * float3(uPalette2.rgb) + b4 * float3(uPalette1.rgb)) / f;\n" +
        "    float body = smoothstep(0.9, 1.2, f);\n" +
        "    float rim = smoothstep(0.5, 0.95, f) * (1.0 - body);\n" +
        "    float core = smoothstep(1.6, 4.0, f);\n" +
        "    float3 col = mix(0.10 * float3(uPalette3.rgb), 0.04 * float3(uPalette3.rgb), s.y) + 0.006;\n" +
        "    col += tint * (0.08 * rim + 0.20 * body + 0.10 * core);\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFFE0673A, 0xFFC23A5A, 0xFFF0A040, 0xFF1A0A10};

    @Override public String id() { return "lava"; }
    @Override public String label() { return "Lava"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
