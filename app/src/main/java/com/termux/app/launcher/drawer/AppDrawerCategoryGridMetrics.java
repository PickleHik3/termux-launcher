package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/** Pure category overview/detail sizing, including shared rendered-icon cache accounting. */
public final class AppDrawerCategoryGridMetrics {
    public static final float MIN_TILE_DP = 144f;
    /**
     * One rhythm for every gap the overview draws — plane edge to card, card to card, card edge to
     * icon, icon to icon. The old set (8dp side padding, a 4dp tile inset, 10dp between slots, 8dp
     * under the heading) added up to a 12dp outer gap beside a 20dp gap between the columns and a
     * 26dp gap between the rows, which is what read as uneven.
     */
    public static final float RHYTHM_DP = 12f;
    public static final float SIDE_PADDING_DP = RHYTHM_DP;
    /** Card gap from the redesign mock: 12dp between the two columns and between rows. */
    public static final float TILE_GAP_DP = RHYTHM_DP;
    /**
     * Zero: the drawn card fills its grid span. The old 4dp inset sat inside every span and so was
     * added to the gap between the columns (12 + 2x4 = 20dp) while the outer edge kept 12dp.
     */
    public static final float TILE_HORIZONTAL_INSET_DP = 0f;
    /** Minimum card inner padding. The resolved spacing only ever grows past it. */
    public static final float TILE_INNER_PADDING_DP = RHYTHM_DP;
    public static final float SMALL_BLOCK_GAP_DP = 5f;
    public static final float ITEM_BOTTOM_GAP_DP = RHYTHM_DP;
    /** The redesign's expanded category grid is a fixed three-across layout. */
    public static final int EXPANDED_COLUMNS = 3;
    /** Ceiling once the height rule starts adding columns; the mock's own maximum is three. */
    public static final int HEIGHT_FIT_MAX_COLUMNS = 6;
    /**
     * A tile may take this much of the viewport's height. Above it the row below is out of reach and
     * the tile's own bottom row of icons is clipped by its border — which is what landscape looked
     * like, where three columns of a 919dp-wide body came out as tall as the whole 297dp viewport.
     */
    public static final float MAX_TILE_VIEWPORT_FRACTION = 0.75f;
    /** Fraction of the viewport a collapse drag spans from fully expanded to fully collapsed. */
    public static final float COLLAPSE_TRAVEL_VIEWPORT_FRACTION = 0.5f;
    /**
     * Ceiling only. The preview icons are sized to fill their half of the card, so on a phone the
     * geometry (or the cache budget) decides — not this. It exists so a very wide card cannot ask
     * for an icon nobody wants to look at.
     */
    public static final float MAX_ICON_DP = 72f;
    public static final float EMPTY_TOP_MIN_DP = 32f;
    public static final float HEADER_LIST_GAP_DP = 12f;
    public static final float PREVIEW_BUDGET_FRACTION = 0.60f;

    public final int columns;
    public final float sidePaddingPx;
    public final float itemGapPx;
    public final float spanWidthPx;
    public final float tileHorizontalInsetPx;
    public final float tileSidePx;
    public final float itemHeightPx;
    public final float headingGapPx;
    public final float headingHeightPx;
    public final float itemBottomGapPx;
    public final float innerPaddingPx;
    public final float slotGapPx;
    public final float largeSlotPx;
    public final float smallCellPx;
    public final float smallBlockGapPx;
    public final int largeIconPx;
    public final int smallIconPx;
    public final int estimatedAttachedTiles;
    public final int expandedColumns;
    public final float expandedRowHeightPx;
    public final float radiusPx;
    public final float collapseTravelPx;
    public final float emptyTopMinPx;
    public final float headerListGapPx;

    private AppDrawerCategoryGridMetrics(int columns, float sidePaddingPx, float itemGapPx,
        float spanWidthPx, float tileHorizontalInsetPx, float tileSidePx, float itemHeightPx,
        float headingGapPx, float headingHeightPx, float itemBottomGapPx, float innerPaddingPx,
        float slotGapPx, float largeSlotPx, float smallBlockGapPx, int largeIconPx,
        int estimatedAttachedTiles,
        int expandedColumns, float expandedRowHeightPx, float radiusPx, float collapseTravelPx,
        float emptyTopMinPx, float headerListGapPx) {
        this.columns = columns;
        this.sidePaddingPx = sidePaddingPx;
        this.itemGapPx = itemGapPx;
        this.spanWidthPx = spanWidthPx;
        this.tileHorizontalInsetPx = tileHorizontalInsetPx;
        this.tileSidePx = tileSidePx;
        this.itemHeightPx = itemHeightPx;
        this.headingGapPx = headingGapPx;
        this.headingHeightPx = headingHeightPx;
        this.itemBottomGapPx = itemBottomGapPx;
        this.innerPaddingPx = innerPaddingPx;
        this.slotGapPx = slotGapPx;
        this.largeSlotPx = largeSlotPx;
        this.smallBlockGapPx = Math.min(Math.max(0f, smallBlockGapPx), largeSlotPx);
        this.smallCellPx = Math.max(0f, (largeSlotPx - this.smallBlockGapPx) / 2f);
        this.largeIconPx = largeIconPx;
        // Off the cell, not half the large icon: the 2x2 block now occupies exactly one icon's
        // footprint, so half a large icon is a hair wider than the cell it would sit in and the four
        // previews would touch across the block's hairline gap.
        this.smallIconPx = Math.max(0, (int) Math.floor(this.smallCellPx));
        this.estimatedAttachedTiles = estimatedAttachedTiles;
        this.expandedColumns = expandedColumns;
        this.expandedRowHeightPx = expandedRowHeightPx;
        this.radiusPx = radiusPx;
        this.collapseTravelPx = collapseTravelPx;
        this.emptyTopMinPx = emptyTopMinPx;
        this.headerListGapPx = headerListGapPx;
    }

    @NonNull
    public static AppDrawerCategoryGridMetrics resolve(float usableWidthPx, float viewportHeightPx,
        float density, float headingHeightPx, float appLabelHeightPx, float drawerRadiusPx,
        int cacheBudgetBytes) {
        return resolve(usableWidthPx, viewportHeightPx, density, headingHeightPx, appLabelHeightPx,
            drawerRadiusPx, cacheBudgetBytes, 0, 0);
    }

    @NonNull
    public static AppDrawerCategoryGridMetrics resolve(float usableWidthPx, float viewportHeightPx,
        float density, float headingHeightPx, float appLabelHeightPx, float drawerRadiusPx,
        int cacheBudgetBytes, int requestedColumns, int requestedIconDp) {
        float d = finitePositive(density, 1f);
        float width = finiteNonNegative(usableWidthPx);
        float viewport = finiteNonNegative(viewportHeightPx);
        float side = SIDE_PADDING_DP * d;
        float gap = TILE_GAP_DP * d;
        float available = Math.max(0f, width - 2f * side);
        // The approved breakpoint is based on the usable body width. Side padding is visual
        // breathing room inside that body and is accounted for when the exact span is resolved.
        int columns = (int) Math.floor((width + gap) / (MIN_TILE_DP * d + gap));
        columns = Math.max(1, Math.min(3, columns));
        if (requestedColumns >= 1 && requestedColumns <= 3) columns = requestedColumns;
        // Width alone decided this, which is why landscape was so sparse: three columns of a 919dp
        // body are 300dp tiles, and a landscape viewport is 297dp tall — one row filled the screen,
        // every tile's bottom row of icons was clipped by its own border, and seven icons sat
        // scattered across a mostly empty card. A tile that would not fit the viewport therefore
        // adds columns until it does, which only ever happens on a body far wider than it is tall.
        if (viewport > 0f) {
            float maxTile = viewport * MAX_TILE_VIEWPORT_FRACTION;
            int fitting = (int) Math.ceil((available + gap) / Math.max(1f, maxTile + gap));
            if (fitting > columns) columns = Math.min(HEIGHT_FIT_MAX_COLUMNS, fitting);
        }
        float span = Math.max(0f, (available - (columns - 1) * gap) / columns);
        float inset = Math.min(TILE_HORIZONTAL_INSET_DP * d, span / 2f);
        float tile = Math.max(0f, span - 2f * inset);
        float heading = finiteNonNegative(headingHeightPx);
        float bottom = ITEM_BOTTOM_GAP_DP * d;
        float pad = Math.min(TILE_INNER_PADDING_DP * d, tile / 4f);
        // Attached-tile estimate for the cache budget, taken at the tightest card the spacing can
        // produce: a shorter item means more of them on screen, so this is the conservative end and
        // it breaks the circle (the real item height depends on the icon the budget is about to
        // decide).
        int attached = Math.max(1, columns * ((int) Math.ceil(
            viewport / Math.max(1f, tile + pad + heading + bottom)) + 1));
        double allowed = Math.max(0d, cacheBudgetBytes) * PREVIEW_BUDGET_FRACTION;
        int budgetIcon = (int) Math.floor(Math.sqrt(allowed / (32d * attached)));
        // Fill, do not float: the icon takes its whole half of the card, so the only space left
        // inside is the rhythm itself. The old 0.80 of an over-sized slot is what left every preview
        // sitting in a ring of dead space.
        int geometryIcon = (int) Math.floor(Math.max(0f, (tile - 3f * pad) / 2f));
        int requestedIconPx = requestedIconDp == 36 || requestedIconDp == 40
            || requestedIconDp == 44 || requestedIconDp == 48
            ? Math.round(requestedIconDp * d) : Math.round(MAX_ICON_DP * d);
        int icon = Math.max(0, Math.min(Math.min(Math.min(Math.round(MAX_ICON_DP * d),
            requestedIconPx), geometryIcon),
            Math.max(0, budgetIcon)));
        int detailColumns = EXPANDED_COLUMNS;
        float detailCell = detailColumns == 0 ? 0f : width / detailColumns;
        int detailIcon = Math.min(icon, Math.max(0, Math.round(detailCell * 0.58f)));
        // One shared size is mandatory: preview-large and detail cache keys are identical.
        icon = Math.min(icon, detailIcon);
        float detailRow = icon + AppDrawerGridMetrics.LABEL_GAP_DP * d
            + finiteNonNegative(appLabelHeightPx)
            + AppDrawerGridMetrics.ROW_BOTTOM_DP * d;
        // The one spacing the whole card is built from: the width the two icons could not take is
        // split three ways, so the left pad, the gap between them and the right pad are the same
        // number. The tile view reuses it above the heading, under it, between the rows and below
        // the bottom row — which is what keeps the padding even on all four sides however small a
        // low-memory budget forced the icons.
        float spacing = icon > 0 ? Math.max(0f, (tile - 2f * icon) / 3f) : pad;
        // Slot == icon: there is no ring left for anything to float inside.
        float largeSlot = icon;
        float itemHeight = tile + spacing + heading + bottom;
        float smallBlockGap = Math.min(SMALL_BLOCK_GAP_DP * d, largeSlot);
        return new AppDrawerCategoryGridMetrics(columns, side, gap, span, inset, tile,
            itemHeight, spacing, heading, bottom, spacing, spacing, largeSlot, smallBlockGap,
            icon, attached,
            detailColumns, detailRow, Math.min(finiteNonNegative(drawerRadiusPx), tile / 2f),
            // The collapse drag's full range. A whole viewport of travel made the shrink lag the
            // finger — two-thirds of the screen dragged still left the pane 60% expanded, and a
            // plain release had to cover half the screen to commit. Half a viewport is the sheet
            // convention: the pane visibly follows the finger and a natural pull commits.
            Math.max(1f, viewport * COLLAPSE_TRAVEL_VIEWPORT_FRACTION),
            EMPTY_TOP_MIN_DP * d, HEADER_LIST_GAP_DP * d);
    }

    public long chargedPreviewBytes() {
        return 32L * largeIconPx * largeIconPx * estimatedAttachedTiles;
    }

    @NonNull
    public DetailLayout resolveDetail(int appCount, float availableHeightPx, float headerHeightPx) {
        int count = Math.max(0, appCount);
        // Division first avoids overflowing when a corrupt/diagnostic count is near Integer.MAX.
        int rows = count / expandedColumns + (count % expandedColumns == 0 ? 0 : 1);
        float desired = rows * expandedRowHeightPx;
        float height = finiteNonNegative(availableHeightPx);
        float header = finiteNonNegative(headerHeightPx);
        float cap = Math.max(0f, height - emptyTopMinPx - header - headerListGapPx);
        float list = Math.min(desired, cap);
        float listTop = height - list;
        float headerBottom = Math.max(0f, listTop - headerListGapPx);
        float headerTop = Math.max(0f, headerBottom - header);
        return new DetailLayout(rows, desired, list, headerTop, headerBottom, listTop, height,
            desired > cap);
    }

    public static final class DetailLayout {
        public final int desiredRows;
        public final float desiredListHeightPx;
        public final float listHeightPx;
        public final float headerTopPx;
        public final float headerBottomPx;
        public final float listTopPx;
        public final float bottomPx;
        public final boolean overflow;

        DetailLayout(int desiredRows, float desiredListHeightPx, float listHeightPx,
            float headerTopPx, float headerBottomPx, float listTopPx, float bottomPx,
            boolean overflow) {
            this.desiredRows = desiredRows;
            this.desiredListHeightPx = desiredListHeightPx;
            this.listHeightPx = listHeightPx;
            this.headerTopPx = headerTopPx;
            this.headerBottomPx = headerBottomPx;
            this.listTopPx = listTopPx;
            this.bottomPx = bottomPx;
            this.overflow = overflow;
        }
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    private static float finitePositive(float value, float fallback) {
        return Float.isFinite(value) && value > 0f ? value : fallback;
    }
}
