// A slice read of a LiteRT 2.x TensorBuffer (com.termux.ai.TaiTensorRead).
//
// LiteRT 2.3.2's Kotlin TensorBuffer.readFloat() copies the whole tensor into a new float[]
// every call. Whisper's decoder output is [1, 128, vocab] floats (about 26 MB) and the decode
// loop needs one 51865-float row of it per token. This locks the buffer through libLiteRt.so's
// C API, copies only the asked-for slice into the caller's array and unlocks it.
//
// The handle the Kotlin API hands out (TensorBuffer.getHandle) is a `litert::TensorBuffer*`
// (litert_compiled_model_jni.cc: `new TensorBuffer(std::move(*tensor_buffer))`). That class has
// no virtual members and derives only from `internal::BaseHandle<LiteRtTensorBuffer>`, whose
// first member is `std::unique_ptr<LiteRtTensorBufferT, std::function<...>>`; under libc++ the
// pointer is the unique_ptr's first word. So the C handle is the first word of the object
// (LiteRT v2.3.2: litert/cc/litert_tensor_buffer.h, litert/cc/internal/litert_handle.h).
// The caller checks the packed size against the tensor's before trusting a handle.
//
// The C functions are looked up in the already loaded libLiteRt.so with dlsym; nothing links
// against it, and every lookup failure leaves the Java side on readFloat().
#include <dlfcn.h>
#include <stddef.h>
#include <stdint.h>
#include <jni.h>

typedef int LiteRtStatus; // kLiteRtStatusOk == 0
typedef void *LiteRtTensorBuffer;
// kLiteRtTensorBufferLockModeRead (litert/c/litert_common.h, v2.3.2).
#define TAI_LOCK_MODE_READ 0

typedef LiteRtStatus (*LockFn)(LiteRtTensorBuffer, void **, int);
typedef LiteRtStatus (*UnlockFn)(LiteRtTensorBuffer);
typedef LiteRtStatus (*PackedSizeFn)(LiteRtTensorBuffer, size_t *);

static LockFn g_lock;
static UnlockFn g_unlock;
static PackedSizeFn g_packed_size;

JNIEXPORT jboolean JNICALL
Java_com_termux_ai_TaiTensorRead_nativeInit(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    if (g_lock != NULL && g_unlock != NULL && g_packed_size != NULL) return JNI_TRUE;
    void *lib = dlopen("libLiteRt.so", RTLD_NOW | RTLD_NOLOAD);
    if (lib == NULL) lib = dlopen("libLiteRt.so", RTLD_NOW);
    if (lib == NULL) return JNI_FALSE;
    LockFn lock = (LockFn) dlsym(lib, "LiteRtLockTensorBuffer");
    UnlockFn unlock = (UnlockFn) dlsym(lib, "LiteRtUnlockTensorBuffer");
    PackedSizeFn packed = (PackedSizeFn) dlsym(lib, "LiteRtGetTensorBufferPackedSize");
    if (lock == NULL || unlock == NULL || packed == NULL) return JNI_FALSE;
    g_lock = lock;
    g_unlock = unlock;
    g_packed_size = packed;
    return JNI_TRUE;
}

static LiteRtTensorBuffer c_handle(jlong handle) {
    if (handle == 0) return NULL;
    return *(LiteRtTensorBuffer *) (intptr_t) handle;
}

// The buffer's packed size in bytes, or -1 when it cannot be read.
JNIEXPORT jlong JNICALL
Java_com_termux_ai_TaiTensorRead_nativePackedBytes(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    LiteRtTensorBuffer buffer = c_handle(handle);
    if (g_packed_size == NULL || buffer == NULL) return -1;
    size_t size = 0;
    if (g_packed_size(buffer, &size) != 0) return -1;
    return (jlong) size;
}

// Copies `count` floats from element `offset` into `into[0..count)`. Returns false when the
// buffer could not be locked or the slice lies outside it; `into` is then left as it was.
JNIEXPORT jboolean JNICALL
Java_com_termux_ai_TaiTensorRead_nativeReadFloats(JNIEnv *env, jclass clazz, jlong handle, jint offset,
                                                  jfloatArray into, jint count) {
    (void) clazz;
    LiteRtTensorBuffer buffer = c_handle(handle);
    if (g_lock == NULL || g_unlock == NULL || g_packed_size == NULL || buffer == NULL) return JNI_FALSE;
    if (offset < 0 || count < 0 || (*env)->GetArrayLength(env, into) < count) return JNI_FALSE;
    size_t size = 0;
    if (g_packed_size(buffer, &size) != 0) return JNI_FALSE;
    if (((size_t) offset + (size_t) count) * sizeof(float) > size) return JNI_FALSE;
    void *host = NULL;
    if (g_lock(buffer, &host, TAI_LOCK_MODE_READ) != 0 || host == NULL) return JNI_FALSE;
    (*env)->SetFloatArrayRegion(env, into, 0, count, ((const jfloat *) host) + offset);
    g_unlock(buffer);
    return JNI_TRUE;
}
