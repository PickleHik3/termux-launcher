package com.termux.app.wayland;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxWindowRowTest {

    @Test
    public void theChipIsTheAppsNameNeverTheTitle() {
        assertEquals("GTK Demo", LinuxWindowRow.chipLabel("GTK Demo", "org.gtk.Demo3", "Linux app"));
        assertEquals("org.gtk.Demo3", LinuxWindowRow.chipLabel(" ", "org.gtk.Demo3", "Linux app"));
        assertEquals("Linux app", LinuxWindowRow.chipLabel(null, "", "Linux app"));
    }

    @Test
    public void aDialogWithAnOpenParentHasNoPageOfItsOwn() {
        assertFalse(LinuxWindowRow.ownsPage(4L, true));
        assertTrue(LinuxWindowRow.ownsPage(4L, false));
        assertTrue(LinuxWindowRow.ownsPage(0L, false));
    }

    @Test
    public void onlyTheBoundWindowOnOrNextToTheScreenHoldsASurface() {
        assertTrue(LinuxWindowRow.holdsSurface(true, 4, 4));
        assertTrue(LinuxWindowRow.holdsSurface(true, 4, 3));
        assertTrue(LinuxWindowRow.holdsSurface(true, 4, 5));
        // Off the neighbours: no buffers.
        assertFalse(LinuxWindowRow.holdsSurface(true, 4, 2));
        assertFalse(LinuxWindowRow.holdsSurface(true, 4, 0));
        // Not bound to the view: never.
        assertFalse(LinuxWindowRow.holdsSurface(false, 4, 4));
        assertFalse(LinuxWindowRow.holdsSurface(true, -1, 4));
    }

    @Test
    public void theRimTabSitsTopRightInsideTheCornerAndAboveTheApp() {
        Rect r = new Rect();
        LinuxWindowRow.tabRect(1080, 60f, 2.625f, r);
        assertEquals(1080 - 60, r.right);
        assertEquals(Math.round(40f * 2.625f), r.width());
        assertTrue(r.bottom <= LinuxWindowRow.bandPx(2.625f));
        assertTrue(r.top >= 0);
    }

    @Test
    public void aDialogIsCentredOverItsParentAndNeverLarger() {
        int[] out = new int[4];
        LinuxWindowRow.dialogFrame(1000, 2000, 600, 400, out);
        assertArrayEquals(new int[] {200, 800, 600, 400}, out);
        LinuxWindowRow.dialogFrame(1000, 2000, 1400, 400, out);
        assertArrayEquals(new int[] {0, 800, 1000, 400}, out);
        LinuxWindowRow.dialogFrame(1000, 2000, 0, 0, out);
        assertArrayEquals(new int[] {0, 0, 1000, 2000}, out);
    }
}
