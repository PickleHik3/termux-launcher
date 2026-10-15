package com.termux.app;

import static org.junit.Assert.assertEquals;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The inset lift stands the dock on the keyboard only for the band of it the root still covers.
 * Below Android 11 a root that fits system windows pads itself by the keyboard when the window
 * resizes, so lifting by the inset as well stacked two keyboard heights under the dock
 * (fullscreen on an Android 10 phone).
 */
@RunWith(RobolectricTestRunner.class)
public class TermuxActivityDockImeLiftTest {

    private static final int KEYBOARD = 800;
    private static final int NAV = 60;

    private static WindowInsetsCompat hiddenBars(int imeBottom) {
        return new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.NONE)
            .setVisible(Type.statusBars(), false)
            .setInsets(Type.navigationBars(), Insets.NONE)
            .setVisible(Type.navigationBars(), false)
            .setInsets(Type.ime(), Insets.of(0, 0, 0, imeBottom))
            .setVisible(Type.ime(), imeBottom > 0)
            .build();
    }

    @Test
    public void fullscreenWithAnUnpaddedRootLiftsByTheKeyboard() {
        assertEquals(KEYBOARD, TermuxActivity.dockImeLiftPx(hiddenBars(KEYBOARD), 0));
    }

    @Test
    public void aRootAlreadyPaddedByTheKeyboardIsNotLiftedAgain() {
        assertEquals(0, TermuxActivity.dockImeLiftPx(hiddenBars(KEYBOARD), KEYBOARD));
    }

    @Test
    public void aRootPaddedByPartOfTheKeyboardIsLiftedForTheRest() {
        assertEquals(KEYBOARD - 300, TermuxActivity.dockImeLiftPx(hiddenBars(KEYBOARD), 300));
    }

    @Test
    public void noKeyboardLiftsNothing() {
        assertEquals(0, TermuxActivity.dockImeLiftPx(hiddenBars(0), 0));
    }

    @Test
    public void aVisibleBarHandsTheLiftToTheRootProbe() {
        WindowInsetsCompat statusShown = new WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, 90, 0, 0))
            .setVisible(Type.statusBars(), true)
            .setInsets(Type.navigationBars(), Insets.NONE)
            .setVisible(Type.navigationBars(), false)
            .setInsets(Type.ime(), Insets.of(0, 0, 0, KEYBOARD))
            .setVisible(Type.ime(), true)
            .build();
        assertEquals(0, TermuxActivity.dockImeLiftPx(statusShown, 0));
        WindowInsetsCompat navShown = new WindowInsetsCompat.Builder()
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 0, NAV))
            .setVisible(Type.navigationBars(), true)
            .setInsets(Type.ime(), Insets.of(0, 0, 0, KEYBOARD))
            .setVisible(Type.ime(), true)
            .build();
        assertEquals(0, TermuxActivity.dockImeLiftPx(navShown, 0));
    }
}
