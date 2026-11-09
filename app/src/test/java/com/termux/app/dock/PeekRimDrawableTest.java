package com.termux.app.dock;

import android.app.Application;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The Peek card's rim takes no part in layout: as a plain InsetDrawable it padded the dock's glass
 * host by the card's side inset and top, and the host's frost and tint stayed in that box after
 * the keyboard came up and the card was gone (pong: layers at 32,95-1048,709 in a 1080 px host).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PeekRimDrawableTest {

    @Test
    public void theRimReportsNoPaddingAndNoSize() {
        PeekRimDrawable rim = new PeekRimDrawable(new ColorDrawable(0x80FFFFFF), 32, 95, 32);
        Rect padding = new Rect(1, 1, 1, 1);
        assertFalse(rim.getPadding(padding));
        assertEquals(new Rect(), padding);
        assertEquals(0, rim.getMinimumWidth());
        assertEquals(0, rim.getMinimumHeight());
        assertEquals(-1, rim.getIntrinsicWidth());
        assertEquals(-1, rim.getIntrinsicHeight());
    }

    @Test
    public void theRimStillDrawsInsetToTheCard() {
        ColorDrawable inner = new ColorDrawable(0x80FFFFFF);
        PeekRimDrawable rim = new PeekRimDrawable(inner, 32, 95, 32);
        rim.setBounds(0, 0, 1080, 709);
        assertEquals(new Rect(32, 95, 1048, 709), inner.getBounds());
    }

    @Test
    public void aHostWearingItThenTheSlabKeepsItsLayersOverTheWholeHost() {
        FrameLayout host = new FrameLayout(ApplicationProvider.getApplicationContext());
        View tint = new View(host.getContext());
        host.addView(tint, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        // The card stands: the rim is the host's background.
        host.setBackground(new PeekRimDrawable(new ColorDrawable(0x80FFFFFF), 32, 95, 32));
        layOut(host);
        assertEquals(new Rect(0, 0, 1080, 709), boundsOf(tint));
        // The keyboard comes up: the card is gone and the slab's rim (no padding) replaces it.
        host.setBackground(new ColorDrawable(0x10FFFFFF));
        layOut(host);
        assertEquals(0, host.getPaddingLeft());
        assertEquals(0, host.getPaddingTop());
        assertEquals(0, host.getPaddingRight());
        assertEquals(new Rect(0, 0, 1080, 709), boundsOf(tint));
    }

    private static void layOut(FrameLayout host) {
        host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(709, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 1080, 709);
    }

    private static Rect boundsOf(View v) {
        return new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
    }
}
