package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LinuxWindowAppIdsTest {

    @Test public void aClassIsScannedOnceAndAnswersAfterwards() {
        List<Runnable> queued = new ArrayList<>();
        int[] scans = {0};
        int[] resolved = {0};
        LinuxWindowAppIds ids = new LinuxWindowAppIds(c -> { scans[0]++; return "app-" + c; },
            queued::add, () -> resolved[0]++);
        assertEquals("", ids.of("gimp"));
        assertEquals("", ids.of("gimp"));
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(1, resolved[0]);
        assertEquals("app-gimp", ids.of("gimp"));
        assertEquals(1, scans[0]);
    }

    @Test public void aClassNoAppHasReadsAsNone() {
        List<Runnable> queued = new ArrayList<>();
        LinuxWindowAppIds ids = new LinuxWindowAppIds(c -> null, queued::add, () -> { });
        ids.of("x");
        queued.get(0).run();
        assertEquals("", ids.of("x"));
        assertEquals(1, queued.size());
    }

    @Test public void anEmptyClassIsNeverScanned() {
        List<Runnable> queued = new ArrayList<>();
        LinuxWindowAppIds ids = new LinuxWindowAppIds(c -> "a", queued::add, () -> { });
        assertEquals("", ids.of(""));
        assertEquals("", ids.of(null));
        assertEquals(0, queued.size());
    }

    @Test public void aClassNoAppHadIsAskedAgainAfterAMinute() {
        List<Runnable> queued = new ArrayList<>();
        long[] now = {0L};
        String[] answer = {null};
        LinuxWindowAppIds ids = new LinuxWindowAppIds(c -> answer[0], queued::add, () -> { }, () -> now[0]);
        ids.of("x");
        queued.get(0).run();
        now[0] = LinuxWindowAppIds.EMPTY_MS - 1;
        assertEquals("", ids.of("x"));
        assertEquals(1, queued.size());
        answer[0] = "late-app";
        now[0] = LinuxWindowAppIds.EMPTY_MS + 1;
        ids.of("x");
        assertEquals(2, queued.size());
        queued.get(1).run();
        assertEquals("late-app", ids.of("x"));
    }
}
