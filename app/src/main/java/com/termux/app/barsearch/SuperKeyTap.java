package com.termux.app.barsearch;

import android.view.KeyEvent;

/**
 * Tells a tap of the Super (Meta) key from a chord (#435): Super pressed and let go with no other
 * key between. Feed it every key event, in order; it answers true on the lift that ends a tap.
 * Android takes a bare Super on some devices for itself, and then no event arrives here: that is
 * outside this class.
 */
public final class SuperKeyTap {

    private boolean mArmed;

    /** @return true when {@code action} on {@code keyCode} completes a Super tap */
    public boolean onKey(int keyCode, int action, int repeatCount) {
        boolean meta = keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT;
        if (action == KeyEvent.ACTION_DOWN) {
            if (meta) {
                if (repeatCount == 0) mArmed = true;
            } else {
                mArmed = false;
            }
            return false;
        }
        if (action == KeyEvent.ACTION_UP && meta) {
            boolean tap = mArmed;
            mArmed = false;
            return tap;
        }
        return false;
    }
}
