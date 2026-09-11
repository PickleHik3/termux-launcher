package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The Layout page's miniature follows the rows: a new arrangement at the same size redraws. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PlaceMiniatureViewTest {

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

    private static PlaceMiniatureView sized() {
        return sized(1000, 400);
    }

    private static PlaceMiniatureView sized(int width, int height) {
        PlaceMiniatureView view = new PlaceMiniatureView(RuntimeEnvironment.getApplication());
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
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
        // The A–Z band no longer depends on the apps row: it is still on screen.
        assertNotNull(view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW));
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

    @Test
    public void thePlaceChangesWhatTheCanvasDraws() {
        PlaceMiniatureView view = sized();
        PlaceLayout arrangement = layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM);

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.TERMINAL);
        assertEquals(PlaceMiniatureView.CanvasKind.TERMINAL, view.canvasKind());

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.WIDGETS);
        assertEquals(PlaceMiniatureView.CanvasKind.HOME_GRID, view.canvasKind());

        view.setLayout(arrangement, PlaceOrientation.PORTRAIT, PaneWallPage.DISPLAY);
        assertEquals(PlaceMiniatureView.CanvasKind.DISPLAY, view.canvasKind());
    }

    @Test
    public void theWidgetGridCollapsesOnlyWhenACellWouldBeTooSmall() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(RowPlacement.BOTTOM, 4, 5), PlaceOrientation.PORTRAIT,
            PaneWallPage.WIDGETS);
        assertTrue("a modest grid draws real cells", !view.isWidgetGridCollapsed());

        view.setLayout(layout(RowPlacement.BOTTOM, 80, 80), PlaceOrientation.PORTRAIT,
            PaneWallPage.WIDGETS);
        assertTrue("an extreme grid collapses to one tinted rect", view.isWidgetGridCollapsed());
    }

    @Test
    public void theAlphabetsRowSitsBetweenThePinnedAppsAndTheExtraKeys() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF apps = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        RectF az = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        RectF keys = view.blockRect(PlaceMiniatureView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(az);
        assertNotNull(keys);
        // The real dock, top to bottom: pinned apps, the A–Z index, then the extra keys.
        assertTrue("A–Z below the pinned apps", az.top >= apps.bottom - 0.5f);
        assertTrue("A–Z above the extra keys", az.bottom <= keys.top + 0.5f);
    }

    @Test
    public void everyBlockHasALegendRowAndHiddenOnesAreMarked() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        for (PlaceMiniatureView.Block block : PlaceMiniatureView.Block.values()) {
            assertNotNull("legend row for " + block, view.legendRect(block));
        }
        assertTrue("hidden apps row is marked", view.isBlockHidden(PlaceMiniatureView.Block.APPS_ROW));
        assertTrue("A–Z shows regardless of the apps row",
            !view.isBlockHidden(PlaceMiniatureView.Block.ALPHABETS_ROW));
        assertTrue("extra keys are on screen", !view.isBlockHidden(PlaceMiniatureView.Block.EXTRA_KEYS));
        assertNull("a hidden block is off the picture",
            view.blockRect(PlaceMiniatureView.Block.APPS_ROW));

        // The legend stands beside the phone, never over it.
        RectF frame = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        RectF legend = view.legendRect(PlaceMiniatureView.Block.STATUS_BAR);
        assertNotNull(frame);
        assertTrue("legend clear of the phone", legend.left >= frame.right);
    }

    @Test
    public void withoutALegendThePhoneTakesTheWholeView() {
        // Narrow enough that the width, not the height, is what the phone has to fit inside.
        PlaceMiniatureView view = sized(200, 400);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        RectF withLegend = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        assertNotNull(withLegend);
        assertNotNull(view.legendRect(PlaceMiniatureView.Block.STATUS_BAR));

        view.setLegendVisible(false);
        RectF alone = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        assertNotNull(alone);
        assertNull("no legend row is laid out",
            view.legendRect(PlaceMiniatureView.Block.STATUS_BAR));
        assertTrue("the phone grew into the legend's width", alone.width() > withLegend.width());
    }

    @Test
    public void theAlphabetsRowIsIndependentOfTheAppsRow() {
        PlaceMiniatureView view = sized();

        // Apps row hidden entirely: the A–Z band still shows.
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, true, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNotNull("A–Z shows with the apps row hidden",
            view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW));

        // Apps row on a side rail: the A–Z band still shows, along the bottom.
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, true, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNotNull("A–Z shows with the apps row on a rail",
            view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW));

        // The switch itself, not the apps row, controls the band.
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, false, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNull("the switch being off hides the band",
            view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW));
    }

    @Test
    public void aSideRailStandsOutsideAnExtraKeysColumnOnTheSameSide() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, RowPlacement.LEFT),
            PlaceOrientation.LANDSCAPE);
        RectF apps = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        RectF keys = view.blockRect(PlaceMiniatureView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(keys);
        // The real device: the pinned-apps rail sits at the screen edge, the extra-keys column is
        // padded inward by the rail's width.
        assertTrue("apps rail is at the left screen edge", apps.left <= keys.left - 0.5f);
        assertTrue("extra keys column stands to the right of the rail", keys.left >= apps.right - 0.5f);
    }

    @Test
    public void theAzBarEdgeOnlyAppliesWhileTheBarStandsAlone() {
        PlaceMiniatureView view = sized();
        // Riding under a bottom apps row: a stored side edge is ignored, the band stays along the
        // bottom, between the pinned apps and the extra keys, same as the default arrangement.
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.LEFT, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        RectF apps = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        RectF ridingRow = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        RectF keys = view.blockRect(PlaceMiniatureView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(ridingRow);
        assertNotNull(keys);
        assertTrue("the ignored side edge never turns the band into a column",
            ridingRow.width() > ridingRow.height());
        assertTrue("still below the pinned apps", ridingRow.top >= apps.bottom - 0.5f);
        assertTrue("still above the extra keys", ridingRow.bottom <= keys.top + 0.5f);
    }

    @Test
    public void aTopAzBarStandsRightUnderTheStatusBar() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, true, Edge.TOP, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        RectF status = view.blockRect(PlaceMiniatureView.Block.STATUS_BAR);
        RectF az = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        assertNotNull(status);
        assertNotNull(az);
        assertTrue("the band sits right under the status bar", az.top >= status.bottom - 0.5f);
        assertTrue("a horizontal band is wider than it is tall", az.width() > az.height());
    }

    @Test
    public void aSideAzBarIsInnermostOfTheSideColumns() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.LEFT, true, Edge.LEFT, RowPlacement.LEFT),
            PlaceOrientation.LANDSCAPE);
        RectF apps = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        RectF keys = view.blockRect(PlaceMiniatureView.Block.EXTRA_KEYS);
        RectF az = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        assertNotNull(apps);
        assertNotNull(keys);
        assertNotNull(az);
        assertTrue("the rail is outermost", apps.left <= keys.left - 0.5f);
        assertTrue("the extra-keys column is next", keys.left <= az.left - 0.5f);
        assertTrue("a vertical band is taller than it is wide", az.height() > az.width());

        // The right edge mirrors the same order from the other side.
        view.setLayout(layout(Edge.TOP, RowPlacement.RIGHT, true, Edge.RIGHT, RowPlacement.RIGHT),
            PlaceOrientation.LANDSCAPE);
        RectF appsRight = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        RectF keysRight = view.blockRect(PlaceMiniatureView.Block.EXTRA_KEYS);
        RectF azRight = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        assertNotNull(appsRight);
        assertNotNull(keysRight);
        assertNotNull(azRight);
        assertTrue("the rail is outermost on the right too", appsRight.right >= keysRight.right + 0.5f);
        assertTrue("the extra-keys column is next", keysRight.right >= azRight.right + 0.5f);
    }

    @Test
    public void bottomStackingOrderIsUnchanged() {
        PlaceMiniatureView view = sized();
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        RectF apps = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        RectF az = view.blockRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        RectF keys = view.blockRect(PlaceMiniatureView.Block.EXTRA_KEYS);
        assertNotNull(apps);
        assertNotNull(az);
        assertNotNull(keys);
        // Bottom stack, outside in from the screen edge: extra keys, then A–Z, then pinned apps.
        assertTrue("extra keys are outermost", keys.bottom >= az.bottom - 0.5f);
        assertTrue("A–Z sits above the extra keys", az.bottom <= keys.top + 0.5f);
        assertTrue("pinned apps are innermost", apps.bottom <= az.top + 0.5f);
    }

    // ---- Grips, slots and the drag -------------------------------------------------------------

    private static PlaceMiniatureView inParent(ScrollingParent parent, int width, int height) {
        PlaceMiniatureView view = new PlaceMiniatureView(parent.getContext());
        parent.addView(view, new FrameLayout.LayoutParams(width, height));
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, width, height);
        return view;
    }

    private static ScrollingParent parent() {
        return new ScrollingParent(RuntimeEnvironment.getApplication());
    }

    private static void touch(PlaceMiniatureView view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    /** Writes a drop the way the Layout page's overview does, for one miniature's orientation. */
    private static PlaceMiniatureView.OnBarDroppedListener writer(
        PlaceLayoutStore places, PlaceOrientation orientation) {
        return (bar, edge) -> {
            MiniatureDragPolicy.Bar dragged = PlaceMiniatureView.barOf(bar);
            assertNotNull(dragged);
            LayoutChooserModel.applyDrop(places, PaneWallPage.TERMINAL, orientation, dragged, edge);
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
    public void everyBarWithAPlacementCarriesAGripAndNothingElseDoes() {
        PlaceMiniatureView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        for (PlaceMiniatureView.Block bar : new PlaceMiniatureView.Block[]{
            PlaceMiniatureView.Block.STATUS_BAR, PlaceMiniatureView.Block.APPS_ROW,
            PlaceMiniatureView.Block.ALPHABETS_ROW, PlaceMiniatureView.Block.EXTRA_KEYS}) {
            RectF grip = view.gripRect(bar);
            assertNotNull("grip for " + bar, grip);
            RectF band = view.blockRect(bar);
            assertNotNull(band);
            assertTrue("the grip rides inside its own band",
                band.contains(grip.centerX(), grip.centerY()));
        }
        assertNull("the terminal has no placement to drag",
            view.gripRect(PlaceMiniatureView.Block.CANVAS));
    }

    @Test
    public void aHiddenBarIsAChipInTheTrayWithAGripOfItsOwn() {
        PlaceMiniatureView view = sized();
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        assertNull("nothing is drawn on the phone for it",
            view.blockRect(PlaceMiniatureView.Block.APPS_ROW));
        RectF chip = view.trayChipRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull("the hidden bar is listed in the tray", chip);
        assertTrue("the tray stands under the phone", chip.top >= view.trayRect().top - 0.5f);
        assertNotNull("and it can be lifted back out",
            view.gripRect(PlaceMiniatureView.Block.APPS_ROW));
        assertNull("a bar on the phone has no chip",
            view.trayChipRect(PlaceMiniatureView.Block.EXTRA_KEYS));
    }

    @Test
    public void aTouchOnAGripLiftsTheBarAtOnceAndStopsTheListScrolling() {
        ScrollingParent parent = parent();
        PlaceMiniatureView view = inParent(parent, 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);

        RectF grip = view.gripRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull(grip);
        touch(view, MotionEvent.ACTION_DOWN, grip.centerX(), grip.centerY());
        assertEquals("no long press: the bar is up on touch-down",
            PlaceMiniatureView.Block.APPS_ROW, view.draggedBar());
        assertTrue("the preference list is told to keep out", parent.disallowedIntercept);
        assertNotNull("landscape offers a column down the left", view.slotFor(Edge.LEFT));
        assertNotNull(view.slotFor(Edge.RIGHT));
        assertNull("a row has no top position", view.slotFor(Edge.TOP));
    }

    @Test
    public void aTouchOffTheGripsLiftsNothingAndLeavesTheListAlone() {
        ScrollingParent parent = parent();
        PlaceMiniatureView view = inParent(parent, 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);

        RectF band = view.blockRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull(band);
        touch(view, MotionEvent.ACTION_DOWN, band.left + 2f, band.centerY());
        assertNull("a tap on the band is still a tap", view.draggedBar());
        assertFalse("so the list keeps its own scroll", parent.disallowedIntercept);
        assertTrue("and nothing is outlined", view.slots().isEmpty());
    }

    @Test
    public void aDragOntoASideSlotWritesThatEdgeForTheOrientationItWasDoneIn() {
        PlaceLayoutStore places = store();
        PlaceMiniatureView landscape = inParent(parent(), 1000, 400);
        landscape.setLegendVisible(false);
        landscape.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        landscape.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        RectF grip = landscape.gripRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull(grip);
        touch(landscape, MotionEvent.ACTION_DOWN, grip.centerX(), grip.centerY());
        MiniatureDragPolicy.Slot slot = landscape.slotFor(Edge.LEFT);
        assertNotNull(slot);
        touch(landscape, MotionEvent.ACTION_MOVE, slot.centerX(), slot.centerY());
        touch(landscape, MotionEvent.ACTION_UP, slot.centerX(), slot.centerY());

        assertNull("the gesture is over", landscape.draggedBar());
        assertEquals("left", prefs().getString("place.terminal.landscape.apps_row", null));
        assertNull("portrait was not touched",
            prefs().getString("place.terminal.portrait.apps_row", null));

        // The same drag on the portrait miniature writes portrait's own key.
        PlaceMiniatureView portrait = inParent(parent(), 1000, 400);
        portrait.setLegendVisible(false);
        portrait.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        portrait.setOnBarDroppedListener(writer(places, PlaceOrientation.PORTRAIT));
        RectF portraitGrip = portrait.gripRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull(portraitGrip);
        touch(portrait, MotionEvent.ACTION_DOWN, portraitGrip.centerX(), portraitGrip.centerY());
        assertNull("portrait has no room for a column", portrait.slotFor(Edge.LEFT));
        RectF tray = portrait.trayRect();
        touch(portrait, MotionEvent.ACTION_MOVE, tray.centerX(), tray.centerY());
        touch(portrait, MotionEvent.ACTION_UP, tray.centerX(), tray.centerY());
        assertEquals("hidden", prefs().getString("place.terminal.portrait.apps_row", null));
        assertEquals("landscape kept the column it was given", "left",
            prefs().getString("place.terminal.landscape.apps_row", null));
    }

    @Test
    public void aReleaseOffEverySlotWritesNothing() {
        PlaceLayoutStore places = store();
        PlaceMiniatureView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        RectF grip = view.gripRect(PlaceMiniatureView.Block.EXTRA_KEYS);
        assertNotNull(grip);
        touch(view, MotionEvent.ACTION_DOWN, grip.centerX(), grip.centerY());
        // The middle of the canvas is inside no slot at all.
        RectF canvas = view.blockRect(PlaceMiniatureView.Block.CANVAS);
        assertNotNull(canvas);
        touch(view, MotionEvent.ACTION_MOVE, canvas.centerX(), canvas.centerY());
        touch(view, MotionEvent.ACTION_UP, canvas.centerX(), canvas.centerY());

        assertNull("nothing was written for the extra keys",
            prefs().getString("place.terminal.landscape.extra_keys", null));
        assertNull("nor for anything else",
            prefs().getString("place.terminal.landscape.apps_row", null));
    }

    @Test
    public void theStatusBarIsNeverOfferedTheTray() {
        PlaceMiniatureView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);

        RectF grip = view.gripRect(PlaceMiniatureView.Block.STATUS_BAR);
        assertNotNull(grip);
        touch(view, MotionEvent.ACTION_DOWN, grip.centerX(), grip.centerY());
        for (MiniatureDragPolicy.Slot slot : view.slots()) {
            assertFalse("the status bar has nowhere to hide", slot.isTray());
        }
        assertNotNull("but it may stand on any edge", view.slotFor(Edge.TOP));
        assertNotNull(view.slotFor(Edge.LEFT));
    }

    @Test
    public void aChipInTheTrayIsDraggedBackOntoAnEdge() {
        PlaceLayoutStore places = store();
        places.setAppsRow(PaneWallPage.TERMINAL, PlaceOrientation.PORTRAIT, RowPlacement.HIDDEN);
        PlaceMiniatureView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.HIDDEN, RowPlacement.BOTTOM),
            PlaceOrientation.PORTRAIT);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.PORTRAIT));

        RectF grip = view.gripRect(PlaceMiniatureView.Block.APPS_ROW);
        assertNotNull("the chip carries the same grip", grip);
        touch(view, MotionEvent.ACTION_DOWN, grip.centerX(), grip.centerY());
        MiniatureDragPolicy.Slot bottom = view.slotFor(Edge.BOTTOM);
        assertNotNull(bottom);
        touch(view, MotionEvent.ACTION_MOVE, bottom.centerX(), bottom.centerY());
        touch(view, MotionEvent.ACTION_UP, bottom.centerX(), bottom.centerY());

        assertEquals("bottom", prefs().getString("place.terminal.portrait.apps_row", null));
    }

    @Test
    public void theAzIndexRidingThePinnedAppsCanOnlyBeDraggedIntoTheTray() {
        PlaceLayoutStore places = store();
        PlaceMiniatureView view = inParent(parent(), 1000, 400);
        view.setLegendVisible(false);
        view.setLayout(layout(Edge.TOP, RowPlacement.BOTTOM, RowPlacement.BOTTOM),
            PlaceOrientation.LANDSCAPE);
        view.setOnBarDroppedListener(writer(places, PlaceOrientation.LANDSCAPE));

        RectF grip = view.gripRect(PlaceMiniatureView.Block.ALPHABETS_ROW);
        assertNotNull(grip);
        touch(view, MotionEvent.ACTION_DOWN, grip.centerX(), grip.centerY());
        assertEquals("the tray is the only target", 1, view.slots().size());
        assertTrue(view.slots().get(0).isTray());
        RectF tray = view.trayRect();
        touch(view, MotionEvent.ACTION_MOVE, tray.centerX(), tray.centerY());
        touch(view, MotionEvent.ACTION_UP, tray.centerX(), tray.centerY());
        assertFalse(prefs().getBoolean("place.terminal.landscape.az_row", true));
    }

    @Test
    public void thePictureNamesEveryBarAndWhereItStands() {
        PlaceMiniatureView view = sized();
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
}
