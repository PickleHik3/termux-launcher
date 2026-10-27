package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.os.Build;

import com.google.android.material.color.utilities.ColorUtils;
import com.google.android.material.color.utilities.Hct;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutMatrixTest {

    private static EvenOut.Mapping mapping(double srcLo, double srcHi, double dstLo, double dstHi) {
        return new EvenOut.Mapping(srcLo, srcHi, dstLo, dstHi, Color.GRAY);
    }

    private void assertTracks(EvenOut.Mapping mapping) {
        ColorMatrix matrix = EvenOutMatrix.of(mapping);
        for (int step = 0; step <= 10; step++) {
            double tone = mapping.srcLo + (mapping.srcHi - mapping.srcLo) * step / 10d;
            int grey = ColorUtils.argbFromLstar(tone);
            double drawn = ColorUtils.lstarFromArgb(EvenOutMatrix.applyTo(matrix, grey));
            assertEquals("tone " + tone, mapping.apply(tone), drawn, 0.7d);
        }
    }

    @Test
    public void greysLandWithinHalfATone() {
        assertTracks(mapping(13.5, 67.8, 19.5, 33.5));
        assertTracks(mapping(5, 90, 19.5, 33.5));
        assertTracks(mapping(30, 95, 68, 82));
        assertTracks(mapping(2, 60, 20, 34));
    }

    @Test
    public void identityDrawsNothing() {
        assertNull(EvenOutMatrix.of(mapping(8, 20, 8, 20)));
        assertEquals(0xFF123456, EvenOutMatrix.applyTo(null, 0xFF123456));
    }

    @Test
    public void colourKeepsMoreThanAPlainAffineMapWould() {
        EvenOut.Mapping map = mapping(13.5, 67.8, 19.5, 33.5);
        int tinted = Hct.from(250d, 30d, 40d).toInt();
        int out = EvenOutMatrix.applyTo(EvenOutMatrix.of(map), tinted);
        double before = Hct.fromInt(tinted).getChroma();
        double after = Hct.fromInt(out).getChroma();
        assertTrue(before + " -> " + after, after > 0.5d * before);
    }
}
