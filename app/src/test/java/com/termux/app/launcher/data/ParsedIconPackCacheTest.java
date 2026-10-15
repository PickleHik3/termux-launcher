package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.termux.app.launcher.model.IconPackInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

public class ParsedIconPackCacheTest {

    private static final long RECHECK_MS = 10_000L;

    private final Map<String, Long> installedVersions = new HashMap<>();
    private final AtomicInteger installedCalls = new AtomicInteger();
    private final AtomicInteger parses = new AtomicInteger();
    private final ExecutorService threads = Executors.newFixedThreadPool(2);

    private final ParsedIconPackCache.Source source = new ParsedIconPackCache.Source() {
        @Override
        public IconPackInfo installed(String packageName) {
            installedCalls.incrementAndGet();
            Long version = installedVersions.get(packageName);
            return version == null ? null : new IconPackInfo(packageName, packageName, version, false);
        }

        @Override
        public IconPack parse(IconPackInfo info) {
            parses.incrementAndGet();
            return pack(info);
        }
    };

    @After
    public void tearDown() {
        threads.shutdownNow();
    }

    private static IconPack pack(IconPackInfo info) {
        return new IconPack(info, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 1f);
    }

    /**
     * The cold-start and post-restyle bug: a thread asking while another parsed a large pack was
     * answered "no pack", resolved the system icon and cached it under the pack's identity.
     */
    @Test
    public void aCallerArrivingMidParseWaitsForThatParse() throws Exception {
        installedVersions.put("arcticons", 1570L);
        CountDownLatch parsing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ParsedIconPackCache.Source slow = new ParsedIconPackCache.Source() {
            @Override
            public IconPackInfo installed(String packageName) {
                return source.installed(packageName);
            }

            @Override
            public IconPack parse(IconPackInfo info) {
                parsing.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return source.parse(info);
            }
        };
        ParsedIconPackCache cache = new ParsedIconPackCache(2, RECHECK_MS);

        Future<IconPack> first = threads.submit(() -> cache.get("arcticons", 0L, slow));
        parsing.await(5, TimeUnit.SECONDS);
        Future<IconPack> second = threads.submit(() -> cache.get("arcticons", 1L, slow));
        try {
            IconPack early = second.get(200, TimeUnit.MILLISECONDS);
            fail("answered while the parse was in flight: " + early);
        } catch (TimeoutException expected) {
            // Waiting on the parse, as it should.
        }
        release.countDown();

        IconPack parsed = first.get(5, TimeUnit.SECONDS);
        assertNotNull(parsed);
        assertSame(parsed, second.get(5, TimeUnit.SECONDS));
        assertEquals(1, parses.get());
    }

    @Test
    public void aKnownAnswerIsReusedUntilTheRecheckAndAnUpgradeIsParsedAgain() {
        installedVersions.put("pack", 1L);
        ParsedIconPackCache cache = new ParsedIconPackCache(2, RECHECK_MS);

        IconPack first = cache.get("pack", 0L, source);
        assertSame(first, cache.get("pack", RECHECK_MS - 1, source));
        assertEquals(1, installedCalls.get());

        assertSame(first, cache.get("pack", RECHECK_MS, source));
        assertEquals(2, installedCalls.get());
        assertEquals(1, parses.get());

        installedVersions.put("pack", 2L);
        IconPack upgraded = cache.get("pack", 2 * RECHECK_MS, source);
        assertNotSame(first, upgraded);
        assertEquals(2L, upgraded.info.versionCode);
        assertEquals(2, parses.get());
    }

    /** Dropping a pack to make room forgets its answer, so the next ask parses, not "no pack". */
    @Test
    public void anEvictedPackIsParsedAgainRatherThanAnsweredNull() {
        installedVersions.put("global", 1L);
        installedVersions.put("picked", 1L);
        ParsedIconPackCache cache = new ParsedIconPackCache(1, RECHECK_MS);

        cache.get("global", 0L, source);
        cache.get("picked", 1L, source);

        assertNotNull(cache.get("global", 2L, source));
        assertEquals(3, parses.get());
    }

    @Test
    public void aMissingPackAnswersNullUntilItIsInstalledAndRechecked() {
        ParsedIconPackCache cache = new ParsedIconPackCache(2, RECHECK_MS);

        assertNull(cache.get("pack", 0L, source));
        installedVersions.put("pack", 1L);
        assertNull(cache.get("pack", 1L, source));
        assertNotNull(cache.get("pack", RECHECK_MS, source));
    }

    @Test
    public void clearForgetsEveryAnswer() {
        installedVersions.put("pack", 1L);
        ParsedIconPackCache cache = new ParsedIconPackCache(2, RECHECK_MS);
        IconPack first = cache.get("pack", 0L, source);

        cache.clear();

        assertNotSame(first, cache.get("pack", 1L, source));
        assertEquals(2, parses.get());
    }
}
