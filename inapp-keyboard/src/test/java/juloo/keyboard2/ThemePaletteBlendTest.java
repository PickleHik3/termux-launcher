package juloo.keyboard2;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The palette blend the launcher drifts legends with across a crossing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ThemePaletteBlendTest {

    private static Theme.Palette palette(int label, int key) {
        return new Theme.Palette(0xFF101010, key, key, key, key, label, label, label, label, label,
            0xFF404040, true, 1f, 4f, 1f);
    }

    @Test
    public void eachEndIsExactlyItself() {
        Theme.Palette dark = palette(0xFF000000, 0xFFEEEEEE);
        Theme.Palette pale = palette(0xFFFFFFFF, 0xFF202020);
        assertSame(dark, Theme.Palette.blend(dark, pale, 0f));
        assertSame(pale, Theme.Palette.blend(dark, pale, 1f));
        assertSame(dark, Theme.Palette.blend(dark, pale, Float.NaN));
    }

    @Test
    public void legendsDriftWithoutAFlipAndGeometryIsTheLeavingOnes() {
        Theme.Palette dark = palette(0xFF000000, 0xFFEEEEEE);
        Theme.Palette pale = palette(0xFFFFFFFF, 0xFF202020);
        int previous = 0;
        for (int step = 1; step < 32; step++) {
            Theme.Palette p = Theme.Palette.blend(dark, pale, step / 32f);
            int label = p.labelColor & 0xFF;
            assertTrue("monotone at " + step, label >= previous);
            assertTrue("no step at " + step, label - previous <= 12);
            previous = label;
            assertEquals(dark.borderRadius, p.borderRadius, 0f);
            assertEquals(dark.labelColor == 0xFF000000 ? 0xFF : 0, p.labelColor >>> 24);
        }
        assertEquals(0x80, Theme.Palette.blend(dark, pale, 0.5f).labelColor & 0xFF);
    }
}
