package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

/**
 * Idle compression: one callback per idle edge, moved by new output, a bounded chain of steps, and
 * nothing left scheduled at rest. Also the trim path and the history byte limit on the host library.
 */
public class GhosttyIdleCompressorTest {

    /** A main-thread queue run by hand, with a clock. */
    static final class FakeScheduler implements GhosttyIdleCompressor.Scheduler {
        static final class Entry {
            final Runnable runnable;
            final long at;

            Entry(Runnable runnable, long at) {
                this.runnable = runnable;
                this.at = at;
            }
        }

        final List<Entry> queue = new ArrayList<>();
        long now;
        int posts;

        @Override
        public void postDelayed(Runnable runnable, long delayMillis) {
            posts++;
            queue.add(new Entry(runnable, now + delayMillis));
        }

        @Override
        public void cancel(Runnable runnable) {
            queue.removeIf(e -> e.runnable == runnable);
        }

        /** Move the clock and run whatever falls due, including what those runs post for now. */
        int advance(long millis) {
            now += millis;
            int ran = 0;
            while (true) {
                Entry due = null;
                for (Entry e : queue) if (e.at <= now && (due == null || e.at < due.at)) due = e;
                if (due == null) return ran;
                queue.remove(due);
                due.runnable.run();
                ran++;
            }
        }
    }

    private final FakeScheduler mScheduler = new FakeScheduler();
    private final List<Long> mSteps = new ArrayList<>();
    private int mPendingSteps;

    private GhosttyIdleCompressor compressor() {
        return new GhosttyIdleCompressor(mScheduler, () -> {
            mSteps.add(mScheduler.now);
            return mPendingSteps-- > 0 ? GhosttyNative.COMPRESS_PENDING : GhosttyNative.COMPRESS_COMPLETE;
        });
    }

    @After
    public void tearDown() {
        GhosttyMemory.clearForTests();
    }

    @Test
    public void oneCallbackPerIdleEdge() {
        GhosttyIdleCompressor compressor = compressor();
        for (int turn = 0; turn < 50; turn++) {
            if (turn > 0) mScheduler.advance(16);
            compressor.onOutput();
            assertEquals("one callback, however many turns", 1, mScheduler.queue.size());
        }
        mScheduler.advance(GhosttyIdleCompressor.IDLE_MILLIS - 1);
        assertTrue("nothing ran during output or before the idle time", mSteps.isEmpty());
        mScheduler.advance(1);
        assertEquals(1, mSteps.size());
        assertEquals("ran 250 ms after the last output", 49 * 16 + GhosttyIdleCompressor.IDLE_MILLIS, (long) mSteps.get(0));
        assertFalse(compressor.isScheduled());
        assertTrue(mScheduler.queue.isEmpty());
    }

    @Test
    public void newOutputCancelsTheWaitingCallback() {
        GhosttyIdleCompressor compressor = compressor();
        compressor.onOutput();
        mScheduler.advance(GhosttyIdleCompressor.IDLE_MILLIS - 1);
        compressor.onOutput();
        mScheduler.advance(GhosttyIdleCompressor.IDLE_MILLIS - 1);
        assertTrue(mSteps.isEmpty());
        mScheduler.advance(1);
        assertEquals(1, mSteps.size());
    }

    @Test
    public void pendingWorkChainsStepsThenStops() {
        mPendingSteps = 3;
        GhosttyIdleCompressor compressor = compressor();
        compressor.onOutput();
        mScheduler.advance(GhosttyIdleCompressor.IDLE_MILLIS);
        assertEquals("three pending steps and the one that completes", 4, mSteps.size());
        assertFalse(compressor.isScheduled());
        assertTrue(mScheduler.queue.isEmpty());
    }

    @Test
    public void aStepThatNeverCompletesIsCappedPerEdge() {
        mPendingSteps = Integer.MAX_VALUE;
        GhosttyIdleCompressor compressor = compressor();
        compressor.onOutput();
        mScheduler.advance(GhosttyIdleCompressor.IDLE_MILLIS);
        assertEquals(GhosttyIdleCompressor.MAX_STEPS, mSteps.size());
        assertTrue("nothing at rest", mScheduler.queue.isEmpty());
    }

    @Test
    public void outputMidChainRestartsTheWait() {
        mPendingSteps = Integer.MAX_VALUE;
        GhosttyIdleCompressor compressor = compressor();
        compressor.onOutput();
        mScheduler.now += GhosttyIdleCompressor.IDLE_MILLIS;
        mScheduler.queue.get(0).runnable.run(); // first step, posts the next
        mScheduler.queue.remove(0);
        assertEquals(1, mScheduler.queue.size());
        compressor.onOutput();
        assertEquals(1, mScheduler.queue.size());
        assertEquals(mScheduler.now + GhosttyIdleCompressor.IDLE_MILLIS, mScheduler.queue.get(0).at);
    }

    @Test
    public void cancelLeavesNothing() {
        GhosttyIdleCompressor compressor = compressor();
        compressor.onOutput();
        compressor.cancel();
        assertTrue(mScheduler.queue.isEmpty());
        assertFalse(compressor.isScheduled());
        mScheduler.advance(10_000);
        assertTrue(mSteps.isEmpty());
    }

    // The session and the library together (host library builds only).

    private static boolean hostLibrary() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    private static byte[] lines(int count, int columns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            // Styled, so pages hold real style data, as a coloured build log would.
            sb.append("\033[3").append(i % 8).append('m').append(i);
            while (sb.length() % columns != 0) sb.append('x');
            sb.append("\033[0m\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** A client that ignores everything (every method returns null or nothing). */
    private static final TerminalSessionClient SILENT_CLIENT = (TerminalSessionClient) java.lang.reflect.Proxy.newProxyInstance(
        TerminalSessionClient.class.getClassLoader(), new Class<?>[]{TerminalSessionClient.class}, (proxy, method, args) -> null);

    private TerminalSession ghosttySession(int columns, int rows, int transcript) {
        TerminalSession session = new TerminalSession("/bin/sh", null, new String[0], new String[0], transcript,
            SILENT_CLIENT);
        session.mGhosttyIdleScheduler = mScheduler;
        session.mEmulator = new TerminalEmulator(session, false, columns, rows,
            TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS, TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, transcript, null);
        assertTrue(session.mEmulator.useGhosttyCore());
        return session;
    }

    @Test
    public void aSessionCompressesAfterOutputAndThenRests() {
        if (!hostLibrary()) return;
        TerminalSession session = ghosttySession(52, 20, 2000);
        byte[] burst = lines(20_000, 52);
        int offset = 0;
        while (offset < burst.length) {
            int n = Math.min(8192, burst.length - offset);
            assertTrue(session.mProcessToTerminalIOQueue.write(burst, offset, n));
            offset += n;
            session.handleNewInput();
            assertEquals("one callback while output flows", 1, mScheduler.queue.size());
            mScheduler.advance(4);
        }
        long[] before = session.mEmulator.getGhosttyMemoryUsage();

        mScheduler.advance(GhosttyIdleCompressor.IDLE_MILLIS);
        long[] after = session.mEmulator.getGhosttyMemoryUsage();
        assertFalse("the chain ended", session.mGhosttyIdleCompressor.isScheduled());
        assertTrue("nothing scheduled at rest", mScheduler.queue.isEmpty());
        if (after[GhosttyNative.MEM_COMPRESSION_SUPPORTED] == 1) {
            assertTrue("pages compressed: " + after[GhosttyNative.MEM_COMPRESSED_PAGES],
                after[GhosttyNative.MEM_COMPRESSED_PAGES] > 0);
            assertTrue("resident " + before[GhosttyNative.MEM_RESIDENT_BYTES] + " -> " + after[GhosttyNative.MEM_RESIDENT_BYTES],
                after[GhosttyNative.MEM_RESIDENT_BYTES] < before[GhosttyNative.MEM_RESIDENT_BYTES]);
        }
        // A second idle edge with no new history costs one activity check and no compression pass.
        int posts = mScheduler.posts;
        session.mGhosttyIdleCompressor.onOutput();
        mScheduler.advance(GhosttyIdleCompressor.IDLE_MILLIS);
        assertEquals(posts + 1, mScheduler.posts);
        assertTrue(mScheduler.queue.isEmpty());

        session.mEmulator.releaseGhosttyCore();
        session.mGhosttyIdleCompressor.cancel();
    }

    @Test
    public void javaCoreOutputSchedulesNothing() {
        TerminalSession session = new TerminalSession("/bin/sh", null, new String[0], new String[0], 100,
            SILENT_CLIENT);
        session.mGhosttyIdleScheduler = mScheduler;
        session.mEmulator = new TerminalEmulator(session, false, 20, 5,
            TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS, TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, 100, null);
        byte[] bytes = "hello\r\n".getBytes(StandardCharsets.US_ASCII);
        assertTrue(session.mProcessToTerminalIOQueue.write(bytes, 0, bytes.length));
        session.handleNewInput();
        assertEquals(0, mScheduler.posts);
    }

    @Test
    public void trimFromBackgroundUpCompressesEveryCore() {
        if (!hostLibrary()) return;
        GhosttyMemory.clearForTests();
        TerminalEmulator a = filled(), b = filled();
        int[] trims = new int[1];
        GhosttyMemory.ImageTrim images = () -> trims[0]++;
        GhosttyMemory.addImageTrim(images);

        assertEquals(0, GhosttyMemory.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN));
        assertEquals(0, GhosttyMemory.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
        assertEquals(0, trims[0]);
        long[] before = a.getGhosttyMemoryUsage();

        assertEquals(2, GhosttyMemory.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND));
        assertEquals(1, trims[0]);
        long[] after = a.getGhosttyMemoryUsage();
        if (after[GhosttyNative.MEM_COMPRESSION_SUPPORTED] == 1) {
            assertTrue(after[GhosttyNative.MEM_COMPRESSED_PAGES] > 0);
            assertTrue(after[GhosttyNative.MEM_RESIDENT_BYTES] < before[GhosttyNative.MEM_RESIDENT_BYTES]);
        }
        GhosttyMemory.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
        assertEquals(2, trims[0]);

        // Released cores leave the registry; a removed image cache hears nothing more.
        a.releaseGhosttyCore();
        GhosttyMemory.removeImageTrim(images);
        assertEquals(1, GhosttyMemory.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE));
        assertEquals(2, trims[0]);
        b.releaseGhosttyCore();
        assertEquals(0, GhosttyMemory.liveCores());
    }

    private static TerminalEmulator filled() {
        TerminalEmulator t = new TerminalEmulator(new TerminalTestCase.MockTerminalOutput(), false, 52, 20,
            TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS, TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, 2000, null);
        assertTrue(t.useGhosttyCore());
        byte[] bytes = lines(10_000, 52);
        t.append(bytes, bytes.length);
        return t;
    }

    @Test
    public void theByteLimitKeepsAFullTranscriptOfPlainRows() {
        if (!hostLibrary()) return;
        int columns = 52, transcript = 2000;
        TerminalEmulator t = new TerminalEmulator(new TerminalTestCase.MockTerminalOutput(), false, columns, 20,
            TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS, TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, transcript, null);
        assertTrue(t.useGhosttyCore());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20_000; i++) sb.append(i).append("\r\n");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.US_ASCII);
        t.append(bytes, bytes.length);
        assertEquals("readers still see the whole transcript", transcript - 20, t.getContent().historyRows());
        t.releaseGhosttyCore();
    }

    @Test
    public void theByteLimitBoundsAStyledFlood() {
        if (!hostLibrary()) return;
        int columns = 52, rows = 20, transcript = 2000;
        TerminalEmulator t = new TerminalEmulator(new TerminalTestCase.MockTerminalOutput(), false, columns, rows,
            TerminalTestCase.INITIAL_CELL_WIDTH_PIXELS, TerminalTestCase.INITIAL_CELL_HEIGHT_PIXELS, transcript, null);
        assertTrue(t.useGhosttyCore());
        // Every cell its own true colour: the most style data a row can carry.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6000; i++) {
            for (int x = 0; x < columns; x++)
                sb.append("\033[38;2;").append(i % 256).append(';').append(x * 4).append(";7m#");
            sb.append("\033[0m\r\n");
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.US_ASCII);
        t.append(bytes, bytes.length);
        long[] usage = t.getGhosttyMemoryUsage();
        long limit = GhosttyCore.historyByteLimit(GhosttyCore.historyLimit(transcript, columns), columns);
        // Ghostty prunes whole pages, and a page of true-colour rows is far larger than a plain one
        // (about 1.3 MB against 0.4 MB): the limit holds to within one page of what the flood wrote.
        // Without it the same flood keeps twice as many pages.
        long resident = usage[GhosttyNative.MEM_RESIDENT_BYTES];
        long page = resident / Math.max(1, usage[GhosttyNative.MEM_PAGES]);
        assertTrue("resident " + resident + " within the limit " + limit + " plus one page of " + page,
            resident <= limit + page);
        assertTrue("fewer rows than the transcript once styles fill the pages", t.getContent().historyRows() < transcript - rows);
        t.releaseGhosttyCore();
    }
}
