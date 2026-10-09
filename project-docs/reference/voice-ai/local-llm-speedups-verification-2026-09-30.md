# Local LLM speed-ups: verification against sources and TAI code

Research, 2026-09-30. This file checks the five "top speed-ups" in
[`local-llm-inference-speed-2026-09-30.md`](local-llm-inference-speed-2026-09-30.md) against primary
sources. For each one it then sets out what TAI does today and what change would close the gap.

**Versions read:**
- **MNN:** tag `3.6.1` (commit `d407447e`). This is the version TAI bundles: `.github/workflows/build_mnn_native.yml:22-27` checks out `ref: '3.6.1'`, and `MnnTaiRuntime.java:43` has `RUNTIME_VERSION = "3.6.1"`. It is also the newest MNN tag.
- **LiteRT-LM:** tag `v0.17.1` (commit `5e58e9a0`). This matches `app/build.gradle:12` `litertLmVersion = "0.17.1"`. It is also the newest tag and the newest version on Google Maven.

Both repos were cloned at those tags, and docs and source were read there, not on `master`/`main`.

**Other sources:** model facts come from the Hugging Face API and from model cards, `config.json` and `export_args.json` read on 2026-09-30.

**What was not checked:** nothing was run on a device. pong's CPU layout and TAI's own pong measurements are quoted from the repo and marked as such. **Unverified** marks anything a primary source did not confirm.

## Verdicts

| # | Claim | Verdict | Applies to TAI? | Gap | Cost |
|---|---|---|---|---|---|
| 1 | Fewer bytes per token: 4-bit weights (`--quant_bit 4`, `--hqq`/`--awq`), smallest model that passes | **Confirmed** (MNN 3.6.1 export defaults to 4-bit, block 64, `--hqq` recommended) | Mostly done: every MNN package TAI uses is 4-bit except SmolVLM (8-bit); Gemma 4 LiteRT files use mixed 2/4/8-bit | Some LiteRT files have no 4-bit build (Qwen2.5-1.5B q8, Granite 4.0-h-1b int8, Qwen3.5 int8). For Qwen3-0.6B there is a smaller int4 file than the one in the draft catalogue | Catalogue choice: cheap. New exports: real work |
| 2 | 4 threads, on the big cores only | **Partly.** 4 threads is the default in both runtimes. MNN pins threads to the big cores only when `power: "high"` is set. LiteRT-LM pins only on Pixel Tensor chips | Yes. TAI sets `thread_num` 4 but never sets `power`, so MNN threads are not pinned. LiteRT-LM threads are not pinned on pong | MNN: add `"power": "high"`. LiteRT-LM: no Kotlin API | MNN: cheap config (one key). LiteRT-LM: real work (native) |
| 3 | KV-cache reuse across turns: MNN `reuse_kv` (off, unset in TAI); LiteRT-LM prefix cache | **Partly; the implication is wrong.** `reuse_kv` is off and unset, but TAI's `prompt_cache: true` already reuses the common-prefix KV in 3.6.1. LiteRT-LM's `PrefixCache` is not wired into the 0.17.1 Engine; TAI gets reuse by keeping the `Conversation` alive | Already in place on both runtimes (by code reading) | Not measured. The bench never runs a second turn. Any change to the replayed assistant text re-prefills everything | Measurement only |
| 4 | GPU for prefill, CPU for decode (Gemma-4-E2B S26 Ultra numbers; Gemma 4 `-gpu` corrupt on Adreno 730) | **Partly.** The S26 Ultra numbers are confirmed, but in that table the GPU also wins decode (52.1 vs 46.9 tok/s). Neither bundled runtime can split prefill and decode across backends. The `-gpu` corruption is TAI's own pong finding, with no upstream issue for Adreno | Yes, as a per-load backend choice, and TAI's defaults already follow it: LiteRT-LM GPU first, MNN CPU | No split is possible. `activationDataType` (the likely `-gpu` fix) is not in 0.17.1 | Nothing cheap. Split or retest: real work or wait for a release |
| 5 | Speculative decoding without a second model: LiteRT-LM MTP for Gemma 4 (opt-in, wired); MNN n-gram `lookahead`; EAGLE-3 slower on pong | **Partly.** MTP is confirmed on CPU and GPU, but the model card shows up to about 1.8×, not 3×, and E2B on CPU is sometimes *slower*. Lookahead exists in MNN 3.6.1 and TAI's packages have the required `logits_index` input. The EAGLE-3 finding is TAI's own measurement | MTP: wired, off by default, pinned files are the MTP uploads. Lookahead: TAI cannot select it | MTP: measure on pong. Lookahead: a load-time option | MTP: cheap (bench flag exists). Lookahead: small code change |

**Proposed changes, ranked by value for effort** (full detail in each section):

1. **Measure MTP on pong** (claim 5). No code: `tai benchmark gemma-4-e2b-it-litert-lm --gpu --eagle` against the same command without `--eagle`, then the same pair with `--cpu`.
2. **MNN `power: "high"`** (claim 2). Add one key in `MnnTaiRuntime.mergedConfigJson` next to the `thread_num` default (`MnnTaiRuntime.java:1208`): `if (!json.has("power")) json.put("power", "high");`. Consider adding it to `overridesJson` too (`:1250-1262`).
3. **Check that KV reuse actually happens** (claim 3). No code: run the two-turn script in the measurement plan below. If turn 2 does not drop, find out why before changing anything.
4. **MNN n-gram lookahead as an opt-in** (claim 5). This is a small code change: a load-time option writes `speculative_type: "lookahead"` in `mergedConfigJson` (`MnnTaiRuntime.java:1229`, `:1245-1248`). It is meant for the voice-cleanup and rewrite paths.
5. **Smaller Qwen3-0.6B LiteRT file** (claim 1). This is a catalogue change: use `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` (329 MB, 4096 context) instead of `qwen3_0_6b_mixed_int4.litertlm` (475 MiB, 2048 context) in `project-docs/active/benchmark/recommended-catalog-draft.json:86`, if it passes the bench and the sanity questions.
6. **Pin LiteRT-LM CPU threads** (claim 2). This is real work: native affinity for `:tai_runtime` before `Engine.initialize()`. It is **unverified** that pinning inherits into XNNPACK's worker threads.
7. **Split prefill and decode across backends** (claim 4). Neither bundled runtime supports it. Leave it.

---

## 1. Fewer bytes per token (4-bit weights, smallest model)

**What the sources say**
- MNN 3.6.1 `llmexport.py:854-877`: `--quant_bit` defaults to 4 (2/3/4/8; 2 and 3 need i8mm and FP16), and `--quant_block` defaults to 64. Other flags: `--lm_quant_bit`, `--embed_bit` 16/8/4, `--awq`, `--hqq`, `--omni`, `--sym`.
- MNN 3.6.1 `docs/transformers/llm.md:37` "generally recommends" adding `--hqq`. Everything the earlier file claimed about export flags exists at 3.6.1.
- The Gemma 4 E2B LiteRT model card says the standard file uses a quantization-aware mix of 2-, 4- and 8-bit weights. For text-only use the weights take as little as 0.8 GB in memory, and the 1.12 GB of embeddings are memory-mapped.
- **Unverified:** that decode speed falls in proportion to weight bytes on pong. No primary source here measures it for pong; the earlier file already marked this as an inference.

**What TAI does**
- TAI does not quantize anything itself. It loads whatever the package or file ships. The MNN runtime settings are:
  - `precision: "low"` (fp16 where possible)
  - `memory: "low"` ("enables runtime quantization", per `llm.md` at 3.6.1)
  - both are set in `MnnTaiRuntime.java:1209-1210`.
- Official quantization of each model TAI uses is in the model availability table below. In short:
  - Every taobao-mnn package is 4-bit, except SmolVLM-500M, which is 8-bit.
  - Only the Gemma 4 and Qwen3.5-2B packages record `hqq: true` (in `export_args.json`). The other packages' cards say "4-bit" and give no algorithm.

**Gap and fix**
- **Qwen3-0.6B (LiteRT).** The draft catalogue uses `qwen3_0_6b_mixed_int4.litertlm` (`recommended-catalog-draft.json:86`). The card lists it as 474.61 MiB at 2048 context, with int4 linears and int8 embeddings. The same repo has `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`: 329 MB at 4096 context, with GPU graph optimizations, re-exported 2026-09-20, and stated to run on 0.17.1. It is smaller and has the longer context. Bench both and pick one. Cost: catalogue edit.
- **No official 4-bit LiteRT file** exists for Qwen2.5-1.5B (the draft uses q8, `:36`), Granite 4.0-h-1b (int8 only), or Qwen3.5-0.8B/2B (int8 only). Closing this would take our own conversion: real work, with quality at risk. The Granite 4.2 card shows int4 costing 11 GSM8K points.
- **MNN packages** are already 4-bit. A re-export with `--hqq` for packages that lack it is real work. It is **unverified** whether those packages were exported without HQQ; their cards do not say.

## 2. Four threads on the big cores only

**What the sources say**
- **MNN 3.6.1, `thread_num`:** defaults to 4 (`llmconfig.hpp:181-184`; `llm.md:486`).
- **MNN on OpenCL:** the doc says to use 68 (`llm.md:486`, `:676`). The source sets the mode bits itself, `numThread |= 64 | 512` (`llm.cpp:204-209`), so TAI's 4 becomes 4|64|512 without any change.
- **Core pinning in MNN (source only):**
  - Pinning depends on a `power` key: `normal` by default, or `high` / `low` (`llmconfig.hpp:190-193`; `llm.cpp:210-214`). `power` is **not documented** in `llm.md` at 3.6.1.
  - `CPURuntime::_validateCpuIds` (`source/backend/cpu/CPUBackend.cpp:183-202` at 3.6.1) works as follows:
    - With `Power_High`, it takes CPU groups from the fastest down until it has `thread_num` cores.
    - With `Power_Low`, it takes the slowest group.
    - With `Power_Normal` it leaves the CPU list empty, so threads are not pinned.
  - Groups come from the cpufreq policy directories, sorted by `cpuinfo_max_freq` (`CPURuntime.cpp:1600-1657`).
  - On a layout of policy0 = 0–3, policy4 = 4–6, policy7 = 7, `power: "high"` with 4 threads would pick {7} then {4,5,6}, which is exactly X2 plus 3×A710.
  - **Unverified on pong:** the cpufreq policy layout (the earlier file read the core types and clocks from `/proc/cpuinfo`, not the policy directories), and whether `Power_High` changes anything besides the core list.
- **LiteRT-LM v0.17.1:**
  - The Kotlin `Backend.CPU(threadCount: Int? = null)` uses the native default when null (`kotlin/.../Config.kt:137-147`).
  - The native CPU default is `number_of_threads = 4` (`runtime/executor/llm_executor_settings.cc:180`, `.h:137`).
  - Affinity is set only when `IsPixelTensorDevice()` (`runtime/engine/engine_factory.h:136-142`), so there is no pinning on Snapdragon. This is now confirmed in source. The earlier file had it as inferred from a header.
- **pong's layout** (4×A510, 3×A710, 1×X2; `asimddp`, `i8mm`, no `sve`) was read on the device by the earlier file and is **not re-checked here**. The finding that decode peaks at 4 big cores on the 8+ Gen 1 comes from the arXiv study the earlier file cites [S30]. It was not re-read.

**What TAI does**
- MNN:
  - Default `thread_num` 4: `MnnTaiRuntime.java:1208`, overridden from settings at `:1220`.
  - Per-model setting `thread_count`, default 4, range 1–16: `TaiSettings.java:575`.
  - Every taobao-mnn `config.json` read also ships `thread_num: 4`.
  - **Nothing sets `power`**, so MNN runs with `Power_Normal` and the scheduler decides where the 4 threads go. They can land on the A510s.
- LiteRT-LM:
  - Every `new Backend.CPU()` passes no thread count: `LiteRtTaiRuntime.java:673, 683, 693, 947, 1048`. That means 4 threads, unpinned.
  - `thread_count` is not in the LiteRT settings schema (`TaiSettings.java:556-567`).

**Gap and fix**
- **MNN, cheap:** in `mergedConfigJson` add `if (!json.has("power")) json.put("power", "high");` beside `MnnTaiRuntime.java:1208`. Optionally add a `power` option to `TaiRuntimeOptions` / `overridesJson` for an A/B test.
  - `power` does not change the weights, so the mmap fingerprint (`:1376-1400`) does not need it.
  - Check: `tai.effectiveConfig.power` in a chat response (the value comes from `dumpConfig`), then the per-thread CPU column on the device (see the measurement plan).
- **LiteRT-LM thread count, cheap but low value:** pass `new Backend.CPU(options.threadCount)` and add `thread_count` to the LiteRT schema. This only matters for a 3/5-thread sweep, since the default is already 4.
- **LiteRT-LM pinning, real work:** there is no Kotlin API. It would need a JNI `sched_setaffinity` on the thread that calls `Engine.initialize()`, before the engine starts. The upstream helper says affinity carries into "any child threads it creates". **Unverified** for TAI's call path.

## 3. KV-cache reuse across turns

**What the sources say**
- **MNN 3.6.1 `reuse_kv`:** default `false` (`llm.md:446`; `llmconfig.hpp:160-162`). With it false, `generate_init` clears `all_seq_len` and history (`llm.cpp:824-828`).
- **MNN 3.6.1 `prompt_cache`** (source only; **not documented** in `llm.md`), `llmconfig.hpp:164`, default false:
  - `Llm::response(const ChatMessages&, …)` renders the chat template (`llm.cpp:1114-1189`).
  - It compares the result with the cached text of the previous turn and tokenizes the full prompt.
  - It splits at the token boundary of the common prefix and saves and restores the KV state around `generate_init`, so this works **without `reuse_kv`**.
  - It then prefills only the delta, and logs `[prompt_cache] cached=…, delta=…`.
  - If the new text does not extend the cached text, it clears state and re-prefills everything (`:1128-1142`).
- **MNN interaction to avoid:** `reuse_kv: true` together with `attention_mode: 10` swaps the attention option to 9 (`llm.cpp:171-173`).
- **MNN Android glue** (MnnLlmChat at 3.6.1): `ResponseWithHistory` calls `llm_->syncPromptCache(temp_history)` after a normal finish. After a cancel it calls `eraseHistory(kv_before_decode, 0)` instead (`apps/Android/MnnLlmChat/app/src/main/cpp/llm_session.cpp:535-551`).
- **LiteRT-LM v0.17.1:**
  - `runtime/core/prefix_cache.{h,cc}` and `cached_session.{h,cc}` exist, but no engine or Conversation source includes `cached_session.h`. Only its test does, and `runtime/core/BUILD:50` lists it as an engine dependency. So the prefix cache is **not reachable from the Kotlin API** in 0.17.1.
  - Reuse inside one live `Conversation` is how the Kotlin API keeps KV (**unverified in docs**; read from TAI's design and the API shape).

**What TAI does**
- **MNN:**
  - Writes `prompt_cache: true` in both the merged config (`MnnTaiRuntime.java:1214`) and the extra config (`:1326`). It also sets `keep_history: false` (`:1325`), which only affects the `submitNative` path.
  - Generation uses `LlmSession.generateHistory` → `submitFullHistoryNative` → `LlmSession::ResponseWithHistory` (`MnnTaiRuntime.java:359`). That is the full-history path that syncs the prompt cache.
  - So **multi-turn prefix reuse is on for MNN today**, by code reading. `reuse_kv` is correctly left unset.
- **LiteRT-LM:**
  - OpenAI requests are built with `reusableConversation = true` (`TaiManager.java:3543-3548`).
  - `ensureConversationLocked` reuses the live conversation when the new transcript continues the old one. The key covers mode, system prompt, options and a hash of the tools (`LiteRtTaiRuntime.java:807-853`). Otherwise it starts fresh.

**Gap and fix**
- **Nothing to switch on.** The gap is that no one has measured whether the reuse happens.
- The bench cannot show it: bench v2 starts a fresh conversation for each prompt (`docs/en/On_Device_AI.md`, Benchmark section).
- **Risks that would silently turn reuse off** (both **unverified**):
  - **MNN:** the prefix check compares rendered text. After a turn MNN caches its *own* untruncated reply, with only `<think>` stripped (`llm_session.cpp:538-546`). TAI then returns a reply shortened by stop sequences or with tool-call blocks stripped (`MnnTaiRuntime.java:399-407`). A client that echoes back TAI's version breaks the prefix at that assistant message, and every later turn re-prefills.
  - **LiteRT-LM:** `TaiConversationTranscript.continuesFrom` must match. A client that rewrites earlier messages starts a new conversation.
- **Fix if the measurement shows no drop (MNN):** call `syncPromptCache` with the text TAI actually returns. Today the native side syncs with its own buffer. This needs a patch in `ci/mnn-patch/llm_session_generation.patch`: real work, but small.

## 4. GPU for prefill, CPU for decode

**What the sources say**
- The **Gemma 4 E2B LiteRT model card** has a table measured with 1024 prefill and 256 decode tokens, a 2048 context and 4 XNNPACK threads, with caches warm:

  | S26 Ultra | Prefill (tok/s) | Decode (tok/s) | TTFT (s) | CPU memory (MB) |
  |---|---|---|---|---|
  | CPU | 557 | 46.9 | 1.8 | 1733 |
  | GPU | 3,808 | 52.1 | 0.3 | 676 |

  For E4B the same table gives CPU 195 / 17.7 / 5.3 s and GPU 1,293 / 22.1 / 0.8 s.
  This confirms the numbers and fills in the conditions the earlier file lacked. **But the GPU also wins decode** here, so "CPU for decode" is not supported by Google's own table. It shows roughly a tie, with the GPU slightly ahead and using far less CPU memory.
- **No split in the bundled runtimes:**
  - LiteRT-LM 0.17.1 `EngineConfig` has one main `backend` plus vision and audio backends (`Config.kt:180-187`), and no separate backend for prefill.
  - MNN 3.6.1 has one `backend_type` per LLM, plus `mllm.backend_type` for the vision/audio encoder (`llmconfig.hpp:176-179`).
  - So "GPU prefill, CPU decode" can only mean choosing a backend per load, not per phase. No split option was found in either tree (grep for prefill/decode backend keys).
- **Gemma 4 `-gpu` files:**
  - The corruption on Adreno 730 is **TAI's own pong measurement** (`voice-cleanup-benchmark-2026-09-27.md`, round 4), not an upstream report.
  - LiteRT-LM #2992 (open) is FP16 activations giving garbage on NVIDIA Blackwell. #2814 (open) is Metal digit corruption on iOS. Both are related, but neither is about Adreno.
  - `activationDataType` is **not** in the 0.17.1 Kotlin `EngineConfig` (`Config.kt:180-187`), which confirms the backlog note (`project-docs/backlog.md:108-115`).
  - Both `-gpu` files were re-uploaded on 2026-08-07 (E2B PR #41, E4B PR #19). TAI's corrupt-output test on 2026-09-27 is later than that.
  - The E2B card does not document the `-gpu` file.

**What TAI does**
- LiteRT-LM: the schema default is `GPU` (`TaiSettings.java:563`). Auto tries GPU first and falls back to CPU at the floor context (`LiteRtTaiRuntime.java:644-689`).
- MNN: Auto maps to `cpu` (`MnnTaiRuntime.java:1427-1430`). That fits TAI's pong measurement (memory note `mnn-update-tai-reload-2026-09-28`, and the draft set in `project-docs/active/benchmark/recommended-set.md`): Qwen3-VL-2B MNN decoded at 21 tok/s on CPU and 15 on OpenCL.
- The catalogue ships only the standard Gemma 4 files (`TaiModelCatalog.java:176-186`).

**Gap and fix**
- A per-phase split is not available in either runtime, so there is no config change.
- The practical form is already in place: LiteRT-LM on GPU, MNN on CPU.
- A per-task switch (load MNN on OpenCL only for long-input jobs) would mean a reload per task at extra memory cost. That is real work and not recommended until the bench's Long input numbers show the OpenCL prefill gain on pong.
- Retesting the `-gpu` files waits for a LiteRT-LM release that carries `activationDataType`.

## 5. Speculative decoding without a second model

**LiteRT-LM MTP (Gemma 4)**
- **Sources:**
  - README at v0.17.1: "v0.11: Support Single Position Multi-token Prediction (MTP) for Gemma 4". The CLI example uses `--enable-speculative-decoding=true`.
  - The Kotlin `ExperimentalFlags.enableSpeculativeDecoding: Boolean?` is read only when an `Engine` is created. Its doc says "If null, use the model's default".
  - In the native code null leaves `enable_speculative_decoding = false` (`llm_executor_settings.h:289`, JNI `litertlm.cc:668-680`). Nothing in `runtime/` turns it on from the file's `supports_speculative_decoding` metadata, which is exposed only through `Capabilities` (`c/capabilities.cc:57-64`).
  - The MTP drafter is created on the static compiled-model executor when the flag is on (`llm_litert_compiled_model_executor.cc:1876-1893`).
- **Model card numbers:** "Speculative decoding is available on CPU and GPU on Mobile and Desktop", and files downloaded before May 5, 2026 must be downloaded again. S26 Ultra decode tok/s, baseline → with MTP by task:

  | Model | Backend | Baseline | Summarize | Code | Rewrite | Free form |
  |---|---|---|---|---|---|---|
  | E2B | GPU | 51.5 | 91.7 | 84.4 | 87.4 | 66.5 |
  | E2B | CPU | 40.7 | 47.5 | **36.3** | 47.1 | **38.1** |
  | E4B | GPU | 21.9 | 46.0 | 49.4 | 47.5 | 36.7 |
  | E4B | CPU | 17.0 | 27.5 | 26.2 | 29.5 | 21.1 |

  - At best that is about **1.8× on GPU** and **1.7× for E4B on CPU**. For E2B on CPU, code and free-form prompts come out *slower*.
  - The "up to 3×" in the earlier file comes from Google's blog [S9]. The model card does not show it.
- **TAI:**
  - The setting `enable_speculative_decoding` defaults to false (`TaiSettings.java:565`).
  - `speculativeDecodingFlag` passes `TRUE` only when the setting is on *and* `Capabilities.hasSpeculativeDecodingSupport()` is true (`LiteRtTaiRuntime.java:1086-1093`). The flag is applied around `initialize()` (`:952-967`).
  - The pinned revisions are `6e5c4f1e` (E2B) and `28299f30` (E4B). Both are the 2026-05-04 uploads of the standard file (HF commit history), the day before the card's May 5 cut-off, and are consistent with MTP-capable files.
  - TAI's Gallery comparison records these exact hashes as `speculative_decoding` in Gallery's allowlist (`gallery-gpu-loading-comparison.md:147-148`). **Unverified** until `Capabilities` reports true on pong.
  - The bench can already test it: `--eagle` sets `speculative` for any model whose spec has the `speculative_decoding` capability, and that includes both Gemma 4 entries (`TaiManager.java:986`, `TaiBenchSuite.java:275`). The flag's name only suggests Eagle.
- **Gap:** only a pong measurement is missing. The bench's Chat prompt is a free-form explanation, the task type with the smallest gain on the card, so a rewrite-type prompt should also be timed (see the measurement plan).

**MNN n-gram lookahead**
- **Sources, MNN 3.6.1:**
  - `speculative_type` dispatches `lookahead`, `mtp`, `eagle` and `dflash` (`speculative_decoding/generate.cpp:18-38`). The 3.6.1 doc still says only `lookahead` is supported (`llm.md:546`), so the doc is behind the source.
  - Lookahead options: `draft_predict_length` (the doc says default 4, the source default is 3, `llmconfig.hpp:543-545`), `draft_match_strictness` (`low`), `draft_selection_rule` (`freqxlen`), `ngram_match_maxlen` (4), `lookup_file`, and `ngram_update` (false) (`llm.md:546-552`; `llmconfig.hpp:536-583`).
  - Any speculative type needs the model graph to have a `logits_index` input with dimensions (`llm.cpp:248-260`). Otherwise `mInSpec = false` and decoding falls back to plain decoding.
  - `speculative_type` is read at `load()` (`llm.cpp:381-383`), so it must be in the load config.
  - Drafts are verified with the configured sampler at each position (`generate.cpp:118-130` `draftVerify`), so the output is exact only with greedy sampling.
- **Model compatibility, read from HF:**
  - `logits_index` with `dims [1]` is present in `llm.mnn.json` for Qwen3-VL-2B-Instruct-MNN, gemma-4-E2B-it-MNN, Qwen3.5-2B-MNN and SmolVLM-500M-Instruct-MNN.
  - The name is present in the binary `llm.mnn` of Qwen3-0.6B-MNN and Qwen3-1.7B-MNN.
  - So all TAI's MNN packages can run lookahead. **Unverified** at runtime.
- **TAI:** there is no way to select lookahead.
  - `applySpeculativeDecodingOverride` either keeps the package's own `speculative_type` (when the setting is true) or writes `""` (`MnnTaiRuntime.java:1245-1248`).
  - No TAI package declares `lookahead`.
- **Fix, a small code change:** add a load-time option, for example a `speculative_mode` of `auto|off|lookahead`. For `lookahead`, `mergedConfigJson` writes:
  - `speculative_type: "lookahead"`, `draft_predict_length: 4`, `ngram_match_maxlen: 4`, `draft_match_strictness: "low"`
  - `speculative_type` is already folded into the mmap fingerprint (`:1382`).
  - Offer it for the voice-cleanup and "fix this text" paths, where the output repeats the input. These should use greedy or low-temperature sampling so the output stays exact.
- **EAGLE-3 slower on pong:** TAI's own measurement, not a primary source. On Qwen3-VL-2B-Instruct-Eagle3 it gave 21 → 14 tok/s on CPU and 15 → 5.3 on OpenCL, and the text changed (memory note `mnn-update-tai-reload-2026-09-28`; `docs/en/On_Device_AI_Backends.md`). The upstream package declares `speculative_type: "eagle"` with `sampler_type: "penalty"` (its `config.json`), which explains why the text differs.

## Resolved: MNN and LiteRT-LM options at the bundled versions

| Option | MNN 3.6.1 docs (`docs/transformers/llm.md`) | MNN 3.6.1 source | Default | Set by TAI? |
|---|---|---|---|---|
| `reuse_kv` | yes (`:446`) | `llmconfig.hpp:160`, `llm.cpp:824` | false | no |
| `prompt_cache` | **no** | `llmconfig.hpp:164`, `llm.cpp:1114-1216` | false | **yes, `true`** (`MnnTaiRuntime.java:1214`) |
| `speculative_type: "lookahead"` | yes (`:546-552`) | `generate.cpp:22`, `lookahead.cpp` | "" | no (only keeps or blanks the package's value) |
| `attention_mode` (8 = FA; 10 = FA + KV int8; 12/14 = TQ3/TQ4, CPU only) | yes (`:448-477`) | `llm.cpp:165-173` | 8 | no |
| `dynamic_option` 8+n | yes (`:490-494`) | `llm.cpp:188`; `llm_bench.cpp:1126` switches 8+n by prompt length ≤ 300 | 0 | no |
| `power` (high = pin to fast groups) | **no** | `llmconfig.hpp:190`, `llm.cpp:210`, `CPUBackend.cpp:183-202` | normal | no |
| `kvcache_mmap` | yes (`:481`) | `llm.cpp:178` | false | no |

LiteRT-LM v0.17.1 (Kotlin):

| Option | Where | Default | Set by TAI? |
|---|---|---|---|
| `Backend.CPU(threadCount)` | `Config.kt:144-147` | native default 4 | no |
| `ExperimentalFlags.enableSpeculativeDecoding` | `ExperimentalFlags.kt` | null (= off natively) | opt-in |
| `ExperimentalFlags.enableBenchmark` | `ExperimentalFlags.kt` | false | no. TAI's `usage` falls back to estimated counts |
| `EngineConfig.cacheDir` | `Config.kt:186` | null | null, except for `/data/local/tmp` models |
| `activationDataType` | not in 0.17.1 | – | – |
| Prefix cache | `runtime/core/prefix_cache.*`, not wired into Engine | – | n/a |

## Model availability (official files, read 2026-09-30)

Sources: `huggingface.co/api/models/<repo>/tree/main`, model cards, `config.json` and `export_args.json`. "Lookahead-ready" means the MNN graph has a `logits_index` input. "MTP" means the card or Gallery says the file supports speculative decoding.

| TAI model | Official repo(s) | Files and quant | 4-bit? | `-gpu` | MTP | Lookahead-ready |
|---|---|---|---|---|---|---|
| Gemma 4 E2B (catalogue; "Gemma 2B" in the 09-29 bench report is this E2B load) | litert-community/gemma-4-E2B-it-litert-lm | `gemma-4-E2B-it.litertlm` 2.59 GB, mixed 2/4/8-bit (card); `-gpu` 2.01 GB (GPU_ARTISAN, text only); `-web`; NPU builds for SM8750, QCS8275, Tensor G5/G6, Intel | yes (mixed) | yes, corrupt on pong (TAI) | yes, CPU+GPU (card); pinned `6e5c4f1e` = 2026-05-04 upload | n/a |
| Gemma 4 E2B (MNN) | taobao-mnn/gemma-4-E2B-it-MNN | `llm.mnn.weight` 1.44 GB; `export_args.json`: `quant_bit 4`, block 64, `lm_quant_bit 4`, `hqq true`, `embed_bit 4`; `ple_embeddings_int4.bin` 1.47 GB | yes (HQQ) | – | no `mtp.mnn` | yes |
| Gemma 4 E4B | litert-community/gemma-4-E4B-it-litert-lm; taobao-mnn/gemma-4-E4B-it-MNN | LiteRT 3.66 GB (2.24 GB decoder + 0.67 GB embeddings); `-gpu` 2.97 GB. MNN weight 2.94 GB, same export args as E2B | LiteRT: **unverified** (card gives no bit mix); MNN: yes | yes, corrupt on pong (TAI) | yes (card); pinned `28299f30` = 2026-05-04 | MNN: **unverified** (graph not read) |
| Gemma 2 2B | litert-community/Gemma2-2B-IT; taobao-mnn/gemma-2-2b-it-MNN | LiteRT: `.task`/`.tflite` q8 only, **no `.litertlm`**; MNN: 4-bit (card), 1.47 GB weight + 1.18 GB bf16 embeddings | MNN only | – | – | **unverified** |
| Gemma 3 1B (draft set) | litert-community/Gemma3-1B-IT (gated) | `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` 584 MB, `gemma3-1b-it-int4.litertlm` 584 MB, SoC-pinned q4 builds (SM8550–SM8850, MT69xx, not SM8475) | yes | – | – | n/a |
| Qwen3 0.6B (LiteRT) | litert-community/Qwen3-0.6B; litert-community/Qwen3-0.6B-int4 | `qwen3_0_6b_mixed_int4` 475 MiB, 2048 ctx (int4 linears, int8 embeddings); `dynamic_wi4b32_afp32` 329 MB, 4096 ctx; `Qwen3-0.6B.litertlm` int8 586 MB; `-int4` repo: `q4_block32_ekv1280` 347 MB (think / no-think) | yes | – | – | n/a |
| Qwen3 0.6B (MNN) | taobao-mnn/Qwen3-0.6B-MNN | 4-bit (card), weight 451 MB, no `export_args.json` | yes | – | – | yes |
| Qwen3-VL 2B (MNN) | taobao-mnn/Qwen3-VL-2B-Instruct-MNN; -Eagle3-MNN | 4-bit (card), weight 1.23 GB; the Eagle3 package adds `eagle*.mnn` and `speculative_type: "eagle"` | yes | – | Eagle3 (separate package) | yes |
| Qwen3.5 2B | taobao-mnn/Qwen3.5-2B-MNN; litert-community/Qwen3.5-2B | MNN: `quant_bit 4`, `hqq true`, `embed_bit 16`; LiteRT: int8 only (text 2.12 GB, VL 3.15 GB); MNN also has a `-Dflash` package | MNN only | – | MNN DFlash package exists (not used by TAI) | yes |
| SmolVLM 500M (MNN) | taobao-mnn/SmolVLM-500M-Instruct-MNN | **8-bit** (card), weight 403 MB + bf16 embeddings 95 MB | no | – | – | yes |
| Qwen2.5-VL 3B (MNN, catalogue code link) | taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN | 4-bit (card), weight 1.74 GB + bf16 embeddings 622 MB | yes | – | – | **unverified** |
| Qwen2.5 1.5B (draft set) | litert-community/Qwen2.5-1.5B-Instruct | `q8_ekv4096.litertlm` 1.60 GB, f32 6.18 GB, **no int4** | no | – | – | n/a |
| Granite 4.2 3B | litert-community/granite-4.2-3b | `int4` 2.19 GB (int4 block-32 + OCTAV linears, int8 embedding); `int8` 3.76 GB | yes (costs 11 GSM8K points per card) | – | – | n/a |
| Granite 4.0-H 1B | litert-community/granite-4.0-h-1b | `int8` 1.68 GB only (re-converted 2026-08-13: about 3.5× CPU decode vs the older file) | no | – | – | n/a |

Notes:
- No MNN package TAI uses sets `power`, `reuse_kv`, `attention_mode` or `dynamic_option` in its `config.json`.
- `mllm.precision` is `normal` in several packages.
- Among MNN MTP-style packages only DFlash and Eagle3 exist for these models, and TAI uses neither by default.

## Measurement plan

All runs go through bench v2 (`tai benchmark`, in Termux on the phone) or the local OpenAI endpoint. Rules for every run:
- Standard preset.
- Same charger state, and no voice or embeddings running.
- The bench's own guards handle thermal and battery. Keep an eye on `warmStart`.

**Metrics bench v2 already records** (`TaiBenchHarness.java`):
- Chat TTFT (`ttftMs`).
- Decode tok/s: (generated − 1) ÷ (last − first token).
- Long-input read time, and prefill tok/s = prompt tokens ÷ TTFT (`:404-406`).
- Peak `:tai_runtime` PSS.
- Sanity.

**Metrics it lacks:** 2nd-turn TTFT, and a rewrite-type prompt.

| Fix | Command / steps | Metric that should move | Expected direction |
|---|---|---|---|
| Baseline (all) | `tai benchmark gemma-4-e2b-it-litert-lm --compare` ; `tai benchmark qwen3-0.6b-mnn qwen3-vl-2b-instruct-mnn --cpu` (use the ids `tai models` prints) ; `tai benchmark --results` | all | reference |
| 5 · MTP | `tai benchmark gemma-4-e2b-it-litert-lm --gpu --eagle` vs `--gpu`; then `--cpu --eagle` vs `--cpu`; repeat for `gemma-4-e4b-it-litert-lm` | Chat decode tok/s; Sanity must stay green | up on GPU; CPU may be flat or down (card) |
| 5 · MTP, rewrite task | two-turn script below with `"speculative_decoding": true`, prompt "Rewrite in a formal tone: <200-word paragraph>", `max_tokens` 300 | `elapsedMs` ÷ completion tokens | larger gain than Chat (card: rewrite ≈ summarize) |
| 2 · MNN `power: "high"` (after the code change) | set the model's Threads to 4, `tai load <mnn-id> --cpu`, check `tai.effectiveConfig.power` in one chat reply, then `tai benchmark <mnn-id> --cpu`; compare with the pre-change run | Chat decode tok/s, Long-input prefill tok/s; spread between the two runs | up, and a smaller spread |
| 2 · thread sweep | per-model Threads = 3, 4, 5 (Model centre › model › parameters, `thread_count`), `tai benchmark <mnn-id> --cpu` each | decode tok/s | peak expected at 4 (**unverified**) |
| 2 · pinning check | during a Chat run: `pid=$(pidof com.termux:tai_runtime); for t in /proc/$pid/task/*; do awk '{print $39}' $t/stat; done \| sort \| uniq -c` (field 39 = last CPU). Process name and readability from the Termux shell are **unverified** | which CPUs the busy threads ran on | 4–7 only with `power: "high"` |
| 3 · KV reuse | two-turn script below, on one MNN model and on Gemma 4 E2B | turn-2 `elapsedMs` vs turn-1; MNN: turn-2 `tai.usage.prompt_tokens` (native, delta only) and `tai.metrics.prefill_time` (µs) | turn 2 ≪ turn 1; MNN prompt_tokens ≈ size of the new message |
| 3 · control | same script, but edit the turn-1 user message in the turn-2 request | turn-2 `elapsedMs` | back to full prefill (shows the check measures reuse) |
| 4 · backend choice | `--compare` rows from the baseline | Long-input read time (GPU should win) vs Chat decode (tie on LiteRT-LM; CPU ahead on MNN) | informs a per-task backend only if the gap is large |
| 1 · smaller file | import `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`, then `tai benchmark <id> --compare` next to the `mixed_int4` entry | decode tok/s, peak PSS, Sanity | smaller file: higher decode, lower PSS; Sanity must pass |
| 5 · lookahead (after the code change) | two-turn script with the rewrite prompt, `temperature` 0, lookahead on vs off | turn decode rate; logcat `draft num / adopt num` lines from `lookahead.cpp` | up on rewrite, flat on free-form |

**Two-turn script** (Termux on the phone; uses the same token and endpoint files as `tai`). With a small `max_tokens`, `elapsedMs` is close to TTFT.

```sh
TOKEN=$(cat ~/.launcherctl/token); BASE=$(sed -n 1p ~/.launcherctl/endpoint)
MODEL=gemma-4-e2b-it-litert-lm        # or the MNN id from `tai models`
SYS=$(yes "You are a careful assistant for a phone terminal. Keep answers short." | head -n 120 | tr '\n' ' ')
req() { curl -sS -X POST "$BASE/v1/chat/completions" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" --data "$1"; }
T1=$(req "{\"model\":\"$MODEL\",\"max_tokens\":16,\"temperature\":0,\"messages\":[{\"role\":\"system\",\"content\":\"$SYS\"},{\"role\":\"user\",\"content\":\"Name one shell.\"}]}")
A1=$(printf '%s' "$T1" | jq -r '.choices[0].message.content')
printf '%s' "$T1" | jq '{turn:1, ms:.tai.elapsedMs, prompt:.tai.usage.prompt_tokens, est:.tai.usageEstimated, prefill_us:.tai.metrics.prefill_time}'
T2=$(req "$(jq -n --arg m "$MODEL" --arg s "$SYS" --arg a "$A1" \
  '{model:$m,max_tokens:16,temperature:0,messages:[{role:"system",content:$s},{role:"user",content:"Name one shell."},{role:"assistant",content:$a},{role:"user",content:"Name another."}]}')")
printf '%s' "$T2" | jq '{turn:2, ms:.tai.elapsedMs, prompt:.tai.usage.prompt_tokens, est:.tai.usageEstimated, prefill_us:.tai.metrics.prefill_time}'
```

- LiteRT-LM token counts are estimated (`usageEstimated: true`), because TAI never sets `ExperimentalFlags.enableBenchmark`. Read `ms` for LiteRT-LM and `prompt` / `prefill_us` for MNN.
- `jq` must be installed in Termux (**unverified** on pong).
- Request-level `thread_count`, `accelerator` and `speculative_decoding` are read by `TaiManager.runtimeOptionsFromRequest` (`TaiManager.java:3034-3056`). They only take effect when the model is loaded with them, so run `tai load` with the same setting first. Whether a differing request option triggers a reload was not traced (**unverified**).

## Sources

**MNN at tag 3.6.1**
- LLM docs: https://github.com/alibaba/MNN/blob/3.6.1/docs/transformers/llm.md
- Config reader: https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/engine/src/llmconfig.hpp
- LLM engine (runtime hints, prompt cache, reuse_kv, speculative setup): https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/engine/src/llm.cpp
- Speculative dispatch and lookahead: https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/engine/src/speculative_decoding/generate.cpp , https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/engine/src/speculative_decoding/lookahead.cpp
- llm_bench: https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/engine/tools/llm_bench.cpp
- Export flags: https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/export/llmexport.py
- CPU power/affinity: https://github.com/alibaba/MNN/blob/3.6.1/source/backend/cpu/CPUBackend.cpp , https://github.com/alibaba/MNN/blob/3.6.1/source/backend/cpu/CPURuntime.cpp
- Android session glue: https://github.com/alibaba/MNN/blob/3.6.1/apps/Android/MnnLlmChat/app/src/main/cpp/llm_session.cpp , https://github.com/alibaba/MNN/blob/3.6.1/apps/Android/MnnLlmChat/app/src/main/cpp/llm_mnn_jni.cpp

**LiteRT-LM at tag v0.17.1**
- README: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/README.md
- Kotlin config and flags: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt , https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt
- JNI: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc
- Executor settings: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/executor/llm_executor_settings.cc , https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/executor/llm_executor_settings.h
- MTP drafter wiring: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/executor/llm_litert_compiled_model_executor.cc
- CPU affinity: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/engine/engine_factory.h , https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/engine/cpu_affinity_utils.h
- Prefix cache (not wired): https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/core/cached_session.h , https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/core/prefix_cache.h , https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/core/BUILD
- Capabilities: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/c/capabilities.cc
- Issues: https://github.com/google-ai-edge/LiteRT-LM/issues/2992 , https://github.com/google-ai-edge/LiteRT-LM/issues/2814
- Google Maven versions: https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml

**Hugging Face: litert-community**
- Org listing: https://huggingface.co/api/models?author=litert-community
- Gemma 4 E2B card, file tree and commits: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm , https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm/tree/main , https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm/commits/main
- Gemma 4 E4B card and commits: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm , https://huggingface.co/api/models/litert-community/gemma-4-E4B-it-litert-lm/commits/main
- Qwen3 0.6B: https://huggingface.co/litert-community/Qwen3-0.6B , https://huggingface.co/litert-community/Qwen3-0.6B-int4
- Granite: https://huggingface.co/litert-community/granite-4.2-3b , https://huggingface.co/litert-community/granite-4.0-h-1b
- Other file trees: https://huggingface.co/api/models/litert-community/Gemma3-1B-IT/tree/main , https://huggingface.co/api/models/litert-community/Gemma2-2B-IT/tree/main , https://huggingface.co/api/models/litert-community/Qwen2.5-1.5B-Instruct/tree/main , https://huggingface.co/api/models/litert-community/Qwen3.5-2B/tree/main , https://huggingface.co/api/models/litert-community/Qwen3.5-0.8B/tree/main

**Hugging Face: taobao-mnn**
- Org listing: https://huggingface.co/api/models?author=taobao-mnn
- Gemma 4 E2B/E4B (`config.json`, `export_args.json`): https://huggingface.co/taobao-mnn/gemma-4-E2B-it-MNN , https://huggingface.co/taobao-mnn/gemma-4-E4B-it-MNN
- Qwen3.5 2B: https://huggingface.co/taobao-mnn/Qwen3.5-2B-MNN
- Qwen3 0.6B / 1.7B: https://huggingface.co/taobao-mnn/Qwen3-0.6B-MNN , https://huggingface.co/taobao-mnn/Qwen3-1.7B-MNN
- Qwen3-VL 2B (plain and Eagle3): https://huggingface.co/taobao-mnn/Qwen3-VL-2B-Instruct-MNN , https://huggingface.co/taobao-mnn/Qwen3-VL-2B-Instruct-Eagle3-MNN
- Others: https://huggingface.co/taobao-mnn/SmolVLM-500M-Instruct-MNN , https://huggingface.co/taobao-mnn/gemma-2-2b-it-MNN , https://huggingface.co/taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN

**TAI's own evidence (not primary sources; cited where used)**
- `project-docs/reference/voice-ai/voice-cleanup-benchmark-2026-09-27.md`: the `-gpu` corruption on pong.
- `project-docs/reference/voice-ai/gallery-gpu-loading-comparison.md`: Gallery's allowlist flags.
- `project-docs/backlog.md:108-115`.
- `docs/en/On_Device_AI_Backends.md`: EAGLE-3 off by default.
- `project-docs/active/benchmark/recommended-set.md`: pong figures for Qwen3-VL-2B on CPU and GPU.
- Earlier research: `local-llm-inference-speed-2026-09-30.md`.
