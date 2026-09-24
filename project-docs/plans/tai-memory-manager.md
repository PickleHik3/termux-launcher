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
2. **Done 2026-09-24 (in dev).** `TaiResidency`: an immutable table behind a volatile field, writes
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
3. Eviction planning in `TaiLoadBudget` + PSS measurement and history correction.
4. Tiered pressure watch, `onTrimMemory`, idle timers.
5. STT kind (lands with the Whisper runtime).
6. Memory row in settings and status.

## Tests

- `TaiLoadBudget` eviction planning: pure JVM tests with synthetic resident tables (fits / fits
  after eviction / busy resident not evicted / STT refused rather than evicting a busy chat).
- Registry + lock split: Robolectric tests that `getState` and `cancel` return while a fake load is
  blocked.
- Device check on pong: E4B chat loaded + embedding + STT, then a heavy Termux job; watch tiers fire
  in order via logcat.
