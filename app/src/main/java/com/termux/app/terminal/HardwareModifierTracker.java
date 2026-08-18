package com.termux.app.terminal;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * Which modifiers a physical keyboard is holding down right now.
 *
 * <p>The keybind hint slab needs this because a held modifier is not an event anyone else asks
 * about: the in-app keyboard reports its own latch through a callback, but a hardware Ctrl+Alt
 * only exists as the meta state riding on other key events. Tracking the modifier key codes
 * themselves is what makes "hold Ctrl+Alt and read the legend" work with nothing else pressed.
 *
 * <p>Ordinary keys re-seed the state from their meta state, so a stroke that arrives while the
 * view had no focus for the matching key-up cannot leave a modifier stuck on.
 */
public final class HardwareModifierTracker {

    private boolean mCtrl;
    private boolean mAlt;
    private boolean mShift;

    /**
     * Folds one key event into the tracked state.
     *
     * @return true when the state changed, i.e. when the caller should refresh anything it draws
     *     from it.
     */
    public boolean track(@NonNull KeyEvent event) {
        // The in-app keyboard bakes its latched modifiers into the metaState of both the down and
        // the up it synthesizes, so reading those as a physical hold latches this tracker forever
        // and the hint legend never goes away after a bind runs. That keyboard reports its own
        // latch through onKeyboardModifiersChanged; this class is only about physical keys.
        if (event.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP)
            return false;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return apply(down, mAlt, mShift);
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return apply(mCtrl, down, mShift);
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return apply(mCtrl, mAlt, down);
            default:
                return apply(event.isCtrlPressed(), event.isAltPressed(), event.isShiftPressed());
        }
    }

    /** Drops every modifier, for a focus or visibility change that ends the hold silently. */
    public boolean clear() {
        return apply(false, false, false);
    }

    /** Whether the Ctrl+Alt prefix the hint legend documents is being held. */
    public boolean isCtrlAltHeld() {
        return mCtrl && mAlt;
    }

    public boolean isShiftHeld() {
        return mShift;
    }

    private boolean apply(boolean ctrl, boolean alt, boolean shift) {
        if (ctrl == mCtrl && alt == mAlt && shift == mShift) return false;
        mCtrl = ctrl;
        mAlt = alt;
        mShift = shift;
        return true;
    }
}
