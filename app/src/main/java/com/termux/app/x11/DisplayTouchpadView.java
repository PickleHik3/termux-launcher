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
 * over the Display place. A finger moves the pointer; a tap clicks; a second finger scrolls, and
 * two fingers tapping together click the right button; hold, then move, to drag. The small
 * arrow in its bottom-left corner leaves mouse mode and brings the keyboard back.
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
    private static final long TAP_MS = 240L;
    private static final long HOLD_MS = 380L;
    private static final float RADIUS_DP = 20f;
    private static final float BACK_SIZE_DP = 40f;
    private static final float BACK_INSET_DP = 10f;

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
    private boolean mTwoFingers;
    private boolean mOnBack;
    private float mScrollLastY;
    private final Runnable mHold = this::onHold;

    /** A finger held still long enough: the left button goes down and stays down for a drag. */
    private void onHold() {
        if (mMoved || mTwoFingers || mOnBack) return;
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
        mGlyphPaint.setTextSize(dp(18f));
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
                mDownX = mLastX = event.getX();
                mDownY = mLastY = event.getY();
                mDownTime = event.getEventTime();
                mMoved = false;
                mDragging = false;
                mTwoFingers = false;
                mOnBack = mBack.contains(mDownX, mDownY);
                if (!mOnBack) postDelayed(mHold, HOLD_MS);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(mHold);
                if (mDragging && display != null) {
                    display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, false, true);
                    mDragging = false;
                }
                mTwoFingers = true;
                mScrollLastY = (event.getY(0) + event.getY(1)) / 2f;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mOnBack) return true;
                if (mTwoFingers && event.getPointerCount() >= 2) {
                    float y = (event.getY(0) + event.getY(1)) / 2f;
                    float dy = y - mScrollLastY;
                    if (Math.abs(dy) > mTouchSlop / 2f) {
                        mMoved = true;
                        if (display != null) display.sendMouseWheelEvent(0f, dy);
                        mScrollLastY = y;
                    }
                    return true;
                }
                float x = event.getX();
                float y = event.getY();
                if (!mMoved && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) {
                    mMoved = true;
                    removeCallbacks(mHold);
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
                    int button = mTwoFingers ? InputStub.BUTTON_RIGHT : InputStub.BUTTON_LEFT;
                    display.sendMouseEvent(0f, 0f, button, true, true);
                    display.sendMouseEvent(0f, 0f, button, false, true);
                }
                mTwoFingers = false;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mHold);
                if (mDragging && display != null) {
                    display.sendMouseEvent(0f, 0f, InputStub.BUTTON_LEFT, false, true);
                }
                mDragging = false;
                mTwoFingers = false;
                mOnBack = false;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mHold);
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
