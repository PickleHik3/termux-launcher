package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.MarksRowLayout;
import com.termux.app.wall.pageswitch.MarksTouch;
import com.termux.app.wall.pageswitch.MarksTouchLayout;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageBarModel.Input;
import com.termux.app.wall.pageswitch.PageMarkIcons;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MarksLineViewTest {

    private final List<String> calls = new ArrayList<>();
    private MarksLineView view;

    private final PageSwitchTargets.Navigator nav = new PageSwitchTargets.Navigator() {
        @Override public void goToPage(int pageId) { calls.add("page " + pageId); }
        @Override public void showTerminalWindow(int index) { calls.add("window " + index); }
        @Override public void goToTerminal() { calls.add("terminal"); }
        @Override public void openOverview() { calls.add("overview"); }
    };

    @Before
    public void setUp() {
        view = new MarksLineView(RuntimeEnvironment.getApplication(), null);
        view.setNavigator(nav);
    }

    private static PageBarModel model(String kinds, int windows, int currentPageId, int currentWindow) {
        Input in = new Input();
        List<PageBarModel.Page> pages = new ArrayList<>();
        for (int i = 0; i < kinds.length(); i++) {
            PageKind kind = kinds.charAt(i) == 'H' ? PageKind.HOME
                : kinds.charAt(i) == 'T' ? PageKind.TERMINAL : PageKind.ASSISTANT;
            pages.add(new PageBarModel.Page(i + 1, kind, null));
        }
        in.pages = pages;
        List<String> names = new ArrayList<>();
        for (int w = 0; w < windows; w++) names.add(null);
        in.windowNames = names;
        in.currentPageId = currentPageId;
        in.currentWindow = currentWindow;
        return PageBarModel.build(in);
    }

    private void layOut(int w, int h) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
    }

    private void tap(float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y, 0);
        view.dispatchTouchEvent(down);
        view.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void send(int action, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, action, x, y, 0);
        view.dispatchTouchEvent(e);
        e.recycle();
    }

    /**
     * Down on rest slot {@code from}, then slides through the touch layout's slots {@code to} (in
     * its own coordinates, read after the down) and lifts on the last; along x at {@code y}.
     */
    private void slide(float y, int from, int... to) {
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(from), y);
        MarksTouch touch = view.touch();
        assertNotNull(touch);
        MarksTouchLayout layout = touch.layout();
        for (int i = 0; i < to.length; i++) {
            float x = layout.centreAt(to[i]);
            send(i == to.length - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, x, y);
        }
    }

    @Test public void theLineAsksForItsMarksAndFoldsByTheRoomItIsOffered() {
        view.setPages(model("HHTA", 1, 1, -1), null);
        int offered = 2000;
        view.measure(View.MeasureSpec.makeMeasureSpec(offered, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        assertEquals("Overview, then every mark open", 5, view.rowLayout().slotCount());
        assertTrue(view.rowLayout().isOverview(0));
        assertTrue("it takes its marks' length, not the room offered",
            view.getMeasuredWidth() < offered);
        int twin = view.getMeasuredWidth();
        int open = (int) Math.ceil(view.rowLayout().contentLengthPx());

        MarksLineView spacer = new MarksLineView(RuntimeEnvironment.getApplication(), null);
        spacer.setPages(model("HHTA", 1, 1, -1), null);
        spacer.measure(View.MeasureSpec.makeMeasureSpec(offered, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        assertEquals("Home's spacer holds the room the fixed line takes", twin,
            spacer.getMeasuredWidth());

        int tight = open - 1;
        view.measure(View.MeasureSpec.makeMeasureSpec(tight, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        assertTrue("fewer slots when the room is short", view.rowLayout().slotCount() < 5);
        assertTrue(view.getMeasuredWidth() <= tight);
    }

    @Test public void theMarkIsCappedAndNeverTallerThanTheRowAllows() {
        view.setPages(model("HTA", 1, 1, -1), null);
        layOut(400, 40);
        float density = RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density;
        assertEquals(MarksLineView.MARK_DP * density, view.rowLayout().markSizePx(), 0.01f);
        view.setMarkCapPx(12f * density);
        layOut(400, 40);
        assertEquals(12f * density, view.rowLayout().markSizePx(), 0.01f);
        view.setMarkCapPx(0f);
        layOut(400, 10);
        assertEquals(10f, view.rowLayout().markSizePx(), 0.01f);
    }

    @Test public void aTapOnAMarkGoesToThatPageAtOnce() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        MarksRowLayout rest = view.rowLayout();
        assertNotNull(rest);
        assertEquals(5, rest.slotCount());
        tap(rest.centreAt(4), 20f);
        assertEquals("page 3", calls.get(0));
        tap(rest.centreAt(3), 20f);
        assertEquals("window 1", calls.get(1));
        tap(rest.centreAt(0), 20f);
        assertEquals("overview", calls.get(2));
    }

    @Test public void aTapOnTheCurrentPageOrBesideTheMarksDoesNothing() {
        view.setPages(model("HTA", 2, 3, -1), null);
        layOut(400, 40);
        tap(view.rowLayout().centreAt(4), 20f);
        tap(399f, 20f);
        assertTrue(calls.isEmpty());
    }

    @Test public void aColumnDrawsAndTapsAlongY() {
        view.setVertical(true);
        view.setPages(model("HTA", 1, 1, -1), null);
        layOut(40, 400);
        MarksRowLayout rest = view.rowLayout();
        assertNotNull(rest);
        tap(20f, rest.centreAt(3));
        assertEquals("page 3", calls.get(0));
        tap(20f, rest.centreAt(2));
        assertEquals("window 0", calls.get(1));
    }

    @Test public void theLineIsRebuiltOnlyWhenWhatIsDrawnChanges() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        MarksRowLayout rest = view.rowLayout();
        view.setPages(model("HTA", 2, 1, -1), null);
        assertSame("the same entries keep the line", rest, view.rowLayout());
        view.setPages(model("HTA", 2, 3, -1), null);
        assertNotSame("another current page rebuilds", rest, view.rowLayout());
        MarksRowLayout moved = view.rowLayout();
        view.setPages(model("HTA", 2, 3, -1), new boolean[] {false, false, true, false});
        assertNotSame("attention rebuilds", moved, view.rowLayout());
    }

    @Test public void aNarrowerLineFoldsInTheSameModel() {
        view.setPages(model("HHHHHT", 4, 1, -1), null);
        layOut(400, 40);
        int wide = view.rowLayout().slotCount();
        layOut(120, 40);
        assertTrue(view.rowLayout().slotCount() < wide);
    }

    @Test public void noEntriesOrNoSizeDrawsNothingAndRoutesNothing() {
        view.setPages(null, null);
        layOut(400, 40);
        assertNull(view.rowLayout());
        tap(10f, 20f);
        assertTrue(calls.isEmpty());
        Bitmap bitmap = Bitmap.createBitmap(400, 40, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
    }

    @Test public void theCurrentPageIsTheAccentWithNothingAroundItAndTheOthersPlainDots() {
        int accent = 0xFF00C000;
        view.setAccent(accent);
        view.setPages(model("HHT", 1, 1, -1), null);
        layOut(400, 40);
        Bitmap bitmap = Bitmap.createBitmap(400, 40, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        MarksRowLayout rest = view.rowLayout();
        float size = rest.markSizePx();
        int current = Math.round(rest.centreAt(1));
        int dot = bitmap.getPixel(current, 20);
        assertTrue("the current dot is in the accent",
            Color.green(dot) > 150 && Color.red(dot) < 80 && Color.alpha(dot) > 200);
        int other = Math.round(rest.centreAt(2));
        assertTrue("the other Home page is a dot at its centre",
            Color.alpha(bitmap.getPixel(other, 20)) > 200);
        assertEquals("and nothing is drawn round the current dot", 0,
            Color.alpha(bitmap.getPixel(Math.round(rest.centreAt(1) + size * 0.375f), 20)));
    }

    private static final class Icons implements PageMarkIcons {
        int asked;
        @Nullable @Override public Drawable icon(@NonNull String iconKey) {
            asked++;
            return new ColorDrawable(0xFFFF0000);
        }
        @Nullable @Override public Typeface glyphTypeface() {
            return null;
        }
    }

    @Test public void aLinuxAppWearsItsIconAskedOnlyWhenTheModelChanges() {
        Input in = new Input();
        List<PageBarModel.Page> pages = new ArrayList<>();
        pages.add(new PageBarModel.Page(1, PageKind.HOME, null));
        pages.add(new PageBarModel.Page(2, PageKind.TERMINAL, null));
        in.pages = pages;
        in.windowNames = Collections.singletonList(null);
        in.linuxWindows = new boolean[] {true};
        in.windowAppIds = Collections.singletonList("org.example.app");
        in.currentPageId = 1;
        PageBarModel model = PageBarModel.build(in);
        Icons icons = new Icons();
        view.setMarkIcons(icons);
        view.setPages(model, null);
        layOut(400, 40);
        int asked = icons.asked;
        assertTrue(asked > 0);
        Bitmap bitmap = Bitmap.createBitmap(400, 40, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        view.draw(new Canvas(bitmap));
        assertEquals("drawing never asks", asked, icons.asked);
        MarksRowLayout rest = view.rowLayout();
        int last = rest.slotCount() - 1;
        int px = bitmap.getPixel(Math.round(rest.centreAt(last)), 20);
        assertTrue("the app's own colours", Color.red(px) > 200 && Color.green(px) < 60);
        view.setPages(PageBarModel.build(in), null);
        assertEquals("the same entries ask nothing", asked, icons.asked);
    }

    @Test public void slidingToAnotherMarkAndReleasingGoesToItsPage() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        slide(20f, 1, 2, 3, 4);
        assertEquals(1, calls.size());
        assertEquals("page 3", calls.get(0));
        assertNull("the touch is gone at the lift", view.touch());
    }

    @Test public void slidingToOverviewOpensIt() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        slide(20f, 2, 1, 0);
        assertEquals(Collections.singletonList("overview"), calls);
    }

    @Test public void slidingBackToTheStartChoosesNothing() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        slide(20f, 2, 4, 2);
        assertTrue(calls.isEmpty());
    }

    @Test public void slidingOntoTheCurrentPageDoesNothing() {
        view.setPages(model("HTA", 2, 3, -1), null);
        layOut(400, 40);
        slide(20f, 1, 3, 4);
        assertTrue(calls.isEmpty());
    }

    @Test public void aColumnSlidesAlongY() {
        view.setVertical(true);
        view.setPages(model("HTA", 1, 1, -1), null);
        layOut(40, 400);
        send(MotionEvent.ACTION_DOWN, 20f, view.rowLayout().centreAt(1));
        MarksTouch touch = view.touch();
        MarksTouchLayout layout = touch.layout();
        send(MotionEvent.ACTION_MOVE, 20f, layout.centreAt(2));
        send(MotionEvent.ACTION_UP, 20f, layout.centreAt(3));
        assertEquals("page 3", calls.get(0));
    }

    @Test public void theTouchIsMadeAtTheDownWithItsFocusOnTheTouchedMark() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(1), 20f);
        MarksTouch touch = view.touch();
        assertNotNull("the focus exists from the down", touch);
        MarksTouchLayout layout = touch.layout();
        assertEquals("every page has its own slot", 5, layout.slotCount());
        assertEquals(touch.layout().anchorSlot(), touch.focusSlot());
        send(MotionEvent.ACTION_CANCEL, 0f, 20f);
        assertNull(view.touch());
    }

    @Test public void everyMarkIsALabelledButtonThatNavigates() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        String[] labels = view.slotLabels();
        assertEquals(view.rowLayout().slotCount(), labels.length);
        for (String label : labels) assertTrue(label, label != null && !label.isEmpty());
        assertEquals(RuntimeEnvironment.getApplication().getString(R.string.overview_title),
            labels[0]);
        assertEquals(view.rowLayout().entries().get(0).label, labels[1]);
        assertTrue(view.clickSlot(4));
        assertEquals("page 3", calls.get(0));
        assertFalse("the current page has nothing to go to", view.clickSlot(1));
        assertTrue(view.clickSlot(0));
        assertEquals("overview", calls.get(1));
    }

    @Test public void theCurrentMarkIsSelectedAndOffersNoClick() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        android.view.accessibility.AccessibilityNodeProvider provider =
            view.getAccessibilityNodeProvider();
        android.view.accessibility.AccessibilityNodeInfo current =
            provider.createAccessibilityNodeInfo(1);
        assertTrue(current.isSelected());
        assertFalse(current.isClickable());
        assertFalse(current.getActionList().contains(
            android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK));
        android.view.accessibility.AccessibilityNodeInfo other =
            provider.createAccessibilityNodeInfo(4);
        assertFalse(other.isSelected());
        assertTrue(other.isClickable());
        assertTrue(other.getActionList().contains(
            android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK));
    }

    @Test public void aFoldedMarkIsLabelledWithItsCount() {
        view.setPages(model("HHHHHHHHHH", 0, 1, -1), null);
        layOut(120, 40);
        boolean any = false;
        MarksRowLayout rest = view.rowLayout();
        for (int i = 0; i < rest.slotCount(); i++) {
            if (rest.slot(i) != null && rest.slot(i).folded()) {
                any = true;
                assertTrue(view.slotLabels()[i].contains(String.valueOf(rest.slot(i).count)));
            }
        }
        assertTrue(any);
    }

    /** Records whether its child asked it to keep its hands off the stream. */
    private static final class Parent extends FrameLayout {
        boolean disallowed;
        Parent() { super(RuntimeEnvironment.getApplication()); }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
            disallowed = disallow;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    @Test public void aPressOnAMarkIsTheLinesWholeGesture() {
        // A slow slide must not be the bar's: the line keeps its parents off from the down, and
        // the bar's long-press popup never arms over a mark (holdsMarkAt).
        Parent parent = new Parent();
        parent.addView(view);
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        MarksRowLayout rest = view.rowLayout();
        assertNotNull(rest);
        assertTrue(view.holdsMarkAt(rest.centreAt(1), 20f));
        assertFalse(view.holdsMarkAt(399f, 20f));
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, rest.centreAt(1), 20f, 0);
        assertTrue(view.dispatchTouchEvent(down));
        down.recycle();
        assertTrue(parent.disallowed);
        // Off the line across its axis before sliding: the parents may have it again.
        MotionEvent across = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_MOVE, rest.centreAt(1), 200f, 0);
        view.dispatchTouchEvent(across);
        across.recycle();
        assertFalse(parent.disallowed);
        assertNull("given up, the touch is gone", view.touch());
    }

    // ---- the zoom in the chrome layer --------------------------------------------------------

    @Test public void theZoomStandsInTheChromeLayerFromTheDownToTheLiftAndTheReadingsGiveWay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout chrome = new FrameLayout(activity);
        chrome.setId(R.id.window_layer_chrome);
        LinearLayout row = new LinearLayout(activity);
        row.setId(R.id.terminal_status_row);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MarksLineView marks = new MarksLineView(activity, null);
        marks.setNavigator(nav);
        marks.setSlideSupport(null, () -> true);
        row.addView(marks, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT));
        View spare = new View(activity);
        row.addView(spare, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        View readings = new View(activity);
        readings.setId(R.id.terminal_status_widgets);
        row.addView(readings, new LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(row, new FrameLayout.LayoutParams(400, 40));
        root.addView(chrome, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        activity.setContentView(root);
        marks.setPages(model("HTA", 2, 1, -1), null);
        List<Float> asked = new ArrayList<>();
        marks.setTitleGlass(new MarksLineView.TitleGlass() {
            @Override public int tint() { return 0x80202020; }
            @Nullable @Override public Drawable ground(float radius, @NonNull View anchor) {
                asked.add(radius);
                return new ColorDrawable(0x80202020);
            }
        });
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 400);

        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN,
            marks.rowLayout().centreAt(1), 20f, 0);
        assertTrue(marks.dispatchTouchEvent(down));
        down.recycle();
        assertTrue(marks.zoomShown());
        assertEquals(1, chrome.getChildCount());
        assertEquals("the capsule's glass is made once per half, as high as the row, a capsule",
            Arrays.asList(20f, 20f), asked);
        assertEquals("the capsule and its other half stand in the overlay", 2,
            ((ViewGroup) chrome.getChildAt(0)).getChildCount());
        assertEquals("the readings give way to the title", 0f, readings.getAlpha(), 0f);
        MarksTouchLayout layout = marks.touch().layout();
        assertTrue("the touch spreads past the marks' own length",
            layout.contentStartPx() + layout.contentLengthPx() > marks.getWidth());
        // The zoomed marks draw without a canvas of the line's own.
        Bitmap bitmap = Bitmap.createBitmap(400, 60, Bitmap.Config.ARGB_8888);
        chrome.getChildAt(0).draw(new Canvas(bitmap));

        MotionEvent up = MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP,
            marks.rowLayout().centreAt(1), 20f, 0);
        marks.dispatchTouchEvent(up);
        up.recycle();
        assertFalse(marks.zoomShown());
        assertEquals("gone at the lift", 0, chrome.getChildCount());
        assertEquals(1f, readings.getAlpha(), 0f);
    }

    @Test public void aPageChangeUnderTheTouchPutsTheZoomAway() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout chrome = new FrameLayout(activity);
        chrome.setId(R.id.window_layer_chrome);
        MarksLineView marks = new MarksLineView(activity, null);
        root.addView(marks, new FrameLayout.LayoutParams(400, 40));
        root.addView(chrome, new FrameLayout.LayoutParams(400, 400));
        activity.setContentView(root);
        marks.setPages(model("HTA", 2, 1, -1), null);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 400);
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN,
            marks.rowLayout().centreAt(1), 20f, 0);
        marks.dispatchTouchEvent(down);
        down.recycle();
        assertEquals(1, chrome.getChildCount());
        marks.setPages(model("HTA", 2, 3, -1), null);
        assertEquals(0, chrome.getChildCount());
        assertNull(marks.touch());
        marks.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN,
            marks.rowLayout().centreAt(1), 20f, 0));
        marks.cancelTouch();
        assertEquals("cancelled from outside", 0, chrome.getChildCount());
    }

    @Test public void aLineThatWantsNoTitleKeepsTheReadingsAndTellsWhenTheZoomComesAndGoes() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout chrome = new FrameLayout(activity);
        chrome.setId(R.id.window_layer_chrome);
        LinearLayout row = new LinearLayout(activity);
        row.setId(R.id.terminal_status_row);
        MarksLineView marks = new MarksLineView(activity, null);
        row.addView(marks, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT));
        View readings = new View(activity);
        readings.setId(R.id.terminal_status_widgets);
        row.addView(readings, new LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(row, new FrameLayout.LayoutParams(400, 40));
        root.addView(chrome, new FrameLayout.LayoutParams(400, 400));
        activity.setContentView(root);
        assertTrue("the title is on until a line turns it off", marks.titleShown());
        marks.setTitleShown(false);
        assertFalse(marks.titleShown());
        List<Boolean> told = new ArrayList<>();
        marks.setZoomListener(() -> told.add(marks.isZoomShown()));
        marks.setSlideSupport(null, () -> true);
        marks.setPages(model("HTA", 2, 1, -1), null);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 400);
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN,
            marks.rowLayout().centreAt(1), 20f, 0);
        marks.dispatchTouchEvent(down);
        down.recycle();
        assertTrue(marks.zoomShown());
        assertEquals("no title: the readings stay", 1f, readings.getAlpha(), 0f);
        marks.cancelTouch();
        assertFalse(marks.zoomShown());
        assertEquals(java.util.Arrays.asList(true, false), told);
    }

    @Test public void aLineOnAGroundItWasGivenSolvesItsInksOnThatGround() {
        view.setAccent(Color.rgb(0x40, 0x80, 0xFF));
        view.setGroundInk(0xFF101010, true);
        assertTrue("pale ink on a dark glass", Color.red(view.ink().other) > 0x80);
        view.setGroundInk(0xFFF0F0F0, false);
        assertTrue("dark ink on a pale glass", Color.red(view.ink().other) < 0x80);
        MarksLineInk held = view.ink();
        view.setGroundInk(0xFFF0F0F0, false);
        assertSame("the same ground again changes nothing", held, view.ink());
    }

    /** A host standing for Home's bar: records what it was handed and told. */
    private static final class FakeHost implements MarksLineView.TitleHost {
        final View readings;
        final float room;
        final List<String> shown = new ArrayList<>();
        int cleared;

        FakeHost(View readings, float room) {
            this.readings = readings;
            this.room = room;
        }

        @Override public View readings() { return readings; }
        @Override public float roomPx() { return room; }
        @Override public void show(@NonNull String text, int color, float sizePx, float progress) {
            shown.add(text);
        }
        @Override public void clear() { cleared++; }
    }

    private MarksLineView pillOnItsOwn(Activity activity, FrameLayout chrome, FakeHost host) {
        FrameLayout root = new FrameLayout(activity);
        chrome.setId(R.id.window_layer_chrome);
        MarksLineView marks = new MarksLineView(activity, null);
        marks.setTitleShown(false);
        marks.setSlideSupport(null, () -> true);
        marks.setTitleHost(host);
        root.addView(marks, new FrameLayout.LayoutParams(400, 40));
        root.addView(chrome, new FrameLayout.LayoutParams(400, 400));
        activity.setContentView(root);
        marks.setPages(model("HTA", 2, 1, -1), null);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 400);
        return marks;
    }

    private static void down(MarksLineView marks, float x) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, 20f, 0);
        marks.dispatchTouchEvent(e);
        e.recycle();
    }

    @Test public void aTitleHostShowsTheNameFlipsItWithTheFocusAndPutsBothBackAtTheLift() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View readings = new View(activity);
        FakeHost host = new FakeHost(readings, 120f);
        MarksLineView marks = pillOnItsOwn(activity, new FrameLayout(activity), host);
        down(marks, marks.rowLayout().centreAt(1));
        assertTrue(marks.hostActive());
        assertEquals("the readings make room", 0f, readings.getAlpha(), 0f);
        assertEquals(1, host.shown.size());
        // The finger slides to another mark: the name flips; the same name is not handed twice.
        MarksTouchLayout layout = marks.touch().layout();
        long t = SystemClock.uptimeMillis();
        for (int slot : new int[] {2, 2}) {
            MotionEvent move = MotionEvent.obtain(t, t, MotionEvent.ACTION_MOVE,
                layout.centreAt(slot), 20f, 0);
            marks.dispatchTouchEvent(move);
            move.recycle();
        }
        assertEquals(2, host.shown.size());
        assertFalse(host.shown.get(0).equals(host.shown.get(1)));
        MotionEvent up = MotionEvent.obtain(t, t, MotionEvent.ACTION_UP,
            layout.centreAt(2), 20f, 0);
        marks.dispatchTouchEvent(up);
        up.recycle();
        assertFalse(marks.hostActive());
        assertEquals("the title is put back", 1, host.cleared);
        assertEquals("the readings come back", 1f, readings.getAlpha(), 0f);
    }

    @Test public void aTitleHostWithNoRoomIsAskedForNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View readings = new View(activity);
        FakeHost host = new FakeHost(readings, 0f);
        MarksLineView marks = pillOnItsOwn(activity, new FrameLayout(activity), host);
        down(marks, marks.rowLayout().centreAt(1));
        assertFalse("Home's bar hidden: no title", marks.hostActive());
        assertTrue(host.shown.isEmpty());
        assertEquals(1f, readings.getAlpha(), 0f);
        marks.cancelTouch();
    }

    @Test public void everyWayATouchEndsPutsTheHostsTitleBack() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        // Cancel from outside, a page change, the line going away, the host being replaced.
        for (int way = 0; way < 4; way++) {
            View readings = new View(activity);
            FakeHost host = new FakeHost(readings, 120f);
            MarksLineView marks = pillOnItsOwn(activity, new FrameLayout(activity), host);
            down(marks, marks.rowLayout().centreAt(1));
            assertTrue("way " + way, marks.hostActive());
            if (way == 0) marks.cancelTouch();
            else if (way == 1) marks.setPages(model("HTA", 2, 3, -1), null);
            else if (way == 2) marks.setVisibility(View.INVISIBLE);
            else marks.setTitleHost(null);
            assertFalse("way " + way, marks.hostActive());
            assertEquals("way " + way, 1, host.cleared);
            assertEquals("way " + way, 1f, readings.getAlpha(), 0f);
        }
    }
}
