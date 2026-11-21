package com.termux.app.dock;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/** The shade the lift's owner supplies hides the slot on begin and shows it on end. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LiftSourceShadeTest {

    @Test
    public void alphaShadeHidesOnBeginAndShowsOnEnd() {
        View slot = new View(Robolectric.buildActivity(Activity.class).setup().get());
        LiftSourceSlot<View> source = new LiftSourceSlot<>(LiftSourceShade.ALPHA);
        source.hide(slot);
        assertEquals(0f, slot.getAlpha(), 0f);
        source.end();
        assertEquals(1f, slot.getAlpha(), 0f);
    }
}
