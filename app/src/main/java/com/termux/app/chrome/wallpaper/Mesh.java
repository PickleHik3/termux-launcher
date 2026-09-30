package com.termux.app.chrome.wallpaper;

/** Four palette colours pinned to slowly drifting anchors and blended into one dim gradient. */
final class Mesh implements AnimatedWallpaper {

    static final float PERIOD = 90f;

    private static final String SCENE =
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float e = uEnergy * 0.12;\n" +
        "    float2 a0 = float2(0.22 + e * sin(ph), 0.20 + e * cos(2.0 * ph));\n" +
        "    float2 a1 = float2(0.80 + e * cos(ph + 1.0), 0.30 + e * sin(ph + 2.0));\n" +
        "    float2 a2 = float2(0.30 + e * sin(2.0 * ph + 3.0), 0.78 + e * cos(ph));\n" +
        "    float2 a3 = float2(0.75 + e * cos(ph + 4.0), 0.85 + e * sin(2.0 * ph + 1.0));\n" +
        "    float2 k = float2(uResolution.x / uResolution.y, 1.0);\n" +
        "    float2 d0 = (s - a0) * k;\n" +
        "    float2 d1 = (s - a1) * k;\n" +
        "    float2 d2 = (s - a2) * k;\n" +
        "    float2 d3 = (s - a3) * k;\n" +
        "    float w0 = 1.0 / (0.04 + dot(d0, d0));\n" +
        "    float w1 = 1.0 / (0.04 + dot(d1, d1));\n" +
        "    float w2 = 1.0 / (0.04 + dot(d2, d2));\n" +
        "    float w3 = 1.0 / (0.04 + dot(d3, d3));\n" +
        "    float3 c = (w0 * float3(uPalette0.rgb) + w1 * float3(uPalette1.rgb)\n" +
        "              + w2 * float3(uPalette2.rgb) + w3 * float3(uPalette3.rgb)) / (w0 + w1 + w2 + w3);\n" +
        "    float vig = 0.30 + 0.30 * (1.0 - length(s - float2(0.5, 0.5)));\n" +
        "    return c * vig;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFF3B5BA9, 0xFF8A3F7D, 0xFF1F7A72, 0xFF0B1020};

    @Override public String id() { return "mesh"; }
    @Override public String label() { return "Mesh"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
