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
    public void theBandAndTabStandBelowTheStatusRowsRoom() {
        float d = 2.625f;
        int room = 42;
        Rect flat = new Rect();
        Rect held = new Rect();
        LinuxWindowRow.tabRect(1080, 60f, d, flat);
        LinuxWindowRow.tabRect(1080, 60f, d, room, held);
        assertEquals(flat.top + room, held.top);
        assertEquals(flat.left, held.left);
        assertEquals(flat.height(), held.height());
        assertTrue(held.bottom <= LinuxWindowRow.slotTopPx(d, room));
        assertEquals(LinuxWindowRow.bandPx(d), LinuxWindowRow.slotTopPx(d, 0));
        assertEquals(LinuxWindowRow.bandPx(d) + room, LinuxWindowRow.slotTopPx(d, room));
        assertEquals(LinuxWindowRow.slotTopPx(d, 0), LinuxWindowRow.slotTopPx(d, -5));
    }

    @Test
    public void goToTerminalFromALinuxPageFindsTheNearestTerminalWindow() {
        // [t1] [gtk] [t2]: the earlier one wins the tie.
        assertEquals(0, LinuxWindowRow.nearestTerminalPlace(1, new boolean[] {false, true, false}));
        // [gtk] [gtk] [t1]
        assertEquals(2, LinuxWindowRow.nearestTerminalPlace(0, new boolean[] {true, true, false}));
        // Only Linux windows: nothing to go to.
        assertEquals(-1, LinuxWindowRow.nearestTerminalPlace(0, new boolean[] {true, true}));
        assertEquals(-1, LinuxWindowRow.nearestTerminalPlace(0, new boolean[0]));
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

    @Test
    public void theClassReadsFromBitsFourToSevenAndUnknownIsMain() {
        assertEquals(0, LinuxWindowRow.windowClass(0));
        assertEquals(0, LinuxWindowRow.windowClass(ILinuxWindows.WINDOW_X11));
        assertEquals(1, LinuxWindowRow.windowClass(1 << 4));
        assertEquals(2, LinuxWindowRow.windowClass(2 << 4 | ILinuxWindows.WINDOW_X11));
        assertEquals(3, LinuxWindowRow.windowClass(3 << 4));
        assertEquals(0, LinuxWindowRow.windowClass(9 << 4));
    }

    @Test
    public void onlyTheServicesReplayMarksAWindowReplayed() {
        assertFalse(LinuxWindowRow.replayed(0));
        assertFalse(LinuxWindowRow.replayed(3 << 4 | ILinuxWindows.WINDOW_X11));
        assertTrue(LinuxWindowRow.replayed(ILinuxWindows.WINDOW_REPLAYED | ILinuxWindows.WINDOW_X11));
        // The bit sits clear of the class bits: a replayed dialog is still a dialog.
        assertEquals(1, LinuxWindowRow.windowClass(1 << 4 | ILinuxWindows.WINDOW_REPLAYED));
    }

    @Test
    public void classDecidesWhoOwnsAPage() {
        int main = ILinuxWindows.WINDOW_CLASS_MAIN;
        int dialog = ILinuxWindows.WINDOW_CLASS_DIALOG;
        int fixed = ILinuxWindows.WINDOW_CLASS_FIXED;
        int transient_ = ILinuxWindows.WINDOW_CLASS_TRANSIENT;
        // Main: today's rule.
        assertTrue(LinuxWindowRow.ownsPage(main, 0L, false));
        assertTrue(LinuxWindowRow.ownsPage(main, 4L, false));
        assertFalse(LinuxWindowRow.ownsPage(main, 4L, true));
        assertEquals(LinuxWindowRow.PLACE_OVER_PARENT, LinuxWindowRow.placement(main, 4L, true));
        // Dialog: over an open parent, else over the front page.
        assertEquals(LinuxWindowRow.PLACE_OVER_PARENT, LinuxWindowRow.placement(dialog, 4L, true));
        assertEquals(LinuxWindowRow.PLACE_OVER_FRONT, LinuxWindowRow.placement(dialog, 4L, false));
        assertEquals(LinuxWindowRow.PLACE_OVER_FRONT, LinuxWindowRow.placement(dialog, 0L, false));
        assertFalse(LinuxWindowRow.ownsPage(dialog, 0L, false));
        // Transient: always over the front page.
        assertEquals(LinuxWindowRow.PLACE_OVER_FRONT, LinuxWindowRow.placement(transient_, 0L, false));
        assertEquals(LinuxWindowRow.PLACE_OVER_FRONT, LinuxWindowRow.placement(transient_, 4L, true));
        // Fixed: its own place.
        assertEquals(LinuxWindowRow.PLACE_ROW, LinuxWindowRow.placement(fixed, 0L, false));
        assertTrue(LinuxWindowRow.ownsPage(fixed, 0L, false));
    }

    @Test
    public void aFixedWindowThatFitsIsCentredAtOneToOne() {
        int[] out = new int[4];
        assertEquals(1f, LinuxWindowRow.fixedFrame(1000, 2000, 600, 400, out), 0f);
        assertArrayEquals(new int[] {200, 800, 600, 400}, out);
        assertEquals(1f, LinuxWindowRow.fixedFrame(1000, 2000, 0, 0, out), 0f);
        assertArrayEquals(new int[] {0, 0, 1000, 2000}, out);
    }

    @Test
    public void aFixedWindowTooWideOrTooTallScalesDownUniformly() {
        int[] out = new int[4];
        // Too wide: width decides.
        assertEquals(0.5f, LinuxWindowRow.fixedFrame(500, 2000, 1000, 400, out), 0f);
        assertArrayEquals(new int[] {0, 900, 500, 200}, out);
        // Too tall: height decides.
        assertEquals(0.5f, LinuxWindowRow.fixedFrame(1000, 400, 600, 800, out), 0f);
        assertArrayEquals(new int[] {350, 0, 300, 400}, out);
        // Both: the tighter axis decides, the aspect stays.
        assertEquals(0.25f, LinuxWindowRow.fixedFrame(500, 500, 2000, 1000, out), 0f);
        assertArrayEquals(new int[] {0, 125, 500, 250}, out);
    }

    /** About Xfce on pong: a 1552x1294 minimum in a 1231x1558 slot is cut off unless shown whole. */
    @Test
    public void aWindowOverItsSlotOnEitherAxisIsShownWholeAndOneThatFitsFillsIt() {
        assertTrue(LinuxWindowRow.overflowsSlot(1231, 1558, 1552, 1294));
        assertTrue(LinuxWindowRow.overflowsSlot(1231, 1558, 1000, 1600));
        assertFalse(LinuxWindowRow.overflowsSlot(1231, 1558, 1231, 1558));
        assertFalse(LinuxWindowRow.overflowsSlot(1231, 1558, 800, 600));
        // Not drawn yet, or the slot not laid out: it fills the slot.
        assertFalse(LinuxWindowRow.overflowsSlot(1231, 1558, 0, 0));
        assertFalse(LinuxWindowRow.overflowsSlot(0, 0, 1552, 1294));
        // The frame it is shown in is the Fixed one: uniformly scaled, centred.
        int[] out = new int[4];
        float scale = LinuxWindowRow.fixedFrame(1231, 1558, 1552, 1294, out);
        assertEquals(1231f / 1552f, scale, 1e-6f);
        assertEquals(1231, out[2]);
        assertTrue(out[3] <= 1558);
    }

    /**
     * About Xfce on pong: laid out at 1552x1294 view px while the host is told its 1038x1328 slot.
     * A tap at the Close button's spot in the view reaches the same spot of the app, which the host
     * finds by mapping the told size onto the app's 776x647 logical window.
     */
    @Test
    public void aTapOnAWindowShownWholeLandsWhereItWasDrawn() {
        float x = LinuxWindowRow.toConfigured(1500f, 1552, 1038);
        float y = LinuxWindowRow.toConfigured(1250f, 1294, 1328);
        assertEquals(750f, x * 776f / 1038f, 1e-3f);
        assertEquals(625f, y * 647f / 1328f, 1e-3f);
        // Told its own view size, or nothing yet: the touch passes unchanged.
        assertEquals(1500f, LinuxWindowRow.toConfigured(1500f, 1552, 1552), 0f);
        assertEquals(1500f, LinuxWindowRow.toConfigured(1500f, 1552, 0), 0f);
        assertEquals(1500f, LinuxWindowRow.toConfigured(1500f, 0, 1038), 0f);
    }
}
