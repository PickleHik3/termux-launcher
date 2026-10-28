package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The linked keys (#84, 2026-10-21): with Home's own layout on, the apps row's height, the Strip
 * row's height, the keyboard's height and chin, and Style, Corners and Margin are still one value
 * on both halves of the wall, edited from either, so nothing resizes on the swipe. Which rows
 * show, where and in what order, the Dock design and its toggles stay Home's own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutLinkedKeysTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-linked-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    /**
     * A store whose Home page holds a page layout of its own (where the Home layout stood before
     * version 16), the normal layout being the root store's view.
     */
    private PlaceLayoutStore withHomesOwn() {
        PlaceLayoutStore store = KindFixtures.withHomePageLayout(store());
        assertTrue(store.hasPageLayout(KindFixtures.HOME_ID));
        return store;
    }

    private static PlaceLayoutStore home(PlaceLayoutStore store) {
        return store.forVariant(KindFixtures.HOME_PAGE);
    }

    private static PlaceLayoutStore normal(PlaceLayoutStore store) {
        return store.forVariant(LayoutVariant.NORMAL);
    }

    @Test
    public void theAppsRowHeightIsOneValueOnBothHalves() {
        PlaceLayoutStore store = withHomesOwn();
        home(store).setDockHeightScale(PORTRAIT, 1.4f);
        assertEquals("set from Home, the window's follows", 1.4f,
            normal(store).dockHeightScale(PORTRAIT), 0.001f);
        normal(store).setDockHeightScale(LANDSCAPE, 0.8f);
        assertEquals("set from the window, Home's follows", 0.8f,
            home(store).dockHeightScale(LANDSCAPE), 0.001f);
        assertFalse("Home keeps no copy",
            prefs.contains(PlaceLayoutStore.layoutKey(LayoutVariant.HOME, PORTRAIT, "dock_height")));
    }

    @Test
    public void theStripRowHeightIsOneValueOnBothHalves() {
        PlaceLayoutStore store = withHomesOwn();
        home(store).setWallStripHeightDp(PORTRAIT, 60);
        assertEquals(60, normal(store).wallStripHeightDp(PORTRAIT));
        normal(store).setWallStripHeightDp(LANDSCAPE, 64);
        assertEquals(64, home(store).wallStripHeightDp(LANDSCAPE));
        assertFalse(prefs.contains(
            PlaceLayoutStore.layoutKey(LayoutVariant.HOME, PORTRAIT, "wall_strip_height")));
    }

    @Test
    public void theKeyboardsHeightAndChinAreOneValueOnBothHalves() {
        PlaceLayoutStore store = withHomesOwn();
        home(store).setKeyboardHeightScale(PORTRAIT, 1.2f);
        home(store).setKeyboardChinDp(PORTRAIT, 24);
        assertEquals(1.2f, normal(store).keyboardHeightScale(PORTRAIT), 0.001f);
        assertEquals(24, normal(store).keyboardChinDp(PORTRAIT));
        normal(store).setKeyboardHeightScale(LANDSCAPE, 0.9f);
        normal(store).setKeyboardChinDp(LANDSCAPE, 8);
        assertEquals(0.9f, home(store).keyboardHeightScale(LANDSCAPE), 0.001f);
        assertEquals(8, home(store).keyboardChinDp(LANDSCAPE));
    }

    @Test
    public void theKeyboardsFormStaysHomesOwn() {
        PlaceLayoutStore store = withHomesOwn();
        home(store).setKeyboardForm(PORTRAIT, KeyboardForm.FLOATING);
        assertEquals(KeyboardForm.DOCKED, normal(store).keyboardForm(PORTRAIT));
        assertEquals(KeyboardForm.FLOATING, home(store).keyboardForm(PORTRAIT));
    }

    @Test
    public void theLinkedValuesComeFromTheNormalLayoutWhateverMinimalModeSays() {
        PlaceLayoutStore store = withHomesOwn();
        normal(store).setDockHeightScale(PORTRAIT, 1.3f);
        store.setMinimal(true);
        store.forVariant(LayoutVariant.MINIMAL).setDockHeightScale(PORTRAIT, 0.7f);
        assertEquals(1.3f, home(store).dockHeightScale(PORTRAIT), 0.001f);
        assertEquals(0.7f, store.dockHeightScale(PORTRAIT), 0.001f);
    }

    @Test
    public void styleCornersAndMarginAreOneValueOnBothHalves() {
        PlaceLayoutStore store = withHomesOwn();
        launcher.setLayoutStyle(true, LayoutStyle.FLOATING);
        launcher.setLayoutCornersDp(true, 30);
        launcher.setLayoutMarginDp(true, 20);
        assertEquals("set from Home, the window's follows",
            WallShape.of(LayoutStyle.FLOATING, 30, 20), WallShape.read(launcher));
        launcher.setLayoutCornersDp(false, 6);
        assertEquals("set from the window, Home's follows", 6, launcher.getLayoutCornersDp(true));
        assertFalse("Home keeps no copy", prefs.contains(TERMUX_APP.KEY_HOME_LAYOUT_STYLE)
            || prefs.contains(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS)
            || prefs.contains(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP));

        DressResolver dresses = DressResolver.of(store);
        assertEquals("Home's dress wears the Workspace's shape",
            dresses.dressOf(PaneWallPage.TERMINAL).shape(),
            dresses.dressOf(PaneWallPage.WIDGETS).shape());
        assertEquals(KindFixtures.HOME_PAGE, dresses.dressOf(PaneWallPage.WIDGETS).layout());
    }

    @Test
    public void rowsOrderDesignAndTogglesStillDiffer() {
        PlaceLayoutStore store = withHomesOwn();
        Slot apps = normal(store).slot(PORTRAIT, Element.APPS);
        home(store).setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.TOP, Element.APPS));
        home(store).setAzRowShown(PORTRAIT, false);
        home(store).setSlotOrder(PORTRAIT, Element.STATUS, 3);
        home(store).setDockStyle(DockStyle.LENS_BEADS);
        home(store).setSearchField(true);
        home(store).setDrawerTile(true);

        assertEquals(apps, normal(store).slot(PORTRAIT, Element.APPS));
        assertTrue(normal(store).azRowShown(PORTRAIT));
        assertNotEquals(3, normal(store).slotOrder(PORTRAIT, Element.STATUS));
        assertEquals(DockStyle.GLASS_TRAY, normal(store).dockStyle());
        assertFalse(normal(store).searchField());
        assertFalse(normal(store).drawerTile());
        assertTrue(store.arrangementsDiffer(LayoutVariant.HOME, LayoutVariant.NORMAL));
    }

    @Test
    public void turnedOnTheHomeLayoutCopiesNoLinkedKeyAndNoShape() {
        PlaceLayoutStore store = store();
        store.setDockHeightScale(PORTRAIT, 1.3f);
        store.setKeyboardChinDp(PORTRAIT, 12);
        store.setWallStripHeightDp(PORTRAIT, 72);
        store.setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.TOP, Element.APPS));
        launcher.setLayoutMarginDp(false, 14);

        store.setHomeLayout(true);
        for (String key : new String[] {"dock_height", "keyboard_height", "keyboard_chin",
                "wall_strip_height"}) {
            assertFalse(key,
                prefs.contains(PlaceLayoutStore.layoutKey(LayoutVariant.HOME, PORTRAIT, key)));
        }
        assertFalse(prefs.contains(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP));
        assertEquals("the arrangement is still copied", Edge.TOP,
            home(store).slot(PORTRAIT, Element.APPS).edge);
        assertEquals(1.3f, home(store).dockHeightScale(PORTRAIT), 0.001f);
        assertEquals(72, home(store).wallStripHeightDp(PORTRAIT));
        assertEquals(14, launcher.getLayoutMarginDp(true));
    }

    @Test
    public void aHomeSnapshotPutsTheSharedSizesBack() {
        PlaceLayoutStore store = withHomesOwn();
        normal(store).setDockHeightScale(PORTRAIT, 1.1f);
        PlaceArrangeSnapshot entry = PlaceArrangeSnapshot.capture(store, LayoutVariant.HOME);
        home(store).setDockHeightScale(PORTRAIT, 1.5f);
        assertNotEquals(entry.signature(),
            PlaceArrangeSnapshot.capture(store, LayoutVariant.HOME).signature());
        entry.restore(store);
        assertEquals(1.1f, normal(store).dockHeightScale(PORTRAIT), 0.001f);
    }

    // ------------------------------------------------------------------ migration

    /** A version 8 install whose Home layout was seeded from the normal one. */
    private SharedPreferences.Editor version8WithHomeAsTheWorkspace() {
        SharedPreferences.Editor editor = prefs.edit().putInt("place.migrated", 8);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            String o = orientation.storageValue();
            String apps = orientation == PORTRAIT ? "bottom" : "left";
            editor.putString("layout." + o + ".apps_row", apps)
                .putString("layout.home." + o + ".apps_row", apps)
                .putFloat("layout." + o + ".dock_height", 1.1f)
                .putFloat("layout.home." + o + ".dock_height", 1.1f)
                .putString("layout." + o + ".status_bar", "top")
                .putString("layout.home." + o + ".status_bar", "hidden")
                .putString("layout.home." + o + ".extra_keys", "hidden");
        }
        return editor;
    }

    @Test
    public void anUpgradeWhoseHomeDiffersOnlyInALinkedValueWearsTheSharedLayout() {
        version8WithHomeAsTheWorkspace()
            .putFloat("layout.home.landscape.dock_height", 0.8f)
            .putInt("layout.home.portrait.wall_strip_height", 60)
            .putBoolean("layout.home.keyboard_shown", true)
            .putFloat("layout.home.portrait.keyboard_height", 1.3f)
            .putInt("layout.home.portrait.keyboard_chin", 40)
            .putInt(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP, 30)
            .putString(TERMUX_APP.KEY_HOME_LAYOUT_STYLE, "floating")
            .commit();
        PlaceLayoutStore store = KindFixtures.onHomePage(store());
        // Version 9 put Home on the shared layout; version 14 gave it a copy of that layout;
        // version 16 finds the copy equal and puts Home back on the shared one.
        assertFalse(store.hasHomeLayout());
        assertFalse(store.hasPageLayout(KindFixtures.HOME_ID));
        assertEquals(1.1f, store.forVariant(LayoutVariant.HOME).dockHeightScale(LANDSCAPE),
            0.001f);
        assertFalse(prefs.contains(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP));
        assertFalse(prefs.contains(TERMUX_APP.KEY_HOME_LAYOUT_STYLE));
        assertFalse(prefs.contains("layout.home.landscape.dock_height"));
    }

    @Test
    public void aKeptHomeLayoutDropsItsLinkedCopiesAndWearsTheShared() {
        version8WithHomeAsTheWorkspace()
            .putString("layout.home.portrait.apps_row", "hidden")
            .putFloat("layout.home.portrait.dock_height", 0.7f)
            .putInt(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS, 40)
            .commit();
        PlaceLayoutStore store = KindFixtures.onHomePage(store());
        // The apps row put away is visibility, the Home kind's since version 15, not an
        // arrangement: version 16 leaves Home on the shared layout, and nothing moves.
        assertFalse(store.hasHomeLayout());
        assertFalse(store.hasPageLayout(KindFixtures.HOME_ID));
        assertTrue(store.resolve(PORTRAIT, PaneWallPage.WIDGETS).slot(Element.APPS).hidden);
        assertEquals(1.1f, store.forVariant(LayoutVariant.HOME).dockHeightScale(PORTRAIT),
            0.001f);
        assertFalse(prefs.contains("layout.home.portrait.dock_height"));
        assertFalse(prefs.contains(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS));
        assertEquals("hidden", prefs.getString("layout.home.portrait.apps_row", null));
    }

    /** An install already on version 12 (this branch before #84's linking) loses Home's copies. */
    @Test
    public void aVersion12HomeLayoutDropsItsLinkedCopiesAndStaysOn() {
        SharedPreferences.Editor editor = prefs.edit().putInt("place.migrated", 12)
            .putBoolean("place.home_layout", true)
            .putInt(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP, 30)
            .putString(TERMUX_APP.KEY_HOME_LAYOUT_STYLE, "floating")
            .putInt(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS, 40);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            String o = orientation.storageValue();
            editor.putFloat("layout." + o + ".dock_height", 1.0f)
                .putFloat("layout.home." + o + ".dock_height", 1.4f)
                .putFloat("layout.home." + o + ".keyboard_height", 1.2f)
                .putInt("layout.home." + o + ".keyboard_chin", 30)
                .putInt("layout.home." + o + ".wall_strip_height", 60)
                .putString("layout.home." + o + ".apps_row", "top");
        }
        editor.commit();
        PlaceLayoutStore store = store();
        assertEquals(PlaceLayoutStore.MIGRATION_VERSION, prefs.getInt("place.migrated", 0));
        assertTrue("Home's rows live on as the Home page's own layout",
            KindFixtures.onHomePage(store).hasPageLayout(KindFixtures.HOME_ID));
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            String o = orientation.storageValue();
            for (String key : new String[] {"dock_height", "keyboard_height", "keyboard_chin",
                    "wall_strip_height"}) {
                assertFalse(o + " " + key, prefs.contains("layout.home." + o + "." + key));
            }
            assertEquals("Home's rows are kept", "top",
                prefs.getString("layout.home." + o + ".apps_row", null));
            assertEquals(1.0f, home(store).dockHeightScale(orientation), 0.001f);
        }
        assertFalse(prefs.contains(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP));
        assertFalse(prefs.contains(TERMUX_APP.KEY_HOME_LAYOUT_STYLE));
        assertFalse(prefs.contains(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS));
    }
}
