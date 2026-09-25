package com.termux.app.fragments.settings;

import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The pinned row of section-jump chips a multi-section settings page shows above its list, and
 * the adapter that filters the list to one section.
 *
 * <p>Kept split from {@link MaterialPreferenceFragment} on purpose: everything below that decides
 * <i>which</i> sections exist and <i>which</i> rows a filter shows is a set of static, pure
 * functions over the real {@link androidx.preference.Preference} tree the page already built (no
 * view, no fragment, no click handling), so a test can drive it directly. {@link FilteringAdapter}
 * and {@link #bind} are the "dumb" view/adapter layer the fragment wires up; they hold no policy
 * of their own beyond "show what the pure functions say".
 *
 * <p>Filtering happens at the adapter level, not by calling {@link Preference#setVisible} on the
 * page's own preferences: many pages already drive {@code setVisible} themselves (X11 gating, TAI
 * rows, privileged gates), and a filter that used the same switch would either fight that logic on
 * the way in or, worse, forget it on the way out — "All" would wrongly reveal a row the page had
 * hidden for its own reasons. {@link FilteringAdapter} instead wraps the page's real
 * {@link PreferenceGroupAdapter} (which already excludes whatever the page marked invisible) and
 * only narrows the *positions* it exposes, so a hidden row is never reachable under any filter,
 * including "All".
 */
@Keep
public final class SettingsSectionChips {

    /** Below this many qualifying sections, the chip row buys nothing and stays hidden. */
    private static final int MIN_SECTIONS_TO_SHOW = 3;

    private SettingsSectionChips() {}

    // ---------------------------------------------------------------------------------------
    // Pure logic: which sections exist, and what a filter lets through.
    // ---------------------------------------------------------------------------------------

    /**
     * The page's top-level {@link PreferenceCategory} sections that currently have at least one
     * visible row of their own, in declaration order. A category the page hid entirely, or left
     * empty of visible children, is not a section here — so it never gets a chip and can never be
     * selected.
     */
    @NonNull
    public static List<PreferenceCategory> visibleSections(@NonNull PreferenceGroup screen) {
        List<PreferenceCategory> sections = new ArrayList<>();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if (!(preference instanceof PreferenceCategory) || !preference.isVisible()) continue;
            PreferenceCategory category = (PreferenceCategory) preference;
            if (hasVisibleChild(category)) sections.add(category);
        }
        return sections;
    }

    /** Whether the section list is worth a chip row at all. */
    public static boolean shouldShowChipRow(@NonNull List<PreferenceCategory> sections) {
        return sections.size() >= MIN_SECTIONS_TO_SHOW;
    }

    private static boolean hasVisibleChild(@NonNull PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference child = group.getPreference(i);
            if (!child.isVisible()) continue;
            if (child instanceof PreferenceGroup) {
                if (hasVisibleChild((PreferenceGroup) child)) return true;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * The top-level {@link PreferenceCategory} a preference falls under, by walking up its parent
     * chain to the child of {@code screen} that contains it — or {@code null} for a row that sits
     * directly on the screen outside any category ("uncategorised"), including the screen itself.
     * A category header's own row maps to itself.
     */
    @Nullable
    public static PreferenceCategory topCategoryOf(@NonNull Preference preference, @NonNull PreferenceGroup screen) {
        if (preference == screen) return null;
        Preference current = preference;
        PreferenceGroup parent = current.getParent();
        while (parent != null && parent != screen) {
            current = parent;
            parent = current.getParent();
        }
        if (parent != screen) return null; // not under this screen at all
        return current instanceof PreferenceCategory ? (PreferenceCategory) current : null;
    }

    /**
     * Whether {@code preference} stays visible under {@code filter}. A {@code null} filter ("All")
     * lets everything the page itself shows through unchanged; a section filter keeps only rows
     * (and the header) under that one category, hiding every other category and every
     * uncategorised top-level row.
     */
    public static boolean isVisibleUnderFilter(@NonNull Preference preference, @NonNull PreferenceGroup screen,
                                               @Nullable PreferenceCategory filter) {
        if (filter == null) return true;
        return filter.equals(topCategoryOf(preference, screen));
    }

    // ---------------------------------------------------------------------------------------
    // Adapter: narrows the real PreferenceGroupAdapter's positions to the active filter.
    // ---------------------------------------------------------------------------------------

    /** Notified whenever the underlying preference tree changes, so the fragment can rebuild chips. */
    public interface OnSectionsChangedListener {
        void onSectionsChanged();
    }

    /**
     * Wraps the page's real {@link PreferenceGroupAdapter}: delegates every row it exposes to the
     * wrapped adapter unchanged, but only exposes the positions {@link #isVisibleUnderFilter} keeps
     * for the current filter. Never calls {@link Preference#setVisible} itself.
     */
    public static final class FilteringAdapter extends RecyclerView.Adapter<PreferenceViewHolder>
        implements PreferenceGroup.PreferencePositionCallback {

        private final PreferenceGroupAdapter inner;
        private final PreferenceGroup screen;
        private final List<Integer> shownPositions = new ArrayList<>();
        @Nullable private PreferenceCategory filter;
        @Nullable private OnSectionsChangedListener listener;

        public FilteringAdapter(@NonNull PreferenceGroupAdapter inner, @NonNull PreferenceGroup screen) {
            this.inner = inner;
            this.screen = screen;
            setHasStableIds(true);
            inner.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { onInnerChanged(); }
                @Override public void onItemRangeChanged(int positionStart, int itemCount) { onInnerChanged(); }
                @Override public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) { onInnerChanged(); }
                @Override public void onItemRangeInserted(int positionStart, int itemCount) { onInnerChanged(); }
                @Override public void onItemRangeRemoved(int positionStart, int itemCount) { onInnerChanged(); }
                @Override public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) { onInnerChanged(); }
            });
            recompute();
        }

        public void setOnSectionsChangedListener(@Nullable OnSectionsChangedListener listener) {
            this.listener = listener;
        }

        /** {@code null} shows every row the page itself shows ("All"). */
        public void setFilter(@Nullable PreferenceCategory filter) {
            if (this.filter == filter) return;
            this.filter = filter;
            recompute();
            notifyDataSetChanged();
        }

        @Nullable
        public PreferenceCategory getFilter() {
            return filter;
        }

        private void onInnerChanged() {
            recompute();
            notifyDataSetChanged();
            if (listener != null) listener.onSectionsChanged();
        }

        private void recompute() {
            shownPositions.clear();
            int count = inner.getItemCount();
            for (int i = 0; i < count; i++) {
                Preference preference = inner.getItem(i);
                if (preference != null && isVisibleUnderFilter(preference, screen, filter)) {
                    shownPositions.add(i);
                }
            }
        }

        private int innerPosition(int position) {
            return shownPositions.get(position);
        }

        /** PreferenceFragmentCompat.scrollToPreference refuses an adapter without these. */
        @Override
        public int getPreferenceAdapterPosition(@NonNull String key) {
            return filteredPosition(inner.getPreferenceAdapterPosition(key));
        }

        @Override
        public int getPreferenceAdapterPosition(@NonNull Preference preference) {
            return filteredPosition(inner.getPreferenceAdapterPosition(preference));
        }

        private int filteredPosition(int innerPosition) {
            if (innerPosition == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION;
            int position = shownPositions.indexOf(innerPosition);
            return position < 0 ? RecyclerView.NO_POSITION : position;
        }

        /** The preference shown at this filtered position — a test's way to check a filter's result
         *  without reaching into the wrapped adapter's private position mapping. */
        @VisibleForTesting
        @NonNull
        public Preference getItem(int position) {
            return inner.getItem(innerPosition(position));
        }

        @Override
        public int getItemCount() {
            return shownPositions.size();
        }

        @Override
        public int getItemViewType(int position) {
            return inner.getItemViewType(innerPosition(position));
        }

        @Override
        public long getItemId(int position) {
            return inner.getItemId(innerPosition(position));
        }

        @NonNull
        @Override
        public PreferenceViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            return inner.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
            inner.onBindViewHolder(holder, innerPosition(position));
        }
    }

    // ---------------------------------------------------------------------------------------
    // View layer: dumb — renders whatever bind() is told to, decides nothing itself.
    // ---------------------------------------------------------------------------------------

    /** Reported when the viewer picks a chip: the section, or {@code null} for "All". */
    public interface OnSectionSelectedListener {
        void onSectionSelected(@Nullable PreferenceCategory section);
    }

    /**
     * Rebuilds the chip row from scratch against the given sections and selection. Cheap enough to
     * call on every preference-tree change: a settings page's section count is small (single
     * digits) and this never runs per animation frame.
     */
    public static void bind(@NonNull View chipsRow, @NonNull List<PreferenceCategory> sections,
                            @Nullable PreferenceCategory selected, @NonNull OnSectionSelectedListener listener) {
        ChipGroup group = chipsRow.findViewById(R.id.settings_section_chips_group);
        if (group == null) return;

        if (!shouldShowChipRow(sections)) {
            chipsRow.setVisibility(View.GONE);
            group.removeAllViews();
            return;
        }
        chipsRow.setVisibility(View.VISIBLE);
        group.removeAllViews();

        android.content.Context context = chipsRow.getContext();
        CharSequence allLabel = context.getString(R.string.settings_section_chip_all);
        group.addView(createChip(context, allLabel, selected == null,
            () -> listener.onSectionSelected(null)));

        for (PreferenceCategory section : sections) {
            CharSequence title = section.getTitle();
            if (title == null) title = allLabel;
            group.addView(createChip(context, title, section.equals(selected),
                () -> listener.onSectionSelected(section)));
        }
    }

    private static Chip createChip(@NonNull android.content.Context context, @NonNull CharSequence label,
                                   boolean checked, @NonNull Runnable onClick) {
        Chip chip = new Chip(context);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setContentDescription(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setFocusable(true);
        // Compact, so a page's sections usually fit in two lines; the touch target stays 48dp.
        chip.setChipMinHeight(dp(context, 30));
        chip.setLayoutParams(new ChipGroup.LayoutParams(
            ChipGroup.LayoutParams.WRAP_CONTENT, ChipGroup.LayoutParams.WRAP_CONTENT));
        chip.setOnClickListener(view -> onClick.run());
        return chip;
    }

    private static int dp(@NonNull android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
