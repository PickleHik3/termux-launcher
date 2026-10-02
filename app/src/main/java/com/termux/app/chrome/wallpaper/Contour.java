package com.termux.app.chrome.wallpaper;

/**
 * A topographic map: iso-lines of a slowly breathing height field, every fifth line an index
 * contour, on a near-black ground tinted by elevation.
 */
final class Contour implements AnimatedWallpaper {

    static final float PERIOD = 120f;

    private static final String SCENE =
        // The field's two octaves each ride a closed circle in noise space, scaled by uEnergy.
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution.y;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float2 o0 = uEnergy * 0.45 * float2(cos(ph), sin(ph));\n" +
        "    float2 o1 = uEnergy * 0.30 * float2(sin(2.0 * ph + 1.0), cos(ph + 2.0));\n" +
        "    float h = 0.62 * vnoise(s * 1.8 + o0 + float2(4.0, 1.0))\n" +
        "            + 0.38 * vnoise(s * 3.9 + o1 + float2(-2.0, 7.0));\n" +
        "    float v = h * 14.0;\n" +
        "    float f = fract(v);\n" +
        "    float line = 1.0 - smoothstep(0.03, 0.09, min(f, 1.0 - f));\n" +
        "    float major = step(mod(floor(v + 0.5), 5.0), 0.5);\n" +
        "    float3 col = float3(0.008, 0.010, 0.013) + 0.08 * float3(uPalette3.rgb)\n" +
        "               + 0.09 * h * float3(uPalette2.rgb);\n" +
        "    col += line * mix(0.09 * float3(uPalette1.rgb), 0.22 * float3(uPalette0.rgb), major);\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFFC9A66B, 0xFF6F8F7A, 0xFF3C5A6E, 0xFF0D1114};

    @Override public String id() { return "contour"; }
    @Override public String label() { return "Contour"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
