package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
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

import java.util.Collections;
import java.util.List;

/**
 * The minimised A&#8211;Z index on screen: a glass half-pill flush against the physical screen's
 * edge, outside the pane's border, and the letters that slide out over the content while a finger
 * holds it.
 *
 * <p>The layer covers the whole screen, above the content and the dock, and takes nothing from
 * either: a touch that does not land on the tab is refused on its DOWN, so whatever is under it
 * gets it exactly as if the layer were not there. A touch that lands on the tab is the tab's from
 * that first event ({@link AzTabReveal}) — taking the DOWN is what keeps the wall's border
 * hold-drag and the corner tab from ever seeing it — and every event of it is handed to the
 * letters as if the finger had landed on their middle line at the same point along the edge
 * ({@link AzTabPolicy#shiftOntoLetters}), so touch, slide along and scrub is one gesture. The
 * letters are the same {@link AzScrubRowView} every other edge uses, with the same callback, the
 * same floating strip and the same launch on release; this view only moves them.
 *
 * <p>The letters belong to the canvas, which is where they come out of: the layer is told which
 * view that is ({@link #setCanvas}) and follows it as it moves, and it clips the letters to it so
 * they slide out from behind its edge rather than over the bars beside it. The tab stands outside
 * that clip, on the screen's side.
 *
 * <p>Where everything stands is {@link AzTabPolicy}'s answer. The slide is a critically damped
 * {@link Spring} that arrives at once under reduced motion.
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
    @Nullable private View mCanvas;

    @NonNull private Edge mEdge = Edge.BOTTOM;
    private int mThicknessPx;
    private int mMarginPx;
    private int mSideInsetPx;
    private float mSheetRadiusPx;
    private boolean mReducedMotion;
    private int mGlassBase = Color.BLACK;
    private int mLettersInk = Color.WHITE;

    @Nullable private AzTabPolicy.Placement mPlacement;
    /** The canvas, in this layer's pixels: what the letters are measured in and clipped to. */
    @NonNull private AzTabPolicy.Box mCanvasBox = AzTabPolicy.EMPTY;
    /** Where the letters rest while out, in this layer's pixels. */
    @NonNull private AzTabPolicy.Box mLettersBox = AzTabPolicy.EMPTY;

    /** Whether the stream in progress began on the tab, and so is being handed to the letters. */
    private boolean mOwnsTouch;
    /** How far the stream in progress is moved for the letters to hear it; set at its DOWN. */
    private float mShiftX;
    private float mShiftY;
    private boolean mTicking;
    private long mLastFrameNanos;
    private final Runnable mTick = this::tick;
    private final int[] mLocation = new int[2];
    private final int[] mCanvasLocation = new int[2];
    private final Rect mExclusion = new Rect();
    private final RectF mClip = new RectF();

    /** The canvas moving is the letters moving, and the tab with them along the screen's side. */
    private final OnLayoutChangeListener mCanvasListener =
        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!readCanvasBox().equals(mCanvasBox)) requestLayout();
        };

    public AzPullTabLayer(@NonNull Context context) {
        this(context, null);
    }

    public AzPullTabLayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // One finger is the gesture; a second one is part of the same stream, never its own.
        setMotionEventSplittingEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(true);

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

        mTab = new TabView(context);
        mTab.setContentDescription(context.getString(R.string.az_pull_tab_description));
        mTab.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(mTab);
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
     * The view the letters come out of: the content's own box, which the layer follows wherever
     * it is laid out — a keyboard opening under it, a bar taking a side of it.
     */
    public void setCanvas(@Nullable View canvas) {
        if (mCanvas == canvas) return;
        if (mCanvas != null) mCanvas.removeOnLayoutChangeListener(mCanvasListener);
        mCanvas = canvas;
        if (canvas != null) canvas.addOnLayoutChangeListener(mCanvasListener);
        requestLayout();
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

    /**
     * What the tab and the letters' sheet are made of: the dock's glass for each, two drawables
     * since each has its bounds, and what the tab is filled with under its glass
     * ({@link AzTabPolicy#tabFill}) — nothing while the glass is the material, the glass's base
     * made solid where there is no glass. {@code glassBase} is that base either way, which the
     * tab's "A" is made legible against.
     *
     * <p>The glass here is the tint alone, with no wallpaper frost and no Fancier Glass
     * refraction, and that is deliberate: the tab and the letters stand over live content — the
     * terminal, a widget — not over the wallpaper, so a frame of the wallpaper bent under them
     * would show a picture that is not behind them.</p>
     */
    public void setGlass(@Nullable Drawable tabGlass, @Nullable Drawable sheetGlass, int tabFill,
                         int glassBase) {
        mTab.setGlass(tabGlass, tabFill);
        mSheet.setBackground(sheetGlass);
        if (mGlassBase != glassBase) {
            mGlassBase = glassBase;
            mTab.setInk(AzTabPolicy.glyphInk(mGlassBase, mLettersInk));
        }
    }

    /** The ink the letters are drawn in, which the tab's "A" is seeded from. */
    public void setInk(int ink) {
        mLettersInk = ink;
        mTab.setInk(AzTabPolicy.glyphInk(mGlassBase, ink));
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

    /**
     * The visible half-pill on the screen, for the tour to point at, or false while there is no
     * tab to see.
     */
    public boolean tabRectOnScreen(@NonNull Rect out) {
        AzTabPolicy.Placement placement = mPlacement;
        if (!isShown() || placement == null || placement.visual.isEmpty()) return false;
        getLocationOnScreen(mLocation);
        out.set(Math.round(placement.visual.left) + mLocation[0],
            Math.round(placement.visual.top) + mLocation[1],
            Math.round(placement.visual.right) + mLocation[0],
            Math.round(placement.visual.bottom) + mLocation[1]);
        return true;
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
        measureTabAndLetters();
    }

    /**
     * Laid out from where the canvas stands now: this layer and the canvas are laid out in the
     * same pass, the canvas first, so its position is read here rather than guessed at measure.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mCanvasBox = readCanvasBox();
        computeGeometry(right - left);
        measureTabAndLetters();
        AzTabPolicy.Box touch = mPlacement == null ? AzTabPolicy.EMPTY : mPlacement.touch;
        layoutAt(mTab, touch);
        layoutAt(mHost, mLettersBox);
        applySlide();
        publishExclusion(touch);
    }

    private void measureTabAndLetters() {
        AzTabPolicy.Box touch = mPlacement == null ? AzTabPolicy.EMPTY : mPlacement.touch;
        mTab.measure(exactly(touch.width()), exactly(touch.height()));
        mHost.measure(exactly(mLettersBox.width()), exactly(mLettersBox.height()));
    }

    /** The canvas's box in this layer's pixels, or nothing while there is no canvas laid out. */
    @NonNull
    private AzTabPolicy.Box readCanvasBox() {
        View canvas = mCanvas;
        if (canvas == null || !canvas.isAttachedToWindow() || canvas.getWidth() <= 0
            || canvas.getHeight() <= 0) return AzTabPolicy.EMPTY;
        canvas.getLocationInWindow(mCanvasLocation);
        getLocationInWindow(mLocation);
        float left = mCanvasLocation[0] - mLocation[0];
        float top = mCanvasLocation[1] - mLocation[1];
        return new AzTabPolicy.Box(left, top, left + canvas.getWidth(), top + canvas.getHeight());
    }

    private void computeGeometry(int width) {
        float density = getResources().getDisplayMetrics().density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        AzTabPolicy.Placement placement = AzTabPolicy.placeOnScreen(mEdge, rtl, mCanvasBox,
            width, density);
        mPlacement = placement;
        AzTabPolicy.Box letters = AzTabPolicy.revealBox(mEdge, mCanvasBox.width(),
            mCanvasBox.height(), mThicknessPx, mMarginPx, mSideInsetPx);
        mLettersBox = mCanvasBox.isEmpty() ? AzTabPolicy.EMPTY : new AzTabPolicy.Box(
            mCanvasBox.left + letters.left, mCanvasBox.top + letters.top,
            mCanvasBox.left + letters.right, mCanvasBox.top + letters.bottom);
        mTab.setVisual(placement.visual.left - placement.touch.left,
            placement.visual.top - placement.touch.top,
            placement.visual.right - placement.touch.left,
            placement.visual.bottom - placement.touch.top,
            placement.side, AzTabPolicy.TAB_RADIUS_DP * density);
    }

    private static int exactly(float sizePx) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(sizePx)), MeasureSpec.EXACTLY);
    }

    private static void layoutAt(@NonNull View child, @NonNull AzTabPolicy.Box box) {
        int left = Math.round(box.left);
        int top = Math.round(box.top);
        child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }

    /**
     * The tab stands on the screen's side, which is where the system's back swipe starts: the
     * platform is told to leave its touch area to it, as the floating keyboard's grip does.
     */
    private void publishExclusion(@NonNull AzTabPolicy.Box touch) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        List<Rect> rects;
        if (touch.isEmpty()) {
            rects = Collections.emptyList();
        } else {
            mExclusion.set(Math.round(touch.left), Math.round(touch.top),
                Math.round(touch.right), Math.round(touch.bottom));
            rects = Collections.singletonList(new Rect(mExclusion));
        }
        setSystemGestureExclusionRects(rects);
    }

    /**
     * The letters stay inside the canvas, so they come out from behind its edge; the tab is
     * outside it, on the screen's side, and is drawn unclipped.
     */
    @Override
    protected boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
        if (child != mHost) return super.drawChild(canvas, child, drawingTime);
        if (mCanvasBox.isEmpty()) return false;
        int saved = canvas.save();
        mClip.set(mCanvasBox.left, mCanvasBox.top, mCanvasBox.right, mCanvasBox.bottom);
        canvas.clipRect(mClip);
        boolean more = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(saved);
        return more;
    }

    // ------------------------------------------------------------------------ touch

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mOwnsTouch = false;
            // Anywhere but the tab is someone else's, refused here so it goes on to them.
            if (mPlacement == null || mRow == null || mRow.getParent() != mHost
                || !mPlacement.hit(event.getX(), event.getY()) || !mReveal.press()) {
                return false;
            }
            mOwnsTouch = true;
            float[] shift = AzTabPolicy.shiftOntoLetters(mEdge, mLettersBox, event.getX(),
                event.getY());
            mShiftX = shift[0];
            mShiftY = shift[1];
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

    /**
     * The event as the letters would have had it, had the finger landed on their middle line: the
     * same point along the edge, moved across onto them by the shift the DOWN settled. Built
     * afresh rather than copied, so the screen position the scrub reads is moved as well as the
     * local one. A second finger is part of this one's stream and is not passed on.
     */
    private void forwardToRow(@NonNull MotionEvent event) {
        AzScrubRowView row = mRow;
        if (row == null || row.getParent() != mHost) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
            return;
        MotionEvent moved = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), action,
            event.getRawX() + mShiftX, event.getRawY() + mShiftY, event.getMetaState());
        moved.setSource(event.getSource());
        // Their resting place, not wherever the slide has them: the finger's position along the
        // letters is the same either way, and the scrub measures the bar where it will stand.
        getLocationOnScreen(mLocation);
        moved.offsetLocation(-(mLocation[0] + mHost.getLeft() + row.getLeft()),
            -(mLocation[1] + mHost.getTop() + row.getTop()));
        row.dispatchTouchEvent(moved);
        moved.recycle();
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
     * The half-pill: the dock's glass, or its base made solid where there is no glass, flat
     * against the screen's side and rounded on the other, with a hairline rim and the letter A.
     * The view is the whole touch area; the half-pill is drawn inside it.
     */
    static final class TabView extends View {

        /** The rim's alpha out of 255: there, but not a drawn border. */
        private static final int RIM_ALPHA = 60;
        private static final float GLYPH_SP = 12f;

        private final RectF mVisual = new RectF();
        private final Path mShape = new Path();
        private final float[] mRadii = new float[8];
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Nullable private Drawable mGlass;
        private int mFillColor;
        private int mInk = Color.WHITE;

        TabView(@NonNull Context context) {
            super(context);
            mFill.setStyle(Paint.Style.FILL);
            mRim.setStyle(Paint.Style.STROKE);
            mRim.setStrokeWidth(getResources().getDisplayMetrics().density);
            mGlyph.setTextAlign(Paint.Align.CENTER);
            mGlyph.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setVisual(float left, float top, float right, float bottom, @NonNull Edge side,
                       float radiusPx) {
            mVisual.set(left, top, right, bottom);
            // Round only the corners away from the screen's side.
            float radius = Math.max(0f,
                Math.min(radiusPx, Math.min(mVisual.width(), mVisual.height() / 2f)));
            boolean flatOnLeft = side == Edge.LEFT;
            float topLeft = flatOnLeft ? 0f : radius;
            float topRight = flatOnLeft ? radius : 0f;
            mRadii[0] = topLeft;
            mRadii[1] = topLeft;
            mRadii[2] = topRight;
            mRadii[3] = topRight;
            mRadii[4] = topRight;
            mRadii[5] = topRight;
            mRadii[6] = topLeft;
            mRadii[7] = topLeft;
            mShape.reset();
            if (!mVisual.isEmpty()) mShape.addRoundRect(mVisual, mRadii, Path.Direction.CW);
            invalidate();
        }

        void setGlass(@Nullable Drawable glass, int fillColor) {
            mGlass = glass;
            mFillColor = fillColor;
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
            int saved = canvas.save();
            canvas.clipPath(mShape);
            if (Color.alpha(mFillColor) > 0) {
                mFill.setColor(mFillColor);
                canvas.drawRect(mVisual, mFill);
            }
            if (mGlass != null) {
                mGlass.setBounds(Math.round(mVisual.left), Math.round(mVisual.top),
                    Math.round(mVisual.right), Math.round(mVisual.bottom));
                mGlass.draw(canvas);
            }
            canvas.restoreToCount(saved);
            mRim.setColor(withAlpha(mInk, RIM_ALPHA));
            canvas.drawPath(mShape, mRim);

            float textSize = Math.min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                GLYPH_SP, getResources().getDisplayMetrics()), mVisual.width() * 0.62f);
            mGlyph.setTextSize(textSize);
            mGlyph.setColor(mInk);
            canvas.drawText("A", mVisual.centerX(),
                mVisual.centerY() - (mGlyph.ascent() + mGlyph.descent()) / 2f, mGlyph);
        }

        private static int withAlpha(int color, int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
        }
    }
}
