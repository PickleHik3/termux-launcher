package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.editmode.EditModeController;
import com.termux.app.editmode.EditModeDoor;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditModeSearchTest {

    private static final Map<Integer, String> STRINGS = new HashMap<>();

    static {
        STRINGS.put(R.string.edit_mode_title, "Edit mode");
        STRINGS.put(R.string.edit_mode_search_summary, "Change how everything looks");
        STRINGS.put(R.string.title_activity_termux_settings, "Settings");
        STRINGS.put(R.string.edit_mode_keyboard_opacity, "Keyboard opacity");
        STRINGS.put(R.string.edit_mode_keyboard_blur, "Keyboard blur");
        STRINGS.put(R.string.edit_mode_bar_opacity, "Control bar opacity");
        STRINGS.put(R.string.edit_mode_bar_blur, "Control bar blur");
        STRINGS.put(R.string.edit_mode_windows_opacity, "Windows opacity");
        STRINGS.put(R.string.edit_mode_windows_blur, "Windows blur");
        STRINGS.put(R.string.edit_mode_card_keyboard, "Keyboard");
        STRINGS.put(R.string.edit_mode_card_control_bar, "Control bar");
        STRINGS.put(R.string.edit_mode_card_windows, "Windows");
        STRINGS.put(R.string.edit_mode_card_global, "All glass");
        STRINGS.put(R.string.edit_mode_look, "Look");
        STRINGS.put(R.string.edit_mode_corners, "Corners");
        STRINGS.put(R.string.edit_mode_tint, "Tint colour");
    }

    private static List<SettingsSearchIndex.Entry> search(String query) {
        return new SettingsSearchIndex(EditModeSearch.entries(STRINGS::get)).search(query, null, 10);
    }

    @Test
    public void searchingOpacityOpensTheKeyboardBarAndWindowsCards() {
        List<SettingsSearchIndex.Entry> hits = search("opacity");
        assertEquals(3, hits.size());
        Map<String, EditModeController.Card> want = new HashMap<>();
        want.put("Keyboard opacity", EditModeController.Card.KEYBOARD);
        want.put("Control bar opacity", EditModeController.Card.BAR);
        want.put("Windows opacity", EditModeController.Card.WINDOWS);
        for (SettingsSearchIndex.Entry hit : hits) {
            assertTrue(EditModeDoor.isDoor(hit.screenClass));
            assertEquals(hit.title, want.get(hit.title),
                EditModeDoor.cardOf(EditModeDoor.extraOf(hit.screenClass)));
            // The hit names the card it opens.
            assertTrue(hit.breadcrumb, hit.breadcrumb.endsWith(
                hit.title.startsWith("Control") ? "Control bar" : hit.title.split(" ")[0]));
        }
    }

    @Test
    public void searchingEditOpensEditModeAtRest() {
        List<SettingsSearchIndex.Entry> hits = search("edit");
        assertEquals("Edit mode", hits.get(0).title);
        String extra = EditModeDoor.extraOf(hits.get(0).screenClass);
        assertEquals(EditModeDoor.AT_REST, extra);
        assertNull(EditModeDoor.cardOf(extra));
    }

    @Test
    public void aSettingsPageIsNoDoor() {
        assertFalse(EditModeDoor.isDoor("com.termux.app.fragments.settings.TermuxPreferencesFragment"));
    }

    @Test
    public void anUnknownCardOpensAtRest() {
        assertNull(EditModeDoor.cardOf("CONTROL_BAR"));
    }

    @Test
    public void searchingCornersOpensTheGlobalCard() {
        for (String query : new String[] {"corners", "tint", "look"}) {
            List<SettingsSearchIndex.Entry> hits = search(query);
            assertFalse(query, hits.isEmpty());
            String extra = EditModeDoor.extraOf(hits.get(0).screenClass);
            assertEquals(query, EditModeController.Card.GLOBAL, EditModeDoor.cardOf(extra));
        }
    }
}
