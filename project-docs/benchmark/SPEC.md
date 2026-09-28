# Model benchmark: spec

Agreed with the developer on 2026-09-28. Review page: `.lavish/model-benchmark.html` (gitignored).

A screen inside On-device AI that shows which models this phone runs well. It offers only models
that can run here, warns before it starts, runs the same fixed tests on each model with a live
view of the reply, keeps every result, and ranks them on a leaderboard that updates as each model
finishes.

## What exists today

- `tai benchmark` → `TaiManager.benchmark()` (`TaiManager.java:641-765`) calls LiteRT-LM's
  `benchmark()` in `:tai_runtime`. It returns `initMs`, `ttftMs`, `prefillTps`, `decodeTps` and
  MemAvailable per run. It is LiteRT only (MNN gets `backend_not_supported`) and saves nothing.
- The only per-model measurement kept is the load memory in `TaiRuntimeHistory`
  (`tai_runtime_history_json`).
- The app reads no battery level, charging state or thermal state.
- No screen streams a reply. `TaiGenerationCallback.onToken` exists and can time every token.
- `TaiImportFit` (YES/SLOW/TOO_BIG/UNKNOWN), `TaiLoadBudget` and `TaiPressureWatch` already decide
  fit, admission and pressure unloads; the benchmark reuses them.

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Default preset | Standard |
| 2 | Downloads | Installed + recommended, with "remove afterwards" |
| 3 | Leaderboard | This phone only; nothing is uploaded |
| 4 | Charging | Allowed; the result is marked "charging" |
| 5 | Verdicts | Smooth ≥ 15 tok/s, Usable 7–15, Slow < 7; Broken when the check fails |
| 6 | Entry points | On-device AI › Models zone row, and Model centre › ⋯ menu (that model only). Not the importer. |
| 7 | Eagle3 | Off by default for MNN; "Draft model (experimental)"; no "Fast" tag; importer prefers the plain build (done on `feat/mnn-tai`) |

## Screens

1. **Home.**
   - A device card: SoC, GPU, RAM class, free RAM, free storage, battery, heat.
   - The leaderboard, with Speed / First word / Memory tabs re-sorting the same entries.
   - A "Run a benchmark" button, and when the last run was.
2. **Choose.**
   - The Quick / Standard / Thorough selector.
   - Installed models first, then "Worth a download" (recommended for this RAM class), each with backend, size, processors and the estimated time.
   - Hidden models are counted, with "Show why".
   - The footer shows the model count, total time and download size.
3. **Check (sheet).** One line per condition:
   - battery;
   - heat;
   - the "will get warm" warning (no direct sun, not outdoors in heat, not in a pocket or under a pillow);
   - keep the screen open;
   - downloads, with the "remove afterwards" toggle.

   Start stays disabled while a blocking condition fails, and the failing line says what to do.
4. **Run.**
   - A stepper per model and phase.
   - A live view: the prompt in grey, the streaming reply in monospace, a token counter. It can be folded away.
   - Tiles: first word (TTFT), reading (prefill tok/s), writing (decode tok/s) with a sparkline, free RAM, heat, battery.
   - Stop keeps the phases that finished.
5. **Cool-down.** A countdown until the heat has recovered. The model that just finished shows on the leaderboard with its rank and a "New best" pill. "Skip the wait" marks the next result "warm start".
6. **Result.**
   - The headline writing speed and the verdict.
   - Details: first word, reading, load, memory used, sustained drop, check.
   - A history chart split at app or runtime version changes.
   - "Run this model again".

Style: the Model centre's tonal cards, monospace detail lines and pills (`TaiModelCentreAdapter`, `item_tai_centre_model.xml`). Reuse `VoiceWaveformView` for the sparkline and `VoiceTranscriptPanel` for the live view. Motion through `TaiMotion`, which respects the animations-off setting.

## Tests (bench v1)

There is one harness for both backends in `:tai_runtime`. It times each token via the generation callback, so LiteRT and MNN are measured the same way. It does not use LiteRT `benchmark()`. `tai benchmark` moves onto the harness and writes to the same store.

| Phase | Runs | Measures | Quick | Standard | Thorough |
|---|---|---|---|---|---|
| Load | Cold load after unload, through the budget | Load ms; memory used (`TaiLoadMeter`) | ✓ | ✓ | ✓ |
| Warm-up | One short reply, discarded | — | ✓ | ✓ | ✓ |
| Reading | Fixed ~512-token bundled passage + "summarise in one line" | Prompt tok/s | 1× | 3× | 3× |
| First word | Short chat message (~30 tokens) | TTFT | 1× | 3× | 3× |
| Writing | 128 tokens, temperature 0, fixed long-output prompt | Decode tok/s, first to last token; median | 1× | 3× | 5× |
| Sustained | 90 s of writing | Speed at the end vs the start; battery used | – | – | ✓ |
| Check | 3 fixed questions with known answers | Pass/fail (catches builds that write nonsense) | ✓ | ✓ | ✓ |
| Processors | CPU always; GPU where the model and phone support it | Each processor is its own entry | best | both | both |

- **Rough time per model:** Quick 1 min, Standard 2 min per processor, Thorough 4 min per processor, plus the cool-down.
- **Versioning:** results from another bench version are shown but not ranked together.

## Leaderboard

- One entry per model + backend + processor (+ Eagle on/off).
- Ranked by the median writing speed of the latest run; ties go to the faster first word.
- A new result appears the moment its model finishes.
- Runs are marked: charging, warm start, low battery, older app or runtime version.

## Safety

| Condition | Source | Before start | While running |
|---|---|---|---|
| Battery | `BatteryManager` (new) | ≥ 30% or charging | Stop below 15% |
| Heat | `PowerManager.getCurrentThermalStatus` (29+), `getThermalHeadroom` (30+), listener (new) | NONE or LIGHT | MODERATE: pause until headroom recovers. SEVERE+: stop and unload |
| Memory | `TaiLoadBudget`, `TaiPressureWatch` | Each model must pass the budget, or it is skipped with the reason | Pressure tiers unload; marked "stopped: memory" |
| Storage | `File.getUsableSpace` | Downloads fit, plus 10% | – |
| Screen/app | Keep-screen-on; runtime ops already run in the foreground | "Keep this screen open" | Leaving pauses at the end of the current step; Resume on return |
| Time | Per phase | – | Stop a phase at 3× its expected time |

## Which models are offered

The filter:
1. RAM: `TaiImportFit` YES is shown, SLOW is shown as "Tight", TOO_BIG is hidden.
2. Storage: the size plus 10% must fit in free space.
3. Chip: builds for another chip's NPU are dropped (`TaiImportProfiles.socMatches`).
4. MNN needs `mnnSupported`.
5. GPU runs only where `supportsAccelerator` allows.

The recommended downloads come from the signed remote catalogue (`TaiRemoteCatalog`), so the list changes without an app release. Starting set (each one is verified on pong before it is published):

| Model | Backend | Download | Offered on | Status |
|---|---|---|---|---|
| Gemma 4 E2B | LiteRT | 2.4 GB | 8 GB+ | in catalogue |
| Gemma 4 E4B | LiteRT | 3.4 GB | 12 GB+ | in catalogue |
| Qwen3-VL 2B Instruct | MNN | 1.5 GB | 6 GB+ | measured: 21 tok/s CPU, 15 GPU on pong |
| Qwen2.5 1.5B Instruct | LiteRT | 1.6 GB | 6 GB+ | to verify |
| Gemma 3 1B | LiteRT | 1.0 GB | 6 GB+ | to verify |
| Qwen2.5 0.5B Instruct | LiteRT | 0.5 GB | 4 GB+ | to verify |
| SmolVLM 500M | MNN | ~0.5 GB | 4 GB+ | to verify |
| Qwen3.5 2B | MNN | ~2 GB | 8 GB+ | to verify (3.6.1 fixes its fused inference) |
| Eagle3 builds | MNN | +90 MB | — | off by default; runnable as a separate entry |

## Storage

- **File:** `files/tai/benchmarks.json`, written by the UI process after the runtime replies (one writer).
- **A record holds:**
  - the model: id, file size and hash;
  - how it ran: backend, processor, Eagle on/off, runtime version (LiteRT-LM / MNN), app version, bench version, preset;
  - the device: SoC, RAM class;
  - the conditions: battery at start and end, charging, heat at start and end, warm start;
  - per phase: median, min and max;
  - whether the check passed, and a timestamp.
- **Kept:** the last 20 runs per leaderboard entry. Deleting a model keeps its results, marked "not installed".
- **Model centre:** each row shows its best recent speed as a quiet pill, e.g. "21 tok/s".
- **API:** `GET /v1/ai/benchmarks` and `tai benchmark --results` return the same records.

## Build order

1. **Harness and store (M):**
   - the token-timed harness for LiteRT and MNN;
   - the prompts and the check questions;
   - `benchmarks.json`;
   - `tai benchmark` moved onto the harness;
   - JVM tests for the statistics and the store.
2. **Guards (S):** battery and thermal reading, the pause and stop rules, the cool-down, per-phase time limits.
3. **Screens (M):** Home + leaderboard, Choose (with the filter), Check, Run with the live view, Cool-down, Result + history.
4. **Wiring (S):** the On-device AI row, the Model centre menu item and speed pill, the API route.
5. **Recommended set (S):** verify on pong, then publish through the remote catalogue.
