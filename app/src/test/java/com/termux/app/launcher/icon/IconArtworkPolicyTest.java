package com.termux.app.launcher.icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The cap and budget rule on its own: pure numbers, pinned. */
public class IconArtworkPolicyTest {

    private static final int MB = 1024 * 1024;

    @Test
    public void aNormalPhoneKeepsTodaysCapOnEverySizeStep() {
        for (float density : new float[] {2.0f, 2.75f, 3.0f}) {
            for (float dp : new float[] {48f, 54f, 60f}) {
                assertEquals("density " + density + " at " + dp + "dp",
                    192, IconArtworkPolicy.capPx(density, dp));
            }
        }
        // 60dp x 3.2 is exactly 192.
        assertEquals(192, IconArtworkPolicy.capPx(3.2f, 60f));
    }

    @Test
    public void aDenseScreenWithTheLargestStepRaisesTheCapToWhatItDraws() {
        assertEquals(210, IconArtworkPolicy.capPx(3.5f, 60f));
        assertEquals(240, IconArtworkPolicy.capPx(4.0f, 60f));
        // Small and Medium on the same displays still fit in the base.
        assertEquals(192, IconArtworkPolicy.capPx(3.5f, 54f));
        assertEquals(192, IconArtworkPolicy.capPx(3.5f, 48f));
        assertEquals(192, IconArtworkPolicy.capPx(4.0f, 48f));
    }

    @Test
    public void theCapNeverPassesTheCeilingAndNeverDropsBelowTheBase() {
        assertEquals(288, IconArtworkPolicy.capPx(6.0f, 60f));
        assertEquals(192, IconArtworkPolicy.capPx(0f, 60f));
        assertEquals(192, IconArtworkPolicy.capPx(3.5f, 0f));
        assertEquals(192, IconArtworkPolicy.capPx(1.0f, 38f));
    }

    @Test
    public void theBudgetAtTheBaseCapIsTodaysHeapShare() {
        assertEquals(4 * MB, IconArtworkPolicy.budgetBytes(0, 192));
        assertEquals(4 * MB, IconArtworkPolicy.budgetBytes(64, 192));
        assertEquals(8 * MB, IconArtworkPolicy.budgetBytes(128, 192));
        assertEquals(16 * MB, IconArtworkPolicy.budgetBytes(512, 192));
        assertEquals(4 * MB, IconArtworkPolicy.budgetBytes(-64, 192));
        // A cap below the base reads as the base.
        assertEquals(16 * MB, IconArtworkPolicy.budgetBytes(512, 100));
    }

    @Test
    public void theBudgetGrowsWithThePixelAreaOfTheCap() {
        // 210^2 / 192^2 = 1.1963
        assertEquals((int) (8L * MB * 210 * 210 / (192 * 192)),
            IconArtworkPolicy.budgetBytes(128, 210));
        assertEquals((int) (16L * MB * 210 * 210 / (192 * 192)),
            IconArtworkPolicy.budgetBytes(512, 210));
        // 240^2 / 192^2 = 1.5625, so 8 MB becomes exactly 12.5 MB.
        assertEquals(12 * MB + MB / 2, IconArtworkPolicy.budgetBytes(128, 240));
    }

    @Test
    public void theBudgetStopsAtTwentyFourMegabytes() {
        assertEquals(24 * MB, IconArtworkPolicy.budgetBytes(512, 240));
        assertEquals(24 * MB, IconArtworkPolicy.budgetBytes(512, 288));
        assertTrue(IconArtworkPolicy.budgetBytes(4096, 288) <= 24 * MB);
    }

    @Test
    public void homeIsFedTheSizeItDrawsNotTheRawPreset() {
        // A 60dp preset held to the dock's 48dp cap draws 48dp: base cap even at density 4.
        com.termux.app.place.layout.IconSize drawn = com.termux.app.dock.IconSizeCap.effective(
            com.termux.app.place.layout.IconSize.XL, com.termux.app.place.layout.IconSize.M);
        assertEquals(48, drawn.dp);
        assertEquals(192, IconArtworkPolicy.capPx(4.0f, drawn.dp));
        assertEquals(240, IconArtworkPolicy.capPx(4.0f, com.termux.app.place.layout.IconSize.XL.dp));
    }
}
