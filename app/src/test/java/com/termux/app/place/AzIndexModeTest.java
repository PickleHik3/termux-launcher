package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.LayoutChooserModel;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.place.PlaceLayout.AzIndexMode;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

/**
 * The alphabets index's three-way form — On, Minimised, Off — from the store through the resolved
 * layout to the chrome's answers and the Layout editor's pill.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AzIndexModeTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("az-index-mode-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(launcher);
    }

    // ------------------------------------------------------------------------ the store

    @Test
    public void anInstallThatOnlyEverStoredTheSwitchReadsAsOnOrOff() {
        prefs.edit()
            .putBoolean(PlaceLayoutStore.layoutKey(PORTRAIT, "az_row"), true)
            .putBoolean(PlaceLayoutStore.layoutKey(LANDSCAPE, "az_row"), false)
            .commit();
        PlaceLayoutStore store = store();
        assertEquals(AzIndexMode.ON, store.azIndexMode(PORTRAIT));
        assertEquals(AzIndexMode.OFF, store.azIndexMode(LANDSCAPE));
        assertFalse(store.resolve(PORTRAIT).azMinimised);
        assertFalse("nothing is written for the upgrade",
            prefs.contains(PlaceLayoutStore.layoutKey(PORTRAIT, "az_minimised")));
    }

    @Test
    public void theOldGlobalSwitchStillAnswersForAnOrientationThatNeverStoredOne() {
        launcher.setAppLauncherAzRowEnabled(false);
        assertEquals(AzIndexMode.OFF, store().azIndexMode(PORTRAIT));
        launcher.setAppLauncherAzRowEnabled(true);
        assertEquals(AzIndexMode.ON, store().azIndexMode(PORTRAIT));
    }

    @Test
    public void everyModeRoundTripsAndStaysInItsOrientation() {
        PlaceLayoutStore store = store();
        for (AzIndexMode mode : AzIndexMode.values()) {
            store.setAzIndexMode(PORTRAIT, mode);
            assertEquals(mode, store().azIndexMode(PORTRAIT));
            assertEquals(mode, store().resolve(PORTRAIT).azIndexMode());
        }
        assertEquals("landscape is a value of its own",
            AzIndexMode.ON, store.azIndexMode(LANDSCAPE));
    }

    @Test
    public void offKeepsTheFormTheIndexComesBackIn() {
        PlaceLayoutStore store = store();
        store.setAzIndexMode(PORTRAIT, AzIndexMode.MINIMISED);
        store.setAzIndexMode(PORTRAIT, AzIndexMode.OFF);
        assertTrue(store.azMinimised(PORTRAIT));
        // Dragged back out of the tray, it is the tab again.
        store.setSlot(PORTRAIT, Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        assertEquals(AzIndexMode.MINIMISED, store.azIndexMode(PORTRAIT));
    }

    @Test
    public void clearingPutsTheBandBack() {
        PlaceLayoutStore store = store();
        store.setAzIndexMode(PORTRAIT, AzIndexMode.MINIMISED);
        store.clear(PORTRAIT);
        assertFalse(store.azMinimised(PORTRAIT));
    }

    // ------------------------------------------------------------------------ the chrome

    @Test
    public void aMinimisedIndexIsOnScreenButClaimsNoBand() {
        PlaceLayoutStore store = store();
        store.setAppsRow(PORTRAIT, PlaceLayout.RowPlacement.BOTTOM);
        store.setAzIndexMode(PORTRAIT, AzIndexMode.MINIMISED);
        PlaceLayout layout = store.resolve(PORTRAIT);

        assertTrue(EdgeStackPolicy.isShown(layout, Element.AZ));
        assertFalse(EdgeStackPolicy.claimsBand(layout, Element.AZ));
        assertFalse(EdgeStackPolicy.stack(layout, Edge.BOTTOM).contains(Element.AZ));
        assertTrue(PlaceChromePolicy.azTabShown(layout));
        assertTrue(PlaceChromePolicy.azIndexShown(layout));
        assertFalse("no row of its own", PlaceChromePolicy.azRowShown(layout));
        assertFalse("nothing on the dock", PlaceChromePolicy.azRowOnDock(layout));
        assertFalse("it never rides the apps row", PlaceChromePolicy.azRidesAppsRow(layout));
        assertTrue(PlaceChromePolicy.azIndexStandsAlone(layout));
    }

    @Test
    public void theTabCostsTheContentNothing() {
        PlaceLayoutStore store = store();
        store.setAzBarEdge(PORTRAIT, Edge.TOP);
        EdgeStackPolicy.Metrics metrics = EdgeStackPolicy.Metrics.builder()
            .az(30, 30).status(40, 40).build();
        store.setAzIndexMode(PORTRAIT, AzIndexMode.ON);
        int withBand = EdgeStackPolicy.contentInsets(store.resolve(PORTRAIT), metrics).top;
        store.setAzIndexMode(PORTRAIT, AzIndexMode.MINIMISED);
        int withTab = EdgeStackPolicy.contentInsets(store.resolve(PORTRAIT), metrics).top;
        assertEquals(30, withBand - withTab);
    }

    @Test
    public void minimalModeTakesTheTabAwayToo() {
        PlaceLayoutStore store = store();
        store.setAzIndexMode(PORTRAIT, AzIndexMode.MINIMISED);
        PlaceLayout minimal = MinimalMode.apply(store.resolve(PORTRAIT));
        assertFalse(PlaceChromePolicy.azTabShown(minimal));
        assertFalse(PlaceChromePolicy.azIndexShown(minimal));
    }

    @Test
    public void theFlagIsPartOfTheValue() {
        PlaceLayout layout = store().resolve(PORTRAIT);
        PlaceLayout tab = layout.withAzMinimised(true);
        assertNotEquals(layout, tab);
        assertEquals(layout, tab.withAzMinimised(false));
        assertTrue("moving an element keeps the form",
            tab.withSlot(Element.AZ, Slot.on(Edge.TOP, Element.AZ)).azMinimised);
        assertTrue(tab.withKeyboardShown(!tab.keyboardShown).azMinimised);
    }

    @Test
    public void droppingTheTabOnAnotherEdgeKeepsItMinimised() {
        PlaceLayoutStore store = store();
        store.setAzIndexMode(LANDSCAPE, AzIndexMode.MINIMISED);
        assertTrue(LayoutChooserModel.applyDrop(store, LANDSCAPE,
            MiniatureDragPolicy.Bar.AZ_INDEX, Edge.RIGHT, 0));
        PlaceLayout layout = store.resolve(LANDSCAPE);
        assertEquals(Edge.RIGHT, layout.slot(Element.AZ).edge);
        assertTrue(PlaceChromePolicy.azTabShown(layout));
    }

    // ------------------------------------------------------------------------ the editor

    @Test
    public void thePillIsThreeWayAndKeepsThePositionWhileTheIndexIsThere() {
        PlaceLayoutStore store = store();
        List<PlaceArrangeModel.Group> groups = PlaceArrangeModel.groups(store,
            PaneWallPage.TERMINAL, PORTRAIT, PlaceArrangeModel.Element.AZ_INDEX);
        PlaceArrangeModel.Pills pill = (PlaceArrangeModel.Pills) groups.get(0);
        assertEquals(Arrays.asList("shown", "minimised", "hidden"), Arrays.asList(pill.values));
        assertEquals("shown", pill.selected);

        pill.writer.write("minimised");
        assertEquals(AzIndexMode.MINIMISED, store.azIndexMode(PORTRAIT));
        groups = PlaceArrangeModel.groups(store, PaneWallPage.TERMINAL, PORTRAIT,
            PlaceArrangeModel.Element.AZ_INDEX);
        assertEquals("minimised", ((PlaceArrangeModel.Pills) groups.get(0)).selected);
        assertEquals("the tab still has an edge to choose", 2, groups.size());

        ((PlaceArrangeModel.Pills) groups.get(0)).writer.write("hidden");
        assertEquals(AzIndexMode.OFF, store.azIndexMode(PORTRAIT));
        assertEquals(1, PlaceArrangeModel.groups(store, PaneWallPage.TERMINAL, PORTRAIT,
            PlaceArrangeModel.Element.AZ_INDEX).size());

        PlaceArrangeModel.Pills off = (PlaceArrangeModel.Pills) PlaceArrangeModel.groups(store,
            PaneWallPage.TERMINAL, PORTRAIT, PlaceArrangeModel.Element.AZ_INDEX).get(0);
        off.writer.write("shown");
        assertEquals(AzIndexMode.ON, store.azIndexMode(PORTRAIT));
    }

    @Test
    public void discardPutsTheFormBackAndMinimisingIsUnsaved() {
        PlaceLayoutStore store = store();
        PlaceArrangeSnapshot before = PlaceArrangeSnapshot.capture(store);
        store.setAzIndexMode(PORTRAIT, AzIndexMode.MINIMISED);
        assertNotEquals(before.signature(), PlaceArrangeSnapshot.capture(store).signature());

        before.restore(store);
        assertEquals(AzIndexMode.ON, store.azIndexMode(PORTRAIT));
        assertEquals(before.signature(), PlaceArrangeSnapshot.capture(store).signature());
    }
}
