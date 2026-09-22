package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
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
        /**
         * Resolves the card's artwork: a live preview where the provider offers one, otherwise its
         * preview bitmap, otherwise its icon.
         */
        void loadPreview(@NonNull WidgetProviderItem item,
                         @NonNull WidgetProviderCatalogLoader.PreviewCallback callback);
        /** This provider's live preview would not inflate; give the card a flat one from now on. */
        default void notePreviewRenderFailed(@NonNull WidgetProviderItem item) { }
        /** The rows are gone, so is every preview that was held for them. */
        void releasePreviews();
    }
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
        // The slot is the card's picture of the widget: sized to a template at bind, clipped to the
        // corner the platform gives widget backgrounds, and holding either a host view or a bitmap.
        PreviewSlot slot = new PreviewSlot(parent.getContext()); slot.setTag("slot");
        slot.setClipChildren(true);
        slot.setOutlineProvider(cardOutline(parent.getContext()));
        slot.setClipToOutline(true);
        ImageView preview = new ImageView(parent.getContext()); preview.setTag("preview");
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        slot.addView(preview, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        WidgetPickerCardTemplate initial = WidgetPickerCardTemplate.forSpan(1, 1);
        card.addView(slot, new LinearLayout.LayoutParams(initial.widthPx(density),
            initial.heightPx(density)));
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
        TextView title = holder.itemView.findViewWithTag("title");
        TextView span = holder.itemView.findViewWithTag("span");
        WidgetPickerCardTemplate template = WidgetPickerCardTemplate.forSpan(item.columnSpan,
            item.rowSpan);
        cell.template = template;
        applySlotSize(cell, template);
        // The gallery glyph stands in while the artwork resolves.
        releaseHost(cell);
        ImageView slotImage = holder.itemView.findViewWithTag("preview");
        slotImage.setVisibility(View.VISIBLE);
        applyPreview(slotImage, null);
        requestArtwork(cell, item, true);
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

    /** A recycled card gives its host view back before the holder is aimed at another provider. */
    @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof Holder) { releaseHost((Holder) holder); ((Holder) holder).bound = null; }
    }

    private void requestArtwork(@NonNull Holder cell, @NonNull WidgetProviderItem item,
                                boolean allowRetry) {
        if (previews == null) return;
        previews.loadPreview(item, (loaded, artwork) -> {
            // The holder may have been recycled onto another row by the time this lands.
            if (cell.bound != loaded) return;
            applyArtwork(cell, item, artwork, allowRetry);
        });
    }

    /**
     * A live preview becomes a real host view with no bound id; anything else stays a bitmap. A
     * provider whose preview will not inflate is reported once and asked again for a flat one —
     * the retry cannot come back live, so it cannot loop.
     */
    private void applyArtwork(@NonNull Holder cell, @NonNull WidgetProviderItem item,
                              @Nullable WidgetPreviewArtwork artwork, boolean allowRetry) {
        ImageView preview = cell.itemView.findViewWithTag("preview");
        if (artwork != null && artwork.isLive()) {
            FrameLayout slot = cell.itemView.findViewWithTag("slot");
            try {
                AppWidgetHostView host = cell.host;
                if (host == null) {
                    host = new AppWidgetHostView(cell.itemView.getContext());
                    cell.host = host;
                }
                if (host.getParent() != slot) {
                    if (host.getParent() instanceof ViewGroup) {
                        ((ViewGroup) host.getParent()).removeView(host);
                    }
                    slot.addView(host, 0);
                }
                host.setAppWidget(0, item.info);
                host.updateAppWidget(artwork.remoteViews);
                // A picture of a widget, not a widget: it says nothing of its own and is not read.
                host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                host.setContentDescription(null);
                scaleIntoSlot(host, item, cell.template,
                    cell.itemView.getResources().getDisplayMetrics().density);
                preview.setVisibility(View.GONE);
                return;
            } catch (RuntimeException | LinkageError failure) {
                releaseHost(cell);
                if (previews != null) previews.notePreviewRenderFailed(item);
                preview.setVisibility(View.VISIBLE);
                applyPreview(preview, null);
                if (allowRetry) requestArtwork(cell, item, false);
                return;
            }
        }
        releaseHost(cell);
        preview.setVisibility(View.VISIBLE);
        applyPreview(preview, artwork == null ? null : artwork.image);
    }

    /** The card is the template's size whatever the artwork turns out to be. */
    private static void applySlotSize(@NonNull Holder cell,
                                      @NonNull WidgetPickerCardTemplate template) {
        View slot = cell.itemView.findViewWithTag("slot");
        if (slot == null) return;
        float density = cell.itemView.getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams params = slot.getLayoutParams();
        int width = template.widthPx(density);
        int height = template.heightPx(density);
        if (params.width != width || params.height != height) {
            params.width = width; params.height = height; slot.setLayoutParams(params);
        }
    }

    /**
     * The host view is laid out at the widget's own pixel size and then scaled into the card, so a
     * preview layout written for a 4x2 widget is not asked to fit a 4x2 card's worth of dp.
     */
    private static void scaleIntoSlot(@NonNull AppWidgetHostView host,
                                      @NonNull WidgetProviderItem item,
                                      @NonNull WidgetPickerCardTemplate template, float density) {
        int slotWidth = template.widthPx(density);
        int slotHeight = template.heightPx(density);
        int naturalWidth = Math.max(1, item.info.minWidth);
        int naturalHeight = Math.max(1, item.info.minHeight);
        float scale = Math.min(slotWidth / (float) naturalWidth,
            slotHeight / (float) naturalHeight);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(naturalWidth, naturalHeight);
        params.leftMargin = Math.round((slotWidth - naturalWidth * scale) / 2f);
        params.topMargin = Math.round((slotHeight - naturalHeight * scale) / 2f);
        host.setLayoutParams(params);
        host.setPivotX(0f); host.setPivotY(0f);
        host.setScaleX(scale); host.setScaleY(scale);
    }

    private static void releaseHost(@NonNull Holder cell) {
        AppWidgetHostView host = cell.host;
        if (host == null) return;
        if (host.getParent() instanceof ViewGroup) ((ViewGroup) host.getParent()).removeView(host);
        cell.host = null;
    }

    /**
     * The slot swallows every touch before its children see it. A preview layout carries the
     * provider's own clickable views and pending intents, and the card is a card: tapping it adds
     * the widget. Intercepting without handling leaves the tap to the card itself.
     */
    private static final class PreviewSlot extends FrameLayout {
        PreviewSlot(@NonNull Context context) { super(context); }
        @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
    }

    /**
     * The corner a widget wears, never more than a quarter of the card — a 1x1 card at the
     * platform's full radius would read as a circle.
     */
    @NonNull private static ViewOutlineProvider cardOutline(@NonNull Context context) {
        final float systemRadius = systemWidgetRadius(context);
        return new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                float radius = Math.min(systemRadius,
                    Math.min(view.getWidth(), view.getHeight()) * 0.25f);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        };
    }

    private static float systemWidgetRadius(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getResources().getDimension(
                    android.R.dimen.system_app_widget_background_radius);
            } catch (Resources.NotFoundException ignored) {
                // Fall through to the fixed radius.
            }
        }
        return 16f * density;
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
    /** Artwork fills the card; the stand-in glyph is left at its own size in the middle of it. */
    private static void applyPreview(@NonNull ImageView view, @Nullable Drawable preview) {
        if (preview != null) {
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setImageDrawable(preview);
        } else {
            view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            view.setImageResource(android.R.drawable.ic_menu_gallery);
        }
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
        @Nullable AppWidgetHostView host;
        @NonNull WidgetPickerCardTemplate template = WidgetPickerCardTemplate.forSpan(1, 1);
        Holder(View item) { super(item); }
    }
}
