package com.termux.app.launcher.drawer;

import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.List;

/** Full-viewport page items whose cells are bound row-major from one result list. */
public final class AppDrawerHorizontalPageAdapter
    extends RecyclerView.Adapter<AppDrawerHorizontalPageAdapter.PageHolder> {

    @Nullable private SuggestionBarView mDock;
    @NonNull private List<LauncherAppEntry> mEntries = new ArrayList<>();
    @NonNull private List<AppDrawerItem> mItems = new ArrayList<>();
    @Nullable private AppDrawerHorizontalGridMetrics mMetrics;
    @NonNull private AppDrawerAppCellView.ClickGate mClickGate =
        AppDrawerAppCellView.ALLOW_CLICKS;
    @Nullable private AppDrawerDragController mDragController;
    private boolean mPickupEnabled;

    public AppDrawerHorizontalPageAdapter(@Nullable SuggestionBarView dock) {
        mDock = dock;
        setHasStableIds(false);
    }

    public void setDock(@Nullable SuggestionBarView dock) {
        mDock = dock;
        notifyDataSetChanged();
    }

    public void setClickGate(@NonNull AppDrawerAppCellView.ClickGate clickGate) {
        mClickGate = clickGate;
    }

    public void setDragController(@Nullable AppDrawerDragController controller) {
        mDragController = controller;
        notifyDataSetChanged();
    }

    /** Set immediately before a list submission, so the submission remains the sole rebind. */
    public void setPickupEnabled(boolean enabled) { mPickupEnabled = enabled; }

    public void setMetrics(@NonNull AppDrawerHorizontalGridMetrics metrics) {
        mMetrics = metrics;
        notifyDataSetChanged();
    }

    @Nullable
    public AppDrawerHorizontalGridMetrics getMetrics() {
        return mMetrics;
    }

    public void submit(@NonNull List<LauncherAppEntry> entries) {
        mEntries = new ArrayList<>(entries);
        mItems = AppDrawerItemComposer.appsOnly(entries);
        notifyDataSetChanged();
    }

    public void submitItems(@NonNull List<AppDrawerItem> items) {
        mItems = new ArrayList<>(items);
        mEntries = new ArrayList<>();
        for (AppDrawerItem item : items) if (item.app != null) mEntries.add(item.app);
        notifyDataSetChanged();
    }

    @NonNull
    public List<LauncherAppEntry> entries() {
        return mEntries;
    }

    @Nullable
    public LauncherAppEntry entryAt(int position) {
        return position >= 0 && position < mEntries.size() ? mEntries.get(position) : null;
    }

    /** The composed rows as bound, for drop-position resolution during a drag. */
    @NonNull public List<AppDrawerItem> items() {
        return mItems;
    }

    @Nullable public AppDrawerItem itemAt(int position) {
        return position >= 0 && position < mItems.size() ? mItems.get(position) : null;
    }

    @Nullable
    public AppDrawerItem itemOnPageByStableId(int page, @NonNull String stableId) {
        int capacity = itemsPerPage();
        int start = AppDrawerPageModel.startForPage(page, mItems.size(), capacity);
        int end = AppDrawerPageModel.endForPage(page, mItems.size(), capacity);
        for (int i = start; i < end; i++) {
            AppDrawerItem item = mItems.get(i);
            if (stableId.equals(item.stableId)) return item;
        }
        return null;
    }

    public int itemsPerPage() {
        return mMetrics == null ? 1 : mMetrics.itemsPerPage;
    }

    @Override
    public int getItemCount() {
        return AppDrawerPageModel.pageCount(mItems.size(), itemsPerPage());
    }

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        GridLayout page = new GridLayout(parent.getContext());
        page.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        page.setUseDefaultMargins(false);
        // Cells never borrow pixels from the pill or page-dot bands. Drag feedback is rendered by
        // AppDrawerDragOverlayView, outside this clipped production page.
        page.setClipChildren(true);
        page.setClipToPadding(true);
        page.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new PageHolder(page);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int pagePosition) {
        AppDrawerHorizontalGridMetrics metrics = mMetrics;
        int columns = metrics == null ? AppDrawerGridMetrics.MIN_COLUMNS : metrics.columns;
        int rows = metrics == null ? 1 : metrics.rows;
        int capacity = Math.max(1, columns * rows);
        int pageWidth = metrics == null ? ViewGroup.LayoutParams.MATCH_PARENT
            : Math.max(1, Math.round(metrics.usablePageWidthPx));
        int pageHeight = metrics == null ? ViewGroup.LayoutParams.MATCH_PARENT
            : Math.max(1, Math.round(metrics.usablePageHeightPx));
        ViewGroup.LayoutParams rootParams = holder.page.getLayoutParams();
        if (rootParams.width != pageWidth || rootParams.height != pageHeight) {
            rootParams.width = pageWidth;
            rootParams.height = pageHeight;
            holder.page.setLayoutParams(rootParams);
        }
        holder.page.setColumnCount(columns);
        holder.page.setRowCount(rows);
        holder.ensureCapacity(capacity);

        int start = AppDrawerPageModel.startForPage(pagePosition, mItems.size(), capacity);
        int end = AppDrawerPageModel.endForPage(pagePosition, mItems.size(), capacity);
        int cellWidth = metrics == null ? 1 : Math.max(1, Math.round(metrics.cellWidthPx));
        int cellHeight = metrics == null ? 1 : Math.max(1, Math.round(metrics.rowHeightPx));
        for (int i = 0; i < holder.cells.size(); i++) {
            AppDrawerAppCellView cell = holder.cells.get(i);
            // FILL alignment carried in the specs themselves (not via setGravity, which would
            // rewrite them and defeat the equality check below).
            GridLayout.Spec rowSpec = GridLayout.spec(i / columns, GridLayout.FILL);
            GridLayout.Spec columnSpec = GridLayout.spec(i % columns, GridLayout.FILL);
            GridLayout.LayoutParams params =
                cell.getLayoutParams() instanceof GridLayout.LayoutParams
                    ? (GridLayout.LayoutParams) cell.getLayoutParams() : null;
            // Unconditional setLayoutParams here re-ran GridLayout's constraint solver twenty
            // times per page bind; a recycled page almost always keeps its exact grid geometry.
            if (params == null || params.width != cellWidth || params.height != cellHeight
                || !rowSpec.equals(params.rowSpec) || !columnSpec.equals(params.columnSpec)) {
                params = new GridLayout.LayoutParams(rowSpec, columnSpec);
                params.width = cellWidth;
                params.height = cellHeight;
                cell.setLayoutParams(params);
            }
            int entryIndex = start + i;
            if (entryIndex < end) {
                AppDrawerItem item = mItems.get(entryIndex);
                boolean wantsFolder = item.kind == AppDrawerItem.Kind.FOLDER;
                if (wantsFolder != (cell instanceof AppDrawerFolderCellView)) {
                    int cellIndex = i;
                    holder.page.removeView(cell);
                    cell.unbind();
                    cell = wantsFolder ? new AppDrawerFolderCellView(holder.page.getContext())
                        : new AppDrawerAppCellView(holder.page.getContext());
                    holder.cells.set(cellIndex, cell);
                    holder.page.addView(cell, cellIndex);
                    cell.setLayoutParams(params);
                }
                cell.setVisibility(View.VISIBLE);
                cell.setClickable(true);
                if (wantsFolder) ((AppDrawerFolderCellView) cell).bindFolder(mDock, item.folder,
                    metrics, mClickGate, mPickupEnabled ? mDragController : null);
                else cell.bind(mDock, item.app, metrics == null ? 0 : Math.round(metrics.iconPx),
                    metrics == null ? 0 : Math.round(metrics.rowHeightPx), mClickGate,
                    mPickupEnabled ? mDragController : null);
                if (mPickupEnabled && mDragController != null) mDragController.bindTarget(cell, item);
                cell.setScrubAppearance('\0', '\0', 0f);
            } else {
                cell.unbind();
                cell.setClickable(false);
                cell.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull PageHolder holder) {
        super.onViewRecycled(holder);
        holder.unbindAll();
    }

    @Nullable
    public View pageZeroIcon(@NonNull RecyclerView pager) {
        RecyclerView.ViewHolder holder = pager.findViewHolderForAdapterPosition(0);
        if (!(holder instanceof PageHolder)) return null;
        PageHolder page = (PageHolder) holder;
        return page.cells.isEmpty() || mItems.isEmpty() ? null : page.cells.get(0).icon;
    }

    public static final class PageHolder extends RecyclerView.ViewHolder {
        @NonNull public final GridLayout page;
        @NonNull public final List<AppDrawerAppCellView> cells = new ArrayList<>();

        PageHolder(@NonNull GridLayout page) {
            super(page);
            this.page = page;
        }

        void ensureCapacity(int capacity) {
            while (cells.size() < capacity) {
                AppDrawerAppCellView cell = new AppDrawerAppCellView(page.getContext());
                cells.add(cell);
                page.addView(cell);
            }
            while (cells.size() > capacity) {
                AppDrawerAppCellView cell = cells.remove(cells.size() - 1);
                cell.unbind();
                page.removeView(cell);
            }
        }

        void unbindAll() {
            for (AppDrawerAppCellView cell : cells) cell.unbind();
        }
    }
}
