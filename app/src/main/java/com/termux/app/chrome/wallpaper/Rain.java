package com.termux.app.chrome.wallpaper;

/**
 * Sparse glyph-cell rain: blocky procedural cells (no font) fall in a minority of columns and
 * fade into a trail. At rest the heads hang where their columns start, so the picture is still.
 */
final class Rain implements AnimatedWallpaper {

    static final float PERIOD = 60f;

    private static final String SCENE =
        "float3 scene(float2 p) {\n" +
        "    float cell = uResolution.y / 70.0;\n" +
        "    float2 g = floor(p / cell);\n" +
        "    float2 f = fract(p / cell);\n" +
        "    float rows = floor(uResolution.y / cell);\n" +
        "    float hc = hash11(g.x * 1.37 + 0.5);\n" +
        "    float live = step(0.62, hash11(g.x * 2.71 + 9.0));\n" +
        // whole cycles per period, 3..7, so the loop closes exactly
        "    float cycles = 3.0 + floor(hash11(g.x * 5.13 + 2.0) * 5.0);\n" +
        "    float t = mod(uTime, PERIOD) / PERIOD * uEnergy;\n" +
        "    float head = fract(hc + cycles * t);\n" +
        "    float d = fract(head - (g.y + 0.5) / rows);\n" +
        "    float trail = exp(-d * 7.0) * live;\n" +
        "    float id = hash21(g);\n" +
        "    float2 sub = floor(f * float2(3.0, 4.0));\n" +
        "    float bits = step(0.45, hash21(sub + id * 37.0));\n" +
        "    float inset = step(0.14, f.x) * step(f.x, 0.86) * step(0.10, f.y) * step(f.y, 0.90);\n" +
        "    float glyph = bits * inset;\n" +
        "    float isHead = step(d, 0.9 / rows);\n" +
        "    float3 col = float3(0.006, 0.010, 0.010) + 0.05 * float3(uPalette3.rgb);\n" +
        "    col += glyph * trail * 0.30 * float3(uPalette1.rgb);\n" +
        "    col += glyph * isHead * live * 0.22 * float3(uPalette2.rgb);\n" +
        "    col += 0.035 * inset * step(0.7, id) * float3(uPalette0.rgb);\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFF1C4B36, 0xFF2E9E6A, 0xFF8FE3B4, 0xFF040A08};

    @Override public String id() { return "rain"; }
    @Override public String label() { return "Rain"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
