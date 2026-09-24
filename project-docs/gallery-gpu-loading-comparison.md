# GPU vs CPU loading: Google AI Edge Gallery vs. TAI

Assessment date: **2026-09-24**. Local checkout (termux-launcher, branch `dev`): **2f72f54fdb8f4474954825ac57e59793262c6350**. Gallery: shallow clone of `https://github.com/google-ai-edge/gallery`, commit **12512bd8b652fd671c82e3e80005f0d9be7f5d2a** (2026-09-23, app `versionName` 1.0.20). LiteRT-LM: shallow clone of `https://github.com/google-ai-edge/LiteRT-LM`, commit **449ae529a51e0eff02d87084ef0c38263a091d3e** (2026-09-23, `VERSION = "0.18.0"`). Both clones are read-only and live in the session scratchpad, not this repo. This is a research comparison, not an implementation plan. No code was changed, built, installed, or run on a device.

Only primary sources were used: Gallery source and allowlist JSON, LiteRT-LM source and docs, and this repo's code and plan notes. Two pinned versions matter here. Gallery HEAD pins `litertlm = "0.11.0"` (`Android/src/gradle/libs.versions.toml:23`). TAI pins `litertLmVersion = "0.14.0"` (`app/build.gradle:12`). LiteRT-LM behaviour below is read at HEAD (0.18.0). It was **not re-checked against 0.11.0 or 0.14.0**.

**Citation keys.** `G:` = `gallery@12512bd8:Android/src/app/src/main/java/com/google/ai/edge/gallery/`. `L:` = `LiteRT-LM@449ae529:`. `AL:` = `gallery@12512bd8:model_allowlists/1_0_19.json`. `T:` = `app/src/main/java/com/termux/ai/` in this repo. Permalink form: `https://github.com/google-ai-edge/gallery/blob/12512bd8b652fd671c82e3e80005f0d9be7f5d2a/<path>#L<n>` and `https://github.com/google-ai-edge/LiteRT-LM/blob/449ae529a51e0eff02d87084ef0c38263a091d3e/<path>#L<n>`.

## Summary of conclusions

1. **Gallery tries the model's first accelerator and relies on the OS.** Nothing in Gallery reads free memory before a load. Its only RAM check compares total RAM with the allowlist's `minDeviceMemoryInGb`, and a failed check opens a dialog with a "proceed anyway" button. A load that throws becomes an error dialog. There is **no automatic GPU→CPU fallback, no retry, no timeout, no crash marker and no failure memory**. The engine runs in the app's main process.
2. **Gallery's accelerator choice is a per-model ordered list in the allowlist JSON.** The first entry is the default and the user can pick another in the config dialog. Gemma 4 E2B/E4B default to **GPU** (`"gpu,cpu"`). Gemma 3n E2B/E4B default to **CPU** (`"cpu,gpu"`). The only device detection strips GPU on Pixel 10.
3. **Gallery sizes GPU and CPU loads with the same context.** Its `maxTokens` goes straight into `EngineConfig.maxNumTokens`, the total KV-cache budget, whatever the backend. The value is 4000 for Gemma 4 and 4096 for Gemma 3n. LiteRT-LM's own default, used when `maxNumTokens` is unset, caps GPU at 4096 on non-Apple platforms.
4. **TAI's GPU estimate is not what makes it conservative. The reserve is.** For E4B at 4k, TAI estimates 3.53 GB (text only). The measured MemAvailable drop was 2.4–4.2 GB. With the 1.77 GB reserve on an 11 GiB phone, a GPU load needs about 5.3 GB free, so at 4.5–5.0 GB free TAI picks CPU. Gallery would load on GPU there. By this repo's own measurements, lmkd would then likely evict cached apps (inferred).
5. **TAI's CPU estimate is the one that is far off.** It is 4.25 GB for E4B at 16k, while the plan measured a 0.45 GB MemAvailable drop. That gap does not make GPU refusals too strict, but it does leave the ladder's CPU sizing unmeasured.
6. **The best thing to take from Gallery is its willingness to try GPU, not its lack of protection.** TAI can already cancel a load in under a second (plan phase 1). That makes a guarded "try GPU, watch MemAvailable, cancel and fall back" possible in TAI, which Gallery cannot do.
7. **TAI's defaults for Gallery's models match the allowlist, with gaps.** Gemma 3n and Gemma3-1B have no profile, so an imported file becomes CPU-only with 1024 tokens. Gallery's `maxTokens` is a total context budget, but TAI stores it as an output cap. The profile source label still says 1.0.15. The values have not changed through 1_0_19.

## Gallery's behaviour

### Where the accelerator is chosen

- **Allowlist field.** Each model's `defaultConfig.accelerators` is a comma-separated string (`G:data/ModelAllowlist.kt:27-36`). It is parsed in order into `accelerators` (`:116-136`). The fallback when the field is absent is `DEFAULT_ACCELERATORS = listOf(Accelerator.GPU)` (`G:data/Consts.kt:48`).
- **Order means default.** "The accelerators this model is compatible with, in preference order. The first entry is the one the model runs on by default" (`G:data/BackendSpec.kt:25-26`). `defaultAccelerator = accelerators.firstOrNull()` (`:53-54`).
- **User setting.** An `ACCELERATOR` segmented button is added to the model's config dialog with the first accelerator as its default (`G:data/Config.kt:404-408`). `Model.currentAccelerator` returns the user's value if set, otherwise the backend default (`G:data/Model.kt:195-200`). A change triggers a forced re-initialization (`G:ui/common/ModelPageAppBar.kt:279-292`). Config values live on the in-memory `Model` and are seeded from the defaults in `preProcess()` (`G:data/Model.kt:249-255`). No persistence of the accelerator choice was found by grepping the data store and view model. *Inferred:* the choice resets on the next app launch.
- **Device detection.** GPU is removed on Pixel 10 (`G:data/ModelAllowlist.kt:129-132`, with `isPixel10()` at `G:common/Utils.kt:366-368`). On Pixel devices "npu" is relabelled "tpu" (`:118-121`). No GPU capability probe exists (no OpenCL check before load). The manifest only declares `libOpenCL.so`/`libvndksupport.so` as optional native libraries (`Android/src/app/src/main/AndroidManifest.xml:163-164`), as LiteRT-LM's docs require (`L:docs/api/kotlin/getting_started.md:101-110`).
- **Mapping to LiteRT-LM.** `preferredBackend = when (model.currentAccelerator ?: Accelerator.GPU)` → `Backend.CPU()`/`Backend.GPU()`/`Backend.NPU(nativeLibraryDir)` (`G:ui/llmchat/LlmChatModelHelper.kt:122-128`).
- **Defaults per model** (`AL:`): Gemma-4-E2B-it `"gpu,cpu"` (`:20`), Gemma-4-E4B-it `"gpu,cpu"` (`:66`), Gemma-3n-E2B-it `"cpu,gpu"` (`:110`), Gemma-3n-E4B-it `"cpu,gpu"` (`:130`). The full table is in [Official defaults](#official-defaults-for-the-tai-importer).
- **Imported (unknown) models** default to **CPU**. The import dialog offers `CPU, GPU, NPU` (Pixel 10: `CPU, NPU`) with the first one preselected (`G:ui/modelmanager/ModelImportDialog.kt:97-103`, `G:data/Config.kt:193-199`). An import with no accelerator falls back to `listOf(Accelerator.CPU)` (`G:ui/modelmanager/ModelManagerViewModel.kt:1529-1541`).
- **Which allowlist is live.** The app fetches `model_allowlists/<versionName>.json` from GitHub `main` and falls back to the last copy saved on disk (`G:ui/modelmanager/ModelManagerViewModel.kt:102-103, 1255-1270, 1823-1825`). At this commit `versionName` is 1.0.20 (`Android/src/app/build.gradle.kts:40`), but the newest file in the repo is `1_0_19.json`. *Inferred:* a 1.0.20 build falls back to its disk copy until `1_0_20.json` is published. The root `model_allowlist.json` is a legacy MediaPipe `.task` list and is not what the Android app fetches.

### Memory checks before loading

- **Only a total-RAM check.** `isMemoryLow` compares `MemoryInfo.totalMem` (on API 34+, `advertisedMem`) against `model.minDeviceMemoryInGb` (`G:ui/common/MemoryWarning.kt:51-72`). Free memory (`availMem`, `lowMemory`) is never read.
- **Soft gate only.** It runs when a model is picked (`G:ui/common/ModelPicker.kt:102-108`) or when the download/try button is pressed (`G:ui/common/DownloadAndTryButton.kt:324-330`). It shows "The model you've selected may exceed your device's memory, which can cause the app to crash…" with a **"proceed anyway"** button (`G:ui/common/MemoryWarning.kt:36-48`; `Android/src/app/src/main/res/values/strings.xml:135-137`).
- **Hugging Face browser.** A file larger than half of total RAM is shown as disabled (`G:huggingface/HfModelUtils.kt:96-106`, applied at `G:ui/modelmanager/HfModelDetailsSheet.kt:134-145`).
- **No context sizing from memory.** `maxTokens` comes from the config, or `DEFAULT_MAX_TOKEN = 1024` (`G:ui/llmchat/LlmChatModelHelper.kt:106-107`, `G:data/Consts.kt:43`). Nothing adjusts it to device RAM or free memory.
- **When the device is too small**, the app does nothing beyond that dialog. *Inferred:* after "proceed anyway" the load either throws (and gets the error dialog below) or the process is killed by the OS. Gallery has no code for the second case.

### GPU failure handling, timeouts, isolation

- **No fallback, no retry.** `Engine(engineConfig)` + `engine.initialize()` run inside one `try`. On `Exception` the status is marked failed with the message and `onDone(errorMsg)` returns (`G:ui/llmchat/LlmChatModelHelper.kt:173-174, 210-214`). Error text is only trimmed of MediaPipe trace noise (`G:common/Utils.kt:76-82`). The screen shows the message in an `ErrorDialog` when the status is `Failed` (`G:ui/common/chat/ChatPanel.kt:314, 746-760`; also `G:ui/navigation/GalleryNavGraph.kt:524-527`).
- **No failure memory.** The only per-backend record is an in-memory `initializedBackends` set, which chooses the "first initialization" loading screen (`G:ui/modelmanager/ModelManagerViewModel.kt:240-245, 760-761`; `G:ui/common/chat/ChatPanel.kt:603-624`). That screen reads "Sit tight, this can take up to 1 minute" (`strings.xml:29-30`). Failures are not persisted.
- **No timeout, no crash recovery.** Grepping the app source for `withTimeout`, `UncaughtExceptionHandler`, or any crash marker finds nothing. LiteRT-LM's `initialize()` is a synchronous JNI call with no timeout parameter (`L:kotlin/java/com/google/ai/edge/litertlm/Engine.kt:61-96`). The docs say only that it "can take a significant amount of time (e.g., up to 10 seconds)… call this on a background thread" (`L:docs/api/kotlin/getting_started.md:77-79`).
- **No process isolation.** The manifest's services are WorkManager's foreground service, Firebase, and FCM (`AndroidManifest.xml:119-146`). No `android:process` attribute exists. The engine runs in the UI process, on `Dispatchers.Default`/`IO` (`G:ui/llmchat/LlmChatTaskModule.kt:109-119`, `G:ui/modelmanager/ModelManagerViewModel.kt:784-793`). No `onTrimMemory`/`ComponentCallbacks2` handler exists in the app source.
- **Inside LiteRT-LM** the only automatic GPU→CPU fallback found is for the **sampler**, used when the GPU sampler library is unavailable (`L:runtime/components/sampler_factory.cc:763-783`). None was found for the main executor (grep for "fall back to cpu"/"fallback" in `runtime`, `kotlin`, `c`).

### Context window / max tokens, caches

- **The same value on GPU and CPU.** `EngineConfig(maxNumTokens = maxTokens, …)` (`G:ui/llmchat/LlmChatModelHelper.kt:131-143`). LiteRT-LM defines `maxNumTokens` as "the maximum number of the sum of input and output tokens. It is equivalent to the size of the kv-cache" (`L:kotlin/java/com/google/ai/edge/litertlm/Config.kt:189-190`). Gallery's "Max tokens" is therefore a **context window**, not an output cap.
- **Slider range.** When the allowlist gives `maxContextLength`, the setting becomes a slider from 2000 to `maxContextLength`, otherwise a fixed label (`G:data/Config.kt:387-397`). Gemma 4 gets 2000–32000 with a default of 4000 (`AL:18-19, 64-65`).
- **The engine's own default, when `maxNumTokens` is unset.** If the model metadata has a `max_num_tokens`, that value is used, **but on GPU (non-Apple) anything above 4096 is cut to the next multiple of 4096 above the prompt**. The comment reads "Metal does not constraint the max allocated GPU buffer size" (`L:runtime/engine/engine_settings.cc:320-339`). Gallery always sets the value, so this path does not apply to it. It does show that the runtime's authors treat >4k on GPU (OpenCL) as unsafe by default.
- **Cache directory.** Gallery passes `cacheDir` only when the model lives under `/data/local/tmp`, and then uses `getExternalFilesDir(null)`. Otherwise it passes `null` (`G:ui/llmchat/LlmChatModelHelper.kt:139-142`). With `null`, LiteRT-LM "uses the directory of the [modelPath]"; `":nocache"` disables caching (`L:kotlin/java/com/google/ai/edge/litertlm/Config.kt:193-195`). Gallery's default model storage is `getExternalFilesDir(null)` (on Pixel 11 CD1A builds, `filesDir`) (`G:common/Utils.kt:378-385`).
- **What is cached.** On GPU the ML Drift **program cache** is `<model>_<id>_mldrift_program_cache.bin` plus a **GPU weight cache** (`L:runtime/executor/executor_settings_base.h:184-187`, `executor_settings_base.cc:296-307`). Both go through `SetSerializationDir`/`SetSerializeProgramCache(true)`/`SetSerializeExternalTensors(true)` (`L:runtime/executor/litert_compiled_model_executor_utils.cc:971-1030`). On CPU there is an XNNPack weight cache (`.xnnpack_cache`, `:941-969`). Cache names include a content identifier, and stale caches are deleted when the current one is missing (`L:runtime/executor/executor_settings_base.cc:331-366`). The docs note that a writable dir "can improve 2nd load time" (`L:docs/api/kotlin/getting_started.md:89-90`). The GPU options also set `SetConvertWeightsOnGpu(true)` and `SetMadviseOriginalSharedTensors(true)` (`L:runtime/executor/litert_compiled_model_executor_utils.cc:934-937`). *Inferred:* the madvise call is what lets the kernel drop the mmapped originals after upload, which fits the large MemAvailable drop on GPU with small PSS growth recorded in the TAI plan.

### Vision and audio backends

- **Vision** uses `model.currentVisionAccelerator`, which comes from the allowlist's `visionAccelerator` (default `DEFAULT_VISION_ACCELERATOR = GPU`, `G:data/Consts.kt:49`) and is independent of the main backend (`G:ui/llmchat/LlmChatModelHelper.kt:112-119`; `G:data/Model.kt:208-213`). The `VISION_ACCELERATOR` config key exists (`G:data/Config.kt:111-112`), but `createLlmChatConfigs` never adds it to the dialog (`:398-409`). In practice vision always runs on the allowlist value, i.e. GPU for Gemma 3n on a CPU main backend too.
- **Audio** is hard-coded: `audioBackend = if (shouldEnableAudio) Backend.CPU() else null // must be CPU for Gemma 3n`. The vision line carries "must be GPU for Gemma 3n" (`G:ui/llmchat/LlmChatModelHelper.kt:136-137`). The allowlist's `audioAccelerator` is parsed into `BackendSpec.audioAccelerator` (`G:data/ModelAllowlist.kt:139, 227`) but the helper never reads it (grep).
- **When the encoders load.** An encoder is created only when the task asks for it. The AI Chat task passes `supportImage = model.supportImage, supportAudio = model.supportAudio` (`G:ui/llmchat/LlmChatTaskModule.kt:110-118`), which flows into `initialize` (`G:agent/DefaultAgentRuntimeExecutor.kt:106-116`). **Gemma 4 chat in Gallery therefore loads both encoders.** Ask Image passes image only (`G:ui/llmchat/LlmChatTaskModule.kt:273-281`), and Ask Audio passes audio only (`:365-374`). LiteRT-LM's sample config uses the same split: main CPU/GPU, `visionBackend = Backend.GPU()`, `audioBackend = Backend.CPU()` (`L:docs/api/kotlin/getting_started.md:243-249`).

### Unload, idle and residency

- **Unload is tied to navigation.** It happens when the user leaves a task screen, for every model of the task (`G:ui/common/chat/ChatView.kt:174-184`), or unless a custom task sets `keepModelAlive`, which defaults to `false` and is overridden by no task (`G:ui/navigation/GalleryNavGraph.kt:300-317`; `G:customtasks/common/CustomTask.kt:70-75`). Picking another model cleans up the previous one first (`G:ui/navigation/GalleryNavGraph.kt:549-561`). A re-init also cleans up before it initializes (`G:ui/modelmanager/ModelManagerViewModel.kt:750-751`).
- **Close.** `conversation.close()` then `engine.close()` (`G:ui/llmchat/LlmChatModelHelper.kt:279-305`). An unload that arrives during init is deferred with `cleanUpAfterInit` (`G:ui/modelmanager/ModelManagerViewModel.kt:763-768, 826-834`).
- **No idle timer, no trim-memory unload.** Grep finds neither.
- **Residency.** Each `Model` holds its own `instance`, and `initialize` skips a model that is already initialized (`G:ui/llmchat/LlmChatModelHelper.kt:98-103`). Nothing enforces one resident engine app-wide. *Inferred:* the navigation cleanup above means only one task's model is resident at a time in normal use.

## Side-by-side

| Axis | Gallery | TAI |
| --- | --- | --- |
| Accelerator choice | Ordered per-model list in the allowlist. The first entry is the default; the user can override in the config dialog (in memory only, inferred). Pixel 10 drops GPU. Imports default to CPU. (`G:data/ModelAllowlist.kt:116-136`, `G:data/BackendSpec.kt:25-26,53-54`, `G:data/Model.kt:195-200`, `G:ui/modelmanager/ModelImportDialog.kt:97-103`) | Profile's `compatibleAccelerators` in order, filtered by device support. Accelerators with a recorded failure move to the end. A persisted user setting (Auto/GPU/CPU) wins. (`T:TaiLoadPreflight.java:52-92`, `T:TaiModelProfile.java:104-131`, `T:TaiSettings.java:390-406,460`) |
| Memory check | `totalMem`/`advertisedMem` against `minDeviceMemoryInGb`: a warning dialog with "proceed anyway". Free memory is never read. (`G:ui/common/MemoryWarning.kt:51-72`) | Hard block below 512 MiB free or on `lowMemory`, and an auto-load blocks on a RAM-recommendation warning (`T:TaiLoadPreflight.java:271-295,338-344`). Then `TaiLoadBudget`: estimate against `availMem − max(1.5 GiB, 15% RAM)`, crediting the resident chat model (`T:TaiLoadBudget.java:59-77,147-175`; `T:TaiManager.java:1313-1351`). RAM class from `totalMem`, **not** `advertisedMem` (`T:TaiDeviceCapabilities.java:37-46,109-124`). |
| GPU failure fallback | None: an error dialog (`G:ui/llmchat/LlmChatModelHelper.kt:210-214`) | Auto mode: GPU exception → record failure → CPU in the same load (`T:LiteRtTaiRuntime.java:616-637`). The budget ladder also moves to CPU at the 4k floor when GPU does not fit (`T:TaiLoadBudget.java:157-172`). |
| Failure memory | None (in-memory "first init" set only, for UI) | Persisted per base model + device (manufacturer, model, SoC, SDK, ABIs) + accelerator. A later success overwrites it; there is no expiry and no runtime version in the key (`T:TaiRuntimeHistory.java:109-132,156-168`). Explicit GPU on a failed pair is blocked (`T:TaiLoadPreflight.java:327-336`). The crash marker halves the window for the crashed accelerator (`T:TaiLoadBudget.java:160-164`; `T:TaiManager.java:1330-1336`). |
| Process isolation | Engine in the UI process; no `android:process` (`AndroidManifest.xml:119-146`) | `:tai_runtime` process (`AndroidManifest.xml:364`), crash marker around each native init (`T:LiteRtTaiRuntime.java:876-887`) |
| Pressure handling | None | A 2 s watch while loaded; unloads everything on `lowMemory` (`T:TaiRuntimeService.java:58,242-284`) |
| Context sizing | `maxTokens` = `maxNumTokens`, the same on GPU/CPU (4000 for Gemma 4, 4096 for Gemma 3n) (`G:ui/llmchat/LlmChatModelHelper.kt:138`, `AL:19,65,109,129`) | RAM tier cap 4k/8k/16k/32k (`T:TaiContextWindowPolicy.java:23-29`). The budget halves the window to fit, down to a 4k floor. The second accelerator gets the floor only (`T:TaiLoadBudget.java:148-170`). |
| Load cancellation | None | `cancel` interrupts a load (plan phase 1: `model_load_cancelled` at 735 ms, `project-docs/plans/tai-memory-manager.md:136-140`) |
| Caches | `cacheDir` = null (next to the model in external files dir), or `getExternalFilesDir` for `/data/local/tmp` (`G:ui/llmchat/LlmChatModelHelper.kt:139-142`) | Same rule, but `getCacheDir()` for `/data/local/tmp` (`T:LiteRtTaiRuntime.java:833-834`). Models live in `filesDir/tai/models/<id>` (`T:TaiModelStore.java:70-72`), so the program cache is written next to them. |
| Vision / audio backend | Vision from the allowlist (GPU default, not user-exposed); audio hard-coded CPU; encoders loaded when the task wants them, and chat wants both (`G:ui/llmchat/LlmChatModelHelper.kt:112-137`, `G:ui/llmchat/LlmChatTaskModule.kt:114-115`) | Vision GPU unless the user forced CPU, the profile/device lacks GPU, or GPU failed before; audio CPU (`T:LiteRtTaiRuntime.java:846-855,889-913`). The canonical id loads text only; `-vision`/`-audio` ids add encoders (`T:TaiModelVariants.java:100-115`). |
| Idle / unload | Navigation-driven; no idle timer | Idle unload, 10 min by default (`T:TaiSettings.java:216-217`; `T:LiteRtTaiRuntime.java:1036-1043`); keep-warm; low-memory release |
| Residency | One `instance` per `Model` object; one per screen in practice (inferred) | One chat model per router plus the embedding runtimes, tracked in `TaiResidency` with a 330 MB runtime baseline (`T:TaiResidency.java:42-47,209-216`) |

## Does TAI's budget make GPU too conservative?

**TAI's numbers for E4B.** Computed from `TaiLoadBudget.estimateBytes` with `sizeBytes = 3,659,530,240` (`T:TaiModelCatalog.java:131-136`). The reserve assumes pong's `totalMem` is about 11 GiB, as the developer's 2026-09-23 session note records. That is an assumption, not a reading taken for this note.

| Load | Estimate (text / with encoders) | Free memory needed (+1.77 GB reserve) |
| --- | ---: | ---: |
| GPU 4k | 3.53 / 3.90 GB | 5.31 / 5.67 GB |
| GPU 8k | 4.32 / 4.69 GB | 6.09 / 6.46 GB |
| CPU 4k | 1.89 / 2.25 GB | 3.66 / 4.02 GB |
| CPU 16k | 4.25 / 4.62 GB | 6.03 / 6.39 GB |

**What was measured on pong** (`project-docs/plans/tai-memory-manager.md:66-80`):

| Load | MemAvailable drop |
| --- | ---: |
| GPU 4k | 2.4–4.2 GB (4 runs, ±0.9 GB noise) |
| CPU 16k | 0.45 GB |

The budget's own calibration note says "2.5–3.2 GB at 4k" for GPU and "1.6 GB at 4k" for CPU (`T:TaiLoadBudget.java:23-27`).

**What follows.**

- **The GPU estimate is not inflated.** 3.53 GB sits inside the measured 2.4–4.2 GB range, below the worst run.
- **What sends E4B to CPU at 4.5–5.0 GB free is the reserve.** Without the reserve, a GPU 4k load would fit on paper, leaving about 1.0–1.5 GB free. The worst measured run (4.2 GB) would leave 0.3–0.8 GB.
- **Gallery at the same point** would start the GPU load: there is no free-memory check, the context is 4000 on the same backend, and in chat both encoders load. Its only protection is whatever lmkd and the OS do.
- **What that costs** is recorded only in the developer's 2026-09-23 session note, not in a repo doc. A GPU 8k E4B load in TAI reached a low of 2.3 GB free while lmkd evicted 16 cached apps and thrashing peaked at 12%. At 32k the same phone froze. *Inferred:* a Gallery-style GPU load that starts at 4.5–5.0 GB free lands in that eviction regime or worse. It is survivable for a foreground app nobody else depends on. For a home screen that is also hosting Termux sessions, it is the failure TAI's budget was written to prevent (`T:TaiLoadBudget.java:13-18`).
- **Why MemAvailable may understate headroom** (*inferred*, not measured): it does not count the anonymous memory of cached background apps that lmkd can kill. So some "free after eviction" headroom exists that neither side measures. Gallery gets it by accident. TAI gives it up by design.
- **The CPU estimate is the part that looks too high.** 4.25 GB estimated against 0.45 GB measured at 16k. *Inferred:* that measurement may undercount because the KV cache fills lazily (a 32k CPU load did reach 4.7 GB RSS per the session note). Either way it is unmeasured, and the ladder's CPU sizing rests on it.

**Verdict.** At most the budget is modestly conservative for GPU. Its GPU number matches measurement, and the reserve is a policy choice rather than an estimation error. Gallery provides no evidence that a GPU load at 4.5–5.0 GB free is safe. Gallery just has no mechanism to notice when it is not.

## What TAI could adopt

Ranked by expected value. Each item names its evidence.

1. **Try GPU with a guard.** At the margin (GPU fits against a smaller reserve, e.g. 1.0 GiB, but not the full one), start the GPU load and sample MemAvailable every 100 ms (plan §3 already specifies the sampler). If free memory crosses the full reserve before init returns, cancel and fall back to CPU at the floor. Evidence:
   - Gallery's default is plainly "first accelerator, no check" (`G:ui/llmchat/LlmChatModelHelper.kt:122-128,173-174`).
   - TAI's load cancel works within about 0.7 s (`project-docs/plans/tai-memory-manager.md:140`).
   - The GPU footprint builds over seconds, not instantly: free memory fell from 5.7 to 1.4 GB over 12 s at 32k (`T:TaiLoadBudget.java:14-17`).
   - *Not verified:* whether cancelling a LiteRT-LM GPU init returns the GPU buffers promptly.
2. **Run the CPU fallback at the floor after a GPU exception.** Right now `LiteRtTaiRuntime` retries CPU with the *same* `options`, including the context window the plan chose for GPU (`T:LiteRtTaiRuntime.java:624-636`). The budget states that "a CPU fallback runs at the floor only" because a larger CPU window "took the phone's network down" (`T:TaiLoadBudget.java:34-37`). The runtime path does not apply that rule.
3. **Make GPU failure memory forget.** Any GPU exception is recorded (`T:LiteRtTaiRuntime.java:626-629`) and permanently moves GPU behind CPU in auto mode (`T:TaiLoadPreflight.java:80-91`). Only a successful GPU load clears it, and auto never tries GPU again. The key has no LiteRT-LM or app version (`T:TaiRuntimeHistory.java:156-168`). The preflight already avoids recording low-memory blocks for this reason (`T:TaiManager.java:466-471`), but an OOM-like exception thrown during init is still recorded. Gallery keeps no memory at all. Two middle grounds: add the runtime version to the key, and expire failures after a set time or a set number of CPU loads.
4. **Cap auto GPU at 4096 tokens.** Only an explicit setting would go above it. LiteRT-LM's own default caps non-Apple GPU at 4096 (`L:runtime/engine/engine_settings.cc:329-336`). Gallery ships Gemma 4 at 4000 (`AL:19,65`). The 8k GPU load is the one that evicted 16 apps (session note). TAI's tier currently hands GPU the RAM-tier cap up to 32k, which the budget then shrinks (`T:TaiContextWindowPolicy.java:23-29`, `T:TaiLoadBudget.java:159`).
5. **Measure the CPU footprint** (plan phase 3) before trusting the CPU side of the ladder. The CPU 16k estimate is about 9× the one measurement (table above).
6. **Keep the text-only canonical id, but fix its comment.** TAI's comment says the canonical id loads text-only "(Gallery's chat task)" (`T:TaiModelVariants.java:107-109`). Current Gallery chat loads both encoders (`G:ui/llmchat/LlmChatTaskModule.kt:114-115`). The TAI choice is still the cheaper one: about 0.3 GiB saved per the session note. This means Gallery's memory experience with Gemma 4 chat includes the encoders, and TAI's does not.
7. **Tell the user that the first GPU load takes longer.** Gallery shows "can take up to 1 minute" on the first init per backend (`G:ui/modelmanager/ModelManagerViewModel.kt:240-245`; `strings.xml:30`). This is when the ML Drift program cache is built (`L:runtime/executor/litert_compiled_model_executor_utils.cc:1009-1030`).
8. **Use a persistent cache dir for `/data/local/tmp` models.** Use `getExternalFilesDir` or `filesDir`, as Gallery does, instead of `getCacheDir()` (`T:LiteRtTaiRuntime.java:833-834` vs `G:ui/llmchat/LlmChatModelHelper.kt:139-142`). *Inferred:* the system can clear `cacheDir`, which forces a GPU program recompile. This matters only for developer side-loads.

Not worth copying:

- Gallery's `advertisedMem` RAM check (`G:ui/common/MemoryWarning.kt:59-62`). TAI already documents why `advertisedMem` overstates RAM on RAM Booster phones (`T:TaiLoadBudget.java:211-216`).
- Running the engine in the UI process.

## Official defaults (for the TAI importer)

### Gallery allowlist 1_0_19

Values come from `AL:`, the file the 1.0.19 app fetches. The same values appear unchanged in `1_0_15` through `1_0_18` (checked by parsing each file). "Tasks" are Gallery task types. Where `visionAccelerator` is absent, the code default GPU applies (`G:data/Consts.kt:49`). A model with `maxContextLength` gets a 2000–max slider, otherwise the value is fixed (`G:data/Config.kt:387-397`).

| Model (AL lines) | File, size, commit | Accel. order | maxTokens (= KV) / max context | topK / topP / temp | Min RAM | Image / audio | Capabilities |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Gemma-4-E2B-it (`:3-48`) | `gemma-4-E2B-it.litertlm`, 2,588,147,712 B, `6e5c4f1e395deb959c494953478fa5cec4b8008f` (older updatable: `7fa1d784…`) | gpu, cpu; vision gpu | 4000 / 32000 | 64 / 0.95 / 1.0 | 8 GB | yes / yes | `llm_thinking`, `speculative_decoding` (MTP) |
| Gemma-4-E4B-it (`:49-94`) | `gemma-4-E4B-it.litertlm`, 3,659,530,240 B, `28299f30ee4d43294517a4ac93abd6163412f07f` (older: `9695417f…`) | gpu, cpu; vision gpu | 4000 / 32000 | 64 / 0.95 / 1.0 | 12 GB | yes / yes | `llm_thinking`, `speculative_decoding` |
| Gemma-3n-E2B-it (`:95-114`) | `gemma-3n-E2B-it-int4.litertlm`, 3,655,827,456 B, `ba9ca88da013b537b6ed38108be609b8db1c3a16` | **cpu**, gpu; vision gpu (default) | 4096 / — | 64 / 0.95 / 1.0 | 8 GB | yes / yes | — |
| Gemma-3n-E4B-it (`:115-133`) | `gemma-3n-E4B-it-int4.litertlm`, 4,919,541,760 B, `297ed75955702dec3503e00c2c2ecbbf475300bc` | **cpu**, gpu; vision gpu (default) | 4096 / — | 64 / 0.95 / 1.0 | 12 GB | yes / yes | — |
| Gemma3-1B-IT (`:134-151`) | `gemma3-1b-it-int4.litertlm`, 584,417,280 B, `42d538a932e8d5b12e6b3b455f5572560bd60b2c` | gpu, cpu | 1024 / — | 64 / 0.95 / 1.0 | 6 GB | no / no | — |
| Qwen2.5-1.5B-Instruct (`:152-168`) | `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`, 1,597,931,520 B, `19edb84c69a0212f29a6ef17ba0d6f278b6a1614` | gpu, cpu | 4096 / — | **20 / 0.8 / 0.7** | 6 GB | no / no | — |
| DeepSeek-R1-Distill-Qwen-1.5B (`:169-185`) | `DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm`, 1,833,451,520 B, `e34bb88632342d1f9640bad579a45134eb1cf988` | gpu, cpu | 4096 / — | 64 / 0.95 / 1.0 | 6 GB | no / no | — |
| TinyGarden-270M (FunctionGemma ft) (`:186-207`) | `tiny_garden_q8_ekv1024.litertlm`, 288,964,608 B, `c205853ff82da86141a1105faa2344a8b176dfe7` | **cpu only** | 1024 / — | 64 / 0.95 / **0.0** | 6 GB | no / no | task `llm_tiny_garden` |
| MobileActions-270M (FunctionGemma ft) (`:208-229`) | `mobile_actions_q8_ekv1024.litertlm`, 288,964,608 B, `38942192c9b723af836d489074823ff33d4a3e7a` | **cpu only** | 1024 / — | 64 / 0.95 / **0.0** | 6 GB | no / no | task `llm_mobile_actions` |

Missing from every Android allowlist file (grep `embedding|qwen3`): **EmbeddingGemma, Qwen3, and base FunctionGemma**. Gallery publishes no official defaults for these. Thinking and speculative decoding default to **off** even where supported (`G:data/Config.kt:412-419`; speculative decoding is also gated on the file's `Capabilities.hasSpeculativeDecodingSupport()`, `G:ui/llmchat/LlmChatModelHelper.kt:145-170`).

### Code defaults

- **Gallery.** `DEFAULT_MAX_TOKEN = 1024`, `DEFAULT_TOPK = 64`, `DEFAULT_TOPP = 0.95`, `DEFAULT_TEMPERATURE = 1.0`, `DEFAULT_ACCELERATORS = [GPU]`, `DEFAULT_VISION_ACCELERATOR = GPU` (`G:data/Consts.kt:43-49`). Slider bounds: max tokens 100–4096 when no `maxContextLength`, topK 1–100, topP 0–1, temperature 0–2 (`G:data/Config.kt:23-31`).
- **Gallery imported model.** Accelerators as ticked, default CPU. maxTokens 1024 (fixed label, no slider, since there is no `maxContextLength`). topK/topP/temp 64/0.95/1.0. Image, audio, thinking and speculative decoding all off (`G:data/Config.kt:172-201`; `G:data/ModelUtils.kt:71-154`; `G:ui/modelmanager/ModelManagerViewModel.kt:1528-1600`). No min-RAM field for imports.
- **LiteRT-LM Kotlin.**
  - `EngineConfig`: `backend = Backend.CPU()`, `visionBackend`/`audioBackend` = null (encoder not initialized), `maxNumTokens` = null (from the model/engine), `cacheDir` = null (the model's directory) (`L:kotlin/java/com/google/ai/edge/litertlm/Config.kt:180-222`).
  - `SamplerConfig(topK, topP, temperature, seed = 0)` has no defaults for the first three (`:295-306`). `ConversationConfig.samplerConfig = null` means "uses the engine's default values" (`:231-232, 268`).
  - The sample CLI uses `Backend.CPU()` (`L:kotlin/java/com/google/ai/edge/litertlm/example/Main.kt:31`).
- **LiteRT-LM engine** (when the model file carries no sampler params). CPU/GPU get `TOP_P, k = 1, p = 0.95, temperature = 1.0, seed = 0` (`L:runtime/engine/engine_settings.cc:395-409`). *Inferred:* k = 1 makes that default effectively greedy. Max tokens: the model metadata, else the next multiple of 4096 above prompt + 1024, with GPU capped to that when metadata > 4096 (`:320-339`). The audio executor inherits the main `maxNumTokens` (`:373-379`).

### TAI's current defaults

| Model | TAI catalog (`T:TaiModelCatalog.java`) | TAI profile (`T:TaiModelProfile.java`) | Match with Gallery 1_0_19 |
| --- | --- | --- | --- |
| Gemma 4 E2B | `6e5c4f1e…`, 2,588,147,712 B, "8GB+" → `recommendedRamGb` 8; endpoint ctx 4096, source ctx 32768; maxOutput 4000 (`:126-130, 252-253`; `T:TaiModelSpec.java:388-405`) | gpu, cpu; 4000; 64/0.95/1.0; min 8; thinking toggleable (`:104-105, 205-208`) | Values match. **Semantic mismatch:** Gallery's 4000 is the total KV budget, while TAI uses it as `defaultMaxTokens`/max output, with the window set separately (4k floor … 32k tier). |
| Gemma 4 E4B | `28299f30…`, 3,659,530,240 B, 12 GB; same windows (`:131-136`) | gpu, cpu; 4000; min 12 (`:107-108`) | Same as E2B |
| Gemma 3n E2B / E4B | not in catalog | **no branch**: an import falls to the generic profile, cpu only, 1024 (`:127-131`) | Gallery: cpu,gpu; 4096; 8/12 GB; image + audio |
| Gemma3-1B-IT | not in catalog | no branch → cpu only, 1024 | Gallery: gpu,cpu; 1024; 6 GB |
| Qwen2.5-1.5B (LiteRT) | import-only, revision `main`, 1,597,931,520 B, 6 GB, ctx 4096 (`:137-141, 219-226`) | gpu, cpu; 4096; 20/0.8/0.7; 6 (`:118-122`) | Profile matches. The catalog does not pin Gallery's commit `19edb84c…`. |
| DeepSeek-R1-Distill 1.5B | revision **`2f8b8ee9…`**, 1,833,451,520 B, sha256 pinned (`:142-149`) | gpu, cpu; 4096; 64/0.95/1.0; 6 (`:114-117`) | Profile matches. **The revision differs** from Gallery's `e34bb886…`; the size is the same. Which one is newer was not checked. |
| FunctionGemma MobileActions | `38942192…`, 288,964,608 B; ctx 1024/1024, output 1024 (`:150-154, 254`) | cpu; 1024; temp 0.0; 6 (`:110-113`) | Match |
| TinyGarden | not in catalog | cpu; 1024; temp 0.0; 6 (`:123-125`) | Match |
| EmbeddingGemma / Qwen3 Embedding | catalog entries (`:158-169`) | n/a (embedding runtime) | No Gallery defaults exist |
| Unknown LiteRT import | endpoint ctx 4096 (`T:TaiModelImporter.java:133`); `recommendedRamGb` 0 (`:136`); caps text_chat unless declared (`:413-431`). URL downloads: ctx 4096, RAM 0 (`T:TaiModelDownloader.java:90-93`) | built-in → gpu,cpu; import → **cpu**; 1024; 64/0.95/1.0 (`T:TaiModelProfile.java:127-131`) | Close to Gallery's import (CPU, 1024, 64/0.95/1.0). TAI's engine window (4096) is larger than Gallery's (1024). |

**Settings schema** (display defaults when nothing is set; `AUTO` resolves to the profile). LiteRT: `maxTokens` 4000 (2000–32000), `contextWindow` 4096 (1024–32768), topK 64 (5–100), topP 0.95, temperature 1.0 (0–2), accelerator GPU/CPU, thinking off, speculative decoding off (`T:TaiSettings.java:453-464, 390-406`). The `maxTokens` range mirrors Gallery's Gemma 4 slider (2000–`maxContextLength`), and the topK floor of 5 is stricter than Gallery's 1.

**Profile source label.** `SOURCE_EDGE_GALLERY_1_0_15` (`T:TaiModelProfile.java:17`). The values are identical through 1_0_19, so only the label is stale.

### Recommendations for importer defaults

1. **Add filename-matched profiles for the missing Gallery models.** For `gemma-3n-E2B-it-int4.litertlm` / `gemma-3n-E4B-it-int4.litertlm`: **cpu, gpu** order, 4096, 64/0.95/1.0, min 8/12 GB, image + audio. For `gemma3-1b-it-int4.litertlm`: gpu, cpu, 1024, 6 GB. Evidence: `AL:95-151` against the absent branches in `T:TaiModelProfile.java:104-125`. Keep Gemma 3n **CPU-first**. Gallery chose `cpu,gpu` for it while choosing `gpu,cpu` for Gemma 4 (`AL:110,130` vs `:20,66`). For its vision encoder, keep GPU even on a CPU main backend: Gallery's comment says "must be GPU for Gemma 3n" (`G:ui/llmchat/LlmChatModelHelper.kt:136`). TAI's `useGpuVision` drops vision to CPU after any recorded GPU failure (`T:LiteRtTaiRuntime.java:905-913`). *Inferred:* for Gemma 3n that may break vision rather than save it. Not verified on a device.
2. **Import Gallery's `maxTokens` as the default engine window, not the output cap.** Gallery passes it to `maxNumTokens`, the KV budget (`G:ui/llmchat/LlmChatModelHelper.kt:138`; `L:…/Config.kt:189-190`). TAI maps the same number to `defaultMaxOutputTokens` (`T:TaiModelSpec.java:394-405`). For Gemma 4 the effect is harmless, because TAI's 4k floor is almost 4000. For Qwen/DeepSeek at 4096 an output cap of 4096 inside a 4096 window leaves no room for the prompt. Take `maxContextLength` (32000) as the source/maximum window where it is given.
3. **Use the `ekvNNNN` filename token as the default window (not a hard limit) for litert-community files.** In all four allowlisted files that carry it, it equals Gallery's `maxTokens`: `_ekv4096` → 4096 (`AL:155,164`; `:172,181`) and `_ekv1024` → 1024 (`:189,198`; `:211,220`). TAI deliberately does not infer limits from arbitrary filenames (`T:TaiContextWindowPolicy.java:53-60`). A *default*, overridable by the user, is a weaker claim that the evidence supports. *Inferred convention:* no LiteRT-LM spec defining `ekv` was found.
4. **Keep unknown imports CPU-only, 1024 tokens, 64/0.95/1.0.** That is exactly Gallery's import default (`G:ui/modelmanager/ModelImportDialog.kt:97-103`; `G:data/Consts.kt:43-46`). When the user or the Hugging Face card declares GPU, order it `gpu, cpu` so TAI's GPU→CPU fallback applies.
5. **Flag an import larger than half of RAM.** Gallery's Hugging Face browser disables files over 50% of total RAM (`G:huggingface/HfModelUtils.kt:96-106`). TAI records `recommendedRamGb = 0` for imports (`T:TaiModelImporter.java:136`), so the preflight's RAM check never fires for them (`T:TaiLoadPreflight.java:272-283`). A derived recommendation (e.g., the RAM class at or above 2× file size) would give imports the same warning.
6. **Pin the revisions Gallery pins where TAI uses `main`, and reconcile DeepSeek.** Qwen2.5-1.5B is `19edb84c…` in Gallery against `main` in the TAI catalog (`T:TaiModelCatalog.java:137-141`). DeepSeek is `e34bb886…` in Gallery against `2f8b8ee9…` in TAI (`:145`). Update the profile source label to 1.0.19 (values unchanged, verified above).
7. **Leave thinking and speculative decoding off by default,** with the toggle exposed only for `llm_thinking`/`speculative_decoding` models. This matches `G:data/Config.kt:412-419` and TAI's schema (`T:TaiSettings.java:461-462`). Gallery also checks the file itself (`Capabilities(modelPath).hasSpeculativeDecodingSupport()`, `G:ui/llmchat/LlmChatModelHelper.kt:145-153`). TAI's `speculativeDecodingFlag(options, modelPath)` (`T:LiteRtTaiRuntime.java:856`) was not read for this note.

## Not verified

- Whether LiteRT-LM 0.11.0 (Gallery) or 0.14.0 (TAI) has the same GPU 4096 default cap, cache naming, and sampler defaults as HEAD 0.18.0. Everything in `L:` was read at HEAD.
- Whether Gallery's published 1.0.20 build reads `1_0_20.json`, which does not exist at this commit. The fallback-to-disk behaviour is read from code; what the shipped app actually loaded is unknown.
- Gallery's real memory footprint on any device. Gallery has no telemetry for it, and nothing was run. All costs quoted come from TAI's own pong measurements. The lmkd eviction count and pong's `totalMem` come from the developer's 2026-09-23 session note, not a repo document.
- Whether cancelling a LiteRT-LM GPU `initialize()` releases GPU memory promptly. This is needed for recommendation 1.
- Whether the config choice in Gallery persists across launches. No persistence code was found, so the negative is inferred from absence.
- Whether TAI's vision fallback to CPU actually fails for Gemma 3n. Gallery's comment asserts GPU is required, but this is untested.

## Benchmark on pong (2026-09-24)

Same model file (`gemma-4-E4B-it.litertlm`, 3 659 530 240 bytes in both apps), GPU, LiteRT-LM's own
`benchmark()` API with Gallery's defaults (256 prefill / 256 decode / 3 runs). TAI side via
`tai benchmark` (1d40e074). Both processes were in the `top-app` cgroup; both used OpenCL.

| | Gallery 1.0.19 | TAI (LiteRT-LM 0.14.0) |
|---|---:|---:|
| first / later init | 38.0 s / 21.7 s | 36.7 s / 21.5 s |
| time to first token | 1.07 s | 2.65 s |
| prefill | 263 tok/s | 100 tok/s |
| decode | 10.6 tok/s | 11.2 tok/s |
| peak MemAvailable drop | 2.96 GB | 3.3–3.5 GB |

TAI's normal path (`tai load --gpu` + streamed chat, 308 prompt tokens) loads in 11.8 s (its GPU
program cache persists; Gallery's benchmark uses a fresh cache dir per run) with TTFT 3.7–4.0 s and
decode 9.8–10.0 tok/s.

**Cause of the prefill gap: prompt padding in LiteRT-LM 0.14.** The E4B file has only `prefill_128`
and `prefill_1024` graphs. TAI prefill by prompt length: 128 → 0.62 s (255 tok/s); **256 → 2.65 s
(100 tok/s)**; 1000 → 2.76 s; 1024 → 3.04 s — a 256-token prompt costs a whole 1024 pass. HEAD's
`GetOptimizedPrefillWorkGroups` ("cautious greedy", `runtime/executor/litert_compiled_model_executor_utils.cc:473`)
splits it into 2 × 128 instead. Its error string ("Chosen prefill work group size exceeds …") is
absent from TAI's 0.14.0 `liblitertlm_jni.so` and present in Gallery's bundled library and in the
0.15.0, 0.16.1 and 0.17.1 AARs. Every prompt of roughly 129–700 tokens — most chat turns — pays
up to ~2 s extra on TAI until LiteRT-LM is upgraded to ≥ 0.15.0.
