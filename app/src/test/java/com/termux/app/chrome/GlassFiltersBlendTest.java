package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A dock crossing between two pages' frosts blends their matrices: the frost filters say what
 * matrix they apply, and the blend runs straight from one to the other.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassFiltersBlendTest {

    @Test public void aFrostFilterSaysItsMatrix() {
        assertNotNull(GlassFilters.matrixOf(GlassFilters.frost()));
        ColorMatrix evenOut = new ColorMatrix();
        evenOut.setScale(0.8f, 0.8f, 0.8f, 1f);
        assertNotNull(GlassFilters.matrixOf(GlassFilters.withEvenOut(evenOut, GlassFilters.frost())));
    }

    @Test public void anyOtherFilterHasNone() {
        assertNull(GlassFilters.matrixOf(null));
        assertNull(GlassFilters.matrixOf(new PorterDuffColorFilter(0, PorterDuff.Mode.SRC_OVER)));
    }

    @Test public void theBlendRunsStraightBetweenTheEnds() {
        ColorMatrix from = new ColorMatrix();
        from.setScale(1f, 1f, 1f, 1f);
        ColorMatrix to = new ColorMatrix();
        to.setScale(0.5f, 0.6f, 0.7f, 1f);
        assertArrayEquals(from.getArray(), matrix(GlassFilters.blend(from, to, 0f)), 1e-6f);
        assertArrayEquals(to.getArray(), matrix(GlassFilters.blend(from, to, 1f)), 1e-6f);
        assertArrayEquals(to.getArray(), matrix(GlassFilters.blend(from, to, 3f)), 1e-6f);
        float[] half = matrix(GlassFilters.blend(from, to, 0.5f));
        assertArrayEquals(new float[] {0.75f, 0.8f, 0.85f},
            new float[] {half[0], half[6], half[12]}, 1e-6f);
    }

    private static float[] matrix(ColorMatrixColorFilter filter) {
        ColorMatrix out = new ColorMatrix();
        filter.getColorMatrix(out);
        return out.getArray();
    }
}
