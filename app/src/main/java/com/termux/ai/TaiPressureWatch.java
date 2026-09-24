package com.termux.ai;

import android.content.ComponentCallbacks2;

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
 * <p>Tiers, from Android's own numbers ({@code MemoryInfo.threshold} is the level at which the
 * system starts killing cached apps; the floor is {@link TaiLoadBudget#floorBytes}, twice that):
 *
 * <pre>
 *   lowMemory                         → RELEASE_ALL: cancel in-flight work, unload everything
 *   availMem &lt; threshold × 1.25       → CHAT: also give up idle chat
 *   availMem &lt; floor                  → AUXILIARY: give up idle embeddings, then idle STT
 *   otherwise                         → NONE
 * </pre>
 *
 * The two eviction tiers give up one resident per evaluation, cheapest first, and the watch looks
 * again on its next tick: MemAvailable is noisy and memory comes back late (§3c of the plan), so
 * closing everything the moment the line is crossed would over-shoot. A busy resident is never
 * touched by either tier; only {@code lowMemory} interrupts work.
 */
final class TaiPressureWatch {

    /** In the order the actions escalate; {@link Enum#compareTo} is meaningful. */
    enum Tier { NONE, AUXILIARY, CHAT, RELEASE_ALL }

    /** Idle chat is given up below this many hundredths of {@code MemoryInfo.threshold}. */
    static final long CHAT_THRESHOLD_PERCENT = 125L;

    /** An embedding interpreter nobody has used for this long is closed. */
    static final long EMBEDDING_IDLE_MS = TimeUnit.MINUTES.toMillis(5);
    /** A speech-to-text model nobody has used for this long is closed. */
    static final long STT_IDLE_MS = TimeUnit.MINUTES.toMillis(2);
    /**
     * With only the {@link TaiResidency#RUNTIME_BASELINE_BYTES} entry left and no request for this
     * long, the process exits to give the baseline back (§3b of the plan).
     */
    static final long IDLE_EXIT_MS = TimeUnit.MINUTES.toMillis(10);

    private TaiPressureWatch() {
    }

    /**
     * The tier for one reading of {@code MemoryInfo}. Unknown free memory ({@code <= 0}) selects
     * nothing on its own; an unknown threshold ({@code <= 0}) leaves the chat line undefined, so
     * only the floor (then the old reserve, see {@link TaiLoadBudget#floorBytes}) and
     * {@code lowMemory} apply.
     */
    @NonNull
    static Tier tier(long availBytes, long floorBytes, long thresholdBytes, boolean lowMemory) {
        if (lowMemory) return Tier.RELEASE_ALL;
        if (availBytes <= 0L) return Tier.NONE;
        if (thresholdBytes > 0L && availBytes < thresholdBytes * CHAT_THRESHOLD_PERCENT / 100L) return Tier.CHAT;
        if (floorBytes > 0L && availBytes < floorBytes) return Tier.AUXILIARY;
        return Tier.NONE;
    }

    /**
     * The tier an {@code onTrimMemory} level maps to. Only the running levels count — the system
     * telling a process it is low while it runs — and they stop being delivered from Android 14,
     * which is why the poll exists. {@code UI_HIDDEN} and the background levels are lifecycle,
     * not pressure: backgrounding the launcher must not drop a warm model.
     */
    @NonNull
    static Tier tierForTrimLevel(int level) {
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
                return Tier.AUXILIARY;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                return Tier.CHAT;
            default:
                return Tier.NONE;
        }
    }

    /**
     * The one resident {@code tier} gives up next, or {@code null} when it has nothing to give:
     * idle embeddings first, then idle STT, then — in {@link Tier#CHAT} only — idle chat; the
     * least recently used first within a kind. Busy residents and the RUNTIME baseline are never
     * candidates. {@link Tier#RELEASE_ALL} is not an eviction and answers {@code null}.
     */
    @Nullable
    static TaiResidency.Entry nextVictim(@NonNull List<TaiResidency.Entry> residents, @NonNull Tier tier) {
        if (tier != Tier.AUXILIARY && tier != Tier.CHAT) return null;
        List<TaiResidency.Entry> ordered = idleInEvictionOrder(residents, tier == Tier.CHAT);
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    /** Idle residents in the order the tiers give them up; see {@link #nextVictim}. */
    @NonNull
    static List<TaiResidency.Entry> idleInEvictionOrder(@NonNull List<TaiResidency.Entry> residents, boolean includeChat) {
        ArrayList<TaiResidency.Entry> ordered = new ArrayList<>();
        for (TaiResidency.Kind kind : new TaiResidency.Kind[] {TaiResidency.Kind.EMBEDDING, TaiResidency.Kind.STT, TaiResidency.Kind.CHAT}) {
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
