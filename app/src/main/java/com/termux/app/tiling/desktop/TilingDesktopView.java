package com.termux.app.tiling.desktop;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.haptics.Haptics;
import com.termux.app.terminal.HostedFloatSlot;
import com.termux.app.tiling.ScrollingLayoutPolicy;
import com.termux.app.tiling.TilingInput;
import com.termux.app.tiling.TilingLivePolicy;
import com.termux.app.wall.BorderDrag;
import com.termux.app.wall.BorderGrabber;
import com.termux.app.wall.BorderHold;
import com.termux.app.wall.BorderHoldGlyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tiling desktop's Workspaces on screen (#418): one page per live Workspace, stacked below
 * Home, which is v1's Home underneath this view. Each page holds its windows' frames on a strip
 * that scrolls sideways; a frame is a {@link HostedFloatSlot} the pane controller draws a shell in.
 *
 * <p>Motion is derived from one number, the position ({@link TilingLivePolicy#offsetPx}): moving
 * between places animates it and only sets each page's translation, so no frame lays anything out.
 * At rest on Home the view is invisible and takes no touches; while any Workspace shows, it takes
 * every touch that lands on it (windows get theirs; the air around them and the gaps between
 * tiles are the border hold's, {@link TilingInput#zoneAt}). A still finger there steps one window
 * or place that way after {@link BorderHold#HOLD_MS}, then again every {@link BorderHold#REPEAT_MS};
 * a finger that drags on a gap between two columns resizes it at once, a double tap on it snaps it.
 *
 * <p>The controller ({@link TilingDesktop}) decides what is shown; this view only applies it.
 */
@SuppressLint("ViewConstructor")
public final class TilingDesktopView extends FrameLayout {

    /** What the view tells its controller. */
    public interface Listener {
        /** A border hold ran its time on {@code side}. */
        void onEdgeHold(@NonNull BorderDrag.Border side);

        /** Whether a hold on {@code side} has a window or place to step to. */
        boolean canStep(@NonNull BorderDrag.Border side);

        /** A finger landed on window {@code windowId}'s frame; the frame's content gets the touch too. */
        void onWindowPressed(long windowId);

        /** The empty Workspace's New terminal was tapped. */
        void onNewTerminalPressed();

        /** The room the Workspaces have changed size; posted, never from inside a layout pass. */
        void onRoomChanged();

        /** A finger on the gap right of column {@code leftColumn} began to drag it. */
        void onGapDragBegin(int leftColumn);

        /** The drag has moved the gap {@code deltaPx} from where the finger went down. */
        void onGapDrag(int leftColumn, float deltaPx);

        /** The gap drag ended (finger up or cancelled). */
        void onGapDragEnd();

        /** A double tap on the gap right of column {@code leftColumn}: it snaps to the nearest preset. */
        void onGapSnap(int leftColumn);
    }

    /** The air around a Workspace's windows, which is also the border hold's band, in dp. */
    public static final float EDGE_DP = 12f;
    /** The gap between two columns, in dp. */
    public static final float GAP_DP = 8f;
    /** A window frame's corner radius, in dp. */
    public static final float CORNER_DP = 14f;
    /** The focused window's rim, in dp. */
    static final float RIM_DP = 2f;
    /** How long a move between places takes, in ms. */
    static final long MOVE_MS = 260L;

    @NonNull private final Listener mListener;
    @NonNull private final Map<Integer, Page> mPages = new HashMap<>();
    @NonNull private final BorderHold mHold = new BorderHold(true);
    @NonNull private final BorderHoldGlyph mGlyph = new BorderHoldGlyph();
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Runnable mHoldElapsed = this::onHoldElapsed;
    private final float mDensity;
    private final int mTouchSlop;
    private final boolean mHasHome;
    private float mPosition;
    @Nullable private ValueAnimator mMotion;
    /** Android's bars over the view's top and bottom: the edge bands stand inside them. */
    private int mInsetTop;
    private int mInsetBottom;
    /** Where the windows stand, in px as every page's viewport is laid out: the air around it is the hold's. */
    @NonNull private final Rect mRoomPx = new Rect();
    private float mHoldX;
    private float mHoldY;
    /** The zone the finger went down in; CONTENT when the view is not tracking a hold or a gap. */
    @NonNull private TilingInput.Zone mZone = TilingInput.zoneNone();
    private boolean mTracking;
    private float mDownX;
    private float mDownY;
    /** A gap drag is running; a hold that has stepped never turns into one. */
    private boolean mGapResizing;
    private boolean mStepped;
    private long mLastTapMs;
    private int mLastTapColumn = -1;
    private int mGroundColor = Color.BLACK;

    public TilingDesktopView(@NonNull Context context, @NonNull Listener listener, boolean hasHome) {
        super(context);
        mListener = listener;
        mHasHome = hasHome;
        mDensity = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
        setClipChildren(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mPosition = hasHome ? 0f : 1f;
        applyVisibility();
    }

    // ---- what the controller sets ----------------------------------------------------------------

    /** The colour of a Workspace's ground, behind its windows and in the air around them. */
    void setGroundColor(int color) {
        if (mGroundColor == color) return;
        mGroundColor = color;
        for (Page page : mPages.values()) page.setBackgroundColor(color);
    }

    /**
     * The room the windows stand in ({@code room}, in the view's coordinates) and how far
     * Android's bars reach over the view's top and bottom. The pages' viewports and the border
     * hold both read it, as {@link #roomBounds} rounds it.
     */
    void setRoom(@NonNull RectF room, int top, int bottom) {
        mInsetTop = Math.max(0, top);
        mInsetBottom = Math.max(0, bottom);
        roomBounds(room, mRoomPx);
    }

    /** {@code room} in whole px, as a page's viewport is laid out. */
    static void roomBounds(@NonNull RectF room, @NonNull Rect out) {
        int left = Math.round(room.left);
        int top = Math.round(room.top);
        out.set(left, top, left + Math.max(0, Math.round(room.width())),
            top + Math.max(0, Math.round(room.height())));
    }

    /** Whether a gap drag is running: the controller lays out without sliding the strip. */
    boolean isResizingGap() {
        return mGapResizing;
    }

    /** The page for the Workspace at {@code level}, made if it is not there. */
    @NonNull
    Page pageAt(int level) {
        Page page = mPages.get(level);
        if (page == null) {
            page = new Page(getContext());
            page.setBackgroundColor(mGroundColor);
            mPages.put(level, page);
            addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            placePage(level, page);
        }
        return page;
    }

    /** Drops every page whose level is not in {@code live}. */
    void keepPages(@NonNull Set<Integer> live) {
        for (Iterator<Map.Entry<Integer, Page>> it = mPages.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Page> entry = it.next();
            if (live.contains(entry.getKey())) continue;
            removeView(entry.getValue());
            it.remove();
        }
    }

    /** The frame of window {@code windowId} on any page, or null. */
    @Nullable
    WindowFrame frameOf(long windowId) {
        for (Page page : mPages.values()) {
            WindowFrame frame = page.mFrames.get(windowId);
            if (frame != null) return frame;
        }
        return null;
    }

    /** The position, a level: 0 Home, n Workspace n. */
    float position() {
        return mPosition;
    }

    /** Moves to {@code level}, sliding there when {@code animate}. */
    void moveTo(int level, boolean animate) {
        if (mMotion != null) mMotion.cancel();
        mMotion = null;
        if (!animate || getHeight() <= 0 || mPosition == level) {
            setPosition(level);
            return;
        }
        ValueAnimator motion = ValueAnimator.ofFloat(mPosition, level);
        motion.setDuration(MOVE_MS);
        motion.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        motion.addUpdateListener(a -> setPosition((float) a.getAnimatedValue()));
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(Animator animation) {
                if (mMotion == animation) mMotion = null;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (mMotion == animation) mMotion = null;
                setPosition(level);
            }
        });
        mMotion = motion;
        // The view shows before the first frame, so the page sliding in is drawn from its start.
        setPosition(mPosition);
        motion.start();
    }

    private void setPosition(float position) {
        mPosition = position;
        for (Map.Entry<Integer, Page> entry : mPages.entrySet()) placePage(entry.getKey(), entry.getValue());
        applyVisibility();
    }

    private void placePage(int level, @NonNull Page page) {
        float y = TilingLivePolicy.offsetPx(level, mPosition, getHeight());
        if (page.getTranslationY() != y) page.setTranslationY(y);
        int visibility = TilingLivePolicy.onScreen(level, mPosition) ? VISIBLE : INVISIBLE;
        if (page.getVisibility() != visibility) page.setVisibility(visibility);
    }

    /** At rest on Home nothing of it shows, so it neither draws nor takes Home's touches. */
    private void applyVisibility() {
        boolean shown = mMotion != null || mPosition > 0f || !mHasHome;
        int visibility = shown ? VISIBLE : INVISIBLE;
        if (getVisibility() != visibility) setVisibility(visibility);
    }

    // ---- size ------------------------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (Map.Entry<Integer, Page> entry : mPages.entrySet()) placePage(entry.getKey(), entry.getValue());
        // The pages are laid out again from the controller, after this pass.
        post(mListener::onRoomChanged);
    }

    // ---- touch: the border hold ------------------------------------------------------------------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return mTracking;
        if (mMotion != null) return true;
        TilingInput.Zone zone = zoneAt(ev.getX(), ev.getY());
        if (zone.kind == TilingInput.Zone.Kind.CONTENT) return false;
        beginHold(zone, ev.getX(), ev.getY());
        return true;
    }

    /** The zone at ({@code x}, {@code y}) on the Workspace in front: its air, its gaps, or its windows. */
    @NonNull
    private TilingInput.Zone zoneAt(float x, float y) {
        Page front = mPages.get(Math.round(mPosition));
        ScrollingLayoutPolicy.Layout layout = front == null ? null : front.mLayout;
        if (layout == null) layout = ScrollingLayoutPolicy.layout(ScrollingLayoutPolicy.State.EMPTY,
            new ScrollingLayoutPolicy.Viewport(0f, 0f, 0f, false));
        float scroll = front == null ? 0f : -front.mStrip.getTranslationX();
        return TilingInput.zoneAt(x, y, getWidth(), mInsetTop, getHeight() - mInsetBottom,
            mRoomPx.left, mRoomPx.top, mRoomPx.right, mRoomPx.bottom, layout, scroll);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mZone.kind == TilingInput.Zone.Kind.COLUMN_GAP && !mStepped) {
                    if (!mGapResizing) {
                        float dx = ev.getX() - mDownX;
                        float dy = ev.getY() - mDownY;
                        if (dx * dx + dy * dy > (float) mTouchSlop * mTouchSlop) {
                            endHold();
                            mGapResizing = true;
                            mListener.onGapDragBegin(mZone.column);
                        }
                    }
                    if (mGapResizing) {
                        mListener.onGapDrag(mZone.column, ev.getX() - mDownX);
                        break;
                    }
                }
                if (mHold.isFilling()) {
                    mHold.moved(ev.getX(), ev.getY(), mTouchSlop, false, SystemClock.uptimeMillis());
                    if (!mHold.isFilling()) endHold();
                }
                break;
            case MotionEvent.ACTION_UP:
                finishTouch(true);
                break;
            case MotionEvent.ACTION_CANCEL:
                finishTouch(false);
                break;
            default:
                break;
        }
        // Whatever reaches the view itself (the air, the ground between windows) is the desktop's:
        // nothing passes through to Home underneath.
        return true;
    }

    /** The finger is up: a gap drag ends, or a quick tap on a gap counts toward its double tap. */
    private void finishTouch(boolean up) {
        boolean tap = up && !mGapResizing && !mStepped && mZone.kind == TilingInput.Zone.Kind.COLUMN_GAP
            && mHold.side() != BorderDrag.Border.NONE;
        int column = mZone.column;
        boolean wasResizing = mGapResizing;
        endHold();
        mTracking = false;
        mGapResizing = false;
        mZone = TilingInput.zoneNone();
        if (wasResizing) {
            mListener.onGapDragEnd();
            return;
        }
        if (!tap) {
            mLastTapColumn = -1;
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (mLastTapColumn == column && now - mLastTapMs <= ViewConfiguration.getDoubleTapTimeout()) {
            mLastTapColumn = -1;
            mListener.onGapSnap(column);
        } else {
            mLastTapColumn = column;
            mLastTapMs = now;
        }
    }

    private void beginHold(@NonNull TilingInput.Zone zone, float x, float y) {
        mZone = zone;
        mTracking = true;
        mGapResizing = false;
        mStepped = false;
        mDownX = x;
        mDownY = y;
        mHoldX = x;
        mHoldY = y;
        mHold.begin(zone.side, x, y, SystemClock.uptimeMillis());
        mHandler.removeCallbacks(mHoldElapsed);
        mHandler.postDelayed(mHoldElapsed, mHold.beatMs());
        invalidate();
    }

    private void endHold() {
        mHandler.removeCallbacks(mHoldElapsed);
        if (mHold.side() == BorderDrag.Border.NONE) return;
        mHold.end();
        invalidate();
    }

    /** The beat is spent: one step, a tick, and the next beat if the finger stays. */
    private void onHoldElapsed() {
        BorderDrag.Border side = mHold.side();
        long now = SystemClock.uptimeMillis();
        if (!mHold.fires(now, side != BorderDrag.Border.NONE && mListener.canStep(side))) {
            invalidate();
            return;
        }
        mStepped = true;
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        mListener.onEdgeHold(side);
        // The hold goes on: the next step comes a beat later, if there is still somewhere to go.
        mHold.repeat(now);
        mHandler.postDelayed(mHoldElapsed, mHold.beatMs());
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        mHandler.removeCallbacks(mHoldElapsed);
        if (mMotion != null) mMotion.cancel();
        super.onDetachedFromWindow();
    }

    /** The hold's chevron at the held edge, pointing the way it steps, drawn only while it fills. */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!mHold.isFilling()) return;
        long now = SystemClock.uptimeMillis();
        float strength = mHold.glyph(now);
        if (strength > 0f) {
            BorderDrag.Border side = mHold.side();
            int color = BorderGrabber.accent(getContext());
            int save = canvas.save();
            float inset = BorderHoldGlyph.INSET_DP * mDensity;
            if (mZone.kind == TilingInput.Zone.Kind.COLUMN_GAP) {
                // The chevron's tip stands in the gap, pointing the way the hold steps.
                mGlyph.draw(canvas, side, mZone.centreX + BorderHold.steps(side) * inset, mHoldY,
                    strength, mDensity, color);
                side = BorderDrag.Border.NONE;
            } else if (mZone.kind == TilingInput.Zone.Kind.STACK_GAP) {
                boolean up = side == BorderDrag.Border.TOP;
                canvas.translate(mHoldX, mZone.centreY + (up ? -inset : inset));
                canvas.rotate(90f);
                mGlyph.draw(canvas, up ? BorderDrag.Border.LEFT : BorderDrag.Border.RIGHT, 0f, 0f,
                    strength, mDensity, color);
                side = BorderDrag.Border.NONE;
            }
            switch (side) {
                case LEFT:
                    mGlyph.draw(canvas, side, 0f, mHoldY, strength, mDensity, color);
                    break;
                case RIGHT:
                    mGlyph.draw(canvas, side, getWidth(), mHoldY, strength, mDensity, color);
                    break;
                case TOP:
                    // A left chevron turned a quarter clockwise points up.
                    canvas.translate(mHoldX, mInsetTop);
                    canvas.rotate(90f);
                    mGlyph.draw(canvas, BorderDrag.Border.LEFT, 0f, 0f, strength, mDensity, color);
                    break;
                case BOTTOM:
                    canvas.translate(mHoldX, getHeight() - mInsetBottom);
                    canvas.rotate(90f);
                    mGlyph.draw(canvas, BorderDrag.Border.RIGHT, 0f, 0f, strength, mDensity, color);
                    break;
                default:
                    break;
            }
            canvas.restoreToCount(save);
        }
        if (mHold.fill(now) < 1f) postInvalidateOnAnimation();
    }

    // ---- a Workspace's page ----------------------------------------------------------------------

    /** One Workspace: its viewport, the strip of window frames in it, or the empty Workspace's button. */
    final class Page extends FrameLayout {
        @NonNull final Map<Long, WindowFrame> mFrames = new HashMap<>();
        /** The layout last set, in the viewport's coordinates; the hold's hit test reads it. */
        @Nullable ScrollingLayoutPolicy.Layout mLayout;
        @NonNull private final FrameLayout mViewport;
        @NonNull private final FrameLayout mStrip;
        @Nullable private TextView mNewTerminal;

        Page(@NonNull Context context) {
            super(context);
            mViewport = new FrameLayout(context);
            mViewport.setClipChildren(true);
            mStrip = new FrameLayout(context);
            mStrip.setClipChildren(false);
            mViewport.addView(mStrip, new LayoutParams(0, 0));
            addView(mViewport, new LayoutParams(0, 0));
        }

        /** Where the windows stand: the room inside the page's air ({@link #setRoom}), in the page's coordinates. */
        void setViewport() {
            LayoutParams params = (LayoutParams) mViewport.getLayoutParams();
            int left = mRoomPx.left;
            int top = mRoomPx.top;
            int width = mRoomPx.width();
            int height = mRoomPx.height();
            if (params.leftMargin == left && params.topMargin == top && params.width == width
                && params.height == height) return;
            params.leftMargin = left;
            params.topMargin = top;
            params.width = width;
            params.height = height;
            mViewport.setLayoutParams(params);
        }

        /**
         * Shows {@code layout}'s windows at {@code scroll}, the focused one with its rim: a frame
         * per window, kept while its window stays, so a shell already drawn in one stays there.
         * Frames of windows no longer here are dropped and returned, for the controller to let
         * their shells go.
         */
        @NonNull
        List<WindowFrame> setWindows(@NonNull ScrollingLayoutPolicy.Layout layout, float scroll,
                                     long focusedId, @NonNull Set<Long> live, boolean animate) {
            mLayout = layout;
            Map<Long, WindowFrame> keep = new HashMap<>();
            for (ScrollingLayoutPolicy.Placement placement : layout.placements) {
                if (!live.contains(placement.windowId)) continue;
                WindowFrame frame = mFrames.remove(placement.windowId);
                if (frame == null) {
                    frame = new WindowFrame(getContext(), placement.windowId);
                    mStrip.addView(frame, new LayoutParams(0, 0));
                }
                frame.place(placement);
                frame.setFocusedRim(placement.windowId == focusedId);
                keep.put(placement.windowId, frame);
            }
            List<WindowFrame> dropped = new ArrayList<>(mFrames.values());
            for (WindowFrame frame : dropped) mStrip.removeView(frame);
            mFrames.clear();
            mFrames.putAll(keep);
            LayoutParams strip = (LayoutParams) mStrip.getLayoutParams();
            int stripWidth = (int) Math.ceil(layout.stripWidth);
            int stripHeight = ViewGroup.LayoutParams.MATCH_PARENT;
            if (strip.width != stripWidth || strip.height != stripHeight) {
                strip.width = stripWidth;
                strip.height = stripHeight;
                mStrip.setLayoutParams(strip);
            }
            // A focus that scrolls slides the strip there: only its translation moves.
            mStrip.animate().cancel();
            if (mStrip.getTranslationX() != -scroll) {
                if (animate && isShown()) mStrip.animate().translationX(-scroll).setDuration(MOVE_MS).start();
                else mStrip.setTranslationX(-scroll);
            }
            showNewTerminal(layout.placements.isEmpty());
            return dropped;
        }

        private void showNewTerminal(boolean show) {
            if (!show) {
                if (mNewTerminal != null && mNewTerminal.getVisibility() != GONE) mNewTerminal.setVisibility(GONE);
                return;
            }
            if (mNewTerminal == null) {
                TextView button = new TextView(getContext());
                button.setText(R.string.tiling_new_terminal);
                button.setTextColor(Color.WHITE);
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
                int pad = Math.round(16f * mDensity);
                button.setPadding(pad, pad, pad, pad);
                button.setGravity(Gravity.CENTER);
                GradientDrawable ground = new GradientDrawable();
                ground.setCornerRadius(CORNER_DP * mDensity);
                ground.setColor(0x33FFFFFF);
                button.setBackground(ground);
                button.setOnClickListener(v -> mListener.onNewTerminalPressed());
                mNewTerminal = button;
                addView(button, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            }
            if (mNewTerminal.getVisibility() != VISIBLE) mNewTerminal.setVisibility(VISIBLE);
        }
    }

    // ---- a window's frame ------------------------------------------------------------------------

    /** One window's place on its strip, and the slot the pane controller draws its shell in. */
    final class WindowFrame extends FrameLayout implements HostedFloatSlot {
        final long mWindowId;
        @NonNull private final GradientDrawable mRim = new GradientDrawable();
        private boolean mFocused;

        WindowFrame(@NonNull Context context, long windowId) {
            super(context);
            mWindowId = windowId;
            float radius = CORNER_DP * mDensity;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
            mRim.setCornerRadius(radius);
            mRim.setColor(Color.TRANSPARENT);
            setForeground(mRim);
        }

        void place(@NonNull ScrollingLayoutPolicy.Placement placement) {
            LayoutParams params = (LayoutParams) getLayoutParams();
            int left = Math.round(placement.left);
            int top = Math.round(placement.top);
            int width = Math.max(0, Math.round(placement.right) - left);
            int height = Math.max(0, Math.round(placement.bottom) - top);
            if (params.leftMargin == left && params.topMargin == top && params.width == width
                && params.height == height) return;
            params.leftMargin = left;
            params.topMargin = top;
            params.width = width;
            params.height = height;
            setLayoutParams(params);
        }

        void setFocusedRim(boolean focused) {
            if (mFocused == focused) return;
            mFocused = focused;
            mRim.setStroke(focused ? Math.max(1, Math.round(RIM_DP * mDensity)) : 0,
                focused ? BorderGrabber.accent(getContext()) : Color.TRANSPARENT);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) mListener.onWindowPressed(mWindowId);
            return false;
        }

        @NonNull @Override public FrameLayout paneFrame() {
            return this;
        }

        @NonNull @Override public View liftTarget() {
            return this;
        }

        @Override public float cornerRadiusPx() {
            return CORNER_DP * mDensity;
        }

        @Override public int groundAlpha() {
            return 255;
        }
    }
}
