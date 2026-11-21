package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

/** The crash marker: when it turns the Ghostty core off, for how long, and what it never touches. */
public class GhosttyCrashGuardTest {

    private static final int OLD_PID = 4100, NEW_PID = 5200;
    private static final long BUILD = 1_700_000_000_000L;

    /** An in-memory store that counts writes. */
    static class FakeStore implements GhosttyCrashGuard.Store {
        String marker, disabled;
        int markerWrites;

        @Override public String readMarker() { return marker; }
        @Override public void writeMarker(String m) { marker = m; markerWrites++; }
        @Override public void clearMarker() { marker = null; }
        @Override public String readDisabled() { return disabled; }
        @Override public void writeDisabled(String r) { disabled = r; }
        @Override public void clearDisabled() { disabled = null; }
    }

    /** Exit records: one process, its reason and when it ended. */
    static final class FakeExits implements GhosttyCrashGuard.ExitReasons {
        int pid = OLD_PID, reason;
        long endedAt;
        final List<Integer> asked = new ArrayList<>();

        FakeExits(int reason, long endedAt) {
            this.reason = reason;
            this.endedAt = endedAt;
        }

        @Override
        public int reasonFor(int pid, long sinceMillis) {
            asked.add(pid);
            return pid == this.pid && endedAt >= sinceMillis ? reason : GhosttyCrashGuard.REASON_UNKNOWN;
        }
    }

    private long mNow = 1_000_000L;
    private final List<String> mLog = new ArrayList<>();

    private GhosttyCrashGuard install(FakeStore store, GhosttyCrashGuard.ExitReasons exits, int pid, long build) {
        return GhosttyCrashGuard.install(store, exits, pid, build, () -> mNow, mLog::add);
    }

    @After
    public void tearDown() {
        GhosttyCrashGuard.uninstallForTests();
    }

    /** A process that wrote to Ghostty and died before closing its panes leaves the marker behind. */
    private FakeStore storeLeftByDeadProcess() {
        FakeStore store = new FakeStore();
        install(store, null, OLD_PID, BUILD);
        GhosttyCrashGuard.beforeWrite();
        assertEquals(OLD_PID + " " + mNow, store.marker);
        GhosttyCrashGuard.uninstallForTests();
        mNow += 60_000;
        return store;
    }

    @Test
    public void theMarkerIsWrittenOnceBeforeTheFirstWrite() {
        FakeStore store = new FakeStore();
        install(store, null, NEW_PID, BUILD);
        assertNull(store.marker);
        GhosttyCrashGuard.beforeWrite();
        GhosttyCrashGuard.beforeWrite();
        GhosttyCrashGuard.beforeWrite();
        assertEquals(1, store.markerWrites);
        assertEquals(NEW_PID + " " + mNow, store.marker);
    }

    @Test
    public void releasingTheLastCoreClearsTheMarker() {
        FakeStore store = new FakeStore();
        install(store, null, NEW_PID, BUILD);
        GhosttyCrashGuard.beforeWrite();
        GhosttyCrashGuard.onCoreReleased(1);
        assertTrue("another pane still writes", store.marker != null);
        GhosttyCrashGuard.onCoreReleased(0);
        assertNull(store.marker);
        // The next write arms it again.
        GhosttyCrashGuard.beforeWrite();
        assertEquals(2, store.markerWrites);
    }

    @Test
    public void aNativeCrashAfterTheMarkerTurnsTheCoreOff() {
        FakeStore store = storeLeftByDeadProcess();
        FakeExits exits = new FakeExits(GhosttyCrashGuard.REASON_CRASH_NATIVE, mNow - 30_000);
        GhosttyCrashGuard guard = install(store, exits, NEW_PID, BUILD);
        assertTrue(guard.disabled());
        assertTrue(GhosttyCrashGuard.isDisabledAfterCrash());
        assertEquals(List.of(OLD_PID), exits.asked);
        assertNull("the marker is consumed", store.marker);
        assertTrue(store.disabled.startsWith(BUILD + "|"));
        assertEquals(1, mLog.size());
        assertTrue(GhosttyCrashGuard.disabledReason().contains("native"));
    }

    @Test
    public void otherDeathsLeaveTheCoreOn() {
        int[] reasons = {GhosttyCrashGuard.REASON_UNKNOWN, 2 /* SIGNALED */, 3 /* LOW_MEMORY */, 4 /* CRASH (Java) */,
            10 /* USER_REQUESTED */};
        for (int reason : reasons) {
            FakeStore store = storeLeftByDeadProcess();
            install(store, new FakeExits(reason, mNow - 30_000), NEW_PID, BUILD);
            assertFalse("reason " + reason, GhosttyCrashGuard.isDisabledAfterCrash());
            assertNull(store.marker);
            assertNull(store.disabled);
        }
    }

    @Test
    public void aNativeCrashFromBeforeTheMarkerDoesNotCount() {
        FakeStore store = storeLeftByDeadProcess();
        // The same pid crashed once long ago (pid reuse), before this marker was written.
        install(store, new FakeExits(GhosttyCrashGuard.REASON_CRASH_NATIVE, 10), NEW_PID, BUILD);
        assertFalse(GhosttyCrashGuard.isDisabledAfterCrash());
    }

    @Test
    public void belowApi30TheMarkerAloneTurnsTheCoreOff() {
        FakeStore store = storeLeftByDeadProcess();
        install(store, null, NEW_PID, BUILD);
        assertTrue(GhosttyCrashGuard.isDisabledAfterCrash());
    }

    @Test
    public void theCoreStaysOffAcrossRestartsOfTheSameBuild() {
        FakeStore store = storeLeftByDeadProcess();
        install(store, null, NEW_PID, BUILD);
        GhosttyCrashGuard.uninstallForTests();
        // A clean restart: no marker this time.
        install(store, new FakeExits(GhosttyCrashGuard.REASON_UNKNOWN, 0), NEW_PID + 1, BUILD);
        assertTrue(GhosttyCrashGuard.isDisabledAfterCrash());
    }

    @Test
    public void anUpdateLetsTheCoreTryAgain() {
        FakeStore store = storeLeftByDeadProcess();
        install(store, null, NEW_PID, BUILD);
        GhosttyCrashGuard.uninstallForTests();
        install(store, null, NEW_PID + 1, BUILD + 1);
        assertFalse(GhosttyCrashGuard.isDisabledAfterCrash());
        assertNull("the old record is dropped", store.disabled);
    }

    @Test
    public void aMarkerFromThisProcessIsNotADeath() {
        FakeStore store = new FakeStore();
        store.marker = NEW_PID + " 5";
        install(store, null, NEW_PID, BUILD);
        assertFalse(GhosttyCrashGuard.isDisabledAfterCrash());
    }

    @Test
    public void withNoGuardNothingIsWrittenAndNothingIsOff() {
        GhosttyCrashGuard.beforeWrite();
        GhosttyCrashGuard.onCoreReleased(0);
        assertFalse(GhosttyCrashGuard.isDisabledAfterCrash());
        assertNull(GhosttyCrashGuard.disabledReason());
    }

    @Test
    public void aStoreThatFailsNeverBreaksAWrite() {
        FakeStore store = new FakeStore() {
            @Override public void writeMarker(String m) { markerWrites++; throw new IllegalStateException("disk full"); }
        };
        install(store, null, NEW_PID, BUILD);
        GhosttyCrashGuard.beforeWrite();
        GhosttyCrashGuard.beforeWrite();
        assertEquals("not retried on every write", 1, store.markerWrites);
    }
}
