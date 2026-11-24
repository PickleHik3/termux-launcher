package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.assistant.AssistantOffersPolicy.Kind;
import com.termux.app.assistant.AssistantOffersPolicy.Offer;
import com.termux.app.launcher.drawer.AppDrawerCategoryNudgePolicy;
import com.termux.shared.file.OlderThanSweeper;

import org.junit.Test;

import java.util.List;

public class AssistantOffersPolicyTest {

    private static final long GB = AssistantOffersPolicy.TMP_THRESHOLD_BYTES;
    private static final long NONE = AssistantOffersPolicy.NONE;

    private static List<Offer> offers(long pending, long tmpBytes, long dPending, long dTmp) {
        return AssistantOffersPolicy.offers(pending, tmpBytes, dPending, dTmp);
    }

    @Test public void unsortedThresholdIsTheDrawerNudgesMinimum() {
        assertEquals(AppDrawerCategoryNudgePolicy.MIN_PENDING, AssistantOffersPolicy.UNSORTED_THRESHOLD);
    }

    @Test public void nothingBelowTheThresholds() {
        assertTrue(offers(5, GB - 1, NONE, NONE).isEmpty());
    }

    @Test public void neverSortedAndUnmeasuredSayNothing() {
        assertTrue(offers(NONE, NONE, NONE, NONE).isEmpty());
    }

    @Test public void unsortedAtTheMinimum() {
        List<Offer> o = offers(6, 0, NONE, NONE);
        assertEquals(1, o.size());
        assertEquals(Kind.UNSORTED, o.get(0).kind);
        assertEquals(6, o.get(0).value);
    }

    @Test public void tmpAtOneGb() {
        List<Offer> o = offers(NONE, GB, NONE, NONE);
        assertEquals(1, o.size());
        assertEquals(Kind.TMP, o.get(0).kind);
    }

    @Test public void atMostTwoInAStableOrder() {
        List<Offer> o = offers(40, 9 * GB, NONE, NONE);
        assertEquals(2, o.size());
        assertEquals(Kind.UNSORTED, o.get(0).kind);
        assertEquals(Kind.TMP, o.get(1).kind);
    }

    @Test public void offersNeedNoModel() {
        // The policy has no model input at all: the same values give the same offers.
        assertEquals(2, offers(12, 3 * GB, NONE, NONE).size());
    }

    @Test public void notNowHoldsUntilGrowthByTheThreshold() {
        assertTrue(offers(11, 0, 6, NONE).isEmpty());
        assertEquals(1, offers(12, 0, 6, NONE).size());
        assertTrue(offers(NONE, 2 * GB - 1, NONE, GB).isEmpty());
        assertEquals(1, offers(NONE, 2 * GB, NONE, GB).size());
    }

    @Test public void dismissingOneLeavesTheOther() {
        List<Offer> o = offers(8, 2 * GB, 8, NONE);
        assertEquals(1, o.size());
        assertEquals(Kind.TMP, o.get(0).kind);
    }

    @Test public void aDismissalIsSpentOnceTheValueFallsUnderItsThreshold() {
        assertTrue(AssistantOffersPolicy.forgetDismissal(2, 6, 8));
        assertFalse(AssistantOffersPolicy.forgetDismissal(7, 6, 8));
        assertFalse(AssistantOffersPolicy.forgetDismissal(NONE, 6, 8));
        assertFalse(AssistantOffersPolicy.forgetDismissal(2, 6, NONE));
    }

    @Test public void measuredAtMostOncePerHour() {
        assertTrue(AssistantOffersPolicy.measureDue(0, 5_000));
        assertFalse(AssistantOffersPolicy.measureDue(1_000, 1_000 + AssistantOffersPolicy.MEASURE_INTERVAL_MS - 1));
        assertTrue(AssistantOffersPolicy.measureDue(1_000, 1_000 + AssistantOffersPolicy.MEASURE_INTERVAL_MS));
        assertTrue(AssistantOffersPolicy.measureDue(9_000, 100));
    }

    @Test public void clearingReportsWhatWasFreed() {
        long[] askedAge = new long[1];
        long freed = AssistantTmpHousekeeping.clearOld(age -> {
            askedAge[0] = age;
            OlderThanSweeper.Result r = new OlderThanSweeper.Result();
            r.bytesFreed = 2_254_857_830L;
            return r;
        });
        assertEquals(2_254_857_830L, freed);
        assertEquals(24L * 60 * 60 * 1000, askedAge[0]);
    }

    @Test public void aFailedClearFreesNothing() {
        assertEquals(0L, AssistantTmpHousekeeping.clearOld(age -> { throw new IllegalStateException(); }));
    }
}
