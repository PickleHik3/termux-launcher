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
import com.termux.app.fragments.settings.LayoutElement;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the surface editor's Place section offers and what a pick writes — and that it is the same
 * offer the Layout page makes, for the orientation the editor is standing in.
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
    public void theIndexGainsAnEdgeRowOnlyOnceTheAppsRowHasLeftTheBottom() {
        assertEquals("riding the apps row it is shown or hidden and nothing else",
            1, groups(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX).size());

        places.setAppsRow(PaneWallPage.TERMINAL, PORTRAIT, RowPlacement.HIDDEN);
        List<Group> standingAlone = groups(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX);
        assertEquals(2, standingAlone.size());
        assertEquals(Arrays.asList("top", "bottom"),
            Arrays.asList(((Pills) standingAlone.get(1)).values));

        places.setAzRowShown(PaneWallPage.TERMINAL, PORTRAIT, false);
        assertEquals("away, it is one switch again",
            1, groups(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX).size());
    }

    @Test
    public void theKeyboardOffersItsTypeAndOnEnterEverywhereAndTheModeOnTheDisplayAlone() {
        assertEquals(2, groups(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD).size());
        assertEquals(Arrays.asList("docked", "floating", "split"),
            Arrays.asList(pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 0).values));
        assertEquals(Arrays.asList("as_left", "open", "closed"),
            Arrays.asList(pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 1).values));

        List<Group> display = groups(PaneWallPage.DISPLAY, LANDSCAPE, Element.KEYBOARD);
        assertEquals(3, display.size());
        assertEquals(Arrays.asList("resize", "overlay"),
            Arrays.asList(((Pills) display.get(2)).values));
        assertEquals("landscape on the display floats by default",
            "overlay", ((Pills) display.get(2)).selected);
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
    public void aPickWritesThePlaceAndOrientationItWasOfferedFor() {
        pills(PaneWallPage.DISPLAY, LANDSCAPE, Element.STATUS_BAR, 0).writer.write("right");

        assertEquals(Edge.RIGHT, places.statusBarEdge(PaneWallPage.DISPLAY, LANDSCAPE));
        assertEquals("portrait is a value of its own",
            Edge.TOP, places.statusBarEdge(PaneWallPage.DISPLAY, PORTRAIT));
        assertEquals("and so is every other place",
            Edge.TOP, places.statusBarEdge(PaneWallPage.TERMINAL, LANDSCAPE));
    }

    @Test
    public void aPickOnEveryOtherElementLandsOnItsOwnKey() {
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.PINNED_APPS, 0).writer.write("hidden");
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.EXTRA_KEYS, 0).writer.write("hidden");
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 0).writer.write("split");
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.KEYBOARD, 1).writer.write("closed");
        pills(PaneWallPage.TERMINAL, PORTRAIT, Element.AZ_INDEX, 0).writer.write("hidden");
        ((Counter) groups(PaneWallPage.WIDGETS, LANDSCAPE, Element.WIDGET_GRID).get(0))
            .writer.write(6);

        assertEquals(RowPlacement.HIDDEN, places.appsRow(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(RowPlacement.HIDDEN, places.extraKeys(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(KeyboardForm.SPLIT, places.keyboardForm(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(KeyboardOnEnter.CLOSED, places.keyboardOnEnter(PaneWallPage.TERMINAL));
        assertFalse(places.azRowShown(PaneWallPage.TERMINAL, PORTRAIT));
        assertEquals(6, places.widgetColumns(PaneWallPage.WIDGETS, LANDSCAPE));
        assertEquals("portrait's grid is untouched",
            4, places.widgetColumns(PaneWallPage.WIDGETS, PORTRAIT));
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

    // ------------------------------------------------------------- the same offer as the page's

    /**
     * The Layout page answers the same questions for both orientations at once. Its value sets and
     * labels are the ones a user has already learned, so the editor may not quietly grow a
     * different set: every pill row here has to appear, values and labels alike, in what the page
     * offers for the same element.
     */
    @Test
    public void everyPillSetIsOneTheLayoutPageAlreadyOffers() {
        for (PaneWallPage place : PaneWallPage.values()) {
            for (LayoutElement pageElement : LayoutElement.values()) {
                if (!pageElement.isOn(place)) continue;
                Element element = Element.valueOf(pageElement.name());
                Set<String> offered = new HashSet<>();
                for (LayoutChooserModel.Group group
                        : LayoutChooserModel.groups(app, places, place, pageElement)) {
                    if (group instanceof LayoutChooserModel.Pills)
                        offered.add(shapeOf(((LayoutChooserModel.Pills) group).values,
                            ((LayoutChooserModel.Pills) group).labelResIds));
                }
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    for (Group group : groups(place, orientation, element)) {
                        if (!(group instanceof Pills)) continue;
                        Pills row = (Pills) group;
                        assertTrue(place + " " + element + " " + orientation + " offers "
                                + Arrays.toString(row.values) + ", which the page does not",
                            offered.contains(shapeOf(row.values, row.labelResIds)));
                    }
                }
            }
        }
    }

    private static String shapeOf(String[] values, int[] labelResIds) {
        List<String> shape = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++)
            shape.add(values[i] + "=" + labelResIds[i]);
        return shape.toString();
    }
}
