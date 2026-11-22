package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * Shell output reaches the emulator in turns, one per main-thread message, each bounded in time and
 * at most one ever pending, so a burst cannot hold the frame clock. The exit message may overtake
 * the turns still to come; it must take them in before writing its own line, or the end of a
 * command's output is lost.
 */
public class SessionOutputDrainTest {

    private static final int LINES = TerminalSession.PROCESS_OUTPUT_QUEUE_BYTES / 8;

    private int mTextChanges;

    private final TerminalSessionClient mClient = new TerminalSessionClient() {
        @Override public void onTextChanged(TerminalSession changedSession) { mTextChanges++; }
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

    private TerminalSession sessionWithEmulator() {
        TerminalSession session = new TerminalSession("/bin/sh", null, new String[0], new String[0], null, mClient);
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

    private static void queue(TerminalSession session, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        assertTrue(session.mProcessToTerminalIOQueue.write(bytes, 0, bytes.length));
    }

    @Test
    public void severalSynchronizedFramesInOneSliceCostOneUpdate() {
        TerminalSession session = sessionWithEmulator();
        queue(session, "\033[?2026ha\033[?2026l\033[?2026hb\033[?2026l\033[?2026hc\033[?2026l");

        session.drainProcessOutputSlice();

        assertEquals(1, mTextChanges);
        assertTrue(session.mEmulator.getScreen().getTranscriptText().contains("abc"));
    }

    @Test
    public void aSliceThatEndsInsideAHoldWaitsForTheHoldToEnd() {
        TerminalSession session = sessionWithEmulator();
        queue(session, "\033[?2026hone\033[?2026l\033[?2026htw");

        session.drainProcessOutputSlice();
        assertEquals("The half-drawn second frame is not shown", 0, mTextChanges);

        queue(session, "o\033[?2026l");
        session.drainProcessOutputSlice();
        assertEquals(1, mTextChanges);
    }

    @Test
    public void aScreenChangeOutsideASliceIsDeliveredAtOnce() {
        TerminalSession session = sessionWithEmulator();
        session.onScreenChanged();
        assertEquals(1, mTextChanges);
    }

    /** A stand-in for the main looper: posts land here and the test thread runs them in order. */
    private final LinkedBlockingQueue<Runnable> mLooper = new LinkedBlockingQueue<>();
    private final AtomicInteger mPosts = new AtomicInteger();
    /** Bytes parsed by each output turn, in order; test thread only. */
    private final List<Integer> mTurnBytes = new ArrayList<>();
    /** Nanoseconds each output turn took on the test thread's real clock. */
    private final List<Long> mTurnNanos = new ArrayList<>();
    private int mTurnsInARow;
    private int mMostTurnsInARow;

    private TerminalSession sessionOnTestLooper() {
        TerminalSession session = sessionWithEmulator();
        session.mNewInputPoster = () -> {
            mPosts.incrementAndGet();
            mLooper.add(() -> {
                long start = System.nanoTime();
                session.handleNewInput();
                mTurnNanos.add(System.nanoTime() - start);
                mTurnBytes.add(session.mLastTurnBytes);
                mMostTurnsInARow = Math.max(mMostTurnsInARow, ++mTurnsInARow);
            });
        };
        return session;
    }

    @Test
    public void aBurstOfTinyWritesPostsOneNotification() {
        // ssh hands a stream over in small segments, and each pty read used to post its own
        // message: thousands a second queued ahead of the frame clock.
        TerminalSession session = sessionOnTestLooper();
        session.mNanoClock = () -> 0L; // no budget runs out: one turn takes the whole burst
        byte[] bytes = burst();
        int writes = 4000;
        int size = 16;
        for (int i = 0; i < writes; i++) {
            assertTrue(session.mProcessToTerminalIOQueue.write(bytes, i * size, size));
            session.onProcessOutputQueued();
        }
        assertEquals("One notification stands for every write before it is handled", 1, mPosts.get());

        while (!mLooper.isEmpty()) mLooper.poll().run();
        String[] lines = session.mEmulator.getScreen().getTranscriptText().split("\n");
        int written = writes * size / 8;
        for (int i = 0; i < written; i++) assertEquals(lastLine(i), lines[i]);
    }

    private static final int FLOOD_BYTES = 1024 * 1024;

    /** A mebibyte of numbered eight-byte lines, 000000 to 01ffff. */
    private static byte[] flood() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < FLOOD_BYTES / 8; i++) text.append(String.format(Locale.ROOT, "%06x\r\n", i));
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Writes a mebibyte from a reader thread in pieces of 1 to 4,096 bytes while this thread plays
     * the main looper, with a frame callback that re-posts itself for as long as output flows, the
     * way the frame clock keeps one callback pending.
     */
    private void runFlood(TerminalSession session) throws Exception {
        byte[] bytes = flood();
        Thread reader = new Thread(() -> {
            Random random = new Random(42);
            int offset = 0;
            while (offset < bytes.length) {
                int size = Math.min(bytes.length - offset, 1 + random.nextInt(4096));
                if (!session.mProcessToTerminalIOQueue.write(bytes, offset, size)) return;
                session.onProcessOutputQueued();
                offset += size;
            }
        });
        Runnable[] frame = new Runnable[1];
        frame[0] = () -> {
            mTurnsInARow = 0;
            if (reader.isAlive() || session.mNewInputPending.get()) mLooper.add(frame[0]);
        };
        mLooper.add(frame[0]);
        reader.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Runnable next = mLooper.poll(50, TimeUnit.MILLISECONDS);
            if (next != null) {
                next.run();
            } else if (!reader.isAlive() && mLooper.isEmpty()) {
                break;
            }
        }
        reader.join(1000);
        assertFalse("The flood finished", reader.isAlive());

        int total = 0;
        for (int turn : mTurnBytes) total += turn;
        assertEquals("Every byte was parsed", FLOOD_BYTES, total);
        String[] rows = session.mEmulator.getScreen().getTranscriptText().split("\n");
        int last = FLOOD_BYTES / 8 - 1;
        for (int i = 0; i < 3; i++) {
            assertEquals("The screen ends on the flood's last lines, in order",
                String.format(Locale.ROOT, "%06x", last - 2 + i), rows[rows.length - 3 + i]);
        }
        assertEquals("The frame callback ran between every two output turns", 1, mMostTurnsInARow);
    }

    @Test
    public void aFloodIsTakenInTurnsBoundedByTheBudget() throws Exception {
        TerminalSession session = sessionOnTestLooper();
        // Every reading of the clock moves it on 1.5 ms, as if each slice cost that much to parse.
        long step = 1_500_000L;
        long[] now = {0};
        session.mNanoClock = () -> now[0] += step;
        runFlood(session);

        int mostSlices = (int) (TerminalSession.DRAIN_BUDGET_NANOS / step) + 1;
        int most = 0;
        for (int turn : mTurnBytes) most = Math.max(most, turn);
        assertTrue("A turn overruns its budget by at most one slice: " + most,
            most <= mostSlices * TerminalSession.DRAIN_SLICE_BYTES);
        assertTrue("A mebibyte takes several turns: " + mTurnBytes.size(),
            mTurnBytes.size() >= FLOOD_BYTES / (mostSlices * TerminalSession.DRAIN_SLICE_BYTES));
    }

    @Test
    public void aFloodOnTheRealClockKeepsItsTurnsShort() throws Exception {
        TerminalSession session = sessionOnTestLooper();
        runFlood(session);
        long longest = 0;
        for (long nanos : mTurnNanos) longest = Math.max(longest, nanos);
        int most = 0;
        for (int turn : mTurnBytes) most = Math.max(most, turn);
        // The figures the performance report asks for; on a JVM, not pong, so not asserted tightly.
        System.out.println(String.format(Locale.ROOT,
            "flood: %d bytes, %d posts, %d turns, %d bytes/turn mean, %d max, longest turn %.2f ms",
            FLOOD_BYTES, mPosts.get(), mTurnBytes.size(), FLOOD_BYTES / Math.max(1, mTurnBytes.size()),
            most, longest / 1e6));
        assertTrue("One notification per turn at most", mPosts.get() <= mTurnBytes.size() + 1);
    }
}
