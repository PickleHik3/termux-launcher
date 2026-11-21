package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * How Home's page bar draws its glass, a developer switch on no settings page, flipped at run
 * time with {@code launcherctl pagebar-glass concave|well}. The one owner: the bar reads it
 * through {@link #current} and is told of a change through {@link #watch}.
 *
 * <ul>
 *   <li>{@link #CONCAVE}, the default: the glass sinks into the page. With Fancier Glass the
 *   wallpaper is seen through a concave lens, drawn in toward the centre line
 *   ({@link com.termux.app.chrome.GlassRefraction.Look#concave}); without it the plain blur. Over
 *   either, only the inverted rim: dark along the upper edge, light along the lower
 *   ({@link com.termux.app.chrome.InsetGlass#concaveRimShadow},
 *   {@link com.termux.app.chrome.InsetGlass#concaveRimLight}). No painted shade.</li>
 *   <li>{@link #WELL}: the shaded well as it was, a floor shade, a contact shadow and the rim of
 *   the hole, over the plain blur ({@link com.termux.app.chrome.InsetGlass}).</li>
 * </ul>
 *
 * <p>Where the bar wears no glass (no wallpaper the launcher set, battery saver, outline text,
 * high contrast) it draws the well on its solid card whichever is chosen, as it always did.</p>
 */
public enum PageBarGlassStyle {
    CONCAVE("concave"),
    WELL("well");

    @NonNull public static final PageBarGlassStyle DEFAULT = CONCAVE;

    /** The stored and typed name. */
    @NonNull public final String id;

    PageBarGlassStyle(@NonNull String id) {
        this.id = id;
    }

    /** The style named {@code name} (any case, trimmed), or null for no such style. */
    @Nullable
    public static PageBarGlassStyle parse(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (PageBarGlassStyle style : values()) {
            if (style.id.equals(key)) return style;
        }
        return null;
    }

    /** The style a stored value names, {@link #DEFAULT} for none or an unknown one. */
    @NonNull
    public static PageBarGlassStyle ofStored(@Nullable String stored) {
        PageBarGlassStyle style = parse(stored);
        return style == null ? DEFAULT : style;
    }

    /** The style as read, kept for the process: a dress pass asks without touching the store. */
    @Nullable private static volatile PageBarGlassStyle sCurrent;
    private static final CopyOnWriteArrayList<Runnable> sWatchers = new CopyOnWriteArrayList<>();

    /** The bar's glass now: the stored style, read once per process and kept. */
    @NonNull
    public static PageBarGlassStyle current(@NonNull Context context) {
        PageBarGlassStyle style = sCurrent;
        if (style != null) return style;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        style = ofStored(preferences == null ? null : preferences.getPageBarGlassStyle());
        sCurrent = style;
        return style;
    }

    /**
     * Stores {@code style} and tells every watching bar on the main thread, which dresses again
     * at once. Any thread.
     */
    public static void set(@NonNull Context context, @NonNull PageBarGlassStyle style) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences != null) preferences.setPageBarGlassStyle(style.id);
        sCurrent = style;
        Runnable tell = () -> {
            for (Runnable watcher : sWatchers) watcher.run();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            tell.run();
        } else {
            new Handler(Looper.getMainLooper()).post(tell);
        }
    }

    /** {@code watcher} runs on the main thread after each {@link #set}; a bar watches while attached. */
    public static void watch(@NonNull Runnable watcher) {
        sWatchers.addIfAbsent(watcher);
    }

    public static void unwatch(@NonNull Runnable watcher) {
        sWatchers.remove(watcher);
    }

    @VisibleForTesting
    static int watcherCount() {
        return sWatchers.size();
    }

    /** Forgets the kept style, so the next {@link #current} reads the store. */
    @VisibleForTesting
    static void forget() {
        sCurrent = null;
    }
}
