package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What every {@link TaiRuntimeLink} does with generations (ADR 0027 §4), run against the in-memory
 * fake ({@link FakeTaiRuntimeLinkContractTest}) and the in-process adapter over a fake runtime
 * ({@link InProcessTaiRuntimeLinkContractTest}): chunks in order, cancel by id of the running and of
 * a waiting generation, an interactive request ahead of waiting normal ones but never the running
 * one, cancel of an unknown id, and the cancel-all.
 */
public abstract class TaiRuntimeLinkContractTest {
    private static final long WAIT_MS = 5_000L;

    private TaiRuntimeLink link;
    private FakeTaiRuntimeLink.Gates gates;
    /** The order generations started in, by id, across every recording sink. */
    private final List<String> startOrder = Collections.synchronizedList(new ArrayList<>());

    /** A fresh link and the gates its scripted generations hold on. */
    protected abstract TaiRuntimeLink newLink(@NonNull FakeTaiRuntimeLink.Gates gates);

    @Before
    public void setUpLink() {
        gates = new FakeTaiRuntimeLink.Gates();
        link = newLink(gates);
    }

    @Test
    public void aStreamsChunksArriveInOrderAfterItStartsAndThenItEnds() throws Exception {
        Recorder sink = new Recorder("a");

        Running running = stream("a", FakeTaiRuntimeLink.script(Arrays.asList("one", "two", "three"), 1L, null, null), sink);
        running.join();

        assertEquals(Arrays.asList("started", "chunk:one", "chunk:two", "chunk:three", "done"), sink.log());
        assertNull(running.failure.get());
    }

    @Test
    public void aNonStreamingGenerationReturnsItsAnswer() throws Exception {
        Recorder sink = new Recorder("a");
        TaiRuntimeLink.Generation generation = link.generate(request("a", false,
            FakeTaiRuntimeLink.script(Arrays.asList("hel", "lo"), 0L, null, null), TaiRuntimeLink.Priority.NORMAL), sink);

        JSONObject answer = generation.await();

        assertEquals("hello", answer.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
        assertEquals(Collections.singletonList("started"), sink.log());
    }

    @Test
    public void cancellingTheRunningGenerationStopsIt() throws Exception {
        Recorder sink = new Recorder("a");
        Running running = stream("a", FakeTaiRuntimeLink.script(Collections.singletonList("one"), 0L, "hold-a", null), sink);
        assertTrue(gates.awaitReached("hold-a", WAIT_MS));

        running.generation.cancel();
        running.join();

        assertEquals(Arrays.asList("started", "chunk:one", "error:generation_cancelled", "done"), sink.log());
    }

    @Test
    public void cancellingWhileTheModelLoadsEndsItBeforeItEverGenerates() throws Exception {
        Recorder sink = new Recorder("a");
        Running running = stream("a", FakeTaiRuntimeLink.script(Collections.singletonList("one"), 0L, null, null, "load-a"), sink);
        assertTrue(gates.awaitReached("load-a", WAIT_MS));

        running.generation.cancel();
        gates.open("load-a");
        running.join();

        assertEquals(Arrays.asList("started", "error:generation_cancelled", "done"), sink.log());
        assertEquals(0, gates.generateCalls.get());
    }

    @Test
    public void cancellingAWaitingGenerationTakesItOutOfTheQueueAndItNeverStarts() throws Exception {
        Recorder first = new Recorder("a");
        Running a = stream("a", FakeTaiRuntimeLink.script(Collections.singletonList("a"), 0L, "hold-a", null), first);
        assertTrue(gates.awaitReached("hold-a", WAIT_MS));
        Recorder second = new Recorder("b");
        Running b = stream("b", FakeTaiRuntimeLink.script(Collections.singletonList("b"), 0L, null, null), second);

        link.cancel("b");
        b.join();
        gates.open("hold-a");
        a.join();

        assertEquals(Arrays.asList("waiting", "error:generation_cancelled", "done"), second.log());
        assertEquals(Arrays.asList("started", "chunk:a", "done"), first.log());
        assertEquals(Collections.singletonList("a"), startOrder);
    }

    @Test
    public void anInteractiveRequestGoesAheadOfWaitingNormalOnesButNotTheRunningOne() throws Exception {
        Running running = stream("running", FakeTaiRuntimeLink.script(Collections.singletonList("r"), 0L, "hold", null),
            new Recorder("running"));
        assertTrue(gates.awaitReached("hold", WAIT_MS));
        Recorder firstNormal = new Recorder("normal-1");
        Running normal1 = stream("normal-1", FakeTaiRuntimeLink.script(Collections.singletonList("n1"), 0L, null, null), firstNormal);
        Running normal2 = stream("normal-2", FakeTaiRuntimeLink.script(Collections.singletonList("n2"), 0L, null, null),
            new Recorder("normal-2"));
        Recorder interactive = new Recorder("interactive");
        Running urgent = stream("interactive", FakeTaiRuntimeLink.script(Collections.singletonList("i"), 0L, null, null),
            interactive, TaiRuntimeLink.Priority.INTERACTIVE);

        // The running generation is never interrupted: it is still the only one started.
        assertEquals(Collections.singletonList("running"), startOrder);
        gates.open("hold");
        running.join();
        urgent.join();
        normal1.join();
        normal2.join();

        assertEquals(Arrays.asList("running", "interactive", "normal-1", "normal-2"), startOrder);
        assertEquals(Arrays.asList("waiting", "started", "chunk:i", "done"), interactive.log());
        assertEquals(Arrays.asList("waiting", "started", "chunk:n1", "done"), firstNormal.log());
    }

    @Test
    public void cancellingAnUnknownIdDoesNothing() throws Exception {
        Recorder sink = new Recorder("a");
        Running running = stream("a", FakeTaiRuntimeLink.script(Collections.singletonList("one"), 0L, "hold-a", null), sink);
        assertTrue(gates.awaitReached("hold-a", WAIT_MS));

        link.cancel("nobody");
        gates.open("hold-a");
        running.join();

        assertEquals(Arrays.asList("started", "chunk:one", "done"), sink.log());
    }

    @Test
    public void cancelAllStopsTheRunningGeneration() throws Exception {
        Recorder sink = new Recorder("a");
        Running running = stream("a", FakeTaiRuntimeLink.script(Collections.singletonList("one"), 0L, "hold-a", null), sink);
        assertTrue(gates.awaitReached("hold-a", WAIT_MS));

        JSONObject result = link.cancelAll(TaiBenchHarness.STOP_CANCELLED);
        running.join();

        assertTrue(result.toString(), result.optBoolean("ok", false));
        assertEquals(Arrays.asList("started", "chunk:one", "error:generation_cancelled", "done"), sink.log());
    }

    @NonNull
    private Running stream(@NonNull String id, @NonNull String body, @NonNull Recorder sink) throws Exception {
        return stream(id, body, sink, TaiRuntimeLink.Priority.NORMAL);
    }

    /** Starts a stream and waits for it on a thread of its own, as a route's request thread would. */
    @NonNull
    private Running stream(@NonNull String id, @NonNull String body, @NonNull Recorder sink,
                           @NonNull TaiRuntimeLink.Priority priority) throws Exception {
        TaiRuntimeLink.Generation generation = link.generate(request(id, true, body, priority), sink);
        assertEquals(id, generation.id());
        Running running = new Running(generation);
        running.thread.start();
        return running;
    }

    @NonNull
    private static TaiRuntimeLink.GenerationRequest request(@NonNull String id, boolean stream, @NonNull String body,
                                                            @NonNull TaiRuntimeLink.Priority priority) {
        return new TaiRuntimeLink.GenerationRequest(TaiRuntimeLink.Kind.CHAT, stream, body, priority, id, WAIT_MS);
    }

    private static final class Running {
        final TaiRuntimeLink.Generation generation;
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final Thread thread;

        Running(@NonNull TaiRuntimeLink.Generation generation) {
            this.generation = generation;
            this.thread = new Thread(() -> {
                try {
                    generation.await();
                } catch (Exception e) {
                    failure.set(e);
                }
            });
        }

        void join() throws InterruptedException {
            thread.join(WAIT_MS);
            assertFalse("generation " + generation.id() + " did not end", thread.isAlive());
            assertNull(failure.get());
        }
    }

    /** Writes what a sink hears as short lines: waiting, started, chunk:<text>, error:<code>, done. */
    private final class Recorder implements TaiRuntimeLink.GenerationSink {
        private final String id;
        private final List<String> log = Collections.synchronizedList(new ArrayList<>());

        Recorder(@NonNull String id) {
            this.id = id;
        }

        @Override
        public void onWaiting() {
            log.add("waiting");
        }

        @Override
        public void onStarted() {
            startOrder.add(id);
            log.add("started");
        }

        @Override
        public void onEvent(@NonNull JSONObject event) {
            JSONObject error = event.optJSONObject("error");
            if (error != null) {
                log.add("error:" + error.optString("code"));
                return;
            }
            JSONArray choices = event.optJSONArray("choices");
            JSONObject delta = choices == null ? null : choices.optJSONObject(0).optJSONObject("delta");
            log.add("chunk:" + (delta == null ? "" : delta.optString("content")));
        }

        @Override
        public void onDone() {
            log.add("done");
        }

        @NonNull
        List<String> log() {
            synchronized (log) {
                return new ArrayList<>(log);
            }
        }
    }
}
