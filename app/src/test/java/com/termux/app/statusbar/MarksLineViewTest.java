package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.MarksLine;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageBarModel.Input;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
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

    @Test public void aTapOnAMarkGoesToThatPage() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        MarksLine line = view.line();
        assertNotNull(line);
        assertEquals(4, line.slotCount());
        tap(line.centreAt(3), 20f);
        assertEquals("page 3", calls.get(0));
        tap(line.centreAt(2), 20f);
        assertEquals("window 1", calls.get(1));
    }

    @Test public void aTapOnTheCurrentPageOrBesideTheMarksDoesNothing() {
        view.setPages(model("HTA", 2, 3, -1), null);
        layOut(400, 40);
        MarksLine line = view.line();
        tap(line.centreAt(3), 20f);
        tap(399f, 20f);
        assertTrue(calls.isEmpty());
    }

    @Test public void aColumnDrawsAndTapsAlongY() {
        view.setVertical(true);
        view.setPages(model("HTA", 1, 1, -1), null);
        layOut(40, 400);
        MarksLine line = view.line();
        assertNotNull(line);
        tap(20f, line.centreAt(2));
        assertEquals("page 3", calls.get(0));
        tap(20f, line.centreAt(1));
        assertEquals("window 0", calls.get(1));
    }

    @Test public void theLineIsRebuiltOnlyWhenWhatIsDrawnChanges() {
        PageBarModel first = model("HTA", 2, 1, -1);
        view.setPages(first, null);
        layOut(400, 40);
        MarksLine line = view.line();
        view.setPages(model("HTA", 2, 1, -1), null);
        assertSame("the same entries keep the line", line, view.line());
        view.setPages(model("HTA", 2, 3, -1), null);
        assertNotSame("another current page rebuilds", line, view.line());
        MarksLine moved = view.line();
        view.setPages(model("HTA", 2, 3, -1), new boolean[] {false, false, true, false});
        assertNotSame("attention rebuilds", moved, view.line());
    }

    @Test public void aNarrowerLineFoldsInTheSameModel() {
        view.setPages(model("HHHHHT", 4, 1, -1), null);
        layOut(400, 40);
        int wide = view.line().slotCount();
        layOut(120, 40);
        assertTrue(view.line().slotCount() < wide);
    }

    @Test public void noEntriesOrNoSizeDrawsNothingAndRoutesNothing() {
        view.setPages(null, null);
        layOut(400, 40);
        assertNull(view.line());
        tap(10f, 20f);
        assertTrue(calls.isEmpty());
        Bitmap bitmap = Bitmap.createBitmap(400, 40, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
    }

    @Test public void drawingPaintsTheCurrentDiscAndTheOthersDots() {
        view.setPages(model("HHT", 1, 1, -1), null);
        layOut(400, 40);
        Bitmap bitmap = Bitmap.createBitmap(400, 40, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        MarksLine line = view.line();
        int rim = 7;
        assertTrue("the current page's disc reaches its rim",
            android.graphics.Color.alpha(bitmap.getPixel(Math.round(line.centreAt(0)) + rim, 20)) > 200);
        assertTrue("the other Home page is a dot at its centre",
            android.graphics.Color.alpha(bitmap.getPixel(Math.round(line.centreAt(1)), 20)) > 200);
        assertEquals("and a dot has no rim", 0,
            android.graphics.Color.alpha(bitmap.getPixel(Math.round(line.centreAt(1)) + rim, 20)));
    }

    private void drag(float y, float... xs) {
        long t = SystemClock.uptimeMillis();
        for (int i = 0; i < xs.length; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN
                : i == xs.length - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent e = MotionEvent.obtain(t, t + 10L * i, action, xs[i], y, 0);
            view.dispatchTouchEvent(e);
            e.recycle();
        }
    }

    @Test public void slidingToAnotherMarkAndReleasingGoesToItsPage() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        MarksLine line = view.line();
        drag(20f, line.centreAt(0), line.centreAt(1), line.centreAt(2), line.centreAt(3));
        assertEquals(1, calls.size());
        assertEquals("page 3", calls.get(0));
    }

    @Test public void slidingBackToTheStartCancelsTheSlide() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        MarksLine line = view.line();
        drag(20f, line.centreAt(1), line.centreAt(3), line.centreAt(1) + 2f);
        assertTrue(calls.isEmpty());
    }

    @Test public void slidingOntoTheCurrentPageDoesNothing() {
        view.setPages(model("HTA", 2, 3, -1), null);
        layOut(400, 40);
        MarksLine line = view.line();
        drag(20f, line.centreAt(0), line.centreAt(2), line.centreAt(3));
        assertTrue(calls.isEmpty());
    }

    @Test public void aColumnSlidesAlongY() {
        view.setVertical(true);
        view.setPages(model("HTA", 1, 1, -1), null);
        layOut(40, 400);
        MarksLine line = view.line();
        long t = SystemClock.uptimeMillis();
        float[] ys = {line.centreAt(0), line.centreAt(1), line.centreAt(2)};
        for (int i = 0; i < ys.length; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN
                : i == ys.length - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent e = MotionEvent.obtain(t, t + 10L * i, action, 20f, ys[i], 0);
            view.dispatchTouchEvent(e);
            e.recycle();
        }
        assertEquals("page 3", calls.get(0));
    }

    @Test public void everyMarkIsALabelledButtonThatNavigates() {
        view.setPages(model("HTA", 2, 1, -1), null);
        layOut(400, 40);
        String[] labels = view.slotLabels();
        assertEquals(view.line().slotCount(), labels.length);
        for (String label : labels) assertTrue(label, label != null && !label.isEmpty());
        assertEquals(view.line().entry(0, 0).label, labels[0]);
        assertTrue(view.clickSlot(3));
        assertEquals("page 3", calls.get(0));
        assertFalse("the current page has nothing to go to", view.clickSlot(0));
    }

    @Test public void aFoldedMarkIsLabelledWithItsCount() {
        view.setPages(model("HHHHHHHHHH", 0, 1, -1), null);
        layOut(120, 40);
        boolean any = false;
        for (int i = 0; i < view.line().slotCount(); i++) {
            if (view.line().slots().get(i).folded()) {
                any = true;
                assertTrue(view.slotLabels()[i].contains(
                    String.valueOf(view.line().slots().get(i).count)));
            }
        }
        assertTrue(any);
    }
}
