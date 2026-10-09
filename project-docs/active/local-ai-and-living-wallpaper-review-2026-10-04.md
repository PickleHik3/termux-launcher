# Review: local AI inference (TAI) and the living-wallpaper creator

Status: review, 2026-10-04, of dev `8411d209c`. Read-only: no build, no device. Every app claim
cites the source at that commit; every library or platform claim cites the owner's source or docs
(listed under Sources). Where code and docs disagree, both are cited. What could not be checked is
listed under Unverified.

Paths below are short: `ai/` is `app/src/main/java/com/termux/ai/`, `wp/` is
`app/src/main/java/com/termux/app/chrome/wallpaper/`, `living/` is `wp/living/`.

Read first (and not re-derived here): `tai-memory-manager.md`, `animated-wallpaper/living-stills-part-c-v2.md`,
`animated-wallpaper/intelligence-director-and-remote-provider-2026-10-04.md`,
`animated-wallpaper/director-comparison-2026-10-04.md`, and the voice-ai speed/verification notes.

## Summary: top findings by severity

| # | Sev | Finding | Where |
|---|---|---|---|
| 1 | High | The memory history cannot help the director: a measurement is looked up by the exact window bucket, so the director's 2048 window never reads the 4096 GPU samples, always gets the ratio estimate plus 25 %, is sent to the CPU, and so never produces a 2048 GPU sample. Vision and text loads also share one key. | `ai/TaiRuntimeHistory.java:293-321` |
| 2 | High | The CPU path, which is where the director actually runs, is admitted on a guess that is never corrected. LiteRT CPU loads are never measured; the ratio for E4B `-vision` at 2048 is about 1.2 GB with margin, while the 2026-10-04 run fell from 5.7 GB free to 3.2 GB. The meter only spans `initialize()`, so anything allocated at send time (prefill, image encoding) is not seen on any backend. | `ai/TaiLoadBudget.java:169-194`, `ai/TaiLoadMeter.java:9-19` |
| 3 | High | A director timeout or a user cancel does not stop the model. The 180 s limit is an IPC wait only; the runtime keeps loading or generating, and when the load was still running the unload is skipped, leaving E4B (about 3–4 GB) resident until the idle unload. Cancel during "Reading the scene" waits up to three minutes. | `ai/TaiRuntimeServiceClient.java:60-67`, `living/TaiGemmaChat.java:312-321,362-369`, `living/TaiLivingEngine.java:42-45` |
| 4 | High (visible) | Why petals render white and everywhere: petals are added to the photo (additive, then clamped), not blended, and the particle plane on the plan path covers the whole frame except the subject, at a minimum of 0.35. | `wp/LivingStill.java:352-375`, `living/ElementMasks.java:185-198` |
| 5 | Medium | Protected (`still`) pixels sample the un-zoomed rest position while their neighbours sample the 3 % zoomed one, so the subject is offset by up to about 1.5 % of the image against its surroundings, with a double edge across the feathered mask. The device test asserts the offset as correct. | `wp/LivingStill.java:144-151,217`, `app/src/androidTest/.../LivingStillAgslInstrumentationTest.java:144-151` |
| 6 | Medium | The director does not use what LiteRT-LM 0.17.1 offers for exactly this job: `responseFormat` (JSON-schema constrained decoding), per-call `maxOutputToken`, and `visualTokenBudget`. `max_tokens` is enforced by counting callbacks. | `ai/LiteRtTaiRuntime.java:372-418,1114-1135` |
| 7 | Medium | The "momentary" reserve changes nothing on a phone whose threshold floor is below 1 GiB (pong: 630 MB), and the `accel_fallback` reason quotes the need at a 4096 window, not the 2048 the director asked for. The "4.4–5.2 GB" figure in the comparison note comes from these two. | `ai/TaiLoadBudget.java:340`, `ai/TaiManager.java:3131-3136` |
| 8 | Medium | The lock wallpaper reports a fixed grey palette through the 3-colour `WallpaperColors` constructor. That constructor never sets `HINT_SUPPORTS_DARK_TEXT`, and with a dark primary it sets `HINT_SUPPORTS_DARK_THEME`, so on a bright photo the system's lock-screen text stays light. The class comment says the palette comes from the photo; it does not. | `wp/LockLiveWallpaperService.java:227-233`, `wp/LivingStill.java:463,480`, `wp/WallpaperPaletteCapture.java:6` |
| 9 | Medium | `MemoryInfo.threshold` is AOSP's HOME_APP_ADJ level, not the cached-app kill level the budget's comment and `tai-memory-manager.md` §3a describe. `onTrimMemory` tiers listen for `RUNNING_LOW/CRITICAL`, which apps have not received since API 34. | `ai/TaiLoadBudget.java:41-43`, `ai/TaiPressureWatch.java:89-98` |
| 10 | Medium | `:tai_runtime` still writes the shared TAI preferences file (crash marker). Android says SharedPreferences gives no cross-process reconciliation, so this can overwrite settings the app process changed. | `ai/TaiRuntimeCrashMarker.java:34,40,54` |

Lower-severity findings follow in each section (T11–T14, W6–W14).

---

## 1. Local AI inference (TAI)

### T1. Measured history cannot reach the director's window; vision and text share a key (High)

- **Claim.** `measuredLoadBytes` reads one exact key, `load|<base model>|<device>|<backend>|<accelerator>|<bucket>`,
  with the bucket the power of two at or above the window (`ai/TaiRuntimeHistory.java:293-321`). The
  director asks for 2048 (`living/SceneReader.java:36,112`), so it reads bucket 2048. The 11 E4B GPU samples
  on file were chat loads at 4096. A 2048 load would never read them, even though a larger window's
  measurement is a valid upper bound for a smaller one.
- With no measurement, the GPU step gets the ratio estimate plus `RATIO_MARGIN_PERCENT` 25
  (`ai/TaiLoadBudget.java:93,330-333`). The plan moves to the CPU. A 2048 GPU load therefore never runs and
  is never measured. The loop does not fix itself.
- The key uses `TaiModelVariants.baseModelId`, so `gemma-4-e4b-it-litert-lm` and `…-vision` share records.
  A vision load also initializes the vision encoder (`ai/LiteRtTaiRuntime.java:960-969`). A text-only
  measurement (smaller) is read back with no margin for a vision load, and a vision measurement inflates
  text loads.
- **Impact.** The director's GPU admission is decided by the most pessimistic estimate available. For E4B
  the ratio at 4096 is about 1.07 × file × 1.25 ≈ 4.9 GB, against 3.07 GB measured
  (`director-comparison-2026-10-04.md`, "11 samples"). This is a large part of why "E4B on the GPU is a
  bonus".
- **Direction.** Look up the smallest measured bucket ≥ the requested window (an upper bound), and only
  then fall back to the ratio. Key vision and audio variants separately, or record an encoder delta. Consider
  seeding the history from the bench, so `tai runtime --clear-history` does not reset everything to the ratio.

### T2. CPU admission is a guess that never self-corrects; the meter misses prefill and vision (High)

- **Claim.** LiteRT CPU load drops are neither recorded nor read (`trustsLoadDrop`,
  `ai/TaiLoadBudget.java:169-178`), so a CPU plan is always the ratio:
  5 % of file + 10 % for encoders + file/19000 per token (`:180-194`). For E4B `-vision` (≈3.66 GB) at 2048 this
  is about 0.94 GB, 1.18 GB with margin.
- **Evidence it is low.** `director-comparison-2026-10-04.md` records E4B `-vision` on the CPU with
  "5.7 GB free" and a lowest MemAvailable of 3.19–3.21 GB. That is a drop of about 2.5 GB, roughly twice the
  admitted estimate. (Derived: the note gives the starting free memory for the run as a whole, not per row.)
- `TaiLoadMeter` samples only between `start()` and `stop()` around `initialize()`
  (`ai/LiteRtTaiRuntime.java:1006-1012`; `ai/TaiLoadMeter.java:9-19`). Anything
  LiteRT-LM allocates when a message is sent (image encoding, prefill activations) falls outside that
  window. The budget's own comment already says this happens for the LiteRT CPU KV cache
  (`ai/TaiLoadBudget.java:170-173`). The LiteRT-LM KDoc says only that `initialize()` loads the model
  (`Engine.kt`, v0.17.1). If the GPU path also allocates at send time, a "measured" GPU figure misses the
  director's real peak too. Whether it does is **unverified**; see Unverified.
- **Impact.** The fallback that is meant to be safe is the one the budget understands least. On an 8 GB
  phone (E2B on the CPU, or E4B after a refusal) the first prefill can take the phone below its floor with
  nothing watching except the 2 s pressure poll.
- **Direction.** Keep the meter running through the first prefill of the first request after a load (for
  every backend), record that as the load's cost, and allow CPU records again once they cover prefill.

### T3. Timeouts and cancels do not reach the native work (High)

- **Claim.** `TaiRuntimeServiceClient.request` waits `timeoutMs`, then removes the pending entry and returns
  `tai_runtime_timeout`. It sends nothing to the runtime (`ai/TaiRuntimeServiceClient.java:60-67`), so the
  `:tai_runtime` load or generation goes on.
- `TaiGemmaChat.complete` unloads in `finally` only when `loadedModelId()` already equals the vision id
  (`living/TaiGemmaChat.java:317-321,362-369`). If the 180 s ran out while the load was in progress, the
  status has no loaded model. The unload is skipped, the load then finishes, the request generates for a
  caller that is gone, and E4B stays resident until the idle unload (10 min by default,
  `ai/TaiSettings.java:233`). If it ran out mid-generation, `unload()` cancels and defers the unload
  (`ai/LiteRtTaiRuntime.java:192-207`). That case is handled.
- The user's cancel calls only `cancelWallpaperAnalysis` (`living/TaiLivingEngine.java:42-45`). The
  builder checks `isCancelled` only after `SceneReader.read` returns (`living/LivingStillBuilder.java:113-120`),
  so a cancel during the director waits up to `TIMEOUT_MS` = 180 s (`living/SceneReader.java:35`).
- **Direction.** On a client timeout, send `OP_CANCEL` and then unload whatever this call loaded. Compare
  against the id, not against "loaded now", and let a pending unload apply when the load returns. Wire
  `LivingStillJob.cancel` to `TaiManager` cancel when the director stage is active.

### T4. LiteRT-LM features the director should use and does not (Medium)

At the pinned `litertlm-android` 0.17.1 (`app/build.gradle:12,207`):

- `Conversation.sendMessageAsync(…, maxOutputToken, thinkingConfig, responseFormat, …)`, where
  `ResponseFormat` is `JSON_OBJECT` (a JSON Schema) or `REGEX`, and `ConversationConfig.enableResponseFormat`
  turns on LLGuidance (LiteRT-LM v0.17.1 `Conversation.kt`, `Config.kt`, `ResponseFormat.kt`).
  TAI builds `ConversationConfig` with seven positional arguments and never sets these
  (`ai/LiteRtTaiRuntime.java:1126-1134`). It sends only `extraContext` (`:379,449`).
- The Scene Plan grammar is closed: fixed enums for `kind`, `motion`, `depth`, `time`, `weather` and `particles`,
  and four integers 0..1000 per box (`living/SceneReader.java:59-79`). Every failure on record is a format
  failure: marks written as strings, a literal `photo|illustration`, broken JSON with thinking on, and boxes
  of 4500/10000 (`director-comparison-2026-10-04.md`, `living-stills-part-c-v2.md`). A schema removes that
  whole class of failure, for E2B especially.
- `max_tokens` is enforced by counting `onMessage` callbacks and calling `cancelProcess()`
  (`ai/LiteRtTaiRuntime.java:372-374,402-418`). LiteRT-LM does not say how many tokens one callback carries
  ("may be called multiple times", `Conversation.kt`). With speculative decoding on (the director turns it
  on) the count is a lower bound, so the cap can be passed. The cancel also forces the conversation closed
  (`:470-480`). `maxOutputToken` is the documented control.
- `ExperimentalFlags.visualTokenBudget` (Gemma 4 only: 70/140/280/560/1120; read at `initialize()` and per
  send) is not set (LiteRT-LM v0.17.1 `ExperimentalFlags.kt`, `Engine.kt`). The image's token count drives
  the CPU prefill, which is most of the 40–50 s. Google's Gemma image guide treats the budget as the
  speed/accuracy knob.
- **Checked and correct.** `setEnableSpeculativeDecoding` is set before `initialize()`, which is where
  0.17.1 reads it (`ai/LiteRtTaiRuntime.java:970-986`; `Engine.kt` passes the flag into `nativeCreateEngine`
  inside `initialize()`).
- **Direction.** Pass `maxOutputToken` per send and drop the callback count. Add a TAI-local
  `response_format: {type: json_schema}` that maps to `ResponseFormat.json(schema)` with
  `enableResponseFormat=true`, and give the director the Scene Plan schema. Expose `visual_token_budget`
  and measure 280 against the default on the eight test pictures.

### T5. The "momentary" reserve and the reason string (Medium)

- `plan` sets `reserve = max(512 MiB, min(floorBytes, 1 GiB))` for a momentary load
  (`ai/TaiLoadBudget.java:339-340`). `floorBytes` is `max(2 × threshold, 512 MiB)` when the threshold is known
  (`:123-126`). On pong that is 630 MB (`tai-memory-manager.md` phase 3), so the momentary reserve is also
  630 MB and the flag changes nothing. It only matters when the threshold is unknown (1.5 GiB/15 % reserve) or
  above 512 MiB. The javadoc's 5.0–5.7 GB example (`:83-90`) reasons from the old reserve.
- `acceleratorFallbackReason` computes the quoted need at `TaiLoadBudget.FLOOR_CONTEXT` (4096)
  (`ai/TaiManager.java:3131-3136`), while the plan tried the GPU at the director's 2048. The string
  overstates the need by the 2048-token KV share plus its margin (about 0.47 GB for a 3.66 GB file by the
  `:180-194` ratio). The "needs 5160 MB" in `director-comparison-2026-10-04.md` is that figure.
- **Direction.** Quote `plan`'s own estimate for the first accelerator at the plan's window. Decide whether
  "momentary" should lower the floor itself (for example to the threshold) or drop the ratio margin, since
  capping the reserve at 1 GiB is a no-op on phones like pong.

### T6. The worst-case ratchet (Medium)

- `recordMeasuredLoad` keeps `max(worst, measured)` forever (`ai/TaiRuntimeHistory.java:259-289`). The class
  comment records ±0.9 GB of noise on identical GPU loads (`ai/TaiLoadMeter.java:16-18`). One sample taken
  while another app was allocating raises that key's estimate permanently. The record never decays and is
  not tied to the app version, unlike failure records (`:213-237`).
- **Direction.** Keep the last N samples and plan on a high percentile, or let the worst case expire like
  failures do.

### T7. What `MemoryInfo` actually reports (Medium)

- AOSP `ProcessList.getMemoryInfo` sets `availMem = getFreeMemory()`, `threshold = getMemLevel(HOME_APP_ADJ)`,
  and `lowMemory = availMem < home + (cachedMin − home)/2`. `getFreeMemory` reads `MemAvailable` alone in
  current `android_util_Process.cpp`. So the budget's use of `availMem` as MemAvailable is right on current
  Android. Older releases summed MemFree and Cached; the version that changed this is unverified.
- The budget's comment (`ai/TaiLoadBudget.java:41-43`) and `tai-memory-manager.md` §3a call `threshold` "the
  level at which the system starts killing cached apps" (`CACHED_APP_MAX_ADJ`). AOSP assigns the
  HOME_APP_ADJ level. On lmkd/PSI devices these levels come from the minfree table and need not match when
  lmkd kills. The floor is still a sensible number, but the wording overstates what it means.
- **Direction.** Fix the comments. If a "kill line" is wanted, `hiddenAppThreshold` is the cached level.

### T8. Pressure tiers on API 34+ (Medium)

- `tierForTrimLevel` maps only `TRIM_MEMORY_RUNNING_LOW` and `RUNNING_CRITICAL` (`ai/TaiPressureWatch.java:89-98`).
  AOSP `ComponentCallbacks2` marks both `@deprecated Apps are not notified of this level since API level 34`.
  On pong (Android 16) `onTrimMemory` never reaches a tier, and the 2 s `MemoryInfo` poll is the only watch.
  `tai-memory-manager.md` phase 4 describes the mapping as live.
- **Direction.** Keep the poll as the mechanism and say so. Optionally shorten the poll while a load or a
  first prefill is running.

### T9. Cross-process SharedPreferences (Medium)

- `TaiRuntimeCrashMarker` writes and removes `KEY_MARKER` in `TaiSettings.PREFS_NAME` from the runtime
  process (`ai/TaiRuntimeCrashMarker.java:34,40,54`). The app process edits the same file for every TAI
  setting. AOSP `Context.MODE_MULTI_PROCESS` is deprecated: SharedPreferences "does not provide any mechanism
  for reconciling concurrent modifications across processes … use … ContentProvider". This is the defect that
  `TaiRuntimeHistory` already left the prefs for (`ai/TaiRuntimeHistory.java:35`). `intelligence-director…`
  §7.1 lists it as open.
- **Direction.** Move the marker to its own locked file, as was done for runtime history and `TaiEventLog`.

### T10. GPU vision encoder never paired with a CPU decoder (Medium, opportunity)

- When the budget picks the CPU, `options.accelerator` becomes `cpu` (`ai/TaiManager.java:3093-3096`), and
  `useGpuVision` then returns false (`ai/LiteRtTaiRuntime.java:1054-1077`). `EngineConfig` takes a separate
  `visionBackend` (LiteRT-LM v0.17.1 `Config.kt`), but the budget's ladder has no "CPU decoder + GPU vision"
  rung. The comparison note says most of the E4B CPU time is the vision encoder plus prefill.
- **Direction.** Measure CPU decoder + GPU vision for E4B `-vision` (memory and time). If the encoder fits,
  add the rung between "GPU at floor" and "CPU".

### T11. Director sampling differs from the model card (Low)

- The director sends `temperature 0` (`living/SceneReader.java:109`). The Gemma 4 card says "Use the following
  standardized sampling configuration across all use cases: temperature=1.0, top_p=0.95, top_k=64". The pong
  data shows greedy decoding is deterministic and useful here (`director-comparison-2026-10-04.md`), so this
  is a recorded deviation, not a bug. A constrained schema (T4) makes the trade-off safer either way.

### T12. MNN configuration against MNN 3.6.1 (Low)

- Keys TAI writes (`ai/MnnTaiRuntime.java:1222-1246`) all exist in 3.6.1 `llmconfig.hpp`: `backend_type`,
  `thread_num`, `power`, `precision`, `memory`, `max_all_tokens` (default 2048), `max_new_tokens`, `prompt_cache`
  (default false), and `speculative_type`. The 3.6.1 `llm.md` documents only some of them: `power`,
  `max_all_tokens` and `prompt_cache` are source-only. A future MNN can drop them silently.
- `power: high` is written at the top level only. `power(mllm=true)` reads the `mllm` section, which defaults
  to `normal` (`llmconfig.hpp`), so a VL model's vision encoder threads stay unpinned.
- The mmap weight cache lives under `getCacheDir()` (`ai/MnnTaiRuntime.java:1303-1342`). AOSP: the system
  deletes cache files "as disk space is needed", oldest first, and apps above
  `StorageManager.getCacheQuotaBytes` go first. A multi-GB mmap set is far above any quota. A partial deletion
  (marker kept, weights gone, or the reverse) is exactly the "MNN never validates" case the comment
  describes. Whether MNN re-converts or reads garbage then is **unverified**.
- **Direction.** Mirror `power` into `mllm`. Move the mmap cache to `noBackupFilesDir` or `filesDir`, with the
  fingerprint pruning that already exists.

### T13. Failure verdicts during a GPU init on a busy phone (Low)

- The auto path records any non-cancel GPU init exception as a GPU failure for 7 days
  (`ai/LiteRtTaiRuntime.java:661-665`; TTL `ai/TaiRuntimeHistory.java:213`). A GPU allocation that failed
  because memory was short at that moment is a moment, not a verdict, the same reasoning `BudgetRefusal`
  applies to the CPU (`:716`). **Unverified:** whether LiteRT-LM's message tells the two apart.

### T14. Things checked and found consistent

- The `availMem` = MemAvailable reading (T7). The flag timing for speculative decoding (T4). The cancelled-load
  and budget-refusal exclusions from failure history (`ai/LiteRtTaiRuntime.java:713-731`). The failure TTL and
  app-version expiry (`ai/TaiRuntimeHistory.java:213-237`). The `TaiEventLog` cross-process lock and rotation
  (`ai/TaiEventLog.java:37-54,164-167`).
- `cacheDir`: LiteRT-LM 0.17.1 defaults it to the model's directory ("If not set, it uses the directory of the
  [modelPath]"). TAI passes null except for `/data/local/tmp` models (`ai/LiteRtTaiRuntime.java:947-948`). That
  is right, since TAI's model folders are writable.

---

## 2. Living-wallpaper creator

### W1. Petals white and everywhere: the cause (High, visible)

- **Blend.** Particle kinds 1–5 and 7–10 end in `col += pa * pc * C.b * pI` (`wp/LivingStill.java:375`). Petal
  colour is `(1.0, 0.74, 0.82)` (`:373`), and the result is clamped to 1 in `TAIL` (`:456`). On any mid-bright
  pixel, adding a pastel saturates all three channels, so the petal renders white. Debris, the one opaque
  kind, uses `mix` (`:350-351`), which is why debris keeps its colour.
- **Coverage.** On the plan path, `particles = (1 − subject)(1 − glow)(0.35 + 0.65(1 − depth))(1 − still)`
  over the whole frame (`living/ElementMasks.java:187-198`). No element, sky or region gates it. The density is
  `cs = 15` cells per unit width with 30 % of cells occupied and a radius of 0.11 cell (`wp/LivingStill.java:359-367`).
  That puts on the order of a hundred-plus petals on a portrait frame at once.
- **Direction.** Use `mix(col, pc, pa·C.b·pI)` for opaque kinds (petals, leaves, snow) and keep additive
  for emissive ones (glints, fireflies, embers, sparks). Gate petals and leaves to a band below and beside
  the `flowers`/`trees` element boxes (or the sky for snow and rain). Lower `cs`/occupancy for petals. Add
  a device-test frame that asserts the mean hue inside a petal is not white over a mid-grey photo.

### W2. Still pixels are drawn at a different zoom from their neighbours (Medium)

- `scene()` zooms every moving sample by `(1 − 0.03e)` about the centre: `uv = 0.5 + (uv0 − 0.5)(1 − 0.03e)`
  (`wp/LivingStill.java:151`). Protected pixels take `s = mix(s, uv0, stl)` (`:217`), the un-zoomed rest
  position. At `e = 1` a pixel 0.5 from the centre is offset by 0.015 of the image (about 16 px on a 1080 px
  frame) relative to a pixel just outside the still mask. Across the feathered mask edge, the two positions
  blend into a double contour. `maskD` itself is read at the drifted `s` (`:170`), so the protected area
  also slides against its content.
- The device test asserts "A protected pixel equals the rest pixel under wind and drift"
  (`LivingStillAgslInstrumentationTest.java:144-151`), which fixes the offset in place.
- **Direction.** Mix toward `uv` (zoomed, undisplaced), not `uv0`. At rest `uv == uv0`, so the rest-pose rule
  still holds. Change the test to compare against a frame drawn with displacement off rather than against rest.

### W3. Director timeout and cancel (High; see T3)

- Repeated here because the user sees it: the progress bar sits at the director's step with no updates for up
  to three minutes (`living/LivingStillJob.java:44-46`, `living/LivingStillBuilder.java:105-121`). Cancel does
  not shorten that.

### W4. Lock-screen colours (Medium)

- `onComputeColors` returns `new WallpaperColors(p0, p1, p2)` from `WallpaperPaletteCapture.own`
  (`wp/LockLiveWallpaperService.java:227-233`). For a living still that is the constant
  `OWN = {0x2A2A2A, 0xB0B0B0, 0x808080, 0x101010}` (`wp/LivingStill.java:463,480`), whatever the photo.
  `WallpaperPaletteCapture` says "always its own palette, taken from its photo" (`wp/WallpaperPaletteCapture.java:6`).
- AOSP `WallpaperColors(primary, secondary, tertiary)` starts hints at 0 and adds only `HINT_SUPPORTS_DARK_THEME`
  when the primary's HSL lightness is below 0.3. With `0x2A2A2A` primary, every living still tells SystemUI
  "dark wallpaper, light text", even a bright beach photo. `HINT_SUPPORTS_DARK_TEXT` ("A launcher may set its
  text color to black if this flag is specified") is never set.
- **Direction.** Compute colours once per still with `WallpaperColors.fromBitmap(image)` (AOSP: "Main colors
  will be extracted from the bitmap"; it derives the hints), cache it next to the manifest, and fix the
  comment. Update `ownPalette` for the in-app palette too, if the photo is meant to drive it.

### W5. Mask and depth are bound as colours (Medium, latent)

- Depth, the four masks and the effects map are bound with `setInputShader` (`wp/WallpaperUniforms.java:185-190,120`).
  AOSP `RuntimeShader`'s class doc says bitmaps holding "heightmaps, or any other purely mathematical data"
  should use `setInputBuffer`, because `setInputShader` children are colour-managed into the destination's
  working space. `setInputBuffer` returns "samples directly from the bitmap's buffer … no transformation …
  such as colorspace conversion or alpha premultiplication".
- No window sets a wide colour mode (no `setColorMode`/`colorMode` in the app), so the working space is sRGB
  and nothing shifts today. A P3/HDR window or wallpaper surface later would quietly bend every mask
  threshold. **Unverified:** the colour space of the wallpaper surface on pong.
- **Direction.** Switch depth, masks and the effects map to `setInputBuffer` (API 33, same as the class floor).

### W6. Recipe rules read the plan, not the masks (Low)

- `fromPlan` sets `waterMode` from the first moving `water` element and enables glints when `plan.has("water")`
  (`living/RecipeRules.java:126-137,187-189`). `ElementMasks` may have dropped that element as warm (the
  pink-meadow guard, `living/ElementMasks.java:108-111`) or the cross-check may have emptied it. The recipe
  then says `lake` and `glints` over a zero water mask: no visible effect, but `uNeed` turns on mask
  sampling (`wp/LivingStill.java:547-550`) and `recipe.json` misdescribes the still. The warnings are not
  persisted (`living/LivingStillBuilder.java:143-146`).
- **Direction.** Pass `em.stats` coverage into `fromPlan` and switch an effect off when its plane is empty.
  Persist `warnings` under `director`.

### W7. Wind direction from light direction (Low)

- `windDirDeg` comes from whether `light_direction` contains "left" or "right" (`living/RecipeRules.java:149-153`).
  Light and wind are unrelated. Either ask the director for `wind_dir` (it already describes the scene), or
  keep a fixed default and drop the parse.

### W8. Builder deletes the working recipe before it starts (Low)

- `build` deletes `recipe.json` first (`living/LivingStillBuilder.java:84-85`). "Read again" builds over the
  still's own folder, so any later failure (decode, director exception escaping, write) leaves a folder
  `Manifest.load` rejects. The still the user had is gone. Build into a sibling temp folder and rename at the
  end, as `Manifest.writeRecipe` already does for the one file (`living/Manifest.java:61-70`).

### W9. No cap on mask size; no EXIF orientation (Low–Medium)

- Masks are `photo width / 4` with no ceiling (`living/LivingStillBuilder.java:36-38,90-91`). `ElementMasks`
  holds about 20 full planes plus one per element (`living/ElementMasks.java:88-229`). A 50 MP photo (8160 px
  wide) gives about 2040 × 1530 = 3.1 M px, 12.5 MB per plane, and several hundred MB in the app heap. Photos
  are kept as raw copies (`wp/RecentWallpapers.java:183`). Cap the mask long side (for example 1024), which
  the shader samples bilinearly anyway.
- Every decode uses `BitmapFactory` (`living/LivingBitmaps.java:39-51`, `wp/LivingStillTextures.java:87-104`).
  BitmapFactory does not apply EXIF orientation, and no code in the app reads EXIF. A camera JPEG with a
  rotation tag is analysed, read by Gemma, masked and played sideways (consistently). **Unverified** on device.

### W10. Gemma 4's own detection format (Low)

- Google's Gemma image guide documents that the models "are trained to detect objects … expressed as
  normalized values relative to a 1000x1000 grid", in the order `y1, x1, y2, x2`, under the keys `box_2d` and
  `label`. The examples put the image before the text. `SceneReader` uses the same order and grid
  (`living/SceneReader.java:65,231-252`) and the same descaling (`living/ElementMasks.java:65-73`). That is
  correct. But it names the key `box`, and the prompt comes before the image (`:103-104`).
- **Direction.** Accept `box_2d` as an alias in `box()`. It costs nothing, and a model leaning on its training
  will sometimes emit it. Try image-first ordering in the next comparison run.

### W11. Lock engine and the redraw contract (Low)

- The engine draws from a Choreographer callback scheduled in `onSurfaceChanged`/`onVisibilityChanged`
  (`wp/LockLiveWallpaperService.java:180-213,306-315`). It does not override `onSurfaceRedrawNeeded`. AOSP
  `SurfaceHolder.Callback2.surfaceRedrawNeeded`: "By not returning from here until the redraw is complete" the
  app keeps the user from seeing the surface "in a bad state". One synchronous `draw`/`drawRest` there would
  close the window for an unpainted first frame on wake.
- `onCommand` handles the three `@hide` commands correctly. The strings match AOSP `WallpaperManager`
  (`android.wallpaper.keyguardgoingaway`, `goingtosleep`, `wakingup`). `COMMAND_FREEZE`/`UNFREEZE` ("the live
  wallpaper needs to be frozen") also exist and are ignored. Stopping frames on freeze is cheap.

### W12. Time precision late in the day (Low)

- `uTime` wraps on a whole number of periods within a day (`wp/WallpaperDirector.java:224-227,360`), so it
  reaches about 86 400 s. Terms like `vnoise(float2(s.x*260, s.y*16 − t*7))` (`wp/LivingStill.java:242`) then
  have arguments near 6 × 10⁵. float32 spacing there is 1/16, so the falling-water streak noise steps late in
  the day. `hash21`'s `sin(dot(p, (127.1, 311.7)))` gets very large arguments. Wrap `t` per effect, for example
  `mod(t, period_of_that_term)`, or wrap the clock at the still's own 120 s with the non-periodic terms
  re-phased.

### W13. Licensing dependency (known, restated)

- SegFormer B0 is marked "NVIDIA source code licence (non-commercial, testing only)" (`ai/TaiModelCatalog.java:381-386`).
  The analysis needs it on both paths: `loadAnalysis` throws when there are no scene groups
  (`living/LivingBitmaps.java:130`), and the rules fallback and the plan cross-check read it. Depth Anything 3
  Small is Apache-2.0 (its LiteRT card: "inherited from the upstream Depth Anything 3"). U-2-Net is Apache-2.0
  per the catalogue. The feature cannot ship without replacing SegFormer, which is already noted as round 3.

### W14. Checked and found consistent

- Depth convention: `WallpaperVisionMath` normalises to near = 1 and flips DA3's distance output
  (`ai/WallpaperVisionMath.java:194-196`). `ElementMasks`' depth bands and mist use near = 1 consistently.
- Box parsing tolerates fences, strings-as-numbers and two missing braces, and rejects out-of-range or
  inverted boxes (`living/SceneReader.java:152-169,231-252`). A truncated answer (cut inside `elements`) is
  always rejected, because only `}` is appended. Closing `]` as well would rescue complete elements; low value
  while `max_tokens` (700) is ample.
- `BitmapShader.setFilterMode(LINEAR)` on children, the CLAMP tiling, and pixel-space `eval` coordinates
  (`wp/WallpaperUniforms.java:204-209`) match the `RuntimeShader` doc ("does not use normalized coordinates …
  (0, 0) in the upper-left corner, and (width, height) in the bottom-right").
- Split render: the effects map in an RGBA8 hardware buffer, `0.5 + d·25` (`wp/LivingStill.java:30-33`), gives
  a step of about 1.6 × 10⁻⁴ image width, below a pixel.

---

## Sources

- LiteRT-LM Kotlin getting started: https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/docs/api/kotlin/getting_started.md
- LiteRT-LM v0.17.1 `Config.kt` (EngineConfig, ConversationConfig, SamplerConfig): https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt
- LiteRT-LM v0.17.1 `Conversation.kt` (sendMessageAsync, maxOutputToken, responseFormat, cancelProcess): https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt
- LiteRT-LM v0.17.1 `ResponseFormat.kt`: https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/ResponseFormat.kt
- LiteRT-LM v0.17.1 `ExperimentalFlags.kt`: https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt
- LiteRT-LM v0.17.1 `Engine.kt`: https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Engine.kt
- LiteRT-LM tags (v0.17.1 newest): https://github.com/google-ai-edge/LiteRT-LM/tags
- MNN 3.6.1 `docs/transformers/llm.md`: https://raw.githubusercontent.com/alibaba/MNN/3.6.1/docs/transformers/llm.md
- MNN 3.6.1 `llmconfig.hpp`: https://raw.githubusercontent.com/alibaba/MNN/3.6.1/transformers/llm/engine/src/llmconfig.hpp
- Gemma 4 E4B model card: https://huggingface.co/google/gemma-4-E4B-it
- Gemma image understanding guide (detection format, token budgets): https://ai.google.dev/gemma/docs/capabilities/vision/image
- Gemma docs overview: https://ai.google.dev/gemma/docs/core
- Depth Anything 3 Small (LiteRT) card: https://huggingface.co/litert-community/Depth-Anything-3-Small
- AOSP `ProcessList.java` (getMemoryInfo): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/services/core/java/com/android/server/am/ProcessList.java
- AOSP `android_util_Process.cpp` (getFreeMemory): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/jni/android_util_Process.cpp
- AOSP `ComponentCallbacks2.java`: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/content/ComponentCallbacks2.java
- AOSP `Context.java` (MODE_MULTI_PROCESS, getCacheDir): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/content/Context.java
- AOSP `WallpaperManager.java` (COMMAND_ constants): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/app/WallpaperManager.java
- AOSP `WallpaperColors.java`: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/app/WallpaperColors.java
- AOSP `SurfaceHolder.java` (Callback2): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/view/SurfaceHolder.java
- AOSP `RuntimeShader.java` (setInputBuffer, colour management, coordinates): https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/RuntimeShader.java
- Android AGSL vs GLSL (colour spaces, `layout(color)`): https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl

AOSP files were read at `main` on the aosp-mirror GitHub mirror, not at a release tag. The behaviour cited
(MemAvailable, HOME_APP_ADJ, deprecation since API 34) may differ on older releases.

## Unverified

- Where LiteRT-LM 0.17.1 allocates the vision encoder's and the prefill's memory (at `initialize()` or at the
  first send), and so how much of the director's peak `TaiLoadMeter` sees (T2). Needs a meter run spanning the
  first send on pong.
- The ~2.5 GB CPU-director drop in T2 is derived from the comparison note's "5.7 GB free" and the row's lowest
  MemAvailable. The note does not give a per-row starting figure.
- How many tokens one `MessageCallback.onMessage` carries with MTP speculative decoding on (T4). The
  LiteRT-LM docs do not say.
- Gemma 4's visual token count for the director's 768 px image with `visualTokenBudget` unset, and whether
  280 keeps box quality (T4). Recording `lastPrefillTokens` from the response's `tai` block into
  `recipe.json` would answer the first.
- Whether `box_2d` or image-first ordering changes the answers (W10).
- Which Android release switched `Process.getFreeMemory` from MemFree + Cached to MemAvailable (T7). This
  matters for the HTC test device (Android 9).
- The colour space of the lock wallpaper surface and of the launcher's backdrop on pong (W5).
- What MNN 3.6.1 does when part of an mmap cache directory is deleted by the system (T12).
- Whether LiteRT-LM's GPU init error text separates "out of memory" from "unsupported" (T13).
- EXIF-rotated photos on device (W9). No test photo with a rotation tag was tried.
- Whether ~150 simultaneous petals is the real on-screen count. That is arithmetic from the shader constants,
  not a measurement.
