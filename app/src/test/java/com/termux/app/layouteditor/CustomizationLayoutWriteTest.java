package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages.PageKind;
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
 * Customization › Layout writes both kinds through the layout session (overhaul §2): Discard
 * (the session's revert) puts both back, Done (the session ended) keeps both.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class CustomizationLayoutWriteTest {

    private static final List<LayoutVariant> BOTH = Arrays.asList(LayoutVariant.HOME,
        LayoutVariant.NORMAL, LayoutVariant.DISPLAY);

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("customization-layout-write-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    private void assertAz(boolean home, boolean workspace) {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertEquals("Home " + orientation, home,
                places.kindShows(PageKind.HOME, orientation, Element.AZ));
            assertEquals("Terminal " + orientation, workspace,
                places.kindShows(PageKind.TERMINAL, orientation, Element.AZ));
            assertEquals("Display " + orientation, workspace,
                places.kindShows(PageKind.DISPLAY, orientation, Element.AZ));
        }
    }

    /** A–Z hidden on both kinds from a session opened on the Workspace. */
    private LayoutEditorPlan hideAzEverywhere() {
        assertAz(true, false);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL,
            PlaceOrientation.PORTRAIT);
        for (LayoutVariant variant : BOTH)
            plan.setShownOnLayout(variant, Element.AZ, false);
        plan.setShownOnLayout(LayoutVariant.NORMAL, Element.AZ, true);
        plan.setShownOnLayout(LayoutVariant.DISPLAY, Element.AZ, true);
        assertAz(false, true);
        assertTrue("the session counts it unsaved", plan.isDirty());
        return plan;
    }

    @Test
    public void discardPutsBothKindsBack() {
        LayoutEditorPlan plan = hideAzEverywhere();
        plan.revert();
        assertAz(true, false);
        assertFalse(plan.isDirty());
    }

    @Test
    public void anElementHiddenInBothOrientationsStaysListedAndComesBack() {
        // Customization hides A–Z on Home in both orientations; Home's own list must still offer
        // it (switch off) and its switch must bring it back (the row once vanished).
        LayoutEditorPlan customization = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL,
            PlaceOrientation.PORTRAIT);
        customization.setShownOnLayout(LayoutVariant.HOME, Element.AZ, false);
        assertFalse(places.kindShowsAny(PageKind.HOME, Element.AZ));

        LayoutEditorPlan home = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS,
            PlaceOrientation.PORTRAIT);
        assertTrue("the Home list keeps the row",
            com.termux.app.chrome.DressResolver.wornOn(home.variant(), Element.AZ));
        assertTrue(home.trayItems().contains(LayoutEditorPlan.TrayItem.AZ_INDEX));
        assertTrue(home.restore(LayoutEditorPlan.TrayItem.AZ_INDEX)
            != LayoutEditorPlan.Drop.NONE);
        assertTrue(places.kindShows(PageKind.HOME, PlaceOrientation.PORTRAIT, Element.AZ));
        assertFalse("Home never lists the extra keys",
            com.termux.app.chrome.DressResolver.wornOn(home.variant(), Element.EXTRA_KEYS));
    }

    @Test
    public void theEditedOrientationIsWhatTheListsRead() {
        // Home's list hides A–Z in portrait only: Customization reads the edited orientation
        // (hidden), not "shown in either orientation" (which still said shown).
        LayoutEditorPlan home = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS,
            PlaceOrientation.PORTRAIT);
        home.drop(com.termux.app.fragments.settings.MiniatureDragPolicy.Bar.of(Element.AZ), null);
        assertTrue(places.forVariant(LayoutVariant.HOME)
            .slot(PlaceOrientation.PORTRAIT, Element.AZ).hidden);
        assertTrue(places.kindShowsAny(PageKind.HOME, Element.AZ));
    }

    @Test
    public void doneKeepsBothKinds() {
        hideAzEverywhere();
        // Done ends the session without writing; the next session opens on what was kept.
        LayoutEditorPlan next = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS,
            PlaceOrientation.PORTRAIT);
        assertAz(false, true);
        assertFalse(next.isDirty());
    }
}
