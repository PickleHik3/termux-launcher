MNN native runtime libraries for the arm64-v8a MNN backend.

MNN is Copyright 2018 Alibaba Group and licensed under Apache-2.0. These are modified object-code
builds; the corresponding upstream source, local patch, and build recipe are identified below.
See the repository's `THIRD_PARTY_NOTICES.md` and `LICENSE-TERMINAL-EMULATOR` (Apache-2.0 text).

These binaries were built locally on **2026-09-28** from upstream MNN **3.6.1**
(tag `3.6.1`) with the same inputs and stages as
`.github/workflows/build_mnn_native.yml`. The local build used NDK r27c
(`27.2.12479018`) and capped parallel compilation at 8 jobs.

- `libMNN.so` — MNN core with the LLM engine bundled (`MNN_BUILD_LLM=ON`,
  `MNN_SEP_BUILD=OFF`, vision + OpenCL + audio), built via `project/android/build_64.sh`.
- `libmnnllmapp.so` — the MnnLlmChat JNI bridge (`apps/Android/MnnLlmChat/app/src/main/cpp`),
  which at 3.6.1 includes `utf8_stream_processor.hpp`, fixing the streaming UTF-8 (emoji)
  `NewStringUTF` crash present in the previous 0.8.3 binaries, the local
  `embedding_jni.cpp` bridge for `MnnEmbeddingSession`, and the
  `llm_session_generation.patch` that drives generation through one upstream
  `response(..., max_new_tokens_)` call (cancellation via `LlmStatus::USER_CANCEL`)
  instead of repeated `generate(1)` steps, so Eagle speculative decoding stays correct.

Local artifact SHA-256 values:

- `libMNN.so`: `9711e66fe177eca9ab0d3b7ae81858979c86c2f00b0aa4e339b92b80ac54db7a`
- `libmnnllmapp.so`: `005f6c78224936e6fffc39a522202f7934ac9966e78767f8842c489c1be6b6af`
  (after `llvm-strip --strip-debug`)

`libmnnllmapp.so` was rebuilt locally on **2026-09-30** to add `ci/mnn-patch/tai_diffusion_jni.cpp`,
the `TaiDiffusionSession` text-to-image bridge (SD 1.5, Taiyi, Sana; OpenCL or CPU; persistent
OpenCL tuning cache). Only the bridge was rebuilt: it was compiled from the patched 3.6.1
`MnnLlmChat` sources and linked against the committed `libMNN.so` above, which is unchanged.

Built with NDK r27c, `ANDROID_STL=c++_static`, min API 30. The Java shims
`com.alibaba.mnnllm.android.llm.LlmSession` and `MnnEmbeddingSession` match the JNI method
names and signatures exported by this `libmnnllmapp.so` (verified with `llvm-nm -D`);
`submitStructuredChatNative` does not exist upstream and the app no longer references it.
