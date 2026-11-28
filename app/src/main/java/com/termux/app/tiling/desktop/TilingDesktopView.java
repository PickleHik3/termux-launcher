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
import android.graphics.drawable.Drawable;
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
import com.termux.app.tiling.navigation.NavigationDrop;
import com.termux.app.tiling.navigation.NavigationEdgePan;
import com.termux.app.tiling.navigation.NavigationGesture;
import com.termux.app.tiling.navigation.NavigationSettle;
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
 * <p>Navigation mode (#433) takes every touch on the view: one finger slides the whole stack, the
 * places by the position and the page nearest the view sideways by its strip's translation, so
 * nothing is laid out while it slides; a long press on a window carries it ({@link NavigationGesture},
 * {@link NavigationDrop}, {@link NavigationEdgePan}, {@link NavigationSettle}).
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

        /** Navigation mode (#433): the view slid (a pan or a carried window at an edge); the live set may follow. */
        void onNavigationMoved();

        /** Navigation mode: a slide was let go; the view settles on {@code target}. */
        void onNavigationSettle(@NonNull NavigationSettle.Target target);

        /** Navigation mode: a tap on window {@code windowId} of the place at {@code level}: it is focused and the mode ends. */
        void onNavigationTap(int level, long windowId);

        /** Navigation mode: carried window {@code windowId} was let go on the place at {@code level} as {@code drop} says. */
        void onNavigationDrop(long windowId, int level, @NonNull NavigationDrop.Drop drop);
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

    /** The name strip along a tiled window's top edge (#422), in dp: drawn over the content, answering no touch. */
    public static final float TILE_NAME_STRIP_DP = 16f;
    /** A Float's name strip along its top, which moves it, in dp. */
    public static final float NAME_STRIP_DP = 22f;
    /** The square at a Float's bottom-right corner that sizes it, in dp. */
    static final float FLOAT_CORNER_DP = 32f;
    /** The band along a Float's sides and bottom that moves it, as its name strip does, in dp. */
    static final float FLOAT_EDGE_DP = 12f;
    /** The focused window's rim, in dp. */
    static final float RIM_DP = 2f;
    /** How long a move between places takes, in ms. */
    static final long MOVE_MS = StackMotion.MOVE_MS;
    /** Navigation mode's scrim over the stack, so the mode shows (#433). */
    static final int NAVIGATION_SCRIM = 0x26000000;
    /** The band along a gap a carried window lands in as a column of its own, in dp. */
    static final float NAVIGATION_GAP_CATCH_DP = 28f;
    /** The band along the screen's edges where a carried window keeps the view moving, in dp. */
    static final float NAVIGATION_EDGE_DP = 48f;
    /** How fast a carried window held at the very edge moves the view, in dp per ms. */
    static final float NAVIGATION_EDGE_SPEED_DP_PER_MS = 0.9f;
    /** How often a carried window held at an edge moves the view, in ms (it moves by the time that passed). */
    static final long NAVIGATION_EDGE_TICK_MS = 16L;
    /** How long a carried window waits at an edge before the view moves. */
    static final long NAVIGATION_EDGE_DWELL_MS = 250L;
    /** The carried window's own frame, faded where it stood. */
    static final float CARRIED_ALPHA = 0.35f;
    /** The carried window's picture under the finger, against its frame's size. */
    static final float CARRIED_SCALE = 0.5f;

    @NonNull private final Listener mListener;
    @NonNull private final Map<Integer, Page> mPages = new HashMap<>();
    @NonNull private final BorderHold mHold = new BorderHold(true);
    @NonNull private final BorderHoldGlyph mGlyph = new BorderHoldGlyph();
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Runnable mHoldElapsed = this::onHoldElapsed;
    private final float mDensity;
    private final int mTouchSlop;
    @NonNull private WindowLook.Source mLook = WindowLook.DEFAULT;
    private boolean mHasHome;
    private float mPosition;
    @Nullable private ValueAnimator mMotion;
    /** What a move looks like now: the slide, or the quick fade under Remove animations (#434). */
    @NonNull private StackMotion.Kind mMotionKind = StackMotion.Kind.SLIDE;
    /** Android's bars over the view's top and bottom: the edge bands stand inside them. */
    private int mInsetTop;
    private int mInsetBottom;
    /** Where the windows stand, in px as every page's viewport is laid out: the air around it is the hold's. */
    @NonNull private final Rect mRoomPx = new Rect();
    /** The room a full-screen page stands in (#426): no air round it, Android's bars still clear. */
    @NonNull private final Rect mFullRoomPx = new Rect();
    /** The desktop changed whether the view reaches over the bar's room: the next size change renders at once. */
    private boolean mResizeExpected;
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
    /** Navigation mode is on (#433): every touch on the view is the mode's. */
    private boolean mNavigating;
    @NonNull private final NavigationGesture mNav;
    @NonNull private final NavigationEdgePan mEdgePan;
    /** Each page's strip as a slide in navigation mode left it, by level; none until one moves it. */
    @NonNull private final Map<Integer, Float> mPanScroll = new HashMap<>();
    /** The levels the view may slide between: Home (or Workspace 1) to the last Workspace. */
    private int mFirstLevel;
    private int mLastLevel;
    private float mNavDownX;
    private float mNavDownY;
    @NonNull private final Runnable mLongPress = this::onNavigationLongPress;
    @NonNull private final Runnable mEdgeFrame = this::onEdgeFrame;
    private boolean mEdgeFrameQueued;
    @NonNull private final float[] mEdgeStep = new float[2];
    /** The window carried, or {@link NavigationDrop#NO_WINDOW}; where the finger holds it, and its picture's size. */
    private long mCarried = NavigationDrop.NO_WINDOW;
    private float mCarryX;
    private float mCarryY;
    private float mCarriedWidth;
    private float mCarriedHeight;
    /** Where the carried window would land now and on which place's page; null for nowhere. */
    @Nullable private NavigationDrop.Drop mDropAt;
    private int mDropLevel;
    @NonNull private final Paint mNavPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final RectF mNavRect = new RectF();

    public TilingDesktopView(@NonNull Context context, @NonNull Listener listener, boolean hasHome) {
        super(context);
        mTap = new WallpaperTap(ViewConfiguration.get(context).getScaledTouchSlop(),
            ViewConfiguration.getTapTimeout());
        mListener = listener;
        mHasHome = hasHome;
        mDensity = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mNav = new NavigationGesture(mTouchSlop, ViewConfiguration.getLongPressTimeout());
        mEdgePan = new NavigationEdgePan(NAVIGATION_EDGE_DP * mDensity,
            NAVIGATION_EDGE_SPEED_DP_PER_MS * mDensity, NAVIGATION_EDGE_DWELL_MS);
        mFirstLevel = hasHome ? 0 : 1;
        mLastLevel = 1;
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

    /** The windows' look (#423): every frame wears it now and as it is made. */
    void setLook(@NonNull WindowLook.Source look) {
        mLook = look;
        for (Page page : mPages.values()) {
            for (WindowFrame frame : page.mFrames.values()) frame.restyle();
            for (FloatFrame floating : page.mFloats.mFrames.values()) floating.restyle();
        }
        for (FloatFrame floating : mPinned.mFrames.values()) floating.restyle();
        if (mHomeFloats != null) for (FloatFrame floating : mHomeFloats.mFrames.values()) floating.restyle();
    }

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
    void setRoom(@NonNull RectF room, @NonNull RectF fullRoom, int top, int bottom) {
        mInsetTop = Math.max(0, top);
        mInsetBottom = Math.max(0, bottom);
        roomBounds(room, mRoomPx);
        roomBounds(fullRoom, mFullRoomPx);
    }

    /** The width and height a full-screen window fills, in px (each page reads it for itself). */
    int fullRoomWidth() {
        return mFullRoomPx.width();
    }

    int fullRoomHeight() {
        return mFullRoomPx.height();
    }

    /** The room {@code page} stands in: the full one when its window is full screen. */
    @NonNull
    private Rect roomOf(@Nullable Page page) {
        return page != null && page.mFullScreen ? mFullRoomPx : mRoomPx;
    }

    /**
     * The desktop changed whether the view reaches over the bar's room: the size change that
     * follows renders in the same pass, so the windows move once.
     */
    void expectResize() {
        mResizeExpected = true;
        // No size change came (the view was already that size): the flag does not outlive the frame.
        post(() -> mResizeExpected = false);
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

    /** Sets what the next moves look like: {@link StackMotion#kindFor}. */
    void setMotionKind(@NonNull StackMotion.Kind kind) {
        mMotionKind = kind;
    }

    /** Fades the stack in over {@link StackMotion#FADE_MS}: the move that replaces a slide. */
    private void fadeIn() {
        animate().cancel();
        setAlpha(StackMotion.FADE_FROM);
        animate().alpha(1f).setDuration(StackMotion.FADE_MS).start();
    }

    /**
     * Moves to {@code level}: slides there when {@code animate}, fades in place when Remove
     * animations has {@link #setMotionKind} on {@link StackMotion.Kind#FADE}.
     */
    void moveTo(int level, boolean animate) {
        if (mMotion != null) mMotion.cancel();
        mMotion = null;
        if (!animate || getHeight() <= 0 || mPosition == level) {
            setPosition(level);
            return;
        }
        if (mMotionKind == StackMotion.Kind.FADE) {
            setPosition(level);
            fadeIn();
            return;
        }
        ValueAnimator motion = ValueAnimator.ofFloat(mPosition, level);
        motion.setDuration(StackMotion.MOVE_MS);
        motion.setInterpolator(new PathInterpolator(StackMotion.EASE[0], StackMotion.EASE[1],
            StackMotion.EASE[2], StackMotion.EASE[3]));
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
        boolean shown = mMotion != null || mPosition > 0f || !mHasHome || mNavigating;
        int visibility = shown ? VISIBLE : INVISIBLE;
        if (getVisibility() != visibility) setVisibility(visibility);
    }

    // ---- size ------------------------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (Map.Entry<Integer, Page> entry : mPages.entrySet()) placePage(entry.getKey(), entry.getValue());
        placePinned();
        // The pages are laid out again from the controller, after this pass; at once after a change
        // of the bar's room, so a frame is never drawn with the windows at the old size.
        if (mResizeExpected) {
            mResizeExpected = false;
            mListener.onRoomChanged();
        } else {
            post(mListener::onRoomChanged);
        }
    }

    // ---- touch: the border hold ------------------------------------------------------------------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Navigation mode: windows take no input, every touch is the mode's.
        if (mNavigating) return true;
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
        return TilingInput.gapCentreX(mZone, front.mLayout, roomOf(front).left, -front.mStrip.getTranslationX());
    }

    private float gapCentreY() {
        Page front = mPages.get(Math.round(mPosition));
        if (front == null || front.mLayout == null) return mZone.centreY;
        return TilingInput.gapCentreY(mZone, front.mLayout, roomOf(front).top);
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
        Rect room = roomOf(front);
        if (front != null && front.mTiles != null) {
            return TilingInput.zoneAtTiles(x, y, getWidth(), mInsetTop, getHeight() - mInsetBottom,
                room.left, room.top, room.right, room.bottom, front.mTiles);
        }
        ScrollingLayoutPolicy.Layout layout = front == null ? null : front.mLayout;
        if (layout == null) layout = ScrollingLayoutPolicy.layout(ScrollingLayoutPolicy.State.EMPTY,
            new ScrollingLayoutPolicy.Viewport(0f, 0f, 0f, false));
        float scroll = front == null ? 0f : -front.mStrip.getTranslationX();
        return TilingInput.zoneAt(x, y, getWidth(), mInsetTop, getHeight() - mInsetBottom,
            room.left, room.top, room.right, room.bottom, layout, scroll);
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
        if (mNavigating) return onNavigationTouch(ev);
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
        mHandler.removeCallbacks(mLongPress);
        mHandler.removeCallbacks(mEdgeFrame);
        mEdgeFrameQueued = false;
        if (mMotion != null) mMotion.cancel();
        super.onDetachedFromWindow();
    }

    /** The hold's chevron at the held edge, pointing the way it steps, drawn only while it fills. */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mNavigating) drawNavigation(canvas);
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

    // ---- navigation mode (#433) -----------------------------------------------------------------

    /**
     * Navigation mode on or off. On, the view shows even at rest on Home and takes every touch;
     * off, a carried window goes back and any slide is forgotten (the controller has settled it).
     */
    void setNavigating(boolean on) {
        if (mNavigating == on) return;
        mNavigating = on;
        mHandler.removeCallbacks(mLongPress);
        if (!on) {
            mNav.cancel();
            endCarry();
            mPanScroll.clear();
        }
        applyVisibility();
        invalidate();
    }

    boolean isNavigating() {
        return mNavigating;
    }

    /** The levels the view may slide between: {@code first} (Home 0, else 1) to {@code last}. */
    void setLevelBounds(int first, int last) {
        mFirstLevel = first;
        mLastLevel = Math.max(first, last);
    }

    /** Where navigation mode has slid the strip of the page at {@code level}, or NaN when it has not. */
    float panScroll(int level) {
        Float scroll = mPanScroll.get(level);
        return scroll == null ? Float.NaN : scroll;
    }

    /** The scroll {@code page} at {@code level} shows: a slide's, else its strip's own. */
    private float scrollOf(int level, @NonNull Page page) {
        Float scroll = mPanScroll.get(level);
        return scroll != null ? scroll : -page.mStrip.getTranslationX();
    }

    /** What {@code page} lays out: its Dwindle tiles' windows or its columns; null before its first. */
    @Nullable
    private static ScrollingLayoutPolicy.Layout layoutOf(@NonNull Page page) {
        return page.mTiles != null ? page.mTiles.layout : page.mLayout;
    }

    private boolean onNavigationTouch(@NonNull MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();
        long now = ev.getEventTime();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // A settle still sliding stops where it is, and the finger takes the stack from there.
                if (mMotion != null) mMotion.cancel();
                mMotion = null;
                for (Map.Entry<Integer, Page> entry : mPages.entrySet()) {
                    Page page = entry.getValue();
                    page.mStrip.animate().cancel();
                }
                mNav.down(x, y, now);
                mNavDownX = x;
                mNavDownY = y;
                mHandler.removeCallbacks(mLongPress);
                mHandler.postAtTime(mLongPress, mNav.longPressAt());
                break;
            case MotionEvent.ACTION_MOVE:
                if (mNav.phase() == NavigationGesture.Phase.CARRYING) {
                    mCarryX = x;
                    mCarryY = y;
                    updateDrop();
                    scheduleEdgeFrame();
                    invalidate();
                    break;
                }
                if (mNav.move(x, y, now) == NavigationGesture.Phase.PANNING) {
                    mHandler.removeCallbacks(mLongPress);
                    // The stack follows the finger: dragging it down shows the places above.
                    panBy(-mNav.stepX(), -mNav.stepY());
                }
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(mLongPress);
                finishNavigation(mNav.up(x, y, now), x, y);
                break;
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeCallbacks(mLongPress);
                finishNavigation(mNav.cancel(), x, y);
                break;
            default:
                break;
        }
        return true;
    }

    private void finishNavigation(@NonNull NavigationGesture.Outcome outcome, float x, float y) {
        switch (outcome) {
            case TAP: {
                long[] hit = windowAt(x, y, true);
                if (hit != null) mListener.onNavigationTap((int) hit[0], hit[1]);
                // A tap beside every window leaves the view where it rests (a settle it stopped goes on).
                else settle();
                break;
            }
            case SETTLE:
                settle();
                break;
            case DROP: {
                long carried = mCarried;
                NavigationDrop.Drop drop = mDropAt;
                int level = mDropLevel;
                endCarry();
                if (drop != null && drop.type != NavigationDrop.Type.NONE) {
                    mPanScroll.clear();
                    mListener.onNavigationDrop(carried, level, drop);
                } else {
                    settle();
                }
                break;
            }
            case CANCEL:
                endCarry();
                settle();
                break;
            default:
                break;
        }
    }

    /**
     * Slides the stack: {@code dx} px along the strip of the page nearest the view (toward its
     * later columns when positive), {@code dy} px through the places (toward the lower ones).
     * Only translations change.
     */
    private void panBy(float dx, float dy) {
        int height = getHeight();
        if (dy != 0f && height > 0) {
            float position = Math.max(mFirstLevel, Math.min(mLastLevel, mPosition + dy / height));
            if (position != mPosition) setPosition(position);
        }
        if (dx != 0f) {
            int level = Math.round(mPosition);
            Page page = mPages.get(level);
            ScrollingLayoutPolicy.Layout layout = page == null ? null : layoutOf(page);
            if (page != null && layout != null && page.mTiles == null && !page.mFullScreen) {
                float max = Math.max(0f, layout.stripWidth - roomOf(page).width());
                float scroll = Math.max(0f, Math.min(max, scrollOf(level, page) + dx));
                mPanScroll.put(level, scroll);
                if (page.mStrip.getTranslationX() != -scroll) page.mStrip.setTranslationX(-scroll);
            }
        }
        mListener.onNavigationMoved();
    }

    /** The slide was let go: the window nearest the view's centre, across places, is where it settles. */
    private void settle() {
        List<NavigationSettle.Candidate> candidates = new ArrayList<>();
        int height = getHeight();
        if (mHasHome && mFirstLevel == 0) {
            float top = TilingLivePolicy.offsetPx(0, mPosition, height);
            candidates.add(new NavigationSettle.Candidate(0, NavigationSettle.NO_WINDOW, 0f, top, getWidth(),
                top + height));
        }
        for (Map.Entry<Integer, Page> entry : mPages.entrySet()) {
            int level = entry.getKey();
            Page page = entry.getValue();
            Rect room = roomOf(page);
            float scroll = scrollOf(level, page);
            float top = page.getTranslationY() + room.top;
            ScrollingLayoutPolicy.Layout layout = layoutOf(page);
            if (layout == null || layout.placements.isEmpty()) {
                candidates.add(new NavigationSettle.Candidate(level, NavigationSettle.NO_WINDOW, room.left, top,
                    room.right, top + room.height()));
                continue;
            }
            for (ScrollingLayoutPolicy.Placement p : layout.placements) {
                candidates.add(new NavigationSettle.Candidate(level, p.windowId, room.left + p.left - scroll,
                    top + p.top, room.left + p.right - scroll, top + p.bottom));
            }
        }
        mPanScroll.clear();
        NavigationSettle.Target target = NavigationSettle.nearest(candidates, mRoomPx.exactCenterX(),
            mRoomPx.exactCenterY());
        if (target != null) mListener.onNavigationSettle(target);
    }

    /**
     * The window at ({@code x}, {@code y}) as {level, id}: a Float of the page there (or a pinned
     * one) when {@code floats}, else a tile; null over no window.
     */
    @Nullable
    private long[] windowAt(float x, float y, boolean floats) {
        if (floats && mPinnedHost.getVisibility() == VISIBLE) {
            long pinned = mPinned.idAt(x - mPinnedHost.getTranslationX(), y - mPinnedHost.getTranslationY());
            if (pinned != NavigationDrop.NO_WINDOW) return new long[] {Math.round(mPosition), pinned};
        }
        for (Map.Entry<Integer, Page> entry : mPages.entrySet()) {
            Page page = entry.getValue();
            float py = y - page.getTranslationY();
            if (page.getVisibility() != VISIBLE || py < 0f || py >= getHeight()) continue;
            int level = entry.getKey();
            if (floats) {
                long floating = page.mFloats.idAt(x - page.getTranslationX(), py);
                if (floating != NavigationDrop.NO_WINDOW) return new long[] {level, floating};
            }
            Rect room = roomOf(page);
            ScrollingLayoutPolicy.Layout layout = layoutOf(page);
            if (layout == null) return null;
            float sx = x - room.left + scrollOf(level, page);
            float sy = py - room.top;
            for (ScrollingLayoutPolicy.Placement p : layout.placements) {
                if (sx >= p.left && sx < p.right && sy >= p.top && sy < p.bottom) return new long[] {level, p.windowId};
            }
            return null;
        }
        return null;
    }

    /** The long press ran its time: a still finger on a tile picks it up. */
    private void onNavigationLongPress() {
        long[] hit = windowAt(mNavDownX, mNavDownY, false);
        if (!mNav.longPress(SystemClock.uptimeMillis(), hit != null)) return;
        mCarried = hit[1];
        mCarryX = mNavDownX;
        mCarryY = mNavDownY;
        WindowFrame frame = frameOf(mCarried);
        float min = 64f * mDensity;
        mCarriedWidth = Math.max(min, (frame == null ? getWidth() : frame.getWidth()) * CARRIED_SCALE);
        mCarriedHeight = Math.max(min, (frame == null ? getHeight() : frame.getHeight()) * CARRIED_SCALE);
        if (frame != null) frame.setAlpha(CARRIED_ALPHA);
        mEdgePan.reset();
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        updateDrop();
        invalidate();
    }

    /** The carried window goes back to looking as it did; nothing is carried. */
    private void endCarry() {
        if (mCarried != NavigationDrop.NO_WINDOW) {
            WindowFrame frame = frameOf(mCarried);
            if (frame != null) frame.setAlpha(1f);
        }
        mCarried = NavigationDrop.NO_WINDOW;
        mDropAt = null;
        mEdgePan.reset();
        mHandler.removeCallbacks(mEdgeFrame);
        mEdgeFrameQueued = false;
        invalidate();
    }

    /** Where the carried window would land with the finger where it is now. */
    private void updateDrop() {
        mDropAt = null;
        if (mCarried == NavigationDrop.NO_WINDOW) return;
        for (Map.Entry<Integer, Page> entry : mPages.entrySet()) {
            Page page = entry.getValue();
            float py = mCarryY - page.getTranslationY();
            if (page.getVisibility() != VISIBLE || py < 0f || py >= getHeight()) continue;
            int level = entry.getKey();
            Rect room = roomOf(page);
            float scroll = scrollOf(level, page);
            ScrollingLayoutPolicy.Layout layout = layoutOf(page);
            List<NavigationDrop.Tile> tiles = new ArrayList<>();
            if (layout != null) {
                for (ScrollingLayoutPolicy.Placement p : layout.placements) {
                    tiles.add(new NavigationDrop.Tile(p.windowId, p.column, p.left, p.top, p.right, p.bottom));
                }
            }
            float[] span = {scroll, 0f, scroll + room.width(), room.height()};
            mDropAt = NavigationDrop.at(tiles, page.mTiles != null, mCarried, mCarryX - room.left + scroll,
                py - room.top, NAVIGATION_GAP_CATCH_DP * mDensity, span);
            mDropLevel = level;
            return;
        }
    }

    private void scheduleEdgeFrame() {
        if (mEdgeFrameQueued) return;
        mEdgeFrameQueued = true;
        mHandler.postDelayed(mEdgeFrame, NAVIGATION_EDGE_TICK_MS);
    }

    /** A tick while a window is carried: held at an edge, the view moves on that way. */
    private void onEdgeFrame() {
        mEdgeFrameQueued = false;
        if (mCarried == NavigationDrop.NO_WINDOW) return;
        boolean inBand = mEdgePan.step(mCarryX, mCarryY, 0f, mInsetTop, getWidth(), getHeight() - mInsetBottom,
            SystemClock.uptimeMillis(), mEdgeStep);
        if (!inBand) return;
        if (mEdgeStep[0] != 0f || mEdgeStep[1] != 0f) {
            panBy(mEdgeStep[0], mEdgeStep[1]);
            updateDrop();
            invalidate();
        }
        scheduleEdgeFrame();
    }

    /** The mode's scrim and outline; where a carried window would land, and its picture under the finger. */
    private void drawNavigation(@NonNull Canvas canvas) {
        int accent = BorderGrabber.accent(getContext());
        float radius = WindowLook.cornerPx(mLook.cornerDp(), mDensity, getWidth(), getHeight());
        mNavPaint.setStyle(Paint.Style.FILL);
        mNavPaint.setColor(NAVIGATION_SCRIM);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), mNavPaint);
        float stroke = RIM_DP * mDensity;
        mNavPaint.setStyle(Paint.Style.STROKE);
        mNavPaint.setStrokeWidth(stroke);
        mNavPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 0x99));
        mNavRect.set(stroke / 2f, mInsetTop + stroke / 2f, getWidth() - stroke / 2f,
            getHeight() - mInsetBottom - stroke / 2f);
        canvas.drawRoundRect(mNavRect, radius, radius, mNavPaint);
        if (mCarried == NavigationDrop.NO_WINDOW) return;
        NavigationDrop.Drop drop = mDropAt;
        Page dropPage = drop == null ? null : mPages.get(mDropLevel);
        if (drop != null && dropPage != null && drop.type != NavigationDrop.Type.NONE) {
            Rect room = roomOf(dropPage);
            float dx = room.left - scrollOf(mDropLevel, dropPage);
            float dy = dropPage.getTranslationY() + room.top;
            mNavRect.set(drop.left + dx, drop.top + dy, drop.right + dx, drop.bottom + dy);
            mNavPaint.setStyle(Paint.Style.FILL);
            mNavPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 0x40));
            canvas.drawRoundRect(mNavRect, radius, radius, mNavPaint);
            mNavPaint.setStyle(Paint.Style.STROKE);
            mNavPaint.setColor(accent);
            canvas.drawRoundRect(mNavRect, radius, radius, mNavPaint);
        }
        mNavRect.set(mCarryX - mCarriedWidth / 2f, mCarryY - mCarriedHeight / 2f, mCarryX + mCarriedWidth / 2f,
            mCarryY + mCarriedHeight / 2f);
        mNavPaint.setStyle(Paint.Style.FILL);
        mNavPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(mGroundColor, 0xCC));
        canvas.drawRoundRect(mNavRect, radius, radius, mNavPaint);
        mNavPaint.setStyle(Paint.Style.STROKE);
        mNavPaint.setColor(accent);
        canvas.drawRoundRect(mNavRect, radius, radius, mNavPaint);
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
        /** Whether a window of this page is full screen: the page stands in the full room. */
        boolean mFullScreen;
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
        /** Where the page's windows stand now: {left, top, width, height}, in the page's coordinates. */
        @NonNull
        int[] viewportBounds() {
            LayoutParams params = (LayoutParams) mViewport.getLayoutParams();
            return new int[] {params.leftMargin, params.topMargin, params.width, params.height};
        }

        void setViewport(boolean fullScreen) {
            mFullScreen = fullScreen;
            LayoutParams params = (LayoutParams) mViewport.getLayoutParams();
            Rect room = fullScreen ? mFullRoomPx : mRoomPx;
            int left = room.left;
            int top = room.top;
            int width = room.width();
            int height = room.height();
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

        /**
         * Gives each frame the name strip of {@code names} (#422); a frame with no entry shows none
         * (tabbed tiles, and everything while a window is full screen).
         */
        void setNameStrips(@NonNull Map<Long, TilingTabStrip.Tab> names) {
            for (Map.Entry<Long, WindowFrame> e : mFrames.entrySet()) e.getValue().setNameStrip(names.get(e.getKey()));
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
                // A frame made again while its window is carried (#433) stays faded where it stood.
                frame.setAlpha(placement.windowId == mCarried ? CARRIED_ALPHA : 1f);
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
                if (animate && isShown() && mMotionKind == StackMotion.Kind.FADE) {
                    mStrip.setTranslationX(-scroll);
                    mStrip.setAlpha(StackMotion.FADE_FROM);
                    mStrip.animate().alpha(1f).setDuration(StackMotion.FADE_MS).start();
                } else if (animate && isShown()) {
                    mStrip.animate().translationX(-scroll).setDuration(StackMotion.MOVE_MS).start();
                } else mStrip.setTranslationX(-scroll);
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
                ground.setCornerRadius(WindowLook.cornerPx(mLook.cornerDp(), mDensity, 1 << 16, 1 << 16));
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
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    float radius = radiusPx();
                    // Under a Float's name strip the top corners are the strip's: square here.
                    outline.setRoundRect(0, inFloat ? -Math.round(radius) : 0, view.getWidth(),
                        view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
            mRim.setCornerRadius(radiusPx());
            mRim.setColor(Color.TRANSPARENT);
            setForeground(mRim);
            setWillNotDraw(false);
        }

        // The window look (#423): the card every kind wears. Drawn once per change, never per frame.
        private int mLinuxOpacity = -1;
        @Nullable private GradientDrawable mGlass;
        private float mRadiusPx = -1f;
        @Nullable private Paint mVeil;

        /** The corner radius now: Global's, clamped for a small tile. */
        private float radiusPx() {
            return WindowLook.cornerPx(mLook.cornerDp(), mDensity, getWidth(), getHeight());
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            float radius = radiusPx();
            if (radius == mRadiusPx) return;
            mRadiusPx = radius;
            mRim.setCornerRadius(radius);
            if (mGlass != null) mGlass.setCornerRadius(radius);
            invalidateOutline();
        }

        /**
         * A Linux window fades whole (its view's alpha) over the window's own glass, which shows
         * through a see-through app. Other kinds (-1) draw their own ground.
         */
        void setLinuxOpacity(int percent) {
            int tintAlpha = WindowLook.glassAlpha(mLook.glassOpacity());
            if (mLinuxOpacity == percent && mGlass != null && mGlassAlpha == tintAlpha) return;
            mLinuxOpacity = percent;
            mGlassAlpha = tintAlpha;
            View content = getChildCount() > 0 ? getChildAt(0) : null;
            if (content != null) content.setAlpha(WindowLook.linuxAlpha(percent));
            if (mGlass == null) {
                mGlass = new GradientDrawable();
                mGlass.setCornerRadius(radiusPx());
                setBackground(mGlass);
            }
            mGlass.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                com.google.android.material.color.MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorSurface, Color.BLACK), tintAlpha));
        }

        private int mGlassAlpha = -1;

        /** Takes the look again: corners, rim, dim, and a Linux window's opacity. */
        void restyle() {
            mRadiusPx = -1f;
            onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
            mRimLaid = false;
            applyRim();
            if (mLinuxOpacity >= 0) {
                int percent = mLinuxOpacity;
                mLinuxOpacity = -1;
                setLinuxOpacity(percent);
            }
            invalidate();
        }

        private boolean mRimLaid;
        /** A Float's content has no rim of its own (the Float wears it); this is its focus, for the dim. */
        private boolean mDimFocus;

        void setDimFocus(boolean focused) {
            if (mDimFocus == focused) return;
            mDimFocus = focused;
            if (mLook.unfocusedDimPercent() > 0) invalidate();
        }

        private void applyRim() {
            int width = WindowLook.rimWidthPx(mFocused, mLook.rimOn(), RIM_DP, mDensity);
            mRim.setStroke(width, width == 0 ? Color.TRANSPARENT
                : WindowLook.rimColour(mLook.rimColour(), BorderGrabber.accent(getContext())));
            mRimLaid = true;
        }

        // The name strip (#422): drawn over the content in dispatchDraw, never a view, so every touch
        // reaches the content. Laid out again only when the name, icon or width changes.
        @Nullable private TilingTabStrip.Tab mNameTab;
        @Nullable private Drawable mNameIcon;
        @Nullable private CharSequence mNameText;
        private int mNameLaidWidth = -1;
        @Nullable private Paint mNamePaint;
        @Nullable private Paint mNameFill;
        private int mNameInk = Color.WHITE;
        private int mNameTint = 0x99000000;

        void setNameStrip(@Nullable TilingTabStrip.Tab tab) {
            if (java.util.Objects.equals(mNameTab, tab)) return;
            mNameTab = tab;
            mNameLaidWidth = -1;
            mNameIcon = null;
            mNameText = null;
            invalidate();
        }

        private void layOutName(int width) {
            TilingTabStrip.Tab tab = mNameTab;
            if (tab == null) return;
            if (mNamePaint == null) {
                mNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mNameInk = com.google.android.material.color.MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
                mNameTint = androidx.core.graphics.ColorUtils.setAlphaComponent(
                    com.google.android.material.color.MaterialColors.getColor(this,
                        com.google.android.material.R.attr.colorSurface, Color.BLACK), 0x99);
                mNamePaint.setColor(mNameInk);
                mNamePaint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
                mNameFill = new Paint();
            }
            Drawable icon = tab.icon != null ? new android.graphics.drawable.BitmapDrawable(getResources(), tab.icon)
                : androidx.core.content.ContextCompat.getDrawable(getContext(), TilingTabStrip.kindIcon(tab.kind));
            int size = Math.round(10f * mDensity);
            if (icon != null) {
                icon = icon.mutate();
                icon.setBounds(0, 0, size, size);
                if (tab.icon == null) icon.setTint(mNameInk);
            }
            mNameIcon = icon;
            float pad = 6f * mDensity;
            float room = width - 2 * pad - (icon == null ? 0f : size + 4f * mDensity);
            mNameText = TextUtils.ellipsize(tab.name, new android.text.TextPaint(mNamePaint), Math.max(0f, room),
                TextUtils.TruncateAt.END);
            mNameLaidWidth = width;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            int dim = WindowLook.dimAlpha(mInFloat ? mDimFocus : mFocused, mLook.unfocusedDimPercent());
            if (dim > 0) {
                if (mVeil == null) mVeil = new Paint();
                mVeil.setColor(Color.argb(dim, 0, 0, 0));
                canvas.drawRect(0, 0, getWidth(), getHeight(), mVeil);
            }
            if (mNameTab == null || mInFloat) return;
            int width = getWidth();
            if (mNameLaidWidth != width) layOutName(width);
            if (mNamePaint == null || mNameText == null) return;
            float h = TILE_NAME_STRIP_DP * mDensity;
            mNameFill.setColor(mNameTint);
            canvas.drawRect(0, 0, width, h, mNameFill);
            float pad = 6f * mDensity;
            float x = pad;
            if (mNameIcon != null) {
                int size = mNameIcon.getBounds().height();
                canvas.save();
                canvas.translate(x, (h - size) / 2f);
                mNameIcon.draw(canvas);
                canvas.restore();
                x += size + 4f * mDensity;
            }
            Paint.FontMetrics fm = mNamePaint.getFontMetrics();
            canvas.drawText(mNameText, 0, mNameText.length(), x, (h - fm.ascent - fm.descent) / 2f, mNamePaint);
            mNameFill.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(mNameInk, 0x40));
            canvas.drawRect(0, h - 1f, width, h, mNameFill);
        }

        /**
         * A terminal in this frame keeps its text below the name strip: its own top inset, so the
         * band is inside the terminal view (touches on it still reach the terminal) and no row is
         * covered. Other kinds stay under the overlay. Only an inset this strip set is changed.
         */
        void syncTerminalInset() {
            com.termux.view.TerminalView terminal = terminalIn(this, 0);
            if (terminal == null) return;
            int strip = NameStripPolicy.insetPx(TILE_NAME_STRIP_DP, mDensity, mNameTab != null && !mInFloat);
            int now = terminal.getTextTopInsetPx();
            int ours = NameStripPolicy.insetPx(TILE_NAME_STRIP_DP, mDensity, true);
            if (strip > 0 && now != strip) terminal.setTextTopInsetPx(strip);
            else if (strip == 0 && now == ours) terminal.setTextTopInsetPx(0);
        }

        @Nullable
        private com.termux.view.TerminalView terminalIn(@NonNull View view, int depth) {
            if (view instanceof com.termux.view.TerminalView) return (com.termux.view.TerminalView) view;
            if (depth > 3 || !(view instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                com.termux.view.TerminalView found = terminalIn(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
            return null;
        }

        void place(@NonNull ScrollingLayoutPolicy.Placement placement) {
            placeAt(this, placement.left, placement.top, placement.right, placement.bottom);
        }

        void setFocusedRim(boolean focused) {
            if (mFocused == focused && mRimLaid) return;
            mFocused = focused;
            applyRim();
            if (mLook.unfocusedDimPercent() > 0) invalidate();
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

        /**
         * Global's radius as asked, not as clamped to a frame that has no size yet: the pane clamps it
         * against its own bounds, and a terminal is dressed before its frame is laid out.
         */
        @Override public float cornerRadiusPx() {
            return Math.max(0, mLook.cornerDp()) * mDensity;
        }

        /** A terminal's ground is the Windows glass, as a Linux frame's is: side by side they match. */
        @Override public int groundAlpha() {
            return WindowLook.glassAlpha(mLook.glassOpacity());
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
            return idAt(x, y) != NavigationDrop.NO_WINDOW;
        }

        /** The window of the Float at ({@code x}, {@code y}) in the layer's coordinates, or {@link NavigationDrop#NO_WINDOW}. */
        long idAt(float x, float y) {
            if (mHost.getVisibility() != VISIBLE) return NavigationDrop.NO_WINDOW;
            for (FloatFrame frame : mFrames.values()) {
                if (frame.getVisibility() != VISIBLE) continue;
                float left = frame.getLeft() + frame.getTranslationX();
                float top = frame.getTop() + frame.getTranslationY();
                if (x >= left && x < left + frame.getWidth() && y >= top && y < top + frame.getHeight())
                    return frame.mWindowId;
            }
            return NavigationDrop.NO_WINDOW;
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
            float radius = floatRadiusPx();
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), floatRadiusPx());
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

        private float floatRadiusPx() {
            return WindowLook.cornerPx(mLook.cornerDp(), mDensity, getWidth() > 0 ? getWidth() : 1 << 16,
                getHeight() > 0 ? getHeight() : 1 << 16);
        }

        /** Takes the look again: the Float's corners, rim and its content's. */
        void restyle() {
            mRim.setCornerRadius(floatRadiusPx());
            applyFloatRim();
            invalidateOutline();
            mContent.restyle();
        }

        private void applyFloatRim() {
            int width = WindowLook.rimWidthPx(mFocused, mLook.rimOn(), RIM_DP, mDensity);
            mRim.setStroke(width, width == 0 ? Color.TRANSPARENT
                : WindowLook.rimColour(mLook.rimColour(), BorderGrabber.accent(getContext())));
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
                applyFloatRim();
            }
            mContent.setDimFocus(spec.focused);
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
