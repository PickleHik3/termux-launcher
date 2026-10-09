# Recommended set: publish checklist

Build order item 5 (`project-docs/active/benchmark/SPEC.md` § "Which models are offered"). This is the
draft prepared before signing and publishing — nothing here has been verified on pong, and nothing
has been signed. The draft payload is `project-docs/active/benchmark/recommended-catalog-draft.json`, in
the shape `TaiModelCatalog.applyRemotePayload` accepts (`{version, expiresAt, entries[...]}`).

## Can the catalogue express MNN models? (yes, already)

MNN packages are multi-file (`config.json`, `llm.mnn`, `llm.mnn.weight`, `llm.mnn.json`,
`llm_config.json`, `tokenizer.txt`, plus `visual.*`/`embeddings_*` for VL and embedding models), but
a `CatalogEntry` only carries one `artifactPath`. This already works without any code change:

- `TaiModelDownloader.isMnnPackage` treats a URL as an MNN package by backend/format hint or by the
  URL containing `-mnn`/`taobao-mnn/` (`TaiModelDownloader.java:872-875`).
- When the main download is an MNN package, the downloader fetches `config.json` first, then calls
  `mnnPackageListingFromHuggingFace` to list the repo tree at the pinned revision, filters it through
  `isMnnPackageFile` (`.mnn`, `.mnn.weight`, `.mnn.json`, `.json`, `.mtok`, `.bin`, `tokenizer.*`),
  and downloads every matching file into the model's directory (`TaiModelDownloader.java:456-548`).
  `TaiMnnPackage.files`/`references` then walks the downloaded `config.json` (and any nested
  `.json` it references) to add anything else it names, so vision/embedding sidecars are picked up
  too.
- So a **remote** MNN catalogue entry just needs `artifactPath: "config.json"` (or
  `"<subdir>/config.json"`), `backend: "mnn-llm"`, `format: "mnn"`, `repositoryId` pointing at the
  HF repo, and `sha256` set to the hash of `config.json` itself (that's the only file the downloader
  hash-verifies before it expands the package) — `sizeBytes` should be the *whole package's* total
  size, since that's what feeds the download progress bar (`expectedSizeBytes` in
  `TaiModelDownloader.download`), not just `config.json`'s few hundred bytes.
- No `files[]` list, sidecar array, or parser change was needed. `applyRemotePayload` does **not**
  currently parse a `sidecars` key for remote entries (only the built-in Whisper/KittenTTS entries
  use `CatalogEntry.Sidecar` today, and they use the `hfSidecar`/`whisperTokenizerSidecar` Java
  constructors, not JSON) — that's fine, because config-driven MNN expansion doesn't need it.

**No java changes were made** (`TaiModelCatalog.java`, `TaiModelDownloader.java` untouched);
`TaiModelDownloaderStateTest`/`TaiModelDownloaderKeepTest` also untouched — there was nothing new
to unit test.

> **Fixed on `feat/mnn-tai` (TaiMnnPackage):** `embedding_file` is now required only when the file exists in the listing or folder. With `tie_embeddings` in `llm_config.json` (as here) MNN 3.6.1 reads the embeddings from `llm.mnn.weight` (`diskembedding.cpp`) and never opens it. The note below is kept for history; the catalogue download still needs its device check.

**One real risk found, not fixed (out of the file scope for this pass):** `TaiMnnPackage.references`
treats *any* string in `config.json` (recursively) that looks like a package filename as a
**required** dependency, even if it isn't declared as required anywhere and doesn't actually exist
in the repo. `taobao-mnn/Qwen3-VL-2B-Instruct-MNN`'s `config.json` sets
`"embedding_file": "embeddings_int4.bin"`, but no such file exists in that repo (confirmed: HF
`resolve/main/embeddings_int4.bin` → 404). If the downloader's dependency walk treats that key the
same way (it does — `references()` doesn't distinguish "referenced" from "required"), a remote
download of this entry will fail with `MNN package file missing: embeddings_int4.bin` even though
the model loads fine without it. This needs a device check to confirm before Qwen3-VL-2B-Instruct
ships through the *catalogue download path* (as opposed to however it was imported for the "measured
on pong" SPEC row today, which may have been a local folder import instead — see below). If it does
reproduce, the smallest fix is almost certainly in `TaiMnnPackage.references`/`files`: only require a
referenced file if it's also present in the repo's file listing (`available`), the same way the
"conventional sidecars" loop already does a few lines below it.

## Models

| Model | Repo | Revision | Backend | Package size | RAM tier | Status |
|---|---|---|---|---|---|---|
| Qwen3-VL 2B Instruct | `taobao-mnn/Qwen3-VL-2B-Instruct-MNN` | `9e49ec71ded22500a997ed0f9961e1e92b85bbc9` | MNN | 1.48 GB (real; config.json 605 B, llm.mnn 452 KB, llm.mnn.weight 1.15 GB, visual.mnn 491 KB, visual.mnn.weight 227 MB, tokenizer.txt 3.0 MB, llm.mnn.json 950 KB, llm_config.json 6.3 KB) | 6 GB+ | **measured: 21 tok/s CPU, 15 GPU on pong** (SPEC); catalogue-download path itself unverified — see the `embeddings_int4.bin` risk above |
| Qwen2.5 1.5B Instruct | `litert-community/Qwen2.5-1.5B-Instruct` | `19edb84c69a0212f29a6ef17ba0d6f278b6a1614` | LiteRT | 1.60 GB (`..._multi-prefill-seq_q8_ekv4096.litertlm`) | 6 GB+ | to verify |
| Gemma 3 1B IT | `litert-community/Gemma3-1B-IT` | `a6306a4e292016480083b73b8dc6f3f939ae04c3` | LiteRT | 584 MB (`..._multi-prefill-seq_q4_ekv4096.litertlm`, the one generic/non-chip-pinned `.litertlm` build in the repo) | 6 GB+ | to verify; **gated repo — sha256 could not be fetched anonymously (HF returns 401 on `resolve` without a token); someone with an accepted-terms HF token must fetch the file and record its sha256 before this entry is signed** |
| Qwen3 0.6B | `litert-community/Qwen3-0.6B` | `a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76` | LiteRT | 498 MB (`qwen3_0_6b_mixed_int4.litertlm`, sha256 `7900eb4e…8c22c1`) | 4 GB+ | to verify; replaces the Qwen2.5 0.5B slot (that repo has no `.litertlm`) |
| Qwen3 0.6B | `taobao-mnn/Qwen3-0.6B-MNN` | `34dfccda1187ded6e07ea06426da576b0b793c6b` | MNN | 454 MB (config.json, llm.mnn, llm.mnn.weight 451 MB, llm_config.json, tokenizer.txt; tie_embeddings) | 4 GB+ | to verify; MNN 3.6.1's own showcase model |
| SmolVLM 500M Instruct | `taobao-mnn/SmolVLM-500M-Instruct-MNN` | `9ab0218bef85c9b44c2aed87ee9bd8004d07bfb2` | MNN | 607 MB (real; config.json 291 B, llm.mnn 495 KB, llm.mnn.weight 384 MB, visual.mnn 223 KB, visual.mnn.weight 102 MB, embeddings_bf16.bin 90 MB, tokenizer.txt 868 KB, llm.mnn.json 1.0 MB) | 4 GB+ | to verify |
| Qwen3.5 2B | `taobao-mnn/Qwen3.5-2B-MNN` | `35781816d7b6a9dcb273a6765ac9563401951c3c` | MNN | 1.39 GB (real; config.json 652 B, export_args.json 1.0 KB, llm.mnn 2.0 MB, llm.mnn.weight 1.10 GB, visual.mnn 477 KB, visual.mnn.weight 187 MB, tokenizer.txt 6.2 MB, llm.mnn.json 5.1 MB) | 8 GB+ | to verify (3.6.1 fixes its fused inference, per SPEC); this build also ships a `visual.mnn`/`visual.mnn.weight` pair the draft entry does not advertise as a capability — confirm on-device whether it's usable before adding `image_input` |
| Eagle3 builds | *(unchanged)* | — | MNN | +90 MB | — | off by default; not a separate catalogue entry, per SPEC decision #7 |

Note: several package sizes above differ from the SPEC table's rough estimates (Gemma 3 1B: 584 MB
vs. "1.0 GB"; SmolVLM: 607 MB vs. "~0.5 GB"; Qwen3.5 2B: 1.39 GB vs. "~2 GB"; Qwen3-VL: 1.48 GB vs.
"1.5 GB", close). The draft catalogue uses the real, measured sizes. Someone should decide whether
to correct the SPEC table's estimate column too, or leave it as a rough pre-verification guess.

All non-gated repos above returned `"gated": false` / a plain 200 on `resolve`; only Gemma 3 1B is
gated. Every `revision` is the exact commit sha returned by
`GET https://huggingface.co/api/models/<repo>/revision/main` at lookup time (2026-09-28), not a
branch name.

## To verify on pong (per model)

1. Trigger the catalogue download for the entry (`downloadCatalogModel`), and confirm the MNN
   package expansion completes without a `package file missing` error (the `embeddings_int4.bin`
   risk above, specifically for Qwen3-VL).
2. `tai benchmark <id> --preset standard` (CPU, then GPU where `supportsAccelerator` allows it, per
   the SPEC "Processors" row).
3. Confirm the Check phase's 3 fixed questions pass (catches a build that loads but answers
   nonsense).
4. Record tok/s (CPU and GPU where applicable), TTFT and load time.
5. Update the Status column above (and the SPEC table) from "to verify" to "measured: `<n>` tok/s
   CPU, `<n>` GPU on pong", matching how the Qwen3-VL row already reads.
6. For Gemma 3 1B specifically: also confirm the app's `gated_model_requires_auth` flow
   (`TaiManager.downloadCatalogModel`, `TaiManager.java:425-432`) actually completes a gated
   download end-to-end with a saved Hugging Face token — no other catalogue entry (built-in or
   remote) currently exercises that path, since both built-in Gemma 4 entries are `gated: false`.

## Signing and publishing (today's process — described, not run)

There is no signing script in this repo (checked `scripts/`, `ci/`, `tools/`, `.github/`: nothing
references `tai-model-catalog.json` or `Ed25519` besides `TaiRemoteCatalog.java` itself and the
research doc `project-docs/reference/voice-ai/tai-model-ecosystem-research.md`). Signing is manual today. From what
`TaiRemoteCatalog.java` and `app/src/main/assets/tai-model-catalog.json` show:

1. The **payload** is exactly the shape of this draft file: `{version, expiresAt, entries: [...]}`,
   UTF-8 JSON bytes.
2. It is signed with **Ed25519**; the public half lives in `TaiRemoteCatalog.PUBLIC_KEY`
   (`MCowBQYDK2VwAyEANd5QI45iw4VvVnbUJ+Fxo4ptanIOdEsYYLf+VSrEXdo=`, an X.509 SubjectPublicKeyInfo,
   base64). The private key is **not in this repo**; whoever signs needs it out-of-band (per the
   developer, the agent is not to use the signing key itself — see this task's constraints).
3. The manifest file (`app/src/main/assets/tai-model-catalog.json`) is
   `{"payload": base64(payload bytes), "signature": base64(Ed25519 signature of the raw payload bytes)}`.
   Today's published manifest decodes to `{"version":1,"expiresAt":"2027-06-08T00:00:00Z","entries":[]}`
   — zero entries, so this is the first real content it would carry.
4. It is served from
   `https://raw.githubusercontent.com/PickleHik3/termux-launcher/experimental/app/src/main/assets/tai-model-catalog.json`
   (`TaiRemoteCatalog.URL`) — i.e. published by committing the signed manifest to
   `app/src/main/assets/tai-model-catalog.json` on the `experimental` branch and pushing it to
   GitHub. There is no CI/release step for this; it's a raw file fetch over HTTPS.
5. `TaiRemoteCatalog.refresh()` only accepts a manifest whose `version` is `>=` the version
   currently stored in `SharedPreferences` on-device, and whose `expiresAt` (an ISO instant) is in
   the future, verified against `Instant.now()` at refresh time. The next real publish must bump
   `version` past `1` — the draft here uses `2` — and pick an `expiresAt` far enough out that it
   doesn't need re-signing on every release (matching the existing `2027-06-08` is fine, or pick a
   fresh date).
6. Publishing steps, once the entries are verified and the sha256/size gaps above are filled in:
   a. Finalize `recommended-catalog-draft.json` (fill in Gemma 3 1B's sha256; (the 0.5B slot is now Qwen3 0.6B, see the table) resolve the Qwen2.5
      0.5B substitution decision; confirm the Qwen3-VL package-completeness risk).
   b. Sign the payload bytes with the Ed25519 private key (holder decides how — this task
      deliberately stops here).
   c. Base64-encode payload and signature into the `{"payload","signature"}` manifest shape and
      overwrite `app/src/main/assets/tai-model-catalog.json`.
   d. Commit and push to `experimental` (or wherever `TaiRemoteCatalog.URL` is repointed to, if that
      changes).
   e. Device-check that `TaiRemoteCatalog.refresh()` picks it up and the six new entries render in
      the catalogue/model centre correctly (this repeats the per-model device checks above, but
      through the actual signed-and-served path rather than a locally injected payload).

`app/src/main/assets/tai-model-catalog.json` was **not** touched by this pass — it's still the
existing empty-entries manifest, exactly as required.

## 0.5B slot survey (2026-09-28)

Candidates checked on huggingface.co (litert-community, taobao-mnn); Qwen3 0.6B was picked for both backends:
best quality and multilingual coverage at this size, ungated, apache-2.0, and a purpose-built `.litertlm`
(mixed int4, 498 MB) plus an MNN package. Qwen3 thinks by default; the check phase and chat should confirm
the no-think template path on pong.

- Lighter fallback if a sub-300 MB model is ever wanted: `taobao-mnn/gemma-3-270m-it-MNN` (~179 MB, ungated
  mirror; the LiteRT copy is gated). Weak out-of-box chat (Google pitches 270M as a fine-tuning base).
- Skipped: Qwen2 0.5B (superseded by Qwen3 0.6B), SmolLM2 360M (English-centric, weaker), Falcon-H1 0.5B and
  granite-4.0-h-350m (LiteRT only, custom/hybrid, less proven), LFM2.5 230M (below the floor, custom licence),
  Qwen3.5 0.8B (LiteRT int8 only, 963 MB; revisit when an int4 lands), Qwen2.5 0.5B / SmolLM 135M /
  TinyLlama (no `.litertlm`).
