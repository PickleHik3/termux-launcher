#!/usr/bin/env bash
# Local replication of .github/workflows/build_mnn_native.yml (termux-launcher).
# Builds libMNN.so + libmnnllmapp.so (arm64-v8a, MNN 3.6.0, UTF-8 + embedding JNI patches).
set -euxo pipefail

SDK=/home/amal/android-sdk
NDK_VER=27.2.12479018
SRC=/home/amal/termux-launcher/app/mnn-src
SELF=/home/amal/termux-launcher/app/termux-launcher
OUT=/home/amal/termux-launcher/app/mnn-out
JOBS=8

# 1. NDK r27c (parity with CI)
if [ ! -d "$SDK/ndk/$NDK_VER" ]; then
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" "ndk;$NDK_VER"
fi
export ANDROID_NDK="$SDK/ndk/$NDK_VER"

# 2. MNN @ 3.6.0
if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 --branch 3.6.0 --recurse-submodules --shallow-submodules \
    https://github.com/alibaba/MNN "$SRC"
fi
cd "$SRC"
git checkout -q 3.6.0 2>/dev/null || true

# 3. Patches (same as CI)
CPP="$SRC/apps/Android/MnnLlmChat/app/src/main/cpp"
cp -v "$SELF/ci/mnn-patch/utf8_stream_processor.hpp" "$CPP/utf8_stream_processor.hpp"
grep -q "0xC0) != 0x80" "$CPP/utf8_stream_processor.hpp"
# One-shot response() generation for Eagle speculative decoding (idempotent: skip if applied).
if grep -Eq '^[[:space:]]*llm_->generate\(1\);' "$CPP/llm_session.cpp"; then
  git -C "$SRC" apply "$SELF/ci/mnn-patch/llm_session_generation.patch"
fi
grep -q 'max_new_tokens_)' "$CPP/llm_session.cpp"
cp -v "$SELF/ci/mnn-patch/embedding_jni.cpp" "$CPP/embedding_jni.cpp"
if ! grep -q "embedding_jni.cpp" "$CPP/CMakeLists.txt"; then
  sed -i 's/        llm_mnn_jni.cpp/        llm_mnn_jni.cpp\n        embedding_jni.cpp/' "$CPP/CMakeLists.txt"
fi
grep -q "embedding_jni.cpp" "$CPP/CMakeLists.txt"

# 4. libMNN.so
cd "$SRC/project/android"
mkdir -p build_64 && cd build_64
MNN_JOBS=$JOBS ../build_64.sh "-DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true -DMNN_ARM82=true -DMNN_USE_LOGCAT=true -DMNN_OPENCL=true -DLLM_SUPPORT_VISION=true -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DLLM_SUPPORT_AUDIO=true -DMNN_BUILD_AUDIO=true -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' -DCMAKE_INSTALL_PREFIX=." || {
  # build_64.sh may not honor MNN_JOBS; retry plain make with capped jobs
  make -j$JOBS
}
mkdir -p lib
find . -maxdepth 2 -name 'libMNN*.so' -exec cp -v {} lib/ \;

# 5. libmnnllmapp.so
cd "$SRC"
cmake -S "$CPP" -B build_app \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-30 \
  -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release
cmake --build build_app -j$JOBS

# 6. Collect
mkdir -p "$OUT"
find "$SRC/project/android/build_64" -maxdepth 2 -name 'libMNN.so' -exec cp -v {} "$OUT/" \;
find "$SRC/build_app" -name 'libmnnllmapp.so' -exec cp -v {} "$OUT/" \;
test -f "$OUT/libMNN.so" && test -f "$OUT/libmnnllmapp.so"
ls -la "$OUT"
echo BUILD_COMPLETE
