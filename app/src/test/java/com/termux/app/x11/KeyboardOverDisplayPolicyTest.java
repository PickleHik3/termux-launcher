package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.place.PlaceLayout.KeyboardMode;

import org.junit.Test;

/** The "Keyboard over display" row: one choice when both orientations agree, both named when not. */
public class KeyboardOverDisplayPolicyTest {

    private static final String SPLIT = "Portrait: %1$s · Landscape: %2$s";

    private static String summary(KeyboardMode portrait, KeyboardMode landscape) {
        return KeyboardOverDisplayPolicy.summary(portrait, landscape, "Overlay", "Resize display", SPLIT);
    }

    @Test
    public void bothOrientationsAgreeing_isThatChoice() {
        assertEquals("overlay",
            KeyboardOverDisplayPolicy.value(KeyboardMode.OVERLAY, KeyboardMode.OVERLAY));
        assertEquals("resize",
            KeyboardOverDisplayPolicy.value(KeyboardMode.RESIZE, KeyboardMode.RESIZE));
        assertEquals("Overlay", summary(KeyboardMode.OVERLAY, KeyboardMode.OVERLAY));
        assertEquals("Resize display", summary(KeyboardMode.RESIZE, KeyboardMode.RESIZE));
    }

    @Test
    public void orientationsApart_checkNothingAndNameBoth() {
        assertNull(KeyboardOverDisplayPolicy.value(KeyboardMode.OVERLAY, KeyboardMode.RESIZE));
        assertNull(KeyboardOverDisplayPolicy.value(KeyboardMode.RESIZE, KeyboardMode.OVERLAY));
        assertEquals("Portrait: Overlay · Landscape: Resize display",
            summary(KeyboardMode.OVERLAY, KeyboardMode.RESIZE));
        assertEquals("Portrait: Resize display · Landscape: Overlay",
            summary(KeyboardMode.RESIZE, KeyboardMode.OVERLAY));
    }
}
