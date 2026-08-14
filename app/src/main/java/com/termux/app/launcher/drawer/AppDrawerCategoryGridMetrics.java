package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/** Pure category overview/detail sizing, including shared rendered-icon cache accounting. */
public final class AppDrawerCategoryGridMetrics {
    public static final float MIN_TILE_DP = 144f;
    public static final float SIDE_PADDING_DP = 8f;
    /** Card gap from the redesign mock: 12dp between the two columns and between rows. */
    public static final float TILE_GAP_DP = 12f;
    public static final float TILE_HORIZONTAL_INSET_DP = 4f;
    /** Card inner padding from the mock (13/12/14 top/side/bottom, folded to one token). */
    public static final float TILE_INNER_PADDING_DP = 12f;
    public static final float SLOT_GAP_DP = 10f;
    public static final float SMALL_BLOCK_GAP_DP = 5f;
    public static final float HEADING_GAP_DP = 8f;
    public static final float ITEM_BOTTOM_GAP_DP = 12f;
    /** The redesign's expanded category grid is a fixed three-across layout. */
    public static final int EXPANDED_COLUMNS = 3;
    public static final float MAX_ICON_DP = 48f;
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
        this.smallIconPx = Math.max(0, largeIconPx / 2);
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
        float span = Math.max(0f, (available - (columns - 1) * gap) / columns);
        float inset = Math.min(TILE_HORIZONTAL_INSET_DP * d, span / 2f);
        float tile = Math.max(0f, span - 2f * inset);
        float heading = finiteNonNegative(headingHeightPx);
        float headingGap = HEADING_GAP_DP * d;
        float bottom = ITEM_BOTTOM_GAP_DP * d;
        float itemHeight = tile + headingGap + heading + bottom;
        float inner = Math.min(TILE_INNER_PADDING_DP * d, tile / 2f);
        float slotGap = Math.min(SLOT_GAP_DP * d, Math.max(0f, tile - 2f * inner));
        float largeSlot = Math.max(0f, (tile - 2f * inner - slotGap) / 2f);
        int attached = Math.max(1, columns * ((int) Math.ceil(
            viewport / Math.max(1f, itemHeight)) + 1));
        double allowed = Math.max(0d, cacheBudgetBytes) * PREVIEW_BUDGET_FRACTION;
        int budgetIcon = (int) Math.floor(Math.sqrt(allowed / (32d * attached)));
        int geometryIcon = (int) Math.floor(largeSlot * 0.80f);
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
        float smallBlockGap = Math.min(SMALL_BLOCK_GAP_DP * d, largeSlot);
        return new AppDrawerCategoryGridMetrics(columns, side, gap, span, inset, tile,
            itemHeight, headingGap, heading, bottom, inner, slotGap, largeSlot, smallBlockGap,
            icon, attached,
            detailColumns, detailRow, Math.min(finiteNonNegative(drawerRadiusPx), tile / 2f),
            Math.max(1f, viewport), EMPTY_TOP_MIN_DP * d, HEADER_LIST_GAP_DP * d);
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
