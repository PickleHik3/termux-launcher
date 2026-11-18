package com.termux.app.place;

/**
 * What a crossing between Home and the Workspace does with the top band (the glass strip behind
 * the system bar and the window bar's glass): when the two ends' looks differ, both ends are
 * built as the crossing begins, the arriving one under the arriving page's look id, and the band
 * crosses between them by {@link SeamFade}. The landing's re-dress reads the same look id, so it
 * builds what the band already wears. Builds nothing itself.
 */
public final class TopBandFadePlan {

    /** No look id: the band is not built at the begin. */
    public static final int NONE = Integer.MIN_VALUE;

    private TopBandFadePlan() {}

    /**
     * The look id the arriving end of the band is built under as the crossing begins, or
     * {@link #NONE} where the ends wear the same look (the first mid-crossing re-dress builds it,
     * as before).
     */
    public static int arrivingLookAtBegin(int hereLookId, int thereLookId) {
        return hereLookId == thereLookId ? NONE : thereLookId;
    }

    /** The look id the band stands in at {@code share} of the way: the arriving end's from 1. */
    public static int settledLook(int hereLookId, int thereLookId, float share) {
        return share >= 1f ? thereLookId : hereLookId;
    }
}
