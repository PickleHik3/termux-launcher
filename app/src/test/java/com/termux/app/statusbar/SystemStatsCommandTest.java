package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class SystemStatsCommandTest {

    private static HardwareNodes nodes(final boolean readable) {
        return HardwareNodes.discover(new HardwareNodes.Fs() {
            @Override public List<String> list(String dir) { return Collections.emptyList(); }
            @Override public String read(String path) { return readable ? "1" : null; }
            @Override public boolean exists(String path) { return path.endsWith("gpu_busy"); }
        });
    }

    @Test
    public void theBatchIsInTheCommandOnlyWhenWantedAndSomeNodeNeedsPrivilege() {
        HardwareNodes needing = nodes(false);
        HardwareNodes readable = nodes(true);

        assertFalse(SystemStatsController.hardwareFragment(true, needing).isEmpty());
        assertEquals("", SystemStatsController.hardwareFragment(false, needing));
        assertEquals("", SystemStatsController.hardwareFragment(true, readable));
        assertEquals("", SystemStatsController.hardwareFragment(true, null));

        String with = SystemStatsController.buildCommand(true,
            SystemStatsController.hardwareFragment(true, needing));
        String without = SystemStatsController.buildCommand(true,
            SystemStatsController.hardwareFragment(false, needing));
        assertTrue(with.contains("echo @@HW"));
        assertFalse(without.contains("@@HW"));
        assertTrue(with.startsWith(without));   // appended to the existing sections, one command
    }
}
