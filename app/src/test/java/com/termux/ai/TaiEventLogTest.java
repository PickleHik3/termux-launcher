package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The AI event log: the line format, the two-file rotation, and reading the tail back. */
public class TaiEventLogTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private TaiEventLog log(long maxBytes) {
        return new TaiEventLog(new File(temp.getRoot(), "tai"), maxBytes);
    }

    @Test
    public void lineCarriesTimeEventModelAndFigures() {
        String line = TaiEventLog.formatLine(0L, TaiEventLog.LOAD_OK, "qwen3-0.6b", "mnn_llm", "gpu", 4096,
            2210L, 512L * 1024L * 1024L, null);
        assertEquals("1970-01-01T00:00:00.000Z load_ok model=qwen3-0.6b backend=mnn_llm accel=gpu ctx=4096 ms=2210 mem=512MB",
            line);
    }

    @Test
    public void acceleratorFallbackLineSaysWhy() {
        String line = TaiEventLog.formatLine(0L, TaiEventLog.ACCEL_FALLBACK, "gemma-4-e4b-it-litert-lm", "litert_lm", "cpu",
            4096, 0L, 0L, "history_failure: Model load cancelled.");
        assertEquals("1970-01-01T00:00:00.000Z accel_fallback model=gemma-4-e4b-it-litert-lm backend=litert_lm accel=cpu "
            + "ctx=4096 reason=\"history_failure: Model load cancelled.\"", line);
    }

    @Test
    public void unknownFiguresAreLeftOut() {
        String line = TaiEventLog.formatLine(0L, TaiEventLog.IDLE_EXIT, null, null, null, 0, 0L, 0L, "baseline only");
        assertEquals("1970-01-01T00:00:00.000Z idle_exit reason=\"baseline only\"", line);
    }

    @Test
    public void reasonIsOneBoundedLine() {
        StringBuilder text = new StringBuilder("first\nsecond \"quoted\"");
        for (int i = 0; i < 400; i++) text.append('x');
        String line = TaiEventLog.formatLine(0L, TaiEventLog.LOAD_FAIL, "m", null, null, 0, 0L, 0L, text.toString());
        assertFalse(line.contains("\n"));
        String reason = line.substring(line.indexOf("reason=\"") + 8, line.length() - 1);
        assertFalse(reason.contains("\""));
        assertTrue(line.length() < 300);
        assertTrue(line.endsWith("...\""));
    }

    @Test
    public void appendRotatesPastTheLimitKeepingTwoFiles() throws Exception {
        TaiEventLog log = log(200L);
        for (int i = 0; i < 12; i++) log.append("line-" + i + " padding-padding-padding");
        File dir = new File(temp.getRoot(), "tai");
        File live = new File(dir, TaiEventLog.FILE_NAME);
        File rotated = new File(dir, TaiEventLog.ROTATED_NAME);
        assertTrue(live.isFile());
        assertTrue(rotated.isFile());
        // A file rotates on the first append after it passes the limit, so it never exceeds it by more than one line.
        assertTrue(live.length() <= 200L + 64L);
        assertTrue(rotated.length() > 0L);
        String all = new String(Files.readAllBytes(live.toPath()), StandardCharsets.UTF_8);
        assertTrue(all.contains("line-11"));
        assertFalse(all.contains("line-0 "));
    }

    @Test
    public void tailReadsAcrossBothFilesOldestFirst() {
        TaiEventLog log = log(100L);
        for (int i = 0; i < 8; i++) log.append("entry-" + i + " padding-padding-padding");
        List<String> tail = log.tail(3);
        assertEquals(3, tail.size());
        assertTrue(tail.get(0).startsWith("entry-5"));
        assertTrue(tail.get(2).startsWith("entry-7"));
    }

    @Test
    public void clearRemovesBothFiles() {
        TaiEventLog log = log(100L);
        for (int i = 0; i < 8; i++) log.append("entry-" + i + " padding-padding-padding");
        assertTrue(log.clear());
        assertTrue(log.tail(10).isEmpty());
        assertFalse(log.clear());
    }
}
