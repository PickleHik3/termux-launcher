package com.termux.privileged;

import static com.termux.privileged.PrivilegedBackendManager.BackendState;
import static com.termux.privileged.PrivilegedChoice.AUTO;
import static com.termux.privileged.PrivilegedChoice.First;
import static com.termux.privileged.PrivilegedChoice.OFF;
import static com.termux.privileged.PrivilegedChoice.ROOT;
import static com.termux.privileged.PrivilegedChoice.SHIZUKU;
import static com.termux.privileged.PrivilegedChoice.Status;
import static com.termux.privileged.PrivilegedChoice.migrate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrivilegedChoiceTest {

    @Test
    public void migrationMapsTheOldSwitches() {
        assertEquals(OFF, migrate(false, true, true));
        assertEquals(OFF, migrate(false, false, false));
        assertEquals(AUTO, migrate(true, true, true));
        assertEquals(SHIZUKU, migrate(true, true, false));
        assertEquals(ROOT, migrate(true, false, true));
        assertEquals(OFF, migrate(true, false, false));
    }

    @Test
    public void storedValuesRoundTripAndUnknownIsNull() {
        for (PrivilegedChoice choice : PrivilegedChoice.values()) {
            assertEquals(choice, PrivilegedChoice.fromStored(choice.stored));
        }
        assertNull(PrivilegedChoice.fromStored(null));
        assertNull(PrivilegedChoice.fromStored("nope"));
    }

    @Test
    public void backendSelectionFollowsTheChoice() {
        assertEquals(First.SHIZUKU, AUTO.first());
        assertTrue(AUTO.shellAfterShizukuFailed());
        assertEquals(First.SHIZUKU, SHIZUKU.first());
        assertFalse(SHIZUKU.shellAfterShizukuFailed());
        assertEquals(First.SHELL, ROOT.first());
        assertEquals(First.NONE, OFF.first());
    }

    @Test
    public void whichBackendsEachChoiceAllows() {
        assertTrue(AUTO.allowsShizuku() && AUTO.allowsRoot() && AUTO.allowsRish());
        assertTrue(SHIZUKU.allowsShizuku());
        assertFalse(SHIZUKU.allowsRoot() || SHIZUKU.allowsRish());
        assertTrue(ROOT.allowsRoot());
        assertFalse(ROOT.allowsShizuku() || ROOT.allowsRish());
        assertFalse(OFF.allowsShizuku() || OFF.allowsRoot() || OFF.allowsRish());
    }

    @Test
    public void statusLineSaysWhatIsRunning() {
        assertEquals(Status.OFF, OFF.status(BackendState.READY, true));
        assertEquals(Status.USING_SHIZUKU, AUTO.status(BackendState.READY, true));
        assertEquals(Status.USING_ROOT, AUTO.status(BackendState.FALLBACK_SHELL, false));
        assertEquals(Status.USING_ROOT, ROOT.status(BackendState.FALLBACK_SHELL, false));
        assertEquals(Status.NEEDS_PERMISSION, SHIZUKU.status(BackendState.PERMISSION_DENIED, true));
        assertEquals(Status.SHIZUKU_NOT_RUNNING, SHIZUKU.status(BackendState.UNAVAILABLE, false));
        assertEquals(Status.SHIZUKU_NOT_RUNNING, SHIZUKU.status(BackendState.SERVICE_NOT_RUNNING, false));
        assertEquals(Status.ROOT_DENIED, ROOT.status(BackendState.UNAVAILABLE, false));
        assertEquals(Status.NOT_AVAILABLE, AUTO.status(BackendState.UNAVAILABLE, false));
        assertEquals(Status.CHECKING, AUTO.status(BackendState.INITIALIZING, false));
    }
}
