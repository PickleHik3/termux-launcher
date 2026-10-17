package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The Home layout (ADR 0008): a third variant beside normal and minimal, keyed like minimal,
 * seeded from the normal layout with the status bar and the extra keys put away and the keyboard
 * put away, offering only the dock's rows and the keyboard, whose element is always on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutHomeLayoutTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-home-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    @Test
    public void theHomeLayoutKeepsItsKeysBesideTheOthers() {
        assertEquals("layout.home.portrait.apps_row",
            PlaceLayoutStore.layoutKey(LayoutVariant.HOME, PORTRAIT, "apps_row"));
        assertEquals("layout.home.keyboard_shown", PlaceLayoutStore.homeKey("keyboard_shown"));
    }

    @Test
    public void itIsSeededFromTheNormalLayoutWithTheWorkspacesElementsPutAway() {
        PlaceLayoutStore store = store();
        store.setStatusBarEdge(PORTRAIT, Edge.TOP);
        store.setAppsRow(PORTRAIT, RowPlacement.BOTTOM);
        store.setExtraKeys(PORTRAIT, RowPlacement.BOTTOM);
        store.setAzRowShown(PORTRAIT, true);
        store.setAzBarEdge(PORTRAIT, Edge.BOTTOM);
        store.setKeyboardForm(PORTRAIT, KeyboardForm.SPLIT);

        PlaceLayout home = store.resolve(PORTRAIT, LayoutVariant.HOME);

        assertTrue("no status bar on Home", home.slot(Element.STATUS).hidden);
        assertTrue("no extra keys on Home", home.slot(Element.EXTRA_KEYS).hidden);
        assertFalse("the pinned apps come along", home.slot(Element.APPS).hidden);
        assertEquals(Edge.BOTTOM, home.slot(Element.APPS).edge);
        assertFalse("the A-Z index comes along", home.slot(Element.AZ).hidden);
        assertEquals(KeyboardForm.SPLIT, home.keyboardForm);
        assertFalse("the keyboard is put away: always on starts off", home.keyboardShown);
        // Seeded as keys of its own; the normal layout is untouched.
        assertTrue(prefs.contains("layout.home.portrait.status_bar"));
        PlaceLayout normal = store.resolve(PORTRAIT, LayoutVariant.NORMAL);
        assertFalse(normal.slot(Element.STATUS).hidden);
        assertFalse(normal.slot(Element.EXTRA_KEYS).hidden);
        assertTrue("the Workspace's keyboard element stays shown", normal.keyboardShown);
    }

    @Test
    public void theHomeLayoutNeverShowsTheStatusBarOrTheExtraKeysWhateverIsStored() {
        PlaceLayoutStore home = store().forVariant(LayoutVariant.HOME);
        home.setSlot(PORTRAIT, Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        home.setSlot(PORTRAIT, Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
        PlaceLayout layout = home.resolve(PORTRAIT);
        assertTrue(layout.slot(Element.STATUS).hidden);
        assertTrue(layout.slot(Element.EXTRA_KEYS).hidden);
        assertFalse(PlaceChromePolicy.extraKeysShown(layout));
    }

    @Test
    public void onlyTheDockRowsAreOfferedOnHome() {
        assertTrue(LayoutVariant.HOME.offers(Element.APPS));
        assertTrue(LayoutVariant.HOME.offers(Element.AZ));
        assertFalse(LayoutVariant.HOME.offers(Element.STATUS));
        assertFalse(LayoutVariant.HOME.offers(Element.EXTRA_KEYS));
        for (Element element : Element.values()) {
            assertTrue(LayoutVariant.NORMAL.offers(element));
            assertTrue(LayoutVariant.MINIMAL.offers(element));
        }
    }

    @Test
    public void theHomeKeyboardElementIsAlwaysOnAndNotTheKeyboardSwitch() {
        PlaceLayoutStore store = store();
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        assertFalse(store.isHomeKeyboardAlwaysOn());
        assertFalse(home.isKeyboardShown());

        home.setKeyboardShown(true);
        assertTrue(store().isHomeKeyboardAlwaysOn());
        assertTrue(home.resolve(PORTRAIT).keyboardShown);
        assertTrue("one value for both orientations", home.resolve(LANDSCAPE).keyboardShown);
        assertFalse("the keyboard switch is not touched", launcher.isKeyboardTurnedOff());

        // The Workspace's element is the switch, and leaves always on alone.
        store.setKeyboardShown(false);
        assertTrue(launcher.isKeyboardTurnedOff());
        assertTrue(store.isHomeKeyboardAlwaysOn());
        home.setKeyboardShown(false);
        assertFalse(store.isHomeKeyboardAlwaysOn());
        assertTrue(launcher.isKeyboardTurnedOff());
    }

    @Test
    public void theRowsAlwaysOnTookAreRemembered() {
        PlaceLayoutStore store = store();
        assertEquals(0, store.homeKeyboardRows());
        store.setHomeKeyboardRows(2);
        assertEquals(2, store().homeKeyboardRows());
        store.setHomeKeyboardRows(-3);
        assertEquals(0, store.homeKeyboardRows());
    }

    @Test
    public void theWidgetGridIsHomesWhicheverVariantAsks() {
        PlaceLayoutStore store = store();
        store.setWidgetColumns(PORTRAIT, 5);
        store.setWidgetRows(PORTRAIT, 6);
        for (LayoutVariant variant : LayoutVariant.values()) {
            PlaceLayout layout = store.resolve(PORTRAIT, variant);
            assertEquals(variant.name(), 5, layout.widgetColumns);
            assertEquals(variant.name(), 6, layout.widgetRows);
        }
        assertEquals(5, prefs.getInt("layout.home.portrait.widget_columns", 0));
        assertFalse(prefs.contains("layout.portrait.widget_columns"));
        // Minimal mode never re-cuts the grid.
        store.setMinimal(true);
        assertEquals(6, store.resolve(PORTRAIT).widgetRows);
    }

    @Test
    public void theGridTheNormalLayoutHeldSeedsHomes() {
        prefs.edit().putInt("layout.portrait.widget_rows", 7).commit();
        assertEquals(7, store().widgetRows(PORTRAIT));
        assertEquals(7, prefs.getInt("layout.home.portrait.widget_rows", 0));
    }

    @Test
    public void aPageSaysWhichVariantItIsArrangedBy() {
        PlaceLayoutStore store = store();
        assertEquals(LayoutVariant.HOME, store.variantFor(PaneWallPage.WIDGETS));
        assertEquals(LayoutVariant.NORMAL, store.variantFor(PaneWallPage.TERMINAL));
        store.setMinimal(true);
        assertEquals(LayoutVariant.HOME, store.variantFor(PaneWallPage.WIDGETS));
        assertEquals(LayoutVariant.MINIMAL, store.variantFor(PaneWallPage.TERMINAL));
        assertEquals("the Workspace's variant is still what the store stands in",
            LayoutVariant.MINIMAL, store.activeVariant());
    }

    @Test
    public void theHomeLayoutIsTheUsersOnceSeededAndEditsStayOnIt() {
        PlaceLayoutStore store = store();
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        home.setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.TOP, Element.APPS));
        store.setAppsRow(PORTRAIT, RowPlacement.HIDDEN);
        PlaceLayout again = store().resolve(PORTRAIT, LayoutVariant.HOME);
        assertFalse(again.slot(Element.APPS).hidden);
        assertEquals(Edge.TOP, again.slot(Element.APPS).edge);
        assertTrue(store.resolve(PORTRAIT, LayoutVariant.NORMAL).slot(Element.APPS).hidden);
    }

    @Test
    public void theEditorsSnapshotOfHomeKeepsAlwaysOn() {
        PlaceLayoutStore store = store();
        PlaceArrangeSnapshot before = PlaceArrangeSnapshot.capture(store, LayoutVariant.HOME);
        store.forVariant(LayoutVariant.HOME).setKeyboardShown(true);
        assertTrue(store.isHomeKeyboardAlwaysOn());
        before.restore(store);
        assertFalse("Discard puts always on back", store.isHomeKeyboardAlwaysOn());
        assertFalse("and never touches the switch", launcher.isKeyboardTurnedOff());
    }

    @Test
    public void clearingTheHomeLayoutSeedsItAgain() {
        PlaceLayoutStore store = store();
        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        home.setSlot(PORTRAIT, Element.APPS, Slot.on(Edge.TOP, Element.APPS));
        store.setKeyboardForm(PORTRAIT, KeyboardForm.FLOATING);
        home.clear(PORTRAIT);
        PlaceLayout reset = home.resolve(PORTRAIT);
        assertEquals("re-seeded from the normal layout as it is now", KeyboardForm.FLOATING,
            reset.keyboardForm);
        assertTrue(reset.slot(Element.STATUS).hidden);
    }
}
