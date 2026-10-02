package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.termux.view.KittyCursorTrail;

/**
 * Motion-blurred cursor trail, an AGSL port of kitty's {@code cursor-trail-motion-blur.slang}.
 *
 * <p>Copyright (C) 2026 Kovid Goyal &lt;kovid at kovidgoyal.net&gt;<br>
 * Distributed under terms of the GPLv3 license.<br>
 * (Jonathan Lippincott originally contributed the shader, kitty commit 6c4170a682.)
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * <p>Modified for Termux Launcher: translated from Slang to AGSL, fed y-down pixels directly, and
 * drawn as a paint shader over the swept bounding box instead of a full-window pass. The quad
 * between the previous frame's corners and the current ones is sampled 16 times, with
 * anti-aliased triangle coverage, and averaged. The bounding-box early-out is the {@code drawRect}
 * itself. Below API 33 or on a software canvas {@link #draw} returns false and the caller draws
 * the plain quad.
 */
public final class CursorTrailMotionBlur {

    /** The AGSL program. Premultiplied output, transparent outside the swept quad. */
    static final String SHADER =
        "uniform float4 uStartX;\n"
        + "uniform float4 uStartY;\n"
        + "uniform float4 uEndX;\n"
        + "uniform float4 uEndY;\n"
        + "uniform float2 uCursorLo;\n"
        + "uniform float2 uCursorHi;\n"
        + "layout(color) uniform half4 uColor;\n"
        + "uniform float uOpacity;\n"
        + "\n"
        + "float outsideEdge(float2 p, float2 a, float2 b, float orientation) {\n"
        + "    float2 e = b - a;\n"
        + "    float dist = orientation * (e.x * (p.y - a.y) - e.y * (p.x - a.x)) / length(e);\n"
        + "    return 1.0 - clamp(0.5 + dist, 0.0, 1.0);\n"
        + "}\n"
        + "\n"
        + "float triangleCoverage(float2 p, float2 a, float2 b, float2 c) {\n"
        + "    float area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);\n"
        + "    if (abs(area) < 1e-6) return 0.0;\n"
        + "    float orientation = area > 0.0 ? 1.0 : -1.0;\n"
        + "    return clamp(1.0 - outsideEdge(p, a, b, orientation)\n"
        + "        - outsideEdge(p, b, c, orientation)\n"
        + "        - outsideEdge(p, c, a, orientation), 0.0, 1.0);\n"
        + "}\n"
        + "\n"
        + "float trailCoverage(float2 p, float4 xs, float4 ys) {\n"
        + "    float2 c0 = float2(xs.x, ys.x);\n"
        + "    float2 c1 = float2(xs.y, ys.y);\n"
        + "    float2 c2 = float2(xs.z, ys.z);\n"
        + "    float2 c3 = float2(xs.w, ys.w);\n"
        + "    return min(triangleCoverage(p, c0, c1, c2) + triangleCoverage(p, c0, c2, c3), 1.0);\n"
        + "}\n"
        + "\n"
        + "float rectCoverage(float2 p, float2 lo, float2 hi) {\n"
        + "    float2 overlap = clamp(min(p + 0.5, hi) - max(p - 0.5, lo), 0.0, 1.0);\n"
        + "    return overlap.x * overlap.y;\n"
        + "}\n"
        + "\n"
        + "half4 main(float2 p) {\n"
        + "    float coverage = 0.0;\n"
        + "    for (int i = 0; i < 16; i++) {\n"
        + "        float time = (float(i) + 0.5) / 16.0;\n"
        + "        coverage += trailCoverage(p, mix(uStartX, uEndX, time), mix(uStartY, uEndY, time));\n"
        + "    }\n"
        + "    coverage /= 16.0;\n"
        + "    coverage *= 1.0 - rectCoverage(p, uCursorLo, uCursorHi);\n"
        + "    float a = clamp(uOpacity * coverage, 0.0, 1.0);\n"
        + "    return half4(uColor.rgb * half(a), half(a));\n"
        + "}\n";

    /** True on phones that run an AGSL shader at all. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean available() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    private Gpu mGpu;

    /**
     * Draws the blurred sweep from the trail's previous corners to its current ones.
     *
     * @param opacity trail colour alpha times the trail's own fade, 0..1
     * @return false when the shader cannot be used and the caller must draw the plain quad
     */
    public boolean draw(@NonNull Canvas canvas, @NonNull KittyCursorTrail trail, int color,
                        float opacity, float cursorLeft, float cursorTop, float cursorRight,
                        float cursorBottom) {
        if (!available() || !canvas.isHardwareAccelerated()) return false;
        if (mGpu == null) mGpu = new Gpu();
        mGpu.draw(canvas, trail, color, opacity, cursorLeft, cursorTop, cursorRight, cursorBottom);
        return true;
    }

    /** Everything that touches {@link RuntimeShader}, loaded only on API 33+. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Gpu {
        private final RuntimeShader mShader = new RuntimeShader(SHADER);
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Gpu() {
            mPaint.setShader(mShader);
        }

        void draw(Canvas canvas, KittyCursorTrail trail, int color, float opacity,
                  float cursorLeft, float cursorTop, float cursorRight, float cursorBottom) {
            float left = Float.MAX_VALUE, top = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
            for (int i = 0; i < KittyCursorTrail.CORNERS; i++) {
                float cx = trail.cornerX(i), cy = trail.cornerY(i);
                float px = trail.prevCornerX(i), py = trail.prevCornerY(i);
                left = Math.min(left, Math.min(cx, px));
                right = Math.max(right, Math.max(cx, px));
                top = Math.min(top, Math.min(cy, py));
                bottom = Math.max(bottom, Math.max(cy, py));
            }
            mShader.setFloatUniform("uStartX", trail.prevCornerX(0), trail.prevCornerX(1),
                trail.prevCornerX(2), trail.prevCornerX(3));
            mShader.setFloatUniform("uStartY", trail.prevCornerY(0), trail.prevCornerY(1),
                trail.prevCornerY(2), trail.prevCornerY(3));
            mShader.setFloatUniform("uEndX", trail.cornerX(0), trail.cornerX(1),
                trail.cornerX(2), trail.cornerX(3));
            mShader.setFloatUniform("uEndY", trail.cornerY(0), trail.cornerY(1),
                trail.cornerY(2), trail.cornerY(3));
            mShader.setFloatUniform("uCursorLo", cursorLeft, cursorTop);
            mShader.setFloatUniform("uCursorHi", cursorRight, cursorBottom);
            // The shader scales by uOpacity, so the colour goes in opaque.
            mShader.setColorUniform("uColor", color | 0xFF000000);
            mShader.setFloatUniform("uOpacity", opacity);
            canvas.drawRect(left - 1f, top - 1f, right + 1f, bottom + 1f, mPaint);
        }
    }
}
