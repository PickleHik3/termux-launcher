package com.termux.app.terminal;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/**
 * The keybind hints when the dock's A–Z row cannot carry them, in the same dress as the mode
 * legends: hanging off the terminal's top edge rather than dropping from the status bar.
 *
 * <p>Two sizes, because the hints have two jobs. The strip is a handful of chips and hangs in the
 * trailing corner exactly like a mode legend, so the two read as the same voice arriving from the
 * same place. The {@code ?} table is the whole keymap, and a corner card is the wrong shape for it:
 * it takes the terminal's full width instead, edge to edge inside the frame, and scrolls within its
 * own height.
 *
 * <p>Both are flat and a little transparent (see {@link TerminalHintSurface}) with the terminal's
 * own corner radius where they touch its corners — a hint is part of the terminal window, not a
 * dialog over it.
 */
public final class TerminalHintPanelView extends FrameLayout {

    private static final long ENTER_MS = 200L;
    private static final long EXIT_MS = 140L;
    /** The corner card's ceiling; the wide table is bounded by its own content instead. */
    private static final int COMPACT_MAX_WIDTH_DP = 300;

    private int mSideMarginPx;
    private int mTopMarginPx;
    private int mBottomMarginPx;
    private float mTerminalCornerRadiusPx;
    /** What the free corners were last cut to, so a resize only re-cuts when it moves them. */
    private float mFreeCornerRadiusPx = -1f;
    private boolean mWide;
    private boolean mShowing;

    public TerminalHintPanelView(@NonNull Context context) {
        super(context);
        setPadding(dp(10), dp(8), dp(10), dp(10));
        // Passive: the prefix that raised this is still being held, and every touch belongs to
        // whatever is underneath — except the chips inside, which take their own.
        setClickable(false);
        setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setElevation(0f);
        setVisibility(GONE);
        setAlpha(0f);
        applyDress();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Never wider than the terminal, and never wider than a card wants to be. The incoming
        // spec is already the terminal's width less its margins, so capping against it is what
        // keeps the corner card inside a narrow or heavily inset terminal.
        int available = MeasureSpec.getSize(widthMeasureSpec);
        if (!mWide && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int maxWidthPx = Math.min(dp(COMPACT_MAX_WIDTH_DP), available);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidthPx,
                MeasureSpec.getMode(widthMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // The table can ask for more height than the terminal has; the scroll view inside it is
        // there for exactly that, so the panel takes the ceiling and lets the content scroll.
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST
            && getMeasuredHeight() > MeasureSpec.getSize(heightMeasureSpec)) {
            setMeasuredDimension(getMeasuredWidth(), MeasureSpec.getSize(heightMeasureSpec));
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // The free corners are clamped against the panel's own height — a one-row strip cannot
        // round as hard as the whole {@code ?} table can — so a height change re-cuts them, but
        // only when the clamp actually moves.
        if (TerminalHintSurface.freeCornerRadiusPx(getContext(), mTerminalCornerRadiusPx, height)
            != mFreeCornerRadiusPx)
            applyDress();
    }

    /**
     * Seats the panel inside the terminal's own frame; live settings, so re-read at every show.
     *
     * <p>All four insets, not just the two it hangs from: the panel is bounded by the terminal on
     * every side, so a tall table stops (and scrolls) at the terminal's bottom edge instead of
     * running on over the dock, whatever margins the user has dialled in.
     */
    public void setTerminalFrame(int sideMarginPx, int topMarginPx, int bottomMarginPx,
                                 float terminalCornerRadiusPx) {
        mSideMarginPx = sideMarginPx;
        mTopMarginPx = topMarginPx;
        mBottomMarginPx = bottomMarginPx;
        if (mTerminalCornerRadiusPx != terminalCornerRadiusPx) {
            mTerminalCornerRadiusPx = terminalCornerRadiusPx;
            applyDress();
        }
        applyLayout();
    }

    /**
     * Shows {@code content}, replacing whatever the panel held.
     *
     * @param wide the {@code ?} table, which spans the terminal; false is the corner strip.
     */
    public void show(@NonNull View content, boolean wide) {
        boolean sizeChanged = mWide != wide;
        mWide = wide;
        removeAllViews();
        addView(content, new LayoutParams(
            wide ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT));
        if (sizeChanged) applyDress();
        applyLayout();
        animate().cancel();
        setVisibility(VISIBLE);
        if (mShowing) {
            // Strip to table under one hold: swap the content, keep the surface. Replaying the
            // entrance would read as a second panel arriving on top of the first.
            setAlpha(TerminalHintSurface.REST_ALPHA);
            setTranslationY(0f);
            return;
        }
        mShowing = true;
        setAlpha(0f);
        setTranslationY(-dp(10));
        animate().alpha(TerminalHintSurface.REST_ALPHA).translationY(0f).setDuration(ENTER_MS)
            .setInterpolator(Motion.settle()).start();
    }

    public void hide(boolean animated) {
        if (!mShowing) {
            setVisibility(GONE);
            return;
        }
        mShowing = false;
        animate().cancel();
        if (!animated) {
            setVisibility(GONE);
            setAlpha(0f);
            setTranslationY(0f);
            removeAllViews();
            return;
        }
        animate().alpha(0f).translationY(-dp(8)).setDuration(EXIT_MS)
            .withEndAction(() -> {
                setVisibility(GONE);
                setTranslationY(0f);
                removeAllViews();
            }).start();
    }

    public boolean isShowing() {
        return mShowing;
    }

    /**
     * The wide table meets both of the terminal's top corners; the corner strip meets only the
     * trailing one and hangs clear of the other.
     */
    private void applyDress() {
        float free = TerminalHintSurface.freeCornerRadiusPx(getContext(), mTerminalCornerRadiusPx,
            getHeight());
        mFreeCornerRadiusPx = free;
        setBackground(TerminalHintSurface.background(getContext(),
            mWide ? mTerminalCornerRadiusPx : 0f, mTerminalCornerRadiusPx, free));
    }

    private void applyLayout() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams))
            return;
        FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) params;
        int targetWidth = mWide ? FrameLayout.LayoutParams.MATCH_PARENT
            : FrameLayout.LayoutParams.WRAP_CONTENT;
        int targetGravity = mWide ? Gravity.TOP : (Gravity.TOP | Gravity.END);
        int startMargin = mWide ? mSideMarginPx : 0;
        if (frameParams.width == targetWidth && frameParams.gravity == targetGravity
            && frameParams.getMarginEnd() == mSideMarginPx
            && frameParams.getMarginStart() == startMargin
            && frameParams.topMargin == mTopMarginPx
            && frameParams.bottomMargin == mBottomMarginPx) {
            return;
        }
        frameParams.width = targetWidth;
        frameParams.gravity = targetGravity;
        frameParams.setMarginStart(startMargin);
        frameParams.setMarginEnd(mSideMarginPx);
        frameParams.topMargin = mTopMarginPx;
        // The bottom inset is not where the panel sits, it is where it has to stop: a wrap-content
        // child of a FrameLayout measures against the parent less its margins, so this is what
        // hands the terminal's floor down to the scrolling table inside.
        frameParams.bottomMargin = mBottomMarginPx;
        setLayoutParams(frameParams);
    }

    /** Top-trailing by default; the wide table widens itself when it arrives. */
    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        return params;
    }

    /** Whether a screen point lands on this panel, for the table's outside-tap retirement. */
    public boolean containsScreenPoint(float rawX, float rawY) {
        if (!mShowing || getVisibility() != VISIBLE)
            return false;
        int[] location = new int[2];
        getLocationOnScreen(location);
        return rawX >= location[0] && rawX <= location[0] + getWidth()
            && rawY >= location[1] && rawY <= location[1] + getHeight();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
