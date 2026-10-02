package com.termux.app.terminal;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;

/**
 * Retro monitor looks for a pane card (API 33+): scanlines, curvature and phosphor tint, or a TFT
 * pixel grid. Each {@link PaneContentFrame} owns one instance, which compiles each shader family
 * once and only re-writes uniforms (and re-wraps the effect) when the size or style moves.
 */
public final class PaneRetroEffect {

    private static final String LOG_TAG = "PaneRetroEffect";

    /** Both programs declare the same uniforms so one binder sets them. */
    static final String CRT_AGSL =
        "uniform shader content;\n"
        + "uniform float2 uSize;\n"
        + "uniform float uDensity;\n"
        + "uniform half4 uTint;\n"
        + "const float PI = 3.14159265;\n"
        + "half4 main(float2 p) {\n"
        + "    float2 c = p / uSize * 2.0 - 1.0;\n"
        + "    c *= 1.0 + 0.045 * dot(c, c);\n"
        + "    float2 q = (c * 0.5 + 0.5) * uSize;\n"
        + "    float edge = smoothstep(0.0, 2.0, min(min(q.x, uSize.x - q.x), min(q.y, uSize.y - q.y)));\n"
        + "    float2 sp = clamp(q, float2(0.5, 0.5), uSize - 0.5);\n"
        + "    float g = 1.5 * uDensity;\n"
        + "    half4 col = content.eval(sp);\n"
        + "    half4 glow = (content.eval(sp + float2(g, 0.0)) + content.eval(sp - float2(g, 0.0))\n"
        + "                + content.eval(sp + float2(0.0, g)) + content.eval(sp - float2(0.0, g))) * 0.25;\n"
        + "    float3 rgb = float3(col.rgb) + 0.35 * float3(glow.rgb);\n"
        + "    float lum = dot(rgb, float3(0.299, 0.587, 0.114));\n"
        + "    rgb = mix(rgb, lum * float3(uTint.rgb) * 1.3, float(uTint.a));\n"
        + "    float scan = mix(0.70, 1.0, 0.5 + 0.5 * cos(2.0 * PI * sp.y / (3.0 * uDensity)));\n"
        + "    float vig = 1.0 - 0.22 * dot(c, c);\n"
        + "    float a = float(col.a) * edge;\n"
        + "    rgb = clamp(rgb * scan * vig * edge, 0.0, a);\n"
        + "    return half4(half3(rgb), half(a));\n"
        + "}\n";

    static final String TFT_AGSL =
        "uniform shader content;\n"
        + "uniform float2 uSize;\n"
        + "uniform float uDensity;\n"
        + "uniform half4 uTint;\n"
        + "half4 main(float2 p) {\n"
        + "    half4 col = content.eval(p);\n"
        + "    float2 f = fract(p / (3.0 * uDensity));\n"
        + "    float gap = max(step(f.x, 0.22), step(f.y, 0.22));\n"
        + "    return half4(col.rgb * half(1.0 - 0.45 * gap), col.a);\n"
        + "}\n";

    private Program mProgram;
    private boolean mFailed;

    /** True on phones that run an AGSL shader at all. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean available() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /**
     * The effect for {@code style} at this size, or null for no effect (NONE, unsupported phone,
     * empty size, or a shader that failed to compile).
     */
    @Nullable
    public RenderEffect effectFor(@NonNull PaneRetroStyle style, float width, float height, float density) {
        if (style == PaneRetroStyle.NONE || !available() || mFailed || width <= 0f || height <= 0f)
            return null;
        try {
            if (mProgram == null) mProgram = new Program();
            return mProgram.build(style, width, height, density);
        } catch (RuntimeException e) {
            mFailed = true;
            Logger.logError(LOG_TAG, "retro shader failed, effect disabled: " + e);
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Program {
        private RuntimeShader mCrt;
        private RuntimeShader mTft;

        RenderEffect build(PaneRetroStyle style, float w, float h, float density) {
            RuntimeShader shader;
            if (style.usesCrt()) {
                if (mCrt == null) mCrt = new RuntimeShader(CRT_AGSL);
                shader = mCrt;
            } else {
                if (mTft == null) mTft = new RuntimeShader(TFT_AGSL);
                shader = mTft;
            }
            float[] tint = style.tint();
            shader.setFloatUniform("uSize", w, h);
            shader.setFloatUniform("uDensity", density);
            shader.setFloatUniform("uTint", tint[0], tint[1], tint[2], tint[3]);
            return RenderEffect.createRuntimeShaderEffect(shader, "content");
        }
    }
}
