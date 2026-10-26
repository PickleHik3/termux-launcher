package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.HashMap;
import java.util.Map;

/**
 * The Match rows (editor-redesign §1): Match Workspace styling and Match Home styling copy the
 * other screen's layout and glass, and Undo puts every key back as it stood.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ScreenMatchTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;
    private PlaceLayoutStore store;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("screen-match-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
        store = new PlaceLayoutStore(launcher);
        assertTrue("every install holds a Home layout", store.hasHomeLayout());

        PlaceLayoutStore normal = store.forVariant(LayoutVariant.NORMAL);
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        normal.setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.LEFT, 0));
        normal.setSlot(PORTRAIT, Element.EXTRA_KEYS, Slot.on(Edge.TOP, 0));
        normal.setSlot(PORTRAIT, Element.STATUS, Slot.hiddenFrom(Edge.TOP, 0));
        normal.setDockStyle(DockStyle.CHOICES.get(1));
        home.setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.BOTTOM, 0));
        home.setSlot(PORTRAIT, Element.STATUS, Slot.on(Edge.TOP, 0));
        home.setDockStyle(DockStyle.CHOICES.get(2));
        home.setWidgetColumns(PORTRAIT, 5);

        launcher.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE, 40);
        launcher.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME, 80);
        launcher.setSurfaceBaseValue(SurfaceProperty.TINT, WallHalf.HOME, 10);
        launcher.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, WallHalf.WORKSPACE,
            30);
    }

    @Test
    public void matchWorkspaceStylingGivesHomeTheWorkspacesLayoutAndGlass() {
        PlaceLayoutStore normal = store.forVariant(LayoutVariant.NORMAL);
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        assertNotNull(ScreenMatch.match(launcher, store, WallHalf.HOME));

        assertEquals(normal.slot(PORTRAIT, Element.APPS), home.slot(PORTRAIT, Element.APPS));
        assertTrue("the hidden stack is copied", home.slot(PORTRAIT, Element.STATUS).hidden);
        assertEquals(normal.dockStyle(), home.dockStyle());
        assertEquals("the widget grid is Home's alone", 5, home.widgetColumns(PORTRAIT));

        for (SurfaceProperty property : new SurfaceProperty[] {SurfaceProperty.OPACITY,
                SurfaceProperty.TINT, SurfaceProperty.BLUR})
            assertEquals(property.name(),
                launcher.getSurfaceBaseValue(property, WallHalf.WORKSPACE),
                launcher.getSurfaceBaseValue(property, WallHalf.HOME));
        assertEquals("a surface's own value comes along",
            launcher.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, WallHalf.WORKSPACE),
            launcher.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, WallHalf.HOME));
    }

    @Test
    public void matchHomeStylingGivesTheWorkspaceHomesLayoutButKeepsItsExtraKeys() {
        PlaceLayoutStore normal = store.forVariant(LayoutVariant.NORMAL);
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        Slot extraKeys = normal.slot(PORTRAIT, Element.EXTRA_KEYS);
        assertNotNull(ScreenMatch.match(launcher, store, WallHalf.WORKSPACE));

        assertEquals(home.slot(PORTRAIT, Element.APPS), normal.slot(PORTRAIT, Element.APPS));
        assertEquals(home.slot(PORTRAIT, Element.STATUS), normal.slot(PORTRAIT, Element.STATUS));
        assertEquals(home.dockStyle(), normal.dockStyle());
        assertEquals("Home wears no extra keys, so the Workspace keeps its own", extraKeys,
            normal.slot(PORTRAIT, Element.EXTRA_KEYS));
        assertEquals(80, launcher.getSurfaceBaseValue(SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE));
        assertEquals(10, launcher.getSurfaceBaseValue(SurfaceProperty.TINT, WallHalf.WORKSPACE));
    }

    @Test
    public void undoPutsEveryKeyBackAsItStood() {
        // A layout's seed is written the first time it is used; a Match uses both layouts, and
        // Undo puts them back on that seed, so the comparison starts from it.
        store.seed(LayoutVariant.HOME);
        store.seed(LayoutVariant.NORMAL);
        for (WallHalf target : WallHalf.values()) {
            Map<String, ?> before = new HashMap<>(prefs.getAll());
            ScreenMatch.Undo undo = ScreenMatch.match(launcher, store, target);
            assertNotNull(undo);
            assertEquals(target, undo.target());
            assertNotEquals(target + ": the copy wrote something", before,
                new HashMap<>(prefs.getAll()));
            undo.restore(launcher);
            assertEquals(target + ": back exactly as it was", before,
                new HashMap<>(prefs.getAll()));
        }
    }

    @Test
    public void aHomeOrientationNotUsedYetIsCopiedAsItWouldShow() {
        PlaceLayoutStore normal = store.forVariant(LayoutVariant.NORMAL);
        normal.setSlot(PlaceOrientation.LANDSCAPE, Element.APPS, Slot.on(Edge.RIGHT, 0));
        assertNotNull(ScreenMatch.match(launcher, store, WallHalf.WORKSPACE));
        assertEquals("Home's landscape, never used, is copied as its seed (the Workspace's own), "
                + "not as an empty layout", Edge.RIGHT,
            normal.slot(PlaceOrientation.LANDSCAPE, Element.APPS).edge);
    }

    @Test
    public void undoOverAnOrientationNotUsedYetPutsItBackOnItsSeed() {
        PlaceLayoutStore normal = store.forVariant(LayoutVariant.NORMAL);
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        normal.setSlot(PlaceOrientation.LANDSCAPE, Element.APPS, Slot.on(Edge.RIGHT, 0));
        ScreenMatch.Undo undo = ScreenMatch.match(launcher, store, WallHalf.HOME);
        assertNotNull(undo);
        undo.restore(launcher);
        assertEquals("Home's landscape reads as it did before the Match", Edge.RIGHT,
            home.slot(PlaceOrientation.LANDSCAPE, Element.APPS).edge);
    }

    @Test
    public void inMinimalModeTheWorkspacesMinimalLayoutIsTheOneMatched() {
        store.setMinimal(true);
        assertEquals(LayoutVariant.MINIMAL, ScreenMatch.layoutOf(WallHalf.WORKSPACE, true));
        assertEquals(LayoutVariant.HOME, ScreenMatch.layoutOf(WallHalf.HOME, true));
        ScreenMatch.Undo undo = ScreenMatch.match(launcher, store, WallHalf.WORKSPACE);
        assertNotNull(undo);
        for (String key : undo.keys()) {
            if (key.startsWith("layout."))
                assertTrue(key, key.startsWith("layout.minimal."));
        }
        assertEquals(store.forVariant(LayoutVariant.HOME).slot(PORTRAIT, Element.APPS),
            store.forVariant(LayoutVariant.MINIMAL).slot(PORTRAIT, Element.APPS));
        assertEquals("the normal layout is left alone", Slot.on(Edge.LEFT, 0).edge,
            store.forVariant(LayoutVariant.NORMAL).slot(PORTRAIT, Element.APPS).edge);
    }
}
