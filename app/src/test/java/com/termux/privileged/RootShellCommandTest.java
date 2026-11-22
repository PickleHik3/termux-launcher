package com.termux.privileged;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootShellCommandTest {

    @Test
    public void line_exportsTermuxEnvironmentBeforeSuResetsIt() {
        String line = RootShellCommand.line();
        for (String name : new String[]{"PREFIX", "TMPDIR", "LD_PRELOAD", "LANG", "TERM",
            "COLORTERM"}) {
            assertTrue(name, line.contains(name + "='$" + name + "'"));
        }
        assertTrue(line.contains("PATH='$PATH:/system/bin'"));
    }

    @Test
    public void line_movesRootHomeToSuroot() {
        String line = RootShellCommand.line();
        assertTrue(line.startsWith("mkdir -p \"$HOME/.suroot\" && exec su -c \""));
        assertTrue(line.contains("HOME='$HOME/.suroot'"));
        assertEquals(".suroot", RootShellCommand.ROOT_HOME_DIR);
    }

    @Test
    public void line_endsInALoginShell() {
        String line = RootShellCommand.line();
        assertTrue(line.contains("cd '$PWD' 2>/dev/null;"));
        assertTrue(line.endsWith("exec \\\"\\${SHELL:-$PREFIX/bin/sh}\\\" -l\""));
    }
}
