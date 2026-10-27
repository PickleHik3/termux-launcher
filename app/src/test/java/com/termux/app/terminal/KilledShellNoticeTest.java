package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Which finished panes stay with their exit line, and when the notice blames the process limit. */
public class KilledShellNoticeTest {

    @Test
    public void aSignalNobodyHereSent_keepsThePane() {
        // The phantom-process trim and the low-memory killer: SIGKILL, exit status -9.
        assertTrue(KilledShellNotice.keepsPane(-9, false));
        assertTrue(KilledShellNotice.keepsPane(-15, false));
    }

    @Test
    public void aShellTheLauncherFinished_goesAtOnce() {
        // A pane or window closed by hand, Kill process: SIGHUP then SIGKILL, both asked for.
        assertFalse(KilledShellNotice.keepsPane(-9, true));
        assertFalse(KilledShellNotice.keepsPane(-1, true));
    }

    @Test
    public void anExit_goesAsBefore() {
        assertFalse(KilledShellNotice.keepsPane(0, false));
        assertFalse(KilledShellNotice.keepsPane(1, false));
        assertFalse(KilledShellNotice.keepsPane(130, false));
    }

    @Test
    public void onlySigkillOnAndroid12AndLater_pointsAtTheProcessLimit() {
        assertTrue(KilledShellNotice.mayBePhantomLimit(9, 31));
        assertTrue(KilledShellNotice.mayBePhantomLimit(9, 36));
        assertFalse(KilledShellNotice.mayBePhantomLimit(9, 30));
        assertFalse(KilledShellNotice.mayBePhantomLimit(15, 36));
    }
}
