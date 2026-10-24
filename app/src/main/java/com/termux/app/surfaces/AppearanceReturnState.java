package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.activities.SettingsBackStackState;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Where the person left the Appearance editor, as plain data: the page (the editor, or the
 * wallpaper picker over it), the card that was up, and the wallpaper card the picker had
 * centred, with the moment it was left.
 *
 * <p>The surface writes it whenever the person leaves (Done, Back, Home) and reads it on the next
 * open through a door that names no object: within Settings' own window
 * ({@link SettingsBackStackState#RETAIN_WINDOW_MS}) the editor opens with that card up. A
 * configuration relaunch carries it too, with the session. States written before the editor's
 * pages merged (Look, Layout, Icon pack, the Overview) read as the editor with no card.</p>
 *
 * <p>No Android classes beyond annotations, so the tests run on the JVM.</p>
 */
public final class AppearanceReturnState {

    /** The preferences key the surface keeps the state under. */
    public static final String PREFS_KEY = "appearance_return_state_v1";

    private static final String JSON_PAGE = "page";
    private static final String JSON_CARD = "card";
    private static final String JSON_WALLPAPER_SLOT = "wallpaper_slot";
    private static final String JSON_LEFT_AT = "left_at_epoch_ms";

    /** Whether the wallpaper picker stood over the editor (the Wallpaper row's Change). */
    public final boolean wallpaper;
    /** The card that was up ({@link EditorCard#key}), or null for none. */
    @Nullable public final String card;
    /** The wallpaper card the picker had centred, or null where it never showed. */
    @Nullable public final String wallpaperSlot;
    /** When the person left, wall-clock milliseconds; 0 for unknown, which is never fresh. */
    public final long leftAtEpochMs;

    private AppearanceReturnState(boolean wallpaper, @Nullable String card,
                                  @Nullable String wallpaperSlot, long leftAtEpochMs) {
        this.wallpaper = wallpaper;
        this.card = blankToNull(card);
        this.wallpaperSlot = blankToNull(wallpaperSlot);
        this.leftAtEpochMs = leftAtEpochMs;
    }

    /** What the surface and the editor fill in as the person leaves. */
    public static final class Builder {
        private final boolean mWallpaper;
        @Nullable private String mCard;
        @Nullable private String mWallpaperSlot;

        /** {@code wallpaper}: the picker stood over the editor. */
        public Builder(boolean wallpaper) {
            mWallpaper = wallpaper;
        }

        /** The card that is up, or null. */
        @NonNull
        public Builder card(@Nullable EditorCard card) {
            mCard = card == null ? null : card.key;
            return this;
        }

        /** The picker's centred wallpaper card. */
        @NonNull
        public Builder wallpaperSlot(@Nullable String slot) {
            mWallpaperSlot = slot;
            return this;
        }

        @NonNull
        public AppearanceReturnState build(long leftAtEpochMs) {
            return new AppearanceReturnState(mWallpaper, mCard, mWallpaperSlot, leftAtEpochMs);
        }
    }

    /** The card to raise again, or null. */
    @Nullable
    public EditorCard editorCard() {
        return EditorCard.forKey(card);
    }

    /**
     * Whether {@code nowEpochMs} is within the window of the moment the person left. A moment in
     * the future (the clock was moved back) is not trusted.
     */
    public boolean isFresh(long nowEpochMs) {
        if (leftAtEpochMs <= 0)
            return false;
        long elapsed = nowEpochMs - leftAtEpochMs;
        return elapsed >= 0 && elapsed < SettingsBackStackState.RETAIN_WINDOW_MS;
    }

    @NonNull
    public String serialize() {
        JSONObject root = new JSONObject();
        try {
            // The name the state has always been written under: the editor, or the picker.
            root.put(JSON_PAGE, wallpaper ? "WALLPAPER" : "EDITOR");
            root.put(JSON_LEFT_AT, leftAtEpochMs);
            if (card != null) root.put(JSON_CARD, card);
            if (wallpaperSlot != null) root.put(JSON_WALLPAPER_SLOT, wallpaperSlot);
        } catch (JSONException ignored) {
            // Strings and numbers: JSONObject#put never rejects them.
        }
        return root.toString();
    }

    /**
     * A state saved by {@link #serialize}, or null when {@code raw} is empty or malformed. The
     * pages before the merge read as the editor; the wallpaper page of then (the Overview) too.
     */
    @Nullable
    public static AppearanceReturnState parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty())
            return null;
        try {
            JSONObject root = new JSONObject(raw);
            Boolean wallpaper = pickerNamed(root.optString(JSON_PAGE, ""));
            if (wallpaper == null)
                return null;
            return new AppearanceReturnState(wallpaper,
                root.has(JSON_CARD) ? root.optString(JSON_CARD, null) : null,
                root.has(JSON_WALLPAPER_SLOT) ? root.optString(JSON_WALLPAPER_SLOT, null) : null,
                root.optLong(JSON_LEFT_AT, 0L));
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Whether a page name stood for the picker over the editor, the merged pages' old names read
     * as the editor; null for a name this build does not know.
     */
    @Nullable
    static Boolean pickerNamed(@NonNull String name) {
        switch (name) {
            case "EDITOR":
            case "LOOK":
            case "LAYOUT":
            case "ICONS":
            case "OVERVIEW":
                return Boolean.FALSE;
            case "WALLPAPER":
                return Boolean.TRUE;
            default:
                return null;
        }
    }

    /** {@link #parse}, kept only while it is fresh at {@code nowEpochMs}; else null. */
    @Nullable
    public static AppearanceReturnState freshFrom(@Nullable String raw, long nowEpochMs) {
        AppearanceReturnState state = parse(raw);
        return state != null && state.isFresh(nowEpochMs) ? state : null;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
