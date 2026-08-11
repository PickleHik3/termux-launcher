package com.termux.app.launcher.drawer;

/** Pure drawer drag thresholds. It never reads live view state after the DOWN snapshot. */
public final class AppDrawerDragPolicy {
    public static final float HORIZONTAL_EDGE_DP = 32f;
    public static final long HORIZONTAL_DWELL_MS = 500L;
    public static final float VERTICAL_EDGE_DP = 48f;
    public static final float MAX_AUTOSCROLL_PX_PER_SEC = 1800f;

    public enum Claim { PENDING, CHILD_SCROLL, CLOSE, PAGE_OR_TILE, CONTEXT, DRAG }

    public static final class FrozenDown {
        public final AppDrawerViewType viewType;
        public final boolean interactive;
        public final boolean emptyQuery;
        public final boolean eligibleCell;
        public final String stableId;

        public FrozenDown(AppDrawerViewType viewType, boolean interactive, boolean emptyQuery,
                          boolean eligibleCell, String stableId) {
            this.viewType = viewType;
            this.interactive = interactive;
            this.emptyQuery = emptyQuery;
            this.eligibleCell = eligibleCell;
            this.stableId = stableId;
        }

        public boolean dragEligible() {
            return interactive && emptyQuery && eligibleCell
                && viewType != AppDrawerViewType.CATEGORIES && stableId != null;
        }
    }

    private final FrozenDown down;
    private Claim claim = Claim.PENDING;

    public AppDrawerDragPolicy(FrozenDown down) { this.down = down; }
    public Claim claim() { return claim; }
    public FrozenDown frozenDown() { return down; }

    public boolean claim(Claim next) {
        if (next == Claim.PENDING) return false;
        // The stationary long-press first owns CONTEXT; only its deliberate lift may refine that
        // one state to DRAG. Every other settled claim remains irreversible.
        if (claim == Claim.CONTEXT && next == Claim.DRAG && down.dragEligible()) {
            claim = Claim.DRAG;
            return true;
        }
        if (claim != Claim.PENDING) return false;
        if (next == Claim.DRAG && !down.dragEligible()) return false;
        claim = next;
        return true;
    }

    public static int edgeDirection(float x, float width, float density) {
        float zone = HORIZONTAL_EDGE_DP * Math.max(1f, density);
        if (x >= 0f && x <= zone) return -1;
        if (x <= width && x >= width - zone) return 1;
        return 0;
    }

    public static float verticalAutoscrollVelocity(float y, float height, float density) {
        float zone = VERTICAL_EDGE_DP * Math.max(1f, density);
        float penetration;
        if (y < zone) penetration = -(zone - Math.max(0f, y)) / zone;
        else if (y > height - zone) penetration = (zone - Math.max(0f, height - y)) / zone;
        else return 0f;
        return Math.max(-MAX_AUTOSCROLL_PX_PER_SEC,
            Math.min(MAX_AUTOSCROLL_PX_PER_SEC, penetration * MAX_AUTOSCROLL_PX_PER_SEC));
    }
}
