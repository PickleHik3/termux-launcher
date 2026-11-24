package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.page.PageKind;
import com.termux.app.place.Element;
import com.termux.app.place.KindFixtures;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.layout.LayoutStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * A dropped bar lands in the editor's draft: Undo takes it back, and nothing reaches the stored
 * layout until Done.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BarDropDraftTest {

    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("bar-drop-draft-test",
            Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        places = KindFixtures.dockOnTheTerminal(
            new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null)));
    }

    private static Landing top(int index) {
        return new Landing(true, Zone.EDGE, Edge.TOP, index, false, null, null);
    }

    @Test
    public void aDropIsADraftWriteUndoTakesBack() {
        LayoutEditorPlan plan = DraftedSession.enter(places, PageKind.TERMINAL,
            PlaceOrientation.PORTRAIT);
        LayoutStore documents = places.layouts();
        PlaceLayout before = plan.arrangedLayout();
        assertNotEquals(Edge.TOP, before.slot(Element.EXTRA_KEYS).edge);

        PlaceLayout next = BarDrop.layoutAfter(before, Bar.EXTRA_KEYS, top(0));
        assertEquals(LayoutEditorPlan.Drop.LIVE, plan.writeSlots(next));

        assertEquals(Edge.TOP, plan.arrangedLayout().slot(Element.EXTRA_KEYS).edge);
        assertTrue(documents.isDraftDirty());

        documents.resetDraft();
        assertEquals(before.slot(Element.EXTRA_KEYS).edge,
            plan.arrangedLayout().slot(Element.EXTRA_KEYS).edge);
        assertFalse(documents.isDraftDirty());
    }

    @Test
    public void aRefusedDropWritesNothing() {
        LayoutEditorPlan plan = DraftedSession.enter(places, PageKind.TERMINAL,
            PlaceOrientation.PORTRAIT);
        Landing refused = new Landing(false, Zone.EDGE, Edge.TOP, 0, false, null, null);
        assertEquals(null, BarDrop.layoutAfter(plan.arrangedLayout(), Bar.EXTRA_KEYS, refused));
        assertFalse(places.layouts().isDraftDirty());
    }
}
