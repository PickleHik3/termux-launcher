package com.termux.privileged;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RootShellSessionTest {

    /** What the fake shell does for one command line. */
    private static final class Reply {
        String stdout = "";
        String stderr = "";
        int rc = 0;
        boolean hang;
        boolean die;
        boolean byteAtATime;

        Reply out(String s) { stdout = s; return this; }
        Reply err(String s) { stderr = s; return this; }
        Reply rc(int r) { rc = r; return this; }
        Reply hang() { hang = true; return this; }
        Reply die() { die = true; return this; }
        Reply slow() { byteAtATime = true; return this; }
    }

    private interface Script {
        Reply on(String command);
    }

    /** A blocking in-memory pipe. */
    private static final class Pipe {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private byte[] pending = new byte[0];
        private int pos;
        private boolean closed;

        final OutputStream writer = new OutputStream() {
            @Override public void write(int b) throws IOException {
                write(new byte[] {(byte) b}, 0, 1);
            }

            @Override public void write(byte[] b, int off, int len) throws IOException {
                synchronized (Pipe.this) {
                    if (closed) {
                        throw new IOException("closed");
                    }
                    byte[] copy = new byte[pending.length - pos + len];
                    System.arraycopy(pending, pos, copy, 0, pending.length - pos);
                    System.arraycopy(b, off, copy, pending.length - pos, len);
                    pending = copy;
                    pos = 0;
                    Pipe.this.notifyAll();
                }
            }

            @Override public void close() {
                Pipe.this.close();
            }
        };

        final InputStream reader = new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
            }

            @Override public int read(byte[] b, int off, int len) throws IOException {
                synchronized (Pipe.this) {
                    while (pos >= pending.length) {
                        if (closed) {
                            return -1;
                        }
                        try {
                            Pipe.this.wait();
                        } catch (InterruptedException e) {
                            throw new IOException("interrupted");
                        }
                    }
                    int n = Math.min(len, pending.length - pos);
                    System.arraycopy(pending, pos, b, off, n);
                    pos += n;
                    return n;
                }
            }
        };

        synchronized void close() {
            closed = true;
            notifyAll();
        }
    }

    /** A process whose "sh" answers the session's framing lines according to a script. */
    private static final class FakeShell extends Process {
        final Pipe stdin = new Pipe();
        final Pipe stdout = new Pipe();
        final Pipe stderr = new Pipe();
        final CountDownLatch exited = new CountDownLatch(1);
        final List<String> commands = new ArrayList<>();
        volatile boolean destroyed;
        volatile boolean sawExitLine;
        private final Thread thread;

        FakeShell(final Script script) {
            thread = new Thread(() -> {
                try {
                    run(script);
                } finally {
                    stdout.close();
                    stderr.close();
                    exited.countDown();
                }
            }, "fake-shell");
            thread.setDaemon(true);
            thread.start();
        }

        private void run(Script script) {
            Pattern p = Pattern.compile("^\\( (.*) \\) </dev/null; __tl_rc=\\$\\?; printf '\\\\n(\\w+):%d\\\\n'");
            StringBuilder line = new StringBuilder();
            int c;
            try {
                while ((c = stdin.reader.read()) >= 0) {
                    if (c != '\n') {
                        line.append((char) c);
                        continue;
                    }
                    String l = line.toString();
                    line.setLength(0);
                    if (l.equals("exit")) {
                        sawExitLine = true;
                        return;
                    }
                    Matcher m = p.matcher(l);
                    assertTrue("unexpected framing: " + l, m.find());
                    String cmd = m.group(1);
                    String s = m.group(2);
                    synchronized (commands) {
                        commands.add(cmd);
                    }
                    Reply r = script.on(cmd);
                    if (r.die) {
                        return;
                    }
                    if (r.hang) {
                        new CountDownLatch(1).await();
                    }
                    byte[] o = (r.stdout + "\n" + s + ":" + r.rc + "\n").getBytes(StandardCharsets.UTF_8);
                    byte[] e = (r.stderr + "\n" + s + "\n").getBytes(StandardCharsets.UTF_8);
                    if (r.byteAtATime) {
                        for (byte b : o) { stdout.writer.write(b); }
                        for (byte b : e) { stderr.writer.write(b); }
                    } else {
                        stdout.writer.write(o, 0, o.length);
                        stderr.writer.write(e, 0, e.length);
                    }
                }
            } catch (IOException | InterruptedException ignored) {
                // destroyed
            }
        }

        @Override public OutputStream getOutputStream() { return stdin.writer; }
        @Override public InputStream getInputStream() { return stdout.reader; }
        @Override public InputStream getErrorStream() { return stderr.reader; }

        @Override public int waitFor() throws InterruptedException {
            exited.await();
            return 0;
        }

        @Override public boolean waitFor(long t, TimeUnit u) throws InterruptedException {
            return exited.await(t, u);
        }

        @Override public int exitValue() {
            if (exited.getCount() > 0) {
                throw new IllegalThreadStateException();
            }
            return 0;
        }

        @Override public void destroy() {
            destroyed = true;
            thread.interrupt();
            stdin.close();
            stdout.close();
            stderr.close();
        }

        @Override public Process destroyForcibly() {
            destroy();
            return this;
        }
    }

    private static final class FakeScheduler implements RootShellSession.Scheduler {
        Runnable task;
        long delayMs = -1;
        boolean cancelled;
        int scheduled;

        @Override public RootShellSession.Cancellable schedule(Runnable t, long d) {
            task = t;
            delayMs = d;
            cancelled = false;
            scheduled++;
            return () -> cancelled = true;
        }

        void fire() {
            Runnable t = task;
            if (t != null && !cancelled) {
                t.run();
            }
        }
    }

    private final List<FakeShell> started = new ArrayList<>();
    private final FakeScheduler scheduler = new FakeScheduler();
    private RootShellSession session;

    private RootShellSession session(Script script) {
        return session(script, RootShellSession.MAX_OUTPUT_CHARS);
    }

    private RootShellSession session(final Script script, int cap) {
        session = new RootShellSession(() -> {
            FakeShell shell = new FakeShell(script);
            synchronized (started) {
                started.add(shell);
            }
            return shell;
        }, System::currentTimeMillis, scheduler, cap);
        return session;
    }

    @After
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    public void startsLazilyAndReturnsOutput() {
        RootShellSession s = session(c -> new Reply().out("hello"));
        assertEquals(0, started.size());
        RootShellSession.Result r = s.run("echo hello", 5_000);
        assertNull(r.failure);
        assertEquals(0, r.exitCode);
        assertEquals("hello", r.stdout);
        assertEquals("", r.stderr);
        assertEquals(1, started.size());
    }

    @Test
    public void reusesTheSameProcessAcrossCommands() {
        RootShellSession s = session(c -> new Reply().out(c));
        assertEquals("a", s.run("a", 5_000).stdout);
        assertEquals("b", s.run("b", 5_000).stdout);
        assertEquals(1, started.size());
    }

    @Test
    public void outputKeepsItsOwnTrailingNewlineAndEmptyOutputIsEmpty() {
        RootShellSession s = session(c -> new Reply().out(c.equals("nl") ? "abc\n" : c.equals("none") ? "abc" : ""));
        assertEquals("abc\n", s.run("nl", 5_000).stdout);
        assertEquals("abc", s.run("none", 5_000).stdout);
        assertEquals("", s.run("empty", 5_000).stdout);
    }

    @Test
    public void nonZeroExitCodeAndStderrAreReturned() {
        RootShellSession s = session(c -> new Reply().out("partial").err("boom").rc(3));
        RootShellSession.Result r = s.run("false", 5_000);
        assertNull(r.failure);
        assertEquals(3, r.exitCode);
        assertEquals("partial", r.stdout);
        assertEquals("boom", r.stderr);
    }

    @Test
    public void sentinelDeliveredOneByteAtATimeIsStillParsed() {
        RootShellSession s = session(c -> new Reply().out("x\ny").err("e").rc(7).slow());
        RootShellSession.Result r = s.run("slow", 5_000);
        assertNull(r.failure);
        assertEquals(7, r.exitCode);
        assertEquals("x\ny", r.stdout);
        assertEquals("e", r.stderr);
    }

    @Test
    public void otherSentinelLookingTextInOutputIsKept() {
        String fake = "line\n0123456789abcdef0123456789abcdef:0\nmore";
        RootShellSession s = session(c -> new Reply().out(fake).err(fake));
        RootShellSession.Result r = s.run("x", 5_000);
        assertEquals(fake, r.stdout);
        assertEquals(fake, r.stderr);
        assertEquals(0, r.exitCode);
    }

    @Test
    public void timeoutKillsTheProcessAndTheNextCallStartsANewOne() {
        RootShellSession s = session(c -> c.equals("hang") ? new Reply().hang() : new Reply().out("ok"));
        RootShellSession.Result r = s.run("hang", 200);
        assertEquals(RootShellSession.Failure.TIMEOUT, r.failure);
        assertTrue(started.get(0).destroyed);
        assertEquals("ok", s.run("next", 5_000).stdout);
        assertEquals(2, started.size());
    }

    @Test
    public void deathMidCommandReturnsDiedThenRestarts() {
        RootShellSession s = session(c -> c.equals("die") ? new Reply().die() : new Reply().out("ok"));
        RootShellSession.Result r = s.run("die", 5_000);
        assertEquals(RootShellSession.Failure.DIED, r.failure);
        assertEquals("ok", s.run("again", 5_000).stdout);
        assertEquals(2, started.size());
    }

    @Test
    public void startFailureIsReported() {
        session = new RootShellSession(() -> { throw new IOException("no su"); },
            System::currentTimeMillis, scheduler);
        RootShellSession.Result r = session.run("x", 1_000);
        assertEquals(RootShellSession.Failure.START_FAILED, r.failure);
        assertEquals("no su", r.reason);
    }

    @Test
    public void concurrentCallersEachGetTheirOwnOutputInFull() throws Exception {
        final String filler = new String(new char[20_000]).replace('\0', 'z');
        RootShellSession s = session(c -> new Reply().out(c + filler));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                final String cmd = "cmd" + i;
                futures.add(pool.submit(() -> {
                    RootShellSession.Result r = session.run(cmd, 10_000);
                    assertNull(r.failure);
                    assertEquals(cmd + filler, r.stdout);
                    return r.stdout;
                }));
            }
            Set<String> seen = new HashSet<>();
            for (Future<String> f : futures) {
                seen.add(f.get(30, TimeUnit.SECONDS).substring(0, 5));
            }
            assertTrue(seen.size() >= 10);
            assertEquals(1, started.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void idleStopFiresAfterSixtySecondsAndNextCallStartsANewProcess() {
        RootShellSession s = session(c -> new Reply().out("ok"));
        s.run("a", 5_000);
        assertEquals(RootShellSession.IDLE_STOP_MS, scheduler.delayMs);
        assertEquals(60_000L, scheduler.delayMs);
        scheduler.fire();
        FakeShell first = started.get(0);
        assertTrue(first.sawExitLine);
        assertEquals(0, first.exited.getCount());
        s.run("b", 5_000);
        assertEquals(2, started.size());
    }

    @Test
    public void newCommandCancelsThePendingIdleStop() {
        RootShellSession s = session(c -> new Reply().out("ok"));
        s.run("a", 5_000);
        s.run("b", 5_000);
        assertEquals(2, scheduler.scheduled);
        scheduler.fire();
        assertEquals(1, started.size());
        assertEquals(0, started.get(0).exited.getCount());
    }

    @Test
    public void closeStopsAtOnce() {
        RootShellSession s = session(c -> new Reply().out("ok"));
        s.run("a", 5_000);
        s.close();
        assertTrue(scheduler.cancelled);
        assertEquals(0, started.get(0).exited.getCount());
        assertTrue(started.get(0).sawExitLine);
    }

    @Test
    public void outputOverTheCapFailsTheCommandAndRestarts() {
        final String big = new String(new char[5_000]).replace('\0', 'q');
        RootShellSession s = session(c -> new Reply().out(c.equals("big") ? big : "ok"), 1_000);
        RootShellSession.Result r = s.run("big", 5_000);
        assertEquals(RootShellSession.Failure.OUTPUT_TOO_LARGE, r.failure);
        assertTrue(started.get(0).destroyed);
        assertEquals("ok", s.run("small", 5_000).stdout);
        assertEquals(2, started.size());
        assertNotNull(r.reason);
        assertFalse(r.reason.isEmpty());
    }
}
