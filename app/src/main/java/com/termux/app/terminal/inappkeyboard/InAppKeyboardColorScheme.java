package com.termux.app.terminal.inappkeyboard;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import juloo.keyboard2.Keyboard2View;

/** Persisted swatches and role assignments for the per-key keyboard color editor. */
public final class InAppKeyboardColorScheme {

    public static final int BASE16_COLOR_COUNT = 16;
    public static final int BASE24_COLOR_COUNT = 24;
    private static final Pattern BASE_COLOR_LINE = Pattern.compile(
        "(?im)^\\s*[\\\"']?base([0-1][0-9a-f])[\\\"']?\\s*[:=]\\s*[\\\"']?#?([0-9a-f]{6})(?:[0-9a-f]{2})?[\\\"']?\\s*,?\\s*(?:#.*)?$");
    private static final Pattern TINTED8_COLOR_LINE = Pattern.compile(
        "(?im)^\\s*(black|white|red|yellow|green|cyan|blue|magenta|orange|gray)(?:-(bright|dim))?\\s*:\\s*[\\\"']?#?([0-9a-f]{6})[\\\"']?\\s*(?:#.*)?$");

    public enum Role { KEY_BACKGROUND, KEY_BORDER, PRIMARY, SECONDARY, SECONDARY_BOTTOM }

    private static final String JSON_SWATCHES = "swatches";
    private static final String JSON_KEYS = "keys";
    private static final String JSON_BASE16_PALETTE = "base16Palette";
    private static final String JSON_IMPORTED_THEME_ID = "importedThemeId";
    private static final String JSON_BACKGROUND = "bg";
    private static final String JSON_BORDER = "border";
    private static final String JSON_PRIMARY = "primary";
    private static final String JSON_SECONDARY = "secondary";
    private static final String JSON_SECONDARY_BOTTOM = "secondaryBottom";

    private final int[] mSwatches;
    private final Map<String, Assignment> mAssignments = new LinkedHashMap<>();
    private boolean mBase16Palette;
    private String mImportedThemeId = "";

    private InAppKeyboardColorScheme(@NonNull int[] swatches) {
        mSwatches = swatches.clone();
    }

    @NonNull
    public static InAppKeyboardColorScheme fromJson(@NonNull Context context, String json) {
        int[] defaults = InAppKeyboardPaletteFactory.defaultEditorSwatches(context);
        InAppKeyboardColorScheme scheme = new InAppKeyboardColorScheme(defaults);
        if (json == null || json.trim().isEmpty())
            return scheme;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray swatches = root.optJSONArray(JSON_SWATCHES);
            if (swatches != null && swatches.length() > 0) {
                // Older builds stored six colors. Copying the common prefix preserves every old
                // assignment while the remaining Base16 slots inherit current Material colors.
                for (int i = 0; i < Math.min(defaults.length, swatches.length()); i++)
                    scheme.mSwatches[i] = swatches.getInt(i);
            }
            JSONObject keys = root.optJSONObject(JSON_KEYS);
            scheme.mBase16Palette = root.optBoolean(JSON_BASE16_PALETTE, false);
            scheme.mImportedThemeId = root.optString(JSON_IMPORTED_THEME_ID, "");
            if (keys != null) {
                Iterator<String> ids = keys.keys();
                while (ids.hasNext()) {
                    String id = ids.next();
                    JSONObject value = keys.optJSONObject(id);
                    if (value == null) continue;
                    Assignment assignment = new Assignment(
                        validIndex(value.optInt(JSON_BACKGROUND, -1), defaults.length),
                        validIndex(value.optInt(JSON_BORDER, -1), defaults.length),
                        validIndex(value.optInt(JSON_PRIMARY, -1), defaults.length),
                        validIndex(value.optInt(JSON_SECONDARY, -1), defaults.length),
                        validIndex(value.optInt(JSON_SECONDARY_BOTTOM, -1), defaults.length));
                    if (!assignment.isEmpty()) scheme.mAssignments.put(id, assignment);
                }
            }
        } catch (JSONException ignored) {
            // A corrupt or old value must never make the keyboard settings page unusable.
        }
        return scheme;
    }

    private static int validIndex(int value, int count) {
        return value >= 0 && value < count ? value : -1;
    }

    public int swatchCount() { return mSwatches.length; }

    @ColorInt
    public int getSwatch(int index) { return mSwatches[index]; }

    public void setSwatch(int index, @ColorInt int color) { mSwatches[index] = color; }

    public boolean hasImportedPalette() { return mBase16Palette; }

    @NonNull
    public String getImportedThemeId() { return mImportedThemeId; }

    public void setImportedThemeId(@Nullable String themeId) {
        mImportedThemeId = themeId == null ? "" : themeId.trim();
    }

    /** Legacy imported palettes had no ID and remain enabled after upgrade. */
    public boolean shouldApplyImportedPalette(@Nullable String selectedTheme) {
        return "custom".equals(selectedTheme) || (mBase16Palette && mImportedThemeId.isEmpty());
    }

    /** Imports a Base16 or Base24 palette from Tinted Theming YAML/JSON-style text. */
    public boolean importBasePalette(@NonNull String text, int colorCount) {
        if (colorCount != BASE16_COLOR_COUNT && colorCount != BASE24_COLOR_COUNT)
            return false;
        int[] imported = new int[colorCount];
        boolean[] found = new boolean[colorCount];
        Matcher matcher = BASE_COLOR_LINE.matcher(text);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1), 16);
            if (index >= colorCount) continue;
            imported[index] = (int) (0xFF000000L | Long.parseLong(matcher.group(2), 16));
            found[index] = true;
        }
        for (boolean present : found) {
            if (!present) return false;
        }
        if (mSwatches.length < colorCount) return false;
        System.arraycopy(imported, 0, mSwatches, 0, colorCount);
        mBase16Palette = true;
        return true;
    }

    /** Backward-compatible Base16 entry point used by existing callers and persisted schemes. */
    public boolean importBase16(@NonNull String text) {
        return importBasePalette(text, BASE16_COLOR_COUNT);
    }

    /** Maps Tinted8's named ANSI palette into the keyboard's Base16-compatible semantic slots. */
    public boolean importTinted8(@NonNull String text) {
        Map<String, Integer> colors = new HashMap<>();
        Matcher matcher = TINTED8_COLOR_LINE.matcher(text);
        while (matcher.find()) {
            String variant = matcher.group(2);
            String key = matcher.group(1).toLowerCase() +
                (variant == null ? "" : "-" + variant.toLowerCase());
            colors.put(key, (int) (0xFF000000L | Long.parseLong(matcher.group(3), 16)));
        }
        String[] required = {"black", "white", "red", "yellow", "green", "cyan", "blue", "magenta"};
        for (String key : required) {
            if (!colors.containsKey(key)) return false;
        }
        if (mSwatches.length < BASE16_COLOR_COUNT) return false;
        int black = colors.get("black");
        int white = colors.get("white");
        int gray = colorOr(colors, "gray", blend(black, white));
        int orange = colorOr(colors, "orange", colors.get("yellow"));
        int[] imported = {
            black,
            colorOr(colors, "black-dim", black),
            gray,
            colorOr(colors, "gray-dim", gray),
            colorOr(colors, "white-dim", white),
            white,
            colorOr(colors, "gray-bright", white),
            colorOr(colors, "white-bright", white),
            colors.get("red"), orange, colors.get("yellow"), colors.get("green"),
            colors.get("cyan"), colors.get("blue"), colors.get("magenta"),
            colorOr(colors, "red-bright", colors.get("red"))
        };
        System.arraycopy(imported, 0, mSwatches, 0, imported.length);
        mBase16Palette = true;
        return true;
    }

    private static int colorOr(@NonNull Map<String, Integer> colors, @NonNull String key,
                               int fallback) {
        Integer color = colors.get(key);
        return color == null ? fallback : color;
    }

    private static int blend(int first, int second) {
        int red = (ColorIntChannel.red(first) + ColorIntChannel.red(second)) / 2;
        int green = (ColorIntChannel.green(first) + ColorIntChannel.green(second)) / 2;
        int blue = (ColorIntChannel.blue(first) + ColorIntChannel.blue(second)) / 2;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static final class ColorIntChannel {
        static int red(int color) { return (color >> 16) & 0xFF; }
        static int green(int color) { return (color >> 8) & 0xFF; }
        static int blue(int color) { return color & 0xFF; }
    }

    /** Applies an imported Base16 scheme to the keyboard's semantic palette roles. */
    @NonNull
    public juloo.keyboard2.Theme.Palette applyToPalette(
        @NonNull juloo.keyboard2.Theme.Palette base) {
        if (!mBase16Palette || mSwatches.length < BASE16_COLOR_COUNT) return base;
        return new juloo.keyboard2.Theme.Palette(
            preserveAlpha(mSwatches[0x0], base.keyboardBackground),
            preserveAlpha(mSwatches[0x1], base.keyBackground),
            preserveAlpha(mSwatches[0x2], base.actionKeyBackground),
            preserveAlpha(mSwatches[0x2], base.spaceBarBackground),
            preserveAlpha(mSwatches[0xD], base.activatedKeyBackground),
            mSwatches[0x5], mSwatches[0x4], mSwatches[0x7], mSwatches[0xC],
            mSwatches[0xE], preserveAlpha(mSwatches[0x3], base.borderColor),
            base.borderEnabled, base.borderWidth, base.borderRadius, base.opacity,
            base.secondaryDimming, base.greyedDimming, mSwatches[0x6], mSwatches[0x4],
            new int[] {mSwatches[0x8], mSwatches[0x9], mSwatches[0xA], mSwatches[0xB],
                mSwatches[0xC], mSwatches[0xD], mSwatches[0xE], mSwatches[0xF]},
            base.keyGradientTopOverlay, base.keyGradientBottomOverlay);
    }

    private static int preserveAlpha(int color, int alphaSource) {
        return (color & 0x00FFFFFF) | (alphaSource & 0xFF000000);
    }

    public void paint(@NonNull String keyId, @NonNull Role role, int swatchIndex) {
        if (swatchIndex < 0 || swatchIndex >= mSwatches.length)
            throw new IllegalArgumentException("Invalid swatch index");
        Assignment old = mAssignments.get(keyId);
        Assignment next = old == null ? new Assignment() : old.copy();
        switch (role) {
            case KEY_BACKGROUND: next.background = swatchIndex; break;
            case KEY_BORDER: next.border = swatchIndex; break;
            case PRIMARY: next.primary = swatchIndex; break;
            case SECONDARY: next.secondary = swatchIndex; break;
            case SECONDARY_BOTTOM: next.secondaryBottom = swatchIndex; break;
        }
        mAssignments.put(keyId, next);
    }

    @NonNull
    public Map<String, Keyboard2View.KeyColorOverride> resolvedOverrides() {
        Map<String, Keyboard2View.KeyColorOverride> result = new HashMap<>();
        for (Map.Entry<String, Assignment> entry : mAssignments.entrySet()) {
            Assignment a = entry.getValue();
            result.put(entry.getKey(), new Keyboard2View.KeyColorOverride(
                resolve(a.background), resolve(a.primary), resolve(a.secondary),
                resolve(a.secondaryBottom), resolve(a.border)));
        }
        return result;
    }

    private Integer resolve(int index) {
        return index < 0 ? null : mSwatches[index];
    }

    @NonNull
    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put(JSON_BASE16_PALETTE, mBase16Palette);
            root.put(JSON_IMPORTED_THEME_ID, mImportedThemeId);
            JSONArray swatches = new JSONArray();
            for (int color : mSwatches) swatches.put(color);
            root.put(JSON_SWATCHES, swatches);
            JSONObject keys = new JSONObject();
            for (Map.Entry<String, Assignment> entry : mAssignments.entrySet()) {
                Assignment a = entry.getValue();
                JSONObject value = new JSONObject();
                if (a.background >= 0) value.put(JSON_BACKGROUND, a.background);
                if (a.border >= 0) value.put(JSON_BORDER, a.border);
                if (a.primary >= 0) value.put(JSON_PRIMARY, a.primary);
                if (a.secondary >= 0) value.put(JSON_SECONDARY, a.secondary);
                if (a.secondaryBottom >= 0)
                    value.put(JSON_SECONDARY_BOTTOM, a.secondaryBottom);
                keys.put(entry.getKey(), value);
            }
            root.put(JSON_KEYS, keys);
            return root.toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Assignment {
        int background = -1;
        int border = -1;
        int primary = -1;
        int secondary = -1;
        int secondaryBottom = -1;

        Assignment() {}

        Assignment(int background, int border, int primary, int secondary,
                   int secondaryBottom) {
            this.background = background;
            this.border = border;
            this.primary = primary;
            this.secondary = secondary;
            this.secondaryBottom = secondaryBottom;
        }

        Assignment copy() {
            return new Assignment(background, border, primary, secondary, secondaryBottom);
        }

        boolean isEmpty() {
            return background < 0 && border < 0 && primary < 0 && secondary < 0
                && secondaryBottom < 0;
        }
    }
}
