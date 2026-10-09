// VAJ Terminal / TAI addition - JNI bridge for MNN text-to-image (Diffusion engine).
//
// Upstream MnnLlmChat hardcodes Stable Diffusion 1.5 + OpenCL in diffusion_jni.cpp and keeps the
// Sana pipeline in a second bridge. TAI needs one surface that takes the model type (SD 1.5,
// Taiyi, Sana) and the backend (opencl|cpu), and that fixes a few upstream gaps:
//
//  * The engine calls runtime_manager_->setCache(".tempcache") with a RELATIVE path. An Android
//    app's working directory is "/", so the OpenCL tuning cache would never persist and every load
//    would re-tune. We chdir() into a per-model cache directory right before createDiffusion/load.
//    The working directory is process-wide and is intentionally not restored: the :tai_runtime
//    process does not rely on relative paths anywhere else.
//  * GetStringUTFChars results are always released, and every entry point catches exceptions.
//  * The engine ignores the progress callback's return value, so a run cannot be aborted mid-way.
//    Cancellation lives in the Java layer (flag between runs, result discarded).
//
// The engine call sequence mirrors the official app (diffusion_session.cpp / sana_session.cpp):
//  * SD 1.5 / Taiyi: createDiffusion + load once, run; with memoryMode != 1 the modules are
//    dropped after each run and loaded again for the next one.
//  * Sana: SanaLlm(dir + "/llm")->process(...), free the LLM, then createDiffusion(type 2) + load +
//    run("text2img" | "img2img"), free everything; the LLM and the diffusion stack are never alive
//    together.
//
// Symbols bind to com.alibaba.mnnllm.android.llm.TaiDiffusionSession. Copied into
// apps/Android/MnnLlmChat/app/src/main/cpp/ and added to the libmnnllmapp.so source list by
// .github/workflows/build_mnn_native.yml.

#include <jni.h>
#include <android/log.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <cstdio>
#include <functional>
#include <memory>
#include <string>

#include <MNN/MNNForwardType.h>
#include "diffusion/diffusion.hpp"
#include "diffusion/sana_llm.hpp"

#define TAI_DIFF_LOG_TAG "TaiMnnDiffusion"
#define TAI_DIFF_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAI_DIFF_LOG_TAG, __VA_ARGS__)
#define TAI_DIFF_LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAI_DIFF_LOG_TAG, __VA_ARGS__)

using MNN::DIFFUSION::Diffusion;
using MNN::DIFFUSION::DiffusionModelType;
using MNN::DIFFUSION::SanaLlm;
using MNN::Express::VARP;

namespace {

constexpr int kTypeSd15 = 0;
constexpr int kTypeTaiyi = 1;
constexpr int kTypeSana = 2;

struct TaiDiffusionHandle {
    std::string modelDir;
    int modelType = kTypeSd15;
    MNNForwardType backend = MNN_FORWARD_OPENCL;
    int memoryMode = 1;
    std::string cacheDir;
    // SD / Taiyi only; Sana builds and frees its stack inside every run.
    std::unique_ptr<Diffusion> diffusion;
};

std::string toStdString(JNIEnv *env, jstring value) {
    if (value == nullptr) return std::string();
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

bool makeDirs(const std::string &path) {
    if (path.empty()) return false;
    std::string partial;
    size_t start = 0;
    while (start <= path.size()) {
        size_t slash = path.find('/', start);
        if (slash == std::string::npos) slash = path.size();
        partial = path.substr(0, slash);
        if (!partial.empty()) {
            if (mkdir(partial.c_str(), 0700) != 0 && errno != EEXIST) return false;
        }
        start = slash + 1;
    }
    return true;
}

// See the file header: the engine's OpenCL tuning cache is a relative ".tempcache".
void enterCacheDir(const std::string &cacheDir) {
    if (cacheDir.empty()) return;
    if (!makeDirs(cacheDir)) {
        TAI_DIFF_LOGE("could not create cache dir %s", cacheDir.c_str());
        return;
    }
    if (chdir(cacheDir.c_str()) != 0) {
        TAI_DIFF_LOGE("could not enter cache dir %s", cacheDir.c_str());
    }
}

std::string jsonEscape(const std::string &in) {
    std::string out;
    out.reserve(in.size() + 8);
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

std::string resultJson(bool ok, long long totalUs, long long loadUs, const std::string &message) {
    char head[128];
    snprintf(head, sizeof(head), "{\"ok\":%s,\"totalUs\":%lld,\"loadUs\":%lld,\"message\":\"",
             ok ? "true" : "false", totalUs, loadUs);
    return std::string(head) + jsonEscape(message) + "\"}";
}

long long elapsedUs(std::chrono::steady_clock::time_point since) {
    return std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - since).count();
}

// Drives the Java GenerateProgressListener; the return value of onProgress is ignored by the
// engine's callback contract (and by us).
struct ProgressBridge {
    JNIEnv *env = nullptr;
    jobject listener = nullptr;
    jmethodID method = nullptr;

    ProgressBridge(JNIEnv *e, jobject l) : env(e), listener(l) {
        if (listener != nullptr) {
            jclass cls = env->GetObjectClass(listener);
            method = env->GetMethodID(cls, "onProgress", "(Ljava/lang/String;)Z");
            env->DeleteLocalRef(cls);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                method = nullptr;
            }
        }
    }

    void report(int percent) const {
        if (listener == nullptr || method == nullptr) return;
        std::string text = std::to_string(percent);
        jstring js = env->NewStringUTF(text.c_str());
        if (js == nullptr) return;
        env->CallBooleanMethod(listener, method, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
};

bool runSd(TaiDiffusionHandle *h, const std::string &prompt, const std::string &outputPath,
           int steps, int seed, const ProgressBridge &progress, long long *loadUs,
           std::string *message) {
    auto loadStart = std::chrono::steady_clock::now();
    if (!h->diffusion) {
        enterCacheDir(h->cacheDir);
        h->diffusion.reset(Diffusion::createDiffusion(
                h->modelDir, static_cast<DiffusionModelType>(h->modelType), h->backend,
                h->memoryMode));
        if (!h->diffusion) {
            *message = "The image engine could not create the model.";
            return false;
        }
        if (!h->diffusion->load()) {
            h->diffusion.reset();
            *message = "The image model failed to load.";
            return false;
        }
    }
    *loadUs = elapsedUs(loadStart);
    // For SD/Taiyi the engine's run(prompt, imagePath, ...) writes the image to imagePath.
    bool ok = h->diffusion->run(prompt, outputPath, steps, seed,
                                [&progress](int p) { progress.report(p); });
    if (!ok) *message = "The image engine reported a failed run.";
    // Upstream marks the session unloaded after every run unless memoryMode == 1 (run() itself
    // drops the text encoder and the modules that do not fit the mode). Drop the whole stack so
    // the next run starts from a clean load.
    if (h->memoryMode != 1) h->diffusion.reset();
    return ok;
}

bool runSana(TaiDiffusionHandle *h, const std::string &prompt, const std::string &inputImage,
             const std::string &outputPath, int width, int height, int steps, int seed,
             bool useCfg, float cfgScale, const ProgressBridge &progress, long long *loadUs,
             std::string *message) {
    h->diffusion.reset();
    VARP llmOut;
    {
        auto llmStart = std::chrono::steady_clock::now();
        std::unique_ptr<SanaLlm> llm(new SanaLlm(h->modelDir + "/llm"));
        *loadUs = elapsedUs(llmStart);
        llmOut = useCfg ? llm->process(prompt, true, "") : llm->process(prompt, false);
        if (llmOut.get() == nullptr) {
            *message = "The prompt encoder produced no features.";
            return false;
        }
        // llm is destroyed here, before the diffusion stack exists, as upstream does. llmOut keeps
        // its own data alive.
    }
    auto loadStart = std::chrono::steady_clock::now();
    enterCacheDir(h->cacheDir);
    std::unique_ptr<Diffusion> diffusion(Diffusion::createDiffusion(
            h->modelDir, static_cast<DiffusionModelType>(kTypeSana), h->backend, h->memoryMode));
    if (!diffusion) {
        llmOut = nullptr;
        *message = "The image engine could not create the model.";
        return false;
    }
    if (!diffusion->load()) {
        llmOut = nullptr;
        *message = "The image model failed to load.";
        return false;
    }
    *loadUs += elapsedUs(loadStart);
    std::string mode = inputImage.empty() ? "text2img" : "img2img";
    bool ok = diffusion->run(llmOut, mode, inputImage, outputPath, width, height, steps, seed,
                             useCfg, cfgScale, [&progress](int p) { progress.report(p); });
    if (ok) progress.report(100);
    else *message = "The image engine reported a failed run.";
    // Feature tensor first: its destructor can touch diffusion-owned graph state.
    llmOut = nullptr;
    diffusion.reset();
    return ok;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_alibaba_mnnllm_android_llm_TaiDiffusionSession_initNative(
        JNIEnv *env, jobject /*thiz*/, jstring model_dir, jint model_type, jstring backend,
        jint memory_mode, jstring cache_dir) {
    try {
        auto *h = new TaiDiffusionHandle();
        h->modelDir = toStdString(env, model_dir);
        h->modelType = model_type;
        h->backend = toStdString(env, backend) == "cpu" ? MNN_FORWARD_CPU : MNN_FORWARD_OPENCL;
        h->memoryMode = memory_mode;
        h->cacheDir = toStdString(env, cache_dir);
        if (h->modelDir.empty() || model_type < kTypeSd15 || model_type > kTypeSana) {
            delete h;
            return 0L;
        }
        // Models load lazily inside generateNative so the load time is reported with the run.
        return reinterpret_cast<jlong>(h);
    } catch (const std::exception &e) {
        TAI_DIFF_LOGE("initNative threw: %s", e.what());
    } catch (...) {
        TAI_DIFF_LOGE("initNative threw an unknown error");
    }
    return 0L;
}

JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_llm_TaiDiffusionSession_generateNative(
        JNIEnv *env, jobject /*thiz*/, jlong ptr, jstring prompt, jstring input_image_path,
        jstring output_path, jint width, jint height, jint steps, jint seed, jboolean use_cfg,
        jfloat cfg_scale, jobject listener) {
    auto *h = reinterpret_cast<TaiDiffusionHandle *>(ptr);
    std::string message;
    long long loadUs = 0;
    auto start = std::chrono::steady_clock::now();
    bool ok = false;
    if (h == nullptr) {
        message = "The image session is not loaded.";
    } else {
        try {
            std::string promptStr = toStdString(env, prompt);
            std::string inputStr = toStdString(env, input_image_path);
            std::string outputStr = toStdString(env, output_path);
            ProgressBridge progress(env, listener);
            if (h->modelType == kTypeSana) {
                ok = runSana(h, promptStr, inputStr, outputStr, width, height, steps, seed,
                             use_cfg == JNI_TRUE, cfg_scale, progress, &loadUs, &message);
            } else {
                ok = runSd(h, promptStr, outputStr, steps, seed, progress, &loadUs, &message);
            }
        } catch (const std::exception &e) {
            TAI_DIFF_LOGE("generateNative threw: %s", e.what());
            message = e.what();
            ok = false;
            h->diffusion.reset();
        } catch (...) {
            TAI_DIFF_LOGE("generateNative threw an unknown error");
            message = "The image engine failed unexpectedly.";
            ok = false;
            h->diffusion.reset();
        }
    }
    std::string json = resultJson(ok, elapsedUs(start), loadUs, message);
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_TaiDiffusionSession_releaseNative(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ptr) {
    auto *h = reinterpret_cast<TaiDiffusionHandle *>(ptr);
    if (h == nullptr) return;
    try {
        delete h;
    } catch (const std::exception &e) {
        TAI_DIFF_LOGE("releaseNative threw: %s", e.what());
    } catch (...) {
        TAI_DIFF_LOGE("releaseNative threw an unknown error");
    }
}

}  // extern "C"
