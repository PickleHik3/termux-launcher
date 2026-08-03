package com.termux.app.terminal;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.terminal.TerminalSession;
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
     * Cycle order for {@link #nextLayout()}. Deliberately not the documentation's listing order:
     * {@code stack} hides every unfocused pane, so it must not be where a single press from an
     * unmanaged window lands. It sits last instead.
     */
    private static final String[] LAYOUT_CYCLE = {
        LAYOUT_GRID, LAYOUT_TALL, LAYOUT_FAT, LAYOUT_HORIZONTAL, LAYOUT_VERTICAL, LAYOUT_STACK};

    public static final String EDGE_LEFT = "left";
    public static final String EDGE_RIGHT = "right";
    public static final String EDGE_UP = "up";
    public static final String EDGE_DOWN = "down";

    private static final String STATE_NODE_TYPE = "type";
    private static final String STATE_NODE_SESSION = "session";
    private static final String STATE_NODE_ORIENTATION = "orientation";
    private static final String STATE_NODE_WEIGHT_A = "weight_a";
    private static final String STATE_NODE_WEIGHT_B = "weight_b";
    private static final String STATE_NODE_A = "a";
    private static final String STATE_NODE_B = "b";
    private static final String STATE_WINDOW_ROOT = "root";
    private static final String STATE_WINDOW_ACTIVE = "active";
    private static final String STATE_WINDOW_LAYOUT = "layout_policy";
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

    /** Callbacks into the hosting activity. */
    public interface Host {
        /** Spawn a new shell rooted at {@code cwd} (or default cwd if null); null on failure. */
        @Nullable TerminalSession createShell(@Nullable String cwd);
        /** Wire client + font + text size + keep-screen-on onto a freshly created pane view. */
        void configurePaneView(TerminalView view);
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
        Leaf(TerminalSession session) { this.session = session; }
    }

    static final class Split extends Node {
        int orientation; // LinearLayout.HORIZONTAL (side by side) / VERTICAL (stacked)
        Node a, b;
        float weightA = 1f, weightB = 1f;
    }

    /** A window = one pane tree + which leaf is focused within it. Stable identity (object). */
    public static final class Window {
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
         * Panes detached from the tiled tree into freely positioned floats. List order is z-order
         * (last on top). A float always coexists with a non-empty tiled tree: the last tiled pane
         * can never float, and a dying tiled root promotes a float back into the tree.
         */
        final List<Leaf> floating = new ArrayList<>();
        Window(Leaf leaf) { root = leaf; active = leaf; }
    }

    private final Host mHost;
    private final FrameLayout mHostView;
    private final LayoutInflater mInflater;

    /** All live windows (across every session). */
    private final List<Window> mWindows = new ArrayList<>();
    /** Cached pane frames + terminal views, keyed by shell session (reused across re-renders). */
    private final Map<TerminalSession, FrameLayout> mPaneFrames = new HashMap<>();
    private final Map<TerminalSession, TerminalView> mPaneViews = new HashMap<>();
    private final Map<Split, LinearLayout> mSplitLayouts = new HashMap<>();
    /** Floating chrome containers of the rendered window, rebuilt on every render. */
    private final Map<Leaf, FloatingPaneContainer> mFloatContainers = new HashMap<>();
    private final PaneInteractionOverlay mInteractionOverlay;

    @Nullable private Window mActiveWindow;
    @Nullable private Leaf mMaximizedLeaf;

    private static final int DIVIDER_DP = 1;

    public TerminalPaneController(Host host, FrameLayout hostView, LayoutInflater inflater) {
        mHost = host;
        mHostView = hostView;
        mInflater = inflater;
        mInteractionOverlay = new PaneInteractionOverlay();
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
        mWindows.add(w);
        return w;
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
            snapshotWorkspaceNode(window.root, capture), floats);
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
        mWindows.add(window);
        return window;
    }

    @NonNull
    private Bundle saveNode(@NonNull Node node) {
        Bundle state = new Bundle();
        if (node instanceof Leaf) {
            state.putInt(STATE_NODE_TYPE, NODE_LEAF);
            state.putString(STATE_NODE_SESSION, ((Leaf) node).session.mHandle);
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
            return session == null || windowOf(session) != null ? null : new Leaf(session);
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

    /** Re-measure every visible pane once layout settles. Returning from another app can leave the
     *  panes measured against a stale (tiny) host size; posting updateSize after the next layout
     *  pass recomputes rows/cols against the restored full size. */
    public void refreshPaneSizes() {
        for (TerminalView v : getVisiblePaneViews())
            v.post(v::updateSize);
    }

    /** Coalesce a host-surface animation into one final PTY resize. */
    public void beginHostSurfaceResize() {
        setPaneSizeUpdatesPaused(true);
    }

    /** Finish a host resize while keeping prompt/content attached to the bottom edge. */
    public void finishHostSurfaceResizeKeepingBottom() {
        for (TerminalView view : getVisiblePaneViews()) {
            view.setTerminalSizeUpdatesPaused(false, true);
        }
    }

    /** The pane view showing {@code session}, if it is a leaf of the active window. */
    @Nullable public TerminalView getViewForSession(@Nullable TerminalSession session) {
        return session == null ? null : mPaneViews.get(session);
    }

    /** Focus the pane showing {@code session} within the active window. */
    public void focusSession(TerminalSession session) {
        if (mActiveWindow == null) return;
        Leaf leaf = findLeafInWindow(mActiveWindow, session);
        if (leaf != null) {
            mActiveWindow.active = leaf;
            if (mMaximizedLeaf != null) mMaximizedLeaf = leaf;
            bringFloatToFront(mActiveWindow, leaf);
            updateActiveBorders();
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

    /** Split the focused pane; new shell fills the new leaf. orientation = LinearLayout.*. */
    public void split(int orientation) {
        if (mActiveWindow == null || mActiveWindow.active == null) return;
        mMaximizedLeaf = null;
        Leaf oldLeaf = mActiveWindow.active;
        // A floating pane shares no divider, so splitting while one is focused splits the tiled
        // area instead of corrupting the tree with a leaf that lives outside it.
        if (mActiveWindow.floating.contains(oldLeaf)) oldLeaf = firstLeaf(mActiveWindow.root);
        String cwd = oldLeaf.session.getCwd();
        TerminalSession newSession = mHost.createShell(cwd != null ? cwd : mHost.defaultCwd());
        if (newSession == null) return;

        Leaf newLeaf = new Leaf(newSession);
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
        mActiveWindow.active = newLeaf;
        // A managed window re-tiles around the new pane instead of keeping the binary split that
        // insertion just produced. This is what makes the layout a policy rather than a one-shot.
        reapplyLayoutPolicy(mActiveWindow);
        render();
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

    /** Focus the pane nearest to the active one in the arrow direction (KeyEvent.KEYCODE_DPAD_*). */
    public boolean focusDirection(int keyCode) {
        TerminalView active = getActivePaneView();
        if (active == null) return true;
        List<TerminalView> views = getVisiblePaneViews();
        if (views.size() < 2) return true;
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
                default: return true;
            }
            if (!match) continue;
            int score = primary + secondary * 2;
            if (score < bestScore) { bestScore = score; best = v; }
        }
        if (best != null) {
            TerminalSession s = best.getCurrentSession();
            if (s != null) focusSession(s);
        }
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
        clearLayoutPolicy(mActiveWindow);
        render();
        return true;
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
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
        return FLOAT_TOGGLE_FLOATED;
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
     * {@link WindowSessionName#MAX_CODE_POINTS}. Existing shells keep this name for the life of
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
        mMaximizedLeaf = null;
        window.floating.add(leaf);
        window.active = leaf;
        mPendingFloatEntryLeaf = leaf;
        render();
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
            removeFloatContainer(leaf);
            if (window.active == leaf)
                window.active = window.root != null ? firstLeaf(window.root) : null;
            if (window == mActiveWindow) {
                render();
                mHost.onActivePaneChanged();
            }
            mHost.onTreesChanged();
        };
        FloatingPaneContainer container = mFloatContainers.get(leaf);
        if (container == null || !arePaneAnimationsEnabled()) {
            remove.run();
            return;
        }
        mHidingScratchpadLeaf = leaf;
        container.animate().cancel();
        container.animate()
            .alpha(0f)
            .translationY(dp(10))
            .scaleX(0.97f).scaleY(0.97f)
            .setDuration(SCRATCHPAD_HIDE_DURATION_MS)
            .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
            .withEndAction(remove)
            .start();
    }

    private boolean arePaneAnimationsEnabled() {
        try {
            return android.provider.Settings.Global.getFloat(
                mHostView.getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Throwable t) {
            return true;
        }
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
            .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
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
        split.orientation = mHostView.getWidth() >= mHostView.getHeight()
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
     * Clamp fractional float bounds against a host size: no smaller than the minimum pane size,
     * no larger than the host, and always leaving at least {@code minVisiblePx} of the drag
     * handle row on screen so a float can never be pushed somewhere it cannot be grabbed from.
     */
    @NonNull
    static RectF clampFloatFractions(@NonNull RectF candidate, float hostWidth, float hostHeight,
                                     float minWidthPx, float minHeightPx, float minVisiblePx) {
        if (hostWidth <= 0f || hostHeight <= 0f) return new RectF(candidate);
        float width = Math.min(1f, Math.max(Math.min(minWidthPx / hostWidth, 1f), candidate.width()));
        float height = Math.min(1f, Math.max(Math.min(minHeightPx / hostHeight, 1f), candidate.height()));
        float minVisibleX = Math.min(minVisiblePx / hostWidth, width);
        float minVisibleY = Math.min(minVisiblePx / hostHeight, height);
        float left = Math.max(minVisibleX - width, Math.min(1f - minVisibleX, candidate.left));
        // The handle is the top edge, so the top may never leave the host upward at all.
        float top = Math.max(0f, Math.min(1f - minVisibleY, candidate.top));
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
            leaf.floatFrac = frac;
        }
        FrameLayout.LayoutParams params = container.getLayoutParams() instanceof FrameLayout.LayoutParams
            ? (FrameLayout.LayoutParams) container.getLayoutParams()
            : new FrameLayout.LayoutParams(0, 0);
        params.width = Math.round(frac.width() * hostWidth);
        params.height = Math.round(frac.height() * hostHeight);
        params.leftMargin = Math.round(frac.left * hostWidth);
        params.topMargin = Math.round(frac.top * hostHeight);
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
        mHostView.removeAllViews();
        mSplitLayouts.clear();
        mFloatContainers.clear();
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
            for (Leaf leaf : mActiveWindow.floating) {
                FloatingPaneContainer container = new FloatingPaneContainer(leaf);
                mFloatContainers.put(leaf, container);
                mHostView.addView(container, new FrameLayout.LayoutParams(0, 0));
                applyFloatBounds(leaf, container);
                maybeAnimateFloatEntry(leaf, container);
            }
        }
        updateActiveBorders();
        focusActiveView();
        mHost.onPanesRendered();
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
        ll.addView(divider, new LinearLayout.LayoutParams(
            vertical ? match : dp(DIVIDER_DP), vertical ? dp(DIVIDER_DP) : match));
        ll.addView(vb, new LinearLayout.LayoutParams(
            vertical ? match : 0, vertical ? 0 : match, split.weightB));
        return ll;
    }

    private FrameLayout paneFrameFor(TerminalSession session) {
        FrameLayout frame = mPaneFrames.get(session);
        if (frame == null) {
            frame = (FrameLayout) mInflater.inflate(R.layout.view_terminal_pane, mHostView, false);
            TerminalView view = frame.findViewById(R.id.terminal_view);
            mHost.configurePaneView(view);
            view.setOnTouchListener((v, ev) -> {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    TerminalSession s = ((TerminalView) v).getCurrentSession();
                    if (s != null) focusSession(s);
                }
                return false;
            });
            view.attachSession(session);
            mPaneFrames.put(session, frame);
            mPaneViews.put(session, view);
        } else if (frame.getParent() instanceof ViewGroup) {
            ((ViewGroup) frame.getParent()).removeView(frame);
        }
        return frame;
    }

    private void detachPaneView(TerminalSession session) {
        FrameLayout frame = mPaneFrames.remove(session);
        mPaneViews.remove(session);
        if (frame != null && frame.getParent() instanceof ViewGroup)
            ((ViewGroup) frame.getParent()).removeView(frame);
    }

    private void updateActiveBorders() {
        List<TerminalView> views = getVisiblePaneViews();
        boolean split = views.size() > 1;
        TerminalSession activeSession = getActiveSession();
        for (TerminalView v : views) {
            FrameLayout frame = mPaneFrames.get(v.getCurrentSession());
            if (frame == null) continue;
            if (!split && mMaximizedLeaf == null) { frame.setForeground(null); continue; }
            boolean isActive = v.getCurrentSession() == activeSession;
            // Same Material primary hue for every pane, but the focused pane's border is at full
            // strength while the rest are dimmed — an unambiguous, theme-proof focus cue.
            android.graphics.drawable.Drawable border =
                ContextCompat.getDrawable(mHostView.getContext(), R.drawable.pane_active_border);
            if (border != null) {
                border = border.mutate();
                border.setAlpha(isActive ? 255 : 64);
            }
            frame.setForeground(border);
        }
        // The float handle pill dims with focus like the pane borders do.
        for (FloatingPaneContainer container : mFloatContainers.values()) container.invalidate();
    }

    private void focusActiveView() {
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

    private void setPaneSizeUpdatesPaused(boolean paused) {
        for (TerminalView view : getVisiblePaneViews()) {
            view.setTerminalSizeUpdatesPaused(paused);
        }
    }

    /** Transparent interaction layer: generous border hit targets without thick layout dividers. */
    private final class PaneInteractionOverlay extends View {

        private static final int ACTION_NONE = -1;
        private static final int ACTION_MOVE_PANE = 0;
        private static final int ACTION_MAXIMIZE = 1;
        private static final int ACTION_CLOSE = 2;

        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
                        setPaneSizeUpdatesPaused(true);
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
                            swapPanePositions(source, target);
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
                        if (mDraggingDivider) setPaneSizeUpdatesPaused(false);
                        resetTouchState();
                        showControls(leaf);
                        if (resized) mHost.onTreesChanged();
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    if (mDraggingDivider) setPaneSizeUpdatesPaused(false);
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
            LinearLayout.LayoutParams a = (LinearLayout.LayoutParams) layout.getChildAt(0).getLayoutParams();
            LinearLayout.LayoutParams b = (LinearLayout.LayoutParams) layout.getChildAt(2).getLayoutParams();
            a.weight = split.weightA;
            b.weight = split.weightB;
            layout.getChildAt(0).setLayoutParams(a);
            layout.getChildAt(2).setLayoutParams(b);
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
                RectF rect = paneRect(leaf);
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
            RectF rect = paneRect(leaf);
            if (rect == null) return false;
            float threshold = dp(12);
            return Math.min(Math.min(Math.abs(x - rect.left), Math.abs(x - rect.right)),
                Math.min(Math.abs(y - rect.top), Math.abs(y - rect.bottom))) <= threshold;
        }

        @Nullable
        private RectF paneRect(@NonNull Leaf leaf) {
            FrameLayout frame = mPaneFrames.get(leaf.session);
            if (frame == null || frame.getParent() == null) return null;
            int[] frameLocation = location(frame);
            int[] hostLocation = location(mHostView);
            float left = frameLocation[0] - hostLocation[0];
            float top = frameLocation[1] - hostLocation[1];
            return new RectF(left, top, left + frame.getWidth(), top + frame.getHeight());
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
            RectF pane = mControlLeaf == null ? null : paneRect(mControlLeaf);
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
            int primary = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(getContext(), R.color.termux_primary));
            int tertiary = MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorTertiary, primary);
            if (mDraggingDivider) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(dp(3));
                mPaint.setColor(ColorUtils.setAlphaComponent(primary, 230));
                drawActiveDivider(canvas, mXSplit);
                drawActiveDivider(canvas, mYSplit);
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(tertiary);
                canvas.drawRoundRect(new RectF(mHandleX - dp(5), mHandleY - dp(2),
                    mHandleX + dp(5), mHandleY + dp(2)), dp(2), dp(2), mPaint);
                if (mXSplit != null && mYSplit != null) {
                    canvas.drawRoundRect(new RectF(mHandleX - dp(2), mHandleY - dp(5),
                        mHandleX + dp(2), mHandleY + dp(5)), dp(2), dp(2), mPaint);
                }
            }
            if (mBorderPressed && mBorderTapLeaf != null && !mDraggingDivider) {
                RectF border = paneRect(mBorderTapLeaf);
                if (border != null) {
                    border.inset(dp(1.5f), dp(1.5f));
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(dp(3));
                    mPaint.setColor(ColorUtils.setAlphaComponent(primary, 230));
                    canvas.drawRect(border, mPaint);
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(tertiary);
                    canvas.drawRoundRect(new RectF(mHandleX - dp(5), mHandleY - dp(2),
                        mHandleX + dp(5), mHandleY + dp(2)), dp(2), dp(2), mPaint);
                }
            }
            if (mMovingLeaf != null && mMoveTarget != null && mMoveTarget != mMovingLeaf) {
                RectF target = paneRect(mMoveTarget);
                if (target != null) {
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(dp(3));
                    mPaint.setColor(ColorUtils.setAlphaComponent(tertiary, 220));
                    canvas.drawRect(target, mPaint);
                }
            }
            if (mControlsShown && mControlLeaf != null && mControlProgress > 0f) {
                drawControls(canvas, primary, tertiary);
            }
        }

        private void drawActiveDivider(Canvas canvas, @Nullable Split split) {
            if (split == null) return;
            LinearLayout layout = mSplitLayouts.get(split);
            if (layout == null || layout.getChildCount() < 3) return;
            View divider = layout.getChildAt(1);
            int[] host = location(mHostView);
            int[] dividerLocation = location(divider);
            float left = dividerLocation[0] - host[0];
            float top = dividerLocation[1] - host[1];
            if (split.orientation == LinearLayout.HORIZONTAL) {
                float x = left + divider.getWidth() / 2f;
                canvas.drawLine(x, top, x, top + divider.getHeight(), mPaint);
            } else {
                float y = top + divider.getHeight() / 2f;
                canvas.drawLine(left, y, left + divider.getWidth(), y, mPaint);
            }
        }

        private void drawControls(Canvas canvas, int primary, int tertiary) {
            computeControlGeometry();
            if (mControlRect.isEmpty()) return;
            int surface = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorSurfacePanel,
                ContextCompat.getColor(getContext(), R.color.termux_surface_panel));
            RectF pane = paneRect(mControlLeaf);
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
            content.setBackgroundColor(MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorSurfacePanel,
                ContextCompat.getColor(getContext(), R.color.termux_surface_panel)));
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
            mDownFrac = mLeaf.floatFrac != null ? new RectF(mLeaf.floatFrac) : defaultFloatFrac(0);
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
            TerminalView view = mPaneViews.get(mLeaf.session);
            if (view != null) view.setTerminalSizeUpdatesPaused(paused);
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
            int panel = MaterialColors.getColor(getContext(),
                com.termux.shared.R.attr.termuxColorSurfacePanel,
                ContextCompat.getColor(getContext(), R.color.termux_surface_panel));
            boolean active = mActiveWindow != null && mActiveWindow.active == mLeaf;
            RectF pill = pillRect();
            float radius = pill.height() / 2f;
            mChromePaint.setStyle(Paint.Style.FILL);
            mChromePaint.setColor(panel);
            canvas.drawRoundRect(pill, radius, radius, mChromePaint);
            int chromeAlpha = active ? 200 : 90;
            if (!mPillExpanded) {
                mChromePaint.setColor(ColorUtils.setAlphaComponent(primary, chromeAlpha));
                canvas.drawRoundRect(new RectF(pill.centerX() - dp(14),
                    pill.centerY() - dp(1.8f), pill.centerX() + dp(14),
                    pill.centerY() + dp(1.8f)), dp(1.8f), dp(1.8f), mChromePaint);
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
                        RectF tiles = new RectF(slotCenterX - dp(6), slotCenterY - dp(4.5f),
                            slotCenterX + dp(6), slotCenterY + dp(4.5f));
                        canvas.drawRoundRect(tiles, dp(1.5f), dp(1.5f), mChromePaint);
                        canvas.drawLine(slotCenterX, tiles.top, slotCenterX, tiles.bottom,
                            mChromePaint);
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
