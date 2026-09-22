package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    }

    private final WidgetPaneView pane;
    private final LauncherWidgetHostController widgets;
    private final WidgetProviderCatalogLoader catalog;
    private final Host host;
    private String liveOrigin;
    private boolean awaitingExternal;
    private int currentPage;
    private boolean editorFocusActive;
    @Nullable private PopupWindow paneMenu;

    public WidgetPaneController(@NonNull WidgetPaneView pane,
                                @NonNull LauncherWidgetHostController widgets,
                                @NonNull Host host) {
        this(pane, widgets, host, new WidgetProviderCatalogLoader(pane.getContext()));
    }

    WidgetPaneController(@NonNull WidgetPaneView pane,
                         @NonNull LauncherWidgetHostController widgets,
                         @NonNull Host host, @NonNull WidgetProviderCatalogLoader catalog) {
        this.pane = pane; this.widgets = widgets; this.host = host; this.catalog = catalog;
        pane.grid().bind(widgets); pane.picker().setReducedMotion(host.reducedMotion());
        pane.setReducedMotion(host.reducedMotion());
        pane.picker().adapter().setPreviewLoader(catalog);
        // The picker's search field is a text input inside the pane like any other: it reaches the
        // system keyboard through the same seam a provider's own editor does, and never by asking
        // for it here.
        pane.picker().setSearchFocusListener(this::relayEditorFocus);
        pane.grid().setListener(new WidgetGridView.Listener() {
            @Override public void onWidgetLongPressed(int appWidgetId, float rawX, float rawY) {
                enterEditMode(appWidgetId, rawX, rawY);
            }
            @Override public void onWidgetEditDragMove(int appWidgetId, float rawX, float rawY) {
                if (edit != null && edit.appWidgetId == appWidgetId) moveDrag(rawX, rawY);
            }
            @Override public void onWidgetEditDragEnd(int appWidgetId, boolean canceled) {
                if (edit != null && edit.appWidgetId == appWidgetId) endMoveDrag(canceled);
            }
            @Override public void onEmptySpaceLongPressed(float rawX, float rawY) {
                showPaneMenu(rawX, rawY);
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
        widgets.repository().trimEmptyPages();
        clampCurrentPage();
        render();
    }

    public void onStart() { render(); }
    /** Draw the pane again from the repository — the layout under it changed without a grid change. */
    public void redraw() { render(); }
    public void onStop() {
        abortCarry();
        catalog.cancel(); pane.picker().closeImmediate(); dismissPaneMenu();
    }
    public void onPackageOrProfileChanged() {
        catalog.invalidate();
        render(); if (pane.picker().isOpen()) loadCatalog();
    }
    /**
     * The wall's Widgets page came to rest on screen, or left it. A page that has gone opens
     * again the way it always does — page 0, no menu — like the pull-down it replaces.
     */
    public void onWallPageShown(boolean shown) {
        if (shown) return;
        dismissPaneMenu();
        if (currentPage != 0) { currentPage = 0; render(); }
    }
    public boolean onBackPressed() {
        if (paneMenu != null && paneMenu.isShowing()) { dismissPaneMenu(); return true; }
        if (pane.widgetEditActive()) { exitEditMode(); return true; }
        return pane.onBackPressed();
    }
    public void destroy() {
        abortCarry();
        pane.removeCallbacks(edgeFlip);
        widgets.setListener(null); catalog.cancel(); dismissPaneMenu();
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

    /** Empty-surface long-press menu; the policy decides which rows this state offers. */
    private void showPaneMenu(float rawX, float rawY) {
        dismissPaneMenu();
        List<WidgetPaneMenuPolicy.Item> items = WidgetPaneMenuPolicy.itemsFor(
            widgets.capability() == LauncherWidgetHostController.Capability.AVAILABLE,
            widgets.repository().pageCount(),
            widgets.repository().recordsOnPage(currentPage).isEmpty());
        if (items.isEmpty()) return;
        paneMenu = WidgetPaneMenu.show(pane, items, rawX, rawY, this::onMenuItemSelected);
    }

    private void dismissPaneMenu() {
        if (paneMenu != null) {
            if (paneMenu.isShowing()) paneMenu.dismiss();
            paneMenu = null;
        }
    }

    void onMenuItemSelected(@NonNull WidgetPaneMenuPolicy.Item item) {
        dismissPaneMenu();
        switch (item) {
            case ADD_WIDGET: openPicker(); break;
            case EDIT_WIDGETS: menuEditWidgets(); break;
            case REMOVE_PAGE: menuRemovePage(); break;
        }
    }

    /**
     * The Widgets page's edit pencil: the same entry the long-press menu's Edit widgets takes,
     * with the same gestures and the same ways out.
     */
    public void editWidgets() {
        menuEditWidgets();
    }

    /** Enters the edit chrome on the current page's first widget, without a live drag. */
    void menuEditWidgets() {
        List<LauncherWidgetRecord> pageRecords = widgets.repository().recordsOnPage(currentPage);
        LauncherWidgetRecord first = null;
        for (LauncherWidgetRecord record : pageRecords) {
            if (first == null || record.cell.top < first.cell.top
                || (record.cell.top == first.cell.top && record.cell.left < first.cell.left)) {
                first = record;
            }
        }
        if (first == null) return;
        beginEditSession(first.appWidgetId);
    }

    /**
     * The + on the page's border tab. The new page is the user's own: it stays, empty, until they
     * put something on it, and from then on it comes and goes with its widgets.
     */
    void menuAddPage() {
        int appended = widgets.repository().addFreshPage();
        if (appended < 0) {
            pane.showNotice(messageFor(LauncherWidgetHostController.AddResult.STORAGE_FAILURE));
            return;
        }
        currentPage = appended;
        render();
    }

    void menuRemovePage() {
        if (!widgets.repository().removePage(currentPage)) return;
        currentPage = Math.max(0, Math.min(widgets.repository().pageCount() - 1, currentPage));
        render();
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
        if (widgets.capability() != LauncherWidgetHostController.Capability.AVAILABLE) return;
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
                    pane.picker().adapter().setFitPredicate(WidgetPaneController.this::canFit);
                    pane.picker().showCatalog(groups);
                }
            });
    }

    private boolean canFit(@NonNull WidgetProviderItem item) {
        if (!item.fits || item.columnSpan <= 0 || item.rowSpan <= 0) return false;
        return WidgetGridPlacementPolicy.findPlacement(widgets.repository().gridDefinition(),
            widgets.repository().recordsOnPage(currentPage), item.columnSpan, item.rowSpan).outcome
            == WidgetGridPlacementPolicy.Outcome.PLACED;
    }

    private void selectProvider(@NonNull WidgetProviderItem item) {
        LauncherWidgetRepository repository = widgets.repository();
        long revision = repository.revision();
        // New widgets always land on the page the user is looking at.
        WidgetGridPlacementPolicy.Result placement = WidgetGridPlacementPolicy.findPlacement(
            repository.gridDefinition(), repository.recordsOnPage(currentPage),
            item.columnSpan, item.rowSpan);
        if (placement.outcome != WidgetGridPlacementPolicy.Outcome.PLACED) {
            pane.picker().adapter().setFitPredicate(this::canFit);
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
            pane.picker().adapter().setFitPredicate(this::canFit);
            pane.picker().showNoSpace(item.columnSpan, item.rowSpan, repository.gridDefinition());
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
        // A widget arrived or left: a page with nothing on it goes with it.
        widgets.repository().trimEmptyPages();
        clampCurrentPage();
        render();
        if (result == LauncherWidgetHostController.AddResult.REMOVE_FAILED) {
            pane.showNotice(pane.getContext().getString(R.string.widget_remove_failed));
        }
        if (awaitingExternal && result != LauncherWidgetHostController.AddResult.IGNORED
            && result != LauncherWidgetHostController.AddResult.STARTED) {
            awaitingExternal = false;
            if (!host.isWidgetSurfaceShowing()) host.restoreWidgetSurfaceOrigin();
            if (result != LauncherWidgetHostController.AddResult.READY) pane.showNotice(messageFor(result));
        }
        if (result != LauncherWidgetHostController.AddResult.IGNORED) liveOrigin = null;
    }

    /** Live widget-edit session; non-null only while the edit chrome owns the pane. */
    private static final class EditState {
        final int appWidgetId;
        final int minColumnSpan, minRowSpan;
        final boolean horizontalResizable, verticalResizable;
        float dragStartRawX, dragStartRawY;
        Rect dragStartBounds;
        WidgetEditPolicy.Candidate moveCandidate;
        WidgetEditPolicy.Candidate resizeCandidate;
        /** The page the drag is over now: the widget's own until the finger turns it. */
        int dragPage;
        /** Which edge band the finger is resting in: -1 the leading one, +1 the trailing one. */
        int edgeDirection;
        /** True once the widget has been taken out of the grid to cross pages. */
        boolean lifted;
        /** The page this drag made past the end of the run, or -1: one drag makes at most one. */
        int createdPage = -1;
        float lastRawX, lastRawY;
        EditState(int appWidgetId, int minColumnSpan, int minRowSpan,
                  boolean horizontalResizable, boolean verticalResizable) {
            this.appWidgetId = appWidgetId;
            this.minColumnSpan = minColumnSpan;
            this.minRowSpan = minRowSpan;
            this.horizontalResizable = horizontalResizable;
            this.verticalResizable = verticalResizable;
        }
    }

    private EditState edit;

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
                widgets.removeWidget(appWidgetId);
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
        AppWidgetProviderInfo info = widgets.providerInfo(appWidgetId);
        WidgetGridMetrics metrics = pane.grid().metrics();
        int minColumns = 1, minRows = 1;
        boolean horizontal = false, vertical = false;
        if (info != null && record.state == LauncherWidgetRecord.State.ACTIVE) {
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
        }
        edit = new EditState(appWidgetId, minColumns, minRows, horizontal, vertical);
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        overlay.setListener(overlayListener);
        overlay.show(paneBounds(record.cell), horizontal, vertical, editableOutlines(appWidgetId),
            widgets.canReconfigure(appWidgetId));
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
        repository.trimEmptyPages();
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
        edit.dragPage = record.page;
        edit.edgeDirection = 0;
        edit.createdPage = -1;
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
        watchPageEdge(rawX);
        updateDragCandidate(record);
    }

    /**
     * Where the widget would land on the page it is over, and what that page would have to shuffle
     * to take it. The page is {@link EditState#dragPage}, which is the widget's own until a turn
     * at the edge moves it on, so the same ghost and the same neighbour preview serve both.
     */
    private void updateDragCandidate(@NonNull LauncherWidgetRecord record) {
        if (edit.dragStartBounds == null) return;
        Rect dragged = new Rect(edit.dragStartBounds);
        dragged.offset(Math.round(edit.lastRawX - edit.dragStartRawX),
            Math.round(edit.lastRawY - edit.dragStartRawY));
        WidgetGridMetrics metrics = pane.grid().metrics();
        edit.moveCandidate = WidgetEditPolicy.snapMove(metrics,
            widgets.repository().recordsOnPage(edit.dragPage), edit.appWidgetId, record.cell,
            dragged);
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        if (edit.moveCandidate.valid) {
            overlay.setGhostBounds(paneBounds(edit.moveCandidate.rect), true);
        } else if (edit.dragPage != record.page) {
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
    private void watchPageEdge(float rawX) {
        pane.getLocationOnScreen(paneLocation);
        float x = rawX - paneLocation[0];
        float band = dp(EDGE_BAND_DP);
        int direction = 0;
        if (pane.getWidth() > 2 * band) {
            if (x <= band) direction = -1;
            else if (x >= pane.getWidth() - band) direction = 1;
        }
        if (direction != 0 && !canTurnTo(edit.dragPage + direction, direction)) direction = 0;
        if (direction == edit.edgeDirection) return;
        edit.edgeDirection = direction;
        pane.removeCallbacks(edgeFlip);
        if (direction != 0) pane.postDelayed(edgeFlip, EDGE_FLIP_DELAY_MS);
    }

    /**
     * Whether the drag can turn onto that page: one that is there, or the one page past the end
     * this drag is allowed to make. Never past the first page, and never a second new one.
     */
    private boolean canTurnTo(int target, int direction) {
        if (target < 0) return false;
        int pages = widgets.repository().pageCount();
        if (target < pages) return true;
        return direction > 0 && target == pages && edit.createdPage < 0;
    }

    /** The pause elapsed with the widget still in the band: the neighbouring page comes in. */
    private void flipDragPage() {
        if (edit == null || edit.edgeDirection == 0 || edit.dragStartBounds == null) return;
        int direction = edit.edgeDirection;
        int target = edit.dragPage + direction;
        if (!canTurnTo(target, direction)) {
            edit.edgeDirection = 0;
            return;
        }
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null) return;
        if (!liftDraggedWidget(record)) { edit.edgeDirection = 0; return; }
        // Past the last page: the page the widget is being carried onto is made here. It is an
        // ordinary empty page, so the trim at the end of the drag takes it away again unless the
        // widget is dropped on it.
        if (target >= widgets.repository().pageCount()) {
            int appended = widgets.repository().addPage();
            if (appended < 0) { edit.edgeDirection = 0; return; }
            edit.createdPage = appended;
            target = appended;
        }
        clearDisplacementPreview(false);
        edit.dragPage = target;
        currentPage = target;
        render();
        pane.slideInFrom(direction);
        updateDragCandidate(record);
        // Still in the band: the next page follows after the same pause.
        pane.postDelayed(edgeFlip, EDGE_FLIP_DELAY_MS);
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
        pane.grid().setDragPinned(-1);
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
        edit.edgeDirection = 0;
        int appWidgetId = edit.appWidgetId;
        boolean lifted = edit.lifted;
        SafeLauncherAppWidgetHostView hostView = safeHostViewFor(appWidgetId);
        if (hostView != null) hostView.endDeferringUpdates();
        LauncherWidgetRecord record = widgets.repository().get(appWidgetId);
        WidgetEditPolicy.Candidate candidate = edit.moveCandidate;
        int target = edit.dragPage;
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
        boolean created = edit.createdPage >= 0;
        edit.createdPage = -1;
        // The drag is over: a page it made and did not land on goes again, and so does a page the
        // widget it carried away was the last thing on.
        if (committed || created) widgets.repository().trimEmptyPages();
        if (committed || crossed) {
            // The trim can have renumbered the pages under all this; the widget's own page is
            // where the pane belongs, whether it landed there or was sent home.
            LauncherWidgetRecord settled = widgets.repository().get(appWidgetId);
            if (settled != null) currentPage = settled.page;
        }
        clampCurrentPage();
        edit.dragPage = currentPage;
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
        /** The page it was picked for: the one that was on screen when the card was held. */
        final int page;
        @NonNull final WidgetCellRect span;
        /** The picture's size in grid pixels — the cell the widget will occupy, not the card. */
        final int width, height;
        @Nullable WidgetEditPolicy.Candidate candidate;
        CarryState(@NonNull WidgetProviderItem item, int page, @NonNull WidgetCellRect span,
                   int width, int height) {
            this.item = item; this.page = page; this.span = span;
            this.width = width; this.height = height;
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
     * <p>A card the grid has no room for is greyed out and does not answer a tap; it does not
     * answer a hold either, so the one rule about space is stated in one place.
     */
    void beginCarry(@NonNull WidgetProviderItem item, @NonNull View card, float rawX, float rawY) {
        if (carry != null || edit != null || awaitingExternal) return;
        if (!canFit(item)) return;
        WidgetCellRect span = new WidgetCellRect(0, 0, item.columnSpan, item.rowSpan);
        Rect cell = pane.grid().metrics().boundsFor(span);
        if (cell.width() <= 0 || cell.height() <= 0) return;
        // The slot is the card's picture of the widget; the card around it is a list row.
        View picture = card.findViewWithTag("slot");
        if (picture == null) picture = card;
        CarryState state = new CarryState(item, currentPage, span, cell.width(), cell.height());
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
        Rect picture = carriedBounds(state, rawX, rawY);
        pane.widgetDragLayer().moveTo(picture);
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
            widgets.repository().recordsOnPage(state.page), CARRIED_APP_WIDGET_ID, state.span,
            dragged);
        state.candidate = candidate;
        pane.widgetDragLayer().setGhost(paneBounds(candidate.rect), candidate.valid);
        previewDisplacement(candidate.valid ? candidate.displaced : Collections.emptyMap());
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
        if (refused) pane.showNotice(pane.getContext().getString(R.string.widget_no_room_on_page));
    }

    /**
     * Commits the drop: the neighbours the target pushes aside move first, because the cell has to
     * be free before the add can reserve it, and they go back if the add is refused.
     */
    private boolean placeCarried(@NonNull CarryState state,
                                 @NonNull WidgetEditPolicy.Candidate candidate) {
        LauncherWidgetRepository repository = widgets.repository();
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
            state.page, repository.revision());
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

    /** Lets go of a carry that the pane is no longer in a position to finish. */
    private void abortCarry() {
        if (carry == null) return;
        carry = null;
        pane.endCarry();
        pane.widgetDragLayer().setGhost(null, true);
        clearDisplacementPreview(false);
        pane.releaseWidgetDragLayer();
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
            handle, gridEdgePx, edit.minColumnSpan, edit.minRowSpan);
        pane.widgetEditOverlay().setFrameBounds(paneBounds(edit.resizeCandidate.rect));
        previewDisplacement(edit.resizeCandidate.displaced);
    }

    private void endResizeDrag() {
        SafeLauncherAppWidgetHostView resized = safeHostViewFor(edit.appWidgetId);
        if (resized != null) resized.endDeferringUpdates();
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        WidgetEditPolicy.Candidate candidate = edit.resizeCandidate;
        edit.resizeCandidate = null;
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
            default: return pane.getContext().getString(R.string.widget_add_failed);
        }
    }
}
