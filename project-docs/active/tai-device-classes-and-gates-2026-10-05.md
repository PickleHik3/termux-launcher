# TAI device classes and memory gates

Status: research proposal, 2026-10-05, against dev `8411d209c`. Read-only. Nothing was built, run or
installed. App claims cite the source at that commit. Platform claims cite AOSP source at a release tag,
or the owner's docs. Model claims cite the model card or the runtime source. Numbers measured on pong
cite the note and its date. A number marked **derived** is my arithmetic, and the arithmetic is shown.
Anything I could not check is listed under Unverified.

Paths are short: `ai/` is `app/src/main/java/com/termux/ai/`, `living/` is
`app/src/main/java/com/termux/app/chrome/wallpaper/living/`. Source keys such as [A1] are listed under
Sources.

Read first: `local-ai-and-living-wallpaper-review-2026-10-04.md` (T1–T14), `tai-memory-manager.md`,
`animated-wallpaper/director-comparison-2026-10-04.md`.

## Summary

### Proposed classes

The class comes from `totalMem` rounded up to a RAM size (§2). The class sets **ceilings and defaults**.
Every load still has to pass the live gate (§4), so a class never forces a load.

| Class | RAM class (from `totalMem`) | Chat / cleanup | Living-still director | Windows (auto / most) | Voice and the rest alongside an LLM |
|---|---|---|---|---|---|
| **Lite** | ≤ 4 GB (HTC hub: 3.4 GB, Android 9) | No automatic local LLM. Qwen3 0.6B or E2B on the CPU only when the user picks them, at 2048. | Remote, else rules only | 2048 / 2048 | Silero VAD, Whisper base, KittenTTS. No LLM resident at the same time. |
| **C6** | 6 and 8 GB | E2B on the GPU (recommended on 8 GB, on demand on 6 GB). E2B on the CPU only with a measured cost that covers the first prefill. E4B only when the user picks it. | E2B `-vision`, GPU, 2048, momentary. Then remote, then rules. | 4096 / 4096 | One LLM. Alongside it: Silero and Whisper base. Kitten only if it fits. Parakeet, embeddings and vision graphs are evicted first. |
| **C12** | 10 and 12 GB (pong) | E2B on the GPU at 4096. E4B on the GPU at 4096 when its measured cost fits. CPU ≤ 4096. | E4B `-vision` on the GPU at 2048. Then CPU 2048 if a measured CPU cost fits. Then E2B on the GPU, then remote, then rules. | 4096 / 8192 | One LLM plus the voice stack (Silero, Whisper small or Parakeet, Kitten) plus one embedding model, all under the hold floor. A director run evicts idle voice and embeddings first. |
| **C16** | ≥ 16 GB | E4B on the GPU at 4096 by default, up to 8192 on request. CPU ≤ 8192 with a measured cost. | E4B `-vision` on the GPU at 2048, with the voice stack left resident | 4096 / 16384 | One LLM, the full voice stack and embeddings resident together. |

### Proposed gate, in a few lines

1. Read free memory as `MemAvailable`. That is `MemoryInfo.availMem` only on Android 16. On Android 9–15
   `availMem` is MemFree + Cached, which reads high. Classify by `totalMem`, never by `advertisedMem` (§2, §4.1).
2. Stop taking the floor from `MemoryInfo.threshold`. On current AOSP that value is the cached-app minfree
   level, about 216 MiB on any modern phone. lmkd in PSI mode does not use it. Pong's real floor today is
   512 MiB, not the documented 630 MB. Use two floors per class instead: a **peak floor** for momentary
   loads and a **hold floor** for anything that stays resident. Add penalties when swap is low or when the
   launcher is not in front (§4.2–4.3).
3. The cost model is per model: resident weights + a per-token slope + an encoder delta, each per backend
   and accelerator. Seed it from the model card or the architecture. Then replace it with measured values.
   A measurement covers the load **and the first prefill**. It is looked up by the nearest window at or
   above the request, interpolated between windows, and keyed by modality. Each key keeps a ring of recent
   samples instead of an all-time maximum (§4.4–4.5).
4. The gate corrects its own floor from `ApplicationExitInfo` low-memory kills of the app's own processes (§4.6).
5. On a refusal, the steps are: evict, a smaller window, the fallback model, remote, wait and retry for
   background jobs, then refuse. Falling back from GPU to CPU is **not** a memory saving for Gemma 4
   (§3, §4.7).

### Top three changes

1. **The floor.** Replace `max(2 × threshold, 512 MiB)`, and the momentary 1 GiB cap that does nothing,
   with the class's peak and hold floors and the swap and background penalties. `ai/TaiLoadBudget.java:123-126,340`.
2. **History that generalises.** Use the nearest bucket ≥ the window, then interpolation. Key by modality.
   Keep a ring of the last 8 samples with expiry. Measure through the first prefill on every backend,
   which lets CPU LiteRT be trusted again. `ai/TaiRuntimeHistory.java:259-322`, `ai/TaiLoadBudget.java:160-194`,
   `ai/TaiLoadMeter.java:9-19`.
3. **Per-model cost seeds instead of `file/19000`.** The ratio's per-token term is about 12× the theoretical KV
   cache for Gemma 4, and that happens to match what LiteRT actually allocates. For Qwen3 0.6B on MNN it is
   about 5× **too small**, and MNN gets the 32k RAM-tier window on pong (§3.3).

---

## 1. What the gate does today (short)

- `plan` walks a ladder. First the asked accelerator at the wanted window, halving down to the floor
  window (`min(4096, cap)`). Then the next accelerator, at the floor window only. A load fits when
  `estimate + margin ≤ availMem − reserve`, after crediting the chat model it replaces and evicting idle
  residents (`ai/TaiLoadBudget.java:336-381`; `ai/TaiManager.java:3042-3101`).
- Reserve: `max(2 × MemoryInfo.threshold, 512 MiB)`. When the threshold is unknown it is
  `max(1.5 GiB, 15 % of totalMem)`. A momentary load uses `max(512 MiB, min(reserve, 1 GiB))`
  (`ai/TaiLoadBudget.java:115-126,339-340`).
- Estimate: the largest measured drop for the exact window bucket, + 10 %. Otherwise the ratio:
  GPU fixed = 3/4 of the file, LiteRT CPU fixed = 5 % of the file, MNN fixed = the whole file, plus
  10 % of the file for encoders, plus `file/19000` bytes per token. A ratio estimate gets a further 25 % margin
  (`ai/TaiLoadBudget.java:93-99,160-194,330-333`).
- RAM class: `totalMem` rounded up to one of {2, 3, 4, 6, 8, 10, 12, 16, …} GiB (`ai/TaiLoadBudget.java:446-461`).
  The context tier caps this at 4k/8k/16k/32k at 5.5/7.5/11.5 GiB (`ai/TaiContextWindowPolicy.java:23-29`).
  Automatic GPU loads and automatic LiteRT CPU loads are capped at 4096 (`ai/TaiLoadBudget.java:353-358`).
  MNN is not capped.
- Other loads (embeddings, STT, TTS, vision graphs, images) use `planFixed`: a file × factor estimate against
  the same reserve (`ai/TaiLoadBudget.java:390-404`; factors `ai/TaiResidency.java:55-100`).
- Pressure watch: a 2 s poll (`ai/TaiRuntimeService.java:142`). Tiers are `lowMemory`, then
  `availMem < 1.25 × threshold`, then `availMem < floor` (`ai/TaiPressureWatch.java:74-80`). `onTrimMemory`
  maps `RUNNING_LOW` / `RUNNING_CRITICAL` (`ai/TaiPressureWatch.java:89-98`).

## 2. How to classify a device

### 2.1 `totalMem`, not `advertisedMem`

- `MemoryInfo.totalMem` is "the total memory accessible by the kernel … not including below-kernel fixed
  allocations" [A3]. AOSP fills it from `sysinfo()` (`getTotalMemory`, [A2]). The app already rounds it up
  to a RAM size (`ai/TaiLoadBudget.java:446-461`).
- `advertisedMem` (API 34) is `Process.getAdvertisedMem()`. It returns `ro.boot.ddr_size` when the
  bootloader sets it ("Vendors can set this command line option from the bootloader" [A7]). Otherwise it
  returns `FileUtils.roundStorageSize(getTotalMemory())` [A4, A5]. That function rounds **storage-style**:
  up to a power of two times 1000ⁿ [A5].
  - **Derived:** pong's kernel memory, about 11 GiB = 11.8 × 10⁹ B, rounds up to **16 × 10⁹**. A 16 GB phone
    whose kernel sees more than 16 × 10⁹ B (14.9 GiB) would be advertised as **32 GB**. A 6 GB phone at about
    5.5 GiB (5.9 × 10⁹ B) becomes 8 GB.
  - So pong's "16 GB" (`ai/TaiLoadBudget.java:450-453`) does not need "RAM Booster" swap to explain it. The
    AOSP fallback alone produces it. Whether Nothing sets `ro.boot.ddr_size` is **unverified**.
  - Either way, `advertisedMem` is unreliable for sizing. Keep it for diagnostics only, as today
    (`ai/TaiDeviceCapabilities.java:44-46,132-134`).
- `getMemoryClass` / `getLargeMemoryClass` come from `dalvik.vm.heapgrowthlimit` / `heapsize` and describe the
  **Java heap** [A3]. LiteRT-LM, MNN and the GPU driver allocate natively, outside it. These values are not
  inputs to the gate. `largeHeap` does not help either.

### 2.2 zram, swap and vendor "virtual RAM"

- `MemAvailable` is "an estimate of how much memory is available for starting new applications, without
  swapping" [K1]. Swap is not in it, and that is correct for the gate. A GPU or anonymous allocation needs
  real pages now. Pages pushed to zram still take compressed RAM, and refaulting them costs CPU.
- Vendor RAM-extension features show up as swap, not as `MemTotal`. **Unverified** per vendor: no vendor doc
  was read. The class ignores swap.
- Swap still matters as **pressure**. lmkd treats swap as starved below `ro.lmk.swap_free_low_percentage`,
  default 10 % of total swap [A8]. With swap low it kills at the low watermark, and it kills
  perceptible-and-above processes when below the min watermark or heavily thrashing [A9, `lmkd.cpp`
  `LOW_SWAP_AND_THRASHING`, `LOW_MEM_AND_SWAP`]. Pong ran 3.3 of 4 GB swap in daily use with YouTube in front,
  so 17.5 % was free (`tai-memory-manager.md` §3a). That is close to lmkd's line. §4.3 uses this as a penalty.

### 2.3 Devices between the classes

- **8 GB → C6, 10 GB → C12.** Rounding down is the conservative choice. The live gate, not the class, decides
  whether E4B runs on an 8 GB phone that happens to be idle. On 8 GB, E2B is the recommended local model,
  matching the catalogue's "8GB+" (`ai/TaiModelCatalog.java:225`).
- A phone with an unusually large carve-out can read `totalMem` just under a boundary, for example a 12 GB
  phone at 9.9 GiB. It then lands one class down. That costs features, not stability. Accept it.
- 18/20/24 GB → C16. Nothing above C16 is needed. One chat model is resident at a time anyway: the router
  has one active assistant (`ai/MultiBackendTaiRuntime.java:53`).

### 2.4 A class below 6 GB

Yes: **Lite**, for a RAM class of 4 GB or less. The HTC hub is the case in hand: 3.4 GB, Android 9 (memory
note `htc-hub-test-device`).

- On Android 9 `availMem` is MemFree + Cached (§4.1), so the free figure reads high.
- E2B's lightest published working set is 1.7 GB RSS on the CPU at 2048 [C1]. On a 3.4 GB phone that
  leaves nothing for the home screen.
- Lite admits the small graphs only: Silero VAD (about 1.2 MB, `SileroVad.java:23`), Whisper base, KittenTTS,
  EmbeddingGemma and the wallpaper vision graphs, one at a time. Chat is remote or user-picked.

## 3. Per-model memory

### 3.1 How to read the published numbers

- LiteRT-LM model cards give "CPU Memory" as `ru_maxrss` on Android [C1, C2]. Maximum RSS does not include
  GPU buffers. It does include resident file-backed pages, such as the mmapped embeddings. So a card's GPU
  row (E2B 676 MB, E4B 710 MB) is **not** the GPU load's cost. A card's CPU row is close to it.
- The cards' benchmarks ran at a 2048 context, with 1024 prefill tokens and 256 decode tokens, on an S26 Ultra [C1, C2].
- On pong the only measure that covers the GPU is the `MemAvailable` drop (`ai/TaiLoadMeter.java:14-18`). For MNN
  with mmap that drop is meaningless: the same model read from 4 MB to 660 MB. PSS is the measure there (memory
  note `mnn-update-tai-reload-2026-09-28`, 09-28).

### 3.2 Table

Measured values are on pong (Nothing Phone 2, SM8475 / Adreno 730, 12 GB class, Android 16) unless a row says
otherwise. The theoretical KV per 1k tokens is derived in §3.3.

| Model (file) | Backend / accel. | Weights resident | KV per 1k tokens | Overhead | What is on record |
|---|---|---|---|---|---|
| Gemma 4 E4B, 3.66 GB (`ai/TaiModelCatalog.java:230`) | LiteRT GPU | Card: 2.24 GB decoder "always" in memory; 0.67 GB embeddings mmapped; vision and audio loaded as needed [C2] | Theory 16 MiB fp16 (8 MiB with unified K/V). **Observed about 195 MB** (derived: 8k cost about 0.8 GB more than 4k, `ai/TaiLoadBudget.java:57-58`; 0.8 GB / 4096 tokens) | Runtime baseline 330 MB after the first load (`ai/TaiResidency.java:50-55`) | 4k: 2.4–4.2 GB (4 runs, LiteRT 0.14, `tai-memory-manager.md` §3); 3.07 GB worst of 11 samples (0.17.1, `director-comparison-2026-10-04.md`); about 3.2 GB (`gallery-gpu-loading-comparison.md`). 2048 `-vision` director: 2.6–3.2 GB (memory note, 2026-10-04 21:10). 16k and 32k did not fit from 5.7–6.0 GB free (memory note `tai-context-window-oom`, 09-23). |
| E4B | LiteRT CPU | Card: 3283 MB RSS at 2048 [C2] | Not separable from the data. A 32k load reached 4.7 GB RSS within 3 s (09-23 note). | XNNPACK weight cache (`gallery-gpu-loading-comparison.md`) | `-vision` at 2048: about 2.5 GB drop (derived: 5.7 GB free → lowest 3.19–3.21 GB, review T2). The load-time drop at 16k was 0.45 GB, but that excludes the first prefill (`tai-memory-manager.md` §3). |
| Gemma 4 E2B, 2.59 GB (`ai/TaiModelCatalog.java:225`) | LiteRT GPU | Card: text weights "as low as 0.8 GB"; 1.12 GB embeddings mmapped; encoders on demand [C1] | Theory 6 MiB fp16 | 330 MB baseline | No drop recorded in the docs. The `-vision` director pass bottomed at 2.49 GB free; the starting figure was not recorded (`director-comparison-2026-10-04.md`). The ratio estimate is about 2.5 GB at 2048 with vision (**derived**, §4.4). |
| E2B | LiteRT CPU | Card: 1733 MB RSS at 2048 [C1] | **About 170 MB observed** (derived: 6.9 GB PSS at 32k (memory note `tai-bench-issues-2026-09-29`) minus the card's 1.73 GB at 2048, over 30,720 tokens; cross-device, rough) | — | Load-time drop 371–422 MB, which excludes the prefill (`ai/TaiLoadBudget.java:171-173`). |
| Qwen3 0.6B (MNN: 451 MB weight; LiteRT: 475 MiB at 2048 or 329 MB at 4096) (`local-llm-speedups-verification-2026-09-30.md`) | MNN CPU (mmap) / LiteRT CPU | ≈ file; MNN maps weights to disk under pressure (`use_mmap` [M1]) | **Theory 112 MiB fp16**, 56 MiB with MNN `attention_mode` 10 (KV int8) [M1] | — | Not measured. |
| Qwen3-VL 2B MNN, 1.23 GB weight | MNN CPU | ≈ file, mmap | Not derived (config not read) | — | Decode measured (21 tok/s on the CPU); memory not measured (09-28 note). |
| Granite 4.0-H 1B int8 1.68 GB; Granite 4.2 3B int4 2.19 GB (`local-llm-speedups-verification-2026-09-30.md`) | LiteRT | ≈ file | **Unverified** (hybrid layers; config not read) | — | Import-only; not measured. |
| EmbeddingGemma 300M, 183 MB graph (`ai/TaiModelCatalog.java:352`) | LiteRT CPU | — | n/a (fixed seq 256/512/1024) | — | 126 MB measured (vs 227 MB estimated); 95 MB in the idle-evict log (`tai-memory-manager.md` phases 3–4). |
| Qwen3 Embedding 0.6B MNN | MNN CPU | — | n/a | — | 360 MB estimated, not measured (`tai-memory-manager.md` phase 2). |
| Whisper ACFT base.en 101 MB / small.en 286 MB (`ai/TaiModelCatalog.java:246,272`) | LiteRT CPU, XNNPACK | — | n/a (5 s or 10 s window) | — | Peak 193 MB / 538 MB under `benchmark_model` (`ai/TaiResidency.java:64-69`). |
| Parakeet TDT 0.6B v3 int8, 614 MB (`ai/TaiModelCatalog.java:304`) | LiteRT CPU | — | n/a (5 s graph) | — | +1.2 GB RSS on load (`ai/TaiResidency.java:70-74`). NVIDIA's "at least 2GB RAM" [C5] is for NeMo/PyTorch, not int8 on a phone. |
| KittenTTS nano 0.8, 94 MB package, 15M params [C6] (`ai/TaiModelCatalog.java:324`) | LiteRT CPU, fp32 | — | n/a | Phonemizer graph + dictionary | Not measured. The 2× estimate is about 189 MB (`ai/TaiResidency.java:75-81`). |
| Silero VAD v5, about 1.2 MB asset (`SileroVad.java:23,37`) | LiteRT CPU, **app process** | — | n/a | — | Not in the residency table. Upstream: "around two megabytes", under 1 ms per chunk [C7]. Negligible. |
| Wallpaper vision: DA3 55 MB, DA2 28 MB, SegFormer 16 MB, U-2-Net 88 MB (`ai/TaiModelCatalog.java:372-390`) | LiteRT CPU | — | n/a | — | Not measured. Loaded one at a time and closed (`ai/WallpaperVisionRuntime.java:302,312`). The peak estimate is the largest × 2, about 176 MB (`ai/TaiManager.java:3719-3753`). |

### 3.3 KV cache: theory against what the runtimes allocate (derived)

Formula: bytes per token = Σ over layers that own a KV cache of `2 (K, V) × kv_heads × head_dim × bytes per element`.
Sliding layers hold at most `sliding_window` tokens. Assumptions: fp16 storage; `num_global_key_value_heads: null`
means the same as `num_key_value_heads`; the last `num_kv_shared_layers` reuse earlier caches. The field names
come from `config.json` [C3, C4]. Their semantics are **unverified** beyond the names, and the Gemma card's
"global layers feature unified Keys and Values" [C8].

- **E4B** [C4]: 42 layers, 18 shared, so 24 own a cache. Of those, 4 are global (indices 5, 11, 17, 23) and
  20 are sliding.
  - Global: 4 × 2 × 2 × 512 × 2 B = **16 KiB/token** (16 MiB per 1k tokens; 8 MiB if K = V).
  - Sliding, fixed: 20 × 2 × 2 × 256 × 2 B × 512 tokens = **20 MiB**.
- **E2B** [C3]: 35 layers, 20 shared, so 15 own a cache. Of those, 3 are global (indices 4, 9, 14) and 12 are sliding.
  - Global: 3 × 2 × 1 × 512 × 2 B = **6 KiB/token**.
  - Sliding, fixed: 12 × 2 × 1 × 256 × 2 B × 512 = **6 MiB**.
- **Qwen3 0.6B** [C9, C10]: 28 layers, 8 KV heads, head_dim 128.
  - 28 × 2 × 8 × 128 × 2 B = **112 KiB/token** (fp16, MNN's default `attention_mode` 8 [M1]).
- **What LiteRT-LM GPU actually costs for E4B**: about 195 KB/token (table above). If the runtime kept a
  full-length **fp32** cache for **all 42 layers**, with no sharing and no sliding cap, the cost would be
  35 × 2 × 2 × 256 × 4 + 7 × 2 × 2 × 512 × 4 = 200,704 B ≈ **196 KB/token**. That matches. This is an
  inference, **unverified** against LiteRT-LM source. It means TAI cannot budget LiteRT from the
  architecture. It must use an observed slope.
- **`file/19000`**, the budget's per-token term (`ai/TaiLoadBudget.java:96-97,192`), gives:
  - E4B: 192.6 KB/token. It matches the observed GPU slope, by accident.
  - E2B: 136 KB/token. That is under the rough 170 KB/token CPU observation.
  - Qwen3 0.6B MNN: 451 × 10⁶ / 19,000 = 23.7 KB/token, against 114.7 KB theoretical, about 5× too small.
    The MNN path is not window-capped (`ai/TaiLoadBudget.java:353-358` caps only GPU and LiteRT CPU), and
    pong's tier is 32k (`ai/TaiContextWindowPolicy.java:25-28`, 12 GiB ≥ 11.5). So an automatic Qwen3 0.6B
    MNN load is planned at 32k for about 1.2 GB. The theoretical KV alone is 3.5 GiB, if MNN pre-allocates
    it; MNN's allocation behaviour is **unverified**.

### 3.4 Consequence: the CPU is not the cheaper rung for Gemma 4

- E4B on the CPU costs about 2.5 GB on pong and 3.3 GB RSS on the card. On the GPU it costs 2.6–3.2 GB at
  2048 and 3.07 GB worst at 4k.
- E2B on the CPU is 1.7 GB RSS on the card. Its GPU cost is unmeasured, estimated at about 2.5 GB.
- **Derived:** for LiteRT Gemma 4, falling back from GPU to CPU saves little or nothing in memory, and costs
  3–4× in time (director: 40–50 s against 12–16 s, `director-comparison-2026-10-04.md`). The ratio model believes
  the CPU costs "5 % of the file", which is why the ladder keeps choosing it (review T2). The fallback should be a
  **smaller model**, not the CPU (§4.7).

## 4. Gate design

### 4.1 Free-memory signal

- AOSP `getMemoryInfo` sets `availMem = getFreeMemory()` (`ProcessList.java:1733-1745` at android-16.0.0_r1 [A1]).
  `getFreeMemory` reads:
  - **MemFree + Cached** at android-9.0.0_r1 through android-15.0.0_r1;
  - **MemAvailable** at android-16.0.0_r1 [A2: `android_util_Process.cpp`, at each tag].
  - So `TaiLoadMeter`'s "MemAvailable" (`ai/TaiLoadMeter.java:10-12`) is true only on API 36. Pong is API 36
    (memory note `mnn-update-tai-reload-2026-09-28`), so pong's numbers are MemAvailable. The HTC hub (API 28) and
    any Android 14/15 phone read MemFree + Cached. `Cached` includes shmem and actively mapped pages that MemAvailable
    discounts [K1]. That reads high, so a gate on those releases admits more than it should.
- **Proposal:**
  - On API < 36, read `MemAvailable` from `/proc/meminfo` directly. Whether an untrusted app can read it on
    every release is **unverified**.
  - When the read fails, fall back to `availMem` and add the swap-low penalty (§4.3) unconditionally.
  - Also read `SwapTotal` / `SwapFree` from the same file.
  - Keep sampling at 100 ms during a load (`ai/TaiLoadMeter.java:24`).
- `MemoryInfo.lowMemory` stays a release-all signal. Note what it is on current AOSP:
  `availMem < home + (cached − home)/2`. HOME_APP_ADJ (600) and CACHED_APP_MIN_ADJ (900) fall into the same minfree
  slot (index 4, `mOomAdj` at `ProcessList.java:404-407` [A1]). So `lowMemory` is simply `availMem < threshold`.

### 4.2 What Android's own numbers mean

- **`threshold`.** It is `getMemLevel(HOME_APP_ADJ)` [A1]. With the slots
  {FG 0, VIS 100, PERCEPTIBLE 200, PERCEPTIBLE_LOW 250, CACHED_MIN 900, LMK_FIRST 950}, HOME (600) maps to
  slot 4, the cached-app level.
  - Its value is `low + (high − low) × scale`, where scale saturates at 1 for any device with more than 700 MB or
    a screen of at least 1280×800 (`updateOomLevels`, `ProcessList.java:1042-1077` [A1]).
  - **Derived:** on a 64-bit phone with no vendor override, slot 4 = 147,456 × 3/2 = 221,184 KiB = **216 MiB**,
    whatever the RAM. Slot 5 = 184,320 × 7/4 = 322,560 KiB = **315 MiB**.
  - Vendors can rescale through `config_lowMemoryKillerMinFreeKbytesAbsolute/Adjust` [A1].
  - `hiddenAppThreshold` equals the same slot, and it is `@hide` with `maxTargetSdk = R` [A3]. It is not
    usable, contrary to the review's T7 direction.
- **Pong's real floor is 512 MiB, not 630 MB.**
  - The 10-04 event line said "budget(momentary): needs 5160 MB free" (`director-comparison-2026-10-04.md`).
  - **Derived:** E4B `-vision` GPU ratio at 4096 (`ai/TaiManager.java:3131-3136`):
    - fixed: 3,659,530,240 × 3/4 = 2,744,647,680;
    - encoders: + 365,953,024;
    - KV: + 192,606 × 4096 = 788,914,176;
    - sum = 3,899,514,880;
    - × 1.25 margin = 4,874,393,600;
    - + reserve R.
  - With R = 512 MiB the total is 5,411,264,512 B = **5160 MiB**. With R = 630 MiB it would be 5278 MiB.
  - So `floorBytes` returned 512 MiB, meaning `2 × threshold ≤ 512 MiB`. Pong's `threshold` is at most 256 MiB,
    consistent with the 216 MiB slot. The "315 MB" in `tai-memory-manager.md` §3a and `ai/TaiDeviceCapabilities.java:49-50`
    is the slot-5 figure from `dumpsys activity oom`, not `threshold`.
  - Ordinary chat loads on pong therefore also keep only 512 MiB free today.
- **lmkd does not act on these levels on PSI devices.**
  - lmkd uses PSI monitors by default (`ro.lmk.use_psi`, default true) and minfree levels only when
    `ro.lmk.use_minfree_levels` is set (default false) [A8, A9 `init_monitors`, property defaults].
  - Its kill decision compares free pages against the zone **watermarks** (min/low/high) and combines that
    with thrashing, swap and PSI stalls [A9 `get_lowest_watermark`, kill-reason chain]. MemAvailable is not
    the input.
  - Watermark spacing is 0.1 % of memory by default (`watermark_scale_factor` 10 [K2]).
  - A plain low-watermark kill targets `lowmem_min_oom_score`, default 701: cached apps only [A8].
  - The thrashing and swap reasons lower the bar to `PERCEPTIBLE_APP_ADJ + 1` = 201 [A9]. That includes
    HOME_APP_ADJ 600, **the launcher itself whenever it is not in front**, with its Termux sessions.
- **What this means for the floor.**
  - A fast GPU allocation drives free pages under the high watermark before kswapd reclaims cache. lmkd then
    kills cached apps even though MemAvailable looks healthy. On 2026-09-23 an E4B GPU 8k load bottomed at
    2.3 GB "free", and lmkd still evicted 16 cached apps (memory note `tai-context-window-oom`).
  - No reasonable floor prevents cached-app kills during a big GPU load. The floor's job is to keep
    **perceptible processes, the home process and Termux** alive. Cached-app kills during a momentary load are
    the accepted cost.

### 4.3 Floors and reserves per class (proposal)

| | Lite | C6 | C12 | C16 |
|---|---|---|---|---|
| **Peak floor**: momentary loads (unloaded by deadline, ≤ 3 min) | 0.75 GiB | 0.75 GiB | 0.75 GiB | 1.0 GiB |
| **Hold floor**: chat kept warm, voice, embeddings | 1.0 GiB | 1.25 GiB | 1.5 GiB | 2.0 GiB |
| Margin on a seed (card or architecture) estimate | +25 % | +25 % | +25 % | +25 % |
| Margin on a measured estimate | +10 % on the ring maximum | same | same | same |
| Swap-low penalty: SwapFree < 15 % of SwapTotal, or swap unknown on API < 36 | +0.5 GiB | +0.5 GiB | +0.5 GiB | +0.5 GiB |
| Background penalty (hold floor only): launcher not in front | +0.25 GiB | +0.25 GiB | +0.25 GiB | +0.25 GiB |

Justification. These are policy choices, to be corrected by §4.6. They are not derived limits.

- **Peak 0.75 GiB.**
  - Today's effective 512 MiB floor (§4.2) admitted the 10-04 21:10 E4B GPU director run without a freeze
    (memory note).
  - The 09-23 freeze came from window size, not floor size: 32k (`ai/TaiLoadBudget.java:16-20`).
  - +0.25 GiB covers the gap between a 100 ms sample and a fast allocation. On C16, 1.0 GiB keeps the same
    share of RAM.
- **Hold 1.5 GiB on C12.**
  - This is the old reserve `max(1.5 GiB, 15 %)`, under which pong stayed usable through phase 3
    (`tai-memory-manager.md` §2–3). A resident model plus a foreground app that grows needs real headroom for
    minutes, not seconds.
  - C6 gets 1.25 GiB, because 1.5 GiB of a roughly 5.5 GiB phone shuts out E2B entirely. C16 gets about 13 %.
- **Swap penalty.**
  - lmkd's line is 10 % [A8]. 15 % keeps one step of cushion.
  - Pong in daily use sat at 17.5 % (`tai-memory-manager.md` §3a). The penalty would rarely fire there, and
    would fire exactly when lmkd is about to start the low-swap kills.
- **Background penalty.** HOME_APP_ADJ 600 is in reach of the thrashing and swap kill reasons (§4.2), and
  Termux sessions live with the launcher.
- **Momentary must be enforced, not declared.** Today a momentary load can outlive its caller: the IPC timeout
  leaves the model resident (review T3). The proposal admits a load against the peak floor only when the runtime
  also gets an unload deadline (load + 3 min). On expiry the runtime cancels and unloads by itself.

The pressure watch follows the same floors. Tier 1 below the hold floor gives up idle auxiliaries. Tier 2 below
the peak floor also gives up idle chat. Tier 3 is `lowMemory`. The `1.25 × threshold` line goes, because it is
about 270 MiB (§4.2) and fires far too late. Drop the `RUNNING_LOW` / `RUNNING_CRITICAL` mapping: apps "are not
notified of this level since API level 34" [A6]; review T8. Poll every 250 ms while a load or a first prefill is
running, and every 2 s otherwise.

### 4.4 Estimates and history

**Cost model.** For each `(model file, backend, accelerator, modality)`:

```
cost(window) = fixed + slope × window          (+ encoderDelta when the modality is vision or audio)
```

- **Seeds** come from §3.2. LiteRT GPU: fixed = measured, or 0.75 × file. slope = 195 KB/token for E4B, which
  is the measured slope. For other LiteRT files, use `file/19000` until measured: it matches E4B's slope and
  sits near E2B's.
- LiteRT CPU: fixed = the card's CPU RSS minus its 2048-token share, or 0.6 × file when no card number exists
  (**derived** from the E4B card: 3283 MB / 3654 MB ≈ 0.9 of the file at 2048; E2B: 1733 / 2583 ≈ 0.67).
  The current 5 % is replaced.
- MNN: fixed = the weight file. It is reclaimable under `use_mmap` [M1], but count it until PSS is measured.
  slope = architecture KV from `config.json` (layers × kv_heads × head_dim × 2 × 2 B, halved when `attention_mode`
  ∈ {10, 2}).
- `encoderDelta` for Gemma 4 vision: vision encoder ~150M parameters [C8], seeded at 0.1 × file as today
  (`ai/TaiLoadBudget.java:191`), then measured.

**History.** Replace the exact-bucket lookup (`ai/TaiRuntimeHistory.java:293-321`):

1. **Key.** `model file identity (base id + size) | device | Android SDK | runtime version | backend |
   accelerator | modality`. Text, vision and audio get separate keys (review T1). Window comes out of the key
   and goes into the sample.
2. **Samples.** A ring of the last 8 `(window, peakDrop, promptTokens, phase, timestamp)` per key. Drop samples
   older than 30 days or from another app or runtime version. Failures already expire this way
   (`ai/TaiRuntimeHistory.java:213-240`). Fixes the ratchet (review T6).
3. **Lookup for a window w**, in this order:
   - (a) the largest sample at the smallest measured window ≥ w. A larger window is an upper bound.
   - (b) when samples exist at two windows, fit `fixed` and `slope` on the per-window maxima and evaluate at w.
   - (c) one window only, below w: `sample + seedSlope × (w − w_s)`.
   - (d) the seed.

   For the director (2048, vision), (a) uses the 4096 GPU samples at once: 3.07 GB × 1.1 = 3.38 GB. Once one
   2048 sample exists, that value is used instead. **Derived:** by the slope, 3.07 − 2048 × 196 KB ≈ 2.67 GB.
4. **Variant fallback.** Before a vision sample exists, use the text sample plus `encoderDelta`. Never
   the reverse without margin.
5. **Seeding from the bench.** The bench measures PSS (memory note `tai-bench-issues-2026-09-29`). It
   can seed MNN and LiteRT CPU keys. It cannot seed GPU keys, because PSS misses GPU buffers.

### 4.5 Measuring every load through the first prefill

- Today the meter spans `initialize()` only (`ai/TaiLoadMeter.java:9-12`). LiteRT CPU samples are discarded for
  that reason (`ai/TaiLoadBudget.java:169-178`).
- **Proposal:** keep the meter running until the first request after a load has produced its first token, capped
  at 60 s. Record two values on the sample:
  - `phase=load`: before − minimum up to the end of `initialize()`;
  - `phase=first_prefill`: before − minimum up to the first token;
  - plus the prompt token count, including image tokens.
- Plan on `first_prefill` when the key has one. With that, LiteRT CPU becomes trustworthy, and `trustsLoadDrop`
  can go.
- **MNN** records PSS growth of `:tai_runtime` (Private_Dirty plus GPU-visible when available) over the same
  span, not the MemAvailable drop.
- **Unverified** for the GPU path: whether LiteRT-LM allocates anything at the first send (review T2). The span
  above catches it either way.

### 4.6 Self-correction of the floor

- After each load, and again at its unload, read `ActivityManager.getHistoricalProcessExitReasons` for the app's
  own package. API 30 has it; on API 28 the loop is off.
- If `:tai_runtime` or the main process died with `REASON_LOW_MEMORY` during the hold, raise this device's hold
  floor (or peak floor, for a momentary load) by 256 MiB, up to 2× the class value.
- `isLowMemoryKillReportSupported()` says whether the reason is reported. Otherwise a kill appears as
  `REASON_SIGNALED` + SIGKILL [A10].
- Lower the floor back by 64 MiB after every 10 clean holds, never below the class value.
- Also record the lowest `availMem` during each hold. A floor that is never approached is a candidate for lowering.
- The app cannot see lmkd killing other apps. Its own processes are the only honest signal it has. Termux
  sessions are in the main process's package, so they count.

### 4.7 On refusal

The order per load class:

| Step | Interactive chat (hold) | Momentary (director, cleanup) | Background (categories, embeddings for dawn) |
|---|---|---|---|
| 1 | Evict idle residents: embeddings → TTS → image → STT (`ai/TaiResidency.java:358-359`, kept) | same, plus idle chat (it is replaced anyway) | Evict idle embeddings only |
| 2 | Halve the window down to 4096 (2048 on Lite/C6) | Window is already 2048: no step | Halve to 2048 |
| 3 | Other accelerator, **only if** its *measured* cost is lower, or the class is GPU-less (Pixel 10 rule, `ai/TaiDeviceCapabilities.java:216`) | Same rule. For Gemma 4 this normally skips the CPU (§3.4) | Same |
| 4 | Offer the smaller model (E4B → E2B), with a notice | Fallback model: E4B → E2B (`-vision`), cleanup E4B → E2B | Fallback model |
| 5 | Remote provider when configured and allowed for the feature | Remote, which is the approved default when a key is set (`intelligence-director…` §6.3) | Remote |
| 6 | Refuse with the plan's own numbers, at the window it tried (review T5) | Rules-only path (director) or raw text (cleanup) | Wait: retry at 20 s, 60 s and 180 s, because memory comes back late (`tai-memory-manager.md` §3c) |

The speed-aware rule already proposed (`intelligence-director…` §7.1, item 4) belongs in step 3 for momentary
and background loads. When step 3 would pick the CPU for a LiteRT file over 3 GB, skip to step 4.

## 5. Concurrency rules per class

Bytes are the §3.2 values, measured or estimated. "Fits" always means it passes §4.3 at the time.

| Workload pair | Lite | C6 | C12 | C16 |
|---|---|---|---|---|
| Two chat LLMs | No (one active assistant, `ai/MultiBackendTaiRuntime.java:53`) | No | No | No |
| Chat LLM + Silero VAD | n/a | Yes (app process, about 2 MB) | Yes | Yes |
| E2B + Whisper base (≈0.2 GB) | n/a | Yes | Yes | Yes |
| E2B + Whisper small (≈0.54 GB) or Parakeet (≈1.2 GB) | n/a | Whisper small if it fits; Parakeet evicts idle chat first (existing STT rule, `ai/TaiManager.java:3762-3779`) | Yes | Yes |
| E4B + Parakeet | n/a | No | If it fits under the hold floor; else Parakeet → Whisper small | Yes |
| Any LLM + KittenTTS (≈0.19 GB est.) | Kitten alone | If it fits; evicted first | Yes | Yes |
| Any LLM + an embedding model | Embedding alone | Embedding evicted before any LLM load; refused while an LLM is busy | Yes (≈0.13 GB measured) | Yes |
| Director run + idle voice residents | n/a | Voice evicted first (except Silero) | Idle voice evicted only if the plan needs it | Voice stays |
| Director run + **busy** dictation (recording or cleanup running) | n/a | Director waits (queue) | Director waits | Director waits; the director and cleanup share the single LLM slot |
| Vision graphs + anything | One graph at a time, alone | One at a time; chat must be idle | One at a time beside an idle chat | Free |
| Image diffusion (IMAGE kind) + LLM | No | No | Only if it fits; image mode 0 or 2 | If it fits |

Rules behind the table:

- **The director is a momentary chat load.** It replaces the resident chat model, which it is credited
  (`ai/TaiManager.java:3065-3071`). It may evict idle voice and embeddings. It never interrupts a busy resident.
  When dictation is active it waits rather than refuses, because the user's voice session is the foreground task.
- **The builder sequence stays:** vision graphs, each closed, then the director, then unload
  (`living/TaiGemmaChat.java:61-67`). On C6, run the graphs only while no chat is resident, or evict idle chat
  first.
- **Cleanup on C6** loads E2B as the mic opens and leaves it resident (`LocalTaiVoiceTextPolisher.java:40-43`).
  On C6 it should be momentary: unload after the pass. Under a 1.25 GiB hold floor, a resident E2B plus a
  foreground app is the main source of pressure on that class.

## 6. What changes in code (for a later brief; not done here)

- `TaiLoadBudget`: a `DeviceClass` (Lite/C6/C12/C16 from `ramClassBytes`); `peakFloor` / `holdFloor` / penalties
  replace `floorBytes` and `MOMENTARY_RESERVE_BYTES`. The ladder's step 3 compares measured costs. `trustsLoadDrop`
  is deleted once §4.5 lands.
- `TaiRuntimeHistory`: the new key and sample ring; `measuredLoadBytes(key, window)` implements §4.4 (a)–(d).
- `TaiLoadMeter`: a `first_prefill` phase, and a PSS mode for MNN.
- `TaiDeviceCapabilities`: `/proc/meminfo` MemAvailable and swap on API < 36; foreground state; fix the
  `threshold` comment.
- `TaiContextWindowPolicy.tierCap`: per class 2048 / 4096 / 8192 / 16384, and MNN capped like LiteRT CPU.
- `TaiPressureWatch`: tiers on the hold and peak floors; trim mapping removed; fast poll during loads.
- `TaiRuntimeService`: the momentary unload deadline (with review T3).
- A seed table for the §3.2 models, in `TaiModelProfile` or the catalogue, with `source` fields.

## 7. Open questions for the developer

1. **Cached-app kills during a momentary load.** Is it acceptable that a director run on a busy phone makes lmkd
   drop cached background apps? The 09-23 data says no floor short of refusing prevents it (§4.2). The
   alternative is refusing E4B on the GPU unless more than about 4.5 GB is free.
2. **E2B on 6 GB phones.** The catalogue and Gallery say 8 GB (`ai/TaiModelCatalog.java:225`). Should C6 at 6 GB
   offer E2B on demand, or go straight to remote and rules?
3. **Hold floor values.** 1.25 / 1.5 / 2.0 GiB are proposals. Should the first release ship them, or a looser
   C12 value, for example 1.0 GiB, and let §4.6 raise it?
4. **The 8 GB boundary.** C6 (conservative, as proposed) or C12-minus (E4B on the GPU when idle)?
5. **Cleanup residency on C6.** Make it momentary (unload after the pass), at the cost of the load time on every
   dictation?
6. **Qwen3 0.6B as the always-fits local model** for small text jobs on Lite/C6. Its cleanup quality was never
   benchmarked against E2B.
7. **Remote as step 5 for interactive chat.** Approved for the launcher's features only ("no `tai chat` serving",
   `intelligence-director…` §6.4). So for interactive chat the ladder skips remote?

## Unverified

- Whether pong's `MemoryInfo.threshold` is 216 MiB. The 5160 MiB arithmetic (§4.2) proves only that it is
  ≤ 256 MiB. A `tai --json runtime` read of `memoryThresholdBytes` settles it.
- Whether Nothing sets `ro.boot.ddr_size`, and so whether pong's 16 GB `advertisedMem` is the AOSP rounding (§2.1).
- Vendor RAM-extension behaviour (swap file, zram size), and whether pong's 4 GB swap is zram or Nothing's
  RAM Booster.
- Whether an untrusted app can read `/proc/meminfo` on every Android release from 9 to 16.
- The semantics of Gemma 4's `num_kv_shared_layers`, `num_global_key_value_heads: null`, and "unified Keys and
  Values" (§3.3); the KV figures assume the plain reading.
- That LiteRT-LM GPU keeps a full-length fp32 KV cache for all layers. Inferred only from the 196 KB/token
  arithmetic matching the observed 0.8 GB per 4k (§3.3).
- Whether MNN 3.6.1 pre-allocates the KV cache for `max_all_tokens`, or grows it (§3.3, Qwen3 0.6B at 32k).
- E2B's GPU load drop on pong (never recorded in the docs); E4B's and E2B's GPU cost at 2048 separately from 4096.
- Memory for KittenTTS, the wallpaper vision graphs, Qwen3 0.6B, Qwen3-VL 2B and Granite: none measured on a phone.
- Whether LiteRT-LM allocates at the first send on the GPU path (carried over from review T2).
- The E2B CPU slope (≈170 KB/token) mixes a pong PSS figure with the card's S26 Ultra RSS. Rough.
- Which release first made `getFreeMemory` read MemAvailable. It was not at android-15.0.0_r1 and was at
  android-16.0.0_r1. A 15 QPR could be the true point; the commit history needs a signed-in Gerrit session.
- Typical `totalMem` and daily free memory for real 6 GB, 8 GB and 16 GB phones. Pong is the only class with data.
- The floor values in §4.3 have no measurement behind them beyond pong's history. They are policy, to be
  corrected by §4.6.

## Sources

AOSP (read 2026-10-05 from android.googlesource.com at the tag named, or `main` where stated):

- [A1] `ProcessList.java`, android-16.0.0_r1: `getMemoryInfo` (1733-1745), `getMemLevel` (1515), `updateOomLevels`
  (1042-1105), `mOomAdj` and minfree tables (404-419), ADJ constants (209-263); android-9.0.0_r1 for the Android 9
  slots. https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/services/core/java/com/android/server/am/ProcessList.java
- [A2] `android_util_Process.cpp` (`getFreeMemory`, `getTotalMemory`) at android-9.0.0_r1 … android-16.0.0_r1.
  https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/jni/android_util_Process.cpp
- [A3] `ActivityManager.java` (`MemoryInfo` fields and javadoc, `getMemoryClass`, `getLargeMemoryClass`), main.
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ActivityManager.java
- [A4] `Process.java` (`getAdvertisedMem`, android-16.0.0_r1 line 1469).
  https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/os/Process.java
- [A5] `FileUtils.java` (`roundStorageSize`), main. https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/FileUtils.java
- [A6] `ComponentCallbacks2.java`, android-16.0.0_r1 (levels deprecated "since API level 34").
  https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/content/ComponentCallbacks2.java
- [A7] `MemoryProperties.sysprop` (`ro.boot.ddr_size`), main. https://android.googlesource.com/platform/system/libsysprop/+/refs/heads/main/srcs/android/sysprop/MemoryProperties.sysprop
- [A8] lmkd `README.md` (properties and defaults), main. https://android.googlesource.com/platform/system/memory/lmkd/+/refs/heads/main/README.md
- [A9] lmkd `lmkd.cpp` (`get_lowest_watermark`, `calc_zone_watermarks`, the kill-reason chain in `mp_event_psi`,
  `init_monitors`, property defaults), main. https://android.googlesource.com/platform/system/memory/lmkd/+/refs/heads/main/lmkd.cpp
- [A10] `ApplicationExitInfo.java` (`REASON_LOW_MEMORY`, `isLowMemoryKillReportSupported` note), main.
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ApplicationExitInfo.java

Linux kernel:

- [K1] `Documentation/filesystems/proc.rst` (MemAvailable). https://github.com/torvalds/linux/blob/master/Documentation/filesystems/proc.rst
- [K2] `Documentation/admin-guide/sysctl/vm.rst` (`watermark_scale_factor`). https://github.com/torvalds/linux/blob/master/Documentation/admin-guide/sysctl/vm.rst

Runtimes:

- [L1] LiteRT-LM v0.17.1 `Config.kt` (`maxNumTokens` = KV cache size). https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt
- [L2] LiteRT-LM v0.17.1 `runtime/engine/engine_settings.cc` (GPU default capped at 4096, lines 299-311). https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/engine/engine_settings.cc
- [M1] MNN 3.6.1 `docs/transformers/llm.md` (`use_mmap`, `kvcache_mmap`, `attention_mode`, `memory`) and
  `transformers/llm/engine/src/llmconfig.hpp` (`max_all_tokens` default 2048, `use_mmap` default false).
  https://github.com/alibaba/MNN/blob/3.6.1/docs/transformers/llm.md

Model cards and configs (read 2026-10-05):

- [C1] litert-community/gemma-4-E2B-it-litert-lm README. https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- [C2] litert-community/gemma-4-E4B-it-litert-lm README. https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- [C3] google/gemma-4-E2B-it `config.json`. https://huggingface.co/google/gemma-4-E2B-it/blob/main/config.json
- [C4] google/gemma-4-E4B-it `config.json`. https://huggingface.co/google/gemma-4-E4B-it/blob/main/config.json
- [C5] nvidia/parakeet-tdt-0.6b-v3 card ("At least 2GB RAM"). https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3
- [C6] KittenML/kitten-tts-nano-0.8 card (15M params) and litert-community/kitten-tts-nano-0.8 card (graph sizes).
  https://huggingface.co/KittenML/kitten-tts-nano-0.8 , https://huggingface.co/litert-community/kitten-tts-nano-0.8
- [C7] snakers4/silero-vad README. https://github.com/snakers4/silero-vad
- [C8] google/gemma-4-E4B-it card (layers, sliding window, unified K/V, encoder sizes). https://huggingface.co/google/gemma-4-E4B-it
- [C9] Qwen/Qwen3-0.6B card. https://huggingface.co/Qwen/Qwen3-0.6B
- [C10] Qwen/Qwen3-0.6B `config.json`. https://huggingface.co/Qwen/Qwen3-0.6B/blob/main/config.json

App notes used for measurements (repo, with dates): `tai-memory-manager.md` (2026-09-24),
`animated-wallpaper/director-comparison-2026-10-04.md`, `local-ai-and-living-wallpaper-review-2026-10-04.md`,
`reference/voice-ai/gallery-gpu-loading-comparison.md` (2026-09-24),
`reference/voice-ai/local-llm-speedups-verification-2026-09-30.md`. Agent memory notes cited by name
(`tai-context-window-oom` 09-23, `tai-bench-issues-2026-09-29`, `mnn-update-tai-reload-2026-09-28`,
`htc-hub-test-device`, `intelligence-director-remote-provider` 10-04 21:10) are session records, not repo docs.
