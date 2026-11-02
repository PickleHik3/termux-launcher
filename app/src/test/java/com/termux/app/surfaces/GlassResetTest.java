package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.termux.AppearanceSettingsStore;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;
import java.util.TreeMap;

/** Reset everything's scope (overhaul §6): glass and colours only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class GlassResetTest {

    private Context context;
    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store = context.getSharedPreferences("glass-reset", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private static Map<String, Object> cells(SharedPreferences prefs) {
        Map<String, Object> out = new TreeMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (LookGrid.parse(entry.getKey()) != null) out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    @Test
    public void resetClearsTheGridKeepsLayoutsAndAppliesThePreset() {
        // The user's glass: a Base value, an element on a kind, and the accent.
        store.edit()
            .putInt(LookGrid.key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE), 3)
            .putInt(LookGrid.key(LookGrid.Property.FROST, LookGrid.Element.DOCK,
                LookGrid.Kind.HOME), 4)
            .putInt(LookGrid.key(LookGrid.Property.TINT_HUE, null, LookGrid.NO_PAGE),
                LookGrid.TINT_HUE_TERTIARY)
            .commit();
        preferences.setTerminalContrastLevel("harder");
        AppearanceSettingsStore.putBoolean(context, GlassReset.KEY_DYNAMIC_COLOUR, true);
        // The user's layout and another kept setting.
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);
        places.setKindShows(PageKind.HOME, PlaceOrientation.PORTRAIT, Element.AZ, false);
        store.edit().putString("unrelated_kept", "kept").commit();

        GlassReset.reset(context, preferences, 1);

        assertFalse(places.kindShows(PageKind.HOME, PlaceOrientation.PORTRAIT, Element.AZ));
        assertEquals("kept", store.getString("unrelated_kept", null));
        assertEquals("default", preferences.getTerminalContrastLevel().value);
        assertFalse(AppearanceSettingsStore.getBoolean(context, GlassReset.KEY_DYNAMIC_COLOUR,
            true));

        // The grid holds exactly what the Look writes on an untouched install.
        SharedPreferences fresh = context.getSharedPreferences("glass-reset-fresh",
            Context.MODE_PRIVATE);
        fresh.edit().clear().commit();
        TermuxAppSharedPreferences freshPrefs = new TermuxAppSharedPreferences(context, fresh,
            null);
        SurfacePresets.applyLook(freshPrefs, AppearanceLooks.presetForStop(
            SurfacePresets.presets(freshPrefs), 1));
        assertEquals(cells(fresh), cells(store));
    }
}
