package com.termux.app.help;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.wall.PaneWallPage;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Help read on a screen of its own: what an Intent opens it on, what Back means, what it hands the
 * launcher back, and the two things the sheet used to say about the screen behind it and no longer
 * can — "On this screen", and a topic refusing to read away from its own place.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w400dp-h800dp")
public class HelpActivityTest {

    private ActivityController<HelpActivity> controller;
    private HelpActivity activity;

    @After public void tearDown() {
        if (controller != null) controller.close();
    }

    private HelpActivity open(Intent intent) {
        controller = Robolectric.buildActivity(HelpActivity.class, intent).setup();
        activity = controller.get();
        return activity;
    }

    private Intent intent(@Nullable PaneWallPage place, @Nullable String topicId,
                          @Nullable Bundle navigation) {
        return HelpActivity.intent(org.robolectric.RuntimeEnvironment.getApplication(),
            place, topicId, navigation);
    }

    private String string(int res) {
        return activity.getString(res);
    }

    private void tap(String name) {
        View view = activity.panel().named(name);
        assertNotNull("no control named " + name, view);
        view.performClick();
    }

    private Intent result() {
        return Shadows.shadowOf(activity).getResultIntent();
    }

    private static HelpTopics.Entry topicWithTarget() {
        for (HelpTopics.Entry entry : HelpTopics.all())
            if (entry.targetId != null && !entry.relatedIds.isEmpty()) return entry;
        throw new AssertionError("no topic points at a control");
    }

    /** A topic of the Display group, which the Terminal place has no control for. */
    private static HelpTopics.Entry displayTopic() {
        for (HelpTopics.Entry entry : HelpTopics.inGroup(HelpTopics.Group.DISPLAY))
            if (!entry.places.contains(PaneWallPage.TERMINAL)) return entry;
        throw new AssertionError("every Display topic is on the Terminal place too");
    }

    // ---- what an Intent opens ----------------------------------------------------------------

    @Test public void noExtrasOpensHelpHome() {
        open(intent(null, null, null));
        assertEquals(HelpNavigation.Screen.HOME, activity.navigation().screen());
        assertEquals(string(R.string.help_centre_title), activity.title());
        assertTrue(activity.panel().pageText().contains(string(R.string.help_home_browse)));
        assertNotNull(activity.panel().named(string(R.string.help_home_glossary)));
    }

    @Test public void aTopicExtraOpensThatTopicPage() {
        HelpTopics.Entry entry = topicWithTarget();
        open(intent(PaneWallPage.TERMINAL, entry.id, null));
        assertEquals(HelpNavigation.Screen.TOPIC, activity.navigation().screen());
        assertEquals(entry.id, activity.navigation().id());
        String page = activity.panel().pageText();
        assertTrue(page.contains(string(entry.summaryRes)));
        // The toolbar carries the page's own title, not the centre's.
        assertEquals(string(entry.titleRes), activity.title());
    }

    /**
     * The bug this screen was built for: a Display topic opened while the reader was on the
     * Terminal place used to come back empty. The screen has no place to refuse from.
     */
    @Test public void aDisplayTopicReadsWhateverPlaceTheReaderCameFrom() {
        HelpTopics.Entry entry = displayTopic();
        open(intent(PaneWallPage.TERMINAL, entry.id, null));
        String page = activity.panel().pageText();
        assertFalse(page.contains(string(R.string.help_topic_unavailable)));
        assertTrue(page.contains(string(entry.summaryRes)));
        assertEquals(string(entry.titleRes), activity.title());
    }

    /** Nothing is measured here, so help home says nothing about what is on any screen. */
    @Test public void helpHomeHasNoOnThisScreenSection() {
        open(intent(PaneWallPage.TERMINAL, null, null));
        String page = activity.panel().pageText();
        for (int placeRes : new int[] { R.string.help_place_terminal, R.string.help_place_home,
                                        R.string.help_place_display }) {
            assertFalse("help home still has an On this screen section",
                page.contains(activity.getString(R.string.help_home_on_this_screen,
                    string(placeRes))));
        }
    }

    // ---- moving about ------------------------------------------------------------------------

    @Test public void backPopsOnePageAndFinishesAtTheRoot() {
        open(intent(PaneWallPage.TERMINAL, null, null));
        HelpTopics.Entry entry = HelpTopics.inGroup(HelpTopics.Group.FIND_YOUR_WAY).get(0);
        tap(string(entry.titleRes));
        assertEquals(HelpNavigation.Screen.TOPIC, activity.navigation().screen());

        activity.onBackPressed();
        assertEquals(HelpNavigation.Screen.HOME, activity.navigation().screen());
        assertFalse("help left on the first press", activity.isFinishing());

        activity.onBackPressed();
        assertTrue("Back at the root did not leave help", activity.isFinishing());
    }

    @Test public void theToolbarSearchActionOpensTheSearchPageWithAField() {
        open(intent(PaneWallPage.TERMINAL, null, null));
        MenuItem search = searchItem();
        assertTrue(activity.onOptionsItemSelected(search));
        assertEquals(HelpNavigation.Screen.SEARCH, activity.navigation().screen());
        EditText field = (EditText) activity.panel().named(string(R.string.help_search_field_hint));
        assertNotNull("the search page has no field", field);
        assertTrue(field.isFocused());
        // Typing filters the results; the system keyboard is the only one involved.
        field.setText("dock");
        assertEquals("dock", activity.navigation().query());
    }

    /** The Search action the toolbar shows, by the route the framework populates it. */
    private MenuItem searchItem() {
        activity.invalidateOptionsMenu();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        MenuItem item = activity.searchMenuItem();
        assertNotNull("the toolbar has no search action", item);
        assertEquals(string(R.string.help_search_field_hint), item.getTitle().toString());
        return item;
    }

    // ---- what it hands the launcher back -----------------------------------------------------

    @Test public void showOnScreenFinishesWithTheActionTheTopicAndThePageStack() {
        HelpTopics.Entry entry = topicWithTarget();
        open(intent(PaneWallPage.DISPLAY, entry.id, null));
        tap(string(R.string.help_show_on_screen));

        assertTrue(activity.isFinishing());
        Intent result = result();
        assertNotNull("the screen finished with no result", result);
        assertEquals(HelpActivity.ACTION_SHOW_ON_SCREEN,
            result.getStringExtra(HelpActivity.EXTRA_ACTION));
        assertEquals(entry.id, result.getStringExtra(HelpActivity.EXTRA_TOPIC));
        Bundle saved = result.getBundleExtra(HelpActivity.EXTRA_NAVIGATION);
        assertNotNull("the result carries no page stack", saved);

        // The stack in the result is the page the reader was on, on the place they came from.
        HelpNavigation restored = new HelpNavigation();
        assertTrue(restored.restoreState(saved));
        assertEquals(HelpNavigation.Screen.TOPIC, restored.screen());
        assertEquals(entry.id, restored.id());
        assertEquals(PaneWallPage.DISPLAY, restored.place());
    }

    @Test public void exploreFromHelpHomeFinishesWithTheExploreAction() {
        open(intent(PaneWallPage.TERMINAL, null, null));
        tap(string(R.string.help_home_explore));
        assertTrue(activity.isFinishing());
        Intent result = result();
        assertNotNull(result);
        assertEquals(HelpActivity.ACTION_EXPLORE, result.getStringExtra(HelpActivity.EXTRA_ACTION));
        assertNull(result.getStringExtra(HelpActivity.EXTRA_TOPIC));
        assertNotNull(result.getBundleExtra(HelpActivity.EXTRA_NAVIGATION));
    }

    @Test public void practiceFinishesWithTheLessonItIsAbout() {
        HelpTopics.Entry entry = null;
        for (HelpTopics.Entry candidate : HelpTopics.all())
            if (candidate.lessonId != null) { entry = candidate; break; }
        assertNotNull("no topic hands practice a lesson", entry);
        open(intent(PaneWallPage.TERMINAL, entry.id, null));
        tap(string(R.string.help_try_it));
        Intent result = result();
        assertNotNull(result);
        assertEquals(HelpActivity.ACTION_PRACTICE, result.getStringExtra(HelpActivity.EXTRA_ACTION));
        assertEquals(entry.id, result.getStringExtra(HelpActivity.EXTRA_TOPIC));
        assertEquals(entry.lessonId, result.getStringExtra(HelpActivity.EXTRA_LESSON));
    }

    // ---- coming back -------------------------------------------------------------------------

    @Test public void aNavigationExtraLandsTheReaderOnThePageTheyLeft() {
        HelpTopics.Entry entry = topicWithTarget();
        HelpNavigation left = new HelpNavigation();
        left.open(PaneWallPage.WIDGETS);
        left.topic(entry.id);
        left.setScroll(42);

        open(intent(PaneWallPage.TERMINAL, null, left.saveState()));
        assertEquals(HelpNavigation.Screen.TOPIC, activity.navigation().screen());
        assertEquals(entry.id, activity.navigation().id());
        assertEquals(42, activity.navigation().frame().scroll);
        // The place came with the stack, not from the Intent's own extra.
        assertEquals(PaneWallPage.WIDGETS, activity.navigation().place());
        assertTrue(activity.panel().pageText().contains(string(entry.summaryRes)));

        // The stack came back whole: Back has the home page under the topic.
        activity.onBackPressed();
        assertEquals(HelpNavigation.Screen.HOME, activity.navigation().screen());
        assertFalse(activity.isFinishing());
    }

    @Test public void theScreenKeepsItsPageAcrossRecreation() {
        HelpTopics.Entry entry = topicWithTarget();
        open(intent(PaneWallPage.TERMINAL, entry.id, null));
        Bundle state = new Bundle();
        controller.saveInstanceState(state);
        assertNotNull(state.getBundle("help_navigation"));

        controller = Robolectric.buildActivity(HelpActivity.class,
            intent(PaneWallPage.TERMINAL, null, null)).create(state).start().resume().visible();
        activity = controller.get();
        assertEquals(HelpNavigation.Screen.TOPIC, activity.navigation().screen());
        assertEquals(entry.id, activity.navigation().id());
    }
}
