package com.termux.app.terminal;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * An additive glow around kitty's trail quad for the Default and Comet styles: about one cell
 * wide, brightest at the cursor end and fading to {@link #TAIL_FACTOR} at the far end. Below
 * Android 13 it draws nothing. The shader compiles once, lazily; per frame only uniforms change.
 */
public final class CursorTrailHalo {

    /** Peak alpha of the halo at the quad's edge nearest the cursor. */
    public static final float PEAK_ALPHA = 0.55f;
    /** Glow strength at the far (tail) end of the quad relative to the cursor end. */
    public static final float TAIL_FACTOR = 0.35f;

    static final String AGSL = ""
        + "uniform float2 uP0;\n"
        + "uniform float2 uP1;\n"
        + "uniform float2 uP2;\n"
        + "uniform float2 uP3;\n"
        + "uniform float2 uHead;\n"
        + "uniform float2 uTail;\n"
        + "uniform float uWidth;\n"
        + "uniform half4 uColor;\n"
        + "uniform float uOpacity;\n"
        + "float edgeDist(float2 p, float2 a, float2 b) {\n"
        + "    float2 e = b - a;\n"
        + "    return (e.x * (p.y - a.y) - e.y * (p.x - a.x)) / max(length(e), 0.001);\n"
        + "}\n"
        + "half4 main(float2 p) {\n"
        + "    float2 e0 = uP1 - uP0;\n"
        + "    float2 e1 = uP2 - uP1;\n"
        + "    float cr = e0.x * e1.y - e0.y * e1.x;\n"
        + "    float s = cr < 0.0 ? 1.0 : -1.0;\n"
        + "    float sd = max(max(s * edgeDist(p, uP0, uP1), s * edgeDist(p, uP1, uP2)),\n"
        + "                   max(s * edgeDist(p, uP2, uP3), s * edgeDist(p, uP3, uP0)));\n"
        + "    float d = max(sd, 0.0);\n"
        + "    float q = d / max(uWidth, 0.001);\n"
        + "    float fall = exp(-2.0 * q * q);\n"
        + "    float2 ht = uHead - uTail;\n"
        + "    float hh = dot(ht, ht);\n"
        + "    float along = hh < 0.0001 ? 1.0\n"
        + "        : clamp(dot(p - uTail, ht) / hh, 0.0, 1.0);\n"
        + "    float k = mix(" + TAIL_FACTOR + ", 1.0, along);\n"
        + "    float a = uColor.a * fall * k * uOpacity;\n"
        + "    return half4(uColor.rgb * half(a), half(a));\n"
        + "}\n";

    private Object mShader;
    private boolean mFailed;
    private final Paint mPaint = new Paint();
    private final float[] mTail = new float[2];
    private final float[] mBounds = new float[4];

    /** True on phones that run an AGSL shader at all. */
    public static boolean available() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /** The quad's bounds (corners as x0,y0..x3,y3) inflated by {@code width} on every side, as l,t,r,b in {@code out}. */
    public static void inflatedBounds(@NonNull float[] pts, float width, @NonNull float[] out) {
        float l = Math.min(Math.min(pts[0], pts[2]), Math.min(pts[4], pts[6]));
        float r = Math.max(Math.max(pts[0], pts[2]), Math.max(pts[4], pts[6]));
        float t = Math.min(Math.min(pts[1], pts[3]), Math.min(pts[5], pts[7]));
        float b = Math.max(Math.max(pts[1], pts[3]), Math.max(pts[5], pts[7]));
        out[0] = l - width;
        out[1] = t - width;
        out[2] = r + width;
        out[3] = b + width;
    }

    /**
     * Centroid of the two corners farthest from the head, written to {@code out[0..1]}.
     * Ties keep the earlier corners.
     */
    public static void tailCentroid(@NonNull float[] pts, float headX, float headY,
                                    @NonNull float[] out) {
        int first = -1, second = -1;
        float d1 = -1f, d2 = -1f;
        for (int i = 0; i < 4; i++) {
            float dx = pts[i * 2] - headX, dy = pts[i * 2 + 1] - headY;
            float d = dx * dx + dy * dy;
            if (d > d1) {
                second = first;
                d2 = d1;
                first = i;
                d1 = d;
            } else if (d > d2) {
                second = i;
                d2 = d;
            }
        }
        out[0] = (pts[first * 2] + pts[second * 2]) / 2f;
        out[1] = (pts[first * 2 + 1] + pts[second * 2 + 1]) / 2f;
    }

    /** Position of (px,py) along tail to head, clamped 0..1; 1 when head and tail coincide. */
    public static float along(float px, float py, float tailX, float tailY,
                              float headX, float headY) {
        float hx = headX - tailX, hy = headY - tailY;
        float hh = hx * hx + hy * hy;
        if (hh < 0.0001f) return 1f;
        float v = ((px - tailX) * hx + (py - tailY) * hy) / hh;
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** The glow strength factor {@code mix(TAIL_FACTOR, 1, along)}. */
    public static float strength(float along) {
        return TAIL_FACTOR + (1f - TAIL_FACTOR) * along;
    }

    /**
     * Draws the halo around the quad {@code pts} behind whatever is drawn next; does nothing
     * below Android 13 or when the shader failed to compile.
     */
    public void draw(@NonNull Canvas canvas, @NonNull float[] pts, float headX, float headY,
                     float width, int color, float opacity) {
        if (!available() || mFailed || width <= 0f || opacity <= 0f) return;
        try {
            drawApi33(canvas, pts, headX, headY, width, color, opacity);
        } catch (RuntimeException e) {
            mFailed = true;
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void drawApi33(Canvas canvas, float[] pts, float headX, float headY, float width,
                           int color, float opacity) {
        RuntimeShader shader = (RuntimeShader) mShader;
        if (shader == null) {
            shader = new RuntimeShader(AGSL);
            mShader = shader;
            mPaint.setShader(shader);
            mPaint.setBlendMode(BlendMode.PLUS);
        }
        tailCentroid(pts, headX, headY, mTail);
        shader.setFloatUniform("uP0", pts[0], pts[1]);
        shader.setFloatUniform("uP1", pts[2], pts[3]);
        shader.setFloatUniform("uP2", pts[4], pts[5]);
        shader.setFloatUniform("uP3", pts[6], pts[7]);
        shader.setFloatUniform("uHead", headX, headY);
        shader.setFloatUniform("uTail", mTail[0], mTail[1]);
        shader.setFloatUniform("uWidth", width);
        shader.setFloatUniform("uColor", ((color >> 16) & 0xff) / 255f,
            ((color >> 8) & 0xff) / 255f, (color & 0xff) / 255f, PEAK_ALPHA);
        shader.setFloatUniform("uOpacity", opacity);
        inflatedBounds(pts, width, mBounds);
        canvas.drawRect(mBounds[0], mBounds[1], mBounds[2], mBounds[3], mPaint);
    }
}
