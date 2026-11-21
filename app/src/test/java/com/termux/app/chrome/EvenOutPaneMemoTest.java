package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.app.terminal.PaneGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A terminal pane asked twice with nothing changed answers from its memo: the same even-out
 * matrix, and no ground-changed notice, so nothing downstream re-dresses or rebuilds.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutPaneMemoTest {

    private static final Rect PANE = new Rect(0, 200, 1080, 1800);
    private static final int FG = 0xFFE6E1E5;
    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;

    private ChromeInk ink;
    private int groundMoves;
    private EvenOut.Ceiling[] ceilings;

    @Before
    public void setUp() {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return 0xFF5A5D62;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                return new GlassBackdropCache.Lightness(13.5d, 39.5d, 67.8d,
                    Hct.from(250d, 6d, 39.5d).toInt());
            }
        });
        ceilings = MaterialTerminalColorScheme.evenOutCeilings(RuntimeEnvironment.getApplication(),
            LEVEL);
        ink.setOnTerminalPaneGroundChanged(() -> groundMoves++);
        ink.setEvenOutPolicy(policy());
        groundMoves = 0;
    }

    private ChromeInk.EvenOutPolicy policy() {
        return new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0], ceilings[1],
            ChromeInk.Polarity.PALE_INK);
    }

    private void measure() {
        ink.terminalPane(PANE, Color.TRANSPARENT, 0xFF101418, FG, PaneGlass.dimTerminalInk(FG));
    }

    @Test
    public void askingTwiceWithNothingChangedKeepsTheMatrixAndTellsNobody() {
        measure();
        ColorMatrix first = ink.evenOutMatrix(PANE);
        assertNotNull(first);
        int movesAfterFirst = groundMoves;
        measure();
        assertSame(first, ink.evenOutMatrix(PANE));
        assertEquals(movesAfterFirst, groundMoves);
    }

    @Test
    public void anEqualPolicyHandedAgainChangesNothing() {
        measure();
        ColorMatrix first = ink.evenOutMatrix(PANE);
        int movesBefore = groundMoves;
        ink.setEvenOutPolicy(policy());
        measure();
        assertSame(first, ink.evenOutMatrix(PANE));
        assertEquals(movesBefore, groundMoves);
    }
}
