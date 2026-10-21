package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The shelf under the Glass tray's floating slab: a soft contact shadow on the wallpaper right
 * under the slab's lower edge, and inside it a faint glow of the light the slab lets out of its
 * lower face ({@link DockGlassRendering#SHELF_SHADOW_ALPHA} and its neighbours). It stands behind
 * the dock's glass host as its sibling, so its pixels outside its own bounds reach the wallpaper
 * under the dock, and it is clipped out of the slab itself.
 *
 * <p>It follows the host it is handed ({@link #follow}): the slab's laid-out bounds and its
 * outline's corner radius whenever the host lays out, and the host's travel and fade on the
 * frames something moves them (the drawer's lift, a press's dip), copied as view properties, so
 * following costs nothing at rest and never redraws the shelf. A tilt of the host alone is not
 * copied: the shadow is on the wallpaper, which does not tip. (With the keyboard down the plank
 * tipped is the whole stack, which holds the shelf too, so the shelf rides its 3 degrees.)</p>
 */
public final class DockShelfView extends View {

    private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mSlab = new RectF();
    private final Path mSlabPath = new Path();
    private final Matrix mScratch = new Matrix();
    private final Outline mOutline = new Outline();
    private float mRadiusPx;
    private boolean mHasSlab;
    /**
     * Lens beads in the host's coordinates, or null for the slab: each bead then wears a small
     * shadow and glow of its own, and the slab none.
     */
    @Nullable private com.termux.app.dock.LensBeadGeometry.Beads mBeads;
    /**
     * One bead's shadow and glow, drawn about x = 0 and moved to each bead as it is drawn, so a
     * row of beads moving with a page swipe rebuilds only their outline, never their paints.
     */
    private final Paint mBeadShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBeadGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The bead radius and lower edge the two paints were built for. */
    private float mBeadPaintRadius = Float.NaN, mBeadPaintBottom = Float.NaN;
    private final Path mBeadPath = new Path();
    @Nullable private View mHost;

    private final View.OnLayoutChangeListener mHostLayout =
        (v, l, t, r, b, ol, ot, or, ob) -> readHostBounds();
    private final ViewTreeObserver.OnPreDrawListener mHostMotion = () -> {
        mirrorHostMotion();
        return true;
    };

    public DockShelfView(Context context) {
        this(context, null);
    }

    public DockShelfView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Stands the shelf under {@code host}'s slab and keeps it there, or lets go of the last host
     * for null. A host that is not this view's sibling is not followed.
     */
    public void follow(@Nullable View host) {
        if (mHost == host) {
            readHostBounds();
            return;
        }
        if (mHost != null) {
            mHost.removeOnLayoutChangeListener(mHostLayout);
            getViewTreeObserver().removeOnPreDrawListener(mHostMotion);
        }
        mHost = host != null && host.getParent() == getParent() ? host : null;
        if (mHost == null) {
            mHasSlab = false;
            invalidate();
            return;
        }
        mHost.addOnLayoutChangeListener(mHostLayout);
        getViewTreeObserver().addOnPreDrawListener(mHostMotion);
        readHostBounds();
        mirrorHostMotion();
    }

    @Override
    protected void onDetachedFromWindow() {
        follow(null);
        super.onDetachedFromWindow();
    }

    /**
     * The host's glass drawn as Lens beads ({@code beadsInHost}, in the host's own coordinates),
     * each with a shadow and glow of its own, or null for the slab. Redrawn only when they moved.
     */
    public void setBeads(@Nullable com.termux.app.dock.LensBeadGeometry.Beads beadsInHost) {
        if (beadsInHost == null ? mBeads == null : beadsInHost.equals(mBeads)) return;
        mBeads = beadsInHost;
        rebuildBeads();
        invalidate();
    }

    /** The beads the shelf shadows, in the host's coordinates; null for the slab. */
    @Nullable
    com.termux.app.dock.LensBeadGeometry.Beads beads() {
        return mBeads;
    }

    /**
     * The beads' outline at the host's current offset, and their one shadow and glow, built
     * again only when the beads' size or line moved.
     */
    private void rebuildBeads() {
        com.termux.app.dock.LensBeadGeometry.Beads beads = mBeads;
        mBeadPath.reset();
        if (beads == null || !mHasSlab) return;
        float density = getResources().getDisplayMetrics().density;
        float dx = mSlab.left;
        float dy = mSlab.top;
        float r = beads.radiusPx;
        float bottom = beads.centerY + dy + r;
        for (int i = 0; i < beads.count(); i++) {
            mBeadPath.addCircle(beads.centerX(i) + dx, beads.centerY + dy, r, Path.Direction.CW);
        }
        if (r == mBeadPaintRadius && bottom == mBeadPaintBottom) return;
        mBeadPaintRadius = r;
        mBeadPaintBottom = bottom;
        float rise = DockGlassRendering.BEAD_SHADOW_RISE_DP * density;
        mBeadShadow.setShader(ellipse(Color.BLACK, DockGlassRendering.BEAD_SHADOW_ALPHA, 0f,
            bottom - rise, r * DockGlassRendering.BEAD_SHADOW_HALF_WIDTH_SHARE,
            DockGlassRendering.BEAD_SHADOW_DEPTH_DP * density + rise));
        mBeadGlow.setShader(ellipse(Color.WHITE, DockGlassRendering.BEAD_GLOW_ALPHA, 0f, bottom,
            r * DockGlassRendering.BEAD_GLOW_HALF_WIDTH_SHARE,
            DockGlassRendering.BEAD_GLOW_DEPTH_DP * density));
    }

    /** The host's laid-out bounds in this view's coordinates, and its outline's corner radius. */
    private void readHostBounds() {
        View host = mHost;
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) {
            setSlab(null, 0f);
            return;
        }
        float radius = 0f;
        ViewOutlineProvider provider = host.getOutlineProvider();
        if (provider != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mOutline.setEmpty();
            provider.getOutline(host, mOutline);
            radius = Math.max(0f, mOutline.getRadius());
        }
        float left = host.getLeft() - getLeft();
        float top = host.getTop() - getTop();
        setSlab(new RectF(left, top, left + host.getWidth(), top + host.getHeight()), radius);
    }

    /** The host's travel and fade as this view's own, compared before each write. */
    private void mirrorHostMotion() {
        View host = mHost;
        if (host == null) return;
        float alpha = host.getVisibility() == View.VISIBLE ? host.getAlpha() : 0f;
        if (getAlpha() != alpha) setAlpha(alpha);
        if (getTranslationX() != host.getTranslationX()) setTranslationX(host.getTranslationX());
        if (getTranslationY() != host.getTranslationY()) setTranslationY(host.getTranslationY());
        float pivotX = host.getLeft() - getLeft() + host.getPivotX();
        float pivotY = host.getTop() - getTop() + host.getPivotY();
        if (getPivotX() != pivotX) setPivotX(pivotX);
        if (getPivotY() != pivotY) setPivotY(pivotY);
        if (getScaleX() != host.getScaleX()) setScaleX(host.getScaleX());
        if (getScaleY() != host.getScaleY()) setScaleY(host.getScaleY());
    }

    /** The slab, in this view's coordinates, or null for none; redrawn only when it moved. */
    void setSlab(@Nullable RectF slab, float radiusPx) {
        if (slab == null) {
            if (!mHasSlab) return;
            mHasSlab = false;
            invalidate();
            return;
        }
        if (mHasSlab && mSlab.equals(slab) && mRadiusPx == radiusPx) return;
        mHasSlab = true;
        mSlab.set(slab);
        mRadiusPx = radiusPx;
        if (mBeads != null) rebuildBeads();
        mSlabPath.reset();
        mSlabPath.addRoundRect(mSlab, radiusPx, radiusPx, Path.Direction.CW);
        float density = getResources().getDisplayMetrics().density;
        float rise = DockGlassRendering.SHELF_SHADOW_RISE_DP * density;
        mShadow.setShader(ellipse(Color.BLACK, DockGlassRendering.SHELF_SHADOW_ALPHA,
            mSlab.centerX(), mSlab.bottom - rise,
            DockGlassRendering.shelfShadowHalfWidthPx(mSlab.width(), radiusPx),
            DockGlassRendering.SHELF_SHADOW_DEPTH_DP * density + rise));
        mGlow.setShader(ellipse(Color.WHITE, DockGlassRendering.SHELF_GLOW_ALPHA,
            mSlab.centerX(), mSlab.bottom,
            DockGlassRendering.shelfGlowHalfWidthPx(mSlab.width(), radiusPx),
            DockGlassRendering.SHELF_GLOW_DEPTH_DP * density));
        invalidate();
    }

    /** A radial falloff of {@code color} stretched into an ellipse at (cx, cy) with radii (rx, ry). */
    @NonNull
    private Shader ellipse(int color, float alpha, float cx, float cy, float rx, float ry) {
        RadialGradient gradient = new RadialGradient(0f, 0f, 1f,
            DockGlassRendering.shelfFalloffColors(color, alpha),
            DockGlassRendering.shelfFalloffStops(), Shader.TileMode.CLAMP);
        mScratch.reset();
        mScratch.setScale(Math.max(1f, rx), Math.max(1f, ry));
        mScratch.postTranslate(cx, cy);
        gradient.setLocalMatrix(mScratch);
        return gradient;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!mHasSlab || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        float density = getResources().getDisplayMetrics().density;
        com.termux.app.dock.LensBeadGeometry.Beads beads = mBeads;
        if (beads != null) {
            // Each bead's own shelf, clipped out of every bead's glass.
            int save = canvas.save();
            canvas.clipOutPath(mBeadPath);
            float r = beads.radiusPx;
            float bottom = beads.centerY + mSlab.top + r;
            float rise = DockGlassRendering.BEAD_SHADOW_RISE_DP * density;
            float shadowReach = DockGlassRendering.BEAD_SHADOW_DEPTH_DP * density;
            float glowReach = DockGlassRendering.BEAD_GLOW_DEPTH_DP * density;
            float hs = r * DockGlassRendering.BEAD_SHADOW_HALF_WIDTH_SHARE;
            float hg = r * DockGlassRendering.BEAD_GLOW_HALF_WIDTH_SHARE;
            for (int i = 0; i < beads.count(); i++) {
                float cx = beads.centerX(i) + mSlab.left;
                // The paints are drawn about x = 0: the canvas carries them to this bead.
                canvas.translate(cx, 0f);
                canvas.drawRect(-hs, bottom - rise - shadowReach, hs, bottom + shadowReach,
                    mBeadShadow);
                canvas.drawRect(-hg, bottom - glowReach, hg, bottom + glowReach, mBeadGlow);
                canvas.translate(-cx, 0f);
            }
            canvas.restoreToCount(save);
            return;
        }
        float reach = DockGlassRendering.SHELF_SHADOW_DEPTH_DP * density;
        int save = canvas.save();
        // The slab's own glass is not the wallpaper: nothing of the shelf is drawn under it.
        canvas.clipOutPath(mSlabPath);
        float halfShadow = DockGlassRendering.shelfShadowHalfWidthPx(mSlab.width(), mRadiusPx);
        canvas.drawRect(mSlab.centerX() - halfShadow, mSlab.bottom - mRadiusPx,
            mSlab.centerX() + halfShadow, mSlab.bottom + reach, mShadow);
        float halfGlow = DockGlassRendering.shelfGlowHalfWidthPx(mSlab.width(), mRadiusPx);
        canvas.drawRect(mSlab.centerX() - halfGlow, mSlab.bottom,
            mSlab.centerX() + halfGlow,
            mSlab.bottom + DockGlassRendering.SHELF_GLOW_DEPTH_DP * density, mGlow);
        canvas.restoreToCount(save);
    }
}
