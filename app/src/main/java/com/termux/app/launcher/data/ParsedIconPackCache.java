package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.IconPackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * The parsed icon packs ({@link IconPack}: a pack's name maps, no pixels), parsed once per
 * installed version and shared by every thread that resolves icons.
 *
 * <p>A pack that is not parsed yet is never answered "no pack". That answer is indistinguishable
 * from the system's icons, and every surface caches what it is given under the pack's identity,
 * so a stock icon handed out mid-parse stayed on screen until the next pack change. A large pack
 * (Arcticons parses for seconds) made that window wide enough to catch the dock on a cold start
 * and the drawer after the editor's restyle. Instead:
 * <ul>
 *   <li>a background caller ({@link #get}) parses on its own thread, or waits for the thread
 *   already parsing the same pack and takes its result;</li>
 *   <li>the main thread ({@link #getNow}) never parses and never waits: it is answered from what
 *   is held, or told {@link Answer#PENDING} while the parse runs on a background thread, and is
 *   called back once it lands.</li>
 * </ul>
 *
 * <p>The installed version is asked at most once per {@code recheckMs}: a known answer (parsed,
 * not installed, unreadable) is reused until then, after which a pack whose version moved is
 * parsed again. Bounded to {@code capacity} packs; dropping one forgets its answer too, so the
 * next ask parses it rather than reading the drop as "no pack".
 */
final class ParsedIconPackCache {

    /** Where packs come from. Both calls are slow (the package manager, the pack's XML). */
    interface Source {
        /** The installed pack, or null when {@code packageName} is not installed. */
        @Nullable IconPackInfo installed(@NonNull String packageName);

        /** {@code info}'s pack parsed, or null when it cannot be read. */
        @Nullable IconPack parse(@NonNull IconPackInfo info);
    }

    /** What a caller that cannot wait is told. */
    static final class Answer {
        /** The pack is being read: draw a stand-in now and keep nothing drawn from this answer. */
        static final Answer PENDING = new Answer(null, true);

        /** The pack, or null when it is not installed or cannot be read. */
        @Nullable final IconPack pack;
        final boolean pending;

        Answer(@Nullable IconPack pack, boolean pending) {
            this.pack = pack;
            this.pending = pending;
        }
    }

    private final long recheckMs;
    @NonNull private final LongSupplier clock;
    /** Guards the maps and the set; never held across a {@link Source} call. */
    private final Object lock = new Object();
    private final Map<String, IconPack> packs;
    /** When each package's answer was settled; absent while nothing is known (or a parse runs). */
    private final Map<String, Long> settledAt = new HashMap<>();
    /** One monitor per package asked for, held across its version check and parse. */
    private final Map<String, Object> parseLocks = new HashMap<>();
    /** Packages a background check was started for on behalf of {@link #getNow}, so later asks join it. */
    private final Set<String> checking = new HashSet<>();

    ParsedIconPackCache(int capacity, long recheckMs, @NonNull LongSupplier clock) {
        this.recheckMs = recheckMs;
        this.clock = clock;
        this.packs = new LinkedHashMap<String, IconPack>(capacity + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, IconPack> eldest) {
                if (size() <= capacity) return false;
                settledAt.remove(eldest.getKey());
                return true;
            }
        };
    }

    /**
     * {@code packageName}'s parsed pack; null when it is not installed or cannot be read. Parses
     * on the calling thread, or blocks while another thread parses the same pack: never on the
     * main thread, which asks {@link #getNow}.
     */
    @Nullable
    IconPack get(@NonNull String packageName, @NonNull Source source) {
        Object parseLock;
        synchronized (lock) {
            if (isSettled(packageName)) return packs.get(packageName);
            parseLock = parseLocks.get(packageName);
            if (parseLock == null) {
                parseLock = new Object();
                parseLocks.put(packageName, parseLock);
            }
        }
        synchronized (parseLock) {
            IconPack cached;
            synchronized (lock) {
                // A parse that finished while this caller waited answers it.
                if (isSettled(packageName)) return packs.get(packageName);
                cached = packs.get(packageName);
            }
            IconPackInfo info = source.installed(packageName);
            IconPack pack;
            if (info == null) {
                pack = null;
            } else if (cached != null && cached.info.versionCode == info.versionCode) {
                pack = cached;
            } else {
                pack = source.parse(info);
            }
            synchronized (lock) {
                if (pack == null) packs.remove(packageName);
                else packs.put(packageName, pack);
                // After the put: an eviction it caused has already forgotten the evicted answer.
                settledAt.put(packageName, clock.getAsLong());
            }
            return pack;
        }
    }

    /**
     * {@code packageName}'s pack without waiting, for the main thread. A settled answer is given
     * as it is. A pack held from before its recheck is due is given at once, and checked again
     * on {@code background}. A pack not held is {@link Answer#PENDING}: it is parsed on
     * {@code background}, and {@code onChanged} runs there once the parse lands, so whoever drew
     * a stand-in draws again. Asks arriving while that runs join it: one parse, one callback.
     * {@code onChanged} also runs when a recheck replaces the pack it answered with.
     */
    @NonNull
    Answer getNow(@NonNull String packageName, @NonNull Source source,
                  @NonNull Executor background, @NonNull Runnable onChanged) {
        final IconPack answered;
        boolean start;
        synchronized (lock) {
            answered = packs.get(packageName);
            if (isSettled(packageName)) return new Answer(answered, false);
            start = checking.add(packageName);
        }
        if (start) {
            background.execute(() -> {
                IconPack after;
                try {
                    after = get(packageName, source);
                } finally {
                    synchronized (lock) {
                        checking.remove(packageName);
                    }
                }
                if (answered == null || after != answered) onChanged.run();
            });
        }
        return answered != null ? new Answer(answered, false) : Answer.PENDING;
    }

    /**
     * Forgets every answer, keeping the parses: the next ask checks each pack's installed
     * version again and parses only one that moved. A pack upgraded in place is therefore read
     * afresh at once, and a pack that did not change is not parsed twice.
     */
    void expire() {
        synchronized (lock) {
            settledAt.clear();
        }
    }

    private boolean isSettled(@NonNull String packageName) {
        Long at = settledAt.get(packageName);
        return at != null && clock.getAsLong() - at < recheckMs;
    }
}
