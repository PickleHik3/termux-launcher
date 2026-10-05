# TAI device tiers: what each phone gets

Status: build spec, 2026-10-05, against dev `8411d209c`. Decisions from the developer are folded in (§9). It
rests on three research notes in this folder:

- `tai-device-classes-and-gates-2026-10-05.md`: memory per model, floors, gate design (cited as "research").
- `tai-android-soc-support-2026-10-05.md`: Android versions, GPU families, CPU features, NPU (cited as "soc").
- `local-ai-and-living-wallpaper-review-2026-10-04.md`: the current gate's faults (cited as "review").

It also keeps the settled model choices: E2B cleanup Light/Polished, Silero VAD, KittenTTS with 4 voices, E4B
director with E2B as the fallback, and no Gemma `-gpu` files.

## Summary

Three tiers, set from the phone's RAM. A tier decides **what we suggest and what we preselect**. It never
forces a load: every load still passes the live gate (§6). It never blocks a user's own choice either: any
model can be added from the Model Centre on any tier.

| Tier | RAM class (`totalMem`, rounded up) | One line |
|---|---|---|
| **Tier 1, "Core"** | 6 GB and under | **Nothing by default.** Voice typing and read aloud are offered but not preselected. LLMs are never suggested; users may add one in the Model Centre and pick it per function (app categories, for example) or serve it on the endpoint. |
| **Tier 2, "Plus"** | 8, 10 and 12 GB (pong) | LLMs on. E2B is the default assistant and tidies dictation. E4B reads wallpapers and sorts apps on 10–12 GB; 8 GB uses E2B for those. |
| **Tier 3, "Max"** | 16 GB and more | Everything local. E4B is the default assistant. Voice and search stay loaded beside it. |

The Android version and the GPU then switch individual features off or move them to the CPU (§2). The Model
Centre is rebuilt around a model picker per function (§4). A welcome card at the end of the tour tells the user
what runs on their phone and downloads the preselected files in one go (§5).

Image generation stays supported (API, `tai image`, import) but is **not declared or suggested anywhere** in
the UI (§3.6).

## 1. How a phone gets its tier

- Read `ActivityManager.MemoryInfo.totalMem` and round it up to a RAM size, as `TaiLoadBudget.ramClassBytes`
  already does (`ai/TaiLoadBudget.java:446-461`). Never use `advertisedMem`: AOSP's storage-style rounding
  reports pong's 12 GB as "16 GB" (research §2.1).
- ≤ 6 GB → Tier 1. 8, 10 and 12 GB → Tier 2. ≥ 16 GB → Tier 3.
- Within Tier 2, an **8 GB** phone has its own row in the tables below where it differs. It never gets E4B by
  default (research §2.3, §3.2).
- Swap and vendor "virtual RAM" do not count. They count as pressure in the gate.
- The tier is computed once per process and shown on the welcome card and in the Model Centre header:
  "Tier 2 · 12 GB".
- A developer override, `tai runtime --tier 1|2|3|auto`, exists for testing on pong and Waydroid. Users get no
  tier picker.

## 2. Android version and GPU

RAM sets the tier. The platform can only take things away:

```
offer(function, model) = tierPolicy(tier, ramClass, function, model)  ∩  platform(abi, sdk, gpuPath)
```

### 2.1 ABI and Android version

| Check | Rule | Source |
|---|---|---|
| ABI | LiteRT-LM needs arm64-v8a or x86_64. MNN ships arm64-v8a only. With neither, no local model is offered; the card says so. | `ai/TaiDeviceCapabilities.java:343,362-369`; soc §2 |
| MNN | **API 30 minimum**, not 24. The bundled bridge is built for API 30 and imports a symbol that exists only from API 28. Change `MNN_SDK_MINIMUM` to 30. | soc §4; `ai/TaiDeviceCapabilities.java:29` |
| Living stills, live lock wallpaper | **API 34.** The renderer is `HardwareBufferRenderer` (34) and the lock code uses `getWallpaperInfo(int)` (34). Below 34 the wallpaper reader, depth and cut-out functions are hidden and their files are never suggested. | soc §6; `chrome/wallpaper/WallpaperPreviewView.java:189`, `WallpaperSlots.java:245` |
| Free memory | `availMem` reads MemFree + Cached up to Android 15 and MemAvailable from 16. Below API 36, read `MemAvailable` from `/proc/meminfo`. Apps can read it on 9 and 16; on a failed read, fall back to `availMem` and always apply the swap-low penalty. | soc §6; research §4.1 |
| Floor self-correction | `ApplicationExitInfo` is API 30. Below that the floors stay at their class values. | research §4.6 |
| Trim callbacks | The old `onTrimMemory` levels are not delivered from API 34. The pressure watch polls. | research §4.3 |
| NPU | Not a tier input. It needs API 31, a per-SoC model file and vendor runtime libraries, and Gemma 4 E4B has no NPU file. | soc §3 |
| Foreground services | Their type limits do not bind at targetSdk 28. When the target rises, downloads move to user-initiated transfer jobs. Not part of this build. | soc §6 |

### 2.2 GPU path

The LiteRT-LM GPU path on Android is OpenCL, and a failed GPU load has no automatic CPU fallback. Upstream keeps
no device denylist and tells apps to build their own (soc §1). So:

| GPU family | Detected how | GPU path | What the picker does |
|---|---|---|---|
| Adreno (Qualcomm) | Vulkan `vendorID` 0x5143 | **Yes** | GPU preselected |
| Adreno 8xx with compiler `E031.47.12.*` | as above, plus `CL_DRIVER_VERSION` read by the OpenCL probe (no GL context needed) | **Unknown** | GPU preselected, with "This GPU driver is known to give wrong answers; update the phone's software". LiteRT-LM's own binary warns about this compiler (soc §1). Settled per device by the GPU check (§2.3). |
| Mali / Immortalis on Tensor G4 and newer | `vendorID` 0x13B5 + `SOC_MODEL` table | **Unknown** | GPU preselected, with the note (decision 3) |
| Older Mali, Samsung Xclipse, PowerVR | `vendorID` 0x13B5 / 0x144D / 0x1010 | **CPU first** | CPU preselected; GPU selectable with "Often fails on this GPU" |
| Pixel 10 | `Build.MODEL` contains "pixel 10" (Gallery's rule, kept as is) | **No** | GPU not offered |
| No OpenCL | JNI probe: `dlopen("libOpenCL.so")` and `clGetPlatformIDs` fail | **No** | GPU not offered |

- Detection order, with no GL context: the Vulkan `vendorID`, then `ro.hardware.egl`, then `Build.SOC_MODEL`
  (API 31+) through our own table (soc §5). The result is cached per install and app version.
- Any GPU load that fails records a verdict-only failure (7-day expiry, already built) and walks the function's
  fallback chain (§3).
- Keep the `<uses-native-library>` entries for `libOpenCL.so` and `libvndksupport.so`. They do nothing at
  targetSdk 28 but become required at 31 (soc §1).

**No GPU path** on Tier 2 or 3: the assistant defaults to E2B on the CPU at 2048. E4B stays selectable on the
CPU, with its speed in the fit line. The wallpaper reader and app categories run E4B on the CPU as
background jobs (40–50 s on pong, measured 2026-10-04).

### 2.3 GPU check: settling Unknown by running it

A rule can only guess. Whether the GPU gives right answers on one phone and driver is settled by running it:

- **Automatic check.** The first GPU load of a Gemma file on a device whose GPU path is Unknown runs a fixed
  canary prompt before the real request: greedy decoding, about 16 output tokens, with an expected answer
  checked by a pure matcher. Pass → the device's GPU path becomes **Verified** (stored with the driver string
  and app version, so a driver update re-runs it). Garbage or a crash → **Failed**, the picker preselects the
  CPU, and the user sees "The GPU gave wrong answers on this phone; using the CPU". It costs about one
  second on top of the load.
- **The benchmark.** A bench run on the GPU records the same verdict from its own outputs, so a user who runs
  the bench settles it too.
- **The user.** The picker keeps GPU and CPU selectable either way. A "Answers look wrong?" action under an
  LLM function switches it to the CPU and records Failed. "Try the GPU again" clears the verdict.

This also covers the Adreno 730 `-gpu` corruption class that led to the ban on those files.

### 2.4 CPU features

`dotprod`, `i8mm` and `sme2` are read for bench labels and diagnostics only, and never gate anything. XNNPACK
and MNN pick their kernels at run time (soc §2, §4).

## 3. What each tier offers

Legend:

- **Preselected**: ticked on the welcome card and used by Automatic.
- **Suggested**: listed as fitting this phone, not ticked.
- **Listed**: shown under Get models with "Made for bigger phones"; the user may add it.
- **Hidden**: not shown; still installable by import.

### 3.1 Voice

| | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Voice activity (Silero, in the APK) | On (no download) | On | On |
| Voice typing | Whisper base(.en) **Suggested**; Whisper small Suggested | Whisper small(.en) **Preselected**; base and Parakeet Suggested | Whisper small Preselected; Parakeet Suggested |
| Read aloud (KittenTTS) | **Suggested** | Preselected | Preselected |
| Tidy dictation | Raw text. A user-added LLM may be picked; it then runs momentary (loads at session end, unloads after the pass). | E2B Preselected, loaded during a mic session | E2B Preselected |

Phones set to English get the `.en` Whisper files; others get the multilingual ones.

### 3.2 Assistant and endpoint (`/v1`, aichat)

| | Tier 1 | Tier 2 (8 GB) | Tier 2 (10–12 GB) | Tier 3 |
|---|---|---|---|---|
| Automatic | none | E2B, GPU | E2B, GPU | **E4B**, GPU |
| Get models | Tiny models and E2B Listed; E4B Listed | E4B Listed | E4B Suggested | E2B Suggested |
| Window, auto / most | 2048 / 4096 | 4096 / 4096 | 4096 / 8192 | 4096 / 16384 |

- One chat model is resident at a time (`ai/MultiBackendTaiRuntime.java:53`).
- MNN models get the same window caps as LiteRT. Today MNN is uncapped and plans Qwen3 0.6B at 32k on pong
  (research §3.3).

### 3.3 App categories

| Tier 1 | Tier 2 (8 GB) | Tier 2 (10–12 GB) | Tier 3 |
|---|---|---|---|
| Off, unless the user picks a model they added. The paste route stays. | E2B | E4B → E2B | E4B → E2B |

### 3.4 Search and memory (embeddings)

| Tier 1 | Tier 2 | Tier 3 |
|---|---|---|
| EmbeddingGemma Suggested; evicted before any LLM load | EmbeddingGemma Preselected; stays loaded beside a chat model | Preselected; resident |

### 3.5 Wallpaper creator (living stills, API 34+)

| | Tier 1 | Tier 2 (8 GB) | Tier 2 (10–12 GB) | Tier 3 |
|---|---|---|---|---|
| Depth + cut-out | DA2 + U-2-Net Suggested | DA2 + U-2-Net Preselected | DA3 + U-2-Net Preselected | DA3 + U-2-Net Preselected |
| Reader, Automatic | Rules only (a user-added vision model may be picked) | E2B `-vision` | E4B `-vision` | E4B `-vision` |
| Reader fallback chain | Rules only | Rules only | E2B `-vision` → rules only | E2B `-vision` → rules only |

- Rendering (AGSL, the lock wallpaper) is not tiered.
- No chain falls back from GPU to the CPU for Gemma 4. The CPU saves no memory and costs 3–4× the time
  (research §3.4). The exception is a phone with no GPU path at all (§2.2).
- A reader run waits while dictation is busy and never interrupts a busy resident.

### 3.6 Image generation: supported, not declared

- It has no Functions row, no catalogue group, no welcome-card row, no suggestion and no settings line.
- It stays reachable only through `POST /v1/ai/images/generations`, `tai image` and import. An imported image
  model shows in the Installed list, because the user put it there, with Delete only (as today).
- Remote catalogue payloads already drop diffusion entries (`ai/TaiModelCatalog.java:210-212`). Keep that.

### 3.7 Remote provider

The BYO-key provider was approved on 2026-10-04 and **is not built**. This build leaves room for it: picks can
hold `remote/<id>`, and the picker has a Remote section that stays hidden while no provider exists. Until it
lands, Tier 1's LLM functions offer "Rules only", "Raw text" or "Off", plus any model the user added.

## 4. Model Centre: a model picker per function

### 4.1 Today

The Centre is organised by model kind: Installed | Chat | Speech
(`app/fragments/settings/termux/TaiModelCentreFragment.java:85-88,406-421`). Each feature picks its model in its
own place:

| Function | Today | Setting |
|---|---|---|
| Assistant and endpoint | `tai_role_default_assistant`, default E2B (`ai/TaiSettings.java:27,102-114`) | Centre → Set active |
| Voice typing | `tai_stt_model_id`, else the first installed (`ai/TaiSpeechModels.java:65-83`) | Centre "Use for voice"; Keyboard → Speech model |
| Tidy dictation | `keyboard_voice_polish_model`, Automatic = E2B then E4B (`LocalTaiVoiceTextPolisher.java:71-96`) | Keyboard → Voice input → Cleanup model |
| Read aloud | no model key; KittenTTS if installed (`ai/TaiTtsModels.java:37-48`) | voice and speed only |
| App categories | hard-coded E4B if it fits, else E2B (`CategorySortDialogs.java:81-88`) | none persisted |
| Wallpaper reader | hard-coded E4B `-vision`, else E2B (`living/SceneReader.java:26-29,87-91`) | none |
| Wallpaper depth | `wallpaper_depth_model`, else DA3, else DA2 (`ai/TaiVisionModels.java:24,88-98`) | Centre "Use for depth" |
| Search and memory | the request's `model`, else **the chat assistant** (`ai/TaiManager.java:2316-2319`), a bug | none |

### 4.2 New layout

Three segments: **Functions | Installed | Get models**. The Centre opens on Functions, with a header line:
"Tier 2 · 12 GB · Snapdragon 8+ Gen 1 · Android 16".

```
┌ Model Centre ─────────────────────────────────────────┐
│  Tier 2 · 12 GB · Snapdragon 8+ Gen 1 · Android 16    │
│  [ Functions ]  Installed   Get models                │
│                                                       │
│  Assistant and endpoint                               │
│  Automatic (Gemma 4 E2B) · GPU                ›       │
│  Voice typing                                         │
│  Whisper Small (English)                      ›       │
│  Tidy dictation                      Polished         │
│  Automatic (Gemma 4 E2B)                      ›       │
│  Read aloud                                           │
│  KittenTTS · Jasper                           ›       │
│  App categories                                       │
│  Automatic (Gemma 4 E4B) · GPU                ›       │
│  May close apps running in the background.            │
│  Wallpaper creator                                    │
│  Reader: Gemma 4 E4B · Depth: DA3 Small       ›       │
│  May close apps running in the background.            │
│  Search and memory                                    │
│  EmbeddingGemma 300M                          ›       │
└───────────────────────────────────────────────────────┘
```

- **Functions**: one row per function: assistant and endpoint, voice typing, tidy dictation, read aloud, app
  categories, wallpaper creator, search and memory.
  - Each row shows its pick and where it runs: GPU, CPU, rules only, raw text or off.
  - A function with nothing usable says "Not set · Add a model".
  - Functions the platform removes are hidden (the wallpaper creator below API 34).
- **Installed**: today's list, plus a "Used by" line per model ("Assistant, Tidy dictation"). Deleting a model
  in use names the functions that fall back and what they fall back to.
- **Get models**: the catalogue grouped as Assistants, Speech, Voice output, Search and Wallpaper vision.
  - The import link bar moves here (`TaiImportFlow.java:383-616`).
  - Each entry shows its size, a fit line ("Fits this phone", "When the phone has room" or "Made for bigger
    phones") and the functions it can serve.
  - Tier 1 lists LLMs without suggesting them.

### 4.3 The picker sheet

Tapping a function opens one shared sheet, filtered to models that can serve it. The wallpaper reader needs a
vision model, search an embedder, voice typing a speech model.

1. **Automatic**: the tier's choice from §3, named in brackets, for example "Automatic (Gemma 4 E4B)". Every
   function starts here, so a fresh install matches the welcome card.
2. **On this phone**: installed models that can serve the function, each with its fit line. LLM functions also
   choose GPU or CPU, preselected by §2.2 with that table's note.
3. **Remote**: hidden until the provider exists (§3.7).
4. **Without a model**, where the function has one: "Rules only" (wallpaper reader), "Raw text" (tidy
   dictation), "Off" (app categories).
5. **Get a model**: compatible catalogue entries, downloadable from inside the sheet.

Below the list, a read-only line shows the fallback chain: "If it can't load: Gemma 4 E2B → rules only".

The sheet also holds each function's extras:

- tidy dictation: the cleanup level (Light / Polished);
- read aloud: voice and speed;
- voice typing: the STT window;
- wallpaper creator: depth as a second picker. The cut-out (U-2-Net) is shown but not pickable.

### 4.4 Background-app warning

Decided: a large load may make Android close cached background apps, and that is acceptable, but the user is
told so. The deterministic rule is: when the resolved model's file is **≥ 25 % of the RAM class**, the
function shows the subtext "May close apps running in the background". It appears in four places:

- under the function's row in Functions;
- beside the model in the picker sheet;
- under the Bring to life / Read again action in the wallpaper creator;
- in the category-sort dialog.

Examples: E4B (3.66 GB) warns on 12 GB (30 %) but not on 16 GB (23 %). E2B (2.59 GB) warns on 8 GB (32 %)
but not on 12 GB.

### 4.5 Storage and resolution

- `TaiFunction`: ASSISTANT, VOICE_TYPING, TIDY_DICTATION, READ_ALOUD, APP_CATEGORIES, WALLPAPER_READER,
  WALLPAPER_DEPTH, EMBEDDINGS. One `TaiFunctionModels` class owns every pick.
- Existing keys stay as they are, so current picks survive: `tai_role_default_assistant`, `tai_stt_model_id`,
  `keyboard_voice_polish_model`, `wallpaper_depth_model`. New keys follow `tai_fn_<function>_model` for read
  aloud, app categories, wallpaper reader and embeddings. Accelerator picks use `tai_fn_<function>_accel`.
- A value is `""` (Automatic), a model id, `remote/<id>` (the remote provider's namespace) or `off`.
- `resolve(function)` returns the model (or a without-a-model choice), the accelerator and the fallback chain,
  in this order:
  - the pick, if it is installed and the platform allows it;
  - else Automatic;
  - else the next link of the chain.
  The live gate (§6) still decides each load, and a refusal walks the chain.
- The callers drop their private rules and call `resolve`:
  - `CategorySortDialogs.resolveModel`, `SceneReader.modelId` and `LocalTaiVoiceTextPolisher.resolveModelId`;
  - `TaiSpeechModels.chooseActive`, `TaiTtsModels.chooseActive` and `TaiVisionModels.chooseDepth`;
  - the embeddings fallback in `TaiManager`.
- `/v1/embeddings` without a `model` gets the embedder, not the chat model.
- The old pickers become entry points into the same sheet: Keyboard → Cleanup model, Keyboard → Speech model,
  the category-sort dialog's model choice, and the Centre's "Set active", "Use for voice" and "Use for depth".

## 5. Welcome card: "What runs on this phone"

### 5.1 When it shows

- **Once, as the last step of the tour**, after its closing card. It follows the tour's visibility rules
  (`app/tour/TourCardVisibility.java`), so it never draws over chrome or help.
- Once after the update that brings tiers, for existing installs that finished the tour before. It shows the
  next time the home screen is idle, and installed rows show as installed.
- Any time from On-device AI settings → "What runs on this phone".
- If the tour is skipped, the card still shows once on the next idle home screen.

It uses the same pattern as the first-run permissions card (`app/firstrun/FirstRunPermissionsCard.java`):

- one card, a row per item, one control per row, one action at the bottom;
- a pure decision class with unit tests, and a view that only draws it.

### 5.2 What it shows

```
┌──────────────────────────────────────────────────────┐
│  What runs on this phone                             │
│  Tier 2 · 12 GB · Snapdragon 8+ Gen 1 · Android 16   │
│                                                      │
│  ☑ Voice typing        Whisper Small     286 MB      │
│      Works offline.                                  │
│  ☑ Read aloud          KittenTTS          94 MB      │
│  ☑ Assistant and tidy  Gemma 4 E2B       2.6 GB      │
│    dictation                                         │
│      Fixes punctuation after you speak; serves apps. │
│  ☑ Search and memory   EmbeddingGemma    183 MB      │
│  ☑ Wallpaper creator   depth + cut-out   143 MB      │
│  ☐ Smarter reading     Gemma 4 E4B       3.7 GB      │
│      Reads wallpapers and sorts apps better.         │
│      May close apps running in the background.       │
│                                                      │
│  Selected: 3.3 GB · 41 GB free · ☑ Wi-Fi only        │
│                                                      │
│              [ Later ]      [ Download selected ]    │
└──────────────────────────────────────────────────────┘
```

- **Header**: tier, RAM class, SoC name and Android version.
- **Rows**: one per download group, each with its model, size, one plain line, and the background-app warning
  where §4.4 applies.
- **Hidden rows**: a row the platform removes is hidden.
- **Tier 1**: the card shows voice typing, read aloud, search and the wallpaper creator, all unticked. It adds
  one line: "Assistants and smart features: add your own model in the Model Centre."
- **Footer**:
  - the selected total against free storage;
  - Download disabled, with the reason, when the selection does not fit with a 1 GB margin;
  - a Wi-Fi-only switch, on by default.
- **Download selected** queues everything through `ai/TaiDownloadQueue.java` /
  `ai/TaiModelDownloadService.java`, smallest first, so voice works within a minute. The card closes at once.
  Ticking a row leaves the functions on Automatic, so the downloaded model becomes the pick.
- **Later** closes the card and leaves the settings entry. It never nags.
- **Copy**: no tier jargon beyond the header, and no parameter counts. Sizes are rounded to two significant
  figures.

### 5.3 Preselection (deterministic)

What is ticked depends only on the device, through this fixed table. There are no heuristics and nothing
depends on how full the phone is today. Storage can disable Download; it never changes the ticks.

| Row | Tier 1 | Tier 2 (8 GB) | Tier 2 (10–12 GB) | Tier 3 |
|---|---|---|---|---|
| Voice typing | ☐ Whisper base | ☑ Whisper small | ☑ Whisper small | ☑ Whisper small |
| Read aloud | ☐ KittenTTS | ☑ | ☑ | ☑ |
| Assistant and tidy dictation | — (the Model Centre line) | ☑ E2B | ☑ E2B | ☑ E2B (tidy dictation) |
| Smarter reading / assistant | — | — | ☐ E4B | ☑ E4B (assistant, reader, categories) |
| Search and memory | ☐ EmbeddingGemma | ☑ | ☑ | ☑ |
| Wallpaper creator (API 34+) | ☐ DA2 + U-2-Net | ☑ DA2 + U-2-Net | ☑ DA3 + U-2-Net | ☑ DA3 + U-2-Net |

Platform rules (§2) apply after the table. A removed row is hidden. On a phone with no GPU path, the E4B row is
unticked.

## 6. Gates

From research §4.3–4.7. The floors are policy values, corrected on the device by the low-memory-kill loop
(research §4.6). They are keyed by RAM class, not tier:

| | ≤ 4 GB | 6 GB | 8 GB | 10–12 GB | ≥ 16 GB |
|---|---|---|---|---|---|
| Peak floor (momentary: reader, categories, Tier 1 cleanup) | 0.75 GiB | 0.75 GiB | 0.75 GiB | 0.75 GiB | 1.0 GiB |
| Hold floor (resident chat, voice, embeddings) | 1.0 GiB | 1.25 GiB | 1.25 GiB | 1.5 GiB | 2.0 GiB |
| Swap-low penalty (SwapFree < 15 %, or swap unknown below API 36) | +0.5 GiB | same | same | same | same |
| Launcher not in front (hold floor only) | +0.25 GiB | same | same | same | same |
| Momentary unload deadline, enforced by the runtime | 3 min | same | same | same | same |

The gate fixes ship with the tiers. Without them, Tier 2's E4B reader keeps landing on the CPU:

1. **Floors.** Replace `max(2 × threshold, 512 MiB)` and the 1 GiB momentary cap with the table above
   (`ai/TaiLoadBudget.java:115-126,339-340`).
2. **History.**
   - Key: model, backend, accelerator and modality. The window moves into each sample.
   - Keep a ring of the last 8 samples, each with an expiry.
   - Look up the nearest measured window at or above the request, else interpolate between windows, else use
     the seed.
   - Vision falls back to the text sample plus an encoder delta.
   - Code: `ai/TaiRuntimeHistory.java:259-322`; research §4.4.
3. **Meter.** Measure through the first prefill, CPU loads included. Record the `load` and `first_prefill`
   phases with the prompt token count. Delete `trustsLoadDrop` (`ai/TaiLoadMeter.java`,
   `ai/TaiLoadBudget.java:160-194`; research §4.5).
4. **Seeds.**
   - LiteRT CPU fixed cost = the card's CPU RSS share, or 0.6 × file, instead of 5 %.
   - The MNN slope comes from the architecture KV, not `file/19000`.
   - Research §3.3, §4.4.
5. **Ladder.** Step 3 (the other accelerator) only if its *measured* cost is lower. For a LiteRT file over
   3 GB, go to the fallback model instead (research §4.7).
6. **Momentary deadline.** The runtime cancels and unloads a momentary load itself after 3 minutes, including
   when the caller's IPC wait has timed out (review T3).
7. **Pressure watch.**
   - Tiers on the hold and peak floors.
   - The `1.25 × threshold` line goes.
   - The trim mapping goes.
   - Poll every 250 ms during a load or first prefill, and every 2 s otherwise.
8. **Window caps** per §3.2, MNN included (`ai/TaiContextWindowPolicy.java`).
9. **The refusal reason** quotes the window that was actually tried (review T5).

## 7. Building it

Work packages, in dependency order:

| # | Package | Main files |
|---|---|---|
| A | **Policy core.**<br>• `TaiDeviceTier`, `TaiPlatformCaps` (ABI, SDK, GPU path with the JNI OpenCL probe and the Vulkan vendor read, CPU features for labels)<br>• `TaiTierPolicy`: §3, §5.3, the §4.4 rule and the fallback chains<br>• `TaiFunction`, `TaiFunctionModels`<br>• the `--tier` override<br>• `MNN_SDK_MINIMUM` → 30 | new classes in `ai/`, `TaiDeviceCapabilities`, `TaiSettings`, `resources/bin/tai`, launcherctl runtime route |
| B | **Gate fixes** (§6) | `TaiLoadBudget`, `TaiRuntimeHistory`, `TaiLoadMeter`, `TaiPressureWatch`, `TaiContextWindowPolicy`, `TaiRuntimeService`, a `/proc/meminfo` reader |
| C | **Model Centre rework** (§4.2–4.4) | `TaiModelCentreFragment`, `TaiModelCentreAdapter`, `TaiModelCentreRows`, new `TaiFunctionPickerSheet`, layouts, `strings_tai_functions.xml` |
| D | **Callers + welcome card** (§4.5, §5) | `CategorySortDialogs`, `SceneReader`, `LocalTaiVoiceTextPolisher`, `TaiSpeechModels`, `TaiTtsModels`, `TaiVisionModels`, `TaiManager` (embeddings), the keyboard/speech pickers, tour end, new `TaiWelcomeCard` + view, `strings_tai_welcome.xml` |

A and B run in parallel. C and D follow on A's API.

Tests:

- the policy table per tier and platform;
- the welcome card's decision table;
- the §4.4 rule;
- each gate fix;
- a Waydroid run per tier using the override.

## 8. Not in this build

- The BYO-key remote provider (§3.7).
- The NPU.
- Moving downloads to transfer jobs (only needed when targetSdk rises).

## 9. Decisions (2026-10-05)

1. Tier 1 (≤ 6 GB) gets **nothing by default**. STT and TTS are offered. Users may add LLMs in the Model
   Centre and use them per function (app categories, for example) or on the endpoint.
2. **8 GB gets LLMs**: it sits in Tier 2, with E2B wherever 10–12 GB uses E4B.
3. **E4B is Tier 3's default assistant.**
4. **The GPU on unconfirmed families is encouraged, not a rule.**
5. **Preselections are deterministic per device.**
6. **The Model Centre gets a model picker per function.**
7. **Cached-app kills during big loads are acceptable**, with a visible subtext warning (§4.4).
8. **Image generation is supported only**, never declared or suggested.
9. **The welcome card comes at the end of the tour.**
