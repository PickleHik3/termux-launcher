# MNN diffusion (text to image)

Status: built on branch `feat/mnn-diffusion-backend` (engine bridge, runtime, admission, route, `tai image`,
tests, docs); not compiled, not device-tested. Nothing in the importer, downloader or catalogue changed.

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
  this backend are dropped (the importer will own installation).
- **Surface** `POST /v1/ai/images/generations`, `POST /v1/ai/images/cancel`, `tai image`.
- **Docs** `docs/en/LauncherCtl_API.md`, `docs/en/On_Device_AI_Backends.md`.

## Decisions to know

- Taiyi cannot be told from Stable Diffusion by files; the type is a request/CLI hint (`model_type`, `--type`)
  and, for a registered model, the spec's `architecture` (`sd15`, `taiyi`, `sana`).
- SD/Taiyi need `tokenizer.mtok`. The published packages ship `vocab.json` + `merges.txt` and fail with
  `tokenizer_mtok_missing`; nothing converts them on the phone.
- Sana emits no progress between its start and its end (the engine calls the callback only for SD), so the
  stream shows 0 then 100.
- Memory factors (1.5 / 1.1 / 0.8 of the peak working set) are guesses; every successful run records its measured
  peak in the runtime history, keyed by memory mode, and later runs use it.
- Sana is never kept resident (it frees the LLM and the diffusion stack each run, like upstream).
- The crash marker the chat loads use is not written for image loads.

## Owed

1. Rebuild `libmnnllmapp.so` (CI or the local recipe) and update the SHA lines in
   `app/src/main/jniLibs/arm64-v8a/README.md`; until then image routes answer `501 mnn_image_unavailable`.
2. Device tests on pong: SD 1.5 at mode 1 and 2, OpenCL vs `--cpu`, the first-load tuning time against the
   cached one (the `tai-diffusion-cache` effect), Sana text and edit, memory behaviour next to a loaded chat model,
   idle unload, Ctrl-C.
3. A `tokenizer.mtok` export for SD 1.5 and Taiyi (MNN's `convert_mnn.py`) to test with.
4. Importer, downloader and catalogue entries for image models (deliberately not started), the Model centre
   section, and the Sana prompt-LLM defaults the upstream app sets.
5. Not supported yet: Wan video and Stable Diffusion 3.5.
