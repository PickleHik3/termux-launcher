package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class LinuxAppWindowMatcherTest {

    private final Map<Integer, Integer> parent = new HashMap<>();
    private final LinuxAppWindowMatcher m = new LinuxAppWindowMatcher(pid -> {
        Integer p = parent.get(pid);
        return p == null ? 0 : p;
    });

    @Test
    public void aNativeWindowWhoseClientIsTheStartedPidMatches() {
        m.launched("a", 0);
        m.started("a", 500);
        m.windowCreated(7, 500, false, 100);
        assertEquals(7, m.windowFor("a"));
        assertEquals(-1, m.windowFor("b"));
    }

    @Test
    public void aDescendantOfTheStartedPidMatches() {
        parent.put(900, 800);
        parent.put(800, 500);
        m.launched("a", 0);
        m.started("a", 500);
        m.windowCreated(7, 900, false, 100);
        assertEquals(7, m.windowFor("a"));
    }

    @Test
    public void anUnrelatedNativeWindowDoesNotMatchAndAVanishedParentEndsTheWalk() {
        parent.put(900, 777);
        m.launched("a", 0);
        m.started("a", 500);
        m.windowCreated(7, 900, false, 100);
        m.windowCreated(8, 901, false, 100);
        assertEquals(-1, m.windowFor("a"));
    }

    @Test
    public void aParentLoopStops() {
        parent.put(900, 901);
        parent.put(901, 900);
        m.launched("a", 0);
        m.started("a", 500);
        m.windowCreated(7, 900, false, 100);
        assertEquals(-1, m.windowFor("a"));
    }

    @Test
    public void anX11WindowMatchesTheOneRecentLaunchWithoutAWindow() {
        m.launched("a", 1000);
        m.started("a", 500);
        m.windowCreated(7, 4242, true, 3000);
        assertEquals(7, m.windowFor("a"));
    }

    @Test
    public void anX11WindowTooLateOrAmbiguousOrNativeIsNotMatchedByTime() {
        m.launched("a", 1000);
        m.windowCreated(7, 4242, true, 1000 + LinuxAppWindowMatcher.X11_MATCH_WINDOW_MS + 1);
        assertEquals(-1, m.windowFor("a"));
        m.launched("b", 20000);
        m.launched("c", 20500);
        m.windowCreated(8, 4242, true, 21000);
        assertEquals(-1, m.windowFor("b"));
        assertEquals(-1, m.windowFor("c"));
        m.windowCreated(9, 4242, false, 21000);
        assertEquals(-1, m.windowFor("b"));
    }

    @Test
    public void aSecondX11WindowIsNotClaimedByTime() {
        m.launched("a", 1000);
        m.windowCreated(7, 4242, true, 2000);
        m.windowCreated(8, 4242, true, 2500);
        assertEquals(7, m.windowFor("a"));
        m.windowDestroyed(7);
        assertEquals(-1, m.windowFor("a"));
    }

    @Test
    public void destroyingTheLastWindowForgetsTheAppAndItsPids() {
        m.launched("a", 0);
        m.started("a", 500);
        m.windowCreated(7, 500, false, 10);
        m.windowDestroyed(7);
        assertEquals(-1, m.windowFor("a"));
        m.windowCreated(8, 500, false, 20);
        assertEquals(-1, m.windowFor("a"));
    }

    @Test
    public void clearForgetsEverything() {
        m.launched("a", 0);
        m.started("a", 500);
        m.windowCreated(7, 500, false, 10);
        m.clear();
        assertEquals(-1, m.windowFor("a"));
        m.windowCreated(8, 500, false, 20);
        assertEquals(-1, m.windowFor("a"));
    }

    @Test
    public void parentFromStatReadsPastANameWithBrackets() {
        assertEquals(12, LinuxAppWindowMatcher.parentFromStat("34 (a) b (c)) S 12 34 34 0"));
        assertEquals(1, LinuxAppWindowMatcher.parentFromStat("5 (sh) S 1 5 5"));
        assertEquals(0, LinuxAppWindowMatcher.parentFromStat("garbage"));
        assertEquals(0, LinuxAppWindowMatcher.parentFromStat(null));
    }
}
