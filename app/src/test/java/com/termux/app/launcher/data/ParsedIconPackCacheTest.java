package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.app.launcher.model.IconPackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Test;

public class ParsedIconPackCacheTest {

    private static final long RECHECK_MS = 10_000L;

    private final Map<String, Long> installedVersions = new ConcurrentHashMap<>();
    private final AtomicInteger installedCalls = new AtomicInteger();
    private final AtomicInteger parses = new AtomicInteger();
    private final AtomicLong clock = new AtomicLong();
    private final ExecutorService threads = Executors.newFixedThreadPool(2);
    /** Background work {@link ParsedIconPackCache#getNow} handed off, run when the test says. */
    private final List<Runnable> handedOff = new ArrayList<>();
    private final AtomicInteger landings = new AtomicInteger();

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

    private ParsedIconPackCache cache(int capacity) {
        return new ParsedIconPackCache(capacity, RECHECK_MS, clock::get);
    }

    private ParsedIconPackCache.Answer getNow(ParsedIconPackCache cache, String packageName) {
        return cache.getNow(packageName, source, handedOff::add, landings::incrementAndGet);
    }

    private void runHandedOff() {
        List<Runnable> work = new ArrayList<>(handedOff);
        handedOff.clear();
        for (Runnable runnable : work) runnable.run();
    }

    /**
     * The cold-start and post-restyle bug: a thread asking while another parsed a large pack was
     * answered "no pack", resolved the system icon and cached it under the pack's identity.
     */
    @Test
    public void aBackgroundCallerArrivingMidParseWaitsForThatParse() throws Exception {
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
        ParsedIconPackCache cache = cache(2);

        Future<IconPack> first = threads.submit(() -> cache.get("arcticons", slow));
        parsing.await(5, TimeUnit.SECONDS);
        Future<IconPack> second = threads.submit(() -> cache.get("arcticons", slow));
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

    /**
     * The main thread is never held by a parse: told "pending" at once, while the parse runs
     * elsewhere; asks in the meantime join it; the landing is announced once, and the next ask
     * has the pack.
     */
    @Test
    public void theMainThreadIsToldPendingAndCalledBackOnceWhenThePackLands() {
        installedVersions.put("arcticons", 1570L);
        ParsedIconPackCache cache = cache(2);

        ParsedIconPackCache.Answer first = getNow(cache, "arcticons");
        ParsedIconPackCache.Answer again = getNow(cache, "arcticons");

        assertTrue(first.pending);
        assertNull(first.pack);
        assertTrue(again.pending);
        assertEquals(0, parses.get());
        assertEquals(1, handedOff.size());

        runHandedOff();

        assertEquals(1, parses.get());
        assertEquals(1, landings.get());
        ParsedIconPackCache.Answer landed = getNow(cache, "arcticons");
        assertFalse(landed.pending);
        assertNotNull(landed.pack);
        assertTrue(handedOff.isEmpty());
    }

    /** A background parse already running is joined, not repeated, and still announced. */
    @Test
    public void theMainThreadJoinsAParseAlreadyRunningOnAWorker() throws Exception {
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
        ParsedIconPackCache cache = cache(2);
        Future<IconPack> worker = threads.submit(() -> cache.get("arcticons", slow));
        parsing.await(5, TimeUnit.SECONDS);

        ParsedIconPackCache.Answer answer = cache.getNow("arcticons", slow,
            threads::execute, landings::incrementAndGet);
        assertTrue(answer.pending);

        release.countDown();
        IconPack parsed = worker.get(5, TimeUnit.SECONDS);
        long deadline = System.currentTimeMillis() + 5_000L;
        while (landings.get() == 0 && System.currentTimeMillis() < deadline) Thread.yield();

        assertEquals(1, landings.get());
        assertEquals(1, parses.get());
        assertSame(parsed, cache.getNow("arcticons", slow, threads::execute, landings::incrementAndGet).pack);
    }

    /** A pack held from before its recheck is answered at once and checked in the background. */
    @Test
    public void theMainThreadIsAnsweredAHeldPackWhileItsVersionIsCheckedAgain() {
        installedVersions.put("pack", 1L);
        ParsedIconPackCache cache = cache(2);
        IconPack held = cache.get("pack", source);

        cache.expire();
        ParsedIconPackCache.Answer answer = getNow(cache, "pack");
        assertFalse(answer.pending);
        assertSame(held, answer.pack);

        runHandedOff();
        assertEquals(1, parses.get());
        assertEquals(0, landings.get());

        installedVersions.put("pack", 2L);
        clock.addAndGet(RECHECK_MS);
        assertSame(held, getNow(cache, "pack").pack);
        runHandedOff();
        assertEquals(2, parses.get());
        assertEquals(1, landings.get());
        assertEquals(2L, getNow(cache, "pack").pack.info.versionCode);
    }

    @Test
    public void aKnownAnswerIsReusedUntilTheRecheckAndAnUpgradeIsParsedAgain() {
        installedVersions.put("pack", 1L);
        ParsedIconPackCache cache = cache(2);

        IconPack first = cache.get("pack", source);
        clock.set(RECHECK_MS - 1);
        assertSame(first, cache.get("pack", source));
        assertEquals(1, installedCalls.get());

        clock.set(RECHECK_MS);
        assertSame(first, cache.get("pack", source));
        assertEquals(2, installedCalls.get());
        assertEquals(1, parses.get());

        installedVersions.put("pack", 2L);
        clock.set(3 * RECHECK_MS);
        IconPack upgraded = cache.get("pack", source);
        assertNotSame(first, upgraded);
        assertEquals(2L, upgraded.info.versionCode);
        assertEquals(2, parses.get());
    }

    /** Dropping a pack to make room forgets its answer, so the next ask parses, not "no pack". */
    @Test
    public void anEvictedPackIsParsedAgainRatherThanAnsweredNull() {
        installedVersions.put("global", 1L);
        installedVersions.put("picked", 1L);
        ParsedIconPackCache cache = cache(1);

        cache.get("global", source);
        cache.get("picked", source);

        assertNotNull(cache.get("global", source));
        assertEquals(3, parses.get());
    }

    @Test
    public void aMissingPackAnswersNullUntilItIsInstalledAndRechecked() {
        ParsedIconPackCache cache = cache(2);

        assertNull(cache.get("pack", source));
        installedVersions.put("pack", 1L);
        assertNull(cache.get("pack", source));
        clock.set(RECHECK_MS);
        assertNotNull(cache.get("pack", source));
    }

    /** Expiring rechecks every version but keeps a parse whose pack did not change. */
    @Test
    public void expireChecksAgainWithoutParsingAnUnchangedPack() {
        installedVersions.put("pack", 1L);
        ParsedIconPackCache cache = cache(2);
        IconPack first = cache.get("pack", source);

        cache.expire();

        assertSame(first, cache.get("pack", source));
        assertEquals(2, installedCalls.get());
        assertEquals(1, parses.get());
    }
}
