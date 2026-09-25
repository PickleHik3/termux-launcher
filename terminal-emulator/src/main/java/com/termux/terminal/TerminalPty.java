package com.termux.terminal;

/**
 * The pty half of {@link JNI} for callers outside this package that want a subprocess on a
 * pseudo-terminal without a {@link TerminalSession} around it: the launcher's privileged lane
 * spawns its child from a Shizuku user service, which runs as the shell uid in a process that
 * has this APK on its class path, so libtermux is the same code that starts every terminal
 * session. {@link JNI} itself stays package-private.
 */
public final class TerminalPty {

    private TerminalPty() {
    }

    /**
     * Opens a pty of {@code rows} x {@code columns} cells, forks, and execs {@code cmd} on the
     * slave side with {@code cwd}, {@code args} (argv, including argv[0]) and {@code envVars}
     * ("VAR=value"; the child's environment is exactly this list).
     *
     * @return the master descriptor; {@code processId[0]} receives the child's pid.
     * @throws RuntimeException when the pty or the fork fails.
     */
    public static int createSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns) {
        return JNI.createSubprocess(cmd, cwd, args, envVars, processId, rows, columns, 0, 0);
    }

    /** @return the exit status when {@code >= 0}, or the negated signal number when the child was killed. */
    public static int waitFor(int processId) {
        return JNI.waitFor(processId);
    }

    public static void close(int fileDescriptor) {
        JNI.close(fileDescriptor);
    }
}
