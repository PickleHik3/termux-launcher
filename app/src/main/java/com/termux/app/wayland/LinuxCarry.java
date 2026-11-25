package com.termux.app.wayland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.haptics.Haptics;
import com.termux.app.wall.BorderDrag;
import com.termux.app.wall.BorderHold;
import com.termux.app.wall.BorderHoldGlyph;

/**
 * One carry of a Linux window (spec #384, "Carry"): the lifted window follows the finger as a small
 * picture over the wall, the wall pages under it by the border hold at a side or by a second
 * finger's swipe, and the lift decides ({@link LinuxCarryPolicy}): between two windows it becomes
 * its own window there, anywhere else it goes back. The activity routes every touch to
 * {@link #onTouch} while a carry is under way, so the stream survives the wall paging.
 *
 * <p>Nothing repaints at rest: the picture is redrawn when the finger moves and, while a hold
 * fills, once per frame. The picture is one half-size bitmap, given back at the end.
 */
public final class LinuxCarry {

    /** What a carry asks of the activity. */
    public interface Host {
        /** The layer the lifted picture is drawn in, over the whole window. */
        @NonNull ViewGroup overlay();
        /** The row place of the window in front; -1 while the front page is not a row window. */
        int frontPlace();
        int placeCount();
        /** Where window {@code id} stands in the row now; -1 when it is gone. */
        int placeOf(long id);
        /** The wall's left and right edge in window coordinates, into {@code out}. */
        void wallSpan(@NonNull float[] out);
        boolean canPage(int steps);
        void page(int steps);
        /** Moves window {@code id} from row place {@code from} to {@code to} and brings it in front. */
        void drop(long id, int from, int to);
        /** Brings window {@code id} back in front where it was. */
        void putBack(long id);
    }

    /** The picture's size against the half-size bitmap it is made from. */
    private static final float GHOST_SCALE = 0.7f;
    private static final float GHOST_FALLBACK_WIDTH_DP = 140f;
    private static final float GHOST_FALLBACK_HEIGHT_DP = 200f;

    /** Frame-source counter for the idle audit: linuxCarry. */
    private static final com.termux.terminal.FrameProbe.Source CARRY_FRAMES =
        com.termux.terminal.FrameProbe.source("linuxCarry");

    @NonNull private final Host mHost;
    private final long mId;
    @Nullable private Bitmap mPicture;
    @NonNull private final CarryView mView;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mFire = this::onHoldElapsed;
    private final BorderHold mHold = new BorderHold();
    private final float[] mSpan = new float[2];
    private final float mSlop;
    private final float mStripPx;
    private final float mSecondPx;
    private boolean mActive;
    private int mPrimaryId = -1;
    private float mX;
    private float mY;
    private int mSecondId = -1;
    private float mSecondAnchorX;
    private long mScheduledStart = -1L;
    /** The gap the preview marks, -1 for none, and where it stands. */
    private int mPreviewGap = -1;
    private float mPreviewX;

    LinuxCarry(@NonNull Context context, @NonNull Host host, long id, @Nullable Bitmap picture) {
        mHost = host;
        mId = id;
        mPicture = picture;
        float density = context.getResources().getDisplayMetrics().density;
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mStripPx = LinuxCarryPolicy.EDGE_STRIP_DP * density;
        mSecondPx = LinuxCarryPolicy.SECOND_SWIPE_DP * density;
        mView = new CarryView(context);
    }

    boolean isActive() {
        return mActive;
    }

    long windowId() {
        return mId;
    }

    /** The window was lifted by pointer {@code pointerId} at ({@code x}, {@code y}) in window coordinates. */
    void begin(float x, float y, int pointerId) {
        mActive = true;
        mPrimaryId = pointerId;
        mX = x;
        mY = y;
        mHost.overlay().addView(mView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        moveTo(x, y, SystemClock.uptimeMillis());
        Haptics.tick(mView, HapticFeedbackConstants.LONG_PRESS);
    }

    /** Every touch of the window while the carry is under way; always consumed. */
    boolean onTouch(@NonNull MotionEvent ev) {
        if (!mActive) return false;
        long now = SystemClock.uptimeMillis();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                int p = ev.findPointerIndex(mPrimaryId);
                if (p < 0) {
                    end(false);
                    break;
                }
                moveTo(ev.getX(p), ev.getY(p), now);
                int s = mSecondId < 0 ? -1 : ev.findPointerIndex(mSecondId);
                if (s >= 0) {
                    int steps = LinuxCarryPolicy.pageSteps(ev.getX(s) - mSecondAnchorX, mSecondPx);
                    if (steps != 0 && mHost.canPage(steps)) {
                        // The next page needs another whole swipe.
                        mSecondAnchorX = ev.getX(s);
                        mHost.page(steps);
                        mView.invalidate();
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = ev.getActionIndex();
                int id = ev.getPointerId(i);
                if (id != mPrimaryId && mSecondId < 0) {
                    mSecondId = id;
                    mSecondAnchorX = ev.getX(i);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int id = ev.getPointerId(ev.getActionIndex());
                if (id == mSecondId) mSecondId = -1;
                else if (id == mPrimaryId) end(true);
                break;
            }
            case MotionEvent.ACTION_UP:
                end(true);
                break;
            case MotionEvent.ACTION_CANCEL:
                end(false);
                break;
            default:
                break;
        }
        return true;
    }

    /** Back, a home press or anything else that takes the carry: the window goes back. */
    void cancel() {
        if (mActive) end(false);
    }

    private void moveTo(float x, float y, long now) {
        mX = x;
        mY = y;
        mHost.wallSpan(mSpan);
        int front = mHost.frontPlace();
        int count = mHost.placeCount();
        int carried = mHost.placeOf(mId);
        mPreviewGap = LinuxCarryPolicy.previewGap(x, mSpan[0], mSpan[1], mStripPx, front, count,
            carried);
        mPreviewX = mPreviewGap < 0 ? 0f : mPreviewGap == front ? mSpan[0] : mSpan[1];
        // The border hold lives on the same strips the drop does; a finger on one keeps its hold
        // while it stays within the slop and begins another where it settles.
        int edge = front < 0 ? 0 : LinuxCarryPolicy.edgeAt(x, mSpan[0], mSpan[1], mStripPx);
        BorderDrag.Border side = edge < 0 ? BorderDrag.Border.LEFT
            : edge > 0 ? BorderDrag.Border.RIGHT : BorderDrag.Border.NONE;
        if (side == BorderDrag.Border.NONE
            || !mHost.canPage(BorderHold.steps(side))) {
            mHold.end();
        } else if (mHold.side() != side) {
            mHold.begin(side, x, y, now);
        } else {
            mHold.moved(x, y, mSlop, true, now);
        }
        if (mHold.isFilling()) {
            if (mHold.startMs() != mScheduledStart) {
                mScheduledStart = mHold.startMs();
                mHandler.removeCallbacks(mFire);
                mHandler.postDelayed(mFire, Math.max(0L, mScheduledStart + BorderHold.HOLD_MS - now));
            }
        } else {
            mHandler.removeCallbacks(mFire);
            mScheduledStart = -1L;
        }
        mView.invalidate();
    }

    private void onHoldElapsed() {
        if (!mActive) return;
        int steps = BorderHold.steps(mHold.side());
        if (!mHold.fires(SystemClock.uptimeMillis(), mHost.canPage(steps))) return;
        Haptics.tick(mView, HapticFeedbackConstants.LONG_PRESS);
        mHost.page(steps);
        mView.invalidate();
    }

    /** The lift ({@code lifted}) decides; anything else puts the window back. */
    private void end(boolean lifted) {
        mActive = false;
        mHandler.removeCallbacks(mFire);
        mHold.end();
        LinuxCarryPolicy.Drop drop = null;
        int from = mHost.placeOf(mId);
        if (lifted) {
            mHost.wallSpan(mSpan);
            drop = LinuxCarryPolicy.decide(mX, mSpan[0], mSpan[1], mStripPx, mHost.frontPlace(),
                mHost.placeCount(), from);
        }
        ViewGroup parent = (ViewGroup) mView.getParent();
        if (parent != null) parent.removeView(mView);
        Bitmap picture = mPicture;
        mPicture = null;
        if (picture != null) picture.recycle();
        if (from < 0) return;
        if (drop != null && drop.kind == LinuxCarryPolicy.Kind.BETWEEN) {
            mHost.drop(mId, from, drop.to);
        } else {
            mHost.putBack(mId);
        }
    }

    /** The lifted picture, the gap preview and the hold's glyph, over the wall. */
    private final class CarryView extends View {
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final Path mClip = new Path();
        private final BorderHoldGlyph mGlyph = new BorderHoldGlyph();
        private final int[] mOrigin = new int[2];
        private final int mAccent;

        CarryView(@NonNull Context context) {
            super(context);
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            mAccent = com.termux.app.wall.BorderGrabber.accent(context);
            mStroke.setStyle(Paint.Style.STROKE);
            mStroke.setColor(mAccent);
            mFill.setColor(mAccent);
            mFill.setAlpha(60);
            mBar.setColor(mAccent);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (!mActive) return;
            float density = getResources().getDisplayMetrics().density;
            getLocationInWindow(mOrigin);
            float x = mX - mOrigin[0];
            float y = mY - mOrigin[1];
            if (mPreviewGap >= 0) {
                float w = 4f * density;
                float px = mPreviewX - mOrigin[0];
                mRect.set(px - w / 2f, 12f * density, px + w / 2f, getHeight() - 12f * density);
                mBar.setAlpha(230);
                canvas.drawRoundRect(mRect, w / 2f, w / 2f, mBar);
            }
            Bitmap picture = mPicture;
            float w = picture != null ? picture.getWidth() * GHOST_SCALE
                : GHOST_FALLBACK_WIDTH_DP * density;
            float h = picture != null ? picture.getHeight() * GHOST_SCALE
                : GHOST_FALLBACK_HEIGHT_DP * density;
            mRect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f);
            float radius = 14f * density;
            int save = canvas.save();
            mClip.rewind();
            mClip.addRoundRect(mRect, radius, radius, Path.Direction.CW);
            canvas.clipPath(mClip);
            if (picture != null && !picture.isRecycled()) {
                mFill.setAlpha(235);
                canvas.drawBitmap(picture, null, mRect, mFill);
            } else {
                mFill.setAlpha(90);
                canvas.drawRect(mRect, mFill);
            }
            canvas.restoreToCount(save);
            mStroke.setStrokeWidth(2f * density);
            canvas.drawRoundRect(mRect, radius, radius, mStroke);
            if (mHold.isFilling()) {
                long now = SystemClock.uptimeMillis();
                float edge = mHold.side() == BorderDrag.Border.LEFT ? mSpan[0] : mSpan[1];
                mGlyph.draw(canvas, mHold.side(), edge - mOrigin[0], y, mHold.glyph(now), density,
                    mAccent);
                if (mHold.fill(now) < 1f) {
                    CARRY_FRAMES.tick();
                    postInvalidateOnAnimation();
                }
            }
        }
    }
}
