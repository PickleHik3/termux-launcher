package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A session's cleanup on plain executors: warm first, early passes while listening, the rest at the
 * end, callbacks never after a cancel. Robolectric only for the logger's {@code android.util.Log}.
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

    /** Holds tasks until {@link #runAll}, as the cleanup thread would while a pass is out. */
    private static final class QueuedExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void execute(@NonNull Runnable command) {
            if (shutdown) throw new java.util.concurrent.RejectedExecutionException();
            queue.add(command);
        }

        void runAll() {
            while (!queue.isEmpty()) queue.poll().run();
        }

        @Override public void shutdown() { shutdown = true; }
        @NonNull @Override public List<Runnable> shutdownNow() { shutdown = true; return new ArrayList<>(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown && queue.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) { return true; }
    }

    private static final class FakePolisher implements VoiceTextPolisher {
        final List<String> calls = new ArrayList<>();
        final List<String> languages = new ArrayList<>();
        String answer = "Cleaned.";
        boolean whileListening;
        String outcome;

        @NonNull
        @Override
        public Result polish(@NonNull String text, @NonNull String context, @Nullable String language, long timeoutMs) {
            languages.add(language);
            return polish(text, context, timeoutMs);
        }

        @NonNull
        @Override
        public Result polish(@NonNull String text, @NonNull String context, long timeoutMs) {
            calls.add("polish:" + text + (context.isEmpty() ? "" : "|after:" + context));
            return outcome != null ? Result.fallback(text, outcome) : Result.polished(answer);
        }

        @Override
        public boolean cleansWhileListening() {
            return whileListening;
        }

        @Override
        public void warm() {
            calls.add("warm");
        }

        @NonNull
        @Override
        public Result polish(@NonNull String text, long timeoutMs) {
            return polish(text, "", timeoutMs);
        }
    }

    @Test
    public void warmThenOnePassAndTheCallbackGetsTheRawTextToo() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        boolean[] warmed = {false};
        cleanup.warm(() -> warmed[0] = true);
        assertTrue(warmed[0]);
        assertFalse(cleanup.isWarming());

        List<String> got = new ArrayList<>();
        String raw = "uh run the the failing tests again on the branch";
        cleanup.run(raw, (cleanedRaw, result) -> got.add(cleanedRaw + "|" + result.text + "|" + result.outcome));
        assertEquals(2, polisher.calls.size());
        assertEquals("warm", polisher.calls.get(0));
        // The filler is stripped before the model sees the text, and the language is named.
        assertEquals("polish:run the the failing tests again on the branch", polisher.calls.get(1));
        assertEquals(Collections.singletonList("en"), polisher.languages);
        assertEquals(1, got.size());
        assertEquals(raw + "|Cleaned.|polished", got.get(0));
    }

    @Test
    public void cleanTextIsKeptAsHeardWithoutAModelCall() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        cleanup.warm(() -> { });
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        String raw = "The keyboard opens fine in portrait mode. In landscape it covers half the screen.";
        cleanup.run(raw, (cleanedRaw, result) -> got.add(result));
        assertEquals(Collections.singletonList("warm"), polisher.calls);
        assertEquals(raw, got.get(0).text);
        assertEquals("fallback:unchanged", got.get(0).outcome);
    }

    @Test
    public void fillersAloneAreStrippedWithoutAModelCall() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        cleanup.warm(() -> { });
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run("So um, I think we should uh move the meeting to Thursday.", (raw, result) -> got.add(result));
        assertEquals(Collections.singletonList("warm"), polisher.calls);
        assertEquals("So I think we should move the meeting to Thursday.", got.get(0).text);
        assertTrue(got.get(0).isPolished());
    }

    @Test
    public void theSpeechModelsLanguageReachesTheModel() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        cleanup.warm(() -> { });
        cleanup.setLanguageHint("es-ES");
        cleanup.run("Mañana no puedo ir a la la reunión del lunes.", (raw, result) -> { });
        assertEquals(Collections.singletonList("es"), polisher.languages);
    }

    @Test
    public void aShortSessionIsHandedBackAsHeardWithoutAModelCall() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
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
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run("git commit dash m fix the failing voice test", (raw, result) -> got.add(result));
        assertTrue(polisher.calls.isEmpty());
        assertEquals(1, got.size());
        assertEquals("fallback:skipped:command", got.get(0).outcome);
    }

    @Test
    public void nothingComesBackAfterACancel() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        cleanup.cancel();
        boolean[] warmed = {false};
        cleanup.warm(() -> warmed[0] = true);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run("please run the tests again", (raw, result) -> got.add(result));
        assertFalse(warmed[0]);
        assertTrue(got.isEmpty());
        assertTrue(polisher.calls.isEmpty());
    }

    /** Four sentences, each with a stutter, so the gate sends the whole part as one group. */
    private static final String SENTENCES = "We are are working on the branch. A user user asked for bigger icons. "
        + "The the biggest size is small. Custom packs packs look off.";

    @Test
    public void earlyPassesWaitForTheWarmUpAndTheEndCleansOnlyTheTail() {
        FakePolisher polisher = new FakePolisher();
        polisher.whileListening = true;
        QueuedExecutor worker = new QueuedExecutor();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, worker, Runnable::run);
        cleanup.warm(() -> { });
        // Not warmed yet: no early pass.
        cleanup.onPhrase(SENTENCES, false, false);
        worker.runAll();
        assertEquals(1, polisher.calls.size());
        polisher.answer = "First part.";
        cleanup.onPhrase(SENTENCES, false, false);
        String raw = SENTENCES + " and then the drawer should open everywhere";
        List<String> got = new ArrayList<>();
        // The end comes while the early pass is still out: it waits for it.
        cleanup.run(raw, (cleanedRaw, result) -> got.add(cleanedRaw + "|" + result.text + "|" + result.outcome));
        assertTrue(got.isEmpty());
        worker.queue.poll().run();
        polisher.answer = "Second part.";
        worker.runAll();
        assertEquals(Arrays.asList("warm", "polish:" + SENTENCES,
            "polish:and then the drawer should open everywhere|after:First part."), polisher.calls);
        assertEquals(Collections.singletonList(raw + "|First part. Second part.|polished"), got);
    }

    @Test
    public void aCpuModelKeepsTheSinglePassAtTheEnd() {
        FakePolisher polisher = new FakePolisher();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        cleanup.warm(() -> { });
        cleanup.onPhrase(SENTENCES, false, false);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run(SENTENCES, (raw, result) -> got.add(result));
        assertEquals(Arrays.asList("warm", "polish:" + SENTENCES), polisher.calls);
        assertEquals("Cleaned.", got.get(0).text);
    }

    @Test
    public void aCancelDropsThePassInFlightAndEverythingAfter() {
        FakePolisher polisher = new FakePolisher();
        polisher.whileListening = true;
        QueuedExecutor worker = new QueuedExecutor();
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, worker, Runnable::run);
        cleanup.warm(() -> { });
        worker.runAll();
        cleanup.onPhrase(SENTENCES, false, false);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run(SENTENCES + " and one more thing here", (raw, result) -> got.add(result));
        cleanup.cancel();
        worker.runAll();
        assertEquals(Collections.singletonList("warm"), polisher.calls);
        assertTrue(got.isEmpty());
    }

    @Test
    public void aTimedOutEarlyPassFallsBackToOnePassOverEverything() {
        FakePolisher polisher = new FakePolisher();
        polisher.whileListening = true;
        VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, new DirectExecutor(), Runnable::run);
        cleanup.warm(() -> { });
        polisher.outcome = "tai_runtime_timeout";
        cleanup.onPhrase(SENTENCES, false, false);
        polisher.outcome = null;
        String raw = SENTENCES + " " + SENTENCES;
        cleanup.onPhrase(raw, false, false);
        List<VoiceTextPolisher.Result> got = new ArrayList<>();
        cleanup.run(raw, (cleanedRaw, result) -> got.add(result));
        assertEquals(Arrays.asList("warm", "polish:" + SENTENCES, "polish:" + raw), polisher.calls);
        assertEquals("Cleaned.", got.get(0).text);
    }

    /**
     * A model that takes its time to load: every call the cleanup thread makes waits for {@link #loaded},
     * as {@code LocalTaiVoiceTextPolisher} waits on the runtime while E4B loads, and notes the thread it ran on.
     */
    private static final class LoadingPolisher implements VoiceTextPolisher {
        final CountDownLatch loaded = new CountDownLatch(1);
        final List<Thread> callers = new CopyOnWriteArrayList<>();
        final boolean warmBlocks;

        LoadingPolisher(boolean warmBlocks) {
            this.warmBlocks = warmBlocks;
        }

        private void waitForLoad() {
            callers.add(Thread.currentThread());
            try {
                loaded.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void warm() {
            if (warmBlocks) waitForLoad();
            else callers.add(Thread.currentThread());
        }

        @Override
        public boolean cleansWhileListening() {
            callers.add(Thread.currentThread());
            return true;
        }

        @NonNull
        @Override
        public Result polish(@NonNull String text, long timeoutMs) {
            waitForLoad();
            return Result.polished("Cleaned.");
        }
    }

    /** Runs what the cleanup posts to the main thread, on the test thread, until {@code done} holds. */
    private static void drainMain(@NonNull BlockingQueue<Runnable> main, @NonNull boolean[] done) throws InterruptedException {
        while (!done[0]) {
            Runnable next = main.poll(5, TimeUnit.SECONDS);
            if (next == null) throw new AssertionError("the cleanup thread never answered");
            next.run();
        }
    }

    /**
     * The home screen never waits on a model load: while the warm-up holds the cleanup thread, every
     * call the activity makes on the main thread returns, and the model is never called from it.
     */
    @Test(timeout = 10_000L)
    public void aModelStillLoadingNeverHoldsTheMainThread() throws InterruptedException {
        LoadingPolisher polisher = new LoadingPolisher(true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        try {
            VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, worker, main::add);
            boolean[] warmed = {false};
            cleanup.warm(() -> warmed[0] = true);
            assertTrue(cleanup.isWarming());
            cleanup.onPhrase(SENTENCES, false, false);
            cleanup.onPhrase(SENTENCES + " " + SENTENCES, true, false);
            boolean[] cleaned = {false};
            cleanup.run(SENTENCES + " " + SENTENCES, (raw, result) -> cleaned[0] = true);
            assertTrue(cleanup.isWarming());
            assertFalse(warmed[0]);
            assertFalse(cleaned[0]);

            polisher.loaded.countDown();
            drainMain(main, cleaned);
            assertTrue(warmed[0]);
            assertFalse(polisher.callers.contains(Thread.currentThread()));
        } finally {
            worker.shutdownNow();
        }
    }

    /** Once warmed, an early pass that waits on the runtime holds the cleanup thread only. */
    @Test(timeout = 10_000L)
    public void anEarlyPassWaitingOnTheRuntimeNeverHoldsTheMainThread() throws InterruptedException {
        LoadingPolisher polisher = new LoadingPolisher(false);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        try {
            VoiceSessionCleanup cleanup = new VoiceSessionCleanup(polisher, VoicePolishRules.LEVEL_LIGHT, worker, main::add);
            boolean[] warmed = {false};
            cleanup.warm(() -> warmed[0] = true);
            drainMain(main, warmed);

            cleanup.onPhrase(SENTENCES, false, false);
            String raw = SENTENCES + " " + SENTENCES;
            cleanup.onPhrase(raw, false, false);
            boolean[] cleaned = {false};
            cleanup.run(raw, (cleanedRaw, result) -> cleaned[0] = true);
            assertFalse(cleaned[0]);

            polisher.loaded.countDown();
            drainMain(main, cleaned);
            assertFalse(polisher.callers.contains(Thread.currentThread()));
        } finally {
            worker.shutdownNow();
        }
    }
}
