package com.termux.app.chrome.wallpaper;

/** Three slow curtains of light drifting across a near-black sky, hanging softer below the line. */
final class Aurora implements AnimatedWallpaper {

    static final float PERIOD = 120f;

    private static final String SCENE =
        // s: x in 0..1.5, y in 0..1 top-down. Every phase term is scaled by uEnergy.
        "float ribbon(float2 s, float fi, float ph) {\n" +
        "    float mv = uEnergy;\n" +
        "    float sway = 0.07 * sin(s.x * 2.6 + fi * 1.9 + mv * 0.9 * sin(ph + fi * 2.0))\n" +
        "               + 0.03 * sin(s.x * 6.1 + fi * 4.3 + mv * 1.2 * sin(2.0 * ph + fi));\n" +
        "    float yc = 0.20 + 0.17 * fi + sway + mv * 0.03 * sin(3.0 * ph + fi * 1.3);\n" +
        "    float th = 0.045 + 0.03 * vnoise(float2(s.x * 2.0 + fi * 7.0, fi));\n" +
        "    float d = (s.y - yc) / th;\n" +
        "    d = d > 0.0 ? d * 0.4 : d;\n" +
        "    float streak = 0.65 + 0.35 * vnoise(float2(s.x * 18.0 + mv * 3.0 * sin(ph + fi), fi * 3.0 + s.y * 2.0));\n" +
        "    return exp(-d * d) * streak;\n" +
        "}\n" +
        "float3 scene(float2 p) {\n" +
        "    float2 s = p / uResolution.y;\n" +
        "    float ph = TAU * mod(uTime, PERIOD) / PERIOD;\n" +
        "    float3 base = float3(0.008, 0.010, 0.016) + 0.07 * float3(uPalette3.rgb) * (1.0 - s.y);\n" +
        "    float3 col = base;\n" +
        "    col += 0.42 * ribbon(s, 0.0, ph) * float3(uPalette0.rgb);\n" +
        "    col += 0.38 * ribbon(s, 1.0, ph) * float3(uPalette1.rgb);\n" +
        "    col += 0.32 * ribbon(s, 2.0, ph) * float3(uPalette2.rgb);\n" +
        "    return col;\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.assemble(PERIOD, SCENE);
    private static final int[] OWN = {0xFF2BB673, 0xFF3A86C8, 0xFF7B52B8, 0xFF07130F};

    @Override public String id() { return "aurora"; }
    @Override public String label() { return "Aurora"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }
}
