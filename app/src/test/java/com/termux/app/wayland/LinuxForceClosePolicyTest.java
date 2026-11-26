package com.termux.app.wayland;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxForceClosePolicyTest {

    private static final int OWN = 100;
    private static final int HOST = 200;

    @Test
    public void aWindowThatStayedWithNoDialogOfItsOwnIsOffered() {
        assertTrue(LinuxForceClosePolicy.shouldOffer(true, false, 4242, OWN, HOST));
    }

    @Test
    public void aWindowThatWentIsNotOffered() {
        assertFalse(LinuxForceClosePolicy.shouldOffer(false, false, 4242, OWN, HOST));
    }

    @Test
    public void anAppAskingTheUserItselfIsNotOffered() {
        assertFalse(LinuxForceClosePolicy.shouldOffer(true, true, 4242, OWN, HOST));
    }

    @Test
    public void noProcessToEndIsNotOffered() {
        assertFalse(LinuxForceClosePolicy.shouldOffer(true, false, 0, OWN, HOST));
        assertFalse(LinuxForceClosePolicy.shouldOffer(true, false, 1, OWN, HOST));
    }

    @Test
    public void theLauncherAndTheCompositorAreNeverEnded() {
        assertFalse(LinuxForceClosePolicy.canKill(OWN, OWN, HOST));
        assertFalse(LinuxForceClosePolicy.canKill(HOST, OWN, HOST));
        assertTrue(LinuxForceClosePolicy.canKill(300, OWN, HOST));
    }
}
