package com.termux.app.place.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.WallPagesStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * The upgrade matrix of ADR 0020 §7: each fixture under {@code layout-upgrade/} is a launcher
 * store as a release left it (stable 1.2.2 at version 8, 1.3.0-pre.4 at 14, the v2 pre-releases
 * at 16). It goes through the whole migration chain, steps 1-16 then step 17, and the five
 * documents are compared field by field with {@code <fixture>-expected.json}: every field the
 * expected file names must hold that value, every path under {@code absent} must be absent, and
 * {@code prefs} / {@code prefs_absent} check the launcher preferences after the migration.
 */
@RunWith(RobolectricTestRunner.class)
// A 412 dp phone: the icon size's cap by the pinned apps is measured at the smallest width.
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "sw412dp-w412dp-h915dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutUpgradeMatrixTest {

    private Application app;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("layout-upgrade-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        app.getSharedPreferences(WallPagesStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        LayoutStore.prefs(app).edit().clear().commit();
    }

    @Test public void stable122() throws Exception { check("v8-stable-1.2.2"); }

    @Test public void stableDefaults() throws Exception { check("v8-defaults"); }

    @Test public void pre4() throws Exception { check("v14-pre4"); }

    @Test public void v2WithoutPageLayouts() throws Exception { check("v16-no-page-layouts"); }

    /** The 2026-11-01 phone: Home 1 with its own dock, Home 8 with none, the Terminal on 4. */
    @Test public void v2HomeOneWithItsOwnDock() throws Exception { check("v16-home1-own-dock"); }

    @Test public void v2StartingInTheTerminal() throws Exception { check("v16-start-in-terminal"); }

    @Test public void v2PageLooks() throws Exception { check("v16-page-looks"); }

    /** The developer's phone: a dock at 2.994 (about a 55 dp icon) folds to L 54, stored as the
     * choice; the seven pinned apps cap only what draws (developer, 2026-11-02). */
    @Test public void v2PhoneIconSize() throws Exception { check("v16-phone-icon-size"); }

    /** Home's own icon size wins over the dock's at the fold (spec §8a). */
    @Test public void v2HomeIconWins() throws Exception { check("v16-home-icon-wins"); }

    /** Five pinned apps: the phone's L is stored as folded. */
    @Test public void v2FivePinsKeepL() throws Exception { check("v16-five-pins-icon-size"); }

    /** One grid for both orientations (ADR 0020 §12): the portrait count wins, landscape goes. */
    @Test public void v2LandscapeGridDropped() throws Exception { check("v16-landscape-grid-dropped"); }

    /** 1.3.0-pre.4 with the shared dock on and Home's own layout's dock off: Home keeps it off. */
    @Test public void pre4HomeDockOff() throws Exception {
        check("v14-home-dock-off");
        com.termux.app.place.PlaceLayoutStore store = new com.termux.app.place.PlaceLayoutStore(
            new com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences(app, prefs, null));
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertFalse(orientation + ": Home shows no apps row",
                store.kindShows(com.termux.app.wall.WallPages.PageKind.HOME, orientation,
                    com.termux.app.place.Element.APPS));
            assertTrue(orientation + ": the Terminal keeps its dock",
                store.kindShows(com.termux.app.wall.WallPages.PageKind.TERMINAL, orientation,
                    com.termux.app.place.Element.APPS));
        }
    }

    /** An install that never used minimal mode: minimal still puts every element away. */
    @Test public void v2WithoutMinimalKeys() throws Exception {
        check("v16-no-minimal");
        com.termux.app.place.PlaceLayoutStore store = new com.termux.app.place.PlaceLayoutStore(
            new com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences(app, prefs, null));
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            com.termux.app.place.PlaceLayout minimal = store.resolve(orientation,
                com.termux.app.place.LayoutVariant.MINIMAL);
            for (com.termux.app.place.Element element : com.termux.app.place.Element.values())
                assertTrue(orientation + " " + element + " is put away in minimal",
                    minimal.slot(element).hidden);
        }
    }

    /** Step 17 reads the flat keys and leaves every one of them as it was (ADR 0020 §8). */
    @Test
    public void theFlatKeysAreLeftAlone() throws Exception {
        JSONObject fixture = load("v16-home1-own-dock.json");
        seed(fixture);
        Map<String, ?> before = new TreeMap<>(prefs.getAll());
        migrate();
        Map<String, Object> after = new TreeMap<>(prefs.getAll());
        for (Map.Entry<String, ?> e : before.entrySet()) {
            if (e.getKey().startsWith("layout.") || e.getKey().startsWith("place.page_layout."))
                assertEquals(e.getKey(), e.getValue(), after.get(e.getKey()));
        }
        assertEquals(17, prefs.getInt("place.migrated", 0));
    }

    /** A store already at 17 is not folded again: an edit made after the upgrade survives. */
    @Test
    public void stepSeventeenRunsOnce() throws Exception {
        seed(load("v16-home1-own-dock.json"));
        migrate();
        LayoutStore.prefs(app).edit().putString(LayoutKind.HOME.key,
            LayoutsJson.write(KindLayout.EMPTY.withWide(LayoutsJson.DOCK_DESIGN, "taskbar")))
            .commit();
        migrate();
        assertEquals("taskbar", layouts().kind(LayoutKind.HOME).wide()
            .string(LayoutsJson.DOCK_DESIGN));
    }

    private void check(String name) throws Exception {
        seed(load(name + ".json"));
        migrate();
        assertEquals(17, prefs.getInt("place.migrated", 0));
        Layouts layouts = layouts();
        JSONObject expected = load(name + "-expected.json");
        for (LayoutKind kind : LayoutKind.values()) {
            JSONObject doc = expected.optJSONObject(kind.key);
            if (doc != null) compare(name + " " + kind.key, layouts.kind(kind), doc);
        }
        JSONObject sizes = expected.optJSONObject("sizes");
        if (sizes != null) compare(name + " sizes", layouts.sizes().document(), sizes);
        JSONArray absent = expected.optJSONArray("absent");
        for (int i = 0; absent != null && i < absent.length(); i++) {
            String path = absent.getString(i);
            String[] parts = path.split("\\.", 3);
            KindLayout doc = "sizes".equals(parts[0]) ? layouts.sizes().document()
                : layouts.kind(LayoutKind.forKey(parts[0]));
            assertFalse(name + ": " + path + " should be absent", section(doc, parts[1]).has(parts[2]));
        }
        JSONObject after = expected.optJSONObject("prefs");
        if (after != null) {
            Map<String, ?> all = prefs.getAll();
            for (Iterator<String> it = after.keys(); it.hasNext(); ) {
                String key = it.next();
                assertTrue(name + ": pref " + key + " = " + all.get(key),
                    LayoutFields.sameValue(after.get(key), all.get(key)));
            }
        }
        JSONArray prefsAbsent = expected.optJSONArray("prefs_absent");
        for (int i = 0; prefsAbsent != null && i < prefsAbsent.length(); i++) {
            assertFalse(name + ": pref " + prefsAbsent.getString(i) + " should be absent",
                prefs.contains(prefsAbsent.getString(i)));
        }
    }

    private static void compare(String what, KindLayout actual, JSONObject expected)
        throws Exception {
        assertEquals(what + " version", KindLayout.VERSION, actual.version);
        for (String section : new String[]{"wide", "portrait", "landscape"}) {
            JSONObject fields = expected.optJSONObject(section);
            if (fields == null) continue;
            LayoutFields have = section(actual, section);
            for (Iterator<String> it = fields.keys(); it.hasNext(); ) {
                String key = it.next();
                assertTrue(what + " " + section + "." + key + " expected " + fields.get(key)
                        + " but was " + have.get(key),
                    LayoutFields.sameValue(fields.get(key), have.get(key)));
            }
        }
    }

    private static LayoutFields section(KindLayout doc, String section) {
        switch (section) {
            case "wide": return doc.wide();
            case "landscape": return doc.orientation(PlaceOrientation.LANDSCAPE);
            default: return doc.orientation(PlaceOrientation.PORTRAIT);
        }
    }

    private void seed(JSONObject fixture) throws Exception {
        String wall = fixture.optString("wall_pages", null);
        if (wall != null) {
            app.getSharedPreferences(WallPagesStore.PREFS, Context.MODE_PRIVATE).edit()
                .putString(WallPagesStore.KEY_STATE, wall).commit();
        }
        JSONObject values = fixture.getJSONObject("prefs");
        SharedPreferences.Editor editor = prefs.edit();
        for (Iterator<String> it = values.keys(); it.hasNext(); ) {
            String key = it.next();
            Object value = values.get(key);
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Number) editor.putFloat(key, ((Number) value).floatValue());
            else editor.putString(key, value.toString());
        }
        editor.commit();
    }

    /**
     * Step 17 alone: the shared-chrome fold (ADR 0023 §1) has its own test, so it is marked done
     * here and the fixtures keep showing what step 17 wrote.
     */
    private void migrate() {
        prefs.edit().putBoolean(com.termux.app.place.layout.SharedChromeFold.KEY_MIGRATED, true)
            .commit();
        new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    private Layouts layouts() {
        return new LayoutStore(new LayoutStore.PrefsStorage(LayoutStore.prefs(app)), false)
            .current();
    }

    private JSONObject load(String file) throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("layout-upgrade/" + file);
        assertNotNull(file, in);
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }
}
