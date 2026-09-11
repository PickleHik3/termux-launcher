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
        /** Neighbours currently shown pushed aside, appWidgetId to the cell they preview. */
        @NonNull Map<Integer, WidgetCellRect> previewDisplaced = Collections.emptyMap();
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
        clearDisplacementPreview(false);
        edit = null;
        pane.hideWidgetEditOverlay();
        syncEditSession();
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
        clearDisplacementPreview(false);
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
        previewDisplacement(edit.moveCandidate.valid
            ? edit.moveCandidate.displaced : Collections.emptyMap());
    }

    /**
     * Slides the neighbours a candidate pushes aside to where they would land. Only the ones
     * whose target actually changed since the last move event are touched, so a drag that keeps
     * the same plan costs nothing per frame.
     */
    private void previewDisplacement(@NonNull Map<Integer, WidgetCellRect> next) {
        Map<Integer, WidgetCellRect> previous = edit.previewDisplaced;
        if (previous.equals(next)) return;
        WidgetGridMetrics metrics = pane.grid().metrics();
        for (Map.Entry<Integer, WidgetCellRect> entry : previous.entrySet()) {
            if (!next.containsKey(entry.getKey())) slideCell(entry.getKey(), null, metrics, true);
        }
        for (Map.Entry<Integer, WidgetCellRect> entry : next.entrySet()) {
            if (entry.getValue().equals(previous.get(entry.getKey()))) continue;
            slideCell(entry.getKey(), entry.getValue(), metrics, true);
        }
        edit.previewDisplaced = next;
    }

    /** Returns every previewed neighbour to its real position; the plan is dropped either way. */
    private void clearDisplacementPreview(boolean animate) {
        if (edit == null || edit.previewDisplaced.isEmpty()) return;
        WidgetGridMetrics metrics = pane.grid().metrics();
        for (Integer appWidgetId : edit.previewDisplaced.keySet()) {
            slideCell(appWidgetId, null, metrics, animate);
        }
        edit.previewDisplaced = Collections.emptyMap();
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
        LauncherWidgetRecord record = widgets.repository().get(edit.appWidgetId);
        WidgetCellView cell = pane.grid().cellForId(edit.appWidgetId);
        WidgetEditPolicy.Candidate candidate = edit.moveCandidate;
        edit.moveCandidate = null;
        edit.dragStartBounds = null;
        boolean committed = false;
        if (!canceled && record != null && candidate != null && candidate.valid
            && !candidate.rect.equals(record.cell)) {
            committed = commitMove(record, candidate);
        }
        // The real layout takes over on render(); a surviving translation would double the offset.
        clearDisplacementPreview(!committed);
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
        } else {
            WidgetEditOverlayView overlay = pane.widgetEditOverlay();
            overlay.setDragging(false);
            overlay.setGhostBounds(null);
        }
    }

    /** One atomic commit for the dragged widget and everything it pushed aside. */
    private boolean commitMove(@NonNull LauncherWidgetRecord record,
                               @NonNull WidgetEditPolicy.Candidate candidate) {
        if (candidate.displaced.isEmpty()) {
            return widgets.repository().putRecord(record.withCell(candidate.rect));
        }
        List<LauncherWidgetRecord> batch = new ArrayList<>();
        batch.add(record.withCell(candidate.rect));
        for (Map.Entry<Integer, WidgetCellRect> entry : candidate.displaced.entrySet()) {
            LauncherWidgetRecord neighbour = widgets.repository().get(entry.getKey());
            if (neighbour == null) return false;
            batch.add(neighbour.withCell(entry.getValue()));
        }
        return widgets.repository().putRecords(batch);
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
        } else if (record != null) {
            pane.widgetEditOverlay().setFrameBounds(paneBounds(record.cell));
        }
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
        if (!beginEditSession(edit.appWidgetId)) edit = null;
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
