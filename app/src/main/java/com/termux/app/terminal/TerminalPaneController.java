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
 * Owns the recursive split-pane layout for the currently displayed tab.
 *
 * <p>Each drawer "tab" is one primary {@link TerminalSession} and owns a binary tree of panes
 * ({@link Node}); a leaf is a shell, an internal {@link Split} arranges two children along an
 * axis. Splitting replaces the focused leaf with a Split of {oldLeaf, newLeaf}, so any number of
 * panes / nesting depths are possible (tmux-style). Only the active tab's tree is rendered into
 * {@link #mHost}; other tabs keep their trees (and running shells) alive off-screen.
 *
 * <p>The windows layer (many windows per session) is not built yet and will wrap this: a window
 * will own a pane tree, a session will own windows.
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
        /** The set of tabs/panes changed; activity should rebuild the drawer. */
        void onTreesChanged();
        /** Default working directory when a cwd can't be derived. */
        String defaultCwd();
    }

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

    private final Host mHost;
    private final FrameLayout mHostView;
    private final LayoutInflater mInflater;

    /** tab primary session -> its pane tree root. */
    private final Map<TerminalSession, Node> mTrees = new HashMap<>();
    /** Cached pane frames + terminal views, keyed by shell session (reused across re-renders). */
    private final Map<TerminalSession, FrameLayout> mPaneFrames = new HashMap<>();
    private final Map<TerminalSession, TerminalView> mPaneViews = new HashMap<>();

    @Nullable private TerminalSession mActiveTab;   // primary session of the visible tab
    @Nullable private Leaf mActiveLeaf;             // focused pane within the visible tab

    private static final int DIVIDER_DP = 1;

    public TerminalPaneController(Host host, FrameLayout hostView, LayoutInflater inflater) {
        mHost = host;
        mHostView = hostView;
        mInflater = inflater;
    }

    // --- Queries ---

    @Nullable public TerminalSession getActiveSession() {
        return mActiveLeaf != null ? mActiveLeaf.session : null;
    }

    @Nullable public TerminalView getActivePaneView() {
        TerminalSession s = getActiveSession();
        return s == null ? null : mPaneViews.get(s);
    }

    @Nullable public TerminalSession getActiveTab() { return mActiveTab; }

    /** All pane views currently rendered (leaves of the active tab). */
    public List<TerminalView> getVisiblePaneViews() {
        List<TerminalView> out = new ArrayList<>();
        if (mActiveTab == null) return out;
        for (Leaf leaf : leavesOf(mTrees.get(mActiveTab)))
            if (mPaneViews.containsKey(leaf.session)) out.add(mPaneViews.get(leaf.session));
        return out;
    }

    /** The pane view showing {@code session}, if it is a leaf of the active tab. */
    @Nullable public TerminalView getViewForSession(@Nullable TerminalSession session) {
        return session == null ? null : mPaneViews.get(session);
    }

    /** Whether {@code session} is a non-primary pane of any tab (hidden from the drawer). */
    public boolean isSecondaryPane(@Nullable TerminalSession session) {
        if (session == null) return false;
        for (Map.Entry<TerminalSession, Node> e : mTrees.entrySet()) {
            if (e.getKey() == session) continue; // the primary itself
            for (Leaf leaf : leavesOf(e.getValue()))
                if (leaf.session == session) return true;
        }
        return false;
    }

    // --- Tab lifecycle ---

    /** Show a tab (primary session), creating a single-pane tree if it has none. */
    public void showTab(TerminalSession primary) {
        if (primary == null) return;
        Node tree = mTrees.get(primary);
        if (tree == null) {
            tree = new Leaf(primary);
            mTrees.put(primary, tree);
        }
        mActiveTab = primary;
        mActiveLeaf = firstLeaf(tree);
        render();
        mHost.onActivePaneChanged();
    }

    /** Focus the pane showing {@code session} (its tab must be shown first). */
    public void focusSession(TerminalSession session) {
        Leaf leaf = findLeaf(session);
        if (leaf != null) {
            mActiveLeaf = leaf;
            updateActiveBorders();
            focusActiveView();
            mHost.onActivePaneChanged();
        }
    }

    /** Remove a tab entirely (its whole pane tree). Caller removes the shells. */
    public List<TerminalSession> removeTab(TerminalSession primary) {
        List<TerminalSession> sessions = new ArrayList<>();
        Node tree = mTrees.remove(primary);
        if (tree != null)
            for (Leaf leaf : leavesOf(tree)) {
                sessions.add(leaf.session);
                detachPaneView(leaf.session);
            }
        if (mActiveTab == primary) { mActiveTab = null; mActiveLeaf = null; }
        return sessions;
    }

    // --- Pane operations ---

    /** Split the focused pane; new shell fills the new leaf. orientation = LinearLayout.*. */
    public void split(int orientation) {
        if (mActiveTab == null || mActiveLeaf == null) return;
        String cwd = mActiveLeaf.session.getCwd();
        TerminalSession newSession = mHost.createShell(cwd != null ? cwd : mHost.defaultCwd());
        if (newSession == null) return;

        Leaf oldLeaf = mActiveLeaf;
        Leaf newLeaf = new Leaf(newSession);
        Split split = new Split();
        split.orientation = orientation;
        split.a = oldLeaf;
        split.b = newLeaf;
        split.parent = oldLeaf.parent;
        oldLeaf.parent = split;
        newLeaf.parent = split;

        if (split.parent == null) {
            mTrees.put(mActiveTab, split);
        } else {
            if (split.parent.a == oldLeaf) split.parent.a = split; else split.parent.b = split;
        }
        mActiveLeaf = newLeaf;
        render();
        // A side-by-side split changes the old pane's column count; nudge the shell to redraw
        // cleanly (Ctrl+L) once the resize settles, avoiding a duplicated prompt.
        if (orientation == LinearLayout.HORIZONTAL) {
            final TerminalSession reflowed = oldLeaf.session;
            mHostView.postDelayed(() -> {
                if (reflowed.isRunning()) reflowed.write("");
            }, 250);
        }
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
    }

    /** Close the focused pane; returns its shell session so the caller can kill it. Null if it was
     *  the tab's last pane (caller should close the whole tab instead). */
    @Nullable public TerminalSession closeActivePane() {
        if (mActiveTab == null || mActiveLeaf == null) return null;
        Leaf leaf = mActiveLeaf;
        Split parent = leaf.parent;
        if (parent == null) {
            // Last pane in the tab.
            return null;
        }
        Node sibling = (parent.a == leaf) ? parent.b : parent.a;
        Split grand = parent.parent;
        sibling.parent = grand;
        if (grand == null) {
            mTrees.put(mActiveTab, sibling);
        } else {
            if (grand.a == parent) grand.a = sibling; else grand.b = sibling;
        }
        detachPaneView(leaf.session);
        mActiveLeaf = firstLeaf(sibling);
        render();
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
        return leaf.session;
    }

    /** Drop a finished shell's pane (e.g. user ran `exit`). Returns true if it was a known pane. */
    public boolean onSessionFinished(TerminalSession session) {
        // Find which tab owns it.
        TerminalSession owningTab = null;
        Leaf owningLeaf = null;
        for (Map.Entry<TerminalSession, Node> e : mTrees.entrySet()) {
            for (Leaf leaf : leavesOf(e.getValue()))
                if (leaf.session == session) { owningTab = e.getKey(); owningLeaf = leaf; break; }
            if (owningLeaf != null) break;
        }
        if (owningLeaf == null) return false;

        Split parent = owningLeaf.parent;
        if (parent == null) {
            // It was the tab's only pane -> the whole tab is gone; caller handles tab removal.
            return false;
        }
        Node sibling = (parent.a == owningLeaf) ? parent.b : parent.a;
        Split grand = parent.parent;
        sibling.parent = grand;
        boolean isPrimaryGone = (session == owningTab);
        if (grand == null) {
            mTrees.remove(owningTab);
            // If the removed leaf was the tab's primary (its drawer identity), re-key the tree
            // under a surviving leaf so the tab persists under a new primary.
            TerminalSession newPrimary = firstLeaf(sibling).session;
            mTrees.put(newPrimary, sibling);
            if (mActiveTab == owningTab) mActiveTab = newPrimary;
        } else {
            if (grand.a == parent) grand.a = sibling; else grand.b = sibling;
        }
        detachPaneView(session);
        if (mActiveTab != null && mActiveLeaf != null && mActiveLeaf.session == session)
            mActiveLeaf = firstLeaf(mTrees.get(mActiveTab));
        render();
        mHost.onActivePaneChanged();
        mHost.onTreesChanged();
        return true;
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
        if (mActiveLeaf == null) return true;
        boolean horizontalAxis = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        int wantOrientation = horizontalAxis ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
        // Walk up to the nearest ancestor split on the matching axis.
        Node node = mActiveLeaf;
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
        if (mActiveTab == null) return;
        Node root = mTrees.get(mActiveTab);
        if (root == null) return;
        View built = buildView(root);
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
        divider.setBackgroundColor(ContextCompat.getColor(mHostView.getContext(),
            android.R.color.transparent));
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
        for (TerminalView v : views) {
            FrameLayout frame = mPaneFrames.get(v.getCurrentSession());
            if (frame == null) continue;
            boolean isActive = split && mActiveLeaf != null && v.getCurrentSession() == mActiveLeaf.session;
            frame.setForeground(isActive
                ? ContextCompat.getDrawable(mHostView.getContext(), R.drawable.pane_active_border) : null);
        }
    }

    private void focusActiveView() {
        TerminalView v = getActivePaneView();
        if (v != null && !v.isFocused()) v.requestFocus();
    }

    // --- Tree helpers ---

    @Nullable private Leaf findLeaf(TerminalSession session) {
        if (mActiveTab == null) return null;
        for (Leaf leaf : leavesOf(mTrees.get(mActiveTab)))
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
