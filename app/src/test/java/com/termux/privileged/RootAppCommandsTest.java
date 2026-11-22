package com.termux.privileged;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootAppCommandsTest {

    private static final String[] BAD = {
        "a.b;reboot", "a.b' x", "", null, "nodots", "a.b c", "a.b$(id)", "1a.b", "a.b\nreboot",
        "a.b`id`", "a..b", "a.b/../c", "a.b&&x", "a.b|x", "a.b*"
    };

    @Test
    public void validationAcceptsPlainPackageNames() {
        assertTrue(RootAppCommands.isValidPackage("com.example.app"));
        assertTrue(RootAppCommands.isValidPackage("org.a_b.C1"));
    }

    @Test
    public void validationRejectsInjectionAndMalformedNames() {
        for (String bad : BAD) assertFalse(String.valueOf(bad), RootAppCommands.isValidPackage(bad));
    }

    @Test
    public void everyPackageBuilderReturnsNullForInvalidInput() {
        for (String bad : BAD) {
            assertNull(RootAppCommands.forceStop(bad));
            assertNull(RootAppCommands.freeze(bad));
            assertNull(RootAppCommands.unfreeze(bad));
            assertNull(RootAppCommands.clearCache(bad));
        }
    }

    @Test
    public void buildersQuoteThePackage() {
        assertEquals("am force-stop 'com.x.y'", RootAppCommands.forceStop("com.x.y"));
        assertEquals("pm disable-user --user 0 'com.x.y'", RootAppCommands.freeze("com.x.y"));
        assertEquals("pm enable 'com.x.y'", RootAppCommands.unfreeze("com.x.y"));
    }

    @Test
    public void clearCacheFallsBackToRemovingCacheDirsWithGlobsOutsideTheQuotes() {
        assertEquals("pm clear --cache-only 'com.x.y' 2>/dev/null"
                + " || rm -rf /data/data/'com.x.y'/cache/* /data/data/'com.x.y'/code_cache/*",
            RootAppCommands.clearCache("com.x.y"));
    }

    @Test
    public void killAndAliveNeedARealPidAndAKnownSignal() {
        assertEquals("kill -TERM 1234", RootAppCommands.killPid(1234, "TERM"));
        assertEquals("kill -KILL 2", RootAppCommands.killPid(2, "KILL"));
        assertEquals("kill -0 1234", RootAppCommands.isAlive(1234));
        assertNull(RootAppCommands.killPid(1, "TERM"));
        assertNull(RootAppCommands.killPid(0, "KILL"));
        assertNull(RootAppCommands.killPid(-5, "KILL"));
        assertNull(RootAppCommands.killPid(1234, "HUP"));
        assertNull(RootAppCommands.killPid(1234, "TERM; reboot"));
        assertNull(RootAppCommands.isAlive(1));
    }
}
