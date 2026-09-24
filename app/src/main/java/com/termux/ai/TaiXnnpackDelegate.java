package com.termux.ai;

import androidx.annotation.Nullable;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.TensorFlowLite;

import android.util.Log;

/**
 * An XNNPACK delegate built by our own tiny JNI shim ({@code libtai_xnnpack.so}), not by
 * litert's {@code Interpreter}. litert 1.4.2's {@code NativeInterpreterWrapper} does pass
 * {@code getNumThreads()}/{@code getUseXNNPACK()} down to its native {@code createInterpreter},
 * but the XNNPACK delegate it builds internally never gets worker threads: simpleperf on pong
 * showed Whisper inference 99.56% on one thread (see {@link WhisperSttRuntime} and
 * {@code project-docs/plans/whisper-voice-input.md}). The native shim dlopens the already-loaded
 * {@code libtensorflowlite_jni.so} and calls its exported {@code TfLiteXNNPackDelegateCreate}
 * directly, which does spawn a pthreadpool.
 *
 * <p>{@link #create} never throws: any failure to load the native shim, or a null/zero handle
 * from it, returns {@code null} so the caller falls back to the stock {@code Interpreter.Options}
 * XNNPACK path.
 */
final class TaiXnnpackDelegate implements Delegate {
    private static final String TAG = "TaiXnnpack";

    private final long handle;
    private boolean closed;

    private TaiXnnpackDelegate(long handle) {
        this.handle = handle;
    }

    /**
     * Builds a delegate with {@code numThreads} XNNPACK worker threads, or {@code null} if the
     * native shim cannot be loaded or the delegate cannot be created — the caller keeps working
     * either way, just without the extra threads.
     */
    @Nullable
    static TaiXnnpackDelegate create(int numThreads) {
        try {
            // TensorFlowLite.init() (present since litert 1.4.2) is the public hook that loads
            // libtensorflowlite_jni.so; our shim's dlopen(..., RTLD_NOLOAD) depends on that
            // library already being resident in the process.
            TensorFlowLite.init();
        } catch (Throwable t) {
            Log.w(TAG, "TensorFlowLite.init() failed; XNNPACK delegate not created", t);
            return null;
        }
        try {
            System.loadLibrary("tai_xnnpack");
        } catch (Throwable t) {
            Log.w(TAG, "libtai_xnnpack.so not available; falling back to stock XNNPACK", t);
            return null;
        }
        long handle;
        try {
            handle = nativeCreate(numThreads);
        } catch (Throwable t) {
            Log.w(TAG, "nativeCreate threw; falling back to stock XNNPACK", t);
            return null;
        }
        if (handle == 0) {
            Log.w(TAG, "nativeCreate returned no delegate; falling back to stock XNNPACK");
            return null;
        }
        return new TaiXnnpackDelegate(handle);
    }

    @Override
    public long getNativeHandle() {
        return handle;
    }

    /** Idempotent; call only after the {@link org.tensorflow.lite.Interpreter} using it is closed. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        nativeDelete(handle);
    }

    private static native long nativeCreate(int numThreads);
    private static native void nativeDelete(long handle);
}
