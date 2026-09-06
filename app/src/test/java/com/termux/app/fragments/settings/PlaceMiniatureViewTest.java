package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceOrientation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The Layout page's miniature follows the rows: a new arrangement at the same size redraws. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PlaceMiniatureViewTest {

    private static PlaceLayout layout(Edge statusBar, RowPlacement appsRow, RowPlacement extraKeys) {
        return new PlaceLayout(statusBar, appsRow, true, extraKeys, KeyboardMode.RESIZE, 4, 5);
    }

    private static PlaceMiniatureView sized() {
        PlaceMiniatureView view = new PlaceMiniatureView(RuntimeEnvironment.getApplication());
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1000, 400);
        return view;
    }

    @Test
    public void aRowChangeAtTheSameSizeMovesTheBlocks() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF appsBottom = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull(appsBottom);
        RectF canvas = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        assertTrue("apps row sits under the canvas", appsBottom.top >= canvas.bottom - 0.5f);

        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF appsLeft = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull(appsLeft);
        assertTrue("apps row now stands on the left edge", appsLeft.right <= appsBottom.left
            + appsBottom.width() / 2f);
        assertTrue(appsLeft.height() > appsLeft.width());
        // Hidden alphabets row: it only shows under a bottom apps row.
        assertNull(view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW));
    }

    @Test
    public void theStatusBarFollowsItsEdge() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.HIDDEN),
            PlaceOrientation.PORTRAIT);
        RectF top = view.blockRect(PlaceMiniatureView.Block.STATUS_BAR);
        view.setLayout(layout(Edge.BOTTOM, RowPlacement.HIDDEN, RowPlacement.HIDDEN),
            PlaceOrientation.PORTRAIT);
        RectF bottom = view.blockRect(PlaceMiniatureView.Block.STATUS_BAR);
        assertNotNull(top);
        assertNotNull(bottom);
        assertTrue(bottom.top > top.bottom);
        assertEquals(top.height(), bottom.height(), 0.5f);
    }

    @Test
    public void theFrameTurnsWithTheOrientation() {
        PlaceMiniatureView view = sized();
        PlaceLayout arrangement = layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM);
        view.setLayout(arrangement, PlaceOrientation.PORTRAIT);
        RectF portrait = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        view.setLayout(arrangement, PlaceOrientation.LANDSCAPE);
        RectF landscape = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        assertTrue(portrait.width() < portrait.height() * 1.5f);
        assertTrue(landscape.width() > landscape.height());
    }
}
