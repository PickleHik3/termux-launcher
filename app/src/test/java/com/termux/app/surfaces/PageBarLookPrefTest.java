package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.wall.pageswitch.PageBarLook;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Status bar card › Page bar: Glyphs or Marks, one global value. A new install is written Marks;
 * every existing install reads the absent key as Glyphs and keeps its window chips.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PageBarLookPrefTest {

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("page-bar-look-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private static PageBarLook look(TermuxAppSharedPreferences prefs) {
        return PageBarLook.fromValue(prefs.getPageBarLook());
    }

    @Test
    public void theEnumReadsItsValuesAndFallsBackToGlyphs() {
        assertEquals(PageBarLook.GLYPHS, PageBarLook.fromValue("glyphs"));
        assertEquals(PageBarLook.MARKS, PageBarLook.fromValue("marks"));
        assertEquals(PageBarLook.GLYPHS, PageBarLook.fromValue(null));
        assertEquals(PageBarLook.GLYPHS, PageBarLook.fromValue("elsewhere"));
        assertTrue(PageBarLook.MARKS.marksShown());
        assertFalse(PageBarLook.GLYPHS.marksShown());
        assertEquals(PageBarLook.DEFAULT.value, TERMUX_APP.DEFAULT_PAGE_BAR_LOOK);
    }

    @Test
    public void aFreshInstallWearsMarks() {
        boolean fresh = preferences.isFreshInstall();
        assertTrue(fresh);
        preferences.migrateLookGrid(java.util.Collections.emptyList(), fresh);
        assertEquals(PageBarLook.MARKS, look(preferences));
    }

    @Test
    public void anExistingInstallKeepsGlyphs() {
        // Any key but the log level means the app has been used.
        store.edit().putString("top_pane_clock_style", "flip").commit();
        boolean fresh = preferences.isFreshInstall();
        assertFalse(fresh);
        preferences.migrateLookGrid(java.util.Collections.emptyList(), fresh);
        assertEquals(PageBarLook.GLYPHS, look(preferences));
        assertFalse(store.contains(TERMUX_APP.KEY_PAGE_BAR_LOOK));
    }

    @Test
    public void theChoiceSurvivesARestart() {
        preferences.setPageBarLook(PageBarLook.MARKS.value);
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        TermuxAppSharedPreferences again = new TermuxAppSharedPreferences(context, store, null);
        assertEquals(PageBarLook.MARKS, look(again));
        again.setPageBarLook(PageBarLook.GLYPHS.value);
        assertEquals(PageBarLook.GLYPHS,
            look(new TermuxAppSharedPreferences(context, store, null)));
    }

    @Test
    public void theAppearanceResetTakesTheKeyAndTheCardResetShipsMarks() {
        assertTrue(AppearanceReset.inScope(TERMUX_APP.KEY_PAGE_BAR_LOOK));
        preferences.setPageBarLook(PageBarLook.GLYPHS.value);
        EditorCardReset.resetStore(preferences, EditorCard.STATUS,
            TermuxAppSharedPreferences.terminalPageId());
        assertEquals(PageBarLook.MARKS, look(preferences));
    }

    @Test
    public void cancellingTheEditorPutsTheChoiceBack() {
        preferences.setPageBarLook(PageBarLook.GLYPHS.value);
        AppearanceSnapshot opened = AppearanceSnapshot.capture(preferences);
        preferences.setPageBarLook(PageBarLook.MARKS.value);
        assertFalse("the draft differs from the store",
            opened.signature().equals(AppearanceSnapshot.signatureOf(preferences)));
        opened.restore(preferences);
        assertEquals(PageBarLook.GLYPHS, look(preferences));
    }
}
