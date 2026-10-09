package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class IconPackResourcesCacheTest {

    private final List<String> loads = new ArrayList<>();
    private final IconPackResourcesCache<Object> cache = new IconPackResourcesCache<>(2);

    private Object load(String packageName) {
        loads.add(packageName);
        return new Object();
    }

    @Test
    public void everyDrawableFromOneInstalledPackSharesOneLoad() {
        Object first = cache.get("pack", "/data/app/a/base.apk", this::load);
        Object second = cache.get("pack", "/data/app/a/base.apk", this::load);
        Object third = cache.get("pack", "/data/app/a/base.apk", this::load);

        assertSame(first, second);
        assertSame(first, third);
        assertEquals(1, loads.size());
    }

    @Test
    public void anUpdatedPackIsLoadedAgain() {
        Object before = cache.get("pack", "/data/app/a/base.apk", this::load);
        Object after = cache.get("pack", "/data/app/b/base.apk", this::load);

        assertEquals(2, loads.size());
        assertSame(after, cache.get("pack", "/data/app/b/base.apk", this::load));
        assertEquals(2, loads.size());
        assertEquals(false, before == after);
    }

    @Test
    public void anUninstalledPackAnswersNullAndIsForgotten() {
        cache.get("pack", "/data/app/a/base.apk", this::load);

        assertNull(cache.get("pack", null, this::load));
        assertEquals(0, cache.size());
        assertEquals(1, loads.size());
    }

    @Test
    public void aPackThatFailsToLoadIsNotKept() {
        assertNull(cache.get("pack", "/data/app/a/base.apk", name -> null));
        assertEquals(0, cache.size());
    }

    @Test
    public void onlyTheMostRecentlyUsedPacksAreKept() {
        cache.get("one", "/1", this::load);
        cache.get("two", "/2", this::load);
        cache.get("one", "/1", this::load);
        cache.get("three", "/3", this::load);

        assertEquals(2, cache.size());
        cache.get("one", "/1", this::load);
        assertEquals("the recently used pack stayed", 3, loads.size());
        cache.get("two", "/2", this::load);
        assertEquals("the eldest was dropped", 4, loads.size());
    }

    @Test
    public void clearDropsEverything() {
        cache.get("pack", "/a", this::load);
        cache.clear();
        cache.get("pack", "/a", this::load);
        assertEquals(2, loads.size());
    }
}
