package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DisplayCardCacheTest {

    private static LinuxAppCatalog.LinuxApp app(String desktopFile) {
        return new LinuxAppCatalog.LinuxApp(desktopFile, desktopFile + " name", desktopFile,
            desktopFile + "-icon", "");
    }

    private final ArrayDeque<Runnable> mBackground = new ArrayDeque<>();
    private final ArrayDeque<Runnable> mMain = new ArrayDeque<>();
    private final AtomicInteger mScans = new AtomicInteger();
    private final AtomicInteger mLoads = new AtomicInteger();
    private List<LinuxAppCatalog.LinuxApp> mCatalog = Arrays.asList(app("gimp"), app("xterm"));

    private DisplayCardCache<String> cache() {
        return new DisplayCardCache<>(
            () -> { mScans.incrementAndGet(); return mCatalog; },
            a -> { mLoads.incrementAndGet(); return a.icon; },
            mBackground::add, mMain::add);
    }

    private void drain() {
        while (!mBackground.isEmpty() || !mMain.isEmpty()) {
            while (!mBackground.isEmpty()) mBackground.poll().run();
            while (!mMain.isEmpty()) mMain.poll().run();
        }
    }

    @Test
    public void readsNeverScanOnTheCallingThread() {
        DisplayCardCache<String> cache = cache();
        assertTrue(cache.apps().isEmpty());
        assertNull(cache.find("gimp"));
        assertNull(cache.card("gimp"));
        assertEquals(0, mScans.get());
        assertEquals(0, mLoads.get());
    }

    @Test
    public void refreshScansOnTheBackgroundAndLandsOnMain() {
        DisplayCardCache<String> cache = cache();
        AtomicInteger done = new AtomicInteger();
        cache.refresh(Collections.singletonList("gimp"), done::incrementAndGet);
        // Asked, not run: nothing happens on the caller's thread.
        assertEquals(0, mScans.get());
        mBackground.poll().run();
        assertEquals(1, mScans.get());
        // Scanned, not yet handed over: the old values still answer.
        assertNull(cache.find("gimp"));
        mMain.poll().run();
        assertEquals(1, done.get());
        assertNotNull(cache.find("gimp"));
        DisplayCardCache.Card<String> card = cache.card("gimp");
        assertNotNull(card);
        assertEquals("gimp name", card.app.name);
        assertEquals("gimp-icon", card.icon);
        // Only bound apps load an icon.
        assertEquals(1, mLoads.get());
        assertNull(cache.card("xterm"));
        // Reads after the refresh scan nothing more.
        cache.find("xterm");
        cache.card("gimp");
        assertEquals(1, mScans.get());
    }

    @Test
    public void aRefreshAskedWhileOneRunsRunsOnceMoreAfter() {
        DisplayCardCache<String> cache = cache();
        cache.refresh(Collections.singletonList("gimp"), null);
        cache.refresh(Collections.singletonList("xterm"), null);
        cache.refresh(Collections.singletonList("xterm"), null);
        drain();
        assertEquals(2, mScans.get());
        assertNotNull(cache.card("xterm"));
        assertNull(cache.card("gimp"));
    }

    @Test
    public void anAppThatWentLeavesNoCard() {
        DisplayCardCache<String> cache = cache();
        cache.refresh(Collections.singletonList("gimp"), null);
        drain();
        mCatalog = Collections.singletonList(app("xterm"));
        cache.refresh(Collections.singletonList("gimp"), null);
        drain();
        assertNull(cache.card("gimp"));
        assertNull(cache.find("gimp"));
    }
}
