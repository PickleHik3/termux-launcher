package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The miniature draws a minimised A–Z index as its pull tab over the pane. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PlaceMiniatureTabTest {

    private static PlaceLayout layout(boolean azShown, Edge azEdge) {
        return new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, azShown, azEdge,
            RowPlacement.BOTTOM, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static PlaceMiniatureView sized() {
        PlaceMiniatureView view = new PlaceMiniatureView(RuntimeEnvironment.getApplication());
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1000, 400);
        return view;
    }

    @Test
    public void theTabLiesOverTheCanvasWhichKeepsTheRoomAnOffIndexLeaves() {
        PlaceMiniatureView view = sized();
        // The legend's labels differ ("hidden", "minimised") and would move the phone sideways.
        view.setLegendVisible(false);
        view.setLayout(layout(false, Edge.BOTTOM), PlaceOrientation.PORTRAIT,
            PaneWallPage.TERMINAL);
        RectF off = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        assertNull(view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW));

        view.setLayout(layout(true, Edge.BOTTOM).withAzMinimised(true), PlaceOrientation.PORTRAIT,
            PaneWallPage.TERMINAL);
        assertTrue(view.isAzTab());
        assertEquals("the tab claims no band", off,
            view.blockRect(PlaceMiniatureView.Block.CANVAS));
        RectF canvas = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        RectF tab = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        assertNotNull(tab);
        assertTrue("inside the pane", canvas.contains(tab));
        assertTrue("wider than it is tall", tab.width() > tab.height());
        assertTrue("at the lower end", tab.centerY() > canvas.centerY());
        assertTrue("at the leading end", tab.centerX() < canvas.centerX());
        assertFalse("not in the tray", view.isBlockHidden(PlaceMiniatureView.Block.ALPHABETS_ROW));
        assertNull(view.trayChipRect(PlaceMiniatureView.Block.ALPHABETS_ROW));
        RectF grip = view.gripRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        assertNotNull("it lifts like any bar", grip);
        assertTrue(tab.contains(grip));
    }

    @Test
    public void aSideTabStandsUpAtTheTopOfItsEdge() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(true, Edge.RIGHT).withAzMinimised(true), PlaceOrientation.LANDSCAPE,
            PaneWallPage.TERMINAL);
        RectF canvas = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        RectF tab = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        assertNotNull(tab);
        assertTrue(canvas.contains(tab));
        assertTrue(tab.height() > tab.width());
        assertTrue(tab.centerX() > canvas.centerX());
        assertEquals(canvas.top, tab.top, 0.01f);
    }

    @Test
    public void aHiddenMinimisedIndexIsInTheTrayLikeAnyOther() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(false, Edge.BOTTOM).withAzMinimised(true), PlaceOrientation.PORTRAIT,
            PaneWallPage.TERMINAL);
        assertFalse(view.isAzTab());
        assertTrue(view.isBlockHidden(PlaceMiniatureView.Block.ALPHABETS_ROW));
        assertNotNull(view.trayChipRect(PlaceMiniatureView.Block.ALPHABETS_ROW));
    }
}
