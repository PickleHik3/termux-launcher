package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The one pass at the end of a session, on direct executors: warm first, one polish, callbacks
 * never after a cancel. Robolectric only for the logger's {@code android.util.Log}.
 */
@RunWith(RobolectricTestRunner.class)
public class VoiceSessionCleanupTest {

    /** Runs each task at once on the calling thread; refuses work once shut down. */
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(@NonNull Runnable command) {
            if (shutdown) throw new java.util.concurrent.RejectedExecutionException();
            command.run();
        }

        @Override public void shutdown() { shutdown = true; }
        @NonNull @Override public List<Runnable> shutdownNow() { shutdown = true; return new ArrayList<>(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) { return true; }
    }

    private static final class FakePolisher implements VoiceTextPolisher {
        final List<String> calls = new ArrayList<>();
        String answer = "Cleaned.";

        @Override
        public void warm() {
            calls.add("warm");
        }

        @NonNull
        @Override
        public Result polish(@NonNull String text, long timeoutMs) {
            calls.add("polish:" + text);
            return Result.polished(answer);
        }
    }

    @Test
    public void warmThenOnePassAndTheCallbackGetsTheRawTextToo() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, new DirectExecutor(), Runnable::run);
        boolean[] warmed = {false};
        cleanup.warm(() -> warmed[0] = true);
        assertTrue(warmed[0]);
        assertFalse(cleanup.isWarming());

        List<String> got = new ArrayList<>();
        cleanup.run("uh run the failing tests again", (raw, result) -> got.add(raw + "|" + result.text + "|" + result.outcome));
        assertEquals(2, polisher.calls.size());
        assertEquals("warm", polisher.calls.get(0));
        assertEquals("polish:uh run the failing tests again", polisher.calls.get(1));
        assertEquals(1, got.size());
        assertEquals("uh run the failing tests again|Cleaned.|polished", got.get(0));
    }

    @Test
    public void aShortSessionIsHandedBackAsHeardWithoutAModelCall() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, new DirectExecutor(), Runnable::run);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run("sounds good", (raw, result) -> got.add(result));
        assertTrue(polisher.calls.isEmpty());
        assertEquals(1, got.size());
        assertEquals("sounds good", got.get(0).text);
        assertEquals("fallback:skipped:short", got.get(0).outcome);
        assertNull(VoiceSessionCleanup.skipReason("please run the tests"));
    }

    @Test
    public void aDictatedCommandNeverReachesTheModel() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, new DirectExecutor(), Runnable::run);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run("git commit dash m fix the failing voice test", (raw, result) -> got.add(result));
        assertTrue(polisher.calls.isEmpty());
        assertEquals(1, got.size());
        assertEquals("fallback:skipped:command", got.get(0).outcome);
    }

    @Test
    public void nothingComesBackAfterACancel() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, new DirectExecutor(), Runnable::run);
        cleanup.cancel();
        boolean[] warmed = {false};
        cleanup.warm(() -> warmed[0] = true);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run("please run the tests again", (raw, result) -> got.add(result));
        assertFalse(warmed[0]);
        assertTrue(got.isEmpty());
        assertTrue(polisher.calls.isEmpty());
    }
}
