package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppDrawerReopenPolicyTest {

    @Test
    public void offAlwaysStartsAtTheTop() {
        assertEquals(0, AppDrawerReopenPolicy.startPosition(false, 40, 100));
    }

    @Test
    public void onReturnsToTheSavedPosition() {
        assertEquals(40, AppDrawerReopenPolicy.startPosition(true, 40, 100));
    }

    @Test
    public void aShorterListClampsToItsLastPosition() {
        assertEquals(9, AppDrawerReopenPolicy.startPosition(true, 40, 10));
    }

    @Test
    public void anEmptyListOrNothingSavedStartsAtTheTop() {
        assertEquals(0, AppDrawerReopenPolicy.startPosition(true, 40, 0));
        assertEquals(0, AppDrawerReopenPolicy.startPosition(true, 0, 100));
        assertEquals(0, AppDrawerReopenPolicy.startPosition(true, -3, 100));
    }
}
