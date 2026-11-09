package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * What moves behind the launcher: nothing (a photo), a video file, or a stored shader. Immutable
 * and pure; persisted as one string under {@link TERMUX_APP#KEY_WALLPAPER_MOTION}:
 * {@code ""} (photo), {@code shader:<id>} or {@code video:<file name>}. An unknown or unsafe value
 * reads as photo.
 */
public final class WallpaperMotion {

    public enum Kind { PHOTO, VIDEO, SHADER }

    static final String SHADER_PREFIX = "shader:";
    static final String VIDEO_PREFIX = "video:";

    private static final WallpaperMotion PHOTO = new WallpaperMotion(Kind.PHOTO, null);

    @NonNull public final Kind kind;
    /** The shader id or the video's file name; null for photo. */
    @Nullable public final String id;

    private WallpaperMotion(@NonNull Kind kind, @Nullable String id) {
        this.kind = kind;
        this.id = id;
    }

    @NonNull
    public static WallpaperMotion photo() {
        return PHOTO;
    }

    /** A stored shader; photo when {@code id} is not a safe name. */
    @NonNull
    public static WallpaperMotion shader(@Nullable String id) {
        return safeName(id) ? new WallpaperMotion(Kind.SHADER, id) : PHOTO;
    }

    /** A video under {@code files/wallpaper/video/}; photo when {@code name} is not a safe name. */
    @NonNull
    public static WallpaperMotion video(@Nullable String name) {
        return safeName(name) ? new WallpaperMotion(Kind.VIDEO, name) : PHOTO;
    }

    @NonNull
    public static WallpaperMotion parse(@Nullable String value) {
        if (value == null) return PHOTO;
        String v = value.trim();
        if (v.startsWith(SHADER_PREFIX)) return shader(v.substring(SHADER_PREFIX.length()));
        if (v.startsWith(VIDEO_PREFIX)) return video(v.substring(VIDEO_PREFIX.length()));
        return PHOTO;
    }

    @NonNull
    public String format() {
        switch (kind) {
            case SHADER: return SHADER_PREFIX + id;
            case VIDEO: return VIDEO_PREFIX + id;
            default: return "";
        }
    }

    public boolean moves() {
        return kind != Kind.PHOTO;
    }

    /** One path segment: not empty, no separators, not {@code .} or {@code ..}, at most 128 chars. */
    static boolean safeName(@Nullable String name) {
        if (name == null || name.isEmpty() || name.length() > 128) return false;
        if (".".equals(name) || "..".equals(name)) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == '\0' || c == '\n' || c == ':') return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WallpaperMotion)) return false;
        WallpaperMotion m = (WallpaperMotion) o;
        return m.kind == kind && java.util.Objects.equals(m.id, id);
    }

    @Override
    public int hashCode() {
        return kind.hashCode() * 31 + (id == null ? 0 : id.hashCode());
    }

    @NonNull
    @Override
    public String toString() {
        return kind == Kind.PHOTO ? "photo" : format();
    }

    /**
     * The one-time move off the AGSL Live scenes (spec decision 1): a stored Live scene means the
     * launcher drew a scene the build no longer has. The five {@code live_wallpaper_*} keys are
     * cleared and the remembered photo goes back through the Live restore files.
     */
    public static final class Migration {

        /** The five keys the Live scenes kept; read once by the migration, then removed. */
        @NonNull
        public static final String[] OLD_KEYS = {
            TERMUX_APP.KEY_LIVE_WALLPAPER_SCENE, TERMUX_APP.KEY_LIVE_WALLPAPER_SPEED,
            TERMUX_APP.KEY_LIVE_WALLPAPER_RATE_HZ, TERMUX_APP.KEY_LIVE_WALLPAPER_DENSITY,
            TERMUX_APP.KEY_LIVE_WALLPAPER_PALETTE,
        };

        public enum Plan {
            /** No Live scene was stored: nothing to do. */
            NONE,
            /** Clear {@link #OLD_KEYS} and put the remembered photo back. */
            CLEAR_AND_RESTORE_PHOTO,
        }

        private Migration() {}

        /** {@code oldScene}: the raw {@code live_wallpaper_scene} value, null when unset. */
        @NonNull
        public static Plan plan(@Nullable String oldScene) {
            return oldScene == null || oldScene.trim().isEmpty() ? Plan.NONE : Plan.CLEAR_AND_RESTORE_PHOTO;
        }
    }
}
