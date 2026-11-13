package com.termux.app.processlimit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.processlimit.ProcessLimitPolicy.Action;
import com.termux.app.processlimit.ProcessLimitPolicy.Card;
import com.termux.app.processlimit.ProcessLimitPolicy.Limit;
import com.termux.app.processlimit.ProcessLimitPolicy.Phase;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ProcessLimitPolicyTest {

    private static final int S = 31;
    private static final int R = 30;

    @Test public void aKillNobodySentRaisesOnlyFromAndroid12WhileTheLimitIsNotKnownOff() {
        assertTrue(ProcessLimitPolicy.raises(S, true, false, Limit.UNKNOWN));
        assertTrue(ProcessLimitPolicy.raises(S, true, false, Limit.ON));
        assertTrue(ProcessLimitPolicy.raises(34, true, false, Limit.ON));
        assertFalse(ProcessLimitPolicy.raises(S, true, false, Limit.OFF));
        assertFalse(ProcessLimitPolicy.raises(R, true, false, Limit.ON));
        assertFalse("the launcher's own stop", ProcessLimitPolicy.raises(S, true, true, Limit.ON));
        assertFalse("another signal", ProcessLimitPolicy.raises(S, false, false, Limit.ON));
    }

    @Test public void onlySigkillCountsAsAKill() {
        assertTrue(ProcessLimitPolicy.isKillExit(137));
        assertFalse(ProcessLimitPolicy.isKillExit(143));
        assertFalse(ProcessLimitPolicy.isKillExit(0));
        assertFalse(ProcessLimitPolicy.isKillExit(9));
        assertTrue(ProcessLimitPolicy.isKillSignal(9));
        assertFalse(ProcessLimitPolicy.isKillSignal(15));
    }

    @Test public void theMonitorSettingIsOffOnlyWhenItReadsFalse() {
        assertEquals(Limit.OFF, ProcessLimitPolicy.fromMonitorSetting("false"));
        assertEquals(Limit.OFF, ProcessLimitPolicy.fromMonitorSetting(" FALSE\n"));
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.fromMonitorSetting("true"));
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.fromMonitorSetting(null));
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.fromMonitorSetting("null"));
    }

    @Test public void theReadBackSaysOffForALargeLimitOrAMonitorThatIsOff() {
        assertEquals(Limit.OFF, ProcessLimitPolicy.fromReadBack("max=2147483647\nmonitor=null\n"));
        assertEquals(Limit.OFF, ProcessLimitPolicy.fromReadBack("max=null\nmonitor=false\n"));
        assertEquals(Limit.OFF, ProcessLimitPolicy.fromReadBack(
            "max=" + ProcessLimitPolicy.OFF_THRESHOLD + "\nmonitor=true"));
    }

    @Test public void theDeveloperOptionsSwitchCountsUnlessTheGlobalSettingSaysOtherwise() {
        assertEquals(Limit.OFF, ProcessLimitPolicy.fromReadBack("max=null\nmonitor=null\noverride=false\n"));
        assertEquals(Limit.ON, ProcessLimitPolicy.fromReadBack("max=null\nmonitor=true\noverride=false\n"));
        assertEquals("false", ProcessLimitPolicy.effectiveMonitor(null, "false"));
        assertEquals("false", ProcessLimitPolicy.effectiveMonitor("null", "false"));
        assertEquals("false", ProcessLimitPolicy.effectiveMonitor(" ", "false"));
        assertEquals("true", ProcessLimitPolicy.effectiveMonitor("true", "false"));
    }

    @Test public void theReadBackSaysOnForAndroidsDefaultOrASmallLimit() {
        assertEquals(Limit.ON, ProcessLimitPolicy.fromReadBack("max=null\nmonitor=null\n"));
        assertEquals(Limit.ON, ProcessLimitPolicy.fromReadBack("max=64\nmonitor=true\n"));
        assertEquals(Limit.ON, ProcessLimitPolicy.fromReadBack("noise\r\n  max=32  \r\nmonitor=\n"));
    }

    @Test public void aReadBackWithoutItsLinesSaysNothing() {
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.fromReadBack(null));
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.fromReadBack(""));
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.fromReadBack("Shizuku permission required"));
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.fromReadBack("Error: binder died"));
    }

    @Test public void offWinsOverOnAndOnOverUnknown() {
        assertEquals(Limit.OFF, ProcessLimitPolicy.combine(Limit.OFF, Limit.ON));
        assertEquals(Limit.OFF, ProcessLimitPolicy.combine(Limit.UNKNOWN, Limit.OFF));
        assertEquals(Limit.ON, ProcessLimitPolicy.combine(Limit.UNKNOWN, Limit.ON));
        assertEquals(Limit.UNKNOWN, ProcessLimitPolicy.combine(Limit.UNKNOWN, Limit.UNKNOWN));
    }

    @Test public void theNoticeStandsUntilTheLimitReadsOffOrItIsDismissed() {
        assertEquals(Card.NOTICE, ProcessLimitPolicy.card(S, true, Limit.ON, Phase.IDLE));
        assertEquals(Card.NOTICE, ProcessLimitPolicy.card(S, true, Limit.UNKNOWN, Phase.IDLE));
        assertEquals(Card.HIDDEN, ProcessLimitPolicy.card(S, true, Limit.OFF, Phase.IDLE));
        assertEquals(Card.HIDDEN, ProcessLimitPolicy.card(S, false, Limit.ON, Phase.IDLE));
        assertEquals(Card.HIDDEN, ProcessLimitPolicy.card(R, true, Limit.ON, Phase.IDLE));
        assertFalse(ProcessLimitPolicy.stillPending(true, Limit.OFF));
        assertTrue(ProcessLimitPolicy.stillPending(true, Limit.UNKNOWN));
    }

    @Test public void aFixInProgressOrJustFinishedShowsWhateverTheNotice() {
        assertEquals(Card.WORKING, ProcessLimitPolicy.card(S, false, Limit.UNKNOWN, Phase.WORKING));
        assertEquals(Card.FIXED, ProcessLimitPolicy.card(S, false, Limit.OFF, Phase.FIXED));
        assertEquals(Card.FAILED, ProcessLimitPolicy.card(S, true, Limit.ON, Phase.FAILED));
    }

    @Test public void theButtonsOfferFixOnlyWithThePrivilegedBackend() {
        assertEquals(Arrays.asList(Action.DISMISS, Action.FIX),
            ProcessLimitPolicy.actions(Card.NOTICE, true));
        assertEquals(Arrays.asList(Action.DISMISS, Action.DIAGNOSTICS, Action.HOW_TO_FIX),
            ProcessLimitPolicy.actions(Card.NOTICE, false));
        assertEquals(Arrays.asList(Action.UNDO, Action.DONE),
            ProcessLimitPolicy.actions(Card.FIXED, true));
        assertEquals(Arrays.asList(Action.DISMISS, Action.HOW_TO_FIX),
            ProcessLimitPolicy.actions(Card.FAILED, true));
        assertEquals(Collections.emptyList(), ProcessLimitPolicy.actions(Card.WORKING, true));
        assertEquals(Collections.emptyList(), ProcessLimitPolicy.actions(Card.HIDDEN, false));
    }

    @Test public void diagnosticsOffersRestoreOnlyWhenOffAndPrivileged() {
        assertTrue(ProcessLimitPolicy.offersRestore(Limit.OFF, true));
        assertFalse(ProcessLimitPolicy.offersRestore(Limit.OFF, false));
        assertFalse(ProcessLimitPolicy.offersRestore(Limit.ON, true));
        assertFalse(ProcessLimitPolicy.offersRestore(Limit.UNKNOWN, true));
    }

    @Test public void theCommandsLiftAndRestoreTheSameThreeThings() {
        assertEquals("device_config set_sync_disabled_for_tests persistent; "
            + "device_config put activity_manager max_phantom_processes 2147483647; "
            + "settings put global settings_enable_monitor_phantom_procs false",
            ProcessLimitPolicy.FIX_COMMAND);
        assertEquals("device_config delete activity_manager max_phantom_processes; "
            + "settings delete global settings_enable_monitor_phantom_procs; "
            + "device_config set_sync_disabled_for_tests none",
            ProcessLimitPolicy.REVERT_COMMAND);
        assertTrue(ProcessLimitPolicy.fixThenRead().startsWith(ProcessLimitPolicy.FIX_COMMAND + "; "));
        assertTrue(ProcessLimitPolicy.fixThenRead().endsWith(ProcessLimitPolicy.READ_COMMAND));
        assertTrue(ProcessLimitPolicy.revertThenRead().startsWith(ProcessLimitPolicy.REVERT_COMMAND + "; "));
        assertTrue(ProcessLimitPolicy.revertThenRead().endsWith(ProcessLimitPolicy.READ_COMMAND));
        assertEquals("echo \"max=$(device_config get activity_manager max_phantom_processes)\"; "
            + "echo \"monitor=$(settings get global settings_enable_monitor_phantom_procs)\"; "
            + "echo \"override=$(getprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs)\"",
            ProcessLimitPolicy.READ_COMMAND);
    }
}
