package com.termux.app.launcher.drawer;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    private static final char[] NO_LETTERS = new char[0];

    @Nullable private final SuggestionBarView mDock;
    @NonNull private List<LauncherAppEntry> mEntries = new ArrayList<>();
    /**
     * The section index's per-position letters, cached parallel to {@link #mEntries}. The scrub's
     * per-frame walk goes from an attached child's adapter position to its letter through this array
     * rather than through an entry lookup and a normalisation, sixty times a second per visible cell.
     */
    @NonNull private char[] mPositionLetters = NO_LETTERS;
    @Nullable private AppDrawerGridMetrics mMetrics;
    /** The letter under the finger, or 0. Written every frame of a scrub and never notified on. */
    private char mScrubLetter = '\0';
    private float mScrubStrength;

    public AppDrawerAppsAdapter(@Nullable SuggestionBarView dock) {
        mDock = dock;
        // Positions are the ranked list's own order and change wholesale on every query, so stable
        // ids would claim a continuity that does not exist.
        setHasStableIds(false);
    }

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
        return position >= 0 && position < mEntries.size() ? mEntries.get(position) : null;
    }

    @NonNull
    public List<LauncherAppEntry> entries() {
        return mEntries;
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

    @NonNull
    @Override
    public Cell onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout root = new LinearLayout(parent.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setClickable(true);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(parent.getContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAdjustViewBounds(true);
        icon.setPadding(0, 0, 0, 0);
        icon.setDuplicateParentStateEnabled(true);
        root.addView(icon, new LinearLayout.LayoutParams(0, 0));

        TextView label = new TextView(parent.getContext());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP);
        label.setSingleLine(true);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setIncludeFontPadding(false);
        root.addView(label, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new Cell(root, icon, label);
    }

    @Override
    public void onBindViewHolder(@NonNull Cell holder, int position) {
        LauncherAppEntry entry = mEntries.get(position);
        AppDrawerGridMetrics metrics = mMetrics;
        int iconPx = metrics != null ? Math.max(1, Math.round(metrics.iconPx)) : 0;
        applyGeometry(holder, metrics, iconPx);

        Drawable artwork = mDock != null && iconPx > 0 ? mDock.getRenderedIcon(entry, iconPx) : null;
        holder.icon.setImageDrawable(artwork != null ? artwork : entry.icon);
        if (mDock != null) mDock.applyIconColorFilter(holder.icon);
        holder.icon.setContentDescription(entry.label);

        holder.label.setText(entry.label);
        holder.label.setTextColor(mDock != null ? mDock.getLauncherTextColor() : Color.WHITE);

        holder.itemView.setContentDescription(entry.label);
        holder.itemView.setOnClickListener(view -> {
            // The icon, not the cell, is the launch source: the dock's ripple and launch transition
            // read their artwork off an ImageView and fall back to a plain fade without one.
            if (mDock != null) mDock.launchEntryFromDrawer(holder.icon, entry);
        });
        if (mDock != null) mDock.bindDrawerAppContextLongPress(holder.itemView, entry);

        // Last, and not optional. A cell the auto-scroll binds mid-scrub has to arrive already
        // dimmed; a cell bound with no scrub in progress is set to exactly 1 and 1, which is what
        // makes a holder taken from the pool at 0.28 alpha come back clean.
        applyScrubHighlight(holder, position);
    }

    private void applyScrubHighlight(@NonNull Cell holder, int position) {
        char letter = letterForPosition(position);
        holder.itemView.setAlpha(
            AppDrawerScrubHighlight.alphaFor(letter, mScrubLetter, mScrubStrength));
        float scale = AppDrawerScrubHighlight.scaleFor(letter, mScrubLetter, mScrubStrength);
        holder.itemView.setScaleX(scale);
        holder.itemView.setScaleY(scale);
    }

    private void applyGeometry(@NonNull Cell holder, @Nullable AppDrawerGridMetrics metrics,
                               int iconPx) {
        if (metrics == null) return;
        ViewGroup.LayoutParams cellParams = holder.itemView.getLayoutParams();
        int rowHeight = Math.max(1, Math.round(metrics.rowHeightPx));
        if (cellParams != null && cellParams.height != rowHeight) {
            cellParams.height = rowHeight;
            holder.itemView.setLayoutParams(cellParams);
        }
        ViewGroup.LayoutParams iconParams = holder.icon.getLayoutParams();
        if (iconParams != null && (iconParams.width != iconPx || iconParams.height != iconPx)) {
            iconParams.width = iconPx;
            iconParams.height = iconPx;
            holder.icon.setLayoutParams(iconParams);
        }
        // The gap between icon and label is the metrics' own, applied as label padding so the two
        // views stay flush and the cell's height keeps matching what the metrics computed.
        int gap = Math.round(AppDrawerGridMetrics.LABEL_GAP_DP
            * holder.itemView.getResources().getDisplayMetrics().density);
        holder.label.setPadding(0, gap, 0, 0);
    }

    @Override
    public void onViewRecycled(@NonNull Cell holder) {
        super.onViewRecycled(holder);
        // A recycled cell must not keep a rendered icon alive, and must not answer a long press for
        // the app it used to show — the dock's gesture state is keyed by the view instance.
        holder.itemView.cancelLongPress();
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
        holder.itemView.setOnTouchListener(null);
        holder.itemView.setLongClickable(false);
        holder.icon.setImageDrawable(null);
        // A holder that left the screen part way through a scrub goes back to the pool at 0.28
        // alpha, and the next position to reuse it would be a permanently dim cell — silent until
        // the drawer is closed and reopened.
        holder.itemView.setAlpha(1f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
    }

    /** Icon over label. Held rather than looked up, so binding costs no {@code findViewById}. */
    public static final class Cell extends RecyclerView.ViewHolder {

        @NonNull public final ImageView icon;
        @NonNull public final TextView label;

        Cell(@NonNull View itemView, @NonNull ImageView icon, @NonNull TextView label) {
            super(itemView);
            this.icon = icon;
            this.label = label;
        }
    }
}
