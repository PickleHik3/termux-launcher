package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.IconPackInfo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parsed icon packs ({@link IconPack}: a pack's name maps, no pixels), parsed once per
 * installed version and shared by every thread that resolves icons.
 *
 * <p>A pack is parsed on whichever thread asks first: the catalogue's worker, the icon thread,
 * the main thread, the Icons mode's warm-up. A caller that arrives while another thread is
 * parsing the same pack waits for that parse and takes its result. It is never answered "no
 * pack" because the parse has not finished: that answer is indistinguishable from the system's
 * icons, and every surface caches what it is given under the pack's identity, so a stock icon
 * handed out mid-parse stayed on screen until the next pack change. A large pack (Arcticons
 * parses for seconds) made that window wide enough to catch the dock on a cold start and the
 * drawer after the editor's restyle dropped the parse.
 *
 * <p>The installed version is asked at most once per {@link #recheckMs}: a known answer
 * (parsed, not installed, unreadable) is reused until then, after which a pack whose version
 * moved is parsed again. Bounded to {@code capacity} packs; dropping one forgets its answer too,
 * so the next ask parses it rather than reading the drop as "no pack".
 */
final class ParsedIconPackCache {

    /** Where packs come from. Both calls are slow (the package manager, the pack's XML). */
    interface Source {
        /** The installed pack, or null when {@code packageName} is not installed. */
        @Nullable IconPackInfo installed(@NonNull String packageName);

        /** {@code info}'s pack parsed, or null when it cannot be read. */
        @Nullable IconPack parse(@NonNull IconPackInfo info);
    }

    private final long recheckMs;
    /** Guards the three maps; never held across a {@link Source} call. */
    private final Object lock = new Object();
    private final Map<String, IconPack> packs;
    /** When each package's answer was settled; absent while nothing is known (or a parse runs). */
    private final Map<String, Long> settledAt = new HashMap<>();
    /** One monitor per package asked for, held across its version check and parse. */
    private final Map<String, Object> parseLocks = new HashMap<>();

    ParsedIconPackCache(int capacity, long recheckMs) {
        this.recheckMs = recheckMs;
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
     * {@code packageName}'s parsed pack; null when it is not installed or cannot be read. Blocks
     * while another thread parses the same pack.
     */
    @Nullable
    IconPack get(@NonNull String packageName, long nowMs, @NonNull Source source) {
        Object parseLock;
        synchronized (lock) {
            if (isSettled(packageName, nowMs)) return packs.get(packageName);
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
                if (isSettled(packageName, nowMs)) return packs.get(packageName);
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
                settledAt.put(packageName, nowMs);
            }
            return pack;
        }
    }

    /** Forgets every parse and every answer; the next ask of each pack checks and parses again. */
    void clear() {
        synchronized (lock) {
            packs.clear();
            settledAt.clear();
        }
    }

    private boolean isSettled(@NonNull String packageName, long nowMs) {
        Long at = settledAt.get(packageName);
        return at != null && nowMs - at < recheckMs;
    }
}
