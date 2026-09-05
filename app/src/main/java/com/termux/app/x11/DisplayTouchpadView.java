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
 * bring the keyboard back. The small arrow in its bottom-left corner does the same.
 *
 * <p>It is drawn as a filled rounded slab where the dock is flush and as an outlined card where
 * the surfaces float, so it belongs to whichever kit is on.
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

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final RectF mBack = new RectF();
    private final int mTouchSlop;
    private final boolean mCard;

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
    /** The last tap's time, for a touch soon after it that turns into a drag. */
    private long mLastTapTime = Long.MIN_VALUE;
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
        mFillPaint.setColor(card ? ColorUtils.setAlphaComponent(onSurfaceColor, 10)
            : ColorUtils.setAlphaComponent(surfaceColor, 230));
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(1.5f));
        mStrokePaint.setColor(ColorUtils.setAlphaComponent(onSurfaceColor, card ? 64 : 24));
        mBackPaint.setColor(ColorUtils.setAlphaComponent(accentColor, 46));
        mGlyphPaint.setColor(accentColor);
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        mGlyphPaint.setTextSize(dp(13f));
        mDotPaint.setColor(ColorUtils.setAlphaComponent(onSurfaceColor, 40));
        setClickable(true);
        setFocusable(false);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = mCard ? dp(1f) : 0f;
        mBounds.set(inset, inset, w - inset, h - inset);
        float size = dp(BACK_SIZE_DP);
        float margin = dp(BACK_INSET_DP);
        mBack.set(margin, h - margin - size, margin + size, h - margin);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float radius = dp(RADIUS_DP);
        canvas.drawRoundRect(mBounds, radius, radius, mFillPaint);
        canvas.drawRoundRect(mBounds, radius, radius, mStrokePaint);
        // A faint grid of dots says "this is a surface you move across", nothing more.
        float step = dp(28f);
        for (float y = mBounds.top + step; y < mBounds.bottom - step / 2f; y += step) {
            for (float x = mBounds.left + step; x < mBounds.right - step / 2f; x += step) {
                canvas.drawCircle(x, y, dp(1f), mDotPaint);
            }
        }
        if (mDragging) {
            mStrokePaint.setAlpha(120);
            canvas.drawRoundRect(mBounds, radius, radius, mStrokePaint);
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
                mTapDragArmed = !mOnBack && mDownTime - mLastTapTime <= TAP_DRAG_MS;
                if (mVelocity == null) mVelocity = android.view.VelocityTracker.obtain();
                else mVelocity.clear();
                mVelocity.addMovement(event);
                if (!mOnBack) postDelayed(mHold, HOLD_MS);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
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
        while (Math.abs(mScrollAccumY) >= notch) {
            float step = Math.signum(mScrollAccumY);
            mScrollAccumY -= step * notch;
            if (display != null) display.sendMouseWheelEvent(0f, -step * SCROLL_NOTCH_UNITS);
        }
        while (Math.abs(mScrollAccumX) >= notch) {
            float step = Math.signum(mScrollAccumX);
            mScrollAccumX -= step * notch;
            if (display != null) display.sendMouseWheelEvent(-step * SCROLL_NOTCH_UNITS, 0f);
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
        stopFling();
        recycleVelocity();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
