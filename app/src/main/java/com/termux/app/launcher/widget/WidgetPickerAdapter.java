package com.termux.app.launcher.widget;

import android.content.res.Resources;
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

import com.termux.R;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collapsed app rows and their provider cards for the focusless in-pane picker.
 *
 * <p>A phone offers a few hundred widgets; one row each is a scroll nobody reads to the end of. The
 * list is therefore one row per app — icon, name, how many widgets it offers — and the cards appear
 * only inside the app the user opened. Which apps are open is sheet-scoped state held here, so a
 * picker that closes and reopens starts collapsed again, and a search opens the apps it found
 * matches in so the results need no second tap.
 */
public final class WidgetPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener { void onProviderSelected(@NonNull WidgetProviderItem item); }
    public interface FitPredicate { boolean canFit(@NonNull WidgetProviderItem item); }
    public interface PreviewLoader {
        /** Resolves the card's artwork: the provider's preview, or its icon when it has none. */
        void loadPreview(@NonNull WidgetProviderItem item,
                         @NonNull WidgetProviderCatalogLoader.PreviewCallback callback);
        /** The rows are gone, so is every preview that was held for them. */
        void releasePreviews();
    }
    /** Edge of the preview slot on a provider card; previews are never held larger than this. */
    static final int PREVIEW_DP = 56;
    private static final int HEADER = 0;
    private static final int PROVIDER = 1;
    private final ArrayList<WidgetAppGroup> catalog = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> expandedBeforeSearch = new HashSet<>();
    private final ArrayList<Object> rows = new ArrayList<>();
    private final Listener listener;
    private String query = "";
    private FitPredicate fit = item -> item.fits;
    @Nullable private PreviewLoader previews;

    public WidgetPickerAdapter(@NonNull Listener listener) { this.listener = listener; }
    public void setFitPredicate(@NonNull FitPredicate value) { fit = value; notifyDataSetChanged(); }
    public void setPreviewLoader(@Nullable PreviewLoader value) { previews = value; }

    /** An empty catalog is the picker closing: collapse state and the query go with it. */
    public void submit(@NonNull List<WidgetAppGroup> groups) {
        catalog.clear(); catalog.addAll(groups);
        if (catalog.isEmpty()) {
            expanded.clear(); expandedBeforeSearch.clear(); query = "";
            if (previews != null) previews.releasePreviews();
        }
        rebuild();
    }

    /**
     * Filters to matching widgets and opens the apps they are in. Clearing the query puts the list
     * back the way the user had it before they started typing.
     */
    public void setQuery(@Nullable String value) {
        String next = value == null ? "" : value;
        boolean wasSearching = !WidgetPickerSearch.normalize(query).isEmpty();
        boolean searching = !WidgetPickerSearch.normalize(next).isEmpty();
        query = next;
        if (searching) {
            if (!wasSearching) { expandedBeforeSearch.clear(); expandedBeforeSearch.addAll(expanded); }
            expanded.clear();
            for (WidgetAppGroup group : WidgetPickerSearch.filter(catalog, query)) {
                expanded.add(group.key());
            }
        } else if (wasSearching) {
            expanded.clear(); expanded.addAll(expandedBeforeSearch); expandedBeforeSearch.clear();
        }
        rebuild();
    }

    @NonNull public String query() { return query; }

    /** True while a query is in force and nothing in the catalog answers it. */
    public boolean searchFoundNothing() {
        return !WidgetPickerSearch.normalize(query).isEmpty() && rows.isEmpty();
    }

    public boolean anyProviderFits() {
        for (WidgetAppGroup group : catalog) {
            for (WidgetProviderItem item : group.providers) if (fit.canFit(item)) return true;
        }
        return false;
    }

    void toggleSection(@NonNull WidgetAppGroup group) {
        if (!expanded.remove(group.key())) expanded.add(group.key());
        rebuild();
    }

    boolean isExpanded(@NonNull WidgetAppGroup group) { return expanded.contains(group.key()); }

    private void rebuild() {
        ArrayList<Object> next = new ArrayList<>();
        for (WidgetAppGroup group : WidgetPickerSearch.filter(catalog, query)) {
            boolean open = expanded.contains(group.key());
            next.add(new Section(group, open));
            if (open) next.addAll(group.providers);
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RowDiff(rows, next));
        rows.clear(); rows.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @Override public int getItemViewType(int position) {
        return rows.get(position) instanceof Section ? HEADER : PROVIDER;
    }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                                         int type) {
        float density = parent.getResources().getDisplayMetrics().density;
        if (type == HEADER) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setMinimumHeight(Math.round(56 * density));
            row.setPadding(Math.round(16 * density), Math.round(10 * density),
                Math.round(16 * density), Math.round(10 * density));
            ImageView icon = new ImageView(parent.getContext()); icon.setTag("icon");
            row.addView(icon, new LinearLayout.LayoutParams(Math.round(28 * density), Math.round(28 * density)));
            TextView label = new TextView(parent.getContext()); label.setTag("label");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMarginStart(Math.round(10 * density)); row.addView(label, lp);
            TextView count = new TextView(parent.getContext()); count.setTag("count");
            count.setAlpha(0.7f);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            countParams.setMarginStart(Math.round(8 * density)); row.addView(count, countParams);
            TextView chevron = new TextView(parent.getContext()); chevron.setTag("chevron");
            chevron.setAlpha(0.7f); chevron.setGravity(Gravity.CENTER);
            chevron.setTypeface(NerdFontSpans.typeface(parent.getContext()));
            chevron.setTextSize(14f);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                Math.round(20 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            chevronParams.setMarginStart(Math.round(8 * density));
            row.addView(chevron, chevronParams);
            return new Holder(row);
        }
        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(Math.round(72 * density)); card.setPadding(Math.round(28 * density),
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
        if (row instanceof Section) { bindSection(holder, (Section) row); return; }
        WidgetProviderItem item = (WidgetProviderItem) row;
        ImageView preview = holder.itemView.findViewWithTag("preview");
        TextView title = holder.itemView.findViewWithTag("title");
        TextView span = holder.itemView.findViewWithTag("span");
        // The gallery glyph stands in while the preview or icon resolves.
        applyPreview(preview, null);
        if (previews != null) {
            previews.loadPreview(item, (loaded, drawable) -> {
                // The holder may have been recycled onto another row by the time this lands.
                if (cell.bound == loaded) {
                    applyPreview(cell.itemView.findViewWithTag("preview"), drawable);
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

    /** The app row stays live whatever its widgets measure: it is how they are reached at all. */
    private void bindSection(@NonNull RecyclerView.ViewHolder holder, @NonNull Section section) {
        WidgetAppGroup group = section.group;
        Resources resources = holder.itemView.getResources();
        ImageView icon = holder.itemView.findViewWithTag("icon");
        TextView label = holder.itemView.findViewWithTag("label");
        TextView count = holder.itemView.findViewWithTag("count");
        TextView chevron = holder.itemView.findViewWithTag("chevron");
        icon.setImageDrawable(group.badgedIcon);
        label.setText(group.label);
        String countText = resources.getQuantityString(R.plurals.widget_picker_widget_count,
            section.count, section.count);
        count.setText(countText);
        // nf-fa-angle_up / nf-fa-angle_down, the same family the page's own chrome wears.
        chevron.setText(section.expanded ? "\uf106" : "\uf107");
        holder.itemView.setEnabled(true); holder.itemView.setAlpha(1f);
        holder.itemView.setClickable(true); holder.itemView.setFocusable(false);
        holder.itemView.setContentDescription(group.label + ", " + countText + ", "
            + resources.getString(section.expanded ? R.string.widget_picker_app_expanded
                : R.string.widget_picker_app_collapsed));
        holder.itemView.setOnClickListener(view -> toggleSection(group));
    }

    @Override public int getItemCount() { return rows.size(); }
    @Nullable public WidgetProviderItem providerAt(int adapterPosition) {
        Object value = rows.get(adapterPosition);
        return value instanceof WidgetProviderItem ? (WidgetProviderItem) value : null;
    }
    @Nullable WidgetAppGroup sectionAt(int adapterPosition) {
        Object value = rows.get(adapterPosition);
        return value instanceof Section ? ((Section) value).group : null;
    }
    private static void applyPreview(@NonNull ImageView view, @Nullable Drawable preview) {
        if (preview != null) view.setImageDrawable(preview);
        else view.setImageResource(android.R.drawable.ic_menu_gallery);
    }

    /** One collapsed or open app row; rebuilt on every change, so it carries its own state. */
    private static final class Section {
        final WidgetAppGroup group; final boolean expanded; final int count;
        Section(WidgetAppGroup group, boolean expanded) {
            this.group = group; this.expanded = expanded; this.count = group.providerCount;
        }
    }

    /** Rows keyed header=profile+package, provider=profile+component; app rows also compare their
     * open state and count, because those change without the catalog changing. */
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
            Object before = old.get(oldPosition);
            Object after = next.get(newPosition);
            if (before instanceof Section && after instanceof Section) {
                Section a = (Section) before; Section b = (Section) after;
                return a.group == b.group && a.expanded == b.expanded && a.count == b.count;
            }
            return before == after;
        }
        static String key(Object row) {
            if (row instanceof Section) return "h " + ((Section) row).group.key();
            WidgetProviderItem item = (WidgetProviderItem) row;
            return "p " + item.profileSerial + " " + item.info.provider.flattenToString();
        }
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        Object bound;
        Holder(View item) { super(item); }
    }
}
