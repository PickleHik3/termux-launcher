package com.termux.app.barsearch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/** A Super tap against a Super chord (#435). */
public class SuperKeyTapTest {

    private static final int DOWN = KeyEvent.ACTION_DOWN;
    private static final int UP = KeyEvent.ACTION_UP;

    @Test
    public void superPressedAndLetGoIsATap() {
        SuperKeyTap tap = new SuperKeyTap();
        assertFalse(tap.onKey(KeyEvent.KEYCODE_META_LEFT, DOWN, 0));
        assertTrue(tap.onKey(KeyEvent.KEYCODE_META_LEFT, UP, 0));
    }

    @Test
    public void eitherSuperKeyCounts() {
        SuperKeyTap tap = new SuperKeyTap();
        tap.onKey(KeyEvent.KEYCODE_META_RIGHT, DOWN, 0);
        assertTrue(tap.onKey(KeyEvent.KEYCODE_META_RIGHT, UP, 0));
    }

    @Test
    public void anotherKeyBetweenMakesItAChord() {
        SuperKeyTap tap = new SuperKeyTap();
        tap.onKey(KeyEvent.KEYCODE_META_LEFT, DOWN, 0);
        tap.onKey(KeyEvent.KEYCODE_DPAD_LEFT, DOWN, 0);
        tap.onKey(KeyEvent.KEYCODE_DPAD_LEFT, UP, 0);
        assertFalse(tap.onKey(KeyEvent.KEYCODE_META_LEFT, UP, 0));
    }

    @Test
    public void aLiftWithoutAPressIsNotATapAndATapIsCountedOnce() {
        SuperKeyTap tap = new SuperKeyTap();
        assertFalse(tap.onKey(KeyEvent.KEYCODE_META_LEFT, UP, 0));
        tap.onKey(KeyEvent.KEYCODE_META_LEFT, DOWN, 0);
        tap.onKey(KeyEvent.KEYCODE_META_LEFT, DOWN, 1);
        assertTrue(tap.onKey(KeyEvent.KEYCODE_META_LEFT, UP, 0));
        assertFalse(tap.onKey(KeyEvent.KEYCODE_META_LEFT, UP, 0));
    }
}
