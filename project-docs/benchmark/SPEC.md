# Model benchmark: spec (bench_v2)

Written 2026-09-28 (v1), reworked 2026-09-29 (v2). Review page: `.lavish/model-benchmark.html` (gitignored).

A screen inside On-device AI that shows which models this phone runs well. It offers only models
that can run here, warns before it starts, runs the same fixed tests on each model with a live
view of the reply, keeps every result, and ranks them on a leaderboard that updates as each model
finishes.

The audience is early-to-moderate power users experimenting with models, not local-LLM hobbyists.
The rule for v2: as simple as the screen and the test can be made, representing real use.

## v1 → v2

- v1 measured five things (reading tok/s, first word, writing tok/s over 128 tokens, sustained
  over 90 s, check) at three presets, on both processors by default, and ranked by the writing
  median. Nobody uses a phone that way.
- v2 has three tests that read as everyday sentences: **Chat** (starts replying in X s, writes N
  tok/s), **Long input** (reads a long page in X s) and **Sanity** (silent unless it fails).
- Thorough and the sustained run are gone. The presets are Quick (1 run) and Standard (2 runs,
  the median is the mean of the two). CPU and GPU are compared only when asked (the "Compare CPU
  and GPU" switch, `--compare`); the default is the processor an automatic load would pick.
- Memory is the peak PSS of `:tai_runtime` sampled while the long input is read, not the PSS after
  the warm-up.
- One verdict word (Smooth, Usable, Slow, Broken) from three figures, and one ranked list. The
  Speed / First word / Memory tabs are gone.
- `BENCH_VERSION` is `bench_v2`. v1 records stay in `benchmarks.json` but the version filter
  never ranks them, so those models show as untested. The store reads them (their key and
  version) and nothing else.
- Kept from tonight's v1 fixes: live figures in every phase with the median held once it lands,
  `finishReason` and the "stopped at the N-token limit" label, thinking tokens emitting events, the
  LiteRT CPU context cap, and the safe cancel.

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Default preset | Standard |
| 2 | Downloads | Installed + recommended, with "remove afterwards" |
| 3 | Leaderboard | This phone only; nothing is uploaded |
| 4 | Charging | Allowed; the result is marked "charging" |
| 5 | Verdicts | See "Verdict"; thresholds are named constants in `TaiBenchStats` |
| 6 | Entry points | On-device AI › Models zone row, and Model centre › ⋯ menu (that model only). Not the importer. |
| 7 | Eagle3 | Off by default for MNN; "Draft model (experimental)"; no "Fast" tag; importer prefers the plain build |
| 8 | Processors | The one an automatic load picks; both only with "Compare CPU and GPU" |

## Tests

There is one harness for both backends in `:tai_runtime`. It times each token via the generation callback, so LiteRT and MNN are measured the same way. It does not use LiteRT `benchmark()`. Every generation is greedy (top-k 1, temperature 0), thinking is off, and each prompt gets a fresh conversation.

| Phase | Runs | Prompt and cap | Measures | Quick | Standard |
|---|---|---|---|---|---|
| Load | Cold load after unload, through the budget | – | Load ms; MemAvailable drop (`TaiLoadMeter`, internal fallback) | ✓ | ✓ |
| Warm-up | One short reply, discarded, not shown | "Say hello.", 8 tokens | – | ✓ | ✓ |
| Chat | Repeated | "Explain what a shell alias is and give two useful examples.", 320 tokens | TTFT ("starts replying in X s"); decode tok/s, first to last token ("writes N tok/s") | 1× | 2× |
| Long input | Repeated | The bundled build log (`assets/tai/bench/build_log.txt`, about 8 KB, about 2000-2700 tokens) then "What went wrong, in two sentences?", 96 tokens | TTFT of this prompt ("reads a long page in X s"); prompt tok/s (internal); peak PSS | 1× | 2× |
| Sanity | 3 fixed questions with known answers, 32 tokens each | `17 + 25`, a fixed JSON object, repeat "pineapple" | Pass/fail (catches builds that write nonsense) | ✓ | ✓ |

- **Median:** the reported figure of a test is the median of its runs; with Standard's two runs that is the mean of the two.
- **Long input fits the window.** The harness reads the loaded window from the load's memory budget (`memoryBudget.contextWindow`). When the log plus the 96-token cap plus 128 tokens of overhead does not fit at 3 characters a token, the log is cut from the top at a line boundary (the error is at the end), at least 512 characters are kept, and the record says `truncated`. A 4096-token window holds the whole log.
- **Memory:** the runtime process's PSS is polled every 250 ms for as long as the Long input phase runs; the maximum is `phases.longInput.peakPssBytes` and is shown as "Memory: X GB". The MemAvailable drop of the load stays in `phases.load.memBytes` and is used only when no peak was sampled.
- **Time limits:** a phase is stopped at three times its expected time and marked `timeout`. Expected: load 90 s, warm-up 30 s, chat 100 s (320 tokens at 3.5 tok/s), long input 120 s (about 2700 prompt tokens at 30 tok/s plus a short reply), sanity 20 s per question set.
- **Rough time per model and processor:** Quick 1.5 min, Standard 3 min, plus the cool-down.
- **Self-heal:** an MNN entry whose sanity replies are all degenerate (one repeated character, whitespace or nothing) has its mmap weight cache cleared and runs once more from the load.
- **Versioning:** results from another bench version are kept but not ranked together.

## Verdict

One word per model and processor. All bounds are inclusive; a figure that was not measured clears no line.

| Verdict | Rule |
|---|---|
| Broken | A sanity question failed, whatever the speed |
| Smooth | decode ≥ 12 tok/s **and** chat TTFT ≤ 1.5 s **and** long-input read ≤ 8 s |
| Usable | decode ≥ 6 tok/s **and** chat TTFT ≤ 3 s **and** long-input read ≤ 20 s |
| Slow | Otherwise |

Each threshold is a named constant with a one-line rationale in `TaiBenchStats`
(`SMOOTH_DECODE_TPS`, `SMOOTH_TTFT_MS`, `SMOOTH_READ_MS`, `USABLE_*`). The leaderboard works the
verdict out from a record's figures instead of trusting the stored word, so retuning a constant
re-grades every v2 record on file.

## Leaderboard

- One entry per model + backend + processor (+ Eagle on/off), one list.
- Ranked by verdict (Smooth, then Usable, then Slow), then decode tok/s, then chat TTFT. Broken entries are listed separately at the bottom, unranked.
- Each row: model name, processor, a verdict chip, the three plain numbers ("Starts in 0.6 s · 14 tok/s · Reads long page in 4 s") and "Memory: X GB".
- A new result appears the moment its model finishes.
- Runs are marked: charging, warm start, low battery, older app or runtime version.

## Screens

1. **Home.**
   - A device card: SoC, GPU, RAM class, free RAM, free storage, battery, heat.
   - The one leaderboard list.
   - A "Run a benchmark" button, and when the last run was.
2. **Choose.**
   - The Quick / Standard selector and the "Compare CPU and GPU" switch (shown where the phone has a usable GPU).
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
   - A "Test 1 of 3: Chat" card with the model and processor.
   - ONE live dial for the current test: decode tok/s while the chat writes; a "reading… N s" counter while the long page is read, then how long it took. The median is held once it lands.
   - The phone's conditions (free RAM, heat, battery).
   - A live view: the prompt in grey, the streaming reply in monospace, a token counter. It can be folded away.
   - A stepper per model and phase (load, warm-up, chat, long input; the sanity check is silent).
   - Stop keeps the phases that finished.
5. **Cool-down.** A countdown until the heat has recovered. The model that just finished shows on the leaderboard with its rank and a "New best" pill. "Skip the wait" marks the next result "warm start".
6. **Result.**
   - The verdict chip, the three numbers and the memory.
   - Details: each figure with its range over the runs, load, the sanity check when it failed, conditions, versions.
   - The Chat reply, collapsed.
   - A history chart of decode speed, split at app or runtime version changes.
   - "Run this model again".

Style: the Model centre's tonal cards, monospace detail lines and pills (`TaiModelCentreAdapter`, `item_tai_centre_model.xml`). Motion through `TaiMotion`, which respects the animations-off setting.

### Screens as built

`app/fragments/settings/termux/TaiBench*`: `TaiBenchHomeFragment` (Home; its static `open(Activity,
modelId)` is the one entry point the wiring calls — it lands on Run while a run is going, on
Choose with only that model selected when a model is named, on Home otherwise),
`TaiBenchChooseFragment`, `TaiBenchCheckSheet` (a bottom sheet), `TaiBenchRunFragment` (Run and
Cool-down), `TaiBenchResultFragment`. All hosted by `SettingsActivity` like the Model centre.

- **A run outlives the screen.** `TaiBenchSession` (app-scoped) owns the worker that downloads,
  calls `TaiManager.benchRun` and reduces the stream into one `TaiBenchRunState` on the main
  thread; the Run screen only attaches to it. Re-opening the benchmark while a run is going
  shows the Run screen; Home shows a "Benchmark running" banner meanwhile.
- **`TaiBenchRunState` is pure.** No clock and no Android type: `dial(nowMs)` takes the time as a
  parameter, and the Run screen redraws the reading counter every 500 ms.
- **Downloads in a run:** the selected "Worth a download" models are downloaded first (shown as
  leading steps in Run), then benched; a failed download skips its model with the reason. With
  "remove afterwards" on, the downloaded models are deleted after the run; their results stay,
  marked "not installed".
- **Keep the screen on:** `FLAG_KEEP_SCREEN_ON` while the Run screen is visible.
- **Live view:** `TaiBenchLiveView` is in the voice views' look; `VoiceTranscriptPanel` was not
  reused because its as-heard/cleaned versions, typewriter reveal and Undo/Copy/Insert pill have no
  use for a benchmark reply that is set whole at 20 Hz. The v1 sparkline is gone with the tiles.
- **Estimated time:** `TaiBenchSuite.estimateMs(preset, processors)`. The Choose screen counts two
  processors when Compare is on and the phone's LiteRT GPU path is available
  (`supportsAccelerator("gpu")`); the runtime's per-model preflight may still leave the GPU out, so
  the estimate is an upper bound.
- **Older version mark:** a record whose `appVersion` differs from this build, or whose
  `runtimeVersion` differs from this build's LiteRT-LM or MNN version (by the record's backend).
- **Low battery mark:** a record that started under 30% and not on the charger (a forced run).

## Events and records

The harness streams JSON events (`TaiBenchHarness`), each with `at`, the wall-clock millisecond:

| Event | Carries |
|---|---|
| `entry_start` | `index`, `total`, `entry{modelId, backend, accelerator, speculative, key}` |
| `phase_start` | `phase` (`load`, `warmup`, `chat`, `longInput`, `check`), `runs`, `prompt`; for `longInput` also `truncated`, `keptChars`, `totalChars` |
| `run_start` | `phase`, `run`, `runs`: one per generation, when it is submitted (the Run screen's reading counter starts here) |
| `token` | `phase`, `run`, `runs`, `text`, `tokens`, `tps`, `ttftMs`: at most one per 50 ms; a stretch of thinking has empty text and still advances `tokens` |
| `check_start` | one per sanity question: `run`, `name`, `prompt` |
| `phase_done` | `phase`, `status`, `reply`, `finishReason`, `reasoningTokens`, `tokenLimit`, `metrics` (chat: `{ttftMs, decodeTps, tokens}`; longInput: `{readMs, promptTps, promptTokens, contextWindow, truncated, keptChars, totalChars, peakPssBytes}`; each figure is `{med, min, max, runs}`) |
| `paused`, `skipped`, `error`, `cache_rebuilt` | as in v1 |
| `entry_done` | `record`, the store's record (below) |
| `done` | `{ok, benchVersion, preset, entries, records, skipped, stopped}` |

The request to `benchRun` is `{models, preset: quick|standard, compare?, processors?, eagle?, force?}`;
`thorough` is refused with `bad_preset`.

**Record layout (bench v2):** `id, benchVersion, preset, timestamp, durationMs, modelId, displayName,
sizeBytes, sha256, backend, accelerator, speculative, runtimeVersion, appVersion, device{soc,
ramClassGb}, conditions{batteryStart, batteryEnd, charging, thermalStart, thermalEnd,
headroomStart, headroomEnd, warmStart}, phases, check{passed, total, details}, status, verdict`,
with

```
phases: {
  load:      {ms, memBytes},
  chat:      {ttftMs{med,min,max,runs}, decodeTps{…}, tokens, reply, finishReason, reasoningTokens, tokenLimit},
  longInput: {readMs{…}, promptTps{…}, promptTokens, contextWindow, truncated, keptChars, totalChars,
              peakPssBytes, finishReason, reasoningTokens, tokenLimit}
}
```

Removed from v1: `phases.reading`, `phases.firstWord`, `phases.writing`, `phases.sustained`,
`phases.load.pssBytes`. The leaderboard row is `{key, recordId, modelId, displayName, backend,
accelerator, speculative, timestamp, preset, decodeTps, ttftMs, readMs, truncated, loadMs,
memBytes, checkPassed, verdict, conditions, runtimeVersion, appVersion, rank}`; `memBytes` is
`TaiBenchStore.memoryBytes(phases)`: the peak PSS, else the load's MemAvailable drop.

## Safety

| Condition | Source | Before start | While running |
|---|---|---|---|
| Battery | `BatteryManager` | ≥ 30% or charging | Stop below 15% |
| Heat | `PowerManager.getCurrentThermalStatus` (29+), `getThermalHeadroom` (30+), listener | NONE or LIGHT | MODERATE: pause until headroom recovers. SEVERE+: stop and unload |
| Memory | `TaiLoadBudget`, `TaiPressureWatch` | Each model must pass the budget, or it is skipped with the reason | Pressure tiers unload; marked "stopped: memory" |
| Storage | `File.getUsableSpace` | Downloads fit, plus 10% | – |
| Screen/app | Keep-screen-on; runtime ops already run in the foreground | "Keep this screen open" | Leaving pauses at the end of the current step (`stopped: left` past 30 min); Resume on return |
| Time | Per phase | – | Stop a phase at 3× its expected time |

The guards are `TaiBenchGuardRules`, `TaiDeviceConditions` and `TaiBenchConditionsGuard`, unchanged
by v2. The runtime process reads battery and thermal state (it is where the harness runs); the app
process is still the only writer of `benchmarks.json`, copying each record's `conditions` field as
it arrives. Between entries, a later model waits (cool-down, polled every 2 s) for thermal status
to return to the run's starting reading and headroom to come back within 0.05 of the start's —
capped at 5 minutes, after which the run proceeds and that entry is marked `warmStart`; "Skip the
wait" ends the cool-down (and marks the same way) at once. The battery-stop and SEVERE+
thermal-stop rules still apply during a cool-down and win over it. `force: true` on the request
(`tai benchmark --force`) skips only the before-start check, not the while-running rules; `tai
benchmark --skip-wait` is the CLI's "Skip the wait".

The Run screen's own hold (`TaiManager#holdBench`, `TaiRuntimeIpc#OP_BENCH_HOLD`) is how "Leaving
pauses…" is implemented: the screen holds the guard in `onStop` (unless the activity is only
changing configurations) and releases it in `onStart`. While held, `beforePhase` pauses with
reason `left`, polled every 1 s, checked ahead of the cool-down and thermal pauses but after the
battery/SEVERE-thermal stop rules, which still win. Held past 30 minutes total stops the run as
`stopped: left`. A `tai benchmark` run from the terminal is never held.

## Which models are offered

The filter:
1. RAM: `TaiImportFit` YES is shown, SLOW is shown as "Tight", TOO_BIG is hidden.
2. Storage: the size plus 10% must fit in free space.
3. Chip: builds for another chip's NPU are dropped (`TaiImportProfiles.socMatches`).
4. MNN needs `mnnSupported`.
5. GPU runs only where `supportsAccelerator` allows.

The recommended downloads come from the signed remote catalogue (`TaiRemoteCatalog`), so the list changes without an app release. Full per-model source/revision, sizes, verification steps and the signing/publish process: `project-docs/benchmark/recommended-set.md`; draft payload: `project-docs/benchmark/recommended-catalog-draft.json`.

## Storage

- **File:** `files/tai/benchmarks.json`, written by the UI process after the runtime replies (one writer).
- **A record holds:** the model (id, file size and hash); how it ran (backend, processor, Eagle on/off, runtime version, app version, bench version, preset); the device (SoC, RAM class); the conditions; the phases above; whether the sanity check passed; and a timestamp.
- **Kept:** the last 20 runs per leaderboard entry. Deleting a model keeps its results, marked "not installed". Old-version records stay until their entry is trimmed or cleared.
- **Model centre:** each row shows its best recent decode speed as a quiet pill, e.g. "21 tok/s".
- **API:** `GET /v1/ai/benchmarks` and `tai benchmark --results` return the same records.

## CLI

`tai benchmark [model...] [--preset quick|standard] [--cpu|--gpu] [--compare] [--eagle] [--force]`,
`tai benchmark --results | --clear [model] | --skip-wait`, and `tai benchmark --native ...` (the
Gallery mirror, untouched by v2). `--thorough` is gone.
