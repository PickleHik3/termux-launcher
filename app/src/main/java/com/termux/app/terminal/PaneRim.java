package com.termux.app.terminal;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * One frame's border, rendered from what {@link PaneBorderStyle} decided: the shared glass rim,
 * the focus colour, or the attention glow. Held by whoever owns the frame — one instance per
 * frame — and re-applied on every render; it reuses the live drawable when nothing has changed
 * and fades a new one in when the kind does.
 *
 * <p>Shared by the terminal panes and by the pane wall's non-terminal pages. It decides nothing
 * about which border a frame wears: a page supplies the decision, a radius and its style.
 */
public final class PaneRim {

    /** The plain stroke's width: the shared rim's hairline, the line a corner tab lines up against. */
    public static final float STOCK_STROKE_DP = 1f;

    /** {@link #STOCK_STROKE_DP} in pixels. */
    public static float stockStrokePx(float density) {
        return STOCK_STROKE_DP * density;
    }

    /**
     * Whether a page with no glass still wears the plain line: the border preference is on
     * ({@link PaneSurfaceStyle#paneBorderEnabled}), so every place's frame draws its line.
     */
    public static boolean plainBorderWanted(@Nullable PaneSurfaceStyle style) {
        return style != null && style.paneBorderEnabled();
    }

    /**
     * The theme's border colours: the Material active colour for focus, and for attention the
     * error role, the same colour the window bar's bell and blocked chip wear.
     */
    @NonNull
    public static PaneBorderStyle.Palette palette(@NonNull Context context) {
        int focus = MaterialColors.getColor(context,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int attention = MaterialColors.getColor(context,
            androidx.appcompat.R.attr.colorError,
            ContextCompat.getColor(context, R.color.termux_error));
        PaneBorderColours.Roles roles = new PaneBorderColours.Roles(
            lineColour(context),
            focus,
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, 0),
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, 0));
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
        if (prefs == null) return new PaneBorderStyle.Palette(focus, attention);
        return PaneBorderColours.palette(
            PaneBorderColours.ColourChoice.fromId(prefs.getTerminalPaneBorderColour(),
                prefs.getTerminalPaneBorderCustom(), PaneBorderColours.Mode.OUTLINE),
            PaneBorderColours.ColourChoice.fromId(prefs.getTerminalPaneFocusColour(),
                prefs.getTerminalPaneFocusCustom(), PaneBorderColours.Mode.PRIMARY),
            prefs.getTerminalPaneGlow(),
            PaneBorderColours.ColourChoice.fromId(prefs.getTerminalPaneGlowColour(),
                prefs.getTerminalPaneGlowCustom(), PaneBorderColours.Mode.SAME_AS_BORDER),
            roles, attention);
    }

    /** How long a border of a new kind fades in. */
    private static final long FADE_MS = 160L;

    private Drawable mDrawable;
    private PaneBorderStyle.Kind mKind;
    private int mColour;
    private boolean mGlow;
    private int mGlowColour;
    private boolean mGlass;
    private boolean mGradientRim;
    private boolean mDocked;
    private boolean mOpeningLine;
    /** The slab whose own edge the resting rim yields to, or null; see {@link #slabEdgeOf}. */
    @Nullable private SlabEdge mSlab;
    private float mRadiusPx;
    /** Frame-source counter for the idle audit: paneRim. */
    private static final com.termux.terminal.FrameProbe.Source FRAMES = com.termux.terminal.FrameProbe.source("paneRim");

    private ValueAnimator mAnimator;
    /** The alpha the kind asks for, before the wall's travel takes its share. */
    private int mBaseAlpha = 255;
    /**
     * How much of the rim the wall's travel leaves showing, 1 at rest: a page mid-slide draws
     * no outline, so two frames are never seen side by side (PaneWallPolicy#outlineAlpha).
     */
    private float mTravelAlpha = 1f;

    /**
     * Whether animators are honoured at all. Cached read of the same setting the system exposes;
     * a {@code Settings.Global} lookup here would hit the content resolver on every focus change,
     * which is every tap on a pane.
     */
    public static boolean animationsEnabled() {
        try {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || ValueAnimator.areAnimatorsEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Put the decided border on {@code frame}.
     *
     * @param glass whether the pane is a glass slab (the focus colour then uses the lit rim)
     * @param radiusPx the pane's radius as the surface asks for it; every drawable caps it
     *                 against the frame's live bounds on each draw ({@link PaneShape})
     * @param style where the shared rim and the pulse preference come from; null in a bare test
     * @return true while the frame carries a border
     */
    public boolean apply(@NonNull FrameLayout frame, boolean glass, float radiusPx,
                         @NonNull PaneBorderStyle.Decision decision,
                         @Nullable PaneSurfaceStyle style) {
        float radius = Math.max(0f, radiusPx);
        boolean gradientRim = style != null && style.paneGlassRimWanted();
        SlabEdge slab = glass && style != null ? slabEdgeOf(frame) : null;
        boolean pulses = animationsEnabled() && (style == null || style.paneAttentionPulses());
        boolean docked = style != null && style.paneDocked();
        boolean openingLine = style != null && style.paneOpeningLine();

        boolean reusable = mDrawable != null && frame.getForeground() == mDrawable
            && mKind == decision.kind && mColour == decision.colour && mGlass == glass
            && mGlow == decision.glow && mGlowColour == decision.glowColour
            && mRadiusPx == radius && mGradientRim == gradientRim && mDocked == docked
            && mOpeningLine == openingLine && mSlab == slab;
        if (reusable) {
            if (mDrawable instanceof PaneAttentionGlow)
                ((PaneAttentionGlow) mDrawable).setPulsing(pulses);
            return true;
        }

        boolean kindChanged = mDrawable != null && mKind != decision.kind;
        cancel();
        releaseDrawable();
        mKind = decision.kind;
        mColour = decision.colour;
        mGlow = decision.glow;
        mGlowColour = decision.glowColour;
        mGlass = glass;
        mGradientRim = gradientRim;
        mDocked = docked;
        mOpeningLine = openingLine;
        mSlab = slab;
        mRadiusPx = radius;
        float density = frame.getResources().getDisplayMetrics().density;
        switch (decision.kind) {
            case ATTENTION: {
                PaneAttentionGlow glow = new PaneAttentionGlow(density, radius, decision.colour);
                glow.setPulsing(pulses);
                mDrawable = glow;
                break;
            }
            case FOCUS:
                // Docked, the focused pane wears the active colour along its own edges of the
                // divider and nowhere else; the opening has no border of its own.
                if (docked) {
                    mDrawable = new PaneDividerEdges(frame, decision.colour, density);
                    break;
                }
                mDrawable = glass
                    ? new com.termux.app.GlassRimDrawable(density, radius, decision.colour)
                    : stroke(radius, decision.colour, density);
                break;
            default:
                // The Docked insert wears the opening's line rather than the glass's rim: a
                // SharedRim with no style to build from is that line. Over a glass slab the rim
                // yields to the slab's own edge whenever its program draws one.
                if (docked) {
                    mDrawable = new PaneDividerEdges(frame, decision.colour != PaneBorderStyle.NO_COLOUR
                        ? decision.colour : lineColour(frame.getContext()), density);
                } else {
                    mDrawable = new SharedRim(frame.getContext(), openingLine ? null : style, radius,
                        density, openingLine ? null : slab, decision.colour);
                }
                break;
        }
        // A steady glow rides over the rim and the focus border; the attention border has its own.
        if (decision.glow && decision.kind != PaneBorderStyle.Kind.ATTENTION && !docked) {
            PaneAttentionGlow steady = new PaneAttentionGlow(density, radius, decision.glowColour);
            steady.setPulsing(false);
            mDrawable = new Layered(mDrawable, steady);
        }
        frame.setForeground(mDrawable);
        setBaseAlpha(255);
        if (kindChanged && animationsEnabled()) fadeIn();
        return true;
    }

    /**
     * A pane's glass slab as its border sees it: whether the slab's program draws the outline right
     * now, and a way to hear when that changes. {@link PaneGlassBackdropView} is the one in the
     * app; the border asks the slab, never the look, so a slab drawn plain keeps its line.
     */
    public interface SlabEdge {
        /** True while the slab's own program draws its outline, the gathered light. */
        boolean drawsEdge();

        /** Run once {@link #drawsEdge} has changed; null for no one. One listener per slab. */
        void setOnEdgeChanged(@Nullable Runnable onEdgeChanged);
    }

    /**
     * The glass slab standing in {@code frame}: a direct child, or one level down, that answers
     * {@link SlabEdge}; null for a frame with none. Read on a border's build, never per draw.
     */
    @Nullable
    static SlabEdge slabEdgeOf(@NonNull android.view.ViewGroup frame) {
        for (int i = 0; i < frame.getChildCount(); i++) {
            android.view.View child = frame.getChildAt(i);
            if (child instanceof SlabEdge) return (SlabEdge) child;
        }
        for (int i = 0; i < frame.getChildCount(); i++) {
            android.view.View child = frame.getChildAt(i);
            if (!(child instanceof android.view.ViewGroup)) continue;
            android.view.ViewGroup group = (android.view.ViewGroup) child;
            for (int j = 0; j < group.getChildCount(); j++)
                if (group.getChildAt(j) instanceof SlabEdge) return (SlabEdge) group.getChildAt(j);
        }
        return null;
    }

    /**
     * The Docked opening's line at {@code radiusPx}: the plain line, {@link #STOCK_STROKE_DP} in
     * {@link #lineColour}, round the insert's own corners. What the lone pane's host draws when
     * the pane itself wears no border; a pane's own frame gets it through {@link #apply}.
     */
    @NonNull
    public static Drawable openingLine(@NonNull Context context, float radiusPx) {
        return stroke(Math.max(0f, radiusPx), lineColour(context),
            context.getResources().getDisplayMetrics().density);
    }

    /**
     * The plain line's colour: the theme's outline role (so light, dark, black and the scheme's
     * chrome each resolve their own) at the divider's strength. Also the Docked opening's line.
     */
    public static int lineColour(@NonNull Context context) {
        return ColorUtils.setAlphaComponent(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutline,
            ContextCompat.getColor(context, R.color.termux_outline_variant)), 150);
    }

    private static Drawable stroke(float radius, int colour, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setCornerRadius(radius);
        d.setStroke(Math.max(1, Math.round(stockStrokePx(density))), colour);
        return d;
    }

    /**
     * How much of the border the wall's slide leaves showing, 0 to 1. Composed with the kind's
     * alpha rather than written over it, so a fade under way keeps its course and the border comes
     * back at settle at exactly the strength it had.
     */
    public void setTravelAlpha(float alpha) {
        float clamped = Float.isNaN(alpha) ? 1f : Math.max(0f, Math.min(1f, alpha));
        if (mTravelAlpha == clamped) return;
        mTravelAlpha = clamped;
        if (mDrawable != null) mDrawable.setAlpha(Math.round(mBaseAlpha * mTravelAlpha));
    }

    private void setBaseAlpha(int alpha) {
        mBaseAlpha = alpha;
        if (mDrawable != null) mDrawable.setAlpha(Math.round(alpha * mTravelAlpha));
    }

    /** Take the border off {@code frame} and stop any fade or pulse. */
    public void clear(@NonNull FrameLayout frame) {
        cancel();
        releaseDrawable();
        frame.setForeground(null);
    }

    /** Stop any fade, leaving the border where it is. */
    public void cancel() {
        if (mAnimator == null) return;
        ValueAnimator superseded = mAnimator;
        mAnimator = null;
        superseded.cancel();
    }

    private void releaseDrawable() {
        if (mDrawable instanceof PaneAttentionGlow) ((PaneAttentionGlow) mDrawable).release();
        if (mDrawable instanceof Layered) ((Layered) mDrawable).release();
        if (mSlab != null && (mDrawable instanceof SharedRim
            || mDrawable instanceof Layered && ((Layered) mDrawable).mBase instanceof SharedRim))
            mSlab.setOnEdgeChanged(null);
        mDrawable = null;
        mKind = null;
    }

    /** The one short fade a border of a new kind gets, so focus and attention do not pop. */
    private void fadeIn() {
        setBaseAlpha(0);
        ValueAnimator animator = ValueAnimator.ofInt(0, 255);
        animator.setDuration(FADE_MS);
        animator.setInterpolator(PaneMotionOverlayView.standardInterpolator());
        animator.addUpdateListener(a -> {
            FRAMES.tick();
            setBaseAlpha((int) a.getAnimatedValue());
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (mAnimator != animation) return; // superseded
                mAnimator = null;
                setBaseAlpha(255);
            }
        });
        mAnimator = animator;
        animator.start();
    }

    /**
     * The rim every other surface wears, cut at this pane's radius capped for its live bounds.
     * The style builds it (hairline or gradient, per preset); with no style it is the
     * outline-colour hairline, which is also the Docked opening's line ({@link #openingLine}).
     * Over a slab whose program draws the edge ({@link SlabEdge#drawsEdge}) it draws nothing, asked
     * on every draw and redrawn when the slab says it changed: the frame still carries its border,
     * so the decision and its reuse read as for any rim, and a slab that goes plain gets it back.
     */
    private static final class SharedRim extends Drawable {
        private final PaneSurfaceStyle mStyle;
        private final float mRequestedRadiusPx;
        private final float mDensity;
        private final int mFallbackColour;
        /** The border colour tinting whatever rim is drawn, or NO_COLOUR for the rim's own. */
        private final int mTint;
        /** The slab whose own edge this rim yields to, or null for none. */
        @Nullable private final SlabEdge mSlab;
        private Drawable mInner;
        private float mInnerRadiusPx = -1f;
        private int mAlpha = 255;

        SharedRim(@NonNull Context context, @Nullable PaneSurfaceStyle style, float radiusPx,
                  float density, @Nullable SlabEdge slab, int tint) {
            mStyle = style;
            mTint = tint;
            mRequestedRadiusPx = radiusPx;
            mDensity = density;
            mFallbackColour = tint != PaneBorderStyle.NO_COLOUR ? tint : lineColour(context);
            mSlab = slab;
            if (slab != null) slab.setOnEdgeChanged(this::invalidateSelf);
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            android.os.Trace.beginSection("Pane.rim.resize");
            try {
                onBoundsChangeImpl(bounds);
            } finally {
                android.os.Trace.endSection();
            }
        }

        private void onBoundsChangeImpl(@NonNull Rect bounds) {
            float radius = PaneShape.radiusForBounds(mRequestedRadiusPx, bounds.width(),
                bounds.height());
            if (mInner == null || radius != mInnerRadiusPx) {
                Drawable built = mStyle == null ? null : mStyle.paneRimDrawable(radius);
                mInner = built != null ? built : stroke(radius, mFallbackColour, mDensity);
                if (built != null && mTint != PaneBorderStyle.NO_COLOUR) {
                    mInner.setColorFilter(new PorterDuffColorFilter(mTint, PorterDuff.Mode.SRC_IN));
                }
                mInnerRadiusPx = radius;
                mInner.setAlpha(mAlpha);
            }
            mInner.setBounds(bounds);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mSlab != null && mSlab.drawsEdge()) return;
            if (mInner != null) mInner.draw(canvas);
        }

        @Override public void setAlpha(int alpha) {
            mAlpha = alpha;
            if (mInner != null) mInner.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
            if (mInner != null) mInner.setColorFilter(colorFilter);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A border and a steady glow over it, one drawable so the frame's alpha reaches both. */
    private static final class Layered extends Drawable {
        final Drawable mBase;
        private final PaneAttentionGlow mGlow;

        Layered(@NonNull Drawable base, @NonNull PaneAttentionGlow glow) {
            mBase = base;
            mGlow = glow;
        }

        void release() {
            mGlow.release();
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            mBase.setBounds(bounds);
            mGlow.setBounds(bounds);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            mGlow.draw(canvas);
            mBase.draw(canvas);
        }

        @Override public void setAlpha(int alpha) {
            mBase.setAlpha(alpha);
            mGlow.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mBase.setColorFilter(colorFilter);
            mGlow.setColorFilter(colorFilter);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
