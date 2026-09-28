package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.launcher.az.AzTabPolicy;
import com.termux.app.launcher.az.AzTabReveal;
import com.termux.app.place.PlaceLayout.Edge;

/**
 * The minimised A&#8211;Z index on screen: a small glass pull tab on the index's edge, laid over
 * the content, and the letters that slide out over the content while a finger holds it.
 *
 * <p>The layer covers the canvas and takes nothing from it: a touch that does not land on the
 * tab is refused on its DOWN, so the pane under it gets it exactly as if the layer were not there.
 * A touch that lands on the tab is the tab's from that first event ({@link AzTabReveal}) — it sits
 * on the page's border band, and taking the DOWN is what keeps the wall's border hold-drag and the
 * corner tab from ever seeing it — and every event of it is handed to the letters as if the finger
 * had landed on them, so touch, slide along and scrub is one gesture. The letters are the same
 * {@link AzScrubRowView} every other edge uses, with the same callback, the same floating strip
 * and the same launch on release; this view only moves them.
 *
 * <p>Where everything stands is {@link AzTabPolicy}'s answer. The slide is a critically damped
 * {@link Spring} that arrives at once under reduced motion, and the whole layer is clipped to the
 * canvas so the letters come out from behind its edge rather than over the bars beside it.
 */
public final class AzPullTabLayer extends FrameLayout {

    /** Quick enough to be out before the thumb has moved a letter, with no overshoot. */
    private static final float SLIDE_STIFFNESS = 900f;
    private static final float SLIDE_DAMPING = 60f;

    @NonNull private final AzTabReveal mReveal = new AzTabReveal();
    @NonNull private final Spring mSlide = new Spring(0f, SLIDE_STIFFNESS, SLIDE_DAMPING);
    @NonNull private final TabView mTab;
    @NonNull private final FrameLayout mHost;
    @NonNull private final View mSheet;
    @Nullable private AzScrubRowView mRow;

    @NonNull private Edge mEdge = Edge.BOTTOM;
    private int mThicknessPx;
    private int mMarginPx;
    private int mSideInsetPx;
    private float mSheetRadiusPx;
    private boolean mReducedMotion;

    @Nullable private AzTabPolicy.Placement mPlacement;
    @NonNull private AzTabPolicy.Box mRevealBox = AzTabPolicy.EMPTY;

    /** Whether the stream in progress began on the tab, and so is being handed to the letters. */
    private boolean mOwnsTouch;
    private boolean mTicking;
    private long mLastFrameNanos;
    private final Runnable mTick = this::tick;
    private final int[] mLocation = new int[2];

    public AzPullTabLayer(@NonNull Context context) {
        this(context, null);
    }

    public AzPullTabLayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // One finger is the gesture; a second one is part of the same stream, never its own.
        setMotionEventSplittingEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);

        mTab = new TabView(context);
        mTab.setContentDescription(context.getString(R.string.az_pull_tab_description));
        mTab.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(mTab);

        mHost = new FrameLayout(context);
        mHost.setClipChildren(false);
        mHost.setClipToPadding(false);
        mHost.setVisibility(INVISIBLE);
        // The sheet is what is rounded and clipped, not the host: the letters' wave lifts them
        // past the bar's own face and the host has to let it out, as every other bar's does.
        mSheet = new View(context);
        mSheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float radius = Math.min(mSheetRadiusPx,
                    Math.min(view.getWidth(), view.getHeight()) / 2f);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        mSheet.setClipToOutline(true);
        mHost.addView(mSheet, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
        addView(mHost);
    }

    // ------------------------------------------------------------------------ what the host sets

    /**
     * The edge the index stands on and the bar's own sizes: the letters' thickness (band and
     * chin), the air it keeps from the edge, and the side inset a row keeps like the dock does.
     * Moving the tab to another edge puts the letters away at once.
     */
    public void configure(@NonNull Edge edge, int thicknessPx, int marginPx, int sideInsetPx,
                          float sheetRadiusPx) {
        boolean moved = edge != mEdge;
        boolean changed = moved || thicknessPx != mThicknessPx || marginPx != mMarginPx
            || sideInsetPx != mSideInsetPx;
        mEdge = edge;
        mThicknessPx = Math.max(0, thicknessPx);
        mMarginPx = Math.max(0, marginPx);
        mSideInsetPx = Math.max(0, sideInsetPx);
        if (mSheetRadiusPx != sheetRadiusPx) {
            mSheetRadiusPx = sheetRadiusPx;
            mSheet.invalidateOutline();
        }
        if (moved) tuck();
        if (changed) requestLayout();
    }

    /**
     * Shows the tab or takes it away. Away, the letters are tucked at once and the layer takes no
     * touch at all.
     */
    public void setTabShown(boolean shown) {
        mReveal.setEnabled(shown);
        if (!shown) tuck();
        setVisibility(shown ? VISIBLE : GONE);
    }

    /** The frame the letters' own view is put in; the host installs it. */
    @NonNull
    public FrameLayout revealHost() {
        return mHost;
    }

    /** The letters the tab hands its gesture to: the view the host installed in the frame. */
    public void setRow(@Nullable AzScrubRowView row) {
        mRow = row;
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /** The glass under the tab and under the letters; two drawables, since each has its bounds. */
    public void setGlass(@Nullable Drawable tabGlass, @Nullable Drawable sheetGlass) {
        mTab.setGlass(tabGlass);
        mSheet.setBackground(sheetGlass);
    }

    /** The ink the letters are drawn in, which the tab's own "A" and grip wear too. */
    public void setInk(int ink) {
        mTab.setInk(ink);
    }

    /**
     * How far the letters stand from where they rest out, right now: the slide in progress. The
     * scrub measures the bar where it will be, not where the spring has carried it this frame, so
     * the host subtracts this from what it reads off the screen.
     */
    public float slideOffsetX() {
        return mHost.getTranslationX();
    }

    public float slideOffsetY() {
        return mHost.getTranslationY();
    }

    /** Whether a point on the screen is on the tab's touch area. */
    public boolean isOnTab(float rawX, float rawY) {
        if (getVisibility() != VISIBLE || mPlacement == null || !mReveal.isEnabled()) return false;
        getLocationOnScreen(mLocation);
        return mPlacement.hit(rawX - mLocation[0], rawY - mLocation[1]);
    }

    /** Whether a finger is holding the tab and the letters are out for it. */
    public boolean isHeld() {
        return mReveal.isHeld();
    }

    /**
     * Puts the letters behind the tab at once, with no slide. A gesture still in progress is
     * cancelled for the letters first, so the scrub is never left holding a finger it will not
     * hear from again.
     */
    public void tuck() {
        if (mOwnsTouch) {
            mOwnsTouch = false;
            cancelRowGesture();
        }
        mReveal.tuck();
        stopTicking();
        mSlide.reset(0f);
        applySlide();
    }

    // ------------------------------------------------------------------------ layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        computeGeometry(width, height);
        AzTabPolicy.Box touch = mPlacement == null ? AzTabPolicy.EMPTY : mPlacement.touch;
        mTab.measure(exactly(touch.width()), exactly(touch.height()));
        mHost.measure(exactly(mRevealBox.width()), exactly(mRevealBox.height()));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        AzTabPolicy.Box touch = mPlacement == null ? AzTabPolicy.EMPTY : mPlacement.touch;
        layoutAt(mTab, touch);
        layoutAt(mHost, mRevealBox);
        applySlide();
    }

    private void computeGeometry(int width, int height) {
        float density = getResources().getDisplayMetrics().density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        AzTabPolicy.Placement placement = AzTabPolicy.place(mEdge, rtl, width, height, density);
        mPlacement = placement;
        mRevealBox = AzTabPolicy.revealBox(mEdge, width, height, mThicknessPx, mMarginPx,
            mSideInsetPx);
        mTab.setVisual(placement.visual.left - placement.touch.left,
            placement.visual.top - placement.touch.top,
            placement.visual.right - placement.touch.left,
            placement.visual.bottom - placement.touch.top,
            mEdge, rtl, AzTabPolicy.TAB_RADIUS_DP * density);
    }

    private static int exactly(float sizePx) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(sizePx)), MeasureSpec.EXACTLY);
    }

    private static void layoutAt(@NonNull View child, @NonNull AzTabPolicy.Box box) {
        int left = Math.round(box.left);
        int top = Math.round(box.top);
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }

    /** Everything the layer draws stays inside the canvas: the letters slide out from its edge. */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        int saved = canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restoreToCount(saved);
    }

    // ------------------------------------------------------------------------ touch

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mOwnsTouch = false;
            // Anywhere but the tab is the content's, refused here so it goes on to the pane.
            if (mPlacement == null || mRow == null || mRow.getParent() != mHost
                || !mPlacement.hit(event.getX(), event.getY()) || !mReveal.press()) {
                return false;
            }
            mOwnsTouch = true;
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            // Out before the letters hear the touch, so they are on screen to be measured.
            mHost.setVisibility(VISIBLE);
            slideTo(mReveal.target());
        }
        if (!mOwnsTouch) return false;
        forwardToRow(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // After the letters have had the release, and launched what it launches.
            mOwnsTouch = false;
            mReveal.release();
            slideTo(mReveal.target());
        }
        return true;
    }

    /** The event as the letters would have had it, had the finger landed on them. */
    private void forwardToRow(@NonNull MotionEvent event) {
        AzScrubRowView row = mRow;
        if (row == null || row.getParent() != mHost) return;
        MotionEvent local = MotionEvent.obtain(event);
        // Their resting place, not wherever the slide has them: the finger's position along the
        // letters is the same either way, and the scrub measures the bar where it will stand.
        local.offsetLocation(-(mHost.getLeft() + row.getLeft()), -(mHost.getTop() + row.getTop()));
        row.dispatchTouchEvent(local);
        local.recycle();
    }

    private void cancelRowGesture() {
        AzScrubRowView row = mRow;
        if (row == null || row.getParent() != mHost) return;
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        row.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    // ------------------------------------------------------------------------ the slide

    private void slideTo(float target) {
        mSlide.target = target;
        if (mReducedMotion || !isAttachedToWindow()) {
            stopTicking();
            mSlide.reset(target);
            applySlide();
            onSlideSettled();
            return;
        }
        if (mTicking) return;
        mTicking = true;
        mLastFrameNanos = System.nanoTime();
        postOnAnimation(mTick);
    }

    private void tick() {
        if (!mTicking) return;
        long now = System.nanoTime();
        float dt = Spring.clampDelta((now - mLastFrameNanos) / 1_000_000_000f);
        mLastFrameNanos = now;
        boolean moving = mSlide.tick(false, dt);
        if (!moving) mSlide.reset(mSlide.target);
        applySlide();
        if (moving) {
            postOnAnimation(mTick);
            return;
        }
        mTicking = false;
        onSlideSettled();
    }

    private void onSlideSettled() {
        mReveal.settled(mSlide.value);
        applySlide();
    }

    private void stopTicking() {
        mTicking = false;
        removeCallbacks(mTick);
    }

    private void applySlide() {
        float progress = mSlide.value;
        float[] offset = AzTabPolicy.slideOffset(mEdge, progress,
            AzTabPolicy.travelPx(mThicknessPx, mMarginPx));
        mHost.setTranslationX(offset[0]);
        mHost.setTranslationY(offset[1]);
        int visibility = mReveal.lettersVisible() ? VISIBLE : INVISIBLE;
        if (mHost.getVisibility() != visibility) mHost.setVisibility(visibility);
        mTab.setAlpha(AzTabReveal.tabAlpha(progress));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        tuck();
    }

    @VisibleForTesting
    @NonNull
    AzTabReveal.Phase revealPhase() {
        return mReveal.phase();
    }

    // ------------------------------------------------------------------------ the tab itself

    /**
     * The pill: the dock's glass, a hairline rim, the letter A at the leading end and the six-dot
     * grip at the trailing one, as the Layout editor draws it. The view is the whole touch area;
     * the pill is drawn inside it.
     */
    static final class TabView extends View {

        /** The rim's alpha out of 255: there, but not a drawn border. */
        private static final int RIM_ALPHA = 60;
        /** The grip's dots at the design's 45%. */
        private static final int GRIP_ALPHA = 115;
        /** Where the A and the grip stand along the pill: the design's 12.9 and 35 of 48. */
        private static final float GLYPH_AT = 0.268f;
        private static final float GRIP_AT = 0.729f;
        private static final float GLYPH_SP = 11f;
        private static final float DOT_RADIUS_DP = 1.6f;
        /** The design's grip: two dots 6 apart along the pill, three 5 apart across it. */
        private static final float DOT_PITCH_ALONG_DP = 6f;
        private static final float DOT_PITCH_ACROSS_DP = 5f;

        private final RectF mVisual = new RectF();
        private final RectF mScratch = new RectF();
        private final Path mClip = new Path();
        private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Nullable private Drawable mGlass;
        private int mInk = Color.WHITE;
        @NonNull private Edge mEdge = Edge.BOTTOM;
        private boolean mRtl;
        private float mRadius;

        TabView(@NonNull Context context) {
            super(context);
            mRim.setStyle(Paint.Style.STROKE);
            mRim.setStrokeWidth(getResources().getDisplayMetrics().density);
            mGlyph.setTextAlign(Paint.Align.CENTER);
            mGlyph.setTypeface(Typeface.DEFAULT_BOLD);
            mDot.setStyle(Paint.Style.FILL);
        }

        void setVisual(float left, float top, float right, float bottom, @NonNull Edge edge,
                       boolean rtl, float radiusPx) {
            mVisual.set(left, top, right, bottom);
            mEdge = edge;
            mRtl = rtl;
            mRadius = radiusPx;
            mClip.reset();
            invalidate();
        }

        void setGlass(@Nullable Drawable glass) {
            mGlass = glass;
            invalidate();
        }

        void setInk(int ink) {
            if (mInk == ink) return;
            mInk = ink;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (mVisual.isEmpty()) return;
            float radius = Math.min(mRadius, Math.min(mVisual.width(), mVisual.height()) / 2f);
            if (mGlass != null) {
                mClip.reset();
                mClip.addRoundRect(mVisual, radius, radius, Path.Direction.CW);
                int saved = canvas.save();
                canvas.clipPath(mClip);
                mGlass.setBounds(Math.round(mVisual.left), Math.round(mVisual.top),
                    Math.round(mVisual.right), Math.round(mVisual.bottom));
                mGlass.draw(canvas);
                canvas.restoreToCount(saved);
            }
            float half = mRim.getStrokeWidth() / 2f;
            mScratch.set(mVisual.left + half, mVisual.top + half, mVisual.right - half,
                mVisual.bottom - half);
            mRim.setColor(withAlpha(mInk, RIM_ALPHA));
            canvas.drawRoundRect(mScratch, radius, radius, mRim);

            boolean column = mEdge.isOnSide();
            float length = column ? mVisual.height() : mVisual.width();
            float across = column ? mVisual.width() : mVisual.height();
            float glyphAlong = alongAt(GLYPH_AT, length);
            float gripAlong = alongAt(GRIP_AT, length);

            float density = getResources().getDisplayMetrics().density;
            float textSize = Math.min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                GLYPH_SP, getResources().getDisplayMetrics()), across * 0.5f);
            mGlyph.setTextSize(textSize);
            mGlyph.setColor(mInk);
            float gx = column ? mVisual.centerX() : mVisual.left + glyphAlong;
            float gy = column ? mVisual.top + glyphAlong : mVisual.centerY();
            canvas.drawText("A", gx, gy - (mGlyph.ascent() + mGlyph.descent()) / 2f, mGlyph);

            mDot.setColor(withAlpha(mInk, GRIP_ALPHA));
            float dot = DOT_RADIUS_DP * density;
            float pitchAlong = DOT_PITCH_ALONG_DP * density;
            float pitchAcross = DOT_PITCH_ACROSS_DP * density;
            float cx = column ? mVisual.centerX() : mVisual.left + gripAlong;
            float cy = column ? mVisual.top + gripAlong : mVisual.centerY();
            // Two dots along the pill and three across it, the way the design's grip stands on a
            // row; turned with the pill on a column.
            for (int a = 0; a < 2; a++) {
                for (int c = -1; c <= 1; c++) {
                    float along = (a - 0.5f) * pitchAlong;
                    float acrossOffset = c * pitchAcross;
                    float x = column ? cx + acrossOffset : cx + along;
                    float y = column ? cy + along : cy + acrossOffset;
                    canvas.drawCircle(x, y, dot, mDot);
                }
            }
        }

        /** A point {@code fraction} of the way along the pill from its leading end. */
        private float alongAt(float fraction, float length) {
            boolean mirrored = mRtl && !mEdge.isOnSide();
            return (mirrored ? 1f - fraction : fraction) * length;
        }

        private static int withAlpha(int color, int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
        }
    }
}
