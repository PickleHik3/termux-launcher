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

import java.util.List;
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
        }, this::selectProvider);
        widgets.setListener(this);
        render();
    }

    public void onStart() { render(); }
    public void onStop() {
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
    public void destroy() { widgets.setListener(null); catalog.cancel(); dismissPaneMenu(); }

    int currentPage() { return currentPage; }

    void setCurrentPage(int page) {
        int clamped = Math.max(0, Math.min(widgets.repository().pageCount() - 1, page));
        if (clamped == currentPage) return;
        currentPage = clamped;
        render();
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
            case ADD_PAGE: menuAddPage(); break;
            case REMOVE_PAGE: menuRemovePage(); break;
        }
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

    void menuAddPage() {
        int appended = widgets.repository().addPage();
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
        catalog.load(metrics, widgets.repository().revision(), (generation, groups) -> {
            if (!pane.picker().isOpen()) return;
            pane.picker().adapter().setFitPredicate(this::canFit);
            pane.picker().showCatalog(groups);
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
        Rect bounds = pane.grid().metrics().boundsFor(placement.rect);
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
        LauncherWidgetHostController.AddResult result = widgets.beginAdd(item.info, placement.rect,
            currentPage, revision, options, liveOrigin);
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
        render();
        // render() hides the edit chrome; drop the session too so no stale state lingers.
        if (edit != null && !pane.widgetEditActive()) edit = null;
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
        WidgetCellRect resizeCandidate;
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
            @Override public void onDismiss() { exitEditMode(); }
        };

    private void enterEditMode(int appWidgetId, float rawX, float rawY) {
        if (!beginEditSession(appWidgetId)) return;
        // The long-press finger is still down: this same gesture continues as a move drag.
        beginMoveDrag(rawX, rawY);
        pane.widgetEditOverlay().setDragging(true);
    }

    /** Shared edit-chrome entry; menu entry stops here, a widget long-press continues as a drag. */
    private boolean beginEditSession(int appWidgetId) {
        LauncherWidgetRecord record = widgets.repository().get(appWidgetId);
        WidgetCellView cell = pane.grid().cellForId(appWidgetId);
        if (record == null || cell == null) return false;
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
        overlay.show(paneBounds(record.cell), horizontal, vertical);
        return true;
    }

    private void exitEditMode() {
        edit = null;
        pane.hideWidgetEditOverlay();
    }

    private void beginMoveDrag(float rawX, float rawY) {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null) { exitEditMode(); return; }
        edit.dragStartRawX = rawX;
        edit.dragStartRawY = rawY;
        edit.dragStartBounds = pane.grid().metrics().boundsFor(record.cell);
        edit.moveCandidate = null;
    }

    private void moveDrag(float rawX, float rawY) {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        WidgetCellView cell = pane.grid().cellForId(edit.appWidgetId);
        if (record == null || cell == null || edit.dragStartBounds == null) return;
        float translationX = rawX - edit.dragStartRawX;
        float translationY = rawY - edit.dragStartRawY;
        cell.setTranslationX(translationX);
        cell.setTranslationY(translationY);
        cell.setTranslationZ(dp(8));
        Rect dragged = new Rect(edit.dragStartBounds);
        dragged.offset(Math.round(translationX), Math.round(translationY));
        edit.moveCandidate = WidgetEditPolicy.snapMove(pane.grid().metrics(),
            widgets.repository().recordsOnPage(record.page), edit.appWidgetId, record.cell,
            dragged);
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        overlay.setGhostBounds(edit.moveCandidate.valid
            ? paneBounds(edit.moveCandidate.rect) : null);
    }

    private void endMoveDrag(boolean canceled) {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        WidgetCellView cell = pane.grid().cellForId(edit.appWidgetId);
        WidgetEditPolicy.Candidate candidate = edit.moveCandidate;
        edit.moveCandidate = null;
        edit.dragStartBounds = null;
        boolean committed = false;
        if (!canceled && record != null && candidate != null && candidate.valid
            && !candidate.rect.equals(record.cell)
            && widgets.repository().putRecord(record.withCell(candidate.rect))) {
            committed = true;
        }
        if (cell != null) {
            if (committed || host.reducedMotion()) {
                cell.setTranslationX(0f); cell.setTranslationY(0f); cell.setTranslationZ(0f);
            } else {
                cell.animate().translationX(0f).translationY(0f).translationZ(0f)
                    .setDuration(160).start();
            }
        }
        if (committed) {
            render();
            reenterEditChrome();
        } else {
            WidgetEditOverlayView overlay = pane.widgetEditOverlay();
            overlay.setDragging(false);
            overlay.setGhostBounds(null);
        }
    }

    private void resizeDrag(@NonNull WidgetEditPolicy.Handle handle, int desiredEdgePx) {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null) return;
        boolean horizontal = handle == WidgetEditPolicy.Handle.LEFT
            || handle == WidgetEditPolicy.Handle.RIGHT;
        int gridEdgePx = horizontal ? desiredEdgePx - pane.grid().getLeft()
            : desiredEdgePx - pane.grid().getTop();
        edit.resizeCandidate = WidgetEditPolicy.resize(pane.grid().metrics(),
            widgets.repository().recordsOnPage(record.page), edit.appWidgetId, record.cell,
            handle, gridEdgePx, edit.minColumnSpan, edit.minRowSpan).rect;
        pane.widgetEditOverlay().setFrameBounds(paneBounds(edit.resizeCandidate));
    }

    private void endResizeDrag() {
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        WidgetCellRect rect = edit.resizeCandidate;
        edit.resizeCandidate = null;
        if (record != null && rect != null && !rect.equals(record.cell)
            && widgets.repository().putRecord(record.withCell(rect))) {
            render();
            reenterEditChrome();
        } else if (record != null) {
            pane.widgetEditOverlay().setFrameBounds(paneBounds(record.cell));
        }
    }

    /** render() hides the chrome; after a commit the frame returns at the new bounds. */
    private void reenterEditChrome() {
        if (edit == null) return;
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        if (record == null) { exitEditMode(); return; }
        WidgetEditOverlayView overlay = pane.widgetEditOverlay();
        overlay.setListener(overlayListener);
        overlay.show(paneBounds(record.cell), edit.horizontalResizable, edit.verticalResizable);
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
        currentPage = Math.max(0, Math.min(widgets.repository().pageCount() - 1, currentPage));
        pane.setReducedMotion(host.reducedMotion());
        pane.render(widgets.repository(), widgets.capability(), currentPage);
    }

    @NonNull private String messageFor(LauncherWidgetHostController.AddResult result) {
        switch (result) {
            case UNSUPPORTED: return "Widgets aren't supported on this device";
            case BUSY: return "Finish adding the current widget first";
            case CONFIGURATION_UNAVAILABLE: return "Widget configuration isn't available";
            case STORAGE_FAILURE: return "Widget couldn't be saved";
            case DECLINED: return "Widget wasn't added";
            case NO_SPACE: return "Grid is full";
            case REMOVE_FAILED: return pane.getContext().getString(R.string.widget_remove_failed);
            default: return "Widget wasn’t added";
        }
    }
}
