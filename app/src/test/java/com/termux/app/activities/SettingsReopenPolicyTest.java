package com.termux.app.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsReopenPolicyTest {

    private static final long CLOSED = 1_000_000L;
    private static final long WINDOW = SettingsReopenPolicy.RETAIN_WINDOW_MS;

    @Test
    public void withinTheWindowKeepsThePage() {
        assertFalse(SettingsReopenPolicy.shouldReset(CLOSED, CLOSED + WINDOW - 1, false));
        assertFalse(SettingsReopenPolicy.shouldReset(CLOSED, CLOSED, false));
    }

    @Test
    public void exactlyAtTheWindowResets() {
        assertTrue(SettingsReopenPolicy.shouldReset(CLOSED, CLOSED + WINDOW, false));
    }

    @Test
    public void pastTheWindowResets() {
        assertTrue(SettingsReopenPolicy.shouldReset(CLOSED, CLOSED + WINDOW + 1, false));
    }

    @Test
    public void noCloseRecordedLeavesThePageAlone() {
        assertFalse(SettingsReopenPolicy.shouldReset(SettingsReopenPolicy.NO_CLOSE, CLOSED, false));
    }

    @Test
    public void aDeepLinkNeverResetsHere() {
        assertFalse(SettingsReopenPolicy.shouldReset(CLOSED, CLOSED + WINDOW * 10, true));
    }

    @Test
    public void windowIsThreeMinutes() {
        assertTrue(WINDOW == 180_000L);
    }
}
