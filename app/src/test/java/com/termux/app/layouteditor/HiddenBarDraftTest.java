package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.page.PageKind;
import com.termux.app.place.Element;
import com.termux.app.place.KindFixtures;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** Hiding a bar goes through the draft, keeps its edge, and the column's chips bring it back. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HiddenBarDraftTest {

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("hidden-bar-draft-test",
            Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = KindFixtures.dockOnTheTerminal(
            new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null)));
    }

    @Test
    public void hidingTheStatusBarKeepsItsEdgeAndUndoBringsItBack() {
        LayoutEditorPlan plan = DraftedSession.enter(places, PageKind.TERMINAL,
            PlaceOrientation.PORTRAIT);
        Edge edge = plan.shownLayout().slot(Element.STATUS).edge;

        plan.drop(MiniatureDragPolicy.Bar.of(Element.STATUS), null);

        assertTrue(plan.shownLayout().slot(Element.STATUS).hidden);
        assertEquals(edge, plan.shownLayout().slot(Element.STATUS).edge);
        assertTrue(plan.trayItems().contains(LayoutEditorPlan.TrayItem.STATUS_BAR));
        assertTrue(places.layouts().isDraftDirty());

        places.layouts().resetDraft();
        assertFalse(plan.shownLayout().slot(Element.STATUS).hidden);
        assertTrue(plan.trayItems().isEmpty());
    }

    @Test
    public void aChipBringsTheBarBack() {
        LayoutEditorPlan plan = DraftedSession.enter(places, PageKind.TERMINAL,
            PlaceOrientation.PORTRAIT);
        plan.drop(MiniatureDragPolicy.Bar.of(Element.EXTRA_KEYS), null);
        assertTrue(plan.shownLayout().slot(Element.EXTRA_KEYS).hidden);
        plan.restore(LayoutEditorPlan.TrayItem.EXTRA_KEYS);
        assertFalse(plan.shownLayout().slot(Element.EXTRA_KEYS).hidden);
    }
}
