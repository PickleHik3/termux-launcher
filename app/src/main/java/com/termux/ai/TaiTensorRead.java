package com.termux.ai;

import android.util.Log;

import com.termux.BuildConfig;

/**
 * A slice read of a LiteRT 2.x {@code TensorBuffer} through {@code libtai_xnnpack.so}: lock the
 * buffer with the C API, copy {@code count} floats from {@code offset}, unlock. The Kotlin API's
 * only read, {@code TensorBuffer.readFloat()}, copies the whole tensor into a new array; for
 * Whisper's {@code [1, 128, vocab]} logits that is about 26 MB per token where 200 KB is needed.
 * See {@code tai_tensor_read.c} for why the Kotlin handle leads to the C handle.
 *
 * <p>{@link #available()} is false when the library or the C symbols are missing, or when the
 * LiteRT in the build is not {@link #VERIFIED_LITERT}, and callers then fall back to
 * {@code readFloat()}. The native read assumes an internal handle layout of that LiteRT version, so
 * a LiteRT bump must re-verify the layout against {@code tai_tensor_read.c} before this constant
 * changes.
 */
final class TaiTensorRead {
    private static final String TAG = "TaiTensorRead";
    /** The only LiteRT version whose TensorBuffer handle layout {@code tai_tensor_read.c} was checked against. */
    static final String VERIFIED_LITERT = "2.3.2";
    private static volatile Boolean available;

    private TaiTensorRead() {
    }

    /** Whether the native slice read is there; looked up once, after libLiteRt.so is loaded. */
    static boolean available() {
        Boolean known = available;
        if (known != null) return known;
        synchronized (TaiTensorRead.class) {
            if (available != null) return available;
            boolean ok;
            if (!isVerifiedLitert(BuildConfig.LITERT_VERSION)) {
                Log.i(TAG, "LiteRT " + BuildConfig.LITERT_VERSION + " is not the verified " + VERIFIED_LITERT
                    + "; whole-tensor reads");
                available = false;
                return false;
            }
            try {
                System.loadLibrary("tai_xnnpack");
                ok = nativeInit();
            } catch (Throwable t) {
                Log.w(TAG, "native slice read unavailable; whole-tensor reads", t);
                ok = false;
            }
            available = ok;
            return ok;
        }
    }

    /** Whether {@code version} is the LiteRT the native handle layout was verified on. */
    static boolean isVerifiedLitert(String version) {
        return VERIFIED_LITERT.equals(version);
    }

    private static native boolean nativeInit();

    /** The buffer's packed size in bytes, or {@code -1} when it cannot be read. */
    static native long nativePackedBytes(long handle);

    /** Copies {@code count} floats from element {@code offset} into {@code into}; false when it could not. */
    static native boolean nativeReadFloats(long handle, int offset, float[] into, int count);
}
