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
        assertFirst(index, "lazy mode", PAGES + "PerformancePreferencesFragment", "lazy_mode");
        assertFirst(index, "low-memory preset", PAGES + "PerformancePreferencesFragment", "low_memory_preset");
        assertFirst(index, "12-hour", PAGES + "StatusBarPreferencesFragment", "top_pane_clock_am_pm");
        assertFirst(index, "wallpaper scrolls", PAGES + "HomePreferencesFragment", "wallpaper_parallax");
        assertFirst(index, "start in", PAGES + "HomePreferencesFragment", "app_launcher_start_in");
        assertFirst(index, "keyboard over the window", PAGES + "X11DisplayInputPreferencesFragment",
            "x11_keyboard_over_display");
        assertFirst(index, "linux apps", PAGES + "X11DisplayPreferencesFragment", null);
        assertFirst(index, "keyboard theme", PAGES + "KeyboardColorSchemeFragment", null);
    }

    @Test
    public void theSearchedFromSectionRanksFirstAmongEqualHits() {
        SettingsSearchIndex index = index();
        // "Appearance" is a row on Home, Terminal and Keyboard, and the root's door.
        List<SettingsSearchIndex.Entry> fromTerminal = index.search("appearance", SettingsTree.Section.TERMINAL, 40);
        List<SettingsSearchIndex.Entry> fromHome = index.search("appearance", SettingsTree.Section.HOME, 40);
        assertEquals(SettingsTree.Section.TERMINAL, firstRow(fromTerminal, "Appearance").section);
        assertEquals(SettingsTree.Section.HOME, firstRow(fromHome, "Appearance").section);
        assertTrue(index.search("   ", null, 40).isEmpty());
        assertFalse(index.search("vibration", null, 40).isEmpty());
    }

    /**
     * The old Look page's rows live in the Appearance editor now (spec §4). Each is still found by
     * its title, and the hit names the Appearance door, which opens the editor.
     */
    @Test
    public void everyFormerLookRowIsStillASearchHitThatOpensTheEditor() {
        SettingsSearchIndex index = index();
        String door = PAGES + "TermuxStylePreferencesFragment";
        assertTrue(SettingsTree.find(door).door);
        String[][] rows = {
            {"app theme", "theme_mode"},
            {"wallpaper colors", "terminal_dynamic_colors_enabled"},
            {"terminal contrast", "terminal_contrast_level"},
            {"tools that follow", "theme_templates_enabled"},
            {"wallpaper alignment", "wallpaper_render_zoom"},
            {"widgets on glass", "widgets_on_glass"},
            {"monochrome icons", "app_launcher_bw_icons"},
            {"icon pack", "app_launcher_icon_pack_package"},
            {"pinned-app icon pack", "app_launcher_pinned_icon_pack_package"},
        };
        for (String[] row : rows) assertHit(index, row[0], door, row[1]);
        assertHit(index, "wallpaper", door, "use_system_wallpaper");
        assertHit(index, "appearance", door, null);
    }

    private static void assertHit(SettingsSearchIndex index, String query, String page, String key) {
        for (SettingsSearchIndex.Entry hit : index.search(query, null, 40)) {
            if (hit.screenClass.equals(page) && (key == null ? hit.key == null : key.equals(hit.key))) return;
        }
        throw new AssertionError(query + " finds no " + key + " on " + page);
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
