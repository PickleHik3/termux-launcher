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

import com.termux.app.terminal.TerminalPaneController;
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
import com.termux.app.tiling.WindowKind;
import com.termux.app.tiling.WindowSpec;
import com.termux.app.wall.BorderDrag;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tiling desktop's tracer (#418, behind the Terl switch): Home, which is v1's Home, then
 * Workspaces of terminal windows below it, laid out by Scrolling. It joins the stack model
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

        /** Whether moves between places jump rather than slide. */
        boolean reducedMotion();
    }

    private static final String LOG_TAG = "TilingDesktop";
    /** How long after the last change the saved form is written. */
    static final long SAVE_DEBOUNCE_MS = 1000L;
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
    /** Each Workspace's Scrolling state as last laid out, by the Workspace itself (numbers move). */
    @NonNull private final Map<TilingWorkspace, ScrollingLayoutPolicy.State> mScrolling = new IdentityHashMap<>();
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Runnable mSaveNow = this::saveNow;
    @NonNull private final RectF mRoom = new RectF();
    private boolean mDestroyed;

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
        } else {
            mStack = TilingStack.firstStart(homePages, null);
        }
        mView = new TilingDesktopView(context, this, hasHome);
        int background = TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND];
        mView.setGroundColor(ColorUtils.blendARGB(background | 0xFF000000, 0xFF000000, 0.35f));
        mView.moveTo(currentLevel(), false);
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

    /** Whether {@code shell} is a window's of this desktop, shown or parked: v1 leaves it alone. */
    public boolean claimsShell(@Nullable TerminalSession shell) {
        return shell != null && shell.mHandle != null && mShells.containsValue(shell.mHandle);
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
        long id = TilingHostedIds.windowId(hostedId);
        mShells.remove(id);
        if (mStack.close(id)) {
            render();
            scheduleSave();
        }
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
            default:
                return false;
        }
    }

    // ---- the view's listener ---------------------------------------------------------------------

    @Override
    public void onEdgeHold(@NonNull BorderDrag.Border side) {
        switch (side) {
            case LEFT: run(TilingInput.Command.LEFT); break;
            case RIGHT: run(TilingInput.Command.RIGHT); break;
            case TOP: run(TilingInput.Command.UP); break;
            case BOTTOM: run(TilingInput.Command.DOWN); break;
            default: break;
        }
    }

    @Override
    public boolean canStep(@NonNull BorderDrag.Border side) {
        int level = currentLevel();
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
    public void onNewTerminalPressed() {
        openTerminal();
    }

    @Override
    public void onRoomChanged() {
        render();
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
        mStack.open(WindowSpec.shell(cwd));
        if (!before.equals(mStack.current())) placeChanged();
        else focusChanged();
    }

    /** Closes the focused window, its shell with it. */
    private void closeFocused() {
        if (!isWorkspaceInFront()) return;
        long id = mStack.focusedWindowId();
        if (id == TilingStack.NO_WINDOW) return;
        TerminalPaneController panes = mHost.panes();
        TerminalPaneController.Window hosted = panes == null ? null
            : panes.hostedFloatFor(TilingHostedIds.hostedId(id));
        TerminalSession shell = shellOf(id);
        if (hosted != null) panes.removeWindow(hosted);
        mShells.remove(id);
        if (shell != null) mHost.killShell(shell);
        mStack.close(id);
        render();
        scheduleSave();
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
        for (int number = 1; number <= count; number++) {
            TilingWorkspace workspace = mStack.workspace(number);
            if (workspace == null) continue;
            ScrollingLayoutPolicy.State before = mScrolling.get(workspace);
            if (before == null) before = ScrollingColumns.decode(workspace.layoutState());
            ScrollingLayoutPolicy.State state = ScrollingColumns.sync(workspace, before, viewport);
            states.put(workspace, state);
            if (!TilingLivePolicy.isLiveLevel(number, current, mHasHome, count)) continue;
            liveLevels.add(number);
            TilingDesktopView.Page page = mView.pageAt(number);
            page.setViewport(mRoom);
            Set<Long> live = TilingLivePolicy.liveWindows(state, viewport);
            ScrollingLayoutPolicy.Layout layout = ScrollingLayoutPolicy.layout(state, viewport);
            page.setWindows(layout, state.scroll, state.focusedWindow, live,
                number == current && !mHost.reducedMotion());
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
        mView.keepPages(liveLevels);
        bindShells(liveFrames);
        applyFocus();
    }

    /**
     * Each live window's shell drawn in its frame, starting or reattaching it first; every other
     * window of this desktop parked: its view goes, its shell keeps running.
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

    /** The keyboard goes to the focused window of the Workspace in front, and leaves on Home. */
    private void applyFocus() {
        TerminalPaneController panes = mHost.panes();
        if (panes == null) return;
        TerminalPaneController.Window focusedFloat = panes.focusedHomeFloat();
        boolean oursFocused = focusedFloat != null
            && TilingHostedIds.isTilingWindow(focusedFloat.hostedWidgetId());
        long id = isWorkspaceInFront() ? mStack.focusedWindowId() : TilingStack.NO_WINDOW;
        TerminalPaneController.Window hosted = id == TilingStack.NO_WINDOW ? null
            : panes.hostedFloatFor(TilingHostedIds.hostedId(id));
        if (hosted != null && panes.isHostedFloatInSlot(hosted)) {
            if (focusedFloat != hosted) panes.focusHomeFloat(hosted);
        } else if (oursFocused) {
            panes.releaseHomeFloatFocus();
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
            ScrollingLayoutPolicy.State state = mScrolling.get(workspace);
            if (state != null) mStack.setLayoutState(number, ScrollingColumns.encode(state));
        }
        String encoded = TilingDesktopStore.encode(mStack, mShells);
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
        mView.setInsetRoom(top, bottom);
        return viewport;
    }
}
