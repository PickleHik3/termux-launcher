package com.termux.app.statusbar;

/** Pure geometry for keeping the status row attached to a directly resized top bar. */
public final class StatusBarResizeGeometry {

    public static final class Row {
        public final int height;
        public final int top;
        public final int clockClipBottom;
        public final float expansion;
        public final float fullExpansion;
        public final float topSlotAlpha;

        private Row(int height, int top, float expansion, float fullExpansion,
                    float topSlotAlpha) {
            this.height = height;
            this.top = top;
            this.clockClipBottom = top;
            this.expansion = expansion;
            this.fullExpansion = fullExpansion;
            this.topSlotAlpha = topSlotAlpha;
        }
    }

    private StatusBarResizeGeometry() {}

    public static Row calculate(int surfaceHeight, int collapsedSurfaceHeight,
                                int expandedSurfaceHeight, int collapsedRowHeight,
                                int expandedRowHeight, int expandedBottomMargin) {
        int surfaceRange = Math.max(1, expandedSurfaceHeight - collapsedSurfaceHeight);
        float expansion = Math.max(0f, Math.min(1f,
            (surfaceHeight - collapsedSurfaceHeight) / (float) surfaceRange));
        int rowHeight = Math.round(collapsedRowHeight
            + (expandedRowHeight - collapsedRowHeight) * expansion);
        int bottomMargin = Math.round(expandedBottomMargin * expansion);

        // The canonical collapsed row is centered, leaving equal top/bottom clearance. Reduce
        // only that bottom clearance while the surface grows so the row follows the moving edge
        // continuously and lands exactly on the canonical expanded bottom position.
        int collapsedBottomClearance = Math.max(0,
            (collapsedSurfaceHeight - collapsedRowHeight) / 2);
        int remainingClearance = Math.round(collapsedBottomClearance * (1f - expansion));
        int rowTop = Math.max(0,
            surfaceHeight - bottomMargin - rowHeight - remainingClearance);
        return new Row(rowHeight, rowTop, expansion, 0f, expansion);
    }

    /** Explicit expanded-to-FULL geometry; normal two-state output above remains unchanged. */
    public static Row calculateFull(int surfaceHeight, int expandedSurfaceHeight,
                                    int fullSurfaceHeight, int expandedRowHeight,
                                    int expandedBottomMargin) {
        int range = Math.max(1, fullSurfaceHeight - expandedSurfaceHeight);
        float fullExpansion = FullStatusBarGeometry.finiteUnit(
            (surfaceHeight - expandedSurfaceHeight) / (float) range);
        // The spring-written surface height is the row's authoritative moving edge. The resolved
        // FULL target can briefly be stale while parent/accessory relayout is being delivered; it
        // is useful for normalized progress but must never clamp real child geometry.
        int actualSurface = Math.max(0, surfaceHeight);
        int top = Math.max(0, actualSurface - Math.max(0, expandedBottomMargin)
            - Math.max(0, expandedRowHeight));
        return new Row(Math.max(0, expandedRowHeight), top, 1f, fullExpansion, 1f);
    }
}
