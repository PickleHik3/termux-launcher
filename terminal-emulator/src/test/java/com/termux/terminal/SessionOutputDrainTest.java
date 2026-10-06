package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.Test;

/**
 * Shell output reaches the emulator in slices, one per main-thread message, so a burst cannot hold
 * the frame clock. The exit message may overtake the slices still to come; it must take them in
 * before writing its own line, or the end of a command's output is lost.
 */
public class SessionOutputDrainTest {

    private static final int LINES = TerminalSession.PROCESS_OUTPUT_QUEUE_BYTES / 8;

    private static final TerminalSessionClient SILENT_CLIENT = new TerminalSessionClient() {
        @Override public void onTextChanged(TerminalSession changedSession) {}
        @Override public void onTitleChanged(TerminalSession changedSession) {}
        @Override public void onSessionFinished(TerminalSession finishedSession) {}
        @Override public void onCopyTextToClipboard(TerminalSession session, String text) {}
        @Override public void onPasteTextFromClipboard(TerminalSession session) {}
        @Override public void onBell(TerminalSession session) {}
        @Override public void onColorsChanged(TerminalSession session) {}
        @Override public void onTerminalCursorStateChange(boolean state) {}
        @Override public void setTerminalShellPid(TerminalSession session, int pid) {}
        @Override public void logError(String tag, String message) {}
        @Override public void logWarn(String tag, String message) {}
        @Override public void logInfo(String tag, String message) {}
        @Override public void logDebug(String tag, String message) {}
        @Override public void logVerbose(String tag, String message) {}
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) {}
        @Override public void logStackTrace(String tag, Exception e) {}
        @Override public Integer getTerminalCursorStyle() { return null; }
    };

    private static TerminalSession sessionWithEmulator() {
        TerminalSession session = new TerminalSession("/bin/sh", null, new String[0], new String[0], null, SILENT_CLIENT);
        session.mEmulator = new TerminalEmulator(session, false, 10, 4,
            TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS, TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS,
            LINES + 100, null);
        return session;
    }

    /** A full queue of numbered eight-byte lines. */
    private static byte[] burst() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < LINES; i++) text.append(String.format(Locale.ROOT, "L%05d\r\n", i));
        byte[] bytes = text.toString().getBytes(StandardCharsets.US_ASCII);
        assertEquals(TerminalSession.PROCESS_OUTPUT_QUEUE_BYTES, bytes.length);
        return bytes;
    }

    private static String lastLine(int index) {
        return String.format(Locale.ROOT, "L%05d", index);
    }

    @Test
    public void oneMessageTakesOneSliceAndAsksForMore() {
        TerminalSession session = sessionWithEmulator();
        byte[] bytes = burst();
        assertTrue(session.mProcessToTerminalIOQueue.write(bytes, 0, bytes.length));

        assertTrue("A full slice means more may be waiting", session.drainProcessOutputSlice());
        String transcript = session.mEmulator.getScreen().getTranscriptText();
        int linesPerSlice = TerminalSession.DRAIN_SLICE_BYTES / 8;
        assertTrue(transcript.contains(lastLine(linesPerSlice - 1)));
        assertFalse(transcript.contains(lastLine(linesPerSlice)));
    }

    @Test
    public void exitAfterALargeBurstLosesNoBytes() {
        TerminalSession session = sessionWithEmulator();
        byte[] bytes = burst();
        assertTrue(session.mProcessToTerminalIOQueue.write(bytes, 0, bytes.length));

        // The message announcing the burst takes its slice and re-posts the rest, but the exit
        // message is already ahead of that re-post in the queue.
        assertTrue(session.drainProcessOutputSlice());
        session.drainProcessOutputBeforeExit();

        assertEquals("Nothing is left behind for the closed queue to drop",
            0, session.mProcessToTerminalIOQueue.read(new byte[1], false));
        String[] lines = session.mEmulator.getScreen().getTranscriptText().split("\n");
        for (int i = 0; i < LINES; i++) assertEquals(lastLine(i), lines[i]);
    }

    @Test
    public void anEmptyQueueAsksForNothing() {
        TerminalSession session = sessionWithEmulator();
        assertFalse(session.drainProcessOutputSlice());
        session.drainProcessOutputBeforeExit();
    }
}
