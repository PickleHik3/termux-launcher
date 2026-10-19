package com.termux.view;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A view with no terminal yet draws nothing. It used to paint itself black, and a pane rebuilt
 * after a launcher restart showed one black frame over its glass before its session came back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalViewNoEmulatorDrawTest {

    @Test
    public void aViewWithoutATerminalLeavesWhatIsBehindItShowing() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.layout(0, 0, 100, 100);
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        int[] fills = {0};
        Canvas canvas = new Canvas(bitmap) {
            @Override public void drawColor(int color) {
                fills[0]++;
                super.drawColor(color);
            }
        };

        view.onDraw(canvas);

        assertEquals("nothing is painted over the pane's glass", 0, fills[0]);
    }
}
