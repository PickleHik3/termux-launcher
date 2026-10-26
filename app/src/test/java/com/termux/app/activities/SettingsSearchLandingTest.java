package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.widget.EditText;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsRowRing;
import com.termux.app.fragments.settings.SettingsSearchFragment;
import com.termux.app.fragments.settings.SettingsSearchIndex;
import com.termux.app.fragments.settings.SettingsSearchPreference;
import com.termux.app.fragments.settings.SettingsTree;
import com.termux.app.fragments.settings.termux.HomePreferencesFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * F1 end to end: the keyboard's search key opens the first result, which lands on its page in the
 * tree (Back walks up through the section), scrolled to the row and ringing it once; every other
 * screen reaches search from the toolbar, scoped to its section.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsSearchLandingTest {

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";

    private static SettingsActivity open(Intent intent) {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class, intent)
            .create().start().resume().visible().get();
        idle(activity);
        return activity;
    }

    private static void idle(SettingsActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Fragment shown(SettingsActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.settings);
    }

    @Test
    public void enterOnTheRootOpensTheFirstResultLandedOnItsRow() {
        SettingsActivity activity = open(new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class));
        SettingsActivity.RootPreferencesFragment root = (SettingsActivity.RootPreferencesFragment) shown(activity);
        SettingsSearchPreference search = root.findPreference("settings_search");
        SettingsRowRing.sLastRungKey = null;

        search.getOnQueryChangedListener().onQueryChanged("12-hour");
        SettingsSearchIndex.Entry first = root.searchResults().results().get(0);
        assertEquals("top_pane_clock_am_pm", first.key);
        search.getOnQuerySubmitListener().onQuerySubmit("12-hour");
        idle(activity);

        assertEquals(PAGES + "StatusBarPreferencesFragment", shown(activity).getClass().getName());
        FragmentManager fm = activity.getSupportFragmentManager();
        assertEquals("Terminal, then Status bar", 2, fm.getBackStackEntryCount());
        assertEquals("the row rang once", "top_pane_clock_am_pm", SettingsRowRing.sLastRungKey);

        // Back walks up the tree, and the page does not ring again on the way.
        SettingsRowRing.sLastRungKey = null;
        fm.popBackStackImmediate();
        idle(activity);
        assertEquals(PAGES + "TerminalPreferencesFragment", shown(activity).getClass().getName());
        assertEquals(null, SettingsRowRing.sLastRungKey);
    }

    /** The search is spent by the result it opened: Back finds the root's own rows, not the query. */
    @Test
    public void backFromAResultFindsTheRootWithoutTheQuery() {
        SettingsActivity activity = open(new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class));
        SettingsActivity.RootPreferencesFragment root = (SettingsActivity.RootPreferencesFragment) shown(activity);
        EditText field = root.getView().findViewById(R.id.settings_search_input);
        field.setText("12-hour");
        assertFalse(root.searchResults().results().isEmpty());

        SettingsSearchPreference search = root.findPreference("settings_search");
        search.getOnQuerySubmitListener().onQuerySubmit("12-hour");
        idle(activity);
        FragmentManager fm = activity.getSupportFragmentManager();
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        idle(activity);

        assertTrue(shown(activity) instanceof SettingsActivity.RootPreferencesFragment);
        SettingsActivity.RootPreferencesFragment back = (SettingsActivity.RootPreferencesFragment) shown(activity);
        EditText again = back.getView().findViewById(R.id.settings_search_input);
        assertEquals("", again.getText().toString());
        assertTrue(back.searchResults().results().isEmpty());
        assertEquals(null, back.findPreference("settings_search_results"));
    }

    /**
     * A page fades through as one: its rows are laid out after the move captures its views, so a
     * page the transition met row by row let the arriving rows draw over the leaving ones.
     */
    @Test
    public void aPageIsOneTargetForItsTransition() {
        SettingsActivity activity = open(new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class));
        assertTrue(androidx.core.view.ViewGroupCompat.isTransitionGroup(
            (android.view.ViewGroup) shown(activity).getView()));
        SettingsSearchPreference search = ((SettingsActivity.RootPreferencesFragment) shown(activity))
            .findPreference("settings_search");
        search.getOnQueryChangedListener().onQueryChanged("12-hour");
        search.getOnQuerySubmitListener().onQuerySubmit("12-hour");
        idle(activity);
        assertTrue(androidx.core.view.ViewGroupCompat.isTransitionGroup(
            (android.view.ViewGroup) shown(activity).getView()));
    }

    @Test
    public void everyOtherScreenSearchesFromTheToolbarScopedToItsSection() {
        Intent home = SettingsActivity.createFragmentIntent(RuntimeEnvironment.getApplication(),
            HomePreferencesFragment.class, R.string.settings_section_home);
        SettingsActivity activity = open(home);
        assertTrue("a section page offers search", activity.showsSearchAction());

        activity.openSearch();
        idle(activity);
        Fragment searchScreen = shown(activity);
        assertTrue(searchScreen instanceof SettingsSearchFragment);
        assertFalse("not twice", activity.showsSearchAction());
        EditText field = searchScreen.getView().findViewById(R.id.settings_search_input);
        assertNotNull(field);
        assertEquals("Search Home", String.valueOf(field.getHint()));

        SettingsSearchFragment search = (SettingsSearchFragment) searchScreen;
        SettingsSearchPreference box = search.findPreference("settings_search");
        box.getOnQueryChangedListener().onQueryChanged("edit on screen");
        SettingsSearchIndex.Entry layoutRow = null;
        for (SettingsSearchIndex.Entry entry : search.results().results()) {
            if ("Edit on screen".equals(entry.title)) { layoutRow = entry; break; }
        }
        assertNotNull(layoutRow);
        assertEquals("Home's own Edit on screen row ranks before the others", SettingsTree.Section.HOME,
            layoutRow.section);
    }

    @Test
    public void theRootOffersNoToolbarSearch() {
        SettingsActivity activity = open(new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class));
        assertFalse(activity.showsSearchAction());
    }
}
