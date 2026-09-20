package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import com.termux.app.wall.PaneWallPage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Help's controller: the corner overview, the explorer that runs over the live launcher, and the
 * hand-off to and from the reading screen. What the pages themselves say is
 * {@link HelpActivityTest}'s and {@link HelpPresentationTest}'s to check.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HelpControllerTest {

    /** The explorer phase C owns, as this side sees it. */
    private static final class FakeExplorer implements HelpController.Explorer {
        HelpController.ExploreListener listener;
        PaneWallPage place;
        String selected;
        boolean demonstrating;
        boolean overviewing;
        boolean showing;
        boolean consumeBack;

        @Override public void setExploreListener(HelpController.ExploreListener listener) {
            this.listener = listener;
        }

        @Override public void overview(PaneWallPage place) {
            this.place = place; this.selected = null; this.demonstrating = false;
            overviewing = true;
            showing = true;
        }

        @Override public void explore(PaneWallPage place, String selectTopicId) {
            this.place = place; this.selected = selectTopicId; this.demonstrating = false;
            overviewing = false;
            showing = true;
        }

        @Override public void demonstrate(PaneWallPage place, String topicId) {
            this.place = place; this.selected = topicId; this.demonstrating = true;
            overviewing = false;
            showing = true;
        }

        @Override public boolean isShowing() { return showing; }

        @Override public void dismiss() { showing = false; }

        @Override public boolean onBackPressed() { return consumeBack; }
    }

    private Activity activity;
    private HelpController controller;
    private FakeExplorer explorer;
    private final List<Boolean> visibility = new ArrayList<>();
    private final List<String> practised = new ArrayList<>();
    /** Every hand-off to the help screen, as "PLACE:topic". */
    private final List<String> screens = new ArrayList<>();

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);

        HelpTargets.ViewFinder finder = new HelpTargets.ViewFinder() {
            @Override public View findHelpView(int id) { return null; }
            @Override public View activePane() { return null; }
            @Override public int paneCount() { return 1; }
            @Override public boolean keyRectOnScreen(String name, Rect out) { return false; }
            @Override public boolean keyCornerRectOnScreen(String name, Rect out) { return false; }
        };
        explorer = new FakeExplorer();
        controller = new HelpController(activity, root, finder, new HelpController.Host() {
            @Override public void onHelpVisibilityChanged(boolean showing) { visibility.add(showing); }
            @Override public void openHelpScreen(PaneWallPage place, String topicId) {
                screens.add(place.name() + ":" + topicId);
            }
        }, explorer);
        controller.setPracticeListener(practised::add);
    }

    private HelpTopics.Entry lessonTopic() {
        for (HelpTopics.Entry entry : HelpTopics.all())
            if (entry.lessonId != null) return entry;
        throw new AssertionError("no topic hands practice a lesson");
    }

    /** The topic the hand-back tests explore with: one with a control of its own on Terminal. */
    private static HelpTopics.Entry targetTopic() {
        for (HelpTopics.Entry entry : HelpTopics.forPlace(PaneWallPage.TERMINAL))
            if (entry.targetId != null) return entry;
        throw new AssertionError("the Terminal place has no topic with a control");
    }

    // ---- opening -----------------------------------------------------------------------------

    /** Every entry point lands on the overview now; the whole guide waits behind its Guide button. */
    @Test public void openingLandsOnTheOverviewOverTheLiveLauncher() {
        controller.show(PaneWallPage.TERMINAL);
        assertTrue(controller.isShowing());
        assertTrue("help did not open on the overview", explorer.overviewing);
        assertTrue(explorer.isShowing());
        assertEquals(PaneWallPage.TERMINAL, explorer.place);
        assertNull("the overview selects nothing", explorer.selected);
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertEquals("[true]", visibility.toString());
    }

    /** Back on the overview leaves help: it is where help opens, so nothing is behind it. */
    @Test public void backFromTheOverviewClosesHelp() {
        controller.show(PaneWallPage.TERMINAL);
        explorer.consumeBack = false;
        assertTrue(controller.onBackPressed());
        assertFalse(controller.isShowing());
        assertFalse(explorer.isShowing());
        assertEquals("[true, false]", visibility.toString());
    }

    /** A card on the overview hands the reader straight to that topic on the screen. */
    @Test public void aCardOnTheOverviewOpensItsTopicOnTheScreen() {
        controller.show(PaneWallPage.TERMINAL);
        HelpTopics.Entry entry = HelpTopics.forTarget(PaneWallPage.TERMINAL, "dock");
        assertNotNull(entry);
        explorer.listener.onReadTopic(entry.id);
        assertFalse(explorer.isShowing());
        assertFalse(controller.isShowing());
        assertEquals("[TERMINAL:" + entry.id + "]", screens.toString());
    }

    /** The overview's Guide button leaves the launcher alone: the whole guide is its own screen. */
    @Test public void theGuideButtonOpensTheHelpScreen() {
        controller.show(PaneWallPage.TERMINAL);
        explorer.listener.onOpenGuide();
        assertEquals("[TERMINAL:null]", screens.toString());
        assertFalse("the overview went away with it", explorer.isShowing());
        assertFalse(controller.isShowing());
        assertEquals("[true, false]", visibility.toString());
    }

    /** A second invocation starts at home again, with no search carried over from the last one. */
    @Test public void openingAgainResetsToHomeWithNoSearchCarriedOver() {
        controller.show(PaneWallPage.DISPLAY);
        assertEquals(PaneWallPage.DISPLAY, controller.navigation().place());
        assertTrue(controller.dismiss());
        assertEquals("[true, false]", visibility.toString());

        controller.navigation().setQuery("dock");
        controller.show(PaneWallPage.WIDGETS);
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertEquals("", controller.navigation().query());
        assertEquals(PaneWallPage.WIDGETS, controller.navigation().place());
    }

    // ---- the explorer, entered from the overview ----------------------------------------------

    /** Settings, the palette or a corner tab while the explorer is up: one overlay at a time. */
    @Test public void openingHelpAgainWhileExploringStartsAtTheOverviewAgain() {
        controller.exploreFromHelpScreen(PaneWallPage.TERMINAL, null, false);
        assertTrue(explorer.isShowing());
        assertFalse(explorer.overviewing);

        controller.show(PaneWallPage.TERMINAL);
        assertTrue(explorer.overviewing);
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
    }

    @Test public void backWhileExploringGoesToTheExplorerFirst() {
        controller.exploreFromHelpScreen(PaneWallPage.TERMINAL, null, false);
        explorer.consumeBack = true;
        assertTrue(controller.onBackPressed());
        assertTrue(explorer.isShowing());
        explorer.consumeBack = false;
        assertTrue(controller.onBackPressed());
        assertFalse(explorer.isShowing());
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertEquals("[TERMINAL:null]", screens.toString());
    }

    // ---- what the help screen hands back -----------------------------------------------------

    /** "Show on screen", asked for on the help screen: the overlay, over the live launcher. */
    @Test public void showOnScreenFromTheScreenSelectsThatControl() {
        HelpTopics.Entry entry = targetTopic();
        controller.exploreFromHelpScreen(PaneWallPage.TERMINAL, entry.id, false);
        assertTrue(controller.isShowing());
        assertTrue(explorer.isShowing());
        assertFalse("this is not the overview", explorer.overviewing);
        assertEquals(entry.id, explorer.selected);
        assertFalse(explorer.demonstrating);
        assertEquals("[true]", visibility.toString());
        assertTrue("nothing was handed back yet", screens.isEmpty());
    }

    @Test public void showTheGestureFromTheScreenPlaysIt() {
        HelpTopics.Entry entry = targetTopic();
        controller.exploreFromHelpScreen(PaneWallPage.DISPLAY, entry.id, true);
        assertTrue(explorer.demonstrating);
        assertEquals(PaneWallPage.DISPLAY, explorer.place);
    }

    /** × on the overlay is done with the launcher, not with help: back to the page, once. */
    @Test public void closingTheOverlayHandsBackToTheScreenOnce() {
        controller.exploreFromHelpScreen(PaneWallPage.DISPLAY, null, false);
        explorer.listener.onCloseHelp();
        assertEquals("[DISPLAY:null]", screens.toString());
        assertFalse(controller.isShowing());
        assertFalse(explorer.isShowing());
        assertEquals("[true, false]", visibility.toString());

        // Every other way the explorer reports itself closed is the same one ask.
        explorer.listener.onCloseHelp();
        explorer.listener.onBackToHelp();
        explorer.listener.onReadTopic(targetTopic().id);
        assertEquals("[DISPLAY:null]", screens.toString());
    }

    /** Back out of an explore that the screen asked for: the page it asked from. */
    @Test public void backOutOfAScreenLaunchedExploreHandsBack() {
        controller.exploreFromHelpScreen(PaneWallPage.WIDGETS, null, false);
        explorer.consumeBack = false;
        assertTrue(controller.onBackPressed());
        assertEquals("[WIDGETS:null]", screens.toString());
        // The explore frame goes with it: the stack that is left is the reading stack.
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertEquals(1, controller.navigation().depth());
        // Nothing is up any more, so the next press is not help's to answer.
        assertFalse(controller.onBackPressed());
        assertEquals("[WIDGETS:null]", screens.toString());
    }

    /** A card that will not fit, or a reader who would rather read: that topic, on the screen. */
    @Test public void aTopicAskedForWhileExploringComesBackAsTheTopicToOpen() {
        HelpTopics.Entry entry = targetTopic();
        controller.exploreFromHelpScreen(PaneWallPage.TERMINAL, null, false);
        explorer.listener.onCardDoesNotFit(entry.id);
        assertEquals("[TERMINAL:" + entry.id + "]", screens.toString());
    }

    /** A control that vanished under a relayout lands on its topic too, not on another control. */
    @Test public void aControlThatVanishedIsReadInsteadToo() {
        HelpTopics.Entry entry = targetTopic();
        controller.exploreFromHelpScreen(PaneWallPage.TERMINAL, null, false);
        explorer.listener.onTargetGone(entry.id);
        assertEquals("[TERMINAL:" + entry.id + "]", screens.toString());
    }

    /** A lesson asked for on the screen runs on the launcher and ends back on that page. */
    @Test public void practiceFromTheScreenRunsTheLessonAndHandsBackWhenItEnds() {
        HelpTopics.Entry entry = lessonTopic();
        controller.practiseFromHelpScreen(PaneWallPage.TERMINAL, entry.id, entry.lessonId);
        assertEquals("[" + entry.lessonId + "]", practised.toString());
        assertFalse("help is down before the lesson starts", controller.isShowing());
        assertTrue(controller.isPracticing());
        assertTrue("nothing was handed back while the lesson runs", screens.isEmpty());

        controller.onPracticeEnded();
        assertEquals("[TERMINAL:" + entry.id + "]", screens.toString());
        // A second signal for the same lesson hands back nothing.
        controller.onPracticeEnded();
        assertEquals("[TERMINAL:" + entry.id + "]", screens.toString());
    }

    /** A lesson with no lesson id is not a lesson. */
    @Test public void practiceWithoutALessonDoesNothing() {
        controller.practiseFromHelpScreen(PaneWallPage.TERMINAL, lessonTopic().id, null);
        assertTrue(practised.isEmpty());
        assertFalse(controller.isPracticing());
    }

    /** Opening help again after a hand-back is a fresh invocation, and may hand back again. */
    @Test public void helpOpenedAgainMayHandBackAgain() {
        controller.show(PaneWallPage.TERMINAL);
        explorer.listener.onOpenGuide();
        explorer.listener.onOpenGuide();
        assertEquals("one ask per invocation", "[TERMINAL:null]", screens.toString());

        controller.show(PaneWallPage.WIDGETS);
        explorer.listener.onOpenGuide();
        assertEquals("[TERMINAL:null, WIDGETS:null]", screens.toString());
    }
}
