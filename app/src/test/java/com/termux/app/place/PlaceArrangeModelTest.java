package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceArrangeModel.Counter;
import com.termux.app.place.PlaceArrangeModel.Element;
import com.termux.app.place.PlaceArrangeModel.Group;
import com.termux.app.place.PlaceArrangeModel.Pills;
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

import java.util.Arrays;
import java.util.List;

/**
 * What the surface editor's Place section offers, for the orientation the editor is standing in,
 * and what a pick writes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceArrangeModelTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;
    private static final PlaceOrientation LANDSCAPE = PlaceOrientation.LANDSCAPE;

    private Application app;
    private SharedPreferences prefs;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-arrange-model-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    private List<Group> groups(PaneWallPage place, PlaceOrientation orientation, Element element) {
        return PlaceArrangeModel.groups(places, place, orientation, element);
    }

    private Pills pills(PaneWallPage place, PlaceOrientation orientation, Element element,
                        int index) {
        Group group = groups(place, orientation, element).get(index);
        assertTrue(element + " group " + index + " is a pill row", group instanceof Pills);
        return (Pills) group;
    }

    // ------------------------------------------------------------------ what each element offers

    @Test
    public void theStatusBarOffersTwoEdgesInPortraitAndFourInLandscape() {
        assertEquals(Arrays.asList("top", "bottom"),
            Arrays.asList(pills(PaneWallPage.TERMINAL, PORTRAIT, Element.STATUS_BAR, 0).values));
        assertEquals(Arrays.asList("top", "bottom", "left", "right"),
            Arrays.asList(pills(PaneWallPage.TERMINAL, LANDSCAPE, Element.STATUS_BAR, 0).values));
    }

    @Test
    public void aRowOffersBottomOrHiddenInPortraitAndTheTwoSidesInLandscape() {
        for (Element element : new Element[] {Element.PINNED_APPS, Element.EXTRA_KEYS}) {
            assertEquals(element + " portrait", Arrays.asList("bottom", "hidden"),
                Arrays.asList(pills(PaneWallPage.TERMINAL, PORTRAIT, element, 0).values));
            assertEquals(element + " landscape", Arrays.asList("bottom", "left", "right", "hidden"),
                Arrays.asList(pills(PaneWallPage.TERMINAL, LANDSCAPE, element, 0).values));
        }
    }

    @Test
    public void theIndexKeepsItsEdgeRowWhereverTheAppsRowStands() {
        // The edge is the index's own wherever it stands: it is what takes it off the apps row and
        // what puts it back, so the control is there while the index is.
        List<Group> riding = groups(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX);
        assertEquals(2, riding.size());
        assertEquals(Arrays.asList("top", "bottom"),
            Arrays.asList(((Pills) riding.get(1)).values));

        places.setAppsRow(PORTRAIT, RowPlacement.HIDDEN);
        List<Group> standingAlone = groups(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX);
        assertEquals(2, standingAlone.size());
        assertEquals(Arrays.asList("top", "bottom"),
            Arrays.asList(((Pills) standingAlone.get(1)).values));

        places.setAzRowShown(PORTRAIT, false);
        assertEquals("away, it is one switch again",
            1, groups(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX).size());
    }

    @Test
    public void theKeyboardOffersItsSwitchAndTypeEverywhereAndTheModeOnTheDisplayAlone() {
        assertEquals(2, groups(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD).size());
        assertEquals(Arrays.asList("on", "off"),
            Arrays.asList(pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 0).values));
        assertEquals(Arrays.asList("docked", "floating", "split"),
            Arrays.asList(pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 1).values));

        List<Group> display = groups(PaneWallPage.DISPLAY, LANDSCAPE, Element.KEYBOARD);
        assertEquals(3, display.size());
        assertEquals(Arrays.asList("resize", "overlay"),
            Arrays.asList(((Pills) display.get(2)).values));
        assertEquals("landscape on the display floats by default",
            "overlay", ((Pills) display.get(2)).selected);
    }

    /**
     * The keyboard element's switch is the palette's Keyboard on/off: one setting, read both
     * ways, and one for both orientations and every place.
     */
    @Test
    public void theKeyboardSwitchIsKeyboardOnOff() {
        assertEquals("on", pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 0).selected);
        assertTrue(places.resolve(PORTRAIT).keyboardShown);

        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 0).writer.write("off");
        assertTrue("the editor's off is the palette's off",
            prefs.getBoolean("keyboard_turned_off", false));
        assertFalse(places.isKeyboardShown());
        assertFalse(places.resolve(PORTRAIT).keyboardShown);
        assertFalse("one switch for both orientations", places.resolve(LANDSCAPE).keyboardShown);
        assertEquals("off", pills(PaneWallPage.DISPLAY, LANDSCAPE, Element.KEYBOARD, 0).selected);

        // The palette writes the same key, and the editor reads it back.
        prefs.edit().putBoolean("keyboard_turned_off", false).commit();
        assertEquals("the palette's on is the editor's on",
            "on", pills(PaneWallPage.WIDGETS, PORTRAIT, Element.KEYBOARD, 0).selected);
    }

    @Test
    public void theWidgetGridIsTheHomePlacesAlone() {
        assertTrue(groups(PaneWallPage.TERMINAL, PORTRAIT, Element.WIDGET_GRID).isEmpty());
        assertTrue(groups(PaneWallPage.DISPLAY, PORTRAIT, Element.WIDGET_GRID).isEmpty());

        List<Group> grid = groups(PaneWallPage.WIDGETS, PORTRAIT, Element.WIDGET_GRID);
        assertEquals(2, grid.size());
        assertTrue(grid.get(0) instanceof Counter);
        assertEquals(4, ((Counter) grid.get(0)).value);
        assertEquals(5, ((Counter) grid.get(1)).value);
    }

    // ------------------------------------------------------------------------ what a pick writes

    @Test
    public void aPickWritesTheOrientationItWasOfferedForOnEveryPlace() {
        pills(PaneWallPage.DISPLAY, LANDSCAPE, Element.STATUS_BAR, 0).writer.write("right");

        assertEquals("a pick offered over the display lands in the one shared layout",
            Edge.RIGHT, places.resolve(LANDSCAPE).slot(com.termux.app.place.Element.STATUS).edge);
        assertEquals("portrait is a value of its own",
            Edge.TOP, places.statusBarEdge(PORTRAIT));
    }

    @Test
    public void aPickOnEveryOtherElementLandsOnItsOwnKey() {
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.PINNED_APPS, 0).writer.write("hidden");
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.EXTRA_KEYS, 0).writer.write("hidden");
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 1).writer.write("split");
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX, 0).writer.write("hidden");
        ((Counter) groups(PaneWallPage.WIDGETS, LANDSCAPE, Element.WIDGET_GRID).get(0))
            .writer.write(6);

        assertEquals(RowPlacement.HIDDEN, places.appsRow(PORTRAIT));
        assertEquals(RowPlacement.HIDDEN, places.extraKeys(PORTRAIT));
        assertEquals(KeyboardForm.SPLIT, places.keyboardForm(PORTRAIT));
        assertFalse(places.azRowShown(PORTRAIT));
        assertEquals(6, places.widgetColumns(LANDSCAPE));
        assertEquals("portrait's grid is untouched",
            4, places.widgetColumns(PORTRAIT));
    }

    @Test
    public void aRowReadsBackWhatWasWrittenForIt() {
        Pills before = pills(PaneWallPage.TERMINAL, LANDSCAPE, Element.EXTRA_KEYS, 0);
        assertEquals("bottom", before.selected);
        assertEquals(0, before.selectedIndex());

        before.writer.write("right");

        Pills after = pills(PaneWallPage.TERMINAL, LANDSCAPE, Element.EXTRA_KEYS, 0);
        assertEquals("right", after.selected);
        assertEquals(2, after.selectedIndex());
        assertNotEquals(before.selectedIndex(), after.selectedIndex());
    }
}
