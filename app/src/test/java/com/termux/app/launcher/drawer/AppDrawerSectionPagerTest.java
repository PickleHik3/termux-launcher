package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.wall.SettleSpring;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AppDrawerSectionPagerTest {

    private static final float W = 1000f;
    private static final float DENSITY = 2f;

    private static final class FakeClock implements AppDrawerSectionPager.Clock {
        AppDrawerSectionPager.Tick tick;
        long durationMs = -1;
        int starts;
        int cancels;

        @Override public void start(long durationMs, @NonNull AppDrawerSectionPager.Tick tick) {
            this.durationMs = durationMs;
            this.tick = tick;
            starts++;
        }

        @Override public void cancel() {
            tick = null;
            cancels++;
        }

        @Override public float springTime() {
            return 1f;
        }

        void run(float fraction) {
            if (tick != null) tick.onFraction(fraction);
        }
    }

    private FakeClock clock;
    private AppDrawerSectionPager pager;
    private final List<Float> positions = new ArrayList<>();
    private final List<String> settles = new ArrayList<>();

    @Before public void setUp() {
        clock = new FakeClock();
        pager = new AppDrawerSectionPager(clock);
        pager.setPageWidthPx(W);
        pager.addListener(new AppDrawerSectionPager.Listener() {
            @Override public void onPositionChanged(float position) {
                positions.add(position);
            }

            @Override public void onSettled(@NonNull AppDrawerSection section, boolean changed) {
                settles.add(section + ":" + changed);
            }
        });
    }

    @Test public void startsAtRestOnApps() {
        assertEquals(0f, pager.position(), 0f);
        assertEquals(AppDrawerSection.APPS, pager.settledSection());
        assertTrue(pager.isAtRest());
    }

    @Test public void dragFollowsTheFingerAndClampsAtBothEnds() {
        pager.beginDrag();
        assertFalse(pager.isAtRest());
        pager.dragBy(250f, W);
        assertEquals(0.25f, pager.position(), 1e-6f);
        pager.dragBy(5000f, W);
        assertEquals(1f, pager.position(), 0f);
        pager.dragBy(-9000f, W);
        assertEquals(0f, pager.position(), 0f);
        // No change reported for a drag that stays clamped.
        int reported = positions.size();
        pager.dragBy(-100f, W);
        assertEquals(reported, positions.size());
    }

    @Test public void dragWithoutBeginStartsOne() {
        pager.dragBy(100f, W);
        assertTrue(pager.isDragging());
        assertEquals(0.1f, pager.position(), 1e-6f);
    }

    @Test public void slowReleaseShortOfTheCommitFractionReturns() {
        pager.beginDrag();
        pager.dragBy(300f, W);
        pager.release(0f, W, DENSITY);
        assertTrue(pager.isSettling());
        clock.run(1f);
        assertEquals(0f, pager.position(), 0f);
        assertEquals(AppDrawerSection.APPS, pager.settledSection());
        assertEquals("[APPS:false]", settles.toString());
    }

    @Test public void slowReleasePastTheCommitFractionTurns() {
        pager.beginDrag();
        pager.dragBy(450f, W);
        pager.release(0f, W, DENSITY);
        clock.run(1f);
        assertEquals(1f, pager.position(), 0f);
        assertEquals(AppDrawerSection.CATEGORIES, pager.settledSection());
        assertEquals("[CATEGORIES:true]", settles.toString());
    }

    @Test public void aFlingTurnsWhateverTheDistance() {
        pager.beginDrag();
        pager.dragBy(50f, W);
        pager.release(3000f, W, DENSITY);
        clock.run(1f);
        assertEquals(AppDrawerSection.CATEGORIES, pager.settledSection());
    }

    /**
     * The emulator's fast swipe (DrawerSection log, 2026-11-21): 750 px of finger on a 1038 px
     * page, but the pages were moved by per-move deltas from the claim, so the travel spent
     * deciding and the lift's own last stretch were lost, the number stood at 0.25 and a swipe
     * that crossed most of the page went home. Absolute travel from the down keeps all of it.
     */
    @Test public void aFastSwipeCountsAllItsTravelFromTheDown() {
        float w = 1038f;
        float slop = 16f;
        pager.beginDrag();
        // Claimed at 150 px; the next event is already the lift, 750 px from the down.
        pager.dragTo(AppDrawerSectionPager.travelPastSlop(150f, slop), w);
        pager.dragTo(AppDrawerSectionPager.travelPastSlop(750f, slop), w);
        assertEquals((750f - slop) / w, pager.position(), 1e-4f);
        // Even with no velocity left at the lift, the distance turns the page.
        pager.release(0f, w, DENSITY);
        clock.run(1f);
        assertEquals(AppDrawerSection.CATEGORIES, pager.settledSection());
    }

    @Test public void aFastSwipeBackFromCategoriesTurnsBack() {
        float w = 1038f;
        pager.jumpTo(AppDrawerSection.CATEGORIES);
        pager.beginDrag();
        pager.dragTo(AppDrawerSectionPager.travelPastSlop(-750f, 16f), w);
        pager.release(0f, w, DENSITY);
        clock.run(1f);
        assertEquals(AppDrawerSection.APPS, pager.settledSection());
    }

    @Test public void aShortFlingPastHomesThresholdTurns() {
        // Short of the commit fraction, but thrown faster than PagePolicy's fling speed.
        pager.beginDrag();
        pager.dragTo(AppDrawerSectionPager.travelPastSlop(80f, 16f), W);
        pager.release(com.termux.app.wall.PagePolicy.FLING_DP_PER_SEC * DENSITY + 1f, W, DENSITY);
        clock.run(1f);
        assertEquals(AppDrawerSection.CATEGORIES, pager.settledSection());
    }

    @Test public void travelPastSlopSpendsTheSlopOnce() {
        assertEquals(0f, AppDrawerSectionPager.travelPastSlop(10f, 16f), 0f);
        assertEquals(4f, AppDrawerSectionPager.travelPastSlop(20f, 16f), 0f);
        assertEquals(-4f, AppDrawerSectionPager.travelPastSlop(-20f, 16f), 0f);
        assertEquals(20f, AppDrawerSectionPager.travelPastSlop(20f, 0f), 0f);
    }

    @Test public void aFlingAwayFromTheOnlyOtherPageStaysPut() {
        pager.beginDrag();
        pager.dragBy(50f, W);
        pager.release(-3000f, W, DENSITY);
        clock.run(1f);
        assertEquals(AppDrawerSection.APPS, pager.settledSection());
        assertEquals(0f, pager.position(), 0f);
    }

    @Test public void targetIndexIsPagePolicyOnTheTravelFromTheStartPage() {
        // From Categories, a drag back 450 px commits to Apps; 300 px returns.
        assertEquals(0, AppDrawerSectionPager.targetIndex(1, 0.55f, 0f, W, DENSITY));
        assertEquals(1, AppDrawerSectionPager.targetIndex(1, 0.7f, 0f, W, DENSITY));
        // Past a third and thrown back towards the start: returns, never the page beyond.
        assertEquals(0, AppDrawerSectionPager.targetIndex(0, 0.5f, -600f, W, DENSITY));
    }

    @Test public void settleRunsTheSpringAndLandsExactlyOnce() {
        pager.beginDrag();
        pager.dragBy(500f, W);
        pager.release(0f, W, DENSITY);
        assertEquals(1, clock.starts);
        assertTrue(clock.durationMs > 0);
        float last = pager.position();
        for (float f = 0.1f; f < 1f; f += 0.1f) {
            clock.run(f);
            assertTrue(pager.position() >= last - 1e-6f);
            assertTrue(pager.position() <= 1f);
            last = pager.position();
        }
        assertTrue(settles.isEmpty());
        clock.run(1f);
        clock.run(1f);
        assertEquals(1f, pager.position(), 0f);
        assertEquals(1, settles.size());
        assertTrue(pager.isAtRest());
    }

    @Test public void springPositionMatchesSettleSpringAtTheStart() {
        SettleSpring spring = AppDrawerSectionPager.settleSpring(0.6f, 1, W, 0f, 1f);
        assertEquals(0.6f, AppDrawerSectionPager.positionAt(spring, 1, W, 0f), 1e-5f);
        assertEquals(1f, AppDrawerSectionPager.positionAt(spring, 1, W, 10f), 1e-3f);
        assertEquals(1f, AppDrawerSectionPager.positionAt(spring, 1, 0f, 0.1f), 0f);
    }

    @Test public void settleToAnimatesAndNotifiesAChange() {
        pager.settleTo(AppDrawerSection.CATEGORIES);
        assertTrue(pager.isSettling());
        clock.run(1f);
        assertEquals("[CATEGORIES:true]", settles.toString());
    }

    @Test public void settleToTheSettledSectionLandsAtOnceWithNoChange() {
        pager.settleTo(AppDrawerSection.APPS);
        assertFalse(pager.isSettling());
        assertEquals(0, clock.starts);
        assertEquals("[APPS:false]", settles.toString());
    }

    @Test public void jumpIsSilentAndCancelsASettle() {
        pager.settleTo(AppDrawerSection.CATEGORIES);
        clock.run(0.3f);
        pager.jumpTo(AppDrawerSection.CATEGORIES);
        assertFalse(pager.isSettling());
        assertEquals(1f, pager.position(), 0f);
        assertEquals(1, clock.cancels);
        assertEquals("[CATEGORIES:false]", settles.toString());
        // The cancelled clock's late tick does nothing.
        clock.run(1f);
        assertEquals(1, settles.size());
    }

    @Test public void jumpToWhereItAlreadyStandsReportsNothing() {
        pager.jumpTo(AppDrawerSection.APPS);
        assertTrue(positions.isEmpty());
        assertTrue(settles.isEmpty());
    }

    @Test public void cancelGoesBackToTheSettledSectionWithoutAChange() {
        pager.beginDrag();
        pager.dragBy(600f, W);
        pager.cancel();
        assertEquals(0f, pager.position(), 0f);
        assertTrue(pager.isAtRest());
        assertEquals("[APPS:false]", settles.toString());
    }

    @Test public void cancelDuringASettleDropsTheClock() {
        pager.settleTo(AppDrawerSection.CATEGORIES);
        clock.run(0.4f);
        pager.cancel();
        assertFalse(pager.isSettling());
        assertEquals(0f, pager.position(), 0f);
        assertEquals(AppDrawerSection.APPS, pager.settledSection());
    }

    @Test public void catchingASettleKeepsThePositionAndContinuesTheDrag() {
        pager.settleTo(AppDrawerSection.CATEGORIES);
        clock.run(0.3f);
        float caught = pager.position();
        assertTrue(caught > 0f && caught < 1f);
        pager.beginDrag();
        assertFalse(pager.isSettling());
        assertEquals(caught, pager.position(), 0f);
        pager.dragBy(-100f, W);
        assertEquals(caught - 0.1f, pager.position(), 1e-5f);
        assertTrue(settles.isEmpty());
    }

    @Test public void releaseWithoutADragDoesNothing() {
        pager.release(1000f, W, DENSITY);
        assertEquals(0, clock.starts);
        assertTrue(settles.isEmpty());
    }

    @Test public void zeroWidthLandsAtOnce() {
        AppDrawerSectionPager bare = new AppDrawerSectionPager(clock);
        bare.settleTo(AppDrawerSection.CATEGORIES);
        assertEquals(1f, bare.position(), 0f);
        assertEquals(AppDrawerSection.CATEGORIES, bare.settledSection());
    }

    @Test public void clampHandlesNaN() {
        assertEquals(0f, AppDrawerSectionPager.clamp(Float.NaN), 0f);
        assertEquals(1f, AppDrawerSectionPager.clamp(4f), 0f);
        assertEquals(0f, AppDrawerSectionPager.clamp(-4f), 0f);
    }
}
