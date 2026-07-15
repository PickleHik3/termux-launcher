MNN native runtime libraries for the arm64-v8a MNN backend.

MNN is Copyright 2018 Alibaba Group and licensed under Apache-2.0. These are modified object-code
builds; the corresponding upstream source, local patch, and build recipe are identified below.
See the repository's `THIRD_PARTY_NOTICES.md` and `LICENSE-TERMINAL-EMULATOR` (Apache-2.0 text).

These binaries were built locally on **2026-07-14** from upstream MNN **3.6.0**
(tag `3.6.0`) with the same inputs and stages as
`.github/workflows/build_mnn_native.yml`. The local build used NDK r27c
(`27.2.12479018`) and capped parallel compilation at 8 jobs.

- `libMNN.so` — MNN core with the LLM engine bundled (`MNN_BUILD_LLM=ON`,
  `MNN_SEP_BUILD=OFF`, vision + OpenCL + audio), built via `project/android/build_64.sh`.
- `libmnnllmapp.so` — the MnnLlmChat JNI bridge (`apps/Android/MnnLlmChat/app/src/main/cpp`),
  which at 3.6.0 includes `utf8_stream_processor.hpp`, fixing the streaming UTF-8 (emoji)
  `NewStringUTF` crash present in the previous 0.8.3 binaries, plus the local
  `embedding_jni.cpp` bridge for `MnnEmbeddingSession`.

Local artifact SHA-256 values:

- `libMNN.so`: `acd53610c6676d98f4bcda431a263b61c24f56fd6e515ce66cd97fa21f0802d6`
- `libmnnllmapp.so`: `3f6de61768a596cfece619bb55122d8e13204a91d5bb2db117791415e77bc30a`
  (after `llvm-strip --strip-debug`)

Built with NDK r27c, `ANDROID_STL=c++_static`, min API 30. The Java shims
`com.alibaba.mnnllm.android.llm.LlmSession` and `MnnEmbeddingSession` match the JNI method
names and signatures exported by this `libmnnllmapp.so` (verified with `llvm-nm -D`);
`submitStructuredChatNative` does not exist upstream and the app no longer references it.
