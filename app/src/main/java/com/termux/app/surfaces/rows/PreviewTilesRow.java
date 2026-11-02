package com.termux.app.surfaces.rows;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A grid of preview tiles (editor-redesign §2, appearance-cards overhaul §4: the Presets, clock
 * faces, dock style and type, cursor trails, screen effects): tiles each a live preview over its
 * name, tapped to choose, with no popup. The tiles wrap onto as many lines as they need, three to
 * five to a line as the width and their names allow; nothing scrolls sideways. The chosen tile is
 * filled with the primary (its name on-primary) with 22dp corners; the rest stand on the
 * surface-container-high with 12dp corners. No outline, no check mark.
 */
public final class PreviewTilesRow {

    /** A tile was tapped. */
    public interface OnPick {
        void onPick(int index);
    }

    /** One tile: its name, and the view that previews it (or none, for the name alone). */
    public static final class Tile {
        @NonNull public final CharSequence name;
        @Nullable public final View preview;

        public Tile(@NonNull CharSequence name, @Nullable View preview) {
            this.name = name;
            this.preview = preview;
        }
    }

    /** A tile's least width: five stand on a phone's card (the overhaul's five Presets). */
    static final int TILE_MIN_DP = 64;
    /** The most tiles to a line. */
    public static final int MAX_COLUMNS = 5;
    static final int PREVIEW_DP = 32;
    static final int GAP_DP = 8;
    static final int CORNER_DP = 12;
    static final int CHOSEN_CORNER_DP = 22;
    static final int PAD_DP = 6;

    @NonNull public final ViewGroup root;
    @NonNull public final List<LinearLayout> tiles;
    @NonNull public final List<TextView> names;
    private int mSelected = -1;

    private PreviewTilesRow(@NonNull ViewGroup root, @NonNull List<LinearLayout> tiles,
                            @NonNull List<TextView> names) {
        this.root = root;
        this.tiles = Collections.unmodifiableList(tiles);
        this.names = Collections.unmodifiableList(names);
    }

    /** A tile's width: its caption's with the padding and slack, never under {@code minPx}. */
    public static int tileWidth(int minPx, int captionPx, int padPx, int slackPx) {
        return Math.max(minPx, captionPx + 2 * padPx + slackPx);
    }

    /**
     * How many tiles of at least {@code tilePx} stand on a line of {@code widthPx} with
     * {@code gapPx} between them: never more than {@code count} or {@link #MAX_COLUMNS}, never
     * fewer than one.
     */
    public static int columns(int widthPx, int tilePx, int gapPx, int count) {
        int fit = Math.max(1, (widthPx + gapPx) / Math.max(1, tilePx + gapPx));
        return Math.max(1, Math.min(fit, Math.min(Math.max(1, count), MAX_COLUMNS)));
    }

    /**
     * The picker row: the row's name on one line (ellipsized), over this grid of options. The
     * Dock's style and design, the clock faces, the cursor trails and the effects wear it.
     */
    @NonNull
    public LinearLayout picker(@NonNull Context context, @NonNull CharSequence name) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView title = RowKit.title(context, name);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        title.setPadding(edge, RowKit.dp(context, 8), edge, 0);
        column.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (root.getParent() instanceof ViewGroup)
            ((ViewGroup) root.getParent()).removeView(root);
        column.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    /** Builds the grid with {@code selected} chosen. */
    @NonNull
    public static PreviewTilesRow build(@NonNull Context context, @NonNull List<Tile> tiles,
                                        int selected, @NonNull OnPick onPick) {
        List<LinearLayout> views = new ArrayList<>(tiles.size());
        List<TextView> names = new ArrayList<>(tiles.size());
        int pad = RowKit.dp(context, PAD_DP);
        int least = RowKit.dp(context, TILE_MIN_DP);
        for (Tile tile : tiles) {
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(pad, pad, pad, pad);
            box.setMinimumHeight(RowKit.dp(context, RowKit.TOUCH_DP));
            box.setClickable(true);
            box.setFocusable(true);
            box.setContentDescription(tile.name);
            GlyphNames.name(box, tile.name);
            FrameLayout preview = new FrameLayout(context);
            if (tile.preview != null) {
                if (tile.preview.getParent() instanceof ViewGroup)
                    ((ViewGroup) tile.preview.getParent()).removeView(tile.preview);
                tile.preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                preview.addView(tile.preview, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, RowKit.dp(context, PREVIEW_DP)));
            TextView name = RowKit.caption(context, tile.name);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = RowKit.dp(context, 4);
            box.addView(name, nameParams);
            // Every tile at least as wide as the widest name needs, so no name is cut.
            least = Math.max(least, tileWidth(least,
                (int) Math.ceil(name.getPaint().measureText(tile.name.toString())), pad,
                RowKit.dp(context, 4)));
            views.add(box);
            names.add(name);
        }
        Grid grid = new Grid(context, least, RowKit.dp(context, GAP_DP),
            context.getResources().getDisplayMetrics().widthPixels);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        grid.setPadding(edge, RowKit.dp(context, 4), edge, RowKit.dp(context, 8));
        for (LinearLayout box : views)
            grid.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        PreviewTilesRow row = new PreviewTilesRow(grid, views, names);
        for (int i = 0; i < views.size(); i++)
            row.style(context, i, false);
        row.select(selected);
        for (int i = 0; i < views.size(); i++) {
            final int index = i;
            views.get(i).setOnClickListener(v -> {
                if (row.mSelected == index)
                    return;
                row.select(index);
                onPick.onPick(index);
            });
        }
        return row;
    }

    /** Shows {@code index} chosen without reporting it. */
    public void select(int index) {
        if (index == mSelected)
            return;
        int previous = mSelected;
        mSelected = index >= 0 && index < tiles.size() ? index : -1;
        Context context = root.getContext();
        if (previous >= 0)
            style(context, previous, false);
        if (mSelected >= 0)
            style(context, mSelected, true);
    }

    public int selected() {
        return mSelected;
    }

    private void style(@NonNull Context context, int index, boolean on) {
        LinearLayout tile = tiles.get(index);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(RowKit.dp(context, on ? CHOSEN_CORNER_DP : CORNER_DP));
        shape.setColor(RowKit.color(context, on
            ? androidx.appcompat.R.attr.colorPrimary
            : com.google.android.material.R.attr.colorSurfaceContainerHigh));
        tile.setBackground(shape);
        tile.setSelected(on);
        names.get(index).setTextColor(RowKit.color(context, on
            ? com.google.android.material.R.attr.colorOnPrimary
            : com.google.android.material.R.attr.colorOnSurface));
    }

    /**
     * The tiles' grid: as many equal columns as fit tiles {@code leastPx} wide (at most
     * {@link #MAX_COLUMNS}), {@code gap} apart, every tile as tall as the tallest so a line never
     * reads ragged. As wide as its parent gives it (the screen's width when unbounded).
     */
    static final class Grid extends ViewGroup {
        private final int mLeast;
        private final int mGap;
        private final int mFallbackWidth;
        private int mColumns = 1;
        private int mTileWidth;
        private int mTileHeight;

        Grid(@NonNull Context context, int leastPx, int gapPx, int fallbackWidthPx) {
            super(context);
            mLeast = leastPx;
            mGap = gapPx;
            mFallbackWidth = fallbackWidthPx;
        }

        int columns() {
            return mColumns;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? mFallbackWidth : MeasureSpec.getSize(widthMeasureSpec);
            int room = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            int count = 0;
            for (int i = 0; i < getChildCount(); i++) {
                if (getChildAt(i).getVisibility() != GONE)
                    count++;
            }
            mColumns = PreviewTilesRow.columns(room, mLeast, mGap, count);
            mTileWidth = Math.max(0, (room - mGap * (mColumns - 1)) / mColumns);
            int across = MeasureSpec.makeMeasureSpec(mTileWidth, MeasureSpec.EXACTLY);
            int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int tallest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE)
                    continue;
                child.measure(across, free);
                tallest = Math.max(tallest, child.getMeasuredHeight());
            }
            int down = MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE && child.getMeasuredHeight() != tallest)
                    child.measure(across, down);
            }
            mTileHeight = tallest;
            int lines = (count + mColumns - 1) / mColumns;
            int height = getPaddingTop() + getPaddingBottom()
                + (lines == 0 ? 0 : lines * tallest + (lines - 1) * mGap);
            setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int slot = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE)
                    continue;
                int column = slot % mColumns;
                int line = slot / mColumns;
                int start = column * (mTileWidth + mGap);
                int left = rtl ? (r - l) - getPaddingRight() - start - mTileWidth
                    : getPaddingLeft() + start;
                int top = getPaddingTop() + line * (mTileHeight + mGap);
                child.layout(left, top, left + child.getMeasuredWidth(),
                    top + child.getMeasuredHeight());
                slot++;
            }
        }
    }
}
