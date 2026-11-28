package com.termux.app.tiling.desktop;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
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
import com.termux.app.tiling.DwindleTree;
import com.termux.app.tiling.FloatGeometry;
import com.termux.app.tiling.ScrollingLayoutPolicy;
import com.termux.app.tiling.TilingInput;
import com.termux.app.tiling.TilingLivePolicy;
import com.termux.app.tiling.WallpaperTap;
import com.termux.app.wall.BorderDrag;
import com.termux.app.wall.BorderGrabber;
import com.termux.app.wall.BorderHold;
import com.termux.app.wall.BorderHoldGlyph;

import java.util.ArrayList;
import java.util.Collections;
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
 * <p>Floats (#421) stand above the tiles: each page has a Float layer over its strip, and the
 * pinned Floats share one layer over every page, which stays put while the Workspaces slide under
 * it and leaves with them for Home. A Home page's Floats stand in Home's own float layer
 * ({@link #setHomeFloats}), so they ride with Home's pages as v1's floats do. A Float is moved by
 * a drag on its name strip and sized by a drag on its bottom-right corner; the drag moves only its
 * translation (a resize draws its outline), and the new box goes to the controller on lift.
 *
 * <p>The controller ({@link TilingDesktop}) decides what is shown; this view only applies it.
 */
@SuppressLint("ViewConstructor")
public final class TilingDesktopView extends FrameLayout {

    /** What the view tells its controller. */
    public interface Listener {
        /** A hold ran its time on {@code zone} (an edge or a gap); the zone's side is the way it steps. */
        void onEdgeHold(@NonNull TilingInput.Zone zone);

        /** Whether a hold on {@code zone} has a window or place to step to. */
        boolean canStep(@NonNull TilingInput.Zone zone);

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

        /** A tap on window {@code windowId}'s tab in a tabbed tile's strip: that window shows. */
        void onTabPressed(long windowId);

        /** A drag on Float {@code windowId}'s name strip, edge or corner ended: it stands at {@code box} now. */
        void onFloatBoxChanged(long windowId, @NonNull FloatGeometry.Box box);

        /** A move between places came to rest. */
        void onMoveSettled();
    }

    /** One Float as the controller shows it. */
    static final class FloatSpec {
        final long windowId;
        @NonNull final String name;
        /** Where it stands in its layer's room, already clamped into it. */
        @NonNull final FloatGeometry.Box box;
        final boolean focused;
        final boolean pinned;

        FloatSpec(long windowId, @NonNull String name, @NonNull FloatGeometry.Box box, boolean focused,
                  boolean pinned) {
            this.windowId = windowId;
            this.name = name;
            this.box = box;
            this.focused = focused;
            this.pinned = pinned;
        }
    }

    /** The air around a Workspace's windows, which is also the border hold's band, in dp. */
    public static final float EDGE_DP = 12f;
    /** The gap between two columns, in dp. */
    public static final float GAP_DP = 8f;
    /** A window frame's corner radius, in dp. */
    public static final float CORNER_DP = 14f;
    /** A tabbed Dwindle tile's tab strip, in dp. */
    public static final float TAB_STRIP_DP = 26f;
    /** A Float's name strip along its top, which moves it, in dp. */
    public static final float NAME_STRIP_DP = 22f;
    /** The square at a Float's bottom-right corner that sizes it, in dp. */
    static final float FLOAT_CORNER_DP = 32f;
    /** The band along a Float's sides and bottom that moves it, as its name strip does, in dp. */
    static final float FLOAT_EDGE_DP = 12f;
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
    private boolean mHasHome;
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
    /** Called on a tap on empty wallpaper while edit mode listens; null at rest. */
    @Nullable private Runnable mWallpaperTap;
    @NonNull private final WallpaperTap mTap;
    /** The pinned Floats' layer: over every page, moving only as the Workspaces leave for Home. */
    @NonNull private final FrameLayout mPinnedHost;
    @NonNull private final FloatLayer mPinned;
    /** Home's own float layer's Floats, or null before the controller hands one over. */
    @Nullable private FloatLayer mHomeFloats;

    public TilingDesktopView(@NonNull Context context, @NonNull Listener listener, boolean hasHome) {
        super(context);
        mTap = new WallpaperTap(ViewConfiguration.get(context).getScaledTouchSlop(),
            ViewConfiguration.getTapTimeout());
        mListener = listener;
        mHasHome = hasHome;
        mDensity = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
        setClipChildren(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mPosition = hasHome ? 0f : 1f;
        mPinnedHost = new FrameLayout(context);
        mPinnedHost.setClipChildren(false);
        addView(mPinnedHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mPinned = new FloatLayer(mPinnedHost);
        placePinned();
        applyVisibility();
    }

    /** Home came or went (Use Terl as): without Home the view stands over the wallpaper at rest. */
    void setHasHome(boolean hasHome) {
        if (mHasHome == hasHome) return;
        mHasHome = hasHome;
        placePinned();
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

    /** The room's width and height now, in px: what a full-screen window fills. */
    int roomWidth() {
        return mRoomPx.width();
    }

    int roomHeight() {
        return mRoomPx.height();
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
            addView(page, indexOfChild(mPinnedHost), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
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
            FloatFrame floating = page.mFloats.mFrames.get(windowId);
            if (floating != null) return floating.mContent;
        }
        FloatFrame floating = mPinned.mFrames.get(windowId);
        if (floating == null && mHomeFloats != null) floating = mHomeFloats.mFrames.get(windowId);
        return floating == null ? null : floating.mContent;
    }

    /** The Float frame of window {@code windowId} in any layer, or null. */
    @Nullable
    FloatFrame floatFrameOf(long windowId) {
        WindowFrame frame = frameOf(windowId);
        return frame != null && frame.getParent() instanceof FloatFrame ? (FloatFrame) frame.getParent() : null;
    }

    /** Shows the pinned Floats ({@code floats}, in {@code room}) over every page. */
    void setPinnedFloats(@NonNull List<FloatSpec> floats, @NonNull FloatGeometry.Room room) {
        mPinned.set(floats, room);
    }

    /** The pinned Floats' layer, which every page slides under. */
    @NonNull
    ViewGroup pinnedLayer() {
        return mPinnedHost;
    }

    /**
     * Shows a Home page's Floats ({@code floats}, in {@code room} in its coordinates) in Home's own
     * float layer {@code layer}, as frames of their own beside v1's floats; none when
     * {@code layer} is null.
     */
    void setHomeFloats(@Nullable ViewGroup layer, @NonNull List<FloatSpec> floats,
                       @NonNull FloatGeometry.Room room) {
        if (mHomeFloats != null && mHomeFloats.mHost != layer) {
            mHomeFloats.set(Collections.<FloatSpec>emptyList(), room);
            mHomeFloats = null;
        }
        if (layer == null) return;
        if (mHomeFloats == null) mHomeFloats = new FloatLayer(layer);
        mHomeFloats.set(floats, room);
    }

    /** Whether a move between places is running. */
    boolean isMoving() {
        return mMotion != null;
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
                boolean settled = mMotion == animation;
                if (settled) mMotion = null;
                setPosition(level);
                if (settled) mListener.onMoveSettled();
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
        placePinned();
        applyVisibility();
    }

    /** The pinned layer stands still over the Workspaces and slides away with Workspace 1 toward Home. */
    private void placePinned() {
        float y = mHasHome ? Math.max(0f, TilingLivePolicy.offsetPx(1, mPosition, getHeight())) : 0f;
        if (mPinnedHost.getTranslationY() != y) mPinnedHost.setTranslationY(y);
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
        placePinned();
        // The pages are laid out again from the controller, after this pass.
        post(mListener::onRoomChanged);
    }

    // ---- touch: the border hold ------------------------------------------------------------------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return mTracking;
        if (mMotion != null) return true;
        // A Float above the tiles takes its own touches: its content, its strip and its corner.
        if (floatAt(ev.getX(), ev.getY())) return false;
        TilingInput.Zone zone = zoneAt(ev.getX(), ev.getY());
        if (zone.kind == TilingInput.Zone.Kind.CONTENT) return false;
        beginHold(zone, ev.getX(), ev.getY());
        return true;
    }

    private float gapCentreX() {
        Page front = mPages.get(Math.round(mPosition));
        if (front == null || front.mLayout == null) return mZone.centreX;
        return TilingInput.gapCentreX(mZone, front.mLayout, mRoomPx.left, -front.mStrip.getTranslationX());
    }

    private float gapCentreY() {
        Page front = mPages.get(Math.round(mPosition));
        if (front == null || front.mLayout == null) return mZone.centreY;
        return TilingInput.gapCentreY(mZone, front.mLayout, mRoomPx.top);
    }

    /** Whether a Float of the place in front, or a pinned one, stands at ({@code x}, {@code y}). */
    private boolean floatAt(float x, float y) {
        if (mPinned.holds(x - mPinnedHost.getTranslationX(), y - mPinnedHost.getTranslationY())) return true;
        Page front = mPages.get(Math.round(mPosition));
        return front != null && front.mFloats.holds(x - front.getTranslationX(), y - front.getTranslationY());
    }

    /** Ends a running gap drag without a word to the listener: the controller has already committed it. */
    void endGapDrag() {
        if (!mGapResizing) return;
        mGapResizing = false;
        mZone = TilingInput.zoneNone();
    }

    /** The zone at ({@code x}, {@code y}) on the Workspace in front: its air, its gaps, or its windows. */
    @NonNull
    private TilingInput.Zone zoneAt(float x, float y) {
        Page front = mPages.get(Math.round(mPosition));
        if (front != null && front.mTiles != null) {
            return TilingInput.zoneAtTiles(x, y, getWidth(), mInsetTop, getHeight() - mInsetBottom,
                mRoomPx.left, mRoomPx.top, mRoomPx.right, mRoomPx.bottom, front.mTiles);
        }
        ScrollingLayoutPolicy.Layout layout = front == null ? null : front.mLayout;
        if (layout == null) layout = ScrollingLayoutPolicy.layout(ScrollingLayoutPolicy.State.EMPTY,
            new ScrollingLayoutPolicy.Viewport(0f, 0f, 0f, false));
        float scroll = front == null ? 0f : -front.mStrip.getTranslationX();
        return TilingInput.zoneAt(x, y, getWidth(), mInsetTop, getHeight() - mInsetBottom,
            mRoomPx.left, mRoomPx.top, mRoomPx.right, mRoomPx.bottom, layout, scroll);
    }

    /**
     * Listens for a tap on the empty wallpaper: the air round the windows or the ground between
     * them, never a window or a gap. It only watches; every gesture of the desktop goes on as ever.
     */
    public void setWallpaperTap(@Nullable Runnable tap) {
        mWallpaperTap = tap;
        mTap.cancel();
    }

    private void watchForWallpaperTap(@NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mZone.kind == TilingInput.Zone.Kind.OUTER
                    || mZone.kind == TilingInput.Zone.Kind.CONTENT) {
                    mTap.down(ev.getX(), ev.getY(), ev.getEventTime());
                } else {
                    mTap.cancel();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                mTap.move(ev.getX(), ev.getY());
                break;
            case MotionEvent.ACTION_UP:
                if (mTap.up(ev.getX(), ev.getY(), ev.getEventTime())) {
                    Runnable tap = mWallpaperTap;
                    if (tap != null) post(tap);
                }
                break;
            default:
                mTap.cancel();
                break;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mWallpaperTap != null) watchForWallpaperTap(ev);
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
        if (!mHold.fires(now, side != BorderDrag.Border.NONE && mListener.canStep(mZone))) {
            invalidate();
            return;
        }
        mStepped = true;
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        mListener.onEdgeHold(mZone);
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
                // Read from the layout as it is now: a step slides the strip under the held finger.
                mGlyph.draw(canvas, side, gapCentreX() + BorderHold.steps(side) * inset, mHoldY,
                    strength, mDensity, color);
                side = BorderDrag.Border.NONE;
            } else if (mZone.kind == TilingInput.Zone.Kind.TILE_GAP) {
                // The chevron stands under the finger, pointing at the tile the hold goes to.
                if (side == BorderDrag.Border.LEFT || side == BorderDrag.Border.RIGHT) {
                    mGlyph.draw(canvas, side, mHoldX + BorderHold.steps(side) * inset, mHoldY,
                        strength, mDensity, color);
                } else {
                    boolean up = side == BorderDrag.Border.TOP;
                    canvas.translate(mHoldX, mHoldY + (up ? -inset : inset));
                    canvas.rotate(90f);
                    mGlyph.draw(canvas, up ? BorderDrag.Border.LEFT : BorderDrag.Border.RIGHT, 0f, 0f,
                        strength, mDensity, color);
                }
                side = BorderDrag.Border.NONE;
            } else if (mZone.kind == TilingInput.Zone.Kind.STACK_GAP) {
                boolean up = side == BorderDrag.Border.TOP;
                canvas.translate(mHoldX, gapCentreY() + (up ? -inset : inset));
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

    /** One Workspace: its viewport, the strip of window frames in it, or the empty Workspace's button; its Floats over them. */
    final class Page extends FrameLayout {
        @NonNull final Map<Long, WindowFrame> mFrames = new HashMap<>();
        /** The layout last set, in the viewport's coordinates; the hold's hit test reads it. */
        @Nullable ScrollingLayoutPolicy.Layout mLayout;
        /** The Dwindle tiles last set, or null for a Scrolling page; the hold's hit test reads them. */
        @Nullable DwindleTree.Tiles mTiles;
        /** Each tabbed tile's strip, by the first window of its tile. */
        @NonNull private final Map<Long, TilingTabStrip> mTabStrips = new HashMap<>();
        @NonNull private final FrameLayout mViewport;
        @NonNull private final FrameLayout mStrip;
        /** The page's own Floats, over its tiles and its New terminal. */
        @NonNull private final FrameLayout mFloatHost;
        @NonNull final FloatLayer mFloats;
        @Nullable private TextView mNewTerminal;

        Page(@NonNull Context context) {
            super(context);
            mViewport = new FrameLayout(context);
            mViewport.setClipChildren(true);
            mStrip = new FrameLayout(context);
            mStrip.setClipChildren(false);
            mViewport.addView(mStrip, new LayoutParams(0, 0));
            addView(mViewport, new LayoutParams(0, 0));
            mFloatHost = new FrameLayout(context);
            mFloatHost.setClipChildren(false);
            addView(mFloatHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            mFloats = new FloatLayer(mFloatHost);
        }

        /** Shows the page's own Floats ({@code floats}, in {@code room}), over its tiles. */
        void setFloats(@NonNull List<FloatSpec> floats, @NonNull FloatGeometry.Room room) {
            mFloats.set(floats, room);
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
            if (mTiles != null) {
                mTiles = null;
                setTabStrips(java.util.Collections.<DwindleTree.TabStrip>emptyList(),
                    java.util.Collections.<Long, TilingTabStrip.Tab>emptyMap());
            }
            return placeWindows(layout, scroll, focusedId, live, animate);
        }

        /**
         * Shows a Dwindle Workspace's {@code tiles}: each tile's shown window as
         * {@link #setWindows} would, and a tab strip over each tabbed tile, its tabs named by
         * {@code tabs}. Returns the frames dropped, as {@link #setWindows} does.
         */
        @NonNull
        List<WindowFrame> setTiles(@NonNull DwindleTree.Tiles tiles, long focusedId, @NonNull Set<Long> live,
                                   @NonNull Map<Long, TilingTabStrip.Tab> tabs) {
            mTiles = tiles;
            List<WindowFrame> dropped = placeWindows(tiles.layout, 0f, focusedId, live, false);
            setTabStrips(tiles.strips, tabs);
            return dropped;
        }

        private void setTabStrips(@NonNull List<DwindleTree.TabStrip> strips,
                                  @NonNull Map<Long, TilingTabStrip.Tab> tabs) {
            Map<Long, TilingTabStrip> keep = new HashMap<>();
            for (DwindleTree.TabStrip strip : strips) {
                long key = strip.windows[0];
                TilingTabStrip view = mTabStrips.remove(key);
                if (view == null) {
                    view = new TilingTabStrip(getContext(), mListener::onTabPressed,
                        BorderGrabber.accent(getContext()));
                    mStrip.addView(view, new LayoutParams(0, 0));
                }
                placeAt(view, strip.left, strip.top, strip.right, strip.bottom);
                List<TilingTabStrip.Tab> line = new ArrayList<>();
                for (long id : strip.windows) {
                    TilingTabStrip.Tab tab = tabs.get(id);
                    if (tab != null) line.add(tab);
                }
                view.setTabs(line, strip.shown, strip.right - strip.left);
                keep.put(key, view);
            }
            for (TilingTabStrip gone : mTabStrips.values()) mStrip.removeView(gone);
            mTabStrips.clear();
            mTabStrips.putAll(keep);
        }

        @NonNull
        private List<WindowFrame> placeWindows(@NonNull ScrollingLayoutPolicy.Layout layout, float scroll,
                                               long focusedId, @NonNull Set<Long> live, boolean animate) {
            mLayout = layout;
            Map<Long, WindowFrame> keep = new HashMap<>();
            for (ScrollingLayoutPolicy.Placement placement : layout.placements) {
                if (!live.contains(placement.windowId)) continue;
                WindowFrame frame = mFrames.remove(placement.windowId);
                if (frame == null) {
                    frame = new WindowFrame(getContext(), placement.windowId, false);
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
                addView(button, indexOfChild(mFloatHost), new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            }
            if (mNewTerminal.getVisibility() != VISIBLE) mNewTerminal.setVisibility(VISIBLE);
        }
    }

    /** Places {@code view} at a rectangle of its strip, rounded as a frame is; nothing is set when it is already there. */
    static void placeAt(@NonNull View view, float l, float t, float r, float b) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        int left = Math.round(l);
        int top = Math.round(t);
        int width = Math.max(0, Math.round(r) - left);
        int height = Math.max(0, Math.round(b) - top);
        if (params.leftMargin == left && params.topMargin == top && params.width == width
            && params.height == height) return;
        params.leftMargin = left;
        params.topMargin = top;
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    // ---- a window's frame ------------------------------------------------------------------------

    /**
     * One window's place on its strip, and the slot the pane controller draws its shell in. In a
     * Float it is the content under the name strip: its top corners square, its press the Float's.
     */
    final class WindowFrame extends FrameLayout implements HostedFloatSlot {
        final long mWindowId;
        private final boolean mInFloat;
        @NonNull private final GradientDrawable mRim = new GradientDrawable();
        private boolean mFocused;

        WindowFrame(@NonNull Context context, long windowId, boolean inFloat) {
            super(context);
            mWindowId = windowId;
            mInFloat = inFloat;
            float radius = CORNER_DP * mDensity;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    // Under a Float's name strip the top corners are the strip's: square here.
                    outline.setRoundRect(0, inFloat ? -Math.round(radius) : 0, view.getWidth(),
                        view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
            mRim.setCornerRadius(radius);
            mRim.setColor(Color.TRANSPARENT);
            setForeground(mRim);
        }

        void place(@NonNull ScrollingLayoutPolicy.Placement placement) {
            placeAt(this, placement.left, placement.top, placement.right, placement.bottom);
        }

        void setFocusedRim(boolean focused) {
            if (mFocused == focused) return;
            mFocused = focused;
            mRim.setStroke(focused ? Math.max(1, Math.round(RIM_DP * mDensity)) : 0,
                focused ? BorderGrabber.accent(getContext()) : Color.TRANSPARENT);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (!mInFloat && ev.getActionMasked() == MotionEvent.ACTION_DOWN) mListener.onWindowPressed(mWindowId);
            return false;
        }

        @NonNull @Override public FrameLayout paneFrame() {
            return this;
        }

        /** In a Float the whole Float rises over the keyboard, its name strip with it. */
        @NonNull @Override public View liftTarget() {
            return mInFloat && getParent() instanceof FloatFrame ? (View) getParent() : this;
        }

        @Override public float cornerRadiusPx() {
            return CORNER_DP * mDensity;
        }

        @Override public int groundAlpha() {
            return 255;
        }
    }

    // ---- Floats (#421) ---------------------------------------------------------------------------

    /**
     * The Floats of one layer (a page's, the pinned one or Home's): a frame per Float, kept while
     * its window stays in the layer, so what it shows stays in it. Their order is the order they
     * came in; nothing is raised.
     */
    final class FloatLayer {
        @NonNull final ViewGroup mHost;
        @NonNull final Map<Long, FloatFrame> mFrames = new HashMap<>();

        FloatLayer(@NonNull ViewGroup host) {
            mHost = host;
        }

        void set(@NonNull List<FloatSpec> floats, @NonNull FloatGeometry.Room room) {
            Map<Long, FloatFrame> keep = new HashMap<>();
            for (FloatSpec spec : floats) {
                FloatFrame frame = mFrames.remove(spec.windowId);
                if (frame == null) {
                    frame = new FloatFrame(getContext(), spec.windowId);
                    mHost.addView(frame, new FrameLayout.LayoutParams(0, 0));
                }
                frame.show(spec, room);
                keep.put(spec.windowId, frame);
            }
            for (FloatFrame gone : mFrames.values()) {
                gone.endDrag();
                mHost.removeView(gone);
            }
            mFrames.clear();
            mFrames.putAll(keep);
        }

        /** Whether a Float of this layer stands at ({@code x}, {@code y}) in the layer's coordinates. */
        boolean holds(float x, float y) {
            if (mHost.getVisibility() != VISIBLE) return false;
            for (FloatFrame frame : mFrames.values()) {
                if (frame.getVisibility() != VISIBLE) continue;
                float left = frame.getLeft() + frame.getTranslationX();
                float top = frame.getTop() + frame.getTranslationY();
                if (x >= left && x < left + frame.getWidth() && y >= top && y < top + frame.getHeight()) return true;
            }
            return false;
        }
    }

    /**
     * One Float: its name strip along the top and a thin band along its other edges (a drag there
     * moves it), its window's frame under the strip, and a corner that sizes it. It stands at its
     * box by its margins; a move only translates it while the finger is down and a resize draws
     * the new outline on the layer, so either lays out once, on lift. Its own translation up is
     * the keyboard's lift: the whole Float rises, name strip and all ({@link WindowFrame#liftTarget}).
     */
    final class FloatFrame extends FrameLayout {
        private static final int DRAG_NONE = 0;
        private static final int DRAG_MOVE = 1;
        private static final int DRAG_RESIZE = 2;

        final long mWindowId;
        @NonNull final WindowFrame mContent;
        @NonNull private final TextView mName;
        @NonNull private final GradientDrawable mRim = new GradientDrawable();
        @NonNull private final GradientDrawable mGhost = new GradientDrawable();
        @NonNull private final Paint mGrip = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Nullable private FloatGeometry.Box mBox;
        @Nullable private FloatGeometry.Room mRoom;
        @Nullable private FloatGeometry.Box mDragged;
        private boolean mFocused;
        private boolean mPinned;
        private int mDrag = DRAG_NONE;
        private boolean mDragging;
        private float mDownRawX;
        private float mDownRawY;
        /** The lift the Float had as the drag began, kept under the drag's own offset. */
        private float mDragBaseY;
        /** A committed move's offset waits for the new margins' layout to go, so nothing jumps. */
        private boolean mSettleTranslation;

        FloatFrame(@NonNull Context context, long windowId) {
            super(context);
            mWindowId = windowId;
            float radius = CORNER_DP * mDensity;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
            GradientDrawable ground = new GradientDrawable();
            ground.setColor(mGroundColor);
            setBackground(ground);
            mRim.setCornerRadius(radius);
            mRim.setColor(Color.TRANSPARENT);
            setForeground(mRim);
            int strip = Math.round(NAME_STRIP_DP * mDensity);
            mName = new TextView(context);
            mName.setSingleLine(true);
            mName.setEllipsize(TextUtils.TruncateAt.END);
            mName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            mName.setTextColor(0xE6FFFFFF);
            mName.setGravity(Gravity.CENTER_VERTICAL);
            mName.setIncludeFontPadding(false);
            int pad = Math.round(10f * mDensity);
            mName.setPadding(pad, 0, pad, 0);
            mName.setCompoundDrawablePadding(Math.round(4f * mDensity));
            mName.setBackgroundColor(0x33FFFFFF);
            mName.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(mName, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, strip, Gravity.TOP));
            mContent = new WindowFrame(context, windowId, true);
            LayoutParams content = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
            content.topMargin = strip;
            addView(mContent, content);
            mGrip.setStyle(Paint.Style.STROKE);
            mGrip.setStrokeWidth(Math.max(1f, 1.5f * mDensity));
            mGrip.setStrokeCap(Paint.Cap.ROUND);
            mGrip.setColor(0x99FFFFFF);
            mGhost.setCornerRadius(radius);
            mGhost.setColor(Color.TRANSPARENT);
            mGhost.setStroke(Math.max(1, Math.round(RIM_DP * mDensity)), BorderGrabber.accent(context));
            setWillNotDraw(false);
        }

        /** Stands at {@code spec}'s box in {@code room}; nothing is set that is already so. */
        void show(@NonNull FloatSpec spec, @NonNull FloatGeometry.Room room) {
            if (!spec.name.contentEquals(mName.getText())) mName.setText(spec.name);
            if (getContentDescription() == null || !spec.name.contentEquals(getContentDescription()))
                setContentDescription(spec.name);
            if (mPinned != spec.pinned) {
                mPinned = spec.pinned;
                mName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    spec.pinned ? R.drawable.ic_tiling_float_pinned_placeholder : 0, 0, 0, 0);
            }
            if (mFocused != spec.focused) {
                mFocused = spec.focused;
                mRim.setStroke(spec.focused ? Math.max(1, Math.round(RIM_DP * mDensity)) : 0,
                    spec.focused ? BorderGrabber.accent(getContext()) : Color.TRANSPARENT);
            }
            mRoom = room;
            // A drag in hand keeps the finger's box until it lifts.
            if (mDragging) return;
            mBox = spec.box;
            applyBox(spec.box);
        }

        /** Stands at {@code box}: its margins and size, laid out only when they changed. */
        private void applyBox(@NonNull FloatGeometry.Box box) {
            FloatGeometry.Room room = mRoom;
            if (room == null || !(getLayoutParams() instanceof MarginLayoutParams)) return;
            MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
            int left = Math.round((room.left + box.left) * mDensity);
            int top = Math.round((room.top + box.top) * mDensity);
            int width = Math.max(1, Math.round(box.width * mDensity));
            int height = Math.max(1, Math.round(box.height * mDensity));
            if (params.leftMargin == left && params.topMargin == top && params.width == width
                && params.height == height) return;
            params.leftMargin = left;
            params.topMargin = top;
            params.width = width;
            params.height = height;
            setLayoutParams(params);
        }

        /** The drag's offset from where the Float stands, over the lift it had. */
        private void offsetBy(float dx, float dy) {
            float y = mDragBaseY + dy;
            if (getTranslationX() != dx) setTranslationX(dx);
            if (getTranslationY() != y) setTranslationY(y);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            // The new margins stand now: the move's offset goes in the same frame.
            if (!mSettleTranslation) return;
            mSettleTranslation = false;
            offsetBy(0f, 0f);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return mDrag != DRAG_NONE;
            mListener.onWindowPressed(mWindowId);
            mDrag = dragAt(ev.getX(), ev.getY());
            if (mDrag == DRAG_NONE) return false;
            mDownRawX = ev.getRawX();
            mDownRawY = ev.getRawY();
            mDragBaseY = getTranslationY();
            mSettleTranslation = false;
            mDragging = false;
            // Nothing beneath (a page's swipe, the border hold) takes the drag from the Float.
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        /** What a press at ({@code x}, {@code y}) drags: the corner sizes, the name strip and the edges move. */
        private int dragAt(float x, float y) {
            float corner = FLOAT_CORNER_DP * mDensity;
            float edge = FLOAT_EDGE_DP * mDensity;
            if (x >= getWidth() - corner && y >= getHeight() - corner) return DRAG_RESIZE;
            if (y < NAME_STRIP_DP * mDensity) return DRAG_MOVE;
            if (x < edge || x >= getWidth() - edge || y >= getHeight() - edge) return DRAG_MOVE;
            return DRAG_NONE;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            // A press on the body that no content took is still the Float's: nothing under it sees it.
            if (mDrag == DRAG_NONE) return true;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    drag(ev.getRawX() - mDownRawX, ev.getRawY() - mDownRawY);
                    return true;
                case MotionEvent.ACTION_UP: {
                    FloatGeometry.Box box = mDragging ? mDragged : null;
                    endDrag();
                    if (box != null && !box.equals(mBox)) {
                        mBox = box;
                        // The offset stays until the new margins are laid out (onLayout).
                        mSettleTranslation = true;
                        applyBox(box);
                        mListener.onFloatBoxChanged(mWindowId, box);
                    } else {
                        offsetBy(0f, 0f);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    endDrag();
                    offsetBy(0f, 0f);
                    return true;
                default:
                    return true;
            }
        }

        private void drag(float dxPx, float dyPx) {
            FloatGeometry.Box box = mBox;
            FloatGeometry.Room room = mRoom;
            if (box == null || room == null) return;
            if (!mDragging) {
                if (dxPx * dxPx + dyPx * dyPx <= (float) mTouchSlop * mTouchSlop) return;
                mDragging = true;
                if (mDrag == DRAG_RESIZE) ((ViewGroup) getParent()).getOverlay().add(mGhost);
            }
            if (mDrag == DRAG_MOVE) {
                mDragged = FloatGeometry.moved(box, dxPx / mDensity, dyPx / mDensity, room);
                offsetBy((mDragged.left - box.left) * mDensity, (mDragged.top - box.top) * mDensity);
            } else {
                mDragged = FloatGeometry.resized(box, dxPx / mDensity, dyPx / mDensity, room);
                // The outline shows the size the lift will give; nothing is laid out meanwhile.
                mGhost.setBounds(Math.round((room.left + mDragged.left) * mDensity),
                    Math.round((room.top + mDragged.top) * mDensity),
                    Math.round((room.left + mDragged.right()) * mDensity),
                    Math.round((room.top + mDragged.bottom()) * mDensity));
            }
        }

        /** Ends a drag in hand: the outline goes, and the frame is free to be placed again. */
        void endDrag() {
            if (mDragging && mDrag == DRAG_RESIZE && getParent() instanceof ViewGroup)
                ((ViewGroup) getParent()).getOverlay().remove(mGhost);
            mDrag = DRAG_NONE;
            mDragging = false;
            mDragged = null;
        }

        /** The corner that sizes it: two short strokes, drawn over the content. */
        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float inset = 5f * mDensity;
            float a = 8f * mDensity;
            float b = 4f * mDensity;
            canvas.drawLine(w - inset - a, h - inset, w - inset, h - inset - a, mGrip);
            canvas.drawLine(w - inset - b, h - inset, w - inset, h - inset - b, mGrip);
        }
    }
}
