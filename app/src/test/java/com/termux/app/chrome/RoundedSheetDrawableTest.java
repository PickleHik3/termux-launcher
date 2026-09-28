package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The under-keyboard card's sheet takes no part in layout: a background that padded or sized its
 * host made the band taller than the stack counted for it, which is what squeezed the Floating
 * dock's rows over the keyboard into a strip.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class RoundedSheetDrawableTest {

    /** A material that asks for padding and a size, the way a stacked glass layer can. */
    private static Drawable pushyMaterial() {
        return new InsetDrawable(new ColorDrawable(0x80000000) {
            @Override public int getIntrinsicHeight() { return 256; }
            @Override public int getMinimumHeight() { return 256; }
        }, 30, 40, 30, 40);
    }

    @Test
    public void theSheetReportsNoPaddingAndNoSize() {
        RoundedSheetDrawable sheet = new RoundedSheetDrawable(pushyMaterial(), 28, 72f);
        Rect padding = new Rect(1, 1, 1, 1);
        assertFalse(sheet.getPadding(padding));
        assertEquals(new Rect(), padding);
        assertEquals(0, sheet.getMinimumHeight());
        assertEquals(-1, sheet.getIntrinsicHeight());
    }

    @Test
    public void aBandWearingItIsExactlyAsTallAsItsRow() {
        LinearLayout band = new LinearLayout(ApplicationProvider.getApplicationContext());
        band.setOrientation(LinearLayout.VERTICAL);
        band.addView(new View(band.getContext()), new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 52));
        band.setBackground(new RoundedSheetDrawable(pushyMaterial(), 28, 72f));
        band.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        assertEquals(52, band.getMeasuredHeight());
        assertEquals(0, band.getPaddingTop());
        assertEquals(0, band.getPaddingLeft());
    }

    @Test
    public void theSheetIsInsetAtItsSidesOnly() {
        assertEquals(new Rect(28, 0, 1052, 120),
            RoundedSheetDrawable.sheetBounds(new Rect(0, 0, 1080, 120), 28));
        assertEquals("never inverted", new Rect(50, 0, 50, 10),
            RoundedSheetDrawable.sheetBounds(new Rect(0, 0, 100, 10), 80));
    }

    @Test
    public void theRadiusIsClampedToAHalfCapsuleOfTheSheet() {
        assertEquals(26f, RoundedSheetDrawable.clampedRadiusPx(72f, 1024, 52), 0.001f);
        assertEquals(24f, RoundedSheetDrawable.clampedRadiusPx(24f, 1024, 200), 0.001f);
        RoundedSheetDrawable sheet = new RoundedSheetDrawable(pushyMaterial(), 28, 72f);
        sheet.setBounds(0, 0, 1080, 60);
        assertEquals(30f, sheet.drawnRadiusPx(), 0.001f);
    }
}
