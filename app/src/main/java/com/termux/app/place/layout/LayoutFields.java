package com.termux.app.place.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable bag of named values: one section of a layout document. A value is a String, a
 * Boolean, a Number, or a JSON value this build does not know (kept as read, so a rewrite does
 * not lose a field a newer build wrote). An absent field reads its shipped default, which the
 * reader names, exactly as an absent flat key did before ADR 0020.
 */
public final class LayoutFields {

    public static final LayoutFields EMPTY = new LayoutFields(Collections.emptyMap());

    @NonNull private final Map<String, Object> mValues;

    private LayoutFields(@NonNull Map<String, Object> values) {
        mValues = values;
    }

    @NonNull
    public static LayoutFields of(@NonNull Map<String, ?> values) {
        if (values.isEmpty()) return EMPTY;
        return new LayoutFields(Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }

    public boolean has(@NonNull String key) {
        return mValues.containsKey(key);
    }

    @Nullable
    public Object get(@NonNull String key) {
        return mValues.get(key);
    }

    @Nullable
    public String string(@NonNull String key) {
        Object value = mValues.get(key);
        return value instanceof String ? (String) value : null;
    }

    public boolean bool(@NonNull String key, boolean fallback) {
        Object value = mValues.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public int integer(@NonNull String key, int fallback) {
        Object value = mValues.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    public float decimal(@NonNull String key, float fallback) {
        Object value = mValues.get(key);
        if (!(value instanceof Number)) return fallback;
        float f = ((Number) value).floatValue();
        return Float.isNaN(f) || Float.isInfinite(f) ? fallback : f;
    }

    /** A copy with {@code key} set to {@code value}; a null value removes it. */
    @NonNull
    public LayoutFields with(@NonNull String key, @Nullable Object value) {
        if (value == null) return without(key);
        if (mValues.containsKey(key) && sameValue(mValues.get(key), value)
            && mValues.get(key).getClass() == value.getClass()) return this;
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(mValues);
        copy.put(key, value);
        return new LayoutFields(Collections.unmodifiableMap(copy));
    }

    @NonNull
    public LayoutFields without(@NonNull String key) {
        if (!mValues.containsKey(key)) return this;
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(mValues);
        copy.remove(key);
        return copy.isEmpty() ? EMPTY : new LayoutFields(Collections.unmodifiableMap(copy));
    }

    @NonNull
    public Set<String> keys() {
        return mValues.keySet();
    }

    @NonNull
    public Map<String, Object> asMap() {
        return mValues;
    }

    /** Whether two values are the same, numbers compared by value whatever their boxing. */
    public static boolean sameValue(@Nullable Object a, @Nullable Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Math.abs(((Number) a).doubleValue() - ((Number) b).doubleValue()) < 1e-6;
        }
        if (a != null && b != null && !(a instanceof String || a instanceof Boolean)) {
            return a.toString().equals(b.toString());
        }
        return Objects.equals(a, b);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LayoutFields)) return false;
        Map<String, Object> other = ((LayoutFields) o).mValues;
        if (!other.keySet().equals(mValues.keySet())) return false;
        for (Map.Entry<String, Object> e : mValues.entrySet()) {
            if (!sameValue(e.getValue(), other.get(e.getKey()))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return mValues.keySet().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return mValues.toString();
    }
}
