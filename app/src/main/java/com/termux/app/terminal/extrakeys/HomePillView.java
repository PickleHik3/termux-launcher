package com.termux.app.terminal.extrakeys;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.haptics.Haptics;
import com.termux.app.notice.TerminalDress;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

/**
 * Home's extra keys as a small floating glass pill: one {@link ExtraKeysPageView} of Home's first
 * page on a static rounded glass. A tap presses a key; a hold picks the pill up (the keys get a
 * cancel, so nothing fires), and while it is picked up the host moves it with {@link
 * Listener#onPillDragged}. Docked (for the keyboard) it is laid out at the docked bar's size, its
 * keys act on a hold as the bar's do and it cannot be picked up.
 *
 * <p>Nothing here repaints at rest: the glass is a static drawable, the Lift is a one-shot property
 * animation, and a layout is requested only when the keys, the scale or the size change.
 */
public final class HomePillView extends ViewGroup {

    /** The keys' inset from the pill's side, floating. */
    public static final float KEY_INSET_DP = 4f;
    /** One key's width at scale 1, floating. */
    public static final float KEY_WIDTH_DP = 48f;
    public static final float LIFT_SCALE = 1.06f;
    public static final float LIFT_Z_DP = 8f;
    public static final long LIFT_MS = 140L;
    private static final float REST_ELEVATION_DP = 4f;

    public interface Listener {
        void onPillPickedUp();

        /** The finger's offset since the pickup, in pixels. */
        void onPillDragged(float dx, float dy);

        void onPillDropped();
    }

    private final ExtraKeysPageView mPage;
    private final GradientDrawable mGlass = new GradientDrawable();
    private final HomePillGesture mGesture;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHoldRunnable = this::onHold;
    private final TimeInterpolator mInterpolator = new DecelerateInterpolator();
    private final float mDensity;

    @Nullable private Listener mListener;
    @Nullable private ExtraKeysInfo mInfo;
    private float mScale = 1f;
    private boolean mFloating = true;
    private int mDockedW = -1;
    private int mDockedH = -1;
    private boolean mTracking;
    private long mDownTime;

    public HomePillView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        ViewConfiguration config = ViewConfiguration.get(context);
        mGesture = new HomePillGesture(config.getScaledTouchSlop(),
            ViewConfiguration.getLongPressTimeout());
        mPage = new ExtraKeysPageView(context);
        addView(mPage);
        applyDress(TerminalDress.stored(context));
        setBackground(mGlass);
        setClipChildren(false);
        setElevation(REST_ELEVATION_DP * mDensity);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    radiusPx(view.getHeight()));
            }
        });
        mPage.setHoldActsOnKey(false);
    }

    /** The page of keys, for the host to hand its client, modifiers and look to. */
    @NonNull
    public ExtraKeysPageView pageView() {
        return mPage;
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Re-reads the glass from a dress, as the notice pill does. */
    public void setDress(@NonNull TerminalDress dress) {
        applyDress(dress);
    }

    private void applyDress(@NonNull TerminalDress dress) {
        mGlass.setColor(dress.fillColor);
        mGlass.setStroke(Math.round(dress.strokeWidthPx), dress.strokeColor);
        mGlass.setCornerRadius(radiusPx(getHeight()));
    }

    /** Shows {@code page} (Home's first) at {@code scale}, the Home set's key size. */
    public void setKeys(@Nullable ExtraKeysInfo page, float scale) {
        float safe = scale > 0f ? scale : 1f;
        boolean keysChanged = page != mInfo;
        boolean scaleChanged = safe != mScale;
        if (!keysChanged && !scaleChanged) return;
        mInfo = page;
        mScale = safe;
        // The page requests its own layout with new keys; a scale alone needs one here.
        if (keysChanged) mPage.setKeys(page);
        else requestLayout();
    }

    /** Floating: a hold on a key goes to the pill and it can be picked up. Docked: the bar's way. */
    public void setFloating(boolean floating) {
        if (floating == mFloating) return;
        mFloating = floating;
        mPage.setHoldActsOnKey(!floating);
        if (!floating) endGesture(true);
        mGlass.setCornerRadius(radiusPx(getHeight()));
        invalidateOutline();
    }

    public boolean isFloating() {
        return mFloating;
    }

    /** Lays the pill out at exactly this size, which spreads its keys as the docked bar does. */
    public void setDockedSize(int width, int height) {
        if (width == mDockedW && height == mDockedH) return;
        mDockedW = width;
        mDockedH = height;
        requestLayout();
    }

    /** Back to the size that wraps the keys. */
    public void clearDockedSize() {
        if (mDockedW < 0 && mDockedH < 0) return;
        mDockedW = -1;
        mDockedH = -1;
        requestLayout();
    }

    public boolean isPickedUp() {
        return mGesture.isPickedUp();
    }

    private boolean hasDockedSize() {
        return mDockedW >= 0 && mDockedH >= 0;
    }

    private int wrapWidthPx() {
        int columns = Math.max(1, mPage.columnCount());
        return Math.round((2f * KEY_INSET_DP + columns * KEY_WIDTH_DP * mScale) * mDensity);
    }

    private int wrapHeightPx() {
        return Math.round(HomePillSpot.PILL_HEIGHT_DP * mScale * mDensity);
    }

    private float radiusPx(int heightPx) {
        if (mFloating) return Math.max(0, heightPx) * 0.5f;
        float docked = HomePillSpot.DOCKED_RADIUS_DP * mDensity;
        return heightPx > 0 ? Math.min(docked, heightPx * 0.5f) : docked;
    }

    // ---- Layout.

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean docked = hasDockedSize();
        int w = docked ? mDockedW : wrapWidthPx();
        int h = docked ? mDockedH : wrapHeightPx();
        setMeasuredDimension(w, h);
        int inset = docked ? 0 : Math.round(KEY_INSET_DP * mDensity);
        mPage.measure(MeasureSpec.makeMeasureSpec(Math.max(0, w - 2 * inset), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int inset = hasDockedSize() ? 0 : Math.round(KEY_INSET_DP * mDensity);
        mPage.layout(inset, 0, Math.max(inset, r - l - inset), b - t);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // The radius alone, in place: this runs inside a layout pass.
        mGlass.setCornerRadius(radiusPx(h));
        invalidateOutline();
    }

    // ---- Touch.

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mFloating) return false;
        track(ev);
        // Once picked up the pill owns the stream; the framework cancels the page for us too.
        return ev.getActionMasked() != MotionEvent.ACTION_DOWN && mGesture.isPickedUp();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mFloating) return ev.getActionMasked() == MotionEvent.ACTION_DOWN;
        // A down between the keys or in the inset lands here, not on the page.
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN || !mTracking) track(ev);
        return true;
    }

    /** Feeds one event to the gesture and carries out what it answers. */
    private void track(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mHandler.removeCallbacks(mHoldRunnable);
                mTracking = true;
                mDownTime = ev.getDownTime();
                // Screen coordinates: the pill moves under the finger by translation, so its own
                // coordinates would cancel the drag they report.
                mGesture.down(ev.getRawX(), ev.getRawY());
                mHandler.postDelayed(mHoldRunnable, mGesture.holdMillis());
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mTracking) break;
                HomePillGesture.Move move = mGesture.move(ev.getRawX(), ev.getRawY());
                if (move == HomePillGesture.Move.CANCEL_HOLD) {
                    mHandler.removeCallbacks(mHoldRunnable);
                } else if (move == HomePillGesture.Move.DRAG && mListener != null) {
                    mListener.onPillDragged(mGesture.dragDx(), mGesture.dragDy());
                }
                break;
            case MotionEvent.ACTION_UP:
                endGesture(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                endGesture(true);
                break;
            default:
                break;
        }
    }

    /** Ends the stream: a drop when picked up (a cancel counts as one), otherwise nothing. */
    private void endGesture(boolean cancelled) {
        mHandler.removeCallbacks(mHoldRunnable);
        mTracking = false;
        boolean dropped;
        if (cancelled) {
            dropped = mGesture.isPickedUp();
            mGesture.cancel();
        } else {
            dropped = mGesture.up() == HomePillGesture.Up.DROP;
        }
        if (dropped) {
            if (mListener != null) mListener.onPillDropped();
            lift(false);
        }
    }

    private void onHold() {
        if (!mTracking || mGesture.holdElapsed() != HomePillGesture.Hold.PICK_UP) return;
        // The key under the finger gets its cancel now, so its release fires nothing.
        MotionEvent cancel = MotionEvent.obtain(mDownTime, SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        mPage.dispatchTouchEvent(cancel);
        cancel.recycle();
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        lift(true);
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        if (mListener != null) mListener.onPillPickedUp();
    }

    /** Lift (up) or settle (down): scale and translationZ, one 140 ms property animation. */
    private void lift(boolean up) {
        float scale = up ? LIFT_SCALE : 1f;
        float z = up ? LIFT_Z_DP * mDensity : 0f;
        animate().cancel();
        animate().scaleX(scale).scaleY(scale).translationZ(z).setDuration(LIFT_MS)
            .setInterpolator(mInterpolator).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        mHandler.removeCallbacks(mHoldRunnable);
        mTracking = false;
        mGesture.cancel();
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        setTranslationZ(0f);
        super.onDetachedFromWindow();
    }
}
