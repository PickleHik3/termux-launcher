package com.termux.app.surfaces.rows;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A row of preview tiles (editor-redesign §2: the clock faces, the cursor trails, the screen
 * effects): small tiles side by side, each a live preview over its name, tapped to choose, with
 * no popup. The chosen tile wears the primary outline on the secondary container. The row
 * scrolls sideways when the tiles outrun the card.
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

    static final int TILE_MIN_DP = 64;
    static final int PREVIEW_DP = 32;
    static final int GAP_DP = 8;
    static final int CORNER_DP = 14;
    static final int PAD_DP = 6;

    @NonNull public final HorizontalScrollView root;
    @NonNull public final List<LinearLayout> tiles;
    @NonNull public final List<TextView> names;
    private int mSelected = -1;

    private PreviewTilesRow(@NonNull HorizontalScrollView root, @NonNull List<LinearLayout> tiles,
                            @NonNull List<TextView> names) {
        this.root = root;
        this.tiles = Collections.unmodifiableList(tiles);
        this.names = Collections.unmodifiableList(names);
    }

    /** Builds the row with {@code selected} chosen. */
    @NonNull
    public static PreviewTilesRow build(@NonNull Context context, @NonNull List<Tile> tiles,
                                        int selected, @NonNull OnPick onPick) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        strip.setPadding(edge, RowKit.dp(context, 4), edge, RowKit.dp(context, 8));
        scroll.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        List<LinearLayout> views = new ArrayList<>(tiles.size());
        List<TextView> names = new ArrayList<>(tiles.size());
        int pad = RowKit.dp(context, PAD_DP);
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(pad, pad, pad, pad);
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
            // As wide as its name needs, never under the tile's least width.
            int width = Math.max(RowKit.dp(context, TILE_MIN_DP),
                (int) Math.ceil(name.getPaint().measureText(tile.name.toString())) + 2 * pad
                    + RowKit.dp(context, 4));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0)
                params.setMarginStart(RowKit.dp(context, GAP_DP));
            strip.addView(box, params);
            views.add(box);
            names.add(name);
        }
        PreviewTilesRow row = new PreviewTilesRow(scroll, views, names);
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
        shape.setCornerRadius(RowKit.dp(context, CORNER_DP));
        if (on) {
            shape.setColor(RowKit.color(context,
                com.google.android.material.R.attr.colorSecondaryContainer));
            shape.setStroke(RowKit.dp(context, 2), RowKit.color(context,
                androidx.appcompat.R.attr.colorPrimary));
        } else {
            shape.setColor(0);
            shape.setStroke(Math.max(1, RowKit.dp(context, 1)), RowKit.color(context,
                com.google.android.material.R.attr.colorOutlineVariant));
        }
        tile.setBackground(shape);
        tile.setSelected(on);
        names.get(index).setTextColor(RowKit.color(context, on
            ? androidx.appcompat.R.attr.colorPrimary
            : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }
}
