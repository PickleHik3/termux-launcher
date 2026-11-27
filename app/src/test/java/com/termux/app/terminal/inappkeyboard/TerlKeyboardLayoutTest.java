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

    /**
     * Terl's layout is today's but for what the spec's Keys section changes: Ctrl and Alt's
     * corners, the space bar (typing gestures only) and the edit keys on A, X, C and V.
     */
    @Test
    public void theTwoLayoutsDifferOnlyWhereTheSpecSays() {
        KeyboardData terl = KeyboardData.load(resources, TerlKeyboardLayout.TERL);
        KeyboardData today = KeyboardData.load(resources, TerlKeyboardLayout.LAUNCHER);
        assertEquals(today.rows.size(), terl.rows.size());
        java.util.Set<String> changed = new java.util.HashSet<>(
            Arrays.asList("ctrl", "alt", "space", "a", "x", "c", "v"));
        for (int r = 0; r < today.rows.size(); r++) {
            assertEquals(today.rows.get(r).keys.size(), terl.rows.get(r).keys.size());
            for (int k = 0; k < today.rows.get(r).keys.size(); k++) {
                KeyboardData.Key key = today.rows.get(r).keys.get(k);
                if (changed.contains(name(key))) continue;
                assertTrue("row " + r + " key " + k, Arrays.equals(key.keys,
                    terl.rows.get(r).keys.get(k).keys));
            }
        }
    }

    @Test
    public void theSpaceBarKeepsTypingGesturesAndTheEditKeysAreOn() {
        KeyboardData terl = InAppKeyboardLayoutSource.load(resources, home.getRoot(), null,
            defaults(), true);
        KeyboardData.Key space = terl.rows.get(terl.rows.size() - 1).keys.get(2);
        // Corners 1-4 are the diagonals.
        for (int slot = 1; slot <= 4; slot++)
            assertEquals("diagonal " + slot, null, space.keys[slot]);
        assertEquals(KeyValue.getKeyByName("selectAll"), keyNamed(terl, "a").keys[4]);
        assertEquals(KeyValue.getKeyByName("cut"), keyNamed(terl, "x").keys[4]);
        assertEquals(KeyValue.getKeyByName("copy"), keyNamed(terl, "c").keys[4]);
        assertEquals(KeyValue.getKeyByName("paste"), keyNamed(terl, "v").keys[4]);
    }

    private static KeyboardData.Key keyNamed(KeyboardData data, String name) {
        for (KeyboardData.Row row : data.rows)
            for (KeyboardData.Key key : row.keys)
                if (name.equals(name(key))) return key;
        throw new AssertionError("no key " + name);
    }

    private static String name(KeyboardData.Key key) {
        KeyValue centre = key.keys[0];
        if (centre == null) return "";
        if (centre.equals(KeyValue.getKeyByName("ctrl"))) return "ctrl";
        if (centre.equals(KeyValue.getKeyByName("alt"))) return "alt";
        if (centre.equals(KeyValue.getKeyByName("space"))) return "space";
        return centre.getString();
    }
}
