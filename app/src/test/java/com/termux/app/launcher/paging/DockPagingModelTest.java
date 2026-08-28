package com.termux.app.launcher.paging;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The dock's page arithmetic, exercised without inflating the row it used to live in. */
public class DockPagingModelTest {

    private static final float DENSITY = 2f;

    // ------------------------------------------------------------ pinned pages

    @Test
    public void pinnedCapacityIsNeverZero() {
        assertEquals(1, DockPagingModel.pinnedItemsPerPage(0));
        assertEquals(1, DockPagingModel.pinnedItemsPerPage(-4));
        assertEquals(5, DockPagingModel.pinnedItemsPerPage(5));
    }

    @Test
    public void anEmptyPinnedRowStillOwnsOnePage() {
        assertEquals(1, DockPagingModel.realPinnedPageCount(0, 4));
        assertEquals(1, DockPagingModel.realPinnedPageCount(-3, 4));
    }

    @Test
    public void pinnedPagesCountUpAcrossItemCountsAndCapacities() {
        assertEquals(1, DockPagingModel.realPinnedPageCount(1, 4));
        assertEquals(1, DockPagingModel.realPinnedPageCount(4, 4));
        assertEquals(2, DockPagingModel.realPinnedPageCount(5, 4));
        assertEquals(2, DockPagingModel.realPinnedPageCount(8, 4));
        assertEquals(3, DockPagingModel.realPinnedPageCount(9, 4));
        assertEquals(6, DockPagingModel.realPinnedPageCount(6, 1));
        assertEquals(2, DockPagingModel.realPinnedPageCount(6, 3));
        // A capacity of zero is read as one slot, not as a division by zero.
        assertEquals(7, DockPagingModel.realPinnedPageCount(7, 0));
    }

    @Test
    public void theMostUsedPageIsAnExtraTrailingPageWhenItIsShown() {
        assertEquals(2, DockPagingModel.pinnedPageCount(6, 3, false));
        assertEquals(3, DockPagingModel.pinnedPageCount(6, 3, true));
        // Even an empty pinned row gets a second page from the dynamic one.
        assertEquals(1, DockPagingModel.pinnedPageCount(0, 3, false));
        assertEquals(2, DockPagingModel.pinnedPageCount(0, 3, true));
    }

    @Test
    public void theMostUsedPageOwnsTheIndexAfterTheRealPinnedPages() {
        assertEquals(2, DockPagingModel.dynamicPageIndex(6, 3, true));
        assertTrue(DockPagingModel.isMostUsedDynamicPage(2, 6, 3, true));
        assertFalse(DockPagingModel.isMostUsedDynamicPage(1, 6, 3, true));
        assertFalse(DockPagingModel.isMostUsedDynamicPage(3, 6, 3, true));
    }

    @Test
    public void withoutTheMostUsedPageNoIndexIsDynamic() {
        assertEquals(-1, DockPagingModel.dynamicPageIndex(6, 3, false));
        assertFalse(DockPagingModel.isMostUsedDynamicPage(2, 6, 3, false));
        assertFalse(DockPagingModel.isMostUsedDynamicPage(0, 0, 3, false));
    }

    @Test
    public void pinnedPagesStartAtWholeMultiplesOfTheCapacity() {
        assertEquals(0, DockPagingModel.pinnedPageStart(0, 4));
        assertEquals(4, DockPagingModel.pinnedPageStart(1, 4));
        assertEquals(8, DockPagingModel.pinnedPageStart(2, 4));
        // A negative page is read as the first page.
        assertEquals(0, DockPagingModel.pinnedPageStart(-2, 4));
    }

    // --------------------------------------------------------------- A–Z pages

    @Test
    public void anEmptyAzCandidateListIsOneEmptyPage() {
        assertArrayEquals(new int[]{0}, DockPagingModel.azPageStarts(0, 4));
        assertEquals(1, DockPagingModel.azPageCount(0, 4));
        assertEquals(0, DockPagingModel.azPageStart(0, 3, 4));
    }

    @Test
    public void azPagesAdvanceARowAtATime() {
        assertArrayEquals(new int[]{0, 3, 6, 9}, DockPagingModel.azPageStarts(12, 3));
        assertEquals(4, DockPagingModel.azPageCount(12, 3));
    }

    @Test
    public void theLastAzPageIsPulledBackSoItRendersAFullRow() {
        // 10 entries over 3 slots: the tail page starts at 7, not at 9, so no short row is drawn.
        assertArrayEquals(new int[]{0, 3, 6, 7}, DockPagingModel.azPageStarts(10, 3));
        assertEquals(7, DockPagingModel.azPageStart(10, 3, 3));
        assertEquals(10, DockPagingModel.azPageEnd(10, 3, 3));
        // A single overflowing entry shifts the second page by one.
        assertArrayEquals(new int[]{0, 1}, DockPagingModel.azPageStarts(4, 3));
    }

    @Test
    public void azCandidatesThatFitOnOnePageDoNotPaginate() {
        assertArrayEquals(new int[]{0}, DockPagingModel.azPageStarts(3, 3));
        assertArrayEquals(new int[]{0}, DockPagingModel.azPageStarts(1, 3));
        assertEquals(1, DockPagingModel.azPageCount(3, 3));
    }

    @Test
    public void azPageIndexesAreClampedIntoRange() {
        assertEquals(0, DockPagingModel.azPageStart(10, -5, 3));
        assertEquals(7, DockPagingModel.azPageStart(10, 99, 3));
    }

    @Test
    public void azPageBoundsNeverRunPastTheCandidates() {
        assertEquals(0, DockPagingModel.azPageStart(10, 0, 3));
        assertEquals(3, DockPagingModel.azPageEnd(10, 0, 3));
        assertEquals(2, DockPagingModel.azPageEnd(2, 0, 3));
        assertEquals(0, DockPagingModel.azPageEnd(0, 0, 3));
    }

    // ---------------------------------------------------------- page signature

    @Test
    public void theSignatureIsStableForTheSamePage() {
        Digest digest = new Digest(new String[]{"a", "b", "c", "d"}, new boolean[]{true, true, true, true});
        assertEquals(DockPagingModel.azPageSignature(digest, 0, 2),
            DockPagingModel.azPageSignature(digest, 0, 2));
    }

    @Test
    public void theSignatureSeparatesPagesKeysIconsAndSlotCounts() {
        Digest base = new Digest(new String[]{"a", "b", "c", "d"}, new boolean[]{true, true, true, true});
        int page0 = DockPagingModel.azPageSignature(base, 0, 2);
        assertNotEquals(page0, DockPagingModel.azPageSignature(base, 1, 2));
        assertNotEquals(page0, DockPagingModel.azPageSignature(base, 0, 3));

        Digest renamed = new Digest(new String[]{"a", "z", "c", "d"}, new boolean[]{true, true, true, true});
        assertNotEquals(page0, DockPagingModel.azPageSignature(renamed, 0, 2));

        // An icon that has since loaded must repaint the page.
        Digest iconless = new Digest(new String[]{"a", "b", "c", "d"}, new boolean[]{true, false, true, true});
        assertNotEquals(page0, DockPagingModel.azPageSignature(iconless, 0, 2));
    }

    @Test
    public void theSignatureToleratesMissingKeysAndEmptyLists() {
        Digest nullKey = new Digest(new String[]{null, "b"}, new boolean[]{false, false});
        DockPagingModel.azPageSignature(nullKey, 0, 2);
        DockPagingModel.azPageSignature(new Digest(new String[0], new boolean[0]), 0, 2);
        DockPagingModel.azPageSignature(null, 0, 2);
    }

    // --------------------------------------------------------- index handling

    @Test
    public void pageIndexesWrapInBothDirections() {
        assertEquals(0, DockPagingModel.wrap(3, 3));
        assertEquals(2, DockPagingModel.wrap(-1, 3));
        assertEquals(1, DockPagingModel.wrap(4, 3));
        assertEquals(0, DockPagingModel.wrap(-3, 3));
    }

    @Test
    public void wrappingHandlesEmptyAndSinglePageRows() {
        assertEquals(0, DockPagingModel.wrap(5, 0));
        assertEquals(0, DockPagingModel.wrap(-5, 1));
        assertEquals(0, DockPagingModel.wrap(0, -2));
    }

    @Test
    public void clampingKeepsAPageIndexInsideTheRow() {
        assertEquals(0, DockPagingModel.clampPage(-4, 3));
        assertEquals(2, DockPagingModel.clampPage(9, 3));
        assertEquals(1, DockPagingModel.clampPage(1, 3));
        assertEquals(0, DockPagingModel.clampPage(4, 0));
    }

    @Test
    public void overflowNeedsMoreThanOnePage() {
        assertFalse(DockPagingModel.hasOverflowPages(0));
        assertFalse(DockPagingModel.hasOverflowPages(1));
        assertTrue(DockPagingModel.hasOverflowPages(2));
    }

    @Test
    public void theVisualPositionFollowsTheFingerOnlyWhileTheRowIsMoving() {
        assertEquals(1.4f, DockPagingModel.visualPagePosition(true, true, false, 1.4f, 1), 0f);
        assertEquals(1.4f, DockPagingModel.visualPagePosition(true, false, true, 1.4f, 1), 0f);
        assertEquals(1f, DockPagingModel.visualPagePosition(true, false, false, 1.4f, 1), 0f);
        // No overflow: the settled page wins even mid-drag.
        assertEquals(1f, DockPagingModel.visualPagePosition(false, true, true, 1.4f, 1), 0f);
        assertEquals(0f, DockPagingModel.visualPagePosition(true, false, false, 1.4f, -2), 0f);
    }

    // ------------------------------------------------------------ drag physics

    @Test
    public void theCommitDistanceTakesTheDpFloorOnNarrowRowsAndTheWidthShareOnWideOnes() {
        // 42dp at density 2 = 84px, which beats 30% of a 200px row.
        assertEquals(84f, DockPagingModel.commitDistancePx(200f, DENSITY), 0.001f);
        assertEquals(216f, DockPagingModel.commitDistancePx(720f, DENSITY), 0.001f);
        // A row with no width yet still asks for the dp floor.
        assertEquals(84f, DockPagingModel.commitDistancePx(0f, DENSITY), 0.001f);
    }

    @Test
    public void aDragCommitsOnDistanceJustPastTheThreshold() {
        float commit = 216f;
        assertEquals(0, DockPagingModel.commitPageDelta(216f, 0f, 0f, commit, DENSITY));
        assertEquals(-1, DockPagingModel.commitPageDelta(216.5f, 0f, 0f, commit, DENSITY));
        assertEquals(1, DockPagingModel.commitPageDelta(-216.5f, 0f, 0f, commit, DENSITY));
        assertEquals(0, DockPagingModel.commitPageDelta(-215f, 0f, 0f, commit, DENSITY));
    }

    @Test
    public void aFlingCommitsShortOfTheDistanceThreshold() {
        float commit = 216f;
        // 56px travel = 28dp at density 2, the fling floor; 901px/s clears the velocity gate.
        assertEquals(1, DockPagingModel.commitPageDelta(-56f, 0f, -901f, commit, DENSITY));
        assertEquals(-1, DockPagingModel.commitPageDelta(56f, 0f, 901f, commit, DENSITY));
        // One pixel short of the travel floor.
        assertEquals(0, DockPagingModel.commitPageDelta(-55f, 0f, -901f, commit, DENSITY));
        // Exactly at the velocity gate is not past it.
        assertEquals(0, DockPagingModel.commitPageDelta(-56f, 0f, -900f, commit, DENSITY));
        // A fling whose direction disagrees with the travel is a bounce, not a page.
        assertEquals(0, DockPagingModel.commitPageDelta(-56f, 0f, 901f, commit, DENSITY));
    }

    @Test
    public void aDragThatIsNotDominantlyHorizontalNeverCommits() {
        float commit = 100f;
        assertEquals(-1, DockPagingModel.commitPageDelta(240f, 199f, 0f, commit, DENSITY));
        assertEquals(0, DockPagingModel.commitPageDelta(240f, 200f, 0f, commit, DENSITY));
        assertEquals(0, DockPagingModel.commitPageDelta(240f, 400f, 0f, commit, DENSITY));
    }

    @Test
    public void aDragHeadsForwardWhenTheFingerMovesLeft() {
        assertEquals(1, DockPagingModel.dragPageDelta(-1f));
        assertEquals(-1, DockPagingModel.dragPageDelta(1f));
        assertEquals(-1, DockPagingModel.dragPageDelta(0f));
    }

    @Test
    public void theSettleVelocityFallsBackToTheTravelWhenTheFlingIsSlow() {
        assertEquals(900f, DockPagingModel.settleVelocityHint(-50f, -900f), 0f);
        assertEquals(800f, DockPagingModel.settleVelocityHint(-100f, -50f), 0f);
    }

    @Test
    public void theSettleDurationShortensWithVelocityInsideAFixedBand() {
        assertEquals(410L, DockPagingModel.settleDurationMs(0f));
        assertEquals(410L, DockPagingModel.settleDurationMs(150f));
        assertEquals(280L, DockPagingModel.settleDurationMs(5200f));
        assertEquals(280L, DockPagingModel.settleDurationMs(-99999f));
        long mid = DockPagingModel.settleDurationMs(2675f);
        assertTrue(mid > 280L && mid < 410L);
    }

    @Test
    public void dragProgressEasesFromZeroToOneAndStopsThere() {
        assertEquals(0f, DockPagingModel.dragEasedProgress(0f, 200f), 0.0001f);
        assertEquals(1f, DockPagingModel.dragEasedProgress(200f, 200f), 0.0001f);
        assertEquals(1f, DockPagingModel.dragEasedProgress(-4000f, 200f), 0.0001f);
        float half = DockPagingModel.dragEasedProgress(100f, 200f);
        assertEquals((float) Math.sin(Math.PI * 0.25), half, 0.0001f);
        assertTrue(half > 0.5f);
    }

    @Test
    public void theVisualDragOffsetIsCappedInBothDirections() {
        // 38% of a 720px row = 273.6px, above the 18dp floor at density 2.
        assertEquals(-273.6f, DockPagingModel.dragVisualOffsetPx(-4000f, 720f, DENSITY), 0.01f);
        assertEquals(273.6f, DockPagingModel.dragVisualOffsetPx(4000f, 720f, DENSITY), 0.01f);
        assertEquals(-50f, DockPagingModel.dragVisualOffsetPx(-50f, 720f, DENSITY), 0.01f);
        // Narrow row: the dp floor keeps a usable travel.
        assertEquals(36f, DockPagingModel.dragVisualOffsetPx(4000f, 10f, DENSITY), 0.01f);
    }

    @Test
    public void theDragPositionMovesForwardOnALeftDragAndBackOnARightOne() {
        assertEquals(1.5f, DockPagingModel.dragPagePosition(1f, -10f, 0.5f, 4), 0.0001f);
        assertEquals(0.5f, DockPagingModel.dragPagePosition(1f, 10f, 0.5f, 4), 0.0001f);
    }

    @Test
    public void theDragPositionStaysInsideTheRealPageRange() {
        // Dragging back off page zero, and forward off the last page, both stick.
        assertEquals(0f, DockPagingModel.dragPagePosition(0f, 10f, 0.9f, 3), 0.0001f);
        assertEquals(2f, DockPagingModel.dragPagePosition(2f, -10f, 0.9f, 3), 0.0001f);
        assertEquals(0f, DockPagingModel.dragPagePosition(0f, -10f, 0.9f, 1), 0.0001f);
    }

    /** A fixed digest of paged entries: keys plus whether each icon has loaded. */
    private static final class Digest implements DockPagingModel.EntryDigest {
        private final String[] keys;
        private final boolean[] icons;

        Digest(String[] keys, boolean[] icons) {
            this.keys = keys;
            this.icons = icons;
        }

        @Override
        public int size() {
            return keys.length;
        }

        @Override
        public String keyAt(int index) {
            return keys[index];
        }

        @Override
        public boolean hasIconAt(int index) {
            return icons[index];
        }
    }
}
