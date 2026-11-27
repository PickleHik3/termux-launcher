package com.termux.app.look;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The three Looks made from the wallpaper: calm, balanced and bold. Each has Opacity and Blur from
 * its step, raised for a brighter wallpaper (more Opacity, so text stays readable) and a busier
 * one (more Opacity and Blur). The numbers are kept in the main preferences ({@code
 * terl.look.wallpaper.*}) so the filmstrip and a worn pick survive a restart; a new wallpaper
 * rewrites them, and a Look worn by id follows.
 */
public final class WallpaperLooks {

    private WallpaperLooks() { }

    public static final String CALM = "wallpaper.calm";
    public static final String BALANCED = "wallpaper.balanced";
    public static final String BOLD = "wallpaper.bold";

    private static final String[] IDS = {CALM, BALANCED, BOLD};
    private static final String[] SLUGS = {"calm", "balanced", "bold"};
    private static final int[] BASE_OPACITY = {30, 46, 62};
    private static final int[] BASE_BLUR = {6, 16, 28};

    /** The three Looks for a wallpaper, in filmstrip order. */
    @NonNull
    public static List<TerlLookSource.Look> from(@NonNull WallpaperSummary summary) {
        List<TerlLookSource.Look> looks = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            int opacity = BASE_OPACITY[i] + Math.round((summary.luminance - 50) * 0.2f)
                + Math.round((summary.busyness - 50) * 0.1f);
            int blur = BASE_BLUR[i] + Math.round((summary.busyness - 50) * 0.15f);
            looks.add(new TerlLookSource.Look(IDS[i], opacity, blur));
        }
        return Collections.unmodifiableList(looks);
    }

    /** Keeps the three Looks made from {@code summary}, replacing the last ones. */
    public static void store(@NonNull LookStore store, @NonNull WallpaperSummary summary) {
        List<TerlLookSource.Look> looks = from(summary);
        for (int i = 0; i < 3; i++) {
            store.putInt(key(i, "opacity"), looks.get(i).opacity);
            store.putInt(key(i, "blur"), looks.get(i).blur);
        }
    }

    @NonNull
    private static String key(int index, @NonNull String property) {
        return StoredTerlLookStore.PREFIX + "wallpaper." + SLUGS[index] + "." + property;
    }

    /** The stored Looks, or those of a neutral wallpaper until one has been looked at. */
    public static final class Source implements TerlLookSource {
        private final LookStore store;

        public Source(@NonNull LookStore store) {
            this.store = store;
        }

        @NonNull
        @Override
        public List<Look> looks() {
            List<Look> fallback = from(WallpaperSummary.NEUTRAL);
            List<Look> looks = new ArrayList<>(3);
            for (int i = 0; i < 3; i++) {
                Look neutral = fallback.get(i);
                looks.add(new Look(IDS[i],
                    store.getInt(key(i, "opacity"), neutral.opacity),
                    store.getInt(key(i, "blur"), neutral.blur)));
            }
            return looks;
        }
    }
}
