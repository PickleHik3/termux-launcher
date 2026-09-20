package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.termux.R;
import com.termux.app.tour.TourGesture;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.launcher.widget.WidgetGridView;
import com.termux.app.terminal.TerminalWindowBar;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Help's overlay, drawn both ways: the curated overview help opens on — a few cards at once, the
 * launcher's own keys labelled, and the two buttons — and "Explore this screen", with a marker on
 * every measured control, one card at a time, a toolbar out of the card's way, and an explicit
 * answer when a control goes away or a card will not fit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w400dp-h800dp")
public class HelpPresentationTest {
    private Activity activity;
    private FrameLayout root;
    private View wall;
    private View status;
    private View dock;
    /** Built by the tests that are about the extra keys row; laid out with everything else. */
    private ExtraKeysView keys;
    private Rect keysBounds = new Rect(0, 560, 400, 620);
    private HelpOverlayView overlay;
    private HelpTargets.ViewFinder finder;

    /** The keyboard's own keys, by the name the targets ask for; empty = the keyboard is down. */
    private final java.util.Map<String, Rect> keyRects = new java.util.HashMap<>();

    private String read;
    private String gone;
    private String noSeat;
    private int backToHelp;
    private int closed;
    private int openedGuide;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        root = new FrameLayout(activity);
        activity.setContentView(root);
        wall = new View(activity); wall.setId(R.id.terminal_pane_wall);
        root.addView(wall, new FrameLayout.LayoutParams(400, 500));
        status = new View(activity); status.setId(R.id.terminal_window_bar_host);
        root.addView(status, new FrameLayout.LayoutParams(400, 40));
        dock = new View(activity); dock.setId(R.id.apps_bar_viewpager);
        root.addView(dock, new FrameLayout.LayoutParams(400, 100));
        finder = new HelpTargets.ViewFinder() {
            @Override public View findHelpView(int id) {
                return id == android.R.id.content ? root : root.findViewById(id);
            }
            @Override public View activePane() { return wall; }
            @Override public int paneCount() { return 1; }
            @Override public boolean keyRectOnScreen(String name, Rect out) {
                Rect rect = keyRects.get(name);
                if (rect == null) return false;
                out.set(rect);
                return true;
            }
            @Override public boolean keyCornerRectOnScreen(String name, Rect out) {
                return keyRectOnScreen(name, out);
            }
        };
        overlay = new HelpOverlayView(activity, finder);
        overlay.setExploreListener(new HelpOverlayView.ExploreListener() {
            @Override public void onReadTopic(String topicId) { read = topicId; }
            @Override public void onOpenGuide() { openedGuide++; }
            @Override public void onBackToHelp() { backToHelp++; }
            @Override public void onCloseHelp() { closed++; }
            @Override public void onTargetGone(String topicId) { gone = topicId; }
            @Override public void onCardDoesNotFit(String topicId) { noSeat = topicId; }
        });
        root.addView(overlay, new FrameLayout.LayoutParams(400, 800));
        layout();
    }

    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        wall.layout(0, 60, 400, 560);
        status.layout(0, 0, 400, 40);
        dock.layout(0, 440, 400, 540);
        if (keys != null) {
            keys.measure(View.MeasureSpec.makeMeasureSpec(keysBounds.width(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(keysBounds.height(), View.MeasureSpec.EXACTLY));
            keys.layout(keysBounds.left, keysBounds.top, keysBounds.right, keysBounds.bottom);
        }
        overlay.layout(0, 0, 400, 800);
    }

    /** The keyboard up, with the two prefix keys and the settings cog where help looks for them. */
    private void keyboardUp() {
        keyRects.put("ctrl", new Rect(0, 700, 60, 760));
        keyRects.put("alt", new Rect(64, 700, 124, 760));
        keyRects.put("config", new Rect(330, 700, 370, 740));
    }

    private void overview(PaneWallPage place) {
        overlay.overview(place);
        layout();
        overlay.refresh();
        layout();
    }

    private void explore(PaneWallPage place) {
        overlay.explore(place, null);
        layout();
        overlay.refresh();
        layout();
    }

    /** The marker dot for one control, tapped the way a finger or TalkBack taps it. */
    private void tapMarker(String targetId) {
        View marker = overlay.markerView(targetId);
        assertNotNull("no marker for " + targetId, marker);
        marker.performClick();
        layout();
    }

    private void tap(float x, float y) {
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, x, y, 0);
        assertTrue(overlay.dispatchTouchEvent(down));
        assertTrue(overlay.dispatchTouchEvent(up));
        down.recycle(); up.recycle();
        layout();
    }

    /** A finger that travelled: a swipe over the launcher under help, not a tap on help. */
    private void swipe(float fromX, float fromY, float toX, float toY) {
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, fromX, fromY, 0);
        MotionEvent move = MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, toX, toY, 0);
        MotionEvent up = MotionEvent.obtain(0, 2, MotionEvent.ACTION_UP, toX, toY, 0);
        assertTrue(overlay.dispatchTouchEvent(down));
        assertTrue(overlay.dispatchTouchEvent(move));
        assertTrue(overlay.dispatchTouchEvent(up));
        down.recycle(); move.recycle(); up.recycle();
        layout();
    }

    private java.util.List<TextView> texts() {
        java.util.List<TextView> found = new java.util.ArrayList<>();
        collect(overlay, found);
        return found;
    }
    private void collect(View view, java.util.List<TextView> out) {
        if (view instanceof TextView) out.add((TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
        }
    }
    private TextView exactly(String text) {
        for (TextView view : texts()) if (text.contentEquals(view.getText())) return view;
        return null;
    }
    private int countExactly(String text) {
        int count = 0;
        for (TextView view : texts()) if (text.contentEquals(view.getText())) count++;
        return count;
    }
    private View described(String description) {
        for (TextView view : texts()) {
            CharSequence had = view.getContentDescription();
            if (had != null && description.contentEquals(had)) return view;
        }
        return null;
    }
    private String string(int res) { return activity.getString(res); }

    private void assertClearOfEverything() {
        Rect card = overlay.cardBounds();
        assertNotNull("nothing is seated", card);
        Rect toolbar = overlay.toolbarBounds();
        assertNotNull(toolbar);
        assertFalse("the card is on the toolbar", Rect.intersects(card, toolbar));
        String topicId = overlay.selectedTopicId();
        HelpTopics.Entry entry = HelpTopics.entry(topicId);
        Rect target = overlay.measuredRect(entry.targetId);
        assertNotNull(target);
        assertFalse("the card covers the control it explains", Rect.intersects(card, target));
        assertFalse("the toolbar covers the control", Rect.intersects(toolbar, target));
        assertTrue("the card left the screen", card.left >= 0 && card.top >= 0
            && card.right <= 400 && card.bottom <= 800);
        for (Rect key : overlay.keyCardBounds())
            assertFalse("the card is on a key card", Rect.intersects(card, key));
    }

    /** The curated controls this bare harness could measure, whatever else is on screen. */
    private java.util.SortedSet<String> curatedOnScreen(PaneWallPage place) {
        java.util.SortedSet<String> ids = new java.util.TreeSet<>();
        for (String targetId : HelpPresentationModel.OVERVIEW_TARGET_IDS) {
            if (HelpTopics.forTarget(place, targetId) == null) continue;
            if (overlay.measuredRect(targetId) != null) ids.add(targetId);
        }
        return ids;
    }

    /** Every card on its own patch: off the other cards, off the boxed controls, off the buttons. */
    private void assertTheOverviewIsClear() {
        java.util.List<Rect> seen = new java.util.ArrayList<>();
        Rect buttons = overlay.buttonsBounds();
        assertNotNull(buttons);
        for (String id : overlay.overviewCardIds()) {
            Rect card = overlay.overviewCardBounds(id);
            assertNotNull("no card for " + id, card);
            assertTrue(id + "'s card left the screen", card.left >= 0 && card.top >= 0
                && card.right <= 400 && card.bottom <= 800);
            assertFalse(id + "'s card is on the buttons", Rect.intersects(card, buttons));
            for (Rect other : seen)
                assertFalse("two cards sit on each other", Rect.intersects(card, other));
            for (String boxed : overlay.overviewCardIds())
                assertFalse(id + "'s card covers " + boxed,
                    Rect.intersects(card, overlay.measuredRect(boxed)));
            for (Rect key : overlay.keyCardBounds())
                assertFalse(id + "'s card is on a key label", Rect.intersects(card, key));
            seen.add(card);
        }
    }

    /** A point the overview has put nothing on, for a tap that must do nothing. */
    private float[] emptyPoint() {
        for (int y = 20; y < 800; y += 10) for (int x = 20; x < 400; x += 10) {
            boolean free = overlay.buttonsBounds() == null
                || !overlay.buttonsBounds().contains(x, y);
            for (String id : overlay.overviewCardIds())
                free &= !overlay.overviewCardBounds(id).contains(x, y);
            for (Rect key : overlay.keyCardBounds()) free &= !key.contains(x, y);
            if (free) return new float[] {x, y};
        }
        throw new AssertionError("the overview covers the whole screen");
    }

    /**
     * Help opens on a handful of cards over the reader's own screen: the curated controls that
     * measured, each with its own card, and nothing else marked at all.
     */
    @Test public void theOverviewDrawsTheCuratedCardsAndNothingElse() {
        keyboardUp();
        overview(PaneWallPage.TERMINAL);
        assertTrue(overlay.isShowing());
        assertTrue("the overview marks nothing", overlay.markerTargetIds().isEmpty());
        assertNull("the overview has no exploration toolbar", overlay.toolbarBounds());
        assertNull("no single card is seated", overlay.cardBounds());
        assertEquals("nothing was left out", java.util.Collections.emptyList(),
            overlay.unplacedOverviewIds());
        java.util.SortedSet<String> curated = curatedOnScreen(PaneWallPage.TERMINAL);
        assertTrue("this test needs the keyboard's own controls", curated.contains("prefix"));
        assertTrue(curated.contains("settings"));
        assertEquals(curated, new java.util.TreeSet<>(overlay.overviewCardIds()));
        for (String id : overlay.overviewCardIds()) {
            HelpTopics.Entry entry = HelpTopics.forTarget(PaneWallPage.TERMINAL, id);
            assertNotNull(id + "'s card is not its topic",
                exactly(string(entry.titleRes) + "\n" + string(entry.actionRes)));
        }
        assertTheOverviewIsClear();
    }

    /** With the keyboard down its own controls are not there, and they get no card. */
    @Test public void theOverviewDropsTheCardsWhoseControlsAreNotOnScreen() {
        overview(PaneWallPage.TERMINAL);
        java.util.SortedSet<String> curated = curatedOnScreen(PaneWallPage.TERMINAL);
        assertFalse("the keyboard is down in this test", curated.contains("prefix"));
        assertEquals(curated, new java.util.TreeSet<>(overlay.overviewCardIds()));
        assertTrue(overlay.overviewCardIds().contains("dock"));
        assertTheOverviewIsClear();
    }

    /** Every place opens on its own overview, and every one of them stays on one page. */
    @Test public void everyPlaceHasAnOverviewOfItsOwn() {
        for (PaneWallPage place : PaneWallPage.values()) {
            keyboardUp();
            overview(place);
            assertTrue(place.name(), overlay.isShowing());
            assertEquals(place.name(), java.util.Collections.emptyList(),
                overlay.unplacedOverviewIds());
            assertEquals(place.name(), curatedOnScreen(place),
                new java.util.TreeSet<>(overlay.overviewCardIds()));
            assertNotNull(place.name(), described(string(R.string.help_close_action)));
            assertNotNull(place.name(), described(string(R.string.help_overview_guide_action)));
            assertTheOverviewIsClear();
            overlay.dismiss();
        }
    }

    @Test public void theOverviewsButtonsOpenTheGuideAndCloseHelp() {
        overview(PaneWallPage.TERMINAL);
        View guide = described(string(R.string.help_overview_guide_action));
        assertNotNull("no way into the guide", guide);
        assertTrue("the Guide button is too small for a thumb", guide.getHeight() >= 48);
        guide.performClick();
        assertEquals(1, openedGuide);
        described(string(R.string.help_close_action)).performClick();
        assertEquals(1, closed);
    }

    @Test public void aCardOnTheOverviewOpensItsTopic() {
        overview(PaneWallPage.TERMINAL);
        View card = overlay.overviewCardView("dock");
        assertNotNull(card);
        card.performClick();
        assertEquals("dock", read);
    }

    /** Back on the overview is the launcher's to answer: it is where help opens. */
    @Test public void backOnTheOverviewIsNotConsumed() {
        overview(PaneWallPage.TERMINAL);
        assertFalse(overlay.onBackPressed());
        assertTrue(overlay.isShowing());
        assertEquals(0, closed);
    }

    /** Nothing to put away on the overview, so the empty space around it is the way out. */
    @Test public void tappingEmptySpaceInTheOverviewClosesHelp() {
        overview(PaneWallPage.TERMINAL);
        float[] point = emptyPoint();
        tap(point[0], point[1]);
        assertNull(read);
        assertEquals(1, closed);
    }

    /** A finger that travelled was a swipe over the launcher, not a tap on the empty space. */
    @Test public void aFingerThatMovedIsNotAWayOutOfTheOverview() {
        overview(PaneWallPage.TERMINAL);
        float[] point = emptyPoint();
        swipe(point[0], point[1], point[0] + 300, point[1] + 300);
        assertTrue(overlay.isShowing());
        assertEquals(0, closed);
    }

    /** A dock that is a rail down one edge is swiped inward, and its card says so. */
    @Test public void aDockThatIsARailSaysSwipeInward() {
        overview(PaneWallPage.TERMINAL);
        assertNotNull(exactly(string(HelpTopics.entry("dock").titleRes) + "\n"
            + string(R.string.help_topic_dock_action)));
        // The wall off the left edge, and the dock a column beside it.
        wall.layout(80, 60, 400, 560);
        dock.layout(0, 100, 60, 500);
        overlay.refresh();
        layout();
        assertNotNull("the rail still has a card", overlay.overviewCardBounds("dock"));
        assertNotNull(exactly(string(HelpTopics.entry("dock").titleRes) + "\n"
            + string(R.string.help_dock_rail_action)));
    }

    @Test public void everyPlaceMarksItsControlsAndSeatsNoCard() {
        for (PaneWallPage place : PaneWallPage.values()) {
            explore(place);
            assertTrue(place.name(), overlay.isShowing());
            assertFalse(place.name() + " marked nothing", overlay.markerTargetIds().isEmpty());
            assertNull(place.name() + " opened with a card up", overlay.cardBounds());
            assertNull(place.name(), overlay.selectedTopicId());
            assertNotNull(place.name(), overlay.toolbarBounds());
            assertNotNull(place.name(), described(string(R.string.help_explore_back)));
            assertNotNull(place.name(), described(string(R.string.help_close_action)));
            assertNull(place.name() + " read something", exactly(string(R.string.help_explore_read)));
            overlay.dismiss();
        }
    }

    /** A marker carries its number and its control's name, and no two sit on each other. */
    @Test public void everyMarkerIsNumberedNamedAndClearOfTheOthers() {
        explore(PaneWallPage.TERMINAL);
        java.util.List<String> ids = overlay.markerTargetIds();
        assertTrue("the terminal marks more than one control", ids.size() > 1);
        java.util.List<Rect> seen = new java.util.ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            View marker = overlay.markerView(ids.get(i));
            assertNotNull(marker);
            assertEquals("marker " + i + " is not numbered", String.valueOf(i + 1),
                ((TextView) marker).getText().toString());
            HelpTopics.Entry entry = HelpTopics.forTarget(PaneWallPage.TERMINAL, ids.get(i));
            assertEquals("marker " + i + " is not named", string(entry.titleRes),
                marker.getContentDescription().toString());
            assertTrue("marker " + i + " is too small for a finger", marker.getWidth() >= 24);
            Rect bounds = new Rect(marker.getLeft(), marker.getTop(), marker.getRight(), marker.getBottom());
            for (Rect other : seen) assertFalse("two markers sit on each other",
                Rect.intersects(other, bounds));
            seen.add(bounds);
        }
    }

    @Test public void aMarkerTapSeatsOneCardClearOfEverything() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        assertEquals("status", overlay.selectedTopicId());
        assertClearOfEverything();
        HelpTopics.Entry entry = HelpTopics.entry("status");
        assertNotNull(exactly(string(entry.titleRes)));
        assertNotNull(exactly(string(entry.actionRes)));
        assertEquals("one card at a time", 1, countExactly(string(R.string.help_explore_read)));
    }

    @Test public void tappingTheControlItselfAlsoSeatsItsCard() {
        explore(PaneWallPage.TERMINAL);
        tap(300, 20);
        assertEquals("status", overlay.selectedTopicId());
        assertClearOfEverything();
    }

    @Test public void tappingAnotherControlSwitchesTheOneCard() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        assertEquals("status", overlay.selectedTopicId());
        tapMarker("dock");
        assertEquals("dock", overlay.selectedTopicId());
        assertEquals("one card at a time", 1, countExactly(string(R.string.help_explore_read)));
        assertClearOfEverything();
    }

    @Test public void tappingEmptySpaceLeavesTheScreenMarkedWithNoCard() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        assertNotNull(overlay.cardBounds());
        tap(200, 400);
        assertNull(overlay.cardBounds());
        assertNull(overlay.selectedTopicId());
        assertTrue("the first outside tap only puts the card away", overlay.isShowing());
        assertEquals(0, closed);
        assertFalse(overlay.markerTargetIds().isEmpty());
    }

    /** With no card up there is nothing left to put away, so the next tap outside closes help. */
    @Test public void aSecondTapOnEmptySpaceClosesHelp() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        tap(200, 400);
        assertEquals(0, closed);
        tap(200, 400);
        assertEquals(1, closed);
    }

    @Test public void backPutsTheCardAwayFirstAndThenHandsBackToHelp() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        assertTrue("back with a card up is consumed", overlay.onBackPressed());
        assertNull(overlay.cardBounds());
        assertTrue(overlay.isShowing());
        assertFalse("back with nothing selected is the launcher's", overlay.onBackPressed());
        assertEquals(0, backToHelp);
    }

    @Test public void readTopicHandsTheTopicOver() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        TextView readTopic = exactly(string(R.string.help_explore_read));
        assertNotNull(readTopic);
        assertTrue(readTopic.getHeight() >= 48);
        readTopic.performClick();
        assertEquals("status", read);
    }

    @Test public void theToolbarLeadsBackAndOut() {
        explore(PaneWallPage.WIDGETS);
        described(string(R.string.help_explore_back)).performClick();
        assertEquals(1, backToHelp);
        described(string(R.string.help_close_action)).performClick();
        assertEquals(1, closed);
    }

    /** The toolbar goes to the edge farthest from the control, so the card keeps the near one. */
    @Test public void theToolbarSitsAtTheEdgeFarthestFromTheControl() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        Rect high = overlay.toolbarBounds();
        assertTrue("a control at the top leaves the toolbar at the bottom", high.top > 400);
        tapMarker("dock");
        Rect low = overlay.toolbarBounds();
        assertTrue("a control at the bottom lifts the toolbar to the top", low.bottom < 400);
        assertClearOfEverything();
    }

    /**
     * A control that goes away stops the demonstration and names its topic; the highlight is never
     * moved to some other control.
     */
    @Test public void aVanishedControlIsSaidRatherThanRedirected() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("dock");
        assertEquals("dock", overlay.selectedTopicId());
        dock.setVisibility(View.GONE);
        overlay.refresh();
        layout();
        assertEquals("dock", gone);
        assertNull(overlay.selectedTopicId());
        assertNull(overlay.cardBounds());
        assertFalse(overlay.markerTargetIds().contains("dock"));
        assertTrue(overlay.isShowing());
    }

    /** Asking for a control this screen does not have is answered, not silently dropped. */
    @Test public void showOnScreenForAControlThatIsNotThereIsSaid() {
        dock.setVisibility(View.GONE);
        overlay.explore(PaneWallPage.TERMINAL, "dock");
        layout();
        overlay.refresh();
        layout();
        assertEquals("dock", gone);
        assertNull(overlay.cardBounds());
    }

    /** Too little room is an answer, not an excuse to shrink the text. */
    @Test public void aScreenWithNoSeatForTheCardSaysSo() {
        root.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 200, 200);
        wall.layout(0, 40, 200, 200);
        status.layout(0, 0, 200, 30);
        dock.setVisibility(View.GONE);
        overlay.layout(0, 0, 200, 200);
        overlay.explore(PaneWallPage.TERMINAL, null);
        overlay.refresh();
        tapMarker("status");
        assertEquals("status", noSeat);
        assertNull(overlay.cardBounds());
        assertNull(overlay.selectedTopicId());
    }

    /** The demonstration is the topic's own movement, and a rail is swiped inward off itself. */
    @Test public void aDemonstrationPlaysTheGestureTheTopicCarries() {
        overlay.demonstrate(PaneWallPage.TERMINAL, "dock");
        layout();
        overlay.refresh();
        layout();
        assertEquals("dock", overlay.selectedTopicId());
        assertTrue(overlay.isShowingGesture());
        assertEquals(TourGesture.DRAG_DOWN, overlay.playingGesture());
    }

    @Test public void aDockThatIsARailIsSwipedInwardInstead() {
        HelpTopics.Entry dockTopic = HelpTopics.entry("dock");
        assertEquals(TourGesture.DRAG_DOWN, dockTopic.gesture);
        explore(PaneWallPage.TERMINAL);
        assertEquals("a row along the bottom is still pulled down",
            TourGesture.DRAG_DOWN, overlay.gestureFor(dockTopic, new Rect(0, 600, 400, 700)));
        // The wall off the left edge, and the dock a column beside it: the same topic, and the
        // movement the copy names -- inward off the rail.
        wall.layout(80, 60, 400, 560);
        overlay.refresh();
        assertEquals(TourGesture.SWIPE_RIGHT,
            overlay.gestureFor(dockTopic, new Rect(0, 100, 60, 500)));
        wall.layout(0, 60, 320, 560);
        overlay.refresh();
        assertEquals(TourGesture.SWIPE_LEFT,
            overlay.gestureFor(dockTopic, new Rect(330, 100, 400, 500)));
    }

    @Test public void aTopicWithNoGesturePlaysNothingAndStillShowsItsCard() {
        HelpTopics.Entry corners = HelpTopics.entry("corners");
        assertNull("this test is about a topic with no gesture", corners.gesture);
        overlay.demonstrate(PaneWallPage.TERMINAL, "corners");
        layout();
        overlay.refresh();
        layout();
        assertEquals("corners", overlay.selectedTopicId());
        assertFalse(overlay.isShowingGesture());
        assertNotNull(overlay.cardBounds());
    }

    /**
     * A row of the launcher's own keys. Three of them, not the seven a fresh install ships with:
     * this harness is a 400x800 screen, and seven labels of real words need more room than that.
     * {@link #onlyTheLaunchersOwnKeysAreLabelled} is where the shipped row is checked.
     */
    private void launcherKeys(int height) throws Exception {
        keys = new ExtraKeysView(activity, null);
        root.addView(keys, new FrameLayout.LayoutParams(keysBounds.width(), keysBounds.height()));
        keys.reload(new ExtraKeysInfo("[['tool:wall.widgets','tool:wall.terminal','tool:mouse.toggle']]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES), height);
    }

    /**
     * The rule for what is labelled: the launcher's own keys, and no others. Every key of the row
     * a fresh install ships with is one; not one key of upstream Termux's row is.
     */
    @Test public void onlyTheLaunchersOwnKeysAreLabelled() throws Exception {
        ExtraKeysInfo launcher = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(7, launcher.getMatrix()[0].length);
        for (ExtraKeyButton key : launcher.getMatrix()[0])
            assertTrue(key.getKey() + " is the launcher's own", HelpCopy.isLauncherKey(key));
        ExtraKeysInfo upstream = new ExtraKeysInfo("[[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        for (ExtraKeyButton key : upstream.getMatrix()[0]) {
            assertFalse(key.getKey() + " sends what it says it sends", HelpCopy.isLauncherKey(key));
            assertFalse(HelpCopy.isLauncherKey(key.getPopup()));
        }
        assertFalse("nothing to filter", HelpCopy.isLauncherKey(null));
    }

    /** Upstream's row of plain terminal keys says nothing: there is nothing to explain. */
    @Test public void aRowOfPlainTerminalKeysIsNotLabelledAtAll() throws Exception {
        keys = new ExtraKeysView(activity, null);
        root.addView(keys, new FrameLayout.LayoutParams(400, 60));
        keys.reload(new ExtraKeysInfo("[[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES), 60);
        overview(PaneWallPage.TERMINAL);
        assertTrue("a plain terminal key was labelled", overlay.keyCardBounds().isEmpty());
        explore(PaneWallPage.TERMINAL);
        tapMarker("keys");
        assertTrue("a plain terminal key was labelled", overlay.keyCardBounds().isEmpty());
    }

    /** The overview labels the keys without being asked: the row is what the labels are about. */
    @Test public void theOverviewLabelsEveryLauncherKey() throws Exception {
        launcherKeys(60);
        overview(PaneWallPage.TERMINAL);
        assertEquals("one label per launcher key", 3, overlay.keyCardBounds().size());
        assertTheOverviewIsClear();
    }

    /**
     * The extra keys row is the one control that brings more than a card: one label per key, for
     * the reader's own key assignments, and only while the row itself is selected.
     */
    @Test public void theExtraKeysRowBringsALabelForEveryKey() throws Exception {
        launcherKeys(60);
        explore(PaneWallPage.TERMINAL);
        assertTrue("the row was not measured", overlay.markerTargetIds().contains("keys"));
        assertTrue("no key cards until the row is selected", overlay.keyCardBounds().isEmpty());
        tapMarker("keys");
        assertEquals("keys", overlay.selectedTopicId());
        assertEquals("one label per key", 3, overlay.keyCardBounds().size());
        assertClearOfEverything();
        for (Rect card : overlay.keyCardBounds())
            assertFalse("a key card is on the row it labels",
                Rect.intersects(card, overlay.measuredRect("keys")));
        tapMarker("status");
        assertTrue("the key cards went with the row", overlay.keyCardBounds().isEmpty());
    }

    /**
     * A row whose caps run down the middle of the screen: the lanes under it used to reach the
     * bottom edge, where the toolbar is, because only the measured controls were obstacles and the
     * toolbar is not one of them. It is an excluded region now, so the labels take the other side.
     */
    @Test public void keyCardsKeepOffTheToolbarWhenTheRowRunsDownTheMiddle() throws Exception {
        keys = new ExtraKeysView(activity, null);
        // A row of tall caps down the middle: its lanes used to reach the bottom edge, where the
        // toolbar is, because only the measured controls were obstacles.
        keysBounds = new Rect(0, 150, 200, 620);
        root.addView(keys, new FrameLayout.LayoutParams(keysBounds.width(), keysBounds.height()));
        keys.reload(new ExtraKeysInfo("[[{key:'tool:pane.split',popup:'tool:window.new'},"
            + "{key:'tool:session.browser',popup:'tool:session.new'}]]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES), keysBounds.height());
        dock.setVisibility(View.GONE);
        explore(PaneWallPage.TERMINAL);
        Rect row = overlay.measuredRect("keys");
        assertNotNull("the row was not measured", row);
        assertTrue("this test needs the row's centre near the middle of the screen",
            Math.abs(row.centerY() - 400) < 100);
        tapMarker("keys");
        assertEquals("keys", overlay.selectedTopicId());
        Rect toolbar = overlay.toolbarBounds();
        assertTrue("this test needs the toolbar at the bottom edge", toolbar.top > 400);
        assertEquals("one label per key", 2, overlay.keyCardBounds().size());
        for (Rect card : overlay.keyCardBounds())
            assertFalse("a key card is under the toolbar", Rect.intersects(card, toolbar));
        assertClearOfEverything();
    }

    /**
     * The lanes with and without the toolbar in the away limit, on the phone's own numbers: the
     * extra keys row low on the screen with the keyboard down (1850–1948 of 2280), and a toolbar
     * 110px tall along the bottom edge from 2162. Left out of the limit, the far lane lands under
     * it; in the limit, both lanes take the wall's side instead.
     */
    @Test public void theToolbarInTheAwayLimitIsWhatKeepsTheFarLaneOffIt() {
        int thickness = 110, gap = 27, leader = 38, toolbarTop = 2162, screen = 2280;
        int[] untrimmed = HelpOverlayView.keyCardLanes(1850, 1948, 232, screen - 8, true,
            thickness, gap, leader);
        assertEquals("the lanes went away from the wall", 1, untrimmed[2]);
        assertTrue("this is the lane the toolbar used to take",
            untrimmed[1] + thickness > toolbarTop);
        int[] trimmed = HelpOverlayView.keyCardLanes(1850, 1948, 232, toolbarTop - 6, true,
            thickness, gap, leader);
        assertEquals("the lanes take the wall's side instead", 0, trimmed[2]);
        assertTrue("the near lane is off the keys it names", trimmed[0] + thickness <= 1850);
        assertTrue("the far lane is on the wash", trimmed[1] >= 232);
    }

    /** The room along the keys' axis, once the toolbar has taken its end of it. */
    @Test public void theKeyCardSpanGivesWayToTheToolbarAtEitherEnd() {
        // A toolbar past the far end of the keys takes that end.
        int[] far = HelpOverlayView.keyCardSpan(12, 788, 200, 600, 723, 792, 6);
        assertEquals(12, far[0]);
        assertEquals(717, far[1]);
        // Past the near end, it takes that one.
        int[] near = HelpOverlayView.keyCardSpan(12, 788, 200, 600, 8, 77, 6);
        assertEquals(83, near[0]);
        assertEquals(788, near[1]);
        // Straddling the keys it changes nothing: the lanes across the axis keep clear of it, and
        // trimming here would cost every card its slot.
        int[] across = HelpOverlayView.keyCardSpan(12, 388, 0, 400, 20, 380, 6);
        assertEquals(12, across[0]);
        assertEquals(388, across[1]);
    }

    @Test public void noExtraKeyCardsUnlessTheExtraKeysRowIsTheSelectedControl() {
        explore(PaneWallPage.TERMINAL);
        assertTrue(overlay.keyCardBounds().isEmpty());
        tapMarker("status");
        assertTrue("the status bar has no key cards", overlay.keyCardBounds().isEmpty());
    }

    /** A control that only moved must not cost the screen its markers: rebuilding them is the flash. */
    @Test public void aRemeasureThatOnlyMovedAControlKeepsTheSameMarkers() {
        explore(PaneWallPage.WIDGETS);
        View before = overlay.markerView("status");
        assertNotNull(before);
        int children = overlay.getChildCount();
        status.layout(0, 6, 400, 46);
        overlay.refresh();
        assertSame(before, overlay.markerView("status"));
        assertSame(overlay, before.getParent());
        assertEquals(children, overlay.getChildCount());
    }

    @Test public void dismissTakesEverythingWithIt() {
        explore(PaneWallPage.TERMINAL);
        tapMarker("status");
        overlay.dismiss();
        assertFalse(overlay.isShowing());
        assertEquals(0, overlay.getChildCount());
        assertNull(overlay.cardBounds());
        assertTrue(overlay.markerTargetIds().isEmpty());
    }

    /**
     * One box for the window strip: the chips and the + are one control, and a chip the
     * measurement cannot see on its own is still inside the box drawn round the row.
     */
    @Test public void theWindowsBoxCoversTheChipsAndThePlus() {
        TerminalWindowBar bar = new TerminalWindowBar(activity, null);
        bar.setId(R.id.terminal_window_bar);
        root.addView(bar, new FrameLayout.LayoutParams(300, 40));
        bar.setWindows(java.util.Arrays.asList(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("zbook", "zbook")), 0);
        layout();
        bar.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        bar.layout(0, 100, 300, 140);
        ViewGroup strip = (ViewGroup) bar.chipStripView();
        View plus = bar.createWindowButtonView();
        assertNotNull(plus);
        Rect windows = null;
        for (HelpTargets.Target target : new HelpTargets(finder, overlay)
                .measure(PaneWallPage.TERMINAL).targets) {
            if ("windows".equals(target.id)) windows = target.rect;
        }
        assertNotNull(windows);
        for (int i = 0; i < strip.getChildCount(); i++) {
            View child = strip.getChildAt(i);
            if (child.getWidth() <= 0 || child.getHeight() <= 0) continue;
            assertTrue("chip " + i + " is outside the windows box", windows.contains(onOverlay(child)));
        }
        assertTrue(windows.contains(onOverlay(strip.getChildAt(0))));
        assertTrue(windows.contains(onOverlay(plus)));
    }

    private Rect onOverlay(View view) {
        int[] source = new int[2], origin = new int[2];
        view.getLocationOnScreen(source);
        overlay.getLocationOnScreen(origin);
        int left = source[0] - origin[0], top = source[1] - origin[1];
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }

    /**
     * One card per extra key, each in the room between its neighbours' keys: cards of one row
     * never meet, every key lies under its own card, and the lane over every key is left open for
     * the other row's leader.
     */
    @Test public void everyExtraKeyGetsACardNearestItsOwnKey() {
        int[] centres = {77, 231, 385, 539, 693, 847, 1001};
        int[][] slots = HelpOverlayView.keyCardSlots(centres, 33, 1047, 27);
        assertEquals(centres.length, slots.length);
        for (int i = 0; i < centres.length; i++) {
            assertTrue("card " + i + " has no room", slots[i][1] - slots[i][0] > 0);
            assertTrue("key " + i + " is not under its card",
                centres[i] > slots[i][0] && centres[i] < slots[i][1]);
            int nearest = 0;
            for (int j = 1; j < centres.length; j++) {
                if (Math.abs(centre(slots[j]) - centres[i])
                        < Math.abs(centre(slots[nearest]) - centres[i])) nearest = j;
            }
            assertEquals("key " + i + " is nearest another key's card", i, nearest);
        }
        for (int i = 0; i + 2 < centres.length; i++) {
            assertTrue("row cards " + i + " and " + (i + 2) + " meet", slots[i][1] < slots[i + 2][0]);
            assertTrue("no lane over key " + (i + 1),
                slots[i][1] < centres[i + 1] && centres[i + 1] < slots[i + 2][0]);
        }
    }
    private int centre(int[] slot) { return (slot[0] + slot[1]) / 2; }

    /**
     * The key cards take the keyboard when the keyboard has the room — on the phone this was
     * measured on the extra keys run 1631 to 1729 and the keyboard's own keys start no higher
     * than 2209 — and lie over the rows above the keys when it has not.
     */
    @Test public void theKeyCardsTakeTheKeyboardWhenItHasTheRoom() {
        int[] under = HelpOverlayView.keyCardRows(1631, 1729, 232, 2209, 110, 27, 38);
        assertEquals(1, under[2]);
        assertEquals(1767, under[0]);
        assertEquals(1904, under[1]);
        assertTrue("the rows meet", under[1] >= under[0] + 110);
        assertTrue("a row lies on the keyboard's own keys", under[1] + 110 <= 2209);
        assertTrue("a row lies on the keys it names", under[0] >= 1729);
        int[] over = HelpOverlayView.keyCardRows(1631, 1729, 232, 1830, 110, 27, 38);
        assertEquals(0, over[2]);
        assertTrue("a row lies on the keys it names", over[0] + 110 <= 1631);
        assertTrue("the rows meet", over[1] + 110 <= over[0]);
        assertTrue("a row is off the top of the wash", over[1] >= 232);
    }

    /**
     * Keys whose wall lies below them — a row along the top — ask the same question in a mirror:
     * the lanes go above the keys when there is room for two, and under them, toward the wall,
     * when there is not.
     */
    @Test public void theKeyCardLanesTurnWithTheKeys() {
        int[] toward = HelpOverlayView.keyCardLanes(100, 198, 600, 8, false, 110, 27, 38);
        assertEquals(0, toward[2]);
        assertEquals(198 + 38, toward[0]);
        assertEquals(198 + 38 + 110 + 27, toward[1]);
        int[] away = HelpOverlayView.keyCardLanes(600, 698, 900, 8, false, 110, 27, 38);
        assertEquals(1, away[2]);
        assertEquals(600 - 38 - 110, away[0]);
        assertEquals(600 - 38 - 110 - 27 - 110, away[1]);
        int[] under = HelpOverlayView.keyCardLanes(1631, 1729, 232, 2209, true, 110, 27, 38);
        assertEquals(1767, under[0]);
        assertEquals(1904, under[1]);
    }

    @Test public void configuredExtraKeyLabelsIncludeSecondaryAndPlainGlyph() throws Exception {
        ExtraKeysInfo keys = new ExtraKeysInfo("[[{key:'tool:pane.split',popup:'tool:window.new'},'LEFT']]",
            "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals("Split", HelpCopy.keyLabel(activity, keys.getMatrix()[0][0]));
        assertEquals("window", HelpCopy.keyLabel(activity, keys.getMatrix()[0][0].getPopup()));
        assertEquals(keys.getMatrix()[0][1].getDisplay(),
            HelpCopy.keyLabel(activity, keys.getMatrix()[0][1]));
    }

    @Test public void emptyWidgetRegionUsesGridMetrics() {
        WidgetGridView grid = new WidgetGridView(activity);
        grid.layout(0, 0, 400, 500);
        assertEquals(grid.metrics().contentBounds(), HelpTargets.largestEmptyRegion(grid));
        View occupied = new View(activity);
        grid.addView(occupied);
        occupied.layout(0, 0, 400, 500);
        assertNull(HelpTargets.largestEmptyRegion(grid));
    }
}
