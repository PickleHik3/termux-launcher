package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides how a model is loaded from the memory the phone has free right now: which accelerator,
 * how large a context window, which idle residents to evict for it, or that it cannot be loaded
 * without starving the home screen.
 *
 * <p>Every load goes through here, because a model that does not fit does not fail — it takes the
 * phone down with it. On a Nothing Phone 2 (11 GiB, about 5.8 GB free) gemma-4-e4b at the 32k
 * window the RAM tier used to pick drove free memory from 5.7 GB to 1.4 GB in twelve seconds and
 * kept going: the system killed everything else, thrashed swap, and the launcher stopped
 * answering. At 4k the same load fit with 2.6–3 GB to spare. A context window is the one knob that
 * turns a freeze into a working model, so the budget spends it.
 *
 * <p><b>The estimate.</b> When {@link TaiRuntimeHistory} holds a measured MemAvailable drop for
 * this model, accelerator and window bucket, the estimate is the largest drop recorded plus 10%
 * ({@code "measured"}). Otherwise it is a ratio of the model file ({@code "ratio"}): a fixed part
 * plus a per-token part (the KV cache), per accelerator, split into what the kernel can reclaim
 * and what it cannot. Fitted on that phone, erring high:
 * <ul>
 *   <li>LiteRT GPU holds about three quarters of the file resident (weights uploaded to GPU
 *       buffers, which are ordinary RAM on a phone), none of it reclaimable. Measured: 2.4–4.2 GB
 *       at 4k for a 3.66 GB file across four runs; ~3.2 GB after LiteRT-LM 0.17.1.</li>
 *   <li>LiteRT CPU maps the file and reads the weights through the page cache, which the kernel
 *       reclaims under pressure and MemAvailable already counts as available. Only the KV cache
 *       and a small anonymous part — 5% of the file, for the interpreter's own buffers — are new
 *       pressure. Measured: a 0.45 GB MemAvailable drop at 16k, against 4.25 GB estimated before
 *       the split.</li>
 *   <li>MNN loads its weights outright, none of it reclaimable.</li>
 *   <li>Per token, about the file size divided by 19000 — some 190 KB for gemma-4-e4b. 8k did not
 *       fit where 4k did, which puts the true figure no lower than about 115 KB.</li>
 * </ul>
 *
 * <p><b>The floor.</b> What stays free for everything else is tied to Android's own line: twice
 * {@code MemoryInfo.threshold} (the level at which the system starts killing cached apps), never
 * below 512 MiB. On pong the threshold is 315 MB, so the floor is 630 MB; the fixed reserve it
 * replaces was max(1.5 GiB, 15% of the RAM class) = 1.8 GB on the same phone, about six times
 * Android's line, and it kept E2B-on-GPU (≈2.5 GB) out of reach in a normal daily-driver state
 * (3.1 GB free with YouTube in front). A ratio estimate is a guess, so it carries a margin of a
 * quarter of itself on top of the floor; a measured worst case does not. When the threshold is not
 * known the old reserve stays in force, margin-free.
 *
 * <p><b>The ladder.</b> At each step — first the requested accelerator at the wanted window, then
 * halving down to a 4k floor, then the next accelerator at the floor only — a load that fits with
 * what is free is taken as is; one that does not is retried with idle residents evicted in the
 * order the caller lists them (embeddings, then STT, then idle chat; never a busy one), taking the
 * shortest prefix that covers the shortfall; only then does the window shrink. The next
 * accelerator is held to the floor since the fallback is there to keep the model usable, and a
 * larger CPU window was the one configuration the calibration could not survive (it took the
 * phone's network down). An automatic GPU load is capped at 4k as well: 8k cost ~0.8 GB more on
 * pong and slowed decode from 10.5 to 9.8 tok/s, and both LiteRT-LM and Gallery default GPU to
 * 4096; an explicit window above that is honoured, subject to the budget. A load whose process was
 * killed last time is not repeated as it was: that accelerator is held to half the window it died
 * at.
 *
 * <p>Pure on purpose: no Android, so the numbers above are pinned by {@code TaiLoadBudgetTest}.
 */
public final class TaiLoadBudget {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    /** The smallest window a chat model is loaded with; below it a load is refused instead. */
    public static final int FLOOR_CONTEXT = 4096;
    /** The largest window an automatic GPU load gets; an explicit setting may go above it. */
    public static final int GPU_AUTO_CONTEXT = 4096;
    /** The pre-threshold reserve, still used when {@code MemoryInfo.threshold} is not known. */
    public static final long MIN_RESERVE_BYTES = 1536L * MIB;
    public static final int RESERVE_PERCENT = 15;
    /** The floor never drops below this, whatever the threshold says. */
    public static final long MIN_FLOOR_BYTES = 512L * MIB;
    public static final int FLOOR_THRESHOLD_MULTIPLIER = 2;
    /** Added to a ratio estimate on top of the floor; a measured estimate carries none. */
    public static final int RATIO_MARGIN_PERCENT = 25;
    /** Added to the largest measured drop before it is used as the estimate. */
    public static final int MEASURED_HEADROOM_PERCENT = 10;
    /** File bytes per context token of KV cache. */
    static final long FILE_BYTES_PER_TOKEN_DIVISOR = 19_000L;
    /** The anonymous share of a LiteRT CPU load: the interpreter's own buffers, not the mapped weights. */
    static final int LITERT_CPU_ANON_PERCENT = 5;

    public static final String SOURCE_MEASURED = "measured";
    public static final String SOURCE_RATIO = "ratio";

    private TaiLoadBudget() {
    }

    /** Measured load costs by accelerator and window; {@code 0} when nothing was recorded. */
    public interface History {
        long measuredBytes(@NonNull String accelerator, int contextTokens);
    }

    public static final History NO_HISTORY = (accelerator, contextTokens) -> 0L;

    /** The pre-threshold reserve: the larger of 1.5 GiB and 15% of RAM. */
    public static long reserveBytes(long physicalBytes) {
        return Math.max(MIN_RESERVE_BYTES, physicalBytes / 100L * RESERVE_PERCENT);
    }

    /**
     * What stays free for the rest of the phone: twice Android's low-memory threshold, at least
     * 512 MiB; the old reserve when the threshold is unknown ({@code <= 0}).
     */
    public static long floorBytes(long thresholdBytes, long physicalBytes) {
        if (thresholdBytes <= 0L) return reserveBytes(physicalBytes);
        return Math.max(MIN_FLOOR_BYTES, thresholdBytes * FLOOR_THRESHOLD_MULTIPLIER);
    }

    /** The cost of one load: what it adds to memory pressure, what it maps reclaimably, and where the figure came from. */
    public static final class Estimate {
        /** Bytes the load takes away from MemAvailable for as long as it is resident. */
        public final long nonReclaimableBytes;
        /** File-backed bytes the kernel can drop and MemAvailable already counts; informational. */
        public final long reclaimableBytes;
        /** {@link #SOURCE_MEASURED} or {@link #SOURCE_RATIO}. */
        @NonNull public final String source;

        Estimate(long nonReclaimableBytes, long reclaimableBytes, @NonNull String source) {
            this.nonReclaimableBytes = nonReclaimableBytes;
            this.reclaimableBytes = reclaimableBytes;
            this.source = source;
        }

        /** A recorded worst case plus its headroom. */
        @NonNull
        public static Estimate measured(long worstMeasuredBytes) {
            return new Estimate(worstMeasuredBytes + worstMeasuredBytes * MEASURED_HEADROOM_PERCENT / 100L, 0L, SOURCE_MEASURED);
        }

        @NonNull
        public static Estimate ratio(long nonReclaimableBytes, long reclaimableBytes) {
            return new Estimate(nonReclaimableBytes, reclaimableBytes, SOURCE_RATIO);
        }

        public boolean isMeasured() {
            return SOURCE_MEASURED.equals(source);
        }
    }

    /** The estimate for a load: the measured worst case when history has one for this accelerator and window, else the ratio. */
    @NonNull
    public static Estimate estimate(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                    boolean encoders, int contextTokens, @NonNull History history) {
        long measured = history.measuredBytes(accelerator, contextTokens);
        if (measured > 0L) return Estimate.measured(measured);
        return Estimate.ratio(estimateBytes(backend, accelerator, fileBytes, encoders, contextTokens),
            reclaimableBytes(backend, accelerator, fileBytes));
    }

    /** The ratio estimate of a load's new, non-reclaimable memory pressure, in bytes. */
    public static long estimateBytes(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                     boolean encoders, int contextTokens) {
        long fixed;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) {
            fixed = fileBytes;
        } else if ("cpu".equals(accelerator)) {
            fixed = fileBytes / 100L * LITERT_CPU_ANON_PERCENT;
        } else {
            fixed = fileBytes * 3L / 4L;
        }
        if (encoders) fixed += fileBytes / 10L;
        long perToken = Math.max(1L, fileBytes / FILE_BYTES_PER_TOKEN_DIVISOR);
        return fixed + perToken * Math.max(0, contextTokens);
    }

    /** The file-backed part of a load the kernel can reclaim: the mapped weights of a LiteRT CPU load, nothing else. */
    public static long reclaimableBytes(@NonNull String backend, @NonNull String accelerator, long fileBytes) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) return 0L;
        return "cpu".equals(accelerator) ? fileBytes : 0L;
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
        final boolean explicitContext;
        final long thresholdBytes;
        @NonNull final History history;
        @NonNull final List<TaiResidency.Entry> evictable;

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
            this(backend, fileBytes, encoders, physicalBytes, availableBytes, accelerators, capContext,
                crashedAccelerator, crashedContext, false, 0L, NO_HISTORY, Collections.<TaiResidency.Entry>emptyList());
        }

        /**
         * @param explicitContext whether the user or the request set the window; an automatic one
         *                        is capped at {@link #GPU_AUTO_CONTEXT} on the GPU
         * @param thresholdBytes  Android's {@code MemoryInfo.threshold}; {@code 0} keeps the old reserve
         * @param history         measured load costs for this model on this device
         * @param evictable       idle residents this load may close, in eviction order; the caller
         *                        leaves out what the load replaces anyway (already credited to
         *                        {@code availableBytes}) and anything busy
         */
        public Request(@NonNull String backend, long fileBytes, boolean encoders, long physicalBytes,
                       long availableBytes, @NonNull List<String> accelerators, int capContext,
                       @Nullable String crashedAccelerator, int crashedContext, boolean explicitContext,
                       long thresholdBytes, @NonNull History history, @NonNull List<TaiResidency.Entry> evictable) {
            this.backend = backend;
            this.fileBytes = fileBytes;
            this.encoders = encoders;
            this.physicalBytes = physicalBytes;
            this.availableBytes = availableBytes;
            this.accelerators = Collections.unmodifiableList(accelerators);
            this.capContext = capContext;
            this.crashedAccelerator = crashedAccelerator;
            this.crashedContext = crashedContext;
            this.explicitContext = explicitContext;
            this.thresholdBytes = thresholdBytes;
            this.history = history;
            this.evictable = Collections.unmodifiableList(evictable);
        }
    }

    public static final class Plan {
        public final boolean fits;
        /** The accelerator to load with; the first one tried when nothing fits. */
        @Nullable public final String accelerator;
        public final int contextWindow;
        /** Non-reclaimable bytes the load is expected to take from MemAvailable. */
        public final long estimatedBytes;
        /** Where {@link #estimatedBytes} came from: {@link #SOURCE_MEASURED} or {@link #SOURCE_RATIO}. */
        @NonNull public final String estimateSource;
        /** File-backed bytes the load maps that the kernel can reclaim; not counted against the floor. */
        public final long reclaimableBytes;
        /** The safety margin on a ratio estimate; {@code 0} for a measured one. */
        public final long marginBytes;
        public final long availableBytes;
        /** What stays free for the rest of the phone: the threshold floor, or the old reserve. */
        public final long reserveBytes;
        /** Whether free memory was known; without it the plan is the floor, unmeasured. */
        public final boolean measured;
        /** Idle residents to close before this load, in order; empty when it fits as is. */
        @NonNull public final List<TaiResidency.Entry> evicted;

        Plan(boolean fits, @Nullable String accelerator, int contextWindow, @NonNull Estimate estimate,
             long marginBytes, long availableBytes, long reserveBytes, boolean measured,
             @NonNull List<TaiResidency.Entry> evicted) {
            this.fits = fits;
            this.accelerator = accelerator;
            this.contextWindow = contextWindow;
            this.estimatedBytes = estimate.nonReclaimableBytes;
            this.estimateSource = estimate.source;
            this.reclaimableBytes = estimate.reclaimableBytes;
            this.marginBytes = marginBytes;
            this.availableBytes = availableBytes;
            this.reserveBytes = reserveBytes;
            this.measured = measured;
            this.evicted = Collections.unmodifiableList(evicted);
        }

        /** Free memory a load of {@link #estimatedBytes} would need with nothing evicted: margin and floor included. */
        public long neededFreeBytes() {
            return estimatedBytes + marginBytes + reserveBytes;
        }

        /** Bytes the chosen evictions give back. */
        public long evictedBytes() {
            long total = 0L;
            for (TaiResidency.Entry entry : evicted) total += entry.bytes();
            return total;
        }
    }

    /** The smallest window this request may be given: the floor, or the model's own limit below it. */
    static int floor(int capContext) {
        return capContext > 0 ? Math.min(FLOOR_CONTEXT, capContext) : FLOOR_CONTEXT;
    }

    /** The margin a ratio estimate carries when the floor is Android's threshold; none otherwise. */
    static long marginBytes(@NonNull Estimate estimate, long thresholdBytes) {
        if (thresholdBytes <= 0L || estimate.isMeasured()) return 0L;
        return estimate.nonReclaimableBytes * RATIO_MARGIN_PERCENT / 100L;
    }

    @NonNull
    public static Plan plan(@NonNull Request r) {
        int floor = floor(r.capContext);
        int cap = Math.max(floor, r.capContext);
        long reserve = floorBytes(r.thresholdBytes, r.physicalBytes);
        String first = r.accelerators.isEmpty() ? null : r.accelerators.get(0);
        List<TaiResidency.Entry> none = Collections.emptyList();
        if (r.availableBytes <= 0L || r.physicalBytes <= 0L || r.fileBytes <= 0L) {
            Estimate estimate = first == null ? Estimate.ratio(0L, 0L)
                : estimate(r.backend, first, r.fileBytes, r.encoders, floor, r.history);
            return new Plan(first != null, first, floor, estimate, marginBytes(estimate, r.thresholdBytes),
                r.availableBytes, reserve, false, none);
        }
        long spendable = r.availableBytes - reserve;
        for (int i = 0; i < r.accelerators.size(); i++) {
            String accelerator = r.accelerators.get(i);
            int limit = i == 0 ? cap : floor;
            if ("gpu".equals(accelerator) && !r.explicitContext) limit = Math.min(limit, GPU_AUTO_CONTEXT);
            if (accelerator.equals(r.crashedAccelerator)) {
                int crashedAt = r.crashedContext > 0 ? r.crashedContext : cap;
                limit = Math.min(limit, crashedAt / 2);
                if (limit < floor) continue;
            }
            for (int context = limit; ; context = Math.max(floor, context / 2)) {
                Estimate estimate = estimate(r.backend, accelerator, r.fileBytes, r.encoders, context, r.history);
                long margin = marginBytes(estimate, r.thresholdBytes);
                long need = estimate.nonReclaimableBytes + margin;
                if (need <= spendable) {
                    return new Plan(true, accelerator, context, estimate, margin, r.availableBytes, reserve, true, none);
                }
                List<TaiResidency.Entry> evicted = evictions(need - spendable, r.evictable);
                if (evicted != null) {
                    return new Plan(true, accelerator, context, estimate, margin, r.availableBytes, reserve, true, evicted);
                }
                if (context == floor) break;
            }
        }
        Estimate smallest = first == null ? Estimate.ratio(0L, 0L)
            : estimate(r.backend, cheapest(r), r.fileBytes, r.encoders, floor, r.history);
        return new Plan(false, first, floor, smallest, marginBytes(smallest, r.thresholdBytes), r.availableBytes, reserve, true, none);
    }

    /**
     * The plan for a load with no window to shrink and no accelerator ladder — an embedding
     * interpreter, later an STT model: it fits when its whole estimate leaves the floor free,
     * with idle residents evicted if that is what it takes, and is refused otherwise. Unknown free
     * memory gives the same unmeasured go-ahead as {@link #plan}.
     */
    @NonNull
    public static Plan planFixed(@NonNull Estimate estimate, @NonNull String accelerator, long physicalBytes,
                                 long availableBytes, long thresholdBytes, @NonNull List<TaiResidency.Entry> evictable) {
        long reserve = floorBytes(thresholdBytes, physicalBytes);
        long margin = marginBytes(estimate, thresholdBytes);
        List<TaiResidency.Entry> none = Collections.emptyList();
        if (availableBytes <= 0L || physicalBytes <= 0L) {
            return new Plan(true, accelerator, 0, estimate, margin, availableBytes, reserve, false, none);
        }
        long need = estimate.nonReclaimableBytes + margin;
        long spendable = availableBytes - reserve;
        if (need <= spendable) return new Plan(true, accelerator, 0, estimate, margin, availableBytes, reserve, true, none);
        List<TaiResidency.Entry> evicted = evictions(need - spendable, evictable);
        return new Plan(evicted != null, accelerator, 0, estimate, margin, availableBytes, reserve, true,
            evicted == null ? none : evicted);
    }

    /** {@link #planFixed} with a ratio estimate, the old reserve and nothing to evict. */
    @NonNull
    public static Plan planFixed(long needBytes, @NonNull String accelerator, long physicalBytes, long availableBytes) {
        return planFixed(Estimate.ratio(needBytes, 0L), accelerator, physicalBytes, availableBytes, 0L,
            Collections.<TaiResidency.Entry>emptyList());
    }

    /**
     * The shortest prefix of {@code evictable} that gives back at least {@code shortfall}, or
     * {@code null} when the whole list would not. Busy residents are skipped even if listed.
     */
    @Nullable
    static List<TaiResidency.Entry> evictions(long shortfall, @NonNull List<TaiResidency.Entry> evictable) {
        if (shortfall <= 0L) return Collections.emptyList();
        ArrayList<TaiResidency.Entry> chosen = new ArrayList<>();
        long reclaimed = 0L;
        for (TaiResidency.Entry entry : evictable) {
            if (entry.busy || entry.kind == TaiResidency.Kind.RUNTIME) continue;
            chosen.add(entry);
            reclaimed += entry.bytes();
            if (reclaimed >= shortfall) return chosen;
        }
        return null;
    }

    /** The accelerator whose floor load costs least, for telling the user how much would be needed. */
    @NonNull
    private static String cheapest(@NonNull Request r) {
        String best = r.accelerators.get(0);
        long bestBytes = Long.MAX_VALUE;
        for (String accelerator : r.accelerators) {
            long bytes = estimate(r.backend, accelerator, r.fileBytes, r.encoders, floor(r.capContext), r.history).nonReclaimableBytes;
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
