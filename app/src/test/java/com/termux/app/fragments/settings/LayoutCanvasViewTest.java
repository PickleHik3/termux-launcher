package com.termux.app.fragments.settings;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Build;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.place.ChromeShape;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.Card;
import com.termux.app.place.ChromeShape.Corners;
import com.termux.app.place.ChromeShape.Pane;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.ChromeShapeModel;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The layout canvas follows the rows: a new arrangement at the same size redraws. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LayoutCanvasViewTest {

    /** A parent that remembers being told to keep its hands off the rest of the gesture. */
    private static final class ScrollingParent extends FrameLayout {
        boolean disallowedIntercept;

        ScrollingParent(@NonNull Context context) {
            super(context);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallow) {
            if (disallow) disallowedIntercept = true;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static PlaceLayout layout(Edge statusBar, RowPlacement appsRow, RowPlacement extraKeys) {
        return new PlaceLayout(statusBar, appsRow, true, Edge.BOTTOM, extraKeys, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
    }

    private static PlaceLayout layout(Edge statusBar, RowPlacement appsRow, boolean azRowShown,
                                      RowPlacement extraKeys) {
        return new PlaceLayout(statusBar, appsRow, azRowShown, Edge.BOTTOM, extraKeys,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static PlaceLayout layout(Edge statusBar, RowPlacement appsRow, boolean azRowShown,
                                      Edge azBarEdge, RowPlacement extraKeys) {
        return new PlaceLayout(statusBar, appsRow, azRowShown, azBarEdge, extraKeys,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static PlaceLayout layout(RowPlacement appsRow, int widgetColumns, int widgetRows) {
        return new PlaceLayout(Edge.TOP, appsRow, true, Edge.BOTTOM, RowPlacement.BOTTOM,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, widgetColumns, widgetRows);
    }

    private static LayoutCanvasView sized() {
        return sized(1000, 400);
    }

    private static LayoutCanvasView sized(int width, int height) {
        LayoutCanvasView view = new LayoutCanvasView(RuntimeEnvironment.getApplication());
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        return view;
    }

    @Test
    public void aRowChangeAtTheSameSizeMovesTheBlocks() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF appsBottom = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(appsBottom);
        RectF canvas = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertTrue("apps row sits under the canvas", appsBottom.top >= canvas.bottom - 0.5f);

        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF appsLeft = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(appsLeft);
        assertTrue("apps row now stands on the left edge", appsLeft.right <= appsBottom.left
            + appsBottom.width() / 2f);
        assertTrue(appsLeft.height() > appsLeft.width());
        // The A–Z band no longer depends on the apps row: it is still on screen.
        assertNotNull(view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW));
    }

    @Test
    public void theStatusBarFollowsItsEdge() {
        // The A-Z index is off for this one: a band claims its share of what the bands outside it
        // left, so a bar measured against a bare edge and one measured under another is not the
        // same height, and the question here is which edge it went to.
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, false, RowPlacement.HIDDEN),
            PlaceOrientation.PORTRAIT);
        RectF top = view.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        view.setLayout(layout(Edge.BOTTOM, RowPlacement.HIDDEN, false, RowPlacement.HIDDEN),
            PlaceOrientation.PORTRAIT);
        RectF bottom = view.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        assertNotNull(top);
        assertNotNull(bottom);
        assertTrue(bottom.top > top.bottom);
        assertEquals(top.height(), bottom.height(), 0.5f);
    }

    @Test
    public void theFrameTurnsWithTheOrientation() {
        LayoutCanvasView view = sized();
        PlaceLayout arrangement = layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM);
        view.setLayout(arrangement, PlaceOrientation.PORTRAIT);
        RectF portrait = view.blockRect(LayoutCanvasView.Block.CANVAS);
        view.setLayout(arrangement, PlaceOrientation.LANDSCAPE);
        RectF landscape = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertTrue(portrait.width() < portrait.height() * 1.5f);
        assertTrue(landscape.width() > landscape.height());
    }

    @Test
    public void thePlaceChangesWhatTheCanvasDraws() {
        LayoutCanvasView view = sized();
        PlaceLayout arrangement = layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM);

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        assertEquals(LayoutCanvasView.CanvasKind.TERMINAL, view.canvasKind());

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.WIDGETS);
        assertEquals(LayoutCanvasView.CanvasKind.HOME_GRID, view.canvasKind());

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.DISPLAY);
        assertEquals(LayoutCanvasView.CanvasKind.DISPLAY, view.canvasKind());
    }

    @Test
    public void theWidgetGridCollapsesOnlyWhenACellWouldBeTooSmall() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(RowPlacement.BOTTOM, 4, 5), PlaceOrientation.PORTRAIT,
            PaneWallPage.WIDGETS);
        assertTrue("a modest grid draws real cells", !view.isWidgetGridCollapsed());

        view.setLayout(layout(RowPlacement.BOTTOM, 80, 80), PlaceOrientation.PORTRAIT,
            PaneWallPage.WIDGETS);
        assertTrue("an extreme grid collapses to one tinted rect", view.isWidgetGridCollapsed());
    }

    @Test
    public void theAlphabetsRowSitsBetweenThePinnedAppsAndTheExtraKeys() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF az = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(az);
        assertNotNull(keys);
        // The real dock, top to bottom: pinned apps, the A–Z index, then the extra keys.
        assertTrue("A–Z below the pinned apps", az.top >= apps.bottom - 0.5f);
        assertTrue("A–Z above the extra keys", az.bottom <= keys.top + 0.5f);
    }

    @Test
    public void everyBlockHasALegendRowAndHiddenOnesAreMarked() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        for (LayoutCanvasView.Block block : LayoutCanvasView.Block.values()) {
            assertNotNull("legend row for " + block, view.legendRect(block));
        }
        assertTrue("hidden apps row is marked", view.isBlockHidden(LayoutCanvasView.Block.APPS_ROW));
        assertTrue("A–Z shows regardless of the apps row",
            !view.isBlockHidden(LayoutCanvasView.Block.ALPHABETS_ROW));
        assertTrue("extra keys are on screen", !view.isBlockHidden(LayoutCanvasView.Block.EXTRA_KEYS));
        assertNull("a hidden block is off the picture",
            view.blockRect(LayoutCanvasView.Block.APPS_ROW));

        // The legend stands beside the phone, never over it.
        RectF frame = view.blockRect(LayoutCanvasView.Block.CANVAS);
        RectF legend = view.legendRect(LayoutCanvasView.Block.STATUS_BAR);
        assertNotNull(frame);
        assertTrue("legend clear of the phone", legend.left >= frame.right);
    }

    @Test
    public void withoutALegendThePhoneTakesTheWholeView() {
        // Narrow enough that the width, not the height, is what the phone has to fit inside.
        LayoutCanvasView view = sized(200, 400);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        RectF withLegend = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull(withLegend);
        assertNotNull(view.legendRect(LayoutCanvasView.Block.STATUS_BAR));

        view.setLegendVisible(false);
        RectF alone = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull(alone);
        assertNull("no legend row is laid out",
            view.legendRect(LayoutCanvasView.Block.STATUS_BAR));
        assertTrue("the phone grew into the legend's width", alone.width() > withLegend.width());
    }

    @Test
    public void theAlphabetsRowIsIndependentOfTheAppsRow() {
        LayoutCanvasView view = sized();

        // Apps row hidden entirely: the A–Z band still shows.
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, true, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNotNull("A–Z shows with the apps row hidden",
            view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW));

        // Apps row on a side rail: the A–Z band still shows, along the bottom.
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, true, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNotNull("A–Z shows with the apps row on a rail",
            view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW));

        // The switch itself, not the apps row, controls the band.
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, false, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNull("the switch being off hides the band",
            view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW));
    }

    @Test
    public void aSideRailStandsOutsideAnExtraKeysColumnOnTheSameSide() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, RowPlacement.LEFT),
            PlaceOrientation.LANDSCAPE);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(keys);
        // The real device: the pinned-apps rail sits at the screen edge, the extra-keys column is
        // padded inward by the rail's width.
        assertTrue("apps rail is at the left screen edge", apps.left <= keys.left - 0.5f);
        assertTrue("extra keys column stands to the right of the rail", keys.left >= apps.right - 0.5f);
    }

    @Test
    public void theAzBarEdgeAlwaysApplies() {
        LayoutCanvasView view = sized();
        // Riding the apps row is the two of them sharing the bottom: the band lies between the
        // pinned apps and the extra keys, which is the default arrangement.
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM), PlaceOrientation.LANDSCAPE);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF ridingRow = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(ridingRow);
        assertNotNull(keys);
        assertTrue("a bottom band is wider than it is tall",
            ridingRow.width() > ridingRow.height());
        assertTrue("below the pinned apps", ridingRow.top >= apps.bottom - 0.5f);
        assertTrue("above the extra keys", ridingRow.bottom <= keys.top + 0.5f);

        // Stored on a side with the apps row still along the bottom: it does not ride that row, so
        // it stands in a column of its own.
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.LEFT, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        RectF column = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        assertNotNull(column);
        assertTrue("its stored side edge stands it in a column", column.height() > column.width());
    }

    /**
     * The decision behind the same-edge rule, on the picture: dragging only the apps row to the
     * top leaves the index's band along the bottom, where the user left it.
     */
    @Test
    public void draggingOnlyTheAppsRowToTheTopLeavesTheIndexAtTheBottom() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM), PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        lift(view, LayoutCanvasView.Block.APPS_ROW);
        MiniatureDragPolicy.Slot top = view.slotFor(Edge.TOP);
        assertNotNull("the top edge is offered", top);
        touch(view, MotionEvent.ACTION_MOVE, top.centerX(), top.centerY());
        touch(view, MotionEvent.ACTION_UP, top.centerX(), top.centerY());

        PlaceLayout after = places.resolve(PlaceOrientation.LANDSCAPE);
        assertEquals(Edge.TOP, after.slot(Element.APPS).edge);
        assertEquals("the index stayed where it was", Edge.BOTTOM, after.slot(Element.AZ).edge);
        assertFalse("and so stopped riding the row", after.slot(Element.AZ).hidden);

        view.setLayout(after, PlaceOrientation.LANDSCAPE);
        RectF movedRow = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF index = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        assertNotNull(movedRow);
        assertNotNull(index);
        assertTrue("the row went to the top", movedRow.top < view.getHeight() / 2f);
        assertTrue("the index band is still at the bottom", index.top > movedRow.bottom);
    }

    @Test
    public void aTopAzBarStandsRightUnderTheStatusBar() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, true, Edge.TOP, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        RectF status = view.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        RectF az = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        assertNotNull(status);
        assertNotNull(az);
        assertTrue("the band sits right under the status bar", az.top >= status.bottom - 0.5f);
        assertTrue("a horizontal band is wider than it is tall", az.width() > az.height());
    }

    @Test
    public void aSideAzBarIsInnermostOfTheSideColumns() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, true, Edge.LEFT, RowPlacement.LEFT),
            PlaceOrientation.LANDSCAPE);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        RectF az = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        assertNotNull(apps);
        assertNotNull(keys);
        assertNotNull(az);
        assertTrue("the rail is outermost", apps.left <= keys.left - 0.5f);
        assertTrue("the extra-keys column is next", keys.left <= az.left - 0.5f);
        assertTrue("a vertical band is taller than it is wide", az.height() > az.width());

        // The right edge mirrors the same order from the other side.
        view.setLayout(layout(Edge.TOP, RowPlacement.RIGHT, true, Edge.RIGHT, RowPlacement.RIGHT),
            PlaceOrientation.LANDSCAPE);
        RectF appsRight = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF keysRight = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        RectF azRight = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        assertNotNull(appsRight);
        assertNotNull(keysRight);
        assertNotNull(azRight);
        assertTrue("the rail is outermost on the right too", appsRight.right >= keysRight.right + 0.5f);
        assertTrue("the extra-keys column is next", keysRight.right >= azRight.right + 0.5f);
    }

    @Test
    public void bottomStackingOrderIsUnchanged() {
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF az = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(az);
        assertNotNull(keys);
        // Bottom stack, outside in from the screen edge: extra keys, then A–Z, then pinned apps.
        assertTrue("extra keys are outermost", keys.bottom >= az.bottom - 0.5f);
        assertTrue("A–Z sits above the extra keys", az.bottom <= keys.top + 0.5f);
        assertTrue("pinned apps are innermost", apps.bottom <= az.top + 0.5f);
    }

    @Test
    public void aBottomStatusBarIsTheInnermostBandOfTheBottomStack() {
        // The launcher draws it above the whole dock; the picture used to claim it first and draw
        // it under everything, which is the one arrangement the two disagreed on.
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.BOTTOM, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF status = view.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF az = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        assertNotNull(status);
        assertNotNull(apps);
        assertNotNull(az);
        assertNotNull(keys);
        assertTrue("the extra keys are still against the screen edge", keys.bottom >= az.bottom);
        assertTrue("the status bar stands above the pinned apps",
            status.bottom <= apps.top + 0.5f);
    }

    @Test
    public void everyBandOnOneEdgeIsDrawnInTheStackOrder() {
        // All four down the left: the status column, the rail, the extra keys and then the index,
        // which is the order a side stack has always been drawn in.
        LayoutCanvasView view = sized();
        view.setLayout(layout(Edge.LEFT, RowPlacement.LEFT, true, Edge.LEFT, RowPlacement.LEFT),
            PlaceOrientation.LANDSCAPE);
        RectF status = view.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        RectF az = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        assertNotNull(status);
        assertNotNull(apps);
        assertNotNull(keys);
        assertNotNull(az);
        assertTrue("the status column is outermost", status.right <= apps.left + 0.5f);
        assertTrue("then the rail", apps.right <= keys.left + 0.5f);
        assertTrue("then the extra keys", keys.right <= az.left + 0.5f);
        RectF canvas = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull(canvas);
        assertTrue("and the canvas has what is left", az.right <= canvas.left + 0.5f);
    }

    @Test
    public void aReorderedStackIsDrawnInItsNewOrder() {
        LayoutCanvasView view = sized();
        PlaceLayout stacked = layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM);
        view.setLayout(stacked, PlaceOrientation.PORTRAIT);
        assertTrue("the extra keys start against the screen edge",
            view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS).bottom
                >= view.blockRect(LayoutCanvasView.Block.APPS_ROW).bottom);

        // The pinned apps pulled out to the screen edge, everything else pushed in behind them.
        view.setLayout(stacked
            .withSlot(Element.APPS, Slot.on(Edge.BOTTOM, 0))
            .withSlot(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, 1))
            .withSlot(Element.AZ, Slot.on(Edge.BOTTOM, 2)), PlaceOrientation.PORTRAIT);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        RectF az = view.blockRect(LayoutCanvasView.Block.ALPHABETS_ROW);
        assertNotNull(apps);
        assertNotNull(keys);
        assertNotNull(az);
        assertTrue("the pinned apps are outermost now", apps.bottom >= keys.bottom - 0.5f);
        assertTrue("the extra keys stand above them", keys.bottom <= apps.top + 0.5f);
        assertTrue("and the index above those", az.bottom <= keys.top + 0.5f);
    }

    // ---- Lifting, slots and the drag -----------------------------------------------------------

    private static LayoutCanvasView inParent(ScrollingParent parent, int width, int height) {
        LayoutCanvasView view = new LayoutCanvasView(parent.getContext());
        parent.addView(view, new FrameLayout.LayoutParams(width, height));
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, width, height);
        return view;
    }

    private static ScrollingParent parent() {
        return new ScrollingParent(RuntimeEnvironment.getApplication());
    }

    private static void touch(LayoutCanvasView view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    /** How far a finger has to travel before the bar under it is lifted rather than tapped. */
    private static float slop(LayoutCanvasView view) {
        return ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    /**
     * Lifts a bar the way a finger does now that bars carry no grip: pressed anywhere on it, in
     * the middle here, and moved past the touch slop.
     */
    private static void lift(LayoutCanvasView view, LayoutCanvasView.Block bar) {
        RectF rect = bar == LayoutCanvasView.Block.KEYBOARD ? view.keyboardRect()
            : view.blockRect(bar);
        assertNotNull("a bar to press: " + bar, rect);
        touch(view, MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY());
        touch(view, MotionEvent.ACTION_MOVE, rect.centerX(), rect.centerY() + 3f * slop(view));
    }

    /** Writes a drop the way the Layout editor does, for one canvas's orientation. */
    private static LayoutCanvasView.OnBarDroppedListener writer(
        PlaceLayoutStore places, PlaceOrientation orientation) {
        return (bar, edge, index) -> {
            MiniatureDragPolicy.Bar dragged = LayoutCanvasView.barOf(bar);
            assertNotNull(dragged);
            LayoutChooserModel.applyDrop(places, orientation, dragged, edge,
                index);
        };
    }

    private static PlaceLayoutStore store() {
        TermuxAppSharedPreferences preferences =
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true);
        assertNotNull(preferences);
        return new PlaceLayoutStore(preferences);
    }

    private static SharedPreferences prefs() {
        TermuxAppSharedPreferences preferences =
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true);
        assertNotNull(preferences);
        return preferences.getSharedPreferences();
    }

    @Test
    public void everyBarWithAPlacementIsLiftedFromAnywhereOnItAndNothingElseIs() {
        for (LayoutCanvasView.Block bar : new LayoutCanvasView.Block[]{
            LayoutCanvasView.Block.STATUS_BAR, LayoutCanvasView.Block.APPS_ROW,
            LayoutCanvasView.Block.ALPHABETS_ROW, LayoutCanvasView.Block.EXTRA_KEYS}) {
            LayoutCanvasView view = inParent(parent(), 1000, 400);
            view.setLegendVisible(false);
            view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
                PlaceOrientation.PORTRAIT);
            RectF band = view.blockRect(bar);
            assertNotNull(band);
            // Any point of the band will do, its corner as much as its middle.
            touch(view, MotionEvent.ACTION_DOWN, band.left + 1f, band.top + 1f);
            touch(view, MotionEvent.ACTION_MOVE, band.left + 1f, band.top + 1f + 3f * slop(view));
            assertEquals("lifted: " + bar, bar, view.draggedBar());
        }
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF canvas = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull(canvas);
        touch(view, MotionEvent.ACTION_DOWN, canvas.centerX(), canvas.centerY());
        touch(view, MotionEvent.ACTION_MOVE, canvas.centerX(),
            canvas.centerY() + 3f * slop(view));
        assertNull("the terminal has no placement to drag", view.draggedBar());
    }

    /**
     * A hidden bar is listed for the tray, where the editor stands a real chip for it that brings
     * it back on a tap; the canvas itself draws nothing for it and there is nothing to lift.
     */
    @Test
    public void aHiddenBarIsListedForTheTrayAndIsNotOnThePhone() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNull("nothing is drawn on the phone for it",
            view.blockRect(LayoutCanvasView.Block.APPS_ROW));
        assertTrue("the hidden bar is listed for the tray",
            view.hiddenBlocks().contains(LayoutCanvasView.Block.APPS_ROW));
        assertFalse("a bar on the phone is not",
            view.hiddenBlocks().contains(LayoutCanvasView.Block.EXTRA_KEYS));
        RectF frame = view.frameRect();
        assertTrue("the tray stands under the phone", view.trayRect().top >= frame.bottom);
    }

    @Test
    public void aPressAndAMovePastTheSlopLiftsTheBarAndStopsTheListScrolling() {
        ScrollingParent parent = parent();
        LayoutCanvasView view = inParent(parent, 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);

        RectF band = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(band);
        touch(view, MotionEvent.ACTION_DOWN, band.centerX(), band.centerY());
        assertNull("a press alone lifts nothing", view.draggedBar());
        touch(view, MotionEvent.ACTION_MOVE, band.centerX(), band.centerY() + slop(view) / 2f);
        assertNull("nor does a move inside the slop", view.draggedBar());
        touch(view, MotionEvent.ACTION_MOVE, band.centerX(), band.centerY() + 3f * slop(view));
        assertEquals("past the slop the bar is up", LayoutCanvasView.Block.APPS_ROW,
            view.draggedBar());
        assertTrue("the preference list is told to keep out", parent.disallowedIntercept);
        assertNotNull("landscape offers a column down the left", view.slotFor(Edge.LEFT));
        assertNotNull(view.slotFor(Edge.RIGHT));
        assertNotNull("every bar stands on every edge now", view.slotFor(Edge.TOP));
    }

    @Test
    public void aTapOnABarLiftsNothingAndSelectsIt() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);

        RectF band = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(band);
        touch(view, MotionEvent.ACTION_DOWN, band.left + 2f, band.centerY());
        touch(view, MotionEvent.ACTION_UP, band.left + 2f, band.centerY());
        assertNull("a tap on the band is a tap", view.draggedBar());
        assertEquals("which selects it", LayoutCanvasView.Block.APPS_ROW, view.selectedBlock());
        assertTrue("and nothing is outlined", view.slots().isEmpty());
    }

    @Test
    public void aDragOntoASideSlotWritesThatEdgeForTheOrientationItWasDoneIn() {
        PlaceLayoutStore places = store();
        LayoutCanvasView landscape = inParent(parent(), 1000, 400);
        landscape.setLegendVisible(false);
        landscape.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        landscape.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        lift(landscape, LayoutCanvasView.Block.APPS_ROW);
        MiniatureDragPolicy.Slot slot = landscape.slotFor(Edge.LEFT);
        assertNotNull(slot);
        touch(landscape, MotionEvent.ACTION_MOVE, slot.centerX(), slot.centerY());
        touch(landscape, MotionEvent.ACTION_UP, slot.centerX(), slot.centerY());

        assertNull("the gesture is over", landscape.draggedBar());
        assertEquals("left", prefs().getString("layout.landscape.apps_row", null));
        assertNull("portrait was not touched",
            prefs().getString("layout.portrait.apps_row", null));

        // The same drag on the portrait canvas writes portrait's own key.
        LayoutCanvasView portrait = inParent(parent(), 1000, 400);
        portrait.setLegendVisible(false);
        portrait.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        portrait.setOnBarDroppedListener(writer(places, PlaceOrientation.PORTRAIT));
        lift(portrait, LayoutCanvasView.Block.APPS_ROW);
        assertNotNull("portrait offers the side columns too", portrait.slotFor(Edge.LEFT));
        RectF tray = portrait.trayRect();
        touch(portrait, MotionEvent.ACTION_MOVE, tray.centerX(), tray.centerY());
        touch(portrait, MotionEvent.ACTION_UP, tray.centerX(), tray.centerY());
        assertEquals("hidden", prefs().getString("layout.portrait.apps_row", null));
        assertEquals("landscape kept the column it was given", "left",
            prefs().getString("layout.landscape.apps_row", null));
    }

    @Test
    public void aReleaseOffEverySlotWritesNothing() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        lift(view, LayoutCanvasView.Block.EXTRA_KEYS);
        // The middle of the canvas is inside no slot at all.
        RectF canvas = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull(canvas);
        touch(view, MotionEvent.ACTION_MOVE, canvas.centerX(), canvas.centerY());
        touch(view, MotionEvent.ACTION_UP, canvas.centerX(), canvas.centerY());

        assertNull("nothing was written for the extra keys",
            prefs().getString("layout.landscape.extra_keys", null));
        assertNull("nor for anything else",
            prefs().getString("layout.landscape.apps_row", null));
    }

    @Test
    public void theStatusBarIsOfferedTheTrayLikeAnyOtherBar() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        lift(view, LayoutCanvasView.Block.STATUS_BAR);
        assertNotNull("it may stand on any edge", view.slotFor(Edge.TOP));
        assertNotNull(view.slotFor(Edge.LEFT));
        boolean tray = false;
        for (MiniatureDragPolicy.Slot slot : view.slots()) tray |= slot.isTray();
        assertTrue("and the tray is where it is put away", tray);

        RectF shelf = view.trayRect();
        touch(view, MotionEvent.ACTION_MOVE, shelf.centerX(), shelf.centerY());
        touch(view, MotionEvent.ACTION_UP, shelf.centerX(), shelf.centerY());
        assertEquals("hidden", prefs().getString("layout.landscape.status_bar", null));

        // Put away, it is listed for the tray's chips and is gone from the phone.
        view.setLayout(places.resolve(PlaceOrientation.LANDSCAPE), PlaceOrientation.LANDSCAPE);
        assertNull(view.blockRect(LayoutCanvasView.Block.STATUS_BAR));
        assertTrue(view.hiddenBlocks().contains(LayoutCanvasView.Block.STATUS_BAR));
        assertEquals(LayoutCanvasView.TrayState.CHIPS, view.trayState());
    }

    /** At rest a touch on the tray (off its trash) is not the canvas's: the editor's views stand there. */
    @Test
    public void aTouchOnTheTrayAtRestGoesOnToTheChipsUnderIt() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF tray = view.trayRect();
        MotionEvent down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, tray.centerX(),
            tray.centerY(), 0);
        assertFalse(view.onTouchEvent(down));
        down.recycle();
    }

    // ---- Selection and handles ----------------------------------------------------------------

    /** A tap selects an element; the dock's selection carries one handle on its inner edge. */
    @Test
    public void aTapSelectsTheDockAndItsHandleStandsOnItsInnerEdge() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        RectF band = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(band);
        touch(view, MotionEvent.ACTION_DOWN, band.left + 2f, band.centerY());
        touch(view, MotionEvent.ACTION_UP, band.left + 2f, band.centerY());
        assertEquals(LayoutCanvasView.Block.APPS_ROW, view.selectedBlock());
        RectF handle = view.handleRect(LayoutCanvasView.Handle.DOCK_HEIGHT);
        assertNotNull(handle);
        assertEquals("on the edge that faces the canvas", band.top, handle.centerY(), 1f);
        assertNull("no keyboard handles for the dock",
            view.handleRect(LayoutCanvasView.Handle.KEYBOARD_HEIGHT));
    }

    /** Dragging the dock's handle up reports a taller dock; letting go ends the gesture. */
    @Test
    public void theDocksHandleDraggedUpAsksForATallerDock() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        float[] asked = {Float.NaN};
        boolean[] released = {false};
        view.setOnCanvasEditListener(new LayoutCanvasView.OnCanvasEditListener() {
            @Override public void onDockHeightDragged(float scale) {
                asked[0] = scale;
            }

            @Override public void onHandleReleased() {
                released[0] = true;
            }
        });
        view.setSelectedBlock(LayoutCanvasView.Block.APPS_ROW);
        RectF handle = view.handleRect(LayoutCanvasView.Handle.DOCK_HEIGHT);
        assertNotNull(handle);
        touch(view, MotionEvent.ACTION_DOWN, handle.centerX(), handle.centerY());
        assertEquals(LayoutCanvasView.Handle.DOCK_HEIGHT, view.draggedHandle());
        assertNull("a handle is not a lift", view.draggedBar());
        touch(view, MotionEvent.ACTION_MOVE, handle.centerX(), handle.centerY() - 10f);
        assertTrue("taller", asked[0] > com.termux.shared.termux.settings.preferences
            .TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT);
        touch(view, MotionEvent.ACTION_UP, handle.centerX(), handle.centerY() - 10f);
        assertNull(view.draggedHandle());
        assertTrue(released[0]);
    }

    /** The keyboard, selected, has a handle on its top and one on the bottom of its keys. */
    @Test
    public void theKeyboardSelectedCarriesItsHeightAndChinHandles() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        RectF keyboard = view.keyboardRect();
        assertNotNull(keyboard);
        touch(view, MotionEvent.ACTION_DOWN, keyboard.centerX(), keyboard.centerY());
        touch(view, MotionEvent.ACTION_UP, keyboard.centerX(), keyboard.centerY());
        assertEquals(LayoutCanvasView.Block.KEYBOARD, view.selectedBlock());
        RectF height = view.handleRect(LayoutCanvasView.Handle.KEYBOARD_HEIGHT);
        RectF chin = view.handleRect(LayoutCanvasView.Handle.KEYBOARD_CHIN);
        assertNotNull(height);
        assertNotNull(chin);
        assertTrue("the height handle is above the chin's", height.centerY() < chin.centerY());
    }

    /** The keyboard lifted off the phone may go to the tray alone, and is put away there. */
    @Test
    public void theKeyboardDraggedIntoTheTrayIsPutAway() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        boolean[] putAway = {false};
        view.setOnCanvasEditListener(new LayoutCanvasView.OnCanvasEditListener() {
            @Override public void onKeyboardPutAway() {
                putAway[0] = true;
            }
        });
        RectF keyboard = view.keyboardRect();
        assertNotNull(keyboard);
        RectF tray = view.trayRect();
        touch(view, MotionEvent.ACTION_DOWN, keyboard.centerX(), keyboard.centerY());
        touch(view, MotionEvent.ACTION_MOVE, tray.centerX(), tray.centerY());
        assertEquals(LayoutCanvasView.Block.KEYBOARD, view.draggedBar());
        for (MiniatureDragPolicy.Slot slot : view.slots())
            assertTrue("the tray is the keyboard's one target", slot.isTray());
        touch(view, MotionEvent.ACTION_UP, tray.centerX(), tray.centerY());
        assertTrue(putAway[0]);
    }

    @Test
    public void aCornerHandleSnapsToWholeCells() {
        // The first cell's corner at a third of the way across a 300px area with a 6px gap.
        assertEquals(3, LayoutCanvasView.cellsFor(0f, 300f, 96f, 6f, 1, 8));
        assertEquals("dragged out, bigger and fewer cells", 2,
            LayoutCanvasView.cellsFor(0f, 300f, 147f, 6f, 1, 8));
        assertEquals("never past the store's range", 8,
            LayoutCanvasView.cellsFor(0f, 300f, 2f, 6f, 1, 8));
    }

    @Test
    public void aScaleDraggedKeepsTheEdgeUnderTheFingerAndStaysInRange() {
        assertEquals(2f, LayoutCanvasView.scaleFor(1f, 50f, 50f, 0.5f, 3f), 1e-4f);
        assertEquals(0.5f, LayoutCanvasView.scaleFor(1f, 50f, -100f, 0.5f, 3f), 1e-4f);
        assertEquals(3f, LayoutCanvasView.scaleFor(1f, 50f, 500f, 0.5f, 3f), 1e-4f);
    }

    @Test
    public void theAzIndexRidingThePinnedAppsIsDraggedOffTheRowOrIntoTheTray() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        lift(view, LayoutCanvasView.Block.ALPHABETS_ROW);
        // Riding is the two of them sharing an edge, so the index may be lifted off the row onto
        // any other — and the tray is still there for putting it away.
        assertNotNull("every edge is a target", view.slotFor(Edge.LEFT));
        assertNotNull(view.slotFor(Edge.TOP));
        RectF tray = view.trayRect();
        touch(view, MotionEvent.ACTION_MOVE, tray.centerX(), tray.centerY());
        touch(view, MotionEvent.ACTION_UP, tray.centerX(), tray.centerY());
        assertFalse(prefs().getBoolean("layout.landscape.az_row", true));
    }

    @Test
    public void anEdgeOffersAGapBetweenEveryPairOfItsBands() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);

        lift(view, LayoutCanvasView.Block.EXTRA_KEYS);
        // The bottom keeps the A-Z index and the pinned apps while the keys are in the air: a gap
        // outside the index, one between it and the apps row, and one against the canvas.
        assertNotNull(view.slotFor(Edge.BOTTOM, 0));
        assertNotNull(view.slotFor(Edge.BOTTOM, 1));
        assertNotNull(view.slotFor(Edge.BOTTOM, 2));
        assertNull("three bands minus the one in the air is three gaps",
            view.slotFor(Edge.BOTTOM, 3));

        // They run outermost first and cover the edge end to end, so a finger is never between two.
        MiniatureDragPolicy.Slot outer = view.slotFor(Edge.BOTTOM, 0);
        MiniatureDragPolicy.Slot inner = view.slotFor(Edge.BOTTOM, 2);
        assertTrue("the outermost gap is nearest the screen edge", outer.line > inner.line);
        assertTrue("and they meet", outer.top <= view.slotFor(Edge.BOTTOM, 1).bottom + 0.5f);

        // The finger picks the gap it is on, not merely the edge.
        touch(view, MotionEvent.ACTION_MOVE, inner.centerX(), inner.centerY());
        assertNotNull(view.hoveredSlot());
        assertEquals(2, view.hoveredSlot().index);
        touch(view, MotionEvent.ACTION_MOVE, outer.centerX(), outer.centerY());
        assertEquals(0, view.hoveredSlot().index);
    }

    @Test
    public void aDropIntoAGapWritesEveryBandOnThatEdgeItsNewPosition() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.PORTRAIT));

        // The pinned apps lifted off the innermost band of the bottom and dropped against the
        // screen edge: every band down there is renumbered, not only the one that moved.
        lift(view, LayoutCanvasView.Block.APPS_ROW);
        MiniatureDragPolicy.Slot outermost = view.slotFor(Edge.BOTTOM, 0);
        assertNotNull(outermost);
        touch(view, MotionEvent.ACTION_MOVE, outermost.centerX(), outermost.centerY());
        touch(view, MotionEvent.ACTION_UP, outermost.centerX(), outermost.centerY());

        assertEquals(0, prefs().getInt("layout.portrait.apps_row_order", -1));
        assertEquals(1, prefs().getInt("layout.portrait.extra_keys_order", -1));
        assertEquals(2, prefs().getInt("layout.portrait.az_bar_order", -1));
        assertEquals("the bar did not leave the bottom", "bottom",
            prefs().getString("layout.portrait.apps_row", null));
        assertEquals("nothing on another edge was touched", 0,
            places.slotOrder(PlaceOrientation.PORTRAIT,
                Element.STATUS));
    }

    // ---- Side columns --------------------------------------------------------------------------

    /** The shipped arrangement with the pinned apps standing as a column down the left. */
    private static PlaceLayout appsOnTheLeft() {
        return layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM)
            .withSlot(Element.APPS, Slot.on(Edge.LEFT, 0));
    }

    @Test
    public void aSideColumnStandsBetweenTheRowsTheWayTheScreenDoes() {
        // The defect: the columns were claimed before the bottom rows, so a rail ran the whole
        // height of the phone — past the dock and into its corner — while the dock's rows were
        // narrowed by it. The screen has done neither since the canvas band.
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(appsOnTheLeft(), PlaceOrientation.PORTRAIT);

        RectF column = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        RectF canvas = view.blockRect(LayoutCanvasView.Block.CANVAS);
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        assertNotNull(column);
        assertNotNull(canvas);
        assertNotNull(keys);
        // The canvas is the rounded insert, a gutter inside the bars, so the column flanks it
        // from outside: no shorter than the insert, and never down past the dock's rows.
        assertTrue("the column flanks the canvas", column.top <= canvas.top + 0.5f);
        assertTrue(column.bottom >= canvas.bottom - 0.5f);
        assertTrue("the column stops at the dock's rows", column.bottom <= keys.top + 0.5f);
        assertTrue("the dock's row keeps the whole width", keys.left <= column.left + 0.5f);
    }

    @Test
    public void aSideColumnIsLiftedFromAnywhereAcrossIt() {
        ScrollingParent parent = parent();
        LayoutCanvasView view = inParent(parent, 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(appsOnTheLeft(), PlaceOrientation.PORTRAIT);

        RectF column = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(column);
        // A column the picture draws is a few dp wide: pressed at its middle, moved along it.
        touch(view, MotionEvent.ACTION_DOWN, column.centerX(), column.centerY());
        touch(view, MotionEvent.ACTION_MOVE, column.centerX(),
            column.centerY() + 3f * slop(view));
        assertEquals(LayoutCanvasView.Block.APPS_ROW, view.draggedBar());
        assertEquals(Element.APPS,
            LayoutCanvasView.barOf(view.draggedBar()).element());
        assertTrue("the preference list is told to keep out", parent.disallowedIntercept);
    }

    @Test
    public void thePictureNamesEveryBarAndWhereItStands() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        String description = String.valueOf(view.getContentDescription());
        assertTrue(description, description.contains("Status bar"));
        assertTrue(description, description.contains("Top"));
        assertTrue("a hidden bar is named as hidden", description.contains("Hidden"));
        assertTrue(description, description.contains("Extra keys"));
        assertTrue(description, description.contains("A–Z index"));
    }

    // ---- The paint: the miniature shows what the screen shows -----------------------------------

    @Test
    public void everyStripIsTheThemesRaisedContainerAndNotARoleColour() {
        // The artwork maps its placeholders to theme roles: the surface for the phone and the
        // pane, the raised container for every strip. No strip wears an accent of its own.
        Context app = RuntimeEnvironment.getApplication();
        int surface = ContextCompat.getColor(app, R.color.termux_surface_base);
        int container = ContextCompat.getColor(app, R.color.termux_surface_panel_high);
        int[] roles = {R.color.termux_primary, R.color.termux_secondary,
            R.color.termux_accent_container, R.color.termux_tertiary_container};
        for (LayoutCanvasView.Block bar : new LayoutCanvasView.Block[]{
            LayoutCanvasView.Block.STATUS_BAR, LayoutCanvasView.Block.APPS_ROW,
            LayoutCanvasView.Block.ALPHABETS_ROW, LayoutCanvasView.Block.EXTRA_KEYS}) {
            int fill = LayoutCanvasView.blockColor(app, bar);
            assertEquals(bar + " is the raised container", container, fill);
            assertEquals(bar + " is opaque: a card, not glass", 255, Color.alpha(fill));
            assertNotEquals(bar + " stands off the surface", surface, fill);
            for (int role : roles) {
                assertNotEquals(bar + " does not wear a role colour",
                    ContextCompat.getColor(app, role) & 0xFFFFFF, fill & 0xFFFFFF);
            }
        }
        assertEquals("the canvas is the surface itself",
            surface, LayoutCanvasView.blockColor(app, LayoutCanvasView.Block.CANVAS));
    }

    @Test
    public void everyStripCarriesTheSameContainer() {
        Context app = RuntimeEnvironment.getApplication();
        int status = LayoutCanvasView.blockColor(app, LayoutCanvasView.Block.STATUS_BAR);
        assertEquals(status, LayoutCanvasView.blockColor(app, LayoutCanvasView.Block.APPS_ROW));
        assertEquals(status,
            LayoutCanvasView.blockColor(app, LayoutCanvasView.Block.ALPHABETS_ROW));
        assertEquals(status,
            LayoutCanvasView.blockColor(app, LayoutCanvasView.Block.EXTRA_KEYS));
    }

    @Test
    public void thePhoneIsDrawnInUnitsOfItsOwnShortSide() {
        // The artwork is laid out on a 240-wide phone; whatever size the editor gives the frame,
        // its corner and its rim are the design's, scaled by that unit.
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF canvas = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull(canvas);
        float unit = view.unitPx();
        assertTrue("one unit is a 240th of the short side, so under a px here", unit < 1f);
        assertEquals("the corner is the design's 24 units", 24f * unit, view.frameRadiusPx(), 0.01f);
        assertEquals("the rim is the design's 1.5 units", 1.5f * unit, view.frameStrokePx(), 0.01f);

        // A larger view scales the unit with it: the picture grows rather than the gaps.
        LayoutCanvasView larger = sized(1000, 800);
        larger.setLegendVisible(false);
        larger.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertTrue(larger.unitPx() > unit * 1.8f);
    }

    @Test
    public void theKeyboardBlockStandsAlongTheBottomWhereThePlaceShowsIt() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        PlaceLayout arrangement = layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM);

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        RectF keyboard = view.keyboardRect();
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        RectF canvas = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull("the terminal shows its keyboard", keyboard);
        assertNotNull(keys);
        assertNotNull(canvas);
        assertTrue("it is the outermost block along the bottom: the dock lifts above it",
            keys.bottom <= keyboard.top + 0.5f);
        assertTrue("and it takes a real share of the phone", keyboard.height() > canvas.height() * 0.3f);

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.WIDGETS);
        assertNull("Home's keyboard opens over the page, so the picture keeps the page",
            view.keyboardRect());

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.DISPLAY);
        assertNotNull("a keyboard that shrinks the display is a block of it", view.keyboardRect());
        view.setLayout(new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM, KeyboardMode.OVERLAY, KeyboardForm.DOCKED, 4, 5),
            PlaceOrientation.PORTRAIT, PaneWallPage.DISPLAY);
        assertNull("one that floats is drawn on the pane instead", view.keyboardRect());

        view.setLayout(arrangement.withKeyboardShown(false), PlaceOrientation.PORTRAIT,
            PaneWallPage.TERMINAL);
        assertNull("the keyboard element off is no keyboard at all", view.keyboardRect());
        RectF taller = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertNotNull(taller);
        assertTrue("and the pane takes the room it left", taller.height() > canvas.height());
    }

    @Test
    public void theShelfIsUnderThePhoneBeforeAnythingIsPutAway() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertEquals("nothing is hidden, but the shelf is still drawn",
            LayoutCanvasView.TrayState.EMPTY, view.trayState());
        assertFalse("and it has room of its own", view.trayRect().isEmpty());

        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertEquals("a put-away bar fills it with chips",
            LayoutCanvasView.TrayState.CHIPS, view.trayState());
    }

    @Test
    public void theShelfOffersItselfWhileABarIsInTheAir() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        lift(view, LayoutCanvasView.Block.APPS_ROW);
        assertEquals("the lifted bar may be dropped on the shelf",
            LayoutCanvasView.TrayState.OFFERING, view.trayState());

        RectF shelf = view.trayRect();
        touch(view, MotionEvent.ACTION_UP, shelf.centerX(), shelf.centerY());
        assertEquals("and the shelf goes back to resting",
            LayoutCanvasView.TrayState.EMPTY, view.trayState());
    }

    @Test
    public void theShelfOffersItselfToTheStatusBarInTheAir() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        lift(view, LayoutCanvasView.Block.STATUS_BAR);
        assertEquals("the status bar hides like the rest now",
            LayoutCanvasView.TrayState.OFFERING, view.trayState());
    }

    // ---- Under the keyboard -----------------------------------------------------------------

    /** The shipped arrangement, with the extra keys standing under the keyboard. */
    private static PlaceLayout keysUnderKeyboard() {
        return layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM)
            .withSlot(Element.EXTRA_KEYS, new Slot(false, Edge.BOTTOM, 0, true));
    }

    @Test
    public void aBandUnderTheKeyboardIsDrawnBelowItAndTheRestAbove() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(keysUnderKeyboard(), PlaceOrientation.PORTRAIT);
        RectF keyboard = view.keyboardRect();
        RectF keys = view.blockRect(LayoutCanvasView.Block.EXTRA_KEYS);
        RectF apps = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(keyboard);
        assertNotNull(keys);
        assertNotNull(apps);
        assertTrue("the keys stand under the keyboard", keys.top >= keyboard.bottom - 0.5f);
        assertTrue("the apps row over it", apps.bottom <= keyboard.top + 0.5f);
    }

    @Test
    public void theKeyboardOffersADropSlotUnderItAndTheDropSaysSo() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        boolean[] heardUnder = {false};
        view.setOnBarDroppedListener(new LayoutCanvasView.OnBarDroppedListener() {
            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar, Edge edge,
                                     int index) {
                onBarDropped(bar, edge, index, false);
            }

            @Override
            public void onBarDropped(@NonNull LayoutCanvasView.Block bar, Edge edge, int index,
                                     boolean underKeyboard) {
                heardUnder[0] = underKeyboard;
                MiniatureDragPolicy.Bar dragged = LayoutCanvasView.barOf(bar);
                assertNotNull(dragged);
                LayoutChooserModel.applyDrop(places, PlaceOrientation.PORTRAIT, dragged, edge,
                    index, underKeyboard);
            }
        });

        lift(view, LayoutCanvasView.Block.APPS_ROW);
        RectF keyboard = view.keyboardRect();
        assertNotNull(keyboard);
        MiniatureDragPolicy.Slot under = view.underKeyboardSlotFor(0);
        assertNotNull("the keyboard has a slot under it", under);
        assertTrue(under.underKeyboard);
        assertTrue("it is under the keyboard's middle", under.top >= keyboard.centerY() - 0.5f);
        MiniatureDragPolicy.Slot over = view.slotFor(Edge.BOTTOM, 0);
        assertNotNull(over);
        assertTrue("and the bottom's own gaps start above it",
            over.bottom <= keyboard.centerY() + 0.5f);

        touch(view, MotionEvent.ACTION_MOVE, under.centerX(), under.centerY());
        touch(view, MotionEvent.ACTION_UP, under.centerX(), under.centerY());
        assertTrue(heardUnder[0]);
        PlaceLayout after = places.resolve(PlaceOrientation.PORTRAIT);
        assertTrue(after.slot(Element.APPS).underKeyboard);
        assertEquals(Edge.BOTTOM, after.slot(Element.APPS).edge);
    }

    @Test
    public void withNoKeyboardDrawnThereIsNothingToBeUnder() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        // Home: the keyboard opens over the page, so the picture draws none.
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT, PaneWallPage.WIDGETS);
        assertNull(view.keyboardRect());
        lift(view, LayoutCanvasView.Block.APPS_ROW);
        assertNull(view.underKeyboardSlotFor(0));
    }

    @Test
    public void theStatusBarIsOfferedNoSlotUnderTheKeyboard() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        lift(view, LayoutCanvasView.Block.STATUS_BAR);
        assertNull(view.underKeyboardSlotFor(0));
    }

    // ---- Every shape is the model's ------------------------------------------------------------

    private static final float CORNERS_DP = 20f;
    private static final float MARGIN_DP = 12f;

    /** The pack's bottom-bars preview: status over the pane, dock, A-Z and extra keys under it. */
    private static PlaceLayout bottomBars() {
        return layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.BOTTOM);
    }

    /** The pack's side-bars preview: the dock as a rail, A-Z down the other side. */
    private static PlaceLayout sideBars() {
        return layout(Edge.TOP, RowPlacement.LEFT, true, Edge.RIGHT, RowPlacement.BOTTOM);
    }

    private static LayoutCanvasView styled(LayoutStyle style, PlaceLayout arrangement) {
        LayoutCanvasView view = sized(1000, 800);
        view.setLegendVisible(false);
        view.setShape(style, CORNERS_DP, MARGIN_DP);
        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        return view;
    }

    private static LayoutCanvasView.Block blockOf(PieceId id) {
        switch (id) {
            case STATUS: return LayoutCanvasView.Block.STATUS_BAR;
            case APPS: return LayoutCanvasView.Block.APPS_ROW;
            case AZ: return LayoutCanvasView.Block.ALPHABETS_ROW;
            case EXTRA_KEYS: return LayoutCanvasView.Block.EXTRA_KEYS;
            default: return LayoutCanvasView.Block.KEYBOARD;
        }
    }

    private static void assertBox(String what, Box expected, RectF frame, RectF actual) {
        assertEquals(what + " left", expected.left + frame.left, actual.left, 0.01f);
        assertEquals(what + " top", expected.top + frame.top, actual.top, 0.01f);
        assertEquals(what + " right", expected.right + frame.left, actual.right, 0.01f);
        assertEquals(what + " bottom", expected.bottom + frame.top, actual.bottom, 0.01f);
    }

    private static void assertRadii(String what, Corners expected, float[] actual) {
        assertArrayEquals(what, expected.toRadii(), actual, 0.001f);
    }

    /**
     * The fill, the one selection outline and the dashed placeholder are the shape model's
     * pieces, cards and opening, for the pack's three previews: the Floating cards, and Docked
     * with bottom bars and with side bars, composed under one frame.
     */
    @Test
    public void theFillTheSelectionAndThePlaceholderAreTheModelsShapesForThePacksPreviews() {
        Object[][] previews = {
            {LayoutStyle.FLOATING, bottomBars()},
            {LayoutStyle.DOCKED, bottomBars()},
            {LayoutStyle.DOCKED, sideBars()}};
        for (Object[] preview : previews) {
            LayoutStyle style = (LayoutStyle) preview[0];
            PlaceLayout arrangement = (PlaceLayout) preview[1];
            String name = style + " " + arrangement;
            LayoutCanvasView view = styled(style, arrangement);
            ChromeShape shape = ChromeShapeModel.shape(view.shapeInput());
            RectF frame = view.frameRect();
            assertTrue(name, !shape.pieces().isEmpty());

            for (Piece piece : shape.pieces()) {
                LayoutCanvasView.Block block = blockOf(piece.id);
                String at = name + " " + piece.id;
                if (block != LayoutCanvasView.Block.KEYBOARD) {
                    assertBox(at + " block", piece.box, frame, view.blockRect(block));
                }
                // The selection outline runs along the piece itself, at the model's corners.
                java.util.List<LayoutCanvasView.ShapeSpec> outline = view.selectionShapes(block);
                assertEquals(at, 1, outline.size());
                assertBox(at + " outline", piece.box, frame, outline.get(0).box);
                assertRadii(at + " outline", piece.corners, outline.get(0).radii);

                // The fill is the card the piece is in: the Docked frame with the opening cut
                // out of it, or the Floating card.
                Card card = shape.cardOf(piece);
                assertNotNull(at, card);
                LayoutCanvasView.ShapeSpec fill = view.fillShape(block);
                assertNotNull(at, fill);
                assertBox(at + " fill", card.box, frame, fill.box);
                assertRadii(at + " fill", card.corners, fill.radii);
                if (style == LayoutStyle.DOCKED && card.frame) {
                    // With side bars: the one joined frame, square, with the opening cut out.
                    assertBox(at + " frame", new Box(0f, 0f, frame.width(), frame.height()), frame,
                        fill.box);
                    assertTrue(at + " square outer corners", card.corners.isSquare());
                    assertNotNull(at + " the opening is cut out", fill.hole);
                    assertBox(at + " hole", card.hole, frame, fill.hole);
                } else if (style == LayoutStyle.DOCKED) {
                    // Without: an edge card, square at the screen, rounded inside, no opening.
                    assertTrue(at + " is an edge card", card.edge);
                    assertNull(at + " no opening in an edge card", fill.hole);
                } else {
                    assertNull(at + " no opening in a card", fill.hole);
                }
            }

            // The pane: the opening under Docked, which has no outline, or the card it is under
            // Floating.
            Pane pane = shape.panes().get(0);
            LayoutCanvasView.ShapeSpec paneFill = view.fillShape(LayoutCanvasView.Block.CANVAS);
            assertNotNull(name, paneFill);
            assertBox(name + " pane", pane.box, frame, paneFill.box);
            assertRadii(name + " pane", pane.corners, paneFill.radii);
            assertBox(name + " opening", shape.opening().box, frame,
                view.blockRect(LayoutCanvasView.Block.CANVAS));
            assertTrue(name + " the pane wears the rim under both Styles", pane.drawsRim);

            // The keyboard is the model's piece, whole.
            Piece keyboard = shape.piece(PieceId.KEYBOARD);
            assertNotNull(name, keyboard);
            assertBox(name + " keyboard", keyboard.box, frame, view.keyboardRect());
            assertEquals(name, 1, view.selectionShapes(LayoutCanvasView.Block.KEYBOARD).size());
        }
    }

    @Test
    public void floatingSpendsMarginAsAirAndDockedAsTheGutterOfTheRoundedInsert() {
        LayoutCanvasView floating = styled(LayoutStyle.FLOATING, bottomBars());
        float scale = floating.canvasScalePx();
        RectF status = floating.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        RectF pane = floating.blockRect(LayoutCanvasView.Block.CANVAS);
        assertEquals("Margin's air between the cards", MARGIN_DP * scale, pane.top - status.bottom,
            0.01f);
        assertEquals("and around them", MARGIN_DP * scale, status.left - floating.frameRect().left,
            0.01f);
        Pane card = floating.shape().panes().get(0);
        assertEquals("every card wears the Corners the user set, scaled to the canvas",
            Math.min(CORNERS_DP * scale, Math.min(card.box.width(), card.box.height()) / 2f),
            card.corners.topLeft, 0.01f);
        assertNotEquals("not the old fixed 6dp", 6f * scale, card.corners.topLeft, 0.01f);

        LayoutCanvasView docked = styled(LayoutStyle.DOCKED, bottomBars());
        RectF dockedStatus = docked.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        RectF dockedPane = docked.blockRect(LayoutCanvasView.Block.CANVAS);
        assertEquals("the insert stands a gutter of Margin below the bar", MARGIN_DP * scale,
            dockedPane.top - dockedStatus.bottom, 0.01f);
        assertEquals("and the same gutter from the screen's side", MARGIN_DP * scale,
            dockedPane.left - docked.frameRect().left, 0.01f);
        assertEquals("the bars themselves stay flush with the screen's edge",
            docked.frameRect().left, dockedStatus.left, 0.01f);
        Pane insert = docked.shape().panes().get(0);
        assertEquals("the insert wears Corners, not the screen's radius",
            Math.min(CORNERS_DP * scale, Math.min(insert.box.width(), insert.box.height()) / 2f),
            insert.corners.topLeft, 0.01f);
        // The outer corners are square (the screen rounds them); only an edge card's inner corners
        // round, at the Corners the user set, never at the screen's radius.
        Piece top = docked.shape().piece(PieceId.STATUS);
        assertEquals("the status bar's top corners are the screen's", 0f, top.corners.topLeft,
            0f);
        assertEquals(0f, top.corners.topRight, 0f);
        assertTrue("its bottom corners round at Corners", top.corners.bottomLeft > 0f);
        assertEquals(top.corners.bottomLeft, top.corners.bottomRight, 0f);
        assertTrue(top.corners.bottomLeft <= CORNERS_DP * scale + 0.01f);
        Piece keyboard = docked.shape().piece(PieceId.KEYBOARD);
        assertEquals("the keyboard's bottom corners are the screen's", 0f,
            keyboard.corners.bottomLeft, 0f);
        assertEquals(0f, keyboard.corners.bottomRight, 0f);
    }

    @Test
    public void theUsersCornersAndMarginMoveTheFloatingShapesLive() {
        LayoutCanvasView view = styled(LayoutStyle.FLOATING, bottomBars());
        RectF before = view.blockRect(LayoutCanvasView.Block.CANVAS);
        float scale = view.canvasScalePx();
        view.setShape(LayoutStyle.FLOATING, 8f, 30f);
        RectF after = view.blockRect(LayoutCanvasView.Block.CANVAS);
        assertTrue("more Margin takes more air", after.width() < before.width());
        assertEquals("Margin around the pane", 30f * scale, after.left - view.frameRect().left,
            0.01f);
        assertEquals("Corners follow the slider", 8f * scale,
            view.shape().piece(PieceId.STATUS).corners.topLeft, 0.01f);
        assertFalse("a slider drag is not a change of Style", view.isStyleMorphing());
    }

    // ---- A floating keyboard and a split one ------------------------------------------------------

    private static PlaceLayout keyboardIn(KeyboardForm form) {
        return new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM, KeyboardMode.RESIZE, form, 4, 5);
    }

    @Test
    public void aFloatingKeyboardIsACardOverThePaneWhichTakesNoBandOfTheBottom() {
        LayoutCanvasView docked = styled(LayoutStyle.FLOATING, keyboardIn(KeyboardForm.DOCKED));
        LayoutCanvasView floating =
            styled(LayoutStyle.FLOATING, keyboardIn(KeyboardForm.FLOATING));
        RectF dockedPane = docked.blockRect(LayoutCanvasView.Block.CANVAS);
        RectF floatingPane = floating.blockRect(LayoutCanvasView.Block.CANVAS);
        assertTrue("the pane keeps the room a docked keyboard would take",
            floatingPane.height() > dockedPane.height());
        RectF card = floating.keyboardRect();
        assertNotNull(card);
        assertTrue("it stands over the pane", floatingPane.contains(card));
        assertTrue("a smaller card than the pane is wide", card.width() < floatingPane.width());
        lift(docked, LayoutCanvasView.Block.APPS_ROW);
        assertNotNull("a docked keyboard has a gap under it", docked.underKeyboardSlotFor(0));
        lift(floating, LayoutCanvasView.Block.APPS_ROW);
        assertNull("no gap stands under a keyboard that is not a band",
            floating.underKeyboardSlotFor(0));
        Piece piece = floating.shape().piece(PieceId.KEYBOARD);
        assertTrue(piece.overlay);
        assertEquals("a floating keyboard wears the user's Corners",
            Math.min(CORNERS_DP * floating.canvasScalePx(),
                Math.min(piece.box.width(), piece.box.height()) / 2f), piece.corners.topLeft,
            0.01f);
    }

    @Test
    public void aSplitKeyboardIsTwoHalvesOfTheModelWithThePartingBetween() {
        LayoutCanvasView view = styled(LayoutStyle.FLOATING, keyboardIn(KeyboardForm.SPLIT));
        Piece left = view.shape().piece(PieceId.KEYBOARD_LEFT);
        Piece right = view.shape().piece(PieceId.KEYBOARD_RIGHT);
        assertNotNull(left);
        assertNotNull(right);
        assertEquals("both halves are outlined", 2,
            view.selectionShapes(LayoutCanvasView.Block.KEYBOARD).size());
        assertTrue("a gap between the halves", right.box.left - left.box.right > 0f);
        RectF block = view.keyboardRect();
        assertNotNull(block);
        assertEquals("the block is what the two halves cut", left.box.left + view.frameRect().left,
            block.left, 0.01f);
        assertEquals(right.box.right + view.frameRect().left, block.right, 0.01f);
    }

    // ---- The lifted copy and its placeholder -----------------------------------------------------

    @Test
    public void theLiftedCopyKeepsItsShapeUntilItHoversATargetThenTakesTheShapeItWouldHaveThere() {
        for (LayoutStyle style : LayoutStyle.values()) {
            LayoutCanvasView view = styled(style, bottomBars());
            Piece own = view.shape().piece(PieceId.APPS);
            RectF frame = view.frameRect();
            RectF band = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
            touch(view, MotionEvent.ACTION_DOWN, band.centerX(), band.centerY());
            touch(view, MotionEvent.ACTION_MOVE, band.centerX(), band.centerY() + 3f * slop(view));
            // Over the middle of the pane, which is no target: the bar is as it was lifted.
            RectF pane = view.blockRect(LayoutCanvasView.Block.CANVAS);
            touch(view, MotionEvent.ACTION_MOVE, pane.centerX(), pane.centerY());
            assertNull(style.toString(), view.hoveredSlot());
            view.settleMotion();
            LayoutCanvasView.ShapeSpec held = view.ghostShape();
            assertNotNull(held);
            assertEquals(style + " keeps its width", own.box.width(), held.box.width(), 0.01f);
            assertEquals(style + " keeps its height", own.box.height(), held.box.height(), 0.01f);
            assertRadii(style + " keeps its corners", own.corners, held.radii);
            assertEquals(0f, view.ghostMorph(), 0f);

            // Over the top edge it takes the shape the model gives a bar dropped there.
            MiniatureDragPolicy.Slot top = view.slotFor(Edge.TOP);
            assertNotNull(top);
            touch(view, MotionEvent.ACTION_MOVE, top.centerX(), top.centerY());
            MiniatureDragPolicy.Slot hovered = view.hoveredSlot();
            assertNotNull(style.toString(), hovered);
            assertEquals("the top edge it was moved onto", Edge.TOP, hovered.edge);
            view.settleMotion();
            Piece dropped = ChromeShapeModel.shapeIfDropped(view.shapeInput(), Element.APPS,
                hovered.edge, hovered.index, hovered.underKeyboard);
            LayoutCanvasView.ShapeSpec ghost = view.ghostShape();
            assertNotNull(ghost);
            assertEquals(1f, view.ghostMorph(), 0f);
            assertEquals(style + " takes the dropped width", dropped.box.width(),
                ghost.box.width(), 0.01f);
            assertEquals(style + " takes the dropped height", dropped.box.height(),
                ghost.box.height(), 0.01f);
            assertRadii(style + " takes the dropped corners", dropped.corners, ghost.radii);

            // The dashed placeholder is the same shape, where it would land.
            java.util.List<LayoutCanvasView.ShapeSpec> placeholder = view.placeholderShapes();
            assertEquals(1, placeholder.size());
            assertBox(style + " placeholder", dropped.box, frame, placeholder.get(0).box);
            assertRadii(style + " placeholder", dropped.corners, placeholder.get(0).radii);

            // Off the target again, the copy changes back to the shape it was lifted with.
            touch(view, MotionEvent.ACTION_MOVE, pane.centerX(), pane.centerY());
            view.settleMotion();
            assertEquals(0f, view.ghostMorph(), 0f);
            LayoutCanvasView.ShapeSpec back = view.ghostShape();
            assertEquals(own.box.width(), back.box.width(), 0.01f);
            assertBox(style + " placeholder where it stood", own.box, frame,
                view.placeholderShapes().get(0).box);
        }
    }

    // ---- Style flips ------------------------------------------------------------------------------

    @Test
    public void flippingStyleMorphsTheCornersGapsAndJoinsOverAQuarterOfASecond() {
        LayoutCanvasView view = styled(LayoutStyle.DOCKED, bottomBars());
        ChromeShape docked = view.shape();
        assertFalse("at rest", view.isStyleMorphing());

        view.setShape(LayoutStyle.FLOATING, CORNERS_DP, MARGIN_DP);
        ChromeShape floating = view.shape();
        assertTrue("a flip morphs", view.isStyleMorphing());
        assertEquals(250L, LayoutCanvasMorph.DURATION_MS);
        RectF frame = view.frameRect();

        view.holdMorphAt(0f);
        assertBox("it starts as Docked", docked.piece(PieceId.STATUS).box, frame,
            view.drawnPieceRect(PieceId.STATUS));
        view.holdMorphAt(0.5f);
        float eased = LayoutCanvasMorph.ease(0.5f);
        Box from = docked.piece(PieceId.APPS).box;
        Box to = floating.piece(PieceId.APPS).box;
        assertBox("half way, a join has half closed", LayoutCanvasMorph.lerp(from, to, eased),
            frame, view.drawnPieceRect(PieceId.APPS));
        RectF mid = view.drawnPieceRect(PieceId.STATUS);
        assertTrue("the gap around the cards is opening", mid.left > frame.left);
        view.holdMorphAt(1f);
        assertBox("and ends as Floating", floating.piece(PieceId.STATUS).box, frame,
            view.drawnPieceRect(PieceId.STATUS));
        assertFalse(view.isStyleMorphing());
    }

    @Test
    public void withReducedMotionAFlipOfStyleJumps() {
        Context app = RuntimeEnvironment.getApplication();
        float was = Settings.Global.getFloat(app.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        Settings.Global.putFloat(app.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        try {
            LayoutCanvasView view = styled(LayoutStyle.DOCKED, bottomBars());
            view.setShape(LayoutStyle.FLOATING, CORNERS_DP, MARGIN_DP);
            assertFalse("no morph", view.isStyleMorphing());
            assertBox("it is Floating at once", view.shape().piece(PieceId.STATUS).box,
                view.frameRect(), view.drawnPieceRect(PieceId.STATUS));
        } finally {
            Settings.Global.putFloat(app.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, was);
        }
    }

    @Test
    public void theMorphBlendsPieceByPieceFromOneShapeToTheOther() {
        LayoutCanvasView docked = styled(LayoutStyle.DOCKED, bottomBars());
        LayoutCanvasView floating = styled(LayoutStyle.FLOATING, bottomBars());
        ChromeShape from = docked.shape();
        ChromeShape to = floating.shape();
        LayoutCanvasMorph.Blend start = LayoutCanvasMorph.blend(from, to, 0f);
        LayoutCanvasMorph.Blend end = LayoutCanvasMorph.blend(from, to, 1f);
        LayoutCanvasMorph.Blend half = LayoutCanvasMorph.blend(from, to, 0.5f);
        for (Piece piece : to.pieces()) {
            assertEquals(from.piece(piece.id).box, start.piece(piece.id).box);
            assertEquals(from.piece(piece.id).corners, start.piece(piece.id).corners);
            assertEquals(piece.box, end.piece(piece.id).box);
            assertEquals(piece.corners, end.piece(piece.id).corners);
            Corners a = from.piece(piece.id).corners;
            Corners b = piece.corners;
            assertEquals((a.topLeft + b.topLeft) / 2f, half.piece(piece.id).corners.topLeft,
                0.001f);
        }
        assertEquals(from.panes().get(0).box, start.pane.box);
        assertEquals(to.panes().get(0).box, end.pane.box);
        assertEquals("ease starts at nothing and ends at all", 0f, LayoutCanvasMorph.ease(0f), 0f);
        assertEquals(1f, LayoutCanvasMorph.ease(1f), 0f);
        assertTrue("and eases out", LayoutCanvasMorph.ease(0.5f) > 0.5f);
    }

    // ---- The pack's artwork ------------------------------------------------------------------------

    @Test
    public void theSymbolsFollowTheRealCountAndStopWhereTheyStopFitting() {
        assertEquals("the pack's own seven when the count is unknown", 7,
            LayoutCanvasArtwork.slotsFor(-1, 336f, 20f));
        assertEquals("the real count", 4, LayoutCanvasArtwork.slotsFor(4, 336f, 20f));
        assertEquals("as many as fit", 16, LayoutCanvasArtwork.slotsFor(40, 336f, 20f));
        assertEquals("none when there are none", 0, LayoutCanvasArtwork.slotsFor(0, 336f, 20f));
        assertEquals("one at the least in a short bar", 1,
            LayoutCanvasArtwork.slotsFor(5, 30f, 20f));
    }

    @Test
    public void everyStateOfTheCanvasDraws() {
        Bitmap bitmap = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (LayoutStyle style : LayoutStyle.values()) {
            for (PlaceLayout arrangement : new PlaceLayout[] {
                bottomBars(), sideBars(), keyboardIn(KeyboardForm.FLOATING),
                keyboardIn(KeyboardForm.SPLIT)}) {
                LayoutCanvasView view = styled(style, arrangement);
                view.setExtraKeyCount(4);
                view.draw(canvas);
                view.setSelectedBlock(LayoutCanvasView.Block.APPS_ROW);
                view.draw(canvas);
                view.setSelectedBlock(LayoutCanvasView.Block.KEYBOARD);
                view.draw(canvas);
                view.setSelectedBlock(LayoutCanvasView.Block.CANVAS);
                view.draw(canvas);
            }
        }
        // Part-way through a flip, and with a bar in the air over a target.
        LayoutCanvasView view = styled(LayoutStyle.DOCKED, bottomBars());
        view.setShape(LayoutStyle.FLOATING, CORNERS_DP, MARGIN_DP);
        view.holdMorphAt(0.4f);
        view.draw(canvas);
        view.holdMorphAt(1f);
        for (PaneWallPage place : PaneWallPage.values()) {
            view.setLayout(bottomBars(), PlaceOrientation.PORTRAIT, place);
            view.draw(canvas);
        }
        view.setLayout(bottomBars(), PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        lift(view, LayoutCanvasView.Block.EXTRA_KEYS);
        MiniatureDragPolicy.Slot top = view.slotFor(Edge.TOP);
        assertNotNull(top);
        touch(view, MotionEvent.ACTION_MOVE, top.centerX(), top.centerY());
        view.settleMotion();
        view.draw(canvas);
        lift(styled(LayoutStyle.DOCKED, bottomBars()), LayoutCanvasView.Block.KEYBOARD);
    }

    @Test
    public void theGlyphsAreThePacksElevenPathsInA24Box() {
        assertEquals(11, LayoutCanvasGlyphs.IDS.length);
        assertEquals("M5 7l5 5-5 5M13 17h6", LayoutCanvasGlyphs.pathData("terminal"));
        assertEquals("M9 6l10 6-10 6Z", LayoutCanvasGlyphs.pathData("play"));
        assertEquals(24f, LayoutCanvasGlyphs.VIEWBOX, 0f);
        assertEquals(1.8f, LayoutCanvasGlyphs.STROKE, 0f);
        for (String id : LayoutCanvasGlyphs.IDS) {
            assertNotNull(id, LayoutCanvasGlyphs.pathData(id));
            assertNotNull(id, LayoutCanvasGlyphs.path(id));
        }
        assertNull(LayoutCanvasGlyphs.pathData("capsule"));
    }

    // ---- The canvas is to scale ------------------------------------------------------------------

    private static float screenHeightDp(LayoutCanvasView view, boolean landscape) {
        android.util.DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        float shortDp = Math.min(metrics.widthPixels, metrics.heightPixels) / metrics.density;
        return landscape ? shortDp : shortDp * 19.5f / 9f;
    }

    @Test
    public void theKeyboardBlockIsItsRealHeightPlusItsChinTimesTheCanvasScale() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setSizes(TERMUX_APP_DEFAULT_DOCK, 1.2f, 0);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        float scale = view.canvasScalePx();
        assertTrue(scale > 0f);
        RectF keyboard = view.keyboardRect();
        assertNotNull(keyboard);
        assertEquals(LayoutCanvasGeometry.keyboardHeightDp(1.2f, false,
                screenHeightDp(view, false)) * scale, keyboard.height(), 0.6f);

        view.setSizes(TERMUX_APP_DEFAULT_DOCK, 1.2f, 20);
        RectF withChin = view.keyboardRect();
        assertNotNull(withChin);
        assertEquals("the chin is 20 real dp more block", 20f * scale,
            withChin.height() - keyboard.height(), 0.6f);
    }

    @Test
    public void theStatusBarIsItsRealThicknessCollapsedOrExpanded() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, false, RowPlacement.HIDDEN),
            PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        float scale = view.canvasScalePx();
        view.setStatusCompact(false);
        RectF expanded = view.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        view.setStatusCompact(true);
        RectF compact = view.blockRect(LayoutCanvasView.Block.STATUS_BAR);
        assertNotNull(expanded);
        assertNotNull(compact);
        assertTrue("collapsed is thinner than expanded", compact.height() < expanded.height());
        assertEquals(LayoutCanvasGeometry.statusBandDp(Edge.TOP, true, false) * scale,
            compact.height(), 0.6f);
        assertTrue(view.isStatusCompact());
    }

    @Test
    public void theDocksBandFollowsItsScale() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.HIDDEN),
            PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        float scale = view.canvasScalePx();
        RectF shipped = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(shipped);
        assertEquals(LayoutCanvasGeometry.dockBandHeightDp(TERMUX_APP_DEFAULT_DOCK, false) * scale,
            shipped.height(), 0.6f);

        // Floating stands the dock in its card, which is a little thicker than the docked one.
        view.setShape(LayoutStyle.FLOATING, CORNERS_DP, 0f);
        RectF card = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(card);
        assertEquals(LayoutCanvasGeometry.dockBandHeightDp(TERMUX_APP_DEFAULT_DOCK, true) * scale,
            card.height(), 0.6f);
        view.setShape(LayoutStyle.DOCKED, CORNERS_DP, 0f);

        view.setSizes(DockLayoutPolicy.maxSizePreset(), 1f, 0);
        RectF tall = view.blockRect(LayoutCanvasView.Block.APPS_ROW);
        assertNotNull(tall);
        assertTrue("a bigger dock scale is a taller band", tall.height() > shipped.height());
    }

    @Test
    public void theChinHandleKeepsItsFullTargetWhileTheChinIsNothing() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setSizes(TERMUX_APP_DEFAULT_DOCK, 1f, 0);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        RectF keyboard = view.keyboardRect();
        assertNotNull(keyboard);
        touch(view, MotionEvent.ACTION_DOWN, keyboard.centerX(), keyboard.centerY());
        touch(view, MotionEvent.ACTION_UP, keyboard.centerX(), keyboard.centerY());
        RectF chin = view.handleRect(LayoutCanvasView.Handle.KEYBOARD_CHIN);
        assertNotNull(chin);
        float density = view.getResources().getDisplayMetrics().density;
        // A finger 20dp off the thin pill, inside the 48dp target, still takes hold of it.
        touch(view, MotionEvent.ACTION_DOWN, chin.centerX(), chin.centerY() - 20f * density);
        assertEquals(LayoutCanvasView.Handle.KEYBOARD_CHIN, view.draggedHandle());
        touch(view, MotionEvent.ACTION_UP, chin.centerX(), chin.centerY() - 20f * density);
    }

    private static final float TERMUX_APP_DEFAULT_DOCK =
        com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP
            .DEFAULT_APP_LAUNCHER_BAR_HEIGHT;

    // ---- The trash and the hidden-elements popup -----------------------------------------------

    @Test
    public void theTrashIsEmptyUntilSomethingIsHiddenThenFilled() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertEquals(MiniatureDragPolicy.TrashState.EMPTY, view.trashState());
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.HIDDEN),
            PlaceOrientation.PORTRAIT);
        assertEquals(MiniatureDragPolicy.TrashState.FILLED, view.trashState());
        assertFalse(view.trashRect().isEmpty());
        assertTrue("it sits on the tray's end", view.trashRect().right
            <= view.trayRect().right + 0.01f);
        assertTrue(view.hiddenDescription().contains("2"));
    }

    @Test
    public void hiddenElementsAreListedInOrderForTheEditorsPopup() {
        LayoutCanvasView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertTrue(view.hiddenElements().isEmpty());
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertEquals(java.util.Collections.singletonList(LayoutCanvasView.Block.APPS_ROW),
            view.hiddenElements());
        assertFalse(view.chipName(LayoutCanvasView.Block.APPS_ROW).isEmpty());
    }

    @Test
    public void aChipDraggedOutOfThePopupIsLiftedAndDroppedOnAnEdgeRestoresIt() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));
        assertTrue(view.beginHiddenDrag(LayoutCanvasView.Block.APPS_ROW));
        assertEquals(LayoutCanvasView.Block.APPS_ROW, view.draggedBar());
        for (MiniatureDragPolicy.Slot slot : view.slots())
            assertFalse("a hidden element is not dropped in the trash again", slot.isTray());

        MiniatureDragPolicy.Slot bottom = view.slotFor(Edge.BOTTOM);
        assertNotNull(bottom);
        float dx = (bottom.left + bottom.right) / 2f;
        float dy = (bottom.top + bottom.bottom) / 2f;
        view.hoverHiddenDrag(dx, dy);
        assertTrue(view.dropHiddenDrag(dx, dy));
        assertEquals("bottom", prefs().getString("layout.landscape.apps_row", null));
        assertNull(view.draggedBar());
    }

    @Test
    public void aChipDroppedOverNoTargetLeavesTheElementHidden() {
        PlaceLayoutStore places = store();
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));
        assertTrue(view.beginHiddenDrag(LayoutCanvasView.Block.APPS_ROW));
        assertFalse(view.dropHiddenDrag(-50f, -50f));
        assertNull(view.draggedBar());
        view.endHiddenDrag();
    }

    @Test
    public void theKeyboardChipDroppedOnThePhoneSwitchesItBackOn() {
        LayoutCanvasView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setFillsView(true);
        view.setExternalTrayRect(new RectF(900f, 420f, 948f, 468f));
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM)
            .withKeyboardShown(false), PlaceOrientation.LANDSCAPE);
        final boolean[] restored = new boolean[1];
        view.setOnCanvasEditListener(new LayoutCanvasView.OnCanvasEditListener() {
            @Override public void onKeyboardRestored() {
                restored[0] = true;
            }
        });
        assertTrue(view.beginHiddenDrag(LayoutCanvasView.Block.KEYBOARD));
        assertEquals(LayoutCanvasView.Block.KEYBOARD, view.draggedBar());
        RectF frame = view.frameRect();
        view.hoverHiddenDrag(frame.centerX(), frame.centerY());
        assertTrue(view.dropHiddenDrag(frame.centerX(), frame.centerY()));
        assertTrue(restored[0]);
    }
}
