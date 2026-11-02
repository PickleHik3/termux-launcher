package com.termux.app.place;

import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * TEMP diag(seam): never merge. The third set of seam timings: what the chrome's render sync and
 * the landing's place-state pass spend, step by step, who booked each render sync, and which
 * views measure slowly in the landing frame. Logs under tag SeamTime3, uptime ms first, each line
 * carrying the crossing's phase ({@code begin#n}, {@code firstMove#n}, {@code drag#n},
 * {@code landing#n}, {@code afterLanding#n}, {@code idle}); Trace sections Seam3:&lt;step&gt;.
 *
 * <p>Lines are written only while a crossing is in play: from its begin to
 * {@link #AFTER_LANDING_MS} after its landing.
 */
public final class SeamTime3 {
    public static final String TAG = "SeamTime3";
    /** How long after a landing its after-effects are still logged. */
    private static final long AFTER_LANDING_MS = 1500L;

    private static int sCrossing;
    @NonNull private static String sPhase = "idle";
    private static long sLandedAtMs = Long.MIN_VALUE;

    /** Who booked the pending render sync, and how many asks it absorbed. */
    @NonNull private static String sBookedBy = "-";
    @NonNull private static String sBookedIn = "-";
    private static int sAsksWhilePending;

    private SeamTime3() {}

    // ---- Phases ------------------------------------------------------------------------------

    /** A crossing begins, inside the travel frame of its first move. */
    public static void crossingBegin() {
        sCrossing++;
        phase("firstMove");
    }

    /** A travel frame begins: past the first move's own frame, the crossing is a drag. */
    public static void travelFrame() {
        if (sPhase.startsWith("firstMoveAfter")) phase("drag");
    }

    /**
     * A travel frame ended: what runs after the first move's frame (its layout, draw and posted
     * render sync) is labelled firstMoveAfter until the next travel frame.
     */
    public static void travelFrameDone() {
        if (sPhase.startsWith("firstMove#")) phase("firstMoveAfter");
    }

    public static void landingBegin() {
        phase("landing");
    }

    public static void landingEnd() {
        phase("afterLanding");
        sLandedAtMs = SystemClock.uptimeMillis();
    }

    private static void phase(@NonNull String name) {
        sPhase = name + "#" + sCrossing;
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " phase=" + sPhase);
    }

    /** Whether anything is logged now: a crossing in play, or a landing just past. */
    public static boolean active() {
        if (sPhase.equals("idle")) return false;
        if (sPhase.startsWith("afterLanding")
                && SystemClock.uptimeMillis() - sLandedAtMs > AFTER_LANDING_MS) {
            sPhase = "idle";
            return false;
        }
        return true;
    }

    @NonNull
    public static String phase() {
        return sPhase;
    }

    // ---- Steps -------------------------------------------------------------------------------

    public static long begin(@NonNull String step) {
        Trace.beginSection("Seam3:" + step);
        return System.nanoTime();
    }

    /** Ends a step; logs it while active when it took {@code minMs} or more. */
    public static void end(@NonNull String step, long t0, float minMs) {
        long ns = System.nanoTime() - t0;
        Trace.endSection();
        float ms = ns / 1e6f;
        if (ms >= minMs && active()) {
            Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " phase=" + sPhase + " step=" + step
                + " ms=" + fmt(ms));
        }
    }

    /** Runs {@code body} as a step logged at 2 ms or more. */
    public static void step(@NonNull String step, @NonNull Runnable body) {
        long t0 = begin(step);
        try {
            body.run();
        } finally {
            end(step, t0, 2f);
        }
    }

    /** A view's measure, logged while active at 3 ms or more (the root at 1 ms). */
    public static void measured(@NonNull View view, long t0, float minMs) {
        end("measure:" + view.getClass().getSimpleName()
            + (view.getId() != View.NO_ID ? "/" + idName(view) : ""), t0, minMs);
    }

    /** A view's layout, as {@link #measured}. */
    public static void laidOut(@NonNull View view, long t0, float minMs) {
        end("layout:" + view.getClass().getSimpleName()
            + (view.getId() != View.NO_ID ? "/" + idName(view) : ""), t0, minMs);
    }

    private static String idName(View view) {
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (RuntimeException e) {
            return Integer.toString(view.getId());
        }
    }

    // ---- Render syncs ------------------------------------------------------------------------

    /** An accessory render was asked for; {@code booked} when this ask posted the pass. */
    public static void renderAsked(boolean booked, boolean running) {
        if (!active()) return;
        String callers = callers(4);
        if (booked) {
            sBookedBy = callers;
            sBookedIn = sPhase;
            sAsksWhilePending = 0;
        } else if (!running) {
            sAsksWhilePending++;
        }
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " phase=" + sPhase + " renderAsk "
            + (booked ? "BOOKED" : running ? "inPass" : "coalesced") + " by " + callers);
    }

    /** The render sync about to run: how it came to run, who booked it, in which phase. */
    public static void renderRun(@NonNull String how) {
        if (!active()) return;
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " phase=" + sPhase + " renderRun how="
            + how + " bookedIn=" + sBookedIn + " asksAbsorbed=" + sAsksWhilePending
            + " bookedBy " + sBookedBy);
        sBookedBy = "-";
        sBookedIn = "-";
        sAsksWhilePending = 0;
    }

    /** {@code n} caller frames above the chrome's own entry points, innermost first. */
    @NonNull
    private static String callers(int n) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder();
        int taken = 0;
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            if (cls.endsWith("SeamTime3") || cls.endsWith("ChromeRenderer")) continue;
            if (taken > 0) out.append(" < ");
            out.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.')
                .append(e.getMethodName()).append(':').append(e.getLineNumber());
            if (++taken >= n) break;
        }
        return out.toString();
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
