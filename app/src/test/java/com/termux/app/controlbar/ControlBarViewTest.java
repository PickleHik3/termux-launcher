package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.tiling.WindowKind;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The bar's view reports a tap on a mark, a slide along the marks and a tool's tap (#424). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class ControlBarViewTest {

    private final List<String> heard = new ArrayList<>();
    private ControlBarView view;
    private Activity activity;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        view = new ControlBarView(activity, new ControlBarView.Listener() {
            @Override public void onMark(int index) { heard.add("mark " + index); }
            @Override public void onTool(@NonNull ControlBarTool tool) { heard.add("tool " + tool); }
            @Override public void onPinnedApp(int index) { heard.add("app " + index); }
            @Override public void onPinnedAppHeld(int index, @NonNull android.graphics.Rect cell) {
                heard.add("held " + index + (cell.width() > 0 && cell.width() < 120 ? " cell" : ""));
            }
            @Override public void onReading() { heard.add("reading"); }
            @Override public void onReadingHeld(@NonNull android.graphics.Rect cell) {
                heard.add("reading held" + (cell.isEmpty() ? " empty" : ""));
            }
            @Override public void onPlaceHeld() { heard.add("place held"); }
            @Override public void onBarHeld() { heard.add("bar held"); }
        });
        view.setRows(ControlBarRows.of(ControlBarFront.workspace(1, WindowKind.SHELL, Arrays.asList(
            new ControlBarFront.Window(1, WindowKind.SHELL), new ControlBarFront.Window(2, WindowKind.SHELL),
            new ControlBarFront.Window(3, WindowKind.SHELL)), 1)));
        view.measure(View.MeasureSpec.makeMeasureSpec(388, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 388, 96);
    }

    /** Mark {@code index}'s centre at mdpi: marks are 32dp cells 2dp apart after the badge. */
    private float markX(int index) {
        return firstMarkLeft() + index * 34f + 16f;
    }

    /** The badge (side 6, then 8 + number + 6 + icon 17 + 10) and 6dp of air. */
    private float firstMarkLeft() {
        android.graphics.Paint text = new android.graphics.Paint();
        text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD));
        text.setTextSize(15f);
        return 6f + 8f + 17f + 10f + 6f + text.measureText("1") + 6f;
    }

    @Test public void aTapOnAMarkGoesToIt() {
        tap(markX(2), 26f);
        assertEquals(Arrays.asList("mark 2"), heard);
    }

    @Test public void aSlideAlongTheMarksGoesToEachOne() {
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, markX(0), 26f, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, markX(1), 26f, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, markX(2), 26f, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, markX(2), 26f, 0));
        assertEquals(Arrays.asList("mark 1", "mark 2"), heard);
    }

    @Test public void theBottomRowsToolsAreTheTerminalsThenNavigationAndSearch() {
        // Tools are 40dp cells from 6dp, 6dp apart; search is the last cell at the right.
        tap(6f + 20f, 70f);
        tap(6f + 46f + 20f, 70f);
        tap(388f - 6f - 20f, 70f);
        tap(388f - 6f - 46f - 20f, 70f);
        assertEquals(Arrays.asList("tool KEYBOARD", "tool SPLIT", "tool SEARCH", "tool NAVIGATION"), heard);
    }

    @Test public void theBarKeepsEveryTouchThatLandsOnIt() {
        long t = SystemClock.uptimeMillis();
        assertTrue(view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 380f, 5f, 0)));
    }

    @Test public void theMarksToolsAndPinnedAppsAreNodesTalkBackCanTap() {
        assertEquals("Terminal, window 2 of 3", view.labelOf(1).toString());
        assertEquals(1, view.virtualViewAt(markX(1), 26f));
        assertEquals("Keyboard", view.labelOf(ControlBarView.TOOL_BASE).toString());
        assertEquals("Search", view.labelOf(ControlBarView.TOOL_BASE + 5).toString());
        assertEquals(ControlBarView.TOOL_BASE + 5, view.virtualViewAt(388f - 6f - 20f, 70f));
        assertTrue(view.clickVirtualView(2));
        assertTrue(view.clickVirtualView(ControlBarView.TOOL_BASE + 4));
        assertEquals(Arrays.asList("mark 2", "tool NAVIGATION"), heard);
        assertEquals(null, view.labelOf(ControlBarView.TOOL_BASE + 6));
    }

    @Test public void homesDotsAndPinnedAppsAreSpokenByName() {
        view.setPinnedApps(Arrays.asList(new ControlBarView.PinnedApp(null, "Camera"),
            new ControlBarView.PinnedApp(null, "Phone")));
        view.setRows(ControlBarRows.of(ControlBarFront.home(3, 0)));
        assertEquals("Home page 2 of 3", view.labelOf(1).toString());
        assertEquals("Phone", view.labelOf(ControlBarView.APP_BASE + 1).toString());
        assertEquals(ControlBarView.APP_BASE, view.virtualViewAt(6f + 20f, 70f));
        assertTrue(view.clickVirtualView(ControlBarView.APP_BASE + 1));
        assertEquals(Arrays.asList("app 1"), heard);
    }

    @Test public void oneRowHoldsTheBadgeTheToolsNavigationAndSearchAndNoMarks() {
        view.setForm(ControlBarForm.ONE_ROW);
        view.measure(View.MeasureSpec.makeMeasureSpec(388, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(52, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 388, 52);
        // The first tool stands after the badge and its air, on the row's centre.
        float firstTool = firstMarkLeft() + 20f;
        tap(firstTool, 26f);
        tap(388f - 6f - 20f, 26f);
        tap(388f - 6f - 46f - 20f, 26f);
        assertEquals(Arrays.asList("tool KEYBOARD", "tool SEARCH", "tool NAVIGATION"), heard);
        assertEquals(null, view.labelOf(0));
        assertEquals(ControlBarView.TOOL_BASE, view.virtualViewAt(firstTool, 26f));
    }

    @Test public void theRailHoldsTheSameZonesTopToBottom() {
        view.setForm(ControlBarForm.RAIL);
        view.measure(View.MeasureSpec.makeMeasureSpec(52, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 52, 400);
        // Search at the bottom, navigation above it; the front's tools below the badge.
        tap(26f, 400f - 6f - 20f);
        tap(26f, 400f - 6f - 46f - 20f);
        assertEquals(Arrays.asList("tool SEARCH", "tool NAVIGATION"), heard);
        heard.clear();
        int keyboard = ControlBarView.TOOL_BASE;
        assertEquals("Keyboard", view.labelOf(keyboard).toString());
        assertTrue(view.clickVirtualView(keyboard));
        assertEquals(Arrays.asList("tool KEYBOARD"), heard);
        assertEquals(null, view.labelOf(0));
    }

    @Test public void foldingBackToTwoRowsBringsTheMarksBack() {
        view.setForm(ControlBarForm.ONE_ROW);
        view.setForm(ControlBarForm.TWO_ROWS);
        tap(markX(2), 26f);
        assertEquals(Arrays.asList("mark 2"), heard);
    }

    /** Every form draws its zones in the order the model gives them ({@link ControlBarRows#lines}). */
    @Test public void eachFormDrawsItsZonesInTheModelsOrder() {
        int[][] sizes = {{388, 96}, {388, 52}, {52, 400}};
        ControlBarForm[] forms = {ControlBarForm.TWO_ROWS, ControlBarForm.ONE_ROW, ControlBarForm.RAIL};
        for (int f = 0; f < forms.length; f++) {
            ControlBarForm form = forms[f];
            view.setForm(form);
            view.measure(View.MeasureSpec.makeMeasureSpec(sizes[f][0], View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(sizes[f][1], View.MeasureSpec.EXACTLY));
            view.layout(0, 0, sizes[f][0], sizes[f][1]);
            java.util.List<java.util.List<ControlBarRows.Zone>> lines = ControlBarRows.lines(form);
            float lastLine = -1f;
            for (java.util.List<ControlBarRows.Zone> line : lines) {
                float last = -1f;
                float lineCentre = -1f;
                for (ControlBarRows.Zone zone : line) {
                    android.graphics.RectF cell = view.zoneBounds(zone);
                    assertTrue(form + " draws " + zone, !cell.isEmpty());
                    float along = form.isRail() ? cell.centerY() : cell.centerX();
                    assertTrue(form + ": " + zone + " after the zone before it", along > last);
                    last = along;
                    lineCentre = form.isRail() ? cell.centerX() : cell.centerY();
                }
                assertTrue(form + ": each line below the one before", lineCentre > lastLine);
                lastLine = lineCentre;
            }
            // What the form leaves out is not drawn.
            for (ControlBarRows.Zone zone : ControlBarRows.Zone.values()) {
                boolean held = false;
                for (java.util.List<ControlBarRows.Zone> line : lines) held |= line.contains(zone);
                if (!held) assertTrue(form + " leaves out " + zone, view.zoneBounds(zone).isEmpty());
            }
        }
    }

    @Test public void holdingAPinnedAppReportsTheHoldAndNotATap() {
        view.setPinnedApps(Arrays.asList(new ControlBarView.PinnedApp(null, "Camera", "cam.pkg")));
        view.setRows(ControlBarRows.of(ControlBarFront.home(3, 0)));
        // Attached, so the hold's delayed callback runs on the main looper.
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 26f, 70f, 0));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            android.view.ViewConfiguration.getLongPressTimeout() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        view.onTouchEvent(MotionEvent.obtain(t, t + 700, MotionEvent.ACTION_UP, 26f, 70f, 0));
        assertEquals(Arrays.asList("held 0 cell"), heard);
    }

    @Test public void holdingTheWorkspaceBadgeAsksForItsMenuAndHomesDoesNot() {
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        android.graphics.RectF badge = view.zoneBounds(ControlBarRows.Zone.PLACE);
        assertTrue(!badge.isEmpty());
        hold(badge.centerX(), badge.centerY());
        assertEquals(Arrays.asList("place held"), heard);

        heard.clear();
        view.setRows(ControlBarRows.of(ControlBarFront.home(3, 0)));
        view.layout(0, 0, 388, 96);
        badge = view.zoneBounds(ControlBarRows.Zone.PLACE);
        hold(badge.centerX(), badge.centerY());
        assertEquals("Home's badge has no menu: its hold is the bar's, Overview",
            Arrays.asList("bar held"), heard);
    }

    // ---- the hold for Overview (#430) ----------------------------------------------------------

    @Test public void holdingTheBarsFreeRoomOpensOverviewAndATapThereDoesNothing() {
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        float x = (markX(2) + 17f + view.zoneBounds(ControlBarRows.Zone.READING).left) / 2f;
        assertEquals("nothing else is there", androidx.customview.widget.ExploreByTouchHelper.INVALID_ID,
            view.virtualViewAt(x, 26f));

        tap(x, 26f);
        assertTrue(heard.isEmpty());
        hold(x, 26f);
        assertEquals(Arrays.asList("bar held"), heard);
    }

    @Test public void holdingAToolOpensOverviewAndSpendsItsTap() {
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        hold(6f + 20f, 70f);
        assertEquals(Arrays.asList("bar held"), heard);
    }

    @Test public void holdingAMarkIsNotOverviewsHold() {
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        hold(markX(1), 26f);
        assertEquals(Arrays.asList("mark 1"), heard);
    }

    @Test public void aSlideFromTheFreeRoomIsNoHold() {
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 6f + 20f, 70f, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, 6f + 20f, 30f, 0));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            android.view.ViewConfiguration.getLongPressTimeout() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        view.onTouchEvent(MotionEvent.obtain(t, t + 700, MotionEvent.ACTION_UP, 6f + 20f, 30f, 0));
        assertTrue(heard.isEmpty());
    }

    @Test public void talkBacksLongPressOnTheBarOpensOverview() {
        assertTrue(view.performLongClick());
        assertEquals(Arrays.asList("bar held"), heard);
    }

    private void hold(float x, float y) {
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            android.view.ViewConfiguration.getLongPressTimeout() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        view.onTouchEvent(MotionEvent.obtain(t, t + 700, MotionEvent.ACTION_UP, x, y, 0));
    }

    @Test public void dotsOffOrNoNotificationsLeaveTheIconAlone() {
        view.setPinnedApps(Arrays.asList(new ControlBarView.PinnedApp(null, "Camera", "cam.pkg")));
        view.setDotPackages(java.util.Collections.singleton("cam.pkg"));
        view.setDotPackages(java.util.Collections.emptySet());
        view.measure(View.MeasureSpec.makeMeasureSpec(388, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        view.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(388, 96,
            android.graphics.Bitmap.Config.ARGB_8888)));
    }

    private float readingX() {
        return view.zoneBounds(ControlBarRows.Zone.READING).centerX();
    }

    private float readingY() {
        return view.zoneBounds(ControlBarRows.Zone.READING).centerY();
    }

    @Test public void aTapOnTheReadingSlotAsksForTheNextReading() {
        tap(readingX(), readingY());
        assertEquals(Arrays.asList("reading"), heard);
    }

    @Test public void holdingTheReadingSlotAsksForTheCardAndTheTapThatFollowsIsSpent() {
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, readingX(), readingY(), 0));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            android.view.ViewConfiguration.getLongPressTimeout() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        view.onTouchEvent(MotionEvent.obtain(t, t + 700, MotionEvent.ACTION_UP, readingX(), readingY(), 0));
        assertEquals(Arrays.asList("reading held"), heard);
    }

    @Test public void theReadingSlotShowsItsIconAndNumberInEveryForm() {
        view.setReading(true, ReadingFormat.percent(ReadingKind.CPU, 42));
        int[][] sizes = {{388, 96}, {388, 52}, {52, 400}};
        ControlBarForm[] forms = {ControlBarForm.TWO_ROWS, ControlBarForm.ONE_ROW, ControlBarForm.RAIL};
        for (int f = 0; f < forms.length; f++) {
            view.setForm(forms[f]);
            view.measure(View.MeasureSpec.makeMeasureSpec(sizes[f][0], View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(sizes[f][1], View.MeasureSpec.EXACTLY));
            view.layout(0, 0, sizes[f][0], sizes[f][1]);
            assertTrue(forms[f] + " holds the slot", !view.zoneBounds(ControlBarRows.Zone.READING).isEmpty());
            view.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(sizes[f][0],
                sizes[f][1], android.graphics.Bitmap.Config.ARGB_8888)));
            assertEquals("42%", view.readingValue().full());
            assertEquals("42", view.readingValue().compact());
        }
    }

    @Test public void withTheSettingOffNoFormHoldsTheSlotAndATouchWhereItWasIsNotAReading() {
        float x = readingX();
        float y = readingY();
        view.setReading(false, null);
        assertTrue(view.zoneBounds(ControlBarRows.Zone.READING).isEmpty());
        tap(x, y);
        assertTrue(heard.isEmpty());
        view.setForm(ControlBarForm.ONE_ROW);
        assertTrue(view.zoneBounds(ControlBarRows.Zone.READING).isEmpty());
    }

    @Test public void theReadingSlotIsANodeWithATapAndALongPressAction() {
        view.setReading(true, ReadingFormat.percent(ReadingKind.BATTERY, 87));
        assertEquals(ControlBarView.READING_NODE, view.virtualViewAt(readingX(), readingY()));
        assertEquals("Battery 87%", view.labelOf(ControlBarView.READING_NODE).toString());
        assertTrue(view.clickVirtualView(ControlBarView.READING_NODE));
        assertEquals(Arrays.asList("reading"), heard);
    }

    @Test public void theBadgeAndTheReadingSlotHoldApartAndAreTwoNodes() {
        activity.addContentView(view, new android.view.ViewGroup.LayoutParams(388, 96));
        view.layout(0, 0, 388, 96);
        android.graphics.RectF badge = view.zoneBounds(ControlBarRows.Zone.PLACE);
        hold(badge.centerX(), badge.centerY());
        hold(readingX(), readingY());
        assertEquals(Arrays.asList("place held", "reading held"), heard);
        assertEquals(ControlBarView.BADGE_NODE, view.virtualViewAt(badge.centerX(), badge.centerY()));
        assertEquals(ControlBarView.READING_NODE, view.virtualViewAt(readingX(), readingY()));
        assertTrue(ControlBarView.BADGE_NODE != ControlBarView.READING_NODE);
        assertTrue("the badge has no tap", !view.clickVirtualView(ControlBarView.BADGE_NODE));
    }

    private void tap(float x, float y) {
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0));
    }

    @Test public void inEditModeANodesClickOpensTheCardAndLaunchesNothing() {
        view.setPinnedApps(Arrays.asList(new ControlBarView.PinnedApp(null, "Camera", "cam.pkg")));
        view.setRows(ControlBarRows.of(ControlBarFront.home(3, 0)));
        int[] taps = {0};
        view.setEditTap(() -> taps[0]++);
        assertTrue(view.clickVirtualView(ControlBarView.APP_BASE));
        assertTrue(view.clickVirtualView(1));
        assertEquals(2, taps[0]);
        assertTrue(heard.isEmpty());
    }

    @Test public void sixPinnedAppsNeverPushTheDrawerOutOfAPhonesRow() {
        view.setPinnedApps(Arrays.asList(
            new ControlBarView.PinnedApp(null, "A", "a"), new ControlBarView.PinnedApp(null, "B", "b"),
            new ControlBarView.PinnedApp(null, "C", "c"), new ControlBarView.PinnedApp(null, "D", "d"),
            new ControlBarView.PinnedApp(null, "E", "e"), new ControlBarView.PinnedApp(null, "F", "f")));
        // The pinned apps first, the Drawer after them.
        view.setRows(ControlBarRows.of(ControlBarFront.home(1, 0), true,
            new ControlBarRows.StoredTools("pinned_apps,drawer", null, null)));
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 360, 96);
        // Drawer, navigation and search are all there; apps took what was left.
        assertEquals("Drawer", view.labelOf(ControlBarView.TOOL_BASE).toString());
        assertTrue(view.labelOf(ControlBarView.TOOL_BASE + 2) != null);
        assertEquals(null, view.labelOf(ControlBarView.TOOL_BASE + 3));
    }
}
