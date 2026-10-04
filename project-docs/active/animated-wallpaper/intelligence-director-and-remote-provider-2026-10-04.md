# Living stills: the director role, Gemma 4 E2B, and a bring-your-own-key provider

Status: proposal, 2026-10-04. Nothing here is built. Developer decisions are listed at the end.

## 1. Review of what exists (dev 2adbaf512 → e291022f0)

What is right and should stay:

- The split of roles. Pixel masks come from deterministic models (SegFormer, U-2-Net, depth);
  the LLM only *reads* the scene and picks regions and styles; AGSL only *renders* a fixed
  program whose recipe is uniforms. Every failure falls back one level (Gemma → rules → still).
- The contract is already OpenAI chat-completions JSON. Every intelligence caller goes through
  `TaiManager.openAiChatCompletions(body[, timeoutMs])`: the category sort service
  (`LauncherCategorySortService:344`), the wallpaper reader (`TaiGemmaChat:298`), dictation
  cleanup (`LocalTaiVoiceTextPolisher:175`), the import probe and the 41237 server. One seam.
- Strict parsing with tolerance for small-model mistakes (marks past k dropped, fences skipped).
- The manifest cache per photo hash, written atomically; split render; the vsync-median budget.

What is holding the goal back:

| # | Finding | Where | Cost |
|---|---|---|---|
| 1 | The director is hard-coded to **E4B** `-vision`, loaded with the full model on **CPU**: 45–57 s, ~3.9 GB. The wrong model for a 400-token JSON answer. | `GemmaSceneReader:30`, `TaiGemmaChat:310` | Users wait a minute; 8 GB phones never get the step |
| 2 | The LLM decides little: six region lists and five enums. Drift, sway amplitude, mist amount, water parameters are constants in `RecipeRules`. `style` is parsed and never read. | `RecipeRules:49-161`, `LivingRecipe` | The "intelligence" ceiling is the schema, not the model |
| 3 | The raw plan is not persisted. "Read again" re-asks Gemma even when only the rules changed. Vision timings are deleted with the analysis folder. | `Manifest`, `LivingStillBuilder:81-146` | No offline iteration on rules; no field data |
| 4 | Mask quality is bounded by SegFormer (non-commercial) plus 10 colour clusters. The LLM cannot name a region finer than a cluster. | `RegionMasks`, `ColourClusters` | The proposed SAM "hands" is the right fix, not a better LLM |
| 5 | No path for phones without local LLM hardware, and no measurement of Home frame cost after the split render. | handoff items 4 and 6 | |

## 2. The ideal shape: Director, Hands, Stage

Keep the three roles and make each one swappable behind a schema:

- **Hands** (always local, deterministic, ~5 s CPU): depth, segmentation, saliency; later SAM 2.1
  Tiny for prompted masks. These never leave the device and never depend on a chat model.
- **Director** (any chat-completions model): reads the picture and returns a **Scene Plan**
  (strict JSON). The director may be Gemma 4 E2B on the phone or a remote model behind the
  user's own key. It never produces shader code.
- **Stage** (AGSL): one program whose recipe is uniforms. It grows as a library of effect modules
  with one to three knobs each; every knob is a Scene Plan field with a rules default.

Why not let the model write AGSL: compile failures and unbounded frame cost on the home screen,
a 2B model cannot write SkSL reliably, and a remote model that can would make every wallpaper a
unique program the test suite cannot cover. Declarative intelligence over deterministic code is
the version of this goal that can ship.

### Scene Plan v2 (what the director fills in)

Superset of today's `Plan`, all fields optional with rules defaults:

```
style, regions{water, falling_water, sky, foliage, lights, subject}     // by mark today; points/boxes later for SAM
water_style, sky_motion, light_style, trail_angle_deg, particles, intensity   // as today
drift 0..1, sway{speed, amp}, mist{amount, colour}, water{freq_x, freq_y, amp_x, amp_y}
time_of_day day|dusk|night, wind_dir_deg, palette_accent #rrggbb, mood calm|lively
effects_off [names]                                                      // a director may veto an effect
```

Persist the raw plan in `recipe.json` as `plan` (recipe version 2, old manifests still load), plus
`analysis.timingsMs`. `RecipeRules.make(stats, plan)` then becomes replayable offline on the
8 test wallpapers, and the per-effect UI (handoff item 5) reads and writes the same plan.

## 3. Gemma 4 E2B as the director: feasibility

Measured numbers already on file (pong, Nothing Phone 2, Adreno 730):

| | E4B `-vision`, CPU (today) | E2B standard file, GPU |
|---|---|---|
| Load | 45–57 s (handoff) | 5.5–6.4 s (`voice-cleanup-benchmark-2026-09-27.md:13,86`) |
| First token | ~5 s class (card: 5.3 s CPU) | 0.5–0.7 s |
| Memory held | ~3.9 GB (`TaiGemmaChat:310`) | ~2.6 GB estimate (`tai-memory-manager.md:94`); card 676 MB CPU-side |
| Catalogue tier | 12 GB+ | 8 GB+ |
| Vision | yes | yes: Gallery lists `vision gpu` for Gemma-4-E2B (`gallery-gpu-loading-comparison.md:147`) |
| Output quality | refused an injection-shaped prompt; cleaner on self-corrections | kept a double negative at "polished"; weaker on counting |

The `-gpu` bundle corruption on Adreno 730 does not apply: the catalogue ships only the standard
file, and the standard file on the GPU backend measured "Use it".

Verdict (desk, superseded by the measurement below): **feasible and should be the default director.** It cuts the step from about a minute
to under ten seconds and opens the step to 8 GB phones. Two risks, both testable:

1. Set-of-mark counting. E4B already miscounts (the parser drops marks past k). E2B may pick
   neighbouring numbers more often. Mitigation already exists: SegFormer leads and Gemma fills
   only missed groups; the shape gates stay.
2. GPU admission. `TaiLoadBudget` may choose CPU at the 4k floor when free RAM is low; the step
   asks for a 2048 window, which helps. Record the chosen backend in `recipe.json`.

Code change (small, this session can do it): `GemmaSceneReader` prefers `gemma-4-e2b-it-litert-lm`
when installed, else E4B; the model id comes from one `LivingDirector.modelId(ctx)` so the remote
provider can override it; `gemmaModel` already records which ran.

Test plan (needs the developer's go, pong is in use): the 8 test wallpapers through pong's
`/v1/chat/completions` with `-vision` ids for E2B and E4B, same prompt and images; score region
picks as IoU against the SegFormer groups where SegFormer is confident, and list style choices
side by side for a visual judgement. Add a remote model as a third column once the provider exists.
Watchdog on `MemAvailable` throughout.

### Measured 2026-10-04 (see `director-comparison-2026-10-04.md`)

E2B runs the step on the GPU in 12–16 s warm against E4B's 40–50 s on the CPU, but it reads scenes
badly: eight of ten regions called water on the BMO picture, no styles, marks written as strings, and
no improvement from stricter wording. E4B goes to the CPU because of the memory budget (~5.7 GB free
needed for the GPU), not only because of the stale failure record. **Decision 1 is therefore
reversed: E4B stays the director when installed; E2B is the fallback for phones without E4B, behind
an over-listing guard; the remote director is the speed fix.**

## 4. Bring-your-own-key provider (OpenAI-compatible)

### 4.1 User-facing

A **Remote provider** category in TAI settings (`termux_ai_preferences.xml`, after the models
category; settings search indexes it for free):

- Base URL (`https://api.openai.com/v1`, `https://openrouter.ai/api/v1`, `http://192.168.1.5:11434/v1`, …).
- API key, masked, edited in a dialog like `TaiHuggingFaceTokenDialog`. Optional for local servers.
- **Fetch models**: `GET {base}/models` → picker over `data[].id`. If the endpoint is missing or
  returns 404/401, a free-text model field appears instead.
- **Understands images**: set by a one-shot probe on selection (a 2×2 PNG and "name the colour");
  a 400 that mentions images marks the model text-only. The user can override.
- **Use it for**: App categories · Wallpaper director · Dictation cleanup (default on when a key is
  saved) · Chat API on port 41237 (phase 2) · Speech (later, off).
- **When**: *Prefer remote* (default once configured) / *Only when no local model fits*.
- A plain line under the key: "Text and, for the wallpaper director, two small JPEGs of your photo
  are sent to this address." Shown again the first time Bring to life runs with remote on.

### 4.2 Wiring

New, all in `com.termux.ai`:

- `TaiSecretStore`: AES-GCM key in `AndroidKeyStore`, ciphertext in the existing `termux_ai`
  prefs. ~80 lines. (`androidx.security:security-crypto` is deprecated; do not add it.) Move the
  Hugging Face token into it in the same change. Add both to `TaiSettings.redactToken` and the
  diagnostics redaction; `toJson` exposes only `remoteConfigured`.
- `TaiRemoteProvider`: settings record (base URL, model, flags, image capability) + `isEnabledFor(feature)`.
- `TaiRemoteClient`: `HttpURLConnection` POST JSON with bearer header, timeouts from the caller,
  `chatCompletions(body)` and `chatCompletionsStream(body, OpenAiStreamSink)` (an SSE line reader
  feeding the existing sink), `listModels()`. Outgoing body sanitiser: strip `context_window` and
  every `_tai*` key; on a 400 naming `max_tokens`, retry once with `max_completion_tokens`; send
  `response_format: {type: json_object}` only when the caller sets a private flag and drop it on a
  400. Errors map to the existing `openAiError` shape, so callers do not change.
- Routing: at the top of `TaiManager.openAiChatCompletions(String, long)` (`:1652`) and the
  stream variant (`:1788`), before `shouldDelegateRuntime()`: if the request's model is
  `remote/<id>`, or no model is given and the provider's policy says remote for this feature, hand
  the body to `TaiRemoteClient`. Network stays in the app process; the `:tai_runtime` process is
  never woken for a remote call.
- Model namespace `remote/<provider model id>`, kept **outside** `TaiModelSpec`/`TaiModelStore`
  (`TaiModelSpec` rejects unknown backends at `:313-348`, and `CatalogEntry` assumes a Hugging
  Face repo). `openAiModels` (`TaiManager:2037`) appends the remote entry in phase 2 so `tai`,
  aichat and the 41237 clients can see it.

Feature gates become one question, `TaiIntelligence.available(ctx, feature)` = remote enabled for
the feature, or a fitting local model is installed:

- App categories: `CategorySortDialogs.resolveModel/unavailableReason` (`:81,96`) learn about
  remote. With remote, send **one batched call** using the existing pasteable prompt and parse it
  with the existing `parsePastedReply` instead of one call per app. This is also faster and cheaper
  than per-app calls.
- Wallpaper director: a `RemoteChat implements GemmaSceneReader.Chat`; `LivingStillBuilder`
  picks it when the provider is on and the model understands images; `recipe.json` records
  `remote/<id>`; the progress label says "Asking <model>…" instead of "Asking Gemma…".
- Dictation cleanup: a `RemoteVoiceTextPolisher implements VoiceTextPolisher` (the javadoc already
  reserves this seam).

### 4.3 Things that bite

- **Cleartext**: release builds set `usesCleartextTraffic=false` and there is no
  `network_security_config`. An `http://` LAN Ollama or LM Studio URL fails in release. Options:
  (a) a `network_security_config` permitting cleartext only for `localhost`/`127.0.0.1` and a
  warning that LAN hosts need `https`; (b) permit cleartext app-wide; (c) tell the user to use
  Termux's own `ssh -L` or a Tailscale HTTPS URL. Developer decision below.
- **Vision capability** cannot be read from `/models` on most servers; hence the probe.
- **Local server answers** differ: Ollama's `/v1` ignores `response_format` silently, some
  return `content` as an array. The existing `TaiGemmaChat.ask` already reads both shapes.
- **Cost and size**: the wallpaper call sends two JPEGs at 768 px (about 100–200 KB each) and
  asks for 400 tokens. Categorisation batched is one call. Cleanup is one short call per session.
- **Logs**: `tai logs` and the diagnostics JSON must never print the `Authorization` header or
  the key; add a unit test for the redaction.
- **No SSRF guard needed** for a user-typed URL, but refuse `file:` and non-http schemes.

### 4.4 Speech

Not in scope now (agreed). The seams, for later: `TaiManager.transcribe` (`:2458`) already has
the WAV path for a multipart `/audio/transcriptions`; `TaiManager.speak`/`synthesizeSpeech`
(`:2551,2639`) would need remote PCM played in the app process, and `TaiSpeechModels`/`TaiTtsModels`
`resolveActive` would need to count a remote as a model.

## 5. Order of work

| Phase | Content | Size | Who |
|---|---|---|---|
| 0 | E2B-first director, model id behind `LivingDirector`, recipe v2 with `plan` + timings + backend | ~150 lines, 5 files | one fixer |
| 1a | `TaiSecretStore` + HF token migration + redaction tests | ~250 lines | fixer (parallel) |
| 1b | `TaiRemoteProvider` + `TaiRemoteClient` (+ SSE) + body sanitiser + JVM tests against a local mock server | ~500 lines | fixer (parallel) |
| 1c | Settings category, key dialog, Fetch models picker, probe, consent copy | ~400 lines + XML | fixer (after 1b's interfaces are fixed) |
| 1d | Routing in `TaiManager` + the three feature gates + batched categorisation | ~300 lines | fixer (after 1b) |
| 2 | `remote/<id>` in `/v1/models` and the 41237 proxy; streaming through `tai chat` | ~200 lines | fixer |
| 3 | Scene Plan v2 fields → `RecipeRules` + uniforms; per-effect Motion controls; comparison harness E2B vs E4B vs remote; then SAM hands | separate spec | |

Phase 0 and the E2B comparison can run before any provider work and already answer "is E2B good
enough". Phase 1 pieces have disjoint files except `TaiSettings` (1a and 1b both add keys; give
1b the keys and let 1a only add the store).

## 6. Decisions

1. **Settled by measurement 2026-10-04:** E4B stays the director; E2B only as the fallback when E4B is
   absent (with an over-listing guard). See §3 and `director-comparison-2026-10-04.md`.
2. **Approved 2026-10-04:** cleartext as recommended in §7.2: manifest allows it, the client accepts
   `http://` only for localhost and private LAN / Tailscale hosts, with an "unencrypted" note.
3. **Approved 2026-10-04:** default routing *Prefer remote*, with per-feature toggles (§7.2).
4. **Decided 2026-10-04: no `tai chat` serving.** The remote model serves only the launcher's
   intelligence features. For dawn, see §7.3: dawn already has its own `provider: openai` bridge,
   so nothing in `tai chat` is needed; the optional 41237 proxy (phase 2) would let dawn's default
   `provider: tai` inherit the key, but it is not required.
5. **Approved 2026-10-04:** consent copy = the settings line under the key plus a one-time dialog
   the first time Bring to life runs with the remote director on.
6. **Done 2026-10-04** with the developer's go: `director-comparison-2026-10-04.md`.

## 7. Follow-ups from the 2026-10-04 review

### 7.1 Why E4B ran on CPU, and how to make the backend choice self-correcting

The lower layer the developer asked for already exists. Gemma 4's profile says `gpu, cpu`
(`TaiModelProfile`, Gallery defaults), `TaiLoadPreflight.autoAccelerators` (`:74-92`) tries them
in that order, and `TaiLoadBudget.plan` (`:312`) walks a ladder: GPU at the wanted window, halving
to 4k, then CPU at the floor. Two things pushed E4B to CPU on pong, confirmed read-only on
2026-10-04 (`tai --json runtime`, `files/tai/events.log`):

1. **A cancelled load was written down as a GPU failure.** The runtime history holds
   `gemma-4-e4b-it-litert-lm | gpu | success=false | reason="Model load cancelled."` at 06:51
   local. `TaiManager.recordRuntimeResult` (`:3812-3821`) records *every* non-ok load result as a
   failure of that accelerator; `isAcceleratorVerdict` filters only preflight refusals, not runtime
   results. From that moment `autoAccelerators` demoted GPU for E4B (and for its `-vision` variant,
   which shares the base id) and every automatic load went CPU-first. The event log shows seven
   `-vision` loads that day, all `accel=cpu ctx=2048`. The load itself takes ~1.3 s on CPU; the
   45–57 s is the CPU vision encoder plus prefill of two images.
2. **The memory budget would also have refused GPU on a busy phone.** The measured worst GPU load
   for E4B at 4k is 3.07 GB (11 samples); with the 10% headroom and the 1.5 GiB reserve, GPU needs
   about 5 GB free. E2B needs about 3.9 GB free by the same arithmetic, which is why it is the
   better director regardless of the first point.

Fixes, in the layer the developer asked for (all small, `com.termux.ai`). **Approved 2026-10-04**; the first three are being built, the fourth (speed-aware) follows:

- **Only a verdict is a failure.** `recordRuntimeResult` records a failure only for codes that
  say something about the accelerator (native init error, unsupported op, corrupt output, crash
  marker). Cancellation, timeout, low memory and a missing file are not recorded. Add the runtime
  result codes to `isAcceleratorVerdict` (or a sibling `isRuntimeVerdict`) and a unit test that a
  `CancellationException` result leaves the history untouched.
- **Failures expire and are retried.** A recorded accelerator failure is demoted, not banned:
  after N days (or after the app version changes) the next automatic load tries it once more.
  Today a single bad day locks the GPU out until the model is deleted.
- **Say why, every time.** When the plan's accelerator differs from the profile's first choice,
  write an event `accel_fallback` with the reason (`history_failure: <reason>` or
  `budget: needs X MB, Y available`) next to `oom_guard`. Surface the same string as
  `backendFallbackReason` (the field already exists in the runtime status) and copy it into
  `recipe.json` for the director step. A future agent then reads the reason in one line instead of
  reconstructing it.
- **Speed-aware for background jobs.** `TaiRuntimeHistory` records bytes but not time. Record load
  ms and the first decode tok/s per model and accelerator (bench and real loads). Let a background
  intelligence caller mark its request `background: true`; for such a request the manager declines
  a plan whose accelerator is known slow for that model (CPU for a LiteRT file over 3 GB, or
  measured load above a limit) and the caller falls back to the smaller model or to rules, instead
  of grinding for a minute. Interactive chat keeps the slow-but-safe fallback.
- **Keep the ladder.** Forcing GPU unconditionally is the 2026-09-23 freeze again. The rule is
  "prefer the model that fits the GPU", not "force the GPU".

### 7.2 Decisions 2 and 3 in plain words

**2. Cleartext.** Android release builds of this app refuse any `http://` connection and accept
only `https://`. Cloud providers are all `https`, so they just work. The people this blocks are
those running a model server at home or on the phone itself (Ollama, LM Studio, llama.cpp), which
usually live at an address like `http://192.168.1.20:11434` with no certificate. Three choices:

| Option | What the user gets | Risk |
|---|---|---|
| a. Allow `http` only to the phone itself (`localhost`) | A server running inside Termux works; a LAN PC does not | None |
| b. Allow `http` anywhere | Everything works | A key typed into an `http` URL crosses the network unencrypted; fine on a home Wi-Fi, bad on a café's |
| c. Keep `https` only | Cloud only; LAN users must use Tailscale's `https` URL or an `ssh -L` tunnel | None, but LAN users need a manual |

Recommended: **allow `http` in the manifest, but the client itself refuses `http://` unless the
host is `localhost` or a private LAN address** (10.x, 172.16–31.x, 192.168.x, 100.64–127.x for
Tailscale). That gives (a) and the home-LAN case while refusing the café case, and the field shows
a small "unencrypted" note when it accepts an `http` address.

**3. Default routing.** This only matters for a user who has *both* a local model installed and a
remote key saved. "Prefer remote" means every intelligence feature asks the cloud first and uses
the phone only when offline: faster, no RAM, but it costs tokens and the wallpaper photo leaves
the phone. "Local first" means the phone does the work whenever a model is installed and fits,
and the cloud is only the fallback: private and free, but slow on weak phones. Recommended
default: **prefer remote**, because typing a key is a clear statement of intent, with the
per-feature toggles so a user can keep, say, the wallpaper director local.

### 7.3 dawn

dawn's `libai/ai_bridge_openai.c` already reads `<config>/dawn/ai.json` with either
`{"provider":"tai"}` (address from `~/.launcherctl/endpoint`, token from `~/.launcherctl/token`)
or `{"provider":"openai","base_url":…,"api_key":…,"model":…}`. So dawn has bring-your-own-key
today, independently of the launcher, and `tai chat` needs nothing. Two optional improvements:

- The launcher's phase-2 **41237 proxy** (remote model listed in `/v1/models`, chat completions
  forwarded) would make dawn's default `provider: tai` use the remote model with no dawn change
  and no second copy of the key. Recommended if the key should be entered once.
- Alternatively dawn gets a settings screen for its `openai` provider instead of hand-editing
  `ai.json`. Independent of the launcher.
