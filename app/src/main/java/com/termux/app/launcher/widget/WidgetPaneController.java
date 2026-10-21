package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.termux.app.launcher.home.HomeLayoutLock;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetConfigSheet;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetHost;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetKind;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetServices;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetSpan;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetStyle;

/** Production coordinator from the real picker through placement into the A-1 transaction. */
public final class WidgetPaneController implements LauncherWidgetHostController.Listener {
    public interface Host {
        boolean reducedMotion();
        /** True while the surface holding the widget grid is on screen. */
        boolean isWidgetSurfaceShowing();
        /**
         * An add-widget flow is about to leave for another app's activity. Remember whatever the
         * surface needs to come back to; the controller does not know what that is.
         */
        void captureWidgetSurfaceOrigin();
        /** That flow has returned and the surface had gone away: bring it back. */
        void restoreWidgetSurfaceOrigin();
        /** A provider text editor took focus; give it the system IME. */
        default void onWidgetEditorFocused(@NonNull View editor) { }
        /** The editor lost focus; restore the terminal's IME arrangement. */
        default void onWidgetEditorClosed() { }
        /** The page on screen was drawn again: its number or its widgets may have changed. */
        default void onWidgetPageRendered() { }
        /**
         * The widget edit chrome came up or went away. The page's own border tab follows it: a
         * render that drops the chrome ends the session as surely as Back does, so this is told
         * from one place rather than from each way in and out.
         */
        default void onWidgetEditSessionChanged(boolean editing) { }
        /** The face of every built-in widget numeral. */
        @NonNull default BuiltinWidgetStyle.Numerals builtinWidgetNumerals() {
            return BuiltinWidgetStyle.Numerals.SANS;
        }
        /**
         * A long press on the page's free area: the host puts up Home's long-press sheet, whose
         * rows act on the page on screen. The raw point is where the finger rested.
         */
        default void onHomeLongPressed(float rawX, float rawY) { }
        /**
         * The palette gesture on the page's free area let go: up opens the palette with the
         * keyboard, down puts them away (spec §5.1).
         */
        default void onHomePaletteSwipe(boolean up) { }
        /**
         * Edit pages moved or removed a page: {@code mapping} is each old page's new number
         * ({@link com.termux.app.launcher.home.HomePageEdits}), for what else names a page.
         */
        default void onHomePagesRenumbered(@NonNull int[] mapping) { }
        /** A double tap on the page's free area (Settings › Home › Gestures). */
        default void onHomeDoubleTap() { }
        /** A two-finger swipe up ({@code up}) or down on the page's free area. */
        default void onHomeTwoFingerSwipe(boolean up) { }
        /**
         * The Home page's content moved sideways under Home's own swipe, or as a page slides in
         * or back; {@link #homePageShiftFraction} says where it stands. Per frame of the motion.
         */
        default void onHomePageShifted() { }
        /** The activity-side services the built-in widgets need; null keeps them inert. */
        @Nullable default BuiltinWidgetServices.Host builtinWidgetHost() { return null; }
        /**
         * The launcher's menu look (the dock's), for the popup a folder on Home opens; null
         * draws it in a plain one.
         */
        @Nullable default com.termux.app.launcher.popup.AnchoredMenuTheme homeMenuTheme() {
            return null;
        }
        /**
         * A tap on a Home folder popup's title: start the dock's focusless rename, drawing the
         * draft into {@code title} and committing through {@code target}. Without a host for it
         * the title stays as it is.
         */
        default void beginHomeFolderRename(@NonNull String name,
                                           @NonNull com.termux.app.launcher.folder.FolderRenameTitleView title,
                                           @NonNull HomeFolderRenameTarget target) { }
        /** The Home folder popup went away: a rename in progress ends with it. */
        default void cancelHomeFolderRename() { }
        /** Whether a touch at this screen point types into a rename in progress (a key). */
        default boolean isFolderRenameTypingTouch(float rawX, float rawY) { return false; }
        /**
         * The edit chrome's remove was tapped on widget {@code appWidgetId}: run {@code remove},
         * now or once the user agrees (a Terminal widget whose shell is still running something).
         */
        default void confirmWidgetRemoval(int appWidgetId, @NonNull Runnable remove) {
            remove.run();
        }
    }

    /** Where a Home folder's rename lands; the activity's rename intake commits through it. */
    public interface HomeFolderRenameTarget {
        /** Stores {@code name} (trimmed, never blank); false when the folder is gone. */
        boolean rename(@NonNull String name);
        /** The folder's name as stored now, for the title once the rename ends. */
        @NonNull String currentName();
    }

    private final WidgetPaneView pane;
    private final LauncherWidgetHostController widgets;
    private final WidgetProviderCatalogLoader catalog;
    private final Host host;
    @NonNull private final BuiltinWidgetHost builtins;
    /** The app icons' and folders' views; inert until the first icon is drawn. */
    @NonNull private final HomeAppIconFactory appIcons;
    /** The popup a folder on Home opens; made on the first tap. */
    @Nullable private HomeFolderPopup folderPopup;
    private String liveOrigin;
    private boolean awaitingExternal;
    private int currentPage;
    /** Settings › Home › Lock Home layout: nothing on the pages moves or changes by hand. */
    private boolean layoutLocked;
    /** Settings › Home › Folders: how many apps a folder takes, and how its popup lays them out. */
    private int folderCapacity = HomeFolderPolicy.MAX_APPS;
    @NonNull private HomeFolderDensity folderDensity = HomeFolderDensity.DEFAULT;
    private boolean editorFocusActive;

    public WidgetPaneController(@NonNull WidgetPaneView pane,
                                @NonNull LauncherWidgetHostController widgets,
                                @NonNull Host host) {
        this(pane, widgets, host, new WidgetProviderCatalogLoader(pane.getContext()));
    }

    WidgetPaneController(@NonNull WidgetPaneView pane,
                         @NonNull LauncherWidgetHostController widgets,
                         @NonNull Host host, @NonNull WidgetProviderCatalogLoader catalog) {
        this.pane = pane; this.widgets = widgets; this.host = host; this.catalog = catalog;
        BuiltinWidgetServices.Host builtinHost = host.builtinWidgetHost();
        builtins = new BuiltinWidgetHost(pane.getContext(),
            new BuiltinWidgetServices(pane.getContext(),
                builtinHost != null ? builtinHost : new InertBuiltinHost()),
            host::builtinWidgetNumerals);
        pane.grid().setBuiltinFactory(builtins);
        appIcons = new HomeAppIconFactory(pane.getContext(), new HomeAppIconFactory.Listener() {
            @Override public void onUnavailableAppTapped(int id) {
                WidgetPaneController.this.onUnavailableAppTapped(id);
            }
            @Override public void onFolderTapped(int id, @NonNull View anchor) {
                openFolder(id, anchor);
            }
        });
        pane.grid().setAppIconFactory(appIcons);
        catalog.setBuiltinSource(builtins);
        pane.grid().addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> onWallLayout());
        pane.grid().bind(widgets); pane.picker().setReducedMotion(host.reducedMotion());
        pane.setReducedMotion(host.reducedMotion());
        pane.picker().adapter().setPreviewLoader(catalog);
        // The picker's search field is a text input inside the pane like any other: it reaches the
        // system keyboard through the same seam a provider's own editor does, and never by asking
        // for it here.
        pane.picker().setSearchFocusListener(this::relayEditorFocus);
        pane.grid().setListener(new WidgetGridView.Listener() {
            @Override public void onWidgetLongPressed(int appWidgetId, float rawX, float rawY) {
                if (refusedWhileLocked(HomeLayoutLock.Edit.HOLD_TO_EDIT)) return;
                enterEditMode(appWidgetId, rawX, rawY);
            }
            @Override public void onWidgetEditDragMove(int appWidgetId, float rawX, float rawY) {
                if (edit != null && edit.appWidgetId == appWidgetId) moveDrag(rawX, rawY);
            }
            @Override public void onWidgetEditDragEnd(int appWidgetId, boolean canceled) {
                if (edit != null && edit.appWidgetId == appWidgetId) endMoveDrag(canceled);
            }
            @Override public void onEmptySpaceLongPressed(float rawX, float rawY) {
                host.onHomeLongPressed(rawX, rawY);
            }
            @Override public void onWidgetEditorFocusChanged(View editor) {
                relayEditorFocus(editor);
            }
        });
        pane.setListener(new WidgetPaneView.Listener() {
            @Override public void onPageChangeRequested(int page) { setCurrentPage(page); }
            @Override public void onWidgetEditCommit() { commitEditSession(); }
            @Override public void onWidgetEditDiscard() { discardEditSession(); }
            @Override public void onWidgetAddPage() { menuAddPage(); }
            @Override public void onFreeAreaVerticalSwipe(boolean up) {
                host.onHomePaletteSwipe(up);
            }
            @Override public void onFreeAreaDoubleTap() { host.onHomeDoubleTap(); }
            @Override public void onFreeAreaTwoFingerSwipe(boolean up) {
                host.onHomeTwoFingerSwipe(up);
            }
            @Override public void onContentShifted() { host.onHomePageShifted(); }
        }, new WidgetPickerAdapter.Listener() {
            @Override public void onProviderSelected(@NonNull WidgetProviderItem item) {
                selectProvider(item);
            }
            @Override public void onProviderHeld(@NonNull WidgetProviderItem item,
                                                 @NonNull View card, float rawX, float rawY) {
                beginCarry(item, card, rawX, rawY);
            }
        });
        widgets.setListener(this);
        // Pages saved before this rule, or left behind by a removed widget while the page was off
        // screen, are brought level with it once here; changes keep it from then on.
        trimPages();
        clampCurrentPage();
        render();
    }

    /** The grid the user asked for on this orientation, before the wall's caps; 0 until asked. */
    private int wantedRows, wantedColumns;
    /** The rows always on gives the keyboard, taken off the capped grid; 0 while it is off. */
    private int keyboardRows;
    /**
     * The largest the wall has measured in each orientation. The caps come from this, not from
     * the live size: the keyboard or a sheet shrinking the wall for a while must not shrink the
     * grid and reflow the widgets, since a reflow is kept. A cap can only grow until the screen
     * turns, and the two orientations keep their own.
     */
    private int portraitWallWidth, portraitWallHeight, landscapeWallWidth, landscapeWallHeight;
    @Nullable private WidgetGridCaps appliedCaps;
    /**
     * Home's Margin as last settled, in dp: the rim the caps are worked out with. The grid's live
     * rim follows a Margin drag tick by tick ({@link #setGridEdge}); the caps wait for it to
     * settle, because a cap that falls reflows the widgets and a reflow is kept.
     */
    private float gridEdgeDp;

    /**
     * What this wall can hold, from the largest it has been in this orientation and Home's
     * Margin as last settled; unbounded before its first layout. The grid is the wall as far as
     * cells go, so this is the answer for the wheels and the apply.
     */
    @NonNull public WidgetGridCaps gridCaps() {
        boolean landscape = isLandscape();
        float density = pane.getResources().getDisplayMetrics().density;
        return WidgetGridCaps.forWallPx(landscape ? landscapeWallWidth : portraitWallWidth,
            landscape ? landscapeWallHeight : portraitWallHeight, density, gridEdgeDp);
    }

    /**
     * Home's Margin, the rim between the page's edge and the cells, in dp; the activity pushes it
     * in, since the pane has no preferences of its own. The rim moves at once. With
     * {@code settled} (anything but a tick of a Margin drag) the caps are worked out again with
     * it, and a grid that no longer fits, or now fits more, is applied through the same path a
     * resized wall takes ({@link #applyWantedGrid}); a drag's ticks leave that to its release.
     */
    public void setGridEdge(float edgeDp, boolean settled) {
        float edge = edgeDp > 0f ? edgeDp : 0f;
        float density = pane.getResources().getDisplayMetrics().density;
        pane.grid().setEdgePadding(Math.round(edge * density));
        if (!settled || Float.compare(edge, gridEdgeDp) == 0) return;
        gridEdgeDp = edge;
        if (wantedRows > 0 && !gridCaps().equals(appliedCaps)) {
            applyWantedGrid(wantedRows, wantedColumns);
        }
    }

    /**
     * Puts the user's grid on the wall, held to what the wall can hold. The stored count is not
     * touched: it is remembered here, and the grid is worked out again when the wall turns out
     * larger, so a wider orientation or a smaller font later gets the full count back.
     */
    public void applyWantedGrid(int rows, int columns) {
        applyWantedGrid(rows, columns, keyboardRows);
    }

    /**
     * {@link #applyWantedGrid(int, int, int)} with Home's Margin as the rim, taken in the same
     * pass: the caps are worked out once, with the rim the grid is about to wear, so a pass that
     * changes both the grid and the Margin (a launch, a turn of the screen) reflows at most once.
     */
    public void applyWantedGrid(int rows, int columns, int takenRows, float edgeDp) {
        float edge = edgeDp > 0f ? edgeDp : 0f;
        float density = pane.getResources().getDisplayMetrics().density;
        pane.grid().setEdgePadding(Math.round(edge * density));
        gridEdgeDp = edge;
        applyWantedGrid(rows, columns, takenRows);
    }

    /**
     * The same, with {@code takenRows} of the capped grid given to an always-on keyboard
     * ({@code LauncherWidgetRepository#shrinkGridRows}): the grid is applied that much shorter on
     * every later pass, so a pass that re-reads the layout never grows it back under the keyboard.
     */
    public void applyWantedGrid(int rows, int columns, int takenRows) {
        wantedRows = rows;
        wantedColumns = columns;
        keyboardRows = Math.max(0, takenRows);
        rememberWall();
        WidgetGridCaps caps = gridCaps();
        appliedCaps = caps;
        // The rows given away stay part of the grid's height, so the cells keep their size.
        pane.grid().setReservedRows(keyboardRows);
        widgets.applyGrid(caps.clampRows(rows) - keyboardRows, caps.clampColumns(columns));
    }

    /** The grid's height as last laid out, in px: what always on measures a keyboard row by. */
    public int gridHeightPx() {
        return pane.grid().getHeight();
    }

    private boolean isLandscape() {
        return pane.getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Folds the grid's measured size into its orientation's record. False when there is nothing
     * to learn: unmeasured, or still the other orientation's size right after a turn.
     */
    private boolean rememberWall() {
        WidgetGridView grid = pane.grid();
        int width = grid.getWidth(), height = grid.getHeight();
        if (width <= 0 || height <= 0) return false;
        boolean landscape = isLandscape();
        if (landscape != (width > height)) return false;
        if (landscape) {
            landscapeWallWidth = Math.max(landscapeWallWidth, width);
            landscapeWallHeight = Math.max(landscapeWallHeight, height);
        } else {
            portraitWallWidth = Math.max(portraitWallWidth, width);
            portraitWallHeight = Math.max(portraitWallHeight, height);
        }
        return true;
    }

    private void onWallLayout() {
        if (wantedRows <= 0 || !rememberWall()) return;
        if (gridCaps().equals(appliedCaps)) return;
        // Never from inside the layout pass that reported the change.
        pane.post(() -> { if (wantedRows > 0) applyWantedGrid(wantedRows, wantedColumns); });
    }

    /**
     * The launcher is back in front: the pane is drawn again and the built-ins' sources catch up.
     * The weather in particular re-reads the place picked in Settings, which the widget's own
     * controller only hears about on a refresh, not on the pane's re-render.
     */
    public void onStart() {
        render();
        builtins.services().onHostStart();
    }
    /** Draw the pane again from the repository — the layout under it changed without a grid change. */
    public void redraw() { render(); }
    /** The theme, the Look or the widget style setting changed: the built-ins redress. */
    public void onLookChanged() { builtins.onStyleChanged(); }
    /** The activity answered a calendar permission request. */
    public void onCalendarPermissionChanged() { builtins.services().onCalendarPermissionChanged(); }
    /** The built-in widgets' services, for the activity to reach a widget's sources. */
    @NonNull public BuiltinWidgetHost builtins() { return builtins; }
    public void onStop() {
        dismissFolderPopup();
        abortCarry(true);
        catalog.cancel(); pane.picker().closeImmediate();
    }
    public void onPackageOrProfileChanged() {
        catalog.invalidate();
        render(); if (pane.picker().isOpen()) loadCatalog();
    }
    /**
     * The wall's Widgets page came to rest on screen, or left it. A page that has gone opens
     * again the way it always does — page 0, no menu — like the pull-down it replaces.
     */
    /**
     * The wall has committed to leaving this page; the slide may still be showing it. Sizes stop
     * reaching the providers from here, not from the settle: the next place's keyboard resizes the
     * wall mid-slide, and that is the size a provider must not hear.
     */
    public void onWallPageLeaving() {
        dismissFolderPopup();
        pane.grid().setPageAtRest(false);
    }

    /**
     * The Appearance editor's session ended. The cells may have passed through several sizes in
     * it (a Margin's ticks, a Style's chrome, an Undo back) while Home's settled Margin ends where
     * it began, so no rim change is left to deliver them: each widget whose cell moved is told
     * its size again once the wall rests here ({@link WidgetGridView#resyncSizes}).
     */
    public void onEditorSessionEnded() {
        pane.grid().resyncSizes();
    }

    public void onWallPageShown(boolean shown) {
        // Sizes reach the providers only while the wall rests here; see WidgetGridView.
        pane.grid().setPageAtRest(shown);
        if (shown) return;
        // Home waits on its last page while the wall is elsewhere: that is the page next to the
        // seam, the one a border drag back off the first window arrives on (ADR 0008). The home
        // press turns it to page 1 itself (showHomePage).
        int last = Math.max(0, widgets.repository().pageCount() - 1);
        if (currentPage != last) { currentPage = last; render(); }
    }

    /** How many Home pages there are. */
    public int homePageCount() {
        return Math.max(1, widgets.repository().pageCount());
    }

    /** Which Home page is in front, counting from 0. */
    public int homePageIndex() {
        return currentPage;
    }

    /**
     * How far the Home page in front stands off its rest while Home's swipe turns it, as a
     * fraction of a page width, positive to the right; 0 at rest.
     */
    public float homePageShiftFraction() {
        return pane.contentShiftFraction();
    }

    /** Turn Home to page {@code page} (clamped), without the swipe's slide. */
    public void showHomePage(int page) {
        setCurrentPage(page);
    }

    /**
     * How a Home page moves while a swipe turns it, and Animation speed's multiplier on its
     * settle; the activity pushes both in as it comes to the front.
     */
    public void setPageMotion(@NonNull com.termux.app.launcher.home.HomePageTransition effect,
                              float speedMultiplier) {
        pane.setPageMotion(effect, speedMultiplier);
    }

    /**
     * Settings › Home › Lock Home layout, and the Home sheet's Lock and Unlock. Locking while the
     * widgets are being edited closes the edit as its tick would.
     */
    public void setLayoutLocked(boolean locked) {
        layoutLocked = locked;
        if (locked && pane.widgetEditActive()) commitEditSession();
    }

    public boolean isLayoutLocked() {
        return layoutLocked;
    }

    /**
     * Whether {@code edit} is refused because the layout is locked; a refusal says so on the page,
     * with the way to unlock.
     */
    public boolean refusedWhileLocked(@NonNull HomeLayoutLock.Edit edit) {
        if (HomeLayoutLock.allows(layoutLocked, edit)) return false;
        pane.showNotice(pane.getContext().getString(R.string.home_layout_locked_refusal));
        return true;
    }

    /**
     * Settings › Home › Folders: how tightly an open folder lays its apps out, and the most apps a
     * folder takes. A folder already past a lower cap keeps its apps; it only takes no more.
     */
    public void setFolderStyle(@NonNull HomeFolderDensity density, int capacity) {
        folderDensity = density;
        folderCapacity = HomeFolderPolicy.clampCapacity(capacity);
    }

    /** The dots under the grid, or none (Settings › Home › Page indicator). */
    public void setPageIndicator(@NonNull com.termux.app.launcher.home.HomePageIndicator indicator) {
        pane.setPageIndicator(indicator);
    }

    /** How Home's app icons and folders are drawn (Settings › Home › Icons and labels). */
    public void setIconStyle(@NonNull HomeIconStyle style) {
        appIcons.setStyle(style);
    }

    /** Whether the page watches for a double tap and a two-finger swipe (each set to something). */
    public void setExtraGestures(boolean doubleTap, boolean twoFinger) {
        pane.setExtraGestures(doubleTap, twoFinger);
    }

    /** The wall's seam out of the last Home page; see {@link WidgetPaneView.Seam}. */
    public void setSeam(@Nullable WidgetPaneView.Seam seam) {
        pane.setSeam(seam);
    }
    public boolean onBackPressed() {
        if (pane.widgetEditActive()) { exitEditMode(); return true; }
        return pane.onBackPressed();
    }
    public void destroy() {
        if (folderPopup != null) folderPopup.dismissImmediate();
        builtins.destroy();
        appIcons.destroy();
        abortCarry(false);
        pane.removeCallbacks(edgeFlip);
        widgets.setListener(null); catalog.cancel();
    }

    int currentPage() { return currentPage; }

    void setCurrentPage(int page) {
        int clamped = Math.max(0, Math.min(widgets.repository().pageCount() - 1, page));
        if (clamped == currentPage) return;
        currentPage = clamped;
        render();
    }

    /** A trim can take pages away from under the page on screen; it follows what is left. */
    private void clampCurrentPage() {
        currentPage = Math.max(0, Math.min(widgets.repository().pageCount() - 1, currentPage));
    }

    /**
     * The Widgets page's edit pencil, and Home's long-press sheet's Edit pages: one entry,
     * with the same gestures and the same ways out.
     */
    public boolean editWidgets() {
        // Locked, the pencil is spent on the notice: no edit mode, and no grid wheels either.
        if (refusedWhileLocked(HomeLayoutLock.Edit.EDIT_WIDGETS)) return true;
        return menuEditWidgets();
    }

    /**
     * Enters the edit chrome on the current page's first widget, without a live drag; false when
     * the page has no widget to start on.
     */
    boolean menuEditWidgets() {
        List<LauncherWidgetRecord> pageRecords = widgets.repository().recordsOnPage(currentPage);
        LauncherWidgetRecord first = null;
        for (LauncherWidgetRecord record : pageRecords) {
            if (first == null || record.cell.top < first.cell.top
                || (record.cell.top == first.cell.top && record.cell.left < first.cell.left)) {
                first = record;
            }
        }
        return first != null && beginEditSession(first.appWidgetId);
    }

    /**
     * The + on the page's border tab. The new page is the user's own: it stays, empty, until they
     * put something on it, and from then on it comes and goes with its widgets.
     */
    void menuAddPage() {
        if (refusedWhileLocked(HomeLayoutLock.Edit.ADD_PAGE)) return;
        int appended = widgets.repository().addFreshPage();
        if (appended < 0) {
            pane.showNotice(messageFor(LauncherWidgetHostController.AddResult.STORAGE_FAILURE));
            return;
        }
        currentPage = appended;
        render();
    }

    /** Whether the Home page on screen holds no widget. */
    public boolean currentPageEmpty() {
        return widgets.repository().recordsOnPage(currentPage).isEmpty();
    }

    /**
     * Puts {@code app} on Home as an icon: the page in front when it has room, else the first page
     * that has, else a new page after the last. Returns the page it landed on, counting from 0,
     * or -1 when it could not be written. The page in front stays in front.
     */
    public int addApp(@NonNull com.termux.app.launcher.model.LauncherAppEntry app) {
        LauncherWidgetRecord placed = widgets.addApp(app.appRef, app.label, currentPage);
        return placed == null ? -1 : placed.page;
    }

    /**
     * A tap on an app icon whose app is gone: the page's edit mode opens on it, without a drag,
     * so its remove chip is one press away.
     */
    private void onUnavailableAppTapped(int id) {
        if (pane.widgetEditActive()) return;
        // The tap on a gone app opens edit mode to remove it: an edit like any hold.
        if (refusedWhileLocked(HomeLayoutLock.Edit.HOLD_TO_EDIT)) return;
        beginEditSession(id);
    }

    // ---- Folders on Home ---------------------------------------------------------------------

    /** A tap on a folder: its popup, unless the page is being edited. */
    private void openFolder(int id, @NonNull View anchor) {
        if (pane.widgetEditActive()) return;
        LauncherWidgetRecord folder = widgets.repository().get(id);
        if (folder == null || !folder.isFolder()) return;
        folderPopup().show(folder, anchor, folderDensity, layoutLocked);
    }

    @NonNull private HomeFolderPopup folderPopup() {
        HomeFolderPopup popup = folderPopup;
        if (popup != null) return popup;
        com.termux.app.launcher.popup.AnchoredMenuTheme theme = host.homeMenuTheme();
        if (theme == null) theme = PLAIN_MENU_THEME;
        popup = new HomeFolderPopup(pane, theme, appIcons, new HomeFolderPopup.Callbacks() {
            @Override public void onRemoveFromFolder(int folderId, @NonNull String stableId) {
                if (refusedWhileLocked(HomeLayoutLock.Edit.HOLD_TO_EDIT)) return;
                removeFromFolder(folderId, stableId);
            }
            @Override public void onBeginRename(int folderId, @NonNull String name,
                                                @NonNull com.termux.app.launcher.folder.FolderRenameTitleView title) {
                if (refusedWhileLocked(HomeLayoutLock.Edit.HOLD_TO_EDIT)) return;
                // The name as stored now: the popup may have outlived a rename already.
                LauncherWidgetRecord held = widgets.repository().get(folderId);
                if (held == null || !held.isFolder()) return;
                host.beginHomeFolderRename(held.folderName(), title, new HomeFolderRenameTarget() {
                    @Override public boolean rename(@NonNull String value) {
                        return widgets.renameFolder(folderId, value, folderFallbackName());
                    }
                    @Override @NonNull public String currentName() {
                        LauncherWidgetRecord now = widgets.repository().get(folderId);
                        return now != null && now.isFolder() ? now.folderName() : name;
                    }
                });
            }
            @Override public void onPopupDismissed() { host.cancelHomeFolderRename(); }
            @Override public boolean keepsOpenOnTouch(float rawX, float rawY) {
                return host.isFolderRenameTypingTouch(rawX, rawY);
            }
        });
        folderPopup = popup;
        return popup;
    }

    private void dismissFolderPopup() {
        if (folderPopup != null && folderPopup.isShowing()) folderPopup.dismiss();
    }

    /**
     * A press anywhere in the activity's window, before any view sees it: the Home folder popup,
     * shown inside the Home pane, closes for one outside the pane as it did as a window of its own
     * ({@link HomeFolderPopup#onWindowTouchDown}).
     */
    public void onWindowTouchDown(float rawX, float rawY) {
        if (folderPopup != null) folderPopup.onWindowTouchDown(rawX, rawY);
    }

    /**
     * Remove from folder: the app goes on Home as an icon, the page in front first, and what is
     * left of the folder follows the folder rules. The page it landed on is named when it is not
     * the one on screen.
     */
    private void removeFromFolder(int folderId, @NonNull String stableId) {
        LauncherWidgetRecord placed = widgets.removeFromFolder(folderId, stableId, currentPage);
        if (placed == null) return;
        if (placed.page != currentPage) {
            pane.showNotice(pane.getContext().getString(R.string.home_app_added, placed.page + 1));
        }
    }

    /** The name a folder takes when its first app has no label. */
    @NonNull private String folderFallbackName() {
        return pane.getContext().getString(R.string.home_folder_default_name);
    }

    /** The folder popup's look when the host lends none: light ink on the default glass. */
    private static final com.termux.app.launcher.popup.AnchoredMenuTheme PLAIN_MENU_THEME =
        new com.termux.app.launcher.popup.AnchoredMenuTheme() {
            @Override public int textColor() { return 0xFFFFFFFF; }
            @Override public int selectedTextColor() { return 0xFFFFFFFF; }
            @Override public int opacityPercent() { return 85; }
            @Override public boolean blurEnabled() { return false; }
            @Override public int blurRadiusDp() { return 0; }
        };

    /** Home's long-press sheet's Remove page: the page on screen goes, if it may. */
    public void removeCurrentPage() {
        menuRemovePage();
    }

    void menuRemovePage() {
        // The sheet offers it only for an empty page, and asks nothing: a page that has gained
        // something since is not taken away from here (Edit pages asks first).
        if (itemsOnPage(currentPage) > 0) return;
        removePage(currentPage);
    }

    /** How many things page {@code page} holds: widgets, icons and folders. */
    public int itemsOnPage(int page) {
        return widgets.repository().recordsOnPage(page).size();
    }

    /** How many apps the folders on page {@code page} hold between them. */
    public int folderAppsOnPage(int page) {
        int apps = 0;
        for (LauncherWidgetRecord record : widgets.repository().recordsOnPage(page)) {
            if (record.isFolder()) apps += record.folderApps().size();
        }
        return apps;
    }

    /**
     * Edit pages: page {@code from} moves to {@code to} with everything on it. The page on screen
     * follows its page, and the host renumbers what else names a page (the floats, the default
     * page) by the same mapping.
     */
    public boolean movePage(int from, int to) {
        if (refusedWhileLocked(HomeLayoutLock.Edit.EDIT_PAGES)) return false;
        LauncherWidgetRepository repository = widgets.repository();
        int count = repository.pageCount();
        if (!com.termux.app.launcher.home.HomePageEdits.canMove(count, from, to)
            || !repository.movePage(from, to)) return false;
        int[] mapping = com.termux.app.launcher.home.HomePageEdits.moveMapping(count, from, to);
        currentPage = com.termux.app.launcher.home.HomePageEdits.remap(currentPage, mapping);
        render();
        host.onHomePagesRenumbered(mapping);
        return true;
    }

    /**
     * Edit pages: page {@code page} goes, and with it every widget, icon and folder on it (the
     * caller has asked first when it held any); a widget's allocation is given back as its own
     * removal does. Never the last page. False when the page could not be taken away.
     */
    public boolean removePage(int page) {
        if (refusedWhileLocked(HomeLayoutLock.Edit.EDIT_PAGES)) return false;
        LauncherWidgetRepository repository = widgets.repository();
        int count = repository.pageCount();
        if (!com.termux.app.launcher.home.HomePageEdits.canRemove(count) || page < 0 || page >= count)
            return false;
        WidgetAddTransaction waiting = repository.pending();
        if (waiting != null && waiting.page == page) {
            // A widget is on its way to this page (its app's setup is open): it stays.
            pane.showNotice(pane.getContext().getString(R.string.home_pages_editor_remove_pending));
            return false;
        }
        if (pane.widgetEditActive()) exitEditMode();
        dismissFolderPopup();
        for (LauncherWidgetRecord record : repository.recordsOnPage(page)) {
            if (widgets.removeWidget(record.appWidgetId)
                    == LauncherWidgetHostController.AddResult.REMOVE_FAILED) {
                // Stop where the store refused. What did go has been renumbered for by the trims
                // its removals ran; the page stays with what is left on it.
                pane.showNotice(messageFor(LauncherWidgetHostController.AddResult.STORAGE_FAILURE));
                render();
                return false;
            }
        }
        int[] mapping = com.termux.app.launcher.home.HomePageEdits.removeMapping(count, page);
        // A page emptied by those removals went with its last widget, and the trim that took it
        // told the host already; a page added by hand is still there and goes now.
        if (repository.pageCount() == count) {
            if (!repository.removePage(page)) {
                pane.showNotice(messageFor(LauncherWidgetHostController.AddResult.STORAGE_FAILURE));
                render();
                return false;
            }
            host.onHomePagesRenumbered(mapping);
        }
        // Edit pages acts on both orientations: the shelved one loses the same page.
        repository.renumberShelvedPages(mapping);
        currentPage = com.termux.app.launcher.home.HomePageEdits.remap(currentPage, mapping);
        clampCurrentPage();
        render();
        return true;
    }

    /**
     * Takes away every empty page ({@link LauncherWidgetRepository#trimEmptyPages}) and hands the
     * renumbering on, so the floats and the default page follow their pages.
     */
    private void trimPages() {
        int[] renumbered = widgets.repository().trimEmptyPagesRenumbering();
        if (renumbered != null) host.onHomePagesRenumbered(renumbered);
    }

    /** Edit pages' Add page: an empty page after the last, kept until something lands on it. */
    public int addPage() {
        if (refusedWhileLocked(HomeLayoutLock.Edit.ADD_PAGE)) return -1;
        int appended = widgets.repository().addFreshPage();
        if (appended >= 0) render();
        return appended;
    }

    private void relayEditorFocus(@Nullable View editor) {
        if (editor != null) {
            editorFocusActive = true;
            host.onWidgetEditorFocused(editor);
        } else if (editorFocusActive) {
            editorFocusActive = false;
            host.onWidgetEditorClosed();
        }
    }

    public void openPicker() {
        if (refusedWhileLocked(HomeLayoutLock.Edit.ADD_ITEM)) return;
        // Built-in widgets need nothing from the platform, so the picker opens even where app
        // widgets are unsupported; the catalogue simply lists the launcher's own.
        pane.picker().setReducedMotion(host.reducedMotion());
        pane.picker().open(); pane.picker().showLoading();
        if (pane.grid().getWidth() == 0 || pane.grid().getHeight() == 0) {
            pane.grid().post(this::loadCatalog);
        } else loadCatalog();
    }

    private void loadCatalog() {
        if (!pane.picker().isOpen()) return;
        WidgetGridMetrics metrics = pane.grid().metrics();
        catalog.load(metrics, widgets.repository().revision(),
            new WidgetProviderCatalogLoader.Callback() {
                @Override public void onCatalogSections(long generation,
                                                        @NonNull List<WidgetAppGroup> sections) {
                    if (pane.picker().isOpen()) pane.picker().showSections(sections);
                }
                @Override public void onCatalog(long generation,
                                                @NonNull List<WidgetAppGroup> groups) {
                    if (!pane.picker().isOpen()) return;
                    pane.picker().adapter().setFitPredicate(WidgetPaneController.this::fitsGrid);
                    pane.picker().showCatalog(groups);
                }
            });
    }

    /**
     * Whether the widget fits the grid at all. A page with no room left is not this: the widget can
     * still be held and carried to another page, so its card stays live. Only a span larger than
     * the whole grid shuts the card, since no page anywhere could take it.
     */
    private boolean fitsGrid(@NonNull WidgetProviderItem item) {
        if (!item.fits || item.columnSpan <= 0 || item.rowSpan <= 0) return false;
        WidgetGridDefinition grid = widgets.repository().gridDefinition();
        return item.columnSpan <= grid.columns && item.rowSpan <= grid.rows;
    }

    private void selectProvider(@NonNull WidgetProviderItem item) {
        LauncherWidgetRepository repository = widgets.repository();
        long revision = repository.revision();
        // New widgets always land on the page the user is looking at.
        WidgetGridPlacementPolicy.Result placement = WidgetGridPlacementPolicy.findPlacement(
            repository.gridDefinition(), repository.recordsOnPage(currentPage),
            item.columnSpan, item.rowSpan);
        if (placement.outcome == WidgetGridPlacementPolicy.Outcome.NO_CONTIGUOUS_SPACE) {
            // It fits the grid, just not this page: the sheet stays up and says how to carry it.
            pane.picker().showNoRoomOnPage();
            return;
        }
        if (placement.outcome != WidgetGridPlacementPolicy.Outcome.PLACED) {
            pane.picker().adapter().setFitPredicate(this::fitsGrid);
            pane.picker().showNoSpace(item.columnSpan, item.rowSpan, repository.gridDefinition());
            return;
        }
        LauncherWidgetHostController.AddResult result = beginAddAt(item, placement.rect,
            currentPage, revision);
        if (result == LauncherWidgetHostController.AddResult.STARTED) {
            awaitingExternal = true; pane.picker().close();
        } else if (result == LauncherWidgetHostController.AddResult.READY) {
            pane.picker().close(); render(); liveOrigin = null;
        } else if (result == LauncherWidgetHostController.AddResult.NO_SPACE) {
            // The span fitted the grid a moment ago, so it is the page that filled up.
            pane.picker().adapter().setFitPredicate(this::fitsGrid);
            pane.picker().showNoRoomOnPage();
        } else {
            pane.showNotice(messageFor(result)); liveOrigin = null;
        }
    }

    /**
     * Binds {@code item} into a cell that has already been chosen, on {@code page}. A tap chooses
     * the first free cell and a carried widget chooses the cell under the finger; from here on
     * there is one add, with one set of initial options and one transaction.
     */
    @NonNull
    private LauncherWidgetHostController.AddResult beginAddAt(@NonNull WidgetProviderItem item,
                                                              @NonNull WidgetCellRect rect,
                                                              int page, long revision) {
        if (item.info == null) {
            // One of the launcher's own: a single durable write, no consent, no configure.
            if (item.builtinKind == null) return LauncherWidgetHostController.AddResult.FAILED;
            return widgets.addBuiltin(item.builtinKind, rect, page, revision,
                builtins.initialConfig(item.builtinKind));
        }
        Rect bounds = pane.grid().metrics().boundsFor(rect);
        // The first options describe the same area the grid will report once the cell is laid
        // out: inside the cell's gutter and the framework's own widget padding. Sizing the bind
        // to the bare cell told the provider it had room it would never get.
        Rect padding = hostPadding(item.info);
        int gutter = WidgetCellView.gutterPx(pane.getResources());
        Bundle options = initialOptions(
            bounds.width() - 2 * gutter - padding.left - padding.right,
            bounds.height() - 2 * gutter - padding.top - padding.bottom);
        liveOrigin = UUID.randomUUID().toString();
        host.captureWidgetSurfaceOrigin();
        return widgets.beginAdd(item.info, rect, page, revision, options, liveOrigin);
    }

    /** The padding the framework's host view will put around this provider's widget. */
    @NonNull private Rect hostPadding(@NonNull AppWidgetProviderInfo info) {
        try {
            return AppWidgetHostView.getDefaultPaddingForWidget(pane.getContext(), info.provider,
                null);
        } catch (RuntimeException exception) {
            return new Rect();
        }
    }

    @NonNull private Bundle initialOptions(int width, int height) {
        WidgetSizeOptionsPolicy.Result calculated = WidgetSizeOptionsPolicy.calculate(new Bundle(),
            width, height, pane.getResources().getDisplayMetrics().density,
            pane.getResources().getConfiguration().orientation, Build.VERSION.SDK_INT);
        return calculated.options;
    }

    @Override public void onWidgetRepositoryChanged(@NonNull LauncherWidgetHostController.AddResult result) {
        // A widget arrived or left: a page with nothing on it goes with it. A carry that has made
        // a page for the widget in the air keeps it until the carry ends, which trims it then.
        if (carry == null || carry.turn.createdPage < 0) trimPages();
        clampCurrentPage();
        render();
        if (result == LauncherWidgetHostController.AddResult.REMOVE_FAILED) {
            pane.showNotice(pane.getContext().getString(R.string.widget_remove_failed));
        }
        // The wall was emptied because nothing on it could be reconnected. One sentence for the
        // whole wall, and the reconciliation that emptied it cannot say this twice: there is
        // nothing left to lose.
        if (result == LauncherWidgetHostController.AddResult.WALL_RESET) {
            pane.showNotice(pane.getContext().getString(R.string.widget_wall_reset));
        }
        if (awaitingExternal && result != LauncherWidgetHostController.AddResult.IGNORED
            && result != LauncherWidgetHostController.AddResult.STARTED) {
            awaitingExternal = false;
            if (!host.isWidgetSurfaceShowing()) host.restoreWidgetSurfaceOrigin();
            if (result != LauncherWidgetHostController.AddResult.READY
                && result != LauncherWidgetHostController.AddResult.WALL_RESET) {
                pane.showNotice(messageFor(result));
            }
        }
        if (result != LauncherWidgetHostController.AddResult.IGNORED) liveOrigin = null;
    }

    /** Live widget-edit session; non-null only while the edit chrome owns the pane. */
    private static final class EditState {
        final int appWidgetId;
        final int minColumnSpan, minRowSpan;
        /** The provider's own ceiling on a span, or 0 where it names none. */
        final int maxColumnSpan, maxRowSpan;
        final boolean horizontalResizable, verticalResizable;
        float dragStartRawX, dragStartRawY;
        Rect dragStartBounds;
        WidgetEditPolicy.Candidate moveCandidate;
        WidgetEditPolicy.Candidate resizeCandidate;
        /** The page the move is over, and the turns at the pane's edge that brought it there. */
        final PageTurn turn = new PageTurn();
        /** True once the widget has been taken out of the grid to cross pages. */
        boolean lifted;
        float lastRawX, lastRawY;
        /**
         * The icon or folder the dragged app icon rests over, or 0 for none ({@link
         * HomeFolderPolicy}): released there after the hover, the two merge. No real key is 0.
         */
        int mergeTargetId;
        /** When the rest over {@link #mergeTargetId} began, in uptime milliseconds. */
        long mergeSince;
        EditState(int appWidgetId, int minColumnSpan, int minRowSpan,
                  int maxColumnSpan, int maxRowSpan,
                  boolean horizontalResizable, boolean verticalResizable) {
            this.appWidgetId = appWidgetId;
            this.minColumnSpan = minColumnSpan;
            this.minRowSpan = minRowSpan;
            this.maxColumnSpan = maxColumnSpan;
            this.maxRowSpan = maxRowSpan;
            this.horizontalResizable = horizontalResizable;
            this.verticalResizable = verticalResizable;
        }
    }

    private EditState edit;

    /**
     * A drag that can turn the page by resting in the pane's edge band: a widget being moved in
     * edit mode, or one carried out of the picker. Both turn pages the same way, so both keep this.
     */
    private static final class PageTurn {
        /** The page the drag is over now: where it started until the finger turns it. */
        int page;
        /** Which edge band the finger is resting in: -1 the leading one, +1 the trailing one. */
        int edgeDirection;
        /** The page this drag made past the end of the run, or -1: one drag makes at most one. */
        int createdPage = -1;
        /**
         * Whether the edge bands count yet. A drag that starts inside a band (a picker card held
         * near the pane's edge, a widget sitting at the grid's edge) arms them only once the
         * finger has been outside a band or has travelled a band's width from where the hold
         * began; otherwise the page turns 350 ms after the hold with the finger never having moved.
         */
        boolean armed;
        float startRawX;

        void start(int startPage, float rawX) {
            page = startPage; edgeDirection = 0; createdPage = -1; armed = false; startRawX = rawX;
        }
    }

    /**
     * Neighbours currently shown pushed aside, appWidgetId to the cell they preview. One drag runs
     * at a time — a widget being moved, a widget being resized, or a widget being carried in from
     * the picker — and all three shuffle the page the same way, so all three preview it from here.
     */
    @NonNull private Map<Integer, WidgetCellRect> previewDisplaced = Collections.emptyMap();

    /** How long a dragged widget rests in an edge band before the page turns under it. */
    private static final long EDGE_FLIP_DELAY_MS = 350L;
    /** How wide that band is, measured in from the pane's leading and trailing edges. */
    private static final int EDGE_BAND_DP = 24;

    private final Runnable edgeFlip = this::flipDragPage;
    /** The hover over a merge target has lasted long enough: say so with the ghost. */
    private final Runnable mergeArm = this::armMergeTarget;
    /** How much a merge target swells once a release would merge into it. */
    private static final float MERGE_TARGET_SCALE = 1.12f;
    private final int[] paneLocation = new int[2];

    /** The layout as the edit session found it; the cross on the border tab puts it back. */
    private static final class EditSnapshot {
        @NonNull final List<LauncherWidgetRecord> records;
        final int pageCount;
        /** Which of those pages were the user's own empty ones, so the cross puts them back too. */
        @NonNull final java.util.Set<Integer> freshPages;
        EditSnapshot(@NonNull List<LauncherWidgetRecord> records, int pageCount,
                     @NonNull java.util.Set<Integer> freshPages) {
            this.records = records;
            this.pageCount = pageCount;
            this.freshPages = freshPages;
        }
    }

    @Nullable private EditSnapshot editSnapshot;

    private final WidgetEditOverlayView.Listener overlayListener =
        new WidgetEditOverlayView.Listener() {
            @Override public void onMoveDragStart(float rawX, float rawY) {
                if (edit == null) return;
                beginMoveDrag(rawX, rawY);
                pane.widgetEditOverlay().setDragging(true);
            }
            @Override public void onMoveDragMove(float rawX, float rawY) {
                if (edit != null) moveDrag(rawX, rawY);
            }
            @Override public void onMoveDragEnd(boolean canceled) {
                if (edit != null) endMoveDrag(canceled);
            }
            @Override public void onResizeDrag(@NonNull WidgetEditPolicy.Handle handle,
                                               int desiredEdgePx) {
                if (edit != null) resizeDrag(handle, desiredEdgePx);
            }
            @Override public void onResizeDragEnd() {
                if (edit != null) endResizeDrag();
            }
            @Override public void onRemove() {
                if (edit == null) return;
                int appWidgetId = edit.appWidgetId;
                exitEditMode();
                host.confirmWidgetRemoval(appWidgetId, () -> widgets.removeWidget(appWidgetId));
            }
            @Override public void onConfigure() {
                if (edit == null) return;
                openWidgetSettings(edit.appWidgetId);
            }
            @Override public void onSelectWidget(int appWidgetId, float rawX, float rawY) {
                if (edit != null && edit.appWidgetId == appWidgetId) return;
                enterEditMode(appWidgetId, rawX, rawY);
            }
            @Override public void onDismiss() { exitEditMode(); }
        };

    /**
     * Selects a widget with the finger already down on it: a long-press on a widget outside edit
     * mode, or a press on one of the outlined widgets while a session is open.
     */
    private void enterEditMode(int appWidgetId, float rawX, float rawY) {
        dismissFolderPopup();
        // Anything the outgoing selection was previewing belongs to a plan that is now over.
        clearDisplacementPreview(false);
        if (!beginEditSession(appWidgetId)) return;
        // The finger is still down: this same gesture continues as a move drag.
        beginMoveDrag(rawX, rawY);
        pane.widgetEditOverlay().setDragging(true);
    }

    /** Shared edit-chrome entry; menu entry stops here, a widget long-press continues as a drag. */
    private boolean beginEditSession(int appWidgetId) {
        LauncherWidgetRecord record = widgets.repository().get(appWidgetId);
        WidgetCellView cell = pane.grid().cellForId(appWidgetId);
        if (record == null || cell == null) return false;
        // The first widget of a session is where the discard button's "as it was" comes from;
        // selecting another widget later is the same session and must not move that mark.
        if (editSnapshot == null) {
            editSnapshot = new EditSnapshot(widgets.repository().records(),
                widgets.repository().pageCount(), widgets.repository().freshPages());
        }
        // Only an app widget has a provider to ask; an app icon is 1×1 with no handles.
        AppWidgetProviderInfo info = record.hasPlatformId()
            ? widgets.providerInfo(appWidgetId) : null;
        WidgetGridMetrics metrics = pane.grid().metrics();
        int minColumns = 1, minRows = 1;
        int maxColumns = 0, maxRows = 0;
        boolean horizontal = false, vertical = false;
        if (record.isBuiltin()) {
            // Designed at five sizes and drawn at the largest that fits: any size the grid
            // allows down to the smallest design, measured in pixels like an app widget's
            // minimum, so a dense grid cannot squeeze one into a cell its text will not fit.
            horizontal = true; vertical = true;
            float density = pane.getResources().getDisplayMetrics().density;
            WidgetGridMetrics.Span minSpan = metrics.spanForPixels(
                Math.round(BuiltinWidgetSpan.ONE_BY_ONE.widthDp * density),
                Math.round(BuiltinWidgetSpan.ONE_BY_ONE.heightDp * density));
            minColumns = minSpan.columns > 0
                ? Math.min(minSpan.columns, record.cell.columnSpan()) : record.cell.columnSpan();
            minRows = minSpan.rows > 0
                ? Math.min(minSpan.rows, record.cell.rowSpan()) : record.cell.rowSpan();
        } else if (info != null && record.state == LauncherWidgetRecord.State.ACTIVE) {
            horizontal = (info.resizeMode
                & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
            vertical = (info.resizeMode
                & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
            WidgetGridMetrics.Span minSpan = metrics.spanForPixels(
                Math.max(1, info.minResizeWidth), Math.max(1, info.minResizeHeight));
            minColumns = minSpan.columns > 0
                ? Math.min(minSpan.columns, record.cell.columnSpan()) : record.cell.columnSpan();
            minRows = minSpan.rows > 0
                ? Math.min(minSpan.rows, record.cell.rowSpan()) : record.cell.rowSpan();
            // Android asks hosts not to grow a widget past the maximum it names; the span is read
            // against the provider's content size like the minimum is, not the cell's.
            int padWidth = cell == null || cell.getWidth() <= 0 ? 0
                : cell.getWidth() - cell.providerContentWidth();
            int padHeight = cell == null || cell.getHeight() <= 0 ? 0
                : cell.getHeight() - cell.providerContentHeight();
            // Maximums arrived in API 31; reading the fields before it throws NoSuchFieldError.
            int maxWidth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? info.maxResizeWidth : 0;
            int maxHeight = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? info.maxResizeHeight : 0;
            WidgetGridMetrics.Span maxSpan = metrics.largestSpanWithin(
                maxWidth > 0 ? maxWidth + padWidth : 0,
                maxHeight > 0 ? maxHeight + padHeight : 0);
            maxColumns = maxSpan.columns;
            maxRows = maxSpan.rows;
        }
        edit = new EditState(appWidgetId, minColumns, minRows, maxColumns, maxRows,
            horizontal, vertical);
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        overlay.setListener(overlayListener);
        overlay.show(paneBounds(record.cell), horizontal, vertical, editableOutlines(appWidgetId),
            record.isBuiltin() ? builtins.hasSettings(record) : widgets.canReconfigure(appWidgetId));
        syncEditSession();
        return true;
    }

    /**
     * The cog on the selected widget: hand the user back to the provider's own settings screen.
     * It is another app's activity, so the surface is remembered the way the add flow remembers
     * it, and the edit session closes — coming back to chrome measured against the old layout
     * would be wrong if the provider resized itself.
     */
    private void openWidgetSettings(int appWidgetId) {
        LauncherWidgetRecord builtin = widgets.repository().get(appWidgetId);
        if (builtin != null && builtin.isBuiltin()) {
            // The launcher's own sheet, over the page: nothing leaves, the session simply ends.
            exitEditMode();
            BuiltinWidgetKind kind = BuiltinWidgetKind.fromId(builtin.builtinKind);
            CharSequence title = pane.getResources().getString(
                R.string.builtin_widget_settings_title,
                kind == null ? builtin.builtinKind : pane.getResources().getString(kind.label));
            builtins.services().host().onSystemImeRequested();
            BuiltinWidgetConfigSheet.show(pane.getContext(), title, builtins.configFields(builtin),
                builtins.settingsOf(builtin), config -> {
                    if (widgets.updateBuiltinConfig(appWidgetId, config)) render();
                }, () -> builtins.services().host().onSystemImeReleased());
            return;
        }
        exitEditMode();
        host.captureWidgetSurfaceOrigin();
        LauncherWidgetHostController.AddResult result = widgets.reconfigureWidget(appWidgetId);
        if (result == LauncherWidgetHostController.AddResult.STARTED) {
            awaitingExternal = true;
        } else if (result != LauncherWidgetHostController.AddResult.IGNORED) {
            pane.showNotice(messageFor(result));
        }
    }

    /**
     * The rest of the page, outlined so edit mode reads as page-wide and one press can take the
     * selection anywhere. Measured here because every session — a pencil, a long-press, the chrome
     * restored after a render — comes through this method, so the outlines can never lag the grid.
     */
    @NonNull private List<WidgetEditOverlayView.Outline> editableOutlines(int selectedId) {
        List<WidgetEditOverlayView.Outline> outlines = new ArrayList<>();
        for (LauncherWidgetRecord other : widgets.repository().recordsOnPage(currentPage)) {
            if (other.appWidgetId == selectedId) continue;
            outlines.add(new WidgetEditOverlayView.Outline(other.appWidgetId,
                paneBounds(other.cell)));
        }
        return outlines;
    }

    private void exitEditMode() {
        pane.removeCallbacks(edgeFlip);
        clearMergeTarget();
        if (edit != null && edit.lifted) returnDraggedCell(edit.appWidgetId);
        pane.releaseWidgetDragLayer();
        clearDisplacementPreview(false);
        edit = null;
        editSnapshot = null;
        pane.hideWidgetEditOverlay();
        syncEditSession();
    }

    /** The tick on the page's border tab. Every change is already saved, so this only closes. */
    void commitEditSession() {
        if (edit == null) return;
        exitEditMode();
    }

    /**
     * The cross. Everything the session moved, resized or carried to another page goes back where
     * it was, pages and all. A widget dropped in the bin does not come back: it was handed to
     * Android the moment it was dropped, and nothing here still holds it.
     */
    void discardEditSession() {
        if (edit == null) return;
        EditSnapshot snapshot = editSnapshot;
        exitEditMode();
        if (snapshot == null) return;
        LauncherWidgetRepository repository = widgets.repository();
        // Room for the pages the snapshot knew about before anything is put back on them.
        repository.setPages(Math.max(repository.pageCount(), snapshot.pageCount),
            snapshot.freshPages);
        List<LauncherWidgetRecord> batch = new ArrayList<>();
        for (LauncherWidgetRecord was : snapshot.records) {
            LauncherWidgetRecord now = repository.get(was.appWidgetId);
            if (now == null) continue;
            if (now.page != was.page || !now.cell.equals(was.cell)) {
                batch.add(now.withPage(was.page).withCell(was.cell));
            }
        }
        if (!batch.isEmpty()) repository.putRecords(batch);
        repository.setPages(snapshot.pageCount, snapshot.freshPages);
        // A widget binned during the session does not come back, so the page it was the last thing
        // on does not either: the restored count is trimmed to what the layout now holds.
        trimPages();
        clampCurrentPage();
        render();
    }

    /** Whether the host has been told the edit chrome is up. */
    private boolean editSessionAnnounced;

    /** Tell the host when, and only when, that has changed. */
    private void syncEditSession() {
        boolean active = pane.widgetEditActive();
        if (active == editSessionAnnounced) return;
        editSessionAnnounced = active;
        host.onWidgetEditSessionChanged(active);
    }

    private void beginMoveDrag(float rawX, float rawY) {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null) { exitEditMode(); return; }
        edit.dragStartRawX = rawX;
        edit.dragStartRawY = rawY;
        edit.dragStartBounds = pane.grid().metrics().boundsFor(record.cell);
        edit.moveCandidate = null;
        edit.turn.start(record.page, rawX);
        edit.lastRawX = rawX;
        edit.lastRawY = rawY;
        pane.removeCallbacks(edgeFlip);
        clearDisplacementPreview(false);
        // Hold the live view still for the drag's duration: a provider push mid-move would
        // re-inflate its content under the finger. endMoveDrag releases it; a dropped gesture is
        // covered by SafeLauncherAppWidgetHostView's own 1 s timeout.
        SafeLauncherAppWidgetHostView hostView = safeHostViewFor(edit.appWidgetId);
        if (hostView != null) hostView.beginDeferringUpdates();
    }

    /** The live host view for {@code appWidgetId}, or null when there is none or it is not ours. */
    @Nullable private SafeLauncherAppWidgetHostView safeHostViewFor(int appWidgetId) {
        AppWidgetHostView view = widgets.createHostView(appWidgetId);
        return view instanceof SafeLauncherAppWidgetHostView
            ? (SafeLauncherAppWidgetHostView) view : null;
    }

    private void moveDrag(float rawX, float rawY) {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null || edit.dragStartBounds == null) return;
        edit.lastRawX = rawX;
        edit.lastRawY = rawY;
        float translationX = rawX - edit.dragStartRawX;
        float translationY = rawY - edit.dragStartRawY;
        if (edit.lifted) {
            Rect carried = paneBounds(record.cell);
            carried.offset(Math.round(translationX), Math.round(translationY));
            pane.widgetDragLayer().moveTo(carried);
        } else {
            WidgetCellView cell = pane.grid().cellForId(edit.appWidgetId);
            if (cell == null) return;
            cell.setTranslationX(translationX);
            cell.setTranslationY(translationY);
            cell.setTranslationZ(dp(8));
        }
        watchPageEdge(edit.turn, rawX);
        updateDragCandidate(record);
    }

    /**
     * Where the widget would land on the page it is over, and what that page would have to shuffle
     * to take it. The page is {@link PageTurn#page}, which is the widget's own until a turn
     * at the edge moves it on, so the same ghost and the same neighbour preview serve both.
     */
    private void updateDragCandidate(@NonNull LauncherWidgetRecord record) {
        if (edit.dragStartBounds == null) return;
        Rect dragged = new Rect(edit.dragStartBounds);
        dragged.offset(Math.round(edit.lastRawX - edit.dragStartRawX),
            Math.round(edit.lastRawY - edit.dragStartRawY));
        WidgetGridMetrics metrics = pane.grid().metrics();
        edit.moveCandidate = WidgetEditPolicy.snapMove(metrics,
            widgets.repository().recordsOnPage(edit.turn.page), edit.appWidgetId, record.cell,
            dragged);
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        LauncherWidgetRecord over = mergeTargetUnder(record, dragged.centerX(), dragged.centerY());
        if (over != null) {
            // Resting over an icon or a folder: the page holds still under it, so the target does
            // not slide out from under the finger. The ghost marks it once a release would merge.
            if (edit.mergeTargetId != over.appWidgetId) {
                clearMergeTarget();
                edit.mergeTargetId = over.appWidgetId;
                edit.mergeSince = SystemClock.uptimeMillis();
                pane.postDelayed(mergeArm, HomeFolderPolicy.MERGE_HOVER_MS);
                overlay.setGhostBounds(null);
            }
            overlay.clearRefusal();
            previewDisplacement(Collections.emptyMap());
            return;
        }
        clearMergeTarget();
        showRefusal(edit.moveCandidate.refusedBy, edit.turn.page);
        if (edit.moveCandidate.valid) {
            overlay.setGhostBounds(paneBounds(edit.moveCandidate.rect), true);
        } else if (edit.turn.page != record.page) {
            // Nowhere on this page to put it: the ghost stays under the finger and says so in red.
            WidgetEditPolicy.Candidate nearest = WidgetEditPolicy.snapMove(metrics,
                Collections.emptyList(), edit.appWidgetId, record.cell, dragged);
            overlay.setGhostBounds(paneBounds(nearest.rect), false);
        } else {
            overlay.setGhostBounds(null);
        }
        previewDisplacement(edit.moveCandidate.valid
            ? edit.moveCandidate.displaced : Collections.emptyMap());
    }

    /**
     * A widget held against the pane's leading or trailing edge turns the page after a pause, and
     * turns it again for as long as it is held there. Leaving the band stops it; so does the first
     * page going back. Past the last page there is one more turn: a page is made for the widget,
     * and it is the drop that keeps it.
     */
    private void watchPageEdge(@NonNull PageTurn turn, float rawX) {
        pane.getLocationOnScreen(paneLocation);
        float x = rawX - paneLocation[0];
        float band = dp(EDGE_BAND_DP);
        int direction = 0;
        if (pane.getWidth() > 2 * band) {
            if (x <= band) direction = -1;
            else if (x >= pane.getWidth() - band) direction = 1;
        }
        if (!turn.armed) {
            if (direction != 0 && Math.abs(rawX - turn.startRawX) < band) return;  // Still where the hold began.
            turn.armed = true;
        }
        if (direction != 0 && !canTurnTo(turn, turn.page + direction, direction)) direction = 0;
        if (direction == turn.edgeDirection) return;
        turn.edgeDirection = direction;
        pane.removeCallbacks(edgeFlip);
        if (direction != 0) pane.postDelayed(edgeFlip, EDGE_FLIP_DELAY_MS);
    }

    /**
     * Whether the drag can turn onto that page: one that is there, or the one page past the end
     * this drag is allowed to make. Never past the first page, and never a second new one.
     */
    private boolean canTurnTo(@NonNull PageTurn turn, int target, int direction) {
        if (target < 0) return false;
        int pages = widgets.repository().pageCount();
        if (target < pages) return true;
        return direction > 0 && target == pages && turn.createdPage < 0;
    }

    /** The pause elapsed with the widget still in the band: the neighbouring page comes in. */
    private void flipDragPage() {
        if (carry != null) { flipCarryPage(carry); return; }
        if (edit == null || edit.dragStartBounds == null || !mayTurn(edit.turn)) return;
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null) return;
        if (!liftDraggedWidget(record)) { edit.turn.edgeDirection = 0; return; }
        if (!turnPage(edit.turn)) return;
        updateDragCandidate(record);
        // Still in the band: the next page follows after the same pause.
        pane.postDelayed(edgeFlip, EDGE_FLIP_DELAY_MS);
    }

    /** Whether the band the finger rests in still leads somewhere; it stops watching if not. */
    private boolean mayTurn(@NonNull PageTurn turn) {
        int direction = turn.edgeDirection;
        if (direction != 0 && canTurnTo(turn, turn.page + direction, direction)) return true;
        turn.edgeDirection = 0;
        return false;
    }

    /**
     * Turns the pane one page in the direction of the band, sliding the page in as a swipe does.
     * Past the last page the page being turned onto is made here. It is an ordinary empty page,
     * so the trim at the end of the drag takes it away again unless the widget is dropped on it.
     */
    private boolean turnPage(@NonNull PageTurn turn) {
        int direction = turn.edgeDirection;
        int target = turn.page + direction;
        if (target >= widgets.repository().pageCount()) {
            int appended = widgets.repository().addPage();
            if (appended < 0) { turn.edgeDirection = 0; return false; }
            turn.createdPage = appended;
            target = appended;
        }
        clearDisplacementPreview(false);
        turn.page = target;
        currentPage = target;
        render();
        pane.slideInFrom(direction);
        return true;
    }

    /**
     * Takes the dragged widget out of the grid for the crossing. What the finger carries from here
     * is a picture of the cell in the pane's drag layer; the cell itself stays attached, hidden and
     * off the page, because the gesture is being delivered through it and removing it with the page
     * it left would cancel the drag in mid-air.
     */
    private boolean liftDraggedWidget(@NonNull LauncherWidgetRecord record) {
        if (edit.lifted) return true;
        WidgetCellView cell = pane.grid().cellForId(edit.appWidgetId);
        if (cell == null) return false;
        Rect carried = paneBounds(record.cell);
        carried.offset(Math.round(cell.getTranslationX()), Math.round(cell.getTranslationY()));
        if (!pane.widgetDragLayer().lift(cell, carried)) return false;
        cell.setTranslationX(0f);
        cell.setTranslationY(0f);
        cell.setTranslationZ(0f);
        cell.setVisibility(View.INVISIBLE);
        pane.grid().setDragPinned(edit.appWidgetId);
        edit.lifted = true;
        return true;
    }

    /** Gives the hidden cell back to the grid; the picture it stood in for is dropped separately. */
    private void returnDraggedCell(int appWidgetId) {
        WidgetCellView cell = pane.grid().cellForId(appWidgetId);
        if (cell != null) {
            cell.setTranslationX(0f);
            cell.setTranslationY(0f);
            cell.setTranslationZ(0f);
            cell.setVisibility(View.VISIBLE);
        }
        pane.grid().setDragPinned(0);
        if (edit != null) edit.lifted = false;
    }

    /**
     * Slides the neighbours a candidate pushes aside to where they would land. Only the ones
     * whose target actually changed since the last move event are touched, so a drag that keeps
     * the same plan costs nothing per frame.
     */
    private void previewDisplacement(@NonNull Map<Integer, WidgetCellRect> next) {
        Map<Integer, WidgetCellRect> previous = previewDisplaced;
        if (previous.equals(next)) return;
        WidgetGridMetrics metrics = pane.grid().metrics();
        for (Map.Entry<Integer, WidgetCellRect> entry : previous.entrySet()) {
            if (!next.containsKey(entry.getKey())) slideCell(entry.getKey(), null, metrics, true);
        }
        for (Map.Entry<Integer, WidgetCellRect> entry : next.entrySet()) {
            if (entry.getValue().equals(previous.get(entry.getKey()))) continue;
            slideCell(entry.getKey(), entry.getValue(), metrics, true);
        }
        previewDisplaced = next;
    }

    /** Returns every previewed neighbour to its real position; the plan is dropped either way. */
    private void clearDisplacementPreview(boolean animate) {
        if (previewDisplaced.isEmpty()) return;
        WidgetGridMetrics metrics = pane.grid().metrics();
        for (Integer appWidgetId : previewDisplaced.keySet()) {
            slideCell(appWidgetId, null, metrics, animate);
        }
        previewDisplaced = Collections.emptyMap();
    }

    /** A null target means "back where the layout puts you". Never raises the cell. */
    private void slideCell(int appWidgetId, @Nullable WidgetCellRect target,
                           @NonNull WidgetGridMetrics metrics, boolean animate) {
        WidgetCellView cell = pane.grid().cellForId(appWidgetId);
        if (cell == null) return;
        float translationX = 0f, translationY = 0f;
        if (target != null) {
            LauncherWidgetRecord record = widgets.repository().get(appWidgetId);
            if (record == null) return;
            Rect from = metrics.boundsFor(record.cell);
            Rect to = metrics.boundsFor(target);
            translationX = to.left - from.left;
            translationY = to.top - from.top;
        }
        cell.animate().cancel();
        if (animate && !host.reducedMotion()) {
            cell.animate().translationX(translationX).translationY(translationY)
                .setDuration(160).start();
        } else {
            cell.setTranslationX(translationX);
            cell.setTranslationY(translationY);
        }
    }

    private void endMoveDrag(boolean canceled) {
        pane.removeCallbacks(edgeFlip);
        edit.turn.edgeDirection = 0;
        int appWidgetId = edit.appWidgetId;
        boolean lifted = edit.lifted;
        SafeLauncherAppWidgetHostView hostView = safeHostViewFor(appWidgetId);
        if (hostView != null) hostView.endDeferringUpdates();
        LauncherWidgetRecord record = widgets.repository().get(appWidgetId);
        if (!canceled && record != null && edit.mergeTargetId != 0) {
            LauncherWidgetRecord target = widgets.repository().get(edit.mergeTargetId);
            HomeFolderPolicy.Drop drop = HomeFolderPolicy.dropAction(record, target,
                SystemClock.uptimeMillis() - edit.mergeSince, folderCapacity);
            if (drop != HomeFolderPolicy.Drop.MOVE && finishMerge(record, target)) return;
        }
        clearMergeTarget();
        WidgetEditPolicy.Candidate candidate = edit.moveCandidate;
        int target = edit.turn.page;
        edit.moveCandidate = null;
        edit.dragStartBounds = null;
        boolean crossed = record != null && target != record.page;
        boolean committed = false;
        if (!canceled && record != null && candidate != null && candidate.valid
            && (crossed || !candidate.rect.equals(record.cell))) {
            committed = commitMove(record, candidate, target);
        }
        // Dropped over a page with no room for it: the widget goes home, and so does the page.
        boolean blocked = crossed && !committed && !canceled;
        // A widget that would not step aside says so as the drop springs back. A drop sent home
        // from another page clears it instead: the page it was measured on is going away, and the
        // no-room notice below already explains that one.
        if (canceled || (crossed && !committed)) pane.widgetEditOverlay().clearRefusal();
        else pane.widgetEditOverlay().flashRefusal();
        if (lifted) returnDraggedCell(appWidgetId);
        // The real layout takes over on render(); a surviving translation would double the offset.
        clearDisplacementPreview(!committed);
        WidgetCellView cell = lifted ? null : pane.grid().cellForId(appWidgetId);
        if (cell != null) {
            if (committed || host.reducedMotion()) {
                cell.setTranslationX(0f); cell.setTranslationY(0f); cell.setTranslationZ(0f);
            } else {
                cell.animate().translationX(0f).translationY(0f).translationZ(0f)
                    .setDuration(160).start();
            }
        }
        int backwards = 0;
        if (crossed && !committed) backwards = record.page > target ? 1 : -1;
        boolean created = edit.turn.createdPage >= 0;
        edit.turn.createdPage = -1;
        // The drag is over: a page it made and did not land on goes again, and so does a page the
        // widget it carried away was the last thing on.
        if (committed || created) trimPages();
        if (committed || crossed) {
            // The trim can have renumbered the pages under all this; the widget's own page is
            // where the pane belongs, whether it landed there or was sent home.
            LauncherWidgetRecord settled = widgets.repository().get(appWidgetId);
            if (settled != null) currentPage = settled.page;
        }
        clampCurrentPage();
        edit.turn.page = currentPage;
        if (committed || crossed || created) {
            render();
            if (backwards != 0) pane.slideInFrom(backwards);
        } else {
            WidgetEditOverlayView overlay = pane.widgetEditOverlay();
            overlay.setDragging(false);
            overlay.setGhostBounds(null);
        }
        if (lifted) {
            LauncherWidgetRecord landed = widgets.repository().get(appWidgetId);
            pane.widgetDragLayer().drop(landed == null ? null : paneBounds(landed.cell),
                !host.reducedMotion());
        }
        if (blocked) {
            pane.showNotice(pane.getContext().getString(R.string.widget_no_room_on_page));
        }
    }

    /**
     * The app icon or folder whose cell, on the page the drag is over, holds the dragged icon's
     * centre and could take it ({@link HomeFolderPolicy#canTarget}); null over anything else.
     */
    @Nullable
    private LauncherWidgetRecord mergeTargetUnder(@NonNull LauncherWidgetRecord dragged,
                                                  int centerX, int centerY) {
        if (!dragged.isApp()) return null;
        WidgetGridMetrics metrics = pane.grid().metrics();
        for (LauncherWidgetRecord other : widgets.repository().recordsOnPage(edit.turn.page)) {
            if (other.appWidgetId == dragged.appWidgetId) continue;
            if (!metrics.boundsFor(other.cell).contains(centerX, centerY)) continue;
            return HomeFolderPolicy.canTarget(dragged, other, folderCapacity) ? other : null;
        }
        return null;
    }

    /** The hover lasted: the target swells and the ghost sits on it, so a release reads as a merge. */
    private void armMergeTarget() {
        if (edit == null || edit.mergeTargetId == 0) return;
        LauncherWidgetRecord dragged = widgets.repository().get(edit.appWidgetId);
        LauncherWidgetRecord target = widgets.repository().get(edit.mergeTargetId);
        if (dragged == null || HomeFolderPolicy.dropAction(dragged, target,
            SystemClock.uptimeMillis() - edit.mergeSince, folderCapacity)
            == HomeFolderPolicy.Drop.MOVE) return;
        pane.widgetEditOverlay().setGhostBounds(paneBounds(target.cell), true);
        WidgetCellView cell = pane.grid().cellForId(target.appWidgetId);
        if (cell == null) return;
        if (host.reducedMotion()) {
            cell.setScaleX(MERGE_TARGET_SCALE); cell.setScaleY(MERGE_TARGET_SCALE);
        } else {
            cell.animate().scaleX(MERGE_TARGET_SCALE).scaleY(MERGE_TARGET_SCALE)
                .setDuration(120).start();
        }
    }

    /** Forgets the merge target, if any, and lets it settle back to its size. */
    private void clearMergeTarget() {
        pane.removeCallbacks(mergeArm);
        if (edit == null || edit.mergeTargetId == 0) return;
        WidgetCellView cell = pane.grid().cellForId(edit.mergeTargetId);
        if (cell != null) {
            cell.animate().scaleX(1f).scaleY(1f).setDuration(host.reducedMotion() ? 0 : 120)
                .start();
        }
        edit.mergeTargetId = 0;
        edit.mergeSince = 0L;
    }

    /**
     * The drop merged: the dragged icon and {@code target} become one folder, or the icon joins
     * the folder ({@link LauncherWidgetRepository#mergeIntoFolder}), in one commit. The session
     * carries on with the folder selected; the dragged icon is gone, so the cross on the border
     * tab does not bring it back, as it does not bring back a widget dropped in the bin.
     */
    private boolean finishMerge(@NonNull LauncherWidgetRecord record,
                                @NonNull LauncherWidgetRecord target) {
        int appWidgetId = record.appWidgetId;
        boolean lifted = edit.lifted;
        clearMergeTarget();
        // Straight to the store, as a move commits: the session settles the page itself below.
        LauncherWidgetRecord folder = widgets.repository().mergeIntoFolder(appWidgetId,
            target.appWidgetId, folderFallbackName(), folderCapacity);
        if (folder == null) return false;
        edit.moveCandidate = null;
        edit.dragStartBounds = null;
        edit.turn.createdPage = -1;
        if (lifted) returnDraggedCell(appWidgetId);
        clearDisplacementPreview(false);
        WidgetCellView cell = pane.grid().cellForId(target.appWidgetId);
        if (cell != null) { cell.animate().cancel(); cell.setScaleX(1f); cell.setScaleY(1f); }
        // A page the drag made, or one the icon was the last thing on, goes again.
        trimPages();
        LauncherWidgetRecord settled = widgets.repository().get(folder.appWidgetId);
        if (settled != null) currentPage = settled.page;
        clampCurrentPage();
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        overlay.setDragging(false);
        overlay.setGhostBounds(null);
        edit = null;
        render();
        if (settled != null) beginEditSession(settled.appWidgetId);
        if (edit == null) exitEditMode();
        if (lifted) {
            pane.widgetDragLayer().drop(settled == null ? null : paneBounds(settled.cell),
                !host.reducedMotion());
        }
        return true;
    }

    /** One atomic commit for the dragged widget and everything it pushed aside. */
    private boolean commitMove(@NonNull LauncherWidgetRecord record,
                               @NonNull WidgetEditPolicy.Candidate candidate, int page) {
        LauncherWidgetRecord moved = record.withPage(page).withCell(candidate.rect);
        if (candidate.displaced.isEmpty()) {
            return widgets.repository().putRecord(moved);
        }
        List<LauncherWidgetRecord> batch = new ArrayList<>();
        batch.add(moved);
        for (Map.Entry<Integer, WidgetCellRect> entry : candidate.displaced.entrySet()) {
            LauncherWidgetRecord neighbour = widgets.repository().get(entry.getKey());
            if (neighbour == null) return false;
            batch.add(neighbour.withCell(entry.getValue()));
        }
        return widgets.repository().putRecords(batch);
    }


    // ---- A widget carried out of the picker -------------------------------------------------

    /**
     * The id the snap runs the carried widget under. It has no {@code appWidgetId} until it binds,
     * and the snap needs one to leave out of the page it is measuring against; no real widget can
     * ever carry this value, so nothing on the page is accidentally ignored.
     */
    private static final int CARRIED_APP_WIDGET_ID = Integer.MIN_VALUE;

    /** A widget on its way from a picker card to the cell the finger is choosing for it. */
    private static final class CarryState {
        @NonNull final WidgetProviderItem item;
        /**
         * The page under the finger: the one on screen when the card was held, until a rest in
         * the pane's edge band turns it, making one past the last page if it has to.
         */
        final PageTurn turn = new PageTurn();
        @NonNull final WidgetCellRect span;
        /** The picture's size in grid pixels — the cell the widget will occupy, not the card. */
        final int width, height;
        @Nullable WidgetEditPolicy.Candidate candidate;
        /** Where the finger last was, so a page turn can measure the new page under it. */
        float lastRawX, lastRawY;
        CarryState(@NonNull WidgetProviderItem item, int page, @NonNull WidgetCellRect span,
                   int width, int height, float rawX) {
            this.item = item; this.span = span;
            this.width = width; this.height = height;
            turn.start(page, rawX);
        }
    }

    @Nullable private CarryState carry;

    private final WidgetPaneView.CarryListener carryListener = new WidgetPaneView.CarryListener() {
        @Override public void onCarryMove(float rawX, float rawY) { carryTo(rawX, rawY); }
        @Override public void onCarryEnd(float rawX, float rawY, boolean canceled) {
            endCarry(rawX, rawY, canceled);
        }
    };

    /**
     * A card was held: the widget comes out of the sheet and follows the finger. The picture is
     * the card's own, drawn at the size the widget will be on the grid, so what the finger carries
     * and what the target under it outlines are the same shape.
     *
     * <p>A page with no room is no reason to refuse: resting the widget in the pane's trailing
     * edge band turns to the next page, and makes one past the last. Only a widget larger than the whole grid is refused, as its greyed card
     * refuses the tap, so the one rule about space is stated in one place.
     */
    void beginCarry(@NonNull WidgetProviderItem item, @NonNull View card, float rawX, float rawY) {
        if (carry != null || edit != null || awaitingExternal) return;
        if (!fitsGrid(item)) return;
        WidgetCellRect span = new WidgetCellRect(0, 0, item.columnSpan, item.rowSpan);
        Rect cell = pane.grid().metrics().boundsFor(span);
        if (cell.width() <= 0 || cell.height() <= 0) return;
        // The slot is the card's picture of the widget; the card around it is a list row.
        View picture = card.findViewWithTag("slot");
        if (picture == null) picture = card;
        CarryState state = new CarryState(item, currentPage, span, cell.width(), cell.height(), rawX);
        if (!pane.widgetDragLayer().lift(picture, carriedBounds(state, rawX, rawY))) return;
        carry = state;
        pane.beginCarry(carryListener);
        // The sheet has done its job; the page under it is what the rest of this gesture is about.
        pane.picker().close();
        carryTo(rawX, rawY);
    }

    /** Where the carried picture sits for a finger at {@code rawX, rawY}, in pane coordinates. */
    @NonNull private Rect carriedBounds(@NonNull CarryState state, float rawX, float rawY) {
        pane.getLocationOnScreen(paneLocation);
        int centerX = Math.round(rawX - paneLocation[0]);
        int centerY = Math.round(rawY - paneLocation[1]);
        return new Rect(centerX - state.width / 2, centerY - state.height / 2,
            centerX - state.width / 2 + state.width, centerY - state.height / 2 + state.height);
    }

    /**
     * The carried widget follows the finger, and the page says where it would land. The snap and
     * the shuffle are the ones a widget already on the page gets when it is moved — the carried
     * widget is simply one the page has never seen, so nothing is left out of the measurement.
     */
    private void carryTo(float rawX, float rawY) {
        CarryState state = carry;
        if (state == null) return;
        state.lastRawX = rawX;
        state.lastRawY = rawY;
        pane.widgetDragLayer().moveTo(carriedBounds(state, rawX, rawY));
        watchPageEdge(state.turn, rawX);
        updateCarryCandidate(state);
    }

    /**
     * The cell the carried widget would take on the page under it, and what that page would have
     * to shuffle to give it; red where the page has no room, as an edit-mode move shows it.
     */
    private void updateCarryCandidate(@NonNull CarryState state) {
        Rect picture = carriedBounds(state, state.lastRawX, state.lastRawY);
        if (!overGrid(picture.centerX(), picture.centerY())) {
            // Off the page: nothing is being offered a cell, so nothing is outlined or pushed.
            state.candidate = null;
            pane.widgetDragLayer().setGhost(null, true);
            previewDisplacement(Collections.emptyMap());
            return;
        }
        Rect dragged = new Rect(picture);
        dragged.offset(-pane.grid().getLeft(), -pane.grid().getTop());
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(pane.grid().metrics(),
            widgets.repository().recordsOnPage(state.turn.page), CARRIED_APP_WIDGET_ID,
            state.span,
            dragged);
        state.candidate = candidate;
        pane.widgetDragLayer().setGhost(paneBounds(candidate.rect), candidate.valid);
        previewDisplacement(candidate.valid ? candidate.displaced : Collections.emptyMap());
    }

    /**
     * The carried widget rested in the edge band: the next page comes in under it, exactly as it
     * does for a widget moved in edit mode. The picture is already in the drag layer, so nothing
     * has to be lifted first.
     */
    private void flipCarryPage(@NonNull CarryState state) {
        if (!mayTurn(state.turn) || !turnPage(state.turn)) return;
        updateCarryCandidate(state);
        // Still in the band: the next page follows after the same pause.
        pane.postDelayed(edgeFlip, EDGE_FLIP_DELAY_MS);
    }

    /** Whether a point in pane coordinates is over the page's grid. */
    private boolean overGrid(int paneX, int paneY) {
        WidgetGridView grid = pane.grid();
        return paneX >= grid.getLeft() && paneX < grid.getRight()
            && paneY >= grid.getTop() && paneY < grid.getBottom();
    }

    /**
     * The finger let go. Over a cell the page can give it, the widget binds there through the
     * same add every tap uses; anywhere else the carry simply ends and nothing is added — the
     * sheet has closed and reopening it under a finger that has already left would be a second
     * gesture, not the end of this one.
     */
    private void endCarry(float rawX, float rawY, boolean canceled) {
        CarryState state = carry;
        carry = null;
        if (state == null) return;
        pane.removeCallbacks(edgeFlip);
        state.turn.edgeDirection = 0;
        Rect picture = carriedBounds(state, rawX, rawY);
        WidgetEditPolicy.Candidate candidate = state.candidate;
        boolean over = !canceled && overGrid(picture.centerX(), picture.centerY());
        boolean refused = over && candidate != null && !candidate.valid;
        boolean placed = over && candidate != null && candidate.valid
            && placeCarried(state, candidate);
        pane.widgetDragLayer().setGhost(null, true);
        // A placement renders the page from the repository, which is where the neighbours now are.
        clearDisplacementPreview(!placed);
        pane.widgetDragLayer().drop(placed ? paneBounds(candidate.rect) : null,
            !host.reducedMotion());
        trimCarryPage(state, true);
        if (refused) pane.showNotice(pane.getContext().getString(R.string.widget_no_room_on_page));
    }

    /**
     * The carry is over: a page it made and left empty goes again, as it does after an edit-mode
     * move. A page the widget landed on, or is waiting on a bind for, is kept by the trim itself.
     */
    private void trimCarryPage(@NonNull CarryState state, boolean redraw) {
        if (state.turn.createdPage < 0) return;
        state.turn.createdPage = -1;
        int before = currentPage;
        trimPages();
        clampCurrentPage();
        if (!redraw) return;
        render();
        // The page on screen was the one taken away: the one before it comes back in.
        if (currentPage < before) pane.slideInFrom(-1);
    }

    /**
     * Commits the drop: the neighbours the target pushes aside move first, because the cell has to
     * be free before the add can reserve it, and they go back if the add is refused.
     */
    private boolean placeCarried(@NonNull CarryState state,
                                 @NonNull WidgetEditPolicy.Candidate candidate) {
        LauncherWidgetRepository repository = widgets.repository();
        // The page under the finger was taken away while the widget was in the air.
        if (state.turn.page < 0 || state.turn.page >= repository.pageCount()) return false;
        List<LauncherWidgetRecord> restore = Collections.emptyList();
        if (!candidate.displaced.isEmpty()) {
            List<LauncherWidgetRecord> batch = new ArrayList<>();
            restore = new ArrayList<>();
            for (Map.Entry<Integer, WidgetCellRect> entry : candidate.displaced.entrySet()) {
                LauncherWidgetRecord neighbour = repository.get(entry.getKey());
                if (neighbour == null) return false;
                restore.add(neighbour);
                batch.add(neighbour.withCell(entry.getValue()));
            }
            if (!repository.putRecords(batch)) return false;
        }
        LauncherWidgetHostController.AddResult result = beginAddAt(state.item, candidate.rect,
            state.turn.page, repository.revision());
        if (result == LauncherWidgetHostController.AddResult.STARTED) {
            awaitingExternal = true;
            render();
            return true;
        }
        if (result == LauncherWidgetHostController.AddResult.READY) {
            liveOrigin = null;
            render();
            return true;
        }
        if (!restore.isEmpty()) repository.putRecords(restore);
        liveOrigin = null;
        pane.showNotice(result == LauncherWidgetHostController.AddResult.NO_SPACE
            ? pane.getContext().getString(R.string.widget_no_room_on_page)
            : messageFor(result));
        render();
        return false;
    }

    /**
     * Lets go of a carry that the pane is no longer in a position to finish. Nothing is placed, so
     * a page the carry made goes again; {@code redraw} is false once the pane is being torn down.
     */
    private void abortCarry(boolean redraw) {
        CarryState state = carry;
        if (state == null) return;
        carry = null;
        pane.removeCallbacks(edgeFlip);
        pane.endCarry();
        pane.widgetDragLayer().setGhost(null, true);
        clearDisplacementPreview(false);
        pane.releaseWidgetDragLayer();
        trimCarryPage(state, redraw);
    }

    /**
     * The edge under the finger, and what the page has to shuffle to give the widget that span.
     * A grow that lands on a neighbour pushes it aside just as a move does, and the neighbours
     * slide to where they would go while the finger is still down.
     */
    private void resizeDrag(@NonNull WidgetEditPolicy.Handle handle, int desiredEdgePx) {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null) return;
        // Same reason as the move path: a provider push mid-resize would re-inflate under the
        // finger. Asked on every move event, so the safety release trails the gesture.
        SafeLauncherAppWidgetHostView hostView = safeHostViewFor(edit.appWidgetId);
        if (hostView != null) hostView.beginDeferringUpdates();
        boolean horizontal = handle == WidgetEditPolicy.Handle.LEFT
            || handle == WidgetEditPolicy.Handle.RIGHT;
        int gridEdgePx = horizontal ? desiredEdgePx - pane.grid().getLeft()
            : desiredEdgePx - pane.grid().getTop();
        edit.resizeCandidate = WidgetEditPolicy.resize(pane.grid().metrics(),
            widgets.repository().recordsOnPage(record.page), edit.appWidgetId, record.cell,
            handle, gridEdgePx, edit.minColumnSpan, edit.minRowSpan,
            edit.maxColumnSpan, edit.maxRowSpan);
        pane.widgetEditOverlay().setFrameBounds(paneBounds(edit.resizeCandidate.rect));
        previewDisplacement(edit.resizeCandidate.displaced);
        showRefusal(edit.resizeCandidate.refusedBy, record.page);
    }

    /**
     * Tints the widgets that would not make room for what the finger asks, in the overlay's
     * refusing red, so a grow that stops short or a drop that springs back shows who stopped it.
     */
    private void showRefusal(@NonNull Set<Integer> refusedBy, int page) {
        if (refusedBy.isEmpty()) {
            pane.widgetEditOverlay().showRefusal(Collections.emptyList());
            return;
        }
        List<Rect> bounds = new ArrayList<>(refusedBy.size());
        for (LauncherWidgetRecord other : widgets.repository().recordsOnPage(page)) {
            if (refusedBy.contains(other.appWidgetId)) bounds.add(paneBounds(other.cell));
        }
        pane.widgetEditOverlay().showRefusal(bounds);
    }

    private void endResizeDrag() {
        SafeLauncherAppWidgetHostView resized = safeHostViewFor(edit.appWidgetId);
        if (resized != null) resized.endDeferringUpdates();
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        WidgetEditPolicy.Candidate candidate = edit.resizeCandidate;
        edit.resizeCandidate = null;
        pane.widgetEditOverlay().flashRefusal();
        // The new span and every neighbour it pushed aside go in together: the same atomic batch
        // the move path commits, so a refused write leaves the page exactly as it was.
        if (record != null && candidate != null && !candidate.rect.equals(record.cell)
            && commitMove(record, candidate, record.page)) {
            // A render lays the neighbours out where the commit put them and drops the preview.
            render();
            return;
        }
        clearDisplacementPreview(true);
        if (record != null) pane.widgetEditOverlay().setFrameBounds(paneBounds(record.cell));
    }

    @NonNull private Rect paneBounds(@NonNull WidgetCellRect rect) {
        Rect bounds = pane.grid().metrics().boundsFor(rect);
        bounds.offset(pane.grid().getLeft(), pane.grid().getTop());
        return bounds;
    }

    private int dp(int value) {
        return Math.round(value * pane.getResources().getDisplayMetrics().density);
    }

    private void render() {
        // Any surviving drag preview belongs to the layout this render is about to replace.
        clearDisplacementPreview(false);
        currentPage = Math.max(0, Math.min(widgets.repository().pageCount() - 1, currentPage));
        pane.setReducedMotion(host.reducedMotion());
        pane.render(widgets.repository(), widgets.capability(), currentPage);
        // Built-in views outlive their page so a return draws them as they were; only a widget
        // gone from the whole wall lets its view go.
        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (LauncherWidgetRecord record : widgets.repository().records()) live.add(record.appWidgetId);
        builtins.retainOnly(live);
        appIcons.retainOnly(live);
        // A render hides the edit chrome. While a session is open and its widget is still on the
        // page - after a commit, a grid resize from the page's own tab, another widget arriving -
        // the chrome comes straight back at the widget's new bounds, and the host never hears the
        // session end. Only a widget that is gone ends it here.
        restoreEditChrome();
        syncEditSession();
        host.onWidgetPageRendered();
    }

    /** The open session again, sized for the grid the render just laid out. */
    private void restoreEditChrome() {
        if (edit == null) return;
        if (edit.lifted) { restoreCrossingChrome(); return; }
        if (!beginEditSession(edit.appWidgetId)) edit = null;
    }

    /**
     * The chrome for a page the dragged widget has not landed on. Its own frame is nowhere — the
     * widget is in the air — so the page underneath is outlined and the ghost carries the session,
     * and the ordinary "the widget is gone, end the session" rule must not fire on the way past.
     */
    private void restoreCrossingChrome() {
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        overlay.setListener(overlayListener);
        overlay.show(new Rect(), false, false, editableOutlines(edit.appWidgetId), false);
        overlay.resumeMoveDrag();
    }

    @NonNull private String messageFor(LauncherWidgetHostController.AddResult result) {
        switch (result) {
            case UNSUPPORTED: return pane.getContext().getString(R.string.widget_unsupported);
            case BUSY: return pane.getContext().getString(R.string.widget_add_busy);
            case CONFIGURATION_UNAVAILABLE:
                return pane.getContext().getString(R.string.widget_configuration_unavailable);
            case STORAGE_FAILURE: return pane.getContext().getString(R.string.widget_storage_failure);
            case DECLINED: return pane.getContext().getString(R.string.widget_add_failed);
            case NO_SPACE: return pane.getContext().getString(R.string.widget_grid_full);
            case REMOVE_FAILED: return pane.getContext().getString(R.string.widget_remove_failed);
            case WALL_RESET: return pane.getContext().getString(R.string.widget_wall_reset);
            default: return pane.getContext().getString(R.string.widget_add_failed);
        }
    }

    /** What the built-ins get when the host offers nothing: no permission, no window, no face. */
    private static final class InertBuiltinHost implements BuiltinWidgetServices.Host {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   @Nullable String title) { return false; }
        @Override @Nullable public android.graphics.Typeface monoTypeface() { return null; }
    }
}
