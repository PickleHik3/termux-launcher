package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Decides how a model is loaded from the memory the phone has free right now: which accelerator,
 * and how large a context window, or that it cannot be loaded without starving the home screen.
 *
 * <p>Every load goes through here, because a model that does not fit does not fail — it takes the
 * phone down with it. On a Nothing Phone 2 (11 GiB, about 5.8 GB free) gemma-4-e4b at the 32k
 * window the RAM tier used to pick drove free memory from 5.7 GB to 1.4 GB in twelve seconds and
 * kept going: the system killed everything else, thrashed swap, and the launcher stopped
 * answering. At 4k the same load fit with 2.6–3 GB to spare. A context window is the one knob that
 * turns a freeze into a working model, so the budget spends it.
 *
 * <p>The estimate is a fixed part plus a per-token part (the KV cache), both proportional to the
 * model file, per accelerator. Fitted to that phone, erring high:
 * <ul>
 *   <li>LiteRT GPU holds about three quarters of the file resident (weights uploaded to GPU
 *       buffers, which are ordinary RAM on a phone). Measured: 2.5–3.2 GB at 4k for a 3.66 GB
 *       file.</li>
 *   <li>LiteRT CPU keeps its weights in a file-backed cache the system can reclaim, so only a third
 *       of that is new pressure. Measured: 1.6 GB at 4k.</li>
 *   <li>MNN loads its weights outright.</li>
 *   <li>Per token, about the file size divided by 19000 — some 190 KB for gemma-4-e4b. 8k did not
 *       fit where 4k did, which puts the true figure no lower than about 115 KB.</li>
 * </ul>
 *
 * <p>What stays free for everything else is the larger of 1.5 GiB and 15% of RAM. The window
 * shrinks by halves from what was asked for down to a 4k floor; the accelerators are tried in the
 * model's order, so a phone that cannot hold the GPU load at 4k still gets the CPU one — at the
 * floor only, since the fallback is there to keep the model usable, and a larger CPU window was the
 * one configuration the calibration could not survive (it took the phone's network down). A load
 * whose process was killed last time is not repeated as it was: that accelerator is held to half
 * the window it died at.
 *
 * <p>Pure on purpose: no Android, so the numbers above are pinned by {@code TaiLoadBudgetTest}.
 */
public final class TaiLoadBudget {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    /** The smallest window a chat model is loaded with; below it a load is refused instead. */
    public static final int FLOOR_CONTEXT = 4096;
    public static final long MIN_RESERVE_BYTES = 1536L * MIB;
    public static final int RESERVE_PERCENT = 15;
    /** File bytes per context token of KV cache. */
    static final long FILE_BYTES_PER_TOKEN_DIVISOR = 19_000L;

    private TaiLoadBudget() {
    }

    /** What the rest of the phone keeps: the larger of 1.5 GiB and 15% of RAM. */
    public static long reserveBytes(long physicalBytes) {
        return Math.max(MIN_RESERVE_BYTES, physicalBytes / 100L * RESERVE_PERCENT);
    }

    /** Estimated new memory pressure of a load, in bytes. */
    public static long estimateBytes(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                     boolean encoders, int contextTokens) {
        long fixed;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) {
            fixed = fileBytes;
        } else if ("cpu".equals(accelerator)) {
            fixed = fileBytes * 3L / 10L;
        } else {
            fixed = fileBytes * 3L / 4L;
        }
        if (encoders) fixed += fileBytes / 10L;
        long perToken = Math.max(1L, fileBytes / FILE_BYTES_PER_TOKEN_DIVISOR);
        return fixed + perToken * Math.max(0, contextTokens);
    }

    public static final class Request {
        @NonNull final String backend;
        final long fileBytes;
        final boolean encoders;
        final long physicalBytes;
        final long availableBytes;
        @NonNull final List<String> accelerators;
        final int capContext;
        @Nullable final String crashedAccelerator;
        final int crashedContext;

        /**
         * @param accelerators       in the order to try them; one entry when the caller chose
         * @param capContext         the largest window wanted: the setting, the request, the RAM tier
         *                           and the model's own limit already applied
         * @param crashedAccelerator the accelerator of a load of this model whose process was killed
         *                           mid-load, or {@code null}
         * @param crashedContext     the window that load asked for; {@code 0} when unknown
         */
        public Request(@NonNull String backend, long fileBytes, boolean encoders, long physicalBytes,
                       long availableBytes, @NonNull List<String> accelerators, int capContext,
                       @Nullable String crashedAccelerator, int crashedContext) {
            this.backend = backend;
            this.fileBytes = fileBytes;
            this.encoders = encoders;
            this.physicalBytes = physicalBytes;
            this.availableBytes = availableBytes;
            this.accelerators = Collections.unmodifiableList(accelerators);
            this.capContext = capContext;
            this.crashedAccelerator = crashedAccelerator;
            this.crashedContext = crashedContext;
        }
    }

    public static final class Plan {
        public final boolean fits;
        /** The accelerator to load with; the first one tried when nothing fits. */
        @Nullable public final String accelerator;
        public final int contextWindow;
        public final long estimatedBytes;
        public final long availableBytes;
        public final long reserveBytes;
        /** Whether free memory was known; without it the plan is the floor, unmeasured. */
        public final boolean measured;

        Plan(boolean fits, @Nullable String accelerator, int contextWindow, long estimatedBytes,
             long availableBytes, long reserveBytes, boolean measured) {
            this.fits = fits;
            this.accelerator = accelerator;
            this.contextWindow = contextWindow;
            this.estimatedBytes = estimatedBytes;
            this.availableBytes = availableBytes;
            this.reserveBytes = reserveBytes;
            this.measured = measured;
        }

        /** Free memory a load of {@link #estimatedBytes} would need, reserve included. */
        public long neededFreeBytes() {
            return estimatedBytes + reserveBytes;
        }
    }

    /** The smallest window this request may be given: the floor, or the model's own limit below it. */
    static int floor(int capContext) {
        return capContext > 0 ? Math.min(FLOOR_CONTEXT, capContext) : FLOOR_CONTEXT;
    }

    @NonNull
    public static Plan plan(@NonNull Request r) {
        int floor = floor(r.capContext);
        int cap = Math.max(floor, r.capContext);
        long reserve = reserveBytes(r.physicalBytes);
        String first = r.accelerators.isEmpty() ? null : r.accelerators.get(0);
        if (r.availableBytes <= 0L || r.physicalBytes <= 0L || r.fileBytes <= 0L) {
            long estimate = first == null ? 0L : estimateBytes(r.backend, first, r.fileBytes, r.encoders, floor);
            return new Plan(first != null, first, floor, estimate, r.availableBytes, reserve, false);
        }
        long spendable = r.availableBytes - reserve;
        for (int i = 0; i < r.accelerators.size(); i++) {
            String accelerator = r.accelerators.get(i);
            int limit = i == 0 ? cap : floor;
            if (accelerator.equals(r.crashedAccelerator)) {
                int crashedAt = r.crashedContext > 0 ? r.crashedContext : cap;
                limit = Math.min(limit, crashedAt / 2);
                if (limit < floor) continue;
            }
            for (int context = limit; ; context = Math.max(floor, context / 2)) {
                long estimate = estimateBytes(r.backend, accelerator, r.fileBytes, r.encoders, context);
                if (estimate <= spendable) {
                    return new Plan(true, accelerator, context, estimate, r.availableBytes, reserve, true);
                }
                if (context == floor) break;
            }
        }
        long smallest = first == null ? 0L : estimateBytes(r.backend, cheapest(r), r.fileBytes, r.encoders, floor);
        return new Plan(false, first, floor, smallest, r.availableBytes, reserve, true);
    }

    /** The accelerator whose floor load costs least, for telling the user how much would be needed. */
    @NonNull
    private static String cheapest(@NonNull Request r) {
        String best = r.accelerators.get(0);
        long bestBytes = Long.MAX_VALUE;
        for (String accelerator : r.accelerators) {
            long bytes = estimateBytes(r.backend, accelerator, r.fileBytes, r.encoders, floor(r.capContext));
            if (bytes < bestBytes) {
                bestBytes = bytes;
                best = accelerator;
            }
        }
        return best;
    }

    /** Standard phone RAM sizes, in GiB; a device is the smallest one its usable RAM fits under. */
    private static final int[] RAM_CLASSES_GIB = {2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32, 48, 64};

    /**
     * The RAM a phone is sold with, from the RAM the kernel reports. The kernel keeps a carve-out,
     * so a 12 GB phone reports about 11 GiB and would fail a "12 GB" recommendation it meets.
     * Android's advertisedMem answers the same question but counts swap sold as RAM (RAM Booster),
     * which is how a 12 GB phone came to be sized as 16 GB.
     */
    public static long ramClassBytes(long totalMemBytes) {
        if (totalMemBytes <= 0L) return 0L;
        for (int gib : RAM_CLASSES_GIB) {
            if (totalMemBytes <= gib * GIB) return gib * GIB;
        }
        return totalMemBytes;
    }
}
