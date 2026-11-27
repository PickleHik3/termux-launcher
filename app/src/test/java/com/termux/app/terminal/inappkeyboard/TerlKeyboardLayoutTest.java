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

    /** Every Terl layout carries the spec's Ctrl, Alt, space bar and edit-key corners. */
    @Test
    public void everyTerlLayoutHasTheSpecsCorners() {
        KeyboardData qwerty = KeyboardData.load(resources, TerlKeyboardLayout.TERL);
        KeyboardData.Row reference = qwerty.rows.get(qwerty.rows.size() - 1);
        for (String choice : TerlKeyboardLayout.CHOICES) {
            KeyboardData data = KeyboardData.load(resources,
                TerlKeyboardLayout.resFor(choice, TerlKeyboardLayout.QWERTY));
            assertNotNull(choice, data);
            KeyboardData.Row bottom = data.rows.get(data.rows.size() - 1);
            assertEquals(choice, reference.keys.size(), bottom.keys.size());
            for (int k = 0; k < reference.keys.size(); k++)
                assertTrue(choice + " bottom key " + k,
                    Arrays.equals(reference.keys.get(k).keys, bottom.keys.get(k).keys));
            assertEquals(choice, KeyValue.getKeyByName("terl"), bottom.keys.get(0).keys[2]);
            assertEquals(choice, KeyValue.getKeyByName("switch_numeric"), bottom.keys.get(0).keys[4]);
            assertEquals(choice, KeyValue.getKeyByName("switch_clipboard"), bottom.keys.get(1).keys[2]);
            KeyboardData.Key space = bottom.keys.get(2);
            for (int slot = 1; slot <= 4; slot++) assertEquals(choice, null, space.keys[slot]);
            assertEquals(choice, KeyValue.getKeyByName("cursor_left"), space.keys[5]);
            assertEquals(choice, KeyValue.getKeyByName("cursor_right"), space.keys[6]);
            assertEquals(choice, KeyValue.getKeyByName("switch_backward"), space.keys[8]);
            assertEquals(choice, KeyValue.getKeyByName("selectAll"), keyNamed(data, "a").keys[4]);
            assertEquals(choice, KeyValue.getKeyByName("cut"), keyNamed(data, "x").keys[4]);
            assertEquals(choice, KeyValue.getKeyByName("copy"), keyNamed(data, "c").keys[4]);
            assertEquals(choice, KeyValue.getKeyByName("paste"), keyNamed(data, "v").keys[4]);
        }
    }

    @Test
    public void theLayoutsTypeTheirOwnLettersInTheirOwnRows() {
        assertEquals("z", rowLetters("qwertz", 0).substring(5, 6));
        assertEquals("a", rowLetters("azerty", 0).substring(0, 1));
        assertEquals("p", rowLetters("dvorak", 0).substring(1, 2));
        assertEquals("f", rowLetters("colemak", 0).substring(2, 3));
    }

    private String rowLetters(String choice, int row) {
        KeyboardData data = KeyboardData.load(resources, TerlKeyboardLayout.resFor(choice, null));
        StringBuilder out = new StringBuilder();
        for (KeyboardData.Key key : data.rows.get(row).keys) out.append(name(key));
        return out.toString();
    }

    @Test
    public void theChoiceDecidesTheLayoutAndCustomShowsItsBase() {
        assertEquals(TerlKeyboardLayout.resFor("dvorak"),
            TerlKeyboardLayout.forTerl(TerlKeyboardLayout.LAUNCHER, true, "dvorak", "azerty"));
        assertEquals(TerlKeyboardLayout.resFor("azerty"),
            TerlKeyboardLayout.forTerl(TerlKeyboardLayout.LAUNCHER, true, "custom", "azerty"));
        assertEquals(TerlKeyboardLayout.TERL,
            TerlKeyboardLayout.forTerl(TerlKeyboardLayout.LAUNCHER, true, "nonsense", null));
        assertEquals(TerlKeyboardLayout.LAUNCHER,
            TerlKeyboardLayout.forTerl(TerlKeyboardLayout.LAUNCHER, false, "dvorak", "azerty"));
        assertEquals(juloo.keyboard2.R.xml.latn_dvorak, TerlKeyboardLayout.forTerl(
            juloo.keyboard2.R.xml.latn_dvorak, true, "colemak", null));
    }

    @Test
    public void aLayoutFileIsTheLayoutOnlyWhenCustomIsChosen() throws Exception {
        java.io.File file = KeyboardLayoutFiles.overrideFile(home.getRoot(), "main");
        file.getParentFile().mkdirs();
        java.nio.file.Files.write(file.toPath(), ("<keyboard script=\"latin\"><row>"
            + "<key c=\"q\"/></row></keyboard>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        KeyboardData custom = InAppKeyboardLayoutSource.load(resources, home.getRoot(), "main",
            defaults(), true, "custom", "dvorak");
        assertEquals("q", name(custom.rows.get(0).keys.get(0)));
        KeyboardData chosen = InAppKeyboardLayoutSource.load(resources, home.getRoot(), "main",
            defaults(), true, "dvorak", "qwerty");
        assertEquals("p", name(chosen.rows.get(0).keys.get(1)));
    }

    @Test
    public void goingToCustomKeepsTheLayoutItCameFrom() {
        assertEquals("dvorak", TerlKeyboardLayout.baseAfter("dvorak", "custom", "azerty"));
        assertEquals("qwerty", TerlKeyboardLayout.baseAfter(null, "custom", null));
        assertEquals("azerty", TerlKeyboardLayout.baseAfter("custom", "custom", "azerty"));
        assertEquals("azerty", TerlKeyboardLayout.baseAfter("dvorak", "colemak", "azerty"));
    }

    @Test
    public void aSaveFromAnyDoorRecordsTheBaseAndARecreatedActivityHearsIt() {
        android.content.Context app = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences prefs =
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
                .build(app, false).getSharedPreferences();
        Runnable first = new Runnable() { @Override public void run() { firstRuns++; } };
        Runnable second = new Runnable() { @Override public void run() { secondRuns++; } };
        prefs.edit().putString(TerlKeyboardLayout.KEY, "dvorak").commit();
        KeyboardLayoutSetting.follow(app, first);
        KeyboardLayoutSetting.follow(app, second); // the activity was recreated
        prefs.edit().putString(TerlKeyboardLayout.KEY, "custom").commit();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals("dvorak", prefs.getString(TerlKeyboardLayout.BASE_KEY, null));
        assertEquals(0, firstRuns);
        assertTrue(secondRuns > 0);
    }

    private int firstRuns;
    private int secondRuns;

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
