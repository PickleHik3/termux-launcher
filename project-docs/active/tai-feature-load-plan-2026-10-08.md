# Feature load plan and feature check (2026-10-08)

Status: designed with the developer on 2026-10-08 (three rounds, confirmed). Both steps go on one
feature branch, `feat/tai-feature-plan`, which is opened as a PR when step 2 is done. Terms are from
`docs/GLOSSARY.md` (Local AI): feature, feature load plan, feature check, feature group, GPU verdict,
speculative decoding.

## Why

No single place decides how an AI feature loads. The accelerator alone passes through nine modules
and three different methods pick it:

- the tier and GPU path (`TaiTierPolicy.defaultAccelerator`);
- the model profile plus failure history (`TaiLoadPreflight.autoAccelerators`);
- the user's bench results (`CategorySortLoadPolicy`).

Each caller builds its own request JSON. What follows from that:

- The Model Centre shows GPU for app sorting, and the sort loads on the CPU.
- Cleanup hard-codes speculative decoding on.
- Sorting decides speculative decoding from chat TTFT on a generic prompt.
- A tier default is sent as an explicit accelerator. The preflight then retries a GPU that already
  failed as if the user had asked for it.
- The per-function accelerator pick for the assistant reaches no load.
- Nothing reports whether speculative decoding actually ran.
- Bench GPU failures reach neither the GPU verdict nor the runtime history.

## Step 1 · the feature load plan

### Decisions

1. **Scope.** Every feature: assistant, cleanup (`TIDY_DICTATION`), app sorting (`APP_CATEGORIES`),
   Dawn search (`EMBEDDINGS`), Dawn chat, voice typing (STT), read aloud (TTS). For the CPU-only
   features the plan is mostly "this model, CPU".
2. **What the plan holds.** Where the feature runs (on-device or remote), the model, the accelerator,
   speculative decoding, the window, residency, and a reason for each value: `PICK` (the user chose
   it), `MEASURED` (this phone's feature check or failure record), `DEFAULT` (tier or GPU verdict),
   `REMOTE` (the provider routing).
3. **Precedence.** The user's pick, then what was measured on this phone for this feature, then the
   tier/GPU default.
   - Only a pick is sent as explicit, so failure history can steer everything else.
   - A pick that measures slower stays. The picker shows a one-tap offer of the faster setup.
4. **Defaults without measurements.**
   - Every feature follows the GPU verdict. Sorting's "CPU unless a speed test says otherwise" goes.
   - Speculative decoding is on whenever the model file declares support, and off only when a feature
     check measured it slower or found it never ran.
5. **Windows.** Cleanup 2048, app sorting 1024, assistant and Dawn chat 4096 (the automatic cap).
   - A resident model is reused when its window is at least the feature's.
   - Otherwise it is reloaded at the feature's window. For example, the assistant after a 2048
     cleanup load reloads once at 4096.
6. **Requests name the feature.** A request carries `"function": "<id>"` and TAI resolves the plan.
   - Callers stop sending accelerator, speculative decoding and window.
   - Dawn and CLI callers may send the same field.
   - Explicit fields an outside caller still sends count as a pick for that request.
7. **Residency is declared by the plan.**
   - Cleanup stays loaded until the idle timeout, as today.
   - App sorting unloads when its run finishes (after every uncategorised app) and does **not**
     reload the model that was resident before. The next feature loads what it needs.
8. **Feature groups.** These stay loaded together while in use:
   - voice typing + cleanup;
   - Dawn search + Dawn chat;
   - a spoken Dawn conversation (voice typing + Dawn search + Dawn chat + read aloud).

   A group member used in the last ~2 minutes is not closed by another feature's load, nor by the
   memory watch's eviction tiers. Only Android's low-memory signal (`RELEASE_ALL`) breaks a group.

   When a whole group does not fit:
   1. the chat model's window shrinks first;
   2. then read aloud stops staying resident and loads per reply;
   3. voice typing is never closed mid-dictation.
9. **Remote.** The plan decides on-device vs remote, folding in the "When to use it" routing
   (`TaiRemoteProvider.prefersRemote`, "Only when no local model fits") and per-feature remote picks
   (`remote/<id>`). This replaces "Polished goes remote whenever a provider is configured" in
   `LocalTaiVoiceTextPolisher`.
10. **Model Centre.** Each feature's row shows one line from the plan: model · CPU/GPU · the
    speed-up when measured, and a short reason ("Your choice", "Measured on this phone",
    "Suggested for this phone", "Remote · your provider").
11. **Measured means this feature's own workload.** Only a feature check of that feature changes its
    plan. Generic chat bench results count for the assistant and Dawn chat only. Load failures and
    crashes always count. Until step 2 ships, launcher features run on the defaults.

### Shape

- **A pure module, `TaiFeaturePlan`.**
  - Input: the feature, the user's picks, device facts (GPU verdict, RAM class, no-GPU), an evidence
    view, the residents and the remote routing.
  - Output: the plan with its reasons.
  - It has no Android dependencies and is tested from tables.
  - It absorbs `CategorySortLoadPolicy.decide`, the request half of `TaiCallerRequests`, the
    polisher's hard-coded values and `TaiFunctionModels.Resolution.accelerator`.
- **An evidence view,** read-only and with one typed reader. It replaces the five bench-JSON parsers
  (`CategorySortLoadPolicy.entriesFrom`, `TaiBenchLeaderboard.Row`/`bestSpeedByModel`,
  `TaiBenchSession`, `TaiBenchRunState`, `TaiCliFormatter`) at least for the plan's reads.
  - It has two adapters: files (production) and in-memory (tests).
  - Behind it: `TaiBenchStore`, `TaiRuntimeHistory` failure records, `TaiGpuVerdict`.
  - Results match on backend as well as model id.
- **Wiring.**
  - `TaiManager.runtimeOptionsFromRequest` resolves `function` through the plan before the existing
    preflight and `decideLoad`.
  - Each load records its feature on its `TaiResidency.Entry`, so groups and window reuse can be
    decided.
  - Reading device, store and bench data must stay off the main thread (`CategorySortDialogs` does
    this today and must stop).
- **Callers shrink to "do this task":** `LocalTaiVoiceTextPolisher` (warm load and request),
  `LauncherCategorySortService` and `CategorySortDialogs`, `TaiCallerRequests`, the embedding / STT /
  TTS paths, and `TaiFunctionPickerModel` / `TaiFunctionPickerSheet` for the one-line display.

### Not in step 1

- Extracting `decideLoad` and `previewMomentaryLoad` into one module.
- Removing the `TaiAcceleratorFallback` side channel.
- Deleting the native `/v1/ai/runtime/benchmark` path.

These were review candidates 4 and 5, and stay open.

## Step 2 · the feature check

1. **Where.** A "Your features" section at the top of the bench home: one row per feature in use and
   one "Check my features" button. No new tab (see the bench-audience preference: simple screens,
   real-use tests).
2. **Workloads are each feature's real request:**

   | Feature | Workload |
   |---|---|
   | Cleanup | the real Light/Polished prompt over a 150-word and a 500-word dictation |
   | App sorting | 10 real installed apps at window 1024 |
   | Dawn chat / assistant | today's chat and long-input tests |
   | Dawn search | 64 notes in Dawn's batches of 8 |
   | Voice typing | a bundled 10-second clip |
   | Read aloud | one sentence |

3. **What is compared.** The plan's current setup, plus one run with each axis flipped (other
   accelerator; speculative decoding toggled), so at most 3 runs. GPU is skipped when the phone has
   no usable GPU. The CPU-only features measure speed, load time and fit.
4. **Speculative decoding** is checked in two steps:
   1. Does the model file declare support? If not, the speculative run is skipped and the row says
      "not available for this model".
   2. Did it actually run? The runtimes (LiteRT and MNN) report this on the load result. If it was
      asked for and did not run, the row says the same and the plan stops asking for it on that model.

   It is judged on decode speed, never TTFT.
5. **When it runs.** When the user presses the button, plus a one-time offer after a feature's model
   is first installed ("Check how it runs on this phone").
6. **How results read.** One plain figure per feature plus Smooth / Usable / Slow, with per-feature
   thresholds taken from the pong runs. Examples: "a minute of speech tidied in 6 s", "0.9 s per
   app", "1.4× real time", "speaks in 0.3 s".
7. **Staleness.** A result is tied to the model file (sha or size+mtime) and the runtime version. A
   stale result is greyed, labelled "from an older version", and ignored by the plan.
8. **GPU verdict.** A feature check on the GPU sets the phone-wide verdict:
   - correct answers mark it verified;
   - wrong answers or a crash mark it failed, so every feature goes CPU-first;
   - the latest result wins;
   - "Try GPU again" clears it, as today.

   Bench GPU loads also go through the self-test (`loadWithCanary`), which they bypass today.

## Sources

- The architecture review of 2026-10-08 (scratchpad HTML, not kept).
- Pong measurements: `project-docs/reference/voice-ai/daytoday-settings-bench-2026-10-05.md`.
- Tiers: `project-docs/active/tai-device-tiers-2026-10-05.md`. Its lines that say cleanup and
  categories send speculative decoding on and leave the window Automatic are superseded here.
