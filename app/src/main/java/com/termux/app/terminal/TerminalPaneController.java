package com.termux.app.terminal;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

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
        /** Default working directory when a cwd can't be derived. */
        String defaultCwd();
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
        final TerminalSession session;
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

    @Nullable private Window mActiveWindow;

    private static final int DIVIDER_DP = 1;

    public TerminalPaneController(Host host, FrameLayout hostView, LayoutInflater inflater) {
        mHost = host;
        mHostView = hostView;
        mInflater = inflater;
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

    /** Make {@code w} the visible window and render its pane tree. */
    public void showWindow(Window w) {
        if (w == null) return;
        mActiveWindow = w;
        render();
        mHost.onActivePaneChanged();
    }

    @Nullable public Window activeWindow() { return mActiveWindow; }

    /** The window whose tree contains {@code shell}, or null. */
    @Nullable public Window windowOf(@Nullable TerminalSession shell) {
        if (shell == null) return null;
        for (Window w : mWindows)
            for (Leaf leaf : leavesOf(w.root))
                if (leaf.session == shell) return w;
        return null;
    }

    /** All shells (leaves) of {@code w}. */
    public List<TerminalSession> shellsOf(Window w) {
        List<TerminalSession> out = new ArrayList<>();
        if (w != null) for (Leaf leaf : leavesOf(w.root)) out.add(leaf.session);
        return out;
    }

    /** The focused shell of {@code w} (its representative for the drawer). */
    @Nullable public TerminalSession windowActiveSession(@Nullable Window w) {
        return w == null || w.active == null ? null : w.active.session;
    }

    /** Remove a whole window (all panes). Returns its shells so the caller can kill them. */
    public List<TerminalSession> removeWindow(Window w) {
        List<TerminalSession> sessions = new ArrayList<>();
        if (w == null) return sessions;
        for (Leaf leaf : leavesOf(w.root)) {
            sessions.add(leaf.session);
            detachPaneView(leaf.session);
        }
        mWindows.remove(w);
        if (mActiveWindow == w) mActiveWindow = null;
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

    /** All pane views currently rendered (leaves of the active window). */
    public List<TerminalView> getVisiblePaneViews() {
        List<TerminalView> out = new ArrayList<>();
        if (mActiveWindow == null) return out;
        for (Leaf leaf : leavesOf(mActiveWindow.root))
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

    /** The pane view showing {@code session}, if it is a leaf of the active window. */
    @Nullable public TerminalView getViewForSession(@Nullable TerminalSession session) {
        return session == null ? null : mPaneViews.get(session);
    }

    /** Focus the pane showing {@code session} within the active window. */
    public void focusSession(TerminalSession session) {
        if (mActiveWindow == null) return;
        Leaf leaf = findLeafIn(mActiveWindow.root, session);
        if (leaf != null) {
            mActiveWindow.active = leaf;
            updateActiveBorders();
            focusActiveView();
            mHost.onActivePaneChanged();
        }
    }

    /** Collapse every window back to its focused single pane, returning dropped shells to kill.
     *  Used when compatibility mode turns split panes off. */
    public List<TerminalSession> collapseAll() {
        List<TerminalSession> dropped = new ArrayList<>();
        for (Window w : mWindows) {
            TerminalSession keep = w.active != null ? w.active.session : firstLeaf(w.root).session;
            for (Leaf leaf : leavesOf(w.root)) {
                if (leaf.session == keep) continue;
                dropped.add(leaf.session);
                detachPaneView(leaf.session);
            }
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
        Leaf oldLeaf = mActiveWindow.active;
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
            for (Leaf leaf : leavesOf(w.root))
                if (leaf.session == session) { owner = w; owningLeaf = leaf; break; }
            if (owningLeaf != null) break;
        }
        if (owningLeaf == null) return FINISHED_UNKNOWN;

        Split parent = owningLeaf.parent;
        if (parent == null) {
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
        render();
        return true;
    }

    // --- Rendering ---

    private void render() {
        mHostView.removeAllViews();
        if (mActiveWindow == null) return;
        View built = buildView(mActiveWindow.root);
        mHostView.addView(built, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateActiveBorders();
        focusActiveView();
    }

    private View buildView(Node node) {
        if (node instanceof Leaf) {
            return paneFrameFor(((Leaf) node).session);
        }
        Split split = (Split) node;
        LinearLayout ll = new LinearLayout(mHostView.getContext());
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
            if (!split) { frame.setForeground(null); continue; }
            boolean isActive = v.getCurrentSession() == activeSession;
            // Active pane: primary-tone border. Inactive panes: secondary-tone border so every
            // pane is delineated (both grounded in Material accents, not grey).
            frame.setForeground(ContextCompat.getDrawable(mHostView.getContext(),
                isActive ? R.drawable.pane_active_border : R.drawable.pane_inactive_border));
        }
    }

    private void focusActiveView() {
        TerminalView v = getActivePaneView();
        if (v != null && !v.isFocused()) v.requestFocus();
    }

    // --- Tree helpers ---

    @Nullable private Leaf findLeafIn(@Nullable Node root, TerminalSession session) {
        for (Leaf leaf : leavesOf(root))
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
}
