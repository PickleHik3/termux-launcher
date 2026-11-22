package com.termux.ai;

import android.os.Process;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.BenchmarkInfo;
import com.google.ai.edge.litertlm.BenchmarkKt;
import com.google.ai.edge.litertlm.Contents;
import com.termux.ai.TaiManager.LoadDecision;
import com.termux.ai.TaiManager.OpenAiStreamSink;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The benchmark and the bench host: bench runs, skip-wait, hold, the harness host adapter, the results file. Moved out of TaiManager. */
public final class TaiBenchHost {
    private final TaiManager manager;

    TaiBenchHost(@NonNull TaiManager manager) {
        this.manager = manager;
    }

    /**
     * "Skip the wait" (spec Screen 5): ends the active bench's current or next cool-down wait at
     * once, and that entry's record is marked {@code warmStart}. A no-op, not an error, when
     * nothing is running.
     */
    @NonNull
    public JSONObject skipBenchCooldown() throws JSONException {
        if (manager.shouldDelegateRuntime()) return manager.runtimeRequest(TaiRuntimeIpc.OP_BENCH_SKIP_WAIT, "{}");
        TaiBenchHarness bench = manager.activeBench;
        if (bench != null) bench.skipCooldown();
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("skipped", bench != null);
        return result;
    }

    /**
     * "Leaving pauses at the end of the current step; Resume on return" (spec Safety table,
     * Screen/app row): holds or releases the active bench's guard. The in-app run screen's only
     * lever — a {@code tai benchmark} from the terminal is never held. A no-op, not an error, when
     * nothing is running.
     */
    @NonNull
    public JSONObject holdBench(@NonNull String body) throws JSONException {
        if (manager.shouldDelegateRuntime()) return manager.runtimeRequest(TaiRuntimeIpc.OP_BENCH_HOLD, manager.delegatedRuntimeBody(body));
        JSONObject request = manager.parseBody(body);
        boolean held = request.optBoolean("held", false);
        TaiBenchHarness bench = manager.activeBench;
        if (bench != null) bench.setHeld(held);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("held", held);
        result.put("active", bench != null);
        return result;
    }

    /** The results file, {@code files/tai/benchmarks.json}, written by this (the app) process only. */
    @NonNull
    TaiBenchStore benchStore() {
        return TaiBenchStore.in(manager.appContext.getFilesDir());
    }

    /**
     * {@code GET /v1/ai/benchmarks}: every record and the leaderboard for the current bench
     * version. Deleting a model removes its results (see {@link #pruneModelRecords}); each record
     * still says whether its model is installed so a reader can mark it.
     */
    @NonNull
    public JSONObject benchmarks() throws JSONException {
        JSONObject data = benchStore().toJson(TaiBenchSuite.BENCH_VERSION);
        JSONArray records = data.optJSONArray("records");
        if (records != null) {
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.optJSONObject(i);
                if (record != null) record.put("installed", manager.resolveModel(record.optString("modelId", "")) != null);
            }
        }
        return data;
    }

    /** {@code DELETE /v1/ai/benchmarks}: clears every record, or {@code modelId}'s only. */
    @NonNull
    public JSONObject clearBenchmarks(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        String modelId = request.optString("modelId", request.optString("model", "")).trim();
        try {
            int removed = benchStore().clear(modelId.isEmpty() ? null : modelId);
            JSONObject data = new JSONObject();
            data.put("ok", true);
            data.put("removed", removed);
            data.put("modelId", modelId.isEmpty() ? JSONObject.NULL : modelId);
            return data;
        } catch (IOException e) {
            return manager.error(500, "benchmarks_clear_failed", "Could not rewrite the benchmark file: " + message(e));
        }
    }

    /**
     * {@code POST /v1/ai/benchmarks/run} without {@code stream}: runs the whole bench and answers
     * once with the {@code done} summary plus the leaderboard the records landed on.
     */
    @NonNull
    public JSONObject benchRunCollect(@NonNull String body) throws JSONException {
        AtomicReference<JSONObject> done = new AtomicReference<>();
        AtomicReference<JSONObject> failure = new AtomicReference<>();
        try {
            benchRun(body, new OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) {
                    String name = event.optString("event", "");
                    if ("done".equals(name)) done.set(event);
                    // An error outside any entry is the run failing to start; entry errors are in the records.
                    else if ("error".equals(name) && !event.has("entry")) failure.compareAndSet(null, event);
                }

                @Override
                public void onDone() {
                }
            });
        } catch (IOException e) {
            return manager.error(500, "benchmark_failed", message(e));
        }
        if (done.get() == null) {
            JSONObject event = failure.get();
            if (event == null) return manager.error(500, "benchmark_failed", "The benchmark ended without a result.");
            return manager.error(event.optInt("status", 500), event.optString("code", "benchmark_failed"),
                event.optString("message", "The benchmark could not start."));
        }
        JSONObject result = new JSONObject(done.get().toString());
        result.remove("event");
        result.remove("at");
        result.put("leaderboard", benchStore().toJson(TaiBenchSuite.BENCH_VERSION).opt("leaderboard"));
        return result;
    }

    /** An exception's message, or its class name when it has none (an IOException often does not). */
    @NonNull
    private static String message(@NonNull Exception e) {
        String text = e.getMessage();
        return text == null || text.isEmpty() ? e.getClass().getSimpleName() : text;
    }

    /**
     * Runs bench v2 over {@code models} and streams {@link TaiBenchHarness} events into
     * {@code sink}. Request: {@code {models: [id…] | model, preset: quick|standard,
     * compare: bool? (both processors where supported), processors: [cpu|gpu…]?, eagle: bool?}}. In the app process the request is resolved
     * (model specs and the settings' runtime options travel with it, as every runtime request's
     * do), forwarded to {@code :tai_runtime} as {@link TaiRuntimeIpc#OP_BENCH_RUN}, and each
     * {@code entry_done} record is appended to the store as it arrives. In the runtime process
     * the harness runs here, against the local router.
     */
    public void benchRun(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        long startedMs = android.os.SystemClock.elapsedRealtime();
        TaiEventLog.log(manager.appContext, TaiEventLog.BENCH_START, "benchmark run");
        String outcome = "finished";
        try {
            if (manager.shouldDelegateRuntime()) {
                benchRunDelegated(body, sink);
            } else {
                benchRunLocal(body, sink);
            }
        } catch (JSONException | IOException | RuntimeException e) {
            outcome = "failed: " + e.getClass().getSimpleName();
            throw e;
        } finally {
            TaiEventLog.log(manager.appContext, TaiEventLog.BENCH_DONE, null, null, null, 0,
                android.os.SystemClock.elapsedRealtime() - startedMs, 0L, outcome);
        }
    }

    private void benchRunDelegated(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        if (manager.runtimeClient == null) {
            emitBenchError(sink, manager.error(503, "tai_runtime_unavailable", "On-device AI runtime service client is unavailable."));
            return;
        }
        JSONObject prepared = prepareBenchRequest(manager.parseBody(body));
        if (!prepared.optBoolean("ok", false)) {
            emitBenchError(sink, prepared);
            return;
        }
        prepared.remove("ok");
        if (!manager.benchStreamActive.compareAndSet(false, true)) {
            emitBenchError(sink, manager.error(409, "benchmark_running", "A benchmark is already running; wait for it or stop it with tai cancel."));
            return;
        }
        try {
            // The runtime can die under a run; the recovery writes what the harness could not and
            // asks for the rest of the entries again (see TaiBenchCrashRecovery).
            new TaiBenchCrashRecovery(benchStore(), com.termux.BuildConfig.VERSION_NAME,
                TaiBenchCrashRecovery.DEFAULT_RESTART_PAUSE_MS).run(prepared, benchErrorMapped(sink),
                (request, attemptSink) -> manager.runtimeClient.stream(TaiRuntimeIpc.OP_BENCH_RUN, request.toString(), attemptSink));
        } finally {
            manager.benchStreamActive.set(false);
        }
    }

    /**
     * {@code sink} with the service client's error shape ({@code {error, tai}}) turned into the
     * bench's own {@code error} event, for errors the recovery does not handle itself.
     */
    @NonNull
    private static OpenAiStreamSink benchErrorMapped(@NonNull OpenAiStreamSink sink) {
        return new OpenAiStreamSink() {
            @Override
            public void onEvent(@NonNull JSONObject event) throws IOException {
                if (!event.has("event") && event.has("error")) {
                    try {
                        sink.onEvent(benchErrorEvent(event.optJSONObject("tai") == null ? event : event.getJSONObject("tai")));
                    } catch (JSONException e) {
                        throw new IOException(e);
                    }
                    return;
                }
                sink.onEvent(event);
            }

            @Override
            public void onDone() throws IOException {
                sink.onDone();
            }
        };
    }

    /**
     * The app-process half of a bench request: every model resolved and checked (a chat model,
     * installed), the preset and processors validated, the specs and runtime options attached the
     * way {@link #delegatedRuntimeBody} attaches them for one model. {@code ok:false} is the error
     * to answer with.
     */
    @NonNull
    private JSONObject prepareBenchRequest(@NonNull JSONObject request) throws JSONException {
        List<String> ids = new ArrayList<>();
        JSONArray models = request.optJSONArray("models");
        if (models != null) {
            for (int i = 0; i < models.length(); i++) {
                Object item = models.opt(i);
                String id = item instanceof JSONObject ? ((JSONObject) item).optString("model", "") : String.valueOf(item);
                if (!id.trim().isEmpty() && !ids.contains(id.trim())) ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            String single = request.optString("model", "").trim();
            ids.add(single.isEmpty() ? manager.settings.getDefaultAssistantModel() : single);
        }
        TaiBenchSuite.Preset preset = TaiBenchSuite.Preset.fromId(request.optString("preset", null));
        if (preset == null) return manager.error(400, "bad_preset", "preset must be quick or standard.");
        JSONArray prepared = new JSONArray();
        for (String id : ids) {
            TaiModelSpec spec = manager.resolveModel(id);
            if (spec == null) return manager.error(404, "model_not_found", "Unknown model: " + id);
            if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
                return manager.error(400, "capability_not_supported", "Model " + id + " is not a chat model.");
            }
            if (spec.localPath == null || !new File(spec.localPath).exists()) {
                return manager.error(404, "model_file_missing", "Download or import " + id + " before benchmarking it.");
            }
            JSONObject entry = new JSONObject();
            entry.put("model", spec.id);
            entry.put(TaiManager.INTERNAL_MODEL_SPEC, spec.toJson());
            entry.put(TaiManager.INTERNAL_RUNTIME_OPTIONS, manager.settings.getRuntimeOptions(spec).toJson());
            prepared.put(entry);
        }
        JSONArray processors = null;
        Object requestedProcessors = request.opt("processors");
        if (requestedProcessors instanceof JSONArray) {
            processors = (JSONArray) requestedProcessors;
        } else if (requestedProcessors instanceof String && !((String) requestedProcessors).trim().isEmpty()) {
            processors = new JSONArray().put(((String) requestedProcessors).trim());
        }
        JSONObject body = new JSONObject();
        body.put("ok", true);
        body.put("models", prepared);
        body.put("preset", preset.id);
        body.put("processors", processors == null ? JSONObject.NULL : processors);
        body.put("compare", request.optBoolean("compare", false));
        body.put("eagle", request.optBoolean("eagle", false));
        body.put("force", request.optBoolean("force", false));
        return body;
    }

    /** The runtime-process half: the harness against the local router. */
    private void benchRunLocal(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        JSONObject request = manager.parseBody(body);
        TaiRuntimeState state = manager.localRuntime().getState();
        if (state.activeGeneration || "loading".equals(state.state)) {
            JSONObject busy = manager.error(409, "runtime_busy", "The runtime is " + ("loading".equals(state.state) ? "loading" : "generating")
                + "; wait for it to finish or cancel it before benchmarking.");
            if (state.loadedModelId != null) busy.put("loadedModelId", state.loadedModelId);
            emitBenchError(sink, busy);
            return;
        }
        if (manager.activeBench != null) {
            emitBenchError(sink, manager.error(409, "benchmark_running", "A benchmark is already running."));
            return;
        }
        TaiBenchSuite.Preset preset = TaiBenchSuite.Preset.fromId(request.optString("preset", null));
        if (preset == null) {
            emitBenchError(sink, manager.error(400, "bad_preset", "preset must be quick or standard."));
            return;
        }
        JSONArray models = request.optJSONArray("models");
        if (models == null || models.length() == 0) {
            emitBenchError(sink, manager.error(400, "no_models", "No models to benchmark."));
            return;
        }
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(manager.appContext);
        Map<String, TaiModelSpec> specs = new LinkedHashMap<>();
        Map<String, TaiRuntimeOptions> baseOptions = new LinkedHashMap<>();
        List<TaiBenchSuite.ModelInput> inputs = new ArrayList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) continue;
            String id = model.optString("model", "");
            TaiModelSpec spec = manager.resolveModel(model, id);
            if (spec == null) {
                emitBenchError(sink, manager.error(404, "model_not_found", "Unknown model: " + id));
                return;
            }
            TaiRuntimeOptions options = manager.runtimeOptionsFromRequest(model, spec);
            // The processor an automatic load would take is the default pick; the GPU is compared
            // only where the phone and this model's preflight allow it. An explicit --gpu is
            // planned regardless and refused, with the reason, by the load.
            boolean gpuSupported = device.supportsAccelerator("gpu")
                && !TaiLoadPreflight.evaluate(manager.appContext, spec, options.withAccelerator("gpu"), false).blocked;
            String best = TaiLoadPreflight.evaluate(manager.appContext, spec, options.withAccelerator("auto"), false).effectiveAccelerator;
            best = "gpu".equalsIgnoreCase(best) ? TaiBenchSuite.ACCELERATOR_GPU : TaiBenchSuite.ACCELERATOR_CPU;
            // A GPU-only file (the Gemma 4 -gpu/-web bundles) has no CPU graph; the comparison skips it.
            boolean cpuSupported = TaiModelProfile.forModel(spec).supports("cpu");
            boolean speculativeCapable = spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING);
            specs.put(spec.id, spec);
            baseOptions.put(spec.id, options);
            inputs.add(new TaiBenchSuite.ModelInput(spec.id, spec.backend, best, cpuSupported, gpuSupported, speculativeCapable));
        }
        List<String> processors = null;
        JSONArray requestedProcessors = request.optJSONArray("processors");
        if (requestedProcessors != null) {
            processors = new ArrayList<>();
            for (int i = 0; i < requestedProcessors.length(); i++) processors.add(requestedProcessors.optString(i, ""));
        }
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(inputs, request.optBoolean("compare", false), processors, request.optBoolean("eagle", false));
        // A restart after the runtime crashed asks only for the entries not yet finished.
        JSONArray skip = request.optJSONArray("skip");
        if (skip != null && skip.length() > 0) {
            Set<String> finished = new LinkedHashSet<>();
            for (int i = 0; i < skip.length(); i++) finished.add(skip.optString(i, ""));
            List<TaiBenchSuite.EntryPlan> remaining = new ArrayList<>();
            for (TaiBenchSuite.EntryPlan planned : entries) if (!finished.contains(planned.key())) remaining.add(planned);
            entries = remaining;
        }
        if (entries.isEmpty()) {
            emitBenchError(sink, manager.error(400, "no_entries", "Nothing to run: no model and processor pair to benchmark."));
            return;
        }
        JSONObject deviceJson = new JSONObject();
        deviceJson.put("soc", device.socModel == null ? JSONObject.NULL : device.socModel);
        deviceJson.put("ramClassGb", device.memoryBytes > 0L ? Math.round(device.memoryBytes / (double) (1024L * 1024L * 1024L)) : JSONObject.NULL);

        TaiDeviceConditions conditionsReader = new TaiDeviceConditions(manager.appContext);
        boolean force = request.optBoolean("force", false);
        if (!force) {
            TaiBenchGuardRules.Snapshot startSnapshot = conditionsReader.snapshot();
            String reason = TaiBenchGuardRules.startCheck(startSnapshot);
            if (reason != null) {
                JSONObject refusal = manager.error(409, "conditions_not_met", conditionsMessage(reason));
                refusal.put("reason", reason);
                refusal.put("batteryPercent", startSnapshot.batteryPercent >= 0 ? startSnapshot.batteryPercent : JSONObject.NULL);
                refusal.put("charging", startSnapshot.charging);
                refusal.put("thermalStatus", startSnapshot.thermalStatus >= 0 ? startSnapshot.thermalStatus : JSONObject.NULL);
                emitBenchError(sink, refusal);
                return;
            }
        }

        TaiBenchGuard guard = new TaiBenchConditionsGuard(conditionsReader::snapshot, System::currentTimeMillis);
        TaiBenchHarness harness = new TaiBenchHarness(preset, entries, new BenchHost(specs, baseOptions),
            guard, sink::onEvent, com.termux.BuildConfig.VERSION_NAME, deviceJson);
        manager.activeBench = harness;
        conditionsReader.startThermalListener(() -> harness.requestStop("thermal"));
        try {
            harness.run();
        } finally {
            conditionsReader.stopThermalListener();
            manager.activeBench = null;
        }
        sink.onDone();
    }

    /** The human message for a {@code conditions_not_met} refusal, by {@link TaiBenchGuardRules#startCheck} reason. */
    @NonNull
    private static String conditionsMessage(@NonNull String reason) {
        switch (reason) {
            case "battery_low":
                return "Battery is below " + TaiBenchGuardRules.START_BATTERY_MIN_PERCENT
                    + "%; plug in or charge before benchmarking, or pass force=true to run anyway.";
            case "too_hot":
                return "The phone is already warm; let it cool before benchmarking, or pass force=true to run anyway.";
            default:
                return "The phone is not in a state to start a benchmark.";
        }
    }

    /** The harness's window on this process: the router, the meter, the log, the stamps. */
    private final class BenchHost implements TaiBenchHarness.Host {
        /** Long enough for the {@code done} event to reach the app over the binder before the process goes. */
        private static final long ABANDON_KILL_DELAY_MS = 1_500L;

        @NonNull private final Map<String, TaiModelSpec> specs;
        @NonNull private final Map<String, TaiRuntimeOptions> baseOptions;
        @Nullable private String log;

        BenchHost(@NonNull Map<String, TaiModelSpec> specs, @NonNull Map<String, TaiRuntimeOptions> baseOptions) {
            this.specs = specs;
            this.baseOptions = baseOptions;
        }

        @NonNull
        private TaiModelSpec spec(@NonNull TaiBenchSuite.EntryPlan entry) {
            TaiModelSpec spec = specs.get(entry.modelId);
            if (spec == null) throw new IllegalStateException("No spec for " + entry.modelId);
            return spec;
        }

        /** The entry's processor and draft-model choice on top of the settings' options; thinking off. */
        @NonNull
        private TaiRuntimeOptions options(@NonNull TaiBenchSuite.EntryPlan entry, @Nullable Integer maxTokens, boolean greedy) {
            TaiRuntimeOptions base = baseOptions.get(entry.modelId);
            if (base == null) base = manager.settings.getRuntimeOptions(spec(entry));
            return base.withGenerationOverrides(maxTokens, greedy ? Integer.valueOf(1) : null, null,
                greedy ? Double.valueOf(0.0) : null, entry.accelerator, null, null, null, null,
                Boolean.FALSE, entry.speculative);
        }

        /** The same preflight, budget and history as {@code tai load}; a refusal skips the entry. */
        @NonNull
        @Override
        public JSONObject load(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            TaiModelSpec spec = spec(entry);
            TaiRuntimeOptions options = options(entry, null, false);
            TaiLoadPreflight.Result preflight = TaiLoadPreflight.evaluate(manager.appContext, spec, options, false);
            if (preflight.blocked) {
                if (TaiRuntimeHistory.isAcceleratorVerdict(preflight.errorCode)) {
                    TaiRuntimeHistory.recordFailure(manager.appContext, spec, preflight.device, spec.backend,
                        preflight.effectiveAccelerator, preflight.message);
                }
                return preflight.blockingError(manager.preflightStatusCode(preflight));
            }
            LoadDecision decision = manager.decideLoad(spec, options, preflight, true);
            if (decision.refusal != null) return decision.refusal;
            // The GPU self-test runs here as on every other load: a bench GPU load is a GPU load.
            JSONObject result = manager.loadWithCanary(spec, decision.options);
            if (result.optBoolean("ok", false)
                    && TaiGpuVerdict.REASON_FAILED.equals(result.optString("backendFallbackReason", ""))) {
                // The self-test failed and moved the model to the CPU: this entry is the GPU's, so it is skipped
                // rather than measured on the other processor under the GPU's name.
                manager.localRuntime().unload();
                return manager.error(409, "gpu_wrong_answers", TaiGpuVerdict.REASON_FAILED);
            }
            result.put("preflight", preflight.toJson());
            decision.describe(result);
            manager.recordRuntimeResult(spec, preflight, result);
            return result;
        }

        @NonNull
        @Override
        public JSONObject unload() throws JSONException {
            return manager.localRuntime().unload();
        }

        /** The chat model resident now, with the processor it runs on and what is left of its keep-warm. */
        @Nullable
        @Override
        public JSONObject residentChat() throws JSONException {
            TaiRuntimeState state = manager.localRuntime().getState();
            if (!state.loaded || state.loadedModelId == null) return null;
            JSONObject resident = new JSONObject().put("modelId", state.loadedModelId);
            MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
            TaiResidency.Entry entry = router == null ? null : router.residency().find(TaiResidency.Kind.CHAT, state.loadedModelId);
            if (entry != null) resident.put("accelerator", entry.accelerator);
            long warmLeftMs = state.keepWarmUntilMs - System.currentTimeMillis();
            if (state.keepWarmUntilMs > 0L && warmLeftMs > 0L) {
                resident.put("keepWarmMinutes", Math.max(1L, (warmLeftMs + 59_999L) / 60_000L));
            }
            return resident;
        }

        /** Loads it back the way {@code tai load} does (or {@code tai keep-warm}, when it was being kept warm). */
        @Override
        public void restoreChat(@NonNull JSONObject resident) throws JSONException {
            JSONObject request = new JSONObject().put("model", resident.optString("modelId", ""));
            String accelerator = resident.optString("accelerator", "");
            if (!accelerator.isEmpty()) request.put("accelerator", accelerator);
            int minutes = resident.optInt("keepWarmMinutes", 0);
            if (minutes > 0) {
                request.put("minutes", minutes);
                manager.keepWarmRuntime(request.toString());
            } else {
                manager.loadModel(request.toString());
            }
        }

        /**
         * A load that never returned is still running on a thread the runtime cannot stop, and the
         * weights it mapped stay mapped for as long as the process lives. Ending the process is the
         * only way to get them back, so it goes a moment after the run's {@code done} has been
         * sent. The app's client sees no request in flight, does not count it a crash, and binds a
         * fresh process on the next request.
         */
        @Override
        public void abandonRuntime() {
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(ABANDON_KILL_DELAY_MS);
                } catch (InterruptedException ignored) {
                }
                Process.killProcess(Process.myPid());
            }, "tai-bench-abandon");
            killer.setDaemon(true);
            killer.start();
        }

        @NonNull
        @Override
        public JSONObject chat(@NonNull TaiBenchSuite.EntryPlan entry, @NonNull String userPrompt, int maxTokens,
                               @NonNull TaiGenerationCallback callback) throws JSONException {
            // Greedy (top_k 1, temperature 0) for every phase: the numbers should not move with
            // the dice, and the check questions have one right answer. One-shot: every prompt gets
            // a fresh conversation, or LiteRT-LM would answer each one with the earlier phases'
            // passages and replies still in context (a check answered as the previous question).
            return manager.localRuntime().generate(spec(entry).id, TaiChatRequest.oneShot(TaiBenchSuite.SYSTEM_PROMPT, userPrompt),
                options(entry, maxTokens, true), callback);
        }

        @Override
        public void cancel() {
            try {
                manager.localRuntime().cancel();
            } catch (JSONException | RuntimeException ignored) {
            }
        }

        @NonNull
        @Override
        public TaiLoadMeter startMeter() {
            return TaiLoadMeter.start(manager.appContext);
        }

        @NonNull
        @Override
        public String longInputLog() throws IOException {
            if (log != null) return log;
            try (InputStream input = manager.appContext.getAssets().open(TaiBenchSuite.LONG_INPUT_ASSET);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                log = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            return log;
        }

        @Override
        public void clearMmapCache(@NonNull TaiBenchSuite.EntryPlan entry) {
            if (!TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) return;
            MnnTaiRuntime.clearMmapCache(manager.appContext, spec(entry).id);
        }

        /** {@code :tai_runtime}'s own PSS (this process, where the model is resident). */
        @Override
        public long processPssBytes() {
            try {
                long kb = android.os.Debug.getPss();
                return kb > 0L ? kb * 1024L : -1L;
            } catch (RuntimeException e) {
                return -1L;
            }
        }

        @NonNull
        @Override
        public JSONObject describe(@NonNull TaiBenchSuite.EntryPlan entry) throws JSONException {
            TaiModelSpec spec = spec(entry);
            JSONObject json = new JSONObject();
            json.put("displayName", spec.displayName);
            // The size the spec knows or the file's; nothing is hashed here — a multi-GB sha256
            // has no place inside a timed run, so the hash is whatever the download recorded.
            long bytes = TaiResidency.fileBytes(spec);
            json.put("sizeBytes", bytes > 0L ? bytes : spec.sizeBytes);
            json.put("sha256", spec.sha256 == null ? JSONObject.NULL : spec.sha256);
            json.put("runtimeVersion", TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)
                ? MnnTaiRuntime.RUNTIME_VERSION : com.termux.BuildConfig.LITERT_LM_VERSION);
            return json;
        }
    }

    /** An {@code error} event in the harness's shape from a manager error object. */
    @NonNull
    private static JSONObject benchErrorEvent(@NonNull JSONObject source) throws JSONException {
        JSONObject event = new JSONObject();
        event.put("event", "error");
        event.put("at", System.currentTimeMillis());
        event.put("code", source.optString("error", "benchmark_failed"));
        event.put("message", source.optString("message", "The benchmark could not start."));
        event.put("status", source.optInt("_statusCode", 500));
        if (source.has("loadedModelId")) event.put("loadedModelId", source.opt("loadedModelId"));
        // A conditions_not_met refusal attaches reason/batteryPercent/charging/thermalStatus; any
        // field beyond the standard shape travels through to the caller unchanged.
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("ok".equals(key) || "error".equals(key) || "message".equals(key)
                || "_statusCode".equals(key) || "loadedModelId".equals(key)) continue;
            event.put(key, source.get(key));
        }
        return event;
    }

    private void emitBenchError(@NonNull OpenAiStreamSink sink, @NonNull JSONObject source) throws JSONException, IOException {
        sink.onEvent(benchErrorEvent(source));
        sink.onDone();
    }

    /** Runs can each spend tens of seconds; the whole {@code tai benchmark} call may take minutes. */
    private static final long BENCHMARK_TIMEOUT_MS = 20 * 60_000L;

    private static final String BENCHMARK_PROMPT = "How are you";

    private static final long BYTES_PER_MB = 1024L * 1024L;

    /**
     * Runs LiteRT-LM's own {@code com.google.ai.edge.litertlm.benchmark()} in the runtime process, so
     * TAI's numbers are comparable 1:1 with Google AI Edge Gallery's Benchmark screen: same API, same
     * default prefill/decode token counts, its own throwaway engine and cache directory. That engine
     * is independent of the one {@link MultiBackendTaiRuntime} tracks, so it is refused while a chat
     * model is loaded or loading rather than silently doubling the memory a resident model already
     * holds — the caller unloads first, or passes {@code force} to accept that risk instead of the
     * memory-budget refusal below.
     */
    @NonNull
    public JSONObject benchmark(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        String modelId = manager.modelOf(request);
        TaiModelSpec spec = manager.resolveModel(request, modelId);
        if (spec == null) return manager.error(404, "model_not_found", "Unknown model: " + modelId);
        if (!spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) {
            return manager.error(400, "capability_not_supported", "Model " + modelId + " is not a chat model.");
        }
        if (!TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend)) {
            return manager.error(400, "backend_not_supported",
                "tai benchmark calls com.google.ai.edge.litertlm.benchmark(), which is LiteRT-LM only.");
        }
        TaiBenchmarkParams params = TaiBenchmarkParams.fromRequest(request);
        if (params == null) return manager.error(400, "bad_request", "accelerator must be gpu or cpu");
        if (manager.shouldDelegateRuntime()) return manager.runtimeRequest(TaiRuntimeIpc.OP_BENCHMARK, manager.delegatedRuntimeBody(body), BENCHMARK_TIMEOUT_MS);

        TaiRuntimeState state = manager.localRuntime().getState();
        if (state.loaded || state.activeGeneration || "loading".equals(state.state)) {
            JSONObject busy = manager.error(409, "model_loaded", "Unload the loaded model first: tai benchmark runs its "
                + "own LiteRT-LM engine and cannot share memory with a resident model.");
            if (state.loadedModelId != null) busy.put("loadedModelId", state.loadedModelId);
            return busy;
        }

        File modelFile = spec.localPath == null ? null : new File(spec.localPath);
        if (modelFile == null || !modelFile.isFile() || !modelFile.canRead()) {
            return manager.error(404, "model_file_not_readable", "Model file does not exist or is not readable: " + modelId);
        }

        if (!params.force) {
            TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(manager.appContext);
            long fileBytes = TaiResidency.fileBytes(spec);
            TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(spec.backend, fileBytes, false,
                device.physicalMemoryBytes, manager.availableMemory(device),
                Collections.singletonList(params.accelerator), params.prefillTokens + params.decodeTokens, null, 0)
                .withConditions(manager.gateConditions()));
            if (!plan.fits) {
                JSONObject refusal = TaiManager.insufficientMemory(spec.displayName, plan);
                refusal.put("hint", "Pass force=true to skip this budget check. Google AI Edge Gallery's Benchmark "
                    + "screen has no memory budget, and force is how tai benchmark stays comparable to it.");
                return refusal;
            }
        }

        File cacheDir = new File(manager.appContext.getCacheDir(), "tai-benchmark-" + System.nanoTime());
        if (!cacheDir.mkdirs()) {
            return manager.error(500, "benchmark_cache_dir_failed",
                "Could not create a benchmark cache directory under " + manager.appContext.getCacheDir());
        }
        try {
            return runBenchmark(spec, modelFile, params, cacheDir);
        } finally {
            deleteRecursively(cacheDir);
        }
    }

    @NonNull
    private JSONObject runBenchmark(@NonNull TaiModelSpec spec, @NonNull File modelFile,
                                     @NonNull TaiBenchmarkParams params, @NonNull File cacheDir) throws JSONException {
        Backend backend = TaiBenchmarkParams.ACCELERATOR_CPU.equals(params.accelerator)
            ? new Backend.CPU() : new Backend.GPU();
        JSONArray runsJson = new JSONArray();
        ValueSeries initMsSeries = new ValueSeries();
        ValueSeries ttftMsSeries = new ValueSeries();
        ValueSeries prefillTpsSeries = new ValueSeries();
        ValueSeries decodeTpsSeries = new ValueSeries();
        Double firstInitMs = null;
        for (int i = 0; i < params.runs; i++) {
            TaiLoadMeter sampler = TaiLoadMeter.start(manager.appContext);
            BenchmarkInfo info;
            try {
                info = BenchmarkKt.benchmark(modelFile.getAbsolutePath(), backend, null, null,
                    params.prefillTokens, params.decodeTokens, cacheDir.getAbsolutePath(),
                    Contents.Companion.of(BENCHMARK_PROMPT), null, null, null);
            } catch (Throwable t) {
                sampler.stop();
                String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                JSONObject failure = manager.error(500, "benchmark_failed", "Benchmark run " + (i + 1) + " failed: " + message);
                failure.put("run", i + 1);
                if (runsJson.length() > 0) failure.put("runs", runsJson);
                return failure;
            }
            sampler.stop();
            double initMs = info.getInitTimeInSecond() * 1000.0;
            double ttftMs = info.getTimeToFirstTokenInSecond() * 1000.0;
            double prefillTps = info.getLastPrefillTokensPerSecond();
            double decodeTps = info.getLastDecodeTokensPerSecond();
            // The first init reads the package off disk cold; later ones are warm. Averaging them
            // together would hide exactly the number Gallery's screen calls out separately.
            if (i == 0) firstInitMs = initMs; else initMsSeries.add(initMs);
            ttftMsSeries.add(ttftMs);
            prefillTpsSeries.add(prefillTps);
            decodeTpsSeries.add(decodeTps);
            JSONObject run = new JSONObject();
            run.put("initMs", initMs);
            run.put("ttftMs", ttftMs);
            run.put("prefillTps", prefillTps);
            run.put("decodeTps", decodeTps);
            run.put("prefillTokenCount", info.getLastPrefillTokenCount());
            run.put("decodeTokenCount", info.getLastDecodeTokenCount());
            run.put("availBeforeMb", sampler.beforeBytes() / BYTES_PER_MB);
            run.put("availMinMb", sampler.minBytes() / BYTES_PER_MB);
            runsJson.put(run);
        }
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("model", spec.id);
        data.put("accelerator", params.accelerator);
        data.put("prefillTokens", params.prefillTokens);
        data.put("decodeTokens", params.decodeTokens);
        data.put("runs", runsJson);
        JSONObject initSummary = new JSONObject();
        if (firstInitMs != null) initSummary.put("firstMs", firstInitMs);
        if (!initMsSeries.isEmpty()) {
            initSummary.put("laterAvgMs", initMsSeries.avg());
            initSummary.put("laterMinMs", initMsSeries.min());
            initSummary.put("laterMaxMs", initMsSeries.max());
        }
        JSONObject summary = new JSONObject();
        summary.put("initMs", initSummary);
        summary.put("ttftMs", ttftMsSeries.toJson());
        summary.put("prefillTps", prefillTpsSeries.toJson());
        summary.put("decodeTps", decodeTpsSeries.toJson());
        data.put("summary", summary);
        data.put("litertLmVersion", com.termux.BuildConfig.LITERT_LM_VERSION);
        return data;
    }

    /** avg/min/max/count over a run of values, the shape Gallery's own ValueSeries reports. */
    private static final class ValueSeries {
        private double sum;
        private double minValue = Double.NaN;
        private double maxValue = Double.NaN;
        private int count;

        void add(double value) {
            minValue = count == 0 ? value : Math.min(minValue, value);
            maxValue = count == 0 ? value : Math.max(maxValue, value);
            sum += value;
            count++;
        }

        boolean isEmpty() {
            return count == 0;
        }

        double avg() {
            return count == 0 ? 0.0 : sum / count;
        }

        double min() {
            return count == 0 ? 0.0 : minValue;
        }

        double max() {
            return count == 0 ? 0.0 : maxValue;
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("avg", avg());
            json.put("min", min());
            json.put("max", max());
            json.put("count", count);
            return json;
        }
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
