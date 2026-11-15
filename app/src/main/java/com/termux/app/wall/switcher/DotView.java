package com.termux.app.wall.switcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import com.termux.R;

import java.util.Collections;

/**
 * The page switcher's dot: one small circle against the screen edge. The view is the touch
 * target ({@code 2 * TOUCH_RADIUS_DP} square) and is laid out once at 0,0 in the layer; the
 * controller moves it by translation only. It draws one circle and holds no bitmap, so nothing
 * repaints at rest.
 */
public final class DotView extends View {

    /** Where the view's touches go, in the layer's coordinates. */
    public interface Listener {
        boolean onDotTouch(@NonNull MotionEvent event);

        /** TalkBack's click: open the picker. */
        void onDotClick();
    }

    static final float LIFT_SCALE = 1.3f;
    static final long LIFT_MS = 120L;
    private static final float LIFT_ELEVATION_DP = 8f;
    private static final float RIM_DP = 1f;
    private static final float GLASS_WHITE_MIX = 0.25f;
    private static final int FILL_ALPHA = 0xE6;
    private static final int RIM_ALPHA = 0x66;

    private final float mDensity;
    private final float mDiameter;
    private final int mSizePx;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Listener mListener;
    private int mGlass;
    private int mInk;
    private boolean mLifted;
    private boolean mReducedMotion;
    @Nullable private ValueAnimator mLiftAnimator;
    private final android.graphics.Rect mExclusion = new android.graphics.Rect();
    private final java.util.List<android.graphics.Rect> mExclusionList =
        Collections.singletonList(mExclusion);

    public DotView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mDiameter = DotPlacement.DIAMETER_DP * mDensity;
        mSizePx = Math.round(2f * DotPlacement.TOUCH_RADIUS_DP * mDensity);
        mRim.setStyle(Paint.Style.STROKE);
        mRim.setStrokeWidth(Math.max(1f, RIM_DP * mDensity));
        setClickable(true);
        setFocusable(false);
        setContentDescription(context.getString(R.string.page_switcher_title));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                float half = mDiameter / 2f;
                float cx = view.getWidth() / 2f;
                float cy = view.getHeight() / 2f;
                outline.setOval(Math.round(cx - half), Math.round(cy - half),
                    Math.round(cx + half), Math.round(cy + half));
            }
        });
        // The dot has no shadow at rest; only the lifted elevation casts one.
        setElevation(0f);
        ViewCompat.setAccessibilityDelegate(this, new androidx.core.view.AccessibilityDelegateCompat() {
            @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                    @NonNull androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat
                    .AccessibilityActionCompat.ACTION_CLICK);
            }

            @Override public boolean performAccessibilityAction(@NonNull View host, int action,
                    @Nullable android.os.Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    Listener l = mListener;
                    if (l != null) l.onDotClick();
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    /** The view's side in px: the controller lays it out at exactly this size. */
    public int sizePx() {
        return mSizePx;
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /** The glass and ink the dot is drawn in; repaints only when they change. */
    public void setColors(int glass, int ink) {
        if (glass == mGlass && ink == mInk) return;
        mGlass = glass;
        mInk = ink;
        mFill.setColor(ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(glass, Color.WHITE, GLASS_WHITE_MIX), FILL_ALPHA));
        mRim.setColor(ColorUtils.setAlphaComponent(ink, RIM_ALPHA));
        invalidate();
    }

    /** Lifted while the user moves it: a little larger with a shadow. */
    public void setLifted(boolean lifted) {
        if (mLifted == lifted) return;
        mLifted = lifted;
        float scale = lifted ? LIFT_SCALE : 1f;
        float elevation = lifted ? LIFT_ELEVATION_DP * mDensity : 0f;
        if (mLiftAnimator != null) mLiftAnimator.cancel();
        setElevation(elevation);
        if (mReducedMotion || !isAttachedToWindow()) {
            setScaleX(scale);
            setScaleY(scale);
            return;
        }
        // One short animator of its own: the controller owns this view's alpha animator.
        ValueAnimator animator = ValueAnimator.ofFloat(getScaleX(), scale);
        animator.setDuration(LIFT_MS);
        animator.addUpdateListener(a -> {
            float v = (Float) a.getAnimatedValue();
            setScaleX(v);
            setScaleY(v);
        });
        mLiftAnimator = animator;
        animator.start();
    }

    public boolean isLifted() {
        return mLifted;
    }

    /**
     * Keeps the system's back and home swipes off the dot: the whole view is excluded while it
     * is shown, and nothing when it is hidden. The system follows the view's translation, so it is
     * called when the dot is shown or hidden and when it settles, not on every drag event.
     */
    public void updateGestureExclusion() {
        if (getVisibility() == VISIBLE && getWidth() > 0 && getHeight() > 0) {
            mExclusion.set(0, 0, getWidth(), getHeight());
            ViewCompat.setSystemGestureExclusionRects(this, mExclusionList);
        } else {
            ViewCompat.setSystemGestureExclusionRects(this, Collections.emptyList());
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) updateGestureExclusion();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = mDiameter / 2f;
        canvas.drawCircle(cx, cy, r, mFill);
        canvas.drawCircle(cx, cy, r - mRim.getStrokeWidth() / 2f, mRim);
    }

    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        Listener l = mListener;
        if (l == null) return false;
        return l.onDotTouch(event);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mLiftAnimator != null) mLiftAnimator.cancel();
    }
}
