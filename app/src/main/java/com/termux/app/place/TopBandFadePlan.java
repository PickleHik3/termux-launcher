package com.termux.app.place;

/**
 * What a crossing between Home and the Terminal does with the top band (the glass strip behind
 * the system bar and the window bar's glass): the chrome wears one look on every page
 * (ADR 0031), so nothing is built for the arriving end as the crossing begins; the first re-dress
 * on the way builds it beside the end the band began with, and the band crosses between them by
 * {@link SeamFade}. Builds nothing itself.
 */
public final class TopBandFadePlan {

    /** No look id: the reads answer the page each surface stands on. */
    public static final int NONE = Integer.MIN_VALUE;

    private TopBandFadePlan() {}
}
