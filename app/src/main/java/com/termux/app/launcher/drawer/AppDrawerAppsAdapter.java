package com.termux.app.launcher.drawer;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawer grid's cells: an icon over a single-line label, bound to one {@link LauncherAppEntry}.
 *
 * <p>Every visual and behavioural decision is delegated to the dock rather than reproduced here.
 * The artwork comes from {@link SuggestionBarView#getRenderedIcon} — the same rendered, byte-budgeted
 * cache the dock draws from, so a drawer cell and a dock icon of the same pixel size are literally
 * one drawable — the tint from {@link SuggestionBarView#applyIconColorFilter}, the label colour from
 * {@link SuggestionBarView#getLauncherTextColor()}, the tap from
 * {@link SuggestionBarView#launchEntryFromDrawer} and the long press from
 * {@link SuggestionBarView#bindDrawerAppContextLongPress}. A cell that owned any of those would be
 * the second copy that drifts: the dock's launch ladder alone carries the clone-profile branch, the
 * activity fallbacks, the launch transition and the usage recording.
 *
 * <p>Cells are built in code, like every other launcher surface in this app
 * ({@code SuggestionBarView#createEntryButton}, {@code #createPopupEntryButton}) — inflating an XML
 * cell per row would put a parser on the scroll path for a two-view layout.
 *
 * <p>The dock reference is nullable so the grid can be measured and driven without one; a cell then
 * renders as its label alone, which is what the Robolectric harness sees.
 */
public final class AppDrawerAppsAdapter extends RecyclerView.Adapter<AppDrawerAppsAdapter.Cell> {

    /** Label size, in sp. Small enough that two words of an app name still fit a 84dp cell. */
    public static final float LABEL_TEXT_SP = 11f;

    /** Where a search-result row's category line comes from; null hides the line. */
    public interface CategoryLabelLookup {
        @Nullable CharSequence categoryLabelFor(@NonNull LauncherAppEntry entry);
    }

    private static final char[] NO_LETTERS = new char[0];
    private static final int VIEW_TYPE_APP = 0;
    private static final int VIEW_TYPE_FOLDER = 1;
    private static final int VIEW_TYPE_SEARCH_ROW = 2;

    @Nullable private SuggestionBarView mDock;
    @NonNull private List<LauncherAppEntry> mEntries = new ArrayList<>();
    @NonNull private List<AppDrawerItem> mItems = new ArrayList<>();
    /**
     * The section index's per-position letters, cached parallel to {@link #mEntries}. The scrub's
     * per-frame walk goes from an attached child's adapter position to its letter through this array
     * rather than through an entry lookup and a normalisation, sixty times a second per visible cell.
     */
    @NonNull private char[] mPositionLetters = NO_LETTERS;
    @Nullable private AppDrawerGridMetrics mMetrics;
    @Nullable private AppDrawerDragController mDragController;
    private boolean mPickupEnabled;
    @NonNull private AppDrawerAppCellView.ClickGate mClickGate = AppDrawerAppCellView.ALLOW_CLICKS;
    /** The letter under the finger, or 0. Written every frame of a scrub and never notified on. */
    private char mScrubLetter = '\0';
    private float mScrubStrength;
    /** True while the categories view type shows ranked results as rows instead of grid cells. */
    private boolean mSearchRowPresentation;
    @Nullable private CategoryLabelLookup mCategoryLookup;

    public AppDrawerAppsAdapter(@Nullable SuggestionBarView dock) {
        mDock = dock;
        // Positions are the ranked list's own order and change wholesale on every query, so stable
        // ids would claim a continuity that does not exist.
        setHasStableIds(false);
    }

    public void setDock(@Nullable SuggestionBarView dock) {
        mDock = dock;
        notifyDataSetChanged();
    }

    public void setDragController(@Nullable AppDrawerDragController controller,
                                  @NonNull AppDrawerAppCellView.ClickGate clickGate) {
        mDragController = controller;
        mClickGate = clickGate;
        notifyDataSetChanged();
    }

    /** Set immediately before a list submission, so the submission remains the sole rebind. */
    public void setPickupEnabled(boolean enabled) { mPickupEnabled = enabled; }

    /**
     * Replaces the visible list. Called with the full catalogue, sorted by label, for an empty query
     * and with the ranked results otherwise.
     */
    public void submit(@NonNull List<LauncherAppEntry> entries) {
        submit(entries, null);
    }

    /**
     * The same, with the section index built over the same list so the letters can be cached now
     * rather than recomputed per cell per frame during a scrub.
     */
    public void submit(@NonNull List<LauncherAppEntry> entries,
                       @Nullable AppDrawerSectionIndex index) {
        mEntries = new ArrayList<>(entries);
        mItems = AppDrawerItemComposer.appsOnly(entries);
        mPositionLetters = index == null ? NO_LETTERS : index.copyPositionLetters();
        notifyDataSetChanged();
    }

    public void submitItems(@NonNull List<AppDrawerItem> items) {
        submitItems(items, null);
    }

    public void submitItems(@NonNull List<AppDrawerItem> items,
                            @Nullable AppDrawerSectionIndex index) {
        mItems = new ArrayList<>(items);
        mEntries = new ArrayList<>();
        for (AppDrawerItem item : items) if (item.app != null) mEntries.add(item.app);
        mPositionLetters = index == null ? NO_LETTERS : index.copyPositionLetters();
        notifyDataSetChanged();
    }

    /**
     * The letter of an adapter position, or {@code 0} outside the list or before an index was
     * submitted with it.
     */
    public char letterForPosition(int position) {
        if (position < 0 || position >= mPositionLetters.length) return '\0';
        return mPositionLetters[position];
    }

    /**
     * The current scrub, for cells bound while one is running.
     *
     * <p><b>Deliberately does not notify.</b> The per-frame writer is
     * {@code AppDrawerContentView}'s walk over the attached children; a notification here would
     * rebind 24-36 cells — rendered icons included — on every frame of a scrub. This exists for the
     * other write path: the auto-scroll binds fresh cells continuously while a finger runs down the
     * alphabet, and without the rule applied at the end of {@link #onBindViewHolder} every one of
     * them would arrive at full opacity and flash for a frame before the walk caught it.
     *
     * @param letter   the letter under the finger, or null when there is no scrub
     * @param strength 0 = no scrub, 1 = fully dimmed
     */
    public void setScrubState(@Nullable Character letter, float strength) {
        mScrubLetter = letter == null ? '\0' : letter;
        mScrubStrength = strength;
    }

    /**
     * Cell geometry. Re-binding every cell is correct rather than lazy: the icon is rendered at a
     * pixel size that has just changed, so every holder is stale.
     */
    public void setMetrics(@Nullable AppDrawerGridMetrics metrics) {
        mMetrics = metrics;
        notifyDataSetChanged();
    }

    @Nullable
    public AppDrawerGridMetrics getMetrics() {
        return mMetrics;
    }

    @Nullable
    public LauncherAppEntry entryAt(int position) {
        return position >= 0 && position < mItems.size() ? mItems.get(position).app : null;
    }

    @NonNull
    public List<LauncherAppEntry> entries() {
        return mEntries;
    }

    @Nullable public AppDrawerItem itemAt(int position) {
        return position >= 0 && position < mItems.size() ? mItems.get(position) : null;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    /**
     * Switches the categories search presentation: ranked results as full-width rows with a
     * category line, instead of grid cells. A distinct view type, so pooled grid holders and row
     * holders can never be exchanged for one another.
     */
    public void setSearchRowPresentation(boolean rows, @Nullable CategoryLabelLookup lookup) {
        if (mSearchRowPresentation == rows && mCategoryLookup == lookup) return;
        mSearchRowPresentation = rows;
        mCategoryLookup = lookup;
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        if (mItems.get(position).kind == AppDrawerItem.Kind.FOLDER) return VIEW_TYPE_FOLDER;
        return mSearchRowPresentation ? VIEW_TYPE_SEARCH_ROW : VIEW_TYPE_APP;
    }

    @NonNull
    @Override
    public Cell onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AppDrawerAppCellView root;
        switch (viewType) {
            case VIEW_TYPE_FOLDER:
                root = new AppDrawerFolderCellView(parent.getContext());
                break;
            case VIEW_TYPE_SEARCH_ROW:
                root = new AppDrawerSearchResultRowView(parent.getContext());
                break;
            default:
                root = new AppDrawerAppCellView(parent.getContext());
                break;
        }
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (viewType == VIEW_TYPE_SEARCH_ROW) {
            float density = parent.getResources().getDisplayMetrics().density;
            params.leftMargin = Math.round(12f * density);
            params.rightMargin = params.leftMargin;
            params.bottomMargin = Math.round(6f * density);
        }
        root.setLayoutParams(params);
        return new Cell(root);
    }

    @Override
    public void onBindViewHolder(@NonNull Cell holder, int position) {
        AppDrawerItem item = mItems.get(position);
        AppDrawerGridMetrics metrics = mMetrics;
        if (item.kind == AppDrawerItem.Kind.FOLDER)
            ((AppDrawerFolderCellView) holder.cell).bindFolder(mDock, item.folder, metrics, mClickGate);
        else holder.cell.bind(mDock, item.app, metrics, mClickGate,
            mPickupEnabled ? mDragController : null);
        if (mPickupEnabled && mDragController != null) mDragController.bindTarget(holder.cell, item);
        if (holder.cell instanceof AppDrawerSearchResultRowView && item.app != null) {
            CategoryLabelLookup lookup = mCategoryLookup;
            ((AppDrawerSearchResultRowView) holder.cell).setCategoryLabel(
                lookup == null ? null : lookup.categoryLabelFor(item.app));
        }

        // Last, and not optional. A cell the auto-scroll binds mid-scrub has to arrive already
        // dimmed; a cell bound with no scrub in progress is set to exactly 1 and 1, which is what
        // makes a holder taken from the pool at 0.28 alpha come back clean.
        applyScrubHighlight(holder, position);
    }

    private void applyScrubHighlight(@NonNull Cell holder, int position) {
        char letter = letterForPosition(position);
        holder.cell.setScrubAppearance(letter, mScrubLetter, mScrubStrength);
    }

    @Override
    public void onViewRecycled(@NonNull Cell holder) {
        super.onViewRecycled(holder);
        // A recycled cell must not keep a rendered icon alive, and must not answer a long press for
        // the app it used to show — the dock's gesture state is keyed by the view instance.
        holder.cell.unbind();
    }

    /** Icon over label. Held rather than looked up, so binding costs no {@code findViewById}. */
    public static final class Cell extends RecyclerView.ViewHolder {

        @NonNull public final ImageView icon;
        @NonNull public final TextView label;
        @NonNull final AppDrawerAppCellView cell;

        Cell(@NonNull AppDrawerAppCellView cell) {
            super(cell);
            this.cell = cell;
            this.icon = cell.icon;
            this.label = cell.label;
        }
    }
}
