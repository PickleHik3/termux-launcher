package com.termux.app.dock;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The prompt line's inks drift between two pages' without a flip. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PromptLineInkBlendTest {

    private static PromptLineInk ink(int background, int foreground) {
        return PromptLineInk.resolve(background, foreground, 0xFF3060C0, 0xFF30A060);
    }

    @Test
    public void eachEndIsItself() {
        PromptLineInk a = ink(0xFFF4F4F4, 0xFF202020);
        PromptLineInk b = ink(0xFF101010, 0xFFE0E0E0);
        assertSame(a, PromptLineInk.blend(a, b, 0f));
        assertSame(b, PromptLineInk.blend(a, b, 1f));
    }

    @Test
    public void theHintDriftsInSmallSteps() {
        PromptLineInk a = ink(0xFFF4F4F4, 0xFF202020);
        PromptLineInk b = ink(0xFF101010, 0xFFE0E0E0);
        int previous = a.hint & 0xFF;
        int direction = Integer.signum((b.hint & 0xFF) - previous);
        for (int step = 1; step <= 32; step++) {
            int now = PromptLineInk.blend(a, b, step / 32f).hint & 0xFF;
            assertTrue("monotone at " + step, direction * (now - previous) >= 0);
            assertTrue("no flip at " + step, Math.abs(now - previous) <= 16);
            previous = now;
        }
        assertEquals(b.hint & 0xFF, previous);
        assertEquals(a.well, PromptLineInk.blend(a, b, 0.5f).well);
    }
}
