package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.statusbar.TopPaneClockForm;

/**
 * Clock widget for the modular top-pane slot: a thin host of {@link ClockFaceRenderer}, which
 * draws the faces. What is left here is the slot's side of the clock — measuring to the slot's
 * height, ticking once a second while shown, asking for animation frames, gating taps to the
 * digits, and the content description. The renderer's date hairline stops at this view's edges;
 * the slot carries it on to the pane gutter.
 *
 * <p>The widget reports its content width so the slot can hand the remaining space to media or
 * pinned notifications, and it compresses through {@link TopPaneClockForm} instead of ever changing
 * the pane height.
 */
public final class TerminalClockWidget extends View {

    private static final float SLOT_HEIGHT_DP =
        com.termux.app.statusbar.StatusBarEdgeGeometry.SLOT_DOCKED_DP;

    private final ClockFaceRenderer mRenderer;
    private final Runnable mTicker = new Runnable() {
        @Override public void run() {
            if (!mTickerRunning || !isAttachedToWindow()) return;
            updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
            long delay = 1000L - Math.floorMod(System.currentTimeMillis(), 1000L);
            postDelayed(this, Math.max(16L, delay));
        }
    };
    private final Runnable mSyncTicker = this::syncTicker;
    private boolean mTickerRunning;

    public TerminalClockWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mRenderer = new ClockFaceRenderer(getResources());
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        refreshColors(context);
        updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
    }

    public void setStyle(@Nullable String style) {
        if (!mRenderer.setStyle(style)) return;
        requestLayout();
        invalidate();
        updateContentDescription();
    }

    @NonNull
    String getStyle() {
        return mRenderer.getStyle();
    }

    /**
     * Horizontal placement of the FULL-form time band and date text; compact forms ignore it.
     */
    public void setAlignment(@Nullable String alignment) {
        if (!mRenderer.setAlignment(alignment)) return;
        requestLayout();
        invalidate();
    }

    @NonNull
    String getAlignment() {
        return mRenderer.getAlignment();
    }

    /** Which grid form renders. Changing it re-measures, since the content width changes with it. */
    public void setForm(@NonNull TopPaneClockForm form) {
        if (!mRenderer.setForm(form)) return;
        requestLayout();
        invalidate();
    }

    @NonNull
    public TopPaneClockForm getForm() {
        return mRenderer.getForm();
    }

    public void setUseAmPm(boolean useAmPm) {
        if (!mRenderer.setUseAmPm(useAmPm)) return;
        updateTime(System.currentTimeMillis(), SystemClock.uptimeMillis());
        requestLayout();
    }

    boolean isUsingAmPm() {
        return mRenderer.isUsingAmPm();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshColors(getContext());
        if (mRenderer.playLoadFlourish(SystemClock.uptimeMillis())) invalidate();
        post(mSyncTicker);
    }

    @Override
    protected void onDetachedFromWindow() {
        mHighRefresh = -1;
        stopTicker();
        removeCallbacks(mSyncTicker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mSyncTicker == null) return;
        removeCallbacks(mSyncTicker);
        post(mSyncTicker);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mSyncTicker == null) return;
        removeCallbacks(mSyncTicker);
        post(mSyncTicker);
    }

    private void syncTicker() {
        if (isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE) startTicker();
        else stopTicker();
    }

    private void startTicker() {
        if (mTickerRunning) return;
        mTickerRunning = true;
        removeCallbacks(mTicker);
        mTicker.run();
    }

    private void stopTicker() {
        mTickerRunning = false;
        removeCallbacks(mTicker);
    }

    /** The bar re-inked or the scheme changed: resolve the roles again and repaint. */
    public void onThemeChanged() {
        refreshColors(getContext());
        invalidate();
    }

    /**
     * The status bar's measured band under the clock: its effective surface and the ink resolved
     * on it. The faces tone their digits, LCD segments and labels against it, on the band ink's
     * side; see {@link ClockFaceRenderer#setBandInk}.
     */
    public void setBandInk(@androidx.annotation.ColorInt int surface,
                           @androidx.annotation.ColorInt int ink) {
        if (mRenderer.setBandInk(surface, ink)) invalidate();
    }

    /** Every face paints out of these roles, so a wallpaper or scheme change re-tints the whole grid. */
    void refreshColors(@NonNull Context context) {
        mRenderer.setPalette(ClockFaceRenderer.Palette.fromTheme(context));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = resolveSize(Math.round(dp(SLOT_HEIGHT_DP)), heightMeasureSpec);
        int width;
        if (mRenderer.getForm() == TopPaneClockForm.FULL
            && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = MeasureSpec.getSize(widthMeasureSpec);
        } else {
            width = resolveSize(Math.round(contentWidth()), widthMeasureSpec);
        }
        setMeasuredDimension(width, height);
    }

    /** Width the current form actually paints, so the slot can place content beside it. */
    public float contentWidth() {
        return mRenderer.contentWidth();
    }

    // ---- Drawing ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mRenderer.hasSnapshot() || getWidth() <= 0 || getHeight() <= 0) return;
        android.os.Trace.beginSection("Clock.onDraw");
        try {
            long now = SystemClock.uptimeMillis();
            mRenderer.draw(canvas, getWidth(), getHeight(), now);
            if (mRenderer.hasRunningAnimation(now)) requestAnimationFrame();
        } finally {
            android.os.Trace.endSection();
        }
    }

    /**
     * Asks for the next frame of a running flip. On a display that refreshes faster than 60 Hz
     * the next vsync is asked for on a 60 Hz cadence instead: a 340 ms card flip does not read any
     * differently at 120 frames a second than at 60, and the seconds card flips every second of
     * the day, so on the phone's 120 Hz panel that one animation was a third of all the frames the
     * launcher drew while idle (measured 2026-09-17). Everything else about the flip — its timing,
     * its curve — is a function of the clock, not of the frame count.
     */
    private void requestAnimationFrame() {
        if (isHighRefreshDisplay()) {
            postInvalidateDelayed(HIGH_REFRESH_FRAME_MS);
        } else {
            postInvalidateOnAnimation();
        }
    }

    /** One 60 Hz frame, the cadence a flip is drawn at on faster panels. */
    private static final long HIGH_REFRESH_FRAME_MS = 16L;
    /** -1 unknown, 0 no, 1 yes; asked of the display once, it does not change while attached. */
    private int mHighRefresh = -1;

    private boolean isHighRefreshDisplay() {
        if (mHighRefresh < 0) {
            android.view.Display display = getDisplay();
            float rate = display == null ? 60f : display.getRefreshRate();
            if (display == null) return false; // not attached yet: decide next time
            mHighRefresh = rate > 70f ? 1 : 0;
        }
        return mHighRefresh == 1;
    }

    /**
     * The slot lays this view out at the pane's full available width (so alignment can place the
     * clock left/center/right within it), which leaves blank space beside and below the painted
     * face. Gate the click listener to the time digits instead of the whole laid-out view: the
     * date row and the slack under it sit directly over the status row's window chips, and a tap
     * meant for a chip must not open the clock app.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
            && !isInsideTapTarget(event.getX(), event.getY())) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @VisibleForTesting
    boolean isInsideTapTarget(float x, float y) {
        return mRenderer.isOverTime(x, y, getWidth(), getHeight());
    }

    /**
     * The trailing edge of the painted clock, in view pixels.
     *
     * <p>In FULL form the view fills the whole slot and paints inside it at its own alignment, so
     * the view's right edge is nowhere near the clock's. Anything placed beside the clock — the
     * surface editor's face picker — has to ask for this rather than measure the view.
     */
    public float paintedRightPx() {
        return mRenderer.paintedRightPx(getWidth(), getHeight());
    }

    /** The leading edge of the painted clock, in view pixels; see {@link #paintedRightPx()}. */
    public float paintedLeftPx() {
        return mRenderer.paintedLeftPx(getWidth(), getHeight());
    }

    /**
     * Center Y, in view pixels, of the FULL-form time band — the digits' line, above the date
     * row — so the status bar's place icon can sit beside the time on its line rather than on
     * the slot's. -1 in any other form.
     */
    public float fullBandCenterYPx() {
        return fullBandCenterYPx(getHeight());
    }

    /** The same for a face {@code heightPx} tall: what the slot asks while it is still measuring. */
    public float fullBandCenterYPx(int heightPx) {
        return mRenderer.fullBandCenterYPx(heightPx);
    }

    /** Height, in view pixels, of the FULL-form time band; -1 in any other form. */
    public float fullBandHeightPx() {
        return mRenderer.fullBandHeightPx(getHeight());
    }

    /**
     * Center Y, in view pixels, of the FULL-form date hairline — for the slot's edge-to-edge
     * extensions. -1 when the current form/style draws no hairline (compact forms, tape).
     */
    public float fullRuleCenterYPx() {
        return mRenderer.fullRuleCenterYPx(getHeight());
    }

    /** The FULL-form hairline's half thickness in view pixels, matching drawRule's 0.5dp. */
    public float fullRuleHalfThicknessPx() {
        return mRenderer.fullRuleHalfThicknessPx(getHeight());
    }

    /** The FULL-form hairline colour, for the slot's edge-to-edge extensions. */
    public int fullRuleColor() {
        return mRenderer.fullRuleColor();
    }

    // ---- Fit ----------------------------------------------------------------

    /**
     * Hands the clock the room the slot can give it. Height is the band between the slot's air
     * (the clock never touches the bar's edges), width the run the neighbours leave. A height of
     * 0 turns the fit off. {@code shared} is true while media or notifications sit beside the
     * clock, which keeps the compact face's size as the floor.
     */
    public void setFitBudget(float widthPx, float heightPx, boolean shared) {
        if (mRenderer.setFitBudget(widthPx, heightPx, shared)) invalidate();
    }

    /** Width the FULL face paints inside the given budget, so the slot can size the clock to it. */
    public float fitWidthPx(float widthBudgetPx, float heightBudgetPx, boolean shared) {
        return mRenderer.fitWidthPx(widthBudgetPx, heightBudgetPx, shared);
    }

    @VisibleForTesting
    void updateTime(long wallTime, long animationTime) {
        mRenderer.updateTime(wallTime, animationTime);
        updateContentDescription();
        invalidate();
    }

    private void updateContentDescription() {
        String description = mRenderer.contentDescription();
        if (description != null) setContentDescription(description);
    }

    /** Lazy mode swaps the digits instead of folding them; see {@link ClockFaceRenderer#setLazyMode}. */
    public void setLazyMode(boolean lazy) {
        if (mRenderer.setLazyMode(lazy)) invalidate();
    }

    @VisibleForTesting
    float progress(int digit, long now, long duration) {
        return mRenderer.progress(digit, now, duration);
    }

    @VisibleForTesting
    float secondsProgress(int digit, long now, long duration) {
        return mRenderer.secondsProgress(digit, now, duration);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
