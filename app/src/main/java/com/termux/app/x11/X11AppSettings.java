package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one Linux app may set differently from the display: its touch mode, scale, GPU profile and paste shortcut. Absent
 * means follow the display's setting. The values are the global preference's own: touch modes
 * match the {@code touchMode} pref, scales are the strings the display's scale picker offers.
 *
 * <p>Stored as one JSON object, {@code {"<appId>": {"touch_mode": "2", "scale": "1.5"}}}, keyed by
 * {@link LinuxAppCatalog.LinuxApp#id}. The codec is tolerant: anything it does not recognise is
 * dropped and it never throws.
 */
public final class X11AppSettings {

    public static final String TOUCH_TRACKPAD = "1";
    public static final String TOUCH_TOUCH = "2";
    public static final String TOUCH_DIRECT = "3";

    /** Every scale an app can ask for. */
    public static final List<String> SCALES = Collections.unmodifiableList(
        Arrays.asList("1", "1.25", "1.5", "2", "2.5", "3"));

    private static final String KEY_TOUCH_MODE = "touch_mode";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_GPU = "gpu";
    private static final String KEY_PASTE = "paste";

    /** The paste key presses Ctrl+V. */
    public static final String PASTE_CTRL_V = "ctrl_v";
    /** The paste key presses Ctrl+Shift+V, which terminal-style apps paste with. */
    public static final String PASTE_CTRL_SHIFT_V = "ctrl_shift_v";

    private X11AppSettings() {}

    public static boolean isTouchMode(@Nullable String value) {
        return TOUCH_TRACKPAD.equals(value) || TOUCH_TOUCH.equals(value) || TOUCH_DIRECT.equals(value);
    }

    public static boolean isPasteShortcut(@Nullable String value) {
        return PASTE_CTRL_V.equals(value) || PASTE_CTRL_SHIFT_V.equals(value);
    }

    public static boolean isScale(@Nullable String value) {
        return value != null && SCALES.contains(value);
    }

    /**
     * A per-app GPU profile id: any profile but software, which keeps its own switch
     * ({@link X11GpuProfileStore#drawsInSoftware}) so choices made before this existed still read.
     */
    public static boolean isGpuProfile(@Nullable String value) {
        X11GpuProbe.Profile profile = X11GpuProbe.Profile.of(value);
        return profile != null && profile != X11GpuProbe.Profile.SOFTWARE;
    }

    /** One app's overrides; a null field follows the display. Immutable. */
    public static final class Overrides {
        @Nullable public final String touchMode;
        @Nullable public final String scale;
        /** A GPU profile id that replaces the display's pick for this app; null follows it. */
        @Nullable public final String gpu;

        public Overrides(@Nullable String touchMode, @Nullable String scale) {
            this(touchMode, scale, null);
        }

        /** A paste shortcut that replaces the display's for this app; null follows it. */
        @Nullable public final String pasteShortcut;

        public Overrides(@Nullable String touchMode, @Nullable String scale, @Nullable String gpu) {
            this(touchMode, scale, gpu, null);
        }

        public Overrides(@Nullable String touchMode, @Nullable String scale, @Nullable String gpu,
                         @Nullable String pasteShortcut) {
            this.pasteShortcut = isPasteShortcut(pasteShortcut) ? pasteShortcut : null;
            this.touchMode = isTouchMode(touchMode) ? touchMode : null;
            this.scale = isScale(scale) ? scale : null;
            this.gpu = isGpuProfile(gpu) ? gpu : null;
        }

        public boolean isEmpty() {
            return touchMode == null && scale == null && gpu == null
                && pasteShortcut == null;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Overrides)) return false;
            Overrides other = (Overrides) o;
            return Objects.equals(touchMode, other.touchMode) && Objects.equals(scale, other.scale)
                && Objects.equals(gpu, other.gpu)
                && Objects.equals(pasteShortcut, other.pasteShortcut);
        }

        @Override public int hashCode() {
            return Objects.hash(touchMode, scale, gpu, pasteShortcut);
        }
    }

    /** The overrides in {@code json}, empty entries left out; empty for null, blank or garbage. */
    @NonNull
    public static Map<String, Overrides> parse(@Nullable String json) {
        Map<String, Overrides> out = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                JSONObject entry = root.optJSONObject(id);
                if (entry == null || id.isEmpty()) continue;
                Overrides overrides = new Overrides(
                    entry.optString(KEY_TOUCH_MODE, null), entry.optString(KEY_SCALE, null),
                    entry.optString(KEY_GPU, null), entry.optString(KEY_PASTE, null));
                if (!overrides.isEmpty()) out.put(id, overrides);
            }
        } catch (JSONException | RuntimeException e) {
            // A damaged blob reads as nothing set.
            return new LinkedHashMap<>();
        }
        return out;
    }

    /** {@code overrides} as JSON, entries with nothing set omitted. */
    @NonNull
    public static String encode(@NonNull Map<String, Overrides> overrides) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, Overrides> entry : overrides.entrySet()) {
                Overrides value = entry.getValue();
                if (value == null || value.isEmpty()) continue;
                JSONObject item = new JSONObject();
                if (value.touchMode != null) item.put(KEY_TOUCH_MODE, value.touchMode);
                if (value.scale != null) item.put(KEY_SCALE, value.scale);
                if (value.gpu != null) item.put(KEY_GPU, value.gpu);
                if (value.pasteShortcut != null) item.put(KEY_PASTE, value.pasteShortcut);
                root.put(entry.getKey(), item);
            }
        } catch (JSONException ignored) {
            // Keys and values are plain strings; put cannot fail.
        }
        return root.toString();
    }
}
