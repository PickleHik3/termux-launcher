package com.termux.app.x11;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.x11.LorieView;
import com.termux.x11.input.InputStub;

/**
 * The display's touchpad: it takes the keyboard's place, and its size, while mouse mode is on
 * over the Display place. Its gestures are a laptop's. One finger moves the pointer and a tap
 * clicks; hold, or tap and touch again, then move, to drag. Two fingers scroll, running on after
 * a fast lift, and pinch to zoom; two fingers tapping together click the right button. Three
 * fingers tapping click the middle button, swiping sideways switch windows, and swiping down
 * bring the keyboard back. The small arrow in its bottom-left corner does the same. A strip down
 * its trailing edge scrolls on its own: drag it with one thumb and no second finger is needed,
 * and flicking it coasts the same way a two-finger scroll does. A second finger set down on the
 * strip is ignored; the first keeps scrolling.
 *
 * <p>It lies over the place, so it is drawn solid: one opaque rounded panel in the overlay
 * surface colour, square-cornered against the dock or rounded as a card with the surfaces, and
 * with no rim at rest. The only stroke it draws is the ring that appears while a drag is held.
 * Standing in a split keyboard's parting it is flush instead — no inset, and the halves' own
 * radius — so the parting reads as a piece cut out of one surface. The strip stands inside that
 * panel along its trailing edge, a faint track down its centre with a short grip pill in the
 * accent colour, and the dot grid that marks the pointing area stops at the strip's own edge; a
 * gap too narrow to spare the strip's width and still leave room to point in drops it rather
 * than grow.
 */
public final class DisplayTouchpadView extends View {

    public interface Listener {
        /** The arrow was tapped: leave mouse mode. */
        void onExitRequested();
    }

    /** Where the pointer goes. */
    public interface PointerSink {
        @Nullable LorieView display();
    }

    private static final float GAIN = 1.25f;
    /**
     * Two-finger travel per wheel notch. A wheel click is one discrete step for X, so the pad
     * counts finger travel and sends one step each time this much has passed, rather than a step
     * per frame - which was fast and jumpy. Natural direction, as a laptop touchpad under
     * libinput: the content follows the fingers.
     */
    private static final float SCROLL_NOTCH_DP = 26f;
    /** What one wheel click sends, in the units a real wheel's click arrives as. */
    private static final float SCROLL_NOTCH_UNITS = 100f;
    /** A doubling of the gap between two fingers is four zoom clicks. */
    private static final float PINCH_STEP_LOG2 = 0.25f;
    /** How far three fingers travel before their swipe counts. */
    private static final float SWIPE_DP = 48f;
    private static final long TAP_MS = 240L;
    private static final long HOLD_MS = 380L;
    /** A touch this soon after a tap that then moves is a drag: tap, touch, pull. */
    private static final long TAP_DRAG_MS = 280L;
    private static final float RADIUS_DP = 20f;
    private static final float BACK_SIZE_DP = 28f;
    private static final float BACK_INSET_DP = 8f;
    /** The scroll strip along the pad's trailing edge, drawn this wide. */
    private static final float STRIP_WIDTH_DP = 30f;
    /**
     * The band, from the pad's trailing edge, a finger has to land in to take the strip — the
     * same value {@code DisplayScaleRailView.HIT_BAND_DP} uses, so both edge controls take the
     * same reach to land a thumb on.
     */
    private static final float STRIP_HIT_BAND_DP = 40f;
    /**
     * Vertical travel per notch on the strip: a shorter throw than the two-finger scroll's
     * {@link #SCROLL_NOTCH_DP}, since one thumb's whole reach along the strip is what has to
     * cover the same wheel clicks.
     */
    private static final float STRIP_SCROLL_NOTCH_DP = 17f;
    /** Below this much pointing area left over, the strip hides rather than crowd it further. */
    private static final float STRIP_MIN_POINTING_WIDTH_DP = 60f;
    private static final float STRIP_GRIP_HEIGHT_DP = 28f;
    private static final float STRIP_GRIP_WIDTH_DP = 6f;
    private static final float STRIP_TRACK_INSET_DP = 10f;

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStripTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStripGripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final RectF mBack = new RectF();
    /** The scroll strip's own rect, along the pad's trailing edge; empty when it is hidden. */
    private final RectF mStrip = new RectF();
    /** The grip pill drawn centred in the strip; empty along with {@link #mStrip}. */
    private final RectF mStripGrip = new RectF();
    private final int mTouchSlop;
    private final boolean mCard;
    /**
     * The pad stands in a split keyboard's parting rather than over the whole keyboard: it is
     * then flush with the halves either side of it, so it drops the card's inset and takes
     * their radius instead of its own.
     */
    private boolean mInGap;
    private float mGapRadiusPx;

    @NonNull private final PointerSink mSink;
    @Nullable private Listener mListener;

    private float mLastX, mLastY;
    private float mDownX, mDownY;
    private long mDownTime;
    private boolean mMoved;
    private boolean mDragging;
    /** The most fingers down at once in this gesture; it names the tap's button. */
    private int mMaxFingers;
    private boolean mOnBack;
    /** The gesture started on the scroll strip; it owns every pointer until the fingers lift. */
    private boolean mOnStrip;
    /** The last tap's time, for a touch soon after it that turns into a drag. */
    private long mLastTapTime = TouchpadGesturePolicy.NO_TAP;
    private boolean mTapDragArmed;
    private TouchpadGesturePolicy.TwoFingerMode mTwoFingerMode = TouchpadGesturePolicy.TwoFingerMode.UNDECIDED;
    /** The fingers' midpoint last seen, and where it was when the current count began. */
    private float mCentroidLastX, mCentroidLastY;
    private float mCentroidStartX, mCentroidStartY;
    /** Two-finger travel since the last notch, signed, in px. */
    private float mScrollAccumX, mScrollAccumY;
    /** The gap between two fingers when they landed, and the zoom clicks sent since. */
    private float mPinchStartSpread;
    private int mPinchClicksSent;
    private boolean mSwipeFired;
    @Nullable private android.view.VelocityTracker mVelocity;
    /** Carries a two-finger scroll on after a fast lift; its axes are finger travel in px. */
    @Nullable private android.widget.Scroller mFling;
    private int mFlingLastX, mFlingLastY;
    private final Runnable mHold = this::onHold;
    private final Runnable mFlingStep = new Runnable() {
        @Override
        public void run() {
            if (mFling == null || !mFling.computeScrollOffset()) return;
            int x = mFling.getCurrX();
            int y = mFling.getCurrY();
            mScrollAccumX += x - mFlingLastX;
            mScrollAccumY += y - mFlingLastY;
            mFlingLastX = x;
            mFlingLastY = y;
            sendScrollNotches(mSink.display());
            if (!mFling.isFinished()) postOnAnimation(this);
        }
    };

    /** A finger held still long enough: the left button goes down and stays down for a drag. */
    private void onHold() {
        if (mMoved || mMaxFingers > 1 || mOnBack) return;
        LorieView display = mSink.display();
        if (display == null) return;
        mDragging = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, true, true);
        invalidate();
    }

    public DisplayTouchpadView(@NonNull Context context, @NonNull PointerSink sink, boolean card,
                               int surfaceColor, int onSurfaceColor, int accentColor) {
        super(context);
        mSink = sink;
        mCard = card;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        // The pad lies over the place in both shapes, so it is solid in both: the surface colour
        // at full alpha, and no rim at rest. The only stroke left is the drag ring below.
        mFillPaint.setColor(ColorUtils.setAlphaComponent(surfaceColor, 255));
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(1.5f));
        mStrokePaint.setColor(ColorUtils.setAlphaComponent(onSurfaceColor, 120));
        mBackPaint.setColor(ColorUtils.setAlphaComponent(accentColor, 46));
        mGlyphPaint.setColor(accentColor);
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        mGlyphPaint.setTextSize(dp(13f));
        mDotPaint.setColor(ColorUtils.setAlphaComponent(onSurfaceColor, 40));
        // The strip's own idiom: a faint track in on-surface, same low alpha as the dot grid,
        // and a grip pill in the full accent colour so a thumb can find it at a glance.
        mStripTrackPaint.setStyle(Paint.Style.STROKE);
        mStripTrackPaint.setStrokeWidth(dp(1.5f));
        mStripTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        mStripTrackPaint.setColor(ColorUtils.setAlphaComponent(onSurfaceColor, 40));
        mStripGripPaint.setColor(accentColor);
        setClickable(true);
        setFocusable(false);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * Whether the pad is standing in a split keyboard's parting, and the radius the halves'
     * slabs are drawn with. Flush in the gap: no inset, and the halves' shape, so the three
     * read as one surface with the parting cut out of it.
     */
    public void setInSplitGap(boolean inGap, float slabRadiusPx) {
        if (mInGap == inGap && Float.compare(mGapRadiusPx, slabRadiusPx) == 0) return;
        mInGap = inGap;
        mGapRadiusPx = slabRadiusPx;
        layOutPanel(getWidth(), getHeight());
        invalidate();
    }

    /** The panel's own radius: the halves' in the parting, its card corner over the keyboard. */
    private float panelRadius() {
        return mInGap ? mGapRadiusPx : dp(RADIUS_DP);
    }

    private void layOutPanel(int w, int h) {
        float inset = mCard && !mInGap ? dp(1f) : 0f;
        mBounds.set(inset, inset, w - inset, h - inset);
        float size = dp(BACK_SIZE_DP);
        float margin = dp(BACK_INSET_DP);
        mBack.set(margin, h - margin - size, margin + size, h - margin);
        layOutStrip();
    }

    /**
     * The strip's rect along the pad's trailing edge, inside the panel inset; empty (and so
     * hidden and untouchable) when the panel is too narrow to spare its width and still leave a
     * usable pointing area, as {@link TouchpadGesturePolicy#stripFits} decides.
     */
    private void layOutStrip() {
        float stripWidth = dp(STRIP_WIDTH_DP);
        boolean fits = TouchpadGesturePolicy.stripFits(mBounds.width(), stripWidth,
            dp(STRIP_MIN_POINTING_WIDTH_DP));
        if (!fits) {
            mStrip.setEmpty();
            mStripGrip.setEmpty();
            return;
        }
        mStrip.set(mBounds.right - stripWidth, mBounds.top, mBounds.right, mBounds.bottom);
        float gripHalfHeight = dp(STRIP_GRIP_HEIGHT_DP) / 2f;
        float gripHalfWidth = dp(STRIP_GRIP_WIDTH_DP) / 2f;
        float centerX = mStrip.centerX();
        float centerY = mStrip.centerY();
        mStripGrip.set(centerX - gripHalfWidth, centerY - gripHalfHeight,
            centerX + gripHalfWidth, centerY + gripHalfHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layOutPanel(w, h);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float radius = panelRadius();
        canvas.drawRoundRect(mBounds, radius, radius, mFillPaint);
        // A faint grid of dots says "this is a surface you move across", nothing more. It stops
        // at the strip's own left edge, when the strip is up, so the pointing area reads smaller.
        boolean stripShown = !mStrip.isEmpty();
        float dotRight = stripShown ? mStrip.left : mBounds.right;
        float step = dp(28f);
        for (float y = mBounds.top + step; y < mBounds.bottom - step / 2f; y += step) {
            for (float x = mBounds.left + step; x < dotRight - step / 2f; x += step) {
                canvas.drawCircle(x, y, dp(1f), mDotPaint);
            }
        }
        // The one stroke the pad draws: a ring while a drag is held, gone the moment it ends.
        if (mDragging) {
            canvas.drawRoundRect(mBounds, radius, radius, mStrokePaint);
        }
        if (stripShown) {
            float trackX = mStrip.centerX();
            float trackInset = dp(STRIP_TRACK_INSET_DP);
            canvas.drawLine(trackX, mStrip.top + trackInset, trackX, mStrip.bottom - trackInset,
                mStripTrackPaint);
            float gripRadius = mStripGrip.width() / 2f;
            canvas.drawRoundRect(mStripGrip, gripRadius, gripRadius, mStripGripPaint);
        }
        canvas.drawRoundRect(mBack, mBack.height() / 2f, mBack.height() / 2f, mBackPaint);
        float baseline = mBack.centerY() - (mGlyphPaint.ascent() + mGlyphPaint.descent()) / 2f;
        canvas.drawText("", mBack.centerX(), baseline, mGlyphPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        LorieView display = mSink.display();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopFling();
                mDownX = mLastX = event.getX();
                mDownY = mLastY = event.getY();
                mDownTime = event.getEventTime();
                mMoved = false;
                mDragging = false;
                mMaxFingers = 1;
                mSwipeFired = false;
                mTwoFingerMode = TouchpadGesturePolicy.TwoFingerMode.UNDECIDED;
                mOnBack = mBack.contains(mDownX, mDownY);
                mOnStrip = !mOnBack && !mStrip.isEmpty()
                    && TouchpadGesturePolicy.stripHit(mDownX, mBounds.right, dp(STRIP_HIT_BAND_DP));
                mTapDragArmed = !mOnBack && !mOnStrip
                    && TouchpadGesturePolicy.tapDragArmed(mDownTime, mLastTapTime, TAP_DRAG_MS);
                if (mVelocity == null) mVelocity = android.view.VelocityTracker.obtain();
                else mVelocity.clear();
                mVelocity.addMovement(event);
                if (!mOnBack && !mOnStrip) postDelayed(mHold, HOLD_MS);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger on the strip does nothing: the first keeps scrolling, and
                // none of the two/three-finger state below gets to touch its accumulators.
                if (mOnStrip) return true;
                removeCallbacks(mHold);
                if (mDragging && display != null) {
                    display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, false, true);
                    mDragging = false;
                    invalidate();
                }
                if (mVelocity != null) mVelocity.addMovement(event);
                mMaxFingers = Math.max(mMaxFingers, event.getPointerCount());
                mCentroidStartX = mCentroidLastX = centroidX(event);
                mCentroidStartY = mCentroidLastY = centroidY(event);
                mScrollAccumX = 0f;
                mScrollAccumY = 0f;
                mPinchStartSpread = spread(event);
                mPinchClicksSent = 0;
                mTwoFingerMode = TouchpadGesturePolicy.TwoFingerMode.UNDECIDED;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mOnBack) return true;
                if (mVelocity != null) mVelocity.addMovement(event);
                if (mOnStrip) {
                    float y = event.getY();
                    mScrollAccumY += y - mLastY;
                    mLastY = y;
                    sendStripScrollNotches(display);
                    return true;
                }
                int fingers = event.getPointerCount();
                if (mMaxFingers >= 3) {
                    if (fingers >= 3) handleThreeFingers(event);
                    return true;
                }
                if (mMaxFingers == 2) {
                    if (fingers >= 2) handleTwoFingers(event, display);
                    return true;
                }
                float x = event.getX();
                float y = event.getY();
                if (!mMoved && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) {
                    mMoved = true;
                    removeCallbacks(mHold);
                    if (mTapDragArmed && display != null) {
                        mDragging = true;
                        display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, true, true);
                        invalidate();
                    }
                }
                if (mMoved && display != null) {
                    display.sendMouseEvent((x - mLastX) * GAIN, (y - mLastY) * GAIN,
                        InputStub.BUTTON_UNDEFINED, false, true);
                }
                mLastX = x;
                mLastY = y;
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (mVelocity != null) mVelocity.addMovement(event);
                // The first finger off a scroll ends it; a fast lift lets it run on.
                if (mMaxFingers == 2 && mTwoFingerMode == TouchpadGesturePolicy.TwoFingerMode.SCROLL
                    && event.getPointerCount() == 2) {
                    flingScroll(event.getPointerId(event.getActionIndex()));
                }
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(mHold);
                long held = event.getEventTime() - mDownTime;
                if (mOnBack) {
                    mOnBack = false;
                    if (mBack.contains(event.getX(), event.getY())) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        if (mListener != null) mListener.onExitRequested();
                    }
                    return true;
                }
                if (mOnStrip) {
                    // No travel is a tap on the strip, which does nothing — never a click.
                    mOnStrip = false;
                    flingScroll(event.getPointerId(event.getActionIndex()));
                    recycleVelocity();
                    return true;
                }
                if (mDragging) {
                    mDragging = false;
                    if (display != null) {
                        display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, false, true);
                    }
                    invalidate();
                } else if (!mMoved && held < TAP_MS && display != null) {
                    int button = TouchpadGesturePolicy.tapButton(mMaxFingers);
                    display.sendMouseEvent(0f, 0f, button, true, true);
                    display.sendMouseEvent(0f, 0f, button, false, true);
                    if (button == InputStub.BUTTON_LEFT) mLastTapTime = event.getEventTime();
                }
                recycleVelocity();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mHold);
                if (mDragging && display != null) {
                    display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, false, true);
                }
                mDragging = false;
                mOnBack = false;
                mOnStrip = false;
                recycleVelocity();
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /**
     * Two fingers moving: their midpoint's travel scrolls, the change in their gap zooms, and
     * whichever shows first owns the gesture until the fingers lift.
     */
    private void handleTwoFingers(@NonNull MotionEvent event, @Nullable LorieView display) {
        float x = centroidX(event);
        float y = centroidY(event);
        float spread = spread(event);
        if (mTwoFingerMode == TouchpadGesturePolicy.TwoFingerMode.UNDECIDED) {
            float travel = (float) Math.hypot(x - mCentroidStartX, y - mCentroidStartY);
            mTwoFingerMode = TouchpadGesturePolicy.decideTwoFingers(travel,
                spread - mPinchStartSpread, mTouchSlop / 2f);
            if (mTwoFingerMode == TouchpadGesturePolicy.TwoFingerMode.UNDECIDED) {
                mCentroidLastX = x;
                mCentroidLastY = y;
                return;
            }
            mMoved = true;
            // Nothing before the decision counts: the notches start from here.
            mCentroidLastX = x;
            mCentroidLastY = y;
            mPinchStartSpread = spread;
        }
        if (mTwoFingerMode == TouchpadGesturePolicy.TwoFingerMode.SCROLL) {
            mScrollAccumX += x - mCentroidLastX;
            mScrollAccumY += y - mCentroidLastY;
            mCentroidLastX = x;
            mCentroidLastY = y;
            sendScrollNotches(display);
            return;
        }
        int clicks = TouchpadGesturePolicy.pinchClicks(mPinchStartSpread, spread, PINCH_STEP_LOG2);
        while (mPinchClicksSent != clicks && display != null) {
            int step = clicks > mPinchClicksSent ? 1 : -1;
            mPinchClicksSent += step;
            // Fingers spreading zoom in: Ctrl with the wheel turning up, as on a laptop.
            display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_CTRL_LEFT, true);
            display.sendMouseWheelEvent(0f, -step * SCROLL_NOTCH_UNITS);
            display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_CTRL_LEFT, false);
        }
    }

    /**
     * Three fingers moving: a swipe, once, in the direction they went. Sideways switches windows
     * with the chords the display's window manager binds; down brings the keyboard back.
     */
    private void handleThreeFingers(@NonNull MotionEvent event) {
        if (mSwipeFired) return;
        float dx = centroidX(event) - mCentroidStartX;
        float dy = centroidY(event) - mCentroidStartY;
        TouchpadGesturePolicy.Swipe swipe = TouchpadGesturePolicy.swipe(dx, dy, dp(SWIPE_DP));
        if (swipe == TouchpadGesturePolicy.Swipe.NONE) return;
        mSwipeFired = true;
        mMoved = true;
        LorieView display = mSink.display();
        switch (swipe) {
            case LEFT:
            case RIGHT:
                if (display == null) return;
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                sendChord(display, swipe == TouchpadGesturePolicy.Swipe.RIGHT);
                break;
            case DOWN:
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                if (mListener != null) mListener.onExitRequested();
                break;
            default:
                break;
        }
    }

    /** Alt+Tab forward, Alt+Shift+Tab back: the modifiers wrap the key so none is left held. */
    private static void sendChord(@NonNull LorieView display, boolean back) {
        display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_ALT_LEFT, true);
        if (back) display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_SHIFT_LEFT, true);
        display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_TAB, true);
        display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_TAB, false);
        if (back) display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_SHIFT_LEFT, false);
        display.sendKeyEvent(0, android.view.KeyEvent.KEYCODE_ALT_LEFT, false);
    }

    /** Turn accumulated travel into wheel clicks, one per notch, in the natural direction. */
    private void sendScrollNotches(@Nullable LorieView display) {
        float notch = dp(SCROLL_NOTCH_DP);
        // Fingers moving down bring the content down: a wheel-up click, which arrives as a
        // negative unit like a real wheel's.
        int notchesY = TouchpadGesturePolicy.notchCount(mScrollAccumY, notch);
        if (notchesY != 0) {
            mScrollAccumY -= notchesY * notch;
            if (display != null) {
                float unit = -Math.signum((float) notchesY) * SCROLL_NOTCH_UNITS;
                for (int i = 0, n = Math.abs(notchesY); i < n; i++) {
                    display.sendMouseWheelEvent(0f, unit);
                }
            }
        }
        int notchesX = TouchpadGesturePolicy.notchCount(mScrollAccumX, notch);
        if (notchesX != 0) {
            mScrollAccumX -= notchesX * notch;
            if (display != null) {
                float unit = -Math.signum((float) notchesX) * SCROLL_NOTCH_UNITS;
                for (int i = 0, n = Math.abs(notchesX); i < n; i++) {
                    display.sendMouseWheelEvent(unit, 0f);
                }
            }
        }
    }

    /**
     * The strip's own, shorter notch: one thumb's vertical drag alone, ticking as it crosses
     * each one so the strip has the same feel a wheel's clicks do.
     */
    private void sendStripScrollNotches(@Nullable LorieView display) {
        float notch = dp(STRIP_SCROLL_NOTCH_DP);
        int notches = TouchpadGesturePolicy.notchCount(mScrollAccumY, notch);
        if (notches == 0) return;
        mScrollAccumY -= notches * notch;
        float unit = -Math.signum((float) notches) * SCROLL_NOTCH_UNITS;
        for (int i = 0, n = Math.abs(notches); i < n; i++) {
            if (display != null) display.sendMouseWheelEvent(0f, unit);
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    /** Let the scroll run on from the speed of the finger that lifted, slowing as a fling does. */
    private void flingScroll(int pointerId) {
        if (mVelocity == null) return;
        mVelocity.computeCurrentVelocity(1000);
        float vx = mVelocity.getXVelocity(pointerId);
        float vy = mVelocity.getYVelocity(pointerId);
        int minimum = ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();
        if (Math.hypot(vx, vy) < minimum) return;
        if (mFling == null) mFling = new android.widget.Scroller(getContext());
        mFlingLastX = 0;
        mFlingLastY = 0;
        mFling.fling(0, 0, Math.round(vx), Math.round(vy), Integer.MIN_VALUE / 2,
            Integer.MAX_VALUE / 2, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
        postOnAnimation(mFlingStep);
    }

    private void stopFling() {
        removeCallbacks(mFlingStep);
        if (mFling != null) mFling.abortAnimation();
    }

    private void recycleVelocity() {
        if (mVelocity != null) {
            mVelocity.recycle();
            mVelocity = null;
        }
    }

    private static float centroidX(@NonNull MotionEvent event) {
        int n = event.getPointerCount();
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += event.getX(i);
        return n == 0 ? 0f : sum / n;
    }

    private static float centroidY(@NonNull MotionEvent event) {
        int n = event.getPointerCount();
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += event.getY(i);
        return n == 0 ? 0f : sum / n;
    }

    /** The gap between the first two fingers. */
    private static float spread(@NonNull MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        return (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mHold);
        // A pad taken away mid-drag must not leave X holding the button.
        if (mDragging) {
            LorieView display = mSink.display();
            if (display != null) display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, false, true);
            mDragging = false;
        }
        stopFling();
        recycleVelocity();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
