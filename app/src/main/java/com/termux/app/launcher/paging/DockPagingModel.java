package com.termux.app.launcher.paging;

/**
 * Pure page arithmetic for the dock / suggestion bar: how many pages the pinned row and the A–Z
 * preview have, where each page starts, how a page index wraps, how a horizontal drag maps onto a
 * fractional page position, and whether a finished drag qualifies as a page commit.
 *
 * <p>Extracted verbatim out of {@code SuggestionBarView} so the arithmetic can be exercised without
 * inflating a View. No Android types: every input is a number, a flag or a plain digest of the
 * entries being paged, and every method is a function of its arguments only.
 */
public final class DockPagingModel {

    /** Minimum finger travel that commits a page, in dp, before the width-proportional floor. */
    public static final float PAGE_SWIPE_COMMIT_DISTANCE_DP = 42f;
    /** Fraction of the row width that a committing drag must cover. */
    public static final float PAGE_SWIPE_COMMIT_WIDTH_RATIO = 0.30f;
    /** A fling may commit below the distance threshold, but never below this travel (dp). */
    public static final float PAGE_SWIPE_FLING_MIN_DISPLACEMENT_DP = 28f;
    /** Horizontal velocity above which a short drag still commits. */
    public static final float PAGE_SWIPE_FLING_VELOCITY_PX_PER_SEC = 900f;

    private static final float SWIPE_HORIZONTAL_DOMINANCE = 1.2f;
    private static final float SETTLE_MIN_VELOCITY = 150f;
    private static final float SETTLE_MAX_VELOCITY = 5200f;
    private static final long SETTLE_MIN_DURATION_MS = 280L;
    private static final long SETTLE_MAX_DURATION_MS = 410L;
    private static final float DRAG_MAX_TRAVEL_DP = 18f;
    private static final float DRAG_MAX_TRAVEL_WIDTH_RATIO = 0.38f;

    private DockPagingModel() {}

    /** Digest of the entry list being paged, so the model never sees an Android-backed entry. */
    public interface EntryDigest {
        int size();

        /** Stable identity of entry {@code index}; may be null. */
        String keyAt(int index);

        /** Whether entry {@code index} already has its icon resolved. */
        boolean hasIconAt(int index);
    }

    // ---------------------------------------------------------------- pinned row

    /** Slots one pinned page renders. Always at least one. */
    public static int pinnedItemsPerPage(int maxButtonCount) {
        return Math.max(1, maxButtonCount);
    }

    /**
     * Pages occupied by the user's persisted pinned items, excluding the dynamic most-used page.
     * An empty row still owns one (empty) page.
     */
    public static int realPinnedPageCount(int pinnedItemCount, int maxButtonCount) {
        int total = Math.max(0, pinnedItemCount);
        int perPage = pinnedItemsPerPage(maxButtonCount);
        if (total <= 0) return 1;
        return (total + perPage - 1) / perPage;
    }

    /** Real pinned pages plus the dynamic most-used page when it is shown. */
    public static int pinnedPageCount(int pinnedItemCount, int maxButtonCount, boolean hasMostUsedPage) {
        return realPinnedPageCount(pinnedItemCount, maxButtonCount) + (hasMostUsedPage ? 1 : 0);
    }

    /** The dynamic page is always the trailing page, right after the real pinned pages. */
    public static boolean isMostUsedDynamicPage(
        int pageIndex, int pinnedItemCount, int maxButtonCount, boolean hasMostUsedPage) {
        return hasMostUsedPage && pageIndex == realPinnedPageCount(pinnedItemCount, maxButtonCount);
    }

    /** Page index of the dynamic most-used page, or -1 when it isn't shown. */
    public static int dynamicPageIndex(int pinnedItemCount, int maxButtonCount, boolean hasMostUsedPage) {
        return hasMostUsedPage ? realPinnedPageCount(pinnedItemCount, maxButtonCount) : -1;
    }

    /** First pinned-item index rendered by {@code pageIndex}. */
    public static int pinnedPageStart(int pageIndex, int maxButtonCount) {
        return Math.max(0, pageIndex) * pinnedItemsPerPage(maxButtonCount);
    }

    // ------------------------------------------------------------------ A–Z rows

    /**
     * Start offsets of every A–Z preview page. Pages advance a full row at a time, except the last,
     * which is pulled back so it renders a full row instead of a short tail.
     */
    public static int[] azPageStarts(int totalEntries, int slots) {
        int total = Math.max(0, totalEntries);
        int perPage = Math.max(1, slots);
        if (total <= 0) return new int[]{0};
        int maxStart = Math.max(0, total - perPage);
        int[] scratch = new int[(maxStart / perPage) + 2];
        int count = 0;
        scratch[count++] = 0;
        int start = 0;
        while (start < maxStart) {
            start = Math.min(start + perPage, maxStart);
            if (scratch[count - 1] != start) {
                scratch[count++] = start;
            }
        }
        if (count == scratch.length) return scratch;
        int[] starts = new int[count];
        System.arraycopy(scratch, 0, starts, 0, count);
        return starts;
    }

    /** Number of A–Z preview pages. Always at least one. */
    public static int azPageCount(int totalEntries, int slots) {
        return Math.max(1, azPageStarts(totalEntries, slots).length);
    }

    /** First entry index rendered by A–Z page {@code pageIndex} (index clamped into range). */
    public static int azPageStart(int totalEntries, int pageIndex, int slots) {
        int[] starts = azPageStarts(totalEntries, slots);
        return starts[clampPage(pageIndex, starts.length)];
    }

    /** Last entry index (exclusive) rendered by A–Z page {@code pageIndex}. */
    public static int azPageEnd(int totalEntries, int pageIndex, int slots) {
        int perPage = Math.max(1, slots);
        return Math.min(Math.max(0, totalEntries), azPageStart(totalEntries, pageIndex, slots) + perPage);
    }

    /**
     * Identity of what one A–Z page would draw — the entry keys, whether each icon is resolved and
     * the page bounds. Equal signatures mean an identical repaint, so the render can be skipped.
     */
    public static int azPageSignature(EntryDigest entries, int pageIndex, int slots) {
        int perPage = Math.max(1, slots);
        int total = entries == null ? 0 : entries.size();
        int start = azPageStart(total, pageIndex, perPage);
        int end = Math.min(total, start + perPage);
        int signature = 17;
        for (int i = start; i < end; i++) {
            String key = entries.keyAt(i);
            signature = (31 * signature) + (key == null ? 0 : key.hashCode());
            signature = (31 * signature) + (entries.hasIconAt(i) ? 1 : 0);
        }
        signature = (31 * signature) + start;
        signature = (31 * signature) + end;
        return signature;
    }

    // ------------------------------------------------------------ index handling

    /** Wraps a page index into {@code [0, totalPages)}; a page-less row stays on page zero. */
    public static int wrap(int targetPage, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        int wrapped = targetPage % totalPages;
        if (wrapped < 0) {
            wrapped += totalPages;
        }
        return wrapped;
    }

    /** Clamps a page index into {@code [0, pageCount - 1]}. */
    public static int clampPage(int pageIndex, int pageCount) {
        return Math.max(0, Math.min(pageIndex, Math.max(0, pageCount - 1)));
    }

    /** Both directions page whenever there is more than one page: the row wraps around. */
    public static boolean hasOverflowPages(int pageCount) {
        return pageCount > 1;
    }

    /**
     * The page position the indicator should draw: the live fractional position while a drag or a
     * switch animation owns the row, and the settled page otherwise.
     */
    public static float visualPagePosition(
        boolean hasOverflowPages, boolean dragging, boolean animating,
        float livePagePosition, int currentPageIndex) {
        return (hasOverflowPages && (dragging || animating))
            ? livePagePosition
            : Math.max(0, currentPageIndex);
    }

    // ------------------------------------------------------------- drag physics

    /** Travel a drag must cover to commit a page: a dp floor, raised on wide rows. */
    public static float commitDistancePx(float rowWidthPx, float density) {
        return Math.max(PAGE_SWIPE_COMMIT_DISTANCE_DP * density,
            Math.max(1f, rowWidthPx) * PAGE_SWIPE_COMMIT_WIDTH_RATIO);
    }

    /**
     * The page delta a finished drag asks for: {@code +1} forward (finger moved left), {@code -1}
     * back, {@code 0} when the drag does not qualify. A drag qualifies on distance, or on a fling
     * that clears a shorter travel with matching direction; either way it must be dominantly
     * horizontal.
     */
    public static int commitPageDelta(
        float dx, float dy, float vx, float commitDistancePx, float density) {
        boolean distanceCommit = Math.abs(dx) > commitDistancePx;
        boolean velocityCommit = Math.abs(dx) >= (PAGE_SWIPE_FLING_MIN_DISPLACEMENT_DP * density)
            && Math.abs(vx) > PAGE_SWIPE_FLING_VELOCITY_PX_PER_SEC
            && Math.signum(dx) == Math.signum(vx);
        if (!distanceCommit && !velocityCommit) return 0;
        if (Math.abs(dx) <= Math.abs(dy) * SWIPE_HORIZONTAL_DOMINANCE) return 0;
        return dx < 0 ? 1 : -1;
    }

    /** The page delta an in-progress drag is heading towards. */
    public static int dragPageDelta(float dx) {
        return dx < 0f ? 1 : -1;
    }

    /** Velocity handed to the settle animation: the fling, or the travel when the fling is slow. */
    public static float settleVelocityHint(float dx, float vx) {
        return Math.max(Math.abs(vx), Math.abs(dx) * 8f);
    }

    /** Settle duration: faster flings land sooner, inside a fixed band. */
    public static long settleDurationMs(float velocityPxPerSec) {
        float v = Math.max(SETTLE_MIN_VELOCITY, Math.min(SETTLE_MAX_VELOCITY, Math.abs(velocityPxPerSec)));
        long ms = (long) (410f - ((v - SETTLE_MIN_VELOCITY) / (SETTLE_MAX_VELOCITY - SETTLE_MIN_VELOCITY)) * 130f);
        return Math.max(SETTLE_MIN_DURATION_MS, Math.min(SETTLE_MAX_DURATION_MS, ms));
    }

    /** Drag progress towards the commit, eased so the first pixels move most. */
    public static float dragEasedProgress(float dx, float commitDistancePx) {
        float raw = clamp01(Math.abs(dx) / Math.max(1f, commitDistancePx));
        return (float) Math.sin(raw * (Math.PI * 0.5f));
    }

    /** The row's visual translation for a drag, capped so the page never slides fully away. */
    public static float dragVisualOffsetPx(float dx, float rowWidthPx, float density) {
        float maxTravel = Math.max(DRAG_MAX_TRAVEL_DP * density,
            Math.max(1f, rowWidthPx) * DRAG_MAX_TRAVEL_WIDTH_RATIO);
        return Math.max(-maxTravel, Math.min(maxTravel, dx));
    }

    /** Fractional page position during a drag, clamped to the real page range. */
    public static float dragPagePosition(float basePage, float dx, float easedProgress, int pageCount) {
        float signedProgress = dx < 0f ? easedProgress : -easedProgress;
        float max = Math.max(0, pageCount - 1);
        float position = basePage + signedProgress;
        return Math.max(0f, Math.min(max, position));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}
