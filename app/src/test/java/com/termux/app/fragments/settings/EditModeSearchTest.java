package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import com.termux.app.editmode.EditModeController;
import com.termux.app.editmode.EditModeDoor;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class EditModeSearchTest {

    private static List<SettingsSearchIndex.Entry> search(String query) {
        return new SettingsSearchIndex(EditModeSearch.all(RuntimeEnvironment.getApplication()))
            .search(query, null, 10);
    }

    @Test
    public void searchingOpacityOpensTheKeyboardBarAndWindowsCards() {
        List<SettingsSearchIndex.Entry> hits = search("opacity");
        assertEquals(4, hits.size());
        Map<String, EditModeController.Card> want = new HashMap<>();
        want.put("Keyboard opacity", EditModeController.Card.KEYBOARD);
        want.put("Control bar opacity", EditModeController.Card.BAR);
        want.put("Windows opacity", EditModeController.Card.WINDOWS);
        want.put("Custom opacity", EditModeController.Card.GLOBAL);
        for (SettingsSearchIndex.Entry hit : hits) {
            assertTrue(EditModeDoor.isDoor(hit.screenClass));
            assertEquals(hit.title, want.get(hit.title),
                EditModeDoor.cardOf(EditModeDoor.extraOf(hit.screenClass)));
            // The hit names the card it opens.
            assertTrue(hit.breadcrumb, hit.breadcrumb.startsWith("Edit mode"
                + SettingsSearchIndex.PATH_SEPARATOR));
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
