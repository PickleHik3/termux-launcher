package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.termux.terminal.CapturingSession;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The rig the key-route tests type through: a Standard view, and, with the host library, a Fast one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyRouteRigTest {

    @After
    public void restore() {
        CapturingSession.inputPropertyForTests(null);
    }

    @Test
    public void aKeyPressedOnTheViewLandsInTheCapture() {
        KeyRouteRig rig = new KeyRouteRig(false, null);
        assertFalse(rig.emulator.usesLibraryInputEncoders());
        rig.view.onKeyDown(KeyEvent.KEYCODE_A, KeyRouteRig.event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0));
        assertEquals("a", rig.drain());
    }

    @Test
    public void theHostLibraryLoadsInsideRobolectric() {
        if (System.getProperty("termux.ghostty.libDir") == null) return;
        KeyRouteRig rig = new KeyRouteRig(true, null);
        assertTrue(rig.emulator.usesLibraryInputEncoders());
        assertFalse(new KeyRouteRig(true, "java").emulator.usesLibraryInputEncoders());
    }
}
