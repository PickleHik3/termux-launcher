package com.termux.app.terminal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.DockPlankController;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the recursive split-pane layout, organised as tmux-style <b>windows</b>.
 *
 * <p>A {@link Window} is one screenful: a binary tree of panes ({@link Node}) where a leaf is a
 * shell and an internal {@link Split} arranges two children along an axis. Splitting replaces the
 * focused leaf with a Split of {oldLeaf, newLeaf}, so any number of panes / nesting depths are
 * possible. Only the active window's tree is rendered into the host; other windows keep their
 * trees (and running shells) alive off-screen.
 *
 * <p>Windows are grouped into <b>sessions</b> by the activity (a session owns an ordered list of
 * {@link Window}s). This controller is window-centric and session-agnostic: it renders/focuses
 * one active window and reports pane/window lifecycle back through {@link Host}.
 */
public class TerminalPaneController {

    public static final String LAYOUT_STACK = "stack";
    public static final String LAYOUT_GRID = "grid";
    public static final String LAYOUT_TALL = "tall";
    public static final String LAYOUT_FAT = "fat";
    public static final String LAYOUT_HORIZONTAL = "horizontal";
    public static final String LAYOUT_VERTICAL = "vertical";
    /**
     * Hyprland-style automatic tiling: every new pane halves the pane it was split from along that
     * pane's longer side, and a pane dragged onto another halves the target the same way. Unlike
     * the other layouts it is incremental — the tree is never rebuilt from the pane list, so the
     * shape the user grew (and every divider they dragged) survives each split and close.
     */
    public static final String LAYOUT_DWINDLE = "dwindle";

    /**
     * Cycle order for {@link #nextLayout()}. Deliberately not the documentation's listing order:
     * {@code stack} hides every unfocused pane, so it must not be where a single press from an
     * unmanaged window lands. It sits last instead.
     */
    private static final String[] LAYOUT_CYCLE = {
        LAYOUT_GRID, LAYOUT_DWINDLE, LAYOUT_TALL, LAYOUT_FAT, LAYOUT_HORIZONTAL, LAYOUT_VERTICAL,
        LAYOUT_STACK};

    public static final String EDGE_LEFT = "left";
    public static final String EDGE_RIGHT = "right";
    public static final String EDGE_UP = "up";
    public static final String EDGE_DOWN = "down";

    private static final String STATE_NODE_TYPE = "type";
    private static final String STATE_NODE_SESSION = "session";
    private static final String STATE_NODE_FONT_SIZE = "font_size";
    private static final String STATE_NODE_ORIENTATION = "orientation";
    private static final String STATE_NODE_WEIGHT_A = "weight_a";
    private static final String STATE_NODE_WEIGHT_B = "weight_b";
    private static final String STATE_NODE_A = "a";
    private static final String STATE_NODE_B = "b";
    private static final String STATE_WINDOW_ROOT = "root";
    private static final String STATE_WINDOW_ACTIVE = "active";
    private static final String STATE_WINDOW_LAYOUT = "layout_policy";
    private static final String STATE_WINDOW_NAME = "window_name";
    private static final String STATE_WINDOW_FLOATS = "floats";
    private static final String STATE_FLOAT_LEFT = "float_left";
    private static final String STATE_FLOAT_TOP = "float_top";
    private static final String STATE_FLOAT_WIDTH = "float_width";
    private static final String STATE_FLOAT_HEIGHT = "float_height";
    private static final String STATE_SCRATCHPAD_LEFT = "scratchpad_left";
    private static final String STATE_SCRATCHPAD_TOP = "scratchpad_top";
    private static final String STATE_SCRATCHPAD_WIDTH = "scratchpad_width";
    private static final String STATE_SCRATCHPAD_HEIGHT = "scratchpad_height";
    private static final int NODE_LEAF = 0;
    private static final int NODE_SPLIT = 1;

    /** toggleFloatActivePane outcomes. */
    public static final int FLOAT_TOGGLE_NONE = 0;        // no active pane to act on
    public static final int FLOAT_TOGGLE_FLOATED = 1;     // tiled pane detached into a float
    public static final int FLOAT_TOGGLE_DOCKED = 2;      // float split back into the tree
    public static final int FLOAT_TOGGLE_SINGLE_PANE = 3; // refused: window's only tiled pane

    private static final int FLOAT_MIN_WIDTH_DP = 120;
    private static final int FLOAT_MIN_HEIGHT_DP = 90;
    /** How much of the drag handle must remain reachable after any move or host resize. */
    private static final int FLOAT_MIN_VISIBLE_DP = 48;
    private static final int FLOAT_HANDLE_DP = 26;
    private static final int FLOAT_GRIP_DP = 28;
    /** The floating pill hovering in the (transparent) handle row above the terminal. */
    private static final int FLOAT_PILL_WIDTH_DP = 48;
    private static final int FLOAT_PILL_HEIGHT_DP = 18;
    /** Per-button slot width once a pill tap expands it into its action buttons. */
    private static final int FLOAT_PILL_BUTTON_DP = 44;
    /** Above tiled panes and the interaction overlay, below the 6dp key chord overlay. */
    private static final int FLOAT_ELEVATION_DP = 4;
    /**
     * Matches pane_active_border.xml's corner radius, so the content clip and its border ring
     * agree. Also the radius a tiled pane wears while that stroke is its frame.
     */
    private static final int FLOAT_CORNER_RADIUS_DP = 6;
    /** How far the resize glow reaches in from the pane's edge. */
    private static final float GLOW_DEPTH_DP = 12f;
    /** Peak alpha of the glow body, at the edge itself. */
    private static final int GLOW_ALPHA = 165;
    /** Steps in the hand-ramped glow's alpha falloff. */
    private static final int GLOW_RAMP_STEPS = 12;
    /** How long after the last resize keypress to commit the resize (mirrors touch drag-end). */
    private static final long RESIZE_KEY_FINISH_DELAY_MS = 220L;

    /** Callbacks into the hosting activity. */
    public interface Host {
        /** Spawn a new shell rooted at {@code cwd} (or default cwd if null); null on failure. */
        @Nullable TerminalSession createShell(@Nullable String cwd);
        /** Wire client + font + text size + keep-screen-on onto a freshly created pane view. */
        void configurePaneView(TerminalView view);
        /** Called after the shell is attached, when per-session view preferences can be selected. */
        default void configureAttachedPaneView(TerminalView view, TerminalSession session) {}
        /** Kill/remove a shell session from the service. */
        void removeShell(TerminalSession session);
        /** The active pane changed; activity should refresh anything keyed off the current view. */
        void onActivePaneChanged();
        /** The set of windows/panes changed; activity should rebuild the drawer. */
        void onTreesChanged();
        /**
         * The pane tree was just laid into the host view. Fires for every structural change —
         * split, close, maximize, layout change, window switch — so anything keyed off how many
         * panes are on screen can refresh from one place.
         */
        default void onPanesRendered() {}
        /** Default working directory when a cwd can't be derived. */
        String defaultCwd();
        /** Spawn a new shell carrying a session name; defaults to an unnamed shell. */
        @Nullable default TerminalSession createNamedShell(@NonNull String name,
                                                           @Nullable String cwd) {
            return createShell(cwd);
        }
        /** An existing, not-currently-displayed shell with this session name, or null. */
        @Nullable default TerminalSession findIdleShellByName(@NonNull String name) {
            return null;
        }
        /**
         * Whether a render/focus pass may move keyboard focus onto the terminal view. False while
         * a launcher-owned text field owns the system IME: stealing its focus mid-lifecycle is what
         * used to strand the system keyboard on screen after the screen turned off and on.
         */
        default boolean shouldTerminalTakeFocus() {
            return true;
        }
    }

    /** Supplies durable metadata for a pane while its tree is being snapshotted. */
    public interface WorkspacePaneCapture {
        @NonNull TerminalWorkspace.Pane capture(@NonNull TerminalSession session);
    }

    /** onSessionFinished outcomes. */
    public static final int FINISHED_UNKNOWN = 0; // shell not in any window
    public static final int FINISHED_PANE = 1;    // pane dropped, window still alive
    public static final int FINISHED_WINDOW = 2;  // window's last pane closed, window removed

    // --- Tree model ---
    abstract static class Node {
        @Nullable Split parent;
    }

    static final class Leaf extends Node {
        TerminalSession session;
        /**
         * Host-relative fractional bounds (0..1 left/top/right/bottom) while this leaf floats,
         * null while it is tiled. Fractions rather than pixels so rotation and host resizes keep
         * the pane proportionally where the user left it.
         */
        @Nullable RectF floatFrac;
        /**
         * What {@link #floatFrac} was clamped to for the host size it was last laid out against —
         * a projection onto the current host, not user intent. Transient: never saved or restored,
         * because remembering a shape the user did not ask for is the ratchet this replaced.
         */
        @Nullable transient RectF appliedFloatFrac;
        /**
         * This pane's pinned font size, or 0 while it follows the app-wide default. Set the first
         * time the pane is zoomed (and inherited by panes split off it), never by the default
         * changing — so zooming one pane can't move any other.
         */
        int fontSize;
        Leaf(TerminalSession session) { this.session = session; }
    }

    static final class Split extends Node {
        int orientation; // LinearLayout.HORIZONTAL (side by side) / VERTICAL (stacked)
        Node a, b;
        float weightA = 1f, weightB = 1f;
    }

    /** A window = one pane tree + which leaf is focused within it. Stable identity (object). */
    public static final class Window {
        private static final java.util.concurrent.atomic.AtomicLong NEXT_ID =
            new java.util.concurrent.atomic.AtomicLong(1L);
        /** Runtime-stable UI identity; independent from the window's mutable list index. */
        public final long id = NEXT_ID.getAndIncrement();
        Node root;
        Leaf active;
        /**
         * Retained automatic layout, or null when the window is manually managed. While set, the
         * layout keeps managing the window: adding or removing a pane recomputes the tree from it.
         * Any hand-shaping operation clears it, because otherwise the next split would silently
         * throw that shaping away.
         */
        @Nullable String layoutPolicy;
        /**
         * User-given tab name, or null while the tab derives its label from the window's foreground
         * process. Held per window rather than per shell so the label survives every pane change
         * inside it — splitting, closing and refocusing panes all leave the name alone.
         */
        @Nullable String name;
        /**
         * Panes detached from the tiled tree into freely positioned floats. List order is z-order
         * (last on top). A float always coexists with a non-empty tiled tree: the last tiled pane
         * can never float, and a dying tiled root promotes a float back into the tree.
         */
        final List<Leaf> floating = new ArrayList<>();
        Window(Leaf leaf) { root = leaf; active = leaf; }
    }

    private final Host mHost;
    @Nullable private PaneSurfaceStyle mSurfaceStyle;
    private final FrameLayout mHostView;
    private final LayoutInflater mInflater;

    /** All live windows (across every session). */
    private final List<Window> mWindows = new ArrayList<>();
    /** Cached pane frames + terminal views, keyed by shell session (reused across re-renders). */
    private final Map<TerminalSession, PaneContentFrame> mPaneFrames = new HashMap<>();
    private final Map<TerminalSession, TerminalView> mPaneViews = new HashMap<>();
    /** Live border drawable + focus state per pane, so a focus flip can crossfade and a
     *  redundant re-render can leave a mid-flight crossfade untouched instead of snapping it. */
    private final Map<TerminalSession, PaneRim> mBorderStates = new HashMap<>();
    private final Map<Split, LinearLayout> mSplitLayouts = new HashMap<>();
    /** The split a keybind resize burst is adjusting, while the finish is still debounced. */
    @Nullable private Split mPendingKeyResizeSplit;
    @Nullable private Runnable mFinishKeyResizeRunnable;
    /** Floating chrome containers of the rendered window, rebuilt on every render. */
    private final Map<Leaf, FloatingPaneContainer> mFloatContainers = new HashMap<>();
    private final PaneInteractionOverlay mInteractionOverlay;
    /** Ghosts of closed panes and the cursor's flight between panes; above everything. */
    private final PaneMotionOverlayView mMotionOverlay;

    /** Nested controller-wide lease covering every source of transient host geometry. */
    private int mHostSurfaceResizeDepth;

    @Nullable private Window mActiveWindow;
    @Nullable private Leaf mMaximizedLeaf;

    /** Fallback gap between tiled panes when no surface style is attached. */
    private static final int DIVIDER_DP = 1;


    public TerminalPaneController(Host host, FrameLayout hostView, LayoutInflater inflater) {
        mHost = host;
        mHostView = hostView;
        mInflater = inflater;
        mInteractionOverlay = new PaneInteractionOverlay();
        mMotionOverlay = new PaneMotionOverlayView(hostView.getContext());
        // Fractional float bounds only become pixels against the live host size, so a rotation or
        // keyboard resize must re-lay every float; the clamp keeps each drag handle reachable.
        mHostView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((r - l) != (or - ol) || (b - t) != (ob - ot)) {
                for (Map.Entry<Leaf, FloatingPaneContainer> entry : mFloatContainers.entrySet())
                    applyFloatBounds(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Create and show a single sessionless pane view so the activity has a non-null active view
     * during onCreate (before any window exists). Discarded on the first {@link #showWindow}.
     */
    public TerminalView createBootstrapView() {
        FrameLayout frame = (FrameLayout) mInflater.inflate(R.layout.view_terminal_pane, mHostView, false);
        TerminalView view = frame.findViewById(R.id.terminal_view);
        mHost.configurePaneView(view);
        mHostView.removeAllViews();
        mHostView.addView(frame, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    // --- Window lifecycle ---

    /** Create a new single-pane window around {@code shell} (not shown yet). */
    public Window newWindow(TerminalSession shell) {
        Window w = new Window(new Leaf(shell));
        // A window opened while another is focused starts at that pane's zoom level.
        ((Leaf) w.root).fontSize =
            inheritableFontSize(mActiveWindow == null ? null : mActiveWindow.active);
        w.layoutPolicy = mDefaultLayoutPolicy;
        mWindows.add(w);
        return w;
    }

    // --- Behaviour preferences ---

    /** Retained layout every new window starts under, or null for manual management. */
    @Nullable private String mDefaultLayoutPolicy;
    /**
     * focus.nvim-style growth: the focused pane takes {@link #FOCUS_GROW_SHARE} of every split on
     * its path to the root, so whichever pane the user taps (or an agent focuses) becomes the big
     * one and the rest slide aside.
     */
    private boolean mFocusGrowEnabled;
    static final float FOCUS_GROW_SHARE = 0.7f;
    @Nullable private ValueAnimator mFocusGrowAnimator;

    /**
     * The layout new windows are born under. Windows still at one unmanaged pane adopt it too, so
     * flipping the setting on takes effect in the tab you are looking at; anything the user has
     * already shaped is left alone.
     */
    public void setDefaultLayoutPolicy(@Nullable String layout) {
        mDefaultLayoutPolicy = isKnownLayout(layout) ? layout : null;
        if (mDefaultLayoutPolicy == null) return;
        for (Window w : mWindows) {
            if (w.layoutPolicy == null && w.root instanceof Leaf && w.floating.isEmpty()) {
                w.layoutPolicy = mDefaultLayoutPolicy;
            }
        }
    }

    /** Turn focus growth on (the focused pane grows now) or off (every divider goes back to 1:1). */
    public void setFocusGrowEnabled(boolean enabled) {
        if (mFocusGrowEnabled == enabled) return;
        mFocusGrowEnabled = enabled;
        if (mFocusGrowAnimator != null) mFocusGrowAnimator.cancel();
        if (mActiveWindow == null) return;
        if (enabled) {
            applyFocusGrowth(true);
        } else {
            for (Window w : mWindows) if (w.root != null) equalizeNode(w.root);
            render();
            mHost.onTreesChanged();
        }
    }

    public boolean isFocusGrowEnabled() {
        return mFocusGrowEnabled;
    }

    /**
     * Re-weight the splits between the focused pane and the root so the focused side holds
     * {@link #FOCUS_GROW_SHARE}. Splits off that path keep their ratios. A float or a maximized
     * pane needs no room made for it, so those leave the tree alone. With {@code animate} the
     * dividers ease over, with PTY resizing held until they settle — one reflow, not sixty.
     */
    private void applyFocusGrowth(boolean animate) {
        if (!mFocusGrowEnabled || mActiveWindow == null || mActiveWindow.active == null) return;
        Leaf active = mActiveWindow.active;
        if (mMaximizedLeaf != null || mActiveWindow.floating.contains(active)) return;
        final List<Split> splits = new ArrayList<>();
        final List<Float> fromA = new ArrayList<>();
        final List<Float> toA = new ArrayList<>();
        Node node = active;
        for (Split parent = node.parent; parent != null; node = parent, parent = parent.parent) {
            float total = parent.weightA + parent.weightB;
            float target = parent.a == node ? total * FOCUS_GROW_SHARE : total * (1f - FOCUS_GROW_SHARE);
            if (Math.abs(target - parent.weightA) < 0.001f) continue;
            splits.add(parent);
            fromA.add(parent.weightA);
            toA.add(target);
        }
        if (splits.isEmpty()) return;
        if (mFocusGrowAnimator != null) mFocusGrowAnimator.cancel();
        boolean live = true;
        for (Split split : splits) live &= mSplitLayouts.containsKey(split);
        if (!animate || !live || !arePaneAnimationsEnabled()) {
            for (int i = 0; i < splits.size(); i++) setSplitWeightA(splits.get(i), toA.get(i));
            if (live) {
                for (Split split : splits) applyWeightsToRenderedLayout(split);
                refreshPaneSizes();
            }
            return;
        }
        beginHostSurfaceResize();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PANE_MOVE_MS);
        animator.setInterpolator(PaneMotionOverlayView.standardInterpolator());
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            for (int i = 0; i < splits.size(); i++) {
                setSplitWeightA(splits.get(i), fromA.get(i) + (toA.get(i) - fromA.get(i)) * t);
                applyWeightsToRenderedLayout(splits.get(i));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                for (int i = 0; i < splits.size(); i++) {
                    setSplitWeightA(splits.get(i), toA.get(i));
                    applyWeightsToRenderedLayout(splits.get(i));
                }
                if (mFocusGrowAnimator == animation) mFocusGrowAnimator = null;
                finishHostSurfaceResizeKeepingBottom();
                mHost.onTreesChanged();
            }
        });
        mFocusGrowAnimator = animator;
        animator.start();
    }

    private static void setSplitWeightA(@NonNull Split split, float weightA) {
        float total = split.weightA + split.weightB;
        split.weightA = weightA;
        split.weightB = total - weightA;
    }

    /**
     * The font size a pane created from {@code leaf} should start with: the leaf's pinned size,
     * or 0 (follow the app default) when it has none or is the independently sized scratchpad.
     */
    private static int inheritableFontSize(@Nullable Leaf leaf) {
        if (leaf == null || leaf.fontSize <= 0 || isScratchpadLeaf(leaf)) return 0;
        return leaf.fontSize;
    }

    /** Export one live window without coupling the pane controller to process/CWD discovery. */
    @NonNull
    public TerminalWorkspace.Window snapshotWorkspaceWindow(
        @NonNull Window window, @NonNull WorkspacePaneCapture capture) {
        List<Leaf> panes = allLeavesOf(window);
        int active = Math.max(0, panes.indexOf(window.active));
        List<TerminalWorkspace.FloatingPane> floats = new ArrayList<>();
        for (Leaf leaf : window.floating) {
            RectF frac = leaf.floatFrac != null ? leaf.floatFrac : defaultFloatFrac(0);
            floats.add(new TerminalWorkspace.FloatingPane(capture.capture(leaf.session),
                frac.left, frac.top, frac.width(), frac.height()));
        }
        return new TerminalWorkspace.Window(active,
            snapshotWorkspaceNode(window.root, capture), floats, window.name);
    }

    /**
     * Rebuild a durable pane tree around newly-created sessions. Sessions must be supplied in the
     * same left-to-right leaf order as the definition, floating panes last. No views are rendered
     * until showWindow().
     */
    @NonNull
    public Window newWorkspaceWindow(@NonNull TerminalWorkspace.Window definition,
                                     @NonNull List<TerminalSession> sessions) {
        int[] position = {0};
        Node root = restoreWorkspaceNode(definition.root, sessions, position);
        List<Leaf> floats = new ArrayList<>();
        for (TerminalWorkspace.FloatingPane saved : definition.floats) {
            if (position[0] >= sessions.size())
                throw new IllegalArgumentException("Not enough sessions for workspace pane tree");
            Leaf leaf = new Leaf(sessions.get(position[0]++));
            leaf.floatFrac = sanitizedFloatFrac(saved.left, saved.top, saved.width, saved.height);
            floats.add(leaf);
        }
        if (position[0] != sessions.size())
            throw new IllegalArgumentException("Session count does not match workspace pane tree");
        root.parent = null;
        List<Leaf> panes = leavesOf(root);
        panes.addAll(floats);
        if (panes.isEmpty() || definition.activePane < 0 || definition.activePane >= panes.size())
            throw new IllegalArgumentException("Workspace active pane is outside pane tree");
        Window window = new Window(firstLeaf(root));
        window.root = root;
        window.floating.addAll(floats);
        window.active = panes.get(definition.activePane);
        window.name = TerminalNamePolicy.normalizeWindow(definition.name);
        mWindows.add(window);
        return window;
    }

    @NonNull
    private TerminalWorkspace.Node snapshotWorkspaceNode(
        @NonNull Node node, @NonNull WorkspacePaneCapture capture) {
        if (node instanceof Leaf) return capture.capture(((Leaf) node).session);
        Split split = (Split) node;
        String orientation = split.orientation == LinearLayout.VERTICAL
            ? TerminalWorkspace.Split.VERTICAL : TerminalWorkspace.Split.HORIZONTAL;
        return new TerminalWorkspace.Split(orientation, split.weightA, split.weightB,
            snapshotWorkspaceNode(split.a, capture), snapshotWorkspaceNode(split.b, capture));
    }

    @NonNull
    private Node restoreWorkspaceNode(@NonNull TerminalWorkspace.Node definition,
                                      @NonNull List<TerminalSession> sessions, @NonNull int[] position) {
        if (definition instanceof TerminalWorkspace.Pane) {
            if (position[0] >= sessions.size())
                throw new IllegalArgumentException("Not enough sessions for workspace pane tree");
            return new Leaf(sessions.get(position[0]++));
        }
        TerminalWorkspace.Split saved = (TerminalWorkspace.Split) definition;
        Split split = new Split();
        split.orientation = TerminalWorkspace.Split.VERTICAL.equals(saved.orientation)
            ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
        split.weightA = saved.weightA;
        split.weightB = saved.weightB;
        split.a = restoreWorkspaceNode(saved.a, sessions, position);
        split.b = restoreWorkspaceNode(saved.b, sessions, position);
        split.a.parent = split;
        split.b.parent = split;
        return split;
    }

    /**
     * Serialize one window's complete pane topology. Terminal processes themselves remain owned by
     * {@code TermuxService}; their stable handles reconnect these leaves after activity recreation.
     */
    @NonNull
    public Bundle saveWindow(@NonNull Window window) {
        Bundle state = new Bundle();
        state.putBundle(STATE_WINDOW_ROOT, saveNode(window.root));
        TerminalSession active = windowActiveSession(window);
        if (active != null) state.putString(STATE_WINDOW_ACTIVE, active.mHandle);
        if (window.layoutPolicy != null) state.putString(STATE_WINDOW_LAYOUT, window.layoutPolicy);
        if (window.name != null) state.putString(STATE_WINDOW_NAME, window.name);
        if (!window.floating.isEmpty()) {
            ArrayList<Bundle> floats = new ArrayList<>();
            for (Leaf leaf : window.floating) {
                Bundle floatState = new Bundle();
                floatState.putString(STATE_NODE_SESSION, leaf.session.mHandle);
                RectF frac = leaf.floatFrac != null ? leaf.floatFrac : defaultFloatFrac(0);
                floatState.putFloat(STATE_FLOAT_LEFT, frac.left);
                floatState.putFloat(STATE_FLOAT_TOP, frac.top);
                floatState.putFloat(STATE_FLOAT_WIDTH, frac.width());
                floatState.putFloat(STATE_FLOAT_HEIGHT, frac.height());
                if (leaf.fontSize > 0) floatState.putInt(STATE_NODE_FONT_SIZE, leaf.fontSize);
                floats.add(floatState);
            }
            state.putParcelableArrayList(STATE_WINDOW_FLOATS, floats);
        }
        return state;
    }

    /**
     * Restore a pane window against the still-running service sessions. Missing/finished leaves are
     * pruned; a window is discarded only when none of its terminals still exist.
     */
    @Nullable
    public Window restoreWindow(@Nullable Bundle state,
                                @NonNull Map<String, TerminalSession> sessionsByHandle) {
        if (state == null) return null;
        Node root = restoreNode(state.getBundle(STATE_WINDOW_ROOT), sessionsByHandle);
        List<Leaf> floats = restoreFloatLeaves(
            state.getParcelableArrayList(STATE_WINDOW_FLOATS), root, sessionsByHandle);
        if (root == null && floats.isEmpty()) return null;
        Window window;
        if (root == null) {
            // Every tiled terminal is gone but a float survives: promote it, because a window
            // whose tree is empty cannot be rendered or split into.
            Leaf promoted = floats.remove(0);
            promoted.floatFrac = null;
            window = new Window(promoted);
        } else {
            root.parent = null;
            window = new Window(firstLeaf(root));
            window.root = root;
        }
        window.floating.addAll(floats);
        String activeHandle = state.getString(STATE_WINDOW_ACTIVE);
        TerminalSession activeSession = activeHandle == null ? null : sessionsByHandle.get(activeHandle);
        Leaf active = activeSession == null ? null : findLeafInWindow(window, activeSession);
        if (active != null) window.active = active;
        // Only accept a layout this build still knows; a stale or hand-edited name must leave the
        // window manually managed rather than wedge reapply on every later split.
        String layout = state.getString(STATE_WINDOW_LAYOUT);
        if (layout != null && isKnownLayout(layout)) window.layoutPolicy = layout;
        window.name = TerminalNamePolicy.normalizeWindow(state.getString(STATE_WINDOW_NAME));
        mWindows.add(window);
        return window;
    }

    @NonNull
    private Bundle saveNode(@NonNull Node node) {
        Bundle state = new Bundle();
        if (node instanceof Leaf) {
            state.putInt(STATE_NODE_TYPE, NODE_LEAF);
            state.putString(STATE_NODE_SESSION, ((Leaf) node).session.mHandle);
            if (((Leaf) node).fontSize > 0)
                state.putInt(STATE_NODE_FONT_SIZE, ((Leaf) node).fontSize);
            return state;
        }
        Split split = (Split) node;
        state.putInt(STATE_NODE_TYPE, NODE_SPLIT);
        state.putInt(STATE_NODE_ORIENTATION, split.orientation);
        state.putFloat(STATE_NODE_WEIGHT_A, split.weightA);
        state.putFloat(STATE_NODE_WEIGHT_B, split.weightB);
        state.putBundle(STATE_NODE_A, saveNode(split.a));
        state.putBundle(STATE_NODE_B, saveNode(split.b));
        return state;
    }

    @Nullable
    private Node restoreNode(@Nullable Bundle state,
                             @NonNull Map<String, TerminalSession> sessionsByHandle) {
        if (state == null) return null;
        if (state.getInt(STATE_NODE_TYPE, NODE_LEAF) == NODE_LEAF) {
            TerminalSession session = sessionsByHandle.get(state.getString(STATE_NODE_SESSION));
            // A terminal may appear only once across the restored tree set.
            if (session == null || windowOf(session) != null) return null;
            Leaf leaf = new Leaf(session);
            leaf.fontSize = Math.max(0, state.getInt(STATE_NODE_FONT_SIZE, 0));
            return leaf;
        }
        Node a = restoreNode(state.getBundle(STATE_NODE_A), sessionsByHandle);
        Node b = restoreNode(state.getBundle(STATE_NODE_B), sessionsByHandle);
        if (a == null) return b;
        if (b == null) return a;
        Split split = new Split();
        split.orientation = state.getInt(STATE_NODE_ORIENTATION, LinearLayout.HORIZONTAL);
        split.weightA = state.getFloat(STATE_NODE_WEIGHT_A, 1f);
        split.weightB = state.getFloat(STATE_NODE_WEIGHT_B, 1f);
        if (split.weightA <= 0f || split.weightB <= 0f) {
            split.weightA = 1f;
            split.weightB = 1f;
        }
        split.a = a;
        split.b = b;
        a.parent = split;
        b.parent = split;
        return split;
    }

    /** Rebuild floating leaves from saved state, dropping dead or already-claimed terminals. */
    @NonNull
    private List<Leaf> restoreFloatLeaves(@Nullable ArrayList<Bundle> floatStates,
                                          @Nullable Node root,
                                          @NonNull Map<String, TerminalSession> sessionsByHandle) {
        List<Leaf> floats = new ArrayList<>();
        if (floatStates == null) return floats;
        for (Bundle floatState : floatStates) {
            if (floatState == null) continue;
            TerminalSession session = sessionsByHandle.get(floatState.getString(STATE_NODE_SESSION));
            if (session == null || windowOf(session) != null
                || (root != null && findLeafIn(root, session) != null)) continue;
            Leaf leaf = new Leaf(session);
            leaf.floatFrac = sanitizedFloatFrac(
                floatState.getFloat(STATE_FLOAT_LEFT, Float.NaN),
                floatState.getFloat(STATE_FLOAT_TOP, Float.NaN),
                floatState.getFloat(STATE_FLOAT_WIDTH, Float.NaN),
                floatState.getFloat(STATE_FLOAT_HEIGHT, Float.NaN));
            leaf.fontSize = Math.max(0, floatState.getInt(STATE_NODE_FONT_SIZE, 0));
            floats.add(leaf);
        }
        return floats;
    }

    /** Fractional bounds from persisted values, falling back to the default when corrupt. */
    @NonNull
    private RectF sanitizedFloatFrac(float left, float top, float width, float height) {
        if (!Float.isFinite(left) || !Float.isFinite(top)
            || !Float.isFinite(width) || !Float.isFinite(height)
            || width <= 0f || height <= 0f) return defaultFloatFrac(0);
        return new RectF(left, top, left + width, top + height);
    }

    /** Make {@code w} the visible window and render its pane tree. */
    public void showWindow(Window w) {
        if (w == null) return;
        // A different window is different content: a cursor flight or ghost captured against the
        // outgoing one would finish over panes it never belonged to. The focusSession() that
        // follows a window switch is also refused a flight — its geometry belongs to a tree that
        // has just been rebuilt, and the window arrival already carries the movement.
        if (mActiveWindow != w) {
            mMotionOverlay.clearMotion();
            mSuppressNextCursorFlight = true;
        }
        mActiveWindow = w;
        if (LAYOUT_STACK.equals(w.layoutPolicy) && w.active != null) {
            // Stack lives in the foreground-presentation field, not the tree, so re-entering a
            // stacked window must re-establish it or the policy silently shows every pane.
            mMaximizedLeaf = w.active;
        } else if (mMaximizedLeaf != null && findLeafIn(w.root, mMaximizedLeaf.session) == null) {
            mMaximizedLeaf = null;
        }
        render();
        mHost.onActivePaneChanged();
    }

    @Nullable public Window activeWindow() { return mActiveWindow; }

    /** The window whose tree or floats contain {@code shell}, or null. */
    @Nullable public Window windowOf(@Nullable TerminalSession shell) {
        if (shell == null) return null;
        for (Window w : mWindows)
            for (Leaf leaf : allLeavesOf(w))
                if (leaf.session == shell) return w;
        return null;
    }

    /** All shells of {@code w}: tiled leaves first, then floating panes. */
    public List<TerminalSession> shellsOf(Window w) {
        List<TerminalSession> out = new ArrayList<>();
        if (w != null) for (Leaf leaf : allLeavesOf(w)) out.add(leaf.session);
        return out;
    }

    /** The focused shell of {@code w} (its representative for the drawer). */
    @Nullable public TerminalSession windowActiveSession(@Nullable Window w) {
        return w == null || w.active == null ? null : w.active.session;
    }

    /** Remove a whole window (all panes, floating included). Returns its shells to kill. */
    public List<TerminalSession> removeWindow(Window w) {
        List<TerminalSession> sessions = new ArrayList<>();
        if (w == null) return sessions;
        for (Leaf leaf : allLeavesOf(w)) {
            sessions.add(leaf.session);
            removeFloatContainer(leaf);
            detachPaneView(leaf.session);
        }
        mWindows.remove(w);
        if (mActiveWindow == w) {
            mActiveWindow = null;
            mMaximizedLeaf = null;
        }
        return sessions;
    }

    // --- Queries ---

    @Nullable public TerminalSession getActiveSession() {
        return mActiveWindow != null && mActiveWindow.active != null ? mActiveWindow.active.session : null;
    }

    @Nullable public TerminalView getActivePaneView() {
        TerminalSession s = getActiveSession();
        return s == null ? null : mPaneViews.get(s);
    }

    /** All pane views currently rendered (tiled and floating leaves of the active window). */
    public List<TerminalView> getVisiblePaneViews() {
        List<TerminalView> out = new ArrayList<>();
        if (mActiveWindow == null) return out;
        if (mMaximizedLeaf != null) {
            TerminalView view = mPaneViews.get(mMaximizedLeaf.session);
            if (view != null) out.add(view);
            return out;
        }
        for (Leaf leaf : allLeavesOf(mActiveWindow))
            if (mPaneViews.containsKey(leaf.session)) out.add(mPaneViews.get(leaf.session));
        return out;
    }

    /**
     * Leaves of the active window's tiled tree, floats excluded; a maximized pane counts as one.
     *
     * <p>Deliberately separate from {@link #getVisiblePaneViews()}, which fans font, size and
     * keyboard changes out and must include floats. This is the count that decides who owns the
     * frame line, and a float must not change that: flipping the pane inset resizes the tiled
     * TerminalView, which reflows its PTY and resets the scroll position — the visible jump when the
     * scratchpad appears.
     *
     * <p>Never less than one, which covers the float-only window (root == null) that dropping the
     * last tiled shell can produce.
     */
    public int tiledPaneCount() {
        if (mActiveWindow == null) return 1;
        if (mMaximizedLeaf != null) return 1;
        return Math.max(1, leavesOf(mActiveWindow.root).size());
    }

    /** Re-measure every visible pane once layout settles. Returning from another app can leave the
     *  panes measured against a stale (tiny) host size; posting updateSize after the next layout
     *  pass recomputes rows/cols against the restored full size. */
    public void refreshPaneSizes() {
        for (TerminalView v : getVisiblePaneViews())
            v.post(v::updateSize);
    }

    /** Coalesce a host-surface animation into one final PTY resize. */
    public void beginHostSurfaceResize() {
        mHostSurfaceResizeDepth++;
        if (mHostSurfaceResizeDepth == 1) setAllPaneSizeUpdatesPaused(true, false);
    }

    /** Finish a host resize while keeping prompt/content attached to the bottom edge. */
    public void finishHostSurfaceResizeKeepingBottom() {
        if (mHostSurfaceResizeDepth == 0) return;
        mHostSurfaceResizeDepth--;
        if (mHostSurfaceResizeDepth == 0) setAllPaneSizeUpdatesPaused(false, true);
    }

    /** True while any host surface owns transient terminal geometry. */
    public boolean isHostSurfaceResizeInProgress() { return mHostSurfaceResizeDepth > 0; }

    /** The pane view showing {@code session}, if it is a leaf of the active window. */
    @Nullable public TerminalView getViewForSession(@Nullable TerminalSession session) {
        return session == null ? null : mPaneViews.get(session);
    }

    /** Focus the pane showing {@code session} within the active window. */
    public void focusSession(TerminalSession session) {
        if (mActiveWindow == null) return;
        Leaf leaf = findLeafInWindow(mActiveWindow, session);
        if (leaf != null) {
            TerminalSession previous = mActiveWindow.active != null
                ? mActiveWindow.active.session : null;
            flyCursorBetweenPanes(previous, session);
            mActiveWindow.active = leaf;
            if (mMaximizedLeaf != null) mMaximizedLeaf = leaf;
            bringFloatToFront(mActiveWindow, leaf);
            updateActiveBorders();
            applyFocusGrowth(true);
            focusActiveView();
            mHost.onActivePaneChanged();
        }
    }

    /** Collapse every window back to its focused single pane, returning dropped shells to kill.
     *  Used when compatibility mode turns split panes off. */
    public List<TerminalSession> collapseAll() {
        List<TerminalSession> dropped = new ArrayList<>();
        mMaximizedLeaf = null;
        for (Window w : mWindows) {
            TerminalSession keep = w.active != null ? w.active.session : firstLeaf(w.root).session;
            for (Leaf leaf : allLeavesOf(w)) {
                if (leaf.session == keep) continue;
                dropped.add(leaf.session);
                removeFloatContainer(leaf);
                detachPaneView(leaf.session);
            }
            w.floating.clear();
            Leaf single = new Leaf(keep);
            w.root = single;
            w.active = single;
        }
        render();
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
        return dropped;
    }

    // --- Pane operations (act on the active window) ---

    /**
     * Split the focused pane; new shell fills the new leaf. orientation = LinearLayout.*.
     *
     * @return true when a pane was actually added, so the caller can say so.
     */
    /**
     * Split the focused pane without being told an axis: along its longer side, the dwindle rule,
     * whatever layout the window is under. This is the "new terminal" key — the user asks for a
     * pane, not for a direction — and a retained layout re-tiles afterwards as it always does.
     */
    public boolean splitAuto() {
        if (mActiveWindow == null || mActiveWindow.active == null) return false;
        Leaf anchor = mActiveWindow.active;
        if (mActiveWindow.floating.contains(anchor)) anchor = firstLeaf(mActiveWindow.root);
        return split(dwindleOrientationFor(anchor));
    }

    public boolean split(int orientation) {
        if (mActiveWindow == null || mActiveWindow.active == null) return false;
        Leaf oldLeaf = splitAnchor(mActiveWindow);
        String cwd = oldLeaf.session.getCwd();
        TerminalSession newSession = mHost.createShell(cwd != null ? cwd : mHost.defaultCwd());
        if (newSession == null) return false;
        insertPane(oldLeaf, newSession, orientation, true);
        return true;
    }

    /**
     * Show an already-created shell as a new pane of the active window, split off the focused pane
     * the way {@link #split} would. Under dwindle the axis follows the pane's longer side; otherwise
     * it follows the host's, since the caller (the local API, on behalf of an agent) has no key to
     * express a direction with. {@code focus} false leaves the user's focus where it is, so a pane
     * an agent opens to show its work does not steal the keyboard.
     */
    public boolean addPane(@NonNull TerminalSession session, boolean focus) {
        if (mActiveWindow == null || mActiveWindow.active == null) return false;
        for (Window w : mWindows) for (Leaf leaf : allLeavesOf(w)) if (leaf.session == session) return false;
        Leaf oldLeaf = splitAnchor(mActiveWindow);
        int orientation = isDwindleManaged(mActiveWindow)
            ? dwindleOrientationFor(oldLeaf)
            : DwindleTilingPolicy.splitOrientationFor(mHostView.getWidth(), mHostView.getHeight());
        insertPane(oldLeaf, session, orientation, focus);
        return true;
    }

    /**
     * The tiled leaf a new pane splits off: the focused one, or the tree's first leaf while a float
     * is focused — a floating pane shares no divider, so splitting it would corrupt the tree with a
     * leaf that lives outside it.
     */
    @NonNull
    private Leaf splitAnchor(@NonNull Window window) {
        mMaximizedLeaf = null;
        Leaf oldLeaf = window.active;
        if (window.floating.contains(oldLeaf)) oldLeaf = firstLeaf(window.root);
        return oldLeaf;
    }

    private void insertPane(@NonNull Leaf oldLeaf, @NonNull TerminalSession newSession,
                            int orientation, boolean focus) {
        Leaf newLeaf = new Leaf(newSession);
        newLeaf.fontSize = inheritableFontSize(oldLeaf);
        // Dwindle decides the axis itself: the caller's orientation is whatever key or button was
        // pressed, but under this policy a pane always halves along its longer side.
        if (isDwindleManaged(mActiveWindow)) orientation = dwindleOrientationFor(oldLeaf);
        Split split = new Split();
        split.orientation = orientation;
        split.a = oldLeaf;
        split.b = newLeaf;
        split.parent = oldLeaf.parent;
        oldLeaf.parent = split;
        newLeaf.parent = split;

        if (split.parent == null) {
            mActiveWindow.root = split;
        } else {
            if (split.parent.a == oldLeaf) split.parent.a = split; else split.parent.b = split;
        }
        if (focus) mActiveWindow.active = newLeaf;
        // Captured before the re-render detaches it: the divider reveal needs the pane's surface
        // as it looked while it still owned the whole region the split is about to share.
        Bitmap revealSnapshot = captureSplitRevealSnapshot(oldLeaf.session);
        Rect revealOrigin = revealSnapshot != null
            ? boundsInHost(mPaneFrames.get(oldLeaf.session)) : null;
        // A managed window re-tiles around the new pane instead of keeping the binary split that
        // insertion just produced. This is what makes the layout a policy rather than a one-shot.
        reapplyLayoutPolicy(mActiveWindow);
        render();
        animateSplitReveal(revealSnapshot, revealOrigin, oldLeaf.session, newSession);
        // Splitting resizes the old pane (fewer cols/rows), which reflows its buffer and can
        // leave the view scrolled up (prompt jumps to the top). Once the resize settles, scroll
        // the old pane back to the bottom so its prompt stays where the shell repainted it.
        final TerminalSession reflowed = oldLeaf.session;
        mHostView.postDelayed(() -> {
            TerminalView v = mPaneViews.get(reflowed);
            if (v != null) v.onScreenUpdated();
        }, 250);
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
    }

    /** Drop a finished shell's pane. Returns one of FINISHED_*. */
    public int onSessionFinished(TerminalSession session) {
        Window owner = null;
        Leaf owningLeaf = null;
        for (Window w : mWindows) {
            for (Leaf leaf : allLeavesOf(w))
                if (leaf.session == session) { owner = w; owningLeaf = leaf; break; }
            if (owningLeaf != null) break;
        }
        if (owningLeaf == null) return FINISHED_UNKNOWN;
        if (mMaximizedLeaf == owningLeaf) mMaximizedLeaf = null;
        // Captured before any of the branches below detach the view: the ghost needs the bounds
        // the pane still has.
        if (owner == mActiveWindow) ghostRemovedPane(session);

        if (owner.floating.contains(owningLeaf)) {
            // A float leaves no hole in the tree; drop it and its chrome, then refocus the tree.
            if (isScratchpadLeaf(owningLeaf)) rememberScratchpadFrac(owningLeaf);
            owner.floating.remove(owningLeaf);
            removeFloatContainer(owningLeaf);
            detachPaneView(session);
            if (owner.active == owningLeaf) owner.active = firstLeaf(owner.root);
            if (owner == mActiveWindow) {
                render();
                mHost.onActivePaneChanged();
            }
            mHost.onTreesChanged();
            return FINISHED_PANE;
        }

        Split parent = owningLeaf.parent;
        if (parent == null) {
            if (!owner.floating.isEmpty()) {
                // The last tiled pane died but floats survive: promote the bottom-most float into
                // the tree so the window keeps a renderable, splittable root.
                detachPaneView(session);
                owner.root = null;
                Leaf promoted = owner.floating.get(0);
                if (owner.active == owningLeaf) owner.active = promoted;
                dockLeaf(owner, promoted);
                if (owner == mActiveWindow) {
                    render();
                    mHost.onActivePaneChanged();
                }
                mHost.onTreesChanged();
                return FINISHED_PANE;
            }
            // Window's only pane -> remove the whole window; caller drops it from its session.
            detachPaneView(session);
            mWindows.remove(owner);
            if (mActiveWindow == owner) mActiveWindow = null;
            return FINISHED_WINDOW;
        }
        Node sibling = (parent.a == owningLeaf) ? parent.b : parent.a;
        Split grand = parent.parent;
        sibling.parent = grand;
        if (grand == null) {
            owner.root = sibling;
        } else {
            if (grand.a == parent) grand.a = sibling; else grand.b = sibling;
        }
        detachPaneView(session);
        if (owner.active == owningLeaf) owner.active = firstLeaf(sibling);
        // Re-tile the survivors. Runs for background windows too, so a window that was managed when
        // it lost a pane is still correctly tiled the next time it is shown.
        reapplyLayoutPolicy(owner);
        if (owner == mActiveWindow) {
            render();
            mHost.onActivePaneChanged();
        }
        mHost.onTreesChanged();
        return FINISHED_PANE;
    }

    /**
     * Focus the pane nearest to the active one in the arrow direction (KeyEvent.KEYCODE_DPAD_*).
     * Returns whether focus moved: false with a single pane or no pane in that direction, so the
     * binding can hand the stroke on to the shell instead of swallowing a key that did nothing.
     */
    public boolean focusDirection(int keyCode) {
        TerminalView active = getActivePaneView();
        if (active == null) return false;
        List<TerminalView> views = getVisiblePaneViews();
        if (views.size() < 2) return false;
        int[] a = center(active);
        TerminalView best = null;
        int bestScore = Integer.MAX_VALUE;
        for (TerminalView v : views) {
            if (v == active) continue;
            int[] c = center(v);
            int dx = c[0] - a[0], dy = c[1] - a[1];
            boolean match;
            int primary, secondary;
            switch (keyCode) {
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT:  match = dx < 0; primary = -dx; secondary = Math.abs(dy); break;
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: match = dx > 0; primary = dx;  secondary = Math.abs(dy); break;
                case android.view.KeyEvent.KEYCODE_DPAD_UP:    match = dy < 0; primary = -dy; secondary = Math.abs(dx); break;
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN:  match = dy > 0; primary = dy;  secondary = Math.abs(dx); break;
                default: return false;
            }
            if (!match) continue;
            int score = primary + secondary * 2;
            if (score < bestScore) { bestScore = score; best = v; }
        }
        if (best == null) return false;
        TerminalSession s = best.getCurrentSession();
        if (s == null) return false;
        focusSession(s);
        return true;
    }

    /** Resize the split enclosing the active pane along the arrow axis. */
    public boolean resizeActive(int keyCode) {
        if (mActiveWindow == null || mActiveWindow.active == null) return true;
        boolean horizontalAxis = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        int wantOrientation = horizontalAxis ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
        // Walk up to the nearest ancestor split on the matching axis.
        Node node = mActiveWindow.active;
        Split target = null;
        while (node.parent != null) {
            if (node.parent.orientation == wantOrientation) { target = node.parent; break; }
            node = node.parent;
        }
        if (target == null) return true;
        boolean growA = (node == target.a)
            == (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP);
        float step = 0.12f;
        float total = target.weightA + target.weightB;
        if (growA) { target.weightA += step; target.weightB -= step; }
        else { target.weightA -= step; target.weightB += step; }
        // Clamp so neither collapses.
        float min = total * 0.18f;
        target.weightA = Math.max(min, Math.min(total - min, target.weightA));
        target.weightB = total - target.weightA;
        // A resized divider is hand-shaping a rebuilt layout would throw away, so those go manual.
        // Dwindle never rebuilds — it keeps every ratio — so under it a resize is just a resize.
        if (!isDwindleManaged(mActiveWindow)) clearLayoutPolicy(mActiveWindow);

        // Held-key repeat fires this far faster than render()'s full removeAllViews()/rebuild can
        // keep up with, which is what read as flashing: touch-drag stays smooth because it only
        // ever pokes the two affected LinearLayout weights. Do the same here, and coalesce the PTY
        // resize into one commit after the burst — same debounce shape as the drag's up-event.
        if (mPendingKeyResizeSplit != target) {
            finishPendingKeyResize();
            beginHostSurfaceResize();
            mPendingKeyResizeSplit = target;
        } else if (mFinishKeyResizeRunnable != null) {
            mHostView.removeCallbacks(mFinishKeyResizeRunnable);
        }
        if (!applyWeightsToRenderedLayout(target)) {
            // Nothing live to poke (e.g. first resize right after a structural change) — fall back.
            finishPendingKeyResize();
            render();
            return true;
        }
        mFinishKeyResizeRunnable = this::finishPendingKeyResize;
        mHostView.postDelayed(mFinishKeyResizeRunnable, RESIZE_KEY_FINISH_DELAY_MS);
        return true;
    }

    /** Push a split's current weights into its already-rendered LinearLayout; false if not live. */
    private boolean applyWeightsToRenderedLayout(@NonNull Split split) {
        LinearLayout layout = mSplitLayouts.get(split);
        if (layout == null || layout.getChildCount() < 3) return false;
        LinearLayout.LayoutParams a = (LinearLayout.LayoutParams) layout.getChildAt(0).getLayoutParams();
        LinearLayout.LayoutParams b = (LinearLayout.LayoutParams) layout.getChildAt(2).getLayoutParams();
        a.weight = split.weightA;
        b.weight = split.weightB;
        layout.getChildAt(0).setLayoutParams(a);
        layout.getChildAt(2).setLayoutParams(b);
        return true;
    }

    /** Commit a debounced keybind resize burst: resume PTY sizing and let listeners know. */
    private void finishPendingKeyResize() {
        if (mFinishKeyResizeRunnable != null) {
            mHostView.removeCallbacks(mFinishKeyResizeRunnable);
            mFinishKeyResizeRunnable = null;
        }
        if (mPendingKeyResizeSplit != null) {
            mPendingKeyResizeSplit = null;
            finishHostSurfaceResizeKeepingBottom();
            mHost.onTreesChanged();
        }
    }

    /**
     * Apply one of the six automatic layouts to the active window without restarting any PTY, and
     * retain it as that window's layout policy so later splits and closes keep honouring it.
     */
    public boolean applyLayout(@NonNull String layout) {
        if (mActiveWindow == null || mActiveWindow.active == null) return false;
        if (!transformToLayout(mActiveWindow, layout)) return false;
        mActiveWindow.layoutPolicy = layout;
        render();
        mHost.onTreesChanged();
        return true;
    }

    /**
     * Advance the active window to the next layout in the cycle and retain it. An unmanaged window
     * adopts the first entry, so one press always produces a managed tiling rather than a no-op.
     */
    public boolean nextLayout() {
        if (mActiveWindow == null || mActiveWindow.active == null) return false;
        return applyLayout(nextLayoutAfter(mActiveWindow.layoutPolicy));
    }

    /** True for the six layout names this build accepts as a retained policy. */
    static boolean isKnownLayout(@Nullable String layout) {
        for (String candidate : LAYOUT_CYCLE) if (candidate.equals(layout)) return true;
        return false;
    }

    /** The next cycle entry after {@code current}; the first entry when unmanaged or unrecognized. */
    @NonNull
    static String nextLayoutAfter(@Nullable String current) {
        for (int i = 0; i < LAYOUT_CYCLE.length; i++) {
            if (LAYOUT_CYCLE[i].equals(current)) return LAYOUT_CYCLE[(i + 1) % LAYOUT_CYCLE.length];
        }
        return LAYOUT_CYCLE[0];
    }

    /** The active window's retained layout, or null when it is manually managed. */
    @Nullable
    public String activeLayoutPolicy() {
        return mActiveWindow == null ? null : mActiveWindow.layoutPolicy;
    }

    /** A window's user-given tab name, or null while its tab labels itself from its panes. */
    @Nullable
    public String windowName(@Nullable Window window) {
        return window == null ? null : window.name;
    }

    /**
     * Set or clear a window's tab name. An empty or blank name clears it, which puts the tab back on
     * the derived process/directory label rather than leaving it blank.
     */
    public void setWindowName(@Nullable Window window, @Nullable CharSequence name) {
        if (window == null) return;
        window.name = TerminalNamePolicy.normalizeWindow(name);
    }

    /**
     * Rebuild {@code window}'s tree into {@code layout}. Pure topology: no render, no host
     * notification, no policy bookkeeping, so both the public entry point and the automatic reapply
     * path can share it.
     */
    private boolean transformToLayout(@NonNull Window window, @NonNull String layout) {
        List<Leaf> leaves = leavesOf(window.root);
        if (leaves.isEmpty()) return false;
        if (LAYOUT_STACK.equals(layout)) {
            // Stack reuses the existing temporary-maximize state, which is a property of the
            // foreground presentation rather than the tree, so only the active window can show it.
            if (window == mActiveWindow) mMaximizedLeaf = window.active;
            return true;
        }

        Node root;
        switch (layout) {
            case LAYOUT_DWINDLE:
                // Incremental: once a window is dwindle-managed, split() and close already leave the
                // tree in the shape this policy wants, so a reapply must not rebuild it (that would
                // throw away every divider the user dragged). Only the switch into dwindle lays the
                // existing panes out afresh, as if they had been spawned one after another.
                if (LAYOUT_DWINDLE.equals(window.layoutPolicy)) {
                    if (window == mActiveWindow) mMaximizedLeaf = null;
                    return true;
                }
                root = buildDwindle(leaves);
                break;
            case LAYOUT_GRID:
                root = buildGrid(leaves);
                break;
            case LAYOUT_TALL:
                root = buildMasterLayout(leaves, LinearLayout.HORIZONTAL, LinearLayout.VERTICAL);
                break;
            case LAYOUT_FAT:
                root = buildMasterLayout(leaves, LinearLayout.VERTICAL, LinearLayout.HORIZONTAL);
                break;
            case LAYOUT_HORIZONTAL:
                root = joinEvenly(new ArrayList<Node>(leaves), LinearLayout.HORIZONTAL);
                break;
            case LAYOUT_VERTICAL:
                root = joinEvenly(new ArrayList<Node>(leaves), LinearLayout.VERTICAL);
                break;
            default:
                return false;
        }
        if (window == mActiveWindow) mMaximizedLeaf = null;
        root.parent = null;
        window.root = root;
        return true;
    }

    /**
     * Recompute {@code window} from its retained layout after its pane set changed. No-op for a
     * manually managed window. Callers render and notify; this only reshapes the tree.
     */
    private void reapplyLayoutPolicy(@Nullable Window window) {
        if (window == null || window.layoutPolicy == null) return;
        if (!transformToLayout(window, window.layoutPolicy)) window.layoutPolicy = null;
    }

    /**
     * Drop the retained layout because the user hand-shaped the tree. Keeping it would mean the
     * next split silently discarded that shaping.
     */
    private void clearLayoutPolicy(@Nullable Window window) {
        if (window != null) window.layoutPolicy = null;
    }

    /** Reset every divider in the active window to an equal 1:1 ratio. */
    public boolean equalizeLayout() {
        if (mActiveWindow == null || mActiveWindow.root == null) return false;
        mMaximizedLeaf = null;
        equalizeNode(mActiveWindow.root);
        render();
        mHost.onTreesChanged();
        return true;
    }

    /** Rotate the entire active pane tree geometrically by ninety degrees. */
    public boolean rotateLayout(boolean clockwise) {
        if (mActiveWindow == null || mActiveWindow.root == null) return false;
        mMaximizedLeaf = null;
        clearLayoutPolicy(mActiveWindow);
        rotateNode(mActiveWindow.root, clockwise);
        mActiveWindow.root.parent = null;
        render();
        mHost.onTreesChanged();
        return true;
    }

    /** Extract the focused pane and attach it as the requested outer edge of the window. */
    public boolean moveActivePaneToEdge(@NonNull String edge) {
        if (mActiveWindow == null || mActiveWindow.active == null
            || mActiveWindow.floating.contains(mActiveWindow.active)
            || !(mActiveWindow.root instanceof Split)) return false;
        final int orientation;
        final boolean activeFirst;
        switch (edge) {
            case EDGE_LEFT:
                orientation = LinearLayout.HORIZONTAL;
                activeFirst = true;
                break;
            case EDGE_RIGHT:
                orientation = LinearLayout.HORIZONTAL;
                activeFirst = false;
                break;
            case EDGE_UP:
                orientation = LinearLayout.VERTICAL;
                activeFirst = true;
                break;
            case EDGE_DOWN:
                orientation = LinearLayout.VERTICAL;
                activeFirst = false;
                break;
            default:
                return false;
        }

        mMaximizedLeaf = null;
        clearLayoutPolicy(mActiveWindow);
        Leaf active = mActiveWindow.active;
        Node remainder = detachLeaf(mActiveWindow, active);
        Split root = new Split();
        root.orientation = orientation;
        root.weightA = 1f;
        root.weightB = 1f;
        root.a = activeFirst ? active : remainder;
        root.b = activeFirst ? remainder : active;
        root.a.parent = root;
        root.b.parent = root;
        mActiveWindow.root = root;
        render();
        mHost.onTreesChanged();
        return true;
    }

    // --- Floating panes ---

    /**
     * Detach the focused tiled pane into a freely positioned float above the tree, or split a
     * focused float back into the tree. Returns one of FLOAT_TOGGLE_*. The window's last tiled
     * pane is refused: an empty tree has nothing to render behind the floats and nothing for a
     * later re-dock to split against.
     */
    public int toggleFloatActivePane() {
        if (mActiveWindow == null || mActiveWindow.active == null) return FLOAT_TOGGLE_NONE;
        Window window = mActiveWindow;
        Leaf leaf = window.active;
        if (window.floating.contains(leaf)) {
            dockLeaf(window, leaf);
            render();
            animateDockDepthLoss(leaf.session);
            mHost.onActivePaneChanged();
            mHost.onTreesChanged();
            return FLOAT_TOGGLE_DOCKED;
        }
        if (!(window.root instanceof Split)) return FLOAT_TOGGLE_SINGLE_PANE;
        mMaximizedLeaf = null;
        detachLeaf(window, leaf);
        leaf.parent = null;
        leaf.floatFrac = defaultFloatFrac(window.floating.size());
        window.floating.add(leaf);
        // The survivors re-tile under a retained layout, exactly as if the pane had closed.
        reapplyLayoutPolicy(window);
        render();
        animateFloatDepthGain(leaf);
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
        return FLOAT_TOGGLE_FLOATED;
    }

    /**
     * Undock: depth is part of the transition, so the new float's shadow grows in on the same
     * clock as its FLIP move instead of popping to full elevation on the first frame. The
     * container's base elevation is constant; translationZ starts at its negative so the net
     * lift ramps 0 &#8594; FLOAT_ELEVATION_DP.
     */
    private void animateFloatDepthGain(@NonNull Leaf leaf) {
        if (!arePaneAnimationsEnabled()) return;
        FloatingPaneContainer container = mFloatContainers.get(leaf);
        if (container == null) return;
        container.setTranslationZ(-dp(FLOAT_ELEVATION_DP));
        container.setScaleX(0.99f);
        container.setScaleY(0.99f);
        container.animate()
            .translationZ(0f)
            .scaleX(1f).scaleY(1f)
            .setDuration(Motion.FLOAT_DEPTH_MS)
            .setInterpolator(PaneMotionOverlayView.standardInterpolator())
            .start();
    }

    /** Dock: the pane keeps its float lift while the FLIP move runs, then settles flat. */
    private void animateDockDepthLoss(@NonNull TerminalSession session) {
        if (!arePaneAnimationsEnabled()) return;
        FrameLayout frame = mPaneFrames.get(session);
        if (frame == null || frame.getParent() == null) return;
        frame.setTranslationZ(dp(FLOAT_ELEVATION_DP));
        frame.animate()
            .translationZ(0f)
            .setDuration(Motion.FLOAT_DEPTH_MS)
            .setInterpolator(PaneMotionOverlayView.standardInterpolator())
            .start();
    }

    /** Close a float from its pill: the scratchpad just hides (shell survives), others die. */
    private void closeFloat(@NonNull Leaf leaf) {
        Window window = mActiveWindow;
        if (window == null || !window.floating.contains(leaf)) return;
        if (isScratchpadLeaf(leaf)) hideScratchpad(window, leaf);
        else leaf.session.finishIfRunning();
    }

    /** Send a float back into the tiled tree from its pill. */
    private void dockFloat(@NonNull Leaf leaf) {
        Window window = mActiveWindow;
        if (window == null || !window.floating.contains(leaf)) return;
        dockLeaf(window, leaf);
        render();
        animateDockDepthLoss(leaf.session);
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
    }

    /** Whether the active window's focused pane is currently floating. */
    public boolean isActivePaneFloating() {
        return mActiveWindow != null && mActiveWindow.active != null
            && mActiveWindow.floating.contains(mActiveWindow.active);
    }

    /** How many of the active window's panes are floating. */
    public int activeFloatingPaneCount() {
        return mActiveWindow == null ? 0 : mActiveWindow.floating.size();
    }

    // ------------------------------------------------------------------ scratchpad

    /** Session name that marks the scratchpad shell; it survives hides and activity restarts. */
    public static final String SCRATCHPAD_SESSION_NAME = "scratch";

    /**
     * What the scratchpad shell was called before the name was shortened to fit
     * {@link TerminalNamePolicy#SESSION_MAX_CODE_POINTS}. Existing shells keep this name for the life of
     * the process, so every recognition path has to accept both spellings.
     */
    public static final String LEGACY_SCRATCHPAD_SESSION_NAME = "scratchpad";

    /** toggleScratchpad outcomes. */
    public static final int SCRATCHPAD_TOGGLE_NONE = 0;
    public static final int SCRATCHPAD_TOGGLE_SHOWN = 1;
    public static final int SCRATCHPAD_TOGGLE_HIDDEN = 2;

    /** Centered, top-biased overlay: wide enough for a real shell, clear of the keyboard. */
    private static final float SCRATCHPAD_LEFT_FRAC = 0.07f;
    private static final float SCRATCHPAD_TOP_FRAC = 0.06f;
    private static final float SCRATCHPAD_RIGHT_FRAC = 0.93f;
    private static final float SCRATCHPAD_BOTTOM_FRAC = 0.58f;
    private static final long SCRATCHPAD_SHOW_DURATION_MS = 220L;
    private static final long SCRATCHPAD_HIDE_DURATION_MS = 160L;

    /** Float whose container should play the entry animation on the next render. */
    @Nullable private Leaf mPendingFloatEntryLeaf;
    /** Scratchpad float currently animating out; guards double-hide re-entry. */
    @Nullable private Leaf mHidingScratchpadLeaf;
    /** Last user-shaped scratchpad bounds; the next show reuses them instead of the default. */
    @Nullable private RectF mScratchpadFrac;

    /**
     * tmux-style scratchpad: a dedicated shell named {@link #SCRATCHPAD_SESSION_NAME} shown as a
     * floating pane over the active window and hidden again by the same toggle. Hiding removes
     * the pane but keeps the shell running; the next toggle re-adopts it wherever the user is,
     * so the scratchpad follows across windows and sessions. Appearance and disappearance are
     * animated (a short rise/fade) unless system animations are disabled.
     */
    public int toggleScratchpad() {
        if (mActiveWindow == null) return SCRATCHPAD_TOGGLE_NONE;
        Window window = mActiveWindow;
        Leaf shown = findScratchpadLeaf(window);
        if (shown != null) {
            if (shown == mHidingScratchpadLeaf) return SCRATCHPAD_TOGGLE_HIDDEN;
            hideScratchpad(window, shown);
            return SCRATCHPAD_TOGGLE_HIDDEN;
        }
        TerminalSession session = mHost.findIdleShellByName(SCRATCHPAD_SESSION_NAME);
        // A scratchpad created before the rename still answers to the old name; re-adopt it
        // instead of creating a second one alongside it.
        if (session == null)
            session = mHost.findIdleShellByName(LEGACY_SCRATCHPAD_SESSION_NAME);
        if (session == null)
            session = mHost.createNamedShell(SCRATCHPAD_SESSION_NAME, mHost.defaultCwd());
        if (session == null) return SCRATCHPAD_TOGGLE_NONE;
        Leaf leaf = new Leaf(session);
        leaf.floatFrac = mScratchpadFrac != null ? new RectF(mScratchpadFrac)
            : new RectF(SCRATCHPAD_LEFT_FRAC, SCRATCHPAD_TOP_FRAC,
                SCRATCHPAD_RIGHT_FRAC, SCRATCHPAD_BOTTOM_FRAC);
        // Captured before clearing it: un-maximizing genuinely restructures the surface, so that
        // case still needs a full render.
        boolean wasMaximized = mMaximizedLeaf != null;
        mMaximizedLeaf = null;
        window.floating.add(leaf);
        window.active = leaf;
        mPendingFloatEntryLeaf = leaf;
        if (wasMaximized || !addFloatOnly(leaf)) render();
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
        return SCRATCHPAD_TOGGLE_SHOWN;
    }

    /** Whether the active window currently shows the scratchpad float. */
    public boolean isScratchpadShown() {
        return mActiveWindow != null && findScratchpadLeaf(mActiveWindow) != null;
    }

    /**
     * Persist the remembered scratchpad bounds into {@code state} so a hidden scratchpad keeps
     * its user-shaped size across activity recreation. A shown scratchpad is a float and saves
     * its live bounds through {@link #saveWindow} independently of this.
     */
    public void saveScratchpadState(@NonNull Bundle state) {
        if (mScratchpadFrac == null) return;
        state.putFloat(STATE_SCRATCHPAD_LEFT, mScratchpadFrac.left);
        state.putFloat(STATE_SCRATCHPAD_TOP, mScratchpadFrac.top);
        state.putFloat(STATE_SCRATCHPAD_WIDTH, mScratchpadFrac.width());
        state.putFloat(STATE_SCRATCHPAD_HEIGHT, mScratchpadFrac.height());
    }

    /** Counterpart of {@link #saveScratchpadState}; corrupt values leave the default in place. */
    public void restoreScratchpadState(@Nullable Bundle state) {
        if (state == null || !state.containsKey(STATE_SCRATCHPAD_LEFT)) return;
        float left = state.getFloat(STATE_SCRATCHPAD_LEFT, Float.NaN);
        float top = state.getFloat(STATE_SCRATCHPAD_TOP, Float.NaN);
        float width = state.getFloat(STATE_SCRATCHPAD_WIDTH, Float.NaN);
        float height = state.getFloat(STATE_SCRATCHPAD_HEIGHT, Float.NaN);
        if (!Float.isFinite(left) || !Float.isFinite(top)
            || !Float.isFinite(width) || !Float.isFinite(height)
            || width <= 0f || height <= 0f) return;
        mScratchpadFrac = new RectF(left, top, left + width, top + height);
    }

    /** True for either spelling of the scratchpad shell's name. */
    public static boolean isScratchpadShellName(@Nullable String sessionName) {
        return SCRATCHPAD_SESSION_NAME.equals(sessionName)
            || LEGACY_SCRATCHPAD_SESSION_NAME.equals(sessionName);
    }

    /**
     * Whether a shell with this name should be adopted as a top-level window session. The
     * scratchpad is a floating leaf that follows the user across windows; adopting it mints a
     * bogus session row in the sessions panel and the drawer.
     */
    public static boolean shouldAdoptAsWindowSession(@Nullable String sessionName) {
        return !isScratchpadShellName(sessionName);
    }

    /** True when {@code leaf} hosts the dedicated scratchpad shell. */
    private static boolean isScratchpadLeaf(@NonNull Leaf leaf) {
        return leaf.session != null && isScratchpadShellName(leaf.session.mSessionName);
    }

    @Nullable
    private static Leaf findScratchpadLeaf(@NonNull Window window) {
        for (Leaf leaf : window.floating) {
            if (isScratchpadLeaf(leaf)) return leaf;
        }
        return null;
    }

    /** Remember where the user last shaped the scratchpad so the next show restores it. */
    private void rememberScratchpadFrac(@NonNull Leaf leaf) {
        if (leaf.floatFrac != null) mScratchpadFrac = new RectF(leaf.floatFrac);
    }

    /** Remove the scratchpad float after its exit animation; the shell keeps running. */
    private void hideScratchpad(@NonNull Window window, @NonNull Leaf leaf) {
        rememberScratchpadFrac(leaf);
        Runnable remove = () -> {
            if (mHidingScratchpadLeaf == leaf) mHidingScratchpadLeaf = null;
            if (!window.floating.remove(leaf)) return;
            if (window.active == leaf)
                window.active = window.root != null ? firstLeaf(window.root) : null;
            if (window == mActiveWindow) {
                // removeFloatOnly drops the container itself; only the fallback needs to be told.
                if (!removeFloatOnly(leaf)) {
                    removeFloatContainer(leaf);
                    render();
                }
                mHost.onActivePaneChanged();
            } else {
                removeFloatContainer(leaf);
            }
            mHost.onTreesChanged();
        };
        FloatingPaneContainer container = mFloatContainers.get(leaf);
        if (container == null || !arePaneAnimationsEnabled()) {
            remove.run();
            return;
        }
        mHidingScratchpadLeaf = leaf;
        // Removal runs from a listener rather than withEndAction, because withEndAction is skipped
        // on cancel — and render() tears this container out from under the animation. Left on
        // withEndAction, a render during the hide re-attached a fresh container at full opacity (the
        // scratchpad popped back, then vanished), and a cancel that never ran the action wedged
        // mHidingScratchpadLeaf so toggleScratchpad answered HIDDEN for the rest of the session.
        final Runnable onceOnly = new Runnable() {
            private boolean done;
            @Override public void run() {
                if (done) return;
                done = true;
                if (mHidingScratchpadLeaf == leaf) mHidingScratchpadLeaf = null;
                remove.run();
            }
        };
        container.animate().cancel();
        container.animate()
            .alpha(0f)
            .translationY(dp(10))
            .scaleX(0.97f).scaleY(0.97f)
            .setDuration(SCRATCHPAD_HIDE_DURATION_MS)
            .setInterpolator(PaneMotionOverlayView.standardInterpolator())
            .setListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    onceOnly.run();
                }
            })
            .start();
    }

    /**
     * Reduce-motion check. {@link ValueAnimator#areAnimatorsEnabled()} is the framework's own
     * cached read of the same setting; the previous {@code Settings.Global} lookup hit the content
     * resolver on every focus change, which is every tap on a pane.
     */
    private boolean arePaneAnimationsEnabled() {
        return PaneRim.animationsEnabled();
    }

    /** Plays the float entry animation queued by {@link #toggleScratchpad}. */
    private void maybeAnimateFloatEntry(@NonNull Leaf leaf, @NonNull View container) {
        if (leaf != mPendingFloatEntryLeaf) return;
        mPendingFloatEntryLeaf = null;
        if (!arePaneAnimationsEnabled()) return;
        container.setAlpha(0f);
        container.setTranslationY(dp(12));
        container.setScaleX(0.96f);
        container.setScaleY(0.96f);
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f).scaleY(1f)
            .setDuration(SCRATCHPAD_SHOW_DURATION_MS)
            .setInterpolator(PaneMotionOverlayView.standardInterpolator())
            .start();
    }

    /**
     * Split a float back into the tiled tree: next to the focused tiled leaf when there is one,
     * else next to the tree's first leaf, or as the root of an empty tree. Callers render.
     */
    private void dockLeaf(@NonNull Window window, @NonNull Leaf leaf) {
        window.floating.remove(leaf);
        leaf.floatFrac = null;
        removeFloatContainer(leaf);
        if (window.root == null) {
            leaf.parent = null;
            window.root = leaf;
            window.active = leaf;
            return;
        }
        Leaf anchor = window.active != null && window.active != leaf
            && findLeafIn(window.root, window.active.session) != null
            ? window.active : firstLeaf(window.root);
        Split split = new Split();
        split.orientation = isDwindleManaged(window)
            ? dwindleOrientationFor(anchor)
            : mHostView.getWidth() >= mHostView.getHeight()
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
        split.a = anchor;
        split.b = leaf;
        split.parent = anchor.parent;
        anchor.parent = split;
        leaf.parent = split;
        if (split.parent == null) {
            window.root = split;
        } else {
            if (split.parent.a == anchor) split.parent.a = split; else split.parent.b = split;
        }
        window.active = leaf;
        reapplyLayoutPolicy(window);
    }

    /** Cascaded default bounds so freshly floated panes don't stack exactly on each other. */
    @NonNull
    private static RectF defaultFloatFrac(int index) {
        float offset = .05f * (index % 4);
        float left = .14f + offset;
        float top = .1f + offset;
        return new RectF(left, top, left + .62f, top + .55f);
    }

    /**
     * Clamp fractional float bounds against a host size: no smaller than the minimum pane size, no
     * larger than the host, horizontal overhang allowed as long as {@code minVisiblePx} of the drag
     * handle stays reachable, and the whole float kept above the host's bottom edge.
     *
     * <p>The vertical rule is stricter than the horizontal one on purpose. Sideways, part of the
     * handle row remains grabbable however far the float hangs off, because the handle spans the
     * float's full width. Downward there is nothing to grab, and the float would paint into the dock
     * band: {@code applyTerminalBorderAppearance} clears clipToOutline the moment a second pane
     * appears — which is exactly what showing the scratchpad does — so an overflowing bottom is
     * visible rather than clipped.
     */
    @NonNull
    static RectF clampFloatFractions(@NonNull RectF candidate, float hostWidth, float hostHeight,
                                     float minWidthPx, float minHeightPx, float minVisiblePx) {
        if (hostWidth <= 0f || hostHeight <= 0f) return new RectF(candidate);
        float width = Math.min(1f, Math.max(Math.min(minWidthPx / hostWidth, 1f), candidate.width()));
        float height = Math.min(1f, Math.max(Math.min(minHeightPx / hostHeight, 1f), candidate.height()));
        float minVisibleX = Math.min(minVisiblePx / hostWidth, width);
        float left = Math.max(minVisibleX - width, Math.min(1f - minVisibleX, candidate.left));
        // The handle is the top edge, so the top may never leave the host upward at all — and
        // bottom <= 1 keeps the float clear of the dock, whether the host shrank under it or the
        // user dragged it down by hand.
        float top = Math.max(0f, Math.min(1f - height, candidate.top));
        return new RectF(left, top, left + width, top + height);
    }

    /** Re-clamp {@code leaf}'s fractions against the live host size and lay its container out. */
    private void applyFloatBounds(@NonNull Leaf leaf, @NonNull FloatingPaneContainer container) {
        float hostWidth = mHostView.getWidth();
        float hostHeight = mHostView.getHeight();
        RectF frac = leaf.floatFrac != null ? leaf.floatFrac : defaultFloatFrac(0);
        if (hostWidth > 0f && hostHeight > 0f) {
            frac = clampFloatFractions(frac, hostWidth, hostHeight,
                dp(FLOAT_MIN_WIDTH_DP), dp(FLOAT_MIN_HEIGHT_DP), dp(FLOAT_MIN_VISIBLE_DP));
            // Deliberately NOT written back into leaf.floatFrac. The clamp is a projection of the
            // user's shape onto the current host, not new intent from the user — writing it back is
            // what ratcheted the scratchpad smaller every time the keyboard opened and closed.
            leaf.appliedFloatFrac = new RectF(frac);
        }
        // The frost is positioned in screen space and a float moves by its container's params, so
        // the pane frame's own layout coordinates never change — nothing would tell its glass that
        // it is now sampling a different part of the wallpaper.
        FrameLayout movedFrame = mPaneFrames.get(leaf.session);
        PaneGlassBackdropView movedGlass = movedFrame == null
            ? null : movedFrame.findViewById(R.id.terminal_pane_glass);
        if (movedGlass != null) movedGlass.invalidateGlassPosition();
        FrameLayout.LayoutParams params = container.getLayoutParams() instanceof FrameLayout.LayoutParams
            ? (FrameLayout.LayoutParams) container.getLayoutParams()
            : new FrameLayout.LayoutParams(0, 0);
        int width = Math.round(frac.width() * hostWidth);
        int height = Math.round(frac.height() * hostHeight);
        int leftMargin = Math.round(frac.left * hostWidth);
        int topMargin = Math.round(frac.top * hostHeight);
        // Skip the no-op relayout: repeated host layout passes (keyboard settle, accessory band
        // churn) would otherwise re-trigger a full measure of the float — and a PTY resize under a
        // busy TUI — for bounds that did not actually change.
        if (container.getLayoutParams() == params && params.width == width && params.height == height
            && params.leftMargin == leftMargin && params.topMargin == topMargin) {
            return;
        }
        params.width = width;
        params.height = height;
        params.leftMargin = leftMargin;
        params.topMargin = topMargin;
        container.setLayoutParams(params);
    }

    /** Raise a floating leaf above its sibling floats, both in the view tree and in z-order. */
    private void bringFloatToFront(@NonNull Window window, @NonNull Leaf leaf) {
        if (!window.floating.contains(leaf)) return;
        if (window.floating.indexOf(leaf) != window.floating.size() - 1) {
            window.floating.remove(leaf);
            window.floating.add(leaf);
        }
        FloatingPaneContainer container = mFloatContainers.get(leaf);
        if (container != null) container.bringToFront();
    }

    private void removeFloatContainer(@NonNull Leaf leaf) {
        FloatingPaneContainer container = mFloatContainers.remove(leaf);
        if (container != null && container.getParent() instanceof ViewGroup)
            ((ViewGroup) container.getParent()).removeView(container);
    }

    @NonNull
    private Node buildGrid(@NonNull List<Leaf> leaves) {
        int columns = (int) Math.ceil(Math.sqrt(leaves.size()));
        int rows = (int) Math.ceil(leaves.size() / (double) columns);
        List<Node> rowNodes = new ArrayList<>();
        int position = 0;
        for (int row = 0; row < rows; row++) {
            int remaining = leaves.size() - position;
            int rowsLeft = rows - row;
            int inRow = (int) Math.ceil(remaining / (double) rowsLeft);
            List<Node> rowLeaves = new ArrayList<>();
            for (int i = 0; i < inRow; i++) rowLeaves.add(leaves.get(position++));
            rowNodes.add(joinEvenly(rowLeaves, LinearLayout.HORIZONTAL));
        }
        return joinEvenly(rowNodes, LinearLayout.VERTICAL);
    }

    /**
     * Lay {@code leaves} out as dwindle would have grown them: pane {@code i+1} halves pane
     * {@code i} along the longer side of the region pane {@code i} holds at that point. Regions are
     * simulated from the host's size, so switching a portrait window into dwindle stacks first and
     * a landscape one splits side by side first.
     */
    @NonNull
    private Node buildDwindle(@NonNull List<Leaf> leaves) {
        Leaf first = leaves.get(0);
        first.parent = null;
        if (leaves.size() == 1) return first;
        int[] orientations = DwindleTilingPolicy.spiralOrientations(
            leaves.size(), mHostView.getWidth(), mHostView.getHeight());
        Node root = first;
        Leaf tail = first;
        for (int i = 1; i < leaves.size(); i++) {
            Leaf next = leaves.get(i);
            Split split = new Split();
            split.orientation = orientations[i - 1];
            split.a = tail;
            split.b = next;
            split.parent = tail.parent;
            if (split.parent == null) root = split;
            else if (split.parent.a == tail) split.parent.a = split;
            else split.parent.b = split;
            tail.parent = split;
            next.parent = split;
            tail = next;
        }
        return root;
    }

    private static boolean isDwindleManaged(@Nullable Window window) {
        return window != null && LAYOUT_DWINDLE.equals(window.layoutPolicy);
    }

    /**
     * The axis dwindle splits {@code leaf} on: its longer side as currently laid out, or the
     * host's when the pane has no frame yet (a freshly restored window before its first layout).
     */
    private int dwindleOrientationFor(@NonNull Leaf leaf) {
        Rect bounds = boundsInHost(mPaneFrames.get(leaf.session));
        if (bounds == null || bounds.isEmpty()) {
            return DwindleTilingPolicy.splitOrientationFor(mHostView.getWidth(), mHostView.getHeight());
        }
        return DwindleTilingPolicy.splitOrientationFor(bounds.width(), bounds.height());
    }

    /**
     * Re-tile {@code source} into the half of {@code target} the finger let go over, instead of
     * swapping the two shells. The target is halved along its longer side (the same rule a split
     * follows), so dragging is just another way of growing the dwindle tree. Both panes must be
     * tiled leaves of the active window; the moved pane keeps focus, as in Hyprland.
     */
    private boolean retileDroppedPane(@NonNull Leaf source, @NonNull Leaf target,
                                      @NonNull RectF targetRect, float x, float y) {
        if (mActiveWindow == null || source == target || source.parent == null
            || mActiveWindow.floating.contains(source) || mActiveWindow.floating.contains(target)) {
            return false;
        }
        int side = DwindleTilingPolicy.dropSideFor(targetRect, x, y);
        // Detach first: when source and target are siblings the detach promotes target upward,
        // and the new split must attach where target ends up, not where it was.
        detachLeaf(mActiveWindow, source);
        Split split = new Split();
        split.orientation = DwindleTilingPolicy.orientationForSide(side);
        boolean sourceFirst = DwindleTilingPolicy.droppedFirst(side);
        split.a = sourceFirst ? source : target;
        split.b = sourceFirst ? target : source;
        split.parent = target.parent;
        if (split.parent == null) {
            mActiveWindow.root = split;
        } else if (split.parent.a == target) {
            split.parent.a = split;
        } else {
            split.parent.b = split;
        }
        source.parent = split;
        target.parent = split;
        mActiveWindow.active = source;
        return true;
    }

    @NonNull
    private Node buildMasterLayout(@NonNull List<Leaf> leaves, int masterOrientation,
                                   int remainderOrientation) {
        if (leaves.size() == 1) {
            leaves.get(0).parent = null;
            return leaves.get(0);
        }
        Leaf master = leaves.get(0);
        List<Node> remainderLeaves = new ArrayList<>();
        for (int i = 1; i < leaves.size(); i++) remainderLeaves.add(leaves.get(i));
        Node remainder = joinEvenly(remainderLeaves, remainderOrientation);
        Split root = new Split();
        root.orientation = masterOrientation;
        root.weightA = 1f;
        root.weightB = 1f;
        root.a = master;
        root.b = remainder;
        master.parent = root;
        remainder.parent = root;
        return root;
    }

    /** Join ordered groups so each receives the same final width/height despite a chain tree. */
    @NonNull
    private Node joinEvenly(@NonNull List<Node> nodes, int orientation) {
        if (nodes.isEmpty()) throw new IllegalArgumentException("Cannot layout zero panes");
        Node root = nodes.get(0);
        root.parent = null;
        int groups = 1;
        for (int i = 1; i < nodes.size(); i++) {
            Node next = nodes.get(i);
            Split split = new Split();
            split.orientation = orientation;
            split.weightA = groups;
            split.weightB = 1f;
            split.a = root;
            split.b = next;
            root.parent = split;
            next.parent = split;
            root = split;
            groups++;
        }
        return root;
    }

    private void equalizeNode(@NonNull Node node) {
        if (!(node instanceof Split)) return;
        Split split = (Split) node;
        split.weightA = 1f;
        split.weightB = 1f;
        equalizeNode(split.a);
        equalizeNode(split.b);
    }

    private void rotateNode(@NonNull Node node, boolean clockwise) {
        if (!(node instanceof Split)) return;
        Split split = (Split) node;
        rotateNode(split.a, clockwise);
        rotateNode(split.b, clockwise);
        boolean wasHorizontal = split.orientation == LinearLayout.HORIZONTAL;
        split.orientation = wasHorizontal ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
        boolean swap = (clockwise && !wasHorizontal) || (!clockwise && wasHorizontal);
        if (swap) {
            Node child = split.a;
            split.a = split.b;
            split.b = child;
            float weight = split.weightA;
            split.weightA = split.weightB;
            split.weightB = weight;
        }
        split.a.parent = split;
        split.b.parent = split;
    }

    /** Remove a leaf but keep every remaining node and session alive. */
    @NonNull
    private Node detachLeaf(@NonNull Window window, @NonNull Leaf leaf) {
        Split parent = leaf.parent;
        if (parent == null) throw new IllegalArgumentException("Cannot detach the only pane");
        Node sibling = parent.a == leaf ? parent.b : parent.a;
        Split grand = parent.parent;
        sibling.parent = grand;
        if (grand == null) {
            window.root = sibling;
        } else if (grand.a == parent) {
            grand.a = sibling;
        } else {
            grand.b = sibling;
        }
        leaf.parent = null;
        return window.root;
    }

    // --- Rendering ---

    private void render() {
        // A re-render invalidates the geometry a running divider reveal was easing toward.
        cancelSplitReveal();
        // Weights first, so the tree is built already grown; the pane-move animation carries the
        // frames from where they were.
        if (mFocusGrowAnimator != null) mFocusGrowAnimator.cancel();
        applyFocusGrowth(false);
        captureMoveOrigins();
        mHostView.removeAllViews();
        mSplitLayouts.clear();
        mFloatContainers.clear();
        // Whatever the scratchpad's hide animation was holding has just been detached; its guard
        // must not outlive it or the scratchpad can never be shown again.
        mHidingScratchpadLeaf = null;
        if (mActiveWindow == null) {
            mHost.onPanesRendered();
            return;
        }
        View built = mMaximizedLeaf != null
            ? paneFrameFor(mMaximizedLeaf.session) : buildView(mActiveWindow.root);
        mHostView.addView(built, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (mInteractionOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) mInteractionOverlay.getParent()).removeView(mInteractionOverlay);
        }
        int paneCount = leavesOf(mActiveWindow.root).size();
        if (shouldShowInteractionOverlay(paneCount, mMaximizedLeaf != null)) {
            mHostView.addView(mInteractionOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mInteractionOverlay.onTreeRendered();
        }
        // Floats are added last so they sit above both the tree and the interaction overlay;
        // list order is z-order. A maximized pane owns the whole surface, floats included.
        if (mMaximizedLeaf == null) {
            for (Leaf leaf : mActiveWindow.floating) attachFloatContainer(leaf);
        }
        // Last child: a ghost or a cursor flight has to draw over the panes and the floats both.
        if (mMotionOverlay.getParent() instanceof ViewGroup)
            ((ViewGroup) mMotionOverlay.getParent()).removeView(mMotionOverlay);
        mHostView.addView(mMotionOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateActiveBorders();
        focusActiveView();
        animateMoveFromOrigins();
        mHost.onPanesRendered();
    }

    /** Build, place and (if pending) animate one float's container. One path, two callers. */
    private void attachFloatContainer(@NonNull Leaf leaf) {
        FloatingPaneContainer container = new FloatingPaneContainer(leaf);
        mFloatContainers.put(leaf, container);
        mHostView.addView(container, new FrameLayout.LayoutParams(0, 0));
        // render() puts the motion overlay last on purpose; a float attached afterwards (the cheap
        // addFloatOnly path) would otherwise draw over the ghosts and smears it must sit under.
        if (mMotionOverlay.getParent() == mHostView) mHostView.bringChildToFront(mMotionOverlay);
        applyFloatBounds(leaf, container);
        maybeAnimateFloatEntry(leaf, container);
    }

    /**
     * Add one float without rebuilding the tiled tree, so the pane behind it is never detached and
     * re-attached. Falls back to {@link #render()} whenever the cheap path does not apply.
     *
     * <p>Skipping {@code mHost.onPanesRendered()} is safe: that callback only re-applies the terminal
     * border appearance, and a float no longer changes who owns the frame line.
     */
    private boolean addFloatOnly(@NonNull Leaf leaf) {
        if (mActiveWindow == null || mActiveWindow.root == null || mMaximizedLeaf != null
            || mHostView.getChildCount() == 0) return false;
        attachFloatContainer(leaf);
        updateActiveBorders();
        focusActiveView();
        return true;
    }

    /** Counterpart of {@link #addFloatOnly}: drop one float's container and nothing else. */
    private boolean removeFloatOnly(@NonNull Leaf leaf) {
        if (mActiveWindow == null || mActiveWindow.root == null || mMaximizedLeaf != null
            || mHostView.getChildCount() == 0) return false;
        removeFloatContainer(leaf);
        updateActiveBorders();
        focusActiveView();
        return true;
    }

    // --- Movement: niri's render-offset model, as FLIP ---

    /** How long a pane takes to slide from where it was to where the layout put it. */
    private static final long PANE_MOVE_MS = 340L;

    /** Screen bounds of every live pane frame, captured just before a re-render replaces them. */
    private final Map<TerminalSession, Rect> mMoveOrigins = new HashMap<>();

    /**
     * Remember where every pane is, so the next layout can be animated from here.
     *
     * <p>niri's model: layout is always final, and motion is a render offset that decays to zero —
     * never a series of layouts. On Android that is FLIP: read the old bounds, let layout happen
     * once, then set {@code translationX/Y} to the difference and animate it away. Because layout
     * runs exactly once, a moving pane reflows its PTY once, which is the whole reason not to
     * animate this by re-laying out repeatedly.
     */
    private void captureMoveOrigins() {
        mMoveOrigins.clear();
        if (!arePaneAnimationsEnabled()) return;
        int[] location = new int[2];
        for (Map.Entry<TerminalSession, PaneContentFrame> entry : mPaneFrames.entrySet()) {
            FrameLayout frame = entry.getValue();
            if (!canAnimateView(frame)) continue;
            frame.getLocationOnScreen(location);
            mMoveOrigins.put(entry.getKey(), new Rect(location[0], location[1],
                location[0] + frame.getWidth(), location[1] + frame.getHeight()));
        }
    }

    /**
     * Slide each surviving pane from where it used to be to where it now is.
     *
     * <p>Position only. A size change would need niri's two-texture crossfade, and this code has
     * already — correctly — refused to hold full-pane bitmaps, so a pane that resizes simply
     * arrives at its new size while it travels.
     */
    private void animateMoveFromOrigins() {
        if (mMoveOrigins.isEmpty()) return;
        final Map<TerminalSession, Rect> origins = new HashMap<>(mMoveOrigins);
        mMoveOrigins.clear();
        OneShotPreDrawListener.add(mHostView, () -> {
            int[] location = new int[2];
            for (Map.Entry<TerminalSession, Rect> entry : origins.entrySet()) {
                FrameLayout frame = mPaneFrames.get(entry.getKey());
                if (!canAnimateView(frame)) continue;
                // The plank owns this frame's translation while a finger is on it; two owners of
                // one property is a fight the user can see.
                if (mPressedPlank != null && mPanePlanks.get(frame) == mPressedPlank) continue;
                frame.getLocationOnScreen(location);
                float dx = entry.getValue().left - location[0];
                float dy = entry.getValue().top - location[1];
                // Sub-pixel moves are layout noise, not movement. niri refuses anything under
                // 10px for the same reason.
                if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) continue;
                frame.animate().cancel();
                frame.setTranslationX(dx);
                frame.setTranslationY(dy);
                frame.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(PANE_MOVE_MS)
                    .setInterpolator(PaneMotionOverlayView.standardInterpolator())
                    .start();
            }
        });
    }

    // --- Pane appearance / disappearance / cursor travel ---

    /** Hyprland-ish open: the pane pops in from slightly small rather than blinking into place. */
    private static final long PANE_ENTER_MS = 280L;
    private static final float PANE_ENTER_SCALE = 0.92f;

    /**
     * Play the entry animation on one pane frame. Runs after layout, because a freshly rendered
     * frame has no size yet and a scale about an unmeasured centre lands in the wrong place.
     */
    private void animatePaneEntry(@Nullable TerminalSession session) {
        if (session == null || !arePaneAnimationsEnabled()) return;
        FrameLayout frame = mPaneFrames.get(session);
        if (frame == null) return;
        // The start state is set now, not inside the posted runnable. render() has only just added
        // the frame and asked for a traversal, so a post runs BEFORE layout: the old version read a
        // width of 0 and dropped the animation, and in the other ordering it flashed the pane at
        // full opacity for a frame first. The float path (attachFloatContainer) always did this
        // correctly; this is the same discipline.
        frame.setAlpha(0f);
        frame.setScaleX(PANE_ENTER_SCALE);
        frame.setScaleY(PANE_ENTER_SCALE);
        OneShotPreDrawListener.add(frame, () -> {
            frame.animate().cancel();
            frame.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(PANE_ENTER_MS)
                .setInterpolator(PaneMotionOverlayView.standardInterpolator())
                .withEndAction(() -> resetFrameTransform(frame))
                .start();
        });
    }

    // --- Split reveal: the divider slides from the edge to its resting place ---

    /**
     * How long the divider takes to sweep in. Long enough to read as geometry forming, short
     * enough that the ~300 ms shell start after it feels like part of the same gesture.
     */
    private static final long SPLIT_REVEAL_MS = 280L;
    /** Bounds agreement below this is layout noise, not a moved edge. */
    private static final int SPLIT_REVEAL_SLACK_PX = 2;

    @Nullable private ValueAnimator mSplitRevealAnimator;

    /**
     * The pre-split pane, frozen as a bitmap and clipped away as the divider sweeps: the live
     * layout underneath is already final (one layout pass, one PTY reflow), and the reveal is a
     * pure overlay — exactly the window-switch snapshot discipline, applied to a split.
     */
    private static final class SplitRevealDrawable extends Drawable {
        final Bitmap bitmap;
        final Rect clip = new Rect();

        SplitRevealDrawable(@NonNull Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.clipRect(clip);
            canvas.drawBitmap(bitmap, getBounds().left, getBounds().top, null);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** The old pane's surface as drawn right now, or null when the reveal cannot run. */
    @Nullable
    private Bitmap captureSplitRevealSnapshot(@NonNull TerminalSession session) {
        if (!arePaneAnimationsEnabled()) return null;
        FrameLayout frame = mPaneFrames.get(session);
        if (frame == null || !frame.isLaidOut()
            || frame.getWidth() <= 0 || frame.getHeight() <= 0) return null;
        try {
            Bitmap snapshot = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(),
                Bitmap.Config.ARGB_8888);
            frame.draw(new Canvas(snapshot));
            return snapshot;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** A view's bounds in the pane host's coordinates, translations included. */
    @Nullable
    private Rect boundsInHost(@Nullable View view) {
        if (view == null) return null;
        int[] viewLocation = new int[2];
        int[] hostLocation = new int[2];
        view.getLocationOnScreen(viewLocation);
        mHostView.getLocationOnScreen(hostLocation);
        int left = viewLocation[0] - hostLocation[0];
        int top = viewLocation[1] - hostLocation[1];
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }

    /**
     * Sweep the divider in over the finished layout: the pre-split snapshot covers the whole
     * region the old pane held, and its clip eases toward the old pane's final bounds, revealing
     * the new pane from the shared edge outward. Falls back to the plain entry pop whenever the
     * layout policy re-tiled the old pane somewhere the sweep cannot explain.
     */
    private void animateSplitReveal(@Nullable Bitmap snapshot, @Nullable Rect origin,
                                    @NonNull TerminalSession oldSession,
                                    @Nullable TerminalSession newSession) {
        if (snapshot == null || origin == null) {
            animatePaneEntry(newSession);
            return;
        }
        OneShotPreDrawListener.add(mHostView, () -> {
            Rect settled = boundsInHost(mPaneFrames.get(oldSession));
            int movedEdge = splitRevealMovedEdge(origin, settled);
            if (settled == null || movedEdge == 0) {
                snapshot.recycle();
                animatePaneEntry(newSession);
                return;
            }
            SplitRevealDrawable reveal = new SplitRevealDrawable(snapshot);
            reveal.setBounds(origin);
            reveal.clip.set(origin);
            mHostView.getOverlay().add(reveal);
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(SPLIT_REVEAL_MS);
            animator.setInterpolator(PaneMotionOverlayView.standardInterpolator());
            animator.addUpdateListener(a -> {
                float fraction = (float) a.getAnimatedValue();
                reveal.clip.set(origin);
                if (movedEdge == Gravity.LEFT)
                    reveal.clip.left = Math.round(origin.left + (settled.left - origin.left) * fraction);
                else if (movedEdge == Gravity.TOP)
                    reveal.clip.top = Math.round(origin.top + (settled.top - origin.top) * fraction);
                else if (movedEdge == Gravity.RIGHT)
                    reveal.clip.right = Math.round(origin.right + (settled.right - origin.right) * fraction);
                else
                    reveal.clip.bottom = Math.round(origin.bottom + (settled.bottom - origin.bottom) * fraction);
                reveal.invalidateSelf();
            });
            // A listener, not withEndAction: cleanup has to run on cancel too, or a cancelled
            // reveal leaves a frozen pane painted over the live layout forever.
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    mHostView.getOverlay().remove(reveal);
                    snapshot.recycle();
                    if (mSplitRevealAnimator == a) mSplitRevealAnimator = null;
                }
            });
            mSplitRevealAnimator = animator;
            animator.start();
        });
    }

    /**
     * Which single edge of the old pane's region moved inward, as a {@link Gravity} constant, or
     * 0 when the settled bounds are not "the origin minus one edge" — the only shape a divider
     * sweep can honestly narrate.
     */
    private static int splitRevealMovedEdge(@NonNull Rect origin, @Nullable Rect settled) {
        if (settled == null || settled.isEmpty()) return 0;
        boolean leftMoved = settled.left - origin.left > SPLIT_REVEAL_SLACK_PX;
        boolean topMoved = settled.top - origin.top > SPLIT_REVEAL_SLACK_PX;
        boolean rightMoved = origin.right - settled.right > SPLIT_REVEAL_SLACK_PX;
        boolean bottomMoved = origin.bottom - settled.bottom > SPLIT_REVEAL_SLACK_PX;
        // The settled pane must still sit inside where it came from; anything else was a re-tile.
        if (settled.left < origin.left - SPLIT_REVEAL_SLACK_PX
            || settled.top < origin.top - SPLIT_REVEAL_SLACK_PX
            || settled.right > origin.right + SPLIT_REVEAL_SLACK_PX
            || settled.bottom > origin.bottom + SPLIT_REVEAL_SLACK_PX) return 0;
        int moved = (leftMoved ? 1 : 0) + (topMoved ? 1 : 0)
            + (rightMoved ? 1 : 0) + (bottomMoved ? 1 : 0);
        if (moved != 1) return 0;
        if (leftMoved) return Gravity.LEFT;
        if (topMoved) return Gravity.TOP;
        if (rightMoved) return Gravity.RIGHT;
        return Gravity.BOTTOM;
    }

    private void cancelSplitReveal() {
        if (mSplitRevealAnimator != null) {
            ValueAnimator animator = mSplitRevealAnimator;
            mSplitRevealAnimator = null;
            animator.cancel();
        }
    }

    /**
     * Neutralise a frame's animated state. Frames are cached per session and re-attached by later
     * renders, so an interrupted entry would otherwise hand back a permanently dimmed pane; this
     * runs where frames are handed out, not only when an animation completes on its own.
     */
    private static void resetFrameTransform(@NonNull View frame) {
        frame.animate().cancel();
        frame.setAlpha(1f);
        frame.setScaleX(1f);
        frame.setScaleY(1f);
        frame.setTranslationX(0f);
        frame.setTranslationY(0f);
        frame.setTranslationZ(0f);
    }

    /**
     * Leave a shrinking ghost where a pane was, before the tree closes over its space.
     *
     * <p>Captured as a rect rather than as a bitmap of the pane: a full-pane bitmap is several
     * megabytes on a phone panel, allocated at the one moment the layout is already busy, and at
     * this duration the frame's own outline is what the eye follows anyway.
     */
    private void ghostRemovedPane(@Nullable TerminalSession session) {
        if (session == null || !arePaneAnimationsEnabled()) return;
        FrameLayout frame = mPaneFrames.get(session);
        // A pane nobody could see leaves no hole to fill: in stack or maximized layout every
        // unfocused pane is off screen, and ghosting one paints a shrinking rectangle over content
        // that never held it. niri skips closing an inactive tab for exactly this reason.
        if (!canAnimateView(frame) || !canAnimateView(mMotionOverlay)) return;
        int[] frameLocation = new int[2];
        int[] overlayLocation = new int[2];
        frame.getLocationOnScreen(frameLocation);
        mMotionOverlay.getLocationOnScreen(overlayLocation);
        RectF bounds = new RectF(frameLocation[0] - overlayLocation[0],
            frameLocation[1] - overlayLocation[1],
            frameLocation[0] - overlayLocation[0] + frame.getWidth(),
            frameLocation[1] - overlayLocation[1] + frame.getHeight());
        int fill = paneGlassActive() && mSurfaceStyle != null
            ? mSurfaceStyle.paneGlassTintColor() : 0;
        int rim = MaterialColors.getColor(mHostView.getContext(),
            com.google.android.material.R.attr.colorOutlineVariant,
            ContextCompat.getColor(mHostView.getContext(), R.color.termux_outline_variant));
        mMotionOverlay.ghostPane(bounds, paneGlassRadiusPx(), fill, rim);
    }

    /**
     * The cursor's flight between panes, the way neovide and kitty smear a cursor across a jump.
     * Only for a real change of pane: a focus call that lands on the pane already focused, or on a
     * pane whose cursor is scrolled out of view, has nothing to travel between.
     */
    private void flyCursorBetweenPanes(@Nullable TerminalSession from, @Nullable TerminalSession to) {
        if (from == null || to == null || from == to) return;
        if (mSuppressNextCursorFlight) {
            mSuppressNextCursorFlight = false;
            return;
        }
        if (!arePaneAnimationsEnabled() || !canAnimateView(mMotionOverlay)) return;
        TerminalView source = mPaneViews.get(from);
        TerminalView target = mPaneViews.get(to);
        RectF fromRect = cursorRectInOverlay(source);
        RectF toRect = cursorRectInOverlay(target);
        if (fromRect == null || toRect == null) return;
        // Both ends go dark for the flight: the smear IS the cursor while it travels, and a smear
        // drawn between two cursors that stay lit reads as decoration flying between them rather
        // than as one cursor moving. kitty masks the live cursor cell out of its trail to the same
        // end. updateActiveBorders() restores the focused pane when the smear settles.
        source.setCursorSuppressed(true);
        target.setCursorSuppressed(true);
        mMotionOverlay.flyCursor(fromRect, toRect, cursorColorOf(target),
            target.getTerminalCellWidthPixels(), target.getTerminalCellHeightPixels(),
            this::applyCursorOwnership);
    }

    /**
     * Only the focused pane paints a cursor.
     *
     * <p>{@code TerminalEmulator.shouldCursorBeVisible} has no focus term, so without this every
     * visible pane carries its own lit block and nothing on screen says which one the keyboard is
     * talking to. Suppression is per view, so two panes showing the same session still resolve
     * independently.
     */
    private void applyCursorOwnership() {
        TerminalSession active = getActiveSession();
        for (Map.Entry<TerminalSession, TerminalView> entry : mPaneViews.entrySet()) {
            TerminalView view = entry.getValue();
            if (view == null) continue;
            view.setCursorSuppressed(entry.getKey() != active);
        }
    }

    /**
     * Whether this view may take part in an animation at all: attached, on screen, and measured.
     *
     * <p>The size test alone is the trap — a detached pane keeps its last measured width and
     * height, so it passes, while {@code getLocationOnScreen} returns {@code 0,0} and every rect
     * derived from it lands at the top-left corner of the screen. Maximized and stack layouts keep
     * every unfocused pane detached, so that was not a corner case.
     */
    private boolean canAnimateView(@Nullable View view) {
        return view != null && PaneMotionMath.canAnimate(view.isAttachedToWindow(), view.isShown(),
            view.getWidth(), view.getHeight());
    }

    /** One pane's cursor cell, in the motion overlay's coordinates, or null when it is not visible. */
    @Nullable
    private RectF cursorRectInOverlay(@Nullable TerminalView view) {
        if (!canAnimateView(view)) return null;
        TerminalEmulator emulator = view.mEmulator;
        if (emulator == null) return null;
        int row = emulator.getCursorRow() - view.getTopRow();
        if (row < 0 || row >= emulator.mRows) return null;
        float cellWidth = view.getTerminalCellWidthPixels();
        float cellHeight = view.getTerminalCellHeightPixels();
        if (cellWidth <= 0f || cellHeight <= 0f) return null;
        int[] viewLocation = new int[2];
        int[] overlayLocation = new int[2];
        view.getLocationOnScreen(viewLocation);
        mMotionOverlay.getLocationOnScreen(overlayLocation);
        float left = viewLocation[0] - overlayLocation[0] + view.getPointX(emulator.getCursorCol());
        float top = viewLocation[1] - overlayLocation[1] + row * cellHeight;
        return new RectF(left, top, left + cellWidth, top + cellHeight);
    }

    private int cursorColorOf(@Nullable TerminalView view) {
        TerminalEmulator emulator = view == null ? null : view.mEmulator;
        if (emulator != null) {
            int color = emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR];
            if (android.graphics.Color.alpha(color) > 0) return color;
        }
        return MaterialColors.getColor(mHostView.getContext(),
            com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(mHostView.getContext(), R.color.termux_primary));
    }

    /**
     * Per-pane glass physics. Each pane frame gets its own spring rig, so touching one slab tips
     * that slab — frost, text and rim together, since the transform sits on their common frame —
     * and the others stay put. One shared rig would tilt the whole split as a sheet, which is the
     * reading the separate slabs exist to break.
     *
     * <p>Tuned far gentler than the dock's: a pane is tall, and the dock's 3° on this surface reads
     * as the screen keeling over.
     */
    private static final float PANE_TILT_DEG = 1.1f;
    // The slide has to stay inside the pane gap: at the dock's 2dp a pressed slab crossed the
    // default 1dp gap and rode over its neighbour's rim, which read as a detached border.
    private static final float PANE_SHIFT_DP = 0.5f;
    private static final float PANE_PRESS_DIP = 0.006f;
    /**
     * How far the press dip may pull a pane's edge in, whatever the pane's size. A pane is most of
     * the screen, so the dock's proportional dip moved each edge more than a dozen pixels here: the
     * slab shrank away from the terminal's own edge and its lit rim went with it, which read as the
     * border detaching from the terminal rather than as the terminal being pressed.
     */
    private static final float PANE_DIP_TRAVEL_DP = 1f;
    /**
     * Room a pressed pane needs outside its own bounds: the plank's slide plus the perspective
     * growth of whichever edge tilts toward the finger.
     *
     * <p>The pane host clips its children to keep a dragged float inside the terminal, and its
     * bounds are the pane area — margin and frame inset already taken off. So the very travel this
     * gesture is made of was being clipped away: the pressed slab, and with it the lit rim that is
     * the pane's edge, was cut off abruptly along the margin the moment it moved. The clip needs
     * this much slack to contain a float and still let a press happen.
     */
    public static final float PANE_PRESS_SLACK_DP = 4f;

    private final Map<FrameLayout, DockPlankController> mPanePlanks = new HashMap<>();
    @Nullable private DockPlankController mPressedPlank;
    /** Set by a window switch so the focus change it triggers does not smear across the rebuild. */
    private boolean mSuppressNextCursorFlight;
    private float mPlankLeft;
    private float mPlankTop;
    private float mPlankWidth;
    private float mPlankHeight;

    /**
     * Feed one touch to whichever pane it landed on. Observes only — the event is never consumed,
     * so terminal scrolling, selection and the float drags all still see it.
     */
    public void dispatchPaneGlassTouch(@NonNull MotionEvent ev, boolean reducedMotion) {
        if (!paneGlassActive()) {
            releasePressedPlank();
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                releasePressedPlank();
                FrameLayout frame = paneFrameAt(ev.getRawX(), ev.getRawY());
                if (frame == null) return;
                DockPlankController plank = mPanePlanks.get(frame);
                if (plank == null) {
                    plank = new DockPlankController(frame, null, null,
                        PANE_TILT_DEG, PANE_SHIFT_DP, PANE_PRESS_DIP);
                    plank.setHingeMode(false);
                    plank.setMotionEnabled(true);
                    plank.setMaxDipTravelDp(PANE_DIP_TRAVEL_DP);
                    mPanePlanks.put(frame, plank);
                }
                plank.setReducedMotion(reducedMotion);
                plank.setEnabled(true);
                mPressedPlank = plank;
                plank.onPointerDown((ev.getRawX() - mPlankLeft) / mPlankWidth,
                    (ev.getRawY() - mPlankTop) / mPlankHeight);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (mPressedPlank != null && mPlankWidth > 0f && mPlankHeight > 0f) {
                    mPressedPlank.onPointerMove((ev.getRawX() - mPlankLeft) / mPlankWidth,
                        (ev.getRawY() - mPlankTop) / mPlankHeight);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releasePressedPlank();
                break;
            default:
                break;
        }
    }

    /** Let go of whatever slab is pressed, for a gesture another surface has taken over. */
    public void cancelPaneGlassTouch() {
        releasePressedPlank();
    }

    private void releasePressedPlank() {
        if (mPressedPlank == null) return;
        mPressedPlank.onPointerUp();
        mPressedPlank = null;
    }

    /**
     * The visible pane frame under a screen point. Later frames win, which puts the topmost float
     * ahead of the tiled pane it covers — the same z-order the touch itself follows.
     */
    @Nullable
    private FrameLayout paneFrameAt(float rawX, float rawY) {
        FrameLayout hit = null;
        int[] location = new int[2];
        for (FrameLayout frame : mPaneFrames.values()) {
            if (frame.getVisibility() != View.VISIBLE || frame.getWindowToken() == null
                || frame.getWidth() <= 0 || frame.getHeight() <= 0) continue;
            frame.getLocationOnScreen(location);
            if (rawX < location[0] || rawX > location[0] + frame.getWidth()
                || rawY < location[1] || rawY > location[1] + frame.getHeight()) continue;
            hit = frame;
            mPlankLeft = location[0];
            mPlankTop = location[1];
            mPlankWidth = frame.getWidth();
            mPlankHeight = frame.getHeight();
        }
        return hit;
    }

    /** Drop the rig of a pane that is going away, and neutralise a slab left mid-tilt. */
    private void releasePanePlank(@Nullable FrameLayout frame) {
        if (frame == null) return;
        DockPlankController plank = mPanePlanks.remove(frame);
        if (plank == null) return;
        if (mPressedPlank == plank) mPressedPlank = null;
        plank.setEnabled(false);
        plank.reset();
    }

    /**
     * Attach the activity's glass supplier. Panes are re-dressed immediately, so a slider drag in
     * the surface editor lands on every pane without rebuilding the tree.
     */
    public void setSurfaceStyle(@Nullable PaneSurfaceStyle style) {
        mSurfaceStyle = style;
        applyPaneGlass();
    }

    /**
     * Re-lay the tiled tree, for a change only layout can express — the inner padding between
     * panes. The pane frames and their shells are reused, so nothing reflows a PTY that did not
     * change size.
     */
    public void refreshPaneLayout() {
        if (mActiveWindow == null) return;
        render();
    }

    /** The configured gap between tiled panes, in dp. */
    private int paneGapDp() {
        return PaneGlass.gapDp(mSurfaceStyle, DIVIDER_DP);
    }

    private float paneGlassRadiusPx() {
        return PaneGlass.radiusPx(mSurfaceStyle,
            mHostView.getResources().getDisplayMetrics().density);
    }

    private boolean paneGlassActive() {
        return PaneGlass.isActive(mSurfaceStyle);
    }

    /**
     * Dress (or undress) every live pane frame as a glass slab. Idempotent and cheap: the backdrop
     * view is created once per pane and only re-fed here, so this can run on every editor slider
     * tick and on every frost refresh.
     */
    public void applyPaneGlass() {
        float radiusPx = paneGlassRadiusPx();
        for (FrameLayout frame : mPaneFrames.values()) {
            PaneGlassBackdropView backdrop = frame.findViewById(R.id.terminal_pane_glass);
            if (backdrop == null) continue;
            if (!PaneGlass.apply(mSurfaceStyle, frame, backdrop, radiusPx))
                releasePanePlank(frame);
        }
        // The clip that keeps the terminal's rectangular cell backgrounds from poking past the
        // slab's corners is part of the pane's shape, which updateActiveBorders owns for every
        // pane, glass or not — it runs on every render, and this does not.
        updateActiveBorders();
    }

    private View buildView(Node node) {
        if (node instanceof Leaf) {
            return paneFrameFor(((Leaf) node).session);
        }
        Split split = (Split) node;
        LinearLayout ll = new LinearLayout(mHostView.getContext());
        mSplitLayouts.put(split, ll);
        ll.setOrientation(split.orientation);
        ll.setClipChildren(false);
        ll.setClipToPadding(false);
        boolean vertical = split.orientation == LinearLayout.VERTICAL;
        int match = LinearLayout.LayoutParams.MATCH_PARENT;

        View va = buildView(split.a);
        View vb = buildView(split.b);
        ll.addView(va, new LinearLayout.LayoutParams(
            vertical ? match : 0, vertical ? 0 : match, split.weightA));
        View divider = new View(mHostView.getContext());
        divider.setBackground(ContextCompat.getDrawable(mHostView.getContext(),
            R.drawable.pane_divider));
        // Inner padding: the gap is what turns two panes into two slabs rather than one sheet with
        // a line through it, so it is user-tunable rather than the old fixed hairline.
        int gapPx = dp(paneGapDp());
        ll.addView(divider, new LinearLayout.LayoutParams(
            vertical ? match : gapPx, vertical ? gapPx : match));
        ll.addView(vb, new LinearLayout.LayoutParams(
            vertical ? match : 0, vertical ? 0 : match, split.weightB));
        return ll;
    }

    private FrameLayout paneFrameFor(TerminalSession session) {
        PaneContentFrame frame = mPaneFrames.get(session);
        if (frame == null) {
            frame = (PaneContentFrame) mInflater.inflate(R.layout.view_terminal_pane, mHostView, false);
            TerminalView view = frame.findViewById(R.id.terminal_view);
            if (mHostSurfaceResizeDepth > 0) view.setTerminalSizeUpdatesPaused(true);
            mHost.configurePaneView(view);
            view.setOnTouchListener((v, ev) -> {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    TerminalSession s = ((TerminalView) v).getCurrentSession();
                    if (s != null) focusSession(s);
                }
                return false;
            });
            view.attachSession(session);
            mHost.configureAttachedPaneView(view, session);
            mPaneFrames.put(session, frame);
            mPaneViews.put(session, view);
            PaneGlass.followLayout(frame.findViewById(R.id.terminal_pane_glass));
            applyPaneGlass();
        } else {
            // A cached frame may still carry a half-finished entry animation's alpha/scale.
            resetFrameTransform(frame);
            if (frame.getParent() instanceof ViewGroup)
                ((ViewGroup) frame.getParent()).removeView(frame);
        }
        TerminalView attachedView = mPaneViews.get(session);
        if (attachedView != null) {
            mHost.configureAttachedPaneView(attachedView, session);
            // Reapply the pane's pinned zoom after the host stamped its default, so re-showing a
            // window (or any re-render) can't fold every pane back to the app-wide size.
            Window owner = windowOf(session);
            Leaf leaf = owner == null ? null : findLeafInWindow(owner, session);
            if (leaf != null && leaf.fontSize > 0) attachedView.setTextSize(leaf.fontSize);
        }
        return frame;
    }

    /** The focused pane's pinned font size, or 0 while it follows the app-wide default. */
    public int getActivePaneFontSize() {
        if (mActiveWindow == null || mActiveWindow.active == null) return 0;
        return mActiveWindow.active.fontSize;
    }

    /**
     * Pin the focused pane's font size and apply it to its view. From then on the pane keeps this
     * size across window switches and re-renders, independent of the app-wide default.
     */
    public boolean setActivePaneFontSize(int size) {
        if (mActiveWindow == null || mActiveWindow.active == null || size <= 0) return false;
        mActiveWindow.active.fontSize = size;
        TerminalView view = mPaneViews.get(mActiveWindow.active.session);
        if (view != null) view.setTextSize(size);
        return true;
    }

    private void detachPaneView(TerminalSession session) {
        PaneRim rim = mBorderStates.remove(session);
        if (rim != null) rim.cancel();
        FrameLayout frame = mPaneFrames.remove(session);
        releasePanePlank(frame);
        mPaneViews.remove(session);
        if (frame != null && frame.getParent() instanceof ViewGroup)
            ((ViewGroup) frame.getParent()).removeView(frame);
    }

    private void updateActiveBorders() {
        List<TerminalView> views = getVisiblePaneViews();
        // Tiled panes, not every pane: a float always carries its own focus-keyed border, and a lone
        // tiled pane keeps the terminal border as its frame. So showing the scratchpad no longer
        // paints and unpaints a border on the pane behind it.
        boolean split = tiledPaneCount() > 1;
        TerminalSession activeSession = getActiveSession();
        java.util.Set<TerminalSession> floatingSessions = new java.util.HashSet<>();
        if (mActiveWindow != null) {
            for (Leaf leaf : mActiveWindow.floating) floatingSessions.add(leaf.session);
        }
        for (TerminalView v : views) {
            TerminalSession paneSession = v.getCurrentSession();
            PaneContentFrame frame = mPaneFrames.get(paneSession);
            if (frame == null) continue;
            boolean floating = floatingSessions.contains(paneSession);
            // The pane's shape, and with it the clearance the terminal is laid out inside: the
            // glass slab's radius, the float's card, or the focus stroke's own arc. Only glass
            // clips here — a float clips on its own wrapper and a stroke does not clip at all —
            // but all three round the same corners over the same cells.
            boolean glassShape = paneGlassActive();
            float shapeRadiusPx = glassShape ? paneGlassRadiusPx()
                : (floating || split || mMaximizedLeaf != null) ? dp(FLOAT_CORNER_RADIUS_DP) : 0f;
            frame.setPaneShape(shapeRadiusPx, glassShape);
            if (!split && mMaximizedLeaf == null && !floating && !glassShape) {
                PaneRim gone = mBorderStates.remove(paneSession);
                if (gone != null) gone.clear(frame);
                else frame.setForeground(null);
                continue;
            }
            // Same Material primary hue for every pane, but the focused pane's border is at full
            // strength while the rest are dimmed — an unambiguous, theme-proof focus cue. On glass
            // the stroke gives way to the shared lit rim, which is the slab's own edge; a drawn
            // outline over frost reads as a box sitting on the material.
            PaneRim rim = mBorderStates.get(paneSession);
            if (rim == null) rim = new PaneRim();
            if (rim.apply(frame, glassShape, paneGlassRadiusPx(), paneSession == activeSession))
                mBorderStates.put(paneSession, rim);
            else
                mBorderStates.remove(paneSession);
        }
        applyCursorOwnership();
        // The float handle pill dims with focus like the pane borders do.
        for (FloatingPaneContainer container : mFloatContainers.values()) container.invalidate();
    }


    private void focusActiveView() {
        if (!mHost.shouldTerminalTakeFocus()) return;
        TerminalView v = getActivePaneView();
        if (v != null && !v.isFocused()) v.requestFocus();
    }

    static float clampFirstWeight(float total, float candidate) {
        float min = total * 0.18f;
        return Math.max(min, Math.min(total - min, candidate));
    }

    static boolean shouldShowInteractionOverlay(int paneCount, boolean maximized) {
        return maximized || paneCount > 1;
    }

    static float snapFirstWeightToCell(float total, float availablePixels,
                                       float currentWeight, float cellPixels) {
        if (total <= 0f || availablePixels <= 0f || cellPixels <= 0f) {
            return clampFirstWeight(total, currentWeight);
        }
        float currentPixels = availablePixels * currentWeight / total;
        float snappedPixels = Math.round(currentPixels / cellPixels) * cellPixels;
        return clampFirstWeight(total, total * snappedPixels / availablePixels);
    }

    static int touchedBorderIndex(@NonNull List<RectF> panes, int activeIndex,
                                  float x, float y, float threshold) {
        int contained = -1;
        int activeCandidate = -1;
        int nearest = -1;
        float containedDistance = Float.MAX_VALUE;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < panes.size(); i++) {
            RectF rect = panes.get(i);
            if (rect == null || x < rect.left - threshold || x > rect.right + threshold
                || y < rect.top - threshold || y > rect.bottom + threshold) continue;
            float edgeDistance = Math.min(
                Math.min(Math.abs(x - rect.left), Math.abs(x - rect.right)),
                Math.min(Math.abs(y - rect.top), Math.abs(y - rect.bottom)));
            if (edgeDistance > threshold) continue;
            if (rect.contains(x, y) && edgeDistance < containedDistance) {
                contained = i;
                containedDistance = edgeDistance;
            }
            if (activeIndex == i) activeCandidate = i;
            if (edgeDistance < nearestDistance) {
                nearest = i;
                nearestDistance = edgeDistance;
            }
        }
        if (contained >= 0) return contained;
        return activeCandidate >= 0 ? activeCandidate : nearest;
    }

    private void setAllPaneSizeUpdatesPaused(boolean paused, boolean keepBottom) {
        if (paused) {
            for (TerminalView view : mPaneViews.values())
                view.setTerminalSizeUpdatesPaused(true, false);
            return;
        }
        java.util.HashSet<TerminalView> visible = new java.util.HashSet<>(getVisiblePaneViews());
        for (TerminalView view : mPaneViews.values()) {
            if (visible.contains(view)) view.setTerminalSizeUpdatesPaused(false, keepBottom);
            else view.resumeTerminalSizeUpdatesDiscardingPending();
        }
    }

    /** Transparent interaction layer: generous border hit targets without thick layout dividers. */
    private final class PaneInteractionOverlay extends View {

        private static final int ACTION_NONE = -1;
        private static final int ACTION_MOVE_PANE = 0;
        private static final int ACTION_MAXIMIZE = 1;
        private static final int ACTION_CLOSE = 2;

        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** Scratch for the handle pips, so a drag does not allocate a rect per frame. */
        private final RectF mHandleRect = new RectF();
        /** Scratch for pane rects read while drawing, and one for the control-tab geometry pass. */
        private final RectF mDrawPaneRect = new RectF();
        private final RectF mDrawDropHalfRect = new RectF();
        private final RectF mGlowClipRect = new RectF();
        private final RectF mGlowBorderRect = new RectF();
        private final RectF mGeometryPaneRect = new RectF();
        /** Scratch for pane rects read while hit-testing a touch stream. */
        private final RectF mHitPaneRect = new RectF();
        private final Path mPath = new Path();
        private final RectF mControlRect = new RectF();
        private final RectF[] mControlButtons = {new RectF(), new RectF(), new RectF()};

        @Nullable private Split mXSplit;
        @Nullable private Split mYSplit;
        @Nullable private Leaf mBorderTapLeaf;
        @Nullable private Leaf mControlLeaf;
        @Nullable private Leaf mMovingLeaf;
        @Nullable private Leaf mMoveTarget;
        private float mDownX;
        private float mDownY;
        private float mHandleX;
        private float mHandleY;
        private float mXWeightA;
        private float mXWeightB;
        private float mYWeightA;
        private float mYWeightB;
        private float mControlProgress;
        private boolean mDraggingDivider;
        private boolean mBorderPressed;
        private boolean mTouchMoved;
        private boolean mControlsShown;
        private int mPressedControlAction = ACTION_NONE;
        @Nullable private ValueAnimator mControlAnimator;

        PaneInteractionOverlay() {
            super(mHostView.getContext());
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription("Pane resize and controls");
        }

        void onTreeRendered() {
            resetTouchState();
            // A dismiss still in flight would overwrite whatever is assigned below on its next
            // frame, fading the controls off a pane that has just been maximized.
            if (mControlAnimator != null) {
                mControlAnimator.cancel();
                mControlAnimator = null;
            }
            if (mMaximizedLeaf != null) {
                mControlLeaf = mMaximizedLeaf;
                mControlsShown = true;
                mControlProgress = 1f;
            } else if (mControlLeaf != null
                && (mActiveWindow == null || findLeafIn(mActiveWindow.root,
                    mControlLeaf.session) == null)) {
                mControlLeaf = null;
                mControlsShown = false;
                mControlProgress = 0f;
            }
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = mHandleX = x;
                    mDownY = mHandleY = y;
                    mTouchMoved = false;
                    mPressedControlAction = controlActionAt(x, y);
                    if (mPressedControlAction != ACTION_NONE) {
                        if (mPressedControlAction == ACTION_MOVE_PANE) {
                            mMovingLeaf = mControlLeaf;
                            mMoveTarget = mControlLeaf;
                        }
                        getParent().requestDisallowInterceptTouchEvent(true);
                        invalidate();
                        return true;
                    }

                    if (mControlsShown && mMaximizedLeaf == null) dismissControls();
                    findDividerTargets(x, y);
                    // Resolve pane ownership from the touched border before falling back to the
                    // nearest pane. This is important for the original pane: the empty pixels in
                    // a shared divider otherwise tend to resolve to the newly-created neighbour.
                    mBorderTapLeaf = leafAtTouchedBorder(x, y);
                    if (mBorderTapLeaf == null) mBorderTapLeaf = leafAtOrNearest(x, y);
                    if (mXSplit != null || mYSplit != null) {
                        mDraggingDivider = true;
                        beginHostSurfaceResize();
                        if (mXSplit != null) {
                            mXWeightA = mXSplit.weightA;
                            mXWeightB = mXSplit.weightB;
                        }
                        if (mYSplit != null) {
                            mYWeightA = mYSplit.weightA;
                            mYWeightB = mYSplit.weightB;
                        }
                        focusLeaf(mBorderTapLeaf);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        invalidate();
                        return true;
                    }
                    if (mBorderTapLeaf != null && isNearPaneBorder(mBorderTapLeaf, x, y)) {
                        mBorderPressed = true;
                        focusLeaf(mBorderTapLeaf);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        invalidate();
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (mMovingLeaf != null) {
                        mHandleX = x;
                        mHandleY = y;
                        mMoveTarget = leafAtOrNearest(x, y);
                        mTouchMoved = true;
                        invalidate();
                        return true;
                    }
                    if (mPressedControlAction != ACTION_NONE) {
                        mTouchMoved |= distance(x, y, mDownX, mDownY) > dp(8);
                        return true;
                    }
                    if (mDraggingDivider) {
                        mHandleX = x;
                        mHandleY = y;
                        mTouchMoved |= distance(x, y, mDownX, mDownY) > dp(3);
                        applySplitDrag(mXSplit, x - mDownX, mXWeightA, mXWeightB);
                        applySplitDrag(mYSplit, y - mDownY, mYWeightA, mYWeightB);
                        invalidate();
                        return true;
                    }
                    if (mBorderTapLeaf != null) {
                        mHandleX = x;
                        mHandleY = y;
                        mTouchMoved |= distance(x, y, mDownX, mDownY) > dp(3);
                        invalidate();
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                    if (mMovingLeaf != null) {
                        Leaf source = mMovingLeaf;
                        Leaf target = mMoveTarget;
                        resetTouchState();
                        if (source != null && target != null && source != target) {
                            RectF targetRect = isDwindleManaged(mActiveWindow)
                                ? paneRect(target) : null;
                            if (targetRect != null
                                && retileDroppedPane(source, target, targetRect, x, y)) {
                                mControlLeaf = source;
                                render();
                                showControls(source);
                                mHost.onActivePaneChanged();
                                mHost.onTreesChanged();
                            } else {
                                swapPanePositions(source, target);
                            }
                        } else {
                            showControls(source);
                        }
                        return true;
                    }
                    if (mPressedControlAction != ACTION_NONE) {
                        int action = mPressedControlAction;
                        Leaf leaf = mControlLeaf;
                        boolean activate = !mTouchMoved && controlActionAt(x, y) == action;
                        resetTouchState();
                        if (activate && leaf != null) performControlAction(action, leaf);
                        return true;
                    }
                    if (mDraggingDivider || mBorderTapLeaf != null) {
                        Leaf leaf = mBorderTapLeaf;
                        boolean resized = mDraggingDivider && mTouchMoved;
                        if (resized) {
                            snapSplitToCellGrid(mXSplit);
                            snapSplitToCellGrid(mYSplit);
                        }
                        if (mDraggingDivider) finishHostSurfaceResizeKeepingBottom();
                        resetTouchState();
                        showControls(leaf);
                        if (resized) mHost.onTreesChanged();
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    if (mDraggingDivider) finishHostSurfaceResizeKeepingBottom();
                    resetTouchState();
                    invalidate();
                    return true;
                default:
                    return false;
            }
        }

        private void performControlAction(int action, @NonNull Leaf leaf) {
            if (action == ACTION_MAXIMIZE) {
                mMaximizedLeaf = mMaximizedLeaf == null ? leaf : null;
                mActiveWindow.active = leaf;
                render();
                mHost.onActivePaneChanged();
            } else if (action == ACTION_CLOSE) {
                dismissControls();
                leaf.session.finishIfRunning();
            }
        }

        private void swapPanePositions(@NonNull Leaf source, @NonNull Leaf target) {
            TerminalSession moved = source.session;
            source.session = target.session;
            target.session = moved;
            mActiveWindow.active = target;
            mControlLeaf = target;
            render();
            showControls(target);
            mHost.onActivePaneChanged();
            mHost.onTreesChanged();
        }

        private void focusLeaf(@Nullable Leaf leaf) {
            if (leaf == null || mActiveWindow == null || mActiveWindow.active == leaf) return;
            mActiveWindow.active = leaf;
            updateActiveBorders();
            applyFocusGrowth(true);
            focusActiveView();
            mHost.onActivePaneChanged();
        }

        private void findDividerTargets(float x, float y) {
            mXSplit = null;
            mYSplit = null;
            float bestX = Float.MAX_VALUE;
            float bestY = Float.MAX_VALUE;
            float threshold = dp(14);
            int[] host = location(mHostView);
            for (Map.Entry<Split, LinearLayout> entry : mSplitLayouts.entrySet()) {
                Split split = entry.getKey();
                LinearLayout layout = entry.getValue();
                if (layout.getChildCount() < 3) continue;
                View divider = layout.getChildAt(1);
                int[] dividerLocation = location(divider);
                int[] layoutLocation = location(layout);
                float left = layoutLocation[0] - host[0];
                float top = layoutLocation[1] - host[1];
                float right = left + layout.getWidth();
                float bottom = top + layout.getHeight();
                if (split.orientation == LinearLayout.HORIZONTAL) {
                    float boundary = dividerLocation[0] - host[0] + divider.getWidth() / 2f;
                    float distance = Math.abs(x - boundary);
                    if (distance <= threshold && y >= top - threshold && y <= bottom + threshold
                        && distance < bestX) {
                        bestX = distance;
                        mXSplit = split;
                    }
                } else {
                    float boundary = dividerLocation[1] - host[1] + divider.getHeight() / 2f;
                    float distance = Math.abs(y - boundary);
                    if (distance <= threshold && x >= left - threshold && x <= right + threshold
                        && distance < bestY) {
                        bestY = distance;
                        mYSplit = split;
                    }
                }
            }
        }

        private void applySplitDrag(@Nullable Split split, float delta,
                                    float startA, float startB) {
            if (split == null) return;
            LinearLayout layout = mSplitLayouts.get(split);
            if (layout == null || layout.getChildCount() < 3) return;
            View divider = layout.getChildAt(1);
            float available = split.orientation == LinearLayout.HORIZONTAL
                ? layout.getWidth() - divider.getWidth()
                : layout.getHeight() - divider.getHeight();
            if (available <= 0f) return;
            float total = startA + startB;
            float startPixels = available * startA / total;
            float candidate = total * (startPixels + delta) / available;
            split.weightA = clampFirstWeight(total, candidate);
            split.weightB = total - split.weightA;
            applyWeightsToRenderedLayout(split);
        }

        private void snapSplitToCellGrid(@Nullable Split split) {
            if (split == null) return;
            LinearLayout layout = mSplitLayouts.get(split);
            if (layout == null || layout.getChildCount() < 3) return;
            View divider = layout.getChildAt(1);
            float available = split.orientation == LinearLayout.HORIZONTAL
                ? layout.getWidth() - divider.getWidth()
                : layout.getHeight() - divider.getHeight();
            TerminalView reference = getActivePaneView();
            if (reference == null || available <= 0f) return;
            float cell = split.orientation == LinearLayout.HORIZONTAL
                ? reference.getTerminalCellWidthPixels()
                : reference.getTerminalCellHeightPixels();
            float total = split.weightA + split.weightB;
            split.weightA = snapFirstWeightToCell(total, available, split.weightA, cell);
            split.weightB = total - split.weightA;
            LinearLayout.LayoutParams a =
                (LinearLayout.LayoutParams) layout.getChildAt(0).getLayoutParams();
            LinearLayout.LayoutParams b =
                (LinearLayout.LayoutParams) layout.getChildAt(2).getLayoutParams();
            a.weight = split.weightA;
            b.weight = split.weightB;
            layout.getChildAt(0).setLayoutParams(a);
            layout.getChildAt(2).setLayoutParams(b);
        }

        /**
         * Return the leaf whose border was actually touched. A point inside a pane wins over an
         * equally-near pane across the divider; for the divider's exact centre, the focused pane
         * wins. This makes the first/original pane as reachable as every pane created after it.
         */
        @Nullable
        private Leaf leafAtTouchedBorder(float x, float y) {
            if (mActiveWindow == null) return null;
            float threshold = dp(12);
            List<Leaf> leaves = new ArrayList<>();
            List<RectF> panes = new ArrayList<>();
            int activeIndex = -1;
            for (Leaf leaf : leavesOf(mActiveWindow.root)) {
                RectF rect = paneRect(leaf);
                if (rect == null) continue;
                if (mActiveWindow.active == leaf) activeIndex = leaves.size();
                leaves.add(leaf);
                panes.add(rect);
            }
            int index = touchedBorderIndex(panes, activeIndex, x, y, threshold);
            return index < 0 ? null : leaves.get(index);
        }

        @Nullable
        private Leaf leafAtOrNearest(float x, float y) {
            if (mActiveWindow == null) return null;
            Leaf best = null;
            float bestDistance = Float.MAX_VALUE;
            for (Leaf leaf : leavesOf(mActiveWindow.root)) {
                RectF rect = paneRect(leaf, mHitPaneRect);
                if (rect == null) continue;
                if (rect.contains(x, y)) return leaf;
                float dx = Math.max(rect.left - x, Math.max(0f, x - rect.right));
                float dy = Math.max(rect.top - y, Math.max(0f, y - rect.bottom));
                float d = dx * dx + dy * dy;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = leaf;
                }
            }
            return best;
        }

        private boolean isNearPaneBorder(@NonNull Leaf leaf, float x, float y) {
            RectF rect = paneRect(leaf, mHitPaneRect);
            if (rect == null) return false;
            float threshold = dp(12);
            return Math.min(Math.min(Math.abs(x - rect.left), Math.abs(x - rect.right)),
                Math.min(Math.abs(y - rect.top), Math.abs(y - rect.bottom))) <= threshold;
        }

        /** Allocating form, for callers that keep several pane rects alive at once. */
        @Nullable
        private RectF paneRect(@NonNull Leaf leaf) {
            return paneRect(leaf, new RectF());
        }

        /**
         * Fills {@code out} with the leaf's frame in host coordinates and returns it, or null when the
         * leaf has no attached frame. Every per-frame and per-touch caller passes its own scratch:
         * these run inside draw and move handling, where one rect per call is one rect per frame.
         */
        @Nullable
        private RectF paneRect(@NonNull Leaf leaf, @NonNull RectF out) {
            FrameLayout frame = mPaneFrames.get(leaf.session);
            if (frame == null || frame.getParent() == null) return null;
            int[] frameLocation = location(frame);
            int[] hostLocation = location(mHostView);
            float left = frameLocation[0] - hostLocation[0];
            float top = frameLocation[1] - hostLocation[1];
            out.set(left, top, left + frame.getWidth(), top + frame.getHeight());
            return out;
        }

        private int[] location(@NonNull View view) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            return location;
        }

        private void showControls(@Nullable Leaf leaf) {
            if (leaf == null || !shouldShowInteractionOverlay(
                mActiveWindow == null ? 0 : leavesOf(mActiveWindow.root).size(),
                mMaximizedLeaf != null)) return;
            mControlLeaf = leaf;
            mControlsShown = true;
            animateControlProgress(1f, false);
        }

        private void dismissControls() {
            if (mMaximizedLeaf != null) return;
            animateControlProgress(0f, true);
        }

        private void animateControlProgress(float target, boolean clearOnEnd) {
            if (mControlAnimator != null) mControlAnimator.cancel();
            mControlAnimator = ValueAnimator.ofFloat(mControlProgress, target);
            mControlAnimator.setDuration(190L);
            mControlAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            mControlAnimator.addUpdateListener(animation -> {
                mControlProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            mControlAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (clearOnEnd && mControlProgress <= 0f) {
                        mControlsShown = false;
                        mControlLeaf = null;
                    }
                }
            });
            mControlAnimator.start();
        }

        private int controlActionAt(float x, float y) {
            if (!mControlsShown || mControlLeaf == null || mControlProgress < .35f) {
                return ACTION_NONE;
            }
            computeControlGeometry();
            if (mMaximizedLeaf != null) {
                if (mControlButtons[0].contains(x, y)) return ACTION_MAXIMIZE;
                if (mControlButtons[1].contains(x, y)) return ACTION_CLOSE;
            } else {
                if (mControlButtons[0].contains(x, y)) return ACTION_MOVE_PANE;
                if (mControlButtons[1].contains(x, y)) return ACTION_MAXIMIZE;
                if (mControlButtons[2].contains(x, y)) return ACTION_CLOSE;
            }
            return ACTION_NONE;
        }

        private void computeControlGeometry() {
            RectF pane = mControlLeaf == null ? null : paneRect(mControlLeaf, mGeometryPaneRect);
            if (pane == null) {
                mControlRect.setEmpty();
                return;
            }
            int count = mMaximizedLeaf == null ? 3 : 2;
            float button = dp(22.4f);
            float width = button * count + dp(4.8f);
            float right = pane.right - dp(3);
            float left = Math.max(pane.left + dp(3), right - width);
            float height = dp(24);
            float top = pane.top - height * (1f - mControlProgress);
            mControlRect.set(left, top, right, top + height);
            for (int i = 0; i < mControlButtons.length; i++) mControlButtons[i].setEmpty();
            float x = left + dp(2.4f);
            for (int i = 0; i < count; i++) {
                mControlButtons[i].set(x, top, x + button, top + dp(22));
                x += button;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!mDraggingDivider && !mBorderPressed && mMovingLeaf == null
                && !(mControlsShown && mControlLeaf != null && mControlProgress > 0f)) {
                // Nothing of ours to draw. Resolving theme colours before this check meant an overlay
                // that draws nothing still did two theme lookups on every pass.
                return;
            }
            int primary = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(getContext(), R.color.termux_primary));
            int tertiary = MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorTertiary, primary);
            if (mDraggingDivider) {
                // The edge being dragged glows on the focused pane instead of drawing a slab down
                // the divider: a resize is a change to *this* pane's edge, and a 3dp accent line
                // over the seam read as a second, thicker border appearing out of nowhere.
                RectF focused = paneRect(mBorderTapLeaf, mDrawPaneRect);
                if (focused != null) {
                    drawEdgeGlow(canvas, focused, primary, edgeBandFor(focused, mXSplit, true));
                    drawEdgeGlow(canvas, focused, primary, edgeBandFor(focused, mYSplit, false));
                }
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(tertiary);
                mHandleRect.set(mHandleX - dp(5), mHandleY - dp(2),
                    mHandleX + dp(5), mHandleY + dp(2));
                canvas.drawRoundRect(mHandleRect, dp(2), dp(2), mPaint);
                if (mXSplit != null && mYSplit != null) {
                    mHandleRect.set(mHandleX - dp(2), mHandleY - dp(5),
                        mHandleX + dp(2), mHandleY + dp(5));
                    canvas.drawRoundRect(mHandleRect, dp(2), dp(2), mPaint);
                }
            }
            if (mBorderPressed && mBorderTapLeaf != null && !mDraggingDivider) {
                RectF border = paneRect(mBorderTapLeaf, mDrawPaneRect);
                if (border != null) {
                    // Grabbed but not yet moved: the whole border of the focused pane glows, so the
                    // pane that will resize is named without drawing a frame around it.
                    drawEdgeGlow(canvas, border, primary, null);
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(tertiary);
                    mHandleRect.set(mHandleX - dp(5), mHandleY - dp(2),
                        mHandleX + dp(5), mHandleY + dp(2));
                    canvas.drawRoundRect(mHandleRect, dp(2), dp(2), mPaint);
                }
            }
            if (mMovingLeaf != null && mMoveTarget != null && mMoveTarget != mMovingLeaf) {
                RectF target = paneRect(mMoveTarget, mDrawPaneRect);
                if (target != null) {
                    if (isDwindleManaged(mActiveWindow)) {
                        // Under dwindle the drop does not swap, it takes half the target: show
                        // which half, so the finger can steer it before letting go.
                        int side = DwindleTilingPolicy.dropSideFor(target, mHandleX, mHandleY);
                        DwindleTilingPolicy.halfFor(target, side, mDrawDropHalfRect);
                        mPaint.setStyle(Paint.Style.FILL);
                        mPaint.setColor(ColorUtils.setAlphaComponent(tertiary, 56));
                        canvas.drawRect(mDrawDropHalfRect, mPaint);
                        mPaint.setStyle(Paint.Style.STROKE);
                        mPaint.setStrokeWidth(dp(3));
                        mPaint.setColor(ColorUtils.setAlphaComponent(tertiary, 220));
                        canvas.drawRect(mDrawDropHalfRect, mPaint);
                    } else {
                        mPaint.setStyle(Paint.Style.STROKE);
                        mPaint.setStrokeWidth(dp(3));
                        mPaint.setColor(ColorUtils.setAlphaComponent(tertiary, 220));
                        canvas.drawRect(target, mPaint);
                    }
                }
            }
            if (mControlsShown && mControlLeaf != null && mControlProgress > 0f) {
                drawControls(canvas, primary, tertiary);
            }
        }

        /**
         * The band to clip a glow to so it lands on the one edge of {@code pane} that {@code split}
         * moves, or null when this split does not touch the pane (or is not being dragged).
         *
         * <p>Which side it is comes from the divider's own position: the pane sits on whichever
         * side of the seam is nearer, and that is the edge whose length is about to change.
         */
        @Nullable
        private RectF edgeBandFor(@NonNull RectF pane, @Nullable Split split, boolean vertical) {
            if (split == null) return null;
            LinearLayout layout = mSplitLayouts.get(split);
            if (layout == null || layout.getChildCount() < 3) return null;
            View divider = layout.getChildAt(1);
            int[] host = location(mHostView);
            int[] dividerLocation = location(divider);
            float reach = dp(GLOW_DEPTH_DP) + dp(2);
            if (vertical) {
                float seam = dividerLocation[0] - host[0] + divider.getWidth() / 2f;
                boolean rightEdge = Math.abs(pane.right - seam) <= Math.abs(pane.left - seam);
                float edge = rightEdge ? pane.right : pane.left;
                mGlowClipRect.set(edge - reach, pane.top - reach, edge + reach, pane.bottom + reach);
            } else {
                float seam = dividerLocation[1] - host[1] + divider.getHeight() / 2f;
                boolean bottomEdge = Math.abs(pane.bottom - seam) <= Math.abs(pane.top - seam);
                float edge = bottomEdge ? pane.bottom : pane.top;
                mGlowClipRect.set(pane.left - reach, edge - reach, pane.right + reach, edge + reach);
            }
            return mGlowClipRect;
        }

        /**
         * Lays a glow inside the pane's border: three concentric rounded strokes, widest and
         * faintest first. Stroking the same rounded rect keeps the light on the 6dp corners instead
         * of squaring them off the way an axis-aligned gradient band would, and it costs no
         * shader allocation per frame during a drag.
         *
         * @param clip band to keep the glow inside, for a single edge; null glows the whole border.
         */
        private void drawEdgeGlow(Canvas canvas, @NonNull RectF pane, int color,
                                  @Nullable RectF clip) {
            float depth = dp(GLOW_DEPTH_DP);
            // The glow must trace the ring the pane already draws. With glass on that ring is the
            // rim at the glass radius (up to 14dp); drawing the glow at the stock 6dp put a second
            // arc inside every corner — a visible double border for the whole grab and drag.
            float radius = paneGlassActive() ? paneGlassRadiusPx() : dp(FLOAT_CORNER_RADIUS_DP);
            int saved = canvas.save();
            // Clip to the pane so the blur falls off inward only: light spilling across the seam
            // would read as the neighbour lighting up too.
            canvas.clipRect(pane);
            if (clip != null) canvas.clipRect(clip);
            mPaint.setStyle(Paint.Style.STROKE);
            // BlurMaskFilter is a no-op on a hardware-accelerated canvas — every View draws on one
            // by default regardless of API level, so gating this on Build.VERSION_CODES.P (as this
            // used to) picked the masked path on every real device and rendered one flat, full-alpha
            // stroke: a hard pink rectangle, not a glow. The ramp below fades through plain alpha,
            // which hardware acceleration does support, so it is the only path that actually glows.
            for (int step = GLOW_RAMP_STEPS; step >= 1; step--) {
                float t = step / (float) GLOW_RAMP_STEPS;
                float width = depth * t;
                float fade = 1f - t;
                mGlowBorderRect.set(pane);
                mGlowBorderRect.inset(width / 2f, width / 2f);
                if (mGlowBorderRect.width() <= 0f || mGlowBorderRect.height() <= 0f) continue;
                mPaint.setStrokeWidth(depth / GLOW_RAMP_STEPS + dp(0.5f));
                mPaint.setColor(ColorUtils.setAlphaComponent(color,
                    Math.round(GLOW_ALPHA * fade * fade)));
                canvas.drawRoundRect(mGlowBorderRect, radius, radius, mPaint);
            }
            // The edge itself stays crisp; without it the glow reads as a smudge rather than a lit
            // border, and the pane's own 1dp focus ring is what the light is supposed to be on.
            mGlowBorderRect.set(pane);
            mGlowBorderRect.inset(dp(0.75f), dp(0.75f));
            mPaint.setStrokeWidth(dp(1.5f));
            mPaint.setColor(ColorUtils.setAlphaComponent(color, 235));
            canvas.drawRoundRect(mGlowBorderRect, radius, radius, mPaint);
            canvas.restoreToCount(saved);
        }

        private void drawControls(Canvas canvas, int primary, int tertiary) {
            computeControlGeometry();
            if (mControlRect.isEmpty()) return;
            int surface = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorSurfacePanel,
                ContextCompat.getColor(getContext(), R.color.termux_surface_panel));
            RectF pane = paneRect(mControlLeaf, mDrawPaneRect);
            if (pane == null) return;
            float paneTop = pane.top;
            float radius = dp(4);
            int canvasState = canvas.save();
            // The tab is revealed through the pane's top edge. Clipping here is what makes the
            // closing motion disappear back into the frame instead of floating above the pane.
            canvas.clipRect(pane.left, paneTop - dp(1), pane.right, pane.bottom);

            mPath.reset();
            mPath.moveTo(mControlRect.left, paneTop);
            mPath.lineTo(mControlRect.right, paneTop);
            mPath.lineTo(mControlRect.right, mControlRect.bottom - radius);
            mPath.quadTo(mControlRect.right, mControlRect.bottom,
                mControlRect.right - radius, mControlRect.bottom);
            mPath.lineTo(mControlRect.left + radius, mControlRect.bottom);
            mPath.quadTo(mControlRect.left, mControlRect.bottom,
                mControlRect.left, mControlRect.bottom - radius);
            mPath.close();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(ColorUtils.setAlphaComponent(surface,
                Math.round(232f * mControlProgress)));
            canvas.drawPath(mPath, mPaint);

            mPath.reset();
            mPath.moveTo(mControlRect.left - dp(5), paneTop);
            mPath.lineTo(mControlRect.left, paneTop);
            mPath.lineTo(mControlRect.left, mControlRect.bottom - radius);
            mPath.quadTo(mControlRect.left, mControlRect.bottom,
                mControlRect.left + radius, mControlRect.bottom);
            mPath.lineTo(mControlRect.right - radius, mControlRect.bottom);
            mPath.quadTo(mControlRect.right, mControlRect.bottom,
                mControlRect.right, mControlRect.bottom - radius);
            mPath.lineTo(mControlRect.right, paneTop);
            mPath.lineTo(mControlRect.right + dp(5), paneTop);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(dp(1));
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setColor(ColorUtils.setAlphaComponent(primary,
                Math.round(225f * mControlProgress)));
            canvas.drawPath(mPath, mPaint);

            int count = mMaximizedLeaf == null ? 3 : 2;
            for (int i = 0; i < count; i++) {
                int action = mMaximizedLeaf == null ? i : i + 1;
                RectF button = mControlButtons[i];
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                mPaint.setStrokeWidth(dp(1.35f));
                mPaint.setColor(ColorUtils.setAlphaComponent(
                    action == ACTION_MOVE_PANE ? tertiary : action == ACTION_CLOSE
                        ? MaterialColors.getColor(getContext(),
                            com.termux.shared.R.attr.termuxColorError, Color.RED) : primary,
                    Math.round(255f * mControlProgress)));
                float cx = button.centerX();
                float cy = button.centerY();
                if (action == ACTION_MOVE_PANE) {
                    canvas.drawLine(cx - dp(4), cy - dp(2.5f), cx + dp(4), cy - dp(2.5f), mPaint);
                    canvas.drawLine(cx - dp(4), cy + dp(2.5f), cx + dp(4), cy + dp(2.5f), mPaint);
                } else if (action == ACTION_MAXIMIZE) {
                    float inset = mMaximizedLeaf == null ? dp(4) : dp(3.5f);
                    canvas.drawRect(cx - inset, cy - inset, cx + inset, cy + inset, mPaint);
                    if (mMaximizedLeaf != null) {
                        canvas.drawLine(cx - dp(5), cy + dp(2), cx - dp(2), cy + dp(5), mPaint);
                        canvas.drawLine(cx + dp(5), cy - dp(2), cx + dp(2), cy - dp(5), mPaint);
                    }
                } else {
                    canvas.drawLine(cx - dp(4), cy - dp(4), cx + dp(4), cy + dp(4), mPaint);
                    canvas.drawLine(cx + dp(4), cy - dp(4), cx - dp(4), cy + dp(4), mPaint);
                }
            }
            mPaint.setStrokeCap(Paint.Cap.BUTT);
            mPaint.setStrokeJoin(Paint.Join.MITER);
            canvas.restoreToCount(canvasState);
        }

        private void resetTouchState() {
            mXSplit = null;
            mYSplit = null;
            mBorderTapLeaf = null;
            mMovingLeaf = null;
            mMoveTarget = null;
            mDraggingDivider = false;
            mBorderPressed = false;
            mTouchMoved = false;
            mPressedControlAction = ACTION_NONE;
        }

        private float distance(float x1, float y1, float x2, float y2) {
            return (float) Math.hypot(x1 - x2, y1 - y2);
        }
    }

    /**
     * Chrome around one floating pane: a transparent top handle row holding a floating pill
     * (drag = move, tap = expand into action buttons) and a bottom-right grip band (resize).
     * The panel surface starts at the terminal's top edge, so nothing extends under the pill.
     * Move/resize deliberately never start from the terminal content itself — long-press
     * plus drag there is mouse-drag reporting (TerminalView.armTouchMouseDragFromLongPress) and
     * must keep reaching the shell — so only these chrome regions ever intercept.
     */
    private final class FloatingPaneContainer extends FrameLayout {

        private static final int DRAG_NONE = 0;
        private static final int DRAG_MOVE = 1;
        private static final int DRAG_RESIZE = 2;

        private static final int PILL_ACTION_NONE = 0;
        private static final int PILL_ACTION_CLOSE = 1;
        private static final int PILL_ACTION_DOCK = 2;

        private final Leaf mLeaf;
        private final Paint mChromePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** Scratch for the pill grip and its glyphs, redrawn on every frame a float is on screen. */
        private final RectF mChromeScratch = new RectF();
        private final Runnable mCollapsePill = this::collapsePill;
        private int mDragMode = DRAG_NONE;
        private float mDownRawX;
        private float mDownRawY;
        private boolean mDragMoved;
        @Nullable private RectF mDownFrac;
        /** Pill expanded into its action buttons (close / dock) after a tap. */
        private boolean mPillExpanded;
        private int mPressedPillAction = PILL_ACTION_NONE;

        FloatingPaneContainer(@NonNull Leaf leaf) {
            super(mHostView.getContext());
            mLeaf = leaf;
            setElevation(dp(FLOAT_ELEVATION_DP));
            // The container itself stays transparent so the handle row shows only the pill;
            // the surface color lives on a wrapper rather than the shared pane frame, which
            // must stay unstyled for tiled rendering.
            FrameLayout content = new FrameLayout(getContext());
            // On glass the float's fill would sit between the frost and the text and flatten the
            // slab back to a panel; the pane's own glass is the float's surface instead.
            if (!paneGlassActive()) {
                content.setBackgroundColor(MaterialColors.getColor(getContext(),
                    com.termux.shared.R.attr.termuxColorSurfacePanel,
                    ContextCompat.getColor(getContext(), R.color.termux_surface_panel)));
            }
            // pane_active_border is a foreground stroke, not a clip — without this the terminal's
            // own rectangular cell-background fill pokes a black triangle past each rounded corner.
            final float cornerRadiusPx = paneGlassActive()
                ? paneGlassRadiusPx() : dp(FLOAT_CORNER_RADIUS_DP);
            content.setClipToOutline(true);
            content.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
            content.addView(paneFrameFor(leaf.session), new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LayoutParams contentParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            contentParams.topMargin = dp(FLOAT_HANDLE_DP);
            addView(content, contentParams);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                // Any touch raises and focuses the float, including ones the terminal keeps.
                // Posted because raising reorders the host's children mid-dispatch otherwise.
                // Skipped while already the focused top-most float, so typing taps don't re-run
                // border/focus work on every DOWN.
                if (mActiveWindow == null || mActiveWindow.active != mLeaf
                    || mActiveWindow.floating.indexOf(mLeaf) != mActiveWindow.floating.size() - 1)
                    post(() -> focusSession(mLeaf.session));
                if (mPillExpanded && event.getY() > dp(FLOAT_HANDLE_DP)) collapsePill();
                // A press on an expanded pill button must fall through to onTouchEvent (the
                // strip has no child, so it lands there) instead of starting a move drag.
                mDragMode = pillActionAt(event.getX(), event.getY()) != PILL_ACTION_NONE
                    ? DRAG_NONE : dragModeAt(event.getX(), event.getY());
                if (mDragMode != DRAG_NONE) {
                    startDrag(event);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Down lands here directly when it hits the handle strip (no child there).
                    if (mDragMode == DRAG_NONE) {
                        int action = pillActionAt(event.getX(), event.getY());
                        if (action != PILL_ACTION_NONE) {
                            mPressedPillAction = action;
                            mDownRawX = event.getRawX();
                            mDownRawY = event.getRawY();
                            getParent().requestDisallowInterceptTouchEvent(true);
                            invalidate();
                            return true;
                        }
                        mDragMode = dragModeAt(event.getX(), event.getY());
                        if (mDragMode == DRAG_NONE) return false;
                        startDrag(event);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (mPressedPillAction != PILL_ACTION_NONE) {
                        if (Math.hypot(event.getRawX() - mDownRawX, event.getRawY() - mDownRawY)
                                > dp(8)) {
                            mPressedPillAction = PILL_ACTION_NONE;
                            invalidate();
                        }
                        return true;
                    }
                    if (mDragMode == DRAG_NONE || mDownFrac == null) return false;
                    float hostWidth = mHostView.getWidth();
                    float hostHeight = mHostView.getHeight();
                    if (hostWidth <= 0f || hostHeight <= 0f) return true;
                    // Raw coordinates: the container moves under the pointer, so view-local
                    // deltas would feed back into themselves.
                    float dxRaw = event.getRawX() - mDownRawX;
                    float dyRaw = event.getRawY() - mDownRawY;
                    // A slop gate keeps a pill tap from nudging the float by a few pixels.
                    if (!mDragMoved && Math.hypot(dxRaw, dyRaw) < dp(6)) return true;
                    if (!mDragMoved) {
                        mDragMoved = true;
                        collapsePill();
                    }
                    float dx = dxRaw / hostWidth;
                    float dy = dyRaw / hostHeight;
                    RectF candidate = new RectF(mDownFrac);
                    if (mDragMode == DRAG_MOVE) {
                        candidate.offset(dx, dy);
                    } else {
                        candidate.right += dx;
                        candidate.bottom += dy;
                    }
                    mLeaf.floatFrac = clampFloatFractions(candidate, hostWidth, hostHeight,
                        dp(FLOAT_MIN_WIDTH_DP), dp(FLOAT_MIN_HEIGHT_DP), dp(FLOAT_MIN_VISIBLE_DP));
                    applyFloatBounds(mLeaf, this);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (mPressedPillAction != PILL_ACTION_NONE) {
                        int action = mPressedPillAction;
                        mPressedPillAction = PILL_ACTION_NONE;
                        if (pillActionAt(event.getX(), event.getY()) == action)
                            performPillAction(action);
                        else invalidate();
                        return true;
                    }
                    if (mDragMode == DRAG_MOVE && !mDragMoved) togglePill();
                    endDrag();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (mPressedPillAction != PILL_ACTION_NONE) {
                        mPressedPillAction = PILL_ACTION_NONE;
                        invalidate();
                        return true;
                    }
                    endDrag();
                    return true;
                default:
                    return false;
            }
        }

        private void startDrag(@NonNull MotionEvent event) {
            mDownRawX = event.getRawX();
            mDownRawY = event.getRawY();
            mDragMoved = false;
            // Seed from what is on screen when the float is currently clamped, so a drag that
            // starts while the host is short does not teleport back to the remembered shape. The
            // MOVE branch still writes floatFrac: a deliberate gesture IS new intent.
            RectF seed = mLeaf.appliedFloatFrac != null ? mLeaf.appliedFloatFrac : mLeaf.floatFrac;
            mDownFrac = seed != null ? new RectF(seed) : defaultFloatFrac(0);
            if (mDragMode == DRAG_RESIZE) setSizeUpdatesPaused(true);
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        private void endDrag() {
            if (mDragMode == DRAG_RESIZE) setSizeUpdatesPaused(false);
            mDragMode = DRAG_NONE;
            mDragMoved = false;
            mDownFrac = null;
        }

        // --- Pill actions ---

        /** Ordered action slots: floats offer dock + close, the scratchpad only close (hide). */
        private int pillActionCount() {
            return isScratchpadLeaf(mLeaf) ? 1 : 2;
        }

        private int pillActionForSlot(int slot) {
            if (pillActionCount() == 1) return PILL_ACTION_CLOSE;
            return slot == 0 ? PILL_ACTION_DOCK : PILL_ACTION_CLOSE;
        }

        /** The pill capsule, collapsed or grown to fit its action slots. */
        @NonNull private RectF pillRect() {
            float centerX = getWidth() / 2f;
            float centerY = dp(FLOAT_HANDLE_DP) / 2f;
            float width = mPillExpanded
                ? pillActionCount() * dp(FLOAT_PILL_BUTTON_DP) : dp(FLOAT_PILL_WIDTH_DP);
            float height = dp(FLOAT_PILL_HEIGHT_DP);
            return new RectF(centerX - width / 2f, centerY - height / 2f,
                centerX + width / 2f, centerY + height / 2f);
        }

        private int pillActionAt(float x, float y) {
            if (!mPillExpanded) return PILL_ACTION_NONE;
            RectF pill = pillRect();
            RectF hit = new RectF(pill);
            hit.inset(-dp(8), -dp(4));
            hit.top = 0f; // The whole handle-row height above the pill is fair game.
            if (!hit.contains(x, y)) return PILL_ACTION_NONE;
            int slot = (int) ((x - pill.left) / dp(FLOAT_PILL_BUTTON_DP));
            return pillActionForSlot(Math.max(0, Math.min(pillActionCount() - 1, slot)));
        }

        private void performPillAction(int action) {
            collapsePill();
            if (action == PILL_ACTION_CLOSE) closeFloat(mLeaf);
            else if (action == PILL_ACTION_DOCK) dockFloat(mLeaf);
        }

        private void togglePill() {
            if (mPillExpanded) {
                collapsePill();
                return;
            }
            mPillExpanded = true;
            invalidate();
            removeCallbacks(mCollapsePill);
            postDelayed(mCollapsePill, 4000);
        }

        private void collapsePill() {
            removeCallbacks(mCollapsePill);
            if (!mPillExpanded && mPressedPillAction == PILL_ACTION_NONE) return;
            mPillExpanded = false;
            mPressedPillAction = PILL_ACTION_NONE;
            invalidate();
        }

        /** Coalesce the resize drag into one final PTY resize, like divider drags do. */
        private void setSizeUpdatesPaused(boolean paused) {
            if (paused) beginHostSurfaceResize();
            else finishHostSurfaceResizeKeepingBottom();
        }

        private int dragModeAt(float x, float y) {
            if (x >= getWidth() - dp(FLOAT_GRIP_DP) && y >= getHeight() - dp(FLOAT_GRIP_DP))
                return DRAG_RESIZE;
            if (y <= dp(FLOAT_HANDLE_DP)) return DRAG_MOVE;
            return DRAG_NONE;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            int primary = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(getContext(), R.color.termux_primary));
            // One step lighter than the float's own slab, so the action strip reads as chrome
            // sitting on the float rather than as a hole punched through it.
            int panelHigh = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                ContextCompat.getColor(getContext(), R.color.termux_surface_panel_high));
            int outlineVariant = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorOutlineVariant,
                ContextCompat.getColor(getContext(), R.color.termux_outline_variant));
            boolean active = mActiveWindow != null && mActiveWindow.active == mLeaf;
            RectF pill = pillRect();
            float radius = pill.height() / 2f;
            int backdropAlpha = pillBackdropAlpha(mPillExpanded, active);
            if (backdropAlpha > 0) {
                mChromePaint.setStyle(Paint.Style.FILL);
                mChromePaint.setColor(ColorUtils.setAlphaComponent(panelHigh, backdropAlpha));
                canvas.drawRoundRect(pill, radius, radius, mChromePaint);
                mChromePaint.setStyle(Paint.Style.STROKE);
                mChromePaint.setStrokeWidth(Math.max(1f, dp(1f)));
                mChromePaint.setColor(ColorUtils.setAlphaComponent(outlineVariant, 0x66));
                canvas.drawRoundRect(pill, radius, radius, mChromePaint);
            }
            int chromeAlpha = active ? 200 : 90;
            mChromePaint.setStyle(Paint.Style.FILL);
            if (!mPillExpanded) {
                // With no slab behind it the grip is the whole affordance, so an inactive float's
                // grip needs a higher floor than the resize chevrons to read on busy output.
                mChromePaint.setColor(ColorUtils.setAlphaComponent(primary,
                    Math.max(chromeAlpha, 120)));
                mChromeScratch.set(pill.centerX() - dp(14), pill.centerY() - dp(1.8f),
                    pill.centerX() + dp(14), pill.centerY() + dp(1.8f));
                canvas.drawRoundRect(mChromeScratch, dp(1.8f), dp(1.8f), mChromePaint);
            } else {
                mChromePaint.setStyle(Paint.Style.STROKE);
                mChromePaint.setStrokeWidth(dp(1.5f));
                mChromePaint.setStrokeCap(Paint.Cap.ROUND);
                int slots = pillActionCount();
                for (int slot = 0; slot < slots; slot++) {
                    int action = pillActionForSlot(slot);
                    int alpha = mPressedPillAction == action ? 255 : Math.max(chromeAlpha, 150);
                    mChromePaint.setColor(ColorUtils.setAlphaComponent(primary, alpha));
                    float slotCenterX = pill.left + (slot + 0.5f) * dp(FLOAT_PILL_BUTTON_DP);
                    float slotCenterY = pill.centerY();
                    if (action == PILL_ACTION_CLOSE) {
                        canvas.drawLine(slotCenterX - dp(4), slotCenterY - dp(4),
                            slotCenterX + dp(4), slotCenterY + dp(4), mChromePaint);
                        canvas.drawLine(slotCenterX + dp(4), slotCenterY - dp(4),
                            slotCenterX - dp(4), slotCenterY + dp(4), mChromePaint);
                    } else {
                        // Dock-back-to-tiling: a small window split down the middle.
                        mChromeScratch.set(slotCenterX - dp(6), slotCenterY - dp(4.5f),
                            slotCenterX + dp(6), slotCenterY + dp(4.5f));
                        canvas.drawRoundRect(mChromeScratch, dp(1.5f), dp(1.5f), mChromePaint);
                        canvas.drawLine(slotCenterX, mChromeScratch.top, slotCenterX,
                            mChromeScratch.bottom, mChromePaint);
                    }
                }
                if (slots > 1) {
                    mChromePaint.setColor(ColorUtils.setAlphaComponent(primary, 60));
                    float dividerX = pill.left + dp(FLOAT_PILL_BUTTON_DP);
                    canvas.drawLine(dividerX, pill.top + dp(4), dividerX, pill.bottom - dp(4),
                        mChromePaint);
                }
                mChromePaint.setStyle(Paint.Style.FILL);
            }
            mChromePaint.setStyle(Paint.Style.STROKE);
            mChromePaint.setStrokeWidth(dp(1.5f));
            mChromePaint.setStrokeCap(Paint.Cap.ROUND);
            mChromePaint.setColor(ColorUtils.setAlphaComponent(primary, chromeAlpha));
            float right = getWidth() - dp(4);
            float bottom = getHeight() - dp(4);
            canvas.drawLine(right - dp(10), bottom, right, bottom - dp(10), mChromePaint);
            canvas.drawLine(right - dp(5), bottom, right, bottom - dp(5), mChromePaint);
            mChromePaint.setStrokeCap(Paint.Cap.BUTT);
        }
    }

    /**
     * Alpha of the slab drawn behind the floating pane's grab pill. Zero while collapsed: there is
     * nothing to read against but the grip itself, and a filled capsule at that size looks like a
     * black border across the top of the float. Expanded, the close and dock glyphs do need a
     * surface, and an inactive float's is a touch more transparent so focus stays legible.
     */
    static int pillBackdropAlpha(boolean expanded, boolean activeFloat) {
        if (!expanded) return 0;
        return activeFloat ? 0xF0 : 0xD0;
    }

    // --- Tree helpers ---

    @Nullable private Leaf findLeafIn(@Nullable Node root, TerminalSession session) {
        for (Leaf leaf : leavesOf(root))
            if (leaf.session == session) return leaf;
        return null;
    }

    /** Every leaf of {@code w}: tiled tree leaves in order, then floats in z-order. */
    @NonNull private List<Leaf> allLeavesOf(@NonNull Window w) {
        List<Leaf> out = leavesOf(w.root);
        out.addAll(w.floating);
        return out;
    }

    @Nullable private Leaf findLeafInWindow(@NonNull Window w, TerminalSession session) {
        for (Leaf leaf : allLeavesOf(w))
            if (leaf.session == session) return leaf;
        return null;
    }

    private Leaf firstLeaf(Node node) {
        while (node instanceof Split) node = ((Split) node).a;
        return (Leaf) node;
    }

    private List<Leaf> leavesOf(@Nullable Node node) {
        List<Leaf> out = new ArrayList<>();
        collectLeaves(node, out);
        return out;
    }

    private void collectLeaves(@Nullable Node node, List<Leaf> out) {
        if (node == null) return;
        if (node instanceof Leaf) out.add((Leaf) node);
        else { collectLeaves(((Split) node).a, out); collectLeaves(((Split) node).b, out); }
    }

    private int[] center(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return new int[]{ loc[0] + v.getWidth() / 2, loc[1] + v.getHeight() / 2 };
    }

    private int dp(int dp) {
        return Math.round(mHostView.getResources().getDisplayMetrics().density * dp);
    }

    private float dp(float dp) {
        return mHostView.getResources().getDisplayMetrics().density * dp;
    }
}
