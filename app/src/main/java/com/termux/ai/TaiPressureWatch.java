package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The decisions behind the runtime process's memory watch, kept free of Android so they can be
 * tested from a table: which pressure tier the phone is in, which resident that tier gives up
 * next, which residents have sat idle past their kind's limit, and when the process itself has
 * nothing left to hold. {@link TaiRuntimeService} reads {@code MemoryInfo} and carries the
 * decisions out; nothing here evicts anything.
 *
 * <p>Tiers, against the budget's floors ({@link TaiLoadBudget#floorBytes}: the hold floor for what
 * stays resident, the lower peak floor for a momentary load, under the same memory limits the gate
 * read; unrestricted, both are {@code 0} and only {@code lowMemory} acts). Free memory is
 * {@link TaiMemInfo}'s: MemAvailable.
 *
 * <pre>
 *   lowMemory                         → RELEASE_ALL: cancel in-flight work, unload everything
 *   avail &lt; peak floor                → CHAT: also give up idle chat
 *   avail &lt; hold floor                → AUXILIARY: give up idle embeddings, idle speech output, then idle STT
 *   otherwise                         → NONE
 * </pre>
 *
 * There is no line above the floors, and no {@code onTrimMemory} mapping: the running trim levels
 * are "not notified of this level since API level 34", so on current Android the poll is the only
 * watch, every {@link #POLL_BUSY_MS} while a load or first prefill runs and every
 * {@link #POLL_IDLE_MS} otherwise.
 *
 * The two eviction tiers give up one resident per evaluation, cheapest first, and the watch looks
 * again on its next tick: MemAvailable is noisy and memory comes back late (§3c of the plan), so
 * closing everything the moment the line is crossed would over-shoot. A busy resident is never
 * touched by either tier; only {@code lowMemory} interrupts work.
 */
final class TaiPressureWatch {

    /** In the order the actions escalate; {@link Enum#compareTo} is meaningful. */
    enum Tier { NONE, AUXILIARY, CHAT, RELEASE_ALL }

    /** An embedding interpreter nobody has used for this long is closed. */
    static final long EMBEDDING_IDLE_MS = TimeUnit.MINUTES.toMillis(5);
    /** A speech-to-text model nobody has used for this long is closed. */
    static final long STT_IDLE_MS = TimeUnit.MINUTES.toMillis(2);
    /**
     * A speech-output model nobody has used for this long is closed. Longer than STT's: reading
     * aloud comes in bursts a few minutes apart, and a reload delays the first word by the load.
     */
    static final long TTS_IDLE_MS = TimeUnit.MINUTES.toMillis(5);
    /**
     * A resident image model (memory mode 1 only; every other mode frees after each run) nobody has
     * used for this long is closed: it holds more memory than any other auxiliary resident, and
     * image generation comes in bursts.
     */
    static final long IMAGE_IDLE_MS = TimeUnit.MINUTES.toMillis(3);
    /**
     * With only the {@link TaiResidency#RUNTIME_BASELINE_BYTES} entry left and no request for this
     * long, the process exits to give the baseline back (§3b of the plan).
     */
    static final long IDLE_EXIT_MS = TimeUnit.MINUTES.toMillis(10);

    /** How often the watch looks while a load or a first prefill is running: the allocation it guards is fast. */
    static final long POLL_BUSY_MS = 250L;
    /** How often it looks otherwise; slow enough to be invisible in battery stats. */
    static final long POLL_IDLE_MS = 2_000L;

    private TaiPressureWatch() {
    }

    /** The poll interval: {@link #POLL_BUSY_MS} while a load or first prefill runs ({@link TaiLoadMeter#anyActive}), else {@link #POLL_IDLE_MS}. */
    static long pollIntervalMs(boolean loadOrFirstPrefillRunning) {
        return loadOrFirstPrefillRunning ? POLL_BUSY_MS : POLL_IDLE_MS;
    }

    /**
     * The tier for one reading of free memory, against the budget's floors
     * ({@link TaiLoadBudget#floorBytes}, under the memory limits the gate read):
     * below the hold floor the watch gives up idle auxiliaries; below the lower peak floor it gives
     * up idle chat too; {@code lowMemory} releases everything. Unknown free memory ({@code <= 0})
     * selects nothing on its own, and a floor of {@code 0} means that line is unknown.
     */
    @NonNull
    static Tier tier(long availBytes, long holdFloorBytes, long peakFloorBytes, boolean lowMemory) {
        if (lowMemory) return Tier.RELEASE_ALL;
        if (availBytes <= 0L) return Tier.NONE;
        if (peakFloorBytes > 0L && availBytes < peakFloorBytes) return Tier.CHAT;
        if (holdFloorBytes > 0L && availBytes < holdFloorBytes) return Tier.AUXILIARY;
        return Tier.NONE;
    }

    /**
     * The one resident {@code tier} gives up next, or {@code null} when it has nothing to give:
     * idle embeddings first, then idle speech output, then an idle image model, then idle STT, then — in {@link Tier#CHAT} only — idle chat; the
     * least recently used first within a kind. Busy residents and the RUNTIME baseline are never
     * candidates. {@link Tier#RELEASE_ALL} is not an eviction and answers {@code null}.
     */
    @Nullable
    static TaiResidency.Entry nextVictim(@NonNull List<TaiResidency.Entry> residents, @NonNull Tier tier) {
        return nextVictim(residents, tier, Long.MIN_VALUE);
    }

    /**
     * {@link #nextVictim(List, Tier)} at {@code nowMs}, passing over every resident that stays for its
     * feature group ({@link TaiFeaturePlan#keptForItsGroup}): the eviction tiers never break a group in
     * use, and only {@link Tier#RELEASE_ALL} does. {@link Long#MIN_VALUE} keeps no group.
     */
    @Nullable
    static TaiResidency.Entry nextVictim(@NonNull List<TaiResidency.Entry> residents, @NonNull Tier tier, long nowMs) {
        if (tier != Tier.AUXILIARY && tier != Tier.CHAT) return null;
        for (TaiResidency.Entry entry : idleInEvictionOrder(residents, tier == Tier.CHAT)) {
            if (nowMs == Long.MIN_VALUE || !TaiFeaturePlan.keptForItsGroup(entry, null, nowMs)) return entry;
        }
        return null;
    }

    /** Idle residents in the order the tiers give them up; see {@link #nextVictim}. */
    @NonNull
    static List<TaiResidency.Entry> idleInEvictionOrder(@NonNull List<TaiResidency.Entry> residents, boolean includeChat) {
        ArrayList<TaiResidency.Entry> ordered = new ArrayList<>();
        for (TaiResidency.Kind kind : new TaiResidency.Kind[] {TaiResidency.Kind.EMBEDDING, TaiResidency.Kind.TTS,
                TaiResidency.Kind.IMAGE, TaiResidency.Kind.STT, TaiResidency.Kind.CHAT}) {
            if (kind == TaiResidency.Kind.CHAT && !includeChat) continue;
            ArrayList<TaiResidency.Entry> ofKind = new ArrayList<>();
            for (TaiResidency.Entry entry : residents) {
                if (entry.kind == kind && !entry.busy) ofKind.add(entry);
            }
            Collections.sort(ofKind, (a, b) -> Long.compare(a.lastUsedMs, b.lastUsedMs));
            ordered.addAll(ofKind);
        }
        return Collections.unmodifiableList(ordered);
    }

    /**
     * How long a resident of {@code kind} may sit unused before the watch closes it; {@code 0}
     * when the watch never does. Chat has its own timers in the backends (the idle-unload setting
     * and keep-warm), and the RUNTIME baseline is the process itself.
     */
    static long idleLimitMs(@NonNull TaiResidency.Kind kind) {
        return idleLimitMs(kind, STT_IDLE_MS);
    }

    /** {@link #idleLimitMs(TaiResidency.Kind)} with the STT limit the settings chose ({@code 0}: never on idle). */
    static long idleLimitMs(@NonNull TaiResidency.Kind kind, long sttIdleLimitMs) {
        switch (kind) {
            case EMBEDDING:
                return EMBEDDING_IDLE_MS;
            case STT:
                return sttIdleLimitMs;
            case TTS:
                return TTS_IDLE_MS;
            case IMAGE:
                return IMAGE_IDLE_MS;
            case VISION:
                // A one-shot vision graph lives for one stage and closes itself; nothing idles.
                return 0L;
            default:
                return 0L;
        }
    }

    /**
     * The residents that have outlived their kind's idle limit at {@code nowMs} and are not busy,
     * in eviction order. {@code lastUsedMs} is stamped at load and at the end of every use
     * ({@link TaiResidency#setBusy}), so a resident mid-use is never "idle".
     */
    @NonNull
    static List<TaiResidency.Entry> idleExpired(@NonNull List<TaiResidency.Entry> residents, long nowMs) {
        return idleExpired(residents, nowMs, STT_IDLE_MS);
    }

    /** {@link #idleExpired(List, long)} with the STT idle limit from settings ({@code TaiSettings#getSttIdleUnloadMinutes}). */
    @NonNull
    static List<TaiResidency.Entry> idleExpired(@NonNull List<TaiResidency.Entry> residents, long nowMs, long sttIdleLimitMs) {
        ArrayList<TaiResidency.Entry> expired = new ArrayList<>();
        for (TaiResidency.Entry entry : idleInEvictionOrder(residents, false)) {
            long limit = idleLimitMs(entry.kind, sttIdleLimitMs);
            if (limit > 0L && nowMs - entry.lastUsedMs >= limit) expired.add(entry);
        }
        return Collections.unmodifiableList(expired);
    }

    /**
     * Whether the process should exit now: it holds the RUNTIME baseline (so there is something to
     * give back) and nothing else, no request is in flight, and {@code idleSinceMs} — the later of
     * the last request that was not a status read and the moment the last model left — is
     * {@link #IDLE_EXIT_MS} ago. A process that never loaded a chat model holds nothing worth an
     * exit and is left alone.
     */
    static boolean idleExitDue(@NonNull List<TaiResidency.Entry> residents, int inFlight, long idleSinceMs, long nowMs) {
        if (inFlight > 0) return false;
        if (idleSinceMs <= 0L || nowMs - idleSinceMs < IDLE_EXIT_MS) return false;
        boolean baseline = false;
        for (TaiResidency.Entry entry : residents) {
            if (entry.kind == TaiResidency.Kind.RUNTIME) baseline = true;
            else return false;
        }
        return baseline;
    }
}
