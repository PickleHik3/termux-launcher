package com.termux.app.overview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.ArrayList;

/**
 * Overview's cards on one screen (spec overview-2026-11-24, 2026-11-27): the wall as a grid that
 * wraps, then each row block (a title line and its cells: a Workspace's waiting sessions and its +
 * tile, or the Saved row's cards) as a smaller grid. Its size comes from {@link OverviewGrid},
 * asked when the board is measured: the cards are measured at the answer and placed in reading
 * order, so nothing is laid out per frame and nothing moves at rest. A view that is gone takes no
 * cell.
 *
 * <p>A card cell is a picture over a caption; the caption's height is read from the first card's
 * own caption, so the font scale reaches the policy through the view that draws it.
 */
final class OverviewBoard extends ViewGroup {

    /** A row card against a wall card. */
    static final float ROW_SCALE = 0.8f;

    /** One row block: its title and its cells, a + tile marked as not a card. */
    private static final class Block {
        @NonNull final View title;
        @NonNull final ArrayList<View> cells = new ArrayList<>();
        @NonNull final ArrayList<Boolean> cards = new ArrayList<>();
        boolean hidden;

        Block(@NonNull View title) {
            this.title = title;
        }
    }

    @NonNull private final ArrayList<View> mWall = new ArrayList<>();
    @NonNull private final ArrayList<Block> mBlocks = new ArrayList<>();
    @NonNull private final OverviewGrid.Spec mSpec = new OverviewGrid.Spec();
    @NonNull private final OverviewGrid.Fit mFit = new OverviewGrid.Fit();
    private final int mGap;
    private final int mBlockGap;
    private final int mTitleGap;
    private final int mCaptionGap;
    /** The room down when a scroller measures the board with no limit; 0 for none. */
    private int mViewport;
    private float mAspect = 1f;
    /** Where the wall's first cell and each block's first cell stand, after layout. */
    private int mWallTop;
    @NonNull private int[] mBlockTops = new int[0];
    @NonNull private int[] mBlockLefts = new int[0];
    /** A held cell, drawn last so it passes over its neighbours with no Z. */
    @Nullable private View mDrawnLast;

    OverviewBoard(@NonNull Context context) {
        super(context);
        mGap = context.getResources().getDimensionPixelSize(R.dimen.overview_card_gap);
        mBlockGap = context.getResources().getDimensionPixelSize(R.dimen.overview_row_gap);
        mTitleGap = context.getResources().getDimensionPixelSize(R.dimen.overview_row_title_gap);
        mCaptionGap = context.getResources().getDimensionPixelSize(R.dimen.overview_caption_gap);
        mSpec.gap = mGap;
        mSpec.blockGap = mBlockGap;
        mSpec.maxLong = context.getResources().getDimension(R.dimen.overview_card_max_long);
        mSpec.minWidth = context.getResources().getDimension(R.dimen.overview_card_min_width);
        mSpec.minHeight = context.getResources().getDimension(R.dimen.overview_card_min_height);
        mSpec.rowScale = ROW_SCALE;
        mSpec.minBlockWidth = context.getResources().getDimension(R.dimen.overview_block_min_width);
        setChildrenDrawingOrderEnabled(true);
        setClipChildren(false);
    }

    // ---- Content ------------------------------------------------------------------------------

    void clear() {
        removeAllViews();
        mWall.clear();
        mBlocks.clear();
        mDrawnLast = null;
    }

    void addWallCard(@NonNull View card) {
        mWall.add(card);
        addView(card);
    }

    /** A new row block under the others; returns its place. */
    int addBlock(@NonNull View title) {
        mBlocks.add(new Block(title));
        addView(title);
        return mBlocks.size() - 1;
    }

    /** A cell at the end of block {@code block}; {@code card} false for a + tile. */
    void addCell(int block, @NonNull View cell, boolean card) {
        Block b = mBlocks.get(block);
        b.cells.add(cell);
        b.cards.add(card);
        addView(cell);
    }

    /** Takes the last block away (the Saved row, drawn again on its own). */
    void removeLastBlock() {
        if (mBlocks.isEmpty()) return;
        Block b = mBlocks.remove(mBlocks.size() - 1);
        removeView(b.title);
        for (View cell : b.cells) removeView(cell);
    }

    int blockCount() {
        return mBlocks.size();
    }

    /** A block stands aside with its Workspace card (a removal held for Undo), or comes back. */
    void setBlockHidden(int block, boolean hidden) {
        Block b = mBlocks.get(block);
        if (b.hidden == hidden) return;
        b.hidden = hidden;
        requestLayout();
    }

    int wallCount() {
        return mWall.size();
    }

    @Nullable
    View wallCard(int index) {
        return index >= 0 && index < mWall.size() ? mWall.get(index) : null;
    }

    int wallIndexOf(@NonNull View card) {
        return mWall.indexOf(card);
    }

    /** The block a cell stands in, or -1. */
    int blockOf(@NonNull View cell) {
        for (int i = 0; i < mBlocks.size(); i++) {
            if (mBlocks.get(i).cells.contains(cell)) return i;
        }
        return -1;
    }

    /** The cards of block {@code block} (its + tile left out). */
    int cardCount(int block) {
        int n = 0;
        for (Boolean card : mBlocks.get(block).cards) if (card) n++;
        return n;
    }

    @Nullable
    View blockCard(int block, int index) {
        Block b = mBlocks.get(block);
        int n = 0;
        for (int i = 0; i < b.cells.size(); i++) {
            if (!b.cards.get(i)) continue;
            if (n++ == index) return b.cells.get(i);
        }
        return null;
    }

    /** The room a scroller above the board has, for a measure with no limit of its own. */
    void setViewport(int height) {
        mViewport = height;
    }

    /** A page's height over its width: Overview's own window, set before the board is measured. */
    void setAspect(float aspect) {
        mAspect = aspect > 0f ? aspect : 1f;
    }

    /** Whether everything fits; false only when the floor does not (the scroller then scrolls). */
    boolean fits() {
        return mFit.fits;
    }

    // ---- Drag geometry (board pixels) --------------------------------------------------------

    /** The wall's gap under the finger at board point ({@code x}, {@code y}). */
    int wallGap(float x, float y) {
        return OverviewDropPolicy.gridGap(x - getPaddingLeft(), y - mWallTop, mWall.size(),
            mFit.columns, mFit.cardWidth, wallCell(), mGap);
    }

    /** The wall's grid row nearest board height {@code y}. */
    int wallRow(float y) {
        return OverviewDropPolicy.gridRow(y - mWallTop, mWall.size(), mFit.columns, wallCell(),
            mGap);
    }

    int wallColumns() {
        return mFit.columns;
    }

    /** Block {@code block}'s gap among its cards under the finger at board point (x, y). */
    int blockGap(int block, float x, float y) {
        return OverviewDropPolicy.gridGap(x - getPaddingLeft() - blockLeft(block),
            y - blockTop(block),
            cardCount(block), mFit.rowColumns, mFit.rowCardWidth, rowCell(), mGap);
    }

    int blockRow(int block, float y) {
        return OverviewDropPolicy.gridRow(y - blockTop(block), cardCount(block), mFit.rowColumns,
            rowCell(), mGap);
    }

    int rowColumns() {
        return mFit.rowColumns;
    }

    /** The space between two cells, where a landing mark stands. */
    int gap() {
        return mGap;
    }

    private int blockLeft(int block) {
        return block >= 0 && block < mBlockLefts.length ? mBlockLefts[block] : 0;
    }

    private int blockTop(int block) {
        return block >= 0 && block < mBlockTops.length ? mBlockTops[block] : 0;
    }

    /** The held cell draws over the others; null for none. */
    void setDrawnLast(@Nullable View cell) {
        if (mDrawnLast == cell) return;
        mDrawnLast = cell;
        invalidate();
    }

    @Override protected int getChildDrawingOrder(int count, int i) {
        View last = mDrawnLast;
        int held = last == null ? -1 : indexOfChild(last);
        if (held < 0) return i;
        if (i == count - 1) return held;
        return i >= held ? i + 1 : i;
    }

    // ---- Measure and layout ------------------------------------------------------------------

    private float wallCell() {
        return mFit.cardHeight + mSpec.caption;
    }

    private float rowCell() {
        return mFit.rowCardHeight + mSpec.caption;
    }

    private int visibleWall() {
        int n = 0;
        for (View v : mWall) if (v.getVisibility() != GONE) n++;
        return n;
    }

    private int visibleCells(@NonNull Block b) {
        if (b.hidden) return 0;
        int n = 0;
        for (View v : b.cells) if (v.getVisibility() != GONE) n++;
        return n;
    }

    /** A caption's height: the first card's own, measured on its own line, with its gap. */
    private int captionHeight() {
        View card = null;
        for (View v : mWall) if (v instanceof ViewGroup) { card = v; break; }
        if (card == null) {
            for (Block b : mBlocks) {
                for (int i = 0; i < b.cells.size() && card == null; i++) {
                    if (b.cards.get(i) && b.cells.get(i) instanceof ViewGroup) card = b.cells.get(i);
                }
            }
        }
        if (card == null || ((ViewGroup) card).getChildCount() < 2) return 0;
        View caption = ((ViewGroup) card).getChildAt(1);
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        caption.measure(unspecified, unspecified);
        return caption.getMeasuredHeight() + mCaptionGap;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int room = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
            ? mViewport : MeasureSpec.getSize(heightSpec);
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int across = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int titleWidth = MeasureSpec.makeMeasureSpec(across, MeasureSpec.EXACTLY);
        int head = 0;
        int blocks = 0;
        if (mSpec.rows.length < mBlocks.size()) mSpec.rows = new int[mBlocks.size()];
        for (Block b : mBlocks) {
            b.title.measure(titleWidth, unspecified);
            head = Math.max(head, b.title.getMeasuredHeight());
            mSpec.rows[blocks++] = visibleCells(b);
        }
        mSpec.rowCount = blocks;
        mSpec.width = across;
        mSpec.height = Math.max(0, room - getPaddingTop() - getPaddingBottom());
        mSpec.aspect = mAspect;
        mSpec.pages = visibleWall();
        mSpec.caption = captionHeight();
        mSpec.head = head + mTitleGap;
        OverviewGrid.fit(mSpec, mFit);
        for (int i = 0; i < mBlocks.size(); i++) {
            mBlocks.get(i).title.measure(
                exactly((int) Math.floor(mFit.blockWidth[i])), unspecified);
        }

        int cardW = (int) Math.floor(mFit.cardWidth);
        int cardH = (int) Math.floor(mFit.cardHeight + mSpec.caption);
        for (View v : mWall) {
            if (v.getVisibility() == GONE) continue;
            v.measure(exactly(cardW), exactly(cardH));
        }
        int rowW = (int) Math.floor(mFit.rowCardWidth);
        int rowPicture = (int) Math.floor(mFit.rowCardHeight);
        int rowH = (int) Math.floor(mFit.rowCardHeight + mSpec.caption);
        for (Block b : mBlocks) {
            for (int i = 0; i < b.cells.size(); i++) {
                View v = b.cells.get(i);
                if (v.getVisibility() == GONE) continue;
                v.measure(exactly(rowW), exactly(b.cards.get(i) ? rowH : rowPicture));
            }
        }
        int height = (int) Math.ceil(mFit.height) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, size), MeasureSpec.EXACTLY);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        int y = getPaddingTop();
        boolean any = false;
        mWallTop = y;
        if (visibleWall() > 0) {
            y = placeGrid(mWall, null, left, y, mFit.columns, (int) Math.floor(mFit.cardWidth),
                (int) Math.floor(mFit.cardHeight + mSpec.caption));
            any = true;
        }
        if (mBlockTops.length < mBlocks.size()) {
            mBlockTops = new int[mBlocks.size()];
            mBlockLefts = new int[mBlocks.size()];
        }
        for (int i = 0; i < mBlocks.size(); i++) {
            Block block = mBlocks.get(i);
            boolean shown = visibleCells(block) > 0;
            if (!shown) {
                // A block standing aside keeps no room; its title is laid out empty.
                block.title.layout(left, y, left, y);
                for (View cell : block.cells) cell.layout(left, y, left, y);
                mBlockTops[i] = y;
                mBlockLefts[i] = 0;
                continue;
            }
            int blockLeft = left + Math.round(mFit.blockLeft[i]);
            int titleTop = getPaddingTop() + Math.round(mFit.blockTop[i]);
            View title = block.title;
            title.layout(blockLeft, titleTop, blockLeft + title.getMeasuredWidth(),
                titleTop + title.getMeasuredHeight());
            int cellsTop = titleTop + Math.round(mSpec.head);
            mBlockTops[i] = cellsTop;
            mBlockLefts[i] = Math.round(mFit.blockLeft[i]);
            placeGrid(block.cells, block, blockLeft, cellsTop, mFit.rowColumns,
                (int) Math.floor(mFit.rowCardWidth),
                (int) Math.floor(mFit.rowCardHeight + mSpec.caption));
            y = Math.max(y, cellsTop);
        }
    }

    /** Lays {@code cells} out in reading order from (left, top); returns the bottom. */
    private int placeGrid(@NonNull ArrayList<View> cells, @Nullable Block block, int left, int top,
                          int columns, int cellW, int cellH) {
        int at = 0;
        int bottom = top;
        for (View v : cells) {
            if (v.getVisibility() == GONE) continue;
            if (block != null && block.hidden) continue;
            int col = at % columns;
            int row = at / columns;
            int x = left + col * (cellW + mGap);
            int y = top + row * (cellH + mGap);
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
            bottom = y + cellH;
            at++;
        }
        return bottom;
    }
}
