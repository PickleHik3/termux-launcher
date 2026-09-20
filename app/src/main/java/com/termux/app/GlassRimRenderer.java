package com.termux.app;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;

/**
 * Shared glass-rim border for elevated surfaces: a hairline base stroke, a top-edge light that
 * sells the "lit glass edge", and an optional shimmer — one bright band swept once around the
 * border, driven by a transition's progress so the animated edges glint while they move.
 *
 * <p>Allocation-free per frame: the shaders are built once and repositioned with a local matrix,
 * so a 1:1 drag can redraw the rim on every frame.
 */
public final class GlassRimRenderer {
    /**
     * The rim as it was authored: white light at three strengths, for glass standing on something
     * dark. Seeds rather than answers — the colours actually painted come from
     * {@link ChromeShade}, which restates them as shadow when the chrome is standing on a light
     * band. A white hairline on the light-mode glass separates by 1.26, which is the containing
     * edge of the drawer plane, the dock capsule, every anchored menu and every terminal pane all
     * being invisible at once.
     */
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
    private boolean mUniformLight;

    /** The three seeds as {@link ChromeShade} last restated them, and the snapshot they came from. */
    private int mShadeBase = BASE_COLOR;
    private int mShadeLightTop = LIGHT_TOP_COLOR;
    private int mShadeShimmer = SHIMMER_COLOR;
    @Nullable private ChromeInk.Polarity mShadePolarity;
    private int mShadeGlass;
    private boolean mShadeRead;

    /**
     * How wide the rim's stroke is at this density. Painted just inside the bounds, so this is
     * also how far in from a glass surface's bounding box its visible border line sits — which is
     * what anything lining up against that border (a corner tab) has to start past.
     */
    public static float strokePx(float density) {
        return Math.max(1f, 1.25f * density);
    }

    public GlassRimRenderer(float density) {
        strokePx = strokePx(density);
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(strokePx);
        readShade();
        basePaint.setColor(tinted(BASE_COLOR));
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
     * Re-reads {@link ChromeShade}'s snapshot when it has moved, and says whether it had.
     *
     * <p>Polled on every draw rather than pushed: a rim renderer lives as long as the view that
     * owns it, the snapshot changes on a theme, palette or wallpaper change, and a reference
     * comparison plus an int comparison is cheaper than a listener registry. The three seeds are
     * resolved once per change, not per frame — the search inside {@link ChromeShade} walks alpha
     * one step at a time.</p>
     */
    private boolean readShade() {
        ChromeInk.Polarity polarity = ChromeShade.polarity();
        int glass = ChromeShade.nominalGlass();
        if (mShadeRead && polarity == mShadePolarity && glass == mShadeGlass) return false;
        mShadePolarity = polarity;
        mShadeGlass = glass;
        mShadeRead = true;
        mShadeBase = ChromeShade.rim(BASE_COLOR);
        mShadeLightTop = ChromeShade.rim(LIGHT_TOP_COLOR);
        mShadeShimmer = ChromeShade.rim(SHIMMER_COLOR);
        return true;
    }

    /** The seed as the chrome's polarity restates it; the plain white edge is only one of two. */
    private int shaded(int seed) {
        if (seed == BASE_COLOR) return mShadeBase;
        if (seed == LIGHT_TOP_COLOR) return mShadeLightTop;
        if (seed == SHIMMER_COLOR) return mShadeShimmer;
        return seed;
    }

    /**
     * One even stroke instead of the lit top edge. A rim that doubles as a focus indicator has to
     * read the same all the way round: with the gradient, the bottom half was carried by the base
     * stroke alone and all but vanished on an unfocused pane.
     */
    public void setUniformLight(boolean uniform) {
        mUniformLight = uniform;
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
        if (mTint == 0) return shaded(color);
        int alpha = color >>> 24;
        if (color == BASE_COLOR) alpha = TINTED_BASE_ALPHA;
        else if (color == LIGHT_TOP_COLOR) alpha = TINTED_LIGHT_ALPHA;
        // A tinted rim keeps its hue — that hue is what it is saying — so only its alpha is
        // allowed to climb, and only when the chrome is standing on the light band its Material
        // role was never checked against.
        return ChromeShade.tinted((alpha << 24) | (mTint & 0x00FFFFFF), ChromeShade.TARGET_RIM);
    }

    /** The alpha a plain (untinted) rim is drawn at, once the polarity has had its say. */
    private int shadedAlpha(int seed) {
        return shaded(seed) >>> 24;
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
        if (readShade()) {
            // The polarity moved under us: the base colour and both baked shaders are stale.
            basePaint.setColor(tinted(BASE_COLOR));
            lightShaderHeight = -1;
            shimmerShaderBuilt = false;
            shimmerPaint.setShader(null);
        }
        float inset = strokePx / 2f;
        rect.set(left + inset, top + inset, right - inset, bottom - inset);
        float radius = Math.max(0f, radiusPx - inset);

        // Paint.setAlpha replaces the colour's own alpha channel, so the tinted strengths have to
        // be re-stated here — setColor(tinted(...)) alone was silently flattened back to the plain
        // white strength on every draw.
        int baseStrength = mUniformLight
            ? (mTint != 0 ? (tinted(LIGHT_TOP_COLOR) >>> 24) : shadedAlpha(LIGHT_TOP_COLOR))
            : (mTint != 0 ? (tinted(BASE_COLOR) >>> 24) : shadedAlpha(BASE_COLOR));
        basePaint.setAlpha(Math.round(baseStrength * a));
        canvas.drawRoundRect(rect, radius, radius, basePaint);

        if (!mUniformLight) {
            int lightHeight = Math.max(1, Math.round(rect.height() * 0.55f));
            if (lightShaderHeight != lightHeight) {
                lightShaderHeight = lightHeight;
                int lit = tinted(LIGHT_TOP_COLOR);
                lightPaint.setShader(new LinearGradient(0f, 0f, 0f, lightHeight,
                    lit, lit & 0x00FFFFFF, Shader.TileMode.CLAMP));
            }
            Shader light = lightPaint.getShader();
            if (light != null) {
                shaderMatrix.setTranslate(0f, rect.top);
                light.setLocalMatrix(shaderMatrix);
            }
            lightPaint.setAlpha(Math.round(255 * a));
            canvas.drawRoundRect(rect, radius, radius, lightPaint);
        }

        if (!(shimmerPhase >= 0f && shimmerPhase < 1f)) return;
        if (!shimmerShaderBuilt) {
            shimmerShaderBuilt = true;
            int glint = shaded(SHIMMER_COLOR);
            shimmerPaint.setShader(new SweepGradient(0f, 0f,
                new int[] {glint & 0x00FFFFFF, glint, glint & 0x00FFFFFF},
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
