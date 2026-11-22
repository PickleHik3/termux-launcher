package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import android.content.res.Resources;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import android.app.Application;

/** The size read-out a pane drag shows on the notice (ADR 0019): cells, columns × rows. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class DragResizeReadoutTest {

    private final Resources mRes = RuntimeEnvironment.getApplication().getResources();

    @Test public void aDividerNamesBothPanesTopOrLeftFirst() {
        assertEquals("80×12 · 80×20", DragResizePause.splitReadout(mRes, 80, 12, 80, 20));
        assertEquals("39×30 · 40×30", DragResizePause.splitReadout(mRes, 39, 30, 40, 30));
    }

    @Test public void aFloatingPaneNamesItsOneSize() {
        assertEquals("80 × 24", DragResizePause.floatReadout(mRes, 80, 24));
    }

    @Test public void theReadoutLingersThreeQuartersOfASecondAfterTheLift() {
        assertEquals(750L, DragResizePause.READOUT_LINGER_MS);
    }
}
