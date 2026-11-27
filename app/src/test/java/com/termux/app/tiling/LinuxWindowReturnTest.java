package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which running Linux app goes back into which saved Linux window after a launcher restart (#419). */
public class LinuxWindowReturnTest {

    private static LinuxWindowReturn.Saved saved(long windowId, String appId, long linuxId) {
        return new LinuxWindowReturn.Saved(windowId, appId, linuxId);
    }

    private static LinuxWindowReturn.Running running(long linuxId, String appId) {
        return new LinuxWindowReturn.Running(linuxId, appId);
    }

    @Test
    public void aWindowTheHostKeptGoesBackByItsHostId() {
        LinuxWindowReturn.Result result = LinuxWindowReturn.match(
            Arrays.asList(saved(4, "firefox", 11), saved(7, "firefox", 12)),
            Arrays.asList(running(12, "firefox"), running(11, "firefox")));

        assertEquals(Long.valueOf(7), result.returned.get(12L));
        assertEquals(Long.valueOf(4), result.returned.get(11L));
        assertTrue(result.unclaimed.isEmpty());
        assertTrue(result.gone.isEmpty());
    }

    @Test
    public void anIdTakenByAnotherAppMatchesByAppIdInstead() {
        LinuxWindowReturn.Result result = LinuxWindowReturn.match(
            Arrays.asList(saved(3, "gimp", 5), saved(8, "firefox", 9)),
            Arrays.asList(running(5, "firefox")));

        assertEquals("the oldest firefox window, not gimp's", Long.valueOf(8), result.returned.get(5L));
        assertEquals(Collections.singletonList(3L), result.gone);
    }

    @Test
    public void withoutASavedHostIdTheOldestWindowOfTheAppIsTaken() {
        LinuxWindowReturn.Result result = LinuxWindowReturn.match(
            Arrays.asList(saved(9, "foot", LinuxWindowReturn.NO_LINUX_ID),
                saved(2, "foot", LinuxWindowReturn.NO_LINUX_ID)),
            Arrays.asList(running(40, "foot")));

        assertEquals(Long.valueOf(2), result.returned.get(40L));
        assertEquals(Collections.singletonList(9L), result.gone);
    }

    @Test
    public void anExactIdIsNotStolenByAnEarlierWindowOfTheSameApp() {
        LinuxWindowReturn.Result result = LinuxWindowReturn.match(
            Arrays.asList(saved(1, "firefox", 20), saved(2, "firefox", 21)),
            Arrays.asList(running(30, "firefox"), running(20, "firefox")));

        assertEquals(Long.valueOf(1), result.returned.get(20L));
        assertEquals(Long.valueOf(2), result.returned.get(30L));
    }

    @Test
    public void aRunningWindowNothingSavedTakesIsNew() {
        LinuxWindowReturn.Result result = LinuxWindowReturn.match(
            Collections.singletonList(saved(1, "firefox", 20)),
            Arrays.asList(running(20, "firefox"), running(21, "gimp"), running(22, "")));

        List<LinuxWindowReturn.Running> unclaimed = result.unclaimed;
        assertEquals(2, unclaimed.size());
        assertEquals(21L, unclaimed.get(0).linuxId);
        assertEquals(22L, unclaimed.get(1).linuxId);
    }

    @Test
    public void nothingRunningClosesEverySavedWindowOldestFirst() {
        LinuxWindowReturn.Result result = LinuxWindowReturn.match(
            Arrays.asList(saved(5, "a", 1), saved(2, "b", 2)), Collections.emptyList());

        assertEquals(Arrays.asList(2L, 5L), result.gone);
        assertTrue(result.returned.isEmpty());
    }
}
