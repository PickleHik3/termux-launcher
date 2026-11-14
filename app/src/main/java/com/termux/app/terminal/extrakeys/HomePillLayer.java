package com.termux.app.terminal.extrakeys;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The full-size layer the Home pill and its "Edit keys" chip float in. It draws nothing itself and
 * claims a touch stream only when the down lands on the visible pill or chip; every other touch
 * is passed over (this returns false), so the home screen under it behaves as if it were not there.
 *
 * <p>Both children are laid out once at the top-left at their wrap size and placed by translation,
 * so dragging asks for no layout.
 */
public final class HomePillLayer extends FrameLayout implements HomePillView.Listener {

    /** How long the chip stays after a drop. */
    public static final long CHIP_LINGER_MS = 2600L;

    public interface Host {
        /** The pill was dropped with its top-left at ({@code left}, {@code top}) in layer pixels. */
        void onPillDropped(float left, float top);

        void onEditKeys();
    }

    private final HomePillView mPill;
    private final HomePillChip mChip;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideChip = this::hideChip;
    private final float[] mSpot = new float[2];
    private final float[] mChipSpot = new float[2];

    @Nullable private Host mHost;
    @Nullable private HomePillSpot.Area mArea;
    private float mPickupLeft;
    private float mPickupTop;
    private boolean mClaimed;

    public HomePillLayer(@NonNull Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        mPill = new HomePillView(context);
        mPill.setListener(this);
        mChip = new HomePillChip(context);
        mChip.setOnEditKeys(this::onChipClicked);
        mChip.setVisibility(INVISIBLE);
        addView(mPill, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(mChip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    @NonNull
    public HomePillView pill() {
        return mPill;
    }

    @NonNull
    public HomePillChip chip() {
        return mChip;
    }

    public void setHost(@Nullable Host host) {
        mHost = host;
    }

    /** Where the pill may rest, for clamping a drag and placing the chip. */
    public void setArea(@Nullable HomePillSpot.Area area) {
        mArea = area;
    }

    /** Puts the pill's top-left at ({@code left}, {@code top}) by translation. */
    public void placePill(float left, float top) {
        mPill.setTranslationX(left);
        mPill.setTranslationY(top);
    }

    /** Shows or hides the pill; hiding it takes the chip with it. */
    public void setPillShown(boolean shown) {
        int visibility = shown ? VISIBLE : INVISIBLE;
        if (mPill.getVisibility() == visibility) return;
        mPill.setVisibility(visibility);
        if (!shown) hideChip();
    }

    public void showChip(float left, float top) {
        mHandler.removeCallbacks(mHideChip);
        mChip.setTranslationX(left);
        mChip.setTranslationY(top);
        if (mChip.getVisibility() != VISIBLE) mChip.setVisibility(VISIBLE);
    }

    public void hideChip() {
        mHandler.removeCallbacks(mHideChip);
        if (mChip.getVisibility() != INVISIBLE) mChip.setVisibility(INVISIBLE);
    }

    public void hideChipAfter(long millis) {
        mHandler.removeCallbacks(mHideChip);
        mHandler.postDelayed(mHideChip, millis);
    }

    // ---- The pill's own gestures.

    @Override
    public void onPillPickedUp() {
        mPickupLeft = mPill.getTranslationX();
        mPickupTop = mPill.getTranslationY();
        followChip(mPickupLeft, mPickupTop);
    }

    @Override
    public void onPillDragged(float dx, float dy) {
        float left = mPickupLeft + dx;
        float top = mPickupTop + dy;
        HomePillSpot.Area area = mArea;
        if (area != null) {
            float w = mPill.getWidth();
            float h = mPill.getHeight();
            HomePillSpot.settle(left + w * 0.5f, top + h * 0.5f, w, h, area, mSpot);
            left = mSpot[0];
            top = mSpot[1];
        }
        placePill(left, top);
        followChip(left, top);
    }

    @Override
    public void onPillDropped() {
        hideChipAfter(CHIP_LINGER_MS);
        if (mHost != null) mHost.onPillDropped(mPill.getTranslationX(), mPill.getTranslationY());
    }

    private void followChip(float pillLeft, float pillTop) {
        HomePillSpot.Area area = mArea;
        if (area == null) return;
        HomePillSpot.chipTopLeft(pillLeft, pillTop, mPill.getWidth(), mPill.getHeight(),
            mChip.getWidth(), mChip.getHeight(),
            HomePillSpot.CHIP_GAP_DP * getResources().getDisplayMetrics().density, area, mChipSpot);
        showChip(mChipSpot[0], mChipSpot[1]);
    }

    private void onChipClicked() {
        hideChip();
        if (mHost != null) mHost.onEditKeys();
    }

    // ---- Touch.

    /**
     * Whether a down at ({@code x}, {@code y}), in this layer's own pixels (the layer is as big as
     * the window, so window pixels less the layer's offset), lands on the visible pill or chip.
     * The activity's surface long-press guard asks this before it arms.
     */
    public boolean claimsDown(float x, float y) {
        return hits(mPill, x, y) || hits(mChip, x, y);
    }

    private static boolean hits(View view, float x, float y) {
        if (view.getVisibility() != VISIBLE) return false;
        float left = view.getX();
        float top = view.getY();
        return x >= left && x < left + view.getWidth() && y >= top && y < top + view.getHeight();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mClaimed = claimsDown(ev.getX(), ev.getY());
            if (!mClaimed) {
                if (mChip.getVisibility() == VISIBLE) hideChip();
                return false;
            }
        } else if (!mClaimed) {
            return false;
        }
        boolean handled = super.dispatchTouchEvent(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) mClaimed = false;
        return handled;
    }

    @Override
    protected void onDetachedFromWindow() {
        mHandler.removeCallbacks(mHideChip);
        mClaimed = false;
        super.onDetachedFromWindow();
    }
}
