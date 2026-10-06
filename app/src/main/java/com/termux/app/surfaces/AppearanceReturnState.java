package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.activities.SettingsBackStackState;
import com.termux.app.surfaces.AppearanceSurfaceController.PageId;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Where the person left the Appearance surface, as plain data: the page (Wallpaper, Look, Layout
 * or Icon pack) and what that page comes back to (Look's selected surface and whether it stood
 * at the Custom stop, Layout's place, the Icon pack row's scroll) plus the wallpaper card the
 * Overview had centred, with the moment it was left.
 *
 * <p>The surface writes it to preferences whenever the person leaves (Done, Back out of the
 * surface, Home) and reads it on the next Overview open. Within Settings' own window
 * ({@link SettingsBackStackState#RETAIN_WINDOW_MS}) the Overview opens on the remembered card
 * and the remembered page is queued behind it, so the surface arrives there through its normal
 * hop; after the window, or with nothing readable, it opens as it always has. Nothing transient
 * is kept: no popup, dialog or unsaved edit, since every leave has committed or discarded them.
 * Preferences survive the launcher being killed, which a home screen is, often.</p>
 *
 * <p>No Android classes beyond annotations, so the tests run on the JVM: names are kept as the
 * strings of their enums ({@code AppearanceLooks.Target}, {@code PaneWallPage},
 * {@code WallpaperSlots.Slot}) and resolved by whoever applies them.</p>
 */
public final class AppearanceReturnState {

    /** The preferences key the surface keeps the state under. */
    public static final String PREFS_KEY = "appearance_return_state_v1";

    private static final String JSON_PAGE = "page";
    private static final String JSON_TARGET = "target";
    private static final String JSON_CUSTOM = "custom";
    private static final String JSON_PLACE = "place";
    private static final String JSON_ICONS_SCROLL = "icons_scroll_px";
    private static final String JSON_WALLPAPER_SLOT = "wallpaper_slot";
    private static final String JSON_LEFT_AT = "left_at_epoch_ms";

    /** The page the person was on. */
    @NonNull public final PageId page;
    /** Look only: the selected surface's name, or null for none (the global row). */
    @Nullable public final String target;
    /** Look only: the slider stood at the Custom stop. */
    public final boolean custom;
    /** Layout only: the place the canvas was showing, or null for the place on screen. */
    @Nullable public final String place;
    /** Icon pack only: how far the tile row was scrolled, px. */
    public final int iconsScrollPx;
    /** The wallpaper card the Overview had centred, or null where it never showed. */
    @Nullable public final String wallpaperSlot;
    /** When the person left, wall-clock milliseconds; 0 for unknown, which is never fresh. */
    public final long leftAtEpochMs;

    private AppearanceReturnState(@NonNull PageId page, @Nullable String target, boolean custom,
                                  @Nullable String place, int iconsScrollPx,
                                  @Nullable String wallpaperSlot, long leftAtEpochMs) {
        // Each page keeps only what it comes back to.
        this.page = page;
        this.target = page == PageId.LOOK ? blankToNull(target) : null;
        this.custom = page == PageId.LOOK && custom;
        this.place = page == PageId.LAYOUT ? blankToNull(place) : null;
        this.iconsScrollPx = page == PageId.ICONS ? Math.max(0, iconsScrollPx) : 0;
        this.wallpaperSlot = blankToNull(wallpaperSlot);
        this.leftAtEpochMs = leftAtEpochMs;
    }

    /** What the surface and the editor fill in as the person leaves. */
    public static final class Builder {
        @NonNull private final PageId mPage;
        @Nullable private String mTarget;
        private boolean mCustom;
        @Nullable private String mPlace;
        private int mIconsScrollPx;
        @Nullable private String mWallpaperSlot;

        public Builder(@NonNull PageId page) {
            mPage = page;
        }

        /** Look: the selected surface (null for none) and whether the slider is at Custom. */
        @NonNull
        public Builder look(@Nullable String target, boolean custom) {
            mTarget = target;
            mCustom = custom;
            return this;
        }

        /** Layout: the place the canvas shows. */
        @NonNull
        public Builder place(@Nullable String place) {
            mPlace = place;
            return this;
        }

        /** Icon pack: the tile row's scroll. */
        @NonNull
        public Builder iconsScroll(int px) {
            mIconsScrollPx = px;
            return this;
        }

        /** The Overview's centred wallpaper card. */
        @NonNull
        public Builder wallpaperSlot(@Nullable String slot) {
            mWallpaperSlot = slot;
            return this;
        }

        @NonNull
        public AppearanceReturnState build(long leftAtEpochMs) {
            return new AppearanceReturnState(mPage, mTarget, mCustom, mPlace, mIconsScrollPx,
                mWallpaperSlot, leftAtEpochMs);
        }
    }

    /** The editor's mode for the remembered page, or null for the Overview. */
    @Nullable
    public EditorMode editorMode() {
        switch (page) {
            case LOOK: return EditorMode.LOOK;
            case LAYOUT: return EditorMode.LAYOUT;
            case ICONS: return EditorMode.ICONS;
            case OVERVIEW:
            default:
                return null;
        }
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
            root.put(JSON_PAGE, page.name());
            root.put(JSON_LEFT_AT, leftAtEpochMs);
            if (target != null) root.put(JSON_TARGET, target);
            if (custom) root.put(JSON_CUSTOM, true);
            if (place != null) root.put(JSON_PLACE, place);
            if (iconsScrollPx > 0) root.put(JSON_ICONS_SCROLL, iconsScrollPx);
            if (wallpaperSlot != null) root.put(JSON_WALLPAPER_SLOT, wallpaperSlot);
        } catch (JSONException ignored) {
            // Strings, a boolean and numbers: JSONObject#put never rejects them.
        }
        return root.toString();
    }

    /**
     * A state saved by {@link #serialize}, or null when {@code raw} is empty, malformed or names a
     * page this build does not have: the surface then opens on the Overview as it always has.
     */
    @Nullable
    public static AppearanceReturnState parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty())
            return null;
        try {
            JSONObject root = new JSONObject(raw);
            PageId page;
            try {
                page = PageId.valueOf(root.getString(JSON_PAGE));
            } catch (IllegalArgumentException e) {
                return null;
            }
            return new AppearanceReturnState(page,
                root.has(JSON_TARGET) ? root.optString(JSON_TARGET, null) : null,
                root.optBoolean(JSON_CUSTOM, false),
                root.has(JSON_PLACE) ? root.optString(JSON_PLACE, null) : null,
                root.optInt(JSON_ICONS_SCROLL, 0),
                root.has(JSON_WALLPAPER_SLOT) ? root.optString(JSON_WALLPAPER_SLOT, null) : null,
                root.optLong(JSON_LEFT_AT, 0L));
        } catch (JSONException e) {
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
