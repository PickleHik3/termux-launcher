package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Collections;
import java.util.List;

/**
 * The card a floating keyboard rides in: a grab handle along the top and the keyboard host under
 * it, parked wherever the user last left it.
 *
 * <p>The frame is deliberately thin. It carries no idea of what a keyboard is — the keyboard's own
 * container is moved into {@link #contentHost()} unchanged, keeps its glass, its suggestion strip
 * and its height controls, and is measured by the normal layout pass against the width this frame
 * was given. What the frame owns is the handle and the drag: it clamps the offset it is dragged to
 * against the travel it was handed and reports where it ended up, so the arithmetic stays in
 * {@link FloatingKeyboardGeometry} and the memory stays in the store. The bottom-left corner is
 * the other half of that: a grip the card is resized from, which scales its width and the
 * keyboard's row height at once and reports both the same way. Out from that corner is bigger —
 * left widens the card, up makes its rows taller — so the two edges the finger is not on, the
 * right one and the bottom one, are the ones that stay put.</p>
 *
 * <p>It lives in {@code floating_keyboard_host}, which covers the whole content region. That is
 * what makes a keyboard parked halfway up the screen touchable: a view translated outside its
 * parent's bounds still draws, but never receives a touch.</p>
 */
public final class FloatingKeyboardFrame extends LinearLayout {

    /** Narrower than this and there is no keyboard left to type on, whatever the share says. */
    private static final float MIN_WIDTH_DP = 240f;

    /** The strip above the keys the card is dragged by. */
    static final float HANDLE_ROW_DP = 18f;

    /**
     * The corner the card is resized from, mirrored across from the floating terminal pane's,
     * which grips at bottom-right. A thumb, not a cursor: the zone is the whole 36dp corner even
     * though the handle drawn inside it is smaller.
     */
    static final float GRIP_DP = 36f;

    /** The handle drawn in it: two diagonals across the corner, the long one over the short one. */
    private static final float GRIP_STROKE_DP = 2f;
    private static final float GRIP_INSET_DP = 4f;
    private static final float GRIP_LONG_DP = 16f;
    private static final float GRIP_SHORT_DP = 9f;

    /** The pill drawn in the middle of that strip. */
    static final float PILL_WIDTH_DP = 52f;
    static final float PILL_HEIGHT_DP = 3.2f;

    /**
     * A 3.2dp pill rounds away to a hairline on the lowest densities, so it never draws thinner
     * than this. {@link R.drawable#floating_keyboard_grab_handle} carries the matching radius.
     */
    static final int PILL_MIN_HEIGHT_PX = 2;

    /** The user's floating width, read at measure time so the frame is never a pass behind it. */
    public interface WidthScaleSource {
        float widthScale();
    }

    /** The user's floating row height, read the same pull-style way, to start a resize from. */
    public interface HeightScaleSource {
        float heightScale();
    }

    /** Where the frame ended up, in pixels from the content's top-left corner. */
    public interface OnFrameMovedListener {
        /**
         * @param committed true when the finger has left the handle, which is when a new place is
         *     worth remembering; false for the frames in between, which only move the card.
         */
        void onFrameMoved(int xPx, int yPx, boolean committed);
    }

    /** Where the grip drag has taken the card's two scales. */
    public interface OnFrameResizedListener {
        /**
         * @param xPx the card's left edge, which moves as it widens because its right edge does not
         * @param committed true once the finger has left the grip, which is when the two scales are
         *     worth writing; false for the frames in between, which only preview them.
         */
        void onFrameResized(float widthScale, float heightScale, int xPx, boolean committed);
    }

    private final FrameLayout mHandle;
    private final FrameLayout mContentHost;

    @Nullable private OnFrameMovedListener mListener;
    @Nullable private OnFrameResizedListener mResizeListener;

    @Nullable private WidthScaleSource mWidthScale;
    @Nullable private HeightScaleSource mHeightScale;
    private int mTravelXPx;
    private int mTravelYPx;
    private int mPositionXPx;
    private int mPositionYPx;

    private float mDragStartRawX;
    private float mDragStartRawY;
    private int mDragStartXPx;
    private int mDragStartYPx;
    private boolean mDragging;

    private final Paint mGripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * The width the card is being resized to. NaN at rest, which is what sends {@link #onMeasure}
     * back to the stored share; a number only for as long as a finger is on the grip, so the live
     * preview never needs anything written to the store to be seen.
     */
    private float mResizeWidthScale = Float.NaN;
    private float mResizeHeightScale = Float.NaN;
    private boolean mResizing;
    private int mResizeStartContentWidthPx;
    private int mResizeStartWidthPx;
    private int mResizeStartKeyboardHeightPx;
    private float mResizeStartWidthScale = 1f;
    private float mResizeStartHeightScale = 1f;

    /**
     * The card's bottom edge is what a grip drag holds still, and the height that edge is measured
     * against arrives a layout late — the keyboard is told its new row height and answers with a
     * height of its own. So the pin outlives the finger: it is on from the touch down until the
     * size the drag asked for has settled, and {@link #ownsPosition()} is how the controller knows
     * not to put the card back where its remembered fraction says while that is going on.
     */
    private boolean mPinBottom;
    private int mPinnedHeightPx;
    private int mResizeStartHeightPx;
    private int mResizeStartYPx;
    private int mResizeStartContentHeightPx;

    /**
     * The grip's own rect, handed to the platform so a drag that starts in it is not read as a
     * back or home gesture. Only the grip: a 36dp square is far inside the 200dp per edge the
     * platform honours, and the keys have no business excluding themselves twice — the keyboard
     * view already publishes its own.
     */
    private final Rect mGripExclusionRect = new Rect();
    private final List<Rect> mGripExclusionRects = Collections.singletonList(mGripExclusionRect);

    public FloatingKeyboardFrame(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundResource(R.drawable.floating_keyboard_card);
        setElevation(dp(8f));
        // The card is opaque, so it takes the taps that land on its rim rather than letting them
        // through to the place it is parked over.
        setClickable(true);

        mHandle = new FrameLayout(context);
        mHandle.setContentDescription(
            context.getString(R.string.termux_in_app_keyboard_floating_handle));
        mHandle.setClickable(true);
        mHandle.setFocusable(true);
        View pill = new View(context);
        pill.setBackgroundResource(R.drawable.floating_keyboard_grab_handle);
        mHandle.addView(pill, new FrameLayout.LayoutParams(
            Math.round(dp(PILL_WIDTH_DP)), pillHeightPx(getResources().getDisplayMetrics().density),
            Gravity.CENTER));
        addView(mHandle,
            new LayoutParams(LayoutParams.MATCH_PARENT, Math.round(dp(HANDLE_ROW_DP))));

        mContentHost = new FrameLayout(context);
        mContentHost.setClipChildren(false);
        mContentHost.setClipToPadding(false);
        addView(mContentHost, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mHandle.setOnTouchListener((view, event) -> onHandleTouch(event));
    }

    /** Where the keyboard's own container goes. */
    @NonNull
    public FrameLayout contentHost() {
        return mContentHost;
    }

    /** The strip the frame is dragged by, for tests and for accessibility focus. */
    @NonNull
    public View grabHandle() {
        return mHandle;
    }

    /** The pill inside that strip, for tests. */
    @NonNull
    View grabPill() {
        return mHandle.getChildAt(0);
    }

    /** The pill's drawn thickness, never below {@link #PILL_MIN_HEIGHT_PX}. */
    static int pillHeightPx(float density) {
        return Math.max(PILL_MIN_HEIGHT_PX, Math.round(PILL_HEIGHT_DP * density));
    }

    /**
     * Where the frame reads the user's width share from, every time it is measured. Pull rather
     * than push: the share changes with the orientation, and a width written into the frame from a
     * layout-change listener is a request the parent has already passed — the frame would sit a
     * pass behind every rotation.
     */
    public void setWidthScaleSource(@Nullable WidthScaleSource source) {
        mWidthScale = source;
    }

    /** The same, for the row height a grip drag starts from. */
    public void setHeightScaleSource(@Nullable HeightScaleSource source) {
        mHeightScale = source;
    }

    public void setOnFrameResizedListener(@Nullable OnFrameResizedListener listener) {
        mResizeListener = listener;
    }

    /** The width the frame takes in a content region this wide. */
    public int frameWidthPx(int contentWidthPx) {
        return frameWidthPx(getContext(), contentWidthPx, widthScale());
    }

    /** The share in force: the one under the finger while resizing, the stored one otherwise. */
    private float widthScale() {
        if (!Float.isNaN(mResizeWidthScale)) return mResizeWidthScale;
        return mWidthScale == null ? 1f : mWidthScale.widthScale();
    }

    /** The room the frame has left to move sideways, which a resize changes as it goes. */
    int travelXPx() {
        return mTravelXPx;
    }

    /** The room the frame may be dragged in, from the content bounds it floats over. */
    public void setTravelPx(int travelXPx, int travelYPx) {
        mTravelXPx = Math.max(0, travelXPx);
        mTravelYPx = Math.max(0, travelYPx);
        // Re-seat inside the new travel: a rotation or a wider frame can leave the old offset
        // hanging off the edge.
        applyPositionPx(mPositionXPx, mPositionYPx);
    }

    /** Puts the frame at an offset from the content's top-left corner, clamped to the travel. */
    public void setPositionPx(int xPx, int yPx) {
        applyPositionPx(xPx, yPx);
    }

    public int positionXPx() {
        return mPositionXPx;
    }

    public int positionYPx() {
        return mPositionYPx;
    }

    public void setOnFrameMovedListener(@Nullable OnFrameMovedListener listener) {
        mListener = listener;
    }

    private void applyPositionPx(int xPx, int yPx) {
        mPositionXPx = FloatingKeyboardGeometry.clampPx(xPx, mTravelXPx);
        mPositionYPx = FloatingKeyboardGeometry.clampPx(yPx, mTravelYPx);
        setTranslationX(mPositionXPx);
        setTranslationY(mPositionYPx);
        // The rect is the card's own, so a move does not change it — but the platform maps it
        // through the card's translation when it is handed the rect, and not again afterwards.
        publishGripExclusionRect();
    }

    // --------------------------------------------------------------------- the grip

    /**
     * Whether a touch landed in the corner the card is resized from. The zone gives way to the
     * handle row rather than growing into it, so a card squeezed down to almost nothing still has
     * a pill to drag and never two gestures fighting over one pixel.
     *
     * <p>Out from the corner is bigger: left widens the card with its right edge fixed, and
     * <em>up</em> makes the keyboard's rows taller with its bottom edge fixed — the direction the
     * dock's own height pill uses, and the only one a card parked along the bottom of the screen
     * has any room for.</p>
     */
    boolean isInGripZone(float x, float y) {
        int height = getHeight();
        if (height <= 0) return false;
        float grip = dp(GRIP_DP);
        float top = Math.max(dp(HANDLE_ROW_DP), height - grip);
        return x >= 0f && x <= grip && y >= top && y <= height;
    }

    /** True for as long as a finger is on the grip. */
    boolean isResizing() {
        return mResizing;
    }

    /**
     * True while the grip, rather than the remembered fraction, says where the card sits: from the
     * touch down until the height the drag asked for has arrived and the bottom edge has been
     * pinned back against it.
     */
    boolean ownsPosition() {
        return mPinBottom;
    }

    /**
     * The grip in the card's own coordinates — what is drawn into, what a touch is tested against
     * and what the platform is asked to leave alone. Empty until the card has been laid out.
     */
    @NonNull
    Rect gripRect() {
        Rect rect = new Rect();
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return rect;
        int grip = Math.round(dp(GRIP_DP));
        int top = Math.max(Math.round(dp(HANDLE_ROW_DP)), height - grip);
        rect.set(0, top, Math.min(width, grip), height);
        return rect;
    }

    /**
     * Tells the platform to leave the grip alone. Parked in the bottom-left corner — where a
     * floating keyboard usually is — the grip lies under both the back-gesture strip along the
     * left edge and the home band along the bottom, and the system cancels the drag a few pixels
     * in. Published again on every layout and every move, because the platform maps the rect
     * through the card's translation when it is handed the rect and not afterwards.
     */
    private void publishGripExclusionRect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        Rect grip = gripRect();
        if (grip.isEmpty()) {
            if (mGripExclusionRect.isEmpty()) return;
            mGripExclusionRect.setEmpty();
            setSystemGestureExclusionRects(Collections.emptyList());
            return;
        }
        mGripExclusionRect.set(grip);
        setSystemGestureExclusionRects(mGripExclusionRects);
    }

    /**
     * The card's height is the keyboard's, which answers a grip drag a layout later. Whatever it
     * came back as, the bottom edge goes back where the drag found it.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        publishGripExclusionRect();
        if (!mPinBottom) return;
        int heightPx = bottom - top;
        if (heightPx == mPinnedHeightPx) {
            // Nothing left to settle, so the remembered fraction owns the card again.
            if (!mResizing) mPinBottom = false;
            return;
        }
        mPinnedHeightPx = heightPx;
        mTravelYPx = FloatingKeyboardGeometry.travelPx(mResizeStartContentHeightPx, heightPx);
        applyPositionPx(mPositionXPx, FloatingKeyboardGeometry.resizeYPx(mResizeStartYPx,
            mResizeStartHeightPx, heightPx, mResizeStartContentHeightPx));
    }

    /**
     * The grip is a corner of the keyboard, so the keys under it would take the touch first. A
     * down inside the zone is claimed here before it ever reaches them; everything else is the
     * keyboard's, untouched.
     */
    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
            && isInGripZone(event.getX(), event.getY()))
            return true;
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isInGripZone(event.getX(), event.getY())) break;
                beginResize(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mResizing) break;
                resizeTo(event, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mResizing) break;
                resizeTo(event, true);
                endResize();
                return true;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private void beginResize(@NonNull MotionEvent event) {
        mResizing = true;
        mDragStartRawX = event.getRawX();
        mDragStartRawY = event.getRawY();
        mDragStartXPx = mPositionXPx;
        mResizeStartWidthPx = getWidth();
        // Travel is what the frame is not, so the two together are the room it floats in.
        mResizeStartContentWidthPx = getWidth() + mTravelXPx;
        mResizeStartHeightPx = getHeight();
        mResizeStartYPx = mPositionYPx;
        mResizeStartContentHeightPx = getHeight() + mTravelYPx;
        mPinnedHeightPx = getHeight();
        mPinBottom = true;
        mResizeStartKeyboardHeightPx = Math.max(1, mContentHost.getHeight());
        mResizeStartWidthScale = mWidthScale == null ? 1f : mWidthScale.widthScale();
        mResizeStartHeightScale = mHeightScale == null ? 1f : mHeightScale.heightScale();
        mResizeWidthScale = mResizeStartWidthScale;
        mResizeHeightScale = mResizeStartHeightScale;
        invalidate();
    }

    private void resizeTo(@NonNull MotionEvent event, boolean committed) {
        int deltaXPx = Math.round(event.getRawX() - mDragStartRawX);
        int deltaYPx = Math.round(event.getRawY() - mDragStartRawY);
        float widthScale = FloatingKeyboardGeometry.widthScaleForResize(mResizeStartWidthScale,
            deltaXPx, mResizeStartContentWidthPx, minWidthPx(getContext()),
            TERMUX_APP.MIN_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
            TERMUX_APP.MAX_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE);
        float heightScale = FloatingKeyboardGeometry.heightScaleForResize(mResizeStartHeightScale,
            deltaYPx, mResizeStartKeyboardHeightPx,
            TERMUX_APP.MIN_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE,
            TERMUX_APP.MAX_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE);
        mResizeWidthScale = widthScale;
        mResizeHeightScale = heightScale;
        int widthPx = frameWidthPx(getContext(), mResizeStartContentWidthPx, widthScale);
        mTravelXPx = FloatingKeyboardGeometry.travelPx(mResizeStartContentWidthPx, widthPx);
        applyPositionPx(FloatingKeyboardGeometry.resizeXPx(mDragStartXPx, mResizeStartWidthPx,
            widthPx, mResizeStartContentWidthPx), mPositionYPx);
        // A layout per frame, which the rest of the launcher never does — but only for as long as
        // a finger is on the grip, and being the size it is dragged to is the whole gesture.
        requestLayout();
        if (mResizeListener != null)
            mResizeListener.onFrameResized(widthScale, heightScale, mPositionXPx, committed);
    }

    /**
     * Hands the card's width back to the stored share. The committed frame of the drag has already
     * written that share, so the size on screen does not move; what stops is the per-frame layout.
     */
    private void endResize() {
        mResizing = false;
        mResizeWidthScale = Float.NaN;
        mResizeHeightScale = Float.NaN;
        requestLayout();
        invalidate();
    }

    /**
     * The handle in the grip: two diagonals across the corner, at full strength so it reads as
     * something to take hold of rather than a smudge, in the colour the grab pill already uses —
     * and in the accent for as long as a finger is on it.
     */
    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        mGripPaint.setStyle(Paint.Style.STROKE);
        mGripPaint.setStrokeWidth(dp(GRIP_STROKE_DP));
        mGripPaint.setStrokeCap(Paint.Cap.ROUND);
        mGripPaint.setColor(gripColor());
        float left = dp(GRIP_INSET_DP);
        float bottom = getHeight() - dp(GRIP_INSET_DP);
        canvas.drawLine(left + dp(GRIP_LONG_DP), bottom, left, bottom - dp(GRIP_LONG_DP),
            mGripPaint);
        canvas.drawLine(left + dp(GRIP_SHORT_DP), bottom, left, bottom - dp(GRIP_SHORT_DP),
            mGripPaint);
    }

    /** {@link R.drawable#floating_keyboard_grab_handle}'s colour at rest, the accent under a
     *  finger. */
    private int gripColor() {
        if (mResizing)
            return MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(getContext(), R.color.termux_primary));
        return MaterialColors.getColor(getContext(),
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant));
    }

    private boolean onHandleTouch(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = true;
                mDragStartRawX = event.getRawX();
                mDragStartRawY = event.getRawY();
                mDragStartXPx = mPositionXPx;
                mDragStartYPx = mPositionYPx;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) return false;
                moveTo(event, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDragging) return false;
                mDragging = false;
                moveTo(event, true);
                return true;
            default:
                return false;
        }
    }

    private void moveTo(@NonNull MotionEvent event, boolean committed) {
        applyPositionPx(
            mDragStartXPx + Math.round(event.getRawX() - mDragStartRawX),
            mDragStartYPx + Math.round(event.getRawY() - mDragStartRawY));
        if (mListener != null)
            mListener.onFrameMoved(mPositionXPx, mPositionYPx, committed);
    }

    /**
     * Collapses to nothing while the keyboard it hosts is gone, so a closed keyboard never leaves
     * its handle floating over the place on its own. {@code INVISIBLE} still lays out — that is the
     * reveal gate staging the first frame, and the frame has to keep its size through it.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View hosted = mContentHost.getChildCount() > 0 ? mContentHost.getChildAt(0) : null;
        if (hosted == null || hosted.getVisibility() == View.GONE) {
            setMeasuredDimension(0, 0);
            return;
        }
        int widthPx = frameWidthPx(MeasureSpec.getSize(widthMeasureSpec));
        super.onMeasure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
            heightMeasureSpec);
    }

    /** The frame's width for one content width and the user's width share. */
    public static int frameWidthPx(@NonNull Context context, int contentWidthPx,
                                   float widthScale) {
        return FloatingKeyboardGeometry.frameWidthPx(contentWidthPx, widthScale,
            minWidthPx(context));
    }

    /** The narrowest a card can be dragged, whatever the share works out to. */
    static int minWidthPx(@NonNull Context context) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MIN_WIDTH_DP,
            context.getResources().getDisplayMetrics()));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            getResources().getDisplayMetrics());
    }

    /**
     * The layout params a frame is added to its host with: the whole content region to be measured
     * against, out of which {@link #onMeasure} keeps the user's share.
     */
    @NonNull
    public static FrameLayout.LayoutParams hostLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
    }
}
