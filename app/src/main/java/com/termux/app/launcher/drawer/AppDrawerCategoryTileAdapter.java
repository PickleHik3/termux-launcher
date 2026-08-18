package com.termux.app.launcher.drawer;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import java.util.ArrayList;
import java.util.List;

/** Stable fixed-order category tile holders with explicit attached-icon release/rebind. */
public final class AppDrawerCategoryTileAdapter
    extends RecyclerView.Adapter<AppDrawerCategoryTileAdapter.Holder> {
    @Nullable private SuggestionBarView dock;
    @NonNull private List<AppDrawerCategoryBucket> buckets = new ArrayList<>();
    @Nullable private AppDrawerCategoryGridMetrics metrics;
    @Nullable private AppDrawerCategoryTileView.ExpansionListener expansionListener;
    @Nullable private AppDrawerCategoryChoiceListener categoryChoiceListener;
    @NonNull private AppDrawerAppCellView.ClickGate clickGate = AppDrawerAppCellView.ALLOW_CLICKS;

    public AppDrawerCategoryTileAdapter(@Nullable SuggestionBarView dock) {
        this.dock = dock;
        setHasStableIds(true);
    }

    public void setDock(@Nullable SuggestionBarView dock) { this.dock = dock; notifyDataSetChanged(); }
    public void setMetrics(@NonNull AppDrawerCategoryGridMetrics metrics) {
        this.metrics = metrics;
        notifyDataSetChanged();
    }
    @Nullable AppDrawerCategoryGridMetrics getMetrics() { return metrics; }
    public void setExpansionListener(@Nullable AppDrawerCategoryTileView.ExpansionListener listener) {
        expansionListener = listener;
    }
    public void setCategoryChoiceListener(@Nullable AppDrawerCategoryChoiceListener listener) {
        categoryChoiceListener = listener;
    }
    public void setClickGate(@NonNull AppDrawerAppCellView.ClickGate gate) { clickGate = gate; }
    public void submit(@NonNull List<AppDrawerCategoryBucket> buckets) {
        this.buckets = new ArrayList<>(buckets);
        notifyDataSetChanged();
    }
    @NonNull public List<AppDrawerCategoryBucket> buckets() { return buckets; }
    @Nullable public AppDrawerCategoryBucket bucketAt(int position) {
        return position >= 0 && position < buckets.size() ? buckets.get(position) : null;
    }
    @Nullable public AppDrawerCategoryBucket bucketForId(@Nullable String id) {
        if (id == null) return null;
        for (AppDrawerCategoryBucket bucket : buckets)
            if (bucket.category.slug.equals(id)) return bucket;
        return null;
    }

    @Override public long getItemId(int position) { return buckets.get(position).category.ordinal(); }
    @Override public int getItemCount() { return buckets.size(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AppDrawerCategoryTileView tile = new AppDrawerCategoryTileView(parent.getContext());
        tile.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(tile);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppDrawerCategoryGridMetrics current = metrics;
        AppDrawerCategoryTileView.ExpansionListener listener = expansionListener;
        if (current == null || listener == null) {
            holder.tile.unbind();
            return;
        }
        holder.tile.bind(dock, buckets.get(position), current, listener, clickGate, categoryChoiceListener);
    }

    @Override public void onViewRecycled(@NonNull Holder holder) {
        super.onViewRecycled(holder);
        holder.tile.unbind();
    }

    public void releaseAttachedPreviews(@NonNull RecyclerView recycler) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recycler.getChildViewHolder(recycler.getChildAt(i));
            if (holder instanceof Holder) ((Holder) holder).tile.releaseDrawables();
        }
    }

    public void rebindAttachedPreviews() { notifyItemRangeChanged(0, getItemCount()); }

    @Nullable
    public Frame selectedTileBounds(@NonNull RecyclerView recycler, @NonNull String categoryId,
                                    @NonNull View coordinateParent) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            RecyclerView.ViewHolder holder = recycler.getChildViewHolder(child);
            if (!(holder instanceof Holder)) continue;
            Holder tileHolder = (Holder) holder;
            AppDrawerCategoryBucket bucket = tileHolder.tile.bucket();
            if (bucket == null || !bucket.category.slug.equals(categoryId)) continue;
            int[] tileLocation = new int[2];
            int[] parentLocation = new int[2];
            tileHolder.tile.getLocationOnScreen(tileLocation);
            coordinateParent.getLocationOnScreen(parentLocation);
            float left = tileLocation[0] - parentLocation[0] + tileHolder.tile.tileLeft();
            float top = tileLocation[1] - parentLocation[1] + tileHolder.tile.tileTop();
            return new Frame(left, top, left + tileHolder.tile.tileSide(),
                top + tileHolder.tile.tileHeight());
        }
        return null;
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        @NonNull public final AppDrawerCategoryTileView tile;
        Holder(@NonNull AppDrawerCategoryTileView tile) { super(tile); this.tile = tile; }
    }
}
