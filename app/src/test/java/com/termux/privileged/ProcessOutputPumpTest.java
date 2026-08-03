package com.termux.privileged;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Plain JUnit: the pump has no Android dependencies, which is the point — the deadlock it fixes is
 * reproducible on a host JVM rather than only on a process-heavy phone. A full ShellBackend test
 * would need a real ProcessBuilder and android.util.Log for no extra coverage of the actual bug.
 */
public class ProcessOutputPumpTest {

    @Test
    public void aQuarterMegabyteRoundTripsThroughAPipeIntact() throws Exception {
        // The regression test for the deadlock class: 256 KiB is several times the ~64 KiB pipe
        // buffer, so a writer whose reader only starts after the fact would block forever.
        StringBuilder expected = new StringBuilder();
        for (int i = 0; expected.length() < 256 * 1024; i++) {
            if (i > 0) expected.append('\n');
            expected.append("line ").append(i).append(" of numbered filler text");
        }
        String payload = expected.toString();

        PipedInputStream in = new PipedInputStream(4096);
        PipedOutputStream out = new PipedOutputStream(in);
        ProcessOutputPump pump = ProcessOutputPump.start("test", in);
        Thread writer = new Thread(() -> {
            try {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.close();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
        writer.start();
        String drained = pump.await(30_000L);
        writer.join(30_000L);

        assertEquals(payload.length(), drained.length());
        assertEquals(payload, drained);
        assertEquals(null, pump.failure());
    }

    @Test
    public void lineSemanticsAreUnchanged() throws Exception {
        // parsePrivileged splits on \n and matches its section markers with equals, so CRLF has to
        // collapse and a missing trailing newline must not add one.
        assertEquals("a\nb\nc", drain("a\r\nb\nc"));
        assertEquals("a\nb", drain("a\nb\n"));
        assertEquals("", drain(""));
        assertEquals("only", drain("only"));
        // A blank line survives as a blank line rather than being swallowed.
        assertEquals("a\n\nb", drain("a\n\nb\n"));
    }

    @Test
    public void awaitOnANeverClosedStreamReturnsInsteadOfHanging() throws Exception {
        PipedInputStream in = new PipedInputStream(4096);
        PipedOutputStream out = new PipedOutputStream(in);
        out.write("partial\n".getBytes(StandardCharsets.UTF_8));
        out.flush();

        ProcessOutputPump pump = ProcessOutputPump.start("test", in);
        long before = System.nanoTime();
        String drained = pump.await(150L);
        long elapsedMs = (System.nanoTime() - before) / 1_000_000L;

        // The caller is released; whatever arrived is fair game, a hang is not.
        assertTrue("waited " + elapsedMs + "ms", elapsedMs < 5_000L);
        assertTrue("drained: " + drained, drained.isEmpty() || drained.startsWith("partial"));
        out.close();
    }

    private static String drain(String text) throws IOException {
        return ProcessOutputPump.drain(
            new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
