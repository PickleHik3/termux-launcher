package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Where a speech-to-text graph runs: {@link #AUTO} (CompiledModel on the CPU, the same as
 * {@link #CPU}; see {@link SttGraphChooser}) or {@link #GPU} (a manual opt-in, with a fallback to
 * CPU) on LiteRT's CompiledModel, or {@link #INTERPRETER}, the stock Interpreter path the runtimes used before
 * (kept for the bench to compare against, not offered in settings).
 *
 * <p>The runtime process keeps the value the app process last sent with a speech request in
 * {@link #requested()} (TaiManager carries it like the STT idle limit); the speech runtimes read
 * it at each load.
 */
enum SttAccelerator {
    AUTO("auto"), CPU("cpu"), GPU("gpu"), INTERPRETER("interp");

    @NonNull final String id;

    SttAccelerator(@NonNull String id) {
        this.id = id;
    }

    private static volatile SttAccelerator requested = AUTO;

    /** {@code value} as an accelerator; anything unknown, or none, is {@link #AUTO}. */
    @NonNull
    static SttAccelerator parse(@Nullable String value) {
        if (value == null) return AUTO;
        String id = value.trim().toLowerCase(Locale.ROOT);
        if ("interpreter".equals(id)) return INTERPRETER;
        for (SttAccelerator accelerator : values()) {
            if (accelerator.id.equals(id)) return accelerator;
        }
        return AUTO;
    }

    /** What the app process last asked for, in the runtime process; {@link #AUTO} until told. */
    @NonNull
    static SttAccelerator requested() {
        return requested;
    }

    static void setRequested(@NonNull SttAccelerator accelerator) {
        requested = accelerator;
    }
}
