package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * <p><b>The estimate.</b> When {@link TaiRuntimeHistory} holds measured drops for this model
 * file, accelerator and modality, the estimate is the largest drop recorded at the smallest
 * measured window at or above the wanted one, plus 10% ({@code "measured"}); with samples only
 * below it, a fit or the seed slope carries them up. Otherwise it is a seed from the model file
 * ({@code "ratio"}): a fixed part plus a per-token part (the KV cache), per accelerator, split
 * into what the kernel can reclaim and what it cannot. Fitted on pong, erring high:
 * <ul>
 *   <li>LiteRT GPU holds about three quarters of the file resident (weights uploaded to GPU
 *       buffers, which are ordinary RAM on a phone), none of it reclaimable. Measured: 2.4–4.2 GB
 *       at 4k for a 3.66 GB file across four runs; ~3.2 GB after LiteRT-LM 0.17.1.</li>
 *   <li>LiteRT CPU: 0.6 of the file (E4B's card puts its CPU RSS at 0.9 of the file at 2048
 *       tokens, E2B's at 0.67). The kernel can reclaim the mapped weights, but a CPU load
 *       allocates its KV cache and buffers at the first prefill, so the seed counts the resident
 *       figure and the meter keeps measuring until the first token.</li>
 *   <li>MNN loads its weights outright, none of it reclaimable.</li>
 *   <li>Per token: the file size divided by 19000 (some 190 KB for gemma-4-e4b, which matches
 *       its measured GPU slope); for MNN the architecture's own KV, layers x kv heads x head dim
 *       x 2 x 2 bytes, when {@code config.json} says what they are.</li>
 * </ul>
 *
 * <p><b>The floor.</b> What stays free for everything else is a policy value by RAM class, not a
 * derived limit (see {@link #floorBytes}): a hold floor for what stays resident, a lower peak
 * floor for a momentary load the runtime itself unloads within {@link #MOMENTARY_DEADLINE_MS},
 * plus a penalty when swap is nearly full or unknown below Android 16 and, for the hold floor, a
 * smaller one while the launcher is not in front. A seed estimate carries a margin of a quarter
 * of itself on top of the floor; a measured one does not. {@code MemoryInfo.threshold} no longer
 * sets the floor; it only says whether the margin applies.
 *
 * <p><b>The ladder.</b> At each step — first the requested accelerator at the wanted window, then
 * halving down to a 4k floor, then the next accelerator at the floor only — a load that fits with
 * what is free is taken as is; one that does not is retried with idle residents evicted in the
 * order the caller lists them (embeddings, then STT, then idle chat; never a busy one), taking the
 * shortest prefix that covers the shortfall; only then does the window shrink. The next
 * accelerator is tried only if it has a measured cost lower than the one just refused, or the
 * device has no GPU: for gemma-4 the CPU saves little or nothing in memory and costs 3-4 times
 * the time, so the answer to "does not fit" is a smaller model, which the caller walks. A LiteRT
 * file over 3 GB is never taken to the CPU as a fallback. The next accelerator is held to the
 * floor window, and a larger CPU window was the one configuration the calibration could not
 * survive (it took the phone's network down). An automatic GPU load is capped at 4k as well: 8k
 * cost ~0.8 GB more on pong and slowed decode from 10.5 to 9.8 tok/s, and both LiteRT-LM and
 * Gallery default GPU to 4096; an explicit window above that is honoured, subject to the budget.
 * An automatic LiteRT CPU load is capped at the floor too, because its runtime buffers are
 * allocated at the first prefill. A load whose process was killed last time is not repeated as it
 * was: that accelerator is held to half the window it died at.
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
    /**
     * Peak floor: what a momentary load (the director, the reader, a cleanup pass) keeps free. The
     * runtime unloads it within {@link #MOMENTARY_DEADLINE_MS}, so it holds its memory for about
     * forty seconds and never grows. The 2026-10-04 E4B GPU director run was admitted at an
     * effective 512 MiB and did not freeze; 0.75 GiB adds a quarter gigabyte for the gap between a
     * 100 ms sample and a fast allocation. On 16 GB and up, 1 GiB keeps the same share of RAM.
     */
    public static final long PEAK_FLOOR_BYTES = 768L * MIB;
    public static final long PEAK_FLOOR_LARGE_BYTES = GIB;
    /** Hold floors by RAM class: 4 GB and under, 6, 8, 10-12, 16 and up. */
    public static final long HOLD_FLOOR_SMALL_BYTES = GIB;
    public static final long HOLD_FLOOR_6_BYTES = 1280L * MIB;
    public static final long HOLD_FLOOR_8_BYTES = 1280L * MIB;
    public static final long HOLD_FLOOR_12_BYTES = 1536L * MIB;
    public static final long HOLD_FLOOR_LARGE_BYTES = 2L * GIB;
    /** Added to either floor when swap is nearly full, or not known below Android 16. */
    public static final long SWAP_LOW_PENALTY_BYTES = 512L * MIB;
    /** Added to the hold floor only, while the launcher is not the app in front. */
    public static final long BACKGROUND_PENALTY_BYTES = 256L * MIB;
    /** How long the runtime lets a momentary load live, counted from its start, before it unloads it itself. */
    public static final long MOMENTARY_DEADLINE_MS = 3L * 60L * 1000L;
    /** A LiteRT file larger than this is not taken to the CPU as a fallback accelerator. */
    public static final long LITERT_CPU_FALLBACK_MAX_FILE_BYTES = 3L * GIB;
    /** Added to a seed estimate on top of the floor; a measured estimate carries none. */
    public static final int RATIO_MARGIN_PERCENT = 25;
    /** Added to the largest measured drop before it is used as the estimate. */
    public static final int MEASURED_HEADROOM_PERCENT = 10;
    /** File bytes per context token of KV cache. */
    static final long FILE_BYTES_PER_TOKEN_DIVISOR = 19_000L;
    /** The fixed part of a LiteRT CPU load, as a share of the file: E4B's card is 0.9 at 2048 tokens, E2B's 0.67. */
    static final int LITERT_CPU_FIXED_PERCENT = 60;

    public static final String SOURCE_MEASURED = "measured";
    public static final String SOURCE_RATIO = "ratio";

    private TaiLoadBudget() {
    }

    /**
     * Measured load costs by accelerator and window; {@code 0} when nothing was recorded. The
     * implementation owns the lookup ({@link TaiRuntimeHistory#lookup}): the nearest measured
     * window at or above the one asked, else a fit or the seed slope carried up from below.
     */
    public interface History {
        long measuredBytes(@NonNull String accelerator, int contextTokens);
    }

    public static final History NO_HISTORY = (accelerator, contextTokens) -> 0L;

    /** The conditions a floor depends on beyond the RAM class; see {@link #floorBytes}. */
    public static final class Conditions {
        /** Normal conditions: swap not low, the launcher in front. */
        public static final Conditions NORMAL = new Conditions(false, true);

        final boolean swapLow;
        final boolean launcherInFront;

        Conditions(boolean swapLow, boolean launcherInFront) {
            this.swapLow = swapLow;
            this.launcherInFront = launcherInFront;
        }
    }

    /** 0 for 4 GB and under, then 1 (6 GB), 2 (8 GB), 3 (10-12 GB), 4 (16 GB and up). */
    private static int ramClassIndex(long ramClassBytes) {
        if (ramClassBytes <= 4L * GIB) return 0;
        if (ramClassBytes <= 6L * GIB) return 1;
        if (ramClassBytes <= 8L * GIB) return 2;
        if (ramClassBytes <= 12L * GIB) return 3;
        return 4;
    }

    /** The peak floor for a RAM class ({@link #ramClassBytes}); unknown RAM counts as the smallest class. */
    public static long peakFloorBytes(long ramClassBytes) {
        return ramClassIndex(ramClassBytes) == 4 ? PEAK_FLOOR_LARGE_BYTES : PEAK_FLOOR_BYTES;
    }

    /** The hold floor for a RAM class ({@link #ramClassBytes}); unknown RAM counts as the smallest class. */
    public static long holdFloorBytes(long ramClassBytes) {
        switch (ramClassIndex(ramClassBytes)) {
            case 1:
                return HOLD_FLOOR_6_BYTES;
            case 2:
                return HOLD_FLOOR_8_BYTES;
            case 3:
                return HOLD_FLOOR_12_BYTES;
            case 4:
                return HOLD_FLOOR_LARGE_BYTES;
            default:
                return HOLD_FLOOR_SMALL_BYTES;
        }
    }

    /**
     * What stays free for the rest of the phone: the peak floor for a momentary load, the hold floor
     * for everything else, plus {@link #SWAP_LOW_PENALTY_BYTES} when swap is low and, on the hold
     * floor only, {@link #BACKGROUND_PENALTY_BYTES} while the launcher is not in front. These are
     * policy values to be corrected on the device by the low-memory-kill record, not derived limits.
     */
    public static long floorBytes(long ramClassBytes, boolean momentary, @NonNull Conditions conditions) {
        long floor = momentary ? peakFloorBytes(ramClassBytes) : holdFloorBytes(ramClassBytes);
        if (conditions.swapLow) floor += SWAP_LOW_PENALTY_BYTES;
        if (!momentary && !conditions.launcherInFront) floor += BACKGROUND_PENALTY_BYTES;
        return floor;
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

    /** The estimate for a load: the measured worst case when history has one for this accelerator and window, else the seed. */
    @NonNull
    public static Estimate estimate(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                    boolean encoders, int contextTokens, @NonNull History history) {
        return estimate(backend, accelerator, fileBytes, encoders, contextTokens, history, 0L);
    }

    /** {@link #estimate(String, String, long, boolean, int, History)} with the architecture's KV bytes per token ({@code 0}: unknown). */
    @NonNull
    public static Estimate estimate(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                    boolean encoders, int contextTokens, @NonNull History history,
                                    long kvBytesPerToken) {
        long measured = history.measuredBytes(accelerator, contextTokens);
        if (measured > 0L) return Estimate.measured(measured);
        return Estimate.ratio(estimateBytes(backend, accelerator, fileBytes, encoders, contextTokens, kvBytesPerToken),
            reclaimableBytes(backend, accelerator, fileBytes));
    }

    /** The seed estimate of a load's new, non-reclaimable memory pressure, in bytes. */
    public static long estimateBytes(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                     boolean encoders, int contextTokens) {
        return estimateBytes(backend, accelerator, fileBytes, encoders, contextTokens, 0L);
    }

    /** {@link #estimateBytes(String, String, long, boolean, int)} with the architecture's KV bytes per token ({@code 0}: unknown). */
    public static long estimateBytes(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                     boolean encoders, int contextTokens, long kvBytesPerToken) {
        return seedFixedBytes(backend, accelerator, fileBytes, encoders)
            + seedSlopeBytes(backend, fileBytes, kvBytesPerToken) * Math.max(0, contextTokens);
    }

    /** The seed's window-independent part: weights and buffers, plus the encoders' share when there are any. */
    public static long seedFixedBytes(@NonNull String backend, @NonNull String accelerator, long fileBytes,
                                      boolean encoders) {
        long fixed;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) {
            fixed = fileBytes;
        } else if ("cpu".equals(accelerator)) {
            fixed = fileBytes / 100L * LITERT_CPU_FIXED_PERCENT;
        } else {
            fixed = fileBytes * 3L / 4L;
        }
        if (encoders) fixed += fileBytes / 10L;
        return fixed;
    }

    /**
     * The seed's bytes per context token: the architecture's KV for MNN when it is known, else the
     * file size divided by 19000, never less than one byte.
     */
    public static long seedSlopeBytes(@NonNull String backend, long fileBytes, long kvBytesPerToken) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend) && kvBytesPerToken > 0L) return kvBytesPerToken;
        return Math.max(1L, fileBytes / FILE_BYTES_PER_TOKEN_DIVISOR);
    }

    /**
     * KV-cache bytes per token from an MNN model's architecture:
     * layers x kv heads x head dim x 2 (K and V) x 2 bytes (fp16), halved for {@code attention_mode}
     * 10 or 2 (8-bit KV). {@code 0} when the layers, heads or head size are not stated.
     */
    public static long kvBytesPerToken(@Nullable JSONObject config) {
        if (config == null) return 0L;
        long layers = firstLong(config, "num_hidden_layers", "layer_nums", "n_layer", "num_layers");
        long attentionHeads = firstLong(config, "num_attention_heads", "attention_heads", "n_head");
        long kvHeads = firstLong(config, "num_key_value_heads", "kv_heads", "key_value_heads", "n_kv_head");
        if (kvHeads <= 0L) kvHeads = attentionHeads;
        long headDim = firstLong(config, "head_dim", "attention_head_dim");
        if (headDim <= 0L) {
            long hidden = firstLong(config, "hidden_size", "n_embd");
            if (hidden > 0L && attentionHeads > 0L) headDim = hidden / attentionHeads;
        }
        if (layers <= 0L || kvHeads <= 0L || headDim <= 0L) return 0L;
        long bytes = layers * kvHeads * headDim * 2L * 2L;
        int mode = config.optInt("attention_mode", 8);
        return mode == 10 || mode == 2 ? bytes / 2L : bytes;
    }

    private static long firstLong(@NonNull JSONObject json, @NonNull String... names) {
        for (String name : names) {
            long value = json.optLong(name, 0L);
            if (value > 0L) return value;
        }
        return 0L;
    }

    /**
     * {@link #kvBytesPerToken(JSONObject)} for the package a model path points at: its
     * {@code config.json} (or the package directory), with {@code llm_config.json} beside it when
     * the architecture lives there. {@code 0} when neither is readable, which keeps {@code file/19000}.
     */
    public static long mnnKvBytesPerToken(@Nullable String modelPath) {
        if (modelPath == null || modelPath.trim().isEmpty()) return 0L;
        File path = new File(modelPath);
        File dir = path.isDirectory() ? path : path.getParentFile();
        if (dir == null) return 0L;
        for (String name : new String[] {"config.json", "llm_config.json"}) {
            File file = new File(dir, name);
            if (!file.isFile() || !file.canRead()) continue;
            try {
                long bytes = kvBytesPerToken(new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
                if (bytes > 0L) return bytes;
            } catch (Exception ignored) {
            }
        }
        return 0L;
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
        /** The runtime unloads this load itself within {@link #MOMENTARY_DEADLINE_MS}, so it keeps the peak floor. */
        final boolean momentary;
        /** Swap and foreground state, which move the floors; {@link Conditions#NORMAL} unless the caller read them. */
        @NonNull final Conditions conditions;
        /** The architecture's KV bytes per token for an MNN model; {@code 0} keeps the file-size slope. */
        final long kvBytesPerToken;
        /** The device has no GPU path, so the other accelerator needs no measurement to be tried. */
        final boolean gpuless;

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
         * @param thresholdBytes  Android's {@code MemoryInfo.threshold}; {@code 0} means unknown and
         *                        a seed estimate then carries no margin
         * @param history         measured load costs for this model on this device
         * @param evictable       idle residents this load may close, in eviction order; the caller
         *                        leaves out what the load replaces anyway (already credited to
         *                        {@code availableBytes}) and anything busy
         */
        public Request(@NonNull String backend, long fileBytes, boolean encoders, long physicalBytes,
                       long availableBytes, @NonNull List<String> accelerators, int capContext,
                       @Nullable String crashedAccelerator, int crashedContext, boolean explicitContext,
                       long thresholdBytes, @NonNull History history, @NonNull List<TaiResidency.Entry> evictable) {
            this(backend, fileBytes, encoders, physicalBytes, availableBytes, accelerators, capContext,
                crashedAccelerator, crashedContext, explicitContext, thresholdBytes, history, evictable, false);
        }

        /** @param momentary whether the runtime unloads this load within {@link #MOMENTARY_DEADLINE_MS}, so it keeps the peak floor */
        public Request(@NonNull String backend, long fileBytes, boolean encoders, long physicalBytes,
                       long availableBytes, @NonNull List<String> accelerators, int capContext,
                       @Nullable String crashedAccelerator, int crashedContext, boolean explicitContext,
                       long thresholdBytes, @NonNull History history, @NonNull List<TaiResidency.Entry> evictable,
                       boolean momentary) {
            this(backend, fileBytes, encoders, physicalBytes, availableBytes, accelerators, capContext,
                crashedAccelerator, crashedContext, explicitContext, thresholdBytes, history, evictable, momentary,
                Conditions.NORMAL, 0L, false);
        }

        private Request(@NonNull String backend, long fileBytes, boolean encoders, long physicalBytes,
                        long availableBytes, @NonNull List<String> accelerators, int capContext,
                        @Nullable String crashedAccelerator, int crashedContext, boolean explicitContext,
                        long thresholdBytes, @NonNull History history, @NonNull List<TaiResidency.Entry> evictable,
                        boolean momentary, @NonNull Conditions conditions, long kvBytesPerToken, boolean gpuless) {
            this.momentary = momentary;
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
            this.conditions = conditions;
            this.kvBytesPerToken = kvBytesPerToken;
            this.gpuless = gpuless;
        }

        /** This request with the swap and foreground state the caller read, which move the floors. */
        @NonNull
        public Request withConditions(@NonNull Conditions newConditions) {
            return new Request(backend, fileBytes, encoders, physicalBytes, availableBytes, accelerators, capContext,
                crashedAccelerator, crashedContext, explicitContext, thresholdBytes, history, evictable, momentary,
                newConditions, kvBytesPerToken, gpuless);
        }

        /** This request with an MNN model's architecture KV bytes per token ({@code 0}: unknown). */
        @NonNull
        public Request withKvBytesPerToken(long newKvBytesPerToken) {
            return new Request(backend, fileBytes, encoders, physicalBytes, availableBytes, accelerators, capContext,
                crashedAccelerator, crashedContext, explicitContext, thresholdBytes, history, evictable, momentary,
                conditions, newKvBytesPerToken, gpuless);
        }

        /** This request with whether the device lacks a GPU path, which lets the other accelerator be tried unmeasured. */
        @NonNull
        public Request withGpuless(boolean newGpuless) {
            return new Request(backend, fileBytes, encoders, physicalBytes, availableBytes, accelerators, capContext,
                crashedAccelerator, crashedContext, explicitContext, thresholdBytes, history, evictable, momentary,
                conditions, kvBytesPerToken, newGpuless);
        }
    }

    public static final class Plan {
        public final boolean fits;
        /** The accelerator to load with; the first one tried when nothing fits. */
        @Nullable public final String accelerator;
        /** The window the plan chose; for a refusal, the smallest window it tried. */
        public final int contextWindow;
        /** Non-reclaimable bytes the load is expected to take from MemAvailable. */
        public final long estimatedBytes;
        /** Where {@link #estimatedBytes} came from: {@link #SOURCE_MEASURED} or {@link #SOURCE_RATIO}. */
        @NonNull public final String estimateSource;
        /** File-backed bytes the load maps that the kernel can reclaim; not counted against the floor. */
        public final long reclaimableBytes;
        /** The safety margin on a seed estimate; {@code 0} for a measured one. */
        public final long marginBytes;
        public final long availableBytes;
        /** What stays free for the rest of the phone: the peak or hold floor with its penalties. */
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

    /** The margin a seed estimate carries when the threshold is known; none for a measured one. */
    static long marginBytes(@NonNull Estimate estimate, long thresholdBytes) {
        if (thresholdBytes <= 0L || estimate.isMeasured()) return 0L;
        return estimate.nonReclaimableBytes * RATIO_MARGIN_PERCENT / 100L;
    }

    @NonNull
    public static Plan plan(@NonNull Request r) {
        int floor = floor(r.capContext);
        int cap = Math.max(floor, r.capContext);
        long reserve = floorBytes(ramClassBytes(r.physicalBytes), r.momentary, r.conditions);
        String first = r.accelerators.isEmpty() ? null : r.accelerators.get(0);
        List<TaiResidency.Entry> none = Collections.emptyList();
        if (r.availableBytes <= 0L || r.physicalBytes <= 0L || r.fileBytes <= 0L) {
            Estimate estimate = first == null ? Estimate.ratio(0L, 0L)
                : estimate(r.backend, first, r.fileBytes, r.encoders, floor, r.history, r.kvBytesPerToken);
            return new Plan(first != null, first, floor, estimate, marginBytes(estimate, r.thresholdBytes),
                r.availableBytes, reserve, false, none);
        }
        long spendable = r.availableBytes - reserve;
        // What the previous accelerator needed at its smallest window; the next one is held to beat it.
        long refusedNeed = Long.MAX_VALUE;
        boolean previousCrashed = false;
        for (int i = 0; i < r.accelerators.size(); i++) {
            String accelerator = r.accelerators.get(i);
            if (i > 0 && !otherAcceleratorAllowed(r, accelerator, floor, refusedNeed, previousCrashed)) continue;
            previousCrashed = false;
            int limit = i == 0 ? cap : floor;
            if ("gpu".equals(accelerator) && !r.explicitContext) limit = Math.min(limit, GPU_AUTO_CONTEXT);
            // The CPU allocates its KV cache at the first prefill, past anything the budget sees at
            // load time: without a window someone chose, it gets the floor rather than the RAM tier.
            if ("cpu".equals(accelerator) && !r.explicitContext && TaiModelSpec.BACKEND_LITERT_LM.equals(r.backend)) {
                limit = Math.min(limit, FLOOR_CONTEXT);
            }
            if (accelerator.equals(r.crashedAccelerator)) {
                int crashedAt = r.crashedContext > 0 ? r.crashedContext : cap;
                limit = Math.min(limit, crashedAt / 2);
                if (limit < floor) {
                    previousCrashed = true;
                    continue;
                }
            }
            for (int context = limit; ; context = Math.max(floor, context / 2)) {
                Estimate estimate = estimate(r.backend, accelerator, r.fileBytes, r.encoders, context, r.history, r.kvBytesPerToken);
                long margin = marginBytes(estimate, r.thresholdBytes);
                long need = estimate.nonReclaimableBytes + margin;
                if (need <= spendable) {
                    return new Plan(true, accelerator, context, estimate, margin, r.availableBytes, reserve, true, none);
                }
                List<TaiResidency.Entry> evicted = evictions(need - spendable, r.evictable);
                if (evicted != null) {
                    return new Plan(true, accelerator, context, estimate, margin, r.availableBytes, reserve, true, evicted);
                }
                if (context == floor) {
                    refusedNeed = need;
                    break;
                }
            }
        }
        Estimate smallest = first == null ? Estimate.ratio(0L, 0L)
            : estimate(r.backend, cheapest(r), r.fileBytes, r.encoders, floor, r.history, r.kvBytesPerToken);
        return new Plan(false, first, floor, smallest, marginBytes(smallest, r.thresholdBytes), r.availableBytes, reserve, true, none);
    }

    /**
     * Whether the ladder may step to {@code accelerator} after the one before it was refused. Not
     * for a LiteRT file over 3 GB on the CPU (the caller walks its fallback model instead). Not
     * unless the device has no GPU, or the one before it could not be tried at all (a load killed
     * there), or this accelerator has a measured cost below the refused one's: the CPU is not the
     * cheaper rung for gemma-4, and an unmeasured guess is what kept choosing it.
     */
    private static boolean otherAcceleratorAllowed(@NonNull Request r, @NonNull String accelerator, int floor,
                                                   long refusedNeed, boolean previousCrashed) {
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(r.backend) && "cpu".equals(accelerator)
                && r.fileBytes > LITERT_CPU_FALLBACK_MAX_FILE_BYTES) {
            return false;
        }
        if (r.gpuless || previousCrashed) return true;
        Estimate candidate = estimate(r.backend, accelerator, r.fileBytes, r.encoders, floor, r.history, r.kvBytesPerToken);
        long need = candidate.nonReclaimableBytes + marginBytes(candidate, r.thresholdBytes);
        return candidate.isMeasured() && need < refusedNeed;
    }

    /**
     * The plan for a load with no window to shrink and no accelerator ladder — an embedding
     * interpreter, later an STT model: it fits when its whole estimate leaves the hold floor free,
     * with idle residents evicted if that is what it takes, and is refused otherwise. Unknown free
     * memory gives the same unmeasured go-ahead as {@link #plan}.
     */
    @NonNull
    public static Plan planFixed(@NonNull Estimate estimate, @NonNull String accelerator, long physicalBytes,
                                 long availableBytes, long thresholdBytes, @NonNull List<TaiResidency.Entry> evictable) {
        return planFixed(estimate, accelerator, physicalBytes, availableBytes, thresholdBytes, evictable, Conditions.NORMAL);
    }

    /** {@link #planFixed(Estimate, String, long, long, long, List)} with the swap and foreground state the caller read. */
    @NonNull
    public static Plan planFixed(@NonNull Estimate estimate, @NonNull String accelerator, long physicalBytes,
                                 long availableBytes, long thresholdBytes, @NonNull List<TaiResidency.Entry> evictable,
                                 @NonNull Conditions conditions) {
        long reserve = floorBytes(ramClassBytes(physicalBytes), false, conditions);
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

    /** {@link #planFixed} with a seed estimate and nothing to evict. */
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
            long bytes = estimate(r.backend, accelerator, r.fileBytes, r.encoders, floor(r.capContext), r.history,
                r.kvBytesPerToken).nonReclaimableBytes;
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
