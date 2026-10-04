package com.termux.app.chrome.wallpaper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.wallpaper.living.LivingStills;
import com.termux.app.chrome.wallpaper.living.Manifest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The living stills the user built from their photos (id {@code living:<hash>}), resolved from
 * disk. They are the only animated backgrounds: the pre-made ones are gone, and a stored id that
 * is not a living id (a retired background's) reads as unknown.
 */
public final class AnimatedWallpapers {

    private static final Pattern LIVING_ID = Pattern.compile("living:[0-9a-f]{16}");

    /** One player per photo, so a caller comparing by identity keeps seeing the same one until the recipe changes. */
    private static final ConcurrentHashMap<String, LivingStill> LIVING = new ConcurrentHashMap<>();

    private AnimatedWallpapers() {}

    /**
     * The living still with this id, or null when unknown. A living still needs the disk: use
     * {@link #byId(Context, String)}; this answers one only if that call resolved it before.
     */
    @Nullable
    public static AnimatedWallpaper byId(@Nullable String id) {
        return isLivingId(id) ? LIVING.get(id) : null;
    }

    /** The living still with this id, read from its manifest; null when that is gone or the id is not a living id. */
    @Nullable
    public static AnimatedWallpaper byId(@NonNull Context context, @Nullable String id) {
        if (id == null) return null;
        if (!isLivingId(id)) return null;
        Manifest manifest = LivingStills.findByHash(context.getApplicationContext(),
            id.substring(LivingStill.ID_PREFIX.length()));
        if (manifest == null) {
            LIVING.remove(id);
            return null;
        }
        LivingStill known = LIVING.get(id);
        long stamp = manifest.recipeFile().lastModified();
        if (known != null && known.stamp() == stamp) return known;
        LivingStill fresh = new LivingStill(manifest);
        LIVING.put(id, fresh);
        return fresh;
    }

    /** Whether the id names a living still (its file may or may not exist). */
    public static boolean isLivingId(@Nullable String id) {
        return id != null && LIVING_ID.matcher(id).matches();
    }

    /** Whether the id is a well-formed living still id: what a stored slot may hold. */
    public static boolean isKnownId(@Nullable String id) {
        return isLivingId(id);
    }
}
