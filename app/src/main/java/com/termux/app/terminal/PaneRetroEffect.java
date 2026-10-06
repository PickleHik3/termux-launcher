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
 * Retro monitor looks (API 33+): scanlines, phosphor glow and tint, or a TFT pixel grid, drawn
 * through a view as its render effect. One family of shaders for every surface on the home screen:
 * a pane card bends the picture like the tube it stands for, the chrome draws it flat, and both
 * take their rows from the screen ({@link RetroUniforms#phase}), so a scanline runs on unbroken
 * from the status bar through the panes into the dock and the keyboard.
 *
 * <p>Each owner ({@link RetroEffectBinder}) holds one instance, which compiles each shader family
 * once and only re-writes uniforms (and re-wraps the effect) when what it draws moved.
 */
public final class PaneRetroEffect {

    private static final String LOG_TAG = "PaneRetroEffect";

    /** The CRT's barrel bend on a pane card; the shader and {@link #displayedPoint} share it. */
    static final float CRT_BEND = 0.04f;

    /** How far a pane card's corners darken: light, the corners hold the prompt as often as not. */
    static final float PANE_VIGNETTE = 0.08f;

    /**
     * Where content at ({@code x}, {@code y}) of a {@code w}×{@code h} card shows on screen once
     * {@code style} bends it: the inverse of the CRT shader's sample map, so things drawn above the
     * card (the cursor trail) land on the cell the user sees. Identity for every other style.
     */
    public static void displayedPoint(@NonNull PaneRetroStyle style, float w, float h, float x, float y,
                                      @NonNull float[] out) {
        if (!style.usesCrt() || w <= 0f || h <= 0f) {
            out[0] = x;
            out[1] = y;
            return;
        }
        float sx = x / w * 2f - 1f;
        float sy = y / h * 2f - 1f;
        float cx = sx;
        float cy = sy;
        // Fixed point of c = s / f(c); f stays within 8 % of 1, so a few steps settle it.
        for (int i = 0; i < 5; i++) {
            float f = (1f + CRT_BEND * (cx * cx + cy * cy)) / (1f + 2f * CRT_BEND);
            cx = sx / f;
            cy = sy / f;
        }
        out[0] = (cx + 1f) * 0.5f * w;
        out[1] = (cy + 1f) * 0.5f * h;
    }

    /** The period every shader takes its rows and cells from, in px: {@link RetroUniforms#PERIOD_DP}. */
    private static final String PERIOD = "(" + RetroUniforms.PERIOD_DP + " * uDensity)";

    /** Both programs declare the same uniforms so one binder sets them. */
    private static final String HEAD =
        "uniform shader content;\n"
        + "uniform float2 uSize;\n"
        // Where this view's 0 falls in the screen's period (RetroUniforms#phase): added to every
        // coordinate before the period is taken, so one row of the screen is one row of every
        // surface on it.
        + "uniform float2 uOrigin;\n"
        + "uniform float uDensity;\n"
        // The card, left/top/right/bottom, and its corners' radius.
        + "uniform float4 uCard;\n"
        + "uniform float uRadius;\n"
        + "uniform half4 uTint;\n"
        + "uniform float uBend;\n"
        + "uniform float uVignette;\n"
        // The card's own rounded corners, redrawn here: a view's outline clip does not reach what
        // its own render effect puts out. A container whose children clip themselves is cut to
        // its bounds, which this leaves whole.
        + "float cardMask(float2 p) {\n"
        + "    float2 h = (uCard.zw - uCard.xy) * 0.5;\n"
        + "    float2 q = abs(p - uCard.xy - h) - (h - uRadius);\n"
        + "    float d = length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - uRadius;\n"
        + "    return 1.0 - smoothstep(-0.5, 0.5, d);\n"
        + "}\n";

    static final String CRT_AGSL = HEAD
        + "const float PI = 3.14159265;\n"
        + "half4 main(float2 p) {\n"
        + "    float2 c = p / uSize * 2.0 - 1.0;\n"
        // Barrel bend normalised so the corners stay put: every cell still lands on the card. Flat
        // (uBend 0) on the chrome, whose keys and icons have to stay under the finger.
        + "    float2 w = c * (1.0 + uBend * dot(c, c)) / (1.0 + 2.0 * uBend);\n"
        + "    float2 sp = clamp((w * 0.5 + 0.5) * uSize, float2(0.5, 0.5), uSize - 0.5);\n"
        + "    float g = 1.5 * uDensity;\n"
        + "    half4 col = content.eval(sp);\n"
        + "    half4 glow = (content.eval(sp + float2(g, 0.0)) + content.eval(sp - float2(g, 0.0))\n"
        + "                + content.eval(sp + float2(0.0, g)) + content.eval(sp - float2(0.0, g))) * 0.25;\n"
        + "    float3 rgb = float3(col.rgb) + 0.35 * float3(glow.rgb);\n"
        + "    float lum = dot(rgb, float3(0.299, 0.587, 0.114));\n"
        + "    rgb = mix(rgb, lum * float3(uTint.rgb) * 1.3, float(uTint.a));\n"
        // The screen's own rows, straight, as kitty draws them: the bend moves the picture, never
        // the scanlines, so they run on into the surface beside this one.
        + "    float scan = mix(0.70, 1.0, 0.5 + 0.5 * cos(2.0 * PI * (p.y + uOrigin.y) / " + PERIOD + "));\n"
        + "    float vig = 1.0 - uVignette * dot(c, c);\n"
        + "    float m = cardMask(p);\n"
        + "    float a = float(col.a) * m;\n"
        + "    rgb = clamp(rgb * scan * vig * m, 0.0, a);\n"
        + "    return half4(half3(rgb), half(a));\n"
        + "}\n";

    static final String TFT_AGSL = HEAD
        + "half4 main(float2 p) {\n"
        + "    half4 col = content.eval(p);\n"
        + "    float2 f = fract((p + uOrigin) / " + PERIOD + ");\n"
        + "    float gap = max(step(f.x, 0.22), step(f.y, 0.22));\n"
        + "    float m = cardMask(p);\n"
        + "    return half4(col.rgb * half((1.0 - 0.45 * gap) * m), col.a * half(m));\n"
        + "}\n";

    private Program mProgram;
    private boolean mFailed;

    /** True on phones that run an AGSL shader at all. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean available() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /**
     * The effect for {@code style} drawn with {@code uniforms}, or null for no effect (NONE,
     * unsupported phone, empty size, or a shader that failed to compile).
     */
    @Nullable
    public RenderEffect effectFor(@NonNull PaneRetroStyle style, @NonNull RetroUniforms uniforms) {
        if (style == PaneRetroStyle.NONE || !available() || mFailed
            || uniforms.width <= 0f || uniforms.height <= 0f)
            return null;
        try {
            if (mProgram == null) mProgram = new Program();
            return mProgram.build(style, uniforms);
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

        RenderEffect build(PaneRetroStyle style, RetroUniforms u) {
            RuntimeShader shader;
            if (style.usesCrt()) {
                if (mCrt == null) mCrt = new RuntimeShader(CRT_AGSL);
                shader = mCrt;
            } else {
                if (mTft == null) mTft = new RuntimeShader(TFT_AGSL);
                shader = mTft;
            }
            float[] tint = style.tint();
            shader.setFloatUniform("uSize", u.width, u.height);
            shader.setFloatUniform("uOrigin", u.phaseX, u.phaseY);
            shader.setFloatUniform("uDensity", u.density);
            shader.setFloatUniform("uCard", u.cardLeft, u.cardTop, u.cardRight, u.cardBottom);
            shader.setFloatUniform("uRadius", u.radius);
            shader.setFloatUniform("uTint", tint[0], tint[1], tint[2], tint[3]);
            shader.setFloatUniform("uBend", u.bend);
            shader.setFloatUniform("uVignette", u.vignette);
            // The effect keeps the uniforms it was made with; the next write starts a new copy.
            return RenderEffect.createRuntimeShaderEffect(shader, "content");
        }
    }
}
