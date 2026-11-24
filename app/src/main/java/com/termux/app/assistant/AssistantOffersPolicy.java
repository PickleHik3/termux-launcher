package com.termux.app.assistant;

import androidx.annotation.NonNull;

import com.termux.app.launcher.drawer.AppDrawerCategoryNudgePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which of the app's own offers the Assistant page shows (#383): apps waiting to be sorted and a
 * tmp folder that has grown. Pure: it takes what was measured and what was dismissed and answers
 * at most two offers in a fixed order. No model is involved, so it answers the same without one.
 *
 * <p>Not now keeps the value at dismissal; the offer returns once its value has grown by the
 * offer's threshold again past that value.
 */
final class AssistantOffersPolicy {

    /** "Not measured" and "never sorted" for a value, and "never dismissed" for a stored one. */
    static final long NONE = -1L;

    /** Apps waiting to be sorted: the drawer nudge's own minimum, and the growth that brings it back. */
    static final int UNSORTED_THRESHOLD = AppDrawerCategoryNudgePolicy.MIN_PENDING;

    /** A GB of tmp, and the growth that brings the offer back. */
    static final long TMP_THRESHOLD_BYTES = 1024L * 1024L * 1024L;

    /** The tmp size is read at most this often. */
    static final long MEASURE_INTERVAL_MS = 60L * 60L * 1000L;

    /** Entries untouched this long are the ones Clear old files removes. */
    static final long CLEAR_AGE_MS = 24L * 60L * 60L * 1000L;

    enum Kind { UNSORTED, TMP }

    /** One offer and the value it was made for, which Not now stores. */
    static final class Offer {
        @NonNull final Kind kind;
        final long value;

        Offer(@NonNull Kind kind, long value) {
            this.kind = kind;
            this.value = value;
        }
    }

    private AssistantOffersPolicy() {}

    /**
     * @param pendingApps apps waiting to be sorted; {@link #NONE} when a sort has never run or the count is not in yet
     * @param tmpBytes size of the tmp folder; {@link #NONE} when not measured
     * @param dismissedPending the pending count at Not now, or {@link #NONE}
     * @param dismissedTmpBytes the tmp size at Not now, or {@link #NONE}
     */
    @NonNull
    static List<Offer> offers(long pendingApps, long tmpBytes, long dismissedPending, long dismissedTmpBytes) {
        List<Offer> out = null;
        if (due(pendingApps, UNSORTED_THRESHOLD, dismissedPending)) {
            out = new ArrayList<>(2);
            out.add(new Offer(Kind.UNSORTED, pendingApps));
        }
        if (due(tmpBytes, TMP_THRESHOLD_BYTES, dismissedTmpBytes)) {
            if (out == null) out = new ArrayList<>(2);
            out.add(new Offer(Kind.TMP, tmpBytes));
        }
        return out == null ? Collections.<Offer>emptyList() : out;
    }

    static boolean due(long value, long threshold, long dismissed) {
        if (value < threshold) return false;
        return dismissed < 0 || value >= dismissed + threshold;
    }

    /**
     * Whether a stored dismissal is spent: the value is measured and has fallen back under its
     * threshold (the apps were sorted, the folder cleared), so the next climb starts fresh.
     */
    static boolean forgetDismissal(long value, long threshold, long dismissed) {
        return dismissed >= 0 && value >= 0 && value < threshold;
    }

    /** Whether the tmp size is due for a fresh read. */
    static boolean measureDue(long lastMeasuredMs, long nowMs) {
        return lastMeasuredMs <= 0 || nowMs < lastMeasuredMs || nowMs - lastMeasuredMs >= MEASURE_INTERVAL_MS;
    }
}
