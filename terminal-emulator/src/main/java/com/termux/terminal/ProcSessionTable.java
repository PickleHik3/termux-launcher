package com.termux.terminal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads process groups by session id out of a procfs tree.
 *
 * <p>Under Android's {@code hidepid} mount only this uid's processes are listed, which is exactly
 * the set a pane can have started. An entry that vanishes or cannot be parsed mid-scan is skipped;
 * a tree that cannot be listed at all reports null so the caller can fall back to signalling the
 * leader's group alone.
 */
public final class ProcSessionTable implements ShellTerminator.ProcessTable {

    private final File mRoot;

    public ProcSessionTable() {
        this(new File("/proc"));
    }

    ProcSessionTable(File root) {
        mRoot = root;
    }

    @Override
    public int[] processGroupsInSession(int sid) {
        String[] entries = mRoot.list();
        if (entries == null) return null;
        Set<Integer> groups = new LinkedHashSet<>();
        for (String name : entries) {
            if (name.isEmpty() || name.charAt(0) < '0' || name.charAt(0) > '9') continue;
            int[] ids = readPgrpAndSid(new File(new File(mRoot, name), "stat"));
            if (ids != null && ids[1] == sid) groups.add(ids[0]);
        }
        int[] out = new int[groups.size()];
        int i = 0;
        for (int g : groups) out[i++] = g;
        return out;
    }

    /** {pgrp, session} from a {@code /proc/<pid>/stat} line, or null if it cannot be read or parsed. */
    static int[] readPgrpAndSid(File stat) {
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(stat))) {
            line = reader.readLine();
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (line == null) return null;
        // "pid (comm) S ppid pgrp session ...": comm may itself hold spaces and parentheses, so the
        // fields are counted from the last ')'.
        int close = line.lastIndexOf(')');
        if (close < 0) return null;
        String[] fields = line.substring(close + 1).trim().split(" ");
        if (fields.length < 4) return null;
        try {
            return new int[]{Integer.parseInt(fields[2]), Integer.parseInt(fields[3])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
