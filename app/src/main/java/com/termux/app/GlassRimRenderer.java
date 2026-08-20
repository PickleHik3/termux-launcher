package com.termux.app;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import androidx.annotation.NonNull;

/**
 * Shared glass-rim border for elevated surfaces: a hairline base stroke, a top-edge light that
 * sells the "lit glass edge", and an optional shimmer — one bright band swept once around the
 * border, driven by a transition's progress so the animated edges glint while they move.
 *
 * <p>Allocation-free per frame: the shaders are built once and repositioned with a local matrix,
 * so a 1:1 drag can redraw the rim on every frame.
 */
public final class GlassRimRenderer {
    private static final int BASE_COLOR = 0x3DFFFFFF;
    private static final int LIGHT_TOP_COLOR = 0x7DFFFFFF;
    private static final int SHIMMER_COLOR = 0xC8FFFFFF;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final float strokePx;
    private int lightShaderHeight = -1;
    private boolean shimmerShaderBuilt;

    public GlassRimRenderer(float density) {
        strokePx = Math.max(1f, 1.25f * density);
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(strokePx);
        basePaint.setColor(BASE_COLOR);
        lightPaint.setStyle(Paint.Style.STROKE);
        lightPaint.setStrokeWidth(strokePx);
        shimmerPaint.setStyle(Paint.Style.STROKE);
        shimmerPaint.setStrokeWidth(strokePx * 1.4f);
    }

    /** Colour the rim takes instead of white light, or 0 for the plain glass edge. */
    private int mTint;

    /**
     * Tint the rim toward a Material role. The elevated surfaces want plain white light — that is
     * what glass does — but the terminal's panes use their rim as the focus indicator, so theirs
     * has to be a colour the user can read focus from at a glance. Alphas are kept; only the hue
     * changes, so a tinted rim is still an edge highlight rather than a drawn stroke.
     */
    public void setTint(int tint) {
        if (mTint == tint) return;
        mTint = tint;
        basePaint.setColor(tinted(BASE_COLOR));
        lightShaderHeight = -1;   // the light gradient bakes the colour, so rebuild it
    }

    /**
     * A tinted rim also carries weight the white one does not need. White light at 24% alpha reads
     * as an edge on any wallpaper; a hue at the same alpha reads as almost nothing over a wallpaper
     * of a similar colour, which is useless for a focus indicator — so the tinted rim gets its own
     * stronger alphas, and the caller separates focused from unfocused with drawable alpha on top.
     */
    private static final int TINTED_BASE_ALPHA = 0xB0;
    private static final int TINTED_LIGHT_ALPHA = 0xE6;

    private int tinted(int color) {
        if (mTint == 0) return color;
        int alpha = color >>> 24;
        if (color == BASE_COLOR) alpha = TINTED_BASE_ALPHA;
        else if (color == LIGHT_TOP_COLOR) alpha = TINTED_LIGHT_ALPHA;
        return (alpha << 24) | (mTint & 0x00FFFFFF);
    }

    /**
     * @param shimmerPhase in [0, 1) sweeps the highlight once around the border; anything else
     *                     draws no shimmer (pass -1 for a settled surface)
     * @param alpha        overall rim opacity, 0..1
     */
    public void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom,
                     float radiusPx, float shimmerPhase, float alpha) {
        float a = Float.isFinite(alpha) ? Math.max(0f, Math.min(1f, alpha)) : 0f;
        if (right - left <= 2f || bottom - top <= 2f || a <= 0f) return;
        float inset = strokePx / 2f;
        rect.set(left + inset, top + inset, right - inset, bottom - inset);
        float radius = Math.max(0f, radiusPx - inset);

        basePaint.setAlpha(Math.round((BASE_COLOR >>> 24) * a));
        canvas.drawRoundRect(rect, radius, radius, basePaint);

        int lightHeight = Math.max(1, Math.round(rect.height() * 0.55f));
        if (lightShaderHeight != lightHeight) {
            lightShaderHeight = lightHeight;
            lightPaint.setShader(new LinearGradient(0f, 0f, 0f, lightHeight,
                tinted(LIGHT_TOP_COLOR), tinted(0x00FFFFFF), Shader.TileMode.CLAMP));
        }
        Shader light = lightPaint.getShader();
        if (light != null) {
            shaderMatrix.setTranslate(0f, rect.top);
            light.setLocalMatrix(shaderMatrix);
        }
        lightPaint.setAlpha(Math.round(255 * a));
        canvas.drawRoundRect(rect, radius, radius, lightPaint);

        if (!(shimmerPhase >= 0f && shimmerPhase < 1f)) return;
        if (!shimmerShaderBuilt) {
            shimmerShaderBuilt = true;
            shimmerPaint.setShader(new SweepGradient(0f, 0f,
                new int[] {0x00FFFFFF, SHIMMER_COLOR, 0x00FFFFFF},
                new float[] {0.44f, 0.5f, 0.56f}));
        }
        Shader shimmer = shimmerPaint.getShader();
        if (shimmer == null) return;
        // The band eases in and out over the sweep so neither transition endpoint pops.
        float envelope = (float) Math.sin(Math.PI * shimmerPhase);
        shaderMatrix.setRotate(-90f + 360f * shimmerPhase);
        shaderMatrix.postTranslate(rect.centerX(), rect.centerY());
        shimmer.setLocalMatrix(shaderMatrix);
        shimmerPaint.setAlpha(Math.round(255 * a * envelope));
        canvas.drawRoundRect(rect, radius, radius, shimmerPaint);
    }
}
