package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.termux.R;
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

/** The reading panel and its controller: which page shows, what Back means, where practice goes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HelpControllerTest {

    /** The explorer phase C owns, as this side sees it. */
    private static final class FakeExplorer implements HelpController.Explorer {
        HelpController.ExploreListener listener;
        PaneWallPage place;
        String selected;
        boolean demonstrating;
        boolean showing;
        boolean consumeBack;

        @Override public void setExploreListener(HelpController.ExploreListener listener) {
            this.listener = listener;
        }

        @Override public void explore(PaneWallPage place, String selectTopicId) {
            this.place = place; this.selected = selectTopicId; this.demonstrating = false;
            showing = true;
        }

        @Override public void demonstrate(PaneWallPage place, String topicId) {
            this.place = place; this.selected = topicId; this.demonstrating = true;
            showing = true;
        }

        @Override public boolean isShowing() { return showing; }

        @Override public void dismiss() { showing = false; }

        @Override public boolean onBackPressed() { return consumeBack; }
    }

    private Activity activity;
    private FrameLayout root;
    private View wall;
    private View terminal;
    private HelpController controller;
    private FakeExplorer explorer;
    private final List<String> input = new ArrayList<>();
    private final List<Boolean> visibility = new ArrayList<>();
    private final List<String> practised = new ArrayList<>();
    private final List<KeyEvent> reachedTerminal = new ArrayList<>();

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        root = new FrameLayout(activity);
        activity.setContentView(root);
        wall = new View(activity);
        wall.setId(R.id.terminal_pane_wall);
        root.addView(wall, new FrameLayout.LayoutParams(400, 500));
        terminal = new View(activity);
        terminal.setOnKeyListener((v, code, event) -> { reachedTerminal.add(event); return true; });
        root.addView(terminal, new FrameLayout.LayoutParams(400, 500));

        HelpTargets.ViewFinder finder = new HelpTargets.ViewFinder() {
            @Override public View findHelpView(int id) {
                return id == android.R.id.content ? root : root.findViewById(id);
            }
            @Override public View activePane() { return wall; }
            @Override public int paneCount() { return 1; }
            @Override public boolean keyRectOnScreen(String name, Rect out) { return false; }
            @Override public boolean keyCornerRectOnScreen(String name, Rect out) { return false; }
        };
        explorer = new FakeExplorer();
        controller = new HelpController(activity, root, finder, new HelpController.Host() {
            @Override public void beginHelpTextInput(EditText field) { input.add("begin"); }
            @Override public void endHelpTextInput() { input.add("end"); }
            @Override public void onHelpVisibilityChanged(boolean showing) { visibility.add(showing); }
        }, explorer);
        controller.setPracticeListener(practised::add);
        layout();
    }

    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        wall.layout(0, 60, 400, 560);
    }

    private HelpPanelView panel() {
        return controller.panel();
    }

    private void tap(String name) {
        View view = panel().named(name);
        assertNotNull("no control named " + name, view);
        view.performClick();
    }

    private String string(int res) {
        return activity.getString(res);
    }

    /** Whether the Terminal place can show this topic at all; a page has to resolve its entry. */
    private static boolean onTerminal(HelpTopics.Entry entry) {
        return entry.targetId == null || entry.places.contains(PaneWallPage.TERMINAL);
    }

    /** A Terminal topic with a control this bare screen never measured, and a way to reveal it. */
    private HelpTopics.Entry hiddenTopic() {
        for (HelpTopics.Entry entry : HelpTopics.forPlace(PaneWallPage.TERMINAL)) {
            if (entry.targetId == null || entry.revealRes == 0) continue;
            if (!controller.measuredTargetIds().contains(entry.targetId)) return entry;
        }
        throw new AssertionError("every hidden topic's control was measured");
    }

    private HelpTopics.Entry lessonTopic() {
        for (HelpTopics.Entry entry : HelpTopics.all())
            if (entry.lessonId != null && onTerminal(entry)) return entry;
        throw new AssertionError("no topic hands practice a lesson");
    }

    private HelpTopics.Entry termTopic() {
        for (HelpTopics.Entry entry : HelpTopics.all())
            if (!entry.termIds.isEmpty() && onTerminal(entry)) return entry;
        throw new AssertionError("no topic leans on a glossary term");
    }

    private HelpTopics.Entry firstTerminalTopic() {
        for (HelpTopics.Entry entry : HelpTopics.all())
            if (onTerminal(entry)) return entry;
        throw new AssertionError("the Terminal place has no topic");
    }

    // ---- opening -----------------------------------------------------------------------------

    @Test public void openingLandsOnHelpHomeWithThisScreensTopics() {
        controller.show(PaneWallPage.TERMINAL);
        assertTrue(controller.isShowing());
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertEquals(PaneWallPage.TERMINAL, controller.navigation().place());
        String page = panel().pageText();
        assertTrue(page.contains(string(R.string.help_place_terminal)));
        assertTrue(page.contains(string(R.string.help_home_browse)));
        // Three to five topics for this screen, each one a row the reader can open.
        List<HelpTopics.Entry> here = HelpTopics.onScreen(PaneWallPage.TERMINAL,
            controller.measuredTargetIds());
        assertTrue(here.size() >= 3 && here.size() <= 5);
        for (HelpTopics.Entry entry : here)
            assertNotNull(panel().named(string(entry.titleRes)));
        assertEquals("[true]", visibility.toString());
    }

    @Test public void everyEntryPointOpensHomeAndCloseEndsIt() {
        controller.show(PaneWallPage.DISPLAY);
        assertEquals(PaneWallPage.DISPLAY, controller.navigation().place());
        tap(string(R.string.help_close_action));
        assertFalse(controller.isShowing());
        assertEquals("[true, false]", visibility.toString());
        // A second invocation starts at home again, with no search carried over.
        controller.navigation().setQuery("dock");
        controller.show(PaneWallPage.WIDGETS);
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertEquals("", controller.navigation().query());
    }

    @Test public void learnMoreOpensTheTopicAndBackLeavesHelp() {
        HelpTopics.Entry entry = firstTerminalTopic();
        controller.showTopic(PaneWallPage.TERMINAL, entry.id);
        assertEquals(HelpNavigation.Screen.TOPIC, controller.navigation().screen());
        assertEquals(entry.id, controller.navigation().id());
        assertTrue(panel().pageText().contains(string(entry.summaryRes)));
        assertTrue(controller.onBackPressed());
        assertFalse(controller.isShowing());
    }

    @Test public void aTopicRowOpensThatTopic() {
        controller.show(PaneWallPage.TERMINAL);
        HelpTopics.Entry entry = HelpTopics.onScreen(PaneWallPage.TERMINAL,
            controller.measuredTargetIds()).get(0);
        tap(string(entry.titleRes));
        assertEquals(HelpNavigation.Screen.TOPIC, controller.navigation().screen());
        assertEquals(entry.id, controller.navigation().id());
    }

    // ---- back --------------------------------------------------------------------------------

    @Test public void backClosesADefinitionThenTextEntryThenThePageThenHelp() {
        controller.show(PaneWallPage.TERMINAL);
        tap(string(R.string.help_search_field_hint));
        assertEquals(HelpNavigation.Screen.SEARCH, controller.navigation().screen());

        // Text entry: the field asks for the configured keyboard through the host.
        EditText field = (EditText) panel().named(string(R.string.help_search_field_hint));
        assertNotNull(field);
        field.requestFocus();
        assertTrue(controller.navigation().textEntryActive());
        assertEquals("[begin]", input.toString());

        // One press dismisses the keyboard, the next the page, the next help itself.
        assertTrue(controller.onBackPressed());
        assertFalse(controller.navigation().textEntryActive());
        assertEquals("[begin, end]", input.toString());
        assertTrue(controller.isShowing());
        assertTrue(controller.onBackPressed());
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertTrue(controller.onBackPressed());
        assertFalse(controller.isShowing());
    }

    @Test public void backCollapsesAnInlineDefinitionFirst() {
        HelpTopics.Entry entry = termTopic();
        controller.showTopic(PaneWallPage.TERMINAL, entry.id);
        HelpGlossary.Term term = HelpGlossary.term(entry.termIds.get(0));
        assertNotNull(term);
        tap(string(term.titleRes));
        assertEquals(term.id, controller.navigation().frame().openTermId);
        assertTrue(panel().pageText().contains(string(term.definitionRes)));
        assertTrue(controller.onBackPressed());
        assertNull(controller.navigation().frame().openTermId);
        assertEquals(HelpNavigation.Screen.TOPIC, controller.navigation().screen());
    }

    // ---- the explorer ------------------------------------------------------------------------

    @Test public void exploreHandsOverAndComesBackThroughItsListener() {
        controller.show(PaneWallPage.TERMINAL);
        tap(string(R.string.help_home_explore));
        assertTrue(explorer.isShowing());
        assertEquals(PaneWallPage.TERMINAL, explorer.place);
        assertNull(explorer.selected);
        assertFalse(panel().isShowing());
        assertTrue(controller.isShowing());

        // "Read topic" on the seated card.
        HelpTopics.Entry entry = HelpTopics.forPlace(PaneWallPage.TERMINAL).get(0);
        explorer.listener.onReadTopic(entry.id);
        assertFalse(explorer.isShowing());
        assertTrue(panel().isShowing());
        assertEquals(HelpNavigation.Screen.TOPIC, controller.navigation().screen());
        assertEquals(entry.id, controller.navigation().id());

        // Back to help, and close help. Back off the topic page first: only home explores.
        assertTrue(controller.onBackPressed());
        tap(string(R.string.help_home_explore));
        explorer.listener.onBackToHelp();
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
        assertTrue(panel().isShowing());
        tap(string(R.string.help_home_explore));
        explorer.listener.onCloseHelp();
        assertFalse(explorer.isShowing());
        assertFalse(controller.isShowing());
    }

    @Test public void aCardThatCannotBeSeatedIsReadInstead() {
        controller.show(PaneWallPage.TERMINAL);
        HelpTopics.Entry entry = HelpTopics.forPlace(PaneWallPage.TERMINAL).get(0);
        tap(string(R.string.help_home_explore));
        explorer.listener.onCardDoesNotFit(entry.id);
        assertEquals(HelpNavigation.Screen.TOPIC, controller.navigation().screen());
        assertEquals(entry.id, controller.navigation().id());
        assertTrue(panel().isShowing());

        // A control that vanished under a relayout lands on its topic too, not on another control.
        assertTrue(controller.onBackPressed());
        tap(string(R.string.help_home_explore));
        explorer.listener.onTargetGone(entry.id);
        assertEquals(entry.id, controller.navigation().id());
    }

    @Test public void backWhileExploringGoesToTheExplorerFirst() {
        controller.show(PaneWallPage.TERMINAL);
        tap(string(R.string.help_home_explore));
        explorer.consumeBack = true;
        assertTrue(controller.onBackPressed());
        assertTrue(explorer.isShowing());
        explorer.consumeBack = false;
        assertTrue(controller.onBackPressed());
        assertFalse(explorer.isShowing());
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
    }

    // ---- a hidden control -------------------------------------------------------------------

    @Test public void aHiddenTargetStillReadsAndSaysHowToBringItBack() {
        controller.show(PaneWallPage.TERMINAL);
        HelpTopics.Entry entry = hiddenTopic();
        controller.showTopic(PaneWallPage.TERMINAL, entry.id);
        String page = panel().pageText();
        assertTrue(page.contains(string(entry.summaryRes)));
        assertTrue(page.contains(string(R.string.help_topic_not_visible)));
        assertTrue(page.contains(string(entry.revealRes)));
        // Nothing offers to show a control that is not there.
        assertNull(panel().named(string(R.string.help_show_on_screen)));
        assertNull(panel().named(string(R.string.help_show_gesture)));
    }

    // ---- practice ----------------------------------------------------------------------------

    @Test public void practiceClosesHelpAndComesBackToWhereItStarted() {
        HelpTopics.Entry entry = lessonTopic();
        controller.setPracticeAvailable(true);
        controller.showTopic(PaneWallPage.TERMINAL, entry.id);
        tap(string(R.string.help_try_it));
        assertEquals("[" + entry.lessonId + "]", practised.toString());
        assertFalse("help is down before the lesson starts", controller.isShowing());
        assertTrue(controller.isPracticing());

        controller.onPracticeEnded();
        assertTrue(controller.isShowing());
        assertEquals(HelpNavigation.Screen.TOPIC, controller.navigation().screen());
        assertEquals(entry.id, controller.navigation().id());
    }

    @Test public void practiceIsNotOfferedWhileARunHoldsIt() {
        HelpTopics.Entry entry = lessonTopic();
        controller.setPracticeAvailable(false);
        controller.showTopic(PaneWallPage.TERMINAL, entry.id);
        assertNull(panel().named(string(R.string.help_try_it)));
        assertTrue(practised.isEmpty());
    }

    @Test public void helpHomeListsTheFourLessons() {
        controller.show(PaneWallPage.TERMINAL);
        tap(string(R.string.help_home_practice));
        assertNotNull(panel().named(string(R.string.help_lesson_find_help_title)));
        assertNotNull(panel().named(string(R.string.help_lesson_keyboard_title)));
        tap(string(R.string.help_lesson_keyboard_title));
        assertEquals("[" + HelpTopics.LESSON_KEYBOARD + "]", practised.toString());
    }

    // ---- search and the glossary -------------------------------------------------------------

    @Test public void searchRanksAndOpensButNeverReachesTheTerminal() {
        controller.show(PaneWallPage.TERMINAL);
        tap(string(R.string.help_search_field_hint));
        EditText field = (EditText) panel().named(string(R.string.help_search_field_hint));
        assertNotNull(field);
        field.setText("dock");
        assertEquals("dock", controller.navigation().query());
        List<HelpSearch.Result> expected = HelpSearch.search("dock", PaneWallPage.TERMINAL,
            HelpSearch.text(activity));
        assertFalse(expected.isEmpty());
        assertNotNull(panel().named(expected.get(0).title));

        // Nothing typed into the panel leaves it; Back is the launcher's to route.
        KeyEvent typed = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
        panel().dispatchKeyEvent(typed);
        assertTrue(reachedTerminal.isEmpty());
        assertTrue(panel().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D)));
        assertFalse(panel().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
        assertTrue(reachedTerminal.isEmpty());
    }

    @Test public void anEmptySearchSaysSoAndOffersAWayOn() {
        controller.show(PaneWallPage.TERMINAL);
        tap(string(R.string.help_search_field_hint));
        EditText field = (EditText) panel().named(string(R.string.help_search_field_hint));
        field.setText("zzzzqqq");
        String page = panel().pageText();
        assertTrue(page.contains(string(R.string.help_search_empty)));
        assertNotNull(panel().named(string(R.string.help_home_browse)));
        assertNotNull(panel().named(string(R.string.help_support_link)));
        tap(string(R.string.help_home_browse));
        assertEquals(HelpNavigation.Screen.HOME, controller.navigation().screen());
    }

    @Test public void theGlossaryIsAReachablePageOfItsOwn() {
        controller.show(PaneWallPage.TERMINAL);
        tap(string(R.string.help_home_glossary));
        assertEquals(HelpNavigation.Screen.GLOSSARY, controller.navigation().screen());
        String page = panel().pageText();
        for (HelpGlossary.Term term : HelpGlossary.all())
            assertTrue(page.contains(string(term.titleRes)));
        assertNotNull(panel().named(string(R.string.help_glossary_filter_hint)));
    }
}
