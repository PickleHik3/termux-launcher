package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.res.Resources;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

/**
 * Terl's launcher layout stands in for today's only while the Terl desktop switch is on: Ctrl's
 * north-east corner is the Terl key, the clipboard is on Alt's, switch-keyboard on Alt's
 * south-west. With the switch off the keyboard is today's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TerlKeyboardLayoutTest {

    @Rule public TemporaryFolder home = new TemporaryFolder();

    private final Resources resources = RuntimeEnvironment.getApplication().getResources();

    private static LayoutModifier.LayoutOptions defaults() {
        return new LayoutModifier.LayoutOptions(true, false, true,
            InAppKeyboardExtraKeys.resolve(InAppKeyboardExtraKeys.defaultStoredValue()));
    }

    private KeyboardData.Row bottomRow(boolean terl) {
        KeyboardData data = InAppKeyboardLayoutSource.load(resources, home.getRoot(), null,
            defaults(), terl);
        assertNotNull(data);
        return data.rows.get(data.rows.size() - 1);
    }

    @Test
    public void onlyTheLauncherLayoutIsSwappedAndOnlyUnderTerl() {
        assertEquals(TerlKeyboardLayout.TERL,
            TerlKeyboardLayout.forTerl(TerlKeyboardLayout.LAUNCHER, true));
        assertEquals(TerlKeyboardLayout.LAUNCHER,
            TerlKeyboardLayout.forTerl(TerlKeyboardLayout.LAUNCHER, false));
        assertEquals(juloo.keyboard2.R.xml.latn_colemak,
            TerlKeyboardLayout.forTerl(juloo.keyboard2.R.xml.latn_colemak, true));
        assertEquals(false, TerlKeyboardLayout.isOn(RuntimeEnvironment.getApplication()));
    }

    @Test
    public void underTerlCtrlCarriesTheTerlKeyAndAltTheClipboard() {
        KeyboardData.Row row = bottomRow(true);
        assertEquals(KeyValue.getKeyByName("terl"), row.keys.get(0).getKeyValue(2));
        assertEquals(KeyValue.getKeyByName("switch_clipboard"), row.keys.get(1).getKeyValue(2));
    }

    @Test
    public void withTheSwitchOffTheKeyboardIsTodays() {
        KeyboardData.Row row = bottomRow(false);
        assertEquals(KeyValue.getKeyByName("switch_clipboard"), row.keys.get(0).getKeyValue(2));
        for (KeyboardData.Key key : row.keys)
            for (int slot = 0; slot < 9; slot++)
                assertTrue(!KeyValue.isTerlKey(key.getKeyValue(slot)));
    }

    /** Terl's layout is today's but for the Ctrl and Alt corners. */
    @Test
    public void theTwoLayoutsDifferOnlyInCtrlAndAlt() {
        KeyboardData terl = KeyboardData.load(resources, TerlKeyboardLayout.TERL);
        KeyboardData today = KeyboardData.load(resources, TerlKeyboardLayout.LAUNCHER);
        assertEquals(today.rows.size(), terl.rows.size());
        for (int r = 0; r < today.rows.size(); r++) {
            assertEquals(today.rows.get(r).keys.size(), terl.rows.get(r).keys.size());
            for (int k = 0; k < today.rows.get(r).keys.size(); k++) {
                if (r == today.rows.size() - 1 && k < 2) continue;
                assertTrue("row " + r + " key " + k, Arrays.equals(
                    today.rows.get(r).keys.get(k).keys, terl.rows.get(r).keys.get(k).keys));
            }
        }
    }
}
