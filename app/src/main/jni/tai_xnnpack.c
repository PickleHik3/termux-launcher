// JNI helpers for libtai_xnnpack.so: the native log floor (com.termux.ai.TaiNativeLog).
// The library name is historical; it once held an XNNPACK delegate shim, removed with litert 2.x.
#include <dlfcn.h>
#include <stdint.h>
#include <jni.h>

// Raises this process's native log floor (com.termux.ai.TaiNativeLog).
//
// Upstream MNN (libmnnllmapp.so, built with MNN_DEBUG strings such as "submitNative
// prompt_string_for_debug" and "generateNative: prompt=%s") can print the user's whole prompt
// at DEBUG under its MNN_DEBUG tag. __android_log_set_minimum_priority is API 30+, so it is
// resolved from liblog with dlsym and is a no-op below that. Returns true when the floor was
// set, false when the symbol is missing.
typedef int32_t (*SetMinPriority_t)(int32_t priority);

JNIEXPORT jboolean JNICALL
Java_com_termux_ai_TaiNativeLog_nativeSetMinimumPriority(JNIEnv *env, jclass clazz, jint priority) {
    (void) env;
    (void) clazz;
    void *lib = dlopen("liblog.so", RTLD_NOW);
    if (lib == NULL) return JNI_FALSE;
    SetMinPriority_t setMin = (SetMinPriority_t) dlsym(lib, "__android_log_set_minimum_priority");
    if (setMin == NULL) return JNI_FALSE;
    setMin((int32_t) priority);
    return JNI_TRUE;
}
