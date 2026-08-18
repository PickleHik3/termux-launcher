package com.termux.app.terminal;

import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The held-modifier state the keybind hint slab is drawn from. */
@RunWith(RobolectricTestRunner.class)
public class HardwareModifierTrackerTest {

    private final HardwareModifierTracker tracker = new HardwareModifierTracker();

    /** A physical keyboard's id: the six-argument KeyEvent constructor would say VIRTUAL_KEYBOARD. */
    private static final int PHYSICAL_DEVICE_ID = 23;

    private static KeyEvent key(int action, int keyCode, int meta) {
        return new KeyEvent(0, 0, action, keyCode, 0, meta, PHYSICAL_DEVICE_ID, 0, 0,
            InputDevice.SOURCE_KEYBOARD);
    }

    private boolean down(int keyCode, int meta) {
        return tracker.track(key(KeyEvent.ACTION_DOWN, keyCode, meta));
    }

    private boolean up(int keyCode, int meta) {
        return tracker.track(key(KeyEvent.ACTION_UP, keyCode, meta));
    }

    @Test
    public void ctrlThenAlt_readsAsTheHeldPrefix() {
        assertTrue(down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON));
        assertFalse(tracker.isCtrlAltHeld());
        assertTrue(down(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON));
        assertTrue(tracker.isCtrlAltHeld());

        // Releasing either one ends the hold, whatever meta state rides on the release.
        assertTrue(up(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON));
        assertFalse(tracker.isCtrlAltHeld());
    }

    @Test
    public void shiftJoiningTheHold_isReportedAsAChange() {
        down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON);
        down(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON);
        assertFalse(tracker.isShiftHeld());
        assertTrue(down(KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON));
        assertTrue(tracker.isShiftHeld());
        assertTrue(tracker.isCtrlAltHeld());
    }

    @Test
    public void repeatOfTheSameState_reportsNoChange() {
        assertTrue(down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON));
        assertFalse(down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON));
    }

    @Test
    public void ordinaryKey_reseedsTheStateFromItsMetaState() {
        // A modifier whose release never reached this view cannot stay stuck on: the next
        // ordinary key says what is really held.
        down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON);
        down(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON);
        assertTrue(tracker.isCtrlAltHeld());
        assertTrue(down(KeyEvent.KEYCODE_M, 0));
        assertFalse(tracker.isCtrlAltHeld());
    }

    @Test
    public void clear_dropsEverythingOnce() {
        down(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON);
        assertTrue(tracker.clear());
        assertFalse(tracker.clear());
        assertFalse(tracker.isCtrlAltHeld());
        assertFalse(tracker.isShiftHeld());
    }

    @Test
    public void inAppKeyboardEvents_neverCountAsAPhysicalHold() {
        // Same shape the in-app keyboard dispatches: VIRTUAL_KEYBOARD device, latched modifiers on
        // the down and on the up alike.
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON;
        KeyEvent down = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_M, 0, meta,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
        KeyEvent up = new KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_M, 0, meta,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
        assertFalse(tracker.track(down));
        assertFalse(tracker.track(up));
        assertFalse(tracker.isCtrlAltHeld());
    }

    @Test
    public void nonPressActions_areIgnored() {
        assertEquals(false, tracker.track(new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE,
            KeyEvent.KEYCODE_CTRL_LEFT, 2, KeyEvent.META_CTRL_ON, PHYSICAL_DEVICE_ID, 0, 0,
            InputDevice.SOURCE_KEYBOARD)));
    }
}
