package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** F1's index: every screen of the tree and its rows, ranked with the searched-from section first. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsSearchIndexTest {

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";

    private static SettingsSearchIndex index() {
        return SettingsSearchIndex.build(RuntimeEnvironment.getApplication());
    }

    @Test
    public void everyScreenIsIndexedAsAPageAndEveryXmlScreenByItsRows() {
        List<SettingsSearchIndex.Entry> entries = index().entries();
        Set<String> pages = new HashSet<>();
        Set<String> withRows = new HashSet<>();
        for (SettingsSearchIndex.Entry entry : entries) {
            if (entry.key == null && pages.add(entry.screenClass)) continue;
            withRows.add(entry.screenClass);
        }
        for (SettingsTree.Screen screen : SettingsTree.screens()) {
            assertTrue("page " + screen.className, pages.contains(screen.className));
            if (screen.xmlRes != 0) {
                assertTrue("rows of " + screen.className, withRows.contains(screen.className));
            }
        }
    }

    @Test
    public void everyIndexedRowLandsOnTheVeryPageThatHoldsIt() {
        // A row's link must not be sent elsewhere by the old-link redirect.
        for (SettingsSearchIndex.Entry entry : index().entries()) {
            if (entry.key == null) continue;
            assertEquals(entry.title + " (" + entry.key + ")", entry.screenClass,
                SettingsTree.redirect(entry.screenClass, entry.key));
        }
    }

    @Test
    public void rowsAreFoundOnThePagesTheyMovedTo() {
        SettingsSearchIndex index = index();
        assertFirst(index, "lazy mode", PAGES + "GeneralPreferencesFragment", "lazy_mode");
        assertFirst(index, "12-hour", PAGES + "StatusBarPreferencesFragment", "top_pane_clock_am_pm");
        assertFirst(index, "wallpaper scrolls", PAGES + "HomePreferencesFragment", "wallpaper_parallax");
        assertFirst(index, "start in", PAGES + "HomePreferencesFragment", "app_launcher_start_in");
        assertFirst(index, "linux apps", PAGES + "X11DisplayPreferencesFragment", null);
        assertFirst(index, "keyboard theme", PAGES + "KeyboardColorSchemeFragment", null);
    }

    @Test
    public void theSearchedFromSectionRanksFirstAmongEqualHits() {
        SettingsSearchIndex index = index();
        // "Layout" is a row on both Home and Terminal.
        List<SettingsSearchIndex.Entry> fromTerminal = index.search("layout", SettingsTree.Section.TERMINAL, 40);
        List<SettingsSearchIndex.Entry> fromHome = index.search("layout", SettingsTree.Section.HOME, 40);
        assertEquals(SettingsTree.Section.TERMINAL, firstRow(fromTerminal, "Layout").section);
        assertEquals(SettingsTree.Section.HOME, firstRow(fromHome, "Layout").section);
        assertTrue(index.search("   ", null, 40).isEmpty());
        assertFalse(index.search("vibration", null, 40).isEmpty());
    }

    private static SettingsSearchIndex.Entry firstRow(List<SettingsSearchIndex.Entry> hits, String title) {
        for (SettingsSearchIndex.Entry hit : hits) if (hit.title.equals(title)) return hit;
        throw new AssertionError("no " + title);
    }

    private static void assertFirst(SettingsSearchIndex index, String query, String page, String key) {
        List<SettingsSearchIndex.Entry> hits = index.search(query, null, 40);
        assertFalse(query, hits.isEmpty());
        assertEquals(query, page, hits.get(0).screenClass);
        assertEquals(query, key, hits.get(0).key);
    }
}
