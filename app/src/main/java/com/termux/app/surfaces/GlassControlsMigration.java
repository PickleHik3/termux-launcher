package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Map;

/**
 * Brings an install's stored glass up to the unified glass controls, once
 * ({@link TERMUX_APP#KEY_SURFACE_GLASS_CONTROLS_FORMAT}). Runs at start-up after the preferences'
 * own migrations and before {@link SurfacePresets#readoptMovedLooks}.
 *
 * <ul>
 *   <li>An install on a shipped Look as it stood before (its old blur and grain, the full tint
 *       strength, Tint's {@code material} family, at any of the Look's edges) takes the Look as it
 *       ships now, so its stop stays that Look. Its colour is exactly what it was: the shipped
 *       Looks render the same tint under the new amount.</li>
 *   <li>Any other look keeps its look: the Tint values become amounts (a {@code material} look's
 *       strengths are its amounts and its family becomes {@code scheme}; any other family's
 *       become 0, its own colour), and every blur and grain pair moves to the nearest Glass
 *       position's ({@link GlassControls#snapped}), Solid's no-blur pair excepted.</li>
 *   <li>The blur wallpaper mode remembers for its return moves onto the curve beside Base's grain
 *       ({@link GlassControls#blurWithGrain}),
 *       and the Glass slider's positions are written for Base and every detached surface.</li>
 *   <li>The saved Custom look is rewritten in the current format ({@link SurfacePresets#fromLegacy},
 *       {@link SurfacePresets#rescaleTintAmounts}).</li>
 *   <li>Format 2: every Tint amount moves onto the stretched scale ({@link #FORMAT}).</li>
 *   <li>Format 3: the retired Shade's stored value is dropped.</li>
 * </ul>
 *
 * <p>A store that never wrote a tint key read the old default, the full strength; it is pinned
 * first, so nothing reads the new default of 0 in its place. Every write, the format marker
 * included, lands in one editor ({@link SurfacePresets.Batch}): a process killed half way leaves
 * the store as it was, never a Tint look stripped of its colour with the marker unset.</p>
 */
public final class GlassControlsMigration {

    private GlassControlsMigration() {}

    /**
     * The format this build brings a store to. 1 is the unification above; 2 stretched the Tint
     * scale so 100 reaches the accent's container, and every stored amount, the base and each
     * per-surface cell, is scaled to draw the colour it drew
     * ({@link com.termux.app.chrome.GlassLook#amountForLegacyPercent}: the Tint Look's 100 is 35).
     * A store at 0 takes both in the same edit; the saved Custom look is scaled on read
     * ({@link SurfacePresets#rescaleTintAmounts}) and rewritten. 3 retired Shade: the stored
     * value is removed (every earlier format takes that in the same edit) and the saved Custom
     * look sheds it on the rewrite ({@link SurfacePresets#RETIRED_SHADE_KEY}).
     */
    static final int FORMAT = 3;
    /** The format that stretched the Tint scale. */
    private static final int FORMAT_TINT_SCALE = 2;

    /**
     * The Looks' start-up work, in its order: this migration first, so a Look it recognises at its
     * old values is applied as it ships now, then {@link SurfacePresets#readoptMovedLooks} for a
     * Look whose edge moved. The application calls this, not the two apart.
     */
    public static void runAtStartUp(@NonNull TermuxAppSharedPreferences prefs) {
        run(prefs);
        SurfacePresets.readoptMovedLooks(prefs);
    }

    public static void run(@NonNull TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        int from = store == null ? FORMAT
            : store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0);
        if (from >= FORMAT)
            return;
        SurfacePresets.Batch batch = new SurfacePresets.Batch(store);
        TermuxAppSharedPreferences into = SurfacePresets.writingInto(prefs, batch);
        if (from < 1) {
            pinLegacyTintStrengths(into, batch);
            SurfacePresets.Preset shipped = SurfacePresets.lookAsShippedBeforeFormat5(into);
            if (shipped != null) {
                // A shipped Look as it ships now: its Tint is already on the stretched scale.
                SurfacePresets.apply(into, shipped);
            } else {
                migrateLive(into);
                rescaleTintAmounts(into, batch);
            }
            snapRememberedBlur(into, batch);
            writeGlassPositions(into);
        } else if (from < FORMAT_TINT_SCALE) {
            rescaleTintAmounts(into, batch);
        }
        // Format 3: Shade is retired; its stored value is dropped, and the look draws as Shade 0.
        batch.edit().remove(SurfacePresets.RETIRED_SHADE_KEY).apply();
        String custom = into.getSurfaceCustomPreset();
        if (!custom.isEmpty()) {
            Map<String, Object> look = SurfacePresets.deserialize(custom);
            if (look != null && !look.isEmpty())
                into.setSurfaceCustomPreset(SurfacePresets.serialize(look));
        }
        batch.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, FORMAT).apply();
        batch.applyToStore();
    }

    /**
     * The blur wallpaper mode puts back when it is turned on again, on the curve: the position
     * nearest that blur with Base's grain, as it will be worn. No blur stays no blur.
     */
    private static void snapRememberedBlur(@NonNull TermuxAppSharedPreferences prefs,
                                           @NonNull SharedPreferences store) {
        if (!store.contains(TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS))
            return;
        int remembered = prefs.getWallpaperEnabledExtraKeysBlurRadius();
        int blur = GlassControls.blurWithGrain(remembered,
            prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        if (blur != remembered)
            prefs.setWallpaperEnabledExtraKeysBlurRadius(blur);
    }

    /** The Glass slider's position for Base, and for each surface with its own blur or grain. */
    private static void writeGlassPositions(@NonNull TermuxAppSharedPreferences prefs) {
        int blur = prefs.getSurfaceBaseValue(SurfaceProperty.BLUR);
        int grain = prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN);
        prefs.setSurfaceGlassPosition(null, GlassControls.glassFor(blur, grain));
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            boolean ownBlur = !prefs.isSurfaceInheriting(slot, SurfaceProperty.BLUR);
            boolean ownGrain = !prefs.isSurfaceInheriting(slot, SurfaceProperty.GRAIN);
            if (!ownBlur && !ownGrain)
                continue;
            prefs.setSurfaceGlassPosition(slot, GlassControls.glassFor(
                ownBlur ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.BLUR) : blur,
                ownGrain ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.GRAIN) : grain));
        }
    }

    /**
     * Every stored Tint amount, the base and each per-surface cell, from the scale before format
     * 2 to its own, so each draws the colour it drew. A cell the store never set reads its
     * default, 0, which stays 0.
     */
    private static void rescaleTintAmounts(@NonNull TermuxAppSharedPreferences prefs,
                                           @NonNull SharedPreferences store) {
        int base = prefs.getSurfaceBaseValue(SurfaceProperty.TINT);
        int scaledBase = com.termux.app.chrome.GlassLook.amountForLegacyPercent(base);
        if (scaledBase != base)
            prefs.setSurfaceBaseValue(SurfaceProperty.TINT, scaledBase);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            String key = TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.TINT);
            if (key == null || !store.contains(key))
                continue;
            int cell = prefs.getSurfaceOverrideValue(slot, SurfaceProperty.TINT);
            int scaled = com.termux.app.chrome.GlassLook.amountForLegacyPercent(cell);
            if (scaled != cell)
                prefs.setSurfaceRawValue(slot, SurfaceProperty.TINT, scaled);
        }
    }

    /** Writes the old default strength into every tint key the store never set. */
    private static void pinLegacyTintStrengths(@NonNull TermuxAppSharedPreferences prefs,
                                               @NonNull SharedPreferences store) {
        if (!store.contains(TERMUX_APP.KEY_SURFACE_BASE_TINT))
            prefs.setSurfaceBaseValue(SurfaceProperty.TINT, SurfacePresets.LEGACY_TINT_STRENGTH);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            String key = TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.TINT);
            if (key != null && !store.contains(key))
                prefs.setSurfaceRawValue(slot, SurfaceProperty.TINT,
                    SurfacePresets.LEGACY_TINT_STRENGTH);
        }
    }

    /** The live look, not a shipped one, carried across with its look kept. */
    static void migrateLive(@NonNull TermuxAppSharedPreferences prefs) {
        boolean material = TERMUX_APP.GLASS_TINT_MATERIAL.equals(prefs.getSurfaceGlassTint());
        if (material) {
            // The Material blend at strength s is the scheme ink at format 1's amount s: the
            // numbers stay here, and format 2's scaling follows.
            prefs.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_SCHEME);
        } else {
            prefs.setSurfaceBaseValue(SurfaceProperty.TINT, 0);
            for (SurfaceSlot slot : SurfaceSlot.values()) {
                if (TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.TINT) != null)
                    prefs.setSurfaceRawValue(slot, SurfaceProperty.TINT, 0);
            }
        }
        int baseBlur = prefs.getSurfaceBaseValue(SurfaceProperty.BLUR);
        int baseGrain = prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.BLUR) == null
                || TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.GRAIN) == null)
                continue;
            boolean ownBlur = !prefs.isSurfaceInheriting(slot, SurfaceProperty.BLUR);
            boolean ownGrain = !prefs.isSurfaceInheriting(slot, SurfaceProperty.GRAIN);
            if (!ownBlur && !ownGrain)
                continue;
            int[] pair = GlassControls.snapped(
                ownBlur ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.BLUR) : baseBlur,
                ownGrain ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.GRAIN) : baseGrain);
            prefs.detachSurfaceValue(slot, SurfaceProperty.BLUR, pair[0]);
            prefs.detachSurfaceValue(slot, SurfaceProperty.GRAIN, pair[1]);
        }
        int[] base = GlassControls.snapped(baseBlur, baseGrain);
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, base[0]);
        prefs.setSurfaceBaseValue(SurfaceProperty.GRAIN, base[1]);
    }
}
