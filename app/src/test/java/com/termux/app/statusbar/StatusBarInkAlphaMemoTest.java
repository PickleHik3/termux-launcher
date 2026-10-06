package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;

import com.termux.app.chrome.OnGlass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The lens's memoised glyph ink answers exactly what {@link StatusBarInk#inkAtAlpha} answers. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarInkAlphaMemoTest {

    private static final int[] SURFACES = {0xFF6A5755, 0xFF101114, 0xFFF4F0F2, 0x806A5755};
    private static final int[] SEEDS = {0xFF4F7BD9, 0xFFE0A030, 0xFF8ED1A2, 0x404F7BD9};
    private static final double[] TARGETS = {OnGlass.TARGET_LARGE_TEXT, OnGlass.TARGET_BODY_TEXT};

    @Test
    public void everyAlphaMatchesTheUnmemoisedAnswer() {
        StatusBarInk.AlphaInkMemo memo = new StatusBarInk.AlphaInkMemo();
        for (int surface : SURFACES) {
            for (int seed : SEEDS) {
                for (double target : TARGETS) {
                    for (int alpha = -3; alpha <= 258; alpha += 7) {
                        assertEquals(StatusBarInk.inkAtAlpha(surface, seed, alpha, target),
                            memo.inkAtAlpha(surface, seed, alpha, target));
                    }
                }
            }
        }
    }

    @Test
    public void aRepeatedAskAfterTheKeyMovedAndCameBackIsStillExact() {
        StatusBarInk.AlphaInkMemo memo = new StatusBarInk.AlphaInkMemo();
        double target = OnGlass.TARGET_LARGE_TEXT;
        int first = memo.inkAtAlpha(SURFACES[0], SEEDS[0], 120, target);
        memo.inkAtAlpha(SURFACES[1], SEEDS[0], 120, target);
        memo.inkAtAlpha(SURFACES[1], SEEDS[1], 40, target);
        assertEquals(first, memo.inkAtAlpha(SURFACES[0], SEEDS[0], 120, target));
        assertEquals(StatusBarInk.inkAtAlpha(SURFACES[0], SEEDS[0], 120, target), first);
    }
}
