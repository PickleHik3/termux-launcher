// JNI shim behind com.termux.ai.TaiXnnpackDelegate.
//
// litert 1.4.2's NativeInterpreterWrapper builds an XNNPACK delegate for the Interpreter, but
// that delegate never gets worker threads: simpleperf on pong showed Whisper inference 99.56%
// on one thread, and the app's per-step timings equalled benchmark_model --num_threads=1 (see
// project-docs/plans/whisper-voice-input.md). setUseXNNPACK(false) proved the interpreter's own
// thread pool is fine (7 threads spawn); it is specifically XNNPACK's pthreadpool that is lost.
//
// The fix builds our own XNNPACK delegate instead of leaning on the Java API's. The C symbols
// (TfLiteXNNPackDelegateOptionsDefault / …Create / …Delete / …GetThreadPool) are exported by
// libtensorflowlite_jni.so, the native library org.tensorflow.lite.Interpreter itself loads
// (confirmed with llvm-nm -D on the .so bundled by com.google.ai.edge.litert:litert:1.4.2), so
// this shim never links or ships its own TFLite: it dlopens the already-resident library and
// dlsym's straight into it.
#include <dlfcn.h>
#include <stdint.h>
#include <string.h>
#include <jni.h>
#include <android/log.h>

#define TAG "TaiXnnpack"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// TfLiteXNNPackDelegateOptions is not in this repo's headers (no TFLite sources are vendored),
// and its true size varies by TFLite version. Only the first field (num_threads) is ever written
// or read here, so a locally declared struct much larger than any real version of the upstream
// struct stands in for it: the default-options function returns the real struct BY VALUE, and on
// both arm64 and x86_64 the calling convention for a struct this large is "caller passes a
// pointer to a buffer, callee fills it in" — so a caller-supplied buffer bigger than the callee
// ever writes is safe, it just leaves the tail bytes whatever OptionsDefault left there (zeroed
// by the callee in every known TFLite version, but this shim never reads past num_threads).
typedef struct {
    int32_t num_threads;
    unsigned char rest[1020];
} TaiXnnOptions;

typedef TaiXnnOptions (*TfLiteXNNPackDelegateOptionsDefault_t)(void);
typedef void *(*TfLiteXNNPackDelegateCreate_t)(const TaiXnnOptions *options);
typedef void (*TfLiteXNNPackDelegateDelete_t)(void *delegate);
typedef void *(*TfLiteXNNPackDelegateGetThreadPool_t)(void *delegate);

JNIEXPORT jlong JNICALL
Java_com_termux_ai_TaiXnnpackDelegate_nativeCreate(JNIEnv *env, jclass clazz, jint numThreads) {
    (void) env;
    (void) clazz;

    void *lib = dlopen("libtensorflowlite_jni.so", RTLD_NOW | RTLD_NOLOAD);
    if (lib == NULL) {
        // Not yet loaded by anything in this process (unexpected — Interpreter loads it — but
        // fall back to a real load rather than fail outright).
        lib = dlopen("libtensorflowlite_jni.so", RTLD_NOW);
    }
    if (lib == NULL) {
        LOGE("dlopen(libtensorflowlite_jni.so) failed: %s", dlerror());
        return 0;
    }

    dlerror();
    TfLiteXNNPackDelegateOptionsDefault_t optionsDefault =
        (TfLiteXNNPackDelegateOptionsDefault_t) dlsym(lib, "TfLiteXNNPackDelegateOptionsDefault");
    TfLiteXNNPackDelegateCreate_t create =
        (TfLiteXNNPackDelegateCreate_t) dlsym(lib, "TfLiteXNNPackDelegateCreate");
    TfLiteXNNPackDelegateGetThreadPool_t getThreadPool =
        (TfLiteXNNPackDelegateGetThreadPool_t) dlsym(lib, "TfLiteXNNPackDelegateGetThreadPool");
    if (optionsDefault == NULL || create == NULL) {
        LOGE("dlsym failed for XNNPACK delegate entry points: %s", dlerror());
        return 0;
    }

    TaiXnnOptions options = optionsDefault();
    options.num_threads = numThreads > 0 ? numThreads : 1;
    void *delegate = create(&options);
    if (delegate == NULL) {
        LOGE("TfLiteXNNPackDelegateCreate returned null for num_threads=%d", options.num_threads);
        return 0;
    }

    if (getThreadPool != NULL) {
        void *pool = getThreadPool(delegate);
        LOGI("created XNNPACK delegate num_threads=%d threadPool=%s", options.num_threads,
             pool != NULL ? "yes" : "no");
    } else {
        LOGI("created XNNPACK delegate num_threads=%d (GetThreadPool unavailable)", options.num_threads);
    }
    return (jlong) (intptr_t) delegate;
}

JNIEXPORT void JNICALL
Java_com_termux_ai_TaiXnnpackDelegate_nativeDelete(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    if (handle == 0) return;

    void *lib = dlopen("libtensorflowlite_jni.so", RTLD_NOW | RTLD_NOLOAD);
    if (lib == NULL) {
        LOGE("dlopen(libtensorflowlite_jni.so) failed on delete: %s", dlerror());
        return;
    }
    dlerror();
    TfLiteXNNPackDelegateDelete_t deleteFn =
        (TfLiteXNNPackDelegateDelete_t) dlsym(lib, "TfLiteXNNPackDelegateDelete");
    if (deleteFn == NULL) {
        LOGE("dlsym(TfLiteXNNPackDelegateDelete) failed: %s", dlerror());
        return;
    }
    deleteFn((void *) (intptr_t) handle);
}
