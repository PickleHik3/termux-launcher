package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.MiniatureDragPolicy.Bar;
import com.termux.app.place.Element;
import com.termux.app.place.KindFixtures;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayout.Edge;
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

/** The Elements card's "<kind> | <page>" chip through the plan (ADR 0015 §3, #172 A2b). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorPageLayoutTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-page-layout-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = KindFixtures.onHomePage(
            new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null)));
        places.setStatusBarEdge(PORTRAIT, Edge.TOP);
    }

    @Test
    public void thePageSideEditsThePagesOwnLayoutAndDiscardPutsBothBack() {
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        assertTrue(plan.offersPageLayout());
        assertFalse(plan.pageLayoutSide());
        assertEquals(LayoutVariant.NORMAL, plan.variant());

        plan.setPageLayoutSide(true);
        assertTrue(plan.pageLayoutSide());
        assertEquals(KindFixtures.HOME_PAGE, plan.variant());
        plan.drop(Bar.of(Element.STATUS), null);

        assertTrue("the page hides its bar",
            plan.shownLayout().slot(Element.STATUS).hidden);
        assertTrue("the kind still shows it",
            places.kindShows(PageKind.HOME, PORTRAIT, Element.STATUS));
        assertTrue(plan.isDirty());

        plan.revert();

        assertFalse("the flag comes back", places.hasPageLayout(KindFixtures.HOME_ID));
        assertTrue(places.kindShows(PageKind.HOME, PORTRAIT, Element.STATUS));
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        assertFalse(plan.shownLayout().slot(Element.STATUS).hidden);
        assertFalse(plan.isDirty());
    }

    @Test
    public void theKindSideOnAPageWithALayoutGoesBackToTheKindsAndDiscardRestoresIt() {
        places.setPageLayout(KindFixtures.HOME_ID, PageKind.HOME, true);
        PlaceLayoutStore own = places.forVariant(KindFixtures.HOME_PAGE);
        own.setSlot(PORTRAIT, Element.STATUS, own.slot(PORTRAIT, Element.STATUS).withHidden(true));
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.WIDGETS, PORTRAIT);
        assertTrue(plan.pageLayoutSide());
        assertTrue(plan.shownLayout().slot(Element.STATUS).hidden);

        plan.setPageLayoutSide(false);
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        assertFalse("the kind's bar shows", plan.shownLayout().slot(Element.STATUS).hidden);

        plan.revert();
        assertTrue(places.hasPageLayout(KindFixtures.HOME_ID));
        assertEquals(KindFixtures.HOME_PAGE, plan.variant());
        assertTrue("the page's own layout is as it was",
            plan.shownLayout().slot(Element.STATUS).hidden);
    }

    @Test
    public void inMinimalModeAWindowOffersNoChip() {
        places.setMinimal(true);
        LayoutEditorPlan plan = LayoutEditorPlan.enter(places, PaneWallPage.TERMINAL, PORTRAIT);
        assertFalse(plan.offersPageLayout());
        plan.setPageLayoutSide(true);
        assertFalse(places.hasPageLayout(1));
    }
}
