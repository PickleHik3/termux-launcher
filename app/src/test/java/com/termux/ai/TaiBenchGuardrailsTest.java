package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The run's four guardrails: a load that never returns is cut off and recorded as a timeout, a
 * runtime that dies mid-entry (or an app that died mid-load) leaves a crashed record and the run
 * goes on, the user's chat model is put back however the run ended, and a memory-pressure stop
 * says so in the record.
 */
public class TaiBenchGuardrailsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final TaiBenchSuite.EntryPlan A = new TaiBenchSuite.EntryPlan("model-a", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false);
    private static final TaiBenchSuite.EntryPlan B = new TaiBenchSuite.EntryPlan("model-b", TaiModelSpec.BACKEND_MNN_LLM, "gpu", false);
    private static final TaiBenchSuite.EntryPlan C = new TaiBenchSuite.EntryPlan("model-c", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false);

    private TaiBenchStore store() {
        return new TaiBenchStore(new File(new File(temp.getRoot(), "tai"), TaiBenchStore.FILE_NAME));
    }

    // ---- a host that does whatever the test needs ---------------------------------------------

    interface ChatScript {
        JSONObject run(@NonNull TaiGenerationCallback callback) throws JSONException;
    }

    private static class ScriptedHost implements TaiBenchHarness.Host {
        final List<String> calls = new ArrayList<>();
        @Nullable TaiBenchHarness harness;
        @Nullable JSONObject resident;
        boolean loadRefuses = true;
        boolean loadHangs;
        boolean stopForMemoryOnLoad;
        boolean restoreThrows;
        /** What {@link #chat} does instead of failing at once; it may drive the callback like a runtime would. */
        @Nullable ChatScript chatScript;
        final AtomicInteger cancels = new AtomicInteger();
        final AtomicInteger restores = new AtomicInteger();
        final AtomicInteger abandons = new AtomicInteger();
        final CountDownLatch neverReleased = new CountDownLatch(1);

        @NonNull
        @Override
        public JSONObject load(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            calls.add("load:" + entry.modelId);
            if (loadHangs) {
                try {
                    // A native load: nothing here looks at cancel(); only the thread being abandoned ends it.
                    neverReleased.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                return new JSONObject().put("ok", true);
            }
            if (stopForMemoryOnLoad && harness != null) harness.requestStop(TaiBenchHarness.STOP_MEMORY_PRESSURE);
            if (loadRefuses) return new JSONObject().put("ok", false).put("error", "load_refused").put("message", "no");
            return new JSONObject().put("ok", true);
        }

        @NonNull
        @Override
        public JSONObject unload() throws JSONException {
            calls.add("unload");
            return new JSONObject().put("ok", true);
        }

        @NonNull
        @Override
        public JSONObject chat(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull String userPrompt, int maxTokens,
                               @NonNull TaiGenerationCallback callback) throws JSONException {
            if (chatScript != null) return chatScript.run(callback);
            return new JSONObject().put("ok", false).put("error", "generation_failed");
        }

        @Override
        public void cancel() {
            cancels.incrementAndGet();
        }

        @NonNull
        @Override
        public TaiLoadMeter startMeter() {
            return TaiLoadMeter.start(null);
        }

        @NonNull
        @Override
        public String longInputLog() {
            return "log";
        }

        @NonNull
        @Override
        public JSONObject describe(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            return new JSONObject().put("displayName", entry.modelId).put("sizeBytes", 1L).put("runtimeVersion", "1");
        }

        @Override
        public void clearMmapCache(@NonNull TaiBenchSuite.EntryPlan entry) {
        }

        @Override
        public long processPssBytes() {
            return -1L;
        }

        @Nullable
        @Override
        public JSONObject residentChat() {
            return resident;
        }

        @Override
        public void restoreChat(@NonNull JSONObject resident) {
            restores.incrementAndGet();
            calls.add("restore:" + resident.optString("modelId"));
            if (restoreThrows) throw new IllegalStateException("reload failed");
        }

        @Override
        public void abandonRuntime() {
            abandons.incrementAndGet();
        }
    }

    private static TaiBenchHarness harness(ScriptedHost host, TaiBenchHarness.Sink sink, TaiBenchSuite.EntryPlan... entries) {
        TaiBenchGuard guard = new TaiBenchGuard() {
            @NonNull
            @Override
            public Decision beforePhase(@NonNull String phase, @NonNull TaiBenchSuite.EntryPlan entry) {
                return Decision.proceed();
            }
        };
        TaiBenchHarness harness = new TaiBenchHarness(TaiBenchSuite.Preset.QUICK, Arrays.asList(entries), host, guard, sink,
            "test", new JSONObject());
        host.harness = harness;
        return harness;
    }

    private static JSONObject lastRecord(List<JSONObject> events) {
        JSONObject record = null;
        for (JSONObject event : events) if ("entry_done".equals(event.optString("event"))) record = event.optJSONObject("record");
        return record;
    }

    // ---- 1. a hung load ------------------------------------------------------------------------

    @Test
    public void aLoadThatNeverReturnsIsRecordedAsStoppedTimeoutAndEndsTheRun() throws Exception {
        ScriptedHost host = new ScriptedHost();
        host.loadHangs = true;
        host.resident = new JSONObject().put("modelId", "chat");
        List<JSONObject> events = new ArrayList<>();
        TaiBenchHarness harness = harness(host, events::add, A, B);
        harness.setLoadHardLimitMs(150L);

        JSONObject done = harness.run();

        JSONObject record = lastRecord(events);
        assertNotNull(record);
        assertEquals("stopped:timeout", record.getString("status"));
        assertEquals("model-a", record.getString("modelId"));
        assertEquals("timeout", done.getString("stopped"));
        // The second entry never started, the stuck runtime is asked to end, and nothing is
        // reloaded into it.
        assertFalse(host.calls.contains("load:model-b"));
        assertEquals(1, host.abandons.get());
        assertEquals(0, host.restores.get());
        // The stuck load is not followed by an unload that would wait behind it: only the one before.
        assertEquals(1, Collections.frequency(host.calls, "unload"));
        host.neverReleased.countDown();
    }

    @Test
    public void theHardLimitIsTheSoftLimitPlusGraceWithinTheFloorAndCeiling() {
        long limit = TaiBenchSuite.loadHardLimitMs();
        assertTrue(limit >= TaiBenchSuite.LOAD_HARD_LIMIT_FLOOR_MS);
        assertTrue(limit <= TaiBenchSuite.LOAD_HARD_LIMIT_CEILING_MS);
        assertTrue(limit >= TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_LOAD));
    }

    // ---- 3. the user's model comes back ------------------------------------------------------

    @Test
    public void theResidentChatModelIsReloadedAfterTheRun() throws Exception {
        ScriptedHost host = new ScriptedHost();
        host.resident = new JSONObject().put("modelId", "chat").put("accelerator", "gpu");
        List<JSONObject> events = new ArrayList<>();

        harness(host, events::add, A).run();

        assertEquals(1, host.restores.get());
        assertEquals("restore:chat", host.calls.get(host.calls.size() - 1));
    }

    @Test
    public void theReloadIsAttemptedWhenTheRunEndsWithAnException() throws Exception {
        ScriptedHost host = new ScriptedHost();
        host.resident = new JSONObject().put("modelId", "chat");
        TaiBenchHarness harness = harness(host, event -> {
            if ("entry_done".equals(event.optString("event"))) throw new IOException("client went away");
        }, A);

        try {
            harness.run();
        } catch (IOException expected) {
            assertEquals("client went away", expected.getMessage());
        }

        assertEquals(1, host.restores.get());
    }

    @Test
    public void aFailedReloadDoesNotFailTheRun() throws Exception {
        ScriptedHost host = new ScriptedHost();
        host.resident = new JSONObject().put("modelId", "chat");
        host.restoreThrows = true;
        List<JSONObject> events = new ArrayList<>();

        JSONObject done = harness(host, events::add, A).run();

        assertTrue(done.getBoolean("ok"));
        assertEquals(1, host.restores.get());
    }

    @Test
    public void nothingIsReloadedWhenNothingWasResident() throws Exception {
        ScriptedHost host = new ScriptedHost();
        harness(host, event -> { }, A).run();
        assertEquals(0, host.restores.get());
    }

    // ---- 4. memory pressure vs the user ------------------------------------------------------

    @Test
    public void aMemoryPressureStopIsRecordedAsSuchAndTheModelIsNotReloadedIntoIt() throws Exception {
        ScriptedHost host = new ScriptedHost();
        host.loadRefuses = false;
        host.stopForMemoryOnLoad = true;
        host.resident = new JSONObject().put("modelId", "chat");
        List<JSONObject> events = new ArrayList<>();

        JSONObject done = harness(host, events::add, A, B).run();

        assertEquals("stopped:memory_pressure", lastRecord(events).getString("status"));
        assertEquals("memory_pressure", done.getString("stopped"));
        assertEquals(0, host.restores.get());
        assertFalse(host.calls.contains("load:model-b"));
    }

    @Test
    public void theUserStopAndTheMemoryStopHaveDistinctReasons() {
        assertEquals("cancelled", TaiBenchHarness.STOP_CANCELLED);
        assertEquals("memory_pressure", TaiBenchHarness.STOP_MEMORY_PRESSURE);
    }

    // ---- 2. crashes --------------------------------------------------------------------------

    private static JSONObject entryEvent(String name, int index, int total, TaiBenchSuite.EntryPlan entry) throws JSONException {
        return new JSONObject().put("event", name).put("index", index).put("total", total).put("entry", entry.toJson());
    }

    private static JSONObject doneRecord(TaiBenchSuite.EntryPlan entry) throws JSONException {
        return new JSONObject().put("modelId", entry.modelId).put("backend", entry.backend)
            .put("accelerator", entry.accelerator).put("speculative", entry.speculative)
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION).put("status", TaiBenchStore.STATUS_COMPLETE)
            .put("timestamp", System.currentTimeMillis());
    }

    private static JSONObject crashEvent() throws JSONException {
        JSONObject tai = new JSONObject().put("ok", false).put("error", TaiBenchCrashRecovery.CRASH_CODE)
            .put("message", "AI runtime process died while loading the model");
        return new JSONObject().put("error", new JSONObject().put("code", TaiBenchCrashRecovery.CRASH_CODE)).put("tai", tai);
    }

    private static final class Collected implements TaiManager.OpenAiStreamSink {
        final List<JSONObject> events = new ArrayList<>();
        int dones;

        @Override
        public void onEvent(@NonNull JSONObject event) {
            events.add(event);
        }

        @Override
        public void onDone() {
            dones++;
        }
    }

    @Test
    public void aCrashWritesACrashedRecordAndTheRunContinuesWithTheNextEntry() throws Exception {
        TaiBenchStore store = store();
        Collected out = new Collected();
        List<JSONObject> requests = new ArrayList<>();
        AtomicInteger attempt = new AtomicInteger();
        TaiBenchCrashRecovery recovery = new TaiBenchCrashRecovery(store, "test", 0L);
        JSONObject request = new JSONObject().put("preset", "quick");

        recovery.run(request, out, (sent, sink) -> {
            requests.add(sent);
            if (attempt.getAndIncrement() == 0) {
                sink.onEvent(entryEvent("entry_start", 0, 3, A));
                sink.onEvent(new JSONObject().put("event", "entry_done").put("index", 0).put("total", 3).put("record", doneRecord(A)));
                sink.onEvent(entryEvent("entry_start", 1, 3, B));
                // The marker is on disk before the load the runtime would have started.
                assertEquals("model-b", store.inProgress().getString("modelId"));
                sink.onEvent(crashEvent());
                sink.onDone();
            } else {
                sink.onEvent(entryEvent("entry_start", 0, 1, C));
                sink.onEvent(new JSONObject().put("event", "entry_done").put("index", 0).put("total", 1).put("record", doneRecord(C)));
                sink.onEvent(new JSONObject().put("event", "done").put("ok", true));
                sink.onDone();
            }
        });

        assertEquals(2, requests.size());
        JSONArray skip = requests.get(1).getJSONArray("skip");
        assertEquals(2, skip.length());
        assertEquals(A.key(), skip.getString(0));
        assertEquals(B.key(), skip.getString(1));
        JSONArray records = store.records();
        assertEquals(3, records.length());
        JSONObject crashed = records.getJSONObject(1);
        assertEquals(TaiBenchStore.STATUS_CRASHED, crashed.getString("status"));
        assertEquals("model-b", crashed.getString("modelId"));
        assertEquals("gpu", crashed.getString("accelerator"));
        assertEquals("model-c", records.getJSONObject(2).getString("modelId"));
        // The run screen sees the crashed entry finish, and the second attempt's entry counted in the whole run.
        JSONObject crashedEvent = null;
        JSONObject lastStart = null;
        for (JSONObject event : out.events) {
            if ("entry_done".equals(event.optString("event"))
                && TaiBenchStore.STATUS_CRASHED.equals(event.getJSONObject("record").optString("status"))) crashedEvent = event;
            if ("entry_start".equals(event.optString("event"))) lastStart = event;
        }
        assertNotNull(crashedEvent);
        assertEquals(1, crashedEvent.getInt("index"));
        assertEquals(3, crashedEvent.getInt("total"));
        assertEquals(2, lastStart.getInt("index"));
        assertEquals(3, lastStart.getInt("total"));
        // No error reached the caller, one done did, and no marker is left behind.
        for (JSONObject event : out.events) assertFalse(event.has("error"));
        assertEquals(1, out.dones);
        assertNull(store.inProgress());
    }

    @Test
    public void aCrashedRecordNeverRanksButShowsAsBrokenWithACrashedVerdict() throws Exception {
        JSONObject marker = new JSONObject().put("modelId", "model-b").put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("accelerator", "gpu").put("speculative", false).put("preset", "quick")
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION);
        JSONArray records = new JSONArray().put(TaiBenchStore.crashedRecord(marker, "died"));

        JSONObject board = TaiBenchStore.leaderboard(records, TaiBenchSuite.BENCH_VERSION);

        assertEquals(0, board.getJSONArray("ranked").length());
        assertEquals(1, board.getJSONArray("broken").length());
        assertEquals(TaiBenchStats.VERDICT_CRASHED, board.getJSONArray("broken").getJSONObject(0).getString("verdict"));
    }

    @Test
    public void aCrashOnTheLastEntryEndsTheRunWithADoneOfItsOwn() throws Exception {
        TaiBenchStore store = store();
        Collected out = new Collected();
        AtomicInteger attempts = new AtomicInteger();

        new TaiBenchCrashRecovery(store, "test", 0L).run(new JSONObject().put("preset", "quick"), out, (sent, sink) -> {
            attempts.incrementAndGet();
            sink.onEvent(entryEvent("entry_start", 0, 1, A));
            sink.onEvent(crashEvent());
            sink.onDone();
        });

        assertEquals(1, attempts.get());
        assertEquals(1, out.dones);
        assertEquals(TaiBenchStore.STATUS_CRASHED, store.records().getJSONObject(0).getString("status"));
        JSONObject last = out.events.get(out.events.size() - 1);
        assertEquals("done", last.getString("event"));
        assertTrue(last.isNull("stopped"));
    }

    @Test
    public void aCrashOutsideAnyEntryIsPassedOnAsAnError() throws Exception {
        TaiBenchStore store = store();
        Collected out = new Collected();

        new TaiBenchCrashRecovery(store, "test", 0L).run(new JSONObject().put("preset", "quick"), out, (sent, sink) -> {
            sink.onEvent(crashEvent());
            sink.onDone();
        });

        assertEquals(0, store.records().length());
        assertEquals(1, out.events.size());
        assertTrue(out.events.get(0).has("error"));
        assertEquals(1, out.dones);
    }

    // ---- stale marker ------------------------------------------------------------------------

    @Test
    public void aMarkerLeftByADeadAppBecomesACrashedRecordWhenTheNextRunStarts() throws Exception {
        TaiBenchStore store = store();
        store.markInProgress(new JSONObject().put("modelId", "model-a").put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("accelerator", "cpu").put("speculative", false).put("preset", "standard")
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION).put("appVersion", "1.0"));
        Collected out = new Collected();

        new TaiBenchCrashRecovery(store, "test", 0L).run(new JSONObject().put("preset", "quick"), out, (sent, sink) -> {
            // The stale entry is already written when the new run asks for its first one.
            assertEquals(1, store.records().length());
            assertNull(store.inProgress());
            sink.onDone();
        });

        JSONObject record = store.records().getJSONObject(0);
        assertEquals(TaiBenchStore.STATUS_CRASHED, record.getString("status"));
        assertEquals("model-a", record.getString("modelId"));
        assertEquals("standard", record.getString("preset"));
        assertEquals(TaiBenchSuite.BENCH_VERSION, record.getString("benchVersion"));
    }

    @Test
    public void recoveringWithNoMarkerWritesNothing() throws Exception {
        TaiBenchStore store = store();
        assertNull(store.recoverStaleMarker("x"));
        assertEquals(0, store.records().length());
    }

    @Test
    public void theMarkerIsClearedAndRecoveredOnlyOnce() throws Exception {
        TaiBenchStore store = store();
        store.markInProgress(new JSONObject().put("modelId", "model-a"));
        assertNotNull(store.inProgress());

        assertNotNull(store.recoverStaleMarker("x"));
        assertNull(store.recoverStaleMarker("x"));
        assertEquals(1, store.records().length());
    }

    // ---- 5. the sink going away and the user stopping mid-generation ----------------------------

    @Test
    public void aSinkThatFailsWhileTokensStreamCancelsTheGenerationAndEndsTheRunWithTheIoError() throws Exception {
        ScriptedHost host = new ScriptedHost();
        host.loadRefuses = false;
        host.resident = new JSONObject().put("modelId", "chat");
        host.chatScript = callback -> {
            callback.onToken("hello");
            callback.onComplete("hello");
            return new JSONObject().put("ok", true);
        };
        TaiBenchHarness harness = harness(host, event -> {
            if ("token".equals(event.optString("event"))) throw new IOException("client went away");
        }, A);

        try {
            harness.run();
            org.junit.Assert.fail("the sink's IOException should end the run");
        } catch (IOException expected) {
            assertEquals("client went away", expected.getMessage());
        }

        assertTrue(host.cancels.get() >= 1);
        // The user's chat model still comes back, and the model this entry loaded goes.
        assertEquals(1, host.restores.get());
        assertTrue(host.calls.contains("unload"));
    }

    @Test
    public void aUserStopMidGenerationCancelsItRecordsTheEntryStoppedAndEndsTheRun() throws Exception {
        ScriptedHost host = new ScriptedHost();
        host.loadRefuses = false;
        AtomicInteger chats = new AtomicInteger();
        List<Boolean> cancelSeenAfterStop = new ArrayList<>();
        List<JSONObject> events = new ArrayList<>();
        host.chatScript = callback -> {
            chats.incrementAndGet();
            callback.onToken("par");
            cancelSeenAfterStop.add(callback.shouldCancelGeneration());
            host.harness.requestStop(TaiBenchHarness.STOP_CANCELLED);
            cancelSeenAfterStop.add(callback.shouldCancelGeneration());
            return new JSONObject().put("ok", false).put("error", "cancelled").put("message", "stopped");
        };

        JSONObject summary = harness(host, events::add, A, B).run();

        assertEquals(Arrays.asList(false, true), cancelSeenAfterStop);
        assertTrue(host.cancels.get() >= 1);
        assertEquals(TaiBenchHarness.STOP_CANCELLED, summary.getString("stopped"));
        assertEquals("stopped:" + TaiBenchHarness.STOP_CANCELLED, lastRecord(events).getString("status"));
        // The second entry never starts.
        assertFalse(host.calls.contains("load:model-b"));
    }
}
