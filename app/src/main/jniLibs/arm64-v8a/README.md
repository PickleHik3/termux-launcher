MNN native runtime libraries for the arm64-v8a MNN backend.

MNN is Copyright 2018 Alibaba Group and licensed under Apache-2.0. These are modified object-code
builds; the corresponding upstream source, local patch, and build recipe are identified below.
See the repository's `THIRD_PARTY_NOTICES.md` and `LICENSE-TERMINAL-EMULATOR` (Apache-2.0 text).

These binaries were built on **2026-11-04** by `.github/workflows/build_mnn_native.yml`
(GitHub Actions run 41821309448, branch `fix/211-mnn-3.6.2`) from upstream MNN **3.6.2**
(tag `3.6.2`), which adds the `IndexedLinear` op (sparse MoE routing, CPU kernel
`CPUIndexedLinear`) that MoE models such as LFM2.5-8B-A1B and MobileMoE need (#211).

- `libMNN.so` — MNN core with the LLM engine bundled (`MNN_BUILD_LLM=ON`,
  `MNN_SEP_BUILD=OFF`, vision + OpenCL + audio), built via `project/android/build_64.sh`.
- `libmnnllmapp.so` — the MnnLlmChat JNI bridge (`apps/Android/MnnLlmChat/app/src/main/cpp`)
  with the local `utf8_stream_processor.hpp` (validated UTF-8 streaming), `embedding_jni.cpp`
  (`MnnEmbeddingSession`), `tai_diffusion_jni.cpp` (`TaiDiffusionSession`: SD 1.5, Taiyi, Sana)
  and `llm_session_generation.patch` (one upstream `response(..., max_new_tokens_)` call, so
  Eagle speculative decoding stays correct). Debug info removed with NDK r29
  `llvm-strip --strip-debug` after download.

SHA-256:

- `libMNN.so`: `16f31ca3367aa5ccebd21890e0e2c7c886346809b68ec7ca41126b5ba6fd2cca`
- `libmnnllmapp.so`: `332e2aa6802f432216adafea58c7a69c96be316361740086f422d36d9fa54a36`

The bridge exports the same 32 JNI methods as the 3.6.1 build (`llvm-nm -D`), so the Java shims
`com.alibaba.mnnllm.android.llm.LlmSession`, `MnnEmbeddingSession` and `TaiDiffusionSession`
are unchanged. Built with `ANDROID_STL=c++_static`, min API 30.
