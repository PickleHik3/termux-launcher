package com.termux.app.fragments.settings.terl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Test;

/**
 * Review P2: under Terl, Home's label size, label shadow, two-line labels, folders, numerals and
 * Labels are reached in Settings' Home group (the Home card holds only its own rows).
 */
public class TerlSettingsHomeIconsTest {

    @Test public void homesIconAndLabelSettingsAreRowsOfTheHomeGroup() {
        for (String id : new String[] {SettingDeclarations.HOME_LABELS.id,
                SettingDeclarations.HOME_LABEL_SIZE.id, SettingDeclarations.HOME_LABEL_SHADOW.id,
                SettingDeclarations.HOME_LABEL_TWO_LINES.id, SettingDeclarations.HOME_FOLDERS.id,
                SettingDeclarations.HOME_NUMERALS.id}) {
            TerlSettingsTree.Item item = TerlSettingsTree.setting(id);
            assertNotNull(id + " has a row", item);
            assertEquals(id + " stands in Home", TerlSettingsTree.Group.HOME, item.group);
        }
    }
}
