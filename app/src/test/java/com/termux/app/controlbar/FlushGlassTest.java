package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** The flush bar's glass reaches below the bar, so its rounded corners are clipped away. */
@RunWith(RobolectricTestRunner.class)
public class FlushGlassTest {

    @Test
    public void theGlassStandsTallerThanTheBarByItsRadius() {
        ColorDrawable inner = new ColorDrawable(0xFF000000);
        FlushGlass glass = new FlushGlass(inner, 30);
        glass.setBounds(0, 0, 400, 100);
        assertEquals(new Rect(0, 0, 400, 130), inner.getBounds());
    }
}
