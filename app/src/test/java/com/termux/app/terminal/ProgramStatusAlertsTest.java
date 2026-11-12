package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.ProgramStatus;
import com.termux.terminal.ProgramStatusStore;

import org.junit.Test;

public class ProgramStatusAlertsTest {

    private static ProgramStatus record(int state, String id) {
        ProgramStatusStore store = new ProgramStatusStore();
        store.apply(state, 0, -1, id, "", "", "");
        return store.get(id);
    }

    @Test
    public void onlyBlockedDoneAndErrorAlert() {
        ProgramStatusAlerts alerts = new ProgramStatusAlerts();
        Object s = new Object();
        assertFalse(alerts.shouldAlert(s, record(0, "a"), 0));
        assertFalse(alerts.shouldAlert(s, record(1, "a"), 0));
        assertTrue(alerts.shouldAlert(s, record(3, "a"), 0));
        assertTrue(alerts.shouldAlert(s, record(2, "b"), 10_000));
        assertTrue(alerts.shouldAlert(s, record(4, "c"), 20_000));
    }

    @Test
    public void blockedWorkingBlockedAlertsTwiceAcrossTurns() {
        ProgramStatusAlerts alerts = new ProgramStatusAlerts();
        ProgramStatusStore store = new ProgramStatusStore();
        Object s = new Object();
        store.apply(3, 0, -1, "a", "", "", "");
        alerts.retainCurrent(s, store);
        assertTrue(alerts.shouldAlert(s, store.get("a"), 0));
        // Next turn: working; the engine raises nothing, but the change re-arms the id.
        store.apply(1, 0, -1, "a", "", "", "");
        alerts.retainCurrent(s, store);
        store.apply(3, 0, -1, "a", "", "", "");
        alerts.retainCurrent(s, store);
        assertTrue(alerts.shouldAlert(s, store.get("a"), 10_000));
        // Blocked again with nothing in between stays quiet.
        alerts.retainCurrent(s, store);
        assertFalse(alerts.shouldAlert(s, store.get("a"), 20_000));
    }

    @Test
    public void sameStateDoesNotRepeatUntilItChanges() {
        ProgramStatusAlerts alerts = new ProgramStatusAlerts();
        Object s = new Object();
        assertTrue(alerts.shouldAlert(s, record(3, "a"), 0));
        assertFalse(alerts.shouldAlert(s, record(3, "a"), 10_000));
        assertTrue(alerts.shouldAlert(s, record(2, "a"), 20_000));
        assertTrue(alerts.shouldAlert(s, record(3, "a"), 30_000));
        // A different id has its own memory.
        assertTrue(alerts.shouldAlert(s, record(3, "b"), 40_000));
    }

    @Test
    public void oneAlertPerSessionPerTwoSeconds() {
        ProgramStatusAlerts alerts = new ProgramStatusAlerts();
        Object s = new Object(), t = new Object();
        assertTrue(alerts.shouldAlert(s, record(3, "a"), 1000));
        assertFalse(alerts.shouldAlert(s, record(3, "b"), 2999));
        assertTrue(alerts.shouldAlert(t, record(3, "a"), 2000));
        // The dropped one was not queued and is not remembered: it may alert once the window is over.
        assertTrue(alerts.shouldAlert(s, record(3, "b"), 3000));
    }

    @Test
    public void forgetStartsOver() {
        ProgramStatusAlerts alerts = new ProgramStatusAlerts();
        Object s = new Object();
        assertTrue(alerts.shouldAlert(s, record(3, "a"), 0));
        alerts.forget(s);
        assertTrue(alerts.shouldAlert(s, record(3, "a"), 100));
    }
}
