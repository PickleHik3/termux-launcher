package com.termux.privileged;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Drains one of a subprocess's output streams on its own thread.
 *
 * <p>Reading stdout only after {@code waitFor} returns deadlocks as soon as the child writes more
 * than the pipe buffer holds — roughly 64 KiB — because the child blocks on a full pipe while the
 * parent waits for the child. The stats command sits close enough to that limit that it hangs on
 * process-heavy devices and not on others, which is exactly the shape of the reported "the process
 * list sometimes disappears".
 *
 * <p>No Android imports, so the deadlock is a plain-JUnit regression test rather than a device
 * anecdote.
 */
public final class ProcessOutputPump {

    private final Thread mThread;
    private final StringBuilder mOutput = new StringBuilder();
    private volatile IOException mFailure;

    private ProcessOutputPump(String name, InputStream in) {
        mThread = new Thread(() -> {
            try {
                String text = drain(in);
                synchronized (mOutput) {
                    mOutput.append(text);
                }
            } catch (IOException e) {
                mFailure = e;
            }
        }, name);
        mThread.setDaemon(true);
    }

    /**
     * Reads {@code in} to EOF as lines joined with {@code \n}.
     *
     * <p>Exactly the old semantics, deliberately: no trailing newline and CRLF collapsed, because
     * {@code SystemStatsController.parsePrivileged} splits this on {@code \n} and matches section
     * markers with {@code equals}.
     */
    public static String drain(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (output.length() > 0) output.append('\n');
            output.append(line);
        }
        return output.toString();
    }

    /** Starts draining immediately, before anything waits on the process. */
    public static ProcessOutputPump start(String name, InputStream in) {
        ProcessOutputPump pump = new ProcessOutputPump(name, in);
        pump.mThread.start();
        return pump;
    }

    /**
     * Whatever has been read once the pump finishes, or once {@code timeoutMs} elapses — a stream
     * that is never closed yields a partial read rather than a hung caller.
     */
    public String await(long timeoutMs) {
        try {
            mThread.join(Math.max(1L, timeoutMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (mOutput) {
            return mOutput.toString();
        }
    }

    /** The read error, if the pump hit one. */
    public IOException failure() {
        return mFailure;
    }
}
