package com.termux.app.terminal.extrakeys;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Names one key within a page so the same key can be found again in another key set: its value, the
 * value of its swipe-up key, and which of the keys sharing that pair it is.
 */
public final class KeyId {

    @NonNull public final String key;
    @Nullable public final String second;
    public final int occurrence;

    public KeyId(@Nullable String key, @Nullable String second, int occurrence) {
        this.key = key == null ? "" : key;
        this.second = second;
        this.occurrence = occurrence;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KeyId)) return false;
        KeyId o = (KeyId) other;
        if (occurrence != o.occurrence || !key.equals(o.key)) return false;
        return second == null ? o.second == null : second.equals(o.second);
    }

    @Override
    public int hashCode() {
        int h = key.hashCode();
        h = 31 * h + (second == null ? 0 : second.hashCode());
        return 31 * h + occurrence;
    }

    @NonNull
    @Override
    public String toString() {
        return key + (second == null ? "" : "/" + second) + "#" + occurrence;
    }
}
