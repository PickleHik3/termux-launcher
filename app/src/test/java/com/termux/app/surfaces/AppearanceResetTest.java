package com.termux.app.surfaces;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.layout.KindLayout;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.Layouts;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.app.setup.AppearanceResetCard;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGridMarginMigration;
import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The appearance reset on upgrade: an upgraded install whose look was its own reads, after the
 * start-up chain, exactly what a new install reads; nothing else it holds changes; it runs once
 * per reset number; a new install is never reset; the card is due once and settles.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppearanceResetTest {

    private Application app;

    /** Values an earlier build left that the reset must keep, flat store. */
    private static final Map<String, Object> SURVIVORS = new LinkedHashMap<>();
    static {
        SURVIVORS.put("setup_wizard_version", 3);
        SURVIVORS.put("first_boot_tour_completed_version", 1);
        SURVIVORS.put("use_as", "launcher");
        SURVIVORS.put("first_run_chain_done", true);
        SURVIVORS.put("first_run_permissions_card_seen", true);
        SURVIVORS.put("first_run_appearance_theme_hint_seen", true);
        SURVIVORS.put("app_launcher_pinned_items_v2", "[{\"pkg\":\"a.b\"}]");
        SURVIVORS.put("app_launcher_pinned_items_schema_version", 2);
        SURVIVORS.put("in_app_keyboard_layouts", "latn_qwerty_us");
        SURVIVORS.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_ENABLED, true);
        SURVIVORS.put("in_app_keyboard_extra_keys", "[[\"ESC\"]]");
        SURVIVORS.put("in_app_keyboard_font_path", "/fonts/k.ttf");
        SURVIVORS.put("layout.portrait.voice_pill_x", 0.25f);
        SURVIVORS.put("layout.portrait.voice_pill_y", 0.75f);
        SURVIVORS.put("layout.portrait.keyboard_float_x", 0.4f);
        SURVIVORS.put(TERMUX_APP.KEY_HOME_PILL_X, 0.3f);
        SURVIVORS.put(TERMUX_APP.KEY_HOME_PILL_Y, 0.6f);
        SURVIVORS.put("status_widget_weather_location", "Lisbon");
        SURVIVORS.put("status_widget_weather_location_coords", "38.7,-9.1");
        SURVIVORS.put("current_session", "a-session");
        SURVIVORS.put("app_launcher_icon_pack_package", "com.icons");
        SURVIVORS.put("wall_pages_migration_version", 2);
        SURVIVORS.put("page_look_upgrade_version", 1);
        SURVIVORS.put("status_bar_cutout_migrated", true);
        SURVIVORS.put("extra_keys_default_offered_1", true);
    }

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        LayoutStore.prefs(app).edit().clear().commit();
        LayoutStore.shared(app).load();
        app.getSharedPreferences(com.termux.app.fonts.FontSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    // ------------------------------------------------------------------------------ the chain

    /**
     * TermuxApplication's look steps in its order, the reset among them when {@code withReset},
     * then the launcher's first layout read (the activity builds a PlaceLayoutStore).
     */
    private void startUp(SharedPreferences store, TermuxAppSharedPreferences prefs,
                         boolean withReset) {
        boolean fresh = prefs.isFreshInstall();
        com.termux.app.WallReworkMigration.migrateIfNeeded(store);
        store.edit().putInt(PageLookKeys.KEY_TERMINAL_PAGE_ID, 0).apply();
        boolean lookFresh = (withReset && AppearanceReset.runAtStartUp(app, prefs, fresh)) || fresh;
        prefs.migrateLookGrid(Collections.emptyList(), lookFresh);
        LookGrid.PrefsStore grid = new LookGrid.PrefsStore(store);
        GlassControlsMigration.runAtStartUp(prefs, lookFresh);
        LookGridMarginMigration.run(grid);
        new PlaceLayoutStore(prefs);
    }

    private SharedPreferences store(String name) {
        SharedPreferences store = app.getSharedPreferences(name, Context.MODE_PRIVATE);
        store.edit().clear().commit();
        return store;
    }

    /** A new install after its first start: the flat store and the layouts. */
    private static final class Fresh {
        Map<String, Object> flat;
        Layouts layouts;
    }

    private Fresh freshInstall() {
        SharedPreferences store = store("appearance-reset-fresh");
        startUp(store, new TermuxAppSharedPreferences(app, store, null), true);
        Fresh fresh = new Fresh();
        fresh.flat = new TreeMap<>(store.getAll());
        fresh.layouts = LayoutStore.shared(app).load();
        // The next store starts from no layouts file, as the fresh one did.
        LayoutStore.prefs(app).edit().clear().commit();
        LayoutStore.shared(app).load();
        return fresh;
    }

    /**
     * An install an earlier build (no reset number) ran and the user dressed: a value off the
     * default in every group the editor owns, page overrides, place layouts and dress, the font.
     */
    private SharedPreferences upgradedAndCustomised(TermuxAppSharedPreferences[] out) {
        SharedPreferences store = store("appearance-reset-upgraded");
        store.edit().putString("current_session", "a-session").commit();
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(app, store, null);
        startUp(store, prefs, false);
        out[0] = prefs;

        SharedPreferences.Editor e = store.edit();
        for (Map.Entry<String, Object> survivor : SURVIVORS.entrySet()) put(e, survivor);
        // Glass, depth, tint, rim, motion, hue, vibrancy, material, positions.
        e.putInt(TERMUX_APP.KEY_FANCIER_GLASS_BEND, 12).putInt(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, 30)
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR, 7).putInt(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY, 5)
            .putString(TERMUX_APP.KEY_SURFACE_MATERIAL, "frosted").putInt(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, 33)
            .putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT, "warm").putString(TERMUX_APP.KEY_SURFACE_GLASS_RIM, "bright")
            .putString(TERMUX_APP.KEY_SURFACE_GLASS_MOTION, "still").putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE, "200")
            .putInt(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, 80).putInt(TERMUX_APP.KEY_SURFACE_GLASS_POSITION, 61)
            .putString(TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET, "{\"look.frost\":9}")
            // Look grid cells, a page's own cell and a surface link.
            .putInt("look.frost", 9).putInt("look.opacity.dock", 77).putInt("look.tint.keyboard@kind:home", 40)
            .putInt("look.opacity@3", 55).putString("surface_inherit_dock_blur", "detached")
            // Dock: style, buttons, legacy keys.
            .putString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, "floating").putInt(TERMUX_APP.KEY_APP_LAUNCHER_BUTTON_COUNT, 6)
            .putInt(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 22).putInt(TERMUX_APP.KEY_APP_BAR_OPACITY, 70)
            // Terminal.
            .putBoolean(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED, true).putInt(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, 20)
            .putInt(TERMUX_APP.KEY_TERMINAL_PANE_GAP, 14).putString(TERMUX_APP.KEY_TERMINAL_PANE_BORDER_COLOUR, "custom")
            .putBoolean(TERMUX_APP.KEY_TERMINAL_PANE_GLOW, true).putString(TERMUX_APP.KEY_TERMINAL_CONTRAST_LEVEL, "high")
            .putBoolean(TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED, true)
            .putBoolean(TERMUX_APP.KEY_TERMINAL_CURSOR_TRAIL, true).putString(TERMUX_APP.KEY_TERMINAL_RETRO_EFFECT, "crt")
            .putInt(TERMUX_APP.KEY_TEXT_SIZE_PERCENT, 130).putString(TERMUX_APP.KEY_FONTSIZE + "0", "18")
            .putBoolean(TERMUX_APP.KEY_DWINDLE_DEFAULT_LAYOUT, true).putBoolean(TERMUX_APP.KEY_FOCUSED_PANE_GROWS, true)
            // Wallpaper dim and soft, wallpaper mode's glass.
            .putInt(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, 40).putBoolean(SoftWallpaper.KEY_WALLPAPER_SOFT, true)
            .putInt(TERMUX_APP.KEY_WALLPAPER_ENABLED_TERMINAL_BACKGROUND_OPACITY, 90)
            // Keyboard.
            .putInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY, 30).putBoolean(TERMUX_APP.KEY_IN_APP_KEYBOARD_OPACITY_LINKED, false)
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, 9f)
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE, 1.4f)
            .putString(TERMUX_APP.KEY_IN_APP_KEYBOARD_THEME, "dark")
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.2f)
            // Status bar.
            .putString(TERMUX_APP.KEY_TOP_PANE_CLOCK_STYLE, "digital").putString(TERMUX_APP.KEY_TOP_PANE_CLOCK_ALIGNMENT, "end")
            .putBoolean(TERMUX_APP.KEY_STATUS_CLOCK_SECONDS, true).putBoolean(TERMUX_APP.KEY_STATUS_WIDGET_CPU, true)
            .putBoolean(TERMUX_APP.KEY_STATUS_WIDGET_WEATHER, true)
            // A place's dress as its flat copy (kept for a downgrade before the reset).
            .putString("place.dress.home.portrait.extra_keys", "true")
            .putString("layout.home.portrait.status_bar", "bottom")
            .commit();

        // The layouts file: Home's keys on, a taller dock, another design, a widget grid.
        LayoutStore documents = LayoutStore.shared(app);
        documents.replaceAll(documents.current()
            .withKind(LayoutKind.HOME, documents.current().kind(LayoutKind.HOME)
                .with(PlaceOrientation.PORTRAIT, LayoutsJson.dressField(com.termux.app.place.Element.EXTRA_KEYS), true)
                .withWide(LayoutsJson.DOCK_DESIGN, DockStyle.LENS_BEADS.storageValue())
                .with(PlaceOrientation.PORTRAIT, LayoutsJson.WIDGET_COLUMNS, 5)
                .with(PlaceOrientation.PORTRAIT, LayoutsJson.WIDGET_ROWS, 7))
            .withSizes(documents.current().sizes().with(PlaceOrientation.PORTRAIT,
                LayoutsJson.DOCK_HEIGHT, 92)));

        // The font.
        app.getSharedPreferences(com.termux.app.fonts.FontSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("active_family_id", "jetbrains").putString("ligatures", "on")
            .putInt("weight", 500).putBoolean("metered_warning_acknowledged", true).commit();
        return store;
    }

    private static void put(SharedPreferences.Editor e, Map.Entry<String, Object> entry) {
        Object v = entry.getValue();
        if (v instanceof Integer) e.putInt(entry.getKey(), (Integer) v);
        else if (v instanceof Boolean) e.putBoolean(entry.getKey(), (Boolean) v);
        else if (v instanceof Float) e.putFloat(entry.getKey(), (Float) v);
        else e.putString(entry.getKey(), (String) v);
    }

    // ------------------------------------------------------------------------------ (a) parity

    @Test
    public void anUpgradedLookReadsExactlyAsANewInstallsAfterTheReset() {
        Fresh fresh = freshInstall();
        TermuxAppSharedPreferences[] prefs = new TermuxAppSharedPreferences[1];
        SharedPreferences store = upgradedAndCustomised(prefs);
        Layouts before = LayoutStore.shared(app).current();

        startUp(store, prefs[0], true);

        // Every flat value but the kept ones and the reset's own bookkeeping is a new install's.
        Map<String, Object> after = new TreeMap<>(store.getAll());
        for (String kept : SURVIVORS.keySet()) after.remove(kept);
        assertEquals(Integer.valueOf(AppearanceReset.CARD_DUE), after.remove(AppearanceReset.KEY_CARD));
        assertEquals(Integer.valueOf(GlassResetNudge.SETTLED), after.remove(GlassResetNudge.KEY));
        assertEquals("every appearance value", fresh.flat, after);

        // The layouts: a new install's, its dock on the new-install design, the widget grid kept.
        Layouts layouts = LayoutStore.shared(app).current();
        KindLayout expectedHome = fresh.layouts.kind(LayoutKind.HOME)
            .withWide(LayoutsJson.DOCK_DESIGN, DockStyle.NEW_INSTALL_DEFAULT.storageValue())
            .with(PlaceOrientation.PORTRAIT, LayoutsJson.WIDGET_COLUMNS, 5)
            .with(PlaceOrientation.PORTRAIT, LayoutsJson.WIDGET_ROWS, 7);
        assertEquals(expectedHome, layouts.kind(LayoutKind.HOME));
        for (LayoutKind kind : LayoutKind.values()) {
            if (kind == LayoutKind.HOME || kind.retired()) continue;
            assertEquals(kind + "'s layout", fresh.layouts.kind(kind), layouts.kind(kind));
        }
        assertEquals("the sizes, dock height among them", fresh.layouts.sizes(), layouts.sizes());
        assertNotEquals(before, layouts);
        assertFalse("Home's keys are off again", Boolean.TRUE.equals(layouts.kind(LayoutKind.HOME)
            .orientation(PlaceOrientation.PORTRAIT)
            .get(LayoutsJson.dressField(com.termux.app.place.Element.EXTRA_KEYS))));

        // The font: no family; the metered-download answer stays.
        Map<String, ?> fonts = app.getSharedPreferences(com.termux.app.fonts.FontSettings.PREFS_NAME,
            Context.MODE_PRIVATE).getAll();
        assertFalse(fonts.containsKey("active_family_id"));
        assertFalse(fonts.containsKey("weight"));
        assertEquals(Boolean.TRUE, fonts.get("metered_warning_acknowledged"));
        // Clear, as the editor lists it.
        assertTrue(SurfacePresets.matches(prefs[0], clear()));
    }

    // ------------------------------------------------------------------------------ (b) survival

    @Test
    public void everythingElseTheInstallHoldsIsUntouched() {
        TermuxAppSharedPreferences[] prefs = new TermuxAppSharedPreferences[1];
        SharedPreferences store = upgradedAndCustomised(prefs);
        SharedPreferences wall = app.getSharedPreferences(com.termux.app.wall.WallPagesStore.PREFS, Context.MODE_PRIVATE);
        SharedPreferences ai = app.getSharedPreferences("termux_ai", Context.MODE_PRIVATE);
        SharedPreferences widgets = app.getSharedPreferences("launcher_widget_repository", Context.MODE_PRIVATE);
        wall.edit().putString("unrelated", "kept").commit();
        ai.edit().putString("model", "kept").commit();
        widgets.edit().putString("widgets", "[1,2]").commit();
        int placeMigrated = store.getInt("place.migrated", -1);
        int wallMigration = store.getInt(TERMUX_APP.KEY_WALL_MIGRATION_VERSION, -1);

        startUp(store, prefs[0], true);

        for (Map.Entry<String, Object> survivor : SURVIVORS.entrySet())
            assertEquals(survivor.getKey(), survivor.getValue(), store.getAll().get(survivor.getKey()));
        assertEquals(placeMigrated, store.getInt("place.migrated", -2));
        assertEquals(wallMigration, store.getInt(TERMUX_APP.KEY_WALL_MIGRATION_VERSION, -2));
        assertEquals("kept", wall.getString("unrelated", null));
        assertEquals("kept", ai.getString("model", null));
        assertEquals("[1,2]", widgets.getString("widgets", null));
    }

    @Test
    public void theScopeNamesBothSidesOfEverySharedPrefix() {
        assertTrue(AppearanceReset.inScope("status_widget_cpu"));
        assertFalse(AppearanceReset.inScope("status_widget_weather_location"));
        assertTrue(AppearanceReset.inScope(TERMUX_APP.KEY_IN_APP_KEYBOARD_THEME));
        assertFalse(AppearanceReset.inScope("in_app_keyboard_layouts"));
        assertFalse(AppearanceReset.inScope(TERMUX_APP.KEY_IN_APP_KEYBOARD_ENABLED));
        assertTrue(AppearanceReset.inScope("place.dress.home.portrait.extra_keys"));
        assertFalse(AppearanceReset.inScope("place.migrated"));
        assertFalse(AppearanceReset.inScope("layout.portrait.voice_pill_x"));
        assertFalse(AppearanceReset.inScope(TERMUX_APP.KEY_HOME_PILL_X));
        assertFalse(AppearanceReset.inScope(GlassResetNudge.KEY));
        assertFalse(AppearanceReset.inScope(AppearanceReset.KEY_SEEN));
    }

    @Test
    public void everyValueTheEditorsSnapshotWritesIsInScope() {
        TermuxAppSharedPreferences[] prefs = new TermuxAppSharedPreferences[1];
        upgradedAndCustomised(prefs);
        SharedPreferences empty = store("appearance-reset-snapshot");
        AppearanceSnapshot.capture(prefs[0]).restore(new TermuxAppSharedPreferences(app, empty, null));
        Set<String> outside = new HashSet<>();
        for (String key : empty.getAll().keySet())
            if (!AppearanceReset.inScope(key)) outside.add(key);
        assertEquals(Collections.emptySet(), outside);
    }

    // ------------------------------------------------------------------------------ (c) once

    @Test
    public void aSecondStartChangesNothing() {
        TermuxAppSharedPreferences[] prefs = new TermuxAppSharedPreferences[1];
        SharedPreferences store = upgradedAndCustomised(prefs);
        startUp(store, prefs[0], true);
        Map<String, Object> first = new HashMap<>(store.getAll());
        Layouts layouts = LayoutStore.shared(app).current();

        startUp(store, prefs[0], true);

        // As on a new install's second start, the two grid migrations mark themselves done.
        Map<String, Object> second = new HashMap<>(store.getAll());
        assertEquals(Boolean.TRUE, second.remove("look_grid_migrated"));
        assertEquals(first, second);
        assertEquals(layouts, LayoutStore.shared(app).current());
    }

    @Test
    public void aNewInstallIsNotResetAndGetsNoCard() {
        SharedPreferences store = store("appearance-reset-new");
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(app, store, null);

        startUp(store, prefs, true);

        assertEquals(AppearanceReset.NUMBER, store.getInt(AppearanceReset.KEY_SEEN, 0));
        assertFalse(store.contains(AppearanceReset.KEY_CARD));
        assertFalse(AppearanceReset.isCardDue(store));
        assertTrue(SurfacePresets.matches(prefs, clear()));
    }

    // ------------------------------------------------------------------------------ (d) raised

    @Test
    public void aHigherNumberThanTheInstallSawResetsAgain() {
        TermuxAppSharedPreferences[] prefs = new TermuxAppSharedPreferences[1];
        SharedPreferences store = upgradedAndCustomised(prefs);
        startUp(store, prefs[0], true);
        AppearanceReset.settleCard(store);
        // The user dresses again; a later build carries a higher number than the one stored.
        store.edit().putInt(TERMUX_APP.KEY_FANCIER_GLASS_BEND, 12).putInt("look.frost", 9)
            .putInt(AppearanceReset.KEY_SEEN, AppearanceReset.NUMBER - 1).commit();

        startUp(store, prefs[0], true);

        assertFalse(store.getInt(TERMUX_APP.KEY_FANCIER_GLASS_BEND, 0) == 12);
        assertTrue(SurfacePresets.matches(prefs[0], clear()));
        assertTrue(AppearanceReset.isCardDue(store));
        assertEquals(AppearanceReset.NUMBER, store.getInt(AppearanceReset.KEY_SEEN, 0));
    }

    // ------------------------------------------------------------------------------ (e) the card

    @Test
    public void theCardIsDueAfterAResetAndSettledByDismiss() {
        assertCardSettledBy(false);
    }

    @Test
    public void theCardIsDueAfterAResetAndSettledByOpeningTheEditor() {
        assertCardSettledBy(true);
    }

    private void assertCardSettledBy(boolean open) {
        TermuxAppSharedPreferences[] prefs = new TermuxAppSharedPreferences[1];
        SharedPreferences store = upgradedAndCustomised(prefs);
        startUp(store, prefs[0], true);
        assertTrue(AppearanceResetCard.isDue(prefs[0]));

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getTheme().applyStyle(com.google.android.material.R.style.Theme_MaterialComponents_DayNight, true);
        boolean[] opened = {false};
        assertTrue(AppearanceResetCard.showIfDue(activity, prefs[0], () -> opened[0] = true));
        ViewGroup content = activity.findViewById(android.R.id.content);
        View button = find(content, app.getString(open
            ? com.termux.R.string.appearance_reset_card_open
            : com.termux.R.string.appearance_reset_card_dismiss));
        button.performClick();

        assertEquals(open, opened[0]);
        assertFalse(AppearanceResetCard.isDue(prefs[0]));
        assertEquals(AppearanceReset.CARD_SETTLED, store.getInt(AppearanceReset.KEY_CARD, 0));
        assertEquals(null, content.findViewWithTag(AppearanceResetCard.class));
        assertFalse(AppearanceResetCard.showIfDue(activity, prefs[0], () -> {}));
    }

    private static View find(View view, CharSequence text) {
        if (view instanceof TextView && text.toString().equals(((TextView) view).getText().toString()))
            return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static SurfacePresets.Preset clear() {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if ("minimal".equals(preset.id)) return preset;
        throw new AssertionError("Clear is a shipped Look");
    }
}
