package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The one-way fold of the old surface-look keys into the {@link LookGrid} (ADR 0017 §5), run once
 * per install behind {@link #KEY_MIGRATED}.
 *
 * <p>It carries its own table of the old keys and their meaning, so nothing of the old resolver
 * has to stay alive for it. The old rule, per surface, property and page: the surface's own value
 * on that page ({@code <own key>@<page>}) when present; else Global while the surface's inherit flag
 * ({@code surface_inherit_<slot>_<property>}, default on) holds; else the surface's own key, or its
 * own shipped number when the key was never written. Corners and margin were one value each, read
 * from Global whatever a flag said.</p>
 *
 * <p>Mapping: Global → {@code look.<p>}; a surface detached on the wall → {@code look.<p>.<element>};
 * a page's own value → {@code look.<p>.<element>@<page>}; blur → the Frost stop nearest by blur,
 * grain kept exactly wherever the stop's own grain is not the old number; Vibrancy, tint colour,
 * rim, the bend (Depth) and dispersion (Edge) → Global; the drawer's corner → the Sheets corner;
 * {@code widgets_on_glass} off → solid Widgets (opacity 100, Frost 0). A final pass compares every
 * (element, property, page) with the old rule and pins the element cell wherever the grid would
 * answer otherwise. Every key read is then deleted.</p>
 */
public final class LookGridMigration {

    private LookGridMigration() {}

    public static final String KEY_MIGRATED = "look_grid_migrated";

    public static final String INHERIT_PREFIX = "surface_inherit_";

    /** The four old glass properties. */
    enum OldProperty {
        BLUR("blur", "surface_base_blur", TERMUX_APP.DEFAULT_SURFACE_GLOBAL_BLUR),
        OPACITY("opacity", "surface_base_opacity", TERMUX_APP.DEFAULT_SURFACE_GLOBAL_OPACITY),
        GRAIN("grain", "surface_base_grain", TERMUX_APP.DEFAULT_SURFACE_GLOBAL_GRAIN),
        TINT("tint", "surface_base_tint", TERMUX_APP.DEFAULT_SURFACE_GLOBAL_TINT);

        final String key;
        final String globalKey;
        final int globalDefault;

        OldProperty(String key, String globalKey, int globalDefault) {
            this.key = key;
            this.globalKey = globalKey;
            this.globalDefault = globalDefault;
        }
    }

    /** The four old surfaces: their flag name, element, own keys and own shipped numbers. */
    enum OldSlot {
        DOCK("dock", Element.DOCK,
            new String[] {TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, TERMUX_APP.KEY_APP_BAR_OPACITY,
                TERMUX_APP.KEY_DOCK_GLASS_GRAIN, TERMUX_APP.KEY_DOCK_TINT_STRENGTH},
            new int[] {TERMUX_APP.DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS,
                TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY, TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN,
                TERMUX_APP.DEFAULT_SURFACE_GLOBAL_TINT}),
        KEYBOARD("keyboard", Element.KEYBOARD,
            new String[] {TERMUX_APP.KEY_IN_APP_KEYBOARD_BLUR_RADIUS,
                TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKGROUND_OPACITY, TERMUX_APP.KEY_IN_APP_KEYBOARD_GRAIN,
                TERMUX_APP.KEY_IN_APP_KEYBOARD_TINT_STRENGTH},
            new int[] {TERMUX_APP.DEFAULT_SURFACE_GLOBAL_BLUR,
                TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY,
                TERMUX_APP.DEFAULT_SURFACE_GLOBAL_GRAIN, TERMUX_APP.DEFAULT_SURFACE_GLOBAL_TINT}),
        STATUS("status", Element.STATUS,
            new String[] {TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, TERMUX_APP.KEY_STATUS_BAR_OPACITY,
                TERMUX_APP.KEY_STATUS_BAR_GRAIN, TERMUX_APP.KEY_STATUS_BAR_TINT_STRENGTH},
            new int[] {TERMUX_APP.DEFAULT_STATUS_BAR_BLUR_RADIUS, TERMUX_APP.DEFAULT_STATUS_BAR_OPACITY,
                TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN, TERMUX_APP.DEFAULT_SURFACE_GLOBAL_TINT}),
        CANVAS("canvas", Element.TERMINAL,
            new String[] {TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS,
                TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY, TERMUX_APP.KEY_TERMINAL_GLASS_GRAIN,
                TERMUX_APP.KEY_TERMINAL_TINT_STRENGTH},
            new int[] {TERMUX_APP.DEFAULT_TERMINAL_GLASS_BLUR_RADIUS,
                TERMUX_APP.DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY,
                TERMUX_APP.DEFAULT_TERMINAL_GLASS_GRAIN, TERMUX_APP.DEFAULT_SURFACE_GLOBAL_TINT});

        final String key;
        final Element element;
        private final String[] ownKeys;
        private final int[] ownDefaults;

        OldSlot(String key, Element element, String[] ownKeys, int[] ownDefaults) {
            this.key = key;
            this.element = element;
            this.ownKeys = ownKeys;
            this.ownDefaults = ownDefaults;
        }

        String ownKey(OldProperty property) { return ownKeys[property.ordinal()]; }
        int ownDefault(OldProperty property) { return ownDefaults[property.ordinal()]; }
    }

    /** Keys whose plain value goes to Global, or only to deletion: shapes and retired switches. */
    private static final String[] RETIRED = {
        "surface_base_corner_radius", "surface_base_side_gap",
        TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS, TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS,
        TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET,
        TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET,
        TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE,
        TERMUX_APP.KEY_SURFACE_GLASS_RIM, TERMUX_APP.KEY_FANCIER_GLASS_BEND,
        TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS,
        TERMUX_APP.KEY_WIDGETS_ON_GLASS,
        TERMUX_APP.KEY_KEYBOARD_OPACITY_SENTINEL_HEALED, TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED,
    };

    /** Whether the store is a fresh install's: nothing in it but the log level. */
    static boolean isFresh(@NonNull LookGrid.Store store) {
        for (String key : store.keys()) {
            if (!TERMUX_APP.KEY_LOG_LEVEL.equals(key)) return false;
        }
        return true;
    }

    /**
     * Folds the old keys into the grid once. A fresh install is left untouched (it has nothing to
     * fold, and must still read as fresh); a store already holding grid keys is only marked.
     *
     * @param pageIds every page id the wall has, so each page's own values are carried over
     */
    public static void run(@NonNull LookGrid.Store store, @NonNull Collection<Integer> pageIds) {
        run(store, pageIds, isFresh(store));
    }

    /** As above, with freshness decided by the caller before other migrations wrote their keys. */
    public static void run(@NonNull LookGrid.Store store, @NonNull Collection<Integer> pageIds,
                           boolean fresh) {
        if (store.getBoolean(KEY_MIGRATED, false) || fresh) return;
        LookGrid grid = new LookGrid(store);
        if (grid.gridKeys().isEmpty()) fold(store, grid, pageIds);
        store.putBoolean(KEY_MIGRATED, true);
    }

    private static void fold(@NonNull LookGrid.Store store, @NonNull LookGrid grid,
                             @NonNull Collection<Integer> pageIds) {
        foldPreInheritance(store);
        healKeyboardOpacitySentinel(store);
        foldKeyboardGlassKeys(store);

        List<Integer> pages = new ArrayList<>();
        for (Integer id : pageIds) {
            if (id != null && id >= 0 && !pages.contains(id)) pages.add(id);
        }

        // The old answer for every (slot, property, where), before anything is written.
        int[][][] old = new int[OldSlot.values().length][OldProperty.values().length][pages.size() + 1];
        for (OldSlot slot : OldSlot.values()) {
            for (OldProperty property : OldProperty.values()) {
                old[slot.ordinal()][property.ordinal()][0] = oldValue(store, slot, property, -1);
                for (int i = 0; i < pages.size(); i++)
                    old[slot.ordinal()][property.ordinal()][i + 1] =
                        oldValue(store, slot, property, pages.get(i));
            }
        }

        // Global. Frost always (the old shipped pair is no stop); grain wherever the stop's is not it.
        int globalBlur = store.getInt(OldProperty.BLUR.globalKey, OldProperty.BLUR.globalDefault);
        int globalGrain = store.getInt(OldProperty.GRAIN.globalKey, OldProperty.GRAIN.globalDefault);
        int globalStop = FrostStops.nearestStopByBlur(globalBlur);
        grid.write(Property.FROST, null, globalStop);
        if (FrostStops.grainPercentFor(globalStop) != globalGrain)
            grid.write(Property.GRAIN, null, globalGrain);
        copyInt(store, grid, OldProperty.OPACITY.globalKey, Property.OPACITY, null);
        copyInt(store, grid, OldProperty.TINT.globalKey, Property.TINT, null);
        copyInt(store, grid, "surface_base_corner_radius", Property.CORNERS, null);
        copyInt(store, grid, "surface_base_side_gap", Property.MARGIN, null);
        copyInt(store, grid, TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, Property.VIBRANCY, null);
        copyInt(store, grid, TERMUX_APP.KEY_FANCIER_GLASS_BEND, Property.DEPTH, null);
        copyInt(store, grid, TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, Property.EDGE, null);
        copyInt(store, grid, TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS, Property.CORNERS,
            Element.SHEETS);
        String hue = store.getString(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE, null);
        if (hue != null) grid.write(Property.TINT_HUE, null, tintHueOf(hue));
        String rim = store.getString(TERMUX_APP.KEY_SURFACE_GLASS_RIM, null);
        if (rim != null) grid.write(Property.RIM, null, rimOf(rim));
        if (!store.getBoolean(TERMUX_APP.KEY_WIDGETS_ON_GLASS, TERMUX_APP.DEFAULT_VALUE_WIDGETS_ON_GLASS)) {
            grid.write(Property.OPACITY, Element.WIDGETS, 100);
            grid.write(Property.FROST, Element.WIDGETS, FrostStops.SOLID);
        }

        // Each surface: detached on the wall → its element cell; a page's own value → that page's.
        for (OldSlot slot : OldSlot.values()) {
            for (OldProperty property : OldProperty.values()) {
                if (!inherits(store, slot, property))
                    writeOld(grid, slot.element, property, LookGrid.NO_PAGE,
                        store.getInt(slot.ownKey(property), slot.ownDefault(property)));
                for (int page : pages) {
                    String pageKey = slot.ownKey(property) + LookGrid.PAGE_SEPARATOR + page;
                    if (store.contains(pageKey))
                        writeOld(grid, slot.element, property, page, store.getInt(pageKey, 0));
                }
            }
        }

        // Pin whatever the grid would still answer otherwise: the wall first, then each page.
        for (OldSlot slot : OldSlot.values()) {
            for (OldProperty property : OldProperty.values()) {
                for (int i = 0; i <= pages.size(); i++) {
                    int page = i == 0 ? LookGrid.NO_PAGE : pages.get(i - 1);
                    int want = old[slot.ordinal()][property.ordinal()][i];
                    if (!agrees(grid, slot.element, property, page, want))
                        writeOld(grid, slot.element, property, page, want);
                }
            }
        }

        deleteOldKeys(store);
    }

    /**
     * An install from before Global existed: Global takes the dock's number, and a surface follows it
     * unless it stored a number of its own that differs (none does once tuning was normalised).
     */
    private static void foldPreInheritance(@NonNull LookGrid.Store store) {
        if (store.getBoolean(TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, false)) return;
        boolean normalized = store.getBoolean(TERMUX_APP.KEY_SURFACE_TUNING_NORMALIZED,
            TERMUX_APP.DEFAULT_VALUE_SURFACE_TUNING_NORMALIZED);
        for (OldProperty property : OldProperty.values()) {
            int global = store.getInt(OldSlot.DOCK.ownKey(property), OldSlot.DOCK.ownDefault(property));
            store.putInt(property.globalKey, global);
            for (OldSlot slot : OldSlot.values()) {
                String own = slot.ownKey(property);
                boolean matches = normalized || !store.contains(own)
                    || store.getInt(own, slot.ownDefault(property)) == global;
                store.putBoolean(flagKey(slot, property), matches);
            }
        }
        // Corners and the edge gap were one value each, read from Global, which took the dock's.
        store.putInt("surface_base_corner_radius", store.getInt(
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS,
            TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS));
        store.putInt("surface_base_side_gap", store.getInt(TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET,
            TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET));
        store.putBoolean(TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, true);
    }

    /** A keyboard detached at the old opacity sentinel follows Global again. */
    private static void healKeyboardOpacitySentinel(@NonNull LookGrid.Store store) {
        if (store.getBoolean(TERMUX_APP.KEY_KEYBOARD_OPACITY_SENTINEL_HEALED, false)) return;
        if (!inherits(store, OldSlot.KEYBOARD, OldProperty.OPACITY)
            && store.getInt(OldSlot.KEYBOARD.ownKey(OldProperty.OPACITY), Integer.MIN_VALUE)
                == TERMUX_APP.LEGACY_IN_APP_KEYBOARD_BACKGROUND_OPACITY_SENTINEL)
            store.putBoolean(flagKey(OldSlot.KEYBOARD, OldProperty.OPACITY), true);
    }

    /** The keyboard's blur and grain stored as -1 followed the dock's own number. */
    private static void foldKeyboardGlassKeys(@NonNull LookGrid.Store store) {
        if (store.getBoolean(TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED, false)) return;
        for (OldProperty property : new OldProperty[] {OldProperty.BLUR, OldProperty.GRAIN}) {
            String own = OldSlot.KEYBOARD.ownKey(property);
            int raw = store.getInt(own, -1);
            if (raw < 0 && !inherits(store, OldSlot.DOCK, property))
                raw = store.getInt(OldSlot.DOCK.ownKey(property), OldSlot.DOCK.ownDefault(property));
            store.putBoolean(flagKey(OldSlot.KEYBOARD, property), raw < 0);
            if (raw >= 0) store.putInt(own, raw);
        }
    }

    private static String flagKey(@NonNull OldSlot slot, @NonNull OldProperty property) {
        return INHERIT_PREFIX + slot.key + "_" + property.key;
    }

    /** The old resolver's answer, from the old keys alone. */
    static int oldValue(@NonNull LookGrid.Store store, @NonNull OldSlot slot,
                        @NonNull OldProperty property, int page) {
        String own = slot.ownKey(property);
        if (page >= 0) {
            String pageKey = own + LookGrid.PAGE_SEPARATOR + page;
            if (store.contains(pageKey)) return store.getInt(pageKey, slot.ownDefault(property));
        }
        if (inherits(store, slot, property))
            return store.getInt(property.globalKey, property.globalDefault);
        return store.getInt(own, slot.ownDefault(property));
    }

    private static boolean inherits(@NonNull LookGrid.Store store, @NonNull OldSlot slot,
                                    @NonNull OldProperty property) {
        return store.getBoolean(flagKey(slot, property),
            TERMUX_APP.DEFAULT_VALUE_SURFACE_INHERITS_GLOBAL);
    }

    /**
     * Whether the grid answers the old number: blur through its Frost stop. On a page, the page's
     * own cell when it holds one, else the wall's (the read this fold's page cells had).
     */
    private static boolean agrees(@NonNull LookGrid grid, @NonNull Element element,
                                  @NonNull OldProperty property, int page, int want) {
        Property target = property == OldProperty.BLUR ? Property.FROST : gridProperty(property);
        int expected = property == OldProperty.BLUR
            ? FrostStops.nearestStopByBlur(want) : target.clamp(want);
        if (page >= 0) {
            String pageCell = LookGrid.key(target, element, page);
            if (grid.store().contains(pageCell))
                return grid.store().getInt(pageCell, Integer.MIN_VALUE) == expected;
            page = LookGrid.NO_PAGE;
        }
        return grid.value(target, element) == expected;
    }

    /**
     * Writes one old value. A page's own value goes to that page's cell
     * ({@code look.<p>.<element>@<page>}), which no build reads any more (ADR 0031): the one-look
     * reset deletes it.
     */
    private static void writeOld(@NonNull LookGrid grid, @NonNull Element element,
                                 @NonNull OldProperty property, int page, int value) {
        Property target = property == OldProperty.BLUR ? Property.FROST : gridProperty(property);
        int stored = property == OldProperty.BLUR ? FrostStops.nearestStopByBlur(value) : value;
        if (page >= 0)
            grid.store().putInt(LookGrid.key(target, element, page), target.clamp(stored));
        else
            grid.write(target, element, stored);
    }

    @NonNull
    private static Property gridProperty(@NonNull OldProperty property) {
        switch (property) {
            case BLUR: return Property.FROST;
            case OPACITY: return Property.OPACITY;
            case GRAIN: return Property.GRAIN;
            default: return Property.TINT;
        }
    }

    private static void copyInt(@NonNull LookGrid.Store store, @NonNull LookGrid grid,
                                @NonNull String oldKey, @NonNull Property property,
                                @Nullable Element element) {
        if (store.contains(oldKey))
            grid.write(property, element, store.getInt(oldKey, property.shipped));
    }

    static int tintHueOf(@NonNull String hue) {
        if (TERMUX_APP.GLASS_TINT_HUE_SECONDARY.equals(hue)) return LookGrid.TINT_HUE_SECONDARY;
        if (TERMUX_APP.GLASS_TINT_HUE_TERTIARY.equals(hue)) return LookGrid.TINT_HUE_TERTIARY;
        return LookGrid.TINT_HUE_PRIMARY;
    }

    static int rimOf(@NonNull String rim) {
        if (TERMUX_APP.GLASS_RIM_GRADIENT.equals(rim)) return LookGrid.RIM_GRADIENT;
        if (TERMUX_APP.GLASS_RIM_HAIRLINE.equals(rim)) return LookGrid.RIM_HAIRLINE;
        return LookGrid.RIM_OFF;
    }

    /** Every old key this fold reads: Global, own keys and their page values, flags, retired keys. */
    private static void deleteOldKeys(@NonNull LookGrid.Store store) {
        List<String> plain = new ArrayList<>();
        for (OldProperty property : OldProperty.values()) {
            plain.add(property.globalKey);
            for (OldSlot slot : OldSlot.values()) plain.add(slot.ownKey(property));
        }
        for (String key : RETIRED) plain.add(key);
        for (String stored : store.keys()) {
            if (stored.startsWith(INHERIT_PREFIX)) {
                store.remove(stored);
                continue;
            }
            int at = stored.lastIndexOf(LookGrid.PAGE_SEPARATOR);
            String bare = at > 0 ? stored.substring(0, at) : stored;
            if (plain.contains(bare) && (at < 0 || isDigits(stored, at + 1))) store.remove(stored);
        }
    }

    private static boolean isDigits(@NonNull String key, int from) {
        if (from >= key.length()) return false;
        for (int i = from; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) return false;
        }
        return true;
    }
}
