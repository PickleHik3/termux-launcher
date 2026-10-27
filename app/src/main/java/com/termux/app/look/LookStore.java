package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * The narrow key-value adapter {@link PageLook} reads and writes through: the main preferences in
 * the app ({@link PrefsLookStore}), a map in tests ({@link MemoryLookStore}).
 */
public interface LookStore {

    /** Told which key changed; a null key means "anything may have changed". */
    interface Listener {
        void onKeyChanged(@Nullable String key);
    }

    int getInt(@NonNull String key, int defaultValue);

    boolean getBoolean(@NonNull String key, boolean defaultValue);

    @NonNull
    String getString(@NonNull String key, @NonNull String defaultValue);

    boolean has(@NonNull String key);

    void putInt(@NonNull String key, int value);

    void putBoolean(@NonNull String key, boolean value);

    void putString(@NonNull String key, @NonNull String value);

    void remove(@NonNull String key);

    /** A copy of every key held. */
    @NonNull
    Set<String> keys();

    void addListener(@NonNull Listener listener);

    void removeListener(@NonNull Listener listener);
}
