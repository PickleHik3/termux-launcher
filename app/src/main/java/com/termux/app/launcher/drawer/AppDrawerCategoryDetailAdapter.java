package com.termux.app.launcher.drawer;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.List;

/** Expanded row-major apps, bound through the exact common drawer cell implementation. */
public final class AppDrawerCategoryDetailAdapter
    extends RecyclerView.Adapter<AppDrawerCategoryDetailAdapter.Holder> {
    @Nullable private SuggestionBarView dock;
    @NonNull private List<LauncherAppEntry> entries = new ArrayList<>();
    @Nullable private AppDrawerCategoryGridMetrics metrics;
    @NonNull private AppDrawerAppCellView.ClickGate clickGate = AppDrawerAppCellView.ALLOW_CLICKS;

    public AppDrawerCategoryDetailAdapter(@Nullable SuggestionBarView dock) {
        this.dock = dock;
        setHasStableIds(true);
    }
    public void setDock(@Nullable SuggestionBarView dock) { this.dock = dock; notifyDataSetChanged(); }
    public void setMetrics(@NonNull AppDrawerCategoryGridMetrics metrics) {
        this.metrics = metrics;
        notifyDataSetChanged();
    }
    public void setClickGate(@NonNull AppDrawerAppCellView.ClickGate clickGate) {
        this.clickGate = clickGate;
    }
    public void submit(@NonNull List<LauncherAppEntry> entries) {
        this.entries = new ArrayList<>(entries);
        notifyDataSetChanged();
    }
    @NonNull public List<LauncherAppEntry> entries() { return entries; }
    @Override public int getItemCount() { return entries.size(); }
    @Override public long getItemId(int position) { return entries.get(position).appRef.stableId().hashCode(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AppDrawerAppCellView cell = new AppDrawerAppCellView(parent.getContext());
        cell.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(cell);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppDrawerCategoryGridMetrics current = metrics;
        if (current == null) { holder.cell.unbind(); return; }
        holder.cell.bind(dock, entries.get(position), current.largeIconPx,
            Math.max(1, Math.round(current.expandedRowHeightPx)), clickGate);
    }
    @Override public void onViewRecycled(@NonNull Holder holder) {
        super.onViewRecycled(holder);
        holder.cell.unbind();
    }
    public void releaseAttached(@NonNull RecyclerView recycler) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recycler.getChildViewHolder(recycler.getChildAt(i));
            if (holder instanceof Holder) ((Holder) holder).cell.unbind();
        }
        submit(java.util.Collections.emptyList());
    }
    public int positionOfStableId(@Nullable String stableId) {
        if (stableId == null) return -1;
        for (int i = 0; i < entries.size(); i++)
            if (stableId.equals(entries.get(i).appRef.stableId())) return i;
        return -1;
    }
    @Nullable public View iconAt(@NonNull RecyclerView recycler, int position) {
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        return holder instanceof Holder ? ((Holder) holder).cell.icon : null;
    }
    public static final class Holder extends RecyclerView.ViewHolder {
        @NonNull public final AppDrawerAppCellView cell;
        Holder(@NonNull AppDrawerAppCellView cell) { super(cell); this.cell = cell; }
    }
}
