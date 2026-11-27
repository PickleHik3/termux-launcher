package com.termux.app.tiling.desktop;

import android.content.Context;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.terminal.DwindleTilingPolicy;
import com.termux.app.terminal.TerminalPaneController;
import com.termux.app.tiling.DwindleTree;
import com.termux.app.tiling.LinuxWindowReturn;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.ScrollingColumns;
import com.termux.app.tiling.ScrollingLayoutPolicy;
import com.termux.app.tiling.TilingDesktopStore;
import com.termux.app.tiling.TilingHostedIds;
import com.termux.app.tiling.TilingInput;
import com.termux.app.tiling.TilingLivePolicy;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.TilingWorkspace;
import com.termux.app.tiling.TileGroup;
import com.termux.app.tiling.WindowKind;
import com.termux.app.tiling.WindowSpec;
import com.termux.app.tiling.WorkspaceLayout;
import com.termux.app.wall.BorderDrag;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tiling desktop's tracer (#418, behind the Terl switch): Home, which is v1's Home, then
 * Workspaces of terminal windows below it, each laid out by Scrolling or Dwindle (#420, its
 * {@link DwindleTree}). It joins the stack model
 * ({@link TilingStack}), the Scrolling geometry ({@link ScrollingColumns}), the view
 * ({@link TilingDesktopView}) and the activity ({@link Host}).
 *
 * <p>Each shell window's shell is drawn as one of the pane controller's hosted floats, the way a
 * Terminal widget's is ({@link TilingHostedIds} names it), so it types, selects and scrolls as any
 * pane does and takes the keyboard while focused. Only the Workspace in front and its neighbours,
 * and in each only the windows on screen and a screen either side, hold a terminal view
 * ({@link TilingLivePolicy}); the others' shells keep running with no view ("parked"), claimed
 * here so v1's Terminal never adopts them ({@link #claimsShell}).
 *
 * <p>Every other kind is a tile too (#419): a Linux app, the X11 Display and the Assistant open as
 * windows after the focused one, and draw through their kind's {@link TilingWindowContent}, which
 * the activity lends ({@link Host#content}). A Linux app outlives the launcher in the Linux windows
 * host; after a restart the host replays what it runs and each app slots back into its saved
 * window ({@link LinuxWindowReturn}); a saved window whose app is no longer running closes (it is
 * not launched again).
 *
 * <p>The saved form ({@link TilingDesktopStore}) is written a second after a change and at once
 * when the launcher pauses; a restart reads it back: a shell still running reattaches by its
 * handle, and one that is gone opens again in its saved directory.
 */
public final class TilingDesktop implements TilingDesktopView.Listener {

    /** What the desktop needs from the activity. */
    public interface Host {
        @Nullable TerminalPaneController panes();

        /** Whether shells may be started now: the launcher's start has settled. */
        boolean shellsReady();

        /** The running shell with handle {@code handle}, or null. */
        @Nullable TerminalSession liveShell(@NonNull String handle);

        /** A new shell in {@code cwd} (null: the home directory), or null when none could start. */
        @Nullable TerminalSession createShell(@Nullable String cwd);

        void killShell(@NonNull TerminalSession shell);

        /**
         * Runs {@code close} at once, or after the user agrees when {@code shell} runs a program
         * other than itself (v1's pane close prompt).
         */
        void confirmClose(@NonNull TerminalSession shell, @NonNull Runnable close);

        /** Whether moves between places jump rather than slide. */
        boolean reducedMotion();

        /** What a window of {@code kind} shows, for every kind but a shell; null when this launcher has none. */
        @Nullable TilingWindowContent content(@NonNull WindowKind kind);
    }

    /**
     * Told after the desktop renders: the place in front, its windows or the focus may have
     * changed. The control bar reads the front through {@link #front} and friends and redraws
     * only when what it shows changed.
     */
    public interface FrontListener {
        void onFrontChanged();
    }

    private static final String LOG_TAG = "TilingDesktop";
    /** A terminal cell when no terminal is shown to measure, in dp (the default font's). */
    private static final float DEFAULT_CELL_WIDTH_DP = 7.2f;
    private static final float DEFAULT_CELL_HEIGHT_DP = 15f;
    /** How long after the last change the saved form is written. */
    static final long SAVE_DEBOUNCE_MS = 1000L;
    /**
     * The longest saved Linux windows wait for the host's replay after the desktop starts: a host
     * that runs but never replays must not leave them as empty frames for good.
     */
    static final long LINUX_REPLAY_BACKSTOP_MS = 5000L;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tiling-desktop-save");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * The saved form as last written in this process: an activity that comes back in the same
     * process reads it rather than the file, which may still be on its way to disk.
     */
    @Nullable private static volatile String sProcessCopy;

    @NonNull private final Host mHost;
    @NonNull private final TilingDesktopStore mStore;
    @NonNull private final Executor mWriter;
    @NonNull private final TilingDesktopView mView;
    private final boolean mHasHome;
    private final float mDensity;
    @NonNull private TilingStack mStack;
    /** Shell window id to the handle of its shell. */
    @NonNull private final Map<Long, String> mShells = new HashMap<>();
    /** Linux window id to the Linux windows host's id for the window it shows. */
    @NonNull private final Map<Long, Long> mLinux = new HashMap<>();
    /**
     * Saved Linux windows waiting, after a restart, for the host's replay ({@link #linuxReplayEnded}):
     * window id to the host id it showed, or {@link LinuxWindowReturn#NO_LINUX_ID}.
     */
    @NonNull private final Map<Long, Long> mLinuxWaiting = new HashMap<>();
    /** What the host replayed so far, matched when the replay ends. */
    @NonNull private final List<LinuxWindowReturn.Running> mReplayed = new ArrayList<>();
    /** The replay ended (or there was no host to replay): a replayed window is a new one now. */
    private boolean mLinuxSettled;
    /** Windows of the other kinds whose content is in their frame now. */
    @NonNull private final Set<Long> mAttached = new HashSet<>();
    /** The window of another kind holding the keyboard, or {@link TilingStack#NO_WINDOW}. */
    private long mContentFocus = TilingStack.NO_WINDOW;
    /** Each Workspace's Scrolling state as last laid out, by the Workspace itself (numbers move). */
    @NonNull private final Map<TilingWorkspace, ScrollingLayoutPolicy.State> mScrolling = new IdentityHashMap<>();
    /** Each Dwindle Workspace's split tree, by the Workspace itself (numbers move). */
    @NonNull private final Map<TilingWorkspace, DwindleTree> mDwindle = new IdentityHashMap<>();
    /** The Dwindle Workspace in front as last laid out, for its holds; null when it is not Dwindle. */
    @Nullable private DwindleTree.Tiles mFrontTiles;
    /** The Workspace's columns as a gap drag began, which every move of the drag resizes from. */
    @Nullable private ScrollingLayoutPolicy.State mGapBase;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Runnable mSaveNow = this::saveNow;
    @NonNull private final Runnable mReplayBackstop = this::linuxReplayBackstop;
    @NonNull private final RectF mRoom = new RectF();
    private boolean mDestroyed;
    @Nullable private FrontListener mFrontListener;

    /**
     * The desktop as it was left, or a first start: Home (when {@code hasHome}; without Home the
     * stack starts at 1), then Workspace 1 with one terminal. Its view is {@link #view()}, for the
     * activity to place.
     */
    public TilingDesktop(@NonNull Context context, @NonNull Host host, @NonNull TilingDesktopStore store,
                         boolean hasHome) {
        this(context, host, store, hasHome, WRITER);
    }

    /** {@link #TilingDesktop(Context, Host, TilingDesktopStore, boolean)} writing through {@code writer}. */
    @VisibleForTesting
    TilingDesktop(@NonNull Context context, @NonNull Host host, @NonNull TilingDesktopStore store,
                  boolean hasHome, @NonNull Executor writer) {
        mHost = host;
        mWriter = writer;
        mStore = store;
        mHasHome = hasHome;
        mDensity = context.getResources().getDisplayMetrics().density;
        String copy = sProcessCopy;
        TilingDesktopStore.Saved saved = copy != null ? TilingDesktopStore.decode(copy) : store.load();
        // Home is one level of the stack; paging among its pages stays v1's Home's own.
        int homePages = hasHome ? 1 : 0;
        if (saved != null) {
            mStack = saved.stack;
            mStack.setHomePageCount(homePages);
            mShells.putAll(saved.shells);
            // Linux windows wait for the host's replay; until then their frames stand empty.
            for (int number = 1; number <= mStack.workspaceCount(); number++) {
                TilingWorkspace workspace = mStack.workspace(number);
                if (workspace == null) continue;
                for (TilingWindow window : workspace.windows()) {
                    if (window.kind() != WindowKind.LINUX_APP) continue;
                    Long linuxId = saved.linux.get(window.id());
                    mLinuxWaiting.put(window.id(), linuxId != null ? linuxId : LinuxWindowReturn.NO_LINUX_ID);
                }
            }
        } else {
            mStack = TilingStack.firstStart(homePages, null);
        }
        mView = new TilingDesktopView(context, this, hasHome);
        int background = TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND];
        mView.setGroundColor(ColorUtils.blendARGB(background | 0xFF000000, 0xFF000000, 0.35f));
        mView.moveTo(currentLevel(), false);
        mHandler.postDelayed(mReplayBackstop, LINUX_REPLAY_BACKSTOP_MS);
        Logger.logInfo(LOG_TAG, "Started " + (saved != null ? "from the saved form" : "fresh") + " on "
            + mStack.current() + " with " + mStack.workspaceCount() + " Workspaces");
    }

    @NonNull
    public View view() {
        return mView;
    }

    // ---- reading ---------------------------------------------------------------------------------

    @VisibleForTesting
    @NonNull
    TilingStack stack() {
        return mStack;
    }

    /** The handle of window {@code windowId}'s shell, or null. */
    @VisibleForTesting
    @Nullable
    String handleOf(long windowId) {
        return mShells.get(windowId);
    }

    /** Forgets this process's copy of the saved form, as a new process starts without one. */
    @VisibleForTesting
    static void forgetProcessCopy() {
        sProcessCopy = null;
    }

    /** Whether a Workspace, not Home, is the place in front. */
    public boolean isWorkspaceInFront() {
        return !mStack.current().isHome();
    }

    /**
     * The kind of the window in front, or null on Home and in an empty Workspace: the control bar
     * shows a kind's own tools from it.
     */
    @Nullable
    public WindowKind frontKind() {
        return isWorkspaceInFront() ? mStack.frontKind() : null;
    }

    /** The window showing Linux host window {@code linuxId}, or {@link TilingStack#NO_WINDOW}. */
    @VisibleForTesting
    long windowOfLinux(long linuxId) {
        for (Map.Entry<Long, Long> entry : mLinux.entrySet()) {
            if (entry.getValue() == linuxId) return entry.getKey();
        }
        return TilingStack.NO_WINDOW;
    }

    /**
     * The control bar's Mouse mode, for the focused window of the Workspace in front: its content
     * switches between touchpad and direct touch ({@link TilingWindowContent#toggleMouseMode}).
     * False when nothing switched: Home, a shell, a kind with no mouse mode, a window not on screen.
     */
    public boolean toggleFrontMouseMode() {
        long id = frontFocusedWindowId();
        TilingWindow window = id == TilingStack.NO_WINDOW ? null : mStack.window(id);
        TilingWindowContent content = window == null || !mAttached.contains(id) ? null
            : mHost.content(window.kind());
        return content != null && content.toggleMouseMode(id, contentIdOf(window));
    }

    /**
     * The control bar's Keyboard, for the focused window of the Workspace in front when it is of a
     * kind with content of its own ({@link TilingWindowContent#toggleKeyboard}). False when the
     * window did not take it: a shell's keyboard is the launcher's own, which the caller toggles.
     */
    public boolean toggleFrontKeyboard() {
        long id = frontFocusedWindowId();
        TilingWindow window = id == TilingStack.NO_WINDOW ? null : mStack.window(id);
        TilingWindowContent content = window == null || !mAttached.contains(id) ? null
            : mHost.content(window.kind());
        return content != null && content.toggleKeyboard(id, contentIdOf(window));
    }

    /** Whoever reads the place in front ({@link FrontListener}); null for none. */
    public void setFrontListener(@Nullable FrontListener listener) {
        mFrontListener = listener;
    }

    /** The place in front: a Home page or a Workspace. */
    @NonNull
    public Place front() {
        return mStack.current();
    }

    /** The windows of the Workspace in front in the order they stand, Floats last; none on Home. */
    @NonNull
    public List<TilingWindow> frontWindows() {
        TilingWorkspace workspace = isWorkspaceInFront() ? mStack.workspace(currentLevel()) : null;
        return workspace == null ? Collections.emptyList() : workspace.windows();
    }

    /** The window whose icon stands for the Workspace in front; null on Home or when it is empty. */
    @Nullable
    public TilingWindow frontMainWindow() {
        TilingWorkspace workspace = isWorkspaceInFront() ? mStack.workspace(currentLevel()) : null;
        return workspace == null ? null : workspace.mainWindow();
    }

    /** The focused window of the Workspace in front, or {@link TilingStack#NO_WINDOW}. */
    public long frontFocusedWindowId() {
        return isWorkspaceInFront() ? mStack.focusedWindowId() : TilingStack.NO_WINDOW;
    }

    /** Focuses window {@code windowId}, going to its place; false when there is no such window. */
    public boolean focusWindow(long windowId) {
        if (mDestroyed || mStack.window(windowId) == null) return false;
        if (mStack.focusedWindowId() == windowId && isWorkspaceInFront()) return true;
        Place before = mStack.current();
        if (!mStack.focusWindow(windowId)) return false;
        if (!before.equals(mStack.current())) placeChanged();
        else focusChanged();
        return true;
    }

    /**
     * A new terminal stacked below the focused window (top over bottom), in its directory; where
     * the focused window's column is full it opens as a column of its own after it. Under Dwindle
     * it splits the focused tile (or joins it as a tab), as any new window does.
     */
    public void split() {
        if (mDestroyed) return;
        long focused = isWorkspaceInFront() ? mStack.focusedWindowId() : TilingStack.NO_WINDOW;
        openTerminal();
        long opened = mStack.focusedWindowId();
        if (focused == TilingStack.NO_WINDOW || opened == focused) return;
        if (mStack.stack(opened, focused)) focusChanged();
    }

    /** Whether {@code shell} is a window's of this desktop, shown or parked: v1 leaves it alone. */
    public boolean claimsShell(@Nullable TerminalSession shell) {
        return shell != null && shell.mHandle != null && mShells.containsValue(shell.mHandle);
    }

    // ---- the Workspace's layout (#420) -----------------------------------------------------------

    /** The layout of the Workspace in front, or null on Home. */
    @Nullable
    public WorkspaceLayout frontLayout() {
        TilingWorkspace workspace = isWorkspaceInFront() ? mStack.workspace(currentLevel()) : null;
        return workspace == null ? null : workspace.layout();
    }

    /**
     * Lays the Workspace in front out by {@code layout}: to Dwindle its windows split the room in
     * their order, each stacked window a tile of its own and tabs one tile; back to Scrolling each
     * tile is a full column. False on Home or when it already is.
     */
    public boolean setFrontLayout(@NonNull WorkspaceLayout layout) {
        TilingWorkspace workspace = isWorkspaceInFront() ? mStack.workspace(currentLevel()) : null;
        if (mDestroyed || workspace == null || workspace.layout() == layout) return false;
        if (mView.isResizingGap()) endDragOnChange();
        int number = currentLevel();
        mStack.setLayout(number, layout);
        // The state kept was the other layout's; the new one starts from the windows' order.
        mStack.setLayoutState(number, null);
        mScrolling.remove(workspace);
        mDwindle.remove(workspace);
        focusChanged();
        return true;
    }

    /** Switches the Workspace in front between Scrolling and Dwindle; false on Home. */
    public boolean toggleFrontLayout() {
        WorkspaceLayout layout = frontLayout();
        if (layout == null) return false;
        return setFrontLayout(layout == WorkspaceLayout.DWINDLE ? WorkspaceLayout.SCROLLING : WorkspaceLayout.DWINDLE);
    }

    // ---- the activity's moments ------------------------------------------------------------------

    /** The service is there: shells still running come back into their windows. */
    public void onServiceConnected() {
        render();
    }

    /** The start has settled ({@link Host#shellsReady}): windows without a shell get one. */
    public void onShellsReady() {
        render();
    }

    // ---- the other kinds' windows (#419) ----------------------------------------------------------

    /**
     * The Linux windows host has window {@code linuxId} of app {@code appId}. A new one (the user
     * opened an app) opens as a window after the focused one, in front; one the host
     * {@code replayed} after a restart waits for the replay's end to slot back into its saved
     * window. A window already shown here stays where it is.
     */
    public void linuxWindowOpened(long linuxId, @Nullable String appId, @Nullable String name, boolean replayed) {
        if (mDestroyed || windowOfLinux(linuxId) != TilingStack.NO_WINDOW) return;
        if (replayed && !mLinuxSettled) {
            mReplayed.add(new LinuxWindowReturn.Running(linuxId, appId));
            return;
        }
        WindowSpec spec = WindowSpec.linuxApp(appId == null ? "" : appId, name);
        if (replayed) {
            // Not asked for now: it joins the Workspace in front (else the waiting one) without moving there.
            mLinux.put(openQuietly(spec), linuxId);
            focusChanged();
            return;
        }
        openInFront(spec, linuxId);
    }

    /**
     * The host's replay ended (or there is no host, so nothing will replay): each running app
     * slots back into its saved window, a saved window whose app is no longer running closes, and
     * an app no saved window takes joins as a new window.
     */
    public void linuxReplayEnded() {
        if (mDestroyed) return;
        if (mLinuxSettled && mReplayed.isEmpty()) return;
        List<LinuxWindowReturn.Saved> waiting = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : mLinuxWaiting.entrySet()) {
            TilingWindow window = mStack.window(entry.getKey());
            if (window == null) continue;
            waiting.add(new LinuxWindowReturn.Saved(window.id(), window.appId(), entry.getValue()));
        }
        LinuxWindowReturn.Result result = LinuxWindowReturn.match(waiting, mReplayed);
        mLinuxWaiting.clear();
        mReplayed.clear();
        mLinuxSettled = true;
        for (Map.Entry<Long, Long> entry : result.returned.entrySet()) mLinux.put(entry.getValue(), entry.getKey());
        for (LinuxWindowReturn.Running window : result.unclaimed) {
            mLinux.put(openQuietly(WindowSpec.linuxApp(window.appId, null)), window.linuxId);
        }
        int before = currentLevel();
        boolean closed = false;
        for (long id : result.gone) closed |= mStack.close(id);
        if (closed && currentLevel() != before) placeChanged();
        else focusChanged();
        Logger.logInfo(LOG_TAG, "Linux windows back: " + result.returned.size() + " returned, "
            + result.gone.size() + " gone, " + result.unclaimed.size() + " new");
    }

    /** The replay did not come in time ({@link #LINUX_REPLAY_BACKSTOP_MS}): the saved Linux windows stop waiting. */
    private void linuxReplayBackstop() {
        if (mLinuxSettled) return;
        Logger.logWarn(LOG_TAG, "No Linux windows replay in " + LINUX_REPLAY_BACKSTOP_MS + " ms; saved Linux windows close");
        linuxReplayEnded();
    }

    /**
     * Linux host window {@code linuxId}'s app id or name changed (an app that names itself after
     * it appears): its window keeps them, so the saved form matches it after a restart.
     */
    public void linuxWindowChanged(long linuxId, @Nullable String appId, @Nullable String name) {
        long id = windowOfLinux(linuxId);
        TilingWindow window = id == TilingStack.NO_WINDOW ? null : mStack.window(id);
        if (window == null) return;
        String app = appId == null ? "" : appId;
        boolean changed = false;
        if (!app.equals(window.appId() == null ? "" : window.appId())) changed |= mStack.setAppId(id, app);
        if (name != null && !name.equals(window.name())) changed |= mStack.renameWindow(id, name);
        if (changed) scheduleSave();
    }

    /** The Linux host window ids this desktop's windows show. */
    @NonNull
    public java.util.Collection<Long> linuxWindowIds() {
        return new ArrayList<>(mLinux.values());
    }

    /** Linux host window {@code linuxId} is gone (its app closed it or ended): its window closes. */
    public void linuxWindowGone(long linuxId) {
        long id = windowOfLinux(linuxId);
        if (id != TilingStack.NO_WINDOW) closeWindow(id);
    }

    /**
     * Every Linux window the host showed is gone (it stopped or died): their windows close. Saved
     * windows still waiting for a replay keep waiting.
     */
    public void linuxWindowsGone() {
        for (long id : new ArrayList<>(mLinux.keySet())) closeWindow(id);
    }

    /** Goes to the window showing Linux host window {@code linuxId}; false when none does. */
    public boolean showLinuxWindow(long linuxId) {
        long id = windowOfLinux(linuxId);
        if (id == TilingStack.NO_WINDOW) return false;
        goToWindow(id);
        return true;
    }

    /** Goes to the X11 Display's window, opening it after the focused window when there is none. */
    public void openDisplay() {
        openOne(WindowKind.DISPLAY);
    }

    /** Goes to the Assistant's window, opening it after the focused window when there is none. */
    public void openAssistant() {
        openOne(WindowKind.ASSISTANT);
    }

    /** The one window of {@code kind}: gone to when there is one, else opened in front. */
    private void openOne(@NonNull WindowKind kind) {
        if (mDestroyed) return;
        TilingWindow existing = mStack.firstOfKind(kind);
        if (existing != null) {
            goToWindow(existing.id());
            return;
        }
        openInFront(kind == WindowKind.DISPLAY ? WindowSpec.display() : WindowSpec.assistant(),
            LinuxWindowReturn.NO_LINUX_ID);
    }

    /** Opens {@code spec} after the focused window and goes there (from Home: the waiting Workspace). */
    private void openInFront(@NonNull WindowSpec spec, long linuxId) {
        Place before = mStack.current();
        long id = openTiled(spec);
        if (linuxId != LinuxWindowReturn.NO_LINUX_ID) mLinux.put(id, linuxId);
        if (!before.equals(mStack.current())) placeChanged();
        else focusChanged();
    }

    /** Focuses window {@code id} and goes to its place. */
    private void goToWindow(long id) {
        int before = currentLevel();
        boolean wasHome = !isWorkspaceInFront();
        if (!mStack.focusWindow(id)) return;
        if (wasHome || currentLevel() != before) placeChanged();
        else focusChanged();
    }

    /**
     * Opens {@code spec} in front as {@link TilingStack#open} does; under Dwindle it then joins the
     * window that had focus as a tab when a split would leave either too small.
     */
    private long openTiled(@NonNull WindowSpec spec) {
        long focused = isWorkspaceInFront() ? mStack.focusedWindowId() : TilingStack.NO_WINDOW;
        long id = mStack.open(spec);
        joinAsTabIfTooSmall(focused, id);
        return id;
    }

    /** Opens {@code spec} where a window nobody asked for joins ({@link #quietPlace}), as {@link #openTiled} does. */
    private long openQuietly(@NonNull WindowSpec spec) {
        Place place = quietPlace();
        TilingWorkspace workspace = mStack.workspace(place.workspaceNumber());
        TilingWindow focused = workspace == null ? null : workspace.focusedWindow();
        long id = mStack.openIn(place, spec, false);
        joinAsTabIfTooSmall(focused == null ? TilingStack.NO_WINDOW : focused.id(), id);
        return id;
    }

    /**
     * Under Dwindle, window {@code opened} (just opened after {@code focused}) splits
     * {@code focused}'s tile along its longer side, unless either half would be too small for its
     * window ({@link DwindleTilingPolicy#placeNewWindow}): then it joins that tile as a tab.
     */
    private void joinAsTabIfTooSmall(long focused, long opened) {
        Place place = mStack.placeOf(opened);
        TilingWindow before = focused == TilingStack.NO_WINDOW ? null : mStack.window(focused);
        TilingWindow window = mStack.window(opened);
        if (place == null || place.isHome() || before == null || window == null || focused == opened
            || before.isFloat()) return;
        TilingWorkspace workspace = mStack.workspace(place.workspaceNumber());
        if (workspace == null || workspace.layout() != WorkspaceLayout.DWINDLE) return;
        ScrollingLayoutPolicy.Viewport viewport = viewport();
        // The tree as it was before the window came, to measure the focused tile.
        List<DwindleTree.Tile> tiles = new ArrayList<>();
        for (DwindleTree.Tile tile : DwindleTree.tilesOf(workspace)) {
            if (!(tile.windows.length == 1 && tile.windows[0] == opened)) tiles.add(tile);
        }
        DwindleTree tree = treeOf(workspace);
        tree.sync(tiles, viewport.width, viewport.height, viewport.gap);
        float stripPx = TilingDesktopView.TAB_STRIP_DP * mDensity;
        ScrollingLayoutPolicy.Placement tile = null;
        TileGroup group = null;
        for (TileGroup g : workspace.groups()) if (g.windows().contains(before)) group = g;
        if (group == null) return;
        for (ScrollingLayoutPolicy.Placement p : tree.layout(viewport.width, viewport.height, viewport.gap,
            stripPx).layout.placements) {
            if (p.windowId == group.active().id()) tile = p;
        }
        if (tile == null) return;
        float focusedMinWidth = 0f;
        float focusedMinHeight = 0f;
        for (TilingWindow member : group.windows()) {
            focusedMinWidth = Math.max(focusedMinWidth, minWidth(member));
            focusedMinHeight = Math.max(focusedMinHeight, minHeight(member));
        }
        DwindleTilingPolicy.Placement placement = DwindleTilingPolicy.placeNewWindow(tile.width(),
            tile.height(), focusedMinWidth, focusedMinHeight, minWidth(window), minHeight(window), viewport.gap);
        if (placement.isTab()) mStack.joinTab(opened, focused);
    }

    /** The narrowest a window of its kind is usable, in px: a terminal of {@link DwindleTilingPolicy#TERMINAL_MIN_COLUMNS}. */
    private float minWidth(@NonNull TilingWindow window) {
        if (window.kind() != WindowKind.SHELL) return 0f;
        return DwindleTilingPolicy.minWidthForColumns(DwindleTilingPolicy.TERMINAL_MIN_COLUMNS, cellSize()[0]);
    }

    /** The shortest a window of its kind is usable, in px: a terminal of {@link DwindleTilingPolicy#TERMINAL_MIN_ROWS}. */
    private float minHeight(@NonNull TilingWindow window) {
        if (window.kind() != WindowKind.SHELL) return 0f;
        return DwindleTilingPolicy.minHeightForRows(DwindleTilingPolicy.TERMINAL_MIN_ROWS, cellSize()[1]);
    }

    /** A terminal cell's width and height in px, from a shown terminal, else the default font's. */
    @NonNull
    private float[] cellSize() {
        TerminalPaneController panes = mHost.panes();
        TerminalView view = panes == null ? null : panes.getActivePaneView();
        if (view != null && view.mRenderer != null && view.mRenderer.getFontWidth() > 0f) {
            return new float[] {view.mRenderer.getFontWidth(), view.mRenderer.getFontLineSpacing()};
        }
        return new float[] {DEFAULT_CELL_WIDTH_DP * mDensity, DEFAULT_CELL_HEIGHT_DP * mDensity};
    }

    /** Where a window nobody asked for joins: the Workspace in front, else the waiting one. */
    @NonNull
    private Place quietPlace() {
        return isWorkspaceInFront() ? mStack.current() : Place.workspace(mStack.workspaceCount());
    }

    /** A kind's content changed under its windows (the display's frame was rebuilt): they draw it again. */
    public void onContentChanged() {
        render();
    }

    /** The launcher pauses or stops: the saved form is written now. */
    public void onPause() {
        saveNow();
    }

    /** The activity goes: the saved form is written; the shells keep running in the service. */
    public void onDestroy() {
        if (mDestroyed) return;
        saveNow();
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * A hosted float's shell ended on its own; true when it was one of this desktop's windows,
     * which then closes.
     */
    public boolean onHostedShellEnded(int hostedId) {
        if (!TilingHostedIds.isTilingWindow(hostedId)) return false;
        closeWindow(TilingHostedIds.windowId(hostedId));
        return true;
    }

    /**
     * A shell ended ({@code TerminalHost.onShellFinished}, before v1 drops anything). A parked
     * window's shell has no pane for v1's finish path to close, so its window closes here; a
     * shown window's closes through {@link #onHostedShellEnded}. True when it was a parked one.
     */
    public boolean onShellFinished(@NonNull TerminalSession shell) {
        long id = windowOfShell(shell);
        if (id == TilingStack.NO_WINDOW) return false;
        TerminalPaneController panes = mHost.panes();
        if (panes != null && panes.hostedFloatFor(TilingHostedIds.hostedId(id)) != null) return false;
        closeWindow(id);
        return true;
    }

    // ---- input -----------------------------------------------------------------------------------

    /** A hardware key ({@link TilingInput#forKey}); true when the stack took it. */
    public boolean onKeyEvent(@NonNull KeyEvent event) {
        TilingInput.Command command = TilingInput.forKey(event.getKeyCode(), event.isCtrlPressed(),
            event.isAltPressed(), event.isShiftPressed(), event.isMetaPressed());
        if (command == TilingInput.Command.NONE) return false;
        // The press runs the move; its repeat and its lift are taken too, so nothing else sees them.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) run(command);
        return true;
    }

    /** One of v1's tools ({@link TilingInput#forTool}); true when the stack took it. */
    public boolean onTool(@Nullable String tool) {
        TilingInput.Command command = TilingInput.forTool(tool, isWorkspaceInFront());
        if (command == TilingInput.Command.NONE) return false;
        run(command);
        return true;
    }

    /** The home press: Home in front. */
    public void home() {
        if (mStack.home()) placeChanged();
    }

    /** Runs {@code command}; always true for a command the stack owns, moved or not. */
    public boolean run(@NonNull TilingInput.Command command) {
        if (mDestroyed) return false;
        switch (command) {
            case LEFT:
                if (isWorkspaceInFront() && mStack.focusLeft()) focusChanged();
                return true;
            case RIGHT:
                if (isWorkspaceInFront() && mStack.focusRight()) focusChanged();
                return true;
            case UP:
                moveVertically(mStack.focusUp());
                return true;
            case DOWN:
                moveVertically(mStack.focusDown());
                return true;
            case NEW_TERMINAL:
                openTerminal();
                return true;
            case CLOSE:
                closeFocused();
                return true;
            case TOGGLE_LAYOUT:
                toggleFrontLayout();
                return true;
            default:
                return false;
        }
    }

    // ---- the view's listener ---------------------------------------------------------------------

    @Override
    public void onEdgeHold(@NonNull TilingInput.Zone zone) {
        if (zone.kind == TilingInput.Zone.Kind.TILE_GAP) {
            holdOnTileGap(zone);
            return;
        }
        if (zone.kind == TilingInput.Zone.Kind.COLUMN_GAP || zone.kind == TilingInput.Zone.Kind.STACK_GAP) {
            holdOnGap(zone);
            return;
        }
        stepToward(zone.side);
    }

    private void stepToward(@NonNull BorderDrag.Border side) {
        // Under Dwindle a hold steps by geometry: the nearest tile that way, else (up, down) the place.
        long next = tileToward(side);
        if (next != ScrollingLayoutPolicy.NO_WINDOW) {
            if (mStack.focusWindow(next)) focusChanged();
            return;
        }
        if (mFrontTiles != null && (side == BorderDrag.Border.LEFT || side == BorderDrag.Border.RIGHT)) return;
        switch (side) {
            case LEFT: run(TilingInput.Command.LEFT); break;
            case RIGHT: run(TilingInput.Command.RIGHT); break;
            case TOP: run(TilingInput.Command.UP); break;
            case BOTTOM: run(TilingInput.Command.DOWN); break;
            default: break;
        }
    }

    /**
     * A hold on a gap acts on that gap: the window beside it on the held side takes the focus;
     * once it has it, a column gap goes on stepping that way, a stack gap has no further to go.
     */
    private void holdOnGap(@NonNull TilingInput.Zone zone) {
        ScrollingLayoutPolicy.State state = frontState();
        if (state == null) return;
        long target = TilingInput.gapTarget(state, zone);
        if (target == ScrollingLayoutPolicy.NO_WINDOW) return;
        if (target != state.focusedWindow) {
            if (mStack.focusWindow(target)) focusChanged();
        } else if (zone.kind == TilingInput.Zone.Kind.COLUMN_GAP) {
            stepToward(zone.side);
        }
    }

    /** The Dwindle tile nearest the focused one on {@code side}, or {@link ScrollingLayoutPolicy#NO_WINDOW}. */
    private long tileToward(@NonNull BorderDrag.Border side) {
        DwindleTree.Tiles tiles = mFrontTiles;
        long focused = mStack.focusedWindowId();
        if (tiles == null || focused == TilingStack.NO_WINDOW) return ScrollingLayoutPolicy.NO_WINDOW;
        return DwindleTree.neighbour(tiles.layout.placements, focused, side);
    }

    /** The Dwindle tile a hold on gap {@code zone} acts on, or {@link ScrollingLayoutPolicy#NO_WINDOW}. */
    private long tileGapTarget(@NonNull TilingInput.Zone zone) {
        DwindleTree.Tiles tiles = mFrontTiles;
        if (tiles == null || zone.column < 0 || zone.column >= tiles.layout.placements.size())
            return ScrollingLayoutPolicy.NO_WINDOW;
        return tiles.layout.placements.get(zone.column).windowId;
    }

    /** A hold on a gap between Dwindle tiles: the tile on the held side takes focus, then the next that way. */
    private void holdOnTileGap(@NonNull TilingInput.Zone zone) {
        long target = tileGapTarget(zone);
        if (target == ScrollingLayoutPolicy.NO_WINDOW) return;
        if (target != mStack.focusedWindowId()) {
            if (mStack.focusWindow(target)) focusChanged();
        } else {
            long next = tileToward(zone.side);
            if (next != ScrollingLayoutPolicy.NO_WINDOW && mStack.focusWindow(next)) focusChanged();
        }
    }

    @Override
    public boolean canStep(@NonNull TilingInput.Zone zone) {
        if (zone.kind == TilingInput.Zone.Kind.TILE_GAP) {
            long target = tileGapTarget(zone);
            if (target == ScrollingLayoutPolicy.NO_WINDOW) return false;
            return target != mStack.focusedWindowId() || tileToward(zone.side) != ScrollingLayoutPolicy.NO_WINDOW;
        }
        if (zone.kind == TilingInput.Zone.Kind.COLUMN_GAP || zone.kind == TilingInput.Zone.Kind.STACK_GAP) {
            ScrollingLayoutPolicy.State state = frontState();
            if (state == null) return false;
            long target = TilingInput.gapTarget(state, zone);
            if (target == ScrollingLayoutPolicy.NO_WINDOW) return false;
            if (target != state.focusedWindow) return true;
            return zone.kind == TilingInput.Zone.Kind.COLUMN_GAP && canStep(zone.side);
        }
        return canStep(zone.side);
    }

    private boolean canStep(@NonNull BorderDrag.Border side) {
        int level = currentLevel();
        if (mFrontTiles != null) {
            if (tileToward(side) != ScrollingLayoutPolicy.NO_WINDOW) return true;
            if (side == BorderDrag.Border.TOP) return level > (mHasHome ? 0 : 1);
            if (side == BorderDrag.Border.BOTTOM) return level < mStack.workspaceCount();
            return false;
        }
        switch (side) {
            case TOP:
                return level > (mHasHome ? 0 : 1) || focusedRow() == 1;
            case BOTTOM:
                return level < mStack.workspaceCount() || focusedRow() == 0 && focusedColumnHeight() > 1;
            case LEFT:
                return focusedColumn() > 0;
            case RIGHT: {
                ScrollingLayoutPolicy.State state = frontState();
                return state != null && focusedColumn() >= 0 && focusedColumn() < state.columnCount() - 1;
            }
            default:
                return false;
        }
    }

    @Override
    public void onWindowPressed(long windowId) {
        if (mStack.focusedWindowId() == windowId) return;
        if (mStack.focusWindow(windowId)) focusChanged();
    }

    @Override
    public void onTabPressed(long windowId) {
        // The tab's window shows in its tile and takes the focus.
        focusWindow(windowId);
    }

    @Override
    public void onNewTerminalPressed() {
        openTerminal();
    }

    @Override
    public void onRoomChanged() {
        render();
    }

    @Override
    public void onGapDragBegin(int leftColumn) {
        mGapBase = frontState();
    }

    @Override
    public void onGapDrag(int leftColumn, float deltaPx) {
        TilingWorkspace workspace = mStack.workspace(currentLevel());
        if (mDestroyed || mGapBase == null || workspace == null) return;
        if (windowsChanged(workspace)) {
            endDragOnChange();
            return;
        }
        // Each move resizes from the state the drag began in, so a clamp never drifts the gap off the finger.
        mScrolling.put(workspace, ScrollingLayoutPolicy.resizeFromGap(mGapBase, viewport(), leftColumn, deltaPx));
        render();
    }

    /** Whether {@code workspace} no longer holds the windows the running gap drag began with. */
    private boolean windowsChanged(@NonNull TilingWorkspace workspace) {
        if (mGapBase == null) return false;
        Set<Long> now = new HashSet<>();
        for (TilingWindow window : workspace.tiledWindows()) now.add(window.id());
        Set<Long> then = new HashSet<>();
        for (long id : mGapBase.windowIdsInOrder()) then.add(id);
        return !now.equals(then);
    }

    /** A window came or went mid-drag: the drag ends here, what it dragged stays, and the next render syncs. */
    private void endDragOnChange() {
        mView.endGapDrag();
        mGapBase = null;
        scheduleSave();
    }

    @Override
    public void onGapDragEnd() {
        if (mGapBase == null) return;
        mGapBase = null;
        scheduleSave();
    }

    @Override
    public void onGapSnap(int leftColumn) {
        ScrollingLayoutPolicy.State state = frontState();
        TilingWorkspace workspace = mStack.workspace(currentLevel());
        if (mDestroyed || state == null || workspace == null) return;
        mScrolling.put(workspace, ScrollingLayoutPolicy.doubleTapGap(state, viewport(), leftColumn));
        render();
        scheduleSave();
    }

    // ---- moves -----------------------------------------------------------------------------------

    private void moveVertically(boolean moved) {
        if (moved) placeChanged();
    }

    /** A new terminal after the focused window, in its directory; from Home in a new Workspace. */
    private void openTerminal() {
        String cwd = null;
        long focused = mStack.focusedWindowId();
        TilingWindow window = focused == TilingStack.NO_WINDOW ? null : mStack.window(focused);
        if (window != null && window.kind() == WindowKind.SHELL) {
            TerminalSession shell = shellOf(focused);
            cwd = shell != null ? shell.getCwd() : window.workingDirectory();
        }
        Place before = mStack.current();
        openTiled(WindowSpec.shell(cwd));
        if (!before.equals(mStack.current())) placeChanged();
        else focusChanged();
    }

    /** Closes the focused window, its shell with it. */
    /**
     * Closes the focused window and its shell, asking first as v1's pane close does when the
     * shell runs a program ({@link Host#confirmClose}); an idle or ended shell closes at once.
     */
    private void closeFocused() {
        if (!isWorkspaceInFront()) return;
        long id = mStack.focusedWindowId();
        if (id == TilingStack.NO_WINDOW) return;
        TilingWindow window = mStack.window(id);
        if (window != null && window.kind() != WindowKind.SHELL) {
            TilingWindowContent content = mHost.content(window.kind());
            long contentId = contentIdOf(window);
            // A Linux window still waiting for its app has nothing to ask: it closes.
            boolean waiting = window.kind() == WindowKind.LINUX_APP && contentId == LinuxWindowReturn.NO_LINUX_ID;
            if (waiting || content == null || content.close(id, contentId)) closeWindow(id);
            return;
        }
        TerminalSession shell = shellOf(id);
        if (shell == null) {
            closeWindow(id);
            return;
        }
        mHost.confirmClose(shell, () -> {
            // The answer may come after the window went another way, or its shell changed.
            if (mDestroyed || mStack.window(id) == null || shellOf(id) != shell) return;
            TerminalPaneController panes = mHost.panes();
            TerminalPaneController.Window hosted = panes == null ? null
                : panes.hostedFloatFor(TilingHostedIds.hostedId(id));
            if (hosted != null) panes.removeWindow(hosted);
            closeWindow(id);
            mHost.killShell(shell);
        });
    }

    /**
     * Takes window {@code id} out of the stack. An emptied Workspace above the one in front goes
     * and renumbers it, so the view follows the level in front whenever it moved.
     */
    private void closeWindow(long id) {
        mShells.remove(id);
        TilingWindow window = mStack.window(id);
        if (window != null && window.kind() != WindowKind.SHELL) letContentGo(window);
        mLinux.remove(id);
        mLinuxWaiting.remove(id);
        int before = currentLevel();
        if (!mStack.close(id)) return;
        if (currentLevel() != before) placeChanged();
        else focusChanged();
    }

    private void focusChanged() {
        render();
        scheduleSave();
    }

    private void placeChanged() {
        mView.moveTo(currentLevel(), !mHost.reducedMotion());
        render();
        scheduleSave();
    }

    // ---- rendering -------------------------------------------------------------------------------

    /**
     * Lays the live Workspaces' windows into their pages, gives each live window its shell and
     * parks the rest, then hands the keyboard to the focused window of the place in front.
     */
    private void render() {
        if (mDestroyed) return;
        ScrollingLayoutPolicy.Viewport viewport = viewport();
        int current = currentLevel();
        int count = mStack.workspaceCount();
        Set<Integer> liveLevels = new HashSet<>();
        Map<Long, TilingDesktopView.WindowFrame> liveFrames = new HashMap<>();
        Map<TilingWorkspace, ScrollingLayoutPolicy.State> states = new IdentityHashMap<>();
        Map<TilingWorkspace, DwindleTree> trees = new IdentityHashMap<>();
        mFrontTiles = null;
        for (int number = 1; number <= count; number++) {
            TilingWorkspace workspace = mStack.workspace(number);
            if (workspace == null) continue;
            if (workspace.layout() == WorkspaceLayout.DWINDLE) {
                renderDwindle(number, workspace, viewport, current, count, trees, liveLevels, liveFrames);
                continue;
            }
            ScrollingLayoutPolicy.State before = mScrolling.get(workspace);
            if (before == null) before = ScrollingColumns.decode(workspace.layoutState());
            // A gap drag lays out the dragged state as it is: sync would pull the focused column whole
            // and slide the gap out from under the finger.
            // If the Workspace's windows changed meanwhile, the drag ends, keeping what it dragged.
            if (number == current && mView.isResizingGap() && windowsChanged(workspace)) endDragOnChange();
            ScrollingLayoutPolicy.State state = number == current && mView.isResizingGap()
                && mScrolling.containsKey(workspace) ? before : ScrollingColumns.sync(workspace, before, viewport);
            states.put(workspace, state);
            if (!TilingLivePolicy.isLiveLevel(number, current, mHasHome, count)) continue;
            liveLevels.add(number);
            TilingDesktopView.Page page = mView.pageAt(number);
            page.setViewport();
            Set<Long> live = TilingLivePolicy.liveWindows(state, viewport);
            ScrollingLayoutPolicy.Layout layout = ScrollingLayoutPolicy.layout(state, viewport);
            page.setWindows(layout, state.scroll, state.focusedWindow, live,
                number == current && !mHost.reducedMotion() && !mView.isResizingGap());
            for (long id : live) {
                TilingDesktopView.WindowFrame frame = mView.frameOf(id);
                if (frame != null) liveFrames.put(id, frame);
            }
            for (ScrollingLayoutPolicy.Placement placement : layout.placements) {
                mStack.reportArea(placement.windowId, (long) placement.width() * (long) placement.height());
            }
        }
        mScrolling.clear();
        mScrolling.putAll(states);
        mDwindle.clear();
        mDwindle.putAll(trees);
        mView.keepPages(liveLevels);
        bindWindows(liveFrames);
        applyFocus();
        FrontListener listener = mFrontListener;
        if (listener != null) listener.onFrontChanged();
    }

    /**
     * A Dwindle Workspace: its tree follows the stack's tiles and, when the Workspace is live,
     * every tile's shown window is laid out (they all stand on screen) with a tab strip over each
     * tabbed tile; the tabs not shown are parked.
     */
    private void renderDwindle(int number, @NonNull TilingWorkspace workspace,
                               @NonNull ScrollingLayoutPolicy.Viewport viewport, int current, int count,
                               @NonNull Map<TilingWorkspace, DwindleTree> trees, @NonNull Set<Integer> liveLevels,
                               @NonNull Map<Long, TilingDesktopView.WindowFrame> liveFrames) {
        DwindleTree tree = treeOf(workspace);
        tree.sync(DwindleTree.tilesOf(workspace), viewport.width, viewport.height, viewport.gap);
        trees.put(workspace, tree);
        if (!TilingLivePolicy.isLiveLevel(number, current, mHasHome, count)) return;
        liveLevels.add(number);
        DwindleTree.Tiles tiles = tree.layout(viewport.width, viewport.height, viewport.gap,
            TilingDesktopView.TAB_STRIP_DP * mDensity);
        if (number == current) mFrontTiles = tiles;
        TilingDesktopView.Page page = mView.pageAt(number);
        page.setViewport();
        Set<Long> live = new HashSet<>();
        for (ScrollingLayoutPolicy.Placement placement : tiles.layout.placements) live.add(placement.windowId);
        Map<Long, TilingTabStrip.Tab> tabs = new HashMap<>();
        for (DwindleTree.TabStrip strip : tiles.strips) {
            for (long id : strip.windows) {
                TilingWindow window = mStack.window(id);
                if (window != null) tabs.put(id, new TilingTabStrip.Tab(id, window.kind(), tabName(window)));
            }
        }
        TilingWindow focused = workspace.focusedWindow();
        page.setTiles(tiles, focused == null ? TilingStack.NO_WINDOW : focused.id(), live, tabs);
        for (long id : live) {
            TilingDesktopView.WindowFrame frame = mView.frameOf(id);
            if (frame != null) liveFrames.put(id, frame);
        }
        for (ScrollingLayoutPolicy.Placement placement : tiles.layout.placements) {
            mStack.reportArea(placement.windowId, (long) placement.width() * (long) placement.height());
        }
    }

    /** {@code workspace}'s tree as last laid out, else as saved, else a new one (built from its order on sync). */
    @NonNull
    private DwindleTree treeOf(@NonNull TilingWorkspace workspace) {
        DwindleTree tree = mDwindle.get(workspace);
        if (tree == null) tree = DwindleTree.decode(workspace.layoutState());
        if (tree == null) tree = DwindleTree.empty();
        mDwindle.put(workspace, tree);
        return tree;
    }

    /** A tab's short name: the window's own, its shell's title, else its kind's. */
    @NonNull
    private String tabName(@NonNull TilingWindow window) {
        String name = window.name();
        if (name == null || name.isEmpty()) {
            TerminalSession shell = window.kind() == WindowKind.SHELL ? shellOf(window.id()) : null;
            name = shell == null ? null : shell.getTitle();
        }
        if (name != null && !name.trim().isEmpty()) return name.trim();
        switch (window.kind()) {
            case LINUX_APP: return mView.getContext().getString(R.string.control_bar_kind_linux_app);
            case DISPLAY: return mView.getContext().getString(R.string.control_bar_kind_display);
            case ASSISTANT: return mView.getContext().getString(R.string.control_bar_kind_assistant);
            case SHELL:
            default: return mView.getContext().getString(R.string.control_bar_kind_terminal);
        }
    }

    /**
     * Each live window's content drawn in its frame (a shell started or reattached first); every
     * other window of this desktop parked: its view goes, what it shows keeps running.
     */
    private void bindWindows(@NonNull Map<Long, TilingDesktopView.WindowFrame> liveFrames) {
        for (int number = 1; number <= mStack.workspaceCount(); number++) {
            TilingWorkspace workspace = mStack.workspace(number);
            if (workspace == null) continue;
            for (TilingWindow window : workspace.tiledWindows()) {
                if (window.kind() != WindowKind.SHELL) bindContent(window, liveFrames.get(window.id()));
            }
        }
        bindShells(liveFrames);
    }

    /** A window of another kind: its content in {@code frame}, or parked when it has none. */
    private void bindContent(@NonNull TilingWindow window, @Nullable TilingDesktopView.WindowFrame frame) {
        long id = window.id();
        if (frame == null) {
            if (mAttached.contains(id)) letContentGo(window);
            return;
        }
        TilingWindowContent content = mHost.content(window.kind());
        long contentId = contentIdOf(window);
        // A Linux window still waiting for its app shows its empty frame until the replay ends.
        if (content == null || window.kind() == WindowKind.LINUX_APP && contentId == LinuxWindowReturn.NO_LINUX_ID) return;
        if (content.attach(id, contentId, frame)) mAttached.add(id);
        else if (mAttached.contains(id)) letContentGo(window);
    }

    /** Window {@code window}'s content leaves its frame, and the keyboard with it. */
    private void letContentGo(@NonNull TilingWindow window) {
        long id = window.id();
        boolean attached = mAttached.remove(id);
        TilingWindowContent content = mHost.content(window.kind());
        if (content == null) return;
        long contentId = contentIdOf(window);
        if (mContentFocus == id) {
            mContentFocus = TilingStack.NO_WINDOW;
            content.focus(id, contentId, false);
        }
        if (attached) content.park(id, contentId);
    }

    /** What window {@code window} shows within its kind ({@link TilingWindowContent}). */
    private long contentIdOf(@NonNull TilingWindow window) {
        if (window.kind() != WindowKind.LINUX_APP) return 0L;
        Long linuxId = mLinux.get(window.id());
        return linuxId == null ? LinuxWindowReturn.NO_LINUX_ID : linuxId;
    }

    /**
     * Each live window's shell drawn in its frame, starting or reattaching it first; every other
     * shell window parked: its view goes, its shell keeps running.
     */
    private void bindShells(@NonNull Map<Long, TilingDesktopView.WindowFrame> liveFrames) {
        TerminalPaneController panes = mHost.panes();
        if (panes == null) return;
        boolean changed = false;
        for (int number = 1; number <= mStack.workspaceCount(); number++) {
            TilingWorkspace workspace = mStack.workspace(number);
            if (workspace == null) continue;
            for (TilingWindow window : workspace.tiledWindows()) {
                if (window.kind() != WindowKind.SHELL) continue;
                long id = window.id();
                int hostedId = TilingHostedIds.hostedId(id);
                if (hostedId == TilingHostedIds.NONE) continue;
                TerminalPaneController.Window hosted = panes.hostedFloatFor(hostedId);
                TilingDesktopView.WindowFrame frame = liveFrames.get(id);
                if (frame == null) {
                    if (hosted != null) {
                        rememberDirectory(window);
                        panes.removeWindow(hosted);
                    }
                    continue;
                }
                if (hosted == null) {
                    TerminalSession shell = shellOf(id);
                    if (shell == null) {
                        // A new window, or one whose shell is gone: a shell in its directory once
                        // the start has settled (before then the bootstrap may not be in place).
                        if (!mHost.shellsReady()) continue;
                        shell = mHost.createShell(window.workingDirectory());
                        if (shell == null) continue;
                        mShells.put(id, shell.mHandle);
                        changed = true;
                    }
                    hosted = panes.openHostedFloat(shell, hostedId);
                }
                panes.attachHostedFloat(hosted, frame);
            }
        }
        if (changed) scheduleSave();
    }

    /**
     * The keyboard goes to the focused window of the Workspace in front, whatever its kind, and
     * leaves on Home.
     */
    private void applyFocus() {
        long id = isWorkspaceInFront() ? mStack.focusedWindowId() : TilingStack.NO_WINDOW;
        TilingWindow window = id == TilingStack.NO_WINDOW ? null : mStack.window(id);
        long contentFront = window != null && window.kind() != WindowKind.SHELL && mAttached.contains(id)
            ? id : TilingStack.NO_WINDOW;
        if (mContentFocus != TilingStack.NO_WINDOW && mContentFocus != contentFront) {
            TilingWindow before = mStack.window(mContentFocus);
            TilingWindowContent content = before == null ? null : mHost.content(before.kind());
            long beforeId = mContentFocus;
            mContentFocus = TilingStack.NO_WINDOW;
            if (content != null) content.focus(beforeId, contentIdOf(before), false);
        }
        TerminalPaneController panes = mHost.panes();
        if (panes != null) {
            TerminalPaneController.Window focusedFloat = panes.focusedHomeFloat();
            boolean oursFocused = focusedFloat != null
                && TilingHostedIds.isTilingWindow(focusedFloat.hostedWidgetId());
            TerminalPaneController.Window hosted = id == TilingStack.NO_WINDOW ? null
                : panes.hostedFloatFor(TilingHostedIds.hostedId(id));
            if (hosted != null && panes.isHostedFloatInSlot(hosted)) {
                if (focusedFloat != hosted) panes.focusHomeFloat(hosted);
            } else if (oursFocused) {
                panes.releaseHomeFloatFocus();
            }
        }
        if (contentFront != TilingStack.NO_WINDOW && mContentFocus != contentFront) {
            TilingWindowContent content = mHost.content(window.kind());
            if (content != null) {
                mContentFocus = contentFront;
                content.focus(contentFront, contentIdOf(window), true);
            }
        }
    }

    // ---- saving ----------------------------------------------------------------------------------

    private void scheduleSave() {
        if (mDestroyed) return;
        mHandler.removeCallbacks(mSaveNow);
        mHandler.postDelayed(mSaveNow, SAVE_DEBOUNCE_MS);
    }

    /** Writes the saved form now: the stack, each Workspace's columns, each shell's directory. */
    private void saveNow() {
        if (mDestroyed) return;
        mHandler.removeCallbacks(mSaveNow);
        for (int number = 1; number <= mStack.workspaceCount(); number++) {
            TilingWorkspace workspace = mStack.workspace(number);
            if (workspace == null) continue;
            for (TilingWindow window : workspace.tiledWindows()) rememberDirectory(window);
            if (workspace.layout() == WorkspaceLayout.DWINDLE) {
                DwindleTree tree = mDwindle.get(workspace);
                if (tree != null) mStack.setLayoutState(number, tree.encode());
                continue;
            }
            ScrollingLayoutPolicy.State state = mScrolling.get(workspace);
            if (state != null) mStack.setLayoutState(number, ScrollingColumns.encode(state));
        }
        Map<Long, Long> linux = new HashMap<>(mLinuxWaiting);
        linux.putAll(mLinux);
        String encoded = TilingDesktopStore.encode(mStack, mShells, linux);
        sProcessCopy = encoded;
        TilingDesktopStore store = mStore;
        mWriter.execute(() -> {
            try {
                store.save(encoded);
            } catch (IOException e) {
                Logger.logWarn(LOG_TAG, "Could not save the tiling desktop: " + e.getMessage());
            }
        });
    }

    /** A running shell's directory, kept with its window so a new shell can start there. */
    private void rememberDirectory(@NonNull TilingWindow window) {
        if (window.kind() != WindowKind.SHELL) return;
        TerminalSession shell = shellOf(window.id());
        String cwd = shell == null ? null : shell.getCwd();
        if (cwd != null && !cwd.isEmpty()) mStack.setWorkingDirectory(window.id(), cwd);
    }

    // ---- small helpers ---------------------------------------------------------------------------

    /** The window showing {@code shell}, shown or parked, or {@link TilingStack#NO_WINDOW}. */
    private long windowOfShell(@NonNull TerminalSession shell) {
        if (shell.mHandle == null) return TilingStack.NO_WINDOW;
        for (Map.Entry<Long, String> entry : mShells.entrySet()) {
            if (shell.mHandle.equals(entry.getValue())) return entry.getKey();
        }
        return TilingStack.NO_WINDOW;
    }

    @Nullable
    private TerminalSession shellOf(long windowId) {
        String handle = mShells.get(windowId);
        return handle == null ? null : mHost.liveShell(handle);
    }

    private int currentLevel() {
        return TilingLivePolicy.level(mStack.current());
    }

    @Nullable
    private ScrollingLayoutPolicy.State frontState() {
        TilingWorkspace workspace = isWorkspaceInFront() ? mStack.workspace(currentLevel()) : null;
        return workspace == null ? null : mScrolling.get(workspace);
    }

    /** The focused window's column in the Workspace in front, or -1. */
    private int focusedColumn() {
        ScrollingLayoutPolicy.State state = frontState();
        if (state == null) return -1;
        for (int i = 0; i < state.columnCount(); i++) {
            ScrollingLayoutPolicy.Column column = state.column(i);
            for (int row = 0; row < column.windowCount(); row++)
                if (column.windowAt(row) == state.focusedWindow) return i;
        }
        return -1;
    }

    /** The focused window's row in its column (0 top), or -1. */
    private int focusedRow() {
        ScrollingLayoutPolicy.State state = frontState();
        int index = focusedColumn();
        if (state == null || index < 0) return -1;
        ScrollingLayoutPolicy.Column column = state.column(index);
        for (int row = 0; row < column.windowCount(); row++)
            if (column.windowAt(row) == state.focusedWindow) return row;
        return -1;
    }

    private int focusedColumnHeight() {
        ScrollingLayoutPolicy.State state = frontState();
        int index = focusedColumn();
        return state == null || index < 0 ? 0 : state.column(index).windowCount();
    }

    /**
     * The Workspaces' viewport: the view less Android's bars where it reaches under them, less
     * the air around the windows. {@link #mRoom} keeps it in the view's coordinates.
     */
    @NonNull
    private ScrollingLayoutPolicy.Viewport viewport() {
        int width = mView.getWidth();
        int height = mView.getHeight();
        int top = 0;
        int bottom = 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mView);
        if (insets != null && height > 0) {
            int[] at = new int[2];
            mView.getLocationOnScreen(at);
            int screenHeight = mView.getRootView().getHeight();
            int bars = WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout();
            top = Math.max(0, insets.getInsets(bars).top - at[1]);
            int below = Math.max(0, screenHeight - (at[1] + height));
            bottom = Math.max(0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom - below);
        }
        float edge = TilingDesktopView.EDGE_DP * mDensity;
        mRoom.set(edge, top + edge, Math.max(edge, width - edge), Math.max(top + edge, height - bottom - edge));
        ScrollingLayoutPolicy.Viewport viewport = new ScrollingLayoutPolicy.Viewport(mRoom.width(),
            mRoom.height(), TilingDesktopView.GAP_DP * mDensity, width > height);
        mView.setRoom(mRoom, top, bottom);
        return viewport;
    }
}
