package com.termux.app.terminal.io;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ExtraKeysPresetsTest {

    /** Issue #22: the editor has to offer CTRL without anyone typing it. */
    @Test
    public void quickKeysLeadWithTheModifiers() {
        List<ExtraKeysPresets.QuickKey> quick = ExtraKeysPresets.quickKeys();
        assertEquals("CTRL", quick.get(0).key);
        assertEquals("ALT", quick.get(1).key);
        Set<String> keys = new HashSet<>();
        for (ExtraKeysPresets.QuickKey key : quick) {
            assertFalse("duplicate quick key " + key.key, keys.contains(key.key));
            keys.add(key.key);
            assertTrue(key.key + " must be a name the row understands, not text",
                ExtraKeyActionLabels.isNamedKey(key.key) || key.key.length() == 1);
        }
    }

    @Test
    public void classicPresetIsUpstreamsRowWithCtrl() {
        ExtraKeysLayoutModel classic = ExtraKeysLayoutModel.parse(ExtraKeysPresets.CLASSIC_TERMUX);
        assertEquals(1, classic.rowCount());
        List<String> names = new ArrayList<>();
        for (ExtraKeysLayoutModel.Key key : classic.row(0)) names.add(key.key);
        assertTrue(names.contains("CTRL"));
        assertTrue(names.contains("ALT"));
        assertEquals("|", classic.row(0).get(4).popup.key);
    }

    @Test
    public void everyPresetParsesAndRoundTrips() {
        for (int page = 0; page < 2; page++) {
            for (ExtraKeysPresets.Preset preset : ExtraKeysPresets.presetsForPage(page)) {
                ExtraKeysLayoutModel model = preset.model();
                ExtraKeysLayoutModel again = ExtraKeysLayoutModel.parse(model.serialize());
                assertEquals(preset.pageValue, model.keyCount(), again.keyCount());
            }
        }
        // Page one's shipped default has keys; page two ships empty and its preset says so.
        assertFalse(ExtraKeysPresets.presetsForPage(0).get(0).model().isEmpty());
        assertTrue(ExtraKeysPresets.presetsForPage(1).get(0).model().isEmpty());
    }

    @Test
    public void modifiersAndRowControlsAreNamedKeys() {
        for (String name : ExtraKeysPresets.MODIFIER_KEYS) assertTrue(ExtraKeyActionLabels.isNamedKey(name));
        for (String name : ExtraKeysPresets.ROW_KEYS) assertTrue(ExtraKeyActionLabels.isNamedKey(name));
        assertFalse(ExtraKeyActionLabels.isNamedKey("ls -la"));
        assertFalse(ExtraKeyActionLabels.isNamedKey("ctrl"));
    }
}
