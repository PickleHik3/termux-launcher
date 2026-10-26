package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A grid of toggle tiles (editor-redesign §2: Options, Indicators, Show wallpaper): each tile a
 * glyph over a word, on or off by itself, on the secondary container when on. The tiles wrap onto
 * as many lines as they need, as many to a line as fit their widest word (four at most), every
 * tile as wide as its line's share so the grid keeps its columns.
 */
public final class TileGrid {

    /** A tile was turned on or off by touch. */
    public interface OnToggle {
        void onToggle(int index, boolean on);
    }

    /** One tile: a glyph, its word, and what its long-press says (the word, unless a hint). */
    public static final class Tile {
        @DrawableRes public final int glyph;
        @NonNull public final CharSequence word;
        @Nullable public final CharSequence hint;

        public Tile(@DrawableRes int glyph, @NonNull CharSequence word,
                    @Nullable CharSequence hint) {
            this.glyph = glyph;
            this.word = word;
            this.hint = hint;
        }

        public Tile(@DrawableRes int glyph, @NonNull CharSequence word) {
            this(glyph, word, null);
        }
    }

    /** The most tiles to a line, as the review's drawings have them. */
    public static final int MAX_COLUMNS = 4;
    static final int GAP_DP = 8;
    static final int MIN_TILE_DP = 64;
    static final int PAD_H_DP = 4;
    static final int PAD_V_DP = 8;
    static final int ICON_GAP_DP = 4;
    static final int CORNER_DP = 16;

    @NonNull public final ViewGroup root;
    @NonNull public final List<MaterialButton> tiles;
    public final int columns;
    private boolean mSetting;

    private TileGrid(@NonNull ViewGroup root, @NonNull List<MaterialButton> tiles,
                     int columns) {
        this.root = root;
        this.tiles = Collections.unmodifiableList(tiles);
        this.columns = columns;
    }

    /** Builds the grid for a card {@code widthPx} wide; {@code on[i]} is tile i's state. */
    @NonNull
    public static TileGrid build(@NonNull Context context, @NonNull List<Tile> tiles,
                                 @NonNull boolean[] on, int widthPx, @NonNull OnToggle onToggle) {
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        int gap = RowKit.dp(context, GAP_DP);
        int padH = RowKit.dp(context, PAD_H_DP);
        int room = Math.max(1, widthPx - 2 * edge);
        List<MaterialButton> buttons = new ArrayList<>(tiles.size());
        float widestWord = 0f;
        for (Tile tile : tiles) {
            MaterialButton button = tile(context, tile);
            for (String word : tile.word.toString().split(" "))
                widestWord = Math.max(widestWord, button.getPaint().measureText(word));
            buttons.add(button);
        }
        int tileMin = Math.max(RowKit.dp(context, MIN_TILE_DP),
            (int) Math.ceil(widestWord) + 2 * padH + RowKit.dp(context, 4));
        int columns = columns(room, tileMin, gap, tiles.size(), MAX_COLUMNS);

        // Every tile as tall as the tallest, two lines of its word at most: the grid measures
        // them all and gives each that one height, so a row never reads ragged.
        Lines root = new Lines(context, columns, gap, widthPx,
            tileHeightPx(context, buttons.isEmpty() ? null : buttons.get(0), 1));
        root.setPadding(edge, RowKit.dp(context, 4), edge, RowKit.dp(context, 8));
        for (MaterialButton button : buttons)
            root.addView(button, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TileGrid grid = new TileGrid(root, buttons, columns);
        for (int i = 0; i < buttons.size(); i++)
            grid.setOn(i, i < on.length && on[i]);
        for (int i = 0; i < buttons.size(); i++) {
            final int index = i;
            buttons.get(i).addOnCheckedChangeListener((button, checked) -> {
                if (!grid.mSetting)
                    onToggle.onToggle(index, checked);
            });
        }
        return grid;
    }

    /** Shows tile {@code index} on or off without reporting it. */
    public void setOn(int index, boolean on) {
        if (index < 0 || index >= tiles.size())
            return;
        MaterialButton tile = tiles.get(index);
        if (tile.isChecked() == on)
            return;
        mSetting = true;
        try {
            tile.setChecked(on);
        } finally {
            mSetting = false;
        }
    }

    public boolean isOn(int index) {
        return index >= 0 && index < tiles.size() && tiles.get(index).isChecked();
    }

    /**
     * How many tiles of at least {@code tilePx} stand on a line of {@code widthPx} with
     * {@code gapPx} between them: never more than {@code count} or {@code maxColumns}, never
     * fewer than one.
     */
    static int columns(int widthPx, int tilePx, int gapPx, int count, int maxColumns) {
        int fit = Math.max(1, (widthPx + gapPx) / Math.max(1, tilePx + gapPx));
        return Math.max(1, Math.min(fit, Math.min(Math.max(1, count), maxColumns)));
    }

    @NonNull
    private static MaterialButton tile(@NonNull Context context, @NonNull Tile tile) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(tile.word);
        RowKit.textAppearance(button, com.google.android.material.R.attr.textAppearanceLabelMedium);
        button.setAllCaps(false);
        button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        int padH = RowKit.dp(context, PAD_H_DP);
        int padV = RowKit.dp(context, PAD_V_DP);
        button.setPaddingRelative(padH, padV, padH, padV);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        // The outline is drawn inside the insets; without them the glyph and word stand inside it.
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(RowKit.dp(context, CORNER_DP));
        button.setCheckable(true);
        // Gravity before the glyph, so the word is measured under it rather than beside it.
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TOP);
        button.setIconPadding(RowKit.dp(context, ICON_GAP_DP));
        button.setIconResource(tile.glyph);
        int[][] states = {{android.R.attr.state_checked}, {}};
        int onSurface = RowKit.color(context, com.google.android.material.R.attr.colorOnSurface);
        button.setBackgroundTintList(new ColorStateList(states, new int[] {
            RowKit.color(context, com.google.android.material.R.attr.colorSecondaryContainer),
            0}));
        ColorStateList content = new ColorStateList(states, new int[] {
            RowKit.color(context, com.google.android.material.R.attr.colorOnSecondaryContainer),
            onSurface});
        button.setTextColor(content);
        button.setIconTint(content);
        button.setStrokeColor(new ColorStateList(states, new int[] {
            RowKit.color(context, com.google.android.material.R.attr.colorSecondaryContainer),
            RowKit.color(context, com.google.android.material.R.attr.colorOutline)}));
        GlyphNames.name(button, tile.hint != null ? tile.hint : tile.word);
        return button;
    }

    /**
     * The grid itself: {@code columns} equal columns {@code gap} apart, top-aligned lines
     * {@code gap} apart, as wide as its parent gives it (the card's width when unbounded). It measures every tile at its column's width, takes the tallest (never
     * under {@code floorPx}) and measures every tile again at that one height, in the same pass, so
     * a one-line word and a two-line word make the same tile at any text size. Hidden tiles
     * ({@link View#INVISIBLE}) keep their place; gone ones give it up.
     */
    static final class Lines extends ViewGroup {
        private final int mColumns;
        private final int mGap;
        private final int mFloor;
        private final int mFallbackWidth;
        private int mTileWidth;
        private int mTileHeight;

        Lines(@NonNull Context context, int columns, int gap, int fallbackWidthPx, int floorPx) {
            super(context);
            mFallbackWidth = fallbackWidthPx;
            mColumns = Math.max(1, columns);
            mGap = gap;
            mFloor = floorPx;
        }

        /** The one height every tile was measured at. */
        int tileHeight() {
            return mTileHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? mFallbackWidth : MeasureSpec.getSize(widthMeasureSpec);
            int room = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            mTileWidth = Math.max(0, (room - mGap * (mColumns - 1)) / mColumns);
            int across = MeasureSpec.makeMeasureSpec(mTileWidth, MeasureSpec.EXACTLY);
            int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int tallest = mFloor;
            int count = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE)
                    continue;
                child.measure(across, free);
                tallest = Math.max(tallest, child.getMeasuredHeight());
                count++;
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
            int room = (r - l) - getPaddingLeft() - getPaddingRight();
            // What the even split leaves over (a pixel or two) goes to both sides alike.
            int spare = Math.max(0, room - mColumns * mTileWidth - (mColumns - 1) * mGap) / 2;
            int slot = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE)
                    continue;
                int column = slot % mColumns;
                int line = slot / mColumns;
                int start = spare + column * (mTileWidth + mGap);
                int left = rtl ? (r - l) - getPaddingRight() - start - mTileWidth
                    : getPaddingLeft() + start;
                int top = getPaddingTop() + line * (mTileHeight + mGap);
                child.layout(left, top, left + child.getMeasuredWidth(),
                    top + child.getMeasuredHeight());
                slot++;
            }
        }
    }

    /** A tile's height: its padding, its glyph, the gap and {@code lines} lines of its word. */
    private static int tileHeightPx(@NonNull Context context, @Nullable MaterialButton probe,
                                    int lines) {
        int touch = RowKit.dp(context, 56);
        if (probe == null)
            return touch;
        int glyph = probe.getIconSize() > 0 ? probe.getIconSize()
            : (probe.getIcon() != null ? probe.getIcon().getIntrinsicHeight()
                : RowKit.dp(context, RowKit.GLYPH_DP));
        Paint.FontMetricsInt metrics = probe.getPaint().getFontMetricsInt();
        int line = Math.max(probe.getLineHeight(), metrics.bottom - metrics.top);
        int need = 2 * RowKit.dp(context, PAD_V_DP) + glyph + probe.getIconPadding()
            + line * lines;
        return Math.max(touch, need);
    }
}
