MNN native runtime libraries for the arm64-v8a MNN backend.

MNN is Copyright 2018 Alibaba Group and licensed under Apache-2.0. These are modified object-code
builds; the corresponding upstream source, local patch, and build recipe are identified below.
See the repository's `THIRD_PARTY_NOTICES.md` and `LICENSE-TERMINAL-EMULATOR` (Apache-2.0 text).

These binaries were built locally on **2026-07-21** from upstream MNN **3.6.0**
(tag `3.6.0`) with the same inputs and stages as
`.github/workflows/build_mnn_native.yml`. The local build used NDK r27c
(`27.2.12479018`) and capped parallel compilation at 8 jobs.

- `libMNN.so` — MNN core with the LLM engine bundled (`MNN_BUILD_LLM=ON`,
  `MNN_SEP_BUILD=OFF`, vision + OpenCL + audio), built via `project/android/build_64.sh`.
- `libmnnllmapp.so` — the MnnLlmChat JNI bridge (`apps/Android/MnnLlmChat/app/src/main/cpp`),
  which at 3.6.0 includes `utf8_stream_processor.hpp`, fixing the streaming UTF-8 (emoji)
  `NewStringUTF` crash present in the previous 0.8.3 binaries, the local
  `embedding_jni.cpp` bridge for `MnnEmbeddingSession`, and the
  `llm_session_generation.patch` that drives generation through one upstream
  `response(..., max_new_tokens_)` call (cancellation via `LlmStatus::USER_CANCEL`)
  instead of repeated `generate(1)` steps, so Eagle speculative decoding stays correct.

Local artifact SHA-256 values:

- `libMNN.so`: `acd53610c6676d98f4bcda431a263b61c24f56fd6e515ce66cd97fa21f0802d6`
- `libmnnllmapp.so`: `ca595350cf011a46702f87600133861b7015187901438069b6223db4cd19b349`
  (after `llvm-strip --strip-debug`)

Built with NDK r27c, `ANDROID_STL=c++_static`, min API 30. The Java shims
`com.alibaba.mnnllm.android.llm.LlmSession` and `MnnEmbeddingSession` match the JNI method
names and signatures exported by this `libmnnllmapp.so` (verified with `llvm-nm -D`);
`submitStructuredChatNative` does not exist upstream and the app no longer references it.
