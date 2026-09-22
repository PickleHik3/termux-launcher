package com.termux.terminal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Runs against a hand-made procfs tree, since the JVM's own /proc says nothing about a shell. */
public class ProcSessionTableTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void collectsTheDistinctGroupsOfOneSession() throws IOException {
        // pid (comm) state ppid pgrp session ...
        stat("100", "100 (fish) S 1 100 100 34816 100 4194560 0 0");
        stat("200", "200 (tty-clock) S 100 200 100 34816 200 0 0");      // foreground job, own group
        stat("300", "300 (my (odd) prog) R 200 200 100 34816 200 0 0");   // a child of that job
        stat("310", "310 (sleep) S 100 310 100 34816 200 0 0");           // background job, own group
        stat("400", "400 (fish) S 1 400 400 34817 400 0 0");              // another pane
        stat("410", "410 (daemon) S 1 410 410 0 -1 0 0");                 // setsid'd itself: not ours
        tmp.newFolder("self");                                            // non-numeric entries are skipped
        tmp.newFolder("500");                                             // numeric entry without a stat file

        int[] groups = new ProcSessionTable(tmp.getRoot()).processGroupsInSession(100);

        Arrays.sort(groups);
        assertArrayEquals(new int[]{100, 200, 310}, groups);
    }

    @Test
    public void reportsAnEmptySessionAsEmptyAndAMissingTreeAsNull() throws IOException {
        stat("400", "400 (fish) S 1 400 400 34817 400 0 0");

        assertEquals(0, new ProcSessionTable(tmp.getRoot()).processGroupsInSession(100).length);
        assertNull(new ProcSessionTable(new File(tmp.getRoot(), "no-such-proc")).processGroupsInSession(100));
    }

    @Test
    public void ignoresLinesItCannotParse() throws IOException {
        assertNull(ProcSessionTable.readPgrpAndSid(stat("1", "garbage")));
        assertNull(ProcSessionTable.readPgrpAndSid(stat("2", "2 (short) S 1")));
        assertNull(ProcSessionTable.readPgrpAndSid(stat("3", "3 (x) S 1 a b")));
        assertArrayEquals(new int[]{200, 100}, ProcSessionTable.readPgrpAndSid(stat("4", "4 (x) S 1 200 100")));
    }

    private File stat(String pid, String line) throws IOException {
        File dir = new File(tmp.getRoot(), pid);
        dir.mkdirs();
        File stat = new File(dir, "stat");
        try (FileWriter w = new FileWriter(stat)) { w.write(line + "\n"); }
        return stat;
    }
}
