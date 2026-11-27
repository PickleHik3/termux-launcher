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
    }

    private static List<SettingsSearchIndex.Entry> search(String query) {
        return new SettingsSearchIndex(EditModeSearch.entries(STRINGS::get)).search(query, null, 10);
    }

    @Test
    public void searchingOpacityOpensTheKeyboardCard() {
        List<SettingsSearchIndex.Entry> hits = search("opacity");
        assertEquals(1, hits.size());
        SettingsSearchIndex.Entry hit = hits.get(0);
        assertEquals("Keyboard opacity", hit.title);
        assertTrue(EditModeDoor.isDoor(hit.screenClass));
        String extra = EditModeDoor.extraOf(hit.screenClass);
        assertEquals(EditModeController.Card.KEYBOARD, EditModeDoor.cardOf(extra));
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
}
