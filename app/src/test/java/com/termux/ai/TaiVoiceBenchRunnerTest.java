package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The voice run against a fake runtime: what it asks for, what it records, how it stops. */
public class TaiVoiceBenchRunnerTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    /** A runtime whose clock moves only when it works: each transcription takes {@code transcribeMs}. */
    private static class FakeHost implements TaiVoiceBenchRunner.Host {
        long now = 1_000L;
        long transcribeMs = 500L;
        long loadMs = 800L;
        /** Where a GPU load really lands; {@code "cpu"} is the fallback. */
        String gpuLandsOn = "gpu";
        @Nullable String refusal;
        @Nullable JSONObject loadError;
        @Nullable JSONObject transcribeError;
        int failTranscribeAt = -1;
        final List<String> calls = new ArrayList<>();
        @Nullable Runnable afterTranscribe;

        @NonNull
        @Override
        public JSONObject loadCold(@NonNull String modelId, @NonNull String accelerator) throws JSONException {
            calls.add("load " + modelId + " " + accelerator);
            now += loadMs;
            if (loadError != null) return loadError;
            JSONObject answer = new JSONObject().put("ok", true).put("loadMs", loadMs - 10L);
            if ("gpu".equals(accelerator) && !"gpu".equals(gpuLandsOn)) {
                return answer.put("accelerator", gpuLandsOn).put("acceleratorFallback", "gpu compile failed");
            }
            return answer.put("accelerator", accelerator);
        }

        @NonNull
        @Override
        public JSONObject transcribe(@NonNull String modelId, @NonNull String accelerator, @NonNull TaiVoiceBench.Clip clip)
                throws JSONException {
            calls.add("transcribe " + modelId + " " + accelerator + " " + clip.id);
            now += transcribeMs;
            if (afterTranscribe != null) afterTranscribe.run();
            if (failTranscribeAt >= 0 && calls.size() - 1 == failTranscribeAt && transcribeError != null) return transcribeError;
            return new JSONObject().put("ok", true).put("text", clip.reference).put("timings", new JSONObject().put("loadMs", 0L));
        }

        @Override
        public long clipDurationMs(@NonNull TaiVoiceBench.Clip clip) {
            return clip.nominalSeconds * 1000L;
        }

        @Nullable
        @Override
        public String startRefusal() {
            return refusal;
        }

        @Override
        public void startThermalWatch(@NonNull Runnable onSevere) {
        }

        @Override
        public void stopThermalWatch() {
        }

        @Override
        public long nowMs() {
            return now;
        }
    }

    private final List<JSONObject> events = new ArrayList<>();

    private TaiBenchStore store() {
        return TaiBenchStore.in(temp.getRoot());
    }

    private TaiVoiceBenchRunner runner(FakeHost host, TaiBenchStore store) {
        return new TaiVoiceBenchRunner(host, TaiBenchGuard.ALWAYS_CONTINUE, store, events::add, "1.0", "2.3.2",
            new JSONObject());
    }

    private static TaiVoiceBenchRunner.Target parakeet(boolean gpu) {
        return new TaiVoiceBenchRunner.Target(TaiModelCatalog.PARAKEET_TDT_V3_ID, "Parakeet", "/m/parakeet.tflite", 1L, gpu);
    }

    private List<JSONObject> named(String name) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject event : events) if (name.equals(event.optString("event"))) out.add(event);
        return out;
    }

    @Test
    public void eachModelRunsColdOnCpuThenGpuWithAWarmUpAndThreeRunsPerClip() throws Exception {
        FakeHost host = new FakeHost();
        TaiBenchStore store = store();
        runner(host, store).run(Collections.singletonList(parakeet(true)), false);
        int perEntry = 1 + 1 + TaiVoiceBench.CLIPS.size() * TaiVoiceBench.TIMED_RUNS;
        assertEquals(2 * perEntry, host.calls.size());
        assertEquals("load parakeet-tdt-0.6b-v3 cpu", host.calls.get(0));
        assertEquals("transcribe parakeet-tdt-0.6b-v3 cpu jfk-3s", host.calls.get(1));
        assertEquals("load parakeet-tdt-0.6b-v3 gpu", host.calls.get(perEntry));
        JSONArray records = store.records();
        assertEquals(2, records.length());
        JSONObject cpu = records.getJSONObject(0);
        assertEquals(TaiBenchStore.STATUS_COMPLETE, cpu.getString("status"));
        assertEquals(TaiVoiceBench.KIND, cpu.getString("kind"));
        assertEquals("parakeet", cpu.getString("engine"));
        assertEquals("parakeet-tdt-0.6b-v3|stt|cpu|off", TaiBenchStore.keyOf(cpu));
        JSONObject voice = cpu.getJSONObject("phases").getJSONObject("voice");
        // 59 s of audio, 1.5 s of transcription.
        assertEquals(59_000.0 / 1_500.0, voice.getDouble("realtime"), 1e-9);
        assertEquals(0.0, voice.getDouble("wer"), 1e-9);
        assertEquals(790L, cpu.getJSONObject("phases").getJSONObject("load").getLong("ms"));
        assertEquals(3, cpu.getJSONObject("phases").getJSONArray("clips").length());
        assertEquals("gpu", records.getJSONObject(1).getString("ranOn"));
        assertFalse(records.getJSONObject(1).has("gpuFallback"));
        assertEquals(1, named("done").size());
        assertTrue(named("done").get(0).isNull("stopped"));
        assertNull(store.inProgress());
    }

    @Test
    public void aGpuLoadThatFellBackIsRecordedAsCpuNotGpu() throws Exception {
        FakeHost host = new FakeHost();
        host.gpuLandsOn = "cpu";
        TaiBenchStore store = store();
        runner(host, store).run(Collections.singletonList(parakeet(true)), false);
        JSONObject gpu = store.records().getJSONObject(1);
        assertEquals("gpu", gpu.getString("accelerator"));
        assertEquals("cpu", gpu.getString("ranOn"));
        assertEquals("gpu compile failed", gpu.getString("gpuFallback"));
        assertTrue(TaiVoiceBench.fellBack(gpu));
        JSONObject board = TaiVoiceBench.leaderboard(store.records());
        assertEquals(1, board.getJSONArray("ranked").length());
        assertTrue(board.getJSONArray("broken").getJSONObject(0).getBoolean("gpuFallback"));
    }

    @Test
    public void noGpuOfferedMeansCpuOnly() {
        FakeHost host = new FakeHost();
        runner(host, store()).run(Collections.singletonList(parakeet(false)), false);
        for (String call : host.calls) assertFalse(call, call.contains(" gpu"));
    }

    @Test
    public void aRefusedLoadSkipsTheEntryAndTheRunGoesOn() throws Exception {
        FakeHost host = new FakeHost();
        host.loadError = new JSONObject().put("error", new JSONObject().put("code", "insufficient_memory")
            .put("message", "Not enough free memory."));
        TaiBenchStore store = store();
        runner(host, store).run(Arrays.asList(parakeet(false),
            new TaiVoiceBenchRunner.Target("whisper-small-en", "Whisper", "/m/whisper.tflite", 1L, false)), false);
        JSONArray records = store.records();
        assertEquals(2, records.length());
        assertEquals("skipped:insufficient_memory", records.getJSONObject(0).getString("status"));
        assertEquals("Not enough free memory.", records.getJSONObject(0).getString("skipReason"));
        assertEquals(2, named("skipped").size());
    }

    @Test
    public void theStartCheckRefusesAHotPhone() {
        FakeHost host = new FakeHost();
        host.refusal = "too_hot";
        runner(host, store()).run(Collections.singletonList(parakeet(true)), false);
        assertTrue(host.calls.isEmpty());
        assertEquals("conditions_not_met", named("error").get(0).optString("code"));
        assertEquals("conditions_not_met", named("done").get(0).optString("stopped"));
    }

    @Test
    public void stopEndsAfterTheTranscriptionInProgressAndKeepsWhatRan() throws Exception {
        FakeHost host = new FakeHost();
        TaiBenchStore store = store();
        TaiVoiceBenchRunner runner = runner(host, store);
        // Stop during the fourth transcription: the warm-up and three timed runs of the first clip.
        host.afterTranscribe = () -> {
            if (host.calls.size() == 5) runner.requestStop("cancelled");
        };
        runner.run(Collections.singletonList(parakeet(true)), false);
        JSONArray records = store.records();
        assertEquals(1, records.length());
        JSONObject record = records.getJSONObject(0);
        assertEquals("stopped:cancelled", record.getString("status"));
        JSONObject voice = record.getJSONObject("phases").getJSONObject("voice");
        assertEquals(3_000L, voice.getLong("audioMs"));
        assertEquals("cancelled", named("done").get(0).optString("stopped"));
    }

    @Test
    public void aRuntimeCrashIsRecordedAsACrash() throws Exception {
        FakeHost host = new FakeHost();
        host.transcribeError = new JSONObject().put("error", TaiBenchCrashRecovery.CRASH_CODE).put("message", "died");
        host.failTranscribeAt = 2;
        TaiBenchStore store = store();
        runner(host, store).run(Collections.singletonList(parakeet(false)), false);
        assertEquals(TaiBenchStore.STATUS_CRASHED, store.records().getJSONObject(0).getString("status"));
    }

    @Test
    public void eventsHaveTheShapeTheRunScreenReads() throws Exception {
        FakeHost host = new FakeHost();
        runner(host, store()).run(Collections.singletonList(parakeet(false)), false);
        JSONObject start = named("entry_start").get(0);
        assertEquals("Parakeet", start.getJSONObject("entry").getString("displayName"));
        assertEquals(TaiVoiceBench.KIND, start.getJSONObject("entry").getString("kind"));
        List<JSONObject> runs = named("run_start");
        assertEquals(1 + 9, runs.size());
        assertEquals(TaiVoiceBench.CLIPS.get(2).reference, runs.get(runs.size() - 1).getString("prompt"));
        JSONObject lastToken = named("token").get(named("token").size() - 1);
        assertTrue(lastToken.getDouble("realtime") > 0.0);
        JSONObject voiceDone = null;
        for (JSONObject event : named("phase_done")) if ("voice".equals(event.getString("phase"))) voiceDone = event;
        assertTrue(voiceDone.getJSONObject("metrics").has("realtime"));
    }

    @Test
    public void transcriptionReloadTimeIsNotCountedAsSpeed() throws Exception {
        FakeHost host = new FakeHost() {
            @NonNull
            @Override
            public JSONObject transcribe(@NonNull String modelId, @NonNull String accelerator, @NonNull TaiVoiceBench.Clip clip)
                    throws JSONException {
                JSONObject answer = super.transcribe(modelId, accelerator, clip);
                now += 300L;
                return answer.put("timings", new JSONObject().put("loadMs", 300L));
            }
        };
        TaiBenchStore store = store();
        runner(host, store).run(Collections.singletonList(parakeet(false)), false);
        JSONObject voice = store.records().getJSONObject(0).getJSONObject("phases").getJSONObject("voice");
        assertEquals(1_500L, voice.getLong("wallMs"));
    }

    @Test
    public void planIsCpuThenGpuPerModel() {
        List<TaiBenchSuite.EntryPlan> plan = TaiVoiceBenchRunner.plan(Arrays.asList(parakeet(true),
            new TaiVoiceBenchRunner.Target("whisper", "Whisper", null, 1L, false)));
        assertEquals(3, plan.size());
        assertEquals("cpu", plan.get(0).accelerator);
        assertEquals("gpu", plan.get(1).accelerator);
        assertEquals("whisper|stt|cpu|off", plan.get(2).key());
    }

    @Test
    public void aSpeechRequestNamesItsProcessorOrFollowsTheSetting() throws Exception {
        assertEquals("gpu", TaiManager.sttAcceleratorFor(new JSONObject().put("accelerator", "GPU"), "auto"));
        assertEquals("cpu", TaiManager.sttAcceleratorFor(new JSONObject().put("accelerator", "cpu"), "gpu"));
        assertEquals("auto", TaiManager.sttAcceleratorFor(new JSONObject(), "auto"));
        // The Interpreter is the shell bench's, never a request's.
        assertEquals("cpu", TaiManager.sttAcceleratorFor(new JSONObject().put("accelerator", "interp"), "cpu"));
    }

    /** The Run screen's reducer over a real voice run's events: entries, phases, the dial and the fallback. */
    @Test
    public void theRunScreenStateFollowsAVoiceRun() {
        com.termux.app.fragments.settings.termux.TaiBenchRunState state = new com.termux.app.fragments.settings.termux.TaiBenchRunState();
        state.begin(TaiVoiceBench.PRESET_ID, Collections.singletonList(
            new com.termux.app.fragments.settings.termux.TaiBenchRunState.Planned(TaiModelCatalog.PARAKEET_TDT_V3_ID, "Parakeet", false)),
            0.0, 0L);
        assertTrue(state.voice());
        FakeHost host = new FakeHost();
        host.gpuLandsOn = "cpu";
        new TaiVoiceBenchRunner(host, TaiBenchGuard.ALWAYS_CONTINUE, store(), state::apply, "1.0", "2.3.2", new JSONObject())
            .run(Collections.singletonList(parakeet(true)), false);
        assertEquals(com.termux.app.fragments.settings.termux.TaiBenchRunState.Phase.DONE, state.phase);
        assertEquals(2, state.entries.size());
        com.termux.app.fragments.settings.termux.TaiBenchRunState.Entry cpu = state.entries.get(0);
        assertTrue(cpu.voice);
        assertEquals(com.termux.app.fragments.settings.termux.TaiBenchRunState.EntryStatus.DONE, cpu.status);
        assertEquals(59_000.0 / 1_500.0, cpu.realtime, 1e-9);
        assertEquals(0.0, cpu.wer, 1e-9);
        assertEquals(790L, cpu.loadMs);
        assertFalse(cpu.gpuFellBack());
        assertTrue(state.entries.get(1).gpuFellBack());
        assertEquals(Arrays.asList("load", "warmup", "voice"),
            com.termux.app.fragments.settings.termux.TaiBenchRunState.phasesFor(cpu));
        // The live view's prompt is the last clip's spoken text; its reply what was heard.
        assertEquals(TaiVoiceBench.CLIPS.get(2).reference, state.live.prompt);
        assertEquals(TaiVoiceBench.CLIPS.get(2).reference, state.live.reply.toString());
    }

    @Test
    public void theDialShowsSpeedWhileClipsRun() {
        com.termux.app.fragments.settings.termux.TaiBenchRunState state = new com.termux.app.fragments.settings.termux.TaiBenchRunState();
        state.begin(TaiVoiceBench.PRESET_ID, Collections.emptyList(), 0.0, 0L);
        FakeHost host = new FakeHost();
        List<JSONObject> seen = new ArrayList<>();
        TaiVoiceBenchRunner runner = new TaiVoiceBenchRunner(host, TaiBenchGuard.ALWAYS_CONTINUE, store(), event -> {
            state.apply(event);
            seen.add(event);
        }, "1.0", "2.3.2", new JSONObject());
        // Stop after the first timed run so the state is caught mid-entry.
        host.afterTranscribe = () -> {
            if (host.calls.size() == 3) runner.requestStop("cancelled");
        };
        runner.run(Collections.singletonList(parakeet(false)), false);
        com.termux.app.fragments.settings.termux.TaiBenchRunState.Dial dial = state.dial(0L);
        assertEquals(com.termux.app.fragments.settings.termux.TaiBenchRunState.DialKind.REALTIME, dial.kind);
        assertEquals(3_000.0 / 500.0, dial.value, 1e-9);
    }
}
