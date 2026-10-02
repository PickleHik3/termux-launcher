package com.termux.app.chrome.wallpaper;

/**
 * A night sky: two layers of sparse stars that twinkle in whole cycles, over a faint nebula that
 * drifts on a closed path. Stars stay put; only their brightness and the gas move.
 */
final class Drift implements AnimatedWallpaper {

    static final float PERIOD = 90f;

    private static final String SCENE =
        // One star per lit cell at a hashed spot; k whole twinkles per period, so the loop closes.
        "float star(float2 p, float cellPx, float seed, float ph) {\n" +
        "    float2 g = floor(p / cellPx);\n" +
        "    float2 f = fract(p / cellPx);\n" +
        "    float h = hash21(g + seed);\n" +
        "    float on = step(0.74, h);\n" +
        "    float2 c = float2(hash21(g + seed + 17.0), hash21(g + seed + 41.0)) * 0.7 + 0.15;\n" +
        "    float r = 0.035 + 0.07 * hash21(g + seed + 5.0);\n" +
        "    float d = length(f - c) / r;\n" +
        "    float k = 1.0 + floor(hash21(g + seed + 9.0) * 3.0);\n" +
        "    float tw = 1.0 - uEnergy * 0.5 * (0.5 + 0.5 * sin(k * ph + h * TAU));\n" +
        "    return on * exp(-d * d) * tw * (0.4 + 0.6 * fract(h * 7.0));\n" +
        "}\n" +
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution.y;\n" +
        "    float H = uResolution.y;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float2 o = uEnergy * 0.35 * float2(cos(ph), sin(ph));\n" +
        "    float n = 0.65 * vnoise(s * 2.3 + o) + 0.35 * vnoise(s * 5.1 - o + 11.0);\n" +
        "    float neb = smoothstep(0.38, 0.85, n);\n" +
        "    float3 col = float3(0.005, 0.006, 0.011) + 0.05 * float3(uPalette3.rgb);\n" +
        "    col += neb * (0.09 * float3(uPalette1.rgb) + 0.06 * float3(uPalette2.rgb) * (1.0 - s.y));\n" +
        "    float3 light = 0.55 + 0.45 * float3(uPalette0.rgb);\n" +
        "    col += 0.34 * star(p, H / 22.0, 0.0, ph) * light;\n" +
        "    col += 0.20 * star(p, H / 46.0, 31.0, ph) * light;\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFFB8C6FF, 0xFF4B3A8C, 0xFF2A6F8F, 0xFF05060C};

    @Override public String id() { return "drift"; }
    @Override public String label() { return "Drift"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
