package com.termux.app.terminal.io;

import android.app.Application;
import android.os.Build;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Who is asked whether to take this release's key row, and who is left alone. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ExtraKeysDefaultOfferTest {

    private static final String CUSTOM_ROW = "[['ESC','TAB','CTRL','ALT','UP','DOWN']]";

    @Test
    public void freshInstallIsNeverAsked() {
        // No extra-keys line in the properties file at all: the shipped row is what they have.
        assertFalse(ExtraKeysDefaultOffer.shouldOffer(null, false, false));
        assertFalse(ExtraKeysDefaultOffer.shouldOffer("", false, false));
        assertFalse(ExtraKeysDefaultOffer.shouldOffer("   ", false, false));
        assertFalse(ExtraKeysDefaultOffer.isCustomRow(null));
    }

    @Test
    public void aRowThatIsAlreadyTheShippedOneIsNotAsked() {
        assertFalse(ExtraKeysDefaultOffer.shouldOffer(
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, false, false));
    }

    /** Someone who opened the editor and saved without changing anything has the same row. */
    @Test
    public void theEditorsOwnSpellingOfTheShippedRowIsNotAsked() {
        String saved = ExtraKeysDefaultOffer.normalise(
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS);
        assertNotEquals("the comparison would be pointless if the text already matched",
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, saved);
        assertTrue(ExtraKeysDefaultOffer.isSameRow(
            saved, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS));
        assertFalse(ExtraKeysDefaultOffer.shouldOffer(saved, false, false));
    }

    @Test
    public void aRowOfTheirOwnIsAsked() {
        assertTrue(ExtraKeysDefaultOffer.isCustomRow(CUSTOM_ROW));
        assertTrue(ExtraKeysDefaultOffer.shouldOffer(CUSTOM_ROW, false, false));
        assertTrue(ExtraKeysDefaultOffer.shouldOffer(ExtraKeysPresets.CLASSIC_TERMUX, false, false));
    }

    @Test
    public void theCardIsAskedOnlyOnce() {
        assertFalse(ExtraKeysDefaultOffer.shouldOffer(CUSTOM_ROW, true, false));
    }

    /** The first-launch run holds the card back rather than cancelling it. */
    @Test
    public void theRunKeepsTheCardWaiting() {
        assertFalse(ExtraKeysDefaultOffer.shouldOffer(CUSTOM_ROW, false, true));
        assertTrue("the row is still theirs, so the card is due once the run is done",
            ExtraKeysDefaultOffer.isCustomRow(CUSTOM_ROW));
        assertTrue(ExtraKeysDefaultOffer.shouldOffer(CUSTOM_ROW, false, false));
    }

    @Test
    public void onlyARowWithKeysInItIsOfferedBack() {
        assertFalse(ExtraKeysDefaultOffer.hasPreviousRow(null));
        assertFalse(ExtraKeysDefaultOffer.hasPreviousRow(""));
        assertFalse(ExtraKeysDefaultOffer.hasPreviousRow("[]"));
        assertFalse(ExtraKeysDefaultOffer.hasPreviousRow("[[]]"));
        assertTrue(ExtraKeysDefaultOffer.hasPreviousRow(CUSTOM_ROW));
    }

    /**
     * The keyboard lesson of the first-launch run points at the key that shows and hides the
     * keyboard, which the shipped row opens with and a row of the user's own usually has not.
     */
    @Test
    public void onlyARowWithTheKeyboardKeyOnItHasOne() {
        assertTrue(ExtraKeysDefaultOffer.hasKeyboardKey(
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS));
        assertFalse(ExtraKeysDefaultOffer.hasKeyboardKey(CUSTOM_ROW));
        assertFalse(ExtraKeysDefaultOffer.hasKeyboardKey(null));
        assertFalse(ExtraKeysDefaultOffer.hasKeyboardKey(""));
        assertTrue("a hand-written file says the same key its own way",
            ExtraKeysDefaultOffer.hasKeyboardKey("[['ESC',{key: keyboard, display: 'k'}]]"));
    }

    /** What the card saves is what the editor reads back: the row survives the round trip. */
    @Test
    public void theSavedRowStillParses() {
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(CUSTOM_ROW);
        assertEquals(6, model.keyCount());
        assertEquals(model.keyCount(),
            ExtraKeysLayoutModel.parse(ExtraKeysDefaultOffer.normalise(CUSTOM_ROW)).keyCount());
    }
}
