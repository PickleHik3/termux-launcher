# TAI memory manager

Status: planned (2026-09-24). Owner of the "can this model load right now" decision for every
on-device model — chat LLM, embedding, and speech-to-text (see
[`whisper-voice-input.md`](whisper-voice-input.md)).

## Why

Today only the chat LLM is budgeted (`TaiLoadBudget.plan`, `TaiManager.decideLoad`). Everything
else is resident without accounting:

- Embedding runtimes (LiteRT and MNN) skip preflight and budget (`TaiManager.embeddings`), load
  lazily, never idle-unload, and their bytes are not subtracted when a chat load is sized.
- There is no list of resident models. A load that does not fit is refused (409
  `insufficient_memory`) even when an idle model could be evicted to make room.
- The pressure watch (`TaiRuntimeService`, 2 s poll) only runs while a chat model is loaded, only
  reacts to `MemoryInfo.lowMemory`, and then unloads everything.
- `MultiBackendTaiRuntime` holds one monitor for the whole native load; `cancel`, `getState`,
  `isModelLoaded` and the pressure watch block behind it, so a load cannot be cancelled and the
  watch cannot fire while memory peaks. Status requests on the service main thread can ANR.
- Fixed footprint ratios (`TaiLoadBudget`) were calibrated on one phone and never corrected by
  measurement.

A Whisper runtime added the same ad-hoc way would inherit all of this.

## Design

### 1. Residency registry (`:tai_runtime` process)

`TaiResidency` — one table of what is resident, owned by `TaiRuntimeService`:

| field | meaning |
|---|---|
| `modelId`, `kind` | `CHAT`, `EMBEDDING`, `STT` |
| `backend`, `accelerator`, `window` | window = context tokens for chat, audio seconds for STT |
| `estimatedBytes` | from the budget model at load time |
| `measuredBytes` | PSS delta across the load (below); replaces the estimate once known |
| `lastUsedMs`, `busy` | busy = generating / embedding / transcribing; busy residents are never evicted |

Every runtime registers on successful load and deregisters on unload/close. Nothing else keeps its
own notion of "loaded".

### 2. One budget for every load

`TaiLoadBudget.plan` gains a `reclaimable` input and an eviction plan:

```
free        = availMem − reserve                  (reserve = max(1.5 GiB, 15 % RAM), unchanged)
need        = estimate(model, accelerator, window)  (measured value if history has one)
if need ≤ free                        → load
elif need ≤ free + Σ evictable idle   → evict (cheapest first, below) then load
elif kind == CHAT                     → existing shrink-context / next-accelerator ladder
elif kind == STT                      → smaller STT size if installed, else refuse
else                                  → refuse
```

Eviction order (idle residents only): embeddings → idle STT → idle chat. A chat model is evicted
for an STT load only when the user explicitly asked for on-device voice input and chat is idle;
otherwise the STT request is refused and the caller falls back (voice input falls back to the
Android recognizer with a one-line notice).

Embeddings route through the same `decideLoad` + preflight path as chat.

### 3. Measurement instead of ratios alone

Measured on pong 2026-09-24 (gemma-4-e4b, `/proc/<pid>/smaps_rollup` of `:tai_runtime` vs
`MemAvailable`):

| load | MemAvailable drop | PSS growth |
|---|---:|---:|
| GPU, 4k | 2.4–4.2 GB (4 runs) | 0.76–0.93 GB |
| CPU, 16k | 0.45 GB | 0.63 GB |

**PSS cannot be the measure**: GPU buffers are not in the process's PSS, and the kgsl per-process
counters are not readable by the app. The measure is therefore system-side:

- Sample `MemAvailable` every 100 ms across the load (loads are already serialized) and record
  `before − minimum` as `measuredBytes`. It is noisy (±0.9 GB across identical GPU loads, because
  the kernel reclaims cache while the load runs) but it measures what actually matters: how close
  the phone came to the floor.
- Store it in `TaiRuntimeHistory` keyed by model + accelerator + window bucket; the next plan for
  that key uses the **largest** recorded value (+10 %), falling back to the ratio estimate. PSS is
  still recorded for CPU loads and for the STT/embedding interpreters, where it is accurate.
- `advertisedContextWindow` and `decideLoad` share one resident-credit function (today one assumes
  CPU, the other the real accelerator).

### 3a. Android's own thresholds (pong, 2026-09-24)

`dumpsys activity oom` on pong (12 GB class, 11.5 GB MemTotal): cached-app kill level
(`CACHED_APP_MAX_ADJ`, which is what `MemoryInfo.threshold` reports) is **315 MB**; the
foreground-app level is 72 MB. TAI's reserve is max(1.5 GiB, 15 % of the RAM class) = 1.8 GB on
the same phone — ~6× Android's own "low" line. With YouTube in the foreground, MemAvailable was
3.1 GB with 3.3 of 4 GB swap in use, i.e. a normal daily-driver state leaves E4B-on-GPU (≈3.5 GB)
out of reach under any reserve; E2B-on-GPU (≈2.6 GB estimate) would fit a ~0.5 GB floor but not
the 1.8 GB reserve.

### 3c. A GPU load cannot be braked mid-way (pong, 2026-09-24)

Measured with E4B `--gpu`, MemAvailable sampled every 100 ms:

- **Cancel does not interrupt native GPU initialization.** Cancel at 0.5 s: init still ran to
  11.6 s and peaked at 3.1 GB; cancel at 3 s: peak 3.5 GB, returned at 8.5 s. The memory came back
  the moment init returned (+3.1 GB within 100 ms). LiteRT-LM only lets TAI discard the engine
  after `initialize()` returns.
- **SIGKILL of `:tai_runtime` is not a prompt brake either.** Killed at 3 s with 2.3 GB taken:
  MemAvailable stayed flat for ≥ 4 s after the kill (ActivityManager logged the death at once), so
  the GPU driver frees a dead process's buffers late. ActivityManager restarted the service 1.5 s
  later (it is bound) and the load call then reported a GPU load — what re-issued it is not
  identified yet and must be before any kill-based brake is designed.
- A cancelled load used to be recorded as a GPU failure, locking the model out of the GPU for good
  (only a success overwrote the record and the preflight blocked every attempt); fixed in 6a08f0fe.

Consequence for the design: the "try GPU, watch, cancel" safety net in
`gallery-gpu-loading-comparison.md` does not work on this stack. Prevention is the only safety for
a GPU load, so the GPU budget must stay a real admission check — it can drop the fixed 1.8 GB
reserve for a floor tied to Android's own threshold (§3a) only once the GPU estimate is the
**measured worst case** per model/window (§3), not the ratio estimate.

### 3b. Runtime baseline

After its first chat unload, `:tai_runtime` keeps ~330 MB of anonymous memory (15 MB before the
first load); three load/unload cycles held it flat at 329–336 MB, so it is not a leak but memory
the LiteRT-LM / driver allocators keep. The registry counts it as a `RUNTIME` resident once a chat
model has been loaded, and when nothing has been resident for the idle timeout the service stops
itself and the process exits, returning it. Keep-warm and an active API client keep the process.

### 4. Pressure handling

Replace the lowMemory-only poll with a tiered watch that runs while **anything** is resident:

| condition | action |
|---|---|
| `availMem < reserve` | evict idle embeddings, then idle STT |
| `availMem < threshold × 1.25` | also evict idle chat |
| `lowMemory` | cancel in-flight work and unload all (today's behaviour, now the last tier) |

Add `onTrimMemory` to `TaiRuntimeService` (pre-API-34 `RUNNING_LOW`/`CRITICAL` map to tiers 1/2;
`TRIM_MEMORY_UI_HIDDEN` is ignored — backgrounding the launcher must not drop a warm model).

Idle timers: embeddings unload after 5 min idle, STT after 2 min (configurable in TAI settings),
MNN chat gets the same keep-warm expiry as LiteRT chat.

### 5. Locking

Split the router monitor:

- a **load lock** held only across native load/close, and
- a **state snapshot** (volatile, immutable) read by `getState`, `isModelLoaded`, status and the
  pressure watch without any lock.

`cancel()` sets the runtime's cancel flag directly and never waits for the load lock, so LiteRT's
load-cancel works through the router. `embed()` no longer holds the router monitor during
inference.

### 6. Termux sessions

No per-session accounting: `availMem` already reflects them. What changes is that the watch keeps
running, so a build or a proot distro that grows RAM mid-session evicts idle models in tiers
instead of waiting for the system `lowMemory` flag.

### 7. Visibility

TAI settings gets a "Memory" row: resident models with measured/estimated size, free vs reserve,
and an "Unload all" action. `tai status` / `/v1/status` return the same table.

## Phases

1. **Done 2026-09-24 (7c562d08).** Lock split: `loadLock` guards load/keep-warm/unload only; the
   active backend is a volatile pointer, so status, cancel and the pressure watch reach the
   backend's own short monitor. A router-level state snapshot was deliberately not added — backends
   change state (generation, idle unload, keep-warm expiry) without passing through the router, so a
   snapshot would go stale. Device-verified on pong 2026-09-24: `tai cancel` 0.3 s into an E4B load answered in 42 ms and the load returned `model_load_cancelled` at 735 ms; status during the load answered in ~50 ms.
   Also: the 2026-09-24 probe lost pong's network while a CPU 16k E4B generation ran; nothing was
   killed, but a re-run with logcat captured belongs in this phase's device check.
2. **Done 2026-09-24 (in dev), device-verified on pong:** E4B chat + EmbeddingGemma + Qwen3
   Embedding MNN listed as residents (1799 / 227 / 360 MB estimated, plus the 330 MB runtime entry);
   `tai unload` leaves only the runtime entry. The check found downloaded MNN specs record
   config.json's length as `sizeBytes`; `TaiResidency.fileBytes` now sums the package on disk and
   sizes chat loads too (f30d76ff). `TaiResidency`: an immutable table behind a volatile field, writes
   under its own leaf monitor, reads lock-free (never the router's load lock). Owned by
   `MultiBackendTaiRuntime` rather than the service as planned above, because it has to be handed
   to the backends: LiteRT and MNN chat register in their load-success block and deregister in
   `closeEngineLocked` / `releaseSessionLocked`, which every close funnels through (unload, idle
   timer, keep-warm expiry, the close before a replacing load, the pending unload after a cancelled
   generation, and the service's low-memory release). Both embedding runtimes register in
   `ensureLoaded` and deregister in `close()`; `busy`/`lastUsedMs` bracket generation and embed
   batches. The first chat load adds the 330 MB `RUNTIME` entry, which never deregisters (the
   process-exit half of §3b is phase 4). Budget: `free = availMem + Σ(residents this load replaces)
   − reserve`, where a chat load is credited every CHAT resident and an embedding load only its own
   backend's EMBEDDING resident; nothing else is credited (`TaiResidency.creditedAvailable`).
   `decideLoad` and `advertisedContextWindow` now share that credit — the runtime process publishes
   `residentChatBytes` in the presence snapshot, replacing the app-side CPU guess. Embeddings go
   through `TaiLoadPreflight` and `TaiLoadBudget.planFixed` (estimate = file × 1.3 for LiteRT
   `.tflite`, × 1.0 for MNN, until phase 3 measures) the first time a model is requested, and are
   refused with the chat path's 409 `insufficient_memory` (OpenAI-wrapped). `tai --json runtime`
   carries a `residents` array (id, kind, backend, accelerator, window, estimatedBytes,
   measuredBytes, busy, lastUsedMs); the app process forwards it from the runtime's status reply.
   Deviations: the estimate a chat resident registers with uses the accelerator the engine actually
   came up on (so a GPU→CPU fallback is booked at the CPU footprint), not the plan's; a chat load
   is credited the resident chat model even when it is the same model, since the backend closes it
   before reloading. Device check pending: `tai --json runtime` residents with chat + embedding
   loaded, and that the MNN embedding package passes the preflight's `llm.mnn` sidecar checks.
3. **Done 2026-09-24 (in dev), device-verified on pong:** E4B auto at 5.3 GB free planned GPU
   4096 on the ratio estimate (3.53 GB + 0.88 GB margin; the old 1.8 GB reserve sent this to CPU),
   measured 2.64 GB; the reload planned from the measurement (3.05 GB, no margin); EmbeddingGemma
   loaded beside it (measured 126 MB vs 227 MB estimated) with nothing evicted. Follow-up fix
   b77a96c5: only an STT load may evict idle chat, never an embedding load. Measurement: `TaiLoadMeter` samples
   `MemoryInfo.availMem` every 100 ms on a helper thread across native init only (LiteRT engine
   init, MNN session load, both embedding interpreters — the benchmark uses the same meter) and
   `before − minimum` lands on the resident's `measuredBytes` and in `TaiRuntimeHistory` under
   `load|<base model>|<device>|<backend>|<accelerator>|<window bucket>` (bucket = window rounded up
   to a power of two, 0 for embeddings), keeping the largest value per key; cancelled and failed
   loads record nothing. Budget: the estimate is the recorded worst case × 1.1 when the key has one
   (`estimateSource: "measured"`), else the ratio (`"ratio"`), now split into non-reclaimable and
   reclaimable — LiteRT CPU counts only the KV cache plus 5% of the file as anonymous, since its
   weights are mapped file pages (E4B CPU 16k: 0.45 GB measured vs 4.25 GB estimated before);
   GPU and MNN stay fully non-reclaimable. The reserve became `floor = max(2 × MemoryInfo.threshold,
   512 MiB)` (pong: 630 MB) plus a 25% margin of a ratio estimate, none for a measured one; the old
   max(1.5 GiB, 15%) reserve remains only when the threshold is unknown (`TaiDeviceCapabilities.
   memoryThresholdBytes`). An automatic GPU window is capped at 4096 (`GPU_AUTO_CONTEXT`); an
   explicit setting or `context_window` above it is honoured, subject to the budget. Eviction: at
   every ladder step a load that does not fit is retried with idle residents evicted in the order
   embeddings → STT → idle chat (chat only for non-chat loads; a chat load replaces chat and is
   credited it), shortest covering prefix, never a busy resident, then the window shrinks / the
   next accelerator at the floor, then refusal. `TaiResidency.evictionCandidates` orders them
   (LRU within a kind), `MultiBackendTaiRuntime.evict` closes them through their runtimes under the
   load lock (skipping any that became busy or left since the plan), and the load result carries
   `evicted: [...]` plus `memoryBudget.{estimateSource, reclaimableBytes, marginBytes, evicted}`.
   Bug fixed: LiteRT's auto GPU→CPU fallback re-ran the CPU with the GPU plan's window; it now runs
   at min(window, 4096) with the accelerator set to cpu and re-checks the budget for the CPU against
   what is free after the GPU attempt (a refusal answers 409 `insufficient_memory` and is not
   recorded as a CPU failure). Deviations from the text above: PSS is not recorded at all (only
   MemAvailable, the one measure that covers GPU); eviction runs before the context shrinks at each
   step rather than only once before the ladder, because an idle embedding model is cheaper to
   drop than half a window; idle chat is evictable by an embedding load as instructed for this
   phase (§2's "only when the user explicitly asked" rule is left for the STT phase); the
   `advertisedContextWindow` path shares floor, history and GPU cap but does not model evictions.
   Device check pending: a `tai load` with an embedding resident short of memory shows `evicted`;
   two E4B GPU loads record a growing `bytes` under the `load|…|gpu|4096` key; a GPU failure with
   an 8k plan falls back to CPU 4k.
4. **Done 2026-09-24 (in dev), idle paths device-verified on pong:** E4B + EmbeddingGemma loaded
   21:09; `idle: evicted embedding embeddinggemma-300m (95 MB, last used 301 s ago)` at 21:14:18;
   chat idle-unloaded by its 10-min timer ~21:20 (MemAvailable 2.7 → 5.9 GB); `idle exit: …
   asking the client to unbind` + `unbound, process exiting` at 21:30:20, `:tai_runtime` gone, no
   crash reported. The pressure tiers are covered by unit tests only (not provoked on the daily
   driver). The watch (`TaiRuntimeService`, 2 s) runs
   while anything is resident — chat loaded / loading / warm, or any EMBEDDING / STT entry — and
   decides from one `MemoryInfo` reading per tick (`TaiPressureWatch.tier`): `lowMemory` → tier 3,
   cancel in-flight work and unload everything (the old behaviour); else `availMem < threshold ×
   1.25` → tier 2, idle chat may go; else `availMem < floor` (`TaiLoadBudget.floorBytes`, pong
   630 MB) → tier 1, idle embeddings then idle STT. Tiers 1–2 give up **one** resident per tick —
   embeddings → STT → chat, LRU within a kind, never a busy one, never RUNTIME — and look again
   2 s later, because MemAvailable is noisy and memory comes back late (§3c). The eviction goes
   through `MultiBackendTaiRuntime.evict` on its own thread (`tai-runtime-pressure`, one action
   queued at a time), so neither the watch tick nor the cancel/unload lane waits behind the load
   lock; `evict` now re-reads each victim and skips one that became busy (embeddings included).
   `onTrimMemory`: `RUNNING_LOW` → tier 1, `RUNNING_CRITICAL` → tier 2, every other level ignored.
   Idle timers on the registry's `lastUsedMs`: embeddings 5 min, STT 2 min
   (`TaiPressureWatch.EMBEDDING_IDLE_MS` / `STT_IDLE_MS`; constants, no setting this phase), so the
   Whisper runtime gets its timer by registering as STT. MNN chat gained LiteRT's scheduler
   (`tai-mnn-idle`): the idle-unload setting and keep-warm expiry now release the session (before,
   the keep-warm only stopped being reported), `keepWarm` on the loaded model extends instead of
   reloading, and status reports `idleUnloadAtMs` / `idle-warm`. Runtime baseline (§3b): when only
   the RUNTIME entry remains, no request is in flight and 10 min (`IDLE_EXIT_MS`) have passed since
   the later of the last non-status request and the last model leaving, the service sends
   `MSG_IDLE_EXIT` to the last client's reply Messenger; `TaiRuntimeServiceClient` unbinds — only
   when it has nothing pending, otherwise it ignores the notice and the service asks again after its
   next idle period — the service is destroyed, and `onDestroy` calls `Process.killProcess` on
   itself, on that announced path only (any other destroy leaves the process to Android, so a
   restarted UI process still finds its model). No binder-death callback follows an unbind, so the
   client never reports `tai_runtime_crashed`; the crash marker is only set inside a load, which the
   exit check refuses to overlap. The client now registers and sends a request under its connection
   lock so a notice cannot slip between the two. Status reads (`status` / `runtimeStatus`) do not
   count as activity, so a UI poll does not keep the baseline alive; the next request binds a fresh
   process. Every action logs at info under the `TaiMemory` tag (`adb logcat -s TaiMemory`).
   Deviations: the service reaches the router through `MultiBackendTaiRuntime.processInstance()`
   (TaiManager exposes no handle and was not to change); one victim per tick rather than a whole
   tier at once; the STT timer is not in settings yet (left for phase 6 with the memory row).
   Device check pending: with E4B + EmbeddingGemma resident, a Termux job that eats RAM logs
   `pressure tier 1` then `tier 2` lines with `availMem`; an embedding left alone 5 min logs
   `idle: evicted embedding …`; ten minutes after `tai unload` the `:tai_runtime` pid is gone and
   the next `tai status` starts a new one.
5. STT kind (lands with the Whisper runtime).
6. Memory row in settings and status.

## Tests

- `TaiLoadBudget` eviction planning: pure JVM tests with synthetic resident tables (fits / fits
  after eviction / busy resident not evicted / STT refused rather than evicting a busy chat).
- Registry + lock split: Robolectric tests that `getState` and `cancel` return while a fake load is
  blocked.
- Device check on pong: E4B chat loaded + embedding + STT, then a heavy Termux job; watch tiers fire
  in order via logcat.
