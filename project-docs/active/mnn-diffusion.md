# MNN diffusion (text to image)

Status: engine bridge, runtime, admission, route, `tai image`, tests and docs are on `feat/mnn-diffusion`; the
importer now installs image models (Hugging Face link, picked folder, `tai import`) and Model Centre lists them
under Image generation. Not compiled on a device, not device-tested.

## What is built

- **Native bridge** `ci/mnn-patch/tai_diffusion_jni.cpp` for `TaiDiffusionSession`: SD 1.5, Taiyi and Sana
  behind one `initNative / generateNative / releaseNative`, OpenCL or CPU, upstream memory modes, a `chdir()`
  into a per-model cache dir so the OpenCL tuning cache persists, released JNI strings, exceptions caught.
  Registered in `.github/workflows/build_mnn_native.yml`.
- **Java** `TaiDiffusionSession`, `TaiDiffusionPackage` (layout detection and validation from the files
  MNN 3.6.1 opens), `TaiImageRequest` (parsing, per-type size rules), `TaiImageAdmission` (fastest memory mode
  that fits), `MnnDiffusionRuntime` (lazy load, one run at a time, discard-on-cancel), `TaiResidency.Kind.IMAGE`
  with idle unload and eviction order, `OP_IMAGE_GENERATE` / `OP_IMAGE_CANCEL` lanes in `TaiRuntimeService`.
- **Model spec** backend `mnn-diffusion`, capability `image_generation`: kept out of chat lists, `/v1/models`,
  the Model centre's chat list and `tai load` (`400 image_model_not_loadable`). Remote catalogue payloads with
  this backend are dropped (the importer owns installation; no recommended-catalogue entries yet).
- **Import** (`TaiDiffusionImport`, `TaiDiffusionTokenizer`, and the existing downloader/importer/manager):
  detection before the MNN chat path, one Hugging Face candidate per package (named by its first small file,
  sized by the whole package, `diffusion` = `sd15|taiyi|sana`), the downloader's multi-file fetch extracted to
  `fetchPackageFile` and shared, small files first, junk skipped, the bundled CLIP tokenizer installed when the
  raw files hash to the standard ones and refused otherwise, the spec's `architecture` holding the type.
  Model Centre: Installed gets an Image generation group (delete only).
- **Surface** `POST /v1/ai/images/generations`, `POST /v1/ai/images/cancel`, `tai image`.
- **Docs** `docs/en/LauncherCtl_API.md`, `docs/en/On_Device_AI_Backends.md`.

## Decisions to know

- Taiyi cannot be told from Stable Diffusion by files; the type is a request/CLI hint (`model_type`, `--type`)
  and, for a registered model, the spec's `architecture` (`sd15`, `taiyi`, `sana`).
- SD/Taiyi need `tokenizer.mtok`. The published packages ship `vocab.json` + `merges.txt`; the import installs
  the bundled CLIP ViT-L/14 `tokenizer.mtok` when both hashes match and refuses anything else. A package the
  importer did not touch (a folder registered by path) still fails with `tokenizer_mtok_missing`.
- Sana emits no progress between its start and its end (the engine calls the callback only for SD), so the
  stream shows 0 then 100.
- Memory factors (1.5 / 1.1 / 0.8 of the peak working set) are guesses; every successful run records its measured
  peak in the runtime history, keyed by memory mode, and later runs use it.
- Sana is never kept resident (it frees the LLM and the diffusion stack each run, like upstream).
- The crash marker the chat loads use is not written for image loads.

## Owed

1. Rebuild `libmnnllmapp.so` (CI or the local recipe) and update the SHA lines in
   `app/src/main/jniLibs/arm64-v8a/README.md`; until then image routes answer `501 mnn_image_unavailable`.
2. Device tests on pong: import both repositories from the app (link) and from a folder; SD 1.5 at mode 1 and 2,
   OpenCL vs `--cpu`, the first-load tuning time against the cached one (the `tai-diffusion-cache` effect),
   Sana text and edit, memory behaviour next to a loaded chat model, idle unload, Ctrl-C.
3. Taiyi has no published `tokenizer.mtok` here: a Taiyi package with raw tokenizer files is refused; one that ships
   `tokenizer.mtok` imports (the type comes from the name containing "taiyi").
4. Catalogue entries for image models (signed and published separately) and the Sana prompt-LLM defaults the
   upstream app sets.
5. Not supported yet: Wan video and Stable Diffusion 3.5; a direct file link to an image package.
