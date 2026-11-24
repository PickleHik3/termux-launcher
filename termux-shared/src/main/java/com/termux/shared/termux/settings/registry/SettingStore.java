package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The few operations the {@link SettingsRegistry} needs of the store a setting already lives in:
 * the app's shared preferences or {@code termux.properties} on a device, an
 * {@link InMemorySettingStore} in tests. A read of a key holding another type answers the default
 * rather than throwing: a hand-edited or older value must not crash home.
 */
public interface SettingStore {

    /** The stored text under {@code key}, or null when nothing is stored there. */
    @Nullable
    String getString(@NonNull String key);

    int getInt(@NonNull String key, int defaultValue);

    boolean getBoolean(@NonNull String key, boolean defaultValue);

    void putString(@NonNull String key, @NonNull String value);

    void putInt(@NonNull String key, int value);

    void putBoolean(@NonNull String key, boolean value);

    /** The store a {@link SettingDeclaration} names. */
    interface Stores {
        @NonNull
        SettingStore store(@NonNull SettingDeclaration.Store which);
    }
}
