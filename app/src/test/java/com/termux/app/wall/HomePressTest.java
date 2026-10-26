package com.termux.app.wall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A home press in front goes to Home page 1; one that brings the launcher back does not. */
public class HomePressTest {

    @Test
    public void aPressWhileInFrontGoesHome() {
        HomePress press = new HomePress();
        press.onResume();
        assertTrue(press.goesHome());
    }

    @Test
    public void aPressFromAnotherAppFindsThePageLeft() {
        HomePress press = new HomePress();
        press.onResume();
        press.onStop();
        assertFalse(press.goesHome());
    }

    @Test
    public void anInstanceThatNeverResumedTreatsThePressAsAReturn() {
        // A new wallpaper restyles every app, so the launcher waiting behind the wallpaper app
        // is relaunched on its way back; the home press that brings it back reaches the new
        // instance before its first resume.
        assertFalse(new HomePress().goesHome());
    }

    @Test
    public void backInFrontAPressGoesHomeAgain() {
        HomePress press = new HomePress();
        press.onResume();
        press.onStop();
        press.onResume();
        assertTrue(press.goesHome());
    }
}
