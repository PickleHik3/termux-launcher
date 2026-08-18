// VAJ Terminal / TAI addition — JNI bridge for MNN text embeddings.
//
// Upstream MnnLlmChat only exposes the chat Llm engine over JNI. TAI needs
// on-device embeddings from MNN-format models (config.json packages), so this
// file exports a tiny embedding surface over MNN's Transformer::Embedding
// engine (already built into libMNN.so via -DMNN_BUILD_LLM=true).
//
// Deliberately dependency-light: it returns a raw float[] and lets the Java/
// Kotlin side (MnnEmbeddingSession -> MnnEmbeddingRuntime) shape the OpenAI /
// Ollama JSON. Symbols bind to com.alibaba.mnnllm.android.llm.MnnEmbeddingSession.
//
// Copied into apps/Android/MnnLlmChat/app/src/main/cpp/ and added to the
// libmnnllmapp.so source list by .github/workflows/build_mnn_native.yml.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "llm/llm.hpp"
#include "MNN/expr/Expr.hpp"

#define TAI_EMBED_LOG_TAG "TaiMnnEmbedding"
#define TAI_EMBED_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAI_EMBED_LOG_TAG, __VA_ARGS__)

using MNN::Transformer::Embedding;
using MNN::Express::VARP;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_alibaba_mnnllm_android_llm_MnnEmbeddingSession_initNative(
        JNIEnv *env, jobject /*thiz*/, jstring config_path) {
    if (config_path == nullptr) return 0L;
    const char *chars = env->GetStringUTFChars(config_path, nullptr);
    if (chars == nullptr) return 0L;
    std::string path(chars);
    env->ReleaseStringUTFChars(config_path, chars);
    Embedding *embedding = nullptr;
    try {
        embedding = Embedding::createEmbedding(path, true);
    } catch (const std::exception &e) {
        TAI_EMBED_LOGE("createEmbedding threw: %s", e.what());
        embedding = nullptr;
    } catch (...) {
        TAI_EMBED_LOGE("createEmbedding threw an unknown error");
        embedding = nullptr;
    }
    return reinterpret_cast<jlong>(embedding);
}

JNIEXPORT jint JNICALL
Java_com_alibaba_mnnllm_android_llm_MnnEmbeddingSession_dimNative(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ptr) {
    auto *embedding = reinterpret_cast<Embedding *>(ptr);
    if (embedding == nullptr) return 0;
    return static_cast<jint>(embedding->dim());
}

JNIEXPORT jfloatArray JNICALL
Java_com_alibaba_mnnllm_android_llm_MnnEmbeddingSession_embedNative(
        JNIEnv *env, jobject /*thiz*/, jlong ptr, jstring text) {
    auto *embedding = reinterpret_cast<Embedding *>(ptr);
    if (embedding == nullptr || text == nullptr) return nullptr;
    const char *chars = env->GetStringUTFChars(text, nullptr);
    if (chars == nullptr) return nullptr;
    std::string input(chars);
    env->ReleaseStringUTFChars(text, chars);

    VARP vector;
    try {
        vector = embedding->txt_embedding(input);
    } catch (const std::exception &e) {
        TAI_EMBED_LOGE("txt_embedding threw: %s", e.what());
        return nullptr;
    } catch (...) {
        TAI_EMBED_LOGE("txt_embedding threw an unknown error");
        return nullptr;
    }
    if (vector.get() == nullptr) return nullptr;
    auto info = vector->getInfo();
    if (info == nullptr) return nullptr;
    const float *data = vector->readMap<float>();
    if (data == nullptr) return nullptr;

    jsize count = static_cast<jsize>(info->size);
    jfloatArray result = env->NewFloatArray(count);
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, count, data);
    return result;
}

JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_MnnEmbeddingSession_releaseNative(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ptr) {
    auto *embedding = reinterpret_cast<Embedding *>(ptr);
    delete embedding;
}

} // extern "C"
