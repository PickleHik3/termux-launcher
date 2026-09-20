# TAI model ecosystem and import experience

Assessment date: **2026-09-11**. Local checkout: **89c8802ff2f71511284435049f0ce3dcf38c48dc**. Sources below were accessed on that date. This is a durable research assessment, not an implementation plan or a device certification.

## Finding

TAI already covers the principal model tasks. The highest-value improvement is making an imported **artifact** behave correctly on its first request: select the right variant, preserve its revision, carry its template and context settings, check runtime compatibility, and expose only capabilities supported by that combination. Adding many more model names before fixing those steps would amplify existing uncertainty. This is an inference from the comparison below, not a measured usability study.

The research samples Google's LiteRT-LM documentation, the `litert-community` publisher, Alibaba's MNN documentation and `taobao-mnn` repositories. It is **not an exhaustive Hugging Face inventory**. Publisher catalog membership, runnable files, upstream model ability, launcher integration and successful execution on a particular phone are separate facts. The LiteRT documentation itself calls its supported-model table a subset; MNN's catalog contains repositories without weights. [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview), [MNN example placeholder](https://huggingface.co/taobao-mnn/LFM2.5-Audio-1.5B-MNN/tree/main).

No models were downloaded or run, and no implementation, user configuration or installed application was changed during this assessment. Compatibility conclusions are source-based; candidates below still require testing.

## Representative catalog comparison

Sizes are publisher-reported download sizes, **not required RAM**. GB/MB and GiB units are retained as published. Each row cites the publisher of the package rather than assuming an upstream checkpoint survives conversion unchanged.

| Package / family | What the published artifact provides | Consequence for TAI |
| --- | --- | --- |
| **LiteRT Gemma 4 E2B / E4B** | Google's deployment table lists 2,583 / 3,654 MB packages. E2B has a separate text-only Web artifact; its normal bundle loads vision/audio components on demand. Its card lists Apache-2.0. [Overview](https://developers.google.com/edge/litert-lm/overview), [E2B card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm). | Already represented in the local curated catalog. A `.litertlm` suffix alone cannot distinguish Android multimodal from Web text-only variants. |
| **LiteRT Gemma3-1B / Qwen2.5-0.5B / Qwen2.5-1.5B** | Google's table lists chat packages at 1,005 / 521 / 1,598 MB. Its measured devices and CPU/GPU results differ by model. [Overview](https://developers.google.com/edge/litert-lm/overview). | Small-chat candidates; prefer a tested package and backend instead of ranking solely by parameter count. Qwen2.5-1.5B is already curated. |
| **LiteRT MedGemma-1.5-4B-IT** | Separate text-only **2.41 GiB** and vision **2.82 GiB** int4 bundles; exported cache is **2,048**, despite the source model's much larger context. The bundled Jinja template is part of the artifact. Access requires HAI-DEF acceptance. The publisher recommends self-contained, single-turn questions. [Package card](https://huggingface.co/litert-community/MedGemma-1.5-4B-IT). | Explicit variant selection, an artifact-specific context limit, correct output-channel handling and a specialist description are essential. Do not present it as the default everyday chat model. |
| **LiteRT Qwen3.5-2B** | Text **1.97 GB** and vision **3.15 GB** builds. The card requires LiteRT-LM **≥0.15**. Its simplified template disables thinking and omits tool-calling sections; that is different from the source family's capabilities. Apache-2.0. [Package card](https://huggingface.co/litert-community/Qwen3.5-2B). | Current launcher dependency is 0.14.0: mark this artifact incompatible pending a validated runtime update. Do not enable tools/reasoning from its name. |
| **LiteRT FunctionGemma 270M Mobile Actions** | A small action-specific finetune for Gallery, benchmarked at **289 MB**, context **1,024**; Gemma acceptance is required. [Package card](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions). | Already curated. Treat it as a task/tool profile whose schemas and response protocol need validation, not a substitute for general conversation. |
| **LiteRT EmbeddingGemma 300M** | `.tflite` embeddings, SentencePiece tokenizer, multiple sequence lengths and backend variants; Gemma-gated. [Package card](https://huggingface.co/litert-community/embeddinggemma-300m). | Already curated with a dedicated embedding runtime. The task comes from model metadata and signatures, not the `.tflite` extension. |
| **MNN Qwen2.5-Coder-1.5B-Instruct** | Four-bit export, loaded through `config.json` with MNN's LLM build. Card lists Apache-2.0. [Package card](https://huggingface.co/taobao-mnn/Qwen2.5-Coder-1.5B-Instruct-MNN). | Already curated, alongside larger coder and small general Qwen alternatives. Improve import correctness before duplicating this coverage. |
| **MNN Qwen3.5-2B** | `config.json` names the graph/weights, sampling defaults, multimodal backend settings and `jinja.context.enable_thinking: true`. [Published config](https://huggingface.co/taobao-mnn/Qwen3.5-2B-MNN/blob/main/config.json). | This MNN configuration differs materially from the LiteRT conversion of the same family. Preserve package settings and record intentional overrides. |
| **MNN Qwen3-VL-2B-Instruct** | Repository is **1.48 GB**, containing decoder graph/weights, `llm_config.json`, `tokenizer.txt`, and `visual.mnn` plus weights; HF still labels it Text Generation. Card/file page lists Apache-2.0. [Package tree](https://huggingface.co/taobao-mnn/Qwen3-VL-2B-Instruct-MNN/tree/main). | A strong candidate for small vision coverage after native-runtime tests. HF task labels alone underdescribe the package; sidecar completeness matters. |
| **MNN SmolVLM-500M-Instruct** | Publisher provides an eight-bit MNN export and lists Apache-2.0. [Package card](https://huggingface.co/taobao-mnn/SmolVLM-500M-Instruct-MNN). | Candidate for smaller vision workloads; validate the actual vision graph, preprocessing and resulting answer quality before recommending it. |
| **MNN LFM2.5-Audio-1.5B** | Catalog entry exists, but the inspected tree contains only `.gitattributes` (**1.52 kB**), no runnable package. [Tree](https://huggingface.co/taobao-mnn/LFM2.5-Audio-1.5B-MNN/tree/main). | Exclude from installable results until artifacts exist; offer a clear unavailable state rather than a failing download. |

The broader MNN publisher also lists Gemma 4, LFM, speculative-decoding and large-model collections. Those are discovery leads, not evidence that every entry is suitable for a phone. [Publisher collections](https://huggingface.co/taobao-mnn/collections). MNN's upstream application supports more than chat, including audio/image tasks, but that application and the launcher's JNI/runtime integration are different products. [MNN upstream](https://github.com/alibaba/MNN).

## What the launcher already does well

The built-in catalog includes Gemma 4, lightweight Qwen, DeepSeek reasoning, FunctionGemma, LiteRT/MNN embeddings and MNN coding models. The signed remote-catalog mechanism exists, but the inspected remote payload currently contains **zero entries**, so it does not add new inventory. Its parser also does not currently ingest full runtime/thinking profiles or minimum runtime requirements. [Built-in catalog](../app/src/main/java/com/termux/ai/TaiModelCatalog.java#L125), [remote payload parser](../app/src/main/java/com/termux/ai/TaiModelCatalog.java#L77), [remote catalog implementation](../app/src/main/java/com/termux/ai/TaiRemoteCatalog.java), [published feed](https://raw.githubusercontent.com/PickleHik3/termux-launcher/experimental/app/src/main/assets/tai-model-catalog.json).

The importer accepts HF URLs, can resolve a repository entry, checks authentication against both metadata and the selected file, and supports main-file HTTP range resume and optional SHA-256 verification. These are useful foundations; the proposal is to extend them, not replace the download subsystem. [Resolver](../app/src/main/java/com/termux/ai/TaiModelDownloader.java#L598), [transfer and verification](../app/src/main/java/com/termux/ai/TaiModelDownloader.java#L193).

Capability handling is richer than text-only: the importer exposes chat, embeddings, image/audio input, tools, code, reasoning and multilingual labels; MNN endpoint capability logic permits image/audio/tools/speculation. LiteRT model variants can be exposed separately or combined. Endpoint URL/token copy controls already exist. [Import UI](../app/src/main/java/com/termux/app/fragments/settings/termux/TaiPreferencesFragment.java#L1493), [endpoint capability calculation](../app/src/main/java/com/termux/ai/TaiModelSpec.java#L320), [variants](../app/src/main/java/com/termux/ai/TaiModelVariants.java#L40), [endpoint controls](../app/src/main/java/com/termux/app/fragments/settings/termux/TaiPreferencesFragment.java#L394).

These are implemented paths, not proof that every model executes each modality correctly. In particular, `capabilitiesVerified` currently means **built-in catalog provenance**, not a successful device probe. [Capability serialization](../app/src/main/java/com/termux/ai/TaiModelSpec.java#L235).

## Gaps that affect the first successful request

### 1. Repository selection loses artifact identity

The HF resolver takes the first `.litertlm`, then `.task`, then a preferred `.tflite`, then a root MNN configuration. Tree/blob URLs lose their supplied revision and are reconstructed using `main`. MNN dependency discovery also queries the default branch. That can select a text build when the user wants images, a Web variant, or files from a different revision. [Resolution and selection](../app/src/main/java/com/termux/ai/TaiModelDownloader.java#L602), [revision parsing](../app/src/main/java/com/termux/ai/TaiModelDownloader.java#L648), [MNN inventory](../app/src/main/java/com/termux/ai/TaiModelDownloader.java#L563).

**Proposal:** resolve to a reviewable artifact manifest before download: repository, immutable revision, selected file or package, total size, task, supported backends, export context, license/access state and known compatibility. Show useful choices such as “Text” / “Text and images”; keep filenames and quantization details available in expanded details. HF exposes revision-aware model information, file sizes and optional LFS metadata; most fields are optional, so unknown must remain a valid state. [HF API reference](https://huggingface.co/docs/huggingface_hub/package_reference/hf_api#huggingface_hub.HfApi.model_info).

### 2. Reasoning labels do not configure reasoning behavior

The UI's Reasoning checkbox adds a label. Automatic thought delimiters are configured only when the identity contains both `qwen3` and `thinking`; other imports receive generic defaults. LiteRT runtime already accepts named channels and thinking template context. [Import profile](../app/src/main/java/com/termux/app/fragments/settings/termux/TaiPreferencesFragment.java#L1751), [channel/template support](../app/src/main/java/com/termux/ai/LiteRtTaiRuntime.java#L948).

The user's MedGemma transcript shows `<unused94>thought` and `<unused95>` reaching the answer. That is evidence of an unresolved formatting/channel boundary, **not proof that those delimiters are universally correct for MedGemma**, or that the model should always think. The previous diagnosis was too definite without inspecting that artifact's template and raw response. The current package card says it bundles a Jinja template; inspect it and reproduce the issue before choosing a profile correction. [MedGemma artifact documentation](https://huggingface.co/litert-community/MedGemma-1.5-4B-IT).

Session evidence also reports a read-only profile audit of the installed text artifact: declared context **4,096**, thinking mode `none`, filename ending in `q4_block32_ekv2048.litertlm`. This conflicts with the publisher's exported cache limit of **2,048**. This is a reported configuration observation, not a model-load reproduction, and is not stored in the repository sources. It strengthens the case for validating artifact-specific limits during import rather than relying on the model-family name.

**Proposal:** separate three properties: model reasoning ability, supported thinking control, and response-channel parsing. Prefer validated artifact profiles; provide an advanced editable profile with provenance and reset-to-default for unknown imports. Do not solve this with a global string replacement: chunk boundaries, quoted marker text and incomplete output need correct handling.

### 3. File compatibility is broader than runtime compatibility

The app pins `litertlm-android:0.14.0`, while the sampled Qwen3.5 artifact requires ≥0.15. MNN's native build workflow targets 3.6.0. Current MNN export documentation warns that newer C4/fused graphs need compatible engines, and some unsupported gate-fold behavior can produce incorrect results rather than a load error. This establishes a compatibility risk to check, not evidence that the launcher's current MNN packages are broken. [Android dependencies](../app/build.gradle#L168), [native build](../.github/workflows/build_mnn_native.yml#L22), [Qwen artifact requirement](https://huggingface.co/litert-community/Qwen3.5-2B), [MNN export compatibility](https://mnn-docs.readthedocs.io/en/latest/transformers/llm.html).

The fallback capability guess also treats `.tflite` as embeddings. LiteRT is a general runtime format; EmbeddingGemma is one specific task packaged in it. [Fallback inference](../app/src/main/java/com/termux/ai/TaiManager.java#L2072), [LiteRT upstream](https://github.com/google-ai-edge/LiteRT).

**Proposal:** express compatibility by artifact revision + engine version/build features + device/backend. Separate “known supported”, “untested” and “incompatible”. Never equate a successful file import or engine load with correct output. Resolve supported context from export/runtime constraints and user/device budget; additional RAM cannot enlarge a statically exported cache.

### 4. MNN needs package import, not a root-file heuristic

Local MNN folder import is explicitly unavailable. HF MNN discovery uses a repository-wide extension list and a small fixed required-file set instead of dependencies referenced by configuration. Main-file resume exists, but MNN sidecars start again from zero. [Local import limitation](../app/src/main/java/com/termux/ai/TaiModelImporter.java#L243), [package selection](../app/src/main/java/com/termux/ai/TaiModelDownloader.java#L706), [sidecar transfer](../app/src/main/java/com/termux/ai/TaiModelDownloader.java#L250).

MNN packages can contain graph/weight files, tokenizers, optional separate embeddings and modality components; current official exports use `tokenizer.mtok`, while the inspected Qwen3-VL export uses `tokenizer.txt`. Therefore one hard-coded filename set cannot describe all valid packages. [MNN packaging documentation](https://mnn-docs.readthedocs.io/en/latest/transformers/llm.html), [Qwen3-VL files](https://huggingface.co/taobao-mnn/Qwen3-VL-2B-Instruct-MNN/tree/main).

**Proposal:** derive the dependency set from a versioned package adapter/configuration, preserve relative paths, verify completeness before activation, resume each file, and add Android folder selection for offline imports. Download only the chosen variant's dependencies. Keep incomplete transfers repairable and separate from ready models.

### 5. Capability and access presentation needs evidence

Import checkboxes and catalog flags describe declarations, not tests. Meanwhile HF model cards can use generic task tags even for vision packages. License gating is a separate state from authentication: HF requires users to request/accept access in the browser, and programmatic downloads then use an authorized user token. [Capability fields](../app/src/main/java/com/termux/ai/TaiModelSpec.java#L235), [HF gating documentation](https://huggingface.co/docs/hub/models-gated).

**Proposal:** distinguish publisher-declared, artifact-inspected and tested-on-this-device capabilities; show the test's scope and timestamp. Add short first-use checks for greeting, second turn, stop behavior, optional image/audio and tool round trip. Offer “Open model terms” / “Check access again” around the existing token flow. After readiness, offer a model-specific aichat snippet using the actual exposed ID and existing endpoint controls. Do not imply that copying configuration guarantees client support for every reasoning or tool channel.

## Priorities and acceptance criteria

These are proposed outcomes; none are implemented by this report.

| Priority | Deliverable | Concrete acceptance criteria |
| --- | --- | --- |
| **P0** | Artifact preview and reproducible selection | A MedGemma repository URL displays both artifacts and their different modalities/sizes; no first-file guess commits silently. A supplied revision survives metadata lookup and every package-file URL. Empty LFM audio repository shows no installable variant. |
| **P0** | First-answer profile correctness | Reproduce the reported MedGemma output against the exact artifact; validate template/channel settings and context before persisting a fix. Both streaming and nonstreaming answer content are free of structural markers; separately represented reasoning does not disappear or contaminate the answer. Test markers split across chunks and ordinary text containing similar strings. |
| **P0** | Compatibility and context limits | The sampled ≥0.15 Qwen3.5 package is identified as incompatible with the pinned 0.14 runtime before a multi-GB transfer. Export cache limits remain effective even when a user requests a larger context. Unknown metadata is displayed as unknown, not asserted compatible. |
| **P1** | Complete, resumable MNN packages | Import Qwen3-VL with all referenced decoder/tokenizer/vision dependencies; interrupt a large sidecar and resume it; reject a missing dependency before activation. Import the same valid package through folder selection without a network request. |
| **P1** | Evidence-backed readiness and client handoff | Distinguish declared vs tested capabilities. A failed image/tool test leaves working text chat usable with accurate labels. Copyable aichat configuration uses the selected model's actual ID, endpoint and context; test first and second turns for general chat, and self-contained single-turn queries for MedGemma. |
| **P2** | Catalog freshness and measured recommendations | Extend signed catalog profiles to carry artifact/runtime/template metadata. Use pinned revisions and recorded device results; refresh without silently replacing installed models. Recommend by intended task and tested hardware budget, with optional advanced alternatives. |

Additional proposed regression cases: nested artifact paths and `/tree/<revision>` links; multiple quantizations; public metadata with gated file access; missing audio/vision dependencies; always-thinking versus no-thinking templates; and an image request offered only for a selected vision artifact. These checks were **not executed** for this research document.

## Catalog decisions

**Use the existing curated set first:** Gemma 4 general/multimodal, small Qwen general chat, Qwen2.5-Coder, FunctionGemma and embedding models already span the core use cases. Add a short suitability explanation and reproducible test status to each. [Current catalog](../app/src/main/java/com/termux/ai/TaiModelCatalog.java#L125).

**Candidate additions after focused tests:** Gemma3-1B for another lightweight chat option; MedGemma as an explicitly specialist text/vision choice after the reported formatting issue is understood; SmolVLM-500M and Qwen3-VL-2B for smaller vision workloads. These are proposals based on the sampled packages, not claims of demonstrated launcher compatibility.

**Require a runtime compatibility project first:** the sampled LiteRT Qwen3.5-2B conversion. Validate a dependency upgrade with existing models, streaming, cancellation, multi-turn history and all exposed modalities; merely changing the dependency version is insufficient.

**Defer:** empty catalog placeholders; speculative draft-only packages without paired-target support; very large variants without a demonstrated device budget; audio generation, diffusion or other tasks whose complete launcher request/response path has not been verified. Keep these discoverable as unsupported/experimental only if the UI can explain the actual limitation. The goal is a small set of reliably useful choices, followed by an importer that safely admits the long tail.

## Remaining verification

This assessment establishes source-level gaps, not phone performance. Before shipping changes, record exact artifact hashes, runtime builds, ABI, device, backend, context and test prompts. Measure cold-load time, time to first answer, throughput and peak memory independently; publisher benchmarks on another device are not promises for this launcher. Verify both aichat streaming and ordinary API responses, then image/audio/tool paths only where advertised. Preserve the launcher memory budget: successful model inference that destabilizes the home screen is not a successful integration. [Repository memory discipline](../AGENTS.md#2-memory-discipline).

## What this branch implemented

Added **2026-09-11**, after the assessment above. The priorities table still describes proposals;
this section records which of them now have code, and what each one still owes a device check.

| Gap | Implemented | Still unverified |
| --- | --- | --- |
| **1. Repository selection** | `TaiHuggingFace` parses repo/revision/path from repo, `/tree`, `/blob` and `/resolve` URLs, resolves the revision to an immutable commit and builds every file URL from that commit. `candidates()` returns every LiteRT artifact and every MNN `config.json` that has a `.mnn` beside it, with size, licence and SHA-256. The import dialog lists them; nothing downloads until one is picked. An empty repository yields no candidate. | Gated repositories where metadata is public but files 401, and repositories large enough to exceed the 2 MB metadata read. |
| **2. Reasoning and context settings** | `TaiModelProfile` carries `maxContextTokens`; `TaiImportProfileDialog` edits thinking mode, both thought markers and the artifact context limit, with a reset to defaults, reachable from the import dialog and from an installed LiteRT model's parameter screen. `TaiContextWindowPolicy.artifactContextLimit` caps the two published MedGemma exports at their exported 2,048 cache, and the policy now clamps user overrides to the artifact limit instead of trusting them. | **The reported MedGemma `<unused94>` output is not reproduced or fixed.** The dialog lets a user correct it by hand; the artifact's own Jinja template has not been inspected, so no validated default profile ships. |
| **3. Runtime compatibility** | `litertLmVersion` is one Gradle value feeding both the dependency and `BuildConfig.LITERT_LM_VERSION`. `TaiArtifactCompatibility.versionAtLeast` compares numerically and treats unparseable versions as unknown, not compatible. A candidate carrying `minimumRuntimeVersion` is refused before the transfer starts. | Only `litert-community/Qwen3.5-2B` is known to need ≥0.15; the requirement is a hard-coded publisher fact, not read from artifact metadata. The `.tflite`-means-embeddings fallback in `TaiManager` is unchanged. |
| **4. MNN packages** | `TaiMnnPackage` derives dependencies from the selected `config.json` — declared graph/weights/tokenizer plus any package file referenced anywhere in the configuration, followed transitively through nested JSON — rejecting paths that escape the package. Sidecars resume per file against a `Content-Range` checked to start at the requested offset, with a `.source` marker so a changed URL restarts rather than appends. `validate()` refuses activation when a dependency is missing. `TaiModelImporter.importMnnDirectory` imports a folder offline through the SAF tree picker, staged and renamed into place. | No package has been imported end to end on a device, by either path. Discovery is still scoped to the config's own directory. |
| **5. Capability evidence** | `capabilitiesVerified` no longer reports built-in catalog provenance as verification: it is always `false`, and `/v1/models` exposes `_capability_verification: "declared"` beside the existing `_capability_source`. `TaiAichatConfig` builds the client snippet from the same discovery response a client receives, so it cannot advertise a model or a context the endpoint does not. | No first-use probe exists, so "tested on this device" is still an unoccupied state. The gating flow is unchanged — no "Open model terms" / "Check access again" controls. |

The catalog screen also remembers its backend, install and sort selections, and orders by
availability (active model, installed, downloading, available, unavailable) unless an explicit
name or size sort is chosen. The recommended star was dropped from catalog rows: the ordering now
carries that signal, and a star that ranked nothing was noise.

**Not attempted:** P2 in full — the signed catalog still carries no artifact/runtime/template
metadata, and no catalog entries were added or removed. The candidate additions and the
runtime-upgrade project remain open.
