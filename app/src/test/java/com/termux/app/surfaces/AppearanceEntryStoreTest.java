package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomeSettings;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

/**
 * The editor's entry state survives a killed launcher: both snapshots go through JSON whole, and
 * the store is written at open, removed at Done, and put back at the next start when left over.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppearanceEntryStoreTest {

    private static final class MapStore implements EditorSettingsSnapshot.Store {
        final Map<String, Object> values = new HashMap<>();

        @Nullable @Override public String getString(@NonNull String key) {
            return (String) values.get(key);
        }

        @Override public void putString(@NonNull String key, @Nullable String value) {
            values.put(key, value);
        }

        @Override public boolean getBoolean(@NonNull String key) {
            Object value = values.get(key);
            return value == null ? EditorSettingsSnapshot.defaultOf(key) : (Boolean) value;
        }

        @Override public void putBoolean(@NonNull String key, boolean value) {
            values.put(key, value);
        }
    }

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("appearance-entry-store-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    /** Non-default values in fields of every kind: ints, booleans, strings, floats, arrays, maps. */
    private void moveEverything(int step) {
        SurfacePresets.apply(preferences, SurfacePresets.presets().get(step % 2 == 0 ? 1 : 3));
        preferences.setAppLauncherDockStyle(step % 2 == 0 ? TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DOCKED
            : TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_FLOATING);
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, 60 + step);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10 + step);
        preferences.setTerminalPaneBorderColour(step % 2 == 0 ? "custom" : "tertiary");
        preferences.setTerminalPaneBorderCustom(0xFFDC143C + step);
        preferences.setTerminalPaneGlow(step % 2 == 0);
        preferences.setInAppKeyboardKeyCornerRadiusDp(9f + step + 0.25f);
        preferences.setTextSizePercent(110 + step);
        preferences.setStatusWidgetCpuEnabled(step % 2 == 0);
        preferences.setSurfaceGlassPosition(SurfaceSlot.CANVAS, 3 + step);
        // A page's own keys of three types, and a look-grid cell.
        String sep = PageLookKeys.PAGE_SEPARATOR;
        store.edit()
            .putInt(SurfaceProperty.BLUR.baseKey + sep + "7", 20 + step)
            .putString(TERMUX_APP.KEY_SURFACE_MATERIAL + sep + "7", step % 2 == 0 ? "frost" : "clear")
            .commit();
        preferences.lookGrid().write(LookGrid.Property.TINT_HUE, null, LookGrid.NO_PAGE, 40 + step);
    }

    @Test
    public void theLookSnapshotRoundTripsThroughJson() throws Exception {
        moveEverything(0);
        // An absent key radius is NaN, and must come back absent.
        store.edit().remove(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP).commit();
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);
        JSONObject json = entry.toJson();
        assertTrue(json.getJSONObject("halfKeys").length() >= 2);
        assertTrue(json.getJSONObject("gridCells").length() >= 1);

        AppearanceSnapshot back = AppearanceSnapshot.fromJson(new JSONObject(json.toString()));
        assertEquals(entry.signature(), back.signature());

        moveEverything(1);
        assertNotEquals(entry.signature(), AppearanceSnapshot.signatureOf(preferences));
        back.restore(preferences);
        assertEquals(entry.signature(), AppearanceSnapshot.signatureOf(preferences));
        assertFalse(store.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP));
        assertEquals(20, store.getInt(SurfaceProperty.BLUR.baseKey + PageLookKeys.PAGE_SEPARATOR + "7", -1));
    }

    @Test
    public void theSettingsSnapshotRoundTripsThroughJsonNullsKept() throws Exception {
        MapStore settings = new MapStore();
        settings.values.put("theme_mode", "dark");
        settings.values.put(HomeSettings.KEY_ICON_SCALE, "120");
        settings.values.put("widgets_on_glass", true);
        EditorSettingsSnapshot entry = EditorSettingsSnapshot.capture(settings);
        EditorSettingsSnapshot back = EditorSettingsSnapshot.fromJson(
            new JSONObject(entry.toJson().toString()));
        assertEquals(entry.signature(), back.signature());

        settings.putString("theme_mode", "light");
        settings.putString("app_launcher_icon_pack_package", "some.pack");
        settings.putBoolean("widgets_on_glass", false);
        back.restore(settings);
        assertEquals("dark", settings.getString("theme_mode"));
        assertNull(settings.getString("app_launcher_icon_pack_package"));
        assertTrue(settings.getBoolean("widgets_on_glass"));
    }

    @Test
    public void openWritesDoneRemovesAndALeftoverIsPutBackAtStart() {
        moveEverything(0);
        MapStore settings = new MapStore();
        settings.values.put("theme_mode", "dark");
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);
        String signature = entry.signature();

        // Open, then Done: nothing is left behind.
        AppearanceEntryStore.save(preferences, entry, EditorSettingsSnapshot.capture(settings), null);
        assertTrue(AppearanceEntryStore.hasLeftover(preferences));
        AppearanceEntryStore.clear(preferences);
        assertFalse(AppearanceEntryStore.hasLeftover(preferences));
        assertFalse(AppearanceEntryStore.restoreLeftover(preferences, settings));

        // Open, change things live, and the process dies: the next start puts the entry back.
        AppearanceEntryStore.save(preferences, entry, EditorSettingsSnapshot.capture(settings),
            "saved-custom");
        moveEverything(1);
        settings.putString("theme_mode", "light");
        // A relaunch carrying the session on leaves it alone.
        assertFalse(AppearanceEntryStore.restoreLeftover(
            RuntimeEnvironment.getApplication(), preferences, true));
        assertTrue(AppearanceEntryStore.hasLeftover(preferences));

        assertTrue(AppearanceEntryStore.restoreLeftover(preferences, settings));
        assertEquals(signature, AppearanceSnapshot.signatureOf(preferences));
        assertEquals("dark", settings.getString("theme_mode"));
        assertEquals("saved-custom", preferences.getSurfaceCustomPreset());
        assertFalse(AppearanceEntryStore.hasLeftover(preferences));
    }

    @Test
    public void anUnreadableLeftoverIsDroppedNotRetried() {
        store.edit().putString(AppearanceEntryStore.PREFS_KEY, "{not json").commit();
        assertFalse(AppearanceEntryStore.restoreLeftover(preferences, new MapStore()));
        assertFalse(AppearanceEntryStore.hasLeftover(preferences));
    }
}
