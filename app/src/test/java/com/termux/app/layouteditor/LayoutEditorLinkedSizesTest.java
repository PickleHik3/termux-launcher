package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceArrangeSnapshot;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;

/**
 * The Layout editor on Home with Home's own layout on (#84): the height handles edit the values
 * both halves share, and Discard puts them back as the session found them, whichever half the
 * session reached first.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LayoutEditorLinkedSizesTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("layout-editor-linked-sizes-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
        places.setDockHeightScale(PORTRAIT, 1.0f);
        places.setHomeLayout(true);
    }

    private LayoutEditorPlan open(PaneWallPage place) {
        return LayoutEditorPlan.enter(places, place, PORTRAIT, (List<PlaceArrangeSnapshot>) null);
    }

    private PlaceLayoutStore normal() {
        return places.forVariant(LayoutVariant.NORMAL);
    }

    @Test
    public void theHeightHandlesOnHomeEditTheSharedValues() {
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        assertEquals(LayoutVariant.HOME, plan.variant());
        plan.setDockHeightScale(1.4f);
        plan.setWallStripHeightDp(60);
        plan.setKeyboardHeightScale(1.2f);
        plan.setKeyboardChinDp(20);
        assertEquals(1.4f, normal().dockHeightScale(PORTRAIT), 0.001f);
        assertEquals(60, normal().wallStripHeightDp(PORTRAIT));
        assertEquals(1.2f, normal().keyboardHeightScale(PORTRAIT), 0.001f);
        assertEquals(20, normal().keyboardChinDp(PORTRAIT));
    }

    @Test
    public void discardPutsTheSharedHeightBackWhenTheWindowWasReachedFirst() {
        LayoutEditorPlan plan = open(PaneWallPage.TERMINAL);
        plan.setDockHeightScale(1.3f);
        plan.showHome(true);
        assertEquals(LayoutVariant.HOME, plan.variant());
        plan.setDockHeightScale(1.5f);
        plan.revert();
        assertEquals(1.0f, normal().dockHeightScale(PORTRAIT), 0.001f);
        assertFalse(plan.isDirty());
    }

    @Test
    public void discardPutsTheSharedHeightBackWhenHomeWasReachedFirst() {
        LayoutEditorPlan plan = open(PaneWallPage.WIDGETS);
        plan.setDockHeightScale(1.3f);
        plan.showHome(false);
        assertEquals(LayoutVariant.NORMAL, plan.variant());
        plan.setDockHeightScale(1.5f);
        plan.revert();
        assertEquals(1.0f, normal().dockHeightScale(PORTRAIT), 0.001f);
        assertFalse(plan.isDirty());
    }
}
