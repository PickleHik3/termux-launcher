package com.termux.app.barsearch;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The bar search's panel in the SHEETS layer: the query in a field at its top, as the prototype's,
 * over its rows (sections in order, the highlighted one marked, each saying at its end what a tap
 * does). The layer's view covers only what the bar and the
 * keyboards leave free, so taps on the keyboard reach it and a rail keeps its side; a tap on the
 * free part outside the panel puts it away. The rows are recycled and their icons come from the
 * launcher's icon store off the main thread, so only the rows in view hold any artwork.
 */
final class BarSearchView extends FrameLayout {

    private static final float WIDTH_DP = 360f;
    private static final float MAX_LIST_DP = 380f;
    private static final float GAP_DP = 8f;
    private static final float ROW_DP = 48f;
    /** The search field's height at the top of the sheet. */
    private static final float FIELD_DP = 44f;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;
    private static final ExecutorService ICONS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bar-search-icons");
        thread.setDaemon(true);
        return thread;
    });

    /** One listed line: a section heading, or a row of the search. */
    private static final class Item {
        @Nullable final SearchSections.Section heading;
        @Nullable final SearchSections.Row row;
        final int rowIndex;

        Item(@Nullable SearchSections.Section heading, @Nullable SearchSections.Row row, int rowIndex) {
            this.heading = heading;
            this.row = row;
            this.rowIndex = rowIndex;
        }
    }

    @NonNull private final BarSearch mSearch;
    @NonNull private final BarSearch.Host mHost;
    private final float mDensity;
    @NonNull private final LinearLayout mCard;
    @NonNull private final RecyclerView mList;
    @NonNull private final TextView mQueryLine;
    @NonNull private final List<Item> mItems = new ArrayList<>();
    @NonNull private final Adapter mAdapter = new Adapter();
    @NonNull private final LinearLayoutManager mManager;
    @Nullable private View mBar;
    private int mMaxListPx;
    private int mAppliedLeft = -1;
    private int mAppliedRight = -1;
    private int mAppliedBottom = -1;
    private boolean mFitPosted;
    private int mHighlightedItem = -1;
    /** Follows the bar and the keyboards once each layout settles (set only on change, in fit()). */
    @NonNull private final ViewTreeObserver.OnGlobalLayoutListener mFollow = this::fit;

    BarSearchView(@NonNull Context context, @NonNull BarSearch search, @NonNull BarSearch.Host host) {
        super(context);
        mSearch = search;
        mHost = host;
        mDensity = context.getResources().getDisplayMetrics().density;
        setOnClickListener(v -> mSearch.close());
        setContentDescription(context.getString(R.string.bar_search_description));
        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        mCard.setClickable(true);
        int pad = Math.round(8f * mDensity);
        mCard.setPadding(pad, pad, pad, pad);
        mManager = new LinearLayoutManager(context);
        mList = new RecyclerView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                // The list takes its whole room whatever it holds, rows filling from the top: the
                // card keeps one height while open, so the field at its top never moves as the
                // results change under typing (round 5 review P2-2).
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(mMaxListPx, MeasureSpec.EXACTLY));
            }
        };
        mList.setLayoutManager(mManager);
        mList.setAdapter(mAdapter);
        mList.setItemAnimator(null);
        mList.setOverScrollMode(OVER_SCROLL_NEVER);
        // The query in a field at the top of the sheet (the prototype's): a pill of the ink's wash
        // with the search glyph, the typed text or the hint in it.
        mQueryLine = new TextView(context);
        mQueryLine.setTextSize(16f);
        mQueryLine.setTextColor(host.ink());
        mQueryLine.setSingleLine(true);
        mQueryLine.setEllipsize(android.text.TextUtils.TruncateAt.START);
        int field = Math.round(FIELD_DP * mDensity);
        mQueryLine.setMinHeight(field);
        mQueryLine.setGravity(Gravity.CENTER_VERTICAL);
        int fieldPad = Math.round(14f * mDensity);
        mQueryLine.setPaddingRelative(fieldPad, 0, fieldPad, 0);
        GradientDrawable fieldGround = new GradientDrawable();
        fieldGround.setColor(ColorUtils.setAlphaComponent(host.ink(), 20));
        fieldGround.setCornerRadius(field / 2f);
        mQueryLine.setBackground(fieldGround);
        Drawable glyph = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_ms_search);
        if (glyph != null) {
            glyph = glyph.mutate();
            int size = Math.round(18f * mDensity);
            glyph.setBounds(0, 0, size, size);
            glyph.setTint(ColorUtils.setAlphaComponent(host.ink(), 170));
            mQueryLine.setCompoundDrawablesRelative(glyph, null, null, null);
            mQueryLine.setCompoundDrawablePadding(Math.round(10f * mDensity));
        }
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.bottomMargin = Math.round(6f * mDensity);
        mCard.addView(mQueryLine, fieldParams);
        mCard.addView(mList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LayoutParams cardParams = new LayoutParams(Math.round(WIDTH_DP * mDensity),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cardParams.bottomMargin = Math.round(GAP_DP * mDensity);
        addView(mCard, cardParams);
    }

    /** Shows the panel in what the bar and the keyboards leave free; false when there is no sheets layer yet. */
    boolean show(@NonNull View bar) {
        ViewGroup host = WindowLayers.host(id -> bar.getRootView().findViewById(id), WindowLayer.SHEETS);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        mBar = bar;
        int[] free = free(host);
        mAppliedLeft = free[0];
        mAppliedRight = free[1];
        mAppliedBottom = free[2];
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(free[1] - free[0], free[2]);
        params.leftMargin = free[0];
        host.addView(this, params);
        getViewTreeObserver().addOnGlobalLayoutListener(mFollow);
        post(this::dress);
        return true;
    }

    void dismiss() {
        getViewTreeObserver().removeOnGlobalLayoutListener(mFollow);
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent != null) parent.removeView(this);
    }

    /**
     * What is free in {@code host}: {left, right, bottom}. Above the bar in a row (and above the
     * keyboard and the extra keys when they stand over it); beside the bar for a rail, whose side
     * the panel keeps clear of.
     */
    @NonNull
    private int[] free(@NonNull ViewGroup host) {
        View bar = mBar;
        int gap = Math.round(GAP_DP * mDensity);
        int[] at = new int[2];
        int[] hostAt = new int[2];
        host.getLocationInWindow(hostAt);
        int left = 0;
        int right = host.getWidth();
        int bottom = host.getHeight();
        if (bar != null && bar.isShown()) {
            bar.getLocationInWindow(at);
            int barLeft = at[0] - hostAt[0];
            int barTop = at[1] - hostAt[1];
            if (bar.getWidth() < host.getWidth() / 2) {
                // A rail: the panel stands beside it.
                if (barLeft + bar.getWidth() / 2 < host.getWidth() / 2) left = barLeft + bar.getWidth() + gap;
                else right = barLeft - gap;
            } else {
                bottom = Math.min(bottom, barTop);
            }
        }
        View root = host.getRootView();
        for (int id : new int[] {R.id.inapp_keyboard_container, R.id.terminal_toolbar_host}) {
            View keys = root.findViewById(id);
            if (keys == null || !keys.isShown() || keys.getHeight() <= 0) continue;
            keys.getLocationInWindow(at);
            bottom = Math.min(bottom, at[1] - hostAt[1]);
        }
        left = Math.max(0, left);
        right = Math.max(left + 1, right);
        bottom = Math.max(1, bottom);
        // The list takes what the panel's field (and its gap) and padding leave.
        int rest = bottom - 2 * Math.round(GAP_DP * mDensity) - Math.round((FIELD_DP + 6f) * mDensity)
            - Math.round(2 * GAP_DP * mDensity);
        int maxList = Math.round(MAX_LIST_DP * mDensity);
        int wanted = Math.max(Math.round(ROW_DP * mDensity), Math.min(maxList, rest));
        if (wanted != mMaxListPx) {
            mMaxListPx = wanted;
            mList.requestLayout();
        }
        return new int[] {left, right, bottom};
    }

    /** Follows the bar and the keyboards: sets the layer view's place only when it changed, never in the pass. */
    private void fit() {
        if (mFitPosted || !(getParent() instanceof ViewGroup)) return;
        int[] free = free((ViewGroup) getParent());
        if (free[0] == mAppliedLeft && free[1] == mAppliedRight && free[2] == mAppliedBottom) return;
        mFitPosted = true;
        post(() -> {
            mFitPosted = false;
            if (!(getParent() instanceof ViewGroup) || !(getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
            int[] now = free((ViewGroup) getParent());
            mAppliedLeft = now[0];
            mAppliedRight = now[1];
            mAppliedBottom = now[2];
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
            params.width = now[1] - now[0];
            params.height = now[2];
            params.leftMargin = now[0];
            setLayoutParams(params);
        });
    }

    private void dress() {
        if (mCard.getWidth() <= 0 || mCard.getHeight() <= 0 || getParent() == null) return;
        float radius = mHost.cornerRadiusPx();
        Drawable glass = mHost.glass(radius, mCard);
        if (glass == null) {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(radius);
            plain.setColor(ColorUtils.setAlphaComponent(0xFF1B1B1F, 0xE6));
            glass = plain;
        }
        mCard.setBackground(glass);
    }

    /** The field's top in the window, for tests: it stands still while the results change. */
    int fieldTopInWindow() {
        int[] at = new int[2];
        mQueryLine.getLocationInWindow(at);
        return at[1];
    }

    /** Draws the rows and the query line for the search's present state. */
    void showRows() {
        mItems.clear();
        SearchSections.Section last = null;
        int index = 0;
        for (SearchSections.Row row : mSearch.rows()) {
            if (row.section != last && row.section != SearchSections.Section.ALL_APPS
                && row.section != SearchSections.Section.EDIT_LOOK) {
                mItems.add(new Item(row.section, null, -1));
            }
            last = row.section;
            mItems.add(new Item(null, row, index++));
        }
        mAdapter.notifyDataSetChanged();
        mHighlightedItem = itemOfRow(mSearch.highlight());
        if (mHighlightedItem >= 0) mManager.scrollToPositionWithOffset(Math.max(0, mHighlightedItem - 1), 0);
        String query = mSearch.query();
        mQueryLine.setText(query.isEmpty() ? getContext().getString(R.string.bar_search_hint) : query);
        mQueryLine.setAlpha(query.isEmpty() ? 0.6f : 1f);
    }

    /** The highlight moved: only the two rows concerned are redrawn. */
    void showHighlight() {
        int old = mHighlightedItem;
        mHighlightedItem = itemOfRow(mSearch.highlight());
        if (old >= 0 && old < mItems.size()) mAdapter.notifyItemChanged(old);
        if (mHighlightedItem >= 0) {
            mAdapter.notifyItemChanged(mHighlightedItem);
            mManager.scrollToPosition(mHighlightedItem);
        }
    }

    private int itemOfRow(int rowIndex) {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).rowIndex == rowIndex) return i;
        return -1;
    }

    private void offerNewWindow(@NonNull View anchor, @NonNull SearchSections.Row row,
                                @NonNull SearchOpen.Action action) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add(R.string.bar_search_new_window_here);
        menu.setOnMenuItemClickListener(item -> {
            mSearch.performHold(row, action);
            return true;
        });
        menu.show();
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        @Nullable final ImageView icon;
        @NonNull final TextView label;
        /** What a tap on the row does, at its end; null on a heading. */
        @Nullable final TextView hint;
        /** The entry whose icon is being loaded into this holder, to drop a load that arrives late. */
        @Nullable LauncherAppEntry wanted;

        Holder(@NonNull View view, @Nullable ImageView icon, @NonNull TextView label,
               @Nullable TextView hint) {
            super(view);
            this.icon = icon;
            this.label = label;
            this.hint = hint;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public int getItemCount() {
            return mItems.size();
        }

        @Override public int getItemViewType(int position) {
            return mItems.get(position).heading != null ? TYPE_HEADER : TYPE_ROW;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context context = parent.getContext();
            int pad = Math.round(8f * mDensity);
            if (type == TYPE_HEADER) {
                TextView view = new TextView(context);
                view.setTextSize(12f);
                view.setPadding(pad, pad, pad, Math.round(2f * mDensity));
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
                return new Holder(view, null, view, null);
            }
            LinearLayout view = new LinearLayout(context);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(pad, 0, pad, 0);
            view.setMinimumHeight(Math.round(ROW_DP * mDensity));
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            int size = Math.round(32f * mDensity);
            ImageView icon = new ImageView(context);
            view.addView(icon, new LinearLayout.LayoutParams(size, size));
            TextView label = new TextView(context);
            label.setTextSize(16f);
            label.setSingleLine(true);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginStart(Math.round(12f * mDensity));
            view.addView(label, labelParams);
            TextView hint = new TextView(context);
            hint.setTextSize(13f);
            hint.setSingleLine(true);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.setMarginStart(Math.round(8f * mDensity));
            view.addView(hint, hintParams);
            return new Holder(view, icon, label, hint);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            int ink = mHost.ink();
            Item item = mItems.get(position);
            if (item.heading != null) {
                holder.label.setText(item.heading == SearchSections.Section.TERMINAL
                    ? R.string.bar_search_section_terminal
                    : item.heading == SearchSections.Section.LINUX ? R.string.bar_search_section_linux
                    : R.string.bar_search_section_android);
                holder.label.setTextColor(ColorUtils.setAlphaComponent(ink, 160));
                return;
            }
            SearchSections.Row row = item.row;
            if (row == null || holder.icon == null) return;
            holder.label.setTextColor(ink);
            bindHint(holder, mSearch.hint(row), ink);
            holder.itemView.setBackgroundColor(item.rowIndex == mSearch.highlight()
                ? ColorUtils.setAlphaComponent(ink, 40) : 0);
            holder.wanted = row.entry;
            if (row.section == SearchSections.Section.EDIT_LOOK) {
                holder.label.setText(R.string.bar_search_edit_look);
                holder.icon.setImageResource(R.drawable.ic_ms_palette);
                holder.icon.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN);
            } else if (row.entry == null) {
                holder.label.setText(R.string.bar_search_all_apps);
                holder.icon.setImageResource(R.drawable.ic_ms_apps);
                holder.icon.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                holder.label.setText(row.entry.label);
                holder.icon.clearColorFilter();
                holder.icon.setImageDrawable(null);
                loadIcon(holder, row.entry);
            }
            holder.itemView.setOnClickListener(v -> mSearch.choose(row));
            SearchOpen.Action hold = mSearch.holdAction(row);
            holder.itemView.setOnLongClickListener(hold == SearchOpen.Action.NONE ? null : v -> {
                offerNewWindow(v, row, hold);
                return true;
            });
        }

        @Override public void onViewRecycled(@NonNull Holder holder) {
            // A row leaving the view keeps no artwork and no pending load.
            holder.wanted = null;
            if (holder.icon != null) holder.icon.setImageDrawable(null);
        }
    }

    /** The row's end says what its tap does; nothing for a plain launch. */
    private static void bindHint(@NonNull Holder holder, @NonNull SearchOpen.Hint hint, int ink) {
        TextView view = holder.hint;
        if (view == null) return;
        int text;
        switch (hint) {
            case OPEN_DRAWER: text = R.string.bar_search_hint_open_drawer; break;
            case NEW_WINDOW: text = R.string.bar_search_hint_new_window; break;
            case OPEN: text = R.string.bar_search_hint_open; break;
            default: text = 0;
        }
        if (text == 0) {
            view.setText(null);
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(text);
        view.setTextColor(ColorUtils.setAlphaComponent(ink, 170));
        view.setVisibility(View.VISIBLE);
    }

    /** The icon from the store off the main thread; it lands only if the row still shows {@code entry}. */
    private void loadIcon(@NonNull Holder holder, @NonNull LauncherAppEntry entry) {
        ICONS.execute(() -> {
            Drawable art = mHost.icon(entry);
            if (art == null) return;
            post(() -> {
                if (holder.wanted == entry && holder.icon != null) holder.icon.setImageDrawable(art);
            });
        });
    }
}
