package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Kind;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

/**
 * Writes each kind's shipped look into its kind cells, once (ADR 0018 §3), behind
 * {@link #KEY_SEEDED}. A kind's shipped look sits below Base in the read order, so on any install
 * holding a Base value (every one that has picked a Look) it would never show; as cells it does.
 *
 * <p>For every element and kind the shipped looks name ({@link LookGrid.ShippedLooks}, today the
 * keyboard: Mist on Home pages, Clear on Display pages), each of Frost, Opacity and Tint the kind
 * cell does not already hold is written. A cell the user holds is never overwritten. Grain is not
 * seeded: it follows the Frost stop. The values come from the supplier, which owns them.</p>
 *
 * <p>Runs last in the start-up chain, on fresh and upgraded installs alike, so no freshness check
 * that runs before it sees its keys.</p>
 */
public final class LookGridKindSeed {

    private LookGridKindSeed() {}

    public static final String KEY_SEEDED = "look_grid_kind_seeded";

    /** The properties a kind's shipped look is seeded for. */
    private static final Property[] SEEDED = {Property.FROST, Property.OPACITY, Property.TINT};

    /** Seeds the kind cells once; returns how many cells it wrote. */
    public static int run(@NonNull LookGrid.Store store, @NonNull LookGrid.ShippedLooks shipped) {
        if (store.getBoolean(KEY_SEEDED, false)) return 0;
        int written = 0;
        for (Element element : Element.values()) {
            for (Kind kind : Kind.values()) {
                for (Property property : SEEDED) {
                    Integer value = shipped.shipped(property, element, kind);
                    if (value == null) continue;
                    String key = LookGrid.key(property, element, kind);
                    if (store.contains(key)) continue;
                    store.putInt(key, property.clamp(value));
                    written++;
                }
            }
        }
        store.putBoolean(KEY_SEEDED, true);
        return written;
    }
}
