package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.statusbar.StatusStatNeedsPolicy.Access;
import com.termux.app.statusbar.StatusStatNeedsPolicy.Hint;
import com.termux.app.statusbar.StatusStatNeedsPolicy.Verdict;
import com.termux.privileged.PrivilegedChoice.Status;

import org.junit.Test;

public class StatusStatNeedsPolicyTest {

    private static Verdict cpu(boolean on, Access a, Status s, boolean shizuku, boolean su) {
        return StatusStatNeedsPolicy.cpu(on, a, s, shizuku, su);
    }

    @Test public void aDirectReadShowsItsValueWithNoBackend() {
        assertSame(Verdict.VALUE, cpu(true, Access.DIRECT, Status.OFF, false, false));
        assertSame(Verdict.VALUE, cpu(true, Access.DIRECT, Status.NOT_AVAILABLE, true, true));
    }

    @Test public void aSwitchedOffStatIsHidden() {
        assertFalse(cpu(false, Access.NEEDS_BACKEND, Status.SHIZUKU_NOT_RUNNING, true, false).shown);
    }

    @Test public void aRefusedReadWithABackendUpShowsTheValue() {
        assertSame(Verdict.VALUE, cpu(true, Access.NEEDS_BACKEND, Status.USING_SHIZUKU, true, false));
        assertSame(Verdict.VALUE, cpu(true, Access.NEEDS_BACKEND, Status.USING_ROOT, false, true));
    }

    @Test public void aRefusedReadWithoutOneStandsGreyedWithWhatIsMissing() {
        assertEquals(Hint.START_SHIZUKU,
            cpu(true, Access.NEEDS_BACKEND, Status.SHIZUKU_NOT_RUNNING, true, false).needs);
        assertEquals(Hint.NEEDS_SHIZUKU,
            cpu(true, Access.NEEDS_BACKEND, Status.SHIZUKU_NOT_RUNNING, false, false).needs);
        assertEquals(Hint.ALLOW_SHIZUKU,
            cpu(true, Access.NEEDS_BACKEND, Status.NEEDS_PERMISSION, true, false).needs);
        assertEquals(Hint.ALLOW_ROOT,
            cpu(true, Access.NEEDS_BACKEND, Status.ROOT_DENIED, false, true).needs);
        assertEquals(Hint.NEEDS_ROOT,
            cpu(true, Access.NEEDS_BACKEND, Status.ROOT_DENIED, false, false).needs);
        assertEquals(Hint.NEEDS_BOTH,
            cpu(true, Access.NEEDS_BACKEND, Status.NOT_AVAILABLE, false, false).needs);
        assertEquals(Hint.NEEDS_BOTH,
            cpu(true, Access.NEEDS_BACKEND, Status.OFF, true, true).needs);
    }

    @Test public void automaticWithShizukuInstalledButStoppedAsksToStartIt() {
        assertEquals(Hint.START_SHIZUKU,
            cpu(true, Access.NEEDS_BACKEND, Status.NOT_AVAILABLE, true, true).needs);
        assertEquals(Hint.ALLOW_ROOT,
            cpu(true, Access.NEEDS_BACKEND, Status.NOT_AVAILABLE, false, true).needs);
    }

    @Test public void anUntriedOrCheckingStatHoldsBackRatherThanFlashAHint() {
        assertFalse(cpu(true, Access.UNKNOWN, Status.SHIZUKU_NOT_RUNNING, true, false).shown);
        assertFalse(cpu(true, Access.NEEDS_BACKEND, Status.CHECKING, true, false).shown);
    }

    @Test public void onlyAMissingPermissionIsAskedForByATap() {
        assertTrue(Hint.ALLOW_SHIZUKU.asksPermission());
        assertTrue(Hint.ALLOW_ROOT.asksPermission());
        assertFalse(Hint.START_SHIZUKU.asksPermission());
        assertFalse(Hint.NEEDS_BOTH.asksPermission());
    }

    @Test public void aGpuWithNoNodesEvenForRootIsHiddenNotGreyed() {
        assertFalse(StatusStatNeedsPolicy.gpu(true, false, Access.ABSENT,
            Status.SHIZUKU_NOT_RUNNING, true, false).shown);
        assertFalse(StatusStatNeedsPolicy.gpu(true, false, Access.ABSENT, Status.OFF, false, false).shown);
    }

    @Test public void aGpuWhoseNodesAreRefusedStandsGreyedUntilABackendReadsIt() {
        assertEquals(Hint.START_SHIZUKU, StatusStatNeedsPolicy.gpu(true, false, Access.NEEDS_BACKEND,
            Status.SHIZUKU_NOT_RUNNING, true, false).needs);
        // With a backend up and still no load, it is a phone with nothing to read: hidden.
        assertFalse(StatusStatNeedsPolicy.gpu(true, false, Access.NEEDS_BACKEND,
            Status.USING_SHIZUKU, true, false).shown);
        assertSame(Verdict.VALUE, StatusStatNeedsPolicy.gpu(true, true, Access.NEEDS_BACKEND,
            Status.USING_SHIZUKU, true, false));
        assertFalse(StatusStatNeedsPolicy.gpu(false, true, Access.DIRECT, Status.OFF, false, false).shown);
    }

    @Test public void aGpuNotYetProbedIsHidden() {
        assertFalse(StatusStatNeedsPolicy.gpu(true, false, Access.UNKNOWN,
            Status.SHIZUKU_NOT_RUNNING, true, false).shown);
        assertNull(StatusStatNeedsPolicy.gpu(true, false, Access.UNKNOWN, Status.OFF, false, false).needs);
    }
}
