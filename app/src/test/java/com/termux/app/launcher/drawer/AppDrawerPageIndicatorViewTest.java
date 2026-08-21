package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerPageIndicatorViewTest {

    @Test public void zeroAndOnePageStayHidden() {
        AppDrawerPageIndicatorView dots = dots();
        dots.setPageCount(0);
        assertEquals(View.GONE, dots.getVisibility());
        dots.setPageCount(1);
        assertEquals(View.GONE, dots.getVisibility());
    }

    @Test public void selectionClampsAndUpdatesAccessibility() {
        AppDrawerPageIndicatorView dots = dots();
        dots.setPageCount(4);
        dots.setSelectedPage(2);
        assertEquals(View.VISIBLE, dots.getVisibility());
        assertEquals(2, dots.getSelectedPage());
        assertEquals("Page 3 of 4", dots.getContentDescription().toString());
        dots.setPageCount(2);
        assertEquals(1, dots.getSelectedPage());
        assertEquals("Page 2 of 2", dots.getContentDescription().toString());
    }

    @Test public void manyDotsCompressIntoANarrowWidthWithoutInvalidCoordinates() {
        AppDrawerPageIndicatorView dots = dots();
        dots.setPageCount(60);
        dots.measure(View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY));
        dots.layout(0, 0, 40, 20);
        Bitmap bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888);
        dots.draw(new Canvas(bitmap));
        assertFalse(bitmap.isRecycled());
        assertEquals(40, dots.getWidth());
    }

    @Test public void rtlMapsPageZeroToTheRightmostDot() {
        assertEquals(2, AppDrawerPageIndicatorView.pageForVisualDot(
            0, 3, View.LAYOUT_DIRECTION_RTL));
        assertEquals(1, AppDrawerPageIndicatorView.pageForVisualDot(
            1, 3, View.LAYOUT_DIRECTION_RTL));
        assertEquals(0, AppDrawerPageIndicatorView.pageForVisualDot(
            2, 3, View.LAYOUT_DIRECTION_RTL));
    }

    private static AppDrawerPageIndicatorView dots() {
        return new AppDrawerPageIndicatorView(RuntimeEnvironment.getApplication());
    }
}
