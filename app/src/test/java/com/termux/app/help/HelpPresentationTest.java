package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.termux.R;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.launcher.widget.WidgetGridView;
import com.termux.app.terminal.TerminalWindowBar;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HelpPresentationTest {
    private Activity activity;
    private FrameLayout root;
    private View wall;
    private View status;
    private HelpOverlayView overlay;
    private HelpTargets.ViewFinder finder;
    private int dismissed;
    private String practised;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        root = new FrameLayout(activity);
        activity.setContentView(root);
        wall = new View(activity); wall.setId(R.id.terminal_pane_wall);
        root.addView(wall, new FrameLayout.LayoutParams(400,500));
        status = new View(activity); status.setId(R.id.terminal_window_bar_host);
        root.addView(status, new FrameLayout.LayoutParams(400,40));
        finder = new HelpTargets.ViewFinder() {
            @Override public View findHelpView(int id) {
                return id == android.R.id.content ? root : root.findViewById(id);
            }
            @Override public View activePane() { return wall; }
            @Override public int paneCount() { return 1; }
            @Override public boolean keyRectOnScreen(String name,Rect out) { return false; }
            @Override public boolean keyCornerRectOnScreen(String name,Rect out) { return false; }
        };
        overlay = new HelpOverlayView(activity, finder, () -> dismissed++);
        overlay.setPracticeListener(lessonId -> practised = lessonId);
        root.addView(overlay,new FrameLayout.LayoutParams(400,800));
        layout();
    }
    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        root.layout(0,0,400,800);
        wall.layout(0,60,400,560);
        status.layout(0,0,400,40);
        overlay.layout(0,0,400,800);
    }
    private void open(PaneWallPage place) {
        overlay.show(place); layout(); overlay.refresh(); layout();
    }
    /** The guide, then the catalogue button: where the topic chooser now lives. */
    private void openTopics(PaneWallPage place) {
        open(place);
        described("Help topics").performClick();
        layout();
    }
    /** One of the overlay's own floating buttons, by the name a reader hears. */
    private View described(String description) {
        for (int i = 0; i < overlay.getChildCount(); i++) {
            View view = overlay.getChildAt(i);
            CharSequence had = view.getContentDescription();
            if (had != null && description.contentEquals(had)) return view;
        }
        return null;
    }
    private void tap(float x,float y) {
        MotionEvent down = MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,y,0);
        MotionEvent up = MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,x,y,0);
        assertTrue(overlay.dispatchTouchEvent(down));
        assertTrue(overlay.dispatchTouchEvent(up));
        down.recycle(); up.recycle();
    }

    /** Every text view under the overlay, in the order they were added. */
    private java.util.List<TextView> texts() {
        java.util.List<TextView> found = new java.util.ArrayList<>();
        collect(overlay, found);
        return found;
    }
    private void collect(View view, java.util.List<TextView> out) {
        if (view instanceof TextView) out.add((TextView) view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
        }
    }
    private TextView exactly(String text) {
        for (TextView view : texts()) if (text.contentEquals(view.getText())) return view;
        return null;
    }
    private TextView startingWith(String prefix) {
        for (TextView view : texts()) if (view.getText().toString().startsWith(prefix)) return view;
        return null;
    }
    private Rect onOverlay(View view) {
        int[] source = new int[2], origin = new int[2];
        view.getLocationOnScreen(source);
        overlay.getLocationOnScreen(origin);
        int left = source[0] - origin[0], top = source[1] - origin[1];
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }

    @Test public void everyPlaceOpensOnTheGuideWithTwoGlyphsAndNoPanel() {
        for (PaneWallPage place : PaneWallPage.values()) {
            open(place);
            assertNotNull(place.name(), startingWith("Status bar\n"));
            assertNotNull(place.name(), described("Close help"));
            assertNotNull(place.name(), described("Help topics"));
            assertNull(place.name(), exactly("Show all"));
            assertNull(place.name(), exactly("Show basics"));
            assertNull(place.name(), exactly("Back to topics"));
            assertNull(place.name(), exactly("Previous"));
            assertNull(place.name(), exactly("Next"));
            assertNull(place.name(), exactly("Show topics"));
            overlay.dismiss();
        }
    }

    @Test public void theGuideFitsOnOnePage() {
        open(PaneWallPage.TERMINAL);
        assertEquals(java.util.Collections.emptyList(), overlay.unplacedGuideIds());
    }

    @Test public void theCloseGlyphDismissesHelpOnce() {
        open(PaneWallPage.WIDGETS);
        View close = described("Close help");
        assertNotNull(close);
        close.performClick();
        assertFalse(overlay.isShowing());
        assertEquals(1, dismissed);
    }

    @Test public void theCatalogueGlyphOpensTheChooserAndPutsItAwayAgain() {
        open(PaneWallPage.TERMINAL);
        described("Help topics").performClick();
        layout();
        assertTrue(overlay.isShowing());
        assertNotNull(exactly("Show all"));
        assertNotNull(startingWith("Pane corners\n"));
        described("Help topics").performClick();
        layout();
        assertNull(exactly("Show all"));
        assertNotNull(startingWith("Status bar\n"));
        assertEquals(0, dismissed);
    }

    @Test public void showAllFromTheChooserReturnsToTheGuide() {
        openTopics(PaneWallPage.TERMINAL);
        exactly("Show all").performClick();
        layout();
        assertTrue(overlay.isShowing());
        assertNull(exactly("Show all"));
        assertNull(exactly("Show basics"));
        assertNotNull(startingWith("Status bar\n"));
        assertNotNull(described("Close help"));
        assertEquals(0, dismissed);
    }

    @Test public void aGlyphTapIsConsumedAndReachesNoLauncherControl() {
        open(PaneWallPage.TERMINAL);
        View catalogue = described("Help topics");
        assertNotNull(catalogue);
        tap(catalogue.getLeft() + catalogue.getWidth() / 2f,
            catalogue.getTop() + catalogue.getHeight() / 2f);
        assertTrue(overlay.isShowing());
        assertEquals(0, dismissed);
    }

    @Test public void anOutsideTapDismissesTheChooserToo() {
        openTopics(PaneWallPage.TERMINAL);
        assertNotNull(exactly("Show all"));
        tap(200, 770);
        assertFalse(overlay.isShowing());
        assertEquals(1, dismissed);
    }

    @Test public void aTopicChipChangesWhatIsReadAndLeavesHelpOpen() {
        openTopics(PaneWallPage.TERMINAL);
        TextView chip = startingWith("Pane corners\n");
        assertNotNull(chip);
        chip.performClick();
        layout();
        assertTrue(overlay.isShowing());
        assertNotNull(exactly("Back to topics"));
        assertNotNull(startingWith("Every pane corner holds"));
        assertNull(exactly("Show all"));
        assertEquals(0, dismissed);
    }

    @Test public void showGestureDemonstratesAndLeavesHelpOpen() {
        openTopics(PaneWallPage.TERMINAL);
        startingWith("Pane corners\n").performClick();
        layout();
        TextView gesture = exactly("Show gesture");
        assertNotNull(gesture);
        assertTrue(gesture.isEnabled());
        gesture.performClick();
        assertTrue(overlay.isShowing());
        assertTrue(overlay.isShowingGesture());
        assertEquals(0, dismissed);
    }

    @Test public void tryItClosesHelpAndNamesTheLesson() {
        openTopics(PaneWallPage.TERMINAL);
        startingWith("Pane corners\n").performClick();
        layout();
        TextView tryIt = exactly("Try it");
        assertNotNull(tryIt);
        assertTrue(tryIt.isEnabled());
        tryIt.performClick();
        assertFalse(overlay.isShowing());
        assertEquals(1, dismissed);
        assertEquals("find_help", practised);
    }

    @Test public void aTopicsSentencesScrollAndItsButtonsDoNot() {
        openTopics(PaneWallPage.TERMINAL);
        startingWith("Pane corners\n").performClick();
        layout();
        assertTrue(inScrollView(startingWith("Every pane corner holds")));
        assertFalse(inScrollView(exactly("Back to topics")));
        assertFalse(inScrollView(exactly("Show gesture")));
    }

    private boolean inScrollView(View view) {
        assertNotNull(view);
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof android.widget.ScrollView) return true;
            parent = parent.getParent();
        }
        return false;
    }

    @Test public void thePopupCarriesNoCloseOfItsOwn() {
        openTopics(PaneWallPage.TERMINAL);
        assertNull(exactly("Close"));
        startingWith("Pane corners\n").performClick();
        layout();
        assertNull(exactly("Close"));
        assertNotNull(described("Close help"));
    }

    @Test public void tryItIsNotOfferedWhileTheLauncherCannotTakeOne() {
        overlay.setPracticeAvailable(false);
        openTopics(PaneWallPage.TERMINAL);
        startingWith("Pane corners\n").performClick();
        layout();
        TextView tryIt = exactly("Try it");
        assertNotNull(tryIt);
        assertFalse(tryIt.isEnabled());
        tryIt.performClick();
        assertTrue(overlay.isShowing());
        assertNull(practised);
    }

    @Test public void theWindowsBoxCoversTheChipsAndThePlus() {
        TerminalWindowBar bar = new TerminalWindowBar(activity, null);
        bar.setId(R.id.terminal_window_bar);
        root.addView(bar, new FrameLayout.LayoutParams(300, 40));
        bar.setWindows(java.util.Arrays.asList(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("zbook", "zbook")), 0);
        layout();
        // Well inside the test window: a rect past its frame is clipped away and measures as
        // "not on screen", which is not what this test is about.
        bar.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        bar.layout(0, 100, 300, 140);
        android.view.ViewGroup strip = (android.view.ViewGroup) bar.chipStripView();
        View plus = bar.createWindowButtonView();
        assertNotNull(plus);
        Rect windows = null;
        for (HelpTargets.Target target : new HelpTargets(finder, overlay)
                .measure(PaneWallPage.TERMINAL).targets) {
            if ("windows".equals(target.id)) windows = target.rect;
        }
        assertNotNull(windows);
        assertTrue(windows.contains(onOverlay(strip.getChildAt(0))));
        assertTrue(windows.contains(onOverlay(plus)));
    }

    @Test public void outsideTapConsumesAndDismissesOnlyOnce() {
        open(PaneWallPage.WIDGETS);
        assertTrue(overlay.isShowing());
        tap(200,770);
        assertFalse(overlay.isShowing());
        overlay.dismiss();
        assertEquals(1,dismissed);
        assertEquals(0,overlay.getChildCount());
    }
    @Test public void cardTapStaysOpenAndRemeasureDropsHiddenTarget() {
        open(PaneWallPage.WIDGETS);
        TextView card = null;
        for (int i=0; i<overlay.getChildCount(); i++) {
            View v = overlay.getChildAt(i);
            if (v instanceof TextView && ((TextView)v).getText().toString().startsWith("Status bar\n"))
                card = (TextView)v;
        }
        assertNotNull(card);
        tap(card.getLeft()+4,card.getTop()+4);
        assertTrue(overlay.isShowing());
        status.setVisibility(View.GONE);
        overlay.refresh(); layout();
        for (int i=0; i<overlay.getChildCount(); i++) {
            View v=overlay.getChildAt(i);
            if (v instanceof TextView) assertFalse(((TextView)v).getText().toString().startsWith("Status bar\n"));
        }
        overlay.dismiss();
        open(PaneWallPage.DISPLAY);
        assertTrue(overlay.isShowing());
    }
    @Test public void configuredExtraKeyLabelsIncludeSecondaryAndPlainGlyph() throws Exception {
        ExtraKeysInfo keys = new ExtraKeysInfo("[[{key:'tool:pane.split',popup:'tool:window.new'},'LEFT']]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals("Split",HelpCopy.keyLabel(activity,keys.getMatrix()[0][0]));
        assertEquals("window",HelpCopy.keyLabel(activity,keys.getMatrix()[0][0].getPopup()));
        assertEquals(keys.getMatrix()[0][1].getDisplay(),HelpCopy.keyLabel(activity,keys.getMatrix()[0][1]));
        assertEquals("Ctrl, Alt, Shift, C",HelpTargets.displayChord("ctrl+alt+shift+c"));
    }
    @Test public void emptyWidgetRegionUsesGridMetrics() {
        WidgetGridView grid = new WidgetGridView(activity);
        grid.layout(0,0,400,500);
        assertEquals(grid.metrics().contentBounds(),HelpTargets.largestEmptyRegion(grid));
        View occupied = new View(activity);
        grid.addView(occupied);
        occupied.layout(0,0,400,500);
        assertNull(HelpTargets.largestEmptyRegion(grid));
    }
}
