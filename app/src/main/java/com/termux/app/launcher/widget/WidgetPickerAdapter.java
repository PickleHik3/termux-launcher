package com.termux.app.launcher.widget;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Group headers and provider cards for the focusless in-pane picker. */
public final class WidgetPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener { void onProviderSelected(@NonNull WidgetProviderItem item); }
    public interface FitPredicate { boolean canFit(@NonNull WidgetProviderItem item); }
    public interface PreviewLoader {
        void loadPreview(@NonNull WidgetProviderItem item,
                         @NonNull WidgetProviderCatalogLoader.PreviewCallback callback);
        /** The rows are gone, so is every preview that was held for them. */
        void releasePreviews();
    }
    /** Edge of the preview slot on a provider card; previews are never held larger than this. */
    static final int PREVIEW_DP = 56;
    private static final int HEADER = 0;
    private static final int PROVIDER = 1;
    private final ArrayList<Object> rows = new ArrayList<>();
    private final Listener listener;
    private FitPredicate fit = item -> item.fits;
    @Nullable private PreviewLoader previews;

    public WidgetPickerAdapter(@NonNull Listener listener) { this.listener = listener; }
    public void setFitPredicate(@NonNull FitPredicate value) { fit = value; notifyDataSetChanged(); }
    public void setPreviewLoader(@Nullable PreviewLoader value) { previews = value; }
    public void submit(@NonNull List<WidgetAppGroup> groups) {
        ArrayList<Object> next = new ArrayList<>();
        for (WidgetAppGroup group : groups) { next.add(group); next.addAll(group.providers); }
        if (next.isEmpty() && previews != null) previews.releasePreviews();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RowDiff(rows, next));
        rows.clear(); rows.addAll(next);
        diff.dispatchUpdatesTo(this);
    }
    public boolean anyProviderFits() {
        for (Object row : rows) if (row instanceof WidgetProviderItem
            && fit.canFit((WidgetProviderItem) row)) return true;
        return false;
    }
    @Override public int getItemViewType(int position) {
        return rows.get(position) instanceof WidgetAppGroup ? HEADER : PROVIDER;
    }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                                         int type) {
        float density = parent.getResources().getDisplayMetrics().density;
        if (type == HEADER) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(Math.round(16 * density), Math.round(12 * density),
                Math.round(16 * density), Math.round(6 * density));
            ImageView icon = new ImageView(parent.getContext()); icon.setTag("icon");
            row.addView(icon, new LinearLayout.LayoutParams(Math.round(28 * density), Math.round(28 * density)));
            TextView label = new TextView(parent.getContext()); label.setTag("label");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMarginStart(Math.round(10 * density)); row.addView(label, lp);
            return new Holder(row);
        }
        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(Math.round(72 * density)); card.setPadding(Math.round(16 * density),
            Math.round(8 * density), Math.round(16 * density), Math.round(8 * density));
        ImageView preview = new ImageView(parent.getContext()); preview.setTag("preview");
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(preview, new LinearLayout.LayoutParams(Math.round(PREVIEW_DP * density),
            Math.round(PREVIEW_DP * density)));
        LinearLayout labels = new LinearLayout(parent.getContext()); labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(parent.getContext()); title.setTag("title");
        TextView span = new TextView(parent.getContext()); span.setTag("span");
        labels.addView(title); labels.addView(span);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(Math.round(12 * density)); card.addView(labels, lp);
        return new Holder(card);
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        Holder cell = (Holder) holder;
        cell.bound = row;
        if (row instanceof WidgetAppGroup) {
            WidgetAppGroup group = (WidgetAppGroup) row;
            ImageView icon = holder.itemView.findViewWithTag("icon");
            TextView label = holder.itemView.findViewWithTag("label");
            icon.setImageDrawable(group.badgedIcon); label.setText(group.label);
            holder.itemView.setContentDescription(group.label);
            holder.itemView.setClickable(false); return;
        }
        WidgetProviderItem item = (WidgetProviderItem) row;
        ImageView preview = holder.itemView.findViewWithTag("preview");
        TextView title = holder.itemView.findViewWithTag("title");
        TextView span = holder.itemView.findViewWithTag("span");
        // App icon (then gallery) doubles as the placeholder while the preview resolves.
        applyPreview(preview, item, null);
        if (previews != null) {
            previews.loadPreview(item, (loaded, drawable) -> {
                // The holder may have been recycled onto another row by the time this lands.
                if (cell.bound == loaded) {
                    applyPreview(cell.itemView.findViewWithTag("preview"), loaded, drawable);
                }
            });
        }
        title.setText(item.label);
        String spanText = item.columnSpan + " × " + item.rowSpan + " cells";
        if (item.minimumColumnSpan > 0 && item.minimumRowSpan > 0
            && (item.minimumColumnSpan != item.columnSpan || item.minimumRowSpan != item.rowSpan)) {
            spanText += " · minimum " + item.minimumColumnSpan + " × " + item.minimumRowSpan;
        }
        span.setText(spanText);
        boolean enabled = fit.canFit(item);
        holder.itemView.setEnabled(enabled); holder.itemView.setAlpha(enabled ? 1f : 0.45f);
        holder.itemView.setClickable(enabled); holder.itemView.setFocusable(false);
        holder.itemView.setContentDescription(item.label + ", " + spanText
            + (enabled ? "" : ", no space"));
        holder.itemView.setOnClickListener(enabled ? view -> listener.onProviderSelected(item) : null);
    }
    @Override public int getItemCount() { return rows.size(); }
    @SuppressWarnings("unchecked") public WidgetProviderItem providerAt(int adapterPosition) {
        Object value = rows.get(adapterPosition);
        return value instanceof WidgetProviderItem ? (WidgetProviderItem) value : null;
    }
    private static void applyPreview(@NonNull ImageView view, @NonNull WidgetProviderItem item,
                                     @Nullable Drawable preview) {
        if (preview != null || item.icon != null) {
            view.setImageDrawable(preview != null ? preview : item.icon);
        } else {
            view.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }
    /** Rows keyed header=profile+package, provider=profile+component; contents by identity,
     * because a rebuilt catalog always creates fresh row objects. */
    private static final class RowDiff extends DiffUtil.Callback {
        final List<Object> old; final List<Object> next;
        RowDiff(List<Object> old, List<Object> next) {
            this.old = new ArrayList<>(old); this.next = next;
        }
        @Override public int getOldListSize() { return old.size(); }
        @Override public int getNewListSize() { return next.size(); }
        @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
            return key(old.get(oldPosition)).equals(key(next.get(newPosition)));
        }
        @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
            return old.get(oldPosition) == next.get(newPosition);
        }
        static String key(Object row) {
            if (row instanceof WidgetAppGroup) {
                WidgetAppGroup group = (WidgetAppGroup) row;
                return "h " + group.profileSerial + " " + group.packageName;
            }
            WidgetProviderItem item = (WidgetProviderItem) row;
            return "p " + item.profileSerial + " " + item.info.provider.flattenToString();
        }
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        Object bound;
        Holder(View item) { super(item); }
    }
}
