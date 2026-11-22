package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

/** Debug-only door to the package-private {@link SileroVad} for {@code com.termux.ai.AuxBench}. */
public final class SileroVadBench {

    private SileroVadBench() {
    }

    /** The app's own Silero instance (one thread, stock XNNPACK path), built from a direct buffer holding the tflite. */
    @NonNull
    public static SileroVoiceDecider.ProbabilitySource open(@NonNull ByteBuffer model) {
        return SileroVad.load(model);
    }
}
