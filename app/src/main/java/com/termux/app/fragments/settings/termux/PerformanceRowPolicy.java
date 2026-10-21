package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;

import com.termux.app.chrome.FancierGlassPolicy;

/**
 * Why a Smoother motion row on Battery &amp; performance is greyed, if it is.
 *
 * <p>A preset that owns the row is asked first: the Low-memory preset owns what it wrote, and
 * Reduce idle activity (on by hand or through the preset) turns the glass off at runtime without
 * writing its switch. Only then what the phone or the wallpaper lacks. Pure: facts in, one reason
 * out, so the order is testable without a screen.
 */
final class PerformanceRowPolicy {

    /** What the row says under its title when it cannot be flipped; {@link #NONE} when it can. */
    enum Reason {
        NONE,
        SET_BY_LOW_MEMORY_PRESET,
        OFF_WHILE_IDLE_REDUCED,
        NEEDS_ANDROID_13,
        NEEDS_PICKED_WALLPAPER,
        NEEDS_GLASS_REFRACTION,
        NEEDS_WALLPAPER;

        boolean enabled() {
            return this == NONE;
        }

        /**
         * The row reads as off whatever is stored. Only idle reduction does this: the glass really
         * is off then, and the stored choice comes back with the glass when it ends.
         */
        boolean shownOff() {
            return this == OFF_WHILE_IDLE_REDUCED;
        }
    }

    /** What the page reads once per refresh. */
    static final class Facts {
        final int sdkInt;
        final boolean idleReduced;
        final boolean lowMemoryPreset;
        final boolean managedWallpaperOnScreen;
        final boolean glassRefractionOn;
        final boolean wallpaperModeOn;

        Facts(int sdkInt, boolean idleReduced, boolean lowMemoryPreset,
              boolean managedWallpaperOnScreen, boolean glassRefractionOn, boolean wallpaperModeOn) {
            this.sdkInt = sdkInt;
            this.idleReduced = idleReduced;
            this.lowMemoryPreset = lowMemoryPreset;
            this.managedWallpaperOnScreen = managedWallpaperOnScreen;
            this.glassRefractionOn = glassRefractionOn;
            this.wallpaperModeOn = wallpaperModeOn;
        }
    }

    private PerformanceRowPolicy() {}

    @NonNull
    static Reason glassRefraction(@NonNull Facts facts) {
        if (facts.idleReduced) return Reason.OFF_WHILE_IDLE_REDUCED;
        return glassUnavailable(facts);
    }

    @NonNull
    static Reason livingGlass(@NonNull Facts facts) {
        if (facts.idleReduced) return Reason.OFF_WHILE_IDLE_REDUCED;
        Reason glass = glassUnavailable(facts);
        if (glass != Reason.NONE) return glass;
        // Only a surface that refracts has glass to move.
        return facts.glassRefractionOn ? Reason.NONE : Reason.NEEDS_GLASS_REFRACTION;
    }

    @NonNull
    static Reason wallpaperParallax(@NonNull Facts facts) {
        if (facts.lowMemoryPreset) return Reason.SET_BY_LOW_MEMORY_PRESET;
        return facts.wallpaperModeOn ? Reason.NONE : Reason.NEEDS_WALLPAPER;
    }

    @NonNull
    static Reason cursorTrail(@NonNull Facts facts) {
        return facts.lowMemoryPreset ? Reason.SET_BY_LOW_MEMORY_PRESET : Reason.NONE;
    }

    @NonNull
    static Reason terminalEffect(@NonNull Facts facts) {
        return facts.lowMemoryPreset ? Reason.SET_BY_LOW_MEMORY_PRESET : Reason.NONE;
    }

    /** The phone and the wallpaper, in {@link FancierGlassPolicy}'s own terms. */
    @NonNull
    private static Reason glassUnavailable(@NonNull Facts facts) {
        if (!FancierGlassPolicy.offered(facts.sdkInt)) return Reason.NEEDS_ANDROID_13;
        if (!FancierGlassPolicy.flippable(facts.sdkInt, facts.managedWallpaperOnScreen))
            return Reason.NEEDS_PICKED_WALLPAPER;
        return Reason.NONE;
    }
}
