package com.termux.app.terminal;

import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * One frame's focus rim: the lit glass edge on a slab, or the stock stroke when glass is off.
 * Held by whoever owns the frame — one instance per frame — and re-applied on every render; it
 * reuses the live drawable when nothing but focus can have changed, and crossfades alpha and hue
 * in place when it has.
 *
 * <p>Shared by the terminal panes and by the pane wall's non-terminal pages. It knows nothing
 * about what the frame contains, so a page supplies the same three answers a pane does: is glass
 * on, at what radius, and does this frame have the focus.
 */
public final class PaneRim {

    private static final int GLASS_FOCUSED_ALPHA = 255;
    private static final int GLASS_UNFOCUSED_ALPHA = 110;
    private static final int STOCK_FOCUSED_ALPHA = 255;
    private static final int STOCK_UNFOCUSED_ALPHA = 64;
    /** How long the focus crossfade runs. */
    private static final long FOCUS_BORDER_MS = 160L;

    private Drawable mDrawable;
    private boolean mGlass;
    private boolean mActive;
    private int mFocusedTint;
    private int mUnfocusedTint;
    private int mCurrentTint;
    private float mRadiusPx;
    private ValueAnimator mAnimator;

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
     * Put the rim on {@code frame}, or crossfade the one already there to a new focus state.
     *
     * @param radiusPx the slab radius, already capped against the frame's own bounds
     * @return true while the frame carries a rim, false when there is nothing to draw (in which
     *         case the caller can drop this instance)
     */
    public boolean apply(@NonNull FrameLayout frame, boolean glass, float radiusPx,
                         boolean active) {
        // Unlike the dock's white glass edge, a pane's rim is also its focus indicator, so it
        // carries a Material colour and a wide alpha spread: the focused pane glows in the accent,
        // the rest fall back to a dim neutral outline. A white rim at two alphas could not say
        // which pane has the keyboard.
        int focusedTint = 0;
        int unfocusedTint = 0;
        float radius = 0f;
        if (glass) {
            focusedTint = MaterialColors.getColor(frame.getContext(),
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(frame.getContext(), R.color.termux_primary));
            unfocusedTint = MaterialColors.getColor(frame.getContext(),
                com.google.android.material.R.attr.colorOutlineVariant,
                ContextCompat.getColor(frame.getContext(), R.color.termux_outline_variant));
            radius = radiusPx;
        }

        // Reuse the live drawable when nothing but focus can have changed. A focus flip then
        // crossfades it in place, and an unchanged re-application (every render calls this) leaves
        // a mid-flight crossfade running instead of stamping the end state over it.
        boolean reusable = mDrawable != null && mGlass == glass
            && frame.getForeground() == mDrawable
            && (!glass || (mFocusedTint == focusedTint && mUnfocusedTint == unfocusedTint
                && mRadiusPx == radius));
        if (reusable) {
            if (mActive != active) {
                mActive = active;
                animateFocus();
            }
            return true;
        }

        cancel();
        mGlass = glass;
        mActive = active;
        mFocusedTint = focusedTint;
        mUnfocusedTint = unfocusedTint;
        mRadiusPx = radius;
        if (glass) {
            mCurrentTint = active ? focusedTint : unfocusedTint;
            mDrawable = new com.termux.app.GlassRimDrawable(
                frame.getResources().getDisplayMetrics().density, radius, mCurrentTint);
            mDrawable.setAlpha(active ? GLASS_FOCUSED_ALPHA : GLASS_UNFOCUSED_ALPHA);
        } else {
            Drawable border = ContextCompat.getDrawable(frame.getContext(),
                R.drawable.pane_active_border);
            if (border != null) {
                border = border.mutate();
                border.setAlpha(active ? STOCK_FOCUSED_ALPHA : STOCK_UNFOCUSED_ALPHA);
            }
            mDrawable = border;
        }
        frame.setForeground(mDrawable);
        return mDrawable != null;
    }

    /** Take the rim off {@code frame} and stop any crossfade. */
    public void clear(@NonNull FrameLayout frame) {
        cancel();
        mDrawable = null;
        frame.setForeground(null);
    }

    /** Stop any crossfade, leaving the rim where it is. */
    public void cancel() {
        if (mAnimator == null) return;
        ValueAnimator superseded = mAnimator;
        mAnimator = null;
        superseded.cancel();
    }

    /**
     * Crossfades the rim between its focused and unfocused treatment instead of snapping. Alpha
     * and (on glass) rim hue move together, so the eye gets a short motion path across the layout
     * on "move pane focus" even though no geometry changes. Starts from wherever the drawable
     * currently is, so an interrupted crossfade reverses smoothly.
     */
    private void animateFocus() {
        final Drawable border = mDrawable;
        if (border == null) return;
        // Read the mid-flight values before cancelling: a reversed crossfade continues from
        // wherever the rim currently is. The superseded animator's end listener checks mAnimator
        // so it cannot stamp its own end state over these.
        final int fromAlpha = border.getAlpha();
        final int fromTint = mCurrentTint;
        cancel();
        final int toAlpha = mGlass
            ? (mActive ? GLASS_FOCUSED_ALPHA : GLASS_UNFOCUSED_ALPHA)
            : (mActive ? STOCK_FOCUSED_ALPHA : STOCK_UNFOCUSED_ALPHA);
        final int toTint = mActive ? mFocusedTint : mUnfocusedTint;
        final boolean tinted = mGlass && border instanceof com.termux.app.GlassRimDrawable;
        if (!animationsEnabled()) {
            border.setAlpha(toAlpha);
            if (tinted) {
                mCurrentTint = toTint;
                ((com.termux.app.GlassRimDrawable) border).setTint(toTint);
            }
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(FOCUS_BORDER_MS);
        animator.setInterpolator(PaneMotionOverlayView.standardInterpolator());
        animator.addUpdateListener(a -> {
            float fraction = (float) a.getAnimatedValue();
            border.setAlpha(Math.round(fromAlpha + (toAlpha - fromAlpha) * fraction));
            if (tinted) {
                mCurrentTint = ColorUtils.blendARGB(fromTint, toTint, fraction);
                ((com.termux.app.GlassRimDrawable) border).setTint(mCurrentTint);
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (mAnimator != animation) return; // superseded by a newer crossfade
                mAnimator = null;
                border.setAlpha(toAlpha);
                if (tinted) {
                    mCurrentTint = toTint;
                    ((com.termux.app.GlassRimDrawable) border).setTint(toTint);
                }
            }
        });
        mAnimator = animator;
        animator.start();
    }
}
