package com.termux.app.fragments.settings.terl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;

/** The search built from Terl's tree reaches every row Terl shows, and nothing that retired. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerlSettingsIndexTest {

    private final Context context = RuntimeEnvironment.getApplication();

    private TerlSettingsSearch.Entry first(TerlSettingsSearch search, String query) {
        List<TerlSettingsSearch.Entry> hits = search.search(query, 5);
        assertFalse("no hit for " + query, hits.isEmpty());
        return hits.get(0);
    }

    @Test
    public void everyShownSettingRowAndLinkIsFound() {
        TerlSettingsSearch search = TerlSettingsIndex.build(context);
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            if (!TerlSettings.shows(context, group)) continue;
            boolean groupFound = false;
            for (TerlSettingsSearch.Entry entry : search.entries()) {
                groupFound |= entry.kind == TerlSettingsSearch.Kind.GROUP && entry.group == group;
            }
            assertTrue(group + " is not found", groupFound);
            for (TerlSettingsTree.Item item : TerlSettingsTree.items(group)) {
                if (item.kind == TerlSettingsTree.Item.Kind.HEADING) continue;
                if (item.kind == TerlSettingsTree.Item.Kind.SETTING && !TerlSettings.has(context, item.need))
                    continue;
                boolean found = false;
                for (TerlSettingsSearch.Entry entry : search.entries()) {
                    found |= item.id != null ? item.id.equals(entry.id)
                        : entry.kind == TerlSettingsSearch.Kind.LINK && item.page.equals(entry.page);
                }
                assertTrue(item.key() + " is not found", found);
            }
        }
    }

    @Test
    public void underTerminalEveryHomeHitCarriesTheHomeIsOffNote() {
        TerlSettings.registry(context).set(com.termux.app.settings.UseAsAdapter.ID,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.USE_AS_TERMINAL);
        int homeHits = 0;
        for (TerlSettingsSearch.Entry entry : TerlSettingsIndex.build(context).entries()) {
            if (entry.group != TerlSettingsTree.Group.HOME) continue;
            homeHits++;
            assertTrue(entry.title + ": " + entry.breadcrumb, entry.breadcrumb.endsWith("Home is off"));
        }
        assertTrue(homeHits > 0);
    }

    @Test
    public void aSettingIsFoundByItsTitleInItsGroup() {
        TerlSettingsSearch.Entry hit = first(TerlSettingsIndex.build(context), "scrollback");
        assertEquals(SettingDeclarations.TERMINAL_SCROLLBACK.id, hit.id);
        assertEquals(TerlSettingsTree.Group.WORKSPACES, hit.group);
    }

    @Test
    public void theAssistantsOwnPagesAreFoundUnderIt() {
        TerlSettingsSearch search = TerlSettingsIndex.build(context);
        TerlSettingsSearch.Entry readAloud = null;
        for (TerlSettingsSearch.Entry entry : search.search("read aloud", 10)) {
            if (entry.page != null && entry.page.endsWith("ReadAloudPreferencesFragment")) readAloud = entry;
        }
        assertNotNull(readAloud);
        assertEquals(TerlSettingsTree.Group.ASSISTANT, readAloud.group);
        assertTrue(readAloud.breadcrumb, readAloud.breadcrumb.startsWith("Assistant"));
    }

    @Test
    public void retiredRowsAreNotFound() {
        TerlSettingsSearch search = TerlSettingsIndex.build(context);
        for (String retired : new String[] {"Page transition", "Dock style", "Pinned notifications",
                "Adaptive touch accuracy", "Battery in the status bar"}) {
            for (TerlSettingsSearch.Entry entry : search.search(retired, 40)) {
                assertFalse(retired + " found as " + entry.title + " in " + entry.breadcrumb,
                    entry.title.equalsIgnoreCase(retired));
            }
        }
    }

    @Test
    public void opacityFindsTheBarsKeyboardsAndWindowsControlsEachWithItsCard() {
        List<TerlSettingsSearch.Entry> hits = TerlSettingsIndex.build(context).search("opacity", 40);
        java.util.Map<String, String> cards = new java.util.HashMap<>();
        for (TerlSettingsSearch.Entry hit : hits) {
            if (hit.kind == TerlSettingsSearch.Kind.EDIT_MODE) cards.put(hit.id, hit.card);
        }
        assertEquals("BAR", cards.get("look.control-bar.opacity"));
        assertEquals("KEYBOARD", cards.get("look.keyboard.opacity"));
        assertEquals("WINDOWS", cards.get("look.windows.opacity"));
        for (TerlSettingsSearch.Entry hit : hits) {
            if (hit.kind != TerlSettingsSearch.Kind.EDIT_MODE) continue;
            assertTrue(hit.breadcrumb, hit.breadcrumb.startsWith("Edit mode"));
            // A hit opens the launcher's edit mode on that card, through the one door.
            assertEquals(com.termux.app.editmode.EditModeController.Card.valueOf(hit.card),
                com.termux.app.editmode.EditModeControls.cardOf(hit.id));
        }
    }

    @Test
    public void underTerminalAHomeRowIsStillFoundWithItsNote() {
        TerlSettings.registry(context).set(com.termux.app.settings.UseAsAdapter.ID,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.USE_AS_TERMINAL);
        TerlSettingsSearch search = TerlSettingsIndex.build(context);
        TerlSettingsSearch.Entry home = first(search, "home");
        assertTrue(home.title + ": " + home.breadcrumb, home.breadcrumb.endsWith("Home is off"));
    }
}
