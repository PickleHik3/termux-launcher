package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.place.PlaceLayout.KeyboardMode;

import org.junit.Test;

public class DisplayKeyboardModeChoiceTest {

    private static final String FORMAT = "Portrait: %1$s · Landscape: %2$s";

    private static String summary(KeyboardMode portrait, KeyboardMode landscape) {
        return DisplayKeyboardModeChoice.summary(portrait, landscape, "Overlay", "Resize display",
            FORMAT);
    }

    @Test
    public void agreeingOrientationsShowOneChoice() {
        assertEquals("Overlay", summary(KeyboardMode.OVERLAY, KeyboardMode.OVERLAY));
        assertEquals("Resize display", summary(KeyboardMode.RESIZE, KeyboardMode.RESIZE));
        assertEquals("overlay",
            DisplayKeyboardModeChoice.commonValue(KeyboardMode.OVERLAY, KeyboardMode.OVERLAY));
        assertEquals("resize",
            DisplayKeyboardModeChoice.commonValue(KeyboardMode.RESIZE, KeyboardMode.RESIZE));
    }

    @Test
    public void differingOrientationsNameBoth() {
        assertEquals("Portrait: Overlay · Landscape: Resize display",
            summary(KeyboardMode.OVERLAY, KeyboardMode.RESIZE));
        assertEquals("Portrait: Resize display · Landscape: Overlay",
            summary(KeyboardMode.RESIZE, KeyboardMode.OVERLAY));
        assertNull(DisplayKeyboardModeChoice.commonValue(KeyboardMode.OVERLAY, KeyboardMode.RESIZE));
    }

    @Test
    public void aPickedValueReadsBackAsItsMode() {
        for (KeyboardMode mode : KeyboardMode.values()) {
            assertEquals(mode,
                DisplayKeyboardModeChoice.modeOf(DisplayKeyboardModeChoice.valueOf(mode)));
        }
        assertEquals(KeyboardMode.OVERLAY, DisplayKeyboardModeChoice.modeOf(null));
    }
}
