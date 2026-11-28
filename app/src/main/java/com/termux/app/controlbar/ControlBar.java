package com.termux.app.controlbar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingInput;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.WorkspaceLayout;
import com.termux.app.tiling.desktop.TilingDesktop;

import java.util.ArrayList;
import java.util.List;

/**
 * The control bar (#424, ADR 0038, behind the Terl switch): one glass card at the bottom of every
 * place. It reads the place in front from the tiling desktop ({@link TilingDesktop.FrontListener})
 * and Home's pages from the activity, turns them into rows ({@link ControlBarRows}) and hands them
 * to its view; a touch on the view comes back here and goes to the desktop or the activity.
 *
 * <p>The reading slot ({@link ReadingSlot}) is wired by {@link #attachReadings}: a tap on it asks for
 * the next reading and a hold opens {@link ReadingCardView}.
 *
 * <p>A hold on the place badge opens the Workspace's menu ({@link WorkspaceMenu}, #420); a hold
 * anywhere else that has no hold of its own opens Overview (#430).
 *
 * <p>A held mark opens that window's menu ({@link WindowMenu}, {@link WindowMenuView}, #425), as
 * the Window menu tool does for the focused window; the window is focused first, so the menu is
 * about the window in front. Dragging a held mark sideways reorders the windows; dragging it up
 * raises the Workspace list ({@link WorkspaceList}) and a release over a row moves the window there.
 *
 * <p>Not here yet: navigation mode (#433), search's own sheet (#435). The navigation tool does
 * nothing until then; search opens the drawer's search. A Linux app has no touchpad yet, so its
 * Mouse mode changes nothing.
 */
public final class ControlBar implements TilingDesktop.FrontListener, ControlBarView.Listener {

    /** What the bar reads of and asks of the tiling desktop ({@link #of(TilingDesktop)}). */
    public interface Desktop {
        void setFrontListener(@Nullable TilingDesktop.FrontListener listener);

        @NonNull Place front();

        @NonNull List<TilingWindow> frontWindows();

        @Nullable TilingWindow frontMainWindow();

        long frontFocusedWindowId();

        boolean focusWindow(long windowId);

        void split();

        void newTerminal();

        /** The window in front's own keyboard; false when it has none (a shell, Home). */
        boolean toggleFrontKeyboard();

        /** The window in front's mouse mode; false when it has none. */
        boolean toggleFrontMouseMode();

        /** The layout of the Workspace in front; null on Home. */
        @Nullable WorkspaceLayout frontLayout();

        /** Lays the Workspace in front out by {@code layout}; false on Home or when it already is. */
        boolean setFrontLayout(@NonNull WorkspaceLayout layout);

        /** Whether the window in focus of the Workspace in front is full screen (#426). */
        boolean frontFullScreen();

        /** Ends or starts full screen for the window in focus of the Workspace in front; false when nothing changed. */
        boolean setFrontFullScreen(boolean fullScreen);

        /** Asks for a name and saves the Workspace in front (#431). */
        default void saveFront() {
        }

        /** What a held mark's window menu does to a window (#425); null for none. */
        @Nullable
        default WindowActions windowActions() {
            return null;
        }
    }

    /** What a held mark's window menu and drag do to window {@code windowId} (#425). */
    public interface WindowActions {
        /** The window's menu as the stack stands; null when there is no such window. */
        @Nullable WindowMenu.Subject menuOf(long windowId);

        /** Floats a tile or tiles a Float (the window is focused first). */
        boolean toggleFloat(long windowId);

        /** Pins a Float to every Workspace, or unpins it (the window is focused first). */
        boolean togglePin(long windowId);

        /** Full screen for the window (focused first), or back. */
        boolean setFullScreen(long windowId, boolean fullScreen);

        boolean setMinimised(long windowId, boolean minimised);

        boolean toggleMaximised(long windowId);

        /** Puts out the window's Scale control. */
        boolean showScale(long windowId);

        boolean moveTo(long windowId, @NonNull Place place);

        boolean joinTab(long windowId, long targetId);

        boolean stack(long windowId, long targetId);

        /** Takes the window out of its tabs or its stack. */
        boolean separate(long windowId);

        /** Closes the window as the Close key does, asking first when its shell runs a program. */
        void close(long windowId);

        /** The window takes {@code targetId}'s slot in its Workspace (a mark dragged onto another's). */
        boolean reorder(long windowId, long targetId);
    }

    /** The tiling desktop's own answers for a held mark's menu. */
    @NonNull
    public static WindowActions windowActionsOf(@NonNull TilingDesktop desktop) {
        return new WindowActions() {
            @Nullable @Override public WindowMenu.Subject menuOf(long windowId) {
                return WindowMenu.subjectOf(desktop.snapshot(), windowId, desktop.windowTakesScale(windowId));
            }
            @Override public boolean toggleFloat(long windowId) {
                return desktop.focusWindow(windowId) && desktop.toggleFrontFloat();
            }
            @Override public boolean togglePin(long windowId) {
                return desktop.focusWindow(windowId) && desktop.toggleFrontPin();
            }
            @Override public boolean setFullScreen(long windowId, boolean fullScreen) {
                return desktop.focusWindow(windowId) && desktop.setFrontFullScreen(fullScreen);
            }
            @Override public boolean setMinimised(long windowId, boolean minimised) {
                return desktop.setWindowMinimised(windowId, minimised);
            }
            @Override public boolean toggleMaximised(long windowId) {
                return desktop.focusWindow(windowId) && desktop.toggleWindowMaximised(windowId);
            }
            @Override public boolean showScale(long windowId) {
                return desktop.focusWindow(windowId) && desktop.showWindowScale(windowId);
            }
            @Override public boolean moveTo(long windowId, @NonNull Place place) {
                return desktop.moveWindow(windowId, place);
            }
            @Override public boolean joinTab(long windowId, long targetId) {
                return desktop.joinTab(windowId, targetId);
            }
            @Override public boolean stack(long windowId, long targetId) {
                return desktop.stackWindow(windowId, targetId);
            }
            @Override public boolean separate(long windowId) {
                return desktop.separateWindow(windowId);
            }
            @Override public void close(long windowId) {
                desktop.closeWindowAsked(windowId);
            }
            @Override public boolean reorder(long windowId, long targetId) {
                return desktop.reorderWindow(windowId, targetId);
            }
        };
    }

    /** The tiling desktop as the bar reads it, with no Save. */
    @NonNull
    public static Desktop of(@NonNull TilingDesktop desktop) {
        return of(desktop, () -> { });
    }

    /** The tiling desktop as the bar reads it; the Workspace menu's Save runs {@code saveFront}. */
    @NonNull
    public static Desktop of(@NonNull TilingDesktop desktop, @NonNull Runnable saveFront) {
        return new Desktop() {
            @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) {
                desktop.setFrontListener(listener);
            }
            @NonNull @Override public Place front() { return desktop.front(); }
            @NonNull @Override public List<TilingWindow> frontWindows() { return desktop.frontWindows(); }
            @Nullable @Override public TilingWindow frontMainWindow() { return desktop.frontMainWindow(); }
            @Override public long frontFocusedWindowId() { return desktop.frontFocusedWindowId(); }
            @Override public boolean focusWindow(long windowId) { return desktop.focusWindow(windowId); }
            @Override public void split() { desktop.split(); }
            @Override public void newTerminal() { desktop.run(TilingInput.Command.NEW_TERMINAL); }
            @Override public boolean toggleFrontKeyboard() { return desktop.toggleFrontKeyboard(); }
            @Override public boolean toggleFrontMouseMode() { return desktop.toggleFrontMouseMode(); }
            @Nullable @Override public WorkspaceLayout frontLayout() { return desktop.frontLayout(); }
            @Override public boolean setFrontLayout(@NonNull WorkspaceLayout layout) {
                return desktop.setFrontLayout(layout);
            }
            @Override public boolean frontFullScreen() { return desktop.frontFullScreen(); }
            @Override public boolean setFrontFullScreen(boolean fullScreen) {
                return desktop.setFrontFullScreen(fullScreen);
            }

            @Override public void saveFront() { saveFront.run(); }

            @NonNull private final WindowActions mActions = windowActionsOf(desktop);
            @NonNull @Override public WindowActions windowActions() { return mActions; }
        };
    }

    /** What the bar needs from the activity; at most a handful of methods. */
    public interface Host {
        /** How many Home pages there are. */
        int homePageCount();

        /** Which Home page is in front, from 0. */
        int homePageInFront();

        /** Turns Home to page {@code page}. */
        void showHomePage(int page);

        /** Home's pinned apps, in order, for the bar's own drawing and spoken labels. */
        @NonNull List<ControlBarView.PinnedApp> pinnedApps();

        /** Opens pinned app {@code index}. */
        void openPinnedApp(int index);

        /** Holds pinned app {@code index}: its notifications and actions, anchored to the held icon, {@code anchor}. */
        void holdPinnedApp(int index, @NonNull View anchor);

        /** The launcher's keyboard comes up or goes, typing into the focused shell. */
        void toggleKeyboard();

        /** Opens search. */
        void openSearch();

        /** Opens the drawer sheet. */
        void openDrawer();

        /** Whether Home's row holds the Drawer item. */
        boolean drawerToolShown();

        /** The bar's glass at {@code radiusPx} for {@code anchor}: Terl's control-bar look. */
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        /** Terl's corner radius, in px. */
        float cornerRadiusPx();

        /** The ink for marks and tools on the bar's glass. */
        int ink();

        /** The scheme's accent, for what is lit. */
        int accent();

        /** Opens Overview (#430): the bar was held away from its marks, apps, reading and badge. */
        void openOverview();
    }

    /** What the bar tells the activity about full screen (#426), and asks of it. */
    public interface FullScreenHost {
        /** The bar's full-screen state changed: the Workspaces are laid out against it again. */
        void onFullScreenChanged(boolean fullScreen);

        /** Whether moves jump rather than slide. */
        boolean reducedMotion();

        /** Whether a surface the bar opened that the bar does not know to close is up (the pinned app's menu). */
        boolean surfaceOpen();
    }

    /** How long the raising bar takes to come up, in ms (no repainting beyond it). */
    static final long RAISE_MS = 160L;

    @NonNull private final Host mHost;
    @NonNull private final Desktop mDesktop;
    @NonNull private final ControlBarView mView;
    @Nullable private ControlBarRows mRows;
    /** The tools each place has stored (#452); null leaves the bar's own lists. */
    @Nullable private ControlBarRows.StoredTools mStoredTools;
    /** Whether the bar is out of sight (full screen, #426) and whether edit mode brought it up. */
    private boolean mFlush;
    /** Full screen (#426): the bar, a handle, or the bar raised over the window. */
    @NonNull private final FullScreenBar mFull = new FullScreenBar();
    /** Edit mode brought a hidden bar up for as long as its card is open (#452). */
    private boolean mRaised;
    @NonNull private final FullScreenHandleView mHandle;
    @Nullable private FullScreenHost mFullScreenHost;
    @Nullable private android.animation.ValueAnimator mRaiseAnimation;
    /** How far below its place the raising bar stands now, in px. */
    private float mRaiseOffsetPx;
    private float mRideTyPx;
    @NonNull private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    /** The Workspace menu, opened from the raised bar, is up. */
    private boolean mMenuOpen;
    @NonNull private final Runnable mIdle = () -> {
        // What the raised bar opened keeps it up; the time starts again when that closes.
        if (keepsRaised()) {
            scheduleIdle();
            return;
        }
        dropRaised(mFull.idle());
    };
    @Nullable private ReadingSlot mReadings;
    @Nullable private ReadingCardView mReadingCard;
    @Nullable private WorkspaceList mWorkspaceList;
    @Nullable private WindowMenuView mWindowMenu;
    /** The window the open menu is for, or -1. */
    private long mMenuWindow = -1L;
    /** A carried mark raised the Workspace list (#425): a drop there moves its window. */
    private boolean mCarryingToList;

    public ControlBar(@NonNull Context context, @NonNull Host host, @NonNull Desktop desktop) {
        mHost = host;
        mDesktop = desktop;
        mView = new ControlBarView(context, this);
        mHandle = new FullScreenHandleView(context, this::onHandleRaise);
        // The pill is dressed as soon as it has a size, the first time the handle shows.
        mHandle.pill().addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) mHandler.post(this::restyle);
        });
        // A touch on the raised bar keeps it up another idle time; the bar still takes it.
        mView.setOnTouchListener((v, event) -> {
            if (mFull.mode() == FullScreenBar.Mode.RAISED) scheduleIdle();
            return false;
        });
        mView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            // A new size needs glass cut for it; posted, never from inside the layout pass.
            if (r - l != or - ol || b - t != ob - ot) v.post(this::restyle);
        });
        desktop.setFrontListener(this);
    }

    @NonNull
    public View view() {
        return mView;
    }

    /** The full-screen handle (#426), added to the pages host beside the bar. */
    @NonNull
    public View handleView() {
        return mHandle;
    }

    /** Where the activity hears of full screen and asks about motion. */
    public void setFullScreenHost(@Nullable FullScreenHost host) {
        mFullScreenHost = host;
    }

    // ---- what the activity tells it --------------------------------------------------------------

    /** The bar's shape (#427); the activity lays the bar out for it. */
    public void setForm(@NonNull ControlBarForm form) {
        mView.setForm(form);
    }

    @NonNull
    public ControlBarForm form() {
        return mView.form();
    }

    /**
     * Moves the bar {@code tyPx} from where it is laid out: riding the stack on a frame the stack
     * is drawn ({@link ControlBarPlacement#rideTyPx}). Written only when it changed.
     */
    public void ride(float tyPx) {
        mRideTyPx = tyPx;
        // The handle stands where the bar's bottom is, and rides with it.
        if (mHandle.getTranslationY() != tyPx) mHandle.setTranslationY(tyPx);
        float ty = tyPx + mRaiseOffsetPx;
        if (mView.getTranslationY() == ty) return;
        mView.setTranslationY(ty);
        // Its glass samples the wallpaper where it is drawn; a transform alone redraws nothing.
        mView.invalidate();
    }

    /** The tools each place holds changed (or were read): the rows are read again. */
    public void setStoredTools(@Nullable ControlBarRows.StoredTools stored) {
        mStoredTools = stored;
        onFrontChanged();
    }

    /** The bar's bottom corners are square while it stands flush with the screen's bottom (margin 0). */
    public void setFlush(boolean flush) {
        if (mFlush == flush) return;
        mFlush = flush;
        mView.setFlush(flush);
        // Its glass is cut again: square at the bottom, or round all round.
        restyle();
    }

    /** Edit mode's tap on the bar; null stops ({@link ControlBarView#setEditTap}). */
    public void setEditTap(@Nullable Runnable tap) {
        mView.setEditTap(tap);
    }

    /** The bar is out of sight, as full screen keeps it (#426), or back. */
    public void setHidden(boolean hidden) {
        setFullScreen(hidden);
    }

    /** Whether the window in front is full screen: the bar is a handle or raised over it. */
    public boolean isFullScreen() {
        return mFull.fullScreen();
    }

    /** Where the bar stands in full screen. */
    @NonNull
    public FullScreenBar.Mode fullScreenMode() {
        return mFull.mode();
    }

    /**
     * The window in front went full screen, or came back: the bar goes down to the handle, or is
     * in its place again.
     */
    void setFullScreen(boolean fullScreen) {
        boolean changed = fullScreen ? mFull.enter() : mFull.leave();
        if (!changed) return;
        cancelRaise();
        mHandler.removeCallbacks(mIdle);
        showForState();
        if (mFullScreenHost != null) mFullScreenHost.onFullScreenChanged(mFull.fullScreen());
    }

    /** Back: ends full screen; false when no window is full screen. */
    public boolean leaveFullScreen() {
        return mFull.fullScreen() && mDesktop.setFrontFullScreen(false);
    }

    /** A slide up on the handle: the bar rises over the window and drops back after use or idle time. */
    private void onHandleRaise() {
        if (!mFull.slideUp()) return;
        showForState();
        startRaise();
        scheduleIdle();
    }

    /** A touch began at screen point ({@code rawX}, {@code rawY}): outside a raised bar it drops back. */
    public void onWindowTouchDown(float rawX, float rawY) {
        if (mFull.mode() != FullScreenBar.Mode.RAISED) return;
        int[] at = new int[2];
        mView.getLocationOnScreen(at);
        boolean inside = rawX >= at[0] && rawX < at[0] + mView.getWidth()
            && rawY >= at[1] && rawY < at[1] + mView.getHeight();
        if (!inside && !keepsRaised()) dropRaised(mFull.tapOutside());
    }

    private void scheduleIdle() {
        mHandler.removeCallbacks(mIdle);
        mHandler.postDelayed(mIdle, FullScreenBar.IDLE_MS);
    }

    private void dropRaised(boolean changed) {
        if (!changed) return;
        cancelRaise();
        mHandler.removeCallbacks(mIdle);
        closeReadingCard();
        showForState();
    }

    /** The raised bar rises from its own height below; one short move, jumping with reduced motion. */
    private void startRaise() {
        cancelRaise();
        float height = mView.getHeight() > 0 ? mView.getHeight()
            : ControlBarPlacement.HEIGHT_DP * mView.getResources().getDisplayMetrics().density;
        boolean reduced = mFullScreenHost != null && mFullScreenHost.reducedMotion();
        // A rail is a column at a side: it comes in place, there is no height to rise from.
        if (reduced || mView.form().isRail() || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            setRaiseOffset(0f);
            return;
        }
        setRaiseOffset(FullScreenHandle.raiseOffsetPx(height, 0f, false));
        android.animation.ValueAnimator animation = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(RAISE_MS);
        animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animation.addUpdateListener(a -> setRaiseOffset(
            FullScreenHandle.raiseOffsetPx(height, (float) a.getAnimatedValue(), false)));
        mRaiseAnimation = animation;
        animation.start();
    }

    private void cancelRaise() {
        if (mRaiseAnimation != null) {
            mRaiseAnimation.removeAllUpdateListeners();
            mRaiseAnimation.cancel();
            mRaiseAnimation = null;
        }
        setRaiseOffset(0f);
    }

    private void setRaiseOffset(float offsetPx) {
        mRaiseOffsetPx = offsetPx;
        ride(mRideTyPx);
    }

    /** After a tool, a mark or an app of the raised bar was used it drops back to the handle. */
    private void used() {
        dropRaised(mFull.used());
    }

    /**
     * Brings a hidden bar up for as long as its card is open; false when it is already showing.
     * {@link #lower} puts it back.
     */
    public boolean raise() {
        if (mRaised) return false;
        if (mFull.mode() == FullScreenBar.Mode.RAISED) {
            // Raised by a slide already: the card keeps it up, the idle drop waits for lower().
            mHandler.removeCallbacks(mIdle);
            mRaised = true;
            return true;
        }
        if (mFull.barShown()) return false;
        mRaised = true;
        showForState();
        return true;
    }

    /** Puts a bar {@link #raise} brought up back out of sight. */
    public void lower() {
        if (!mRaised) return;
        mRaised = false;
        showForState();
        // A bar the slide had raised goes on from here with its idle time.
        if (mFull.mode() == FullScreenBar.Mode.RAISED) scheduleIdle();
    }

    /** Whether the raised bar stays up: a surface it opened is up, or edit mode brought it up. */
    private boolean keepsRaised() {
        return mRaised || mMenuOpen || isReadingCardOpen() || isWindowMenuOpen() || isWorkspaceListOpen()
            || (mFullScreenHost != null && mFullScreenHost.surfaceOpen());
    }

    private void showForState() {
        int visibility = mFull.barShown() || mRaised ? View.VISIBLE : View.GONE;
        if (mView.getVisibility() != visibility) mView.setVisibility(visibility);
        int handle = mFull.handleShown() && !mRaised ? View.VISIBLE : View.GONE;
        if (mHandle.getVisibility() != handle) {
            // Dressed before it first shows: the scheme's ink now, the look's glass once it has a size.
            if (handle == View.VISIBLE) mHandle.setPillInk(mHost.ink());
            mHandle.setVisibility(handle);
        }
    }

    /** The place in front, its windows or Home's page may have changed: the rows are read again. */
    @Override
    public void onFrontChanged() {
        setFullScreen(mDesktop.frontFullScreen());
        ControlBarRows rows = ControlBarRows.of(front(), mHost.drawerToolShown(), mStoredTools);
        if (rows.equals(mRows)) return;
        boolean homeChanged = mRows == null || mRows.home != rows.home;
        mRows = rows;
        mView.setRows(rows);
        if (homeChanged && rows.home) refreshPinnedApps();
    }

    /** Home's pinned apps or their icons changed. */
    public void refreshPinnedApps() {
        List<ControlBarView.PinnedApp> apps = new ArrayList<>();
        for (ControlBarView.PinnedApp app : mHost.pinnedApps()) {
            Drawable icon = app.icon;
            Drawable.ConstantState state = icon == null ? null : icon.getConstantState();
            // An own instance: the bar sets bounds on it, and the drawer may draw the same one.
            apps.add(new ControlBarView.PinnedApp(
                state != null ? state.newDrawable(mView.getResources()) : icon, app.label,
                app.packageName));
        }
        mView.setPinnedApps(apps);
    }

    /** The look, the scheme or the glass's frame changed: the card is dressed again. */
    public void restyle() {
        float radius = mHost.cornerRadiusPx();
        mView.setCornerRadiusPx(radius);
        mView.setInks(mHost.ink(), mHost.accent());
        if (mView.getWidth() > 0 && mView.getHeight() > 0) {
            Drawable glass = mHost.glass(radius, mView);
            // Flush: the glass reaches a radius below the bar and the outline clips it square.
            mView.setBackground(glass != null && mFlush
                ? new FlushGlass(glass, Math.round(radius) + 1) : glass);
        }
        View pill = mHandle.pill();
        if (pill.getWidth() > 0 && pill.getHeight() > 0) {
            mHandle.setPillGlass(mHost.glass(pill.getHeight() / 2f, pill));
        }
    }

    @NonNull
    private ControlBarFront front() {
        Place place = mDesktop.front();
        if (place.isHome()) return ControlBarFront.home(mHost.homePageCount(), mHost.homePageInFront());
        List<ControlBarFront.Window> windows = new ArrayList<>();
        for (TilingWindow window : mDesktop.frontWindows()) {
            windows.add(new ControlBarFront.Window(window.id(), window.kind(), window.name(), window.isMinimised()));
        }
        TilingWindow main = mDesktop.frontMainWindow();
        return ControlBarFront.workspace(place.workspaceNumber(), main == null ? null : main.kind(),
            windows, mDesktop.frontFocusedWindowId());
    }

    // ---- what a touch on the view asks -----------------------------------------------------------

    @Override
    public void onMark(int index) {
        ControlBarRows rows = mRows;
        if (rows == null || index < 0 || index >= rows.marks.size()) return;
        long target = rows.marks.get(index).target;
        if (rows.home) {
            mHost.showHomePage((int) target);
            onFrontChanged();
        } else {
            mDesktop.focusWindow(target);
        }
        used();
    }

    @Override
    public void onTool(@NonNull ControlBarTool tool) {
        runTool(tool);
        used();
    }

    private void runTool(@NonNull ControlBarTool tool) {
        switch (tool) {
            case SPLIT:
                mDesktop.split();
                break;
            case NEW_TERMINAL:
                mDesktop.newTerminal();
                break;
            case KEYBOARD:
                // A Linux app, the Display or the Assistant takes it as its own; a shell types
                // from the launcher's keyboard, which follows the focused window.
                if (!mDesktop.toggleFrontKeyboard()) mHost.toggleKeyboard();
                break;
            case MOUSE_MODE:
                // Only the window in front's own: v1's Display page is not touched.
                mDesktop.toggleFrontMouseMode();
                break;
            case SEARCH:
                mHost.openSearch();
                break;
            case DRAWER:
                mHost.openDrawer();
                break;
            case WINDOW_MENU: {
                // The focused window's menu, over the tool's own cell.
                long focused = mDesktop.frontFocusedWindowId();
                android.graphics.Rect cell = mView.toolCell(ControlBarTool.WINDOW_MENU);
                if (focused >= 0 && cell != null) openWindowMenu(focused, cell);
                break;
            }
            case NAVIGATION:
            case PINNED_APPS:
            default:
                // Navigation mode (#433) comes with its ticket.
                break;
        }
    }

    @Override
    public void onPinnedApp(int index) {
        mHost.openPinnedApp(index);
        used();
    }

    @Override
    public void onPinnedAppHeld(int index, @NonNull android.graphics.Rect cell) {
        mHost.holdPinnedApp(index, new PinnedCellAnchor(mView.getContext(), mView, cell));
    }

    // ---- the reading slot (#428) -----------------------------------------------------------------

    /**
     * Gives the bar its reading slot, reading and asking through {@code host}. Until this, the slot
     * is drawn empty. Calls {@link ReadingSlot#refresh()}.
     */
    public void attachReadings(@NonNull ReadingSlot.Host host) {
        if (mReadings != null) return;
        mReadings = new ReadingSlot(mView.getContext(), host, new ReadingSlot.Sink() {
            @Override public void onSlot(boolean shown, @Nullable ReadingValue value) {
                mView.setReading(shown, value);
            }

            @Override public void onAll(@NonNull ReadingSet set) {
                if (mReadingCard != null) mReadingCard.bind(set);
            }
        });
        mReadings.refresh();
    }

    /** {@code bar.reading} or {@code bar.reading_kind} changed: the slot is read again. */
    public void onReadingSettingChanged() {
        if (mReadings == null) return;
        mReadings.refresh();
        if (!isReadingShown()) closeReadingCard();
    }

    private boolean isReadingShown() {
        return mView.readingShown();
    }

    /** One system sample, from the activity's sampler. */
    public void onStats(@NonNull com.termux.app.statusbar.SystemStatsController.Stats stats, long nowMs) {
        if (mReadings != null) mReadings.onStats(stats, nowMs);
    }

    /** The weather controller answered. */
    public void onWeather(@NonNull com.termux.app.statusbar.WeatherController.Weather weather) {
        if (mReadings != null) mReadings.onWeather(weather);
    }

    /** The launcher is in front, or is not. */
    public void setInFront(boolean front) {
        if (mReadings != null) mReadings.setInFront(front);
    }

    /** Whether the slot wants the system sampler now. */
    public boolean readingWantsSampler() {
        return mReadings != null && mReadings.wantsSampler();
    }

    /** Whether the card of every reading is open. */
    public boolean isReadingCardOpen() {
        return mReadingCard != null && mReadingCard.isOpen();
    }

    /** Closes the card of every reading; false when it was not open (Back's answer). */
    public boolean closeReadingCard() {
        if (!isReadingCardOpen()) return false;
        mReadingCard.close();
        if (mReadings != null) mReadings.setCardOpen(false);
        return true;
    }

    /** The bar is going away: the readings' sources stop and the card goes. */
    public void releaseReadings() {
        closeReadingCard();
        if (mReadings != null) mReadings.release();
    }

    @Override
    public void onReading() {
        if (mReadings != null) mReadings.next();
    }

    @Override
    public void onReadingHeld(@NonNull android.graphics.Rect cell) {
        if (mReadings == null || isReadingCardOpen()) return;
        if (mReadingCard == null) {
            mReadingCard = new ReadingCardView(mView.getContext(), new ReadingCardView.Style() {
                @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) {
                    return mHost.glass(radiusPx, anchor);
                }
                @Override public float cornerRadiusPx() { return mHost.cornerRadiusPx(); }
                @Override public int ink() { return mHost.ink(); }
            }, this::closeReadingCard);
        }
        if (!mReadingCard.show(mView, cell, mView.form().isRail())) return;
        mReadings.setCardOpen(true);
        mReadingCard.bind(mReadings.readings());
    }

    // ---- the Workspace list (#429) ------------------------------------------------------------------

    /**
     * Gives the bar its Workspace list: a slide up on the bar raises it ({@link WorkspaceList}),
     * letting go on a row goes there and a long press renames. Until this, a slide up does nothing.
     */
    public void attachWorkspaceList(@NonNull android.app.Activity activity, @NonNull WorkspaceList.Host host) {
        if (mWorkspaceList != null) return;
        mWorkspaceList = new WorkspaceList(activity, host);
        mView.setListEnabled(true);
    }

    /** Whether the Workspace list is up. */
    public boolean isWorkspaceListOpen() {
        return mWorkspaceList != null && mWorkspaceList.isOpen();
    }

    /** Back puts the Workspace list away (or drops a rename); false when it was not up. */
    public boolean closeWorkspaceList() {
        return mWorkspaceList != null && mWorkspaceList.onBack();
    }

    @Override
    public boolean onListRaise() {
        if (mWorkspaceList == null) return false;
        mWorkspaceList.raise(mView, mView.form().isRail());
        return mWorkspaceList.isOpen();
    }

    @Override
    public void onListTrack(float rawX, float rawY) {
        if (mWorkspaceList != null) mWorkspaceList.track(rawX, rawY);
    }

    @Override
    public void onListRelease(float rawX, float rawY) {
        if (mWorkspaceList != null) mWorkspaceList.release(rawX, rawY);
    }

    @Override
    public void onListCancel() {
        if (mWorkspaceList != null) mWorkspaceList.cancel();
    }

    @Override
    public void onListHeldOpen() {
        if (mWorkspaceList != null) mWorkspaceList.openHeld(mView, mView.form().isRail());
    }

    /** The packages whose pinned icons carry a notification dot (none while dots are off). */
    public void setDotPackages(@NonNull java.util.Set<String> packages) {
        mView.setDotPackages(packages);
    }

    // ---- the held mark's window menu and drag (#425) --------------------------------------------

    /** Whether a sheet of the bar is open: the card of every reading or a window's menu. */
    public boolean isSheetOpen() {
        return isReadingCardOpen() || isWindowMenuOpen();
    }

    /** Closes the bar's open sheet; false when none was open (Back's answer). */
    public boolean closeSheet() {
        boolean menu = closeWindowMenu();
        return closeReadingCard() || menu;
    }

    /** Whether a window's menu is open. */
    public boolean isWindowMenuOpen() {
        return mWindowMenu != null && mWindowMenu.isOpen();
    }

    /** Closes the window menu; false when it was not open. */
    public boolean closeWindowMenu() {
        mMenuWindow = -1L;
        if (!isWindowMenuOpen()) return false;
        mWindowMenu.close();
        return true;
    }

    /** The window a mark stands for, or -1 for a Home page's dot or no mark. */
    private long windowOfMark(int index) {
        ControlBarRows rows = mRows;
        if (rows == null || rows.home || index < 0 || index >= rows.marks.size()) return -1L;
        return rows.marks.get(index).target;
    }

    @Override
    public void onMarkHeld(int index, @NonNull android.graphics.Rect cell) {
        long window = windowOfMark(index);
        if (window < 0) return;
        // The menu is about the window in front: the held one comes in front first. A minimised
        // one stays minimised (focusing it would show it); its menu offers Restore.
        ControlBarRows rows = mRows;
        if (rows != null && !rows.marks.get(index).dim) mDesktop.focusWindow(window);
        openWindowMenu(window, cell);
    }

    /** Opens window {@code window}'s menu over {@code cell} (bar coordinates). */
    void openWindowMenu(long window, @NonNull android.graphics.Rect cell) {
        WindowActions actions = mDesktop.windowActions();
        WindowMenu.Subject subject = actions == null ? null : actions.menuOf(window);
        if (subject == null) return;
        if (mWindowMenu == null) {
            mWindowMenu = new WindowMenuView(mView.getContext(), style(), new WindowMenuView.Listener() {
                @Override public void onRow(@NonNull WindowMenu.Row row) {
                    long target = mMenuWindow;
                    closeWindowMenu();
                    if (target >= 0) onWindowMenuRow(target, row);
                }
                @Override public void onDismiss() {
                    closeWindowMenu();
                }
            });
        }
        closeReadingCard();
        mMenuWindow = window;
        if (!mWindowMenu.show(mView, cell, mView.form().isRail(), subject, nameOf(window))) mMenuWindow = -1L;
    }

    @NonNull
    private String nameOf(long window) {
        for (TilingWindow each : mDesktop.frontWindows()) {
            if (each.id() == window) return WindowMenuView.windowName(mView.getContext(), each.name(), each.kind());
        }
        return WindowMenuView.windowName(mView.getContext(), null, null);
    }

    @NonNull
    private ReadingCardView.Style style() {
        return new ReadingCardView.Style() {
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) {
                return mHost.glass(radiusPx, anchor);
            }
            @Override public float cornerRadiusPx() { return mHost.cornerRadiusPx(); }
            @Override public int ink() { return mHost.ink(); }
        };
    }

    /** Row {@code row} of window {@code window}'s menu was chosen (from the card or a screen reader). */
    void onWindowMenuRow(long window, @NonNull WindowMenu.Row row) {
        WindowActions actions = mDesktop.windowActions();
        if (actions == null) return;
        switch (row.action) {
            case FLOAT:
            case TILE:
                actions.toggleFloat(window);
                break;
            case PIN:
            case UNPIN:
                actions.togglePin(window);
                break;
            case FULL_SCREEN:
                actions.setFullScreen(window, true);
                break;
            case LEAVE_FULL_SCREEN:
                actions.setFullScreen(window, false);
                break;
            case MINIMISE:
                actions.setMinimised(window, true);
                break;
            case RESTORE:
                actions.setMinimised(window, false);
                break;
            case MAXIMISE:
            case UNMAXIMISE:
                actions.toggleMaximised(window);
                break;
            case SCALE:
                actions.showScale(window);
                break;
            case MOVE:
                if (row.place != null) actions.moveTo(window, row.place);
                break;
            case TAB:
                actions.joinTab(window, row.window);
                break;
            case STACK:
                actions.stack(window, row.window);
                break;
            case SEPARATE:
                actions.separate(window);
                break;
            case CLOSE:
                actions.close(window);
                break;
            default:
                // A list's row opens its list on the card.
                break;
        }
    }

    /** The rows a screen reader offers on mark {@code index} (its window's menu, lists flattened). */
    @NonNull
    @Override
    public List<WindowMenu.Row> markActions(int index) {
        long window = windowOfMark(index);
        WindowActions actions = window < 0 ? null : mDesktop.windowActions();
        WindowMenu.Subject subject = actions == null ? null : actions.menuOf(window);
        return subject == null ? java.util.Collections.<WindowMenu.Row>emptyList() : subject.flat();
    }

    @Override
    public void onMarkAction(int index, @NonNull WindowMenu.Row row) {
        long window = windowOfMark(index);
        if (window >= 0) onWindowMenuRow(window, row);
    }

    @Override
    public void onMarkDragStart(int index) {
        // A held mark that moves is carried: its menu goes.
        closeWindowMenu();
    }

    @Override
    public void onMarkReorder(int index, int onto) {
        long window = windowOfMark(index);
        long target = windowOfMark(onto);
        WindowActions actions = mDesktop.windowActions();
        if (window < 0 || target < 0 || window == target || actions == null) return;
        actions.reorder(window, target);
    }

    @Override
    public void onMarkLifted(float rawX, float rawY) {
        WorkspaceList list = mWorkspaceList;
        if (list == null) return;
        if (!mCarryingToList) {
            // The list rises for the carried window: its rows light under the finger, no rename.
            list.raiseToCarry(mView, mView.form().isRail());
            mCarryingToList = list.isOpen();
        }
        list.track(rawX, rawY);
    }

    @Override
    public void onMarkDropped(int index, float rawX, float rawY) {
        WorkspaceList list = mWorkspaceList;
        if (list == null || !mCarryingToList) return;
        mCarryingToList = false;
        Place place = list.drop(rawX, rawY);
        long window = windowOfMark(index);
        WindowActions actions = mDesktop.windowActions();
        if (place != null && window >= 0 && actions != null) actions.moveTo(window, place);
    }

    @Override
    public void onMarkDragCancelled() {
        if (mCarryingToList && mWorkspaceList != null) mWorkspaceList.cancel();
        mCarryingToList = false;
    }

    /** A hold on the place badge: on a Workspace, its menu ({@link WorkspaceMenu}). */
    @Override
    public void onPlaceHeld() {
        WorkspaceLayout layout = mDesktop.frontLayout();
        if (layout == null) return;
        mMenuOpen = true;
        WorkspaceMenu.show(mView, layout, !mDesktop.frontWindows().isEmpty(), new WorkspaceMenu.Choice() {
            @Override public void onLayoutChosen(@NonNull WorkspaceLayout chosen) {
                ControlBar.this.onLayoutChosen(chosen);
            }
            @Override public void onSaveChosen() {
                ControlBar.this.onSaveChosen();
            }
        }, () -> {
            mMenuOpen = false;
            if (mFull.mode() == FullScreenBar.Mode.RAISED) scheduleIdle();
        });
    }

    /** A hold on the bar away from what has a hold of its own: Overview. */
    @Override
    public void onBarHeld() {
        mHost.openOverview();
    }

    /** The Workspace menu's Layout row {@code layout} was chosen. */
    void onLayoutChosen(@NonNull WorkspaceLayout layout) {
        mDesktop.setFrontLayout(layout);
    }

    /** The Workspace menu's Save Workspace was chosen (#431). */
    void onSaveChosen() {
        mDesktop.saveFront();
    }
}
