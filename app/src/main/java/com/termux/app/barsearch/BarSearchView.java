package com.termux.app.barsearch;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

/**
 * The bar search's panel in the SHEETS layer: its rows (sections in order, the highlighted one
 * marked) over a line showing the query, standing above the control bar. A tap outside puts it away.
 */
final class BarSearchView extends FrameLayout {

    private static final float WIDTH_DP = 360f;
    private static final float MAX_HEIGHT_DP = 420f;
    private static final float GAP_DP = 8f;
    private static final float ROW_DP = 48f;

    @NonNull private final BarSearch mSearch;
    @NonNull private final BarSearch.Host mHost;
    private final float mDensity;
    @NonNull private final LinearLayout mCard;
    @NonNull private final LinearLayout mList;
    @NonNull private final ScrollView mScroll;
    @NonNull private final TextView mQueryLine;

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
        mList = new LinearLayout(context);
        mList.setOrientation(LinearLayout.VERTICAL);
        mScroll = new ScrollView(context);
        mScroll.addView(mList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mCard.addView(mScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mQueryLine = new TextView(context);
        mQueryLine.setTextSize(16f);
        mQueryLine.setTextColor(host.ink());
        mQueryLine.setMinHeight(Math.round(ROW_DP * mDensity));
        mQueryLine.setGravity(Gravity.CENTER_VERTICAL);
        mQueryLine.setPadding(pad, 0, pad, 0);
        mCard.addView(mQueryLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mCard, new LayoutParams(Math.round(WIDTH_DP * mDensity),
            ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Shows the panel above {@code bar}; false when there is no sheets layer yet. */
    boolean show(@NonNull View bar) {
        ViewGroup host = WindowLayers.host(id -> bar.getRootView().findViewById(id), WindowLayer.SHEETS);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        int[] bl = new int[2];
        int[] hl = new int[2];
        bar.getLocationInWindow(bl);
        host.getLocationInWindow(hl);
        int gap = Math.round(GAP_DP * mDensity);
        int width = Math.min(Math.round(WIDTH_DP * mDensity), host.getWidth() - 2 * gap);
        LayoutParams params = new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = Math.max(gap, host.getHeight() - (bl[1] - hl[1]) + gap);
        mCard.setLayoutParams(params);
        host.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        post(this::dress);
        return true;
    }

    void dismiss() {
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent != null) parent.removeView(this);
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

    /** Draws the rows and the query line for the search's present state. */
    void showRows() {
        Context context = getContext();
        int ink = mHost.ink();
        mList.removeAllViews();
        SearchSections.Section last = null;
        int index = 0;
        for (SearchSections.Row row : mSearch.rows()) {
            if (row.section != last && row.section != SearchSections.Section.ALL_APPS) {
                mList.addView(header(row.section, ink));
            }
            last = row.section;
            mList.addView(rowView(row, index == mSearch.highlight(), ink));
            index++;
        }
        mScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        String query = mSearch.query();
        mQueryLine.setText(query.isEmpty() ? context.getString(R.string.bar_search_hint) : query);
        mQueryLine.setAlpha(query.isEmpty() ? 0.6f : 1f);
        mList.post(this::capHeight);
    }

    private void capHeight() {
        int max = Math.round(MAX_HEIGHT_DP * mDensity);
        ViewGroup.LayoutParams params = mScroll.getLayoutParams();
        int wanted = mList.getHeight() > max ? max : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (params.height != wanted) {
            params.height = wanted;
            mScroll.setLayoutParams(params);
        }
    }

    private TextView header(@NonNull SearchSections.Section section, int ink) {
        TextView view = new TextView(getContext());
        view.setText(section == SearchSections.Section.TERMINAL ? R.string.bar_search_section_terminal
            : section == SearchSections.Section.LINUX ? R.string.bar_search_section_linux
            : R.string.bar_search_section_android);
        view.setTextSize(12f);
        view.setTextColor(ColorUtils.setAlphaComponent(ink, 160));
        int pad = Math.round(8f * mDensity);
        view.setPadding(pad, pad, pad, Math.round(2f * mDensity));
        return view;
    }

    private View rowView(@NonNull SearchSections.Row row, boolean highlighted, int ink) {
        Context context = getContext();
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(8f * mDensity);
        view.setPadding(pad, 0, pad, 0);
        view.setMinimumHeight(Math.round(ROW_DP * mDensity));
        if (highlighted) view.setBackgroundColor(ColorUtils.setAlphaComponent(ink, 40));
        int size = Math.round(32f * mDensity);
        ImageView icon = new ImageView(context);
        if (row.entry != null) icon.setImageDrawable(mHost.icon(row.entry));
        else {
            icon.setImageResource(R.drawable.ic_ms_apps);
            icon.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        view.addView(icon, new LinearLayout.LayoutParams(size, size));
        TextView label = new TextView(context);
        label.setText(row.entry == null ? context.getString(R.string.bar_search_all_apps) : row.entry.label);
        label.setTextColor(ink);
        label.setTextSize(16f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.setMarginStart(Math.round(12f * mDensity));
        view.addView(label, labelParams);
        view.setOnClickListener(v -> mSearch.choose(row));
        SearchOpen.Action hold = mSearch.holdAction(row);
        if (hold != SearchOpen.Action.NONE) {
            view.setOnLongClickListener(v -> {
                offerNewWindow(v, row, hold);
                return true;
            });
        }
        return view;
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
}
