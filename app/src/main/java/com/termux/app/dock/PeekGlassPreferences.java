package com.termux.app.dock;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Peek glass's one knob, Reflection: how strongly the icons' reflections show on the card, 0
 * (off) to {@link PeekGlassGeometry#REFLECTION_MAX}, {@link PeekGlassGeometry#REFLECTION_DEFAULT}
 * until it is moved. One value for the launcher, in its own preferences file beside the dock's
 * other keys; the Dock card's Reflection slider writes it and the dock reads it.
 */
public final class PeekGlassPreferences {

    /** The stored strength, an int; absent is {@link PeekGlassGeometry#REFLECTION_DEFAULT}. */
    public static final String KEY_REFLECTION = "dock_peek_reflection";

    private PeekGlassPreferences() {}

    /** The Reflection knob as stored, held to its range; the default where nothing is stored. */
    public static int reflection(@Nullable SharedPreferences store) {
        if (store == null) return PeekGlassGeometry.REFLECTION_DEFAULT;
        try {
            return PeekGlassGeometry.clampReflection(
                store.getInt(KEY_REFLECTION, PeekGlassGeometry.REFLECTION_DEFAULT));
        } catch (ClassCastException e) {
            return PeekGlassGeometry.REFLECTION_DEFAULT;
        }
    }

    /** Stores the Reflection knob, held to its range; nothing is written for an unchanged value. */
    public static void setReflection(@Nullable SharedPreferences store, int value) {
        if (store == null) return;
        int clamped = PeekGlassGeometry.clampReflection(value);
        if (store.contains(KEY_REFLECTION) && reflection(store) == clamped) return;
        store.edit().putInt(KEY_REFLECTION, clamped).apply();
    }
}
