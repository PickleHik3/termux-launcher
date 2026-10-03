package com.termux.app.chrome.wallpaper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.wallpaper.living.LivingStills;
import com.termux.app.chrome.wallpaper.living.Manifest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The built-in generated backgrounds, in picker order, and the living stills the user built from
 * their photos (id {@code living:<hash>}), which are resolved from disk and kept out of
 * {@link #all()}.
 */
public final class AnimatedWallpapers {

    private static final List<AnimatedWallpaper> ALL = Collections.unmodifiableList(Arrays.<AnimatedWallpaper>asList(
        new Mesh(), new Aurora(), new Tide(), new Rain(),
        new Contour(), new Drift(), new Lava(), new Silk(), new Caustics(), new Chrome()));

    private static final Pattern LIVING_ID = Pattern.compile("living:[0-9a-f]{16}");

    /** One player per photo, so a caller comparing by identity keeps seeing the same one until the recipe changes. */
    private static final ConcurrentHashMap<String, LivingStill> LIVING = new ConcurrentHashMap<>();

    private AnimatedWallpapers() {}

    public static List<AnimatedWallpaper> all() {
        return ALL;
    }

    /**
     * The built-in background with this id, or null when unknown (a restore from a newer build,
     * say). A living still needs the disk: use {@link #byId(Context, String)}; this answers one only
     * if that call resolved it before.
     */
    public static AnimatedWallpaper byId(String id) {
        if (id == null) return null;
        for (AnimatedWallpaper w : ALL) if (w.id().equals(id)) return w;
        return isLivingId(id) ? LIVING.get(id) : null;
    }

    /** As {@link #byId(String)}, and a {@code living:<hash>} id is read from its manifest; null when that is gone. */
    @Nullable
    public static AnimatedWallpaper byId(@NonNull Context context, @Nullable String id) {
        if (id == null) return null;
        if (!isLivingId(id)) return byId(id);
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

    /** Whether the id is a built-in or a well-formed living still id: what a stored slot may hold. */
    public static boolean isKnownId(@Nullable String id) {
        return id != null && (byId(id) != null || isLivingId(id));
    }
}
